package com.example.platform

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.engine.ColorFillMode
import com.example.engine.VectorScene
import com.example.engine.WatermarkPlacement
import com.example.engine.WatermarkPlacementSolver
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface WallpaperSaveResult {
    data class Success(
        val uri: Uri,
        val fileName: String,
        val widthPx: Int,
        val heightPx: Int,
        val measuredBlackPercent: Int
    ) : WallpaperSaveResult

    data class Error(val reason: String) : WallpaperSaveResult
}

sealed interface WallpaperShareResult {
    data class Success(
        val chooserIntent: Intent,
        val contentUri: Uri,
        val cacheFile: File,
        val widthPx: Int,
        val heightPx: Int
    ) : WallpaperShareResult

    data class Error(val reason: String) : WallpaperShareResult
}

/**
 * Android platform service for lossless PNG gallery saving (`MediaStore`) and `FileProvider`
 * share sheet export. Separated from `com.example.engine` so core wallpaper math stays portable.
 */
object WallpaperExporter {

    const val SHARED_WALLPAPERS_DIR = "shared_wallpapers"
    const val GALLERY_SUBFOLDER = "Rimalux"

    fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"

    suspend fun saveWallpaperToDevice(
        context: Context,
        scene: VectorScene,
        patternId: String,
        seed: Long,
        paletteColors: List<Long>,
        pitchBlackRatio: Float,
        colorFillMode: ColorFillMode
    ): WallpaperSaveResult = withContext(Dispatchers.IO) {
        runCatching {
            val spec = WallpaperRenderer.getTargetWallpaperSpec(context)
            val bitmap = withContext(Dispatchers.Default) {
                WallpaperRenderer.renderToBitmap(
                    scene = scene,
                    paletteColors = paletteColors,
                    pitchBlackRatio = pitchBlackRatio,
                    widthPx = spec.widthPx,
                    heightPx = spec.heightPx,
                    colorFillMode = colorFillMode,
                    densityDpi = spec.densityDpi
                )
            }
            try {
                val measuredBlack = WallpaperRenderer.sampleTrueBlackPercentage(bitmap)
                val safePattern = sanitizePatternToken(patternId)
                val seedHex = (seed and 0xFFFFFFFFFFFFL).toString(16).uppercase(Locale.ROOT)
                val fileName = "Rimalux_${safePattern}_${seedHex}_${System.currentTimeMillis()}.png"

                val resolver = context.applicationContext.contentResolver
                val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val nowSeconds = System.currentTimeMillis() / 1000L
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.DATE_ADDED, nowSeconds)
                    put(MediaStore.Images.Media.DATE_MODIFIED, nowSeconds)
                    put(MediaStore.Images.Media.WIDTH, spec.widthPx)
                    put(MediaStore.Images.Media.HEIGHT, spec.heightPx)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/$GALLERY_SUBFOLDER"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val insertedUri = resolver.insert(collectionUri, values)
                    ?: throw IOException("MediaStore could not create wallpaper entry")

                try {
                    val stream = resolver.openOutputStream(insertedUri)
                        ?: throw IOException("Unable to open gallery output stream")
                    stream.buffered().use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            throw IOException("Failed to encode lossless PNG")
                        }
                        out.flush()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val publishValues = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }
                        resolver.update(insertedUri, publishValues, null, null)
                    }
                } catch (writeError: Throwable) {
                    runCatching { resolver.delete(insertedUri, null, null) }
                    throw writeError
                }

                WallpaperSaveResult.Success(
                    uri = insertedUri,
                    fileName = fileName,
                    widthPx = spec.widthPx,
                    heightPx = spec.heightPx,
                    measuredBlackPercent = measuredBlack
                )
            } finally {
                bitmap.recycle()
            }
        }.getOrElse { error ->
            WallpaperSaveResult.Error(error.localizedMessage ?: "Gallery storage unavailable")
        }
    }

    suspend fun prepareShareWallpaperIntent(
        context: Context,
        scene: VectorScene,
        patternId: String,
        patternTitle: String,
        seed: Long,
        paletteColors: List<Long>,
        pitchBlackRatio: Float,
        colorFillMode: ColorFillMode
    ): WallpaperShareResult = withContext(Dispatchers.IO) {
        runCatching {
            val spec = WallpaperRenderer.getTargetWallpaperSpec(context)
            val bitmap = withContext(Dispatchers.Default) {
                WallpaperRenderer.renderToBitmap(
                    scene = scene,
                    paletteColors = paletteColors,
                    pitchBlackRatio = pitchBlackRatio,
                    widthPx = spec.widthPx,
                    heightPx = spec.heightPx,
                    colorFillMode = colorFillMode,
                    densityDpi = spec.densityDpi
                ).also { rendered ->
                    applyShareWatermark(
                        context = context,
                        bitmap = rendered,
                        patternTitle = patternTitle,
                        paletteColors = paletteColors
                    )
                }
            }
            try {
                val shareDir = File(context.cacheDir, SHARED_WALLPAPERS_DIR)
                if (!shareDir.exists() && !shareDir.mkdirs()) {
                    throw IOException("Unable to prepare share cache directory")
                }
                cleanupOldSharedWallpapers(shareDir, keepMaxCount = 3)

                val safePattern = sanitizePatternToken(patternId)
                val seedHex = (seed and 0xFFFFFFFFFFFFL).toString(16).uppercase(Locale.ROOT)
                val cacheFile = File(shareDir, "Rimalux_${safePattern}_${seedHex}.png")

                FileOutputStream(cacheFile).buffered().use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("Failed to encode lossless PNG")
                    }
                    out.flush()
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    fileProviderAuthority(context),
                    cacheFile
                )
                val subject = context.getString(R.string.share_wallpaper_subject, patternTitle)
                val sendIntent = Intent.ACTION_SEND.let { action ->
                    Intent(action).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        putExtra(Intent.EXTRA_TITLE, subject)
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        clipData = ClipData.newRawUri(subject, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                val chooserTitle = context.getString(R.string.share_chooser_title)
                val chooserIntent = Intent.createChooser(sendIntent, chooserTitle).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                WallpaperShareResult.Success(
                    chooserIntent = chooserIntent,
                    contentUri = contentUri,
                    cacheFile = cacheFile,
                    widthPx = spec.widthPx,
                    heightPx = spec.heightPx
                )
            } finally {
                bitmap.recycle()
            }
        }.getOrElse { error ->
            WallpaperShareResult.Error(error.localizedMessage ?: "Unable to prepare wallpaper for sharing")
        }
    }

    /**
     * Draws a barely noticeable `"Procedurally generated by Rimalux · <pattern>"` micro-watermark
     * strictly inside the social media safe zone, dynamically positioned in the darkest / emptiest
     * `#000000` pocket of [bitmap]. Called exclusively when sharing.
     */
    internal fun applyShareWatermark(
        context: Context,
        bitmap: Bitmap,
        patternTitle: String,
        paletteColors: List<Long> = emptyList()
    ): WatermarkPlacement {
        val cleanTitle = patternTitle.trim().ifEmpty { context.getString(R.string.app_name) }
        val watermarkText = context.getString(R.string.share_watermark_text, cleanTitle)
        val safeZone = WatermarkPlacementSolver.computeSocialSafeZone(bitmap.width, bitmap.height)

        val typeface = runCatching {
            ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)
        }.getOrNull() ?: Typeface.SANS_SERIF

        val shortSide = min(bitmap.width, bitmap.height).toFloat()
        var textSizePx = (shortSide * 0.0125f).coerceIn(10f, 32f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx
            this.letterSpacing = 0.03f
        }

        val maxAllowedTextWidth = safeZone.widthPx * 0.88f
        val initialWidth = paint.measureText(watermarkText)
        if (initialWidth > maxAllowedTextWidth && initialWidth > 0f) {
            textSizePx = (textSizePx * (maxAllowedTextWidth / initialWidth)).coerceAtLeast(8f)
            paint.textSize = textSizePx
        }

        val fm = paint.fontMetrics
        val textWidth = paint.measureText(watermarkText).coerceIn(1f, safeZone.widthPx.toFloat())
        val textHeight = (fm.descent - fm.ascent).coerceIn(1f, safeZone.heightPx.toFloat())

        val rowBuffer = IntArray(bitmap.width)
        var cachedY = -1
        val placement = WatermarkPlacementSolver.findOptimalPlacement(
            widthPx = bitmap.width,
            heightPx = bitmap.height,
            textWidthPx = textWidth,
            textHeightPx = textHeight
        ) { x, y ->
            if (y != cachedY) {
                bitmap.getPixels(rowBuffer, 0, bitmap.width, 0, y, bitmap.width, 1)
                cachedY = y
            }
            rowBuffer[x]
        }

        paint.color = WatermarkPlacementSolver.computeSubtleWatermarkArgb(
            meanLuminance = placement.meanLuminance,
            paletteColors = paletteColors
        )

        val baselineY = placement.topPx - fm.ascent
        Canvas(bitmap).drawText(watermarkText, placement.leftPx, baselineY, paint)
        return placement
    }

    private fun sanitizePatternToken(patternId: String): String {
        return patternId
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifEmpty { "pattern" }
    }

    private fun cleanupOldSharedWallpapers(dir: File, keepMaxCount: Int) {
        val pngFiles = dir.listFiles { file -> file.isFile && file.name.endsWith(".png") } ?: return
        if (pngFiles.size <= keepMaxCount) return
        pngFiles.sortByDescending { it.lastModified() }
        for (i in keepMaxCount until pngFiles.size) {
            runCatching { pngFiles[i].delete() }
        }
    }
}

package com.example.platform

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient as AndroidLinearGradient
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.engine.ArcSectorPrimitive
import com.example.engine.ColorFillMode
import com.example.engine.ColorHarmonyEngine
import com.example.engine.DeepBlackRatioEngine
import com.example.engine.PolygonPrimitive
import com.example.engine.PolylineRibbonPrimitive
import com.example.engine.VectorPrimitive
import com.example.engine.VectorScene
import com.example.model.WallpaperTarget
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface WallpaperApplyResult {
    data class Success(
        val widthPx: Int,
        val heightPx: Int,
        val densityDpi: Int,
        val measuredBlackPercent: Int,
        val skippedAsDuplicate: Boolean = false
    ) : WallpaperApplyResult

    data class Error(val reason: String) : WallpaperApplyResult
}

data class WallpaperDisplaySpec(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int
)

/**
 * Android platform adapter that rasterizes pure [VectorScene] descriptions from `com.example.engine`
 * onto `android.graphics.Bitmap` surfaces and applies them via `android.app.WallpaperManager`.
 *
 * All mathematical geometry, color shading, gradient vectors, viewport scaling, and `#000000`
 * True Black percentage calculations are delegated to [DeepBlackRatioEngine] and [ColorHarmonyEngine].
 */
object WallpaperRenderer {

    const val MAX_WALLPAPER_AXIS_PX: Int = DeepBlackRatioEngine.MAX_WALLPAPER_AXIS_PX

    fun resolveArgbColor(
        paletteColors: List<Long>,
        colorIndex: Int,
        shadeFactor: Float
    ): Long = ColorHarmonyEngine.resolveArgbColor(paletteColors, colorIndex, shadeFactor)

    fun renderToBitmap(
        scene: VectorScene,
        paletteColors: List<Long>,
        pitchBlackRatio: Float,
        widthPx: Int,
        heightPx: Int,
        colorFillMode: ColorFillMode = ColorFillMode.GRADIENT,
        densityDpi: Int = DisplayMetrics.DENSITY_XXHIGH
    ): Bitmap {
        val (safeW, safeH) = DeepBlackRatioEngine.clampDimensionsToMaxAxis(widthPx, heightPx)
        val clampedBlack = DeepBlackRatioEngine.clampPitchBlackRatio(pitchBlackRatio)
        val isGradient = colorFillMode == ColorFillMode.GRADIENT

        val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        bitmap.density = densityDpi.coerceIn(DisplayMetrics.DENSITY_LOW, DisplayMetrics.DENSITY_XXXHIGH)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(AndroidColor.BLACK)

        val transform = DeepBlackRatioEngine.computeViewportTransform(
            canvasWidth = safeW.toFloat(),
            canvasHeight = safeH.toFloat(),
            sceneAspectRatio = scene.aspectRatio
        )
        val scale = transform.scale
        val offsetX = transform.offsetX
        val offsetY = transform.offsetY
        val drawMicroSeams = transform.drawMicroSeams

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            isDither = true
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isDither = true
        }
        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            color = AndroidColor.BLACK
        }

        val path = AndroidPath()
        val outerOval = RectF()
        val innerOval = RectF()

        for (primitive in scene.primitives) {
            if (!DeepBlackRatioEngine.isPrimitiveVisible(primitive, clampedBlack)) continue

            val solidArgb = ColorHarmonyEngine.resolveArgbColor(
                paletteColors = paletteColors,
                colorIndex = primitive.colorIndex,
                shadeFactor = primitive.shadeFactor
            ).toInt()

            when (primitive) {
                is PolygonPrimitive -> {
                    val verts = primitive.vertices
                    if (verts.size >= 3) {
                        path.reset()
                        path.fillType = AndroidPath.FillType.WINDING
                        var minX = Float.POSITIVE_INFINITY
                        var minY = Float.POSITIVE_INFINITY
                        var maxX = Float.NEGATIVE_INFINITY
                        var maxY = Float.NEGATIVE_INFINITY

                        val x0 = offsetX + verts[0].x * scale
                        val y0 = offsetY + verts[0].y * scale
                        path.moveTo(x0, y0)
                        minX = min(minX, x0)
                        minY = min(minY, y0)
                        maxX = max(maxX, x0)
                        maxY = max(maxY, y0)

                        for (i in 1 until verts.size) {
                            val px = offsetX + verts[i].x * scale
                            val py = offsetY + verts[i].y * scale
                            path.lineTo(px, py)
                            minX = min(minX, px)
                            minY = min(minY, py)
                            maxX = max(maxX, px)
                            maxY = max(maxY, py)
                        }
                        path.close()

                        configureAndroidPaintColorOrShader(
                            paint = fillPaint,
                            isGradient = isGradient,
                            solidArgb = solidArgb,
                            paletteColors = paletteColors,
                            primitive = primitive,
                            minX = minX,
                            minY = minY,
                            maxX = maxX,
                            maxY = maxY
                        )
                        canvas.drawPath(path, fillPaint)

                        if (drawMicroSeams && primitive.strokeSeamNorm > 0f) {
                            seamPaint.strokeWidth = (primitive.strokeSeamNorm * scale).coerceIn(0.5f, 1.7f)
                            canvas.drawPath(path, seamPaint)
                        }
                    }
                }

                is ArcSectorPrimitive -> {
                    val cx = offsetX + primitive.center.x * scale
                    val cy = offsetY + primitive.center.y * scale
                    val outR = primitive.outerRadius * scale
                    val inR = primitive.innerRadius * scale
                    if (outR <= 0.5f) continue

                    val minX = cx - outR
                    val minY = cy - outR
                    val maxX = cx + outR
                    val maxY = cy + outR
                    outerOval.set(minX, minY, maxX, maxY)

                    configureAndroidPaintColorOrShader(
                        paint = fillPaint,
                        isGradient = isGradient,
                        solidArgb = solidArgb,
                        paletteColors = paletteColors,
                        primitive = primitive,
                        minX = minX,
                        minY = minY,
                        maxX = maxX,
                        maxY = maxY
                    )

                    if (inR <= 0.5f && primitive.sweepAngleDeg >= 359.5f) {
                        canvas.drawCircle(cx, cy, outR, fillPaint)
                    } else if (inR <= 0.5f) {
                        path.reset()
                        path.fillType = AndroidPath.FillType.WINDING
                        path.moveTo(cx, cy)
                        path.arcTo(outerOval, primitive.startAngleDeg, primitive.sweepAngleDeg, false)
                        path.close()
                        canvas.drawPath(path, fillPaint)
                    } else if (primitive.sweepAngleDeg >= 359.5f) {
                        innerOval.set(cx - inR, cy - inR, cx + inR, cy + inR)
                        path.reset()
                        path.fillType = AndroidPath.FillType.EVEN_ODD
                        path.addOval(outerOval, AndroidPath.Direction.CW)
                        path.addOval(innerOval, AndroidPath.Direction.CW)
                        canvas.drawPath(path, fillPaint)
                    } else {
                        innerOval.set(cx - inR, cy - inR, cx + inR, cy + inR)
                        path.reset()
                        path.fillType = AndroidPath.FillType.WINDING
                        val startRad = (primitive.startAngleDeg * PI / 180.0).toFloat()
                        path.moveTo(cx + inR * cos(startRad), cy + inR * sin(startRad))
                        path.arcTo(outerOval, primitive.startAngleDeg, primitive.sweepAngleDeg, false)
                        path.arcTo(
                            innerOval,
                            primitive.startAngleDeg + primitive.sweepAngleDeg,
                            -primitive.sweepAngleDeg,
                            false
                        )
                        path.close()
                        canvas.drawPath(path, fillPaint)
                    }
                }

                is PolylineRibbonPrimitive -> {
                    val pts = primitive.points
                    if (pts.size >= 2) {
                        path.reset()
                        var minX = Float.POSITIVE_INFINITY
                        var minY = Float.POSITIVE_INFINITY
                        var maxX = Float.NEGATIVE_INFINITY
                        var maxY = Float.NEGATIVE_INFINITY

                        val x0 = offsetX + pts[0].x * scale
                        val y0 = offsetY + pts[0].y * scale
                        path.moveTo(x0, y0)
                        minX = min(minX, x0)
                        minY = min(minY, y0)
                        maxX = max(maxX, x0)
                        maxY = max(maxY, y0)

                        for (i in 1 until pts.size) {
                            val px = offsetX + pts[i].x * scale
                            val py = offsetY + pts[i].y * scale
                            path.lineTo(px, py)
                            minX = min(minX, px)
                            minY = min(minY, py)
                            maxX = max(maxX, px)
                            maxY = max(maxY, py)
                        }
                        if (primitive.closed) {
                            path.close()
                        }

                        strokePaint.strokeWidth = (primitive.strokeWidthNorm * scale).coerceAtLeast(1.3f)
                        configureAndroidPaintColorOrShader(
                            paint = strokePaint,
                            isGradient = isGradient,
                            solidArgb = solidArgb,
                            paletteColors = paletteColors,
                            primitive = primitive,
                            minX = minX,
                            minY = minY,
                            maxX = maxX,
                            maxY = maxY
                        )
                        canvas.drawPath(path, strokePaint)
                    }
                }
            }
        }
        return bitmap
    }

    private fun configureAndroidPaintColorOrShader(
        paint: Paint,
        isGradient: Boolean,
        solidArgb: Int,
        paletteColors: List<Long>,
        primitive: VectorPrimitive,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float
    ) {
        if (!isGradient) {
            paint.shader = null
            paint.color = solidArgb
            return
        }
        val segment = ColorHarmonyEngine.computeLinearGradient(
            paletteColors = paletteColors,
            primitive = primitive,
            minX = minX,
            minY = minY,
            maxX = maxX,
            maxY = maxY
        )
        paint.color = AndroidColor.WHITE
        paint.shader = AndroidLinearGradient(
            segment.startX,
            segment.startY,
            segment.endX,
            segment.endY,
            segment.startColorArgb.toInt(),
            segment.endColorArgb.toInt(),
            Shader.TileMode.CLAMP
        )
    }

    fun sampleTrueBlackPercentage(bitmap: Bitmap): Int {
        return DeepBlackRatioEngine.measureTrueBlackPercentage(
            widthPx = bitmap.width,
            heightPx = bitmap.height
        ) { x, y ->
            bitmap.getPixel(x, y)
        }
    }

    fun getTargetWallpaperSpec(context: Context): WallpaperDisplaySpec {
        val dm = context.resources.displayMetrics
        val densityDpi = (dm.densityDpi.takeIf { it >= DisplayMetrics.DENSITY_LOW }
            ?: DisplayMetrics.DENSITY_XXHIGH).coerceIn(
            DisplayMetrics.DENSITY_LOW,
            DisplayMetrics.DENSITY_XXXHIGH
        )

        var physicalW = dm.widthPixels
        var physicalH = dm.heightPixels

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bounds = windowManager.maximumWindowMetrics.bounds
                    if (bounds.width() >= 320 && bounds.height() >= 480) {
                        physicalW = bounds.width()
                        physicalH = bounds.height()
                    }
                } else {
                    val realMetrics = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay?.getRealMetrics(realMetrics)
                    if (realMetrics.widthPixels >= 320 && realMetrics.heightPixels >= 480) {
                        physicalW = realMetrics.widthPixels
                        physicalH = realMetrics.heightPixels
                    }
                }
            }
        }

        val rawW = physicalW.takeIf { it >= 320 } ?: 1080
        val rawH = physicalH.takeIf { it >= 480 } ?: 2400
        val (clampedW, clampedH) = DeepBlackRatioEngine.clampDimensionsToMaxAxis(rawW, rawH)
        return WallpaperDisplaySpec(
            widthPx = clampedW,
            heightPx = clampedH,
            densityDpi = densityDpi
        )
    }

    fun getTargetWallpaperDimensions(context: Context): Pair<Int, Int> {
        val spec = getTargetWallpaperSpec(context)
        return Pair(spec.widthPx, spec.heightPx)
    }

    suspend fun applyWallpaperToSystem(
        context: Context,
        scene: VectorScene,
        paletteColors: List<Long>,
        pitchBlackRatio: Float,
        colorFillMode: ColorFillMode,
        target: WallpaperTarget
    ): WallpaperApplyResult = withContext(Dispatchers.IO) {
        runCatching {
            val spec = getTargetWallpaperSpec(context)
            val bitmap = renderToBitmap(
                scene = scene,
                paletteColors = paletteColors,
                pitchBlackRatio = pitchBlackRatio,
                widthPx = spec.widthPx,
                heightPx = spec.heightPx,
                colorFillMode = colorFillMode,
                densityDpi = spec.densityDpi
            )
            try {
                val measuredBlack = sampleTrueBlackPercentage(bitmap)
                val wallpaperManager = WallpaperManager.getInstance(context.applicationContext)
                wallpaperManager.setBitmap(bitmap, null, true, target.flag)
                WallpaperApplyResult.Success(
                    widthPx = spec.widthPx,
                    heightPx = spec.heightPx,
                    densityDpi = spec.densityDpi,
                    measuredBlackPercent = measuredBlack
                )
            } finally {
                bitmap.recycle()
            }
        }.getOrElse { error ->
            WallpaperApplyResult.Error(error.localizedMessage ?: "Wallpaper service unavailable")
        }
    }

    suspend fun applySeparateWallpapersToSystem(
        context: Context,
        homeScene: VectorScene,
        homePaletteColors: List<Long>,
        homePitchBlackRatio: Float,
        lockScene: VectorScene,
        lockPaletteColors: List<Long>,
        lockPitchBlackRatio: Float,
        colorFillMode: ColorFillMode
    ): WallpaperApplyResult = withContext(Dispatchers.IO) {
        runCatching {
            val spec = getTargetWallpaperSpec(context)
            val wallpaperManager = WallpaperManager.getInstance(context.applicationContext)

            val homeMeasuredBlack: Int
            val homeBitmap = renderToBitmap(
                scene = homeScene,
                paletteColors = homePaletteColors,
                pitchBlackRatio = homePitchBlackRatio,
                widthPx = spec.widthPx,
                heightPx = spec.heightPx,
                colorFillMode = colorFillMode,
                densityDpi = spec.densityDpi
            )
            try {
                homeMeasuredBlack = sampleTrueBlackPercentage(homeBitmap)
                wallpaperManager.setBitmap(homeBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            } finally {
                homeBitmap.recycle()
            }

            val lockMeasuredBlack: Int
            val lockBitmap = renderToBitmap(
                scene = lockScene,
                paletteColors = lockPaletteColors,
                pitchBlackRatio = lockPitchBlackRatio,
                widthPx = spec.widthPx,
                heightPx = spec.heightPx,
                colorFillMode = colorFillMode,
                densityDpi = spec.densityDpi
            )
            try {
                lockMeasuredBlack = sampleTrueBlackPercentage(lockBitmap)
                wallpaperManager.setBitmap(lockBitmap, null, true, WallpaperManager.FLAG_LOCK)
            } finally {
                lockBitmap.recycle()
            }

            WallpaperApplyResult.Success(
                widthPx = spec.widthPx,
                heightPx = spec.heightPx,
                densityDpi = spec.densityDpi,
                measuredBlackPercent = ((homeMeasuredBlack + lockMeasuredBlack) / 2f).roundToInt()
            )
        }.getOrElse { error ->
            WallpaperApplyResult.Error(error.localizedMessage ?: "Wallpaper service unavailable")
        }
    }
}

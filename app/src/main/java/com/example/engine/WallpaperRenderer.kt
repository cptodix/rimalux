package com.example.engine

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.example.model.ColorFillMode
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
        val measuredBlackPercent: Int
    ) : WallpaperApplyResult

    data class Error(val reason: String) : WallpaperApplyResult
}

data class WallpaperDisplaySpec(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int
)

object WallpaperRenderer {

    private const val PITCH_BLACK_ARGB: Long = 0xFF000000L

    fun resolveArgbColor(
        paletteColors: List<Long>,
        colorIndex: Int,
        shadeFactor: Float
    ): Long {
        if (paletteColors.isEmpty()) return 0xFF00F5D4L
        val raw = paletteColors[colorIndex.mod(paletteColors.size)]
        val safeShade = shadeFactor.coerceIn(0.18f, 1.35f)
        val r = (((raw shr 16) and 0xFF) * safeShade).roundToInt().coerceIn(0, 255)
        val g = (((raw shr 8) and 0xFF) * safeShade).roundToInt().coerceIn(0, 255)
        var b = ((raw and 0xFF) * safeShade).roundToInt().coerceIn(0, 255)
        if ((raw and 0xFFFFFFL) != 0L && r == 0 && g == 0 && b == 0) {
            b = 4
        }
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    private fun resolveGradientColors(
        paletteColors: List<Long>,
        primitive: VectorPrimitive
    ): Pair<Long, Long> {
        if (paletteColors.size <= 1) {
            val c1 = resolveArgbColor(paletteColors, primitive.colorIndex, primitive.shadeFactor * 1.18f)
            val c2 = resolveArgbColor(paletteColors, primitive.colorIndex, primitive.shadeFactor * 0.48f)
            return c1 to c2
        }
        val primaryIdx = primitive.colorIndex.mod(paletteColors.size)
        var secondaryIdx = primitive.secondaryColorIndex.mod(paletteColors.size)
        if (secondaryIdx == primaryIdx) {
            secondaryIdx = (primaryIdx + 1) % paletteColors.size
        }
        val c1 = resolveArgbColor(paletteColors, primaryIdx, primitive.shadeFactor * 1.10f)
        val c2 = resolveArgbColor(paletteColors, secondaryIdx, primitive.shadeFactor * 0.78f)
        return c1 to c2
    }

    fun DrawScope.drawVectorScene(
        scene: VectorScene,
        paletteColors: List<Long>,
        pitchBlackRatio: Float,
        colorFillMode: ColorFillMode = ColorFillMode.GRADIENT
    ) {
        val clampedBlack = pitchBlackRatio.coerceIn(0f, 0.95f)
        clipRect {
            drawRect(color = Color(PITCH_BLACK_ARGB))

            if (size.width <= 1f || size.height <= 1f) return@clipRect

            val scale = max(size.width / 1f, size.height / scene.aspectRatio)
            val offsetX = (size.width - scale * 1f) * 0.5f
            val offsetY = (size.height - scale * scene.aspectRatio) * 0.5f
            val drawMicroSeams = scale >= 220f

            val reusablePath = Path()

            for (primitive in scene.primitives) {
                if (primitive.threshold < clampedBlack) continue

                val isGradient = colorFillMode == ColorFillMode.GRADIENT
                val solidColor = Color(
                    resolveArgbColor(paletteColors, primitive.colorIndex, primitive.shadeFactor)
                )

                when (primitive) {
                    is PolygonPrimitive -> {
                        val verts = primitive.vertices
                        if (verts.size >= 3) {
                            reusablePath.reset()
                            reusablePath.fillType = PathFillType.NonZero
                            var minX = Float.POSITIVE_INFINITY
                            var minY = Float.POSITIVE_INFINITY
                            var maxX = Float.NEGATIVE_INFINITY
                            var maxY = Float.NEGATIVE_INFINITY

                            val x0 = offsetX + verts[0].x * scale
                            val y0 = offsetY + verts[0].y * scale
                            reusablePath.moveTo(x0, y0)
                            minX = min(minX, x0)
                            minY = min(minY, y0)
                            maxX = max(maxX, x0)
                            maxY = max(maxY, y0)

                            for (i in 1 until verts.size) {
                                val px = offsetX + verts[i].x * scale
                                val py = offsetY + verts[i].y * scale
                                reusablePath.lineTo(px, py)
                                minX = min(minX, px)
                                minY = min(minY, py)
                                maxX = max(maxX, px)
                                maxY = max(maxY, py)
                            }
                            reusablePath.close()

                            if (isGradient) {
                                val brush = buildComposeGradientBrush(
                                    paletteColors,
                                    primitive,
                                    minX,
                                    minY,
                                    maxX,
                                    maxY
                                )
                                drawPath(path = reusablePath, brush = brush)
                            } else {
                                drawPath(path = reusablePath, color = solidColor)
                            }

                            if (drawMicroSeams && primitive.strokeSeamNorm > 0f) {
                                val seamPx = (primitive.strokeSeamNorm * scale).coerceIn(0.6f, 2.2f)
                                drawPath(
                                    path = reusablePath,
                                    color = Color.Black.copy(alpha = 0.80f),
                                    style = Stroke(width = seamPx, join = StrokeJoin.Round)
                                )
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

                        if (inR <= 0.5f && primitive.sweepAngleDeg >= 359.5f) {
                            if (isGradient) {
                                val brush = buildComposeGradientBrush(
                                    paletteColors,
                                    primitive,
                                    minX,
                                    minY,
                                    maxX,
                                    maxY
                                )
                                drawCircle(brush = brush, radius = outR, center = Offset(cx, cy))
                            } else {
                                drawCircle(color = solidColor, radius = outR, center = Offset(cx, cy))
                            }
                        } else {
                            reusablePath.reset()
                            if (inR <= 0.5f) {
                                reusablePath.fillType = PathFillType.NonZero
                                reusablePath.moveTo(cx, cy)
                                reusablePath.arcTo(
                                    rect = Rect(minX, minY, maxX, maxY),
                                    startAngleDegrees = primitive.startAngleDeg,
                                    sweepAngleDegrees = primitive.sweepAngleDeg,
                                    forceMoveTo = false
                                )
                                reusablePath.close()
                            } else if (primitive.sweepAngleDeg >= 359.5f) {
                                reusablePath.fillType = PathFillType.EvenOdd
                                reusablePath.addOval(Rect(minX, minY, maxX, maxY))
                                reusablePath.addOval(Rect(cx - inR, cy - inR, cx + inR, cy + inR))
                            } else {
                                reusablePath.fillType = PathFillType.NonZero
                                val startRad = (primitive.startAngleDeg * PI / 180.0).toFloat()
                                reusablePath.moveTo(
                                    cx + inR * cos(startRad),
                                    cy + inR * sin(startRad)
                                )
                                reusablePath.arcTo(
                                    rect = Rect(minX, minY, maxX, maxY),
                                    startAngleDegrees = primitive.startAngleDeg,
                                    sweepAngleDegrees = primitive.sweepAngleDeg,
                                    forceMoveTo = false
                                )
                                reusablePath.arcTo(
                                    rect = Rect(cx - inR, cy - inR, cx + inR, cy + inR),
                                    startAngleDegrees = primitive.startAngleDeg + primitive.sweepAngleDeg,
                                    sweepAngleDegrees = -primitive.sweepAngleDeg,
                                    forceMoveTo = false
                                )
                                reusablePath.close()
                            }

                            if (isGradient) {
                                val brush = buildComposeGradientBrush(
                                    paletteColors,
                                    primitive,
                                    minX,
                                    minY,
                                    maxX,
                                    maxY
                                )
                                drawPath(path = reusablePath, brush = brush)
                            } else {
                                drawPath(path = reusablePath, color = solidColor)
                            }
                        }
                    }

                    is PolylineRibbonPrimitive -> {
                        val pts = primitive.points
                        if (pts.size >= 2) {
                            reusablePath.reset()
                            var minX = Float.POSITIVE_INFINITY
                            var minY = Float.POSITIVE_INFINITY
                            var maxX = Float.NEGATIVE_INFINITY
                            var maxY = Float.NEGATIVE_INFINITY

                            val x0 = offsetX + pts[0].x * scale
                            val y0 = offsetY + pts[0].y * scale
                            reusablePath.moveTo(x0, y0)
                            minX = min(minX, x0)
                            minY = min(minY, y0)
                            maxX = max(maxX, x0)
                            maxY = max(maxY, y0)

                            for (i in 1 until pts.size) {
                                val px = offsetX + pts[i].x * scale
                                val py = offsetY + pts[i].y * scale
                                reusablePath.lineTo(px, py)
                                minX = min(minX, px)
                                minY = min(minY, py)
                                maxX = max(maxX, px)
                                maxY = max(maxY, py)
                            }
                            if (primitive.closed) {
                                reusablePath.close()
                            }

                            val strokeStyle = Stroke(
                                width = (primitive.strokeWidthNorm * scale).coerceAtLeast(1.1f),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                            if (isGradient) {
                                val brush = buildComposeGradientBrush(
                                    paletteColors,
                                    primitive,
                                    minX,
                                    minY,
                                    maxX,
                                    maxY
                                )
                                drawPath(path = reusablePath, brush = brush, style = strokeStyle)
                            } else {
                                drawPath(path = reusablePath, color = solidColor, style = strokeStyle)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildComposeGradientBrush(
        paletteColors: List<Long>,
        primitive: VectorPrimitive,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float
    ): Brush {
        val (c1, c2) = resolveGradientColors(paletteColors, primitive)
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val halfSpan = (max(maxX - minX, maxY - minY) * 0.62f).coerceAtLeast(6f)
        val dx = cos(primitive.gradientAngleRad) * halfSpan
        val dy = sin(primitive.gradientAngleRad) * halfSpan
        return Brush.linearGradient(
            colors = listOf(Color(c1), Color(c2)),
            start = Offset(cx - dx, cy - dy),
            end = Offset(cx + dx, cy + dy)
        )
    }

    const val MAX_WALLPAPER_AXIS_PX = 4096

    fun renderToBitmap(
        scene: VectorScene,
        paletteColors: List<Long>,
        pitchBlackRatio: Float,
        widthPx: Int,
        heightPx: Int,
        colorFillMode: ColorFillMode = ColorFillMode.GRADIENT,
        densityDpi: Int = DisplayMetrics.DENSITY_XXHIGH
    ): Bitmap {
        val rawW = widthPx.coerceAtLeast(240)
        val rawH = heightPx.coerceAtLeast(240)
        val maxAxis = max(rawW, rawH)
        val (safeW, safeH) = if (maxAxis > MAX_WALLPAPER_AXIS_PX) {
            val factor = MAX_WALLPAPER_AXIS_PX.toFloat() / maxAxis
            Pair(
                (rawW * factor).roundToInt().coerceAtLeast(240),
                (rawH * factor).roundToInt().coerceAtLeast(240)
            )
        } else {
            Pair(rawW, rawH)
        }
        val clampedBlack = pitchBlackRatio.coerceIn(0f, 0.95f)
        val isGradient = colorFillMode == ColorFillMode.GRADIENT

        val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        bitmap.density = densityDpi.coerceIn(DisplayMetrics.DENSITY_LOW, DisplayMetrics.DENSITY_XXXHIGH)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(AndroidColor.BLACK)

        val scale = max(safeW.toFloat() / 1f, safeH.toFloat() / scene.aspectRatio)
        val offsetX = (safeW - scale * 1f) * 0.5f
        val offsetY = (safeH - scale * scene.aspectRatio) * 0.5f
        val drawMicroSeams = scale >= 220f

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
            color = AndroidColor.argb(205, 0, 0, 0)
        }

        val path = AndroidPath()
        val outerOval = RectF()
        val innerOval = RectF()

        for (primitive in scene.primitives) {
            if (primitive.threshold < clampedBlack) continue

            val solidArgb = resolveArgbColor(
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
                            seamPaint.strokeWidth = (primitive.strokeSeamNorm * scale).coerceIn(0.6f, 2.4f)
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
        val (c1, c2) = resolveGradientColors(paletteColors, primitive)
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val halfSpan = (max(maxX - minX, maxY - minY) * 0.62f).coerceAtLeast(6f)
        val dx = cos(primitive.gradientAngleRad) * halfSpan
        val dy = sin(primitive.gradientAngleRad) * halfSpan
        paint.color = AndroidColor.WHITE
        paint.shader = AndroidLinearGradient(
            cx - dx,
            cy - dy,
            cx + dx,
            cy + dy,
            c1.toInt(),
            c2.toInt(),
            Shader.TileMode.CLAMP
        )
    }

    fun sampleTrueBlackPercentage(bitmap: Bitmap): Int {
        val stepX = (bitmap.width / 50).coerceAtLeast(1)
        val stepY = (bitmap.height / 50).coerceAtLeast(1)
        var blackPixels = 0
        var totalSamples = 0
        var y = stepY / 2
        while (y < bitmap.height) {
            var x = stepX / 2
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if ((pixel and 0x00FFFFFF) == 0) {
                    blackPixels++
                }
                totalSamples++
                x += stepX
            }
            y += stepY
        }
        if (totalSamples == 0) return 0
        return ((blackPixels * 100f) / totalSamples).roundToInt().coerceIn(0, 100)
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
        val maxAxis = max(rawW, rawH)
        return if (maxAxis > MAX_WALLPAPER_AXIS_PX) {
            val factor = MAX_WALLPAPER_AXIS_PX.toFloat() / maxAxis
            WallpaperDisplaySpec(
                widthPx = (rawW * factor).roundToInt(),
                heightPx = (rawH * factor).roundToInt(),
                densityDpi = densityDpi
            )
        } else {
            WallpaperDisplaySpec(
                widthPx = rawW,
                heightPx = rawH,
                densityDpi = densityDpi
            )
        }
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
}

package com.cptodix.rimalux.oled.wallpaper.platform

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
import com.cptodix.rimalux.oled.wallpaper.engine.ArcSectorPrimitive
import com.cptodix.rimalux.oled.wallpaper.engine.ColorFillMode
import com.cptodix.rimalux.oled.wallpaper.engine.ColorHarmonyEngine
import com.cptodix.rimalux.oled.wallpaper.engine.DeepBlackRatioEngine
import com.cptodix.rimalux.oled.wallpaper.engine.PolygonPrimitive
import com.cptodix.rimalux.oled.wallpaper.engine.PolylineRibbonPrimitive
import com.cptodix.rimalux.oled.wallpaper.engine.VectorPrimitive
import com.cptodix.rimalux.oled.wallpaper.engine.VectorScene
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Jetpack Compose `DrawScope` adapter for rendering pure [VectorScene] instances directly onto a
 * Compose Canvas when needed, delegating all viewport scaling, color shading, and gradient vectors
 * to [DeepBlackRatioEngine] and [ColorHarmonyEngine].
 */
object ComposeSceneRenderer {

    fun DrawScope.drawVectorScene(
        scene: VectorScene,
        paletteColors: List<Long>,
        pitchBlackRatio: Float,
        colorFillMode: ColorFillMode = ColorFillMode.GRADIENT
    ) {
        val clampedBlack = DeepBlackRatioEngine.clampPitchBlackRatio(pitchBlackRatio)
        clipRect {
            drawRect(color = Color(ColorHarmonyEngine.PITCH_BLACK_ARGB))

            if (size.width <= 1f || size.height <= 1f) return@clipRect

            val transform = DeepBlackRatioEngine.computeViewportTransform(
                canvasWidth = size.width,
                canvasHeight = size.height,
                sceneAspectRatio = scene.aspectRatio
            )
            val scale = transform.scale
            val offsetX = transform.offsetX
            val offsetY = transform.offsetY
            val drawMicroSeams = transform.drawMicroSeams

            val reusablePath = Path()
            val isGradient = colorFillMode == ColorFillMode.GRADIENT

            for (primitive in scene.primitives) {
                if (!DeepBlackRatioEngine.isPrimitiveVisible(primitive, clampedBlack)) continue

                val solidColor = Color(
                    ColorHarmonyEngine.resolveArgbColor(
                        paletteColors = paletteColors,
                        colorIndex = primitive.colorIndex,
                        shadeFactor = primitive.shadeFactor
                    )
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
                                val seamPx = (primitive.strokeSeamNorm * scale).coerceIn(0.5f, 1.6f)
                                drawPath(
                                    path = reusablePath,
                                    color = Color.Black,
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
        val segment = ColorHarmonyEngine.computeLinearGradient(
            paletteColors = paletteColors,
            primitive = primitive,
            minX = minX,
            minY = minY,
            maxX = maxX,
            maxY = maxY
        )
        return Brush.linearGradient(
            colors = listOf(Color(segment.startColorArgb), Color(segment.endColorArgb)),
            start = Offset(segment.startX, segment.startY),
            end = Offset(segment.endX, segment.endY)
        )
    }
}

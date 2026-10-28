package com.example.engine

import com.example.model.ArtStyle
import kotlin.math.roundToInt

/**
 * Euclidean 2D coordinate in normalized vector space where X spans [0f, 1f]
 * and Y spans [0f, aspectRatio] so circles, spirals, and symmetry angles remain distortion-free.
 */
data class Vec2(
    val x: Float,
    val y: Float
)

sealed interface VectorPrimitive {
    val threshold: Float
    val areaWeight: Float
    val colorIndex: Int
    val secondaryColorIndex: Int
    val shadeFactor: Float
    val gradientAngleRad: Float
}

data class PolygonPrimitive(
    val vertices: List<Vec2>,
    override val threshold: Float,
    override val areaWeight: Float,
    override val colorIndex: Int,
    override val secondaryColorIndex: Int = colorIndex + 1,
    override val shadeFactor: Float = 1.0f,
    override val gradientAngleRad: Float = 0.785f,
    val strokeSeamNorm: Float = 0.0018f
) : VectorPrimitive

data class ArcSectorPrimitive(
    val center: Vec2,
    val innerRadius: Float,
    val outerRadius: Float,
    val startAngleDeg: Float,
    val sweepAngleDeg: Float,
    override val threshold: Float,
    override val areaWeight: Float,
    override val colorIndex: Int,
    override val secondaryColorIndex: Int = colorIndex + 1,
    override val shadeFactor: Float = 1.0f,
    override val gradientAngleRad: Float = 0.52f
) : VectorPrimitive

data class PolylineRibbonPrimitive(
    val points: List<Vec2>,
    val strokeWidthNorm: Float,
    override val threshold: Float,
    override val areaWeight: Float,
    override val colorIndex: Int,
    override val secondaryColorIndex: Int = colorIndex + 1,
    override val shadeFactor: Float = 1.05f,
    override val gradientAngleRad: Float = 0.35f,
    val closed: Boolean = false
) : VectorPrimitive

internal class DraftCell(
    val score: Float,
    val area: Float,
    val build: (assignedThreshold: Float) -> List<VectorPrimitive>
)

data class SceneRatioStats(
    val pitchBlackPercent: Int,
    val coloredPercent: Int
)

data class VectorScene(
    val seed: Long,
    val style: ArtStyle,
    val aspectRatio: Float,
    val primitives: List<VectorPrimitive>
) {
    fun computeStats(pitchBlackRatio: Float): SceneRatioStats {
        val clamped = pitchBlackRatio.coerceIn(0f, 0.95f)
        if (primitives.isEmpty()) {
            val pct = (clamped * 100f).roundToInt()
            return SceneRatioStats(pct, 100 - pct)
        }
        var blackWeight = 0f
        var coloredWeight = 0f
        for (p in primitives) {
            if (p.threshold < clamped) {
                blackWeight += p.areaWeight
            } else {
                coloredWeight += p.areaWeight
            }
        }
        val total = (blackWeight + coloredWeight).coerceAtLeast(0.0001f)
        val measuredBlackPct = ((blackWeight / total) * 100f).roundToInt().coerceIn(0, 100)
        return SceneRatioStats(
            pitchBlackPercent = measuredBlackPct,
            coloredPercent = 100 - measuredBlackPct
        )
    }
}

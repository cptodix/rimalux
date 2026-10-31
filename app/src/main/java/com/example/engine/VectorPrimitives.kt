package com.example.engine

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

    fun withCalibration(newThreshold: Float, newAreaWeight: Float): VectorPrimitive
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
) : VectorPrimitive {
    override fun withCalibration(newThreshold: Float, newAreaWeight: Float): VectorPrimitive =
        copy(threshold = newThreshold, areaWeight = newAreaWeight)
}

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
) : VectorPrimitive {
    override fun withCalibration(newThreshold: Float, newAreaWeight: Float): VectorPrimitive =
        copy(threshold = newThreshold, areaWeight = newAreaWeight)
}

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
) : VectorPrimitive {
    override fun withCalibration(newThreshold: Float, newAreaWeight: Float): VectorPrimitive =
        copy(threshold = newThreshold, areaWeight = newAreaWeight)
}

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
    val pattern: ArtPattern,
    val aspectRatio: Float,
    val primitives: List<VectorPrimitive>
) {
    fun computeStats(pitchBlackRatio: Float): SceneRatioStats =
        DeepBlackRatioEngine.computeAnalyticalSceneStats(primitives, pitchBlackRatio)
}

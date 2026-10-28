package com.example.engine

import com.example.model.ArtStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Smooth multi-octave sculptural field used to carve cohesive, artistic #000000 negative space
 * rather than noisy random speckles.
 */
internal class SculptField(rng: Random, private val aspect: Float) {
    private val mode = rng.nextInt(4)
    private val fx1 = 2.1f + rng.nextFloat() * 2.2f
    private val fy1 = 1.8f + rng.nextFloat() * 2.2f
    private val fx2 = 4.2f + rng.nextFloat() * 3.0f
    private val fy2 = 3.8f + rng.nextFloat() * 3.0f
    private val p1 = rng.nextFloat() * (2f * PI.toFloat())
    private val p2 = rng.nextFloat() * (2f * PI.toFloat())
    private val p3 = rng.nextFloat() * (2f * PI.toFloat())
    private val focusX = 0.28f + rng.nextFloat() * 0.44f
    private val focusY = aspect * (0.30f + rng.nextFloat() * 0.40f)
    private val diagAngle = rng.nextFloat() * PI.toFloat()

    fun evaluate(x: Float, y: Float, rng: Random): Float {
        val wave1 = 0.5f + 0.5f * sin(x * fx1 + p1 + 0.6f * cos(y * fy1 + p2))
        val wave2 = 0.5f + 0.5f * cos(y * fy2 / aspect + p3 + 0.5f * sin(x * fx2))
        val dx = x - focusX
        val dy = (y - focusY) / aspect.coerceAtLeast(0.6f)
        val radialDist = hypot(dx, dy)

        val macro = when (mode) {
            0 -> {
                val proj = (x - 0.5f) * cos(diagAngle) + ((y / aspect) - 0.5f) * sin(diagAngle)
                val band = 1f - (abs(proj + 0.18f * sin(y * 2.6f + p1)) * 2.1f).coerceIn(0f, 1f)
                0.65f * band + 0.35f * wave1
            }
            1 -> {
                val aura = (1f - radialDist * 1.35f).coerceIn(0f, 1f)
                0.62f * aura + 0.38f * wave1
            }
            2 -> 0.62f * wave1 + 0.38f * wave2
            else -> {
                val ring = 1f - (abs(radialDist - 0.34f) * 2.8f).coerceIn(0f, 1f)
                0.58f * ring + 0.42f * wave1
            }
        }
        return 0.94f * macro + 0.06f * rng.nextFloat()
    }
}

object MathPatternGenerator {

    fun resolveStyleForSeed(
        seed: Long,
        stylesRandomized: Boolean,
        selectedStyles: Set<ArtStyle>
    ): ArtStyle {
        val pool = if (stylesRandomized || selectedStyles.isEmpty()) {
            ArtStyle.entries
        } else {
            selectedStyles.toList()
        }
        val rng = Random(seed xor 0x6A09E667F3BCC908L)
        return pool[rng.nextInt(pool.size)]
    }

    fun generate(
        seed: Long,
        style: ArtStyle,
        intricacy: Float = 0.60f,
        aspectRatio: Float = 2.15f
    ): VectorScene {
        val safeAspect = aspectRatio.coerceIn(0.45f, 3.2f)
        val safeIntricacy = intricacy.coerceIn(0.15f, 1.0f)
        val rng = Random(seed)
        val field = SculptField(rng, safeAspect)

        val draftCells: List<DraftCell> = when (style) {
            ArtStyle.SILK_RIBBONS -> OrganicFlowStyles.buildSilkRibbons(rng, field, safeIntricacy, safeAspect)
            ArtStyle.TOPO_CONTOURS -> OrganicFlowStyles.buildTopoContours(rng, field, safeIntricacy, safeAspect)
            ArtStyle.GUILLOCHE_ROSETTE -> OrganicFlowStyles.buildGuillocheRosette(rng, field, safeIntricacy, safeAspect)
            ArtStyle.FIBONACCI_LOTUS -> OrganicFlowStyles.buildFibonacciLotus(rng, field, safeIntricacy, safeAspect)
            ArtStyle.HORIZON_WAVES -> OrganicFlowStyles.buildHorizonWaves(rng, field, safeIntricacy, safeAspect)

            ArtStyle.SACRED_MANDALA -> GeometricSymmetryStyles.buildSacredMandala(rng, field, safeIntricacy, safeAspect)
            ArtStyle.PENROSE_QUASICRYSTAL -> GeometricSymmetryStyles.buildPenroseQuasicrystal(rng, field, safeIntricacy, safeAspect)
            ArtStyle.GIRIH_STARWORK -> GeometricSymmetryStyles.buildGirihStarwork(rng, field, safeIntricacy, safeAspect)
            ArtStyle.HARMONIC_CARDIOID -> GeometricSymmetryStyles.buildHarmonicCardioid(rng, field, safeIntricacy, safeAspect)
            ArtStyle.ART_DECO_SUNBURST -> GeometricSymmetryStyles.buildArtDecoSunburst(rng, field, safeIntricacy, safeAspect)
            ArtStyle.FRACTAL_LATTICE -> GeometricSymmetryStyles.buildFractalLattice(rng, field, safeIntricacy, safeAspect)

            ArtStyle.CRYSTAL_VORONOI -> ArchitecturalStyles.buildCrystalVoronoi(rng, field, safeIntricacy, safeAspect)
            ArtStyle.ISOMETRIC_MONUMENT -> ArchitecturalStyles.buildIsometricMonument(rng, field, safeIntricacy, safeAspect)
            ArtStyle.BAUHAUS_CONSTRUCT -> ArchitecturalStyles.buildBauhausConstruct(rng, field, safeIntricacy, safeAspect)
            ArtStyle.ORIGAMI_MIURA -> ArchitecturalStyles.buildOrigamiMiura(rng, field, safeIntricacy, safeAspect)
            ArtStyle.OP_ART_GRAVITY -> ArchitecturalStyles.buildOpArtGravity(rng, field, safeIntricacy, safeAspect)
        }

        val primitives = assignAreaQuantileThresholds(draftCells, safeAspect)
        return VectorScene(
            seed = seed,
            style = style,
            aspectRatio = safeAspect,
            primitives = primitives
        )
    }

    private fun assignAreaQuantileThresholds(
        cells: List<DraftCell>,
        canvasAspect: Float
    ): List<VectorPrimitive> {
        if (cells.isEmpty()) return emptyList()
        val indexed = cells.withIndex().sortedBy { it.value.score }
        val totalArea = indexed.sumOf { it.value.area.toDouble() }.toFloat().coerceAtLeast(0.0001f)
        val canvasArea = canvasAspect.coerceAtLeast(0.45f)
        val effectiveDenom = kotlin.math.min(totalArea, canvasArea * 1.15f).coerceAtLeast(0.0001f)
        val excessArea = (totalArea - effectiveDenom).coerceAtLeast(0f)

        val thresholds = FloatArray(cells.size)
        var cumulativeArea = 0f
        for (entry in indexed) {
            val midArea = cumulativeArea + entry.value.area * 0.5f
            val remainingFromTop = (totalArea - midArea).coerceAtLeast(0f)
            val threshold = if (remainingFromTop <= effectiveDenom) {
                val base = 1f - (remainingFromTop / effectiveDenom)
                if (excessArea > 0f) {
                    (0.02f + base * 0.979f).coerceIn(0.02f, 0.999f)
                } else {
                    base.coerceIn(0.001f, 0.999f)
                }
            } else {
                val excessFrac = ((totalArea - remainingFromTop) / excessArea.coerceAtLeast(0.0001f))
                (0.001f + excessFrac * 0.019f).coerceIn(0.001f, 0.02f)
            }
            thresholds[entry.index] = threshold
            cumulativeArea += entry.value.area
        }

        val out = ArrayList<VectorPrimitive>(cells.size * 2)
        for (i in cells.indices) {
            out.addAll(cells[i].build(thresholds[i]))
        }
        return out
    }
}

package com.example.engine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Multi-octave sculptural field distributed across the full [0, 1] x [0, aspect] canvas
 * to carve cohesive, balanced #000000 negative space without clumping in a single corner.
 */
internal class SculptField(
    rng: Random,
    private val aspect: Float,
    val weightMode: PatternWeightMode = PatternWeightMode.NORMAL
) {
    private val mode = rng.nextInt(4)
    private val fx1 = 2.4f + rng.nextFloat() * 2.4f
    private val fy1 = 2.2f + rng.nextFloat() * 2.4f
    private val fx2 = 4.5f + rng.nextFloat() * 3.0f
    private val fy2 = 4.2f + rng.nextFloat() * 3.0f
    private val p1 = rng.nextFloat() * (2f * PI.toFloat())
    private val p2 = rng.nextFloat() * (2f * PI.toFloat())
    private val p3 = rng.nextFloat() * (2f * PI.toFloat())
    private val focusA = Vec2(0.28f + rng.nextFloat() * 0.44f, aspect * (0.22f + rng.nextFloat() * 0.24f))
    private val focusB = Vec2(1f - focusA.x, aspect * (0.56f + rng.nextFloat() * 0.24f))
    private val diagAngle = rng.nextFloat() * PI.toFloat()

    /**
     * Aspect-aware normalized radial contour distance from viewport center `(0.5, aspect * 0.5)`
     * with subtle multi-lobed harmonic modulation so center-weighted clumps look organic rather
     * than cookie-cutter circular while keeping side margins clean.
     */
    fun evaluateCenterContourDistance(x: Float, y: Float): Float {
        val halfW = 0.46f
        val halfH = kotlin.math.max(0.46f, aspect * 0.42f)
        val dx = (x - 0.5f) / halfW
        val dy = (y - aspect * 0.5f) / halfH
        val r = hypot(dx, dy)
        if (r < 1e-4f) return 0f
        val theta = kotlin.math.atan2(dy, dx)
        val lobeA = 2 + (mode and 3)
        val lobeB = 3 + ((mode + 1) and 3)
        val contour = 1f + 0.09f * sin(lobeA * theta + p1) + 0.05f * cos(lobeB * theta + p2)
        return r * contour
    }

    fun evaluate(x: Float, y: Float, rng: Random): Float {
        val jitter = rng.nextFloat()
        if (weightMode == PatternWeightMode.CENTER_WEIGHTED) {
            val radial = evaluateCenterContourDistance(x, y)
            val centeredMacro = ((2.4f - radial) / 2.4f).coerceIn(0f, 1f)
            return 0.97f * centeredMacro + 0.03f * jitter
        }

        val ny = y / aspect.coerceAtLeast(0.6f)
        val wave1 = 0.5f + 0.5f * sin(x * fx1 + p1 + 0.65f * cos(ny * fy1 * 2f + p2))
        val wave2 = 0.5f + 0.5f * cos(ny * fy2 * 2f + p3 + 0.55f * sin(x * fx2))
        val dA = hypot(x - focusA.x, (y - focusA.y) / aspect.coerceAtLeast(0.6f))
        val dB = hypot(x - focusB.x, (y - focusB.y) / aspect.coerceAtLeast(0.6f))
        val minFocalDist = kotlin.math.min(dA, dB)

        val macro = when (mode) {
            0 -> {
                val proj = (x - 0.5f) * cos(diagAngle) + (ny - 0.5f) * sin(diagAngle)
                val ribbons = 0.5f + 0.5f * cos(proj * 9.5f + 0.9f * sin(ny * 4.5f + p1))
                0.60f * ribbons + 0.40f * wave1
            }
            1 -> {
                val twinAura = (1f - minFocalDist * 1.85f).coerceIn(0f, 1f)
                0.56f * twinAura + 0.44f * wave1
            }
            2 -> 0.58f * wave1 + 0.42f * wave2
            else -> {
                val ring = 1f - (abs(minFocalDist - 0.26f) * 3.2f).coerceIn(0f, 1f)
                0.55f * ring + 0.45f * wave2
            }
        }
        return 0.94f * macro + 0.06f * jitter
    }
}

object MathPatternGenerator {

    fun resolvePatternForSeed(
        seed: Long,
        patternsRandomized: Boolean,
        selectedPatterns: Set<ArtPattern>,
        excludePattern: ArtPattern? = null
    ): ArtPattern {
        val basePool = if (patternsRandomized || selectedPatterns.isEmpty()) {
            ArtPattern.entries
        } else {
            selectedPatterns.toList()
        }
        val pool = if (excludePattern != null && basePool.size > 1) {
            basePool.filterNot { it == excludePattern }
        } else {
            basePool
        }
        val rng = Random(seed xor 0x6A09E667F3BCC908L)
        return pool[rng.nextInt(pool.size)]
    }

    fun generate(
        seed: Long,
        pattern: ArtPattern,
        intricacy: Float = 0.60f,
        aspectRatio: Float = 2.15f,
        patternWeightMode: PatternWeightMode = PatternWeightMode.NORMAL
    ): VectorScene {
        val safeAspect = aspectRatio.coerceIn(0.45f, 3.2f)
        val safeIntricacy = intricacy.coerceIn(0.15f, 1.0f)
        val rng = Random(seed)
        val field = SculptField(rng, safeAspect, patternWeightMode)

        val draftCells: List<DraftCell> = when (pattern) {
            // Original 16 Patterns
            ArtPattern.SILK_RIBBONS -> OrganicFlowPatterns.buildSilkRibbons(rng, field, safeIntricacy, safeAspect)
            ArtPattern.TOPO_CONTOURS -> OrganicFlowPatterns.buildTopoContours(rng, field, safeIntricacy, safeAspect)
            ArtPattern.GUILLOCHE_ROSETTE -> OrganicFlowPatterns.buildGuillocheRosette(rng, field, safeIntricacy, safeAspect)
            ArtPattern.FIBONACCI_LOTUS -> OrganicFlowPatterns.buildFibonacciLotus(rng, field, safeIntricacy, safeAspect)
            ArtPattern.HORIZON_WAVES -> OrganicFlowPatterns.buildHorizonWaves(rng, field, safeIntricacy, safeAspect)

            ArtPattern.SACRED_MANDALA -> GeometricSymmetryPatterns.buildSacredMandala(rng, field, safeIntricacy, safeAspect)
            ArtPattern.PENROSE_QUASICRYSTAL -> GeometricSymmetryPatterns.buildPenroseQuasicrystal(rng, field, safeIntricacy, safeAspect)
            ArtPattern.GIRIH_STARWORK -> GeometricSymmetryPatterns.buildGirihStarwork(rng, field, safeIntricacy, safeAspect)
            ArtPattern.HARMONIC_CARDIOID -> GeometricSymmetryPatterns.buildHarmonicCardioid(rng, field, safeIntricacy, safeAspect)
            ArtPattern.ART_DECO_SUNBURST -> GeometricSymmetryPatterns.buildArtDecoSunburst(rng, field, safeIntricacy, safeAspect)
            ArtPattern.FRACTAL_LATTICE -> GeometricSymmetryPatterns.buildFractalLattice(rng, field, safeIntricacy, safeAspect)

            ArtPattern.CRYSTAL_VORONOI -> ArchitecturalPatterns.buildCrystalVoronoi(rng, field, safeIntricacy, safeAspect)
            ArtPattern.ISOMETRIC_MONUMENT -> ArchitecturalPatterns.buildIsometricMonument(rng, field, safeIntricacy, safeAspect)
            ArtPattern.BAUHAUS_CONSTRUCT -> ArchitecturalPatterns.buildBauhausConstruct(rng, field, safeIntricacy, safeAspect)
            ArtPattern.ORIGAMI_MIURA -> ArchitecturalPatterns.buildOrigamiMiura(rng, field, safeIntricacy, safeAspect)
            ArtPattern.OP_ART_GRAVITY -> ArchitecturalPatterns.buildOpArtGravity(rng, field, safeIntricacy, safeAspect)

            // 16 New Patterns (#17 - #32)
            ArtPattern.LISSAJOUS_HARMONIC -> CelestialWavePatterns.buildLissajousHarmonic(rng, field, safeIntricacy, safeAspect)
            ArtPattern.LOGARITHMIC_NAUTILUS -> CelestialWavePatterns.buildLogarithmicNautilus(rng, field, safeIntricacy, safeAspect)
            ArtPattern.CHLADNI_CYMATICS -> CelestialWavePatterns.buildChladniCymatics(rng, field, safeIntricacy, safeAspect)
            ArtPattern.MOIRE_INTERFERENCE -> CelestialWavePatterns.buildMoireInterference(rng, field, safeIntricacy, safeAspect)
            ArtPattern.VECTOR_MAGNETIC_DIPOLE -> CelestialWavePatterns.buildMagneticDipole(rng, field, safeIntricacy, safeAspect)
            ArtPattern.AURORA_CURTAINS -> CelestialWavePatterns.buildAuroraCurtains(rng, field, safeIntricacy, safeAspect)
            ArtPattern.APOLLONIAN_CIRCLES -> CelestialWavePatterns.buildApollonianCircles(rng, field, safeIntricacy, safeAspect)
            ArtPattern.SHATTERED_GEODE -> CelestialWavePatterns.buildShatteredGeode(rng, field, safeIntricacy, safeAspect)

            ArtPattern.VORONOI_STAINED_GLASS -> TessellationKineticPatterns.buildVoronoiStainedGlass(rng, field, safeIntricacy, safeAspect)
            ArtPattern.HEX_HONEYCOMB_PRISM -> TessellationKineticPatterns.buildHexHoneycombPrism(rng, field, safeIntricacy, safeAspect)
            ArtPattern.TRUCHET_LABYRINTH -> TessellationKineticPatterns.buildTruchetLabyrinth(rng, field, safeIntricacy, safeAspect)
            ArtPattern.SUPERELLIPSE_NEST -> TessellationKineticPatterns.buildSuperellipseNest(rng, field, safeIntricacy, safeAspect)
            ArtPattern.KALEIDOSCOPE_PRISM -> TessellationKineticPatterns.buildKaleidoscopePrism(rng, field, safeIntricacy, safeAspect)
            ArtPattern.HYPERBOLIC_POINCARE -> TessellationKineticPatterns.buildHyperbolicPoincare(rng, field, safeIntricacy, safeAspect)
            ArtPattern.CELTIC_INTERLACE -> TessellationKineticPatterns.buildCelticInterlace(rng, field, safeIntricacy, safeAspect)
            ArtPattern.KUMIKO_WOODWORK -> TessellationKineticPatterns.buildKumikoWoodwork(rng, field, safeIntricacy, safeAspect)

            // Patterns #33 - #48
            ArtPattern.LORENZ_ATTRACTOR -> AttractorFractalPatterns.buildLorenzAttractor(rng, field, safeIntricacy, safeAspect)
            ArtPattern.FOURIER_EPICYCLES -> AttractorFractalPatterns.buildFourierEpicycles(rng, field, safeIntricacy, safeAspect)
            ArtPattern.KELVIN_VORTEX -> AttractorFractalPatterns.buildKelvinVortex(rng, field, safeIntricacy, safeAspect)
            ArtPattern.CAUSTIC_WATER -> AttractorFractalPatterns.buildCausticWater(rng, field, safeIntricacy, safeAspect)
            ArtPattern.MAURER_ROSE -> AttractorFractalPatterns.buildMaurerRose(rng, field, safeIntricacy, safeAspect)
            ArtPattern.SUPERFORMULA_BLOOM -> AttractorFractalPatterns.buildSuperformulaBloom(rng, field, safeIntricacy, safeAspect)
            ArtPattern.FERMAT_PARABOLIC -> AttractorFractalPatterns.buildFermatParabolic(rng, field, safeIntricacy, safeAspect)
            ArtPattern.METATRON_POLYHEDRA -> AttractorFractalPatterns.buildMetatronPolyhedra(rng, field, safeIntricacy, safeAspect)
            ArtPattern.GEODESIC_DOME -> ConstructTessellationPatterns.buildGeodesicDome(rng, field, safeIntricacy, safeAspect)
            ArtPattern.PERSPECTIVE_COLONNADE -> ConstructTessellationPatterns.buildPerspectiveColonnade(rng, field, safeIntricacy, safeAspect)
            ArtPattern.DE_STIJL_MONDRIAN -> ConstructTessellationPatterns.buildDeStijlMondrian(rng, field, safeIntricacy, safeAspect)
            ArtPattern.BRUTALIST_ZIGGURAT -> ConstructTessellationPatterns.buildBrutalistZiggurat(rng, field, safeIntricacy, safeAspect)
            ArtPattern.HILBERT_FRACTAL -> ConstructTessellationPatterns.buildHilbertFractal(rng, field, safeIntricacy, safeAspect)
            ArtPattern.DRAGON_CURVE -> ConstructTessellationPatterns.buildDragonCurve(rng, field, safeIntricacy, safeAspect)
            ArtPattern.CAIRO_PENTAGONAL -> ConstructTessellationPatterns.buildCairoPentagonal(rng, field, safeIntricacy, safeAspect)
            ArtPattern.KAGOME_TRIHEXAGONAL -> ConstructTessellationPatterns.buildKagomeTrihexagonal(rng, field, safeIntricacy, safeAspect)

            // Patterns #49 - #64
            ArtPattern.THOMAS_ATTRACTOR -> ChaoticSacredPatterns.buildThomasAttractor(rng, field, safeIntricacy, safeAspect)
            ArtPattern.AIRY_WAVEFRONT -> ChaoticSacredPatterns.buildAiryWavefront(rng, field, safeIntricacy, safeAspect)
            ArtPattern.ROSSLER_SPIRAL -> ChaoticSacredPatterns.buildRosslerSpiral(rng, field, safeIntricacy, safeAspect)
            ArtPattern.CASSINI_LEMNISCATE -> ChaoticSacredPatterns.buildCassiniLemniscate(rng, field, safeIntricacy, safeAspect)
            ArtPattern.DELTOID_ASTROID -> ChaoticSacredPatterns.buildDeltoidAstroid(rng, field, safeIntricacy, safeAspect)
            ArtPattern.ZELLIJ_KHATAM -> ChaoticSacredPatterns.buildZellijKhatam(rng, field, safeIntricacy, safeAspect)
            ArtPattern.VESICA_LENSES -> ChaoticSacredPatterns.buildVesicaLenses(rng, field, safeIntricacy, safeAspect)
            ArtPattern.TRISKELION_SPIRALS -> ChaoticSacredPatterns.buildTriskelionSpirals(rng, field, safeIntricacy, safeAspect)
            ArtPattern.ESCHER_STAIRCASES -> ParametricQuasicrystalPatterns.buildEscherStaircases(rng, field, safeIntricacy, safeAspect)
            ArtPattern.KINETIC_FACADE -> ParametricQuasicrystalPatterns.buildKineticFacade(rng, field, safeIntricacy, safeAspect)
            ArtPattern.PANTHEON_OCULUS -> ParametricQuasicrystalPatterns.buildPantheonOculus(rng, field, safeIntricacy, safeAspect)
            ArtPattern.SUPREMATIST_PLANES -> ParametricQuasicrystalPatterns.buildSuprematistPlanes(rng, field, safeIntricacy, safeAspect)
            ArtPattern.SIERPINSKI_PRISM -> ParametricQuasicrystalPatterns.buildSierpinskiPrism(rng, field, safeIntricacy, safeAspect)
            ArtPattern.ARCHIMEDEAN_SNUB -> ParametricQuasicrystalPatterns.buildArchimedeanSnub(rng, field, safeIntricacy, safeAspect)
            ArtPattern.AMMANN_QUASICRYSTAL -> ParametricQuasicrystalPatterns.buildAmmannQuasicrystal(rng, field, safeIntricacy, safeAspect)
            ArtPattern.PYTHAGORAS_CANOPY -> ParametricQuasicrystalPatterns.buildPythagorasCanopy(rng, field, safeIntricacy, safeAspect)

            // Patterns #65 - #80
            ArtPattern.CLIFFORD_ATTRACTOR -> HarmonicAttractorPatterns.buildCliffordAttractor(rng, field, safeIntricacy, safeAspect)
            ArtPattern.FARADAY_RIPPLES -> HarmonicAttractorPatterns.buildFaradayRipples(rng, field, safeIntricacy, safeAspect)
            ArtPattern.BERNOULLI_SPIRALS -> HarmonicAttractorPatterns.buildBernoulliSpirals(rng, field, safeIntricacy, safeAspect)
            ArtPattern.SOLITON_TRAINS -> HarmonicAttractorPatterns.buildSolitonTrains(rng, field, safeIntricacy, safeAspect)
            ArtPattern.ENNEAGRAM_STAR -> HarmonicAttractorPatterns.buildEnneagramStar(rng, field, safeIntricacy, safeAspect)
            ArtPattern.RHODONEA_BLOOM -> HarmonicAttractorPatterns.buildRhodoneaBloom(rng, field, safeIntricacy, safeAspect)
            ArtPattern.EPITROCHOID_CROWN -> HarmonicAttractorPatterns.buildEpitrochoidCrown(rng, field, safeIntricacy, safeAspect)
            ArtPattern.SRI_YANTRA_TRIANGLES -> HarmonicAttractorPatterns.buildSriYantraTriangles(rng, field, safeIntricacy, safeAspect)
            ArtPattern.CALATRAVA_RIBS -> VaultedLatticePatterns.buildCalatravaRibs(rng, field, safeIntricacy, safeAspect)
            ArtPattern.HEXAGONAL_BASALT -> VaultedLatticePatterns.buildHexagonalBasalt(rng, field, safeIntricacy, safeAspect)
            ArtPattern.METABOLIST_CAPSULES -> VaultedLatticePatterns.buildMetabolistCapsules(rng, field, safeIntricacy, safeAspect)
            ArtPattern.DIAGRID_SKYSCRAPER -> VaultedLatticePatterns.buildDiagridSkyscraper(rng, field, safeIntricacy, safeAspect)
            ArtPattern.LAVES_FLORET -> VaultedLatticePatterns.buildLavesFloret(rng, field, safeIntricacy, safeAspect)
            ArtPattern.KITE_DART_STAR -> VaultedLatticePatterns.buildKiteDartStar(rng, field, safeIntricacy, safeAspect)
            ArtPattern.TRIANGULAR_GYROID -> VaultedLatticePatterns.buildTriangularGyroid(rng, field, safeIntricacy, safeAspect)
            ArtPattern.GOSPER_FLOWSNAKE -> VaultedLatticePatterns.buildGosperFlowsnake(rng, field, safeIntricacy, safeAspect)
        }

        val calibratedPrimitives = CoverageCalibrator.calibrate(
            primaryCells = draftCells,
            rng = rng,
            field = field,
            aspect = safeAspect,
            patternWeightMode = patternWeightMode
        )

        return VectorScene(
            seed = seed,
            pattern = pattern,
            aspectRatio = safeAspect,
            primitives = calibratedPrimitives
        )
    }
}

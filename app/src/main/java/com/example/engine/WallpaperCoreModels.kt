package com.example.engine

/**
 * Pure, platform-agnostic domain models for the Rimalux procedural mathematical wallpaper engine.
 *
 * PORTABILITY CONTRACT:
 * This file (and the entire `com.example.engine` package) MUST remain 100% pure Kotlin
 * (`kotlin.*` standard library only) with zero Android SDK, Compose, resource ID, or app-state imports.
 */
enum class PatternCategory(
    val id: String,
    val defaultLabel: String
) {
    FLOW_WAVES("flow_waves", "Flow & Waves"),
    SACRED_SYMMETRY("sacred_symmetry", "Symmetry & Sacred"),
    ARCHITECTURAL("architectural", "Architectural"),
    TESSELLATION("tessellation", "Tessellations");

    companion object {
        fun fromId(id: String): PatternCategory? = entries.find { it.id == id }
    }
}

enum class ArtPattern(
    val id: String,
    val category: PatternCategory,
    val defaultTitle: String,
    val defaultSubtitle: String
) {
    // Flow & Waves (23 patterns)
    SILK_RIBBONS(
        id = "silk_ribbons",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Silk Flow Ribbons",
        defaultSubtitle = "Differential curl-field streamlines"
    ),
    TOPO_CONTOURS(
        id = "topo_contours",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Topo Contours",
        defaultSubtitle = "Multi-octave elevation isolines"
    ),
    HORIZON_WAVES(
        id = "horizon_waves",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Horizon Strata",
        defaultSubtitle = "Layered harmonic ridges & eclipse"
    ),
    LISSAJOUS_HARMONIC(
        id = "lissajous_harmonic",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Lissajous Harmonics",
        defaultSubtitle = "Phase-locked orbital wave ribbons"
    ),
    LOGARITHMIC_NAUTILUS(
        id = "logarithmic_nautilus",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Golden Nautilus",
        defaultSubtitle = "Equiangular spiral chamber septa"
    ),
    CHLADNI_CYMATICS(
        id = "chladni_cymatics",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Chladni Cymatics",
        defaultSubtitle = "Acoustic standing-wave nodal cells"
    ),
    MOIRE_INTERFERENCE(
        id = "moire_interference",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Moiré Interference",
        defaultSubtitle = "Dual-focus wave ring resonance"
    ),
    VECTOR_MAGNETIC_DIPOLE(
        id = "magnetic_dipole",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Magnetic Flux",
        defaultSubtitle = "Bipolar vector field streamlines"
    ),
    AURORA_CURTAINS(
        id = "aurora_curtains",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Borealis Drapery",
        defaultSubtitle = "Vertical harmonic sky curtains"
    ),
    SHATTERED_GEODE(
        id = "shattered_geode",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Agate Geode",
        defaultSubtitle = "Crystalline mineral ring strata"
    ),
    OP_ART_GRAVITY(
        id = "op_art_gravity",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Op-Art Gravity",
        defaultSubtitle = "Lens-warped kinetic ribbons"
    ),
    LORENZ_ATTRACTOR(
        id = "lorenz_attractor",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Lorenz Butterfly",
        defaultSubtitle = "Chaotic strange-attractor phase wings"
    ),
    FOURIER_EPICYCLES(
        id = "fourier_epicycles",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Fourier Epicycles",
        defaultSubtitle = "Harmonic phasor orbital ribbons"
    ),
    KELVIN_VORTEX(
        id = "kelvin_vortex",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Vortex Street",
        defaultSubtitle = "Kármán counter-rotating fluid eddies"
    ),
    CAUSTIC_WATER(
        id = "caustic_water",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Lagoon Caustics",
        defaultSubtitle = "Refracted wave-envelope light cells"
    ),
    THOMAS_ATTRACTOR(
        id = "thomas_attractor",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Thomas Labyrinth",
        defaultSubtitle = "Cyclically symmetric 3D phase braids"
    ),
    AIRY_WAVEFRONT(
        id = "airy_wavefront",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Airy Diffraction",
        defaultSubtitle = "Parabolic catastrophe wavefront fringes"
    ),
    ROSSLER_SPIRAL(
        id = "rossler_spiral",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Rössler Vortex",
        defaultSubtitle = "Folded chaotic band & saddle loops"
    ),
    CASSINI_LEMNISCATE(
        id = "cassini_lemniscate",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Cassini Ovals",
        defaultSubtitle = "Bipolar constant-product lemniscates"
    ),
    CLIFFORD_ATTRACTOR(
        id = "clifford_attractor",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Clifford Strange",
        defaultSubtitle = "Iterated trigonometric phase veil"
    ),
    FARADAY_RIPPLES(
        id = "faraday_ripples",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Faraday Standing",
        defaultSubtitle = "Parametric capillary wave lattice"
    ),
    BERNOULLI_SPIRALS(
        id = "bernoulli_spirals",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "Spira Mirabilis",
        defaultSubtitle = "Dual counter-rotating equiangular arms"
    ),
    SOLITON_TRAINS(
        id = "soliton_trains",
        category = PatternCategory.FLOW_WAVES,
        defaultTitle = "KdV Solitons",
        defaultSubtitle = "Interacting nonlinear sech² wave pulses"
    ),

    // Symmetry & Sacred Geometry (21 patterns)
    SACRED_MANDALA(
        id = "sacred_mandala",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Sacred Mandala",
        defaultSubtitle = "Polar rose harmonics & lotus arches"
    ),
    GUILLOCHE_ROSETTE(
        id = "guilloche_rosette",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Guilloché Spiro",
        defaultSubtitle = "Hypotrochoid moiré filigree"
    ),
    FIBONACCI_LOTUS(
        id = "fibonacci_lotus",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Fibonacci Spiral",
        defaultSubtitle = "137.5° golden-angle petal vortex"
    ),
    GIRIH_STARWORK(
        id = "girih_starwork",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Girih Starwork",
        defaultSubtitle = "12-fold interlaced star rosettes"
    ),
    HARMONIC_CARDIOID(
        id = "harmonic_cardioid",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Harmonic Cardioid",
        defaultSubtitle = "Modular chord & nephroid webs"
    ),
    APOLLONIAN_CIRCLES(
        id = "apollonian_circles",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Apollonian Rings",
        defaultSubtitle = "Tangent circle & crescent packing"
    ),
    KALEIDOSCOPE_PRISM(
        id = "kaleidoscope_prism",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Mirror Kaleidoscope",
        defaultSubtitle = "Dihedral symmetrical shard facets"
    ),
    HYPERBOLIC_POINCARE(
        id = "hyperbolic_poincare",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Poincaré Geodesics",
        defaultSubtitle = "Hyperbolic disk orthogonal arcs"
    ),
    SUPERELLIPSE_NEST(
        id = "superellipse_nest",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Lamé Superellipses",
        defaultSubtitle = "Twisted Piet Hein squircle rings"
    ),
    MAURER_ROSE(
        id = "maurer_rose",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Maurer Rose",
        defaultSubtitle = "Modular polar chord rose petals"
    ),
    SUPERFORMULA_BLOOM(
        id = "superformula_bloom",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Gielis Morphogen",
        defaultSubtitle = "Generalized superformula star shells"
    ),
    FERMAT_PARABOLIC(
        id = "fermat_parabolic",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Fermat Parastichy",
        defaultSubtitle = "Counter-winding sunflower spirals"
    ),
    METATRON_POLYHEDRA(
        id = "metatron_polyhedra",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Metatron Star",
        defaultSubtitle = "13-node sacred polyhedral lattice"
    ),
    DELTOID_ASTROID(
        id = "deltoid_astroid",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Astroid Cusps",
        defaultSubtitle = "Hypocycloid star envelopes & cusps"
    ),
    ZELLIJ_KHATAM(
        id = "zellij_khatam",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Zellij Khatam",
        defaultSubtitle = "16-fold Moroccan star & saft rosette"
    ),
    VESICA_LENSES(
        id = "vesica_lenses",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Vesica Piscis",
        defaultSubtitle = "Overlapping sacred lens petal matrix"
    ),
    TRISKELION_SPIRALS(
        id = "triskelion_spirals",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Triskelion Spirals",
        defaultSubtitle = "Multi-arm counter-spun spiral blades"
    ),
    ENNEAGRAM_STAR(
        id = "enneagram_star",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Enneagram Rosette",
        defaultSubtitle = "9-fold nonagonal chord & petal mandala"
    ),
    RHODONEA_BLOOM(
        id = "rhodonea_bloom",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Rhodonea Curves",
        defaultSubtitle = "Harmonic Grandi polar petal shells"
    ),
    EPITROCHOID_CROWN(
        id = "epitrochoid_crown",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Epitrochoid Crown",
        defaultSubtitle = "Rolling planetary orbital cusps & loops"
    ),
    SRI_YANTRA_TRIANGLES(
        id = "sri_yantra_triangles",
        category = PatternCategory.SACRED_SYMMETRY,
        defaultTitle = "Yantra Interlace",
        defaultSubtitle = "Interlocking sacred triangles & lotus"
    ),

    // Architectural & Constructivist (17 patterns)
    ISOMETRIC_MONUMENT(
        id = "isometric_monument",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Isometric Prisms",
        defaultSubtitle = "30° architectural stepped blocks"
    ),
    BAUHAUS_CONSTRUCT(
        id = "bauhaus_construct",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Bauhaus Construct",
        defaultSubtitle = "Golden-ratio arcs & fine louvres"
    ),
    ART_DECO_SUNBURST(
        id = "art_deco_sunburst",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Art Deco Sunburst",
        defaultSubtitle = "Radiating fan arches & spires"
    ),
    ORIGAMI_MIURA(
        id = "origami_miura",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Origami Miura",
        defaultSubtitle = "Pleated herringbone crease folds"
    ),
    KUMIKO_WOODWORK(
        id = "kumiko_woodwork",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Asanoha Kumiko",
        defaultSubtitle = "Japanese 6-fold hemp-leaf joinery"
    ),
    GEODESIC_DOME(
        id = "geodesic_dome",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Geodesic Dome",
        defaultSubtitle = "Triangulated spherical truss facets"
    ),
    PERSPECTIVE_COLONNADE(
        id = "perspective_colonnade",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Infinite Atrium",
        defaultSubtitle = "Vanishing-point coffered portals"
    ),
    DE_STIJL_MONDRIAN(
        id = "de_stijl_mondrian",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Neoplastic Grid",
        defaultSubtitle = "Golden-ratio orthogonal color slabs"
    ),
    BRUTALIST_ZIGGURAT(
        id = "brutalist_ziggurat",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Stepped Ziggurat",
        defaultSubtitle = "Cantilevered shadow-bevel terraces"
    ),
    ESCHER_STAIRCASES(
        id = "escher_staircases",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Escher Staircases",
        defaultSubtitle = "Axonometric terraced flights & risers"
    ),
    KINETIC_FACADE(
        id = "kinetic_facade",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Kinetic Facade",
        defaultSubtitle = "Twisted parametric brise-soleil fins"
    ),
    PANTHEON_OCULUS(
        id = "pantheon_oculus",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Pantheon Coffers",
        defaultSubtitle = "Recessed radial dome caissons & oculus"
    ),
    SUPREMATIST_PLANES(
        id = "suprematist_planes",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Suprematist Planes",
        defaultSubtitle = "Cantilevered constructivist vectors"
    ),
    CALATRAVA_RIBS(
        id = "calatrava_ribs",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Vaulted Parabolas",
        defaultSubtitle = "Sweeping parabolic rib-arch colonnade"
    ),
    HEXAGONAL_BASALT(
        id = "hexagonal_basalt",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Basalt Columns",
        defaultSubtitle = "Axonometric stepped hexagonal pillars"
    ),
    METABOLIST_CAPSULES(
        id = "metabolist_capsules",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Capsule Towers",
        defaultSubtitle = "Modular cantilevered oculus pods"
    ),
    DIAGRID_SKYSCRAPER(
        id = "diagrid_skyscraper",
        category = PatternCategory.ARCHITECTURAL,
        defaultTitle = "Diagrid Atrium",
        defaultSubtitle = "Tapered structural diamond exoskeleton"
    ),

    // Tessellations & Lattices (19 patterns)
    CRYSTAL_VORONOI(
        id = "crystal_voronoi",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Crystal Facets",
        defaultSubtitle = "Beveled Delaunay gemstone shards"
    ),
    PENROSE_QUASICRYSTAL(
        id = "penrose_quasicrystal",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Penrose Tiling",
        defaultSubtitle = "Aperiodic φ-triangle quasicrystal"
    ),
    FRACTAL_LATTICE(
        id = "fractal_lattice",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Fractal Weave",
        defaultSubtitle = "Recursive barycentric subdivision"
    ),
    VORONOI_STAINED_GLASS(
        id = "voronoi_stained_glass",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Cathedral Glass",
        defaultSubtitle = "Polygonal Voronoi leaded jewels"
    ),
    HEX_HONEYCOMB_PRISM(
        id = "hex_honeycomb_prism",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Hexagonal Matrix",
        defaultSubtitle = "Nested 6-fold rhombic prism cells"
    ),
    TRUCHET_LABYRINTH(
        id = "truchet_labyrinth",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Truchet Meander",
        defaultSubtitle = "Seamless quarter-arc ribbon paths"
    ),
    CELTIC_INTERLACE(
        id = "celtic_interlace",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Braided Lattice",
        defaultSubtitle = "Over-under woven diagonal straps"
    ),
    HILBERT_FRACTAL(
        id = "hilbert_fractal",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Hilbert Labyrinth",
        defaultSubtitle = "Space-filling continuous fractal path"
    ),
    DRAGON_CURVE(
        id = "dragon_curve",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Heighway Dragon",
        defaultSubtitle = "Self-similar 90° fold mosaic"
    ),
    CAIRO_PENTAGONAL(
        id = "cairo_pentagonal",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Cairo Pentagons",
        defaultSubtitle = "4-fold alternating street-tile mesh"
    ),
    KAGOME_TRIHEXAGONAL(
        id = "kagome_trihexagonal",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Kagome Lattice",
        defaultSubtitle = "Trihexagonal woven star basketry"
    ),
    SIERPINSKI_PRISM(
        id = "sierpinski_prism",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Sierpiński Prism",
        defaultSubtitle = "Recursive triangular gasket facets"
    ),
    ARCHIMEDEAN_SNUB(
        id = "archimedean_snub",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Archimedean Snub",
        defaultSubtitle = "Chiral square-triangle semi-regular mesh"
    ),
    AMMANN_QUASICRYSTAL(
        id = "ammann_quasicrystal",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Ammann Quasicrystal",
        defaultSubtitle = "8-fold silver-ratio rhombus & square"
    ),
    PYTHAGORAS_CANOPY(
        id = "pythagoras_canopy",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Pythagoras Fractal",
        defaultSubtitle = "Recursive right-triangle square canopy"
    ),
    LAVES_FLORET(
        id = "laves_floret",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Floret Pentagons",
        defaultSubtitle = "6-fold chiral Laves pentagonal rosettes"
    ),
    KITE_DART_STAR(
        id = "kite_dart_star",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Decagonal Stars",
        defaultSubtitle = "5-fold golden kite & dart sunburst tiling"
    ),
    TRIANGULAR_GYROID(
        id = "triangular_gyroid",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Triangular Bevels",
        defaultSubtitle = "3-way isometric cube-corner lattice"
    ),
    GOSPER_FLOWSNAKE(
        id = "gosper_flowsnake",
        category = PatternCategory.TESSELLATION,
        defaultTitle = "Gosper Island",
        defaultSubtitle = "Hexagonal recursive Peano-Gosper tiles"
    );

    companion object {
        fun fromId(id: String): ArtPattern? = entries.find { it.id == id }
    }
}

enum class PatternWeightMode(val id: String) {
    NORMAL("normal"),
    CENTER_WEIGHTED("center_weighted");

    companion object {
        fun fromId(id: String): PatternWeightMode =
            entries.find { it.id == id } ?: NORMAL
    }
}

enum class ColorFillMode(val id: String) {
    SOLID("solid"),
    GRADIENT("gradient");

    companion object {
        fun fromId(id: String): ColorFillMode =
            entries.find { it.id == id } ?: GRADIENT
    }
}

enum class PaletteMode(val id: String) {
    RANDOMIZED("randomized"),
    PALETTE_LIST("palette_list");

    companion object {
        fun fromId(id: String): PaletteMode =
            entries.find { it.id == id } ?: RANDOMIZED
    }
}

enum class PitchBlackMode(val id: String) {
    SINGLE("single"),
    RANGE("range");

    companion object {
        fun fromId(id: String): PitchBlackMode =
            entries.find { it.id == id } ?: SINGLE
    }
}

data class ColorPalette(
    val id: String,
    val name: String,
    val colors: List<Long>
)

/**
 * Platform-independent generation specification containing strictly mathematical, pattern,
 * color, and #000000 True Black Ratio parameters (free of OS targets or scheduler state).
 */
data class CoreGenerationSpec(
    val patternsRandomized: Boolean = true,
    val selectedPatterns: Set<ArtPattern> = DEFAULT_SELECTED_PATTERNS,
    val patternWeightMode: PatternWeightMode = PatternWeightMode.NORMAL,
    val colorFillMode: ColorFillMode = ColorFillMode.GRADIENT,
    val paletteMode: PaletteMode = PaletteMode.RANDOMIZED,
    val selectedPaletteIds: Set<String> = setOf("obsidian_aurora"),
    val pitchBlackMode: PitchBlackMode = PitchBlackMode.SINGLE,
    val pitchBlackRatio: Float = DeepBlackRatioEngine.DEFAULT_PITCH_BLACK_RATIO,
    val pitchBlackRangeMin: Float = 0.75f,
    val pitchBlackRangeMax: Float = 0.90f
) {
    companion object {
        val DEFAULT_SELECTED_PATTERNS: Set<ArtPattern> = setOf(
            ArtPattern.SILK_RIBBONS,
            ArtPattern.CRYSTAL_VORONOI,
            ArtPattern.SACRED_MANDALA,
            ArtPattern.PENROSE_QUASICRYSTAL
        )
    }
}

/**
 * Pure, self-contained mathematical wallpaper descriptor produced by [WallpaperGeneratorEngine].
 * Can be rendered onto any platform surface (Android Bitmap/Canvas, Jetpack Compose, iOS CoreGraphics,
 * Web Canvas2D/SVG/WebGL, Desktop Skia) without touching app logic.
 */
data class SynthesizedWallpaper(
    val seed: Long,
    val pattern: ArtPattern,
    val paletteColors: List<Long>,
    val colorFillMode: ColorFillMode,
    val pitchBlackRatio: Float,
    val intricacy: Float,
    val aspectRatio: Float,
    val scene: VectorScene,
    val ratioStats: SceneRatioStats,
    val patternWeightMode: PatternWeightMode = PatternWeightMode.NORMAL
)

object StarterPalettes {
    val defaults: List<ColorPalette> = listOf(
        ColorPalette("obsidian_aurora", "Aurora Silk", listOf(0xFF00F5D4, 0xFF00BBF9, 0xFF7B2CBF, 0xFFF15BB5, 0xFFFFD166)),
        ColorPalette("monochromatic_oled", "Monochromatic", listOf(0xFFFFFFFF, 0xFFE4E4E7, 0xFFA1A1AA, 0xFF71717A, 0xFF52525B)),
        ColorPalette("imperial_gold", "Imperial Gold", listOf(0xFFFFD166, 0xFFF4A261, 0xFFE76F51, 0xFF2A9D8F, 0xFFE9C46A)),
        ColorPalette("bauhaus_studio", "Bauhaus Modern", listOf(0xFFE63946, 0xFFFFB703, 0xFF219EBC, 0xFFF1FAEE)),
        ColorPalette("nordic_glacier", "Nordic Glacier", listOf(0xFFE0FBFC, 0xFF98C1D9, 0xFF3D5A80, 0xFF48CAE4)),
        ColorPalette("titanium_mono", "Silver Silk", listOf(0xFFFFFFFF, 0xFFD4D4D8, 0xFF9CA3AF, 0xFF64748B)),
        ColorPalette("cyberpunk_shibuya", "Cyberpunk Shibuya", listOf(0xFFFF007F, 0xFF00F0FF, 0xFFC8FF00, 0xFF8A2BE2, 0xFFFFBE0B)),
        ColorPalette("kyoto_autumn", "Kyoto Maple", listOf(0xFFD62828, 0xFFF77F00, 0xFFFCBF49, 0xFFEAE2B7, 0xFF4F772D)),
        ColorPalette("synthwave_sunset", "Synthwave Dusk", listOf(0xFFFF2A85, 0xFFFF6B35, 0xFFFFC145, 0xFF9B5DE5, 0xFF4EA8DE)),
        ColorPalette("emerald_peacock", "Emerald Peacock", listOf(0xFF10B981, 0xFF06B6D4, 0xFF2563EB, 0xFFFBBF24, 0xFFA7F3D0)),
        ColorPalette("solar_flare", "Solar Flare", listOf(0xFFFFF3B0, 0xFFFFB703, 0xFFFB8500, 0xFFE63946, 0xFF9E2A2B)),
        ColorPalette("bioluminescent_reef", "Coral Biolume", listOf(0xFF06D6A0, 0xFF118AB2, 0xFFEF476F, 0xFFFFD166, 0xFF72EFDD)),
        ColorPalette("royal_amethyst", "Royal Amethyst", listOf(0xFFC77DFF, 0xFF9D4EDD, 0xFFE0AAFF, 0xFFF4A261, 0xFFFF758F)),
        ColorPalette("matcha_botanical", "Matcha Studio", listOf(0xFFA3B18A, 0xFF588157, 0xFFDAD7CD, 0xFFE07A5F, 0xFFF2CC8F)),
        ColorPalette("sahara_terracotta", "Sahara Adobe", listOf(0xFFE07A5F, 0xFFF4A261, 0xFFE9C46A, 0xFF81B29A, 0xFFF2E8CF)),
        ColorPalette("tokyo_neon_mint", "Hyper Mint", listOf(0xFF00FFB3, 0xFF3A86FF, 0xFFFF595E, 0xFFA9DEF9, 0xFF8338EC)),
        ColorPalette("copper_patina", "Copper Verdigris", listOf(0xFF4ECDC4, 0xFFD97742, 0xFFB5651D, 0xFFFFE66D, 0xFFA8DADC)),
        ColorPalette("cosmic_nebula", "Cosmic Nebula", listOf(0xFFF72585, 0xFF7209B7, 0xFF4361EE, 0xFF4CC9F0, 0xFFFF9E7D)),
        ColorPalette("pastel_macaron", "Pastel Silk", listOf(0xFFFFADAD, 0xFFFFD6A5, 0xFFFDFFB6, 0xFFCAFFBF, 0xFF9BF6FF, 0xFFBDB2FF)),
        ColorPalette("crimson_samurai", "Crimson Lacquer", listOf(0xFFE63946, 0xFFFFD166, 0xFFF8F9FA, 0xFF8D99AE, 0xFFA4161A)),
        ColorPalette("oceanic_abyss", "Pacific Crest", listOf(0xFF90E0EF, 0xFF00B4D8, 0xFF0077B6, 0xFF48CAE4, 0xFFCAF0F8)),
        ColorPalette("electric_citrus", "Electric Citrus", listOf(0xFFAACC00, 0xFFFFDD00, 0xFFFF7B00, 0xFFFF3659, 0xFF55A630)),
        ColorPalette("velvet_rose", "Velvet Rose", listOf(0xFFFF4D6D, 0xFFFF8FA3, 0xFFFFB3C1, 0xFFE5989B, 0xFFFFD6BA)),
        ColorPalette("arctic_prism", "Arctic Prism", listOf(0xFF64DFDF, 0xFF80FFDB, 0xFF7400B8, 0xFF5390D9, 0xFFE2EAFC)),
        ColorPalette("monochrome_champagne", "Champagne Brass", listOf(0xFFFDF0D5, 0xFFE6CCB2, 0xFFDDB892, 0xFFB08968, 0xFF9C89B8)),
        ColorPalette("sakura_twilight", "Sakura Twilight", listOf(0xFFFF85A1, 0xFFFBB1BD, 0xFFC9184A, 0xFF9D4EDD, 0xFFFFE5EC)),
        ColorPalette("amber_phosphor", "Amber Phosphor", listOf(0xFFFFB000, 0xFFFFD166, 0xFFFF7B00, 0xFFEA580C, 0xFFFEF08A)),
        ColorPalette("aegean_caldera", "Aegean Caldera", listOf(0xFF0096C7, 0xFF48CAE4, 0xFFF8F9FA, 0xFFFF9F1C, 0xFF023E8A)),
        ColorPalette("ultraviolet_pulse", "Ultraviolet Pulse", listOf(0xFFB5179E, 0xFF7209B7, 0xFF4CC9F0, 0xFFF72585, 0xFFE0AAFF)),
        ColorPalette("celadon_jade", "Celadon Jade", listOf(0xFF52B788, 0xFF95D5B2, 0xFFD8F3DC, 0xFF2D6A4F, 0xFFE9C46A)),
        ColorPalette("andes_alpenglow", "Andes Alpenglow", listOf(0xFFFF6B6B, 0xFFFFBE76, 0xFF686DE0, 0xFF4834D4, 0xFFF9CA24)),
        ColorPalette("marrakesh_saffron", "Marrakesh Saffron", listOf(0xFFE76F51, 0xFFF4A261, 0xFFE9C46A, 0xFF2A9D8F, 0xFF9B2226)),
        ColorPalette("venetian_murano", "Venetian Murano", listOf(0xFFD90429, 0xFFFFB703, 0xFF8ECAE6, 0xFF219EBC, 0xFFEDF2F4)),
        ColorPalette("plasma_prism", "Plasma Prism", listOf(0xFF00F5D4, 0xFF9B5DE5, 0xFFF15BB5, 0xFFFEE440, 0xFF00BBF9)),
        ColorPalette("boreal_fjord", "Boreal Fjord", listOf(0xFF6FFFE9, 0xFF5BC0BE, 0xFF3A506B, 0xFFA9DEF9, 0xFF80FFDB)),
        ColorPalette("gilded_bronze", "Gilded Bronze", listOf(0xFFD4AF37, 0xFFF5CB5C, 0xFFCFDBD5, 0xFFE8EDDF, 0xFFB07D62)),
        ColorPalette("miami_mirage", "Miami Mirage", listOf(0xFFFF2A85, 0xFF00F0FF, 0xFFFF9E00, 0xFF7B2CBF, 0xFF90F1EF)),
        ColorPalette("tropical_orchid", "Tropical Orchid", listOf(0xFFFF5D8F, 0xFFC77DFF, 0xFF06D6A0, 0xFFFFD166, 0xFFFFC6FF)),
        ColorPalette("lunar_frost", "Lunar Frost", listOf(0xFFE2E8F0, 0xFF94A3B8, 0xFF38BDF8, 0xFF818CF8, 0xFFF8FAFC)),
        ColorPalette("obsidian_ember", "Obsidian Ember", listOf(0xFFFF4800, 0xFFFF7900, 0xFFFFAA00, 0xFFD00000, 0xFFFFEA00)),
        ColorPalette("blackfire_opal", "Blackfire Opal", listOf(0xFF00FFA3, 0xFF00D1FF, 0xFFBD00FF, 0xFFFF2E93, 0xFFFFF500))
    )
}

# Rimalux Pattern Directory & Cross-Platform Porting Reference

This document serves as the canonical catalog of all **80 procedural mathematical patterns** in Rimalux (`com.cptodix.rimalux.oled.wallpaper.engine`). Use this reference to verify, cross-reference, and synchronize pattern implementations when porting Rimalux to other platforms (iOS Swift / CoreGraphics / Metal, Web TypeScript / Canvas2D / WebGL / SVG, Desktop, or Kotlin Multiplatform `commonMain`).

For core mathematical contracts and renderer specifications, see [`ARCHITECTURE_AND_PORTING.md`](ARCHITECTURE_AND_PORTING.md). For project rules and build restrictions, see [`../AGENTS.md`](../AGENTS.md).

---

## 1. Pattern Implementation Contract

Every pattern generator function adheres to an identical, pure mathematical contract:

```kotlin
// Input signature for all 80 builders:
fun build<PatternName>(
    rng: Random,              // Seeded deterministic PRNG (Random(seed))
    field: SculptField,       // Multi-octave macro spatial sculpting field
    intricacy: Float,         // Complexity factor [0.15f, 1.0f], default ~0.60f
    aspect: Float             // Canvas height / width ratio (e.g. 2400 / 1080 = 2.222f)
): List<DraftCell>
```

### Key Engine Rules
1. **Normalized Euclidean Space**: All coordinates live in `[0.0, 1.0] x [0.0, aspect]`. Distance `1.0` in $x$ equals distance `1.0` in $y$.
2. **`DraftCell` Output**: Each cell packages:
   - `score: Float` — spatial priority evaluated via `field.evaluate(x, y, rng)` (or `field.evaluateCenterContourDistance(x, y)` in Center Weighted mode),
   - `area: Float` — relative weight for multi-primitive cells,
   - `build: (assignedThreshold: Float) -> List<VectorPrimitive>` — factory creating the concrete vector primitives.
3. **Three Vector Primitive Types**:
   - `PolygonPrimitive(points: List<Vec2>, colorIndex: Int, fillStyle: ColorFillMode)`
   - `PolylineRibbonPrimitive(points: List<Vec2>, halfWidths: List<Float>, colorIndex: Int)`
   - `ArcSectorPrimitive(center: Vec2, radius: Float, startAngleRad: Float, sweepAngleRad: Float, innerRadiusRatio: Float, colorIndex: Int)`
4. **Deterministic Coverage Calibration**: Every builder's `List<DraftCell>` is passed through `CoverageCalibrator.calibrate(...)`, which rasterizes cells onto a $200 \times \text{round}(200 \times \text{aspect})$ boolean grid and assigns exact visibility thresholds $\in [0.0, 1.0]$ to guarantee the user's selected `#000000` True Black Ratio ($0\% - 95\%$).

---

## 2. Master Pattern Catalog (All 80 Patterns)

| # | Enum Identifier (`ArtPattern`) | String ID (`id`) | Category | Title | Subtitle | Source File (`engine/`) | Function Name | Primitives |
|---|---|---|---|---|---|---|---|---|
| 1 | `SILK_RIBBONS` | `silk_ribbons` | Flow & Waves | Silk Flow Ribbons | Differential curl-field streamlines | `OrganicFlowPatterns.kt` | `buildSilkRibbons` | Ribbon |
| 2 | `TOPO_CONTOURS` | `topo_contours` | Flow & Waves | Topo Contours | Multi-octave elevation isolines | `OrganicFlowPatterns.kt` | `buildTopoContours` | Ribbon |
| 3 | `HORIZON_WAVES` | `horizon_waves` | Flow & Waves | Horizon Strata | Layered harmonic ridges & eclipse | `OrganicFlowPatterns.kt` | `buildHorizonWaves` | Polygon, ArcSector |
| 4 | `LISSAJOUS_HARMONIC` | `lissajous_harmonic` | Flow & Waves | Lissajous Harmonics | Phase-locked orbital wave ribbons | `CelestialWavePatterns.kt` | `buildLissajousHarmonic` | Ribbon |
| 5 | `LOGARITHMIC_NAUTILUS` | `logarithmic_nautilus` | Flow & Waves | Golden Nautilus | Equiangular spiral chamber septa | `CelestialWavePatterns.kt` | `buildLogarithmicNautilus` | Polygon, Ribbon |
| 6 | `CHLADNI_CYMATICS` | `chladni_cymatics` | Flow & Waves | Chladni Cymatics | Acoustic standing-wave nodal cells | `CelestialWavePatterns.kt` | `buildChladniCymatics` | Polygon |
| 7 | `MOIRE_INTERFERENCE` | `moire_interference` | Flow & Waves | Moiré Interference | Dual-focus wave ring resonance | `CelestialWavePatterns.kt` | `buildMoireInterference` | ArcSector, Ribbon |
| 8 | `VECTOR_MAGNETIC_DIPOLE` | `magnetic_dipole` | Flow & Waves | Magnetic Flux | Bipolar vector field streamlines | `CelestialWavePatterns.kt` | `buildMagneticDipole` | Ribbon |
| 9 | `AURORA_CURTAINS` | `aurora_curtains` | Flow & Waves | Borealis Drapery | Vertical harmonic sky curtains | `CelestialWavePatterns.kt` | `buildAuroraCurtains` | Polygon, Ribbon |
| 10 | `SHATTERED_GEODE` | `shattered_geode` | Flow & Waves | Agate Geode | Crystalline mineral ring strata | `CelestialWavePatterns.kt` | `buildShatteredGeode` | Polygon |
| 11 | `OP_ART_GRAVITY` | `op_art_gravity` | Flow & Waves | Op-Art Gravity | Lens-warped kinetic ribbons | `ArchitecturalPatterns.kt` | `buildOpArtGravity` | Ribbon |
| 12 | `LORENZ_ATTRACTOR` | `lorenz_attractor` | Flow & Waves | Lorenz Butterfly | Chaotic strange-attractor phase wings | `AttractorFractalPatterns.kt` | `buildLorenzAttractor` | Ribbon |
| 13 | `FOURIER_EPICYCLES` | `fourier_epicycles` | Flow & Waves | Fourier Epicycles | Harmonic phasor orbital ribbons | `AttractorFractalPatterns.kt` | `buildFourierEpicycles` | Ribbon |
| 14 | `KELVIN_VORTEX` | `kelvin_vortex` | Flow & Waves | Vortex Street | Kármán counter-rotating fluid eddies | `AttractorFractalPatterns.kt` | `buildKelvinVortex` | Polygon, Ribbon |
| 15 | `CAUSTIC_WATER` | `caustic_water` | Flow & Waves | Lagoon Caustics | Refracted wave-envelope light cells | `AttractorFractalPatterns.kt` | `buildCausticWater` | Polygon |
| 16 | `THOMAS_ATTRACTOR` | `thomas_attractor` | Flow & Waves | Thomas Labyrinth | Cyclically symmetric 3D phase braids | `ChaoticSacredPatterns.kt` | `buildThomasAttractor` | Ribbon |
| 17 | `AIRY_WAVEFRONT` | `airy_wavefront` | Flow & Waves | Airy Diffraction | Parabolic catastrophe wavefront fringes | `ChaoticSacredPatterns.kt` | `buildAiryWavefront` | Ribbon, Polygon |
| 18 | `ROSSLER_SPIRAL` | `rossler_spiral` | Flow & Waves | Rössler Vortex | Folded chaotic band & saddle loops | `ChaoticSacredPatterns.kt` | `buildRosslerSpiral` | Ribbon |
| 19 | `CASSINI_LEMNISCATE` | `cassini_lemniscate` | Flow & Waves | Cassini Ovals | Bipolar constant-product lemniscates | `ChaoticSacredPatterns.kt` | `buildCassiniLemniscate` | Polygon, Ribbon |
| 20 | `CLIFFORD_ATTRACTOR` | `clifford_attractor` | Flow & Waves | Clifford Strange | Iterated trigonometric phase veil | `HarmonicAttractorPatterns.kt` | `buildCliffordAttractor` | Ribbon, Polygon |
| 21 | `FARADAY_RIPPLES` | `faraday_ripples` | Flow & Waves | Faraday Standing | Parametric capillary wave lattice | `HarmonicAttractorPatterns.kt` | `buildFaradayRipples` | Polygon, Ribbon |
| 22 | `BERNOULLI_SPIRALS` | `bernoulli_spirals` | Flow & Waves | Spira Mirabilis | Dual counter-rotating equiangular arms | `HarmonicAttractorPatterns.kt` | `buildBernoulliSpirals` | Ribbon |
| 23 | `SOLITON_TRAINS` | `soliton_trains` | Flow & Waves | KdV Solitons | Interacting nonlinear sech² wave pulses | `HarmonicAttractorPatterns.kt` | `buildSolitonTrains` | Polygon, Ribbon |
| 24 | `SACRED_MANDALA` | `sacred_mandala` | Sacred & Symmetry | Sacred Mandala | Polar rose harmonics & lotus arches | `GeometricSymmetryPatterns.kt` | `buildSacredMandala` | Polygon, ArcSector |
| 25 | `GUILLOCHE_ROSETTE` | `guilloche_rosette` | Sacred & Symmetry | Guilloché Spiro | Hypotrochoid moiré filigree | `OrganicFlowPatterns.kt` | `buildGuillocheRosette` | Ribbon |
| 26 | `FIBONACCI_LOTUS` | `fibonacci_lotus` | Sacred & Symmetry | Fibonacci Spiral | 137.5° golden-angle petal vortex | `OrganicFlowPatterns.kt` | `buildFibonacciLotus` | Polygon, ArcSector |
| 27 | `GIRIH_STARWORK` | `girih_starwork` | Sacred & Symmetry | Girih Starwork | 12-fold interlaced star rosettes | `GeometricSymmetryPatterns.kt` | `buildGirihStarwork` | Polygon, Ribbon |
| 28 | `HARMONIC_CARDIOID` | `harmonic_cardioid` | Sacred & Symmetry | Harmonic Cardioid | Modular chord & nephroid webs | `GeometricSymmetryPatterns.kt` | `buildHarmonicCardioid` | Ribbon |
| 29 | `APOLLONIAN_CIRCLES` | `apollonian_circles` | Sacred & Symmetry | Apollonian Rings | Tangent circle & crescent packing | `CelestialWavePatterns.kt` | `buildApollonianCircles` | ArcSector, Polygon |
| 30 | `KALEIDOSCOPE_PRISM` | `kaleidoscope_prism` | Sacred & Symmetry | Mirror Kaleidoscope | Dihedral symmetrical shard facets | `TessellationKineticPatterns.kt` | `buildKaleidoscopePrism` | Polygon |
| 31 | `HYPERBOLIC_POINCARE` | `hyperbolic_poincare` | Sacred & Symmetry | Poincaré Geodesics | Hyperbolic disk orthogonal arcs | `TessellationKineticPatterns.kt` | `buildHyperbolicPoincare` | ArcSector, Polygon |
| 32 | `SUPERELLIPSE_NEST` | `superellipse_nest` | Sacred & Symmetry | Lamé Superellipses | Twisted Piet Hein squircle rings | `TessellationKineticPatterns.kt` | `buildSuperellipseNest` | Ribbon, Polygon |
| 33 | `MAURER_ROSE` | `maurer_rose` | Sacred & Symmetry | Maurer Rose | Modular polar chord rose petals | `AttractorFractalPatterns.kt` | `buildMaurerRose` | Ribbon |
| 34 | `SUPERFORMULA_BLOOM` | `superformula_bloom` | Sacred & Symmetry | Gielis Morphogen | Generalized superformula star shells | `AttractorFractalPatterns.kt` | `buildSuperformulaBloom` | Polygon, Ribbon |
| 35 | `FERMAT_PARABOLIC` | `fermat_parabolic` | Sacred & Symmetry | Fermat Parastichy | Counter-winding sunflower spirals | `AttractorFractalPatterns.kt` | `buildFermatParabolic` | Polygon |
| 36 | `METATRON_POLYHEDRA` | `metatron_polyhedra` | Sacred & Symmetry | Metatron Star | 13-node sacred polyhedral lattice | `AttractorFractalPatterns.kt` | `buildMetatronPolyhedra` | Ribbon, Polygon |
| 37 | `DELTOID_ASTROID` | `deltoid_astroid` | Sacred & Symmetry | Astroid Cusps | Hypocycloid star envelopes & cusps | `ChaoticSacredPatterns.kt` | `buildDeltoidAstroid` | Ribbon, Polygon |
| 38 | `ZELLIJ_KHATAM` | `zellij_khatam` | Sacred & Symmetry | Zellij Khatam | 16-fold Moroccan star & saft rosette | `ChaoticSacredPatterns.kt` | `buildZellijKhatam` | Polygon |
| 39 | `VESICA_LENSES` | `vesica_lenses` | Sacred & Symmetry | Vesica Piscis | Overlapping sacred lens petal matrix | `ChaoticSacredPatterns.kt` | `buildVesicaLenses` | Polygon, ArcSector |
| 40 | `TRISKELION_SPIRALS` | `triskelion_spirals` | Sacred & Symmetry | Triskelion Spirals | Multi-arm counter-spun spiral blades | `ChaoticSacredPatterns.kt` | `buildTriskelionSpirals` | Polygon, Ribbon |
| 41 | `ENNEAGRAM_STAR` | `enneagram_star` | Sacred & Symmetry | Enneagram Rosette | 9-fold nonagonal chord & petal mandala | `HarmonicAttractorPatterns.kt` | `buildEnneagramStar` | Polygon, Ribbon |
| 42 | `RHODONEA_BLOOM` | `rhodonea_bloom` | Sacred & Symmetry | Rhodonea Curves | Harmonic Grandi polar petal shells | `HarmonicAttractorPatterns.kt` | `buildRhodoneaBloom` | Ribbon, Polygon |
| 43 | `EPITROCHOID_CROWN` | `epitrochoid_crown` | Sacred & Symmetry | Epitrochoid Crown | Rolling planetary orbital cusps & loops | `HarmonicAttractorPatterns.kt` | `buildEpitrochoidCrown` | Ribbon |
| 44 | `SRI_YANTRA_TRIANGLES` | `sri_yantra_triangles` | Sacred & Symmetry | Yantra Interlace | Interlocking sacred triangles & lotus | `HarmonicAttractorPatterns.kt` | `buildSriYantraTriangles` | Polygon |
| 45 | `ISOMETRIC_MONUMENT` | `isometric_monument` | Architectural | Isometric Prisms | 30° architectural stepped blocks | `ArchitecturalPatterns.kt` | `buildIsometricMonument` | Polygon |
| 46 | `BAUHAUS_CONSTRUCT` | `bauhaus_construct` | Architectural | Bauhaus Construct | Golden-ratio arcs & fine louvres | `ArchitecturalPatterns.kt` | `buildBauhausConstruct` | Polygon, ArcSector, Ribbon |
| 47 | `ART_DECO_SUNBURST` | `art_deco_sunburst` | Architectural | Art Deco Sunburst | Radiating fan arches & spires | `GeometricSymmetryPatterns.kt` | `buildArtDecoSunburst` | Polygon, ArcSector |
| 48 | `ORIGAMI_MIURA` | `origami_miura` | Architectural | Origami Miura | Pleated herringbone crease folds | `ArchitecturalPatterns.kt` | `buildOrigamiMiura` | Polygon |
| 49 | `KUMIKO_WOODWORK` | `kumiko_woodwork` | Architectural | Asanoha Kumiko | Japanese 6-fold hemp-leaf joinery | `TessellationKineticPatterns.kt` | `buildKumikoWoodwork` | Polygon, Ribbon |
| 50 | `GEODESIC_DOME` | `geodesic_dome` | Architectural | Geodesic Dome | Triangulated spherical truss facets | `ConstructTessellationPatterns.kt` | `buildGeodesicDome` | Polygon |
| 51 | `PERSPECTIVE_COLONNADE` | `perspective_colonnade` | Architectural | Infinite Atrium | Vanishing-point coffered portals | `ConstructTessellationPatterns.kt` | `buildPerspectiveColonnade` | Polygon |
| 52 | `DE_STIJL_MONDRIAN` | `de_stijl_mondrian` | Architectural | Neoplastic Grid | Golden-ratio orthogonal color slabs | `ConstructTessellationPatterns.kt` | `buildDeStijlMondrian` | Polygon |
| 53 | `BRUTALIST_ZIGGURAT` | `brutalist_ziggurat` | Architectural | Stepped Ziggurat | Cantilevered shadow-bevel terraces | `ConstructTessellationPatterns.kt` | `buildBrutalistZiggurat` | Polygon |
| 54 | `ESCHER_STAIRCASES` | `escher_staircases` | Architectural | Escher Staircases | Axonometric terraced flights & risers | `ParametricQuasicrystalPatterns.kt` | `buildEscherStaircases` | Polygon |
| 55 | `KINETIC_FACADE` | `kinetic_facade` | Architectural | Kinetic Facade | Twisted parametric brise-soleil fins | `ParametricQuasicrystalPatterns.kt` | `buildKineticFacade` | Polygon |
| 56 | `PANTHEON_OCULUS` | `pantheon_oculus` | Architectural | Pantheon Coffers | Recessed radial dome caissons & oculus | `ParametricQuasicrystalPatterns.kt` | `buildPantheonOculus` | Polygon, ArcSector |
| 57 | `SUPREMATIST_PLANES` | `suprematist_planes` | Architectural | Suprematist Planes | Cantilevered constructivist vectors | `ParametricQuasicrystalPatterns.kt` | `buildSuprematistPlanes` | Polygon, Ribbon |
| 58 | `CALATRAVA_RIBS` | `calatrava_ribs` | Architectural | Vaulted Parabolas | Sweeping parabolic rib-arch colonnade | `VaultedLatticePatterns.kt` | `buildCalatravaRibs` | Polygon, Ribbon |
| 59 | `HEXAGONAL_BASALT` | `hexagonal_basalt` | Architectural | Basalt Columns | Axonometric stepped hexagonal pillars | `VaultedLatticePatterns.kt` | `buildHexagonalBasalt` | Polygon |
| 60 | `METABOLIST_CAPSULES` | `metabolist_capsules` | Architectural | Capsule Towers | Modular cantilevered oculus pods | `VaultedLatticePatterns.kt` | `buildMetabolistCapsules` | Polygon, ArcSector |
| 61 | `DIAGRID_SKYSCRAPER` | `diagrid_skyscraper` | Architectural | Diagrid Atrium | Tapered structural diamond exoskeleton | `VaultedLatticePatterns.kt` | `buildDiagridSkyscraper` | Polygon, Ribbon |
| 62 | `CRYSTAL_VORONOI` | `crystal_voronoi` | Tessellations | Crystal Facets | Beveled Delaunay gemstone shards | `ArchitecturalPatterns.kt` | `buildCrystalVoronoi` | Polygon |
| 63 | `PENROSE_QUASICRYSTAL` | `penrose_quasicrystal` | Tessellations | Penrose Tiling | Aperiodic φ-triangle quasicrystal | `GeometricSymmetryPatterns.kt` | `buildPenroseQuasicrystal` | Polygon |
| 64 | `FRACTAL_LATTICE` | `fractal_lattice` | Tessellations | Fractal Weave | Recursive barycentric subdivision | `GeometricSymmetryPatterns.kt` | `buildFractalLattice` | Polygon |
| 65 | `VORONOI_STAINED_GLASS` | `voronoi_stained_glass` | Tessellations | Cathedral Glass | Polygonal Voronoi leaded jewels | `TessellationKineticPatterns.kt` | `buildVoronoiStainedGlass` | Polygon |
| 66 | `HEX_HONEYCOMB_PRISM` | `hex_honeycomb_prism` | Tessellations | Hexagonal Matrix | Nested 6-fold rhombic prism cells | `TessellationKineticPatterns.kt` | `buildHexHoneycombPrism` | Polygon |
| 67 | `TRUCHET_LABYRINTH` | `truchet_labyrinth` | Tessellations | Truchet Meander | Seamless quarter-arc ribbon paths | `TessellationKineticPatterns.kt` | `buildTruchetLabyrinth` | Ribbon |
| 68 | `CELTIC_INTERLACE` | `celtic_interlace` | Tessellations | Braided Lattice | Over-under woven diagonal straps | `TessellationKineticPatterns.kt` | `buildCelticInterlace` | Ribbon |
| 69 | `HILBERT_FRACTAL` | `hilbert_fractal` | Tessellations | Hilbert Labyrinth | Space-filling continuous fractal path | `ConstructTessellationPatterns.kt` | `buildHilbertFractal` | Ribbon |
| 70 | `DRAGON_CURVE` | `dragon_curve` | Tessellations | Heighway Dragon | Self-similar 90° fold mosaic | `ConstructTessellationPatterns.kt` | `buildDragonCurve` | Ribbon, Polygon |
| 71 | `CAIRO_PENTAGONAL` | `cairo_pentagonal` | Tessellations | Cairo Pentagons | 4-fold alternating street-tile mesh | `ConstructTessellationPatterns.kt` | `buildCairoPentagonal` | Polygon |
| 72 | `KAGOME_TRIHEXAGONAL` | `kagome_trihexagonal` | Tessellations | Kagome Lattice | Trihexagonal woven star basketry | `ConstructTessellationPatterns.kt` | `buildKagomeTrihexagonal` | Polygon |
| 73 | `SIERPINSKI_PRISM` | `sierpinski_prism` | Tessellations | Sierpiński Prism | Recursive triangular gasket facets | `ParametricQuasicrystalPatterns.kt` | `buildSierpinskiPrism` | Polygon |
| 74 | `ARCHIMEDEAN_SNUB` | `archimedean_snub` | Tessellations | Archimedean Snub | Chiral square-triangle semi-regular mesh | `ParametricQuasicrystalPatterns.kt` | `buildArchimedeanSnub` | Polygon |
| 75 | `AMMANN_QUASICRYSTAL` | `ammann_quasicrystal` | Tessellations | Ammann Quasicrystal | 8-fold silver-ratio rhombus & square | `ParametricQuasicrystalPatterns.kt` | `buildAmmannQuasicrystal` | Polygon |
| 76 | `PYTHAGORAS_CANOPY` | `pythagoras_canopy` | Tessellations | Pythagoras Fractal | Recursive right-triangle square canopy | `ParametricQuasicrystalPatterns.kt` | `buildPythagorasCanopy` | Polygon |
| 77 | `LAVES_FLORET` | `laves_floret` | Tessellations | Floret Pentagons | 6-fold chiral Laves pentagonal rosettes | `VaultedLatticePatterns.kt` | `buildLavesFloret` | Polygon |
| 78 | `KITE_DART_STAR` | `kite_dart_star` | Tessellations | Decagonal Stars | 5-fold golden kite & dart sunburst tiling | `VaultedLatticePatterns.kt` | `buildKiteDartStar` | Polygon |
| 79 | `TRIANGULAR_GYROID` | `triangular_gyroid` | Tessellations | Triangular Bevels | 3-way isometric cube-corner lattice | `VaultedLatticePatterns.kt` | `buildTriangularGyroid` | Polygon |
| 80 | `GOSPER_FLOWSNAKE` | `gosper_flowsnake` | Tessellations | Gosper Island | Hexagonal recursive Peano-Gosper tiles | `VaultedLatticePatterns.kt` | `buildGosperFlowsnake` | Polygon |

---

## 3. Detailed Architecture of the 11 Pattern Source Files

Each source file implements a cohesive mathematical domain. All functions are packaged as `internal object <FileName>` inside `com.cptodix.rimalux.oled.wallpaper.engine`.

### 3.1 `OrganicFlowPatterns.kt` (5 Patterns)
- **Path**: `app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/OrganicFlowPatterns.kt`
- **Domain**: Continuous fluid dynamics, differential field integration, and natural phyllotaxis.
- **Patterns**:
  1. `buildSilkRibbons`: 4th-order Runge-Kutta streamline integration through a multi-frequency curl field.
  2. `buildTopoContours`: Multi-octave Perlin-like 2D noise elevation slicing with smooth contour marching ribbons.
  3. `buildGuillocheRosette`: Mechanical geometric lathe simulation with coupled epicycloid/hypotrochoid ratio equations.
  4. `buildFibonacciLotus`: Golden angle ($\approx 137.507764^\circ$) phyllotaxis spiral packing with outward-expanding petal polygons.
  5. `buildHorizonWaves`: Layered horizontal harmonic sin/cos strata with occluding celestial eclipse disc.

### 3.2 `GeometricSymmetryPatterns.kt` (6 Patterns)
- **Path**: `app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/GeometricSymmetryPatterns.kt`
- **Domain**: Dihedral symmetry, aperiodic Penrose projection, Islamic geometric starwork, and modular cardioids.
- **Patterns**:
  1. `buildSacredMandala`: N-fold polar radial symmetry with multi-tiered lotus petal arches and nested mandala rings.
  2. `buildPenroseQuasicrystal`: Robinson triangle deflation method for 5-fold aperiodic golden-ratio ($\phi = 1.618$) tilings.
  3. `buildGirihStarwork`: Historical 8- and 12-fold Islamic strapwork with interlaced strap borders and central star rosettes.
  4. `buildHarmonicCardioid`: Circular modular multiplication envelopes ($z \mapsto z \cdot m \pmod n$) producing cardioid and nephroid caustics.
  5. `buildArtDecoSunburst`: Stepped 1920s skyscraper sunburst with radiating fan arches, chevrons, and vertical spires.
  6. `buildFractalLattice`: Recursive barycentric triangle subdivision with alternating centroid voids.

### 3.3 `ArchitecturalPatterns.kt` (5 Patterns)
- **Path**: `app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/ArchitecturalPatterns.kt`
- **Domain**: Axonometric projection, structural constructivism, and origami tessellation.
- **Patterns**:
  1. `buildCrystalVoronoi`: Lloyd-relaxed Voronoi cell decomposition with beveled gem facets and 3D lighting normal shading.
  2. `buildIsometricMonument`: True $30^\circ$ isometric projection of stepped cubic monolithic monuments and stairwells.
  3. `buildBauhausConstruct`: Orthogonal constructivist balance of golden-ratio rectangular planes, circular cutouts, and fine louvres.
  4. `buildOrigamiMiura`: Spacecraft solar panel Miura-ori herringbone crease pattern with alternating parallelogram facets.
  5. `buildOpArtGravity`: Victor Vasarely-inspired spherically warped grid lines creating the illusion of deep gravitational distortion.

### 3.4 `CelestialWavePatterns.kt` (8 Patterns)
- **Path**: `app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/CelestialWavePatterns.kt`
- **Domain**: Wave interference, acoustic nodal lines, physics vector fields, and conformal geometry.
- **Patterns**:
  1. `buildLissajousHarmonic`: Orthogonal harmonic frequency curves with irrational phase shifts ($x = \sin(at + \delta), y = \sin(bt)$).
  2. `buildLogarithmicNautilus`: Equiangular logarithmic spiral ($r = a e^{b\theta}$) with chambers and transverse septa.
  3. `buildChladniCymatics`: 2D acoustic plate vibration nodal lines ($\cos(n\pi x)\cos(m\pi y) - \cos(m\pi x)\cos(n\pi y) = 0$).
  4. `buildMoireInterference`: Concentric circular wave emission from dual focal points producing hyperbolic interference fringes.
  5. `buildMagneticDipole`: Maxwell electromagnetic field line integration between positive and negative magnetic poles.
  6. `buildAuroraCurtains`: Vertical undulating ribbon curtains modulated by low-frequency harmonic drift.
  7. `buildApollonianCircles`: Recursive Descartes circle theorem circle-packing with crescent moon negative space.
  8. `buildShatteredGeode`: Perturbed concentric polygon rings simulating cut mineral agate geode bands.

### 3.5 `TessellationKineticPatterns.kt` (8 Patterns)
- **Path**: `app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/TessellationKineticPatterns.kt`
- **Domain**: Regular and semi-regular tilings, non-Euclidean geometry, and traditional woodworking joinery.
- **Patterns**:
  1. `buildVoronoiStainedGlass`: Random Voronoi seeds with thick leaded borders and faceted polygonal jewel panes.
  2. `buildHexHoneycombPrism`: 3D axonometric hexagonal prisms with 3-tone diamond tops and isometric side faces.
  3. `buildTruchetLabyrinth`: Smith-Truchet quarter-circle arc tiles with smooth path continuity across square grid edges.
  4. `buildSuperellipseNest`: Concentric Lamé curves ($|x/a|^n + |y/b|^n = 1$) with variable exponent $n \in [2.5, 4.0]$ and twist rotation.
  5. `buildKaleidoscopePrism`: Dihedral reflection group kaleidoscopic tiling replicating multiple mirror reflections.
  6. `buildHyperbolicPoincare`: Poincaré disk model of the hyperbolic plane with orthogonal circular geodesics.
  7. `buildCelticInterlace`: Over-and-under woven diagonal strapwork with rounded ribbon corners.
  8. `buildKumikoWoodwork`: Japanese *Asanoha* (hemp leaf) joinery based on a regular triangular grid with internal 3-way struts.

### 3.6 `AttractorFractalPatterns.kt` (8 Patterns)
- **Path**: `app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/AttractorFractalPatterns.kt`
- **Domain**: Non-linear dynamical systems, strange attractors, and parametric botanic forms.
- **Patterns**:
  1. `buildLorenzAttractor`: Classical Lorenz system ($\dot{x} = \sigma(y-x), \dot{y} = x(\rho-z)-y, \dot{z} = xy-\beta z$) projected onto the 2D viewing plane.
  2. `buildFourierEpicycles`: Epicyclic phasor superposition simulating complex closed curve reconstruction.
  3. `buildKelvinVortex`: Counter-rotating vortex street fluid velocity potential streamlines.
  4. `buildCausticWater`: Ray-traced optical refraction envelopes through sinusoidal surface water waves.
  5. `buildMaurerRose`: Polar rose $r = \sin(n\theta)$ sampled at modular integer degrees $d^\circ$, creating intricate chord stars.
  6. `buildSuperformulaBloom`: Johan Gielis's generalized superformula generating complex botanical star-flower boundaries.
  7. `buildFermatParabolic`: Fermat spiral $r = \pm a\sqrt{\theta}$ modeling counter-winding sunflower seed parastichies.
  8. `buildMetatronPolyhedra`: Sacred geometry cube connecting the centers of 13 tangent circles representing the 5 Platonic solids.

### 3.7 `ConstructTessellationPatterns.kt` (8 Patterns)
- **Path**: `app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/ConstructTessellationPatterns.kt`
- **Domain**: Structural geodesic geometry, space-filling fractal curves, and modern art grids.
- **Patterns**:
  1. `buildGeodesicDome`: Class-I icosahedral geodesic dome projection with planar triangular facets.
  2. `buildPerspectiveColonnade`: 1-point central perspective colonnade with coffered ceiling caissons and receding floor grid.
  3. `buildDeStijlMondrian`: Piet Mondrian-inspired asymmetric grid with heavy black orthogonal rules and balanced primary color slabs.
  4. `buildBrutalistZiggurat`: Massive stepped cantilevered concrete blocks with deep shadow bevels.
  5. `buildHilbertFractal`: Recursive 2D space-filling Hilbert curve expanded into a ribbon maze.
  6. `buildDragonCurve`: Heighway dragon curve generated by iterative $90^\circ$ paper-folding sequence.
  7. `buildCairoPentagonal`: Dual of the snub square tiling, composed of equilateral pentagons with 4-fold alternating orientations.
  8. `buildKagomeTrihexagonal`: Trihexagonal Archimedean tiling ($3.6.3.6$) popular in traditional Japanese woven basketry.

### 3.8 `ChaoticSacredPatterns.kt` (8 Patterns)
- **Path**: `app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/ChaoticSacredPatterns.kt`
- **Domain**: Chaotic ODE braids, optical caustic envelopes, and Islamic tilework.
- **Patterns**:
  1. `buildThomasAttractor`: Cyclic symmetric differential system ($\dot{x} = \sin(y) - bx, \dots$) integrated in 3D and projected.
  2. `buildAiryWavefront`: Caustic optical catastrophe wavefront following the Airy cubic fold envelope ($y^2 = x^3$).
  3. `buildRosslerSpiral`: Otto Rössler's strange attractor with a planar spiral band that folds upward into a non-linear saddle loop.
  4. `buildCassiniLemniscate`: Curves where the product of distances to two foci is constant ($d_1 \cdot d_2 = b^2$), transitioning from two ovals to a figure-eight.
  5. `buildDeltoidAstroid`: Hypocycloids with 3 and 4 cusps formed by a circle rolling inside another circle of 3x or 4x radius.
  6. `buildZellijKhatam`: Traditional 16-fold Moroccan Islamic star rosette tiling with interlacing strap borders.
  7. `buildVesicaLenses`: Overlapping circles of identical radius where the center of each lies on the perimeter of the other (*Vesica Piscis*).
  8. `buildTriskelionSpirals`: Ancient triple-spiral motif with 3 counter-rotating logarithmic arms radiating from an equilateral center.

### 3.9 `ParametricQuasicrystalPatterns.kt` (8 Patterns)
- **Path**: `app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/ParametricQuasicrystalPatterns.kt`
- **Domain**: Non-periodic quasicrystals, Escher impossible spaces, and classical dome architecture.
- **Patterns**:
  1. `buildEscherStaircases`: M.C. Escher-style axonometric multi-directional stairs and connecting landings.
  2. `buildKineticFacade`: Contemporary parametric architecture with twisted brise-soleil sun louvres.
  3. `buildPantheonOculus`: Rome's Pantheon rotunda ceiling with 5 concentric tiers of 28 recessed caissons converging on an open oculus.
  4. `buildSuprematistPlanes`: Kazimir Malevich-inspired floating geometric polygons, trapezoids, and dynamic velocity rays.
  5. `buildSierpinskiPrism`: 3D isometric projection of the recursive Sierpiński triangle gasket.
  6. `buildArchimedeanSnub`: Semi-regular snub square tiling ($3.3.3.3.4$) featuring chiral squares surrounded by triangles.
  7. `buildAmmannQuasicrystal`: Ammann-Beenker 8-fold aperiodic quasicrystal tiling using silver-ratio rhombi and squares.
  8. `buildPythagorasCanopy`: Recursive Pythagoras tree fractal where each square sprouts two smaller squares on a right-angled triangle.

### 3.10 `HarmonicAttractorPatterns.kt` (8 Patterns)
- **Path**: `app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/HarmonicAttractorPatterns.kt`
- **Domain**: Iterated map attractors, nonlinear waves, and esoteric mandalas.
- **Patterns**:
  1. `buildCliffordAttractor`: Discrete 2D iterated map ($x_{n+1} = \sin(a y_n) + c \cos(a x_n), y_{n+1} = \sin(b x_n) + d \cos(b y_n)$).
  2. `buildFaradayRipples`: Surface capillary wave standing patterns under vertical sinusoidal oscillation.
  3. `buildBernoulliSpirals`: Jakob Bernoulli's *Spira Mirabilis* ($r = a e^{b\theta}$) with dual interlocked arms.
  4. `buildSolitonTrains`: Korteweg-de Vries (KdV) equation soliton wave packet solutions ($u(x,t) = 2\kappa^2 \operatorname{sech}^2(\kappa(x - 4\kappa^2 t))$).
  5. `buildEnneagramStar`: Sacred 9-pointed nonagonal geometric star with periodic internal chords.
  6. `buildRhodoneaBloom`: Luigi Guido Grandi's mathematical rose curves ($r = a \cos(k\theta)$) with multi-layered petals.
  7. `buildEpitrochoidCrown`: Rolling circle roulette curves tracing outward orbital cusps and loops.
  8. `buildSriYantraTriangles`: Sacred Tantric diagram consisting of 9 interlocking isosceles triangles surrounding a central bindu point.

### 3.11 `VaultedLatticePatterns.kt` (8 Patterns)
- **Path**: `app/src/main/java/com/cptodix/rimalux/oled/wallpaper/engine/VaultedLatticePatterns.kt`
- **Domain**: Organic structural architecture, volcanic cooling columns, and space-filling Peano curves.
- **Patterns**:
  1. `buildCalatravaRibs`: Santiago Calatrava-inspired ribbed concrete parabolic arches with cantilevered spines.
  2. `buildHexagonalBasalt`: Giant's Causeway cooling basalt hexagonal prisms in axonometric stepped heights.
  3. `buildMetabolistCapsules`: Kisho Kurokawa's Nakagin Capsule Tower aesthetic with modular cantilevered residential pods.
  4. `buildDiagridSkyscraper`: Norman Foster-style structural diagonal grid diamond tube exoskeleton.
  5. `buildLavesFloret`: Dual of the snub trihexagonal tiling featuring 6-fold chiral pentagonal flower florets.
  6. `buildKiteDartStar`: Penrose P2 tiling using convex kites and non-convex darts forming 10-pointed starbursts.
  7. `buildTriangularGyroid`: Tri-directional isometric cube corner tessellation resembling a triply periodic minimal surface (TPMS).
  8. `buildGosperFlowsnake`: Recursive Peano-Gosper fractal curve tiling hexagonal boundaries with space-filling flowlines.

---

## 4. Central Dispatch Registry Reference

When porting to another language (Swift, TypeScript, C++, Rust), implement the dispatch lookup exactly as structured in `MathPatternGenerator.generate`:

```kotlin
val draftCells: List<DraftCell> = when (pattern) {
    // 1-5: Organic Flow
    ArtPattern.SILK_RIBBONS -> OrganicFlowPatterns.buildSilkRibbons(rng, field, safeIntricacy, safeAspect)
    ArtPattern.TOPO_CONTOURS -> OrganicFlowPatterns.buildTopoContours(rng, field, safeIntricacy, safeAspect)
    ArtPattern.GUILLOCHE_ROSETTE -> OrganicFlowPatterns.buildGuillocheRosette(rng, field, safeIntricacy, safeAspect)
    ArtPattern.FIBONACCI_LOTUS -> OrganicFlowPatterns.buildFibonacciLotus(rng, field, safeIntricacy, safeAspect)
    ArtPattern.HORIZON_WAVES -> OrganicFlowPatterns.buildHorizonWaves(rng, field, safeIntricacy, safeAspect)

    // 6-11: Geometric Symmetry
    ArtPattern.SACRED_MANDALA -> GeometricSymmetryPatterns.buildSacredMandala(rng, field, safeIntricacy, safeAspect)
    ArtPattern.PENROSE_QUASICRYSTAL -> GeometricSymmetryPatterns.buildPenroseQuasicrystal(rng, field, safeIntricacy, safeAspect)
    ArtPattern.GIRIH_STARWORK -> GeometricSymmetryPatterns.buildGirihStarwork(rng, field, safeIntricacy, safeAspect)
    ArtPattern.HARMONIC_CARDIOID -> GeometricSymmetryPatterns.buildHarmonicCardioid(rng, field, safeIntricacy, safeAspect)
    ArtPattern.ART_DECO_SUNBURST -> GeometricSymmetryPatterns.buildArtDecoSunburst(rng, field, safeIntricacy, safeAspect)
    ArtPattern.FRACTAL_LATTICE -> GeometricSymmetryPatterns.buildFractalLattice(rng, field, safeIntricacy, safeAspect)

    // 12-16: Architectural
    ArtPattern.CRYSTAL_VORONOI -> ArchitecturalPatterns.buildCrystalVoronoi(rng, field, safeIntricacy, safeAspect)
    ArtPattern.ISOMETRIC_MONUMENT -> ArchitecturalPatterns.buildIsometricMonument(rng, field, safeIntricacy, safeAspect)
    ArtPattern.BAUHAUS_CONSTRUCT -> ArchitecturalPatterns.buildBauhausConstruct(rng, field, safeIntricacy, safeAspect)
    ArtPattern.ORIGAMI_MIURA -> ArchitecturalPatterns.buildOrigamiMiura(rng, field, safeIntricacy, safeAspect)
    ArtPattern.OP_ART_GRAVITY -> ArchitecturalPatterns.buildOpArtGravity(rng, field, safeIntricacy, safeAspect)

    // 17-24: Celestial Wave
    ArtPattern.LISSAJOUS_HARMONIC -> CelestialWavePatterns.buildLissajousHarmonic(rng, field, safeIntricacy, safeAspect)
    ArtPattern.LOGARITHMIC_NAUTILUS -> CelestialWavePatterns.buildLogarithmicNautilus(rng, field, safeIntricacy, safeAspect)
    ArtPattern.CHLADNI_CYMATICS -> CelestialWavePatterns.buildChladniCymatics(rng, field, safeIntricacy, safeAspect)
    ArtPattern.MOIRE_INTERFERENCE -> CelestialWavePatterns.buildMoireInterference(rng, field, safeIntricacy, safeAspect)
    ArtPattern.VECTOR_MAGNETIC_DIPOLE -> CelestialWavePatterns.buildMagneticDipole(rng, field, safeIntricacy, safeAspect)
    ArtPattern.AURORA_CURTAINS -> CelestialWavePatterns.buildAuroraCurtains(rng, field, safeIntricacy, safeAspect)
    ArtPattern.APOLLONIAN_CIRCLES -> CelestialWavePatterns.buildApollonianCircles(rng, field, safeIntricacy, safeAspect)
    ArtPattern.SHATTERED_GEODE -> CelestialWavePatterns.buildShatteredGeode(rng, field, safeIntricacy, safeAspect)

    // 25-32: Tessellation Kinetic
    ArtPattern.VORONOI_STAINED_GLASS -> TessellationKineticPatterns.buildVoronoiStainedGlass(rng, field, safeIntricacy, safeAspect)
    ArtPattern.HEX_HONEYCOMB_PRISM -> TessellationKineticPatterns.buildHexHoneycombPrism(rng, field, safeIntricacy, safeAspect)
    ArtPattern.TRUCHET_LABYRINTH -> TessellationKineticPatterns.buildTruchetLabyrinth(rng, field, safeIntricacy, safeAspect)
    ArtPattern.SUPERELLIPSE_NEST -> TessellationKineticPatterns.buildSuperellipseNest(rng, field, safeIntricacy, safeAspect)
    ArtPattern.KALEIDOSCOPE_PRISM -> TessellationKineticPatterns.buildKaleidoscopePrism(rng, field, safeIntricacy, safeAspect)
    ArtPattern.HYPERBOLIC_POINCARE -> TessellationKineticPatterns.buildHyperbolicPoincare(rng, field, safeIntricacy, safeAspect)
    ArtPattern.CELTIC_INTERLACE -> TessellationKineticPatterns.buildCelticInterlace(rng, field, safeIntricacy, safeAspect)
    ArtPattern.KUMIKO_WOODWORK -> TessellationKineticPatterns.buildKumikoWoodwork(rng, field, safeIntricacy, safeAspect)

    // 33-40: Attractor & Fractal
    ArtPattern.LORENZ_ATTRACTOR -> AttractorFractalPatterns.buildLorenzAttractor(rng, field, safeIntricacy, safeAspect)
    ArtPattern.FOURIER_EPICYCLES -> AttractorFractalPatterns.buildFourierEpicycles(rng, field, safeIntricacy, safeAspect)
    ArtPattern.KELVIN_VORTEX -> AttractorFractalPatterns.buildKelvinVortex(rng, field, safeIntricacy, safeAspect)
    ArtPattern.CAUSTIC_WATER -> AttractorFractalPatterns.buildCausticWater(rng, field, safeIntricacy, safeAspect)
    ArtPattern.MAURER_ROSE -> AttractorFractalPatterns.buildMaurerRose(rng, field, safeIntricacy, safeAspect)
    ArtPattern.SUPERFORMULA_BLOOM -> AttractorFractalPatterns.buildSuperformulaBloom(rng, field, safeIntricacy, safeAspect)
    ArtPattern.FERMAT_PARABOLIC -> AttractorFractalPatterns.buildFermatParabolic(rng, field, safeIntricacy, safeAspect)
    ArtPattern.METATRON_POLYHEDRA -> AttractorFractalPatterns.buildMetatronPolyhedra(rng, field, safeIntricacy, safeAspect)

    // 41-48: Construct & Tessellation
    ArtPattern.GEODESIC_DOME -> ConstructTessellationPatterns.buildGeodesicDome(rng, field, safeIntricacy, safeAspect)
    ArtPattern.PERSPECTIVE_COLONNADE -> ConstructTessellationPatterns.buildPerspectiveColonnade(rng, field, safeIntricacy, safeAspect)
    ArtPattern.DE_STIJL_MONDRIAN -> ConstructTessellationPatterns.buildDeStijlMondrian(rng, field, safeIntricacy, safeAspect)
    ArtPattern.BRUTALIST_ZIGGURAT -> ConstructTessellationPatterns.buildBrutalistZiggurat(rng, field, safeIntricacy, safeAspect)
    ArtPattern.HILBERT_FRACTAL -> ConstructTessellationPatterns.buildHilbertFractal(rng, field, safeIntricacy, safeAspect)
    ArtPattern.DRAGON_CURVE -> ConstructTessellationPatterns.buildDragonCurve(rng, field, safeIntricacy, safeAspect)
    ArtPattern.CAIRO_PENTAGONAL -> ConstructTessellationPatterns.buildCairoPentagonal(rng, field, safeIntricacy, safeAspect)
    ArtPattern.KAGOME_TRIHEXAGONAL -> ConstructTessellationPatterns.buildKagomeTrihexagonal(rng, field, safeIntricacy, safeAspect)

    // 49-56: Chaotic & Sacred
    ArtPattern.THOMAS_ATTRACTOR -> ChaoticSacredPatterns.buildThomasAttractor(rng, field, safeIntricacy, safeAspect)
    ArtPattern.AIRY_WAVEFRONT -> ChaoticSacredPatterns.buildAiryWavefront(rng, field, safeIntricacy, safeAspect)
    ArtPattern.ROSSLER_SPIRAL -> ChaoticSacredPatterns.buildRosslerSpiral(rng, field, safeIntricacy, safeAspect)
    ArtPattern.CASSINI_LEMNISCATE -> ChaoticSacredPatterns.buildCassiniLemniscate(rng, field, safeIntricacy, safeAspect)
    ArtPattern.DELTOID_ASTROID -> ChaoticSacredPatterns.buildDeltoidAstroid(rng, field, safeIntricacy, safeAspect)
    ArtPattern.ZELLIJ_KHATAM -> ChaoticSacredPatterns.buildZellijKhatam(rng, field, safeIntricacy, safeAspect)
    ArtPattern.VESICA_LENSES -> ChaoticSacredPatterns.buildVesicaLenses(rng, field, safeIntricacy, safeAspect)
    ArtPattern.TRISKELION_SPIRALS -> ChaoticSacredPatterns.buildTriskelionSpirals(rng, field, safeIntricacy, safeAspect)

    // 57-64: Parametric & Quasicrystal
    ArtPattern.ESCHER_STAIRCASES -> ParametricQuasicrystalPatterns.buildEscherStaircases(rng, field, safeIntricacy, safeAspect)
    ArtPattern.KINETIC_FACADE -> ParametricQuasicrystalPatterns.buildKineticFacade(rng, field, safeIntricacy, safeAspect)
    ArtPattern.PANTHEON_OCULUS -> ParametricQuasicrystalPatterns.buildPantheonOculus(rng, field, safeIntricacy, safeAspect)
    ArtPattern.SUPREMATIST_PLANES -> ParametricQuasicrystalPatterns.buildSuprematistPlanes(rng, field, safeIntricacy, safeAspect)
    ArtPattern.SIERPINSKI_PRISM -> ParametricQuasicrystalPatterns.buildSierpinskiPrism(rng, field, safeIntricacy, safeAspect)
    ArtPattern.ARCHIMEDEAN_SNUB -> ParametricQuasicrystalPatterns.buildArchimedeanSnub(rng, field, safeIntricacy, safeAspect)
    ArtPattern.AMMANN_QUASICRYSTAL -> ParametricQuasicrystalPatterns.buildAmmannQuasicrystal(rng, field, safeIntricacy, safeAspect)
    ArtPattern.PYTHAGORAS_CANOPY -> ParametricQuasicrystalPatterns.buildPythagorasCanopy(rng, field, safeIntricacy, safeAspect)

    // 65-72: Harmonic & Attractor
    ArtPattern.CLIFFORD_ATTRACTOR -> HarmonicAttractorPatterns.buildCliffordAttractor(rng, field, safeIntricacy, safeAspect)
    ArtPattern.FARADAY_RIPPLES -> HarmonicAttractorPatterns.buildFaradayRipples(rng, field, safeIntricacy, safeAspect)
    ArtPattern.BERNOULLI_SPIRALS -> HarmonicAttractorPatterns.buildBernoulliSpirals(rng, field, safeIntricacy, safeAspect)
    ArtPattern.SOLITON_TRAINS -> HarmonicAttractorPatterns.buildSolitonTrains(rng, field, safeIntricacy, safeAspect)
    ArtPattern.ENNEAGRAM_STAR -> HarmonicAttractorPatterns.buildEnneagramStar(rng, field, safeIntricacy, safeAspect)
    ArtPattern.RHODONEA_BLOOM -> HarmonicAttractorPatterns.buildRhodoneaBloom(rng, field, safeIntricacy, safeAspect)
    ArtPattern.EPITROCHOID_CROWN -> HarmonicAttractorPatterns.buildEpitrochoidCrown(rng, field, safeIntricacy, safeAspect)
    ArtPattern.SRI_YANTRA_TRIANGLES -> HarmonicAttractorPatterns.buildSriYantraTriangles(rng, field, safeIntricacy, safeAspect)

    // 73-80: Vaulted & Lattice
    ArtPattern.CALATRAVA_RIBS -> VaultedLatticePatterns.buildCalatravaRibs(rng, field, safeIntricacy, safeAspect)
    ArtPattern.HEXAGONAL_BASALT -> VaultedLatticePatterns.buildHexagonalBasalt(rng, field, safeIntricacy, safeAspect)
    ArtPattern.METABOLIST_CAPSULES -> VaultedLatticePatterns.buildMetabolistCapsules(rng, field, safeIntricacy, safeAspect)
    ArtPattern.DIAGRID_SKYSCRAPER -> VaultedLatticePatterns.buildDiagridSkyscraper(rng, field, safeIntricacy, safeAspect)
    ArtPattern.LAVES_FLORET -> VaultedLatticePatterns.buildLavesFloret(rng, field, safeIntricacy, safeAspect)
    ArtPattern.KITE_DART_STAR -> VaultedLatticePatterns.buildKiteDartStar(rng, field, safeIntricacy, safeAspect)
    ArtPattern.TRIANGULAR_GYROID -> VaultedLatticePatterns.buildTriangularGyroid(rng, field, safeIntricacy, safeAspect)
    ArtPattern.GOSPER_FLOWSNAKE -> VaultedLatticePatterns.buildGosperFlowsnake(rng, field, safeIntricacy, safeAspect)
}
```

---

## 5. Cross-Platform Parity Checklist

When implementing these patterns on a new platform, verify that:
1. **PRNG Reproducibility**: Seeding `Random(seed)` with identical 64-bit integer seeds produces identical pseudo-random sequences (Kotlin uses a standard 48-bit linear congruential generator equivalent to Java `java.util.Random`).
2. **Aspect Ratio Clamping**: Clamped to `[0.45, 3.2]`.
3. **Intricacy Clamping**: Clamped to `[0.15, 1.0]`.
4. **Drawing Rule**: For any primitive, it is rendered if and only if:
   $$\text{primitive.threshold} \ge \text{targetPitchBlackRatio}$$
5. **Non-Black Shading Floor**: Shaded non-black colors that evaluate to `(0, 0, 0)` under dark gradients must be clamped to `0xFF000004` so colored geometry is never counted as true `#000000` OLED background.

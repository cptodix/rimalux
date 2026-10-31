package com.example.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.R

/**
 * Maps pure, platform-agnostic [PatternCategory] and [ArtPattern] definitions from `com.example.engine`
 * onto localized Android string resources and pre-rendered preview drawables.
 *
 * Keeping these `@StringRes` and `@DrawableRes` mappings in `com.example.model` ensures
 * `com.example.engine` remains 100% free of `com.example.R` and `androidx.annotation.*`.
 */
@get:StringRes
val PatternCategory.labelRes: Int
    get() = when (this) {
        PatternCategory.FLOW_WAVES -> R.string.cat_flow
        PatternCategory.SACRED_SYMMETRY -> R.string.cat_symmetry
        PatternCategory.ARCHITECTURAL -> R.string.cat_architectural
        PatternCategory.TESSELLATION -> R.string.cat_tessellation
    }

@get:StringRes
val ArtPattern.titleRes: Int
    get() = when (this) {
        ArtPattern.SILK_RIBBONS -> R.string.pattern_silk_ribbons
        ArtPattern.TOPO_CONTOURS -> R.string.pattern_topo_contours
        ArtPattern.HORIZON_WAVES -> R.string.pattern_horizon_waves
        ArtPattern.LISSAJOUS_HARMONIC -> R.string.pattern_lissajous_harmonic
        ArtPattern.LOGARITHMIC_NAUTILUS -> R.string.pattern_logarithmic_nautilus
        ArtPattern.CHLADNI_CYMATICS -> R.string.pattern_chladni_cymatics
        ArtPattern.MOIRE_INTERFERENCE -> R.string.pattern_moire_interference
        ArtPattern.VECTOR_MAGNETIC_DIPOLE -> R.string.pattern_magnetic_dipole
        ArtPattern.AURORA_CURTAINS -> R.string.pattern_aurora_curtains
        ArtPattern.SHATTERED_GEODE -> R.string.pattern_shattered_geode
        ArtPattern.OP_ART_GRAVITY -> R.string.pattern_op_art_gravity
        ArtPattern.LORENZ_ATTRACTOR -> R.string.pattern_lorenz_attractor
        ArtPattern.FOURIER_EPICYCLES -> R.string.pattern_fourier_epicycles
        ArtPattern.KELVIN_VORTEX -> R.string.pattern_kelvin_vortex
        ArtPattern.CAUSTIC_WATER -> R.string.pattern_caustic_water
        ArtPattern.THOMAS_ATTRACTOR -> R.string.pattern_thomas_attractor
        ArtPattern.AIRY_WAVEFRONT -> R.string.pattern_airy_wavefront
        ArtPattern.ROSSLER_SPIRAL -> R.string.pattern_rossler_spiral
        ArtPattern.CASSINI_LEMNISCATE -> R.string.pattern_cassini_lemniscate
        ArtPattern.CLIFFORD_ATTRACTOR -> R.string.pattern_clifford_attractor
        ArtPattern.FARADAY_RIPPLES -> R.string.pattern_faraday_ripples
        ArtPattern.BERNOULLI_SPIRALS -> R.string.pattern_bernoulli_spirals
        ArtPattern.SOLITON_TRAINS -> R.string.pattern_soliton_trains

        ArtPattern.SACRED_MANDALA -> R.string.pattern_sacred_mandala
        ArtPattern.GUILLOCHE_ROSETTE -> R.string.pattern_guilloche_rosette
        ArtPattern.FIBONACCI_LOTUS -> R.string.pattern_fibonacci_lotus
        ArtPattern.GIRIH_STARWORK -> R.string.pattern_girih_starwork
        ArtPattern.HARMONIC_CARDIOID -> R.string.pattern_harmonic_cardioid
        ArtPattern.APOLLONIAN_CIRCLES -> R.string.pattern_apollonian_circles
        ArtPattern.KALEIDOSCOPE_PRISM -> R.string.pattern_kaleidoscope_prism
        ArtPattern.HYPERBOLIC_POINCARE -> R.string.pattern_hyperbolic_poincare
        ArtPattern.SUPERELLIPSE_NEST -> R.string.pattern_superellipse_nest
        ArtPattern.MAURER_ROSE -> R.string.pattern_maurer_rose
        ArtPattern.SUPERFORMULA_BLOOM -> R.string.pattern_superformula_bloom
        ArtPattern.FERMAT_PARABOLIC -> R.string.pattern_fermat_parabolic
        ArtPattern.METATRON_POLYHEDRA -> R.string.pattern_metatron_polyhedra
        ArtPattern.DELTOID_ASTROID -> R.string.pattern_deltoid_astroid
        ArtPattern.ZELLIJ_KHATAM -> R.string.pattern_zellij_khatam
        ArtPattern.VESICA_LENSES -> R.string.pattern_vesica_lenses
        ArtPattern.TRISKELION_SPIRALS -> R.string.pattern_triskelion_spirals
        ArtPattern.ENNEAGRAM_STAR -> R.string.pattern_enneagram_star
        ArtPattern.RHODONEA_BLOOM -> R.string.pattern_rhodonea_bloom
        ArtPattern.EPITROCHOID_CROWN -> R.string.pattern_epitrochoid_crown
        ArtPattern.SRI_YANTRA_TRIANGLES -> R.string.pattern_sri_yantra_triangles

        ArtPattern.ISOMETRIC_MONUMENT -> R.string.pattern_isometric_monument
        ArtPattern.BAUHAUS_CONSTRUCT -> R.string.pattern_bauhaus_construct
        ArtPattern.ART_DECO_SUNBURST -> R.string.pattern_art_deco_sunburst
        ArtPattern.ORIGAMI_MIURA -> R.string.pattern_origami_miura
        ArtPattern.KUMIKO_WOODWORK -> R.string.pattern_kumiko_woodwork
        ArtPattern.GEODESIC_DOME -> R.string.pattern_geodesic_dome
        ArtPattern.PERSPECTIVE_COLONNADE -> R.string.pattern_perspective_colonnade
        ArtPattern.DE_STIJL_MONDRIAN -> R.string.pattern_de_stijl_mondrian
        ArtPattern.BRUTALIST_ZIGGURAT -> R.string.pattern_brutalist_ziggurat
        ArtPattern.ESCHER_STAIRCASES -> R.string.pattern_escher_staircases
        ArtPattern.KINETIC_FACADE -> R.string.pattern_kinetic_facade
        ArtPattern.PANTHEON_OCULUS -> R.string.pattern_pantheon_oculus
        ArtPattern.SUPREMATIST_PLANES -> R.string.pattern_suprematist_planes
        ArtPattern.CALATRAVA_RIBS -> R.string.pattern_calatrava_ribs
        ArtPattern.HEXAGONAL_BASALT -> R.string.pattern_hexagonal_basalt
        ArtPattern.METABOLIST_CAPSULES -> R.string.pattern_metabolist_capsules
        ArtPattern.DIAGRID_SKYSCRAPER -> R.string.pattern_diagrid_skyscraper

        ArtPattern.CRYSTAL_VORONOI -> R.string.pattern_crystal_voronoi
        ArtPattern.PENROSE_QUASICRYSTAL -> R.string.pattern_penrose_quasicrystal
        ArtPattern.FRACTAL_LATTICE -> R.string.pattern_fractal_lattice
        ArtPattern.VORONOI_STAINED_GLASS -> R.string.pattern_voronoi_stained_glass
        ArtPattern.HEX_HONEYCOMB_PRISM -> R.string.pattern_hex_honeycomb_prism
        ArtPattern.TRUCHET_LABYRINTH -> R.string.pattern_truchet_labyrinth
        ArtPattern.CELTIC_INTERLACE -> R.string.pattern_celtic_interlace
        ArtPattern.HILBERT_FRACTAL -> R.string.pattern_hilbert_fractal
        ArtPattern.DRAGON_CURVE -> R.string.pattern_dragon_curve
        ArtPattern.CAIRO_PENTAGONAL -> R.string.pattern_cairo_pentagonal
        ArtPattern.KAGOME_TRIHEXAGONAL -> R.string.pattern_kagome_trihexagonal
        ArtPattern.SIERPINSKI_PRISM -> R.string.pattern_sierpinski_prism
        ArtPattern.ARCHIMEDEAN_SNUB -> R.string.pattern_archimedean_snub
        ArtPattern.AMMANN_QUASICRYSTAL -> R.string.pattern_ammann_quasicrystal
        ArtPattern.PYTHAGORAS_CANOPY -> R.string.pattern_pythagoras_canopy
        ArtPattern.LAVES_FLORET -> R.string.pattern_laves_floret
        ArtPattern.KITE_DART_STAR -> R.string.pattern_kite_dart_star
        ArtPattern.TRIANGULAR_GYROID -> R.string.pattern_triangular_gyroid
        ArtPattern.GOSPER_FLOWSNAKE -> R.string.pattern_gosper_flowsnake
    }

@get:StringRes
val ArtPattern.subtitleRes: Int
    get() = when (this) {
        ArtPattern.SILK_RIBBONS -> R.string.pattern_silk_ribbons_sub
        ArtPattern.TOPO_CONTOURS -> R.string.pattern_topo_contours_sub
        ArtPattern.HORIZON_WAVES -> R.string.pattern_horizon_waves_sub
        ArtPattern.LISSAJOUS_HARMONIC -> R.string.pattern_lissajous_harmonic_sub
        ArtPattern.LOGARITHMIC_NAUTILUS -> R.string.pattern_logarithmic_nautilus_sub
        ArtPattern.CHLADNI_CYMATICS -> R.string.pattern_chladni_cymatics_sub
        ArtPattern.MOIRE_INTERFERENCE -> R.string.pattern_moire_interference_sub
        ArtPattern.VECTOR_MAGNETIC_DIPOLE -> R.string.pattern_magnetic_dipole_sub
        ArtPattern.AURORA_CURTAINS -> R.string.pattern_aurora_curtains_sub
        ArtPattern.SHATTERED_GEODE -> R.string.pattern_shattered_geode_sub
        ArtPattern.OP_ART_GRAVITY -> R.string.pattern_op_art_gravity_sub
        ArtPattern.LORENZ_ATTRACTOR -> R.string.pattern_lorenz_attractor_sub
        ArtPattern.FOURIER_EPICYCLES -> R.string.pattern_fourier_epicycles_sub
        ArtPattern.KELVIN_VORTEX -> R.string.pattern_kelvin_vortex_sub
        ArtPattern.CAUSTIC_WATER -> R.string.pattern_caustic_water_sub
        ArtPattern.THOMAS_ATTRACTOR -> R.string.pattern_thomas_attractor_sub
        ArtPattern.AIRY_WAVEFRONT -> R.string.pattern_airy_wavefront_sub
        ArtPattern.ROSSLER_SPIRAL -> R.string.pattern_rossler_spiral_sub
        ArtPattern.CASSINI_LEMNISCATE -> R.string.pattern_cassini_lemniscate_sub
        ArtPattern.CLIFFORD_ATTRACTOR -> R.string.pattern_clifford_attractor_sub
        ArtPattern.FARADAY_RIPPLES -> R.string.pattern_faraday_ripples_sub
        ArtPattern.BERNOULLI_SPIRALS -> R.string.pattern_bernoulli_spirals_sub
        ArtPattern.SOLITON_TRAINS -> R.string.pattern_soliton_trains_sub

        ArtPattern.SACRED_MANDALA -> R.string.pattern_sacred_mandala_sub
        ArtPattern.GUILLOCHE_ROSETTE -> R.string.pattern_guilloche_rosette_sub
        ArtPattern.FIBONACCI_LOTUS -> R.string.pattern_fibonacci_lotus_sub
        ArtPattern.GIRIH_STARWORK -> R.string.pattern_girih_starwork_sub
        ArtPattern.HARMONIC_CARDIOID -> R.string.pattern_harmonic_cardioid_sub
        ArtPattern.APOLLONIAN_CIRCLES -> R.string.pattern_apollonian_circles_sub
        ArtPattern.KALEIDOSCOPE_PRISM -> R.string.pattern_kaleidoscope_prism_sub
        ArtPattern.HYPERBOLIC_POINCARE -> R.string.pattern_hyperbolic_poincare_sub
        ArtPattern.SUPERELLIPSE_NEST -> R.string.pattern_superellipse_nest_sub
        ArtPattern.MAURER_ROSE -> R.string.pattern_maurer_rose_sub
        ArtPattern.SUPERFORMULA_BLOOM -> R.string.pattern_superformula_bloom_sub
        ArtPattern.FERMAT_PARABOLIC -> R.string.pattern_fermat_parabolic_sub
        ArtPattern.METATRON_POLYHEDRA -> R.string.pattern_metatron_polyhedra_sub
        ArtPattern.DELTOID_ASTROID -> R.string.pattern_deltoid_astroid_sub
        ArtPattern.ZELLIJ_KHATAM -> R.string.pattern_zellij_khatam_sub
        ArtPattern.VESICA_LENSES -> R.string.pattern_vesica_lenses_sub
        ArtPattern.TRISKELION_SPIRALS -> R.string.pattern_triskelion_spirals_sub
        ArtPattern.ENNEAGRAM_STAR -> R.string.pattern_enneagram_star_sub
        ArtPattern.RHODONEA_BLOOM -> R.string.pattern_rhodonea_bloom_sub
        ArtPattern.EPITROCHOID_CROWN -> R.string.pattern_epitrochoid_crown_sub
        ArtPattern.SRI_YANTRA_TRIANGLES -> R.string.pattern_sri_yantra_triangles_sub

        ArtPattern.ISOMETRIC_MONUMENT -> R.string.pattern_isometric_monument_sub
        ArtPattern.BAUHAUS_CONSTRUCT -> R.string.pattern_bauhaus_construct_sub
        ArtPattern.ART_DECO_SUNBURST -> R.string.pattern_art_deco_sunburst_sub
        ArtPattern.ORIGAMI_MIURA -> R.string.pattern_origami_miura_sub
        ArtPattern.KUMIKO_WOODWORK -> R.string.pattern_kumiko_woodwork_sub
        ArtPattern.GEODESIC_DOME -> R.string.pattern_geodesic_dome_sub
        ArtPattern.PERSPECTIVE_COLONNADE -> R.string.pattern_perspective_colonnade_sub
        ArtPattern.DE_STIJL_MONDRIAN -> R.string.pattern_de_stijl_mondrian_sub
        ArtPattern.BRUTALIST_ZIGGURAT -> R.string.pattern_brutalist_ziggurat_sub
        ArtPattern.ESCHER_STAIRCASES -> R.string.pattern_escher_staircases_sub
        ArtPattern.KINETIC_FACADE -> R.string.pattern_kinetic_facade_sub
        ArtPattern.PANTHEON_OCULUS -> R.string.pattern_pantheon_oculus_sub
        ArtPattern.SUPREMATIST_PLANES -> R.string.pattern_suprematist_planes_sub
        ArtPattern.CALATRAVA_RIBS -> R.string.pattern_calatrava_ribs_sub
        ArtPattern.HEXAGONAL_BASALT -> R.string.pattern_hexagonal_basalt_sub
        ArtPattern.METABOLIST_CAPSULES -> R.string.pattern_metabolist_capsules_sub
        ArtPattern.DIAGRID_SKYSCRAPER -> R.string.pattern_diagrid_skyscraper_sub

        ArtPattern.CRYSTAL_VORONOI -> R.string.pattern_crystal_voronoi_sub
        ArtPattern.PENROSE_QUASICRYSTAL -> R.string.pattern_penrose_quasicrystal_sub
        ArtPattern.FRACTAL_LATTICE -> R.string.pattern_fractal_lattice_sub
        ArtPattern.VORONOI_STAINED_GLASS -> R.string.pattern_voronoi_stained_glass_sub
        ArtPattern.HEX_HONEYCOMB_PRISM -> R.string.pattern_hex_honeycomb_prism_sub
        ArtPattern.TRUCHET_LABYRINTH -> R.string.pattern_truchet_labyrinth_sub
        ArtPattern.CELTIC_INTERLACE -> R.string.pattern_celtic_interlace_sub
        ArtPattern.HILBERT_FRACTAL -> R.string.pattern_hilbert_fractal_sub
        ArtPattern.DRAGON_CURVE -> R.string.pattern_dragon_curve_sub
        ArtPattern.CAIRO_PENTAGONAL -> R.string.pattern_cairo_pentagonal_sub
        ArtPattern.KAGOME_TRIHEXAGONAL -> R.string.pattern_kagome_trihexagonal_sub
        ArtPattern.SIERPINSKI_PRISM -> R.string.pattern_sierpinski_prism_sub
        ArtPattern.ARCHIMEDEAN_SNUB -> R.string.pattern_archimedean_snub_sub
        ArtPattern.AMMANN_QUASICRYSTAL -> R.string.pattern_ammann_quasicrystal_sub
        ArtPattern.PYTHAGORAS_CANOPY -> R.string.pattern_pythagoras_canopy_sub
        ArtPattern.LAVES_FLORET -> R.string.pattern_laves_floret_sub
        ArtPattern.KITE_DART_STAR -> R.string.pattern_kite_dart_star_sub
        ArtPattern.TRIANGULAR_GYROID -> R.string.pattern_triangular_gyroid_sub
        ArtPattern.GOSPER_FLOWSNAKE -> R.string.pattern_gosper_flowsnake_sub
    }

@get:DrawableRes
val ArtPattern.previewRes: Int
    get() = when (this) {
        ArtPattern.SILK_RIBBONS -> R.drawable.pattern_preview_silk_ribbons
        ArtPattern.TOPO_CONTOURS -> R.drawable.pattern_preview_topo_contours
        ArtPattern.HORIZON_WAVES -> R.drawable.pattern_preview_horizon_waves
        ArtPattern.LISSAJOUS_HARMONIC -> R.drawable.pattern_preview_lissajous_harmonic
        ArtPattern.LOGARITHMIC_NAUTILUS -> R.drawable.pattern_preview_logarithmic_nautilus
        ArtPattern.CHLADNI_CYMATICS -> R.drawable.pattern_preview_chladni_cymatics
        ArtPattern.MOIRE_INTERFERENCE -> R.drawable.pattern_preview_moire_interference
        ArtPattern.VECTOR_MAGNETIC_DIPOLE -> R.drawable.pattern_preview_magnetic_dipole
        ArtPattern.AURORA_CURTAINS -> R.drawable.pattern_preview_aurora_curtains
        ArtPattern.SHATTERED_GEODE -> R.drawable.pattern_preview_shattered_geode
        ArtPattern.OP_ART_GRAVITY -> R.drawable.pattern_preview_op_art_gravity
        ArtPattern.LORENZ_ATTRACTOR -> R.drawable.pattern_preview_lorenz_attractor
        ArtPattern.FOURIER_EPICYCLES -> R.drawable.pattern_preview_fourier_epicycles
        ArtPattern.KELVIN_VORTEX -> R.drawable.pattern_preview_kelvin_vortex
        ArtPattern.CAUSTIC_WATER -> R.drawable.pattern_preview_caustic_water
        ArtPattern.THOMAS_ATTRACTOR -> R.drawable.pattern_preview_thomas_attractor
        ArtPattern.AIRY_WAVEFRONT -> R.drawable.pattern_preview_airy_wavefront
        ArtPattern.ROSSLER_SPIRAL -> R.drawable.pattern_preview_rossler_spiral
        ArtPattern.CASSINI_LEMNISCATE -> R.drawable.pattern_preview_cassini_lemniscate
        ArtPattern.CLIFFORD_ATTRACTOR -> R.drawable.pattern_preview_clifford_attractor
        ArtPattern.FARADAY_RIPPLES -> R.drawable.pattern_preview_faraday_ripples
        ArtPattern.BERNOULLI_SPIRALS -> R.drawable.pattern_preview_bernoulli_spirals
        ArtPattern.SOLITON_TRAINS -> R.drawable.pattern_preview_soliton_trains

        ArtPattern.SACRED_MANDALA -> R.drawable.pattern_preview_sacred_mandala
        ArtPattern.GUILLOCHE_ROSETTE -> R.drawable.pattern_preview_guilloche_rosette
        ArtPattern.FIBONACCI_LOTUS -> R.drawable.pattern_preview_fibonacci_lotus
        ArtPattern.GIRIH_STARWORK -> R.drawable.pattern_preview_girih_starwork
        ArtPattern.HARMONIC_CARDIOID -> R.drawable.pattern_preview_harmonic_cardioid
        ArtPattern.APOLLONIAN_CIRCLES -> R.drawable.pattern_preview_apollonian_circles
        ArtPattern.KALEIDOSCOPE_PRISM -> R.drawable.pattern_preview_kaleidoscope_prism
        ArtPattern.HYPERBOLIC_POINCARE -> R.drawable.pattern_preview_hyperbolic_poincare
        ArtPattern.SUPERELLIPSE_NEST -> R.drawable.pattern_preview_superellipse_nest
        ArtPattern.MAURER_ROSE -> R.drawable.pattern_preview_maurer_rose
        ArtPattern.SUPERFORMULA_BLOOM -> R.drawable.pattern_preview_superformula_bloom
        ArtPattern.FERMAT_PARABOLIC -> R.drawable.pattern_preview_fermat_parabolic
        ArtPattern.METATRON_POLYHEDRA -> R.drawable.pattern_preview_metatron_polyhedra
        ArtPattern.DELTOID_ASTROID -> R.drawable.pattern_preview_deltoid_astroid
        ArtPattern.ZELLIJ_KHATAM -> R.drawable.pattern_preview_zellij_khatam
        ArtPattern.VESICA_LENSES -> R.drawable.pattern_preview_vesica_lenses
        ArtPattern.TRISKELION_SPIRALS -> R.drawable.pattern_preview_triskelion_spirals
        ArtPattern.ENNEAGRAM_STAR -> R.drawable.pattern_preview_enneagram_star
        ArtPattern.RHODONEA_BLOOM -> R.drawable.pattern_preview_rhodonea_bloom
        ArtPattern.EPITROCHOID_CROWN -> R.drawable.pattern_preview_epitrochoid_crown
        ArtPattern.SRI_YANTRA_TRIANGLES -> R.drawable.pattern_preview_sri_yantra_triangles

        ArtPattern.ISOMETRIC_MONUMENT -> R.drawable.pattern_preview_isometric_monument
        ArtPattern.BAUHAUS_CONSTRUCT -> R.drawable.pattern_preview_bauhaus_construct
        ArtPattern.ART_DECO_SUNBURST -> R.drawable.pattern_preview_art_deco_sunburst
        ArtPattern.ORIGAMI_MIURA -> R.drawable.pattern_preview_origami_miura
        ArtPattern.KUMIKO_WOODWORK -> R.drawable.pattern_preview_kumiko_woodwork
        ArtPattern.GEODESIC_DOME -> R.drawable.pattern_preview_geodesic_dome
        ArtPattern.PERSPECTIVE_COLONNADE -> R.drawable.pattern_preview_perspective_colonnade
        ArtPattern.DE_STIJL_MONDRIAN -> R.drawable.pattern_preview_de_stijl_mondrian
        ArtPattern.BRUTALIST_ZIGGURAT -> R.drawable.pattern_preview_brutalist_ziggurat
        ArtPattern.ESCHER_STAIRCASES -> R.drawable.pattern_preview_escher_staircases
        ArtPattern.KINETIC_FACADE -> R.drawable.pattern_preview_kinetic_facade
        ArtPattern.PANTHEON_OCULUS -> R.drawable.pattern_preview_pantheon_oculus
        ArtPattern.SUPREMATIST_PLANES -> R.drawable.pattern_preview_suprematist_planes
        ArtPattern.CALATRAVA_RIBS -> R.drawable.pattern_preview_calatrava_ribs
        ArtPattern.HEXAGONAL_BASALT -> R.drawable.pattern_preview_hexagonal_basalt
        ArtPattern.METABOLIST_CAPSULES -> R.drawable.pattern_preview_metabolist_capsules
        ArtPattern.DIAGRID_SKYSCRAPER -> R.drawable.pattern_preview_diagrid_skyscraper

        ArtPattern.CRYSTAL_VORONOI -> R.drawable.pattern_preview_crystal_voronoi
        ArtPattern.PENROSE_QUASICRYSTAL -> R.drawable.pattern_preview_penrose_quasicrystal
        ArtPattern.FRACTAL_LATTICE -> R.drawable.pattern_preview_fractal_lattice
        ArtPattern.VORONOI_STAINED_GLASS -> R.drawable.pattern_preview_voronoi_stained_glass
        ArtPattern.HEX_HONEYCOMB_PRISM -> R.drawable.pattern_preview_hex_honeycomb_prism
        ArtPattern.TRUCHET_LABYRINTH -> R.drawable.pattern_preview_truchet_labyrinth
        ArtPattern.CELTIC_INTERLACE -> R.drawable.pattern_preview_celtic_interlace
        ArtPattern.HILBERT_FRACTAL -> R.drawable.pattern_preview_hilbert_fractal
        ArtPattern.DRAGON_CURVE -> R.drawable.pattern_preview_dragon_curve
        ArtPattern.CAIRO_PENTAGONAL -> R.drawable.pattern_preview_cairo_pentagonal
        ArtPattern.KAGOME_TRIHEXAGONAL -> R.drawable.pattern_preview_kagome_trihexagonal
        ArtPattern.SIERPINSKI_PRISM -> R.drawable.pattern_preview_sierpinski_prism
        ArtPattern.ARCHIMEDEAN_SNUB -> R.drawable.pattern_preview_archimedean_snub
        ArtPattern.AMMANN_QUASICRYSTAL -> R.drawable.pattern_preview_ammann_quasicrystal
        ArtPattern.PYTHAGORAS_CANOPY -> R.drawable.pattern_preview_pythagoras_canopy
        ArtPattern.LAVES_FLORET -> R.drawable.pattern_preview_laves_floret
        ArtPattern.KITE_DART_STAR -> R.drawable.pattern_preview_kite_dart_star
        ArtPattern.TRIANGULAR_GYROID -> R.drawable.pattern_preview_triangular_gyroid
        ArtPattern.GOSPER_FLOWSNAKE -> R.drawable.pattern_preview_gosper_flowsnake
    }

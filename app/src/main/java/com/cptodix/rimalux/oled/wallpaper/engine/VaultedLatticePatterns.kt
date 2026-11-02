package com.cptodix.rimalux.oled.wallpaper.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure mathematical generators for Patterns #73 - #80 (4 Architectural + 4 Tessellation).
 * Strictly `kotlin.*` standard library only.
 */
internal object VaultedLatticePatterns {

    private const val TWO_PI = (2.0 * PI).toFloat()

    // 73. Vaulted Parabolas (Calatrava Sweeping Parabolic Rib-Arch Colonnade)
    fun buildCalatravaRibs(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val vaults = 3
        val ribsPerVault = (12 + (intricacy * 8f).roundToInt()).coerceIn(12, 20)
        val segsPerRib = 10
        val cells = ArrayList<DraftCell>(vaults * ribsPerVault * segsPerRib)

        for (v in 0 until vaults) {
            val baseY = aspect * (0.28f + v * 0.34f)
            val crownHeight = aspect * (0.24f + 0.06f * sin(v * 1.4f + rng.nextFloat()))
            val apexShift = 0.5f + (rng.nextFloat() - 0.5f) * 0.16f

            for (rib in 0 until ribsPerVault) {
                val uFoot = -0.06f + 1.12f * (rib.toFloat() / (ribsPerVault - 1).coerceAtLeast(1))
                val foot = Vec2(uFoot, baseY + 0.08f * aspect * abs(uFoot - 0.5f))
                val crownX = apexShift + (uFoot - 0.5f) * 0.28f
                val crown = Vec2(crownX, baseY - crownHeight * (1f - 0.45f * (uFoot - 0.5f) * (uFoot - 0.5f)))
                val ctrl = Vec2((foot.x + crown.x) * 0.5f + (uFoot - 0.5f) * 0.18f, baseY - crownHeight * 0.72f)

                for (s in 0 until segsPerRib) {
                    val t0 = s.toFloat() / segsPerRib
                    val t1 = (s + 1).toFloat() / segsPerRib
                    fun bezier(t: Float): Vec2 {
                        val om = 1f - t
                        return Vec2(
                            om * om * foot.x + 2f * om * t * ctrl.x + t * t * crown.x,
                            om * om * foot.y + 2f * om * t * ctrl.y + t * t * crown.y
                        )
                    }
                    val p0 = bezier(t0)
                    val p1 = bezier(t1)
                    val mid = Vec2((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                    val taper = 0.015f * (1f - 0.48f * t0)
                    val area = (hypot(p1.x - p0.x, p1.y - p0.y) * taper).coerceAtLeast(0.00008f)
                    val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolylineRibbonPrimitive(
                                    points = listOf(p0, p1),
                                    strokeWidthNorm = taper,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = (v * 2 + rib % 3),
                                    secondaryColorIndex = (v * 2 + 1),
                                    shadeFactor = 0.84f + 0.34f * t0,
                                    gradientAngleRad = atan2(p1.y - p0.y, p1.x - p0.x)
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 74. Basalt Columns (Axonometric Stepped Hexagonal Pillars)
    fun buildHexagonalBasalt(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (8 + (intricacy * 5f).roundToInt()).coerceIn(8, 13)
        val hexR = 1f / (cols * 1.55f)
        val dx = hexR * 1.7320508f
        val dy = hexR * 1.25f
        val rows = ((aspect / dy).roundToInt() + 4).coerceAtLeast(12)
        val phase = rng.nextFloat() * TWO_PI
        val cells = ArrayList<DraftCell>(rows * cols)

        // Back-to-front painter's order (top rows first so front pillars overlap cleanly)
        for (r in -1..rows) {
            val baseY = r * dy
            val xOff = if (r.mod(2) == 1) dx * 0.5f else 0f
            for (c in -1..cols + 1) {
                val cx = c * dx + xOff
                if (cx < -0.14f || cx > 1.14f) continue

                val elevWave = 0.5f + 0.5f * sin(cx * 5.4f + (baseY / aspect) * 4.8f + phase)
                val colH = hexR * (0.55f + 1.35f * elevWave)
                val topY = baseY - colH * 0.45f
                if (topY < -0.18f || baseY > aspect + 0.22f) continue

                val rIn = hexR * 0.90f
                val cap = List(6) { k ->
                    val a = -PI.toFloat() * 0.5f + k * (PI.toFloat() / 3f)
                    Vec2(cx + rIn * cos(a), topY + rIn * 0.62f * sin(a))
                }
                // Lower-left face: vertices 4 -> 3 dropped by colH; lower-right face: vertices 3 -> 2 dropped by colH
                val vLeft = cap[4]
                val vBottom = cap[3]
                val vRight = cap[2]
                val leftFace = listOf(
                    vLeft,
                    vBottom,
                    Vec2(vBottom.x, vBottom.y + colH),
                    Vec2(vLeft.x, vLeft.y + colH)
                )
                val rightFace = listOf(
                    vBottom,
                    vRight,
                    Vec2(vRight.x, vRight.y + colH),
                    Vec2(vBottom.x, vBottom.y + colH)
                )

                val area = (2.1f * rIn * rIn + rIn * colH).coerceAtLeast(0.00012f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), topY.coerceIn(0f, aspect), rng)
                val cIdx = abs(r * 2 + c) % 5

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = leftFace,
                                threshold = threshold,
                                areaWeight = area * 0.34f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.72f,
                                gradientAngleRad = 1.57f,
                                strokeSeamNorm = 0.0014f
                            ),
                            PolygonPrimitive(
                                vertices = rightFace,
                                threshold = threshold,
                                areaWeight = area * 0.32f,
                                colorIndex = cIdx + 1,
                                secondaryColorIndex = cIdx + 2,
                                shadeFactor = 0.54f,
                                gradientAngleRad = 1.57f,
                                strokeSeamNorm = 0.0014f
                            ),
                            PolygonPrimitive(
                                vertices = cap,
                                threshold = threshold,
                                areaWeight = area * 0.34f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 2,
                                shadeFactor = 1.20f,
                                gradientAngleRad = -0.52f,
                                strokeSeamNorm = 0.0016f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 75. Capsule Towers (Metabolist Cantilevered Oculus Pods)
    fun buildMetabolistCapsules(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val towers = 4
        val floors = (10 + (intricacy * 6f).roundToInt()).coerceIn(10, 16)
        val towerSpacing = 1f / towers.toFloat()
        val floorH = aspect / floors.toFloat()
        val cells = ArrayList<DraftCell>(towers * floors * 2)

        for (t in 0 until towers) {
            val spineX = (t + 0.5f) * towerSpacing
            for (f in 0 until floors) {
                val cy = (f + 0.5f) * floorH
                for (side in -1..1 step 2) {
                    if (rng.nextFloat() < 0.14f) continue
                    val podW = towerSpacing * (0.38f + 0.08f * ((f + t) % 2))
                    val podH = floorH * 0.76f
                    val podCx = spineX + side * podW * 0.54f
                    val podCy = cy + side * floorH * 0.12f

                    val x0 = podCx - podW * 0.46f
                    val x1 = podCx + podW * 0.46f
                    val y0 = podCy - podH * 0.46f
                    val y1 = podCy + podH * 0.46f
                    val bevel = podW * 0.14f

                    val face = listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x1, y1), Vec2(x0, y1))
                    val sideFlank = if (side > 0) {
                        listOf(Vec2(x1, y0), Vec2(x1 + bevel, y0 + bevel * 0.6f), Vec2(x1 + bevel, y1 + bevel * 0.6f), Vec2(x1, y1))
                    } else {
                        listOf(Vec2(x0 - bevel, y0 + bevel * 0.6f), Vec2(x0, y0), Vec2(x0, y1), Vec2(x0 - bevel, y1 + bevel * 0.6f))
                    }
                    val oculusR = min(podW, podH) * 0.28f
                    val area = (podW * podH * 0.92f).coerceAtLeast(0.00012f)
                    val score = field.evaluate(podCx.coerceIn(0f, 1f), podCy.coerceIn(0f, aspect), rng)
                    val cIdx = abs(t * 2 + f + (if (side > 0) 1 else 0)) % 6

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = sideFlank,
                                    threshold = threshold,
                                    areaWeight = area * 0.20f,
                                    colorIndex = cIdx + 1,
                                    secondaryColorIndex = cIdx,
                                    shadeFactor = 0.62f,
                                    gradientAngleRad = 1.57f,
                                    strokeSeamNorm = 0.0012f
                                ),
                                PolygonPrimitive(
                                    vertices = face,
                                    threshold = threshold,
                                    areaWeight = area * 0.56f,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 1,
                                    shadeFactor = 0.96f,
                                    gradientAngleRad = 0.78f,
                                    strokeSeamNorm = 0.0016f
                                ),
                                ArcSectorPrimitive(
                                    center = Vec2(podCx, podCy),
                                    innerRadius = oculusR * 0.45f,
                                    outerRadius = oculusR,
                                    startAngleDeg = 0f,
                                    sweepAngleDeg = 360f,
                                    threshold = threshold,
                                    areaWeight = area * 0.24f,
                                    colorIndex = cIdx + 2,
                                    secondaryColorIndex = cIdx + 3,
                                    shadeFactor = 1.24f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 76. Diagrid Atrium (Tapered Structural Diamond Exoskeleton)
    fun buildDiagridSkyscraper(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (7 + (intricacy * 4f).roundToInt()).coerceIn(7, 11)
        val rows = (12 + (intricacy * 6f).roundToInt()).coerceIn(12, 18)
        val taperStrength = 0.16f + rng.nextFloat() * 0.14f
        val cells = ArrayList<DraftCell>(cols * rows * 2)

        fun gridNode(r: Int, c: Int): Vec2 {
            val v = r.toFloat() / rows.toFloat()
            val widthScale = 1.06f - taperStrength * (1f - v)
            val u = (c.toFloat() / cols.toFloat()) - 0.5f
            return Vec2(0.5f + u * widthScale, v * aspect)
        }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val pTop = gridNode(r, c)
                val pRight = Vec2(
                    (gridNode(r, c + 1).x + gridNode(r + 1, c + 1).x) * 0.5f,
                    (r + 0.5f) * (aspect / rows)
                )
                val pBottom = gridNode(r + 1, c)
                val pLeft = Vec2(
                    (gridNode(r, c).x + gridNode(r + 1, c).x) * 0.5f - (pRight.x - pTop.x),
                    (r + 0.5f) * (aspect / rows)
                )
                val cx = (pTop.x + pBottom.x) * 0.5f
                val cy = (pTop.y + pBottom.y) * 0.5f
                if (cx < -0.10f || cx > 1.10f) continue

                val shrink = 0.86f
                val outerDiamond = listOf(pTop, pRight, pBottom, pLeft).map { pt ->
                    Vec2(cx + (pt.x - cx) * shrink, cy + (pt.y - cy) * shrink)
                }
                val upperPane = listOf(
                    Vec2(cx + (pTop.x - cx) * 0.54f, cy + (pTop.y - cy) * 0.54f),
                    Vec2(cx + (pRight.x - cx) * 0.54f, cy + (pRight.y - cy) * 0.54f),
                    Vec2(cx + (pLeft.x - cx) * 0.54f, cy + (pLeft.y - cy) * 0.54f)
                )
                val lowerPane = listOf(
                    Vec2(cx + (pRight.x - cx) * 0.54f, cy + (pRight.y - cy) * 0.54f),
                    Vec2(cx + (pBottom.x - cx) * 0.54f, cy + (pBottom.y - cy) * 0.54f),
                    Vec2(cx + (pLeft.x - cx) * 0.54f, cy + (pLeft.y - cy) * 0.54f)
                )

                val wSpan = abs(pRight.x - pLeft.x)
                val hSpan = abs(pBottom.y - pTop.y)
                val area = (0.5f * wSpan * hSpan * shrink * shrink).coerceAtLeast(0.0001f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val cIdx = (r + c * 2) % 5

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = outerDiamond,
                                threshold = threshold,
                                areaWeight = area * 0.58f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.82f,
                                gradientAngleRad = 0.95f,
                                strokeSeamNorm = 0.0018f
                            ),
                            PolygonPrimitive(
                                vertices = upperPane,
                                threshold = threshold,
                                areaWeight = area * 0.21f,
                                colorIndex = cIdx + 2,
                                secondaryColorIndex = cIdx + 3,
                                shadeFactor = 1.22f,
                                gradientAngleRad = -0.78f,
                                strokeSeamNorm = 0.0012f
                            ),
                            PolygonPrimitive(
                                vertices = lowerPane,
                                threshold = threshold,
                                areaWeight = area * 0.21f,
                                colorIndex = cIdx + 1,
                                secondaryColorIndex = cIdx + 2,
                                shadeFactor = 0.66f,
                                gradientAngleRad = 0.78f,
                                strokeSeamNorm = 0.0012f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 77. Floret Pentagons (6-Fold Chiral Laves [3^4.6] Pentagonal Rosettes)
    fun buildLavesFloret(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (4 + (intricacy * 2.5f).roundToInt()).coerceIn(4, 7)
        val spacing = 1f / cols.toFloat()
        val sin60 = 0.8660254f
        val rowStep = spacing * sin60
        val rows = ((aspect / rowStep).roundToInt() + 3).coerceAtLeast(8)
        val chiralSign = if (rng.nextBoolean()) 1f else -1f
        val chiralAngle = chiralSign * kotlin.math.atan(sqrt(3f) / 5f)
        val cells = ArrayList<DraftCell>(rows * cols * 6)

        for (r in -1..rows) {
            val cy = r * rowStep
            val xOff = if (r.mod(2) == 1) spacing * 0.5f else 0f
            for (c in -1..cols + 1) {
                val cx = c * spacing + xOff
                if (cx < -0.18f || cx > 1.18f || cy < -0.18f || cy > aspect + 0.18f) continue

                val rLong = spacing * 0.54f
                val rMid = spacing * 0.38f
                for (petal in 0 until 6) {
                    val a0 = chiralAngle + petal * (PI.toFloat() / 3f)
                    val a1 = a0 + (PI.toFloat() / 3f)
                    val aTip = a0 + (PI.toFloat() / 6f)

                    val vHub = Vec2(cx, cy)
                    val vShoulderA = Vec2(cx + rMid * cos(a0), cy + rMid * sin(a0))
                    val vOuterA = Vec2(cx + rLong * cos(a0 + 0.22f), cy + rLong * sin(a0 + 0.22f))
                    val vTip = Vec2(cx + rLong * 1.05f * cos(aTip), cy + rLong * 1.05f * sin(aTip))
                    val vShoulderB = Vec2(cx + rMid * cos(a1), cy + rMid * sin(a1))

                    val rawPent = listOf(vHub, vShoulderA, vOuterA, vTip, vShoulderB)
                    val px = rawPent.sumOf { it.x.toDouble() }.toFloat() / 5f
                    val py = rawPent.sumOf { it.y.toDouble() }.toFloat() / 5f
                    val shrunkPent = rawPent.map { v ->
                        Vec2(px + (v.x - px) * 0.90f, py + (v.y - py) * 0.90f)
                    }
                    val area = (spacing * spacing * 0.13f).coerceAtLeast(0.00008f)
                    val score = field.evaluate(px.coerceIn(0f, 1f), py.coerceIn(0f, aspect), rng)
                    val cIdx = abs(r + c * 2 + petal) % 6

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = shrunkPent,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 1,
                                    shadeFactor = 0.84f + 0.26f * ((petal % 3) * 0.5f),
                                    gradientAngleRad = aTip,
                                    strokeSeamNorm = 0.0016f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 78. Decagonal Stars (5-Fold Golden Kite & Dart Sunburst Tiling)
    fun buildKiteDartStar(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val phi = 1.618034f
        val center = Vec2(0.5f, aspect * 0.5f)
        val maxR = hypot(0.56f, aspect * 0.56f)
        val baseRot = rng.nextFloat() * TWO_PI
        val step10 = TWO_PI / 10f
        val halfStep = step10 * 0.5f
        val rings = (7 + (intricacy * 4f).roundToInt()).coerceIn(7, 11)
        val cells = ArrayList<DraftCell>(rings * 20)

        for (ring in 0 until rings) {
            val r0 = 0.04f + (maxR - 0.04f) * (ring.toFloat() / rings)
            val r1 = 0.04f + (maxR - 0.04f) * ((ring + 1f) / rings)
            val rSplit = r0 + (r1 - r0) / phi
            val twist = baseRot + (if (ring % 2 == 1) halfStep else 0f)

            for (k in 0 until 10) {
                val aMid = twist + k * step10
                val aLeft = aMid - halfStep
                val aRight = aMid + halfStep

                // Golden Kite (convex 72-72-72-144 quad)
                val kite = listOf(
                    Vec2(center.x + r0 * cos(aMid), center.y + r0 * sin(aMid)),
                    Vec2(center.x + rSplit * cos(aLeft * 0.9f + aMid * 0.1f), center.y + rSplit * sin(aLeft * 0.9f + aMid * 0.1f)),
                    Vec2(center.x + r1 * cos(aMid), center.y + r1 * sin(aMid)),
                    Vec2(center.x + rSplit * cos(aRight * 0.9f + aMid * 0.1f), center.y + rSplit * sin(aRight * 0.9f + aMid * 0.1f))
                )
                // Complementary interstitial Dart-wedge between adjacent kites
                val dartMid = aMid + halfStep
                val dart = listOf(
                    Vec2(center.x + rSplit * cos(aRight * 0.92f + aMid * 0.08f), center.y + rSplit * sin(aRight * 0.92f + aMid * 0.08f)),
                    Vec2(center.x + r1 * cos(aMid), center.y + r1 * sin(aMid)),
                    Vec2(center.x + (rSplit + (r1 - rSplit) * 0.42f) * cos(dartMid), center.y + (rSplit + (r1 - rSplit) * 0.42f) * sin(dartMid)),
                    Vec2(center.x + r1 * cos(aMid + step10), center.y + r1 * sin(aMid + step10))
                )

                for ((qIdx, rawQuad) in listOf(kite, dart).withIndex()) {
                    val qx = rawQuad.sumOf { it.x.toDouble() }.toFloat() * 0.25f
                    val qy = rawQuad.sumOf { it.y.toDouble() }.toFloat() * 0.25f
                    if (qx < -0.14f || qx > 1.14f || qy < -0.14f || qy > aspect + 0.14f) continue

                    val quad = rawQuad.map { pt ->
                        Vec2(qx + (pt.x - qx) * 0.91f, qy + (pt.y - qy) * 0.91f)
                    }
                    val area = ((r1 - r0) * ((r0 + r1) * 0.5f) * step10 * 0.46f).coerceAtLeast(0.00008f)
                    val score = field.evaluate(qx.coerceIn(0f, 1f), qy.coerceIn(0f, aspect), rng)
                    val cIdx = (ring + k % 2 + qIdx * 2) % 6

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = quad,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 1,
                                    shadeFactor = if (qIdx == 0) 1.12f else 0.78f,
                                    gradientAngleRad = aMid,
                                    strokeSeamNorm = 0.0014f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 79. Triangular Bevels (3-Way Isometric Cube-Corner Gyroid Lattice)
    fun buildTriangularGyroid(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (6 + (intricacy * 4f).roundToInt()).coerceIn(6, 10)
        val rHex = 1f / (cols * 1.5f)
        val cos30 = 0.8660254f
        val dx = rHex * cos30 * 2f
        val dy = rHex * 1.5f
        val rows = ((aspect / dy).roundToInt() + 3).coerceAtLeast(10)
        val twistSign = if (rng.nextBoolean()) 1f else -1f
        val cells = ArrayList<DraftCell>(rows * cols * 3)

        for (r in -1..rows) {
            val cy = r * dy
            val xOff = if (r.mod(2) == 1) dx * 0.5f else 0f
            for (c in -1..cols + 1) {
                val cx = c * dx + xOff
                if (cx < -0.15f || cx > 1.15f || cy < -0.15f || cy > aspect + 0.15f) continue

                val rOuter = rHex * 0.92f
                val rInner = rOuter * 0.42f
                for (wing in 0 until 3) {
                    val baseA = -PI.toFloat() * 0.5f + wing * (TWO_PI / 3f)
                    val nextA = baseA + (TWO_PI / 3f)
                    val midA = baseA + (PI.toFloat() / 3f)

                    // L-shaped / chevron isometric bevel ribbon around central hollow triangle
                    val chevron = listOf(
                        Vec2(cx + rInner * cos(baseA + twistSign * 0.25f), cy + rInner * sin(baseA + twistSign * 0.25f)),
                        Vec2(cx + rOuter * cos(baseA), cy + rOuter * sin(baseA)),
                        Vec2(cx + rOuter * cos(midA), cy + rOuter * sin(midA)),
                        Vec2(cx + rInner * cos(nextA + twistSign * 0.25f), cy + rInner * sin(nextA + twistSign * 0.25f))
                    )
                    val px = chevron.sumOf { it.x.toDouble() }.toFloat() * 0.25f
                    val py = chevron.sumOf { it.y.toDouble() }.toFloat() * 0.25f
                    val area = (rOuter * rOuter * 0.58f).coerceAtLeast(0.00008f)
                    val score = field.evaluate(px.coerceIn(0f, 1f), py.coerceIn(0f, aspect), rng)
                    val shade = when (wing) {
                        0 -> 1.22f
                        1 -> 0.90f
                        else -> 0.64f
                    }

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = chevron,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = wing + abs(r + c) % 3,
                                    secondaryColorIndex = wing + 2,
                                    shadeFactor = shade,
                                    gradientAngleRad = midA,
                                    strokeSeamNorm = 0.0016f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 80. Gosper Island (Hexagonal Recursive Peano-Gosper Flowsnake Tiles)
    fun buildGosperFlowsnake(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val invSqrt7 = 1f / sqrt(7f)
        val twist = kotlin.math.acos((5.0 / (2.0 * sqrt(7.0))).coerceIn(-1.0, 1.0)).toFloat()
        val cells = ArrayList<DraftCell>(400)

        data class HexTile(val center: Vec2, val radius: Float, val angle: Float, val depth: Int, val tag: Int)

        fun subdivide(tile: HexTile, maxDepth: Int, out: MutableList<HexTile>) {
            if (tile.depth >= maxDepth) {
                out.add(tile)
                return
            }
            val childR = tile.radius * invSqrt7
            val childAngle = tile.angle + twist
            // Center child + 6 ring children on rotated sqrt(7) hexagonal lattice
            out.run {
                subdivide(HexTile(tile.center, childR, childAngle, tile.depth + 1, tile.tag * 7), maxDepth, out)
                val ringDist = childR * 1.7320508f
                for (k in 0 until 6) {
                    val a = childAngle + k * (PI.toFloat() / 3f) + PI.toFloat() / 6f
                    val cc = Vec2(tile.center.x + ringDist * cos(a), tile.center.y + ringDist * sin(a))
                    subdivide(HexTile(cc, childR, childAngle + (k % 2) * 0.52f, tile.depth + 1, tile.tag * 7 + k + 1), maxDepth, out)
                }
            }
        }

        val depth = if (intricacy >= 0.52f) 3 else 2
        val rootR = max(0.58f, aspect * 0.36f)
        val seeds = listOf(
            Vec2(0.5f, aspect * 0.5f),
            Vec2(0.5f, aspect * 0.5f - rootR * 1.52f),
            Vec2(0.5f, aspect * 0.5f + rootR * 1.52f)
        )
        val leaves = ArrayList<HexTile>(1100)
        val baseRot = rng.nextFloat() * TWO_PI
        for ((sIdx, sc) in seeds.withIndex()) {
            subdivide(HexTile(sc, rootR * 0.88f, baseRot, 0, sIdx + 1), depth, leaves)
        }

        for (leaf in leaves) {
            val cx = leaf.center.x
            val cy = leaf.center.y
            if (cx < -0.10f || cx > 1.10f || cy < -0.10f || cy > aspect + 0.10f) continue

            val rOuter = leaf.radius * 0.88f
            val hex = List(6) { k ->
                val a = leaf.angle + k * (PI.toFloat() / 3f)
                Vec2(cx + rOuter * cos(a), cy + rOuter * sin(a))
            }
            val innerTri = List(3) { k ->
                val a = leaf.angle + k * (TWO_PI / 3f) + PI.toFloat() / 6f
                Vec2(cx + rOuter * 0.52f * cos(a), cy + rOuter * 0.52f * sin(a))
            }
            val area = (2.598f * rOuter * rOuter).coerceAtLeast(0.00006f)
            val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
            val cIdx = abs(leaf.tag) % 6

            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    listOf(
                        PolygonPrimitive(
                            vertices = hex,
                            threshold = threshold,
                            areaWeight = area * 0.72f,
                            colorIndex = cIdx,
                            secondaryColorIndex = cIdx + 1,
                            shadeFactor = 0.84f + 0.24f * ((leaf.tag and 3) * 0.25f),
                            gradientAngleRad = leaf.angle,
                            strokeSeamNorm = 0.0013f
                        ),
                        PolygonPrimitive(
                            vertices = innerTri,
                            threshold = threshold,
                            areaWeight = area * 0.28f,
                            colorIndex = cIdx + 2,
                            secondaryColorIndex = cIdx,
                            shadeFactor = 1.20f,
                            gradientAngleRad = leaf.angle + 1.05f,
                            strokeSeamNorm = 0.0010f
                        )
                    )
                }
            )
        }
        return cells
    }
}

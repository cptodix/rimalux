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

internal object GeometricSymmetryPatterns {

    private const val TWO_PI = (2.0 * PI).toFloat()
    private const val PHI = 1.618034f

    // 6. Sacred Mandala (Concentric Lotus & Diamond Rose Symmetry)
    fun buildSacredMandala(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val symmetry = listOf(8, 10, 12, 16)[rng.nextInt(4)]
        val rings = (7 + (intricacy * 6f).roundToInt()).coerceIn(7, 14)
        val maxR = hypot(0.56f, aspect * 0.56f)
        val basePhase = rng.nextFloat() * TWO_PI
        val cells = ArrayList<DraftCell>(rings * symmetry * 2)

        for (ring in 0 until rings) {
            val t0 = ring.toFloat() / rings
            val t1 = (ring + 1).toFloat() / rings
            val rIn = t0 * maxR * 0.98f
            val rOut = (t1 * maxR * 0.98f).coerceAtLeast(rIn + 0.022f)
            val rMid = (rIn + rOut) * 0.5f
            val sectorSpan = TWO_PI / symmetry
            val ringTwist = basePhase + (ring % 2) * (sectorSpan * 0.5f)

            for (s in 0 until symmetry) {
                val theta = ringTwist + s * sectorSpan
                val halfSpan = sectorSpan * 0.40f

                val tip = Vec2(center.x + rOut * cos(theta), center.y + rOut * sin(theta))
                val basePt = Vec2(center.x + (rIn + 0.006f) * cos(theta), center.y + (rIn + 0.006f) * sin(theta))
                val shoulderL = Vec2(
                    center.x + rMid * cos(theta - halfSpan),
                    center.y + rMid * sin(theta - halfSpan)
                )
                val shoulderR = Vec2(
                    center.x + rMid * cos(theta + halfSpan),
                    center.y + rMid * sin(theta + halfSpan)
                )
                val upperL = Vec2(
                    center.x + (rMid + (rOut - rMid) * 0.52f) * cos(theta - halfSpan * 0.58f),
                    center.y + (rMid + (rOut - rMid) * 0.52f) * sin(theta - halfSpan * 0.58f)
                )
                val upperR = Vec2(
                    center.x + (rMid + (rOut - rMid) * 0.52f) * cos(theta + halfSpan * 0.58f),
                    center.y + (rMid + (rOut - rMid) * 0.52f) * sin(theta + halfSpan * 0.58f)
                )

                val midX = center.x + rMid * cos(theta)
                val midY = center.y + rMid * sin(theta)
                if (midX < -0.18f || midX > 1.18f || midY < -0.18f || midY > aspect + 0.18f) continue

                val petalPoly = listOf(basePt, shoulderL, upperL, tip, upperR, shoulderR)
                val innerJewel = listOf(
                    Vec2(center.x + (rIn + (rOut - rIn) * 0.24f) * cos(theta), center.y + (rIn + (rOut - rIn) * 0.24f) * sin(theta)),
                    Vec2(center.x + rMid * cos(theta - halfSpan * 0.42f), center.y + rMid * sin(theta - halfSpan * 0.42f)),
                    Vec2(center.x + (rIn + (rOut - rIn) * 0.78f) * cos(theta), center.y + (rIn + (rOut - rIn) * 0.78f) * sin(theta)),
                    Vec2(center.x + rMid * cos(theta + halfSpan * 0.42f), center.y + rMid * sin(theta + halfSpan * 0.42f))
                )

                val area = (0.42f * (rOut * rOut - rIn * rIn) * sectorSpan).coerceAtLeast(0.0001f)
                val score = field.evaluate(midX.coerceIn(0f, 1f), midY.coerceIn(0f, aspect), rng)
                val cIdx = ring + (s % 2)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = petalPoly,
                                threshold = threshold,
                                areaWeight = area * 0.74f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.84f + (ring % 3) * 0.12f,
                                gradientAngleRad = theta,
                                strokeSeamNorm = 0.0020f
                            ),
                            PolygonPrimitive(
                                vertices = innerJewel,
                                threshold = threshold,
                                areaWeight = area * 0.26f,
                                colorIndex = cIdx + 2,
                                secondaryColorIndex = cIdx,
                                shadeFactor = 1.18f,
                                gradientAngleRad = theta + PI.toFloat() * 0.5f,
                                strokeSeamNorm = 0.0016f
                            )
                        )
                    }
                )

                // Interstitial crown arc between rings on even tiers
                if (ring > 0 && ring % 2 == 0) {
                    val degCenter = Math.toDegrees((theta + sectorSpan * 0.5f).toDouble()).toFloat()
                    val degSweep = Math.toDegrees((sectorSpan * 0.56f).toDouble()).toFloat()
                    val arcMidX = center.x + rIn * cos(theta + sectorSpan * 0.5f)
                    val arcMidY = center.y + rIn * sin(theta + sectorSpan * 0.5f)
                    if (arcMidX in -0.15f..1.15f && arcMidY in -0.15f..(aspect + 0.15f)) {
                        val bandThickness = (rOut - rIn) * 0.22f
                        val arcArea = (rIn * bandThickness * sectorSpan * 0.56f).coerceAtLeast(0.0001f)
                        val arcScore = field.evaluate(arcMidX.coerceIn(0f, 1f), arcMidY.coerceIn(0f, aspect), rng)
                        cells.add(
                            DraftCell(score = arcScore, area = arcArea) { threshold ->
                                listOf(
                                    ArcSectorPrimitive(
                                        center = center,
                                        innerRadius = (rIn - bandThickness * 0.5f).coerceAtLeast(0.01f),
                                        outerRadius = rIn + bandThickness * 0.5f,
                                        startAngleDeg = degCenter - degSweep * 0.5f,
                                        sweepAngleDeg = degSweep,
                                        threshold = threshold,
                                        areaWeight = arcArea,
                                        colorIndex = cIdx + 3,
                                        secondaryColorIndex = cIdx + 1,
                                        shadeFactor = 1.12f,
                                        gradientAngleRad = theta
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
        return cells
    }

    // 7. Penrose Quasicrystal (5-Fold Robinson Golden Triangle Deflation)
    private data class RobinsonTri(
        val type: Int, // 0 = acute half-kite, 1 = obtuse half-dart
        val a: Vec2,
        val b: Vec2,
        val c: Vec2
    )

    fun buildPenroseQuasicrystal(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val radius = hypot(0.64f, aspect * 0.64f)
        val rotOffset = rng.nextFloat() * TWO_PI
        val depth = if (intricacy >= 0.58f) 5 else 4

        var triangles = ArrayList<RobinsonTri>(10)
        for (i in 0 until 10) {
            val a1 = rotOffset + (2 * i - 1) * PI.toFloat() / 10f
            val a2 = rotOffset + (2 * i + 1) * PI.toFloat() / 10f
            val p1 = Vec2(center.x + radius * cos(a1), center.y + radius * sin(a1))
            val p2 = Vec2(center.x + radius * cos(a2), center.y + radius * sin(a2))
            if (i % 2 == 0) {
                triangles.add(RobinsonTri(0, center, p2, p1))
            } else {
                triangles.add(RobinsonTri(0, center, p1, p2))
            }
        }

        repeat(depth) {
            val next = ArrayList<RobinsonTri>(triangles.size * 3)
            for (t in triangles) {
                if (t.type == 0) {
                    val p = Vec2(
                        t.a.x + (t.b.x - t.a.x) / PHI,
                        t.a.y + (t.b.y - t.a.y) / PHI
                    )
                    next.add(RobinsonTri(0, t.c, p, t.b))
                    next.add(RobinsonTri(1, p, t.c, t.a))
                } else {
                    val q = Vec2(
                        t.b.x + (t.a.x - t.b.x) / PHI,
                        t.b.y + (t.a.y - t.b.y) / PHI
                    )
                    val r = Vec2(
                        t.b.x + (t.c.x - t.b.x) / PHI,
                        t.b.y + (t.c.y - t.b.y) / PHI
                    )
                    next.add(RobinsonTri(1, r, t.c, t.a))
                    next.add(RobinsonTri(1, q, r, t.b))
                    next.add(RobinsonTri(0, r, q, t.a))
                }
            }
            triangles = next
        }

        val cells = ArrayList<DraftCell>(triangles.size)
        for (t in triangles) {
            val cx = (t.a.x + t.b.x + t.c.x) / 3f
            val cy = (t.a.y + t.b.y + t.c.y) / 3f
            if (cx < -0.12f || cx > 1.12f || cy < -0.12f || cy > aspect + 0.12f) continue

            val area = (0.5f * abs((t.b.x - t.a.x) * (t.c.y - t.a.y) - (t.c.x - t.a.x) * (t.b.y - t.a.y)))
                .coerceAtLeast(0.0001f)
            val distNorm = (hypot(cx - center.x, cy - center.y) / radius).coerceIn(0f, 1f)
            val ringBand = (distNorm * 7f).toInt()
            val cIdx = if (t.type == 0) ringBand else ringBand + 2
            val gradAng = atan2(cy - center.y, cx - center.x)
            val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
            val shade = if (t.type == 0) 1.08f else 0.86f

            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    listOf(
                        PolygonPrimitive(
                            vertices = listOf(t.a, t.b, t.c),
                            threshold = threshold,
                            areaWeight = area,
                            colorIndex = cIdx,
                            secondaryColorIndex = cIdx + 1,
                            shadeFactor = shade,
                            gradientAngleRad = gradAng,
                            strokeSeamNorm = 0.0022f
                        )
                    )
                }
            )
        }
        return cells
    }

    // 8. Girih Starwork (Islamic Polygonal Star & Kite Strapwork Lattice)
    fun buildGirihStarwork(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (4 + (intricacy * 3f).roundToInt()).coerceIn(4, 7)
        val cellSize = 1f / cols.toFloat()
        val rows = max(6, (aspect / (cellSize * 0.866f)).roundToInt() + 1)
        val points = if (rng.nextBoolean()) 8 else 10
        val cells = ArrayList<DraftCell>(cols * rows * (points + 1))

        for (r in -1..rows) {
            val rowOffset = if (r % 2 != 0) cellSize * 0.5f else 0f
            val cy = r * cellSize * 0.866f
            for (c in -1..cols) {
                val cx = c * cellSize + rowOffset
                if (cx < -0.18f || cx > 1.18f || cy < -0.18f || cy > aspect + 0.18f) continue

                val outerR = cellSize * 0.50f
                val midR = outerR * 0.62f
                val innerR = outerR * 0.36f
                val stepAng = TWO_PI / points

                // Central star polygon
                val starVerts = ArrayList<Vec2>(points * 2)
                for (i in 0 until points * 2) {
                    val rad = if (i % 2 == 0) midR else innerR
                    val ang = i * (stepAng * 0.5f)
                    starVerts.add(Vec2(cx + rad * cos(ang), cy + rad * sin(ang)))
                }
                val starArea = (PI.toFloat() * innerR * midR).coerceAtLeast(0.0001f)
                val centerScore = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val baseColor = abs(r * 2 + c)

                cells.add(
                    DraftCell(score = centerScore, area = starArea) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = starVerts,
                                threshold = threshold,
                                areaWeight = starArea,
                                colorIndex = baseColor,
                                secondaryColorIndex = baseColor + 2,
                                shadeFactor = 1.16f,
                                gradientAngleRad = 0.78f,
                                strokeSeamNorm = 0.0022f
                            )
                        )
                    }
                )

                // Surrounding Girih kite rosettes
                for (p in 0 until points) {
                    val ang = p * stepAng + stepAng * 0.5f
                    val leftVal = ang - stepAng * 0.5f
                    val rightVal = ang + stepAng * 0.5f

                    val vInner = Vec2(cx + innerR * cos(ang), cy + innerR * sin(ang))
                    val vLeft = Vec2(cx + midR * cos(leftVal), cy + midR * sin(leftVal))
                    val vOuter = Vec2(cx + outerR * cos(ang), cy + outerR * sin(ang))
                    val vRight = Vec2(cx + midR * cos(rightVal), cy + midR * sin(rightVal))

                    val kx = (vInner.x + vOuter.x) * 0.5f
                    val ky = (vInner.y + vOuter.y) * 0.5f
                    val kArea = (0.5f * (outerR - innerR) * midR * stepAng).coerceAtLeast(0.0001f)
                    val kScore = field.evaluate(kx.coerceIn(0f, 1f), ky.coerceIn(0f, aspect), rng)

                    cells.add(
                        DraftCell(score = kScore, area = kArea) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = listOf(vInner, vLeft, vOuter, vRight),
                                    threshold = threshold,
                                    areaWeight = kArea,
                                    colorIndex = baseColor + 1 + (p % 2),
                                    secondaryColorIndex = baseColor + 3,
                                    shadeFactor = 0.88f + (p % 2) * 0.14f,
                                    gradientAngleRad = ang,
                                    strokeSeamNorm = 0.0022f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 9. Harmonic Cardioid (Modular Caustic Envelope & Epicycloid Shells)
    fun buildHarmonicCardioid(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val shells = (9 + (intricacy * 7f).roundToInt()).coerceIn(9, 17)
        val segments = (32 + (intricacy * 20f).roundToInt()).coerceIn(32, 56)
        val cuspOrder = listOf(1, 2, 3)[rng.nextInt(3)] // 1 = cardioid, 2 = nephroid, 3 = trefoil caustic
        val rotBase = rng.nextFloat() * TWO_PI
        val maxR = hypot(0.54f, aspect * 0.54f)
        val cells = ArrayList<DraftCell>(shells * 8)

        for (sh in 0 until shells) {
            val u0 = sh.toFloat() / shells
            val u1 = (sh + 1).toFloat() / shells
            val rScale0 = (0.12f + 0.88f * u0) * maxR
            val rScale1 = (0.12f + 0.88f * u1) * maxR
            val phase = rotBase + sh * 0.16f

            fun epicycloidPt(scale: Float, t: Float): Vec2 {
                val modR = scale * (0.64f + 0.34f * cos(cuspOrder * t))
                val ang = t + phase + 0.18f * sin(cuspOrder * t)
                return Vec2(center.x + modR * cos(ang), center.y + modR * sin(ang))
            }

            val chunks = 8
            val stepsPerChunk = segments / chunks
            for (ch in 0 until chunks) {
                val outerArc = ArrayList<Vec2>(stepsPerChunk + 1)
                val innerArc = ArrayList<Vec2>(stepsPerChunk + 1)
                for (s in 0..stepsPerChunk) {
                    val idx = ch * stepsPerChunk + s
                    val t = (idx.toFloat() / segments) * TWO_PI
                    val pIn = epicycloidPt(rScale0 * 1.03f, t)
                    val pOut = epicycloidPt(rScale1 * 0.96f, t)
                    innerArc.add(pIn)
                    outerArc.add(pOut)
                }

                val mid = outerArc[outerArc.size / 2]
                if (mid.x < -0.16f || mid.x > 1.16f || mid.y < -0.16f || mid.y > aspect + 0.16f) continue

                val poly = outerArc + innerArc.asReversed()
                val area = ((rScale1 * rScale1 - rScale0 * rScale0) * PI.toFloat() / chunks).coerceAtLeast(0.0001f)
                val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                val gradAng = atan2(mid.y - center.y, mid.x - center.x)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = poly,
                                threshold = threshold,
                                areaWeight = area * 0.82f,
                                colorIndex = sh + (ch % 2),
                                secondaryColorIndex = sh + 2,
                                shadeFactor = 0.82f + (sh % 4) * 0.10f,
                                gradientAngleRad = gradAng,
                                strokeSeamNorm = 0.0020f
                            ),
                            PolylineRibbonPrimitive(
                                points = outerArc,
                                strokeWidthNorm = 0.0026f,
                                threshold = threshold,
                                areaWeight = area * 0.18f,
                                colorIndex = sh + 3,
                                secondaryColorIndex = sh,
                                shadeFactor = 1.20f,
                                gradientAngleRad = gradAng + 0.9f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 10. Art Deco Sunburst (Chrysler Stepped Radial Arches & Chevron Rays)
    fun buildArtDecoSunburst(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val focalY = aspect * (0.68f + (rng.nextFloat() - 0.5f) * 0.24f)
        val center = Vec2(0.5f, focalY)
        val rays = (16 + (intricacy * 8f).roundToInt()).coerceIn(16, 24)
        val tiers = (7 + (intricacy * 5f).roundToInt()).coerceIn(7, 13)
        val maxR = hypot(0.65f, aspect * 0.85f)
        val cells = ArrayList<DraftCell>(rays * tiers)

        val sectorDeg = 360f / rays.toFloat()
        val gapDeg = 4.6f // Wide architectural gap avoids sub-pixel AA bleed

        for (tier in 0 until tiers) {
            val t0 = tier.toFloat() / tiers
            val t1 = (tier + 1).toFloat() / tiers
            val rIn = t0 * maxR
            val rOut = (t1 * maxR).coerceAtLeast(rIn + 0.032f)
            val radialGap = (rOut - rIn) * 0.12f
            val staggerDeg = if (tier % 2 == 1) sectorDeg * 0.5f else 0f

            for (r in 0 until rays) {
                val startDeg = r * sectorDeg + staggerDeg + gapDeg * 0.5f
                val sweepDeg = (sectorDeg - gapDeg).coerceAtLeast(4f)
                val midRad = Math.toRadians((startDeg + sweepDeg * 0.5f).toDouble()).toFloat()
                val rMid = (rIn + rOut) * 0.5f
                val mx = center.x + rMid * cos(midRad)
                val my = center.y + rMid * sin(midRad)
                if (mx < -0.18f || mx > 1.18f || my < -0.18f || my > aspect + 0.18f) continue

                val score = field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
                val area = (0.5f * (rOut * rOut - rIn * rIn) * Math.toRadians(sweepDeg.toDouble()).toFloat())
                    .coerceAtLeast(0.0001f)
                val cIdx = tier + (r % 2)

                if (tier % 2 == 0) {
                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                ArcSectorPrimitive(
                                    center = center,
                                    innerRadius = rIn + radialGap * 0.5f,
                                    outerRadius = (rOut - radialGap * 0.5f).coerceAtLeast(rIn + 0.015f),
                                    startAngleDeg = startDeg,
                                    sweepAngleDeg = sweepDeg,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 2,
                                    shadeFactor = 0.88f + (tier % 3) * 0.12f,
                                    gradientAngleRad = midRad
                                )
                            )
                        }
                    )
                } else {
                    // Stepped Chrysler pointed chevron crown
                    val leftRad = Math.toRadians(startDeg.toDouble()).toFloat()
                    val rightRad = Math.toRadians((startDeg + sweepDeg).toDouble()).toFloat()
                    val rBase = rIn + radialGap * 0.5f
                    val rShoulder = rIn + (rOut - rIn) * 0.68f
                    val rPeak = rOut - radialGap * 0.35f
                    val chevron = listOf(
                        Vec2(center.x + rBase * cos(leftRad), center.y + rBase * sin(leftRad)),
                        Vec2(center.x + rShoulder * cos(leftRad), center.y + rShoulder * sin(leftRad)),
                        Vec2(center.x + rPeak * cos(midRad), center.y + rPeak * sin(midRad)),
                        Vec2(center.x + rShoulder * cos(rightRad), center.y + rShoulder * sin(rightRad)),
                        Vec2(center.x + rBase * cos(rightRad), center.y + rBase * sin(rightRad))
                    )
                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = chevron,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = cIdx + 1,
                                    secondaryColorIndex = cIdx + 3,
                                    shadeFactor = 1.14f,
                                    gradientAngleRad = midRad,
                                    strokeSeamNorm = 0.0020f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 11. Fractal Lattice (Multi-Scale Sierpiński & Subdivision Triangular Mesh)
    fun buildFractalLattice(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val baseCols = (3 + (intricacy * 2f).roundToInt()).coerceIn(3, 5)
        val side = 1f / baseCols.toFloat()
        val triH = side * 0.8660254f
        val baseRows = max(5, (aspect / triH).roundToInt() + 1)
        val maxDepth = if (intricacy >= 0.55f) 3 else 2
        val cells = ArrayList<DraftCell>(baseCols * baseRows * 12)

        fun subdivide(a: Vec2, b: Vec2, c: Vec2, depth: Int, colorSeed: Int) {
            val cx = (a.x + b.x + c.x) / 3f
            val cy = (a.y + b.y + c.y) / 3f
            if (cx < -0.15f || cx > 1.15f || cy < -0.15f || cy > aspect + 0.15f) return

            val localScore = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
            if (depth < maxDepth && (depth == 0 || localScore > 0.34f)) {
                val ab = Vec2((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
                val bc = Vec2((b.x + c.x) * 0.5f, (b.y + c.y) * 0.5f)
                val ca = Vec2((c.x + a.x) * 0.5f, (c.y + a.y) * 0.5f)

                subdivide(a, ab, ca, depth + 1, colorSeed + 1)
                subdivide(ab, b, bc, depth + 1, colorSeed + 2)
                subdivide(ca, bc, c, depth + 1, colorSeed + 3)

                // Central inverted jewel facet with distinct lower score so Sierpiński voids emerge naturally
                val centerArea = (0.5f * abs((bc.x - ab.x) * (ca.y - ab.y) - (ca.x - ab.x) * (bc.y - ab.y)))
                    .coerceAtLeast(0.0001f)
                val centerFacetScore = (localScore * 0.72f).coerceIn(0.02f, 0.98f)
                cells.add(
                    DraftCell(score = centerFacetScore, area = centerArea) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = listOf(ab, bc, ca),
                                threshold = threshold,
                                areaWeight = centerArea,
                                colorIndex = colorSeed + 3,
                                secondaryColorIndex = colorSeed + 1,
                                shadeFactor = 0.78f,
                                gradientAngleRad = -0.78f,
                                strokeSeamNorm = 0.0024f
                            )
                        )
                    }
                )
            } else {
                val area = (0.5f * abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y)))
                    .coerceAtLeast(0.0001f)
                cells.add(
                    DraftCell(score = localScore, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = listOf(a, b, c),
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = colorSeed,
                                secondaryColorIndex = colorSeed + 2,
                                shadeFactor = 0.92f + (depth % 3) * 0.12f,
                                gradientAngleRad = 0.65f * depth,
                                strokeSeamNorm = 0.0024f
                            )
                        )
                    }
                )
            }
        }

        for (r in -1..baseRows) {
            val y0 = r * triH
            val y1 = (r + 1) * triH
            val xShift0 = if (r % 2 != 0) side * 0.5f else 0f
            val xShift1 = if ((r + 1) % 2 != 0) side * 0.5f else 0f

            for (c in -1..baseCols) {
                val p00 = Vec2(c * side + xShift0, y0)
                val p10 = Vec2((c + 1) * side + xShift0, y0)
                val p01 = Vec2(c * side + xShift1, y1)
                val p11 = Vec2((c + 1) * side + xShift1, y1)
                val baseColor = abs(r * 3 + c * 2)
                if (r % 2 == 0) {
                    subdivide(p00, p10, p01, 0, baseColor)
                    subdivide(p10, p11, p01, 0, baseColor + 1)
                } else {
                    subdivide(p00, p11, p01, 0, baseColor)
                    subdivide(p00, p10, p11, 0, baseColor + 1)
                }
            }
        }
        return cells
    }
}

package com.cptodix.rimalux.oled.wallpaper.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

internal object TessellationKineticPatterns {

    private const val TWO_PI = (2.0 * PI).toFloat()

    // 25. True Centroidal Voronoi Cathedral Stained Glass
    fun buildVoronoiStainedGlass(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (5 + (intricacy * 4f).roundToInt()).coerceIn(5, 9)
        val rows = max(7, (cols * aspect).roundToInt())
        val sx = 1f / cols
        val sy = aspect / rows

        val sites = ArrayList<Vec2>((cols + 2) * (rows + 2))
        for (r in -1..rows) {
            for (c in -1..cols) {
                sites.add(
                    Vec2(
                        (c + 0.5f + (rng.nextFloat() - 0.5f) * 0.76f) * sx,
                        (r + 0.5f + (rng.nextFloat() - 0.5f) * 0.76f) * sy
                    )
                )
            }
        }

        fun clipHalfPlane(poly: List<Vec2>, site: Vec2, neighbor: Vec2): List<Vec2> {
            if (poly.isEmpty()) return poly
            val mx = (site.x + neighbor.x) * 0.5f
            val my = (site.y + neighbor.y) * 0.5f
            val nx = neighbor.x - site.x
            val ny = neighbor.y - site.y
            val out = ArrayList<Vec2>(poly.size + 1)
            var prev = poly.last()
            var prevDist = (prev.x - mx) * nx + (prev.y - my) * ny

            for (curr in poly) {
                val currDist = (curr.x - mx) * nx + (curr.y - my) * ny
                val prevIn = prevDist <= 0f
                val currIn = currDist <= 0f
                if (prevIn != currIn) {
                    val denom = (prevDist - currDist)
                    val t = if (abs(denom) > 1e-6f) (prevDist / denom).coerceIn(0f, 1f) else 0.5f
                    out.add(Vec2(prev.x + t * (curr.x - prev.x), prev.y + t * (curr.y - prev.y)))
                }
                if (currIn) out.add(curr)
                prev = curr
                prevDist = currDist
            }
            return out
        }

        val cells = ArrayList<DraftCell>(sites.size)
        val clipRadius = max(sx, sy) * 2.5f

        for ((idx, site) in sites.withIndex()) {
            if (site.x < -sx * 0.5f || site.x > 1f + sx * 0.5f || site.y < -sy * 0.5f || site.y > aspect + sy * 0.5f) continue
            var poly: List<Vec2> = listOf(
                Vec2(-0.04f, -0.04f),
                Vec2(1.04f, -0.04f),
                Vec2(1.04f, aspect + 0.04f),
                Vec2(-0.04f, aspect + 0.04f)
            )
            for ((j, other) in sites.withIndex()) {
                if (idx == j) continue
                if (abs(other.x - site.x) > clipRadius || abs(other.y - site.y) > clipRadius) continue
                poly = clipHalfPlane(poly, site, other)
                if (poly.size < 3) break
            }
            if (poly.size < 3) continue

            val cx = poly.sumOf { it.x.toDouble() }.toFloat() / poly.size
            val cy = poly.sumOf { it.y.toDouble() }.toFloat() / poly.size
            val outerPane = poly.map { Vec2(it.x + (cx - it.x) * 0.09f, it.y + (cy - it.y) * 0.09f) }
            val innerJewel = outerPane.map { Vec2(cx + (it.x - cx) * 0.54f, cy + (it.y - cy) * 0.54f) }
            val area = (sx * sy * 0.85f).coerceAtLeast(0.0001f)
            val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
            val cIdx = idx % 6

            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    listOf(
                        PolygonPrimitive(
                            vertices = outerPane,
                            threshold = threshold,
                            areaWeight = area * 0.68f,
                            colorIndex = cIdx,
                            secondaryColorIndex = cIdx + 1,
                            shadeFactor = 0.84f + (idx % 3) * 0.10f,
                            gradientAngleRad = idx * 0.65f,
                            strokeSeamNorm = 0.0022f
                        ),
                        PolygonPrimitive(
                            vertices = innerJewel,
                            threshold = threshold,
                            areaWeight = area * 0.32f,
                            colorIndex = cIdx + 2,
                            secondaryColorIndex = cIdx,
                            shadeFactor = 1.20f,
                            gradientAngleRad = -idx * 0.65f
                        )
                    )
                }
            )
        }
        return cells
    }

    // 26. Nested Hexagonal Honeycomb Matrix
    fun buildHexHoneycombPrism(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (4 + (intricacy * 4f).roundToInt()).coerceIn(4, 9)
        val hexR = (1f / (cols * 1.7320508f)).coerceAtLeast(0.065f)
        val colStep = hexR * 1.7320508f
        val rowStep = hexR * 1.5f
        val rows = ((aspect / rowStep).roundToInt() + 2).coerceAtLeast(6)
        val cells = ArrayList<DraftCell>(rows * cols * 3)

        for (r in -1..rows) {
            val ox = if (r % 2 != 0) colStep * 0.5f else 0f
            for (c in -1..(cols + 1)) {
                val cx = c * colStep + ox
                val cy = r * rowStep
                if (cx < -hexR || cx > 1f + hexR || cy < -hexR || cy > aspect + hexR) continue

                val corners = List(6) { k ->
                    val ang = (k * 60f - 30f) * PI.toFloat() / 180f
                    Vec2(cx + hexR * 0.91f * cos(ang), cy + hexR * 0.91f * sin(ang))
                }
                val innerCorners = List(6) { k ->
                    val ang = (k * 60f - 30f) * PI.toFloat() / 180f
                    Vec2(cx + hexR * 0.48f * cos(ang), cy + hexR * 0.48f * sin(ang))
                }
                val baseColor = abs(r * 2 + c * 3)
                val area = (2.598f * hexR * hexR / 3f).coerceAtLeast(0.0001f)

                // Split hexagon into 3 bevelled rhombic facets + inner hex jewel
                for (f in 0 until 3) {
                    val k0 = f * 2
                    val k1 = (k0 + 1) % 6
                    val k2 = (k0 + 2) % 6
                    val rhomb = listOf(Vec2(cx, cy), corners[k0], corners[k1], corners[k2])
                    val rcx = rhomb.sumOf { it.x.toDouble() }.toFloat() * 0.25f
                    val rcy = rhomb.sumOf { it.y.toDouble() }.toFloat() * 0.25f
                    val score = field.evaluate(rcx.coerceIn(0f, 1f), rcy.coerceIn(0f, aspect), rng)
                    val shade = when (f) {
                        0 -> 1.16f
                        1 -> 0.88f
                        else -> 0.64f
                    }
                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            val list = ArrayList<VectorPrimitive>(2)
                            list.add(
                                PolygonPrimitive(
                                    vertices = rhomb,
                                    threshold = threshold,
                                    areaWeight = area * 0.80f,
                                    colorIndex = baseColor + f,
                                    secondaryColorIndex = baseColor + f + 1,
                                    shadeFactor = shade,
                                    gradientAngleRad = f * 2.094f,
                                    strokeSeamNorm = 0.0018f
                                )
                            )
                            if (f == 0) {
                                list.add(
                                    PolylineRibbonPrimitive(
                                        points = innerCorners,
                                        strokeWidthNorm = 0.0024f,
                                        threshold = threshold,
                                        areaWeight = area * 0.20f,
                                        colorIndex = baseColor + 3,
                                        secondaryColorIndex = baseColor,
                                        shadeFactor = 1.24f,
                                        gradientAngleRad = 0.52f,
                                        closed = true
                                    )
                                )
                            }
                            list
                        }
                    )
                }
            }
        }
        return cells
    }

    // 27. Multi-Band Smith-Truchet Quarter-Arc Labyrinth
    fun buildTruchetLabyrinth(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (6 + (intricacy * 5f).roundToInt()).coerceIn(6, 12)
        val tile = 1f / cols
        val rows = max(8, (aspect / tile).roundToInt() + 1)
        val cells = ArrayList<DraftCell>(cols * rows * 2)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x0 = c * tile
                val y0 = r * tile
                val flip = rng.nextBoolean()
                val arcPairs = if (flip) {
                    listOf(Triple(x0, y0, 0f), Triple(x0 + tile, y0 + tile, 180f))
                } else {
                    listOf(Triple(x0 + tile, y0, 90f), Triple(x0, y0 + tile, 270f))
                }

                for ((aIdx, arcDef) in arcPairs.withIndex()) {
                    val (acx, acy, startDeg) = arcDef
                    val midRad = (startDeg + 45f) * PI.toFloat() / 180f
                    val mx = acx + tile * 0.5f * cos(midRad)
                    val my = acy + tile * 0.5f * sin(midRad)
                    val score = field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
                    val area = (tile * tile * 0.42f).coerceAtLeast(0.0001f)
                    val cIdx = abs(r + c + aIdx)

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                // Outer wide Truchet ribbon
                                ArcSectorPrimitive(
                                    center = Vec2(acx, acy),
                                    innerRadius = tile * 0.34f,
                                    outerRadius = tile * 0.66f,
                                    startAngleDeg = startDeg,
                                    sweepAngleDeg = 90f,
                                    threshold = threshold,
                                    areaWeight = area * 0.72f,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 1,
                                    shadeFactor = 0.96f,
                                    gradientAngleRad = midRad
                                ),
                                // Inner parallel pinstripe arc
                                ArcSectorPrimitive(
                                    center = Vec2(acx, acy),
                                    innerRadius = tile * 0.46f,
                                    outerRadius = tile * 0.54f,
                                    startAngleDeg = startDeg,
                                    sweepAngleDeg = 90f,
                                    threshold = threshold,
                                    areaWeight = area * 0.28f,
                                    colorIndex = cIdx + 2,
                                    secondaryColorIndex = cIdx,
                                    shadeFactor = 1.22f,
                                    gradientAngleRad = midRad + 1.57f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 28. Twisted Piet Hein Lamé Superellipse Rings
    fun buildSuperellipseNest(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val rings = (11 + (intricacy * 9f).roundToInt()).coerceIn(11, 22)
        val exponentN = listOf(0.65f, 1.4f, 2.6f, 3.8f)[rng.nextInt(4)]
        val powerExp = 2f / exponentN
        val maxRx = 0.64f
        val maxRy = aspect * 0.60f
        val twistRate = (rng.nextFloat() - 0.5f) * 0.14f
        val segments = 8
        val ptsPerSeg = 9
        val cells = ArrayList<DraftCell>(rings * segments)

        fun lamePoint(rx: Float, ry: Float, theta: Float, twist: Float): Vec2 {
            val ct = cos(theta)
            val st = sin(theta)
            val lx = sign(ct) * abs(ct).pow(powerExp) * rx
            val ly = sign(st) * abs(st).pow(powerExp) * ry
            val cw = cos(twist)
            val sw = sin(twist)
            return Vec2(
                center.x + lx * cw - ly * sw,
                center.y + lx * sw + ly * cw
            )
        }

        for (r in 0 until rings) {
            val f0 = (r + 0.14f) / rings
            val f1 = (r + 0.84f) / rings
            val twist = r * twistRate

            for (seg in 0 until segments) {
                val outerPts = ArrayList<Vec2>(ptsPerSeg + 1)
                val innerPts = ArrayList<Vec2>(ptsPerSeg + 1)
                val tStart = (seg.toFloat() / segments) * TWO_PI + 0.02f
                val tEnd = ((seg + 1).toFloat() / segments) * TWO_PI - 0.02f

                for (p in 0..ptsPerSeg) {
                    val th = tStart + (tEnd - tStart) * (p.toFloat() / ptsPerSeg)
                    outerPts.add(lamePoint(maxRx * f1, maxRy * f1, th, twist))
                    innerPts.add(lamePoint(maxRx * f0, maxRy * f0, th, twist))
                }
                val mid = outerPts[ptsPerSeg / 2]
                if (mid.x < -0.15f || mid.x > 1.15f || mid.y < -0.15f || mid.y > aspect + 0.15f) continue

                val poly = outerPts + innerPts.asReversed()
                val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                val area = ((f1 * f1 - f0 * f0) * maxRx * maxRy * 0.4f).coerceAtLeast(0.0001f)
                val cIdx = r + (seg % 2)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = poly,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 2,
                                shadeFactor = 0.82f + 0.32f * ((r % 3) / 2f),
                                gradientAngleRad = (tStart + tEnd) * 0.5f + twist,
                                strokeSeamNorm = 0.0016f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 29. Dihedral D_{2n} Mirror Kaleidoscope Shards
    fun buildKaleidoscopePrism(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val mirrors = listOf(6, 8, 10, 12)[rng.nextInt(4)]
        val wedgeRad = PI.toFloat() / mirrors
        val maxR = hypot(0.58f, aspect * 0.58f)
        val shardTemplates = (12 + (intricacy * 10f).roundToInt()).coerceIn(12, 24)
        val cells = ArrayList<DraftCell>(shardTemplates * mirrors * 2)

        for (sh in 0 until shardTemplates) {
            val r0 = (sh.toFloat() / shardTemplates) * maxR * 0.92f + rng.nextFloat() * 0.03f
            val r1 = (r0 + maxR * (0.09f + rng.nextFloat() * 0.10f)).coerceAtMost(maxR * 1.05f)
            val a0 = rng.nextFloat() * wedgeRad * 0.72f
            val a1 = (a0 + wedgeRad * (0.28f + rng.nextFloat() * 0.55f)).coerceAtMost(wedgeRad * 0.96f)
            val aMid = (a0 + a1) * 0.5f
            val rMid = (r0 + r1) * 0.5f
            val baseColor = sh

            // Replicate across all 2 * mirrors dihedral reflections for crystal-perfect symmetry
            for (m in 0 until mirrors) {
                val baseAng = m * 2f * wedgeRad
                for (signFlip in intArrayOf(1, -1)) {
                    val p0 = Vec2(center.x + r0 * cos(baseAng + signFlip * aMid), center.y + r0 * sin(baseAng + signFlip * aMid))
                    val p1 = Vec2(center.x + rMid * cos(baseAng + signFlip * a0), center.y + rMid * sin(baseAng + signFlip * a0))
                    val p2 = Vec2(center.x + r1 * cos(baseAng + signFlip * aMid), center.y + r1 * sin(baseAng + signFlip * aMid))
                    val p3 = Vec2(center.x + rMid * cos(baseAng + signFlip * a1), center.y + rMid * sin(baseAng + signFlip * a1))

                    val cx = (p0.x + p1.x + p2.x + p3.x) * 0.25f
                    val cy = (p0.y + p1.y + p2.y + p3.y) * 0.25f
                    if (cx < -0.15f || cx > 1.15f || cy < -0.15f || cy > aspect + 0.15f) continue

                    val verts = if (signFlip == 1) listOf(p0, p1, p2, p3) else listOf(p0, p3, p2, p1)
                    val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                    val area = (0.5f * (r1 - r0) * rMid * abs(a1 - a0)).coerceAtLeast(0.00006f)

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = verts,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = baseColor + (if (signFlip == 1) 0 else 1),
                                    secondaryColorIndex = baseColor + 2,
                                    shadeFactor = if (signFlip == 1) 1.10f else 0.84f,
                                    gradientAngleRad = baseAng + signFlip * aMid,
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

    // 30. Hyperbolic Poincaré Disk Orthogonal Geodesics
    fun buildHyperbolicPoincare(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val symmetryP = listOf(5, 6, 7, 8)[rng.nextInt(4)]
        val diskRadii = listOf(0.46f, min(0.72f, aspect * 0.46f), hypot(0.54f, aspect * 0.54f))
        val layersPerDisk = (3 + (intricacy * 3f).roundToInt()).coerceIn(3, 6)
        val cells = ArrayList<DraftCell>()

        for ((dIdx, diskR) in diskRadii.withIndex()) {
            for (layer in 0 until layersPerDisk) {
                val frac = (layer + 1f) / (layersPerDisk + 0.6f)
                val halfSpan = (PI.toFloat() / symmetryP) * (0.55f + 0.35f * frac)
                val twist = (dIdx * 0.3f) + layer * (PI.toFloat() / symmetryP)

                // Orthogonal circle to disk of radius R spanning angle 2*halfSpan has:
                // center distance d = R / cos(halfSpan), radius r = R * tan(halfSpan)
                val safeHalf = halfSpan.coerceIn(0.18f, 1.15f)
                val orthoDist = diskR / cos(safeHalf)
                val orthoR = diskR * tan(safeHalf)

                for (s in 0 until symmetryP * 2) {
                    val theta = twist + s * (PI.toFloat() / symmetryP)
                    val ocx = center.x + orthoDist * cos(theta)
                    val ocy = center.y + orthoDist * sin(theta)

                    // Build hyperbolic crescent lens inside the Poincaré disk
                    val steps = 12
                    val arc1 = ArrayList<Vec2>(steps + 1)
                    val arc2 = ArrayList<Vec2>(steps + 1)
                    val inwardAng = theta + PI.toFloat()
                    val maxArcSweep = (PI.toFloat() * 0.5f - safeHalf) * 1.85f

                    for (k in 0..steps) {
                        val u = (k.toFloat() / steps) - 0.5f
                        val a = inwardAng + u * maxArcSweep
                        arc1.add(Vec2(ocx + orthoR * 0.96f * cos(a), ocy + orthoR * 0.96f * sin(a)))
                        arc2.add(Vec2(ocx + orthoR * 1.12f * cos(a), ocy + orthoR * 1.12f * sin(a)))
                    }
                    val mid = arc1[steps / 2]
                    if (mid.x < -0.15f || mid.x > 1.15f || mid.y < -0.15f || mid.y > aspect + 0.15f) continue

                    val poly = arc1 + arc2.asReversed()
                    val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                    val area = (orthoR * orthoR * 0.16f).coerceAtLeast(0.00008f)
                    val cIdx = dIdx * 2 + layer + (s % 2)

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = poly,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 1,
                                    shadeFactor = 0.84f + 0.30f * (1f - frac),
                                    gradientAngleRad = theta,
                                    strokeSeamNorm = 0.0015f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 31. Over-Under Braided Celtic Diagonal Lattice
    fun buildCelticInterlace(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (5 + (intricacy * 5f).roundToInt()).coerceIn(5, 10)
        val pitch = 1f / cols
        val rows = max(8, (aspect / pitch).roundToInt() + 2)
        val halfStrap = pitch * 0.21f
        val gap = pitch * 0.08f
        val cells = ArrayList<DraftCell>(cols * rows * 2)

        for (r in -1..rows) {
            for (c in -1..cols) {
                val cx = (c + 0.5f) * pitch
                val cy = (r + 0.5f) * pitch
                val parity = (r + c) and 1
                // Two crossing diagonal straps at 45° and 135°; the "under" strap stops short by `gap`
                for (dir in 0..1) {
                    val isOver = (dir == parity)
                    val ang = if (dir == 0) PI.toFloat() * 0.25f else PI.toFloat() * 0.75f
                    val dx = cos(ang)
                    val dy = sin(ang)
                    val nx = -dy
                    val ny = dx
                    val halfLen = pitch * 0.7071f

                    val segments = if (isOver) {
                        listOf(-halfLen to halfLen)
                    } else {
                        listOf(-halfLen to -(halfStrap + gap), (halfStrap + gap) to halfLen)
                    }

                    val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                    val area = (pitch * halfStrap * 1.4f).coerceAtLeast(0.00008f)
                    val cIdx = dir * 2 + (abs(r - c) % 3)

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            val prims = ArrayList<VectorPrimitive>(segments.size * 2)
                            for ((s0, s1) in segments) {
                                val quad = listOf(
                                    Vec2(cx + dx * s0 + nx * halfStrap, cy + dy * s0 + ny * halfStrap),
                                    Vec2(cx + dx * s1 + nx * halfStrap, cy + dy * s1 + ny * halfStrap),
                                    Vec2(cx + dx * s1 - nx * halfStrap, cy + dy * s1 - ny * halfStrap),
                                    Vec2(cx + dx * s0 - nx * halfStrap, cy + dy * s0 - ny * halfStrap)
                                )
                                prims.add(
                                    PolygonPrimitive(
                                        vertices = quad,
                                        threshold = threshold,
                                        areaWeight = area / segments.size,
                                        colorIndex = cIdx,
                                        secondaryColorIndex = cIdx + 1,
                                        shadeFactor = if (isOver) 1.12f else 0.76f,
                                        gradientAngleRad = ang,
                                        strokeSeamNorm = 0.0014f
                                    )
                                )
                            }
                            prims
                        }
                    )
                }
            }
        }
        return cells
    }

    // 32. Japanese Asanoha 6-Fold Hemp-Leaf Kumiko Joinery
    fun buildKumikoWoodwork(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (4 + (intricacy * 4f).roundToInt()).coerceIn(4, 8)
        val side = (1f / cols).coerceAtLeast(0.10f)
        val triH = side * 0.8660254f
        val rows = ((aspect / triH).roundToInt() + 2).coerceAtLeast(6)
        val cells = ArrayList<DraftCell>(rows * cols * 6)

        for (r in -1..rows) {
            val ox = if (r % 2 != 0) side * 0.5f else 0f
            for (c in -1..(cols + 1)) {
                val x0 = c * side + ox
                val y0 = r * triH
                // Upward and downward equilateral triangles in the lattice
                val macroTris = listOf(
                    Triple(Vec2(x0, y0 + triH), Vec2(x0 + side, y0 + triH), Vec2(x0 + side * 0.5f, y0)),
                    Triple(Vec2(x0 + side * 0.5f, y0), Vec2(x0 + side, y0 + triH), Vec2(x0 + side * 1.5f, y0))
                )

                for ((tParity, tri) in macroTris.withIndex()) {
                    val (a, b, d) = tri
                    val cx = (a.x + b.x + d.x) / 3f
                    val cy = (a.y + b.y + d.y) / 3f
                    if (cx < -side || cx > 1f + side || cy < -triH || cy > aspect + triH) continue

                    // Subdivide each equilateral triangle into 3 Asanoha hemp-leaf sub-triangles around centroid
                    val subTris = listOf(
                        Triple(a, b, Vec2(cx, cy)),
                        Triple(b, d, Vec2(cx, cy)),
                        Triple(d, a, Vec2(cx, cy))
                    )
                    for ((sIdx, sub) in subTris.withIndex()) {
                        val (u, v, w) = sub
                        val scx = (u.x + v.x + w.x) / 3f
                        val scy = (u.y + v.y + w.y) / 3f
                        val inset = listOf(u, v, w).map { pt ->
                            Vec2(pt.x + (scx - pt.x) * 0.085f, pt.y + (scy - pt.y) * 0.085f)
                        }
                        val area = (0.433f * side * side / 3f).coerceAtLeast(0.00008f)
                        val score = field.evaluate(scx.coerceIn(0f, 1f), scy.coerceIn(0f, aspect), rng)
                        val cIdx = abs(r * 2 + c + tParity) + sIdx

                        cells.add(
                            DraftCell(score = score, area = area) { threshold ->
                                listOf(
                                    PolygonPrimitive(
                                        vertices = inset,
                                        threshold = threshold,
                                        areaWeight = area,
                                        colorIndex = cIdx,
                                        secondaryColorIndex = cIdx + 1,
                                        shadeFactor = when (sIdx) {
                                            0 -> 1.14f
                                            1 -> 0.90f
                                            else -> 0.72f
                                        },
                                        gradientAngleRad = atan2(scy - cy, scx - cx),
                                        strokeSeamNorm = 0.0020f
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
}

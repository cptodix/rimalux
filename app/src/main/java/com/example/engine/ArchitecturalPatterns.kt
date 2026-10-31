package com.example.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

internal object ArchitecturalPatterns {

    private const val TWO_PI = (2.0 * PI).toFloat()

    // 12. Crystal Voronoi (3D-Faceted Mineral Shards & Prismatic Cleavage)
    fun buildCrystalVoronoi(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (5 + (intricacy * 4f).roundToInt()).coerceIn(5, 9)
        val rows = max(8, (cols * aspect).roundToInt())
        val sx = 1f / cols.toFloat()
        val sy = aspect / rows.toFloat()
        val lightAngle = rng.nextFloat() * TWO_PI
        val lightDir = Vec2(cos(lightAngle), sin(lightAngle))

        val sites = ArrayList<Vec2>((cols + 2) * (rows + 2))
        for (r in -1..rows) {
            for (c in -1..cols) {
                sites.add(
                    Vec2(
                        (c + 0.5f + (rng.nextFloat() - 0.5f) * 0.74f) * sx,
                        (r + 0.5f + (rng.nextFloat() - 0.5f) * 0.74f) * sy
                    )
                )
            }
        }

        fun clipBisector(poly: List<Vec2>, site: Vec2, other: Vec2): List<Vec2> {
            if (poly.isEmpty()) return poly
            val mx = (site.x + other.x) * 0.5f
            val my = (site.y + other.y) * 0.5f
            val nx = other.x - site.x
            val ny = other.y - site.y
            val out = ArrayList<Vec2>(poly.size + 1)
            var prev = poly.last()
            var prevD = (prev.x - mx) * nx + (prev.y - my) * ny

            for (curr in poly) {
                val currD = (curr.x - mx) * nx + (curr.y - my) * ny
                val prevIn = prevD <= 0f
                val currIn = currD <= 0f
                if (prevIn != currIn) {
                    val denom = prevD - currD
                    val t = if (abs(denom) > 1e-6f) (prevD / denom).coerceIn(0f, 1f) else 0.5f
                    out.add(Vec2(prev.x + t * (curr.x - prev.x), prev.y + t * (curr.y - prev.y)))
                }
                if (currIn) out.add(curr)
                prev = curr
                prevD = currD
            }
            return out
        }

        val gridCols = cols + 2
        val cells = ArrayList<DraftCell>(cols * rows * 5)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val siteIdx = (r + 1) * gridCols + (c + 1)
                val site = sites[siteIdx]
                val radX = sx * 2.2f
                val radY = sy * 2.2f
                var cellPoly: List<Vec2> = listOf(
                    Vec2(site.x - radX, site.y - radY),
                    Vec2(site.x + radX, site.y - radY),
                    Vec2(site.x + radX, site.y + radY),
                    Vec2(site.x - radX, site.y + radY)
                )

                for (dr in -2..2) {
                    for (dc in -2..2) {
                        if (dr == 0 && dc == 0) continue
                        val nr = r + 1 + dr
                        val nc = c + 1 + dc
                        if (nr in 0..(rows + 1) && nc in 0..(cols + 1)) {
                            cellPoly = clipBisector(cellPoly, site, sites[nr * gridCols + nc])
                        }
                    }
                }

                if (cellPoly.size < 3) continue
                val apex = Vec2(
                    site.x + (rng.nextFloat() - 0.5f) * sx * 0.28f,
                    site.y + (rng.nextFloat() - 0.5f) * sy * 0.28f
                )
                val cellScore = field.evaluate(site.x.coerceIn(0f, 1f), site.y.coerceIn(0f, aspect), rng)
                val baseColor = abs(r * 3 + c * 2)

                // Subdivide crystal cell from apex into directional 3D mineral facets
                val facets = ArrayList<VectorPrimitive>(cellPoly.size)
                var totalCellArea = 0f
                val facetWeights = FloatArray(cellPoly.size)

                for (i in cellPoly.indices) {
                    val v1 = cellPoly[i]
                    val v2 = cellPoly[(i + 1) % cellPoly.size]
                    val fArea = (0.5f * abs((v1.x - apex.x) * (v2.y - apex.y) - (v2.x - apex.x) * (v1.y - apex.y)))
                        .coerceAtLeast(0.00005f)
                    facetWeights[i] = fArea
                    totalCellArea += fArea
                }

                val safeTotal = totalCellArea.coerceAtLeast(0.0002f)
                cells.add(
                    DraftCell(score = cellScore, area = safeTotal) { threshold ->
                        for (i in cellPoly.indices) {
                            val v1 = cellPoly[i]
                            val v2 = cellPoly[(i + 1) % cellPoly.size]
                            val midEdgeX = (v1.x + v2.x) * 0.5f - apex.x
                            val midEdgeY = (v1.y + v2.y) * 0.5f - apex.y
                            val nLen = hypot(midEdgeX, midEdgeY).coerceAtLeast(0.0001f)
                            val dot = (midEdgeX / nLen) * lightDir.x + (midEdgeY / nLen) * lightDir.y
                            val shade = (0.98f + 0.30f * dot).coerceIn(0.68f, 1.28f)
                            facets.add(
                                PolygonPrimitive(
                                    vertices = listOf(apex, v1, v2),
                                    threshold = threshold,
                                    areaWeight = safeTotal * (facetWeights[i] / safeTotal),
                                    colorIndex = baseColor + (i % 2),
                                    secondaryColorIndex = baseColor + 2,
                                    shadeFactor = shade,
                                    gradientAngleRad = atan2(midEdgeY, midEdgeX),
                                    strokeSeamNorm = 0.0018f
                                )
                            )
                        }
                        facets
                    }
                )
            }
        }
        return cells
    }

    // 13. Isometric Monument (30-Degree Architectural Cubes & Stepped Escher Pillars)
    fun buildIsometricMonument(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (6 + (intricacy * 4f).roundToInt()).coerceIn(6, 11)
        val radius = 1f / (cols * 1.7320508f)
        val dx = radius * 1.7320508f
        val dy = radius * 1.5f
        val rows = max(9, (aspect / dy).roundToInt() + 2)
        val cos30 = 0.8660254f
        val sin30 = 0.5f
        val cells = ArrayList<DraftCell>(cols * rows * 3)

        for (r in -1..rows) {
            val xOffset = if (r % 2 != 0) dx * 0.5f else 0f
            val cy = r * dy
            for (c in -1..cols) {
                val cx = c * dx + xOffset
                if (cx < -0.15f || cx > 1.15f || cy < -0.15f || cy > aspect + 0.15f) continue

                val pCenter = Vec2(cx, cy)
                val pTop = Vec2(cx, cy - radius)
                val pTopRight = Vec2(cx + radius * cos30, cy - radius * sin30)
                val pBotRight = Vec2(cx + radius * cos30, cy + radius * sin30)
                val pBot = Vec2(cx, cy + radius)
                val pBotLeft = Vec2(cx - radius * cos30, cy + radius * sin30)
                val pTopLeft = Vec2(cx - radius * cos30, cy - radius * sin30)

                val topRhombus = listOf(pTop, pTopRight, pCenter, pTopLeft)
                val leftRhombus = listOf(pTopLeft, pCenter, pBot, pBotLeft)
                val rightRhombus = listOf(pCenter, pTopRight, pBotRight, pBot)

                val faceArea = (radius * radius * 0.866f).coerceAtLeast(0.0001f)
                val baseScore = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val baseColor = abs(r * 2 + c * 3)
                val hasInnerCap = (r + c) % 3 == 0

                // Emit the 3 faces with tightly coupled scores so cubes emerge architecturally
                val faces = listOf(
                    Triple(topRhombus, 1.22f, -PI.toFloat() * 0.5f),
                    Triple(leftRhombus, 0.94f, 2.6f),
                    Triple(rightRhombus, 0.68f, 0.52f)
                )

                for ((fIdx, triple) in faces.withIndex()) {
                    val (verts, shade, gradAng) = triple
                    val fScore = (baseScore - fIdx * 0.015f).coerceIn(0.01f, 0.99f)
                    cells.add(
                        DraftCell(score = fScore, area = faceArea) { threshold ->
                            val list = ArrayList<VectorPrimitive>(2)
                            list.add(
                                PolygonPrimitive(
                                    vertices = verts,
                                    threshold = threshold,
                                    areaWeight = if (hasInnerCap && fIdx == 0) faceArea * 0.72f else faceArea,
                                    colorIndex = baseColor + (if (fIdx == 0) 0 else 1),
                                    secondaryColorIndex = baseColor + 2,
                                    shadeFactor = shade,
                                    gradientAngleRad = gradAng,
                                    strokeSeamNorm = 0.0020f
                                )
                            )
                            if (hasInnerCap && fIdx == 0) {
                                val rIn = radius * 0.48f
                                val cMidY = cy - radius * 0.5f
                                val innerCap = listOf(
                                    Vec2(cx, cMidY - rIn * 0.5f),
                                    Vec2(cx + rIn * cos30 * 0.5f, cMidY),
                                    Vec2(cx, cMidY + rIn * 0.5f),
                                    Vec2(cx - rIn * cos30 * 0.5f, cMidY)
                                )
                                list.add(
                                    PolygonPrimitive(
                                        vertices = innerCap,
                                        threshold = threshold,
                                        areaWeight = faceArea * 0.28f,
                                        colorIndex = baseColor + 3,
                                        secondaryColorIndex = baseColor,
                                        shadeFactor = 1.30f,
                                        gradientAngleRad = 0f,
                                        strokeSeamNorm = 0.0016f
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

    // 14. Bauhaus Construct (Weimar Modular Quarter-Arcs, Diagonals & Split Blocks)
    fun buildBauhausConstruct(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (5 + (intricacy * 3f).roundToInt()).coerceIn(5, 8)
        val tile = 1f / cols.toFloat()
        val rows = max(8, (aspect / tile).roundToInt() + 1)
        val pad = tile * 0.06f
        val cells = ArrayList<DraftCell>(cols * rows * 2)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x0 = c * tile + pad
                val y0 = r * tile + pad
                val x1 = (c + 1) * tile - pad
                val y1 = (r + 1) * tile - pad
                if (y0 > aspect + tile) continue

                val w = (x1 - x0).coerceAtLeast(0.01f)
                val cx = (x0 + x1) * 0.5f
                val cy = (y0 + y1) * 0.5f
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val area = (w * w).coerceAtLeast(0.0001f)
                val motif = rng.nextInt(5)
                val cIdx = abs(r * 2 + c + rng.nextInt(3))

                when (motif) {
                    0 -> {
                        // Quarter-circle Bauhaus arch + concentric inner quarter
                        val corner = rng.nextInt(4)
                        val pivot = when (corner) {
                            0 -> Vec2(x0, y0)
                            1 -> Vec2(x1, y0)
                            2 -> Vec2(x1, y1)
                            else -> Vec2(x0, y1)
                        }
                        val startDeg = corner * 90f
                        cells.add(
                            DraftCell(score = score, area = area * 0.78f) { threshold ->
                                listOf(
                                    ArcSectorPrimitive(
                                        center = pivot,
                                        innerRadius = w * 0.44f,
                                        outerRadius = w,
                                        startAngleDeg = startDeg,
                                        sweepAngleDeg = 90f,
                                        threshold = threshold,
                                        areaWeight = area * 0.54f,
                                        colorIndex = cIdx,
                                        secondaryColorIndex = cIdx + 1,
                                        shadeFactor = 1.06f,
                                        gradientAngleRad = Math.toRadians((startDeg + 45f).toDouble()).toFloat()
                                    ),
                                    ArcSectorPrimitive(
                                        center = pivot,
                                        innerRadius = 0f,
                                        outerRadius = w * 0.36f,
                                        startAngleDeg = startDeg,
                                        sweepAngleDeg = 90f,
                                        threshold = threshold,
                                        areaWeight = area * 0.24f,
                                        colorIndex = cIdx + 2,
                                        secondaryColorIndex = cIdx + 3,
                                        shadeFactor = 1.18f,
                                        gradientAngleRad = 0f
                                    )
                                )
                            }
                        )
                    }
                    1 -> {
                        // Diagonal split triangles
                        val flip = rng.nextBoolean()
                        val triA = if (flip) {
                            listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x0, y1))
                        } else {
                            listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x1, y1))
                        }
                        val triB = if (flip) {
                            listOf(Vec2(x1, y0), Vec2(x1, y1), Vec2(x0, y1))
                        } else {
                            listOf(Vec2(x0, y0), Vec2(x1, y1), Vec2(x0, y1))
                        }
                        cells.add(
                            DraftCell(score = score, area = area * 0.5f) { threshold ->
                                listOf(
                                    PolygonPrimitive(
                                        vertices = triA,
                                        threshold = threshold,
                                        areaWeight = area * 0.5f,
                                        colorIndex = cIdx,
                                        secondaryColorIndex = cIdx + 2,
                                        shadeFactor = 1.10f,
                                        gradientAngleRad = 0.78f,
                                        strokeSeamNorm = 0.0022f
                                    )
                                )
                            }
                        )
                        cells.add(
                            DraftCell(score = (score * 0.88f).coerceAtLeast(0.02f), area = area * 0.5f) { threshold ->
                                listOf(
                                    PolygonPrimitive(
                                        vertices = triB,
                                        threshold = threshold,
                                        areaWeight = area * 0.5f,
                                        colorIndex = cIdx + 1,
                                        secondaryColorIndex = cIdx + 3,
                                        shadeFactor = 0.86f,
                                        gradientAngleRad = -0.78f,
                                        strokeSeamNorm = 0.0022f
                                    )
                                )
                            }
                        )
                    }
                    2 -> {
                        // Full Bauhaus target disc & ring
                        cells.add(
                            DraftCell(score = score, area = area * 0.78f) { threshold ->
                                listOf(
                                    ArcSectorPrimitive(
                                        center = Vec2(cx, cy),
                                        innerRadius = w * 0.24f,
                                        outerRadius = w * 0.48f,
                                        startAngleDeg = 0f,
                                        sweepAngleDeg = 360f,
                                        threshold = threshold,
                                        areaWeight = area * 0.56f,
                                        colorIndex = cIdx,
                                        secondaryColorIndex = cIdx + 1,
                                        shadeFactor = 1.04f,
                                        gradientAngleRad = 0.52f
                                    ),
                                    ArcSectorPrimitive(
                                        center = Vec2(cx, cy),
                                        innerRadius = 0f,
                                        outerRadius = w * 0.16f,
                                        startAngleDeg = 0f,
                                        sweepAngleDeg = 360f,
                                        threshold = threshold,
                                        areaWeight = area * 0.22f,
                                        colorIndex = cIdx + 3,
                                        secondaryColorIndex = cIdx,
                                        shadeFactor = 1.22f,
                                        gradientAngleRad = -0.52f
                                    )
                                )
                            }
                        )
                    }
                    3 -> {
                        // Parallel architectural Bauhaus bars
                        val vertical = rng.nextBoolean()
                        for (b in 0..1) {
                            val barVerts = if (vertical) {
                                val bx0 = if (b == 0) x0 else cx + pad * 0.6f
                                val bx1 = if (b == 0) cx - pad * 0.6f else x1
                                listOf(Vec2(bx0, y0), Vec2(bx1, y0), Vec2(bx1, y1), Vec2(bx0, y1))
                            } else {
                                val by0 = if (b == 0) y0 else cy + pad * 0.6f
                                val by1 = if (b == 0) cy - pad * 0.6f else y1
                                listOf(Vec2(x0, by0), Vec2(x1, by0), Vec2(x1, by1), Vec2(x0, by1))
                            }
                            cells.add(
                                DraftCell(
                                    score = (score - b * 0.06f).coerceAtLeast(0.02f),
                                    area = area * 0.45f
                                ) { threshold ->
                                    listOf(
                                        PolygonPrimitive(
                                            vertices = barVerts,
                                            threshold = threshold,
                                            areaWeight = area * 0.45f,
                                            colorIndex = cIdx + b * 2,
                                            secondaryColorIndex = cIdx + b * 2 + 1,
                                            shadeFactor = if (b == 0) 1.12f else 0.88f,
                                            gradientAngleRad = if (vertical) PI.toFloat() * 0.5f else 0f,
                                            strokeSeamNorm = 0.0018f
                                        )
                                    )
                                }
                            )
                        }
                    }
                    else -> {
                        // Framed square slab with offset inner square
                        val inset = w * 0.26f
                        val outerRect = listOf(Vec2(x0, y0), Vec2(x1, y0), Vec2(x1, y1), Vec2(x0, y1))
                        val innerRect = listOf(
                            Vec2(x0 + inset, y0 + inset),
                            Vec2(x1 - inset * 0.5f, y0 + inset),
                            Vec2(x1 - inset * 0.5f, y1 - inset * 0.5f),
                            Vec2(x0 + inset, y1 - inset * 0.5f)
                        )
                        cells.add(
                            DraftCell(score = score, area = area) { threshold ->
                                listOf(
                                    PolygonPrimitive(
                                        vertices = outerRect,
                                        threshold = threshold,
                                        areaWeight = area * 0.72f,
                                        colorIndex = cIdx,
                                        secondaryColorIndex = cIdx + 1,
                                        shadeFactor = 0.90f,
                                        gradientAngleRad = 0.78f,
                                        strokeSeamNorm = 0.0020f
                                    ),
                                    PolygonPrimitive(
                                        vertices = innerRect,
                                        threshold = threshold,
                                        areaWeight = area * 0.28f,
                                        colorIndex = cIdx + 2,
                                        secondaryColorIndex = cIdx + 3,
                                        shadeFactor = 1.20f,
                                        gradientAngleRad = -0.78f,
                                        strokeSeamNorm = 0.0016f
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

    // 15. Origami Miura (Rigid Herringbone Mountain-Valley Crease Tessellation)
    fun buildOrigamiMiura(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (8 + (intricacy * 6f).roundToInt()).coerceIn(8, 15)
        val rows = max(12, (cols * aspect * 1.15f).roundToInt())
        val dx = 1f / cols.toFloat()
        val dy = aspect / rows.toFloat()
        val chevronShift = dx * (0.34f + rng.nextFloat() * 0.22f)
        val rippleAmp = dy * 0.14f
        val ripplePhase = rng.nextFloat() * TWO_PI

        fun vertex(r: Int, c: Int): Vec2 {
            val zig = if (r % 2 == 0) -chevronShift * 0.5f else chevronShift * 0.5f
            val waveY = rippleAmp * sin(c * 0.65f + ripplePhase) * (if (c % 2 == 0) 1f else -1f)
            return Vec2(c * dx + zig, r * dy + waveY)
        }

        val cells = ArrayList<DraftCell>((cols + 2) * (rows + 2))
        for (r in -1..rows) {
            for (c in -1..cols) {
                val p00 = vertex(r, c)
                val p10 = vertex(r, c + 1)
                val p11 = vertex(r + 1, c + 1)
                val p01 = vertex(r + 1, c)

                val cx = (p00.x + p10.x + p11.x + p01.x) * 0.25f
                val cy = (p00.y + p10.y + p11.y + p01.y) * 0.25f
                if (cx < -0.12f || cx > 1.12f || cy < -0.12f || cy > aspect + 0.12f) continue

                val parity = ((r and 1) shl 1) or (c and 1)
                val shade = when (parity) {
                    0 -> 1.24f
                    1 -> 0.96f
                    2 -> 0.84f
                    else -> 0.68f
                }
                val gradAng = when (parity) {
                    0 -> -0.55f
                    1 -> 0.55f
                    2 -> 2.45f
                    else -> -2.45f
                }
                val area = (dx * dy).coerceAtLeast(0.0001f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val cIdx = abs((r / 2) + (c / 2) + (parity % 2))

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = listOf(p00, p10, p11, p01),
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = shade,
                                gradientAngleRad = gradAng,
                                strokeSeamNorm = 0.0020f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 16. Op-Art Gravity (Vasarely Relativistic Lens-Warped Checkerboard Lattice)
    fun buildOpArtGravity(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (9 + (intricacy * 5f).roundToInt()).coerceIn(9, 15)
        val rows = max(14, (cols * aspect).roundToInt())
        val dx = 1f / cols.toFloat()
        val dy = aspect / rows.toFloat()

        val lensA = Vec2(0.35f + rng.nextFloat() * 0.30f, aspect * (0.30f + rng.nextFloat() * 0.18f))
        val lensB = Vec2(1f - lensA.x, aspect * (0.62f + rng.nextFloat() * 0.18f))
        val strengthA = 0.52f + rng.nextFloat() * 0.24f
        val strengthB = -0.38f - rng.nextFloat() * 0.18f
        val sigmaSq = 0.075f

        fun warp(x: Float, y: Float): Vec2 {
            var wx = x
            var wy = y
            val dAx = wx - lensA.x
            val dAy = wy - lensA.y
            val rSqA = dAx * dAx + dAy * dAy
            val fA = strengthA * exp(-rSqA / sigmaSq)
            wx += dAx * fA
            wy += dAy * fA

            val dBx = wx - lensB.x
            val dBy = wy - lensB.y
            val rSqB = dBx * dBx + dBy * dBy
            val fB = strengthB * exp(-rSqB / (sigmaSq * 1.15f))
            wx += dBx * fB
            wy += dBy * fB
            return Vec2(wx, wy)
        }

        val cells = ArrayList<DraftCell>((cols + 2) * (rows + 2))
        for (r in -1..rows) {
            for (c in -1..cols) {
                val p00 = warp(c * dx, r * dy)
                val p10 = warp((c + 1) * dx, r * dy)
                val p11 = warp((c + 1) * dx, (r + 1) * dy)
                val p01 = warp(c * dx, (r + 1) * dy)

                val cx = (p00.x + p10.x + p11.x + p01.x) * 0.25f
                val cy = (p00.y + p10.y + p11.y + p01.y) * 0.25f
                if (cx < -0.15f || cx > 1.15f || cy < -0.15f || cy > aspect + 0.15f) continue

                val area = (0.5f * abs((p10.x - p00.x) * (p01.y - p00.y) - (p01.x - p00.x) * (p10.y - p00.y)) +
                    0.5f * abs((p10.x - p11.x) * (p01.y - p11.y) - (p01.x - p11.x) * (p10.y - p11.y)))
                    .coerceAtLeast(0.0001f)
                val checker = (r + c) and 1
                val rawScore = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                // Alternate Vasarely checkerboard hierarchy while preserving smooth ratio calibration
                val score = if (checker == 0) {
                    (rawScore * 0.78f + 0.20f).coerceIn(0.02f, 0.99f)
                } else {
                    (rawScore * 0.78f).coerceIn(0.01f, 0.95f)
                }

                val cIdx = abs((r + c) / 2 + checker * 2)
                val innerDiamond = listOf(
                    Vec2((p00.x + p10.x) * 0.5f, (p00.y + p10.y) * 0.5f),
                    Vec2((p10.x + p11.x) * 0.5f, (p10.y + p11.y) * 0.5f),
                    Vec2((p11.x + p01.x) * 0.5f, (p11.y + p01.y) * 0.5f),
                    Vec2((p01.x + p00.x) * 0.5f, (p01.y + p00.y) * 0.5f)
                )

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        if (checker == 0) {
                            listOf(
                                PolygonPrimitive(
                                    vertices = listOf(p00, p10, p11, p01),
                                    threshold = threshold,
                                    areaWeight = area * 0.72f,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 1,
                                    shadeFactor = 1.08f,
                                    gradientAngleRad = atan2(cy - lensA.y, cx - lensA.x),
                                    strokeSeamNorm = 0.0022f
                                ),
                                PolygonPrimitive(
                                    vertices = innerDiamond,
                                    threshold = threshold,
                                    areaWeight = area * 0.28f,
                                    colorIndex = cIdx + 2,
                                    secondaryColorIndex = cIdx,
                                    shadeFactor = 1.24f,
                                    gradientAngleRad = atan2(cy - lensB.y, cx - lensB.x),
                                    strokeSeamNorm = 0.0016f
                                )
                            )
                        } else {
                            listOf(
                                PolygonPrimitive(
                                    vertices = listOf(p00, p10, p11, p01),
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = cIdx + 1,
                                    secondaryColorIndex = cIdx + 3,
                                    shadeFactor = 0.84f,
                                    gradientAngleRad = atan2(cy - lensA.y, cx - lensA.x),
                                    strokeSeamNorm = 0.0022f
                                )
                            )
                        }
                    }
                )
            }
        }
        return cells
    }
}

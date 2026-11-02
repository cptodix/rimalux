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
import kotlin.math.sin
import kotlin.random.Random

internal object ConstructTessellationPatterns {

    private const val TWO_PI = (2.0 * PI).toFloat()

    // 41. Geodesic Dome (Triangulated Spherical Truss Facets)
    fun buildGeodesicDome(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val domeR = hypot(0.58f, aspect * 0.56f)
        val rings = (6 + (intricacy * 4f).roundToInt()).coerceIn(6, 10)
        val baseSectors = 12
        val lightDir = Vec2(0.6f, -0.8f)
        val cells = ArrayList<DraftCell>(rings * baseSectors * 3)

        fun projectSphere(uRing: Float, angle: Float): Vec2 {
            val curvedR = domeR * sin(uRing * PI.toFloat() * 0.5f)
            return Vec2(center.x + curvedR * cos(angle), center.y + curvedR * sin(angle))
        }

        for (r in 0 until rings) {
            val u0 = r.toFloat() / rings
            val u1 = (r + 1).toFloat() / rings
            val sectors = if (r == 0) baseSectors else baseSectors * 2
            val step = TWO_PI / sectors
            val twist0 = if (r % 2 == 1) step * 0.5f else 0f
            val twist1 = if ((r + 1) % 2 == 1) step * 0.5f else 0f

            for (s in 0 until sectors) {
                val a0 = s * step + twist0
                val a1 = (s + 1) * step + twist0
                val b0 = s * step + twist1
                val b1 = (s + 1) * step + twist1

                if (r == 0) {
                    val pCenter = center
                    val pL = projectSphere(u1, b0)
                    val pR = projectSphere(u1, b1)
                    emitDomeTri(cells, pCenter, pL, pR, r + s, lightDir, center, field, rng, aspect)
                } else {
                    val p00 = projectSphere(u0, a0)
                    val p10 = projectSphere(u0, a1)
                    val p01 = projectSphere(u1, b0)
                    val p11 = projectSphere(u1, b1)
                    emitDomeTri(cells, p00, p01, p11, r * 2 + (s % 2), lightDir, center, field, rng, aspect)
                    emitDomeTri(cells, p00, p11, p10, r * 2 + 1 + (s % 2), lightDir, center, field, rng, aspect)
                }
            }
        }
        return cells
    }

    private fun emitDomeTri(
        cells: MutableList<DraftCell>,
        a: Vec2,
        b: Vec2,
        c: Vec2,
        colorSeed: Int,
        lightDir: Vec2,
        center: Vec2,
        field: SculptField,
        rng: Random,
        aspect: Float
    ) {
        val cx = (a.x + b.x + c.x) / 3f
        val cy = (a.y + b.y + c.y) / 3f
        if (cx < -0.15f || cx > 1.15f || cy < -0.15f || cy > aspect + 0.15f) return
        val area = (0.5f * abs((b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y))).coerceAtLeast(0.0001f)
        val nx = cx - center.x
        val ny = cy - center.y
        val nLen = hypot(nx, ny).coerceAtLeast(0.001f)
        val dot = (nx / nLen) * lightDir.x + (ny / nLen) * lightDir.y
        val shade = (0.98f + 0.28f * dot).coerceIn(0.70f, 1.28f)
        val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)

        cells.add(
            DraftCell(score = score, area = area) { threshold ->
                listOf(
                    PolygonPrimitive(
                        vertices = listOf(a, b, c),
                        threshold = threshold,
                        areaWeight = area,
                        colorIndex = colorSeed,
                        secondaryColorIndex = colorSeed + 2,
                        shadeFactor = shade,
                        gradientAngleRad = atan2(ny, nx),
                        strokeSeamNorm = 0.0024f
                    )
                )
            }
        )
    }

    // 42. Infinite Atrium (Vanishing-Point Coffered Architectural Portals)
    fun buildPerspectiveColonnade(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val vp = Vec2(0.5f + (rng.nextFloat() - 0.5f) * 0.18f, aspect * (0.44f + (rng.nextFloat() - 0.5f) * 0.14f))
        val depths = (8 + (intricacy * 6f).roundToInt()).coerceIn(8, 15)
        val bayDivs = (4 + (intricacy * 3f).roundToInt()).coerceIn(4, 7)
        val corners = listOf(
            Vec2(-0.08f, -0.08f),
            Vec2(1.08f, -0.08f),
            Vec2(1.08f, aspect + 0.08f),
            Vec2(-0.08f, aspect + 0.08f)
        )
        val cells = ArrayList<DraftCell>(depths * 4 * bayDivs)

        fun lerpPt(a: Vec2, b: Vec2, t: Float) = Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

        for (d in 0 until depths) {
            val z0 = (d.toFloat() / depths).pow(1.45f)
            val z1 = ((d + 1).toFloat() / depths).pow(1.45f)
            val ring0 = corners.map { lerpPt(it, vp, z0) }
            val ring1 = corners.map { lerpPt(it, vp, z1) }

            for (wall in 0 until 4) {
                val nextWall = (wall + 1) % 4
                val shadeBase = when (wall) {
                    0 -> 1.18f
                    2 -> 0.96f
                    1 -> 0.84f
                    else -> 0.74f
                }
                for (b in 0 until bayDivs) {
                    val u0 = b.toFloat() / bayDivs
                    val u1 = (b + 1).toFloat() / bayDivs
                    val q00 = lerpPt(ring0[wall], ring0[nextWall], u0)
                    val q10 = lerpPt(ring0[wall], ring0[nextWall], u1)
                    val q11 = lerpPt(ring1[wall], ring1[nextWall], u1)
                    val q01 = lerpPt(ring1[wall], ring1[nextWall], u0)

                    val cx = (q00.x + q10.x + q11.x + q01.x) * 0.25f
                    val cy = (q00.y + q10.y + q11.y + q01.y) * 0.25f
                    if (cx < -0.12f || cx > 1.12f || cy < -0.12f || cy > aspect + 0.12f) continue

                    val area = (0.5f * abs((q10.x - q00.x) * (q01.y - q00.y) - (q01.x - q00.x) * (q10.y - q00.y)) +
                        0.5f * abs((q10.x - q11.x) * (q01.y - q11.y) - (q01.x - q11.x) * (q10.y - q11.y)))
                        .coerceAtLeast(0.0001f)
                    val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                    val cIdx = d + wall + (b % 2)

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = listOf(q00, q10, q11, q01),
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 2,
                                    shadeFactor = shadeBase * (1f - z0 * 0.22f),
                                    gradientAngleRad = atan2(vp.y - cy, vp.x - cx),
                                    strokeSeamNorm = 0.0024f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 43. Neoplastic Grid (De Stijl Recursive Golden-Ratio Orthogonal Slabs)
    fun buildDeStijlMondrian(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val maxDepth = (4 + (intricacy * 2f).roundToInt()).coerceIn(4, 6)
        val cells = ArrayList<DraftCell>(96)
        val channelHalf = 0.0065f

        fun splitRect(x0: Float, y0: Float, x1: Float, y1: Float, depth: Int, seedIdx: Int) {
            val w = x1 - x0
            val h = y1 - y0
            if (depth >= maxDepth || (w < 0.13f && h < 0.13f)) {
                val ix0 = x0 + channelHalf
                val iy0 = y0 + channelHalf
                val ix1 = x1 - channelHalf
                val iy1 = y1 - channelHalf
                if (ix1 - ix0 <= 0.012f || iy1 - iy0 <= 0.012f) return

                val cx = (ix0 + ix1) * 0.5f
                val cy = (iy0 + iy1) * 0.5f
                val area = ((ix1 - ix0) * (iy1 - iy0)).coerceAtLeast(0.0001f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val verts = listOf(Vec2(ix0, iy0), Vec2(ix1, iy0), Vec2(ix1, iy1), Vec2(ix0, iy1))
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = verts,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = seedIdx,
                                secondaryColorIndex = seedIdx + 1,
                                shadeFactor = 0.90f + (seedIdx % 3) * 0.10f,
                                gradientAngleRad = if (w > h) 0f else PI.toFloat() * 0.5f,
                                strokeSeamNorm = 0.0020f
                            )
                        )
                    }
                )
                return
            }

            val splitVertical = if (w > h * 1.15f) true else if (h > w * 1.15f) false else rng.nextBoolean()
            val goldenPick = if (rng.nextBoolean()) 0.382f else 0.618f
            val ratio = (goldenPick + (rng.nextFloat() - 0.5f) * 0.14f).coerceIn(0.30f, 0.70f)
            if (splitVertical) {
                val sx = x0 + w * ratio
                splitRect(x0, y0, sx, y1, depth + 1, seedIdx * 3 + 1)
                splitRect(sx, y0, x1, y1, depth + 1, seedIdx * 3 + 2)
            } else {
                val sy = y0 + h * ratio
                splitRect(x0, y0, x1, sy, depth + 1, seedIdx * 3 + 1)
                splitRect(x0, sy, x1, y1, depth + 1, seedIdx * 3 + 2)
            }
        }

        splitRect(0f, 0f, 1f, aspect, 0, rng.nextInt(5))
        return cells
    }

    // 44. Stepped Ziggurat (Cantilevered Architectural Bevel Terraces)
    fun buildBrutalistZiggurat(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (3 + (intricacy * 2f).roundToInt()).coerceIn(3, 5)
        val tileW = 1f / cols.toFloat()
        val rows = max(5, (aspect / tileW).roundToInt() + 1)
        val tiers = 3
        val cells = ArrayList<DraftCell>(cols * rows * tiers * 4)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val bx0 = c * tileW
                val by0 = r * tileW
                val bx1 = (c + 1) * tileW
                val by1 = (r + 1) * tileW
                if (by0 > aspect + tileW) continue

                val baseColor = abs(r * 2 + c * 3)
                for (t in 0 until tiers) {
                    val in0 = (t.toFloat() / (tiers + 1)) * tileW * 0.48f
                    val in1 = ((t + 1).toFloat() / (tiers + 1)) * tileW * 0.48f
                    val ox0 = bx0 + in0
                    val oy0 = by0 + in0
                    val ox1 = bx1 - in0
                    val oy1 = by1 - in0

                    val ix0 = bx0 + in1
                    val iy0 = by0 + in1
                    val ix1 = bx1 - in1
                    val iy1 = by1 - in1

                    val slopes = listOf(
                        Triple(listOf(Vec2(ox0, oy0), Vec2(ox1, oy0), Vec2(ix1, iy0), Vec2(ix0, iy0)), 1.24f, -PI.toFloat() * 0.5f),
                        Triple(listOf(Vec2(ox0, oy0), Vec2(ix0, iy0), Vec2(ix0, iy1), Vec2(ox0, oy1)), 1.04f, PI.toFloat()),
                        Triple(listOf(Vec2(ix1, iy0), Vec2(ox1, oy0), Vec2(ox1, oy1), Vec2(ix1, iy1)), 0.78f, 0f),
                        Triple(listOf(Vec2(ix0, iy1), Vec2(ix1, iy1), Vec2(ox1, oy1), Vec2(ox0, oy1)), 0.66f, PI.toFloat() * 0.5f)
                    )

                    val bevelArea = (((ox1 - ox0) * (oy1 - oy0) - (ix1 - ix0) * (iy1 - iy0)) * 0.25f)
                        .coerceAtLeast(0.0001f)
                    for (slope in slopes) {
                        val (verts, shade, grad) = slope
                        val mx = verts.sumOf { it.x.toDouble() }.toFloat() * 0.25f
                        val my = verts.sumOf { it.y.toDouble() }.toFloat() * 0.25f
                        val score = field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
                        cells.add(
                            DraftCell(score = score, area = bevelArea) { threshold ->
                                listOf(
                                    PolygonPrimitive(
                                        vertices = verts,
                                        threshold = threshold,
                                        areaWeight = bevelArea,
                                        colorIndex = baseColor + t,
                                        secondaryColorIndex = baseColor + t + 1,
                                        shadeFactor = shade,
                                        gradientAngleRad = grad,
                                        strokeSeamNorm = 0.0018f
                                    )
                                )
                            }
                        )
                    }

                    if (t == tiers - 1) {
                        val capArea = ((ix1 - ix0) * (iy1 - iy0)).coerceAtLeast(0.0001f)
                        val cx = (ix0 + ix1) * 0.5f
                        val cy = (iy0 + iy1) * 0.5f
                        val capScore = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                        cells.add(
                            DraftCell(score = capScore, area = capArea) { threshold ->
                                listOf(
                                    PolygonPrimitive(
                                        vertices = listOf(Vec2(ix0, iy0), Vec2(ix1, iy0), Vec2(ix1, iy1), Vec2(ix0, iy1)),
                                        threshold = threshold,
                                        areaWeight = capArea,
                                        colorIndex = baseColor + 3,
                                        secondaryColorIndex = baseColor,
                                        shadeFactor = 1.28f,
                                        gradientAngleRad = 0.78f,
                                        strokeSeamNorm = 0.0018f
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

    // 45. Hilbert Labyrinth (Space-Filling Continuous Fractal Corridor)
    fun buildHilbertFractal(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val order = 4
        val n = 1 shl order
        val totalN = n * n
        val cellW = 1f / n.toFloat()
        val verticalBlocks = max(1, aspect.roundToInt().coerceAtLeast(2))
        val blockH = aspect / verticalBlocks.toFloat()
        val cellH = blockH / n.toFloat()
        val ribbonHalfW = min(cellW, cellH) * (0.30f + (1f - intricacy * 0.25f) * 0.08f)

        fun d2xy(gridN: Int, dInput: Int): Pair<Int, Int> {
            var rx: Int
            var ry: Int
            var s = 1
            var t = dInput
            var x = 0
            var y = 0
            while (s < gridN) {
                rx = 1 and (t / 2)
                ry = 1 and (t xor rx)
                if (ry == 0) {
                    if (rx == 1) {
                        x = s - 1 - x
                        y = s - 1 - y
                    }
                    val tmp = x
                    x = y
                    y = tmp
                }
                x += s * rx
                y += s * ry
                t /= 4
                s *= 2
            }
            return x to y
        }

        val cells = ArrayList<DraftCell>(verticalBlocks * totalN)
        for (vb in 0 until verticalBlocks) {
            val flipBlock = vb % 2 == 1
            val yOrigin = vb * blockH
            val pts = List(totalN) { d ->
                val (gx, gyRaw) = d2xy(n, d)
                val gy = if (flipBlock) (n - 1 - gyRaw) else gyRaw
                Vec2((gx + 0.5f) * cellW, yOrigin + (gy + 0.5f) * cellH)
            }

            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = hypot(dx, dy).coerceAtLeast(0.0001f)
                val ux = dx / len
                val uy = dy / len
                val nx = -uy * ribbonHalfW
                val ny = ux * ribbonHalfW
                val ax = a.x - ux * ribbonHalfW
                val ay = a.y - uy * ribbonHalfW
                val bx = b.x + ux * ribbonHalfW
                val by = b.y + uy * ribbonHalfW

                val rect = listOf(
                    Vec2(ax + nx, ay + ny),
                    Vec2(bx + nx, by + ny),
                    Vec2(bx - nx, by - ny),
                    Vec2(ax - nx, ay - ny)
                )
                val mx = (a.x + b.x) * 0.5f
                val my = (a.y + b.y) * 0.5f
                val score = field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
                val area = ((len + 2f * ribbonHalfW) * ribbonHalfW * 2f).coerceAtLeast(0.0001f)
                val cIdx = (i / 12) + vb * 2

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = rect,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.88f + ((i / 6) % 3) * 0.12f,
                                gradientAngleRad = atan2(dy, dx),
                                strokeSeamNorm = 0.0014f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 46. Heighway Dragon (Self-Similar 90-Degree Fold Mosaic)
    fun buildDragonCurve(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val iterations = if (intricacy >= 0.55f) 9 else 8
        var turns = BooleanArray(0)
        repeat(iterations) {
            val next = BooleanArray(turns.size * 2 + 1)
            for (i in turns.indices) next[i] = turns[i]
            next[turns.size] = true
            for (i in turns.indices) {
                next[turns.size + 1 + i] = !turns[turns.lastIndex - i]
            }
            turns = next
        }

        val rawPts = ArrayList<Vec2>(turns.size + 2)
        var x = 0f
        var y = 0f
        var dir = rng.nextInt(4)
        rawPts.add(Vec2(x, y))
        x += cos(dir * PI.toFloat() * 0.5f)
        y += sin(dir * PI.toFloat() * 0.5f)
        rawPts.add(Vec2(x, y))
        for (turnRight in turns) {
            dir = if (turnRight) (dir + 1) and 3 else (dir + 3) and 3
            x += cos(dir * PI.toFloat() * 0.5f)
            y += sin(dir * PI.toFloat() * 0.5f)
            rawPts.add(Vec2(x, y))
        }

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in rawPts) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val spanX = (maxX - minX).coerceAtLeast(1f)
        val spanY = (maxY - minY).coerceAtLeast(1f)
        val rotate90 = spanX > spanY

        val normPts = rawPts.map { p ->
            val u = if (rotate90) (p.y - minY) / spanY else (p.x - minX) / spanX
            val v = if (rotate90) (p.x - minX) / spanX else (p.y - minY) / spanY
            Vec2(0.06f + u * 0.88f, aspect * (0.04f + v * 0.92f))
        }

        val segLen = hypot(normPts[1].x - normPts[0].x, normPts[1].y - normPts[0].y).coerceAtLeast(0.012f)
        val halfW = segLen * 0.34f
        val cells = ArrayList<DraftCell>(normPts.size)

        for (i in 0 until normPts.size - 1) {
            val a = normPts[i]
            val b = normPts[i + 1]
            val p0 = Vec2(a.x + (b.x - a.x) * 0.12f, a.y + (b.y - a.y) * 0.12f)
            val p1 = Vec2(a.x + (b.x - a.x) * 0.88f, a.y + (b.y - a.y) * 0.88f)
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            val len = hypot(dx, dy).coerceAtLeast(0.0001f)
            val nx = (-dy / len) * halfW
            val ny = (dx / len) * halfW

            val quad = listOf(
                Vec2(p0.x + nx, p0.y + ny),
                Vec2(p1.x + nx, p1.y + ny),
                Vec2(p1.x - nx, p1.y - ny),
                Vec2(p0.x - nx, p0.y - ny)
            )
            val mx = (p0.x + p1.x) * 0.5f
            val my = (p0.y + p1.y) * 0.5f
            val score = field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
            val area = (len * halfW * 2f).coerceAtLeast(0.0001f)
            val cIdx = (i * 6) / normPts.size

            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    listOf(
                        PolygonPrimitive(
                            vertices = quad,
                            threshold = threshold,
                            areaWeight = area,
                            colorIndex = cIdx,
                            secondaryColorIndex = cIdx + 2,
                            shadeFactor = 0.88f + ((i / 16) % 3) * 0.12f,
                            gradientAngleRad = atan2(dy, dx),
                            strokeSeamNorm = 0.0016f
                        )
                    )
                }
            )
        }
        return cells
    }

    // 47. Cairo Pentagons (4-Fold Alternating Dual Street-Tile Mesh)
    fun buildCairoPentagonal(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (4 + (intricacy * 3f).roundToInt()).coerceIn(4, 7)
        val s = 1f / cols.toFloat()
        val rows = max(7, (aspect / s).roundToInt() + 1)
        val b = s * 0.268f
        val cells = ArrayList<DraftCell>(cols * rows * 4)

        for (r in -1..rows) {
            for (c in -1..cols) {
                val x0 = c * s
                val y0 = r * s
                val x1 = (c + 1) * s
                val y1 = (r + 1) * s
                val cx = (x0 + x1) * 0.5f
                val cy = (y0 + y1) * 0.5f
                if (cx < -0.2f || cx > 1.2f || cy < -0.2f || cy > aspect + 0.2f) continue

                val horizontalBridge = (r + c) % 2 == 0
                val pentagons: List<List<Vec2>> = if (horizontalBridge) {
                    val pL = Vec2(cx - b, cy)
                    val pR = Vec2(cx + b, cy)
                    val topMid = Vec2(cx, y0)
                    val botMid = Vec2(cx, y1)
                    val leftMid = Vec2(x0, cy)
                    val rightMid = Vec2(x1, cy)
                    listOf(
                        listOf(Vec2(x0, y0), topMid, pR, pL, leftMid),
                        listOf(topMid, Vec2(x1, y0), rightMid, pR),
                        listOf(leftMid, pL, pR, botMid, Vec2(x0, y1)),
                        listOf(pR, rightMid, Vec2(x1, y1), botMid)
                    )
                } else {
                    val pT = Vec2(cx, cy - b)
                    val pB = Vec2(cx, cy + b)
                    val topMid = Vec2(cx, y0)
                    val botMid = Vec2(cx, y1)
                    val leftMid = Vec2(x0, cy)
                    val rightMid = Vec2(x1, cy)
                    listOf(
                        listOf(Vec2(x0, y0), topMid, pT, pB, leftMid),
                        listOf(topMid, Vec2(x1, y0), rightMid, pB, pT),
                        listOf(leftMid, pB, botMid, Vec2(x0, y1)),
                        listOf(pB, rightMid, Vec2(x1, y1), botMid)
                    )
                }

                for ((qIdx, poly) in pentagons.withIndex()) {
                    val mx = poly.sumOf { it.x.toDouble() }.toFloat() / poly.size
                    val my = poly.sumOf { it.y.toDouble() }.toFloat() / poly.size
                    if (mx < -0.14f || mx > 1.14f || my < -0.14f || my > aspect + 0.14f) continue
                    val area = (s * s * 0.25f).coerceAtLeast(0.0001f)
                    val score = field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
                    val cIdx = abs(r * 2 + c + qIdx)

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = poly,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 2,
                                    shadeFactor = 0.84f + (qIdx % 4) * 0.11f,
                                    gradientAngleRad = qIdx * 0.78f,
                                    strokeSeamNorm = 0.0024f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 48. Kagome Lattice (Trihexagonal Woven Star Basketry)
    fun buildKagomeTrihexagonal(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (4 + (intricacy * 3f).roundToInt()).coerceIn(4, 7)
        val spacingX = 1f / cols.toFloat()
        val spacingY = spacingX * 0.8660254f
        val hexR = spacingX * 0.47f
        val rows = max(8, (aspect / spacingY).roundToInt() + 2)
        val cells = ArrayList<DraftCell>(cols * rows * 3)

        for (r in -1..rows) {
            val shiftX = if (r % 2 != 0) spacingX * 0.5f else 0f
            val cy = r * spacingY
            for (c in -1..cols) {
                val cx = c * spacingX + shiftX
                if (cx < -0.18f || cx > 1.18f || cy < -0.18f || cy > aspect + 0.18f) continue

                val hexVerts = List(6) { k ->
                    val ang = k * (PI.toFloat() / 3f)
                    Vec2(cx + hexR * cos(ang), cy + hexR * sin(ang))
                }
                val hexInner = List(6) { k ->
                    val ang = k * (PI.toFloat() / 3f)
                    Vec2(cx + hexR * 0.54f * cos(ang), cy + hexR * 0.54f * sin(ang))
                }
                val hexArea = (2.598f * hexR * hexR).coerceAtLeast(0.0001f)
                val hexScore = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val baseColor = abs(r * 2 + c)

                cells.add(
                    DraftCell(score = hexScore, area = hexArea) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = hexVerts,
                                threshold = threshold,
                                areaWeight = hexArea * 0.72f,
                                colorIndex = baseColor,
                                secondaryColorIndex = baseColor + 1,
                                shadeFactor = 0.94f,
                                gradientAngleRad = 0.52f,
                                strokeSeamNorm = 0.0022f
                            ),
                            PolygonPrimitive(
                                vertices = hexInner,
                                threshold = threshold,
                                areaWeight = hexArea * 0.28f,
                                colorIndex = baseColor + 2,
                                secondaryColorIndex = baseColor,
                                shadeFactor = 1.22f,
                                gradientAngleRad = -0.52f,
                                strokeSeamNorm = 0.0016f
                            )
                        )
                    }
                )

                val triSide = spacingX * 0.45f
                val triH = triSide * 0.8660254f
                val upCx = cx + spacingX * 0.5f
                val upCy = cy + spacingY * 0.333333f
                val upTri = listOf(
                    Vec2(upCx, upCy - triH * 0.6667f),
                    Vec2(upCx + triSide * 0.5f, upCy + triH * 0.3333f),
                    Vec2(upCx - triSide * 0.5f, upCy + triH * 0.3333f)
                )
                val dnCx = cx + spacingX * 0.5f
                val dnCy = cy - spacingY * 0.333333f
                val dnTri = listOf(
                    Vec2(dnCx - triSide * 0.5f, dnCy - triH * 0.3333f),
                    Vec2(dnCx + triSide * 0.5f, dnCy - triH * 0.3333f),
                    Vec2(dnCx, dnCy + triH * 0.6667f)
                )

                for ((dirIdx, pair) in listOf(Triple(upTri, upCx, upCy), Triple(dnTri, dnCx, dnCy)).withIndex()) {
                    val (triVerts, tcx, tcy) = pair
                    if (tcx in -0.15f..1.15f && tcy in -0.15f..(aspect + 0.15f)) {
                        val triArea = (0.433f * triSide * triSide).coerceAtLeast(0.0001f)
                        val triScore = field.evaluate(tcx.coerceIn(0f, 1f), tcy.coerceIn(0f, aspect), rng)
                        cells.add(
                            DraftCell(score = triScore, area = triArea) { threshold ->
                                listOf(
                                    PolygonPrimitive(
                                        vertices = triVerts,
                                        threshold = threshold,
                                        areaWeight = triArea,
                                        colorIndex = baseColor + 1 + dirIdx,
                                        secondaryColorIndex = baseColor + 3,
                                        shadeFactor = if (dirIdx == 0) 1.16f else 0.84f,
                                        gradientAngleRad = if (dirIdx == 0) -1.57f else 1.57f,
                                        strokeSeamNorm = 0.0022f
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

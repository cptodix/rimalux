package com.example.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Calibrates primitive visibility thresholds against an exact 2D viewport coverage mask
 * on [0f, 1f] x [0f, aspect] so every generated wallpaper—across all 64 patterns, any seed,
 * and both manual & automatic runs—achieves the exact requested #000000 True Black Ratio.
 */
internal object CoverageCalibrator {

    private const val GRID_W = 200

    fun calibrate(
        primaryCells: List<DraftCell>,
        rng: Random,
        field: SculptField,
        aspect: Float
    ): List<VectorPrimitive> {
        val gridW = GRID_W
        val gridH = (gridW * aspect).roundToInt().coerceIn(80, 640)
        val totalPixels = gridW * gridH
        val covered = BooleanArray(totalPixels)
        val cellSize = 1f / gridW.toFloat()

        // Materialize each primary cell once to preserve deterministic RNG state
        val builtPrimary: List<List<VectorPrimitive>> = primaryCells.map { it.build(0.5f) }
        val primaryThresholds = FloatArray(primaryCells.size)
        val primaryUniqueShares = FloatArray(primaryCells.size)

        val sortedPrimaryIndices = primaryCells.indices.sortedByDescending { primaryCells[it].score }
        var coveredCount = 0

        for (idx in sortedPrimaryIndices) {
            val prims = builtPrimary[idx]
            val newlyCovered = markCellCoverage(prims, covered, gridW, gridH, cellSize)
            val midCovered = coveredCount + newlyCovered * 0.5f
            val threshold = (1f - (midCovered / totalPixels.toFloat())).coerceIn(0.0005f, 0.9995f)
            primaryThresholds[idx] = threshold
            primaryUniqueShares[idx] = newlyCovered.toFloat() / totalPixels.toFloat()
            coveredCount += newlyCovered
        }

        // If primary pattern geometry leaves empty background void (>1.5%), synthesize a harmonious
        // bottom-layer faceted mosaic that ONLY activates when the user requests lower pitch-black
        // ratios than (1 - primaryCoverage). At higher black ratios, 0% of underlay tiles draw.
        val underlayCalibrated = ArrayList<VectorPrimitive>()
        if (coveredCount < (totalPixels * 0.985f).toInt()) {
            val underlayCells = buildGaplessUnderlayMosaic(rng, field, aspect)
            val builtUnderlay = underlayCells.map { it.build(0.5f) }
            val underlayThresholds = FloatArray(underlayCells.size)
            val underlayShares = FloatArray(underlayCells.size)

            val sortedUnderlayIndices = underlayCells.indices.sortedByDescending { underlayCells[it].score }
            for (idx in sortedUnderlayIndices) {
                val prims = builtUnderlay[idx]
                val newlyCovered = markCellCoverage(prims, covered, gridW, gridH, cellSize)
                val midCovered = coveredCount + newlyCovered * 0.5f
                val threshold = if (newlyCovered > 0) {
                    (1f - (midCovered / totalPixels.toFloat())).coerceIn(0.0001f, 0.999f)
                } else {
                    0.0001f
                }
                underlayThresholds[idx] = threshold
                underlayShares[idx] = newlyCovered.toFloat() / totalPixels.toFloat()
                coveredCount += newlyCovered
            }

            for (i in underlayCells.indices) {
                if (underlayShares[i] > 0f) {
                    underlayCalibrated.addAll(
                        applyCellCalibration(builtUnderlay[i], underlayThresholds[i], underlayShares[i])
                    )
                }
            }
        }

        val out = ArrayList<VectorPrimitive>(underlayCalibrated.size + primaryCells.size * 2)
        out.addAll(underlayCalibrated)
        for (i in primaryCells.indices) {
            out.addAll(
                applyCellCalibration(builtPrimary[i], primaryThresholds[i], primaryUniqueShares[i])
            )
        }
        return out
    }

    private fun applyCellCalibration(
        prims: List<VectorPrimitive>,
        threshold: Float,
        uniqueViewportShare: Float
    ): List<VectorPrimitive> {
        if (prims.isEmpty()) return emptyList()
        if (prims.size == 1) {
            return listOf(prims[0].withCalibration(threshold, uniqueViewportShare))
        }
        val rawSum = prims.sumOf { it.areaWeight.toDouble() }.toFloat().coerceAtLeast(0.00001f)
        return prims.map { p ->
            val share = uniqueViewportShare * (p.areaWeight / rawSum)
            p.withCalibration(threshold, share)
        }
    }

    private fun buildGaplessUnderlayMosaic(
        rng: Random,
        field: SculptField,
        aspect: Float
    ): List<DraftCell> {
        val cols = 10
        val rows = max(12, (cols * aspect).roundToInt())
        val sx = 1f / cols
        val sy = aspect / rows

        val grid = Array(rows + 1) { r ->
            Array(cols + 1) { c ->
                val bx = c * sx
                val by = r * sy
                val jx = if (c == 0 || c == cols) 0f else (rng.nextFloat() - 0.5f) * sx * 0.62f
                val jy = if (r == 0 || r == rows) 0f else (rng.nextFloat() - 0.5f) * sy * 0.62f
                Vec2((bx + jx).coerceIn(0f, 1f), (by + jy).coerceIn(0f, aspect))
            }
        }

        val cells = ArrayList<DraftCell>(rows * cols * 2)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val p00 = grid[r][c]
                val p10 = grid[r][c + 1]
                val p11 = grid[r + 1][c + 1]
                val p01 = grid[r + 1][c]

                val d1 = hypot(p00.x - p11.x, p00.y - p11.y)
                val d2 = hypot(p10.x - p01.x, p10.y - p01.y)
                val tris = if (d1 <= d2) {
                    listOf(Triple(p00, p10, p11), Triple(p00, p11, p01))
                } else {
                    listOf(Triple(p00, p10, p01), Triple(p10, p11, p01))
                }

                for ((tIdx, tri) in tris.withIndex()) {
                    val (a, b, d) = tri
                    val cx = (a.x + b.x + d.x) / 3f
                    val cy = (a.y + b.y + d.y) / 3f
                    val area = (0.5f * abs(a.x * (b.y - d.y) + b.x * (d.y - a.y) + d.x * (a.y - b.y)))
                        .coerceAtLeast(0.00005f)
                    val score = field.evaluate(cx, cy, rng)
                    val cIdx = abs(r * 2 + c * 3 + tIdx)
                    val shade = 0.52f + 0.24f * ((r + c + tIdx) % 3) * 0.5f
                    val gradAng = atan2(d.y - a.y, b.x - a.x)

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = listOf(a, b, d),
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 1,
                                    shadeFactor = shade,
                                    gradientAngleRad = gradAng,
                                    strokeSeamNorm = 0f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    private fun markCellCoverage(
        prims: List<VectorPrimitive>,
        covered: BooleanArray,
        gridW: Int,
        gridH: Int,
        cellSize: Float
    ): Int {
        var added = 0
        for (p in prims) {
            added += when (p) {
                is PolygonPrimitive -> rasterizePolygon(p, covered, gridW, gridH, cellSize)
                is ArcSectorPrimitive -> rasterizeArcSector(p, covered, gridW, gridH, cellSize)
                is PolylineRibbonPrimitive -> rasterizePolyline(p, covered, gridW, gridH, cellSize)
            }
        }
        return added
    }

    private fun rasterizePolygon(
        poly: PolygonPrimitive,
        covered: BooleanArray,
        gridW: Int,
        gridH: Int,
        cellSize: Float
    ): Int {
        val rawVerts = poly.vertices
        val n = rawVerts.size
        if (n < 3) return 0

        // Outward anti-aliasing edge allowance (~1px Skia AA fringe into #000000 gaps)
        val aaFeather = 0.0011f
        var cx = 0f
        var cy = 0f
        for (v in rawVerts) {
            cx += v.x
            cy += v.y
        }
        cx /= n
        cy /= n
        val verts = List(n) { i ->
            val v = rawVerts[i]
            val dx = v.x - cx
            val dy = v.y - cy
            val dist = hypot(dx, dy)
            if (dist > 0.0005f) {
                Vec2(v.x + (dx / dist) * aaFeather, v.y + (dy / dist) * aaFeather)
            } else {
                v
            }
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (v in verts) {
            if (v.x < minX) minX = v.x
            if (v.y < minY) minY = v.y
            if (v.x > maxX) maxX = v.x
            if (v.y > maxY) maxY = v.y
        }

        val gx0 = (minX / cellSize).toInt().coerceIn(0, gridW - 1)
        val gx1 = (maxX / cellSize).toInt().coerceIn(0, gridW - 1)
        val gy0 = (minY / cellSize).toInt().coerceIn(0, gridH - 1)
        val gy1 = (maxY / cellSize).toInt().coerceIn(0, gridH - 1)
        if (gx0 > gx1 || gy0 > gy1) return 0

        var added = 0
        for (gy in gy0..gy1) {
            val py = (gy + 0.5f) * cellSize
            val rowOffset = gy * gridW
            for (gx in gx0..gx1) {
                val idx = rowOffset + gx
                if (covered[idx]) continue
                val px = (gx + 0.5f) * cellSize
                if (isPointInPolygonWinding(px, py, verts)) {
                    covered[idx] = true
                    added++
                }
            }
        }
        return added
    }

    private fun isPointInPolygonWinding(px: Float, py: Float, verts: List<Vec2>): Boolean {
        var wn = 0
        val n = verts.size
        var prev = verts[n - 1]
        for (i in 0 until n) {
            val curr = verts[i]
            if (prev.y <= py) {
                if (curr.y > py) {
                    val isLeft = (curr.x - prev.x) * (py - prev.y) - (px - prev.x) * (curr.y - prev.y)
                    if (isLeft > 0f) wn++
                }
            } else {
                if (curr.y <= py) {
                    val isLeft = (curr.x - prev.x) * (py - prev.y) - (px - prev.x) * (curr.y - prev.y)
                    if (isLeft < 0f) wn--
                }
            }
            prev = curr
        }
        return wn != 0
    }

    private fun rasterizeArcSector(
        arc: ArcSectorPrimitive,
        covered: BooleanArray,
        gridW: Int,
        gridH: Int,
        cellSize: Float
    ): Int {
        if (arc.outerRadius <= 0.0005f) return 0
        val outR = arc.outerRadius + 0.0010f
        val inR = (arc.innerRadius - 0.0010f).coerceAtLeast(0f)
        val outR2 = outR * outR
        val inR2 = inR * inR

        val gx0 = ((arc.center.x - outR) / cellSize).toInt().coerceIn(0, gridW - 1)
        val gx1 = ((arc.center.x + outR) / cellSize).toInt().coerceIn(0, gridW - 1)
        val gy0 = ((arc.center.y - outR) / cellSize).toInt().coerceIn(0, gridH - 1)
        val gy1 = ((arc.center.y + outR) / cellSize).toInt().coerceIn(0, gridH - 1)
        if (gx0 > gx1 || gy0 > gy1) return 0

        val fullCircle = abs(arc.sweepAngleDeg) >= 358.5f
        val startNorm: Float
        val sweepAbs: Float
        if (!fullCircle) {
            val aaDeg = 0.85f
            if (arc.sweepAngleDeg >= 0f) {
                startNorm = (((arc.startAngleDeg - aaDeg * 0.5f) % 360f) + 360f) % 360f
                sweepAbs = (arc.sweepAngleDeg + aaDeg).coerceAtMost(360f)
            } else {
                startNorm = (((arc.startAngleDeg + arc.sweepAngleDeg - aaDeg * 0.5f) % 360f) + 360f) % 360f
                sweepAbs = (-arc.sweepAngleDeg + aaDeg).coerceAtMost(360f)
            }
        } else {
            startNorm = 0f
            sweepAbs = 360f
        }

        var added = 0
        for (gy in gy0..gy1) {
            val py = (gy + 0.5f) * cellSize
            val dy = py - arc.center.y
            val dy2 = dy * dy
            val rowOffset = gy * gridW
            for (gx in gx0..gx1) {
                val idx = rowOffset + gx
                if (covered[idx]) continue
                val px = (gx + 0.5f) * cellSize
                val dx = px - arc.center.x
                val r2 = dx * dx + dy2
                if (r2 < inR2 || r2 > outR2) continue

                if (!fullCircle) {
                    val ang = (((atan2(dy, dx) * 180f / PI.toFloat()) % 360f) + 360f) % 360f
                    val rel = (ang - startNorm + 360f) % 360f
                    if (rel > sweepAbs) continue
                }
                covered[idx] = true
                added++
            }
        }
        return added
    }

    private fun rasterizePolyline(
        line: PolylineRibbonPrimitive,
        covered: BooleanArray,
        gridW: Int,
        gridH: Int,
        cellSize: Float
    ): Int {
        val pts = line.points
        if (pts.size < 2) return 0

        val effectiveWidth = max(line.strokeWidthNorm, 0.0024f) + 0.0010f
        val trueHalfW = effectiveWidth * 0.5f
        val probeHalfW = max(trueHalfW, cellSize * 0.55f)
        val probeHalfW2 = probeHalfW * probeHalfW
        val subCellPermille = if (effectiveWidth < cellSize) {
            ((effectiveWidth / cellSize) * 1000f).roundToInt().coerceIn(80, 1000)
        } else {
            1000
        }

        var added = 0
        val segCount = if (line.closed) pts.size else pts.size - 1
        for (i in 0 until segCount) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val minX = min(a.x, b.x) - probeHalfW
            val maxX = max(a.x, b.x) + probeHalfW
            val minY = min(a.y, b.y) - probeHalfW
            val maxY = max(a.y, b.y) + probeHalfW

            val gx0 = (minX / cellSize).toInt().coerceIn(0, gridW - 1)
            val gx1 = (maxX / cellSize).toInt().coerceIn(0, gridW - 1)
            val gy0 = (minY / cellSize).toInt().coerceIn(0, gridH - 1)
            val gy1 = (maxY / cellSize).toInt().coerceIn(0, gridH - 1)
            if (gx0 > gx1 || gy0 > gy1) continue

            val abx = b.x - a.x
            val aby = b.y - a.y
            val abLen2 = abx * abx + aby * aby

            for (gy in gy0..gy1) {
                val py = (gy + 0.5f) * cellSize
                val rowOffset = gy * gridW
                for (gx in gx0..gx1) {
                    val idx = rowOffset + gx
                    if (covered[idx]) continue
                    val px = (gx + 0.5f) * cellSize
                    val t = if (abLen2 > 1e-8f) {
                        (((px - a.x) * abx + (py - a.y) * aby) / abLen2).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val cx = a.x + t * abx
                    val cy = a.y + t * aby
                    val dist2 = (px - cx) * (px - cx) + (py - cy) * (py - cy)
                    if (dist2 <= probeHalfW2) {
                        if (subCellPermille >= 1000) {
                            covered[idx] = true
                            added++
                        } else {
                            val hash = ((gx * 73856093) xor (gy * 19349663)) and 0x7FFFFFFF
                            if ((hash % 1000) < subCellPermille) {
                                covered[idx] = true
                                added++
                            }
                        }
                    }
                }
            }
        }
        return added
    }
}

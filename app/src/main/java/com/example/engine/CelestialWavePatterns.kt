package com.example.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

internal object CelestialWavePatterns {

    private const val TWO_PI = (2.0 * PI).toFloat()

    // 17. Lissajous Phase-Locked Orbital Harmonic Ribbons
    fun buildLissajousHarmonic(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val ratioPairs = listOf(3 to 4, 3 to 5, 4 to 5, 5 to 6, 5 to 7)
        val (freqX, freqY) = ratioPairs[rng.nextInt(ratioPairs.size)]
        val ribbonFamilies = (5 + (intricacy * 5f).roundToInt()).coerceIn(5, 10)
        val totalSteps = (180 + (intricacy * 90f).roundToInt()).coerceIn(180, 280)
        val chunkSteps = 6
        val baseDelta = rng.nextFloat() * PI.toFloat()
        val cells = ArrayList<DraftCell>((totalSteps / chunkSteps) * ribbonFamilies)

        for (fam in 0 until ribbonFamilies) {
            val famFrac = fam.toFloat() / ribbonFamilies
            val delta = baseDelta + famFrac * 0.68f
            val rx = 0.44f - famFrac * 0.06f
            val ry = aspect * (0.44f - famFrac * 0.05f)
            val halfW = (0.014f + (1f - intricacy * 0.35f) * 0.012f) * (1f - famFrac * 0.25f)

            val spine = List(totalSteps + 1) { s ->
                val t = (s.toFloat() / totalSteps) * TWO_PI
                Vec2(
                    0.5f + rx * sin(freqX * t + delta),
                    aspect * 0.5f + ry * sin(freqY * t)
                )
            }

            var idx = 0
            while (idx + 1 < spine.size) {
                val endIdx = min(idx + chunkSteps, spine.size - 1)
                val left = ArrayList<Vec2>(endIdx - idx + 1)
                val right = ArrayList<Vec2>(endIdx - idx + 1)
                for (k in idx..endIdx) {
                    val prev = spine[(k - 1).coerceAtLeast(0)]
                    val next = spine[(k + 1).coerceAtMost(spine.lastIndex)]
                    val tx = next.x - prev.x
                    val ty = next.y - prev.y
                    val len = hypot(tx, ty).coerceAtLeast(0.0001f)
                    val nx = -ty / len
                    val ny = tx / len
                    val env = 0.55f + 0.45f * sin((k.toFloat() / totalSteps) * TWO_PI * 3f)
                    val w = halfW * env
                    left.add(Vec2(spine[k].x + nx * w, spine[k].y + ny * w))
                    right.add(Vec2(spine[k].x - nx * w, spine[k].y - ny * w))
                }
                val mid = spine[(idx + endIdx) / 2]
                val poly = left + right.asReversed()
                val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                val area = (halfW * 2f * 0.06f).coerceAtLeast(0.0001f)
                val cIdx = fam + (idx / 24)
                val ang = atan2(spine[endIdx].y - spine[idx].y, spine[endIdx].x - spine[idx].x)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = poly,
                                threshold = threshold,
                                areaWeight = area * 0.85f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.84f + 0.30f * (1f - famFrac),
                                gradientAngleRad = ang,
                                strokeSeamNorm = 0.0012f
                            ),
                            PolylineRibbonPrimitive(
                                points = left,
                                strokeWidthNorm = 0.0020f,
                                threshold = threshold,
                                areaWeight = area * 0.15f,
                                colorIndex = cIdx + 2,
                                secondaryColorIndex = cIdx,
                                shadeFactor = 1.18f,
                                gradientAngleRad = ang
                            )
                        )
                    }
                )
                idx = endIdx
            }
        }
        return cells
    }

    // 18. Equiangular Logarithmic Nautilus Spiral Chambers
    fun buildLogarithmicNautilus(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * (0.48f + (rng.nextFloat() - 0.5f) * 0.08f))
        val arms = listOf(2, 3, 4)[rng.nextInt(3)]
        val turns = 3.6f
        val septaPerTurn = (20 + (intricacy * 14f).roundToInt()).coerceIn(20, 36)
        val totalSepta = (turns * septaPerTurn).roundToInt()
        val maxR = hypot(0.58f, aspect * 0.58f)
        val minR = 0.016f
        val growthB = kotlin.math.ln((maxR / minR).toDouble()).toFloat() / (turns * TWO_PI)
        val rotOffset = rng.nextFloat() * TWO_PI
        val spinSign = if (rng.nextBoolean()) 1f else -1f

        val cells = ArrayList<DraftCell>(arms * totalSepta)
        for (arm in 0 until arms) {
            val armOffset = rotOffset + arm * (TWO_PI / arms)
            for (s in 0 until totalSepta) {
                val th0 = (s.toFloat() / septaPerTurn) * TWO_PI
                val th1 = ((s + 1).toFloat() / septaPerTurn) * TWO_PI
                val r0Inner = minR * exp(growthB * (th0 - TWO_PI / arms))
                val r0Outer = minR * exp(growthB * th0)
                val r1Inner = minR * exp(growthB * (th1 - TWO_PI / arms))
                val r1Outer = minR * exp(growthB * th1)

                val a0 = armOffset + spinSign * th0
                val a1 = armOffset + spinSign * th1
                val aMid = (a0 + a1) * 0.5f
                val rMid = (r0Inner + r1Outer) * 0.5f
                val cx = center.x + rMid * cos(aMid)
                val cy = center.y + rMid * sin(aMid)
                if (cx < -0.2f || cx > 1.2f || cy < -0.2f || cy > aspect + 0.2f) continue

                // Curved chamber quad with septal arch
                val v0 = Vec2(center.x + r0Inner * cos(a0), center.y + r0Inner * sin(a0))
                val v1 = Vec2(center.x + r0Outer * cos(a0), center.y + r0Outer * sin(a0))
                val v2 = Vec2(center.x + r1Outer * cos(a1), center.y + r1Outer * sin(a1))
                val v3 = Vec2(center.x + r1Inner * cos(a1), center.y + r1Inner * sin(a1))
                val rawChamber = listOf(v0, v1, v2, v3)
                val qx = rawChamber.sumOf { it.x.toDouble() }.toFloat() * 0.25f
                val qy = rawChamber.sumOf { it.y.toDouble() }.toFloat() * 0.25f
                val chamber = rawChamber.map { Vec2(it.x + (qx - it.x) * 0.07f, it.y + (qy - it.y) * 0.07f) }
                val innerPearl = chamber.map { Vec2(qx + (it.x - qx) * 0.45f, qy + (it.y - qy) * 0.45f) }

                val area = (0.5f * (r1Outer * r1Outer - r0Inner * r0Inner) * abs(th1 - th0)).coerceAtLeast(0.00008f)
                val score = field.evaluate(qx.coerceIn(0f, 1f), qy.coerceIn(0f, aspect), rng)
                val cIdx = arm * 2 + (s / 3)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = chamber,
                                threshold = threshold,
                                areaWeight = area * 0.74f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.82f + 0.28f * ((s % 5) / 4f),
                                gradientAngleRad = aMid,
                                strokeSeamNorm = 0.0018f
                            ),
                            PolygonPrimitive(
                                vertices = innerPearl,
                                threshold = threshold,
                                areaWeight = area * 0.26f,
                                colorIndex = cIdx + 2,
                                secondaryColorIndex = cIdx,
                                shadeFactor = 1.18f,
                                gradientAngleRad = aMid + PI.toFloat() * 0.5f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 19. Chladni Acoustic Standing-Wave Cymatics
    fun buildChladniCymatics(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (9 + (intricacy * 6f).roundToInt()).coerceIn(9, 15)
        val rows = max(14, (cols * aspect).roundToInt())
        val nMode = 2 + rng.nextInt(3)
        var mMode = 3 + rng.nextInt(4)
        if (mMode == nMode) mMode++
        val coefA = 1.0f
        val coefB = if (rng.nextBoolean()) 1.0f else -1.0f
        val sx = 1f / cols
        val sy = aspect / rows

        val cells = ArrayList<DraftCell>(cols * rows)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = (c + 0.5f) * sx
                val cy = (r + 0.5f) * sy
                // Mirror-symmetric normalized plate coordinates [-1, 1]
                val ux = (cx - 0.5f) * 2f
                val uy = ((cy / aspect) - 0.5f) * 2f
                val wave = coefA * sin(PI.toFloat() * nMode * ux) * sin(PI.toFloat() * mMode * uy) +
                    coefB * sin(PI.toFloat() * mMode * ux) * sin(PI.toFloat() * nMode * uy)
                val nodalProximity = 1f - (abs(wave) * 0.65f).coerceIn(0f, 1f)

                val score = 0.62f * nodalProximity + 0.38f * field.evaluate(cx, cy, rng)
                val scaleCell = 0.48f + 0.46f * nodalProximity
                val hw = sx * 0.5f * scaleCell
                val hh = sy * 0.5f * scaleCell

                val diamond = if ((r + c) % 2 == 0) {
                    listOf(
                        Vec2(cx, cy - hh),
                        Vec2(cx + hw, cy),
                        Vec2(cx, cy + hh),
                        Vec2(cx - hw, cy)
                    )
                } else {
                    val k = 0.78f
                    listOf(
                        Vec2(cx - hw * k, cy - hh * k),
                        Vec2(cx + hw * k, cy - hh * k),
                        Vec2(cx + hw * k, cy + hh * k),
                        Vec2(cx - hw * k, cy + hh * k)
                    )
                }
                val innerCore = diamond.map { Vec2(cx + (it.x - cx) * 0.46f, cy + (it.y - cy) * 0.46f) }
                val area = (sx * sy * scaleCell * scaleCell).coerceAtLeast(0.00008f)
                val cIdx = (abs(wave * 3f).toInt()) + (if (wave >= 0f) 0 else 2)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        val out = ArrayList<VectorPrimitive>(2)
                        out.add(
                            PolygonPrimitive(
                                vertices = diamond,
                                threshold = threshold,
                                areaWeight = area * 0.76f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.78f + 0.40f * nodalProximity,
                                gradientAngleRad = wave * 1.2f,
                                strokeSeamNorm = 0.0014f
                            )
                        )
                        if (nodalProximity > 0.58f) {
                            out.add(
                                PolygonPrimitive(
                                    vertices = innerCore,
                                    threshold = threshold,
                                    areaWeight = area * 0.24f,
                                    colorIndex = cIdx + 2,
                                    secondaryColorIndex = cIdx,
                                    shadeFactor = 1.16f,
                                    gradientAngleRad = -wave * 1.2f
                                )
                            )
                        }
                        out
                    }
                )
            }
        }
        return cells
    }

    // 20. Dual-Focus Moiré Interference Wave Rings
    fun buildMoireInterference(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val f1 = Vec2(0.34f + rng.nextFloat() * 0.14f, aspect * (0.34f + rng.nextFloat() * 0.14f))
        val f2 = Vec2(1f - f1.x, aspect - f1.y)
        val rings = (14 + (intricacy * 10f).roundToInt()).coerceIn(14, 26)
        val sectors = (16 + (intricacy * 8f).roundToInt()).coerceIn(16, 26)
        val maxR = hypot(0.75f, aspect * 0.75f)
        val cells = ArrayList<DraftCell>(rings * sectors * 2)

        for ((focusIdx, focus) in listOf(f1, f2).withIndex()) {
            for (ring in 0 until rings) {
                val rIn = (ring + 0.18f) * (maxR / rings)
                val rOut = (ring + 0.76f) * (maxR / rings)
                val stepDeg = 360f / sectors
                val offsetDeg = if ((ring + focusIdx) % 2 == 0) 0f else stepDeg * 0.5f

                for (sec in 0 until sectors) {
                    val startDeg = offsetDeg + sec * stepDeg + 1.4f
                    val sweepDeg = stepDeg - 2.8f
                    val midRad = (startDeg + sweepDeg * 0.5f) * PI.toFloat() / 180f
                    val rMid = (rIn + rOut) * 0.5f
                    val mx = focus.x + rMid * cos(midRad)
                    val my = focus.y + rMid * sin(midRad)
                    if (mx < -0.08f || mx > 1.08f || my < -0.08f || my > aspect + 0.08f) continue

                    val dOther = hypot(mx - (if (focusIdx == 0) f2.x else f1.x), my - (if (focusIdx == 0) f2.y else f1.y))
                    val beat = 0.5f + 0.5f * cos((rMid - dOther) * 28f)
                    val score = 0.55f * beat + 0.45f * field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
                    val area = (0.5f * (rOut * rOut - rIn * rIn) * (sweepDeg * PI.toFloat() / 180f)).coerceAtLeast(0.00008f)
                    val cIdx = focusIdx * 2 + (ring % 3)

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                ArcSectorPrimitive(
                                    center = focus,
                                    innerRadius = rIn,
                                    outerRadius = rOut,
                                    startAngleDeg = startDeg,
                                    sweepAngleDeg = sweepDeg,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 1,
                                    shadeFactor = 0.80f + 0.36f * beat,
                                    gradientAngleRad = midRad
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 21. Magnetic Dipole Vector Flux Streamlines
    fun buildMagneticDipole(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val tilt = (rng.nextFloat() - 0.5f) * 0.8f
        val sep = aspect * 0.24f
        val cx = 0.5f
        val cy = aspect * 0.5f
        val north = Vec2(cx + sin(tilt) * sep, cy - cos(tilt) * sep)
        val south = Vec2(cx - sin(tilt) * sep, cy + cos(tilt) * sep)

        val numSeeds = (26 + (intricacy * 18f).roundToInt()).coerceIn(26, 46)
        val steps = 42
        val stepLen = 0.028f
        val cells = ArrayList<DraftCell>(numSeeds * 7 + 8)

        // Pole core medallions
        for ((pIdx, pole) in listOf(north, south).withIndex()) {
            for (ring in 0 until 3) {
                val rIn = if (ring == 0) 0f else 0.026f * ring
                val rOut = 0.022f * (ring + 1)
                val area = PI.toFloat() * (rOut * rOut - rIn * rIn)
                cells.add(
                    DraftCell(score = 0.96f - ring * 0.04f, area = area) { threshold ->
                        listOf(
                            ArcSectorPrimitive(
                                center = pole,
                                innerRadius = rIn,
                                outerRadius = rOut,
                                startAngleDeg = 0f,
                                sweepAngleDeg = 360f,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = pIdx * 2 + ring,
                                secondaryColorIndex = pIdx * 2 + ring + 1,
                                shadeFactor = 1.22f,
                                gradientAngleRad = tilt
                            )
                        )
                    }
                )
            }
        }

        fun dipoleDir(x: Float, y: Float): Vec2 {
            val dx1 = x - north.x
            val dy1 = y - north.y
            val r1Sq = (dx1 * dx1 + dy1 * dy1).coerceAtLeast(0.0008f)
            val r1 = kotlin.math.sqrt(r1Sq)
            val dx2 = x - south.x
            val dy2 = y - south.y
            val r2Sq = (dx2 * dx2 + dy2 * dy2).coerceAtLeast(0.0008f)
            val r2 = kotlin.math.sqrt(r2Sq)
            val bx = dx1 / (r1Sq * r1) - dx2 / (r2Sq * r2)
            val by = dy1 / (r1Sq * r1) - dy2 / (r2Sq * r2)
            val mag = hypot(bx, by).coerceAtLeast(1e-6f)
            return Vec2(bx / mag, by / mag)
        }

        for (s in 0 until numSeeds) {
            val launchAng = (s.toFloat() / numSeeds) * TWO_PI
            var px = north.x + 0.038f * cos(launchAng)
            var py = north.y + 0.038f * sin(launchAng)
            val spine = ArrayList<Vec2>(steps + 1)
            val norms = ArrayList<Vec2>(steps + 1)
            spine.add(Vec2(px, py))

            for (st in 0 until steps) {
                val dir = dipoleDir(px, py)
                norms.add(Vec2(-dir.y, dir.x))
                px += dir.x * stepLen
                py += dir.y * stepLen
                spine.add(Vec2(px, py))
                if (hypot(px - south.x, py - south.y) < 0.035f) break
            }
            if (spine.size < 4) continue
            val lastDir = dipoleDir(px, py)
            norms.add(Vec2(-lastDir.y, lastDir.x))

            val chunk = 5
            var k = 0
            while (k + 1 < spine.size) {
                val endK = min(k + chunk, spine.size - 1)
                val left = ArrayList<Vec2>(endK - k + 1)
                val right = ArrayList<Vec2>(endK - k + 1)
                for (i in k..endK) {
                    val u = i.toFloat() / (spine.size - 1).coerceAtLeast(1)
                    val hw = (0.008f + 0.016f * sin(u * PI.toFloat())) * (1.05f - intricacy * 0.25f)
                    left.add(Vec2(spine[i].x + norms[i].x * hw, spine[i].y + norms[i].y * hw))
                    right.add(Vec2(spine[i].x - norms[i].x * hw, spine[i].y - norms[i].y * hw))
                }
                val mid = spine[(k + endK) / 2]
                if (mid.x in -0.15f..1.15f && mid.y in -0.15f..(aspect + 0.15f)) {
                    val poly = left + right.asReversed()
                    val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                    val area = (stepLen * (endK - k) * 0.022f).coerceAtLeast(0.00008f)
                    val cIdx = (s % 4) + (k / 8)
                    val ang = atan2(spine[endK].y - spine[k].y, spine[endK].x - spine[k].x)

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = poly,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 1,
                                    shadeFactor = 0.85f + 0.30f * sin((k.toFloat() / spine.size) * PI.toFloat()),
                                    gradientAngleRad = ang,
                                    strokeSeamNorm = 0.0012f
                                )
                            )
                        }
                    )
                }
                k = endK
            }
        }
        return cells
    }

    // 22. Borealis Aurora Harmonic Curtains
    fun buildAuroraCurtains(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val curtains = (5 + (intricacy * 4f).roundToInt()).coerceIn(5, 9)
        val xSlices = (24 + (intricacy * 16f).roundToInt()).coerceIn(24, 42)
        val phaseBase = rng.nextFloat() * TWO_PI
        val cells = ArrayList<DraftCell>(curtains * xSlices)

        for (cur in 0 until curtains) {
            val baseAlt = aspect * (0.14f + 0.72f * (cur.toFloat() / (curtains - 1).coerceAtLeast(1)))
            val curtainHeight = aspect * (0.20f + 0.10f * sin(cur * 1.3f))
            val freqA = 3.2f + (cur % 3) * 1.4f
            val freqB = 7.5f + (cur % 2) * 2.5f

            for (s in 0 until xSlices) {
                val u0 = s.toFloat() / xSlices
                val u1 = (s + 1).toFloat() / xSlices
                val uMid = (u0 + u1) * 0.5f

                fun foldX(u: Float): Float =
                    u + 0.045f * sin(u * freqA + phaseBase + cur * 0.9f)

                fun baseWaveY(u: Float): Float =
                    baseAlt + 0.07f * aspect * sin(u * freqA + phaseBase + cur * 1.1f) +
                        0.025f * aspect * cos(u * freqB - phaseBase)

                val x0 = foldX(u0)
                val x1 = foldX(u1)
                val yb0 = baseWaveY(u0)
                val yb1 = baseWaveY(u1)
                val env = (0.45f + 0.55f * (0.5f + 0.5f * sin(uMid * freqB + cur))).coerceIn(0.25f, 1.0f)
                val yt0 = yb0 - curtainHeight * env
                val yt1 = yb1 - curtainHeight * env

                val blade = listOf(
                    Vec2(x0, yt0),
                    Vec2(x1, yt1),
                    Vec2(x1, yb1),
                    Vec2(x0, yb0)
                )
                val cx = (x0 + x1) * 0.5f
                val cy = (yt0 + yb1) * 0.5f
                val area = (abs(x1 - x0).coerceAtLeast(0.008f) * curtainHeight * env).coerceAtLeast(0.0001f)
                val score = 0.45f * env + 0.55f * field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val cIdx = cur + (s / 8)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = blade,
                                threshold = threshold,
                                areaWeight = area * 0.85f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 2,
                                shadeFactor = 0.74f + 0.46f * env,
                                gradientAngleRad = -PI.toFloat() * 0.5f,
                                strokeSeamNorm = 0.0008f
                            ),
                            PolylineRibbonPrimitive(
                                points = listOf(Vec2(x0, yb0), Vec2(x1, yb1)),
                                strokeWidthNorm = 0.0032f,
                                threshold = threshold,
                                areaWeight = area * 0.15f,
                                colorIndex = cIdx + 1,
                                secondaryColorIndex = cIdx,
                                shadeFactor = 1.24f,
                                gradientAngleRad = 0f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 23. Apollonian Tangent Circle & Crescent Packing
    fun buildApollonianCircles(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        data class Disk(val x: Float, val y: Float, val r: Float)
        val disks = ArrayList<Disk>(180)
        val targetCount = (95 + (intricacy * 85f).roundToInt()).coerceIn(95, 190)

        // Seed harmonious golden-ratio anchor circles, then greedily pack largest tangent circles
        for (attempt in 0 until 2600) {
            if (disks.size >= targetCount) break
            val px = 0.04f + rng.nextFloat() * 0.92f
            val py = aspect * (0.03f + rng.nextFloat() * 0.94f)
            var maxAllowed = min(min(px, 1f - px), min(py, aspect - py)).coerceAtMost(0.24f)
            var insideExisting = false
            for (d in disks) {
                val dist = hypot(px - d.x, py - d.y) - d.r - 0.004f
                if (dist < 0.012f) {
                    insideExisting = true
                    break
                }
                if (dist < maxAllowed) maxAllowed = dist
            }
            if (!insideExisting && maxAllowed >= 0.014f) {
                disks.add(Disk(px, py, maxAllowed))
            }
        }

        val cells = ArrayList<DraftCell>(disks.size * 2)
        for ((idx, d) in disks.withIndex()) {
            val score = field.evaluate(d.x, d.y, rng)
            val area = PI.toFloat() * d.r * d.r
            val cIdx = idx % 6
            val crescentAngle = rng.nextFloat() * 360f

            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    if (d.r > 0.055f) {
                        listOf(
                            ArcSectorPrimitive(
                                center = Vec2(d.x, d.y),
                                innerRadius = d.r * 0.56f,
                                outerRadius = d.r,
                                startAngleDeg = 0f,
                                sweepAngleDeg = 360f,
                                threshold = threshold,
                                areaWeight = area * 0.68f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.94f,
                                gradientAngleRad = crescentAngle * PI.toFloat() / 180f
                            ),
                            ArcSectorPrimitive(
                                center = Vec2(d.x, d.y),
                                innerRadius = 0f,
                                outerRadius = d.r * 0.42f,
                                startAngleDeg = crescentAngle,
                                sweepAngleDeg = 285f,
                                threshold = threshold,
                                areaWeight = area * 0.32f,
                                colorIndex = cIdx + 2,
                                secondaryColorIndex = cIdx,
                                shadeFactor = 1.18f,
                                gradientAngleRad = (crescentAngle + 90f) * PI.toFloat() / 180f
                            )
                        )
                    } else {
                        listOf(
                            ArcSectorPrimitive(
                                center = Vec2(d.x, d.y),
                                innerRadius = d.r * 0.36f,
                                outerRadius = d.r,
                                startAngleDeg = 0f,
                                sweepAngleDeg = 360f,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 2,
                                shadeFactor = 1.05f,
                                gradientAngleRad = 0.78f
                            )
                        )
                    }
                }
            )
        }
        return cells
    }

    // 24. Crystalline Agate Geode Strata
    fun buildShatteredGeode(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f + (rng.nextFloat() - 0.5f) * 0.14f, aspect * (0.5f + (rng.nextFloat() - 0.5f) * 0.14f))
        val bands = (13 + (intricacy * 10f).roundToInt()).coerceIn(13, 24)
        val facets = (18 + (intricacy * 12f).roundToInt()).coerceIn(18, 32)
        val maxR = hypot(0.66f, aspect * 0.66f)
        val lobes = 5 + rng.nextInt(4)
        val lobePhase = rng.nextFloat() * TWO_PI
        val facetFactors = FloatArray(facets + 1) { i ->
            val th = (i.toFloat() / facets) * TWO_PI
            1f + 0.14f * sin(lobes * th + lobePhase) + 0.07f * cos((lobes * 2 - 1) * th - lobePhase)
        }
        facetFactors[facets] = facetFactors[0]

        val cells = ArrayList<DraftCell>(bands * facets)
        for (b in 0 until bands) {
            val rBase0 = ((b + 0.12f) / bands).pow(0.88f) * maxR
            val rBase1 = ((b + 0.90f) / bands).pow(0.88f) * maxR

            for (f in 0 until facets) {
                val a0 = (f.toFloat() / facets) * TWO_PI
                val a1 = ((f + 1).toFloat() / facets) * TWO_PI
                val f0 = facetFactors[f]
                val f1 = facetFactors[f + 1]

                val quad = listOf(
                    Vec2(center.x + rBase0 * f0 * cos(a0), center.y + rBase0 * f0 * sin(a0)),
                    Vec2(center.x + rBase1 * f0 * cos(a0), center.y + rBase1 * f0 * sin(a0)),
                    Vec2(center.x + rBase1 * f1 * cos(a1), center.y + rBase1 * f1 * sin(a1)),
                    Vec2(center.x + rBase0 * f1 * cos(a1), center.y + rBase0 * f1 * sin(a1))
                )
                val cx = quad.sumOf { it.x.toDouble() }.toFloat() * 0.25f
                val cy = quad.sumOf { it.y.toDouble() }.toFloat() * 0.25f
                if (cx < -0.15f || cx > 1.15f || cy < -0.15f || cy > aspect + 0.15f) continue

                val inset = quad.map { Vec2(it.x + (cx - it.x) * 0.05f, it.y + (cy - it.y) * 0.05f) }
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val area = (0.5f * (rBase1 * rBase1 - rBase0 * rBase0) * (TWO_PI / facets)).coerceAtLeast(0.00008f)
                val cIdx = b + (f % 2)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = inset,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 2,
                                shadeFactor = 0.76f + 0.38f * ((b % 4) / 3f),
                                gradientAngleRad = (a0 + a1) * 0.5f,
                                strokeSeamNorm = 0.0018f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }
}

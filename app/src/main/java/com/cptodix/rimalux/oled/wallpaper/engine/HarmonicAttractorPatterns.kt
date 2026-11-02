package com.cptodix.rimalux.oled.wallpaper.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure mathematical generators for Patterns #65 - #72 (4 Flow & Waves + 4 Symmetry & Sacred).
 * Strictly `kotlin.*` standard library only.
 */
internal object HarmonicAttractorPatterns {

    private const val TWO_PI = (2.0 * PI).toFloat()

    // 65. Clifford Strange Attractor (Iterated Trigonometric Phase Veil)
    fun buildCliffordAttractor(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val presets = arrayOf(
            floatArrayOf(-1.4f, 1.6f, 1.0f, 0.7f),
            floatArrayOf(1.5f, -1.8f, 1.6f, 0.9f),
            floatArrayOf(-1.7f, 1.3f, -0.1f, -1.2f),
            floatArrayOf(-1.8f, -2.0f, -0.5f, -0.9f)
        )
        val p = presets[rng.nextInt(presets.size)]
        val a = p[0] + (rng.nextFloat() - 0.5f) * 0.06f
        val b = p[1] + (rng.nextFloat() - 0.5f) * 0.06f
        val c = p[2] + (rng.nextFloat() - 0.5f) * 0.06f
        val d = p[3] + (rng.nextFloat() - 0.5f) * 0.06f

        val strands = (22 + (intricacy * 16f).roundToInt()).coerceIn(22, 38)
        val stepsPerStrand = 42
        val rot = rng.nextFloat() * TWO_PI
        val cosR = cos(rot)
        val sinR = sin(rot)

        val rawTrajectories = ArrayList<List<Vec2>>(strands)
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        for (s in 0 until strands) {
            val ang = (s.toFloat() / strands) * TWO_PI
            var x = 0.65f * cos(ang) + (rng.nextFloat() - 0.5f) * 0.15f
            var y = 0.65f * sin(ang) + (rng.nextFloat() - 0.5f) * 0.15f
            repeat(12) {
                val nx = sin(a * y) + c * cos(a * x)
                val ny = sin(b * x) + d * cos(b * y)
                x = nx
                y = ny
            }
            val pts = ArrayList<Vec2>(stepsPerStrand)
            for (st in 0 until stepsPerStrand) {
                // Smooth sub-stepped phase flow along the Clifford vector field
                val tx = sin(a * y) + c * cos(a * x)
                val ty = sin(b * x) + d * cos(b * y)
                x += (tx - x) * 0.34f
                y += (ty - y) * 0.34f
                val rx = x * cosR - y * sinR
                val ry = x * sinR + y * cosR
                pts.add(Vec2(rx, ry))
                if (rx < minX) minX = rx
                if (rx > maxX) maxX = rx
                if (ry < minY) minY = ry
                if (ry > maxY) maxY = ry
            }
            rawTrajectories.add(pts)
        }

        val spanX = (maxX - minX).coerceAtLeast(0.5f)
        val spanY = (maxY - minY).coerceAtLeast(0.5f)
        val cells = ArrayList<DraftCell>(strands * 8)

        for ((sIdx, traj) in rawTrajectories.withIndex()) {
            val mapped = traj.map { pt ->
                Vec2(
                    0.06f + ((pt.x - minX) / spanX) * 0.88f,
                    aspect * (0.08f + ((pt.y - minY) / spanY) * 0.84f)
                )
            }
            val chunk = 5
            var k = 0
            while (k + 1 < mapped.size) {
                val endK = min(k + chunk, mapped.size - 1)
                val sub = mapped.subList(k, endK + 1)
                val mid = sub[sub.size / 2]
                val w = (0.011f + 0.012f * sin((k.toFloat() / mapped.size) * PI.toFloat())) *
                    (1.06f - intricacy * 0.22f)
                var segLen = 0f
                for (i in 0 until sub.size - 1) {
                    segLen += hypot(sub[i + 1].x - sub[i].x, sub[i + 1].y - sub[i].y)
                }
                val area = (segLen * w).coerceAtLeast(0.0001f)
                val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                val cIdx = (sIdx + k / 5) % 6
                val ang = atan2(sub.last().y - sub.first().y, sub.last().x - sub.first().x)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolylineRibbonPrimitive(
                                points = sub,
                                strokeWidthNorm = w,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.86f + 0.28f * sin((k.toFloat() / mapped.size) * PI.toFloat()),
                                gradientAngleRad = ang
                            )
                        )
                    }
                )
                k = endK
            }
        }
        return cells
    }

    // 66. Faraday Standing Ripples (Parametric Capillary Wave Lattice)
    fun buildFaradayRipples(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val waveModes = if (rng.nextBoolean()) 6 else 5
        val freq = (14f + intricacy * 10f)
        val rotOffset = rng.nextFloat() * TWO_PI
        val phases = FloatArray(waveModes) { rng.nextFloat() * TWO_PI }
        val dirs = Array(waveModes) { m ->
            val a = rotOffset + (m.toFloat() / waveModes) * PI.toFloat()
            Vec2(cos(a), sin(a))
        }

        val cols = (16 + (intricacy * 8f).roundToInt()).coerceIn(16, 24)
        val rows = ((cols * aspect).roundToInt()).coerceIn(22, 52)
        val dx = 1f / cols
        val dy = aspect / rows
        val cx0 = 0.5f
        val cy0 = aspect * 0.5f
        val cells = ArrayList<DraftCell>(cols * rows)

        for (r in 0 until rows) {
            val py = (r + 0.5f) * dy
            for (c in 0 until cols) {
                val px = (c + 0.5f) * dx
                var ampSum = 0f
                for (m in 0 until waveModes) {
                    val proj = (px - cx0) * dirs[m].x + (py - cy0) * dirs[m].y
                    ampSum += cos(proj * freq + phases[m])
                }
                val normAmp = abs(ampSum) / waveModes.toFloat()
                if (normAmp < 0.14f) continue

                val radius = min(dx, dy) * (0.28f + 0.58f * normAmp)
                val sides = if (waveModes == 6) 6 else 4
                val twist = ampSum * 0.35f + rotOffset
                val outerVerts = List(sides) { k ->
                    val a = twist + k * (TWO_PI / sides)
                    Vec2(px + radius * cos(a), py + radius * sin(a))
                }
                val innerVerts = List(sides) { k ->
                    val a = twist + k * (TWO_PI / sides) + (PI.toFloat() / sides)
                    Vec2(px + radius * 0.52f * cos(a), py + radius * 0.52f * sin(a))
                }
                val area = (PI.toFloat() * radius * radius * 0.78f).coerceAtLeast(0.00008f)
                val score = field.evaluate(px, py, rng) * 0.78f + normAmp * 0.22f
                val cIdx = ((normAmp * 6f).toInt() + (r + c) % 2)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = outerVerts,
                                threshold = threshold,
                                areaWeight = area * 0.70f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.80f + 0.32f * normAmp,
                                gradientAngleRad = twist,
                                strokeSeamNorm = 0.0014f
                            ),
                            PolygonPrimitive(
                                vertices = innerVerts,
                                threshold = threshold,
                                areaWeight = area * 0.30f,
                                colorIndex = cIdx + 2,
                                secondaryColorIndex = cIdx,
                                shadeFactor = 1.18f,
                                gradientAngleRad = twist + 1.57f,
                                strokeSeamNorm = 0.0010f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 67. Spira Mirabilis (Dual Counter-Rotating Bernoulli Equiangular Arms)
    fun buildBernoulliSpirals(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val armsCw = (8 + (intricacy * 6f).roundToInt()).coerceIn(8, 14)
        val radialRings = (14 + (intricacy * 8f).roundToInt()).coerceIn(14, 22)
        val maxR = hypot(0.54f, aspect * 0.54f)
        val minR = 0.024f
        val growth = kotlin.math.ln(maxR / minR) / radialRings.toFloat()
        val twistRate = 0.26f + rng.nextFloat() * 0.14f
        val baseAngle = rng.nextFloat() * TWO_PI
        val dTheta = TWO_PI / armsCw.toFloat()
        val cells = ArrayList<DraftCell>(armsCw * radialRings)

        for (ring in 0 until radialRings) {
            val r0 = minR * exp(ring * growth)
            val r1 = minR * exp((ring + 1) * growth)
            val rMid = (r0 + r1) * 0.5f
            for (arm in 0 until armsCw) {
                val aBase0 = baseAngle + arm * dTheta + ring * twistRate
                val aBase1 = baseAngle + arm * dTheta + (ring + 1) * twistRate
                val span = dTheta * 0.78f

                val p00 = Vec2(center.x + r0 * cos(aBase0), center.y + r0 * sin(aBase0))
                val p10 = Vec2(center.x + r0 * cos(aBase0 + span), center.y + r0 * sin(aBase0 + span))
                val p11 = Vec2(center.x + r1 * cos(aBase1 + span), center.y + r1 * sin(aBase1 + span))
                val p01 = Vec2(center.x + r1 * cos(aBase1), center.y + r1 * sin(aBase1))

                val cx = (p00.x + p10.x + p11.x + p01.x) * 0.25f
                val cy = (p00.y + p10.y + p11.y + p01.y) * 0.25f
                if (cx < -0.14f || cx > 1.14f || cy < -0.14f || cy > aspect + 0.14f) continue

                val inset = 0.24f
                val innerQuad = listOf(p00, p10, p11, p01).map { v ->
                    Vec2(cx + (v.x - cx) * (1f - inset * 2f), cy + (v.y - cy) * (1f - inset * 2f))
                }
                val area = (0.5f * (r1 * r1 - r0 * r0) * span).coerceAtLeast(0.00008f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val cIdx = (arm + ring) % 5
                val gradAng = atan2(cy - center.y, cx - center.x)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = listOf(p00, p10, p11, p01),
                                threshold = threshold,
                                areaWeight = area * 0.74f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.84f + 0.26f * (1f - (rMid / maxR).coerceIn(0f, 1f)),
                                gradientAngleRad = gradAng,
                                strokeSeamNorm = 0.0016f
                            ),
                            PolygonPrimitive(
                                vertices = innerQuad,
                                threshold = threshold,
                                areaWeight = area * 0.26f,
                                colorIndex = cIdx + 2,
                                secondaryColorIndex = cIdx,
                                shadeFactor = 1.18f,
                                gradientAngleRad = gradAng + 1.57f,
                                strokeSeamNorm = 0.0010f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 68. KdV Soliton Trains (Interacting Nonlinear sech^2 Wave Pulses)
    fun buildSolitonTrains(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val channels = (13 + (intricacy * 8f).roundToInt()).coerceIn(13, 21)
        val slices = 22
        val tilt = (rng.nextFloat() - 0.5f) * 0.42f
        val peakA = 0.22f + rng.nextFloat() * 0.28f
        val peakB = 0.58f + rng.nextFloat() * 0.24f
        val cells = ArrayList<DraftCell>(channels * slices)

        fun sech2(v: Float): Float {
            val ch = cosh(v.coerceIn(-8f, 8f))
            return 1f / (ch * ch)
        }

        for (ch in 0 until channels) {
            val vNorm = (ch + 0.5f) / channels.toFloat()
            val baseY = vNorm * aspect
            val shift1 = peakA + (vNorm - 0.5f) * 0.55f
            val shift2 = peakB - (vNorm - 0.5f) * 0.48f
            val spacing = aspect / channels.toFloat()

            for (s in 0 until slices) {
                val u0 = s.toFloat() / slices
                val u1 = (s + 1).toFloat() / slices
                val uMid = (u0 + u1) * 0.5f

                fun pulseHeight(u: Float): Float {
                    val p1 = 0.92f * sech2((u - shift1) * 7.2f)
                    val p2 = 0.64f * sech2((u - shift2) * 5.6f)
                    val interact = -0.18f * p1 * p2
                    return (0.18f + p1 + p2 + interact) * spacing * 0.72f
                }

                val h0 = pulseHeight(u0)
                val h1 = pulseHeight(u1)
                val yOff0 = baseY + (u0 - 0.5f) * tilt * aspect
                val yOff1 = baseY + (u1 - 0.5f) * tilt * aspect

                val quad = listOf(
                    Vec2(u0, yOff0 + spacing * 0.24f),
                    Vec2(u1, yOff1 + spacing * 0.24f),
                    Vec2(u1, yOff1 + spacing * 0.24f - h1),
                    Vec2(u0, yOff0 + spacing * 0.24f - h0)
                )
                val cx = uMid
                val cy = ((yOff0 + yOff1) * 0.5f - (h0 + h1) * 0.25f).coerceIn(0f, aspect)
                val area = ((u1 - u0) * (h0 + h1) * 0.5f).coerceAtLeast(0.00008f)
                val score = field.evaluate(cx, cy, rng)
                val cIdx = (ch + (s / 4)) % 6

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = quad,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.80f + 0.36f * ((h0 + h1) / (spacing * 1.4f)).coerceIn(0f, 1.2f),
                                gradientAngleRad = -1.2f,
                                strokeSeamNorm = 0.0014f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 69. Enneagram Rosette (9-Fold Nonagonal Chord & Petal Mandala)
    fun buildEnneagramStar(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val symmetry = 9
        val tiers = (5 + (intricacy * 4f).roundToInt()).coerceIn(5, 9)
        val maxR = min(0.47f, aspect * 0.44f)
        val rot0 = -PI.toFloat() * 0.5f + (rng.nextFloat() - 0.5f) * 0.25f
        val stepAng = TWO_PI / symmetry
        val cells = ArrayList<DraftCell>(symmetry * tiers * 3)

        for (t in 0 until tiers) {
            val rIn = maxR * (0.10f + 0.86f * (t.toFloat() / tiers))
            val rOut = maxR * (0.10f + 0.86f * ((t + 0.86f) / tiers))
            val rMid = (rIn + rOut) * 0.5f
            val twist = rot0 + (if (t % 2 == 1) stepAng * 0.5f else 0f)

            for (k in 0 until symmetry) {
                val aMid = twist + k * stepAng
                val halfW = stepAng * 0.38f
                val kite = listOf(
                    Vec2(center.x + rIn * cos(aMid), center.y + rIn * sin(aMid)),
                    Vec2(center.x + rMid * cos(aMid - halfW), center.y + rMid * sin(aMid - halfW)),
                    Vec2(center.x + rOut * cos(aMid), center.y + rOut * sin(aMid)),
                    Vec2(center.x + rMid * cos(aMid + halfW), center.y + rMid * sin(aMid + halfW))
                )
                val cx = center.x + rMid * cos(aMid)
                val cy = center.y + rMid * sin(aMid)
                val area = ((rOut - rIn) * rMid * halfW).coerceAtLeast(0.0001f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = kite,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = (t + k % 3),
                                secondaryColorIndex = t + 2,
                                shadeFactor = 0.86f + 0.26f * (1f - t.toFloat() / tiers),
                                gradientAngleRad = aMid,
                                strokeSeamNorm = 0.0016f
                            )
                        )
                    }
                )
            }
        }

        // Sacred {9/3} and {9/4} nonagram chord ribbons inside inner sanctuary
        val chordR = maxR * 0.72f
        val jumpSteps = intArrayOf(3, 4)
        for ((jIdx, step) in jumpSteps.withIndex()) {
            for (k in 0 until symmetry) {
                val a0 = rot0 + k * stepAng
                val a1 = rot0 + ((k + step) % symmetry) * stepAng
                val p0 = Vec2(center.x + chordR * cos(a0), center.y + chordR * sin(a0))
                val p1 = Vec2(center.x + chordR * cos(a1), center.y + chordR * sin(a1))
                val mid = Vec2((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                val w = 0.0095f
                val area = (hypot(p1.x - p0.x, p1.y - p0.y) * w).coerceAtLeast(0.00008f)
                val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolylineRibbonPrimitive(
                                points = listOf(p0, mid, p1),
                                strokeWidthNorm = w,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = jIdx + 3,
                                secondaryColorIndex = jIdx + 1,
                                shadeFactor = 1.18f,
                                gradientAngleRad = atan2(p1.y - p0.y, p1.x - p0.x)
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 70. Rhodonea Curves (Harmonic Grandi Polar Petal Shells)
    fun buildRhodoneaBloom(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val ratios = arrayOf(5 to 3, 7 to 3, 8 to 5, 7 to 4, 9 to 4)
        val (n, d) = ratios[rng.nextInt(ratios.size)]
        val kRatio = n.toFloat() / d.toFloat()
        val shells = (4 + (intricacy * 3f).roundToInt()).coerceIn(4, 7)
        val maxR = min(0.47f, aspect * 0.45f)
        val totalPeriod = PI.toFloat() * d * (if ((n * d) % 2 == 1) 1 else 2)
        val totalSamples = 180 * d
        val basePhase = rng.nextFloat() * TWO_PI
        val cells = ArrayList<DraftCell>(shells * 48)

        for (sh in 0 until shells) {
            val rScale = maxR * (0.28f + 0.72f * ((sh + 1f) / shells))
            val phase = basePhase + sh * 0.24f
            val pts = List(totalSamples + 1) { i ->
                val t = (i.toFloat() / totalSamples) * totalPeriod
                val r = rScale * cos(kRatio * t)
                Vec2(center.x + r * cos(t + phase), center.y + r * sin(t + phase))
            }
            val chunk = 5
            var idx = 0
            while (idx + 1 < pts.size) {
                val endIdx = min(idx + chunk, pts.size - 1)
                val seg = pts.subList(idx, endIdx + 1)
                val mid = seg[seg.size / 2]
                val w = 0.011f + 0.005f * (1f - sh.toFloat() / shells)
                var len = 0f
                for (j in 0 until seg.size - 1) {
                    len += hypot(seg[j + 1].x - seg[j].x, seg[j + 1].y - seg[j].y)
                }
                val area = (len * w).coerceAtLeast(0.00008f)
                val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolylineRibbonPrimitive(
                                points = seg,
                                strokeWidthNorm = w,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = sh + (idx / 12) % 4,
                                secondaryColorIndex = sh + 2,
                                shadeFactor = 0.88f + 0.26f * (1f - sh.toFloat() / shells),
                                gradientAngleRad = atan2(mid.y - center.y, mid.x - center.x)
                            )
                        )
                    }
                )
                idx = endIdx
            }
        }
        return cells
    }

    // 71. Epitrochoid Crown (Rolling Planetary Orbital Cusps & Loops)
    fun buildEpitrochoidCrown(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val cusps = intArrayOf(6, 7, 8, 10, 12)[rng.nextInt(5)]
        val layers = (4 + (intricacy * 3f).roundToInt()).coerceIn(4, 7)
        val maxBound = min(0.46f, aspect * 0.44f)
        val rot = rng.nextFloat() * TWO_PI
        val cells = ArrayList<DraftCell>(layers * 56)

        for (layer in 0 until layers) {
            val normL = (layer + 1f) / layers
            val smallR = (maxBound * normL) / (cusps + 2.4f)
            val bigR = smallR * cusps
            val dArm = smallR * (1.35f + 0.65f * sin(layer * 0.9f))
            val steps = 160
            val pts = List(steps + 1) { i ->
                val t = (i.toFloat() / steps) * TWO_PI
                val sumR = bigR + smallR
                val rawX = sumR * cos(t) - dArm * cos((sumR / smallR) * t)
                val rawY = sumR * sin(t) - dArm * sin((sumR / smallR) * t)
                val rx = rawX * cos(rot) - rawY * sin(rot)
                val ry = rawX * sin(rot) + rawY * cos(rot)
                Vec2(center.x + rx, center.y + ry)
            }

            val chunk = 4
            var k = 0
            while (k + 1 < pts.size) {
                val endK = min(k + chunk, pts.size - 1)
                val sub = pts.subList(k, endK + 1)
                val mid = sub[sub.size / 2]
                val w = 0.0115f
                var segLen = 0f
                for (j in 0 until sub.size - 1) {
                    segLen += hypot(sub[j + 1].x - sub[j].x, sub[j + 1].y - sub[j].y)
                }
                val area = (segLen * w).coerceAtLeast(0.00008f)
                val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolylineRibbonPrimitive(
                                points = sub,
                                strokeWidthNorm = w,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = layer + (k / 10) % 4,
                                secondaryColorIndex = layer + 2,
                                shadeFactor = 0.86f + 0.28f * (1f - normL),
                                gradientAngleRad = atan2(mid.y - center.y, mid.x - center.x)
                            )
                        )
                    }
                )
                k = endK
            }
        }
        return cells
    }

    // 72. Yantra Interlace (9 Interlocking Sacred Triangles & Lotus Rings)
    fun buildSriYantraTriangles(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val maxR = min(0.46f, aspect * 0.44f)
        val cells = ArrayList<DraftCell>(160)

        // 1. Inner 8-petal and outer 16-petal lotus rings
        val ringDefs = listOf(Triple(8, maxR * 0.62f, maxR * 0.78f), Triple(16, maxR * 0.79f, maxR * 0.96f))
        for ((ringIdx, def) in ringDefs.withIndex()) {
            val (petals, rIn, rOut) = def
            val dAng = TWO_PI / petals
            for (p in 0 until petals) {
                val aMid = -PI.toFloat() * 0.5f + p * dAng
                val rMid = (rIn + rOut) * 0.5f
                val petal = listOf(
                    Vec2(center.x + rIn * cos(aMid - dAng * 0.42f), center.y + rIn * sin(aMid - dAng * 0.42f)),
                    Vec2(center.x + rMid * cos(aMid - dAng * 0.46f), center.y + rMid * sin(aMid - dAng * 0.46f)),
                    Vec2(center.x + rOut * cos(aMid), center.y + rOut * sin(aMid)),
                    Vec2(center.x + rMid * cos(aMid + dAng * 0.46f), center.y + rMid * sin(aMid + dAng * 0.46f)),
                    Vec2(center.x + rIn * cos(aMid + dAng * 0.42f), center.y + rIn * sin(aMid + dAng * 0.42f))
                )
                val cx = center.x + rMid * cos(aMid)
                val cy = center.y + rMid * sin(aMid)
                val area = ((rOut - rIn) * rMid * dAng * 0.65f).coerceAtLeast(0.0001f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = petal,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = ringIdx * 2 + (p % 2),
                                secondaryColorIndex = ringIdx * 2 + 1,
                                shadeFactor = 0.90f + 0.20f * (1 - ringIdx),
                                gradientAngleRad = aMid,
                                strokeSeamNorm = 0.0015f
                            )
                        )
                    }
                )
            }
        }

        // 2. Nine interlocking sacred triangles (4 upward Shiva + 5 downward Shakti)
        val innerR = maxR * 0.60f
        val triSpecs = listOf(
            Triple(true, -0.94f, 0.58f),
            Triple(true, -0.74f, 0.72f),
            Triple(true, -0.52f, 0.44f),
            Triple(true, -0.30f, 0.24f),
            Triple(false, 0.94f, -0.56f),
            Triple(false, 0.76f, -0.70f),
            Triple(false, 0.56f, -0.46f),
            Triple(false, 0.36f, -0.28f),
            Triple(false, 0.18f, -0.14f)
        )
        for ((tIdx, spec) in triSpecs.withIndex()) {
            val (_, apexFactor, baseFactor) = spec
            val yApex = center.y + apexFactor * innerR
            val yBase = center.y + baseFactor * innerR
            val halfSpan = sqrt((innerR * innerR - (baseFactor * innerR) * (baseFactor * innerR)).coerceAtLeast(0.002f)) * 0.92f
            val verts = listOf(
                Vec2(center.x, yApex),
                Vec2(center.x + halfSpan, yBase),
                Vec2(center.x - halfSpan, yBase)
            )
            for (edge in 0 until 3) {
                val v0 = verts[edge]
                val v1 = verts[(edge + 1) % 3]
                val segs = 4
                for (s in 0 until segs) {
                    val u0 = s.toFloat() / segs
                    val u1 = (s + 1).toFloat() / segs
                    val p0 = Vec2(v0.x + (v1.x - v0.x) * u0, v0.y + (v1.y - v0.y) * u0)
                    val p1 = Vec2(v0.x + (v1.x - v0.x) * u1, v0.y + (v1.y - v0.y) * u1)
                    val mid = Vec2((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                    val w = 0.0105f * (1.06f - intricacy * 0.15f)
                    val area = (hypot(p1.x - p0.x, p1.y - p0.y) * w).coerceAtLeast(0.00008f)
                    val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolylineRibbonPrimitive(
                                    points = listOf(p0, p1),
                                    strokeWidthNorm = w,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = (tIdx + edge) % 5,
                                    secondaryColorIndex = (tIdx + 2) % 5,
                                    shadeFactor = 1.12f,
                                    gradientAngleRad = atan2(p1.y - p0.y, p1.x - p0.x)
                                )
                            )
                        }
                    )
                }
            }
        }

        // Central bindu medallion
        val binduR = maxR * 0.065f
        val binduArea = PI.toFloat() * binduR * binduR
        cells.add(
            DraftCell(score = field.evaluate(center.x, center.y, rng), area = binduArea) { threshold ->
                listOf(
                    ArcSectorPrimitive(
                        center = center,
                        innerRadius = 0f,
                        outerRadius = binduR,
                        startAngleDeg = 0f,
                        sweepAngleDeg = 360f,
                        threshold = threshold,
                        areaWeight = binduArea,
                        colorIndex = 0,
                        secondaryColorIndex = 2,
                        shadeFactor = 1.28f
                    )
                )
            }
        )
        return cells
    }
}

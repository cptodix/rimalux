package com.example.engine

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

internal object OrganicFlowPatterns {

    private const val TWO_PI = (2.0 * PI).toFloat()
    private const val GOLDEN_ANGLE = 2.3999631f

    // 1. Flowing Silk Ribbons (Differential Curl-Field Streamlines)
    fun buildSilkRibbons(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (6 + (intricacy * 5f).roundToInt()).coerceIn(6, 12)
        val rows = (8 + (intricacy * 7f).roundToInt()).coerceIn(8, 16)
        val fx = 2.2f + rng.nextFloat() * 2.4f
        val fy = 1.9f + rng.nextFloat() * 2.2f
        val p1 = rng.nextFloat() * TWO_PI
        val p2 = rng.nextFloat() * TWO_PI
        val stepLen = 0.026f
        val steps = 20 + (intricacy * 14f).roundToInt()
        val maxHalfWidth = 0.024f + (1f - intricacy * 0.45f) * 0.018f

        fun flowAngle(x: Float, y: Float): Float {
            val n1 = sin(x * fx + p1) * cos(y * fy + p2)
            val n2 = cos((x - y * 0.5f) * (fx * 0.7f) - p2)
            return (n1 * 1.65f + n2 * 0.95f) * PI.toFloat() * 0.65f
        }

        val cells = ArrayList<DraftCell>(cols * rows * 4)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                var px = (c + 0.5f + (rng.nextFloat() - 0.5f) * 0.5f) / cols
                var py = ((r + 0.5f + (rng.nextFloat() - 0.5f) * 0.5f) / rows) * aspect

                val spine = ArrayList<Vec2>(steps + 1)
                val normals = ArrayList<Vec2>(steps + 1)
                spine.add(Vec2(px, py))

                for (s in 0 until steps) {
                    val ang = flowAngle(px, py)
                    normals.add(Vec2(-sin(ang), cos(ang)))
                    px += cos(ang) * stepLen
                    py += sin(ang) * stepLen
                    spine.add(Vec2(px, py))
                }
                val lastAng = flowAngle(px, py)
                normals.add(Vec2(-sin(lastAng), cos(lastAng)))

                val baseColor = abs(r * 3 + c * 2 + rng.nextInt(3))
                val secColor = baseColor + 1 + (c % 2)

                // Group every 4 steps into a sculpted tapered silk segment + filament
                val chunkSize = 4
                var idx = 0
                while (idx + 1 < spine.size) {
                    val endIdx = min(idx + chunkSize, spine.size - 1)
                    val leftSide = ArrayList<Vec2>(endIdx - idx + 1)
                    val rightSide = ArrayList<Vec2>(endIdx - idx + 1)
                    val filament = ArrayList<Vec2>(endIdx - idx + 1)

                    for (k in idx..endIdx) {
                        val tGlobal = k.toFloat() / (spine.size - 1).coerceAtLeast(1)
                        val taper = sin(tGlobal * PI.toFloat()).coerceAtLeast(0.08f)
                        val hw = maxHalfWidth * taper
                        val pt = spine[k]
                        val nm = normals[k]
                        leftSide.add(Vec2(pt.x + nm.x * hw, pt.y + nm.y * hw))
                        rightSide.add(Vec2(pt.x - nm.x * hw, pt.y - nm.y * hw))
                        filament.add(Vec2(pt.x + nm.x * hw * 1.32f, pt.y + nm.y * hw * 1.32f))
                    }

                    val polyVerts = leftSide + rightSide.asReversed()
                    val midPt = spine[(idx + endIdx) / 2]
                    if (midPt.x in -0.1f..1.1f && midPt.y in -0.1f..(aspect + 0.1f)) {
                        val segAngle = atan2(
                            spine[endIdx].y - spine[idx].y,
                            spine[endIdx].x - spine[idx].x
                        )
                        val segArea = (stepLen * (endIdx - idx) * maxHalfWidth * 1.5f).coerceAtLeast(0.0001f)
                        val score = field.evaluate(midPt.x, midPt.y, rng)
                        val shade = 0.85f + 0.30f * sin((idx.toFloat() / spine.size) * PI.toFloat())

                        cells.add(
                            DraftCell(score = score, area = segArea) { threshold ->
                                listOf(
                                    PolygonPrimitive(
                                        vertices = polyVerts,
                                        threshold = threshold,
                                        areaWeight = segArea * 0.85f,
                                        colorIndex = baseColor,
                                        secondaryColorIndex = secColor,
                                        shadeFactor = shade,
                                        gradientAngleRad = segAngle,
                                        strokeSeamNorm = 0.0012f
                                    ),
                                    PolylineRibbonPrimitive(
                                        points = filament,
                                        strokeWidthNorm = 0.0022f,
                                        threshold = threshold,
                                        areaWeight = segArea * 0.15f,
                                        colorIndex = secColor,
                                        secondaryColorIndex = baseColor,
                                        shadeFactor = 1.15f,
                                        gradientAngleRad = segAngle
                                    )
                                )
                            }
                        )
                    }
                    idx = endIdx
                }
            }
        }
        return cells
    }

    // 2. Topographic Elevation Contours & Terraced Strata
    fun buildTopoContours(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val rings = (14 + (intricacy * 14f).roundToInt()).coerceIn(14, 28)
        val angularSteps = (48 + (intricacy * 32f).roundToInt()).coerceIn(48, 84)
        val peaks = List(2) {
            Vec2(
                0.28f + rng.nextFloat() * 0.44f,
                aspect * (0.28f + rng.nextFloat() * 0.44f)
            )
        }
        val harmonicA = 3 + rng.nextInt(4)
        val harmonicB = 5 + rng.nextInt(4)
        val phaseA = rng.nextFloat() * TWO_PI
        val phaseB = rng.nextFloat() * TWO_PI
        val maxR = hypot(0.62f, aspect * 0.62f)

        val cells = ArrayList<DraftCell>(peaks.size * rings * 4)

        for ((peakIdx, center) in peaks.withIndex()) {
            for (ring in 0 until rings) {
                val t0 = ring.toFloat() / rings
                val t1 = (ring + 1).toFloat() / rings
                val baseR0 = t0 * maxR * (if (peakIdx == 0) 0.95f else 0.68f)
                val baseR1 = t1 * maxR * (if (peakIdx == 0) 0.95f else 0.68f)
                val gap = (baseR1 - baseR0) * 0.14f

                fun contourRadius(baseR: Float, theta: Float, ringF: Float): Float {
                    val warp = 1f +
                        0.16f * sin(harmonicA * theta + phaseA + ringF * 0.35f) +
                        0.08f * cos(harmonicB * theta - phaseB - ringF * 0.25f)
                    return (baseR * warp).coerceAtLeast(0.004f)
                }

                // Slice each organic contour ring into 6 smooth arc-ribbons so SculptField carves gracefully
                val chunks = 6
                val stepsPerChunk = angularSteps / chunks
                for (ch in 0 until chunks) {
                    val outerPts = ArrayList<Vec2>(stepsPerChunk + 1)
                    val innerPts = ArrayList<Vec2>(stepsPerChunk + 1)
                    for (s in 0..stepsPerChunk) {
                        val globalS = ch * stepsPerChunk + s
                        val theta = (globalS.toFloat() / angularSteps) * TWO_PI
                        val rIn = contourRadius(baseR0 + gap * 0.5f, theta, ring.toFloat())
                        val rOut = contourRadius(baseR1 - gap * 0.5f, theta, ring + 0.8f).coerceAtLeast(rIn + 0.003f)
                        outerPts.add(Vec2(center.x + rOut * cos(theta), center.y + rOut * sin(theta)))
                        innerPts.add(Vec2(center.x + rIn * cos(theta), center.y + rIn * sin(theta)))
                    }

                    val mid = outerPts[outerPts.size / 2]
                    if (mid.x < -0.15f || mid.x > 1.15f || mid.y < -0.15f || mid.y > aspect + 0.15f) continue

                    val polyVerts = outerPts + innerPts.asReversed()
                    val score = field.evaluate(mid.x, mid.y, rng)
                    val area = (((baseR1 * baseR1 - baseR0 * baseR0) * PI.toFloat()) / chunks).coerceAtLeast(0.0001f)
                    val cIdx = ring + peakIdx * 2
                    val gradAng = atan2(mid.y - center.y, mid.x - center.x)

                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = polyVerts,
                                    threshold = threshold,
                                    areaWeight = area * 0.82f,
                                    colorIndex = cIdx,
                                    secondaryColorIndex = cIdx + 1,
                                    shadeFactor = 0.78f + (ring % 4) * 0.10f,
                                    gradientAngleRad = gradAng,
                                    strokeSeamNorm = 0.0018f
                                ),
                                PolylineRibbonPrimitive(
                                    points = outerPts,
                                    strokeWidthNorm = 0.0026f,
                                    threshold = threshold,
                                    areaWeight = area * 0.18f,
                                    colorIndex = cIdx + 2,
                                    secondaryColorIndex = cIdx,
                                    shadeFactor = 1.18f,
                                    gradientAngleRad = gradAng + 0.8f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 3. Guilloché Spirograph Rosette
    fun buildGuillocheRosette(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val symmetry = listOf(8, 10, 12, 16)[rng.nextInt(4)]
        val layers = (7 + (intricacy * 7f).roundToInt()).coerceIn(7, 15)
        val maxR = hypot(0.54f, aspect * 0.54f)
        val cells = ArrayList<DraftCell>(layers * symmetry)

        for (layer in 0 until layers) {
            val t0 = layer.toFloat() / layers
            val t1 = (layer + 1).toFloat() / layers
            val rInner = t0 * maxR * 0.96f
            val rOuter = (t1 * maxR * 0.96f).coerceAtLeast(rInner + 0.015f)
            val twist = layer * (PI.toFloat() / symmetry) * 0.5f

            for (s in 0 until symmetry) {
                val baseTheta = twist + s * TWO_PI / symmetry
                val span = TWO_PI / symmetry

                // Sculpted guilloché wave-petal + fine nested spirograph curve
                val steps = 14
                val outerLoop = ArrayList<Vec2>(steps + 1)
                val innerLoop = ArrayList<Vec2>(steps + 1)
                val fineThread = ArrayList<Vec2>(steps + 1)

                for (i in 0..steps) {
                    val u = i.toFloat() / steps
                    val ang = baseTheta + (u - 0.5f) * span * 1.18f
                    val env = sin(u * PI.toFloat())
                    val rTop = rInner + (rOuter - rInner) * (0.25f + 0.75f * env)
                    val rBot = rInner + (rOuter - rInner) * (0.05f + 0.22f * env)
                    val rFil = rInner + (rOuter - rInner) * (0.55f + 0.42f * sin(u * PI.toFloat() * 2f))
                    outerLoop.add(Vec2(center.x + rTop * cos(ang), center.y + rTop * sin(ang)))
                    innerLoop.add(Vec2(center.x + rBot * cos(ang), center.y + rBot * sin(ang)))
                    fineThread.add(Vec2(center.x + rFil * cos(ang), center.y + rFil * sin(ang)))
                }

                val mid = outerLoop[steps / 2]
                if (mid.x < -0.15f || mid.x > 1.15f || mid.y < -0.15f || mid.y > aspect + 0.15f) continue

                val verts = outerLoop + innerLoop.asReversed()
                val score = field.evaluate(mid.x, mid.y, rng)
                val area = (0.5f * (rOuter * rOuter - rInner * rInner) * span).coerceAtLeast(0.0001f)
                val cIdx = layer + (s % 2)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = verts,
                                threshold = threshold,
                                areaWeight = area * 0.78f,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = 0.84f + (layer % 3) * 0.12f,
                                gradientAngleRad = baseTheta,
                                strokeSeamNorm = 0.0016f
                            ),
                            PolylineRibbonPrimitive(
                                points = fineThread,
                                strokeWidthNorm = 0.0024f,
                                threshold = threshold,
                                areaWeight = area * 0.22f,
                                colorIndex = cIdx + 2,
                                secondaryColorIndex = cIdx,
                                shadeFactor = 1.20f,
                                gradientAngleRad = baseTheta + 1.1f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 4. Fibonacci Golden-Angle Lotus Vortex
    fun buildFibonacciLotus(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * (0.46f + (rng.nextFloat() - 0.5f) * 0.12f))
        val count = (95 + (intricacy * 110f).roundToInt()).coerceIn(95, 210)
        val maxR = hypot(0.56f, aspect * 0.58f)
        val phase = rng.nextFloat() * TWO_PI
        val cells = ArrayList<DraftCell>(count)

        // Draw from outer to inner so inner petals layer crisply if overlapping
        for (idx in count downTo 1) {
            val norm = idx.toFloat() / count
            val r = sqrt(norm) * maxR
            val theta = idx * GOLDEN_ANGLE + phase
            val cx = center.x + r * cos(theta)
            val cy = center.y + r * sin(theta)
            if (cx < -0.12f || cx > 1.12f || cy < -0.12f || cy > aspect + 0.12f) continue

            val petalLen = (0.042f + 0.105f * sqrt(norm)) * (1.08f - intricacy * 0.22f)
            val petalWid = petalLen * 0.46f

            val outerPetal = buildPointedPetal(cx, cy, petalLen, petalWid, theta)
            val innerPetal = buildPointedPetal(cx, cy, petalLen * 0.56f, petalWid * 0.44f, theta)
            val area = (petalLen * petalWid * 0.72f).coerceAtLeast(0.0001f)
            val score = field.evaluate(cx, cy, rng)
            val cIdx = (idx % 5) + (norm * 4f).toInt()

            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    listOf(
                        PolygonPrimitive(
                            vertices = outerPetal,
                            threshold = threshold,
                            areaWeight = area * 0.74f,
                            colorIndex = cIdx,
                            secondaryColorIndex = cIdx + 1,
                            shadeFactor = 0.82f + 0.30f * (1f - norm),
                            gradientAngleRad = theta,
                            strokeSeamNorm = 0.0020f
                        ),
                        PolygonPrimitive(
                            vertices = innerPetal,
                            threshold = threshold,
                            areaWeight = area * 0.26f,
                            colorIndex = cIdx + 2,
                            secondaryColorIndex = cIdx,
                            shadeFactor = 1.16f,
                            gradientAngleRad = theta + PI.toFloat()
                        )
                    )
                }
            )
        }
        return cells
    }

    // 5. Horizon Harmonic Strata & Celestial Eclipse
    fun buildHorizonWaves(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val bands = (14 + (intricacy * 12f).roundToInt()).coerceIn(14, 28)
        val xChunks = 8
        val ptsPerChunk = 8
        val freq1 = 2.4f + rng.nextFloat() * 2.5f
        val freq2 = 5.2f + rng.nextFloat() * 3.5f
        val basePhase = rng.nextFloat() * TWO_PI
        val cells = ArrayList<DraftCell>(bands * xChunks + 8)

        // Celestial concentric rings / crescent in upper third
        val sunCenter = Vec2(0.32f + rng.nextFloat() * 0.36f, aspect * (0.24f + rng.nextFloat() * 0.14f))
        val sunR = 0.15f + rng.nextFloat() * 0.08f
        for (ring in 0 until 4) {
            val rIn = if (ring == 0) 0f else sunR * (0.35f + ring * 0.22f)
            val rOut = if (ring == 0) sunR * 0.28f else rIn + sunR * 0.12f
            val area = PI.toFloat() * (rOut * rOut - rIn * rIn).coerceAtLeast(0.0002f)
            // High score so celestial disc stays luminous even at high pitch-black ratios
            val score = (field.evaluate(sunCenter.x, sunCenter.y, rng) * 0.5f + 0.48f).coerceAtMost(0.99f)
            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    listOf(
                        ArcSectorPrimitive(
                            center = sunCenter,
                            innerRadius = rIn,
                            outerRadius = rOut,
                            startAngleDeg = if (ring == 3) 200f else 0f,
                            sweepAngleDeg = if (ring == 3) 290f else 360f,
                            threshold = threshold,
                            areaWeight = area,
                            colorIndex = ring,
                            secondaryColorIndex = ring + 2,
                            shadeFactor = 1.18f,
                            gradientAngleRad = -PI.toFloat() * 0.5f
                        )
                    )
                }
            )
        }

        fun waveY(bandIdx: Int, x: Float): Float {
            val base = (bandIdx.toFloat() / bands) * aspect
            val amp1 = (aspect / bands) * 0.85f
            val amp2 = (aspect / bands) * 0.32f
            return base +
                amp1 * sin(x * freq1 + basePhase + bandIdx * 0.52f) +
                amp2 * cos(x * freq2 - basePhase * 0.6f + bandIdx * 0.85f)
        }

        for (b in 0 until bands) {
            for (ch in 0 until xChunks) {
                val x0 = ch.toFloat() / xChunks
                val x1 = (ch + 1).toFloat() / xChunks
                val topCurve = ArrayList<Vec2>(ptsPerChunk + 1)
                val botCurve = ArrayList<Vec2>(ptsPerChunk + 1)
                val gap = (aspect / bands) * 0.08f

                for (i in 0..ptsPerChunk) {
                    val x = x0 + (x1 - x0) * (i.toFloat() / ptsPerChunk)
                    val yt = waveY(b, x) + gap
                    val yb = max(yt + 0.004f, waveY(b + 1, x) - gap)
                    topCurve.add(Vec2(x, yt))
                    botCurve.add(Vec2(x, yb))
                }

                val midX = (x0 + x1) * 0.5f
                val midY = (topCurve[ptsPerChunk / 2].y + botCurve[ptsPerChunk / 2].y) * 0.5f
                val poly = topCurve + botCurve.asReversed()
                val area = ((x1 - x0) * (aspect / bands)).coerceAtLeast(0.0001f)
                val score = field.evaluate(midX, midY.coerceIn(0f, aspect), rng)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = poly,
                                threshold = threshold,
                                areaWeight = area * 0.84f,
                                colorIndex = b,
                                secondaryColorIndex = b + 1,
                                shadeFactor = 0.76f + 0.38f * (b.toFloat() / bands),
                                gradientAngleRad = PI.toFloat() * 0.5f,
                                strokeSeamNorm = 0.0018f
                            ),
                            PolylineRibbonPrimitive(
                                points = topCurve,
                                strokeWidthNorm = 0.0025f,
                                threshold = threshold,
                                areaWeight = area * 0.16f,
                                colorIndex = b + 2,
                                secondaryColorIndex = b,
                                shadeFactor = 1.18f,
                                gradientAngleRad = 0f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    private fun buildPointedPetal(
        cx: Float,
        cy: Float,
        length: Float,
        width: Float,
        angleRad: Float
    ): List<Vec2> {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        fun rot(lx: Float, ly: Float): Vec2 {
            return Vec2(
                cx + lx * cosA - ly * sinA,
                cy + lx * sinA + ly * cosA
            )
        }
        val halfL = length * 0.5f
        val halfW = width * 0.5f
        return listOf(
            rot(-halfL, 0f),
            rot(-halfL * 0.35f, -halfW * 0.72f),
            rot(halfL * 0.15f, -halfW),
            rot(halfL * 0.62f, -halfW * 0.52f),
            rot(halfL, 0f),
            rot(halfL * 0.62f, halfW * 0.52f),
            rot(halfL * 0.15f, halfW),
            rot(-halfL * 0.35f, halfW * 0.72f)
        )
    }
}

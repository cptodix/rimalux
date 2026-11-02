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
import kotlin.math.sqrt
import kotlin.random.Random

internal object AttractorFractalPatterns {

    private const val TWO_PI = (2.0 * PI).toFloat()
    private const val GOLDEN_ANGLE = 2.3999631f

    // 33. Lorenz Butterfly (Chaotic Strange-Attractor Phase-Space Ribbons)
    fun buildLorenzAttractor(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val trajectories = (5 + (intricacy * 4f).roundToInt()).coerceIn(5, 9)
        val steps = (220 + (intricacy * 90f).roundToInt()).coerceIn(220, 320)
        val dt = 0.0085f
        val sigma = 10f
        val rho = 28f
        val beta = 8f / 3f
        val rotAngle = (rng.nextFloat() - 0.5f) * 0.45f
        val cosR = cos(rotAngle)
        val sinR = sin(rotAngle)
        val cells = ArrayList<DraftCell>(trajectories * (steps / 6))

        for (tr in 0 until trajectories) {
            var x = 0.8f + (tr - trajectories * 0.5f) * 0.65f + (rng.nextFloat() - 0.5f) * 0.3f
            var y = 1.0f + (rng.nextFloat() - 0.5f) * 0.5f
            var z = 18f + (rng.nextFloat() - 0.5f) * 3f

            // Burn-in onto attractor manifold
            repeat(45) {
                val dx = sigma * (y - x)
                val dy = x * (rho - z) - y
                val dz = x * y - beta * z
                x += dx * dt
                y += dy * dt
                z += dz * dt
            }

            val spine = ArrayList<Vec2>(steps + 1)
            for (s in 0..steps) {
                val nx = (x / 21.5f)
                val nz = ((z - 25.5f) / 23.5f)
                val rx = nx * cosR - nz * sinR
                val ry = nx * sinR + nz * cosR
                spine.add(
                    Vec2(
                        0.5f + rx * 0.48f,
                        aspect * (0.5f + ry * 0.44f)
                    )
                )
                val dx = sigma * (y - x)
                val dy = x * (rho - z) - y
                val dz = x * y - beta * z
                x += dx * dt
                y += dy * dt
                z += dz * dt
            }

            val baseHalfW = 0.015f + (1f - intricacy * 0.3f) * 0.010f
            val chunk = 6
            var idx = 0
            while (idx + 1 < spine.size) {
                val endIdx = min(idx + chunk, spine.size - 1)
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
                    val taper = 0.58f + 0.42f * sin((k.toFloat() / steps) * TWO_PI * 2f)
                    val w = baseHalfW * taper
                    left.add(Vec2(spine[k].x + nx * w, spine[k].y + ny * w))
                    right.add(Vec2(spine[k].x - nx * w, spine[k].y - ny * w))
                }
                val mid = spine[(idx + endIdx) / 2]
                if (mid.x in -0.15f..1.15f && mid.y in -0.15f..(aspect + 0.15f)) {
                    val poly = left + right.asReversed()
                    val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                    val area = (baseHalfW * 2f * 0.08f).coerceAtLeast(0.0001f)
                    val gradAng = atan2(spine[endIdx].y - spine[idx].y, spine[endIdx].x - spine[idx].x)
                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = poly,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = tr + (idx / 24),
                                    secondaryColorIndex = tr + (idx / 24) + 2,
                                    shadeFactor = 0.86f + (tr % 3) * 0.14f,
                                    gradientAngleRad = gradAng,
                                    strokeSeamNorm = 0.0018f
                                )
                            )
                        }
                    )
                }
                idx = endIdx
            }
        }
        return cells
    }

    // 34. Fourier Epicycles (Complex Harmonic Phasor Orbital Synthesis)
    fun buildFourierEpicycles(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val families = (5 + (intricacy * 4f).roundToInt()).coerceIn(5, 9)
        val steps = (180 + (intricacy * 80f).roundToInt()).coerceIn(180, 260)
        val w1 = listOf(2, 3, 4)[rng.nextInt(3)]
        val w2 = listOf(-5, -7, 5, 7)[rng.nextInt(4)]
        val w3 = listOf(9, -11, 11)[rng.nextInt(3)]
        val phase0 = rng.nextFloat() * TWO_PI
        val maxR = min(0.46f, aspect * 0.44f)
        val cells = ArrayList<DraftCell>(families * 36)

        for (fam in 0 until families) {
            val u = (fam + 1).toFloat() / families
            val scale = maxR * (0.36f + 0.72f * u)
            val r1 = scale * 0.56f
            val r2 = scale * 0.28f
            val r3 = scale * 0.14f
            val ph = phase0 + fam * 0.42f
            val halfW = (0.013f + (1f - intricacy * 0.35f) * 0.009f)

            val spine = List(steps + 1) { s ->
                val t = (s.toFloat() / steps) * TWO_PI
                val px = center.x + r1 * cos(w1 * t + ph) + r2 * cos(w2 * t - ph * 0.6f) + r3 * cos(w3 * t)
                val py = center.y + (r1 * sin(w1 * t + ph) + r2 * sin(w2 * t - ph * 0.6f) + r3 * sin(w3 * t)) * (aspect * 0.62f).coerceIn(0.85f, 1.35f)
                Vec2(px, py)
            }

            val chunk = 6
            var idx = 0
            while (idx + 1 < spine.size) {
                val endIdx = min(idx + chunk, spine.size - 1)
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
                    left.add(Vec2(spine[k].x + nx * halfW, spine[k].y + ny * halfW))
                    right.add(Vec2(spine[k].x - nx * halfW, spine[k].y - ny * halfW))
                }
                val mid = spine[(idx + endIdx) / 2]
                val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                val area = (halfW * 2f * 0.07f).coerceAtLeast(0.0001f)
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = left + right.asReversed(),
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = fam + (idx / 18),
                                secondaryColorIndex = fam + 2,
                                shadeFactor = 0.85f + (fam % 3) * 0.14f,
                                gradientAngleRad = atan2(mid.y - center.y, mid.x - center.x),
                                strokeSeamNorm = 0.0018f
                            )
                        )
                    }
                )
                idx = endIdx
            }
        }
        return cells
    }

    // 35. Vortex Street (Kármán Counter-Rotating Fluid Eddies)
    fun buildKelvinVortex(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val eddyCount = (6 + (intricacy * 4f).roundToInt()).coerceIn(6, 10)
        val armsPerEddy = 4
        val cells = ArrayList<DraftCell>(eddyCount * armsPerEddy * 6)

        for (e in 0 until eddyCount) {
            val isLeft = e % 2 == 0
            val chirality = if (isLeft) 1f else -1f
            val cx = if (isLeft) 0.30f + rng.nextFloat() * 0.10f else 0.70f - rng.nextFloat() * 0.10f
            val cy = ((e + 0.5f) / eddyCount) * aspect
            val maxEddyR = 0.28f + (1f - intricacy * 0.25f) * 0.08f
            val basePhase = rng.nextFloat() * TWO_PI

            for (arm in 0 until armsPerEddy) {
                val armOffset = basePhase + arm * (TWO_PI / armsPerEddy)
                val totalSteps = 30
                val spine = List(totalSteps + 1) { s ->
                    val u = s.toFloat() / totalSteps
                    val r = 0.025f + u * maxEddyR
                    val theta = armOffset + chirality * u * 4.4f
                    Vec2(cx + r * cos(theta), cy + r * sin(theta))
                }

                val chunk = 5
                var idx = 0
                while (idx + 1 < spine.size) {
                    val endIdx = min(idx + chunk, spine.size - 1)
                    val left = ArrayList<Vec2>(endIdx - idx + 1)
                    val right = ArrayList<Vec2>(endIdx - idx + 1)
                    for (k in idx..endIdx) {
                        val u = k.toFloat() / totalSteps
                        val hw = (0.024f * (1f - 0.55f * u)).coerceAtLeast(0.008f)
                        val prev = spine[(k - 1).coerceAtLeast(0)]
                        val next = spine[(k + 1).coerceAtMost(spine.lastIndex)]
                        val tx = next.x - prev.x
                        val ty = next.y - prev.y
                        val len = hypot(tx, ty).coerceAtLeast(0.0001f)
                        val nx = -ty / len
                        val ny = tx / len
                        left.add(Vec2(spine[k].x + nx * hw, spine[k].y + ny * hw))
                        right.add(Vec2(spine[k].x - nx * hw, spine[k].y - ny * hw))
                    }
                    val mid = spine[(idx + endIdx) / 2]
                    if (mid.x in -0.15f..1.15f && mid.y in -0.15f..(aspect + 0.15f)) {
                        val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                        val area = 0.0022f
                        cells.add(
                            DraftCell(score = score, area = area) { threshold ->
                                listOf(
                                    PolygonPrimitive(
                                        vertices = left + right.asReversed(),
                                        threshold = threshold,
                                        areaWeight = area,
                                        colorIndex = e + arm,
                                        secondaryColorIndex = e + arm + 2,
                                        shadeFactor = 0.84f + (arm % 3) * 0.14f,
                                        gradientAngleRad = atan2(mid.y - cy, mid.x - cx),
                                        strokeSeamNorm = 0.0018f
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

    // 36. Lagoon Caustics (Refracted Wave-Envelope Light Cells)
    fun buildCausticWater(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (9 + (intricacy * 5f).roundToInt()).coerceIn(9, 15)
        val rows = max(14, (cols * aspect).roundToInt())
        val dx = 1f / cols.toFloat()
        val dy = aspect / rows.toFloat()
        val ph1 = rng.nextFloat() * TWO_PI
        val ph2 = rng.nextFloat() * TWO_PI
        val amp = 0.048f

        fun refract(x: Float, y: Float): Vec2 {
            val rx = x + amp * sin(x * 7.2f + y * 4.1f + ph1) + amp * 0.5f * cos(y * 9.3f - ph2)
            val ry = y + amp * cos(y * 6.4f - x * 5.2f + ph2) + amp * 0.5f * sin(x * 8.7f + ph1)
            return Vec2(rx, ry)
        }

        val cells = ArrayList<DraftCell>((cols + 2) * (rows + 2))
        for (r in -1..rows) {
            for (c in -1..cols) {
                val p00 = refract(c * dx, r * dy)
                val p10 = refract((c + 1) * dx, r * dy)
                val p11 = refract((c + 1) * dx, (r + 1) * dy)
                val p01 = refract(c * dx, (r + 1) * dy)

                val cx = (p00.x + p10.x + p11.x + p01.x) * 0.25f
                val cy = (p00.y + p10.y + p11.y + p01.y) * 0.25f
                if (cx < -0.14f || cx > 1.14f || cy < -0.14f || cy > aspect + 0.14f) continue

                val jacobianArea = (0.5f * abs((p10.x - p00.x) * (p01.y - p00.y) - (p01.x - p00.x) * (p10.y - p00.y)) +
                    0.5f * abs((p10.x - p11.x) * (p01.y - p11.y) - (p01.x - p11.x) * (p10.y - p11.y)))
                    .coerceAtLeast(0.00005f)
                val nominalArea = dx * dy
                // Compressed wave convergence focuses higher caustic intensity
                val focusIntensity = (nominalArea / jacobianArea).coerceIn(0.65f, 1.42f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val cIdx = abs((r + c) / 2 + if (focusIntensity > 1.08f) 2 else 0)

                cells.add(
                    DraftCell(score = score, area = jacobianArea) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = listOf(p00, p10, p11, p01),
                                threshold = threshold,
                                areaWeight = jacobianArea,
                                colorIndex = cIdx,
                                secondaryColorIndex = cIdx + 1,
                                shadeFactor = (0.76f * focusIntensity).coerceIn(0.70f, 1.32f),
                                gradientAngleRad = atan2(p11.y - p00.y, p11.x - p00.x),
                                strokeSeamNorm = 0.0022f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 37. Maurer Rose (Modular Polar Chord Rose Petals)
    fun buildMaurerRose(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val nPetals = listOf(5, 6, 7, 8)[rng.nextInt(4)]
        val dStepDeg = listOf(29, 41, 47, 71)[rng.nextInt(4)]
        val maxR = hypot(0.52f, aspect * 0.52f)
        val layers = (4 + (intricacy * 3f).roundToInt()).coerceIn(4, 7)
        val cells = ArrayList<DraftCell>(layers * 48)

        for (layer in 0 until layers) {
            val rScale = maxR * (0.28f + 0.72f * ((layer + 1).toFloat() / layers))
            val rotOff = layer * 0.24f

            // Rhodonea petal wedges around this harmonic shell
            val petalCount = if (nPetals % 2 == 0) nPetals * 2 else nPetals
            val lobeSpan = PI.toFloat() / nPetals
            for (p in 0 until petalCount) {
                val centerTheta = p * (TWO_PI / petalCount) + rotOff
                val pts = ArrayList<Vec2>(17)
                pts.add(Vec2(center.x + rScale * 0.14f * cos(centerTheta), center.y + rScale * 0.14f * sin(centerTheta)))
                for (s in -7..7) {
                    val localT = (s / 8f) * (lobeSpan * 0.86f)
                    val env = cos((s / 8f) * PI.toFloat() * 0.5f)
                    val rr = (rScale * (0.18f + 0.80f * env)).coerceAtLeast(0.02f)
                    val ang = centerTheta + localT
                    pts.add(Vec2(center.x + rr * cos(ang), center.y + rr * sin(ang)))
                }
                val mid = Vec2(center.x + rScale * 0.62f * cos(centerTheta), center.y + rScale * 0.62f * sin(centerTheta))
                if (mid.x in -0.16f..1.16f && mid.y in -0.16f..(aspect + 0.16f)) {
                    val area = (rScale * rScale * 0.12f).coerceAtLeast(0.0001f)
                    val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = pts,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = layer + (p % 2),
                                    secondaryColorIndex = layer + 2,
                                    shadeFactor = 0.84f + (layer % 3) * 0.14f,
                                    gradientAngleRad = centerTheta,
                                    strokeSeamNorm = 0.0020f
                                )
                            )
                        }
                    )
                }
            }

            // Maurer modular chord star overlay on outer shells
            if (layer >= layers - 2) {
                val chordCount = 72
                var prevPt: Vec2? = null
                for (k in 0..chordCount) {
                    val deg = (k * dStepDeg) % 360
                    val rad = Math.toRadians(deg.toDouble()).toFloat()
                    val r = rScale * 0.92f * sin(nPetals * rad)
                    val pt = Vec2(center.x + r * cos(rad + rotOff), center.y + r * sin(rad + rotOff))
                    val p0 = prevPt
                    if (p0 != null) {
                        val mx = (p0.x + pt.x) * 0.5f
                        val my = (p0.y + pt.y) * 0.5f
                        val score = field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
                        val area = (hypot(pt.x - p0.x, pt.y - p0.y) * 0.004f).coerceAtLeast(0.0001f)
                        cells.add(
                            DraftCell(score = score, area = area) { threshold ->
                                listOf(
                                    PolylineRibbonPrimitive(
                                        points = listOf(p0, pt),
                                        strokeWidthNorm = 0.0038f,
                                        threshold = threshold,
                                        areaWeight = area,
                                        colorIndex = layer + 3 + (k % 2),
                                        secondaryColorIndex = layer,
                                        shadeFactor = 1.22f,
                                        gradientAngleRad = atan2(pt.y - p0.y, pt.x - p0.x)
                                    )
                                )
                            }
                        )
                    }
                    prevPt = pt
                }
            }
        }
        return cells
    }

    // 38. Gielis Superformula (Morphogenetic Botanical Star Shells)
    fun buildSuperformulaBloom(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val m = listOf(6f, 8f, 10f, 12f)[rng.nextInt(4)]
        val n1 = 0.35f + rng.nextFloat() * 0.45f
        val n2 = 0.8f + rng.nextFloat() * 1.1f
        val n3 = 0.8f + rng.nextFloat() * 1.1f
        val shells = (10 + (intricacy * 6f).roundToInt()).coerceIn(10, 17)
        val steps = 96
        val maxR = hypot(0.54f, aspect * 0.54f)
        val cells = ArrayList<DraftCell>(shells * 8)

        fun superRadius(phi: Float): Float {
            val t1 = abs(cos(m * phi / 4f)).pow(n2)
            val t2 = abs(sin(m * phi / 4f)).pow(n3)
            val sum = (t1 + t2).coerceAtLeast(0.05f)
            return sum.pow(-1f / n1).coerceIn(0.18f, 1.45f)
        }

        for (sh in 0 until shells) {
            val u0 = sh.toFloat() / shells
            val u1 = (sh + 1).toFloat() / shells
            val s0 = (0.08f + 0.92f * u0) * maxR * 0.78f
            val s1 = (0.08f + 0.92f * u1) * maxR * 0.78f
            val twist = sh * 0.08f

            val chunks = 8
            val perChunk = steps / chunks
            for (ch in 0 until chunks) {
                val outer = ArrayList<Vec2>(perChunk + 1)
                val inner = ArrayList<Vec2>(perChunk + 1)
                for (k in 0..perChunk) {
                    val phi = ((ch * perChunk + k).toFloat() / steps) * TWO_PI
                    val sr = superRadius(phi)
                    val rIn = s0 * sr * 1.04f
                    val rOut = max(rIn + 0.008f, s1 * sr * 0.95f)
                    inner.add(Vec2(center.x + rIn * cos(phi + twist), center.y + rIn * sin(phi + twist)))
                    outer.add(Vec2(center.x + rOut * cos(phi + twist), center.y + rOut * sin(phi + twist)))
                }
                val mid = outer[outer.size / 2]
                if (mid.x < -0.18f || mid.x > 1.18f || mid.y < -0.18f || mid.y > aspect + 0.18f) continue
                val area = ((s1 * s1 - s0 * s0) * PI.toFloat() / chunks).coerceAtLeast(0.0001f)
                val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = outer + inner.asReversed(),
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = sh + (ch % 2),
                                secondaryColorIndex = sh + 2,
                                shadeFactor = 0.84f + (sh % 3) * 0.14f,
                                gradientAngleRad = atan2(mid.y - center.y, mid.x - center.x),
                                strokeSeamNorm = 0.0020f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 39. Fermat Parastichy (Counter-Winding Parabolic Sunflower Diamonds)
    fun buildFermatParabolic(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val count = (120 + (intricacy * 90f).roundToInt()).coerceIn(120, 220)
        val maxR = hypot(0.56f, aspect * 0.56f)
        val rotOff = rng.nextFloat() * TWO_PI
        val cells = ArrayList<DraftCell>(count)

        for (i in count downTo 1) {
            val norm = i.toFloat() / count
            val r = sqrt(norm) * maxR
            val theta = i * GOLDEN_ANGLE + rotOff
            val cx = center.x + r * cos(theta)
            val cy = center.y + r * sin(theta)
            if (cx < -0.14f || cx > 1.14f || cy < -0.14f || cy > aspect + 0.14f) continue

            val scaleLen = (0.044f + 0.088f * sqrt(norm)) * (1.06f - intricacy * 0.18f)
            val scaleWid = scaleLen * 0.64f
            val cosT = cos(theta)
            val sinT = sin(theta)

            fun local(lx: Float, ly: Float) = Vec2(
                cx + lx * cosT - ly * sinT,
                cy + lx * sinT + ly * cosT
            )

            val diamond = listOf(
                local(-scaleLen * 0.52f, 0f),
                local(0f, -scaleWid * 0.5f),
                local(scaleLen * 0.56f, 0f),
                local(0f, scaleWid * 0.5f)
            )
            val core = listOf(
                local(-scaleLen * 0.24f, 0f),
                local(0f, -scaleWid * 0.22f),
                local(scaleLen * 0.28f, 0f),
                local(0f, scaleWid * 0.22f)
            )
            val area = (scaleLen * scaleWid * 0.5f).coerceAtLeast(0.0001f)
            val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
            val cIdx = (i % 5) + (norm * 3f).toInt()

            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    listOf(
                        PolygonPrimitive(
                            vertices = diamond,
                            threshold = threshold,
                            areaWeight = area * 0.76f,
                            colorIndex = cIdx,
                            secondaryColorIndex = cIdx + 1,
                            shadeFactor = 0.84f + 0.28f * (1f - norm),
                            gradientAngleRad = theta,
                            strokeSeamNorm = 0.0020f
                        ),
                        PolygonPrimitive(
                            vertices = core,
                            threshold = threshold,
                            areaWeight = area * 0.24f,
                            colorIndex = cIdx + 2,
                            secondaryColorIndex = cIdx,
                            shadeFactor = 1.20f,
                            gradientAngleRad = theta + PI.toFloat() * 0.5f,
                            strokeSeamNorm = 0.0014f
                        )
                    )
                }
            )
        }
        return cells
    }

    // 40. Metatron Star (13-Node Sacred Fruit-of-Life Polyhedral Lattice)
    fun buildMetatronPolyhedra(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val unitR = min(0.20f, aspect * 0.16f)
        val rotOff = rng.nextFloat() * (PI.toFloat() / 6f)
        val nodes = ArrayList<Vec2>(19)
        nodes.add(center)
        for (i in 0 until 6) {
            val ang = rotOff + i * (TWO_PI / 6f)
            nodes.add(Vec2(center.x + unitR * 2f * cos(ang), center.y + unitR * 2f * sin(ang)))
        }
        for (i in 0 until 6) {
            val ang = rotOff + i * (TWO_PI / 6f)
            nodes.add(Vec2(center.x + unitR * 4f * cos(ang), center.y + unitR * 4f * sin(ang)))
            val midAng = ang + PI.toFloat() / 6f
            nodes.add(Vec2(center.x + unitR * 3.464f * cos(midAng), center.y + unitR * 3.464f * sin(midAng)))
        }

        val cells = ArrayList<DraftCell>(160)

        // Sacred concentric discs at each Fruit-of-Life node
        for ((idx, node) in nodes.withIndex()) {
            val score = field.evaluate(node.x.coerceIn(0f, 1f), node.y.coerceIn(0f, aspect), rng)
            val ringArea = (PI.toFloat() * unitR * unitR * 0.55f).coerceAtLeast(0.0001f)
            cells.add(
                DraftCell(score = score, area = ringArea) { threshold ->
                    listOf(
                        ArcSectorPrimitive(
                            center = node,
                            innerRadius = unitR * 0.52f,
                            outerRadius = unitR * 0.92f,
                            startAngleDeg = 0f,
                            sweepAngleDeg = 360f,
                            threshold = threshold,
                            areaWeight = ringArea * 0.68f,
                            colorIndex = idx,
                            secondaryColorIndex = idx + 2,
                            shadeFactor = 1.06f,
                            gradientAngleRad = idx * 0.52f
                        ),
                        ArcSectorPrimitive(
                            center = node,
                            innerRadius = 0f,
                            outerRadius = unitR * 0.34f,
                            startAngleDeg = 0f,
                            sweepAngleDeg = 360f,
                            threshold = threshold,
                            areaWeight = ringArea * 0.32f,
                            colorIndex = idx + 3,
                            secondaryColorIndex = idx + 1,
                            shadeFactor = 1.24f,
                            gradientAngleRad = -idx * 0.52f
                        )
                    )
                }
            )
        }

        // Polyhedral Metatron chord beams connecting the first 13 sacred nodes
        val coreCount = min(13, nodes.size)
        val maxBeamConnections = if (intricacy >= 0.55f) coreCount else 7
        for (i in 0 until maxBeamConnections) {
            for (j in i + 1 until coreCount) {
                val a = nodes[i]
                val b = nodes[j]
                val mx = (a.x + b.x) * 0.5f
                val my = (a.y + b.y) * 0.5f
                val len = hypot(b.x - a.x, b.y - a.y)
                if (len < 0.01f) continue
                val score = field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
                val area = (len * 0.0045f).coerceAtLeast(0.0001f)
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolylineRibbonPrimitive(
                                points = listOf(a, b),
                                strokeWidthNorm = 0.0042f,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = (i + j) % 6,
                                secondaryColorIndex = (i + j + 2) % 6,
                                shadeFactor = 1.15f,
                                gradientAngleRad = atan2(b.y - a.y, b.x - a.x)
                            )
                        )
                    }
                )
            }
        }
        return cells
    }
}

package com.example.engine

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

internal object ChaoticSacredPatterns {

    private const val TWO_PI = (2.0 * PI).toFloat()

    // 49. Thomas Labyrinth (Cyclically Symmetric 3D Strange-Attractor Braids)
    fun buildThomasAttractor(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val trajectories = (7 + (intricacy * 4f).roundToInt()).coerceIn(7, 11)
        val steps = (190 + (intricacy * 80f).roundToInt()).coerceIn(190, 270)
        val b = 0.188f + rng.nextFloat() * 0.014f
        val dt = 0.115f
        val yaw = rng.nextFloat() * TWO_PI
        val pitch = 0.52f + rng.nextFloat() * 0.35f
        val cosY = cos(yaw)
        val sinY = sin(yaw)
        val cosP = cos(pitch)
        val sinP = sin(pitch)

        val cells = ArrayList<DraftCell>(trajectories * (steps / 5))

        for (tr in 0 until trajectories) {
            val angle0 = (tr.toFloat() / trajectories) * TWO_PI
            var x = 1.6f * cos(angle0) + (rng.nextFloat() - 0.5f) * 0.4f
            var y = 1.6f * sin(angle0) + (rng.nextFloat() - 0.5f) * 0.4f
            var z = ((tr % 3) - 1f) * 1.35f + (rng.nextFloat() - 0.5f) * 0.3f

            repeat(40) {
                val dx = sin(y) - b * x
                val dy = sin(z) - b * y
                val dz = sin(x) - b * z
                x += dx * dt
                y += dy * dt
                z += dz * dt
            }

            val spine = ArrayList<Vec2>(steps + 1)
            for (s in 0..steps) {
                val rx = x * cosY - y * sinY
                val ry0 = x * sinY + y * cosY
                val ry = ry0 * cosP - z * sinP
                spine.add(
                    Vec2(
                        0.5f + (rx / 4.4f) * 0.48f,
                        aspect * (0.5f + (ry / 4.4f) * 0.46f)
                    )
                )
                val dx = sin(y) - b * x
                val dy = sin(z) - b * y
                val dz = sin(x) - b * z
                x += dx * dt
                y += dy * dt
                z += dz * dt
            }

            val baseHalfW = 0.015f + (1f - intricacy * 0.3f) * 0.008f
            emitRibbonSpineCells(
                cells = cells,
                spine = spine,
                chunk = 5,
                baseHalfW = baseHalfW,
                colorBase = tr * 2,
                shade = 0.86f + (tr % 3) * 0.14f,
                field = field,
                rng = rng,
                aspect = aspect
            )
        }
        return cells
    }

    // 50. Airy Diffraction (Parabolic Catastrophe Wavefront Interference Fringes)
    fun buildAiryWavefront(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val bands = (13 + (intricacy * 6f).roundToInt()).coerceIn(13, 19)
        val cols = (20 + (intricacy * 8f).roundToInt()).coerceIn(20, 28)
        val causticFocusX = 0.32f + rng.nextFloat() * 0.36f
        val curvature = (if (rng.nextBoolean()) 1f else -1f) * (0.26f + rng.nextFloat() * 0.22f)
        val omega = 4.2f + rng.nextFloat() * 2.4f
        val phase = rng.nextFloat() * TWO_PI
        val cells = ArrayList<DraftCell>(bands * cols)

        for (b in 0 until bands) {
            val vNorm = (b + 0.5f) / bands.toFloat()
            val airySpacing = vNorm.pow(0.82f)
            val baseY = -0.04f * aspect + airySpacing * (aspect * 1.08f)
            val bandAmp = (0.024f + 0.036f * sin(vNorm * PI.toFloat())) * aspect
            val halfThick = (aspect / bands) * (0.31f + 0.12f * cos(b * 0.7f))

            for (c in 0 until cols) {
                val x0 = -0.03f + (c.toFloat() / cols) * 1.06f
                val x1 = -0.03f + ((c + 1).toFloat() / cols) * 1.06f

                fun waveY(x: Float): Float {
                    val dx = x - causticFocusX
                    val fold = curvature * dx * dx * aspect * 0.55f * (1f - 0.65f * vNorm)
                    val osc = bandAmp * sin(omega * x + phase + b * 0.52f)
                    return baseY + fold + osc
                }

                val y0 = waveY(x0)
                val y1 = waveY(x1)
                val env0 = 0.65f + 0.35f * sin(x0 * PI.toFloat() + b * 0.4f)
                val env1 = 0.65f + 0.35f * sin(x1 * PI.toFloat() + b * 0.4f)
                val w0 = halfThick * env0
                val w1 = halfThick * env1

                val quad = listOf(
                    Vec2(x0, y0 - w0),
                    Vec2(x1, y1 - w1),
                    Vec2(x1, y1 + w1),
                    Vec2(x0, y0 + w0)
                )
                val cx = (x0 + x1) * 0.5f
                val cy = (y0 + y1) * 0.5f
                if (cy < -0.12f || cy > aspect + 0.12f) continue

                val area = ((x1 - x0) * (w0 + w1)).coerceAtLeast(0.0001f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = quad,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = b + (c / 5),
                                secondaryColorIndex = b + (c / 5) + 2,
                                shadeFactor = (0.82f + 0.32f * env0).coerceIn(0.72f, 1.24f),
                                gradientAngleRad = atan2(y1 - y0, x1 - x0),
                                strokeSeamNorm = 0.0018f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 51. Rössler Vortex (Folded Chaotic Band & Saddle-Focus Loops)
    fun buildRosslerSpiral(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val bands = (6 + (intricacy * 4f).roundToInt()).coerceIn(6, 10)
        val steps = (210 + (intricacy * 80f).roundToInt()).coerceIn(210, 290)
        val a = 0.2f
        val b = 0.2f
        val cParam = 5.7f
        val dt = 0.042f
        val rot = rng.nextFloat() * TWO_PI
        val cosR = cos(rot)
        val sinR = sin(rot)
        val cells = ArrayList<DraftCell>(bands * (steps / 5))

        for (bd in 0 until bands) {
            var x = 2.0f + bd * 0.85f + (rng.nextFloat() - 0.5f) * 0.3f
            var y = -1.5f + (rng.nextFloat() - 0.5f) * 0.4f
            var z = 0.2f

            repeat(90) {
                val dx = -y - z
                val dy = x + a * y
                val dz = b + z * (x - cParam)
                x += dx * dt
                y += dy * dt
                z += dz * dt
            }

            val spine = ArrayList<Vec2>(steps + 1)
            for (s in 0..steps) {
                val nx = x / 11.8f
                val ny = y / 11.8f
                val nz = (z - 4.5f) / 14.5f
                val ux = nx * cosR - ny * sinR
                val uy = (nx * sinR + ny * cosR) * 0.72f - nz * 0.62f
                spine.add(
                    Vec2(
                        0.5f + ux * 0.46f,
                        aspect * (0.54f + uy * 0.42f)
                    )
                )
                val dx = -y - z
                val dy = x + a * y
                val dz = b + z * (x - cParam)
                x += dx * dt
                y += dy * dt
                z += dz * dt
            }

            val baseHalfW = 0.014f + (1f - intricacy * 0.3f) * 0.008f
            emitRibbonSpineCells(
                cells = cells,
                spine = spine,
                chunk = 5,
                baseHalfW = baseHalfW,
                colorBase = bd,
                shade = 0.84f + (bd % 3) * 0.16f,
                field = field,
                rng = rng,
                aspect = aspect
            )
        }
        return cells
    }

    // 52. Cassini Ovals (Bipolar Constant-Product Equipotential Shells & Lemniscates)
    fun buildCassiniLemniscate(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cx = 0.5f
        val cy = aspect * 0.5f
        val tilt = (rng.nextFloat() - 0.5f) * 0.65f + (PI.toFloat() * 0.5f)
        val cosT = cos(tilt)
        val sinT = sin(tilt)
        val focalHalfDist = min(0.32f, aspect * 0.24f)

        val outerShells = (7 + (intricacy * 4f).roundToInt()).coerceIn(7, 11)
        val innerLoops = 4
        val angSteps = (44 + (intricacy * 16f).roundToInt()).coerceIn(44, 60)
        val cells = ArrayList<DraftCell>((outerShells + innerLoops * 2) * angSteps)

        fun toWorld(u: Float, v: Float): Vec2 = Vec2(
            cx + u * cosT - v * sinT,
            cy + u * sinT + v * cosT
        )

        // Inner twin focal loops (b < a: two disjoint ovals around +a and -a)
        for (lobe in listOf(-1f, 1f)) {
            val focusU = lobe * focalHalfDist
            for (s in 0 until innerLoops) {
                val r0 = focalHalfDist * (0.14f + 0.17f * s)
                val r1 = r0 + focalHalfDist * 0.135f
                val segs = 26
                for (k in 0 until segs) {
                    val a0 = (k.toFloat() / segs) * TWO_PI
                    val a1 = ((k + 1).toFloat() / segs) * TWO_PI
                    val stretchU = 1f + 0.18f * (s + 1) / innerLoops
                    val p00 = toWorld(focusU + r0 * cos(a0) * stretchU, r0 * sin(a0) * 0.88f)
                    val p10 = toWorld(focusU + r0 * cos(a1) * stretchU, r0 * sin(a1) * 0.88f)
                    val p11 = toWorld(focusU + r1 * cos(a1) * stretchU, r1 * sin(a1) * 0.88f)
                    val p01 = toWorld(focusU + r1 * cos(a0) * stretchU, r1 * sin(a0) * 0.88f)
                    emitQuadCell(cells, p00, p10, p11, p01, s + if (lobe > 0) 2 else 0, 1.08f, field, rng, aspect)
                }
            }
        }

        // Lemniscate & outer Cassini peanut/oval shells: r^2(theta) = a^2 cos(2 theta) + sqrt(b^4 - a^4 sin^2(2 theta))
        val a2 = focalHalfDist * focalHalfDist
        val a4 = a2 * a2
        for (sh in 0 until outerShells) {
            val b0 = focalHalfDist * (1.03f + sh * 0.24f)
            val b1 = b0 + focalHalfDist * 0.19f
            val b0Pow4 = b0.pow(4)
            val b1Pow4 = b1.pow(4)

            fun cassiniRadius(bPow4: Float, theta: Float): Float {
                val c2 = cos(2f * theta)
                val s2 = sin(2f * theta)
                val disc = (bPow4 - a4 * s2 * s2).coerceAtLeast(0.00001f)
                val r2 = (a2 * c2 + sqrt(disc)).coerceAtLeast(0.0001f)
                return sqrt(r2)
            }

            for (k in 0 until angSteps) {
                val t0 = (k.toFloat() / angSteps) * TWO_PI
                val t1 = ((k + 1).toFloat() / angSteps) * TWO_PI
                val rad00 = cassiniRadius(b0Pow4, t0)
                val rad10 = cassiniRadius(b0Pow4, t1)
                val rad11 = cassiniRadius(b1Pow4, t1)
                val rad01 = cassiniRadius(b1Pow4, t0)

                val p00 = toWorld(rad00 * cos(t0), rad00 * sin(t0))
                val p10 = toWorld(rad10 * cos(t1), rad10 * sin(t1))
                val p11 = toWorld(rad11 * cos(t1), rad11 * sin(t1))
                val p01 = toWorld(rad01 * cos(t0), rad01 * sin(t0))
                val shade = 0.84f + 0.28f * (0.5f + 0.5f * cos(2f * t0))
                emitQuadCell(cells, p00, p10, p11, p01, sh + (k / 6), shade, field, rng, aspect)
            }
        }
        return cells
    }

    // 53. Astroid Cusps (Nested Hypocycloid Star Envelopes & Tangent Facets)
    fun buildDeltoidAstroid(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val cusps = listOf(4, 5, 6)[rng.nextInt(3)]
        val shells = (9 + (intricacy * 5f).roundToInt()).coerceIn(9, 14)
        val steps = cusps * 14
        val maxR = hypot(0.54f, aspect * 0.52f)
        val baseRot = rng.nextFloat() * TWO_PI
        val twistStep = (if (rng.nextBoolean()) 1f else -1f) * (PI.toFloat() / (cusps * 3.2f))
        val cells = ArrayList<DraftCell>(shells * steps)

        fun hypocycloidPoint(radius: Float, t: Float, rot: Float): Vec2 {
            val k = cusps.toFloat()
            val rawX = ((k - 1f) * cos(t) + cos((k - 1f) * t)) / k
            val rawY = ((k - 1f) * sin(t) - sin((k - 1f) * t)) / k
            val rx = rawX * cos(rot) - rawY * sin(rot)
            val ry = rawX * sin(rot) + rawY * cos(rot)
            return Vec2(center.x + radius * rx, center.y + radius * ry)
        }

        for (sh in 0 until shells) {
            val r0 = maxR * (0.08f + 0.94f * (sh.toFloat() / shells))
            val r1 = maxR * (0.08f + 0.94f * ((sh + 0.82f) / shells))
            val rot0 = baseRot + sh * twistStep
            val rot1 = baseRot + (sh + 0.82f) * twistStep

            for (s in 0 until steps) {
                val t0 = (s.toFloat() / steps) * TWO_PI
                val t1 = ((s + 1).toFloat() / steps) * TWO_PI
                val p00 = hypocycloidPoint(r0, t0, rot0)
                val p10 = hypocycloidPoint(r0, t1, rot0)
                val p11 = hypocycloidPoint(r1, t1, rot1)
                val p01 = hypocycloidPoint(r1, t0, rot1)
                val shade = 0.82f + 0.34f * (0.5f + 0.5f * cos(cusps * t0))
                emitQuadCell(cells, p00, p10, p11, p01, sh + (s / 7), shade, field, rng, aspect)
            }
        }
        return cells
    }

    // 54. Zellij Khatam (16-Fold Moroccan Star & Saft Petal Rosette)
    fun buildZellijKhatam(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val symmetry = if (rng.nextBoolean()) 16 else 12
        val rings = (6 + (intricacy * 4f).roundToInt()).coerceIn(6, 10)
        val maxR = hypot(0.58f, aspect * 0.54f)
        val rot0 = rng.nextFloat() * (TWO_PI / symmetry)
        val dTheta = TWO_PI / symmetry
        val halfTheta = dTheta * 0.5f
        val cells = ArrayList<DraftCell>(rings * symmetry * 3)

        fun polar(r: Float, ang: Float) = Vec2(center.x + r * cos(ang), center.y + r * sin(ang))

        // Central khatam star petals
        val rCore = maxR * 0.13f
        val rInnerValley = rCore * 0.52f
        for (i in 0 until symmetry) {
            val aMid = rot0 + i * dTheta
            val pCenter = polar(rCore * 0.14f, aMid)
            val pLeft = polar(rInnerValley, aMid - halfTheta)
            val pTip = polar(rCore, aMid)
            val pRight = polar(rInnerValley, aMid + halfTheta)
            emitQuadCell(cells, pCenter, pLeft, pTip, pRight, i % 4, 1.16f, field, rng, aspect)
        }

        // Concentric saft & lozenge rosette rings
        for (rg in 0 until rings) {
            val u0 = 0.13f + 0.89f * (rg.toFloat() / rings)
            val u1 = 0.13f + 0.89f * ((rg + 1).toFloat() / rings)
            val rIn = maxR * u0
            val rMid = maxR * ((u0 + u1) * 0.5f)
            val rOut = maxR * u1
            val phase = rot0 + if (rg % 2 == 1) halfTheta else 0f

            for (i in 0 until symmetry) {
                val a0 = phase + i * dTheta
                val aHalf = a0 + halfTheta
                val a1 = a0 + dTheta

                // Diamond kite (saft)
                val kIn = polar(rIn * 1.03f, a0)
                val kRight = polar(rMid, aHalf - halfTheta * 0.34f)
                val kOut = polar(rOut * 0.97f, a0)
                val kLeft = polar(rMid, a0 - halfTheta * 0.66f)
                emitQuadCell(cells, kIn, kRight, kOut, kLeft, rg * 2 + (i % 2), 1.12f, field, rng, aspect)

                // Interlocking hexagonal/lozenge bridge between adjacent kites
                val bIn = polar(rIn * 1.08f, aHalf)
                val bR = polar(rMid, a1 - halfTheta * 0.66f)
                val bOut = polar(rOut * 0.95f, aHalf)
                val bL = polar(rMid, a0 + halfTheta * 0.66f)
                emitQuadCell(cells, bIn, bR, bOut, bL, rg * 2 + 1, 0.88f, field, rng, aspect)
            }
        }
        return cells
    }

    // 55. Vesica Piscis (7-Fold Seed & Flower of Life Overlapping Almond Lenses)
    fun buildVesicaLenses(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (4 + (intricacy * 3f).roundToInt()).coerceIn(4, 7)
        val radius = 1f / cols.toFloat()
        val dx = radius
        val dy = radius * (sqrt(3f) * 0.5f)
        val rows = ((aspect / dy).roundToInt() + 3).coerceAtLeast(6)
        val neighborAngles = floatArrayOf(0f, PI.toFloat() / 3f, 2f * PI.toFloat() / 3f)
        val cells = ArrayList<DraftCell>(rows * cols * 4)

        for (r in -1..rows) {
            val cy = r * dy
            val xShift = if (r.mod(2) == 1) dx * 0.5f else 0f
            for (c in -1..cols + 1) {
                val cx = c * dx + xShift

                // 3 unique vesica piscis lenses per hexagonal lattice node (0°, 60°, 120°)
                for ((dirIdx, dirAng) in neighborAngles.withIndex()) {
                    val nx = cx + radius * cos(dirAng)
                    val ny = cy + radius * sin(dirAng)
                    val mx = (cx + nx) * 0.5f
                    val my = (cy + ny) * 0.5f
                    if (mx < -0.12f || mx > 1.12f || my < -0.12f || my > aspect + 0.12f) continue

                    // Build exact convex vesica lens bounded by two shrunk 120° circular arcs
                    val shrinkR = radius * 0.94f
                    val arcSamples = 7
                    val verts = ArrayList<Vec2>((arcSamples + 1) * 2)
                    val span = PI.toFloat() / 3f * 0.92f

                    // Arc centered at C1 facing C2
                    for (s in 0..arcSamples) {
                        val a = dirAng - span + (2f * span) * (s.toFloat() / arcSamples)
                        verts.add(Vec2(cx + shrinkR * cos(a), cy + shrinkR * sin(a)))
                    }
                    // Arc centered at C2 facing C1
                    val oppAng = dirAng + PI.toFloat()
                    for (s in 0..arcSamples) {
                        val a = oppAng - span + (2f * span) * (s.toFloat() / arcSamples)
                        verts.add(Vec2(nx + shrinkR * cos(a), ny + shrinkR * sin(a)))
                    }

                    val area = (0.614f * shrinkR * shrinkR).coerceAtLeast(0.0001f)
                    val score = field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
                    val colorIdx = abs(r * 2 + c + dirIdx)
                    val shade = 0.86f + 0.15f * dirIdx
                    cells.add(
                        DraftCell(score = score, area = area) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = verts,
                                    threshold = threshold,
                                    areaWeight = area,
                                    colorIndex = colorIdx,
                                    secondaryColorIndex = colorIdx + 2,
                                    shadeFactor = shade,
                                    gradientAngleRad = dirAng + PI.toFloat() * 0.5f,
                                    strokeSeamNorm = 0.0018f
                                )
                            )
                        }
                    )
                }
            }
        }
        return cells
    }

    // 56. Triskelion Spirals (Multi-Arm Counter-Spun Archimedean Blades)
    fun buildTriskelionSpirals(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val center = Vec2(0.5f, aspect * 0.5f)
        val arms = listOf(3, 5, 6)[rng.nextInt(3)]
        val subBands = 2
        val totalStreams = arms * subBands
        val radialSteps = (26 + (intricacy * 12f).roundToInt()).coerceIn(26, 38)
        val maxR = hypot(0.58f, aspect * 0.56f)
        val spin = (if (rng.nextBoolean()) 1f else -1f) * (3.6f + rng.nextFloat() * 1.8f)
        val basePhase = rng.nextFloat() * TWO_PI
        val cells = ArrayList<DraftCell>(totalStreams * radialSteps)

        val dStream = TWO_PI / totalStreams
        for (st in 0 until totalStreams) {
            val streamOffset = basePhase + st * dStream
            val angularFill = dStream * 0.78f

            for (rs in 0 until radialSteps) {
                val u0 = (rs.toFloat() / radialSteps).pow(0.88f)
                val u1 = ((rs + 1).toFloat() / radialSteps).pow(0.88f)
                val r0 = 0.025f + u0 * maxR
                val r1 = 0.025f + u1 * maxR

                val a0 = streamOffset + spin * u0
                val a1 = streamOffset + spin * u1
                val w0 = angularFill * (0.55f + 0.45f * sin(u0 * PI.toFloat()).coerceAtLeast(0.25f))
                val w1 = angularFill * (0.55f + 0.45f * sin(u1 * PI.toFloat()).coerceAtLeast(0.25f))

                val p00 = Vec2(center.x + r0 * cos(a0 - w0 * 0.5f), center.y + r0 * sin(a0 - w0 * 0.5f))
                val p10 = Vec2(center.x + r0 * cos(a0 + w0 * 0.5f), center.y + r0 * sin(a0 + w0 * 0.5f))
                val p11 = Vec2(center.x + r1 * cos(a1 + w1 * 0.5f), center.y + r1 * sin(a1 + w1 * 0.5f))
                val p01 = Vec2(center.x + r1 * cos(a1 - w1 * 0.5f), center.y + r1 * sin(a1 - w1 * 0.5f))

                val shade = if (st % 2 == 0) 1.14f else 0.84f
                emitQuadCell(cells, p00, p10, p11, p01, (st / subBands) + (rs / 5), shade, field, rng, aspect)
            }
        }
        return cells
    }

    private fun emitRibbonSpineCells(
        cells: MutableList<DraftCell>,
        spine: List<Vec2>,
        chunk: Int,
        baseHalfW: Float,
        colorBase: Int,
        shade: Float,
        field: SculptField,
        rng: Random,
        aspect: Float
    ) {
        val total = spine.size.coerceAtLeast(2)
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
                val taper = 0.62f + 0.38f * sin((k.toFloat() / total) * TWO_PI * 2f)
                val w = baseHalfW * taper
                left.add(Vec2(spine[k].x + nx * w, spine[k].y + ny * w))
                right.add(Vec2(spine[k].x - nx * w, spine[k].y - ny * w))
            }
            val mid = spine[(idx + endIdx) / 2]
            if (mid.x in -0.14f..1.14f && mid.y in -0.14f..(aspect + 0.14f)) {
                val poly = left + right.asReversed()
                val score = field.evaluate(mid.x.coerceIn(0f, 1f), mid.y.coerceIn(0f, aspect), rng)
                val segLen = hypot(spine[endIdx].x - spine[idx].x, spine[endIdx].y - spine[idx].y)
                val area = (baseHalfW * 2f * max(segLen, 0.03f)).coerceAtLeast(0.0001f)
                val gradAng = atan2(spine[endIdx].y - spine[idx].y, spine[endIdx].x - spine[idx].x)
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = poly,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = colorBase + (idx / 20),
                                secondaryColorIndex = colorBase + (idx / 20) + 2,
                                shadeFactor = shade,
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

    private fun emitQuadCell(
        cells: MutableList<DraftCell>,
        p0: Vec2,
        p1: Vec2,
        p2: Vec2,
        p3: Vec2,
        colorIndex: Int,
        shade: Float,
        field: SculptField,
        rng: Random,
        aspect: Float
    ) {
        val cx = (p0.x + p1.x + p2.x + p3.x) * 0.25f
        val cy = (p0.y + p1.y + p2.y + p3.y) * 0.25f
        if (cx < -0.14f || cx > 1.14f || cy < -0.14f || cy > aspect + 0.14f) return
        val area = (0.5f * abs((p1.x - p0.x) * (p3.y - p0.y) - (p3.x - p0.x) * (p1.y - p0.y)) +
            0.5f * abs((p1.x - p2.x) * (p3.y - p2.y) - (p3.x - p2.x) * (p1.y - p2.y)))
            .coerceAtLeast(0.00008f)
        val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
        val gradAng = atan2(p2.y - p0.y, p2.x - p0.x)

        cells.add(
            DraftCell(score = score, area = area) { threshold ->
                listOf(
                    PolygonPrimitive(
                        vertices = listOf(p0, p1, p2, p3),
                        threshold = threshold,
                        areaWeight = area,
                        colorIndex = abs(colorIndex),
                        secondaryColorIndex = abs(colorIndex) + 2,
                        shadeFactor = shade.coerceIn(0.65f, 1.30f),
                        gradientAngleRad = gradAng,
                        strokeSeamNorm = 0.0019f
                    )
                )
            }
        )
    }
}

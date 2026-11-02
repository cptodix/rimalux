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

internal object ParametricQuasicrystalPatterns {

    private const val TWO_PI = (2.0 * PI).toFloat()

    // 57. Escher Staircases (Axonometric Multi-Directional Flights, Treads & Risers)
    fun buildEscherStaircases(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (4 + (intricacy * 3f).roundToInt()).coerceIn(4, 7)
        val unitR = 1f / (cols * 1.45f)
        val cos30 = 0.8660254f
        val sin30 = 0.5f
        val stepX = unitR * cos30 * 2f
        val stepY = unitR * 1.5f
        val rows = ((aspect / stepY).roundToInt() + 4).coerceAtLeast(8)
        val stepsPerFlight = 4
        val cells = ArrayList<DraftCell>(rows * cols * 4)

        for (r in -1..rows) {
            val baseY = r * stepY
            val xOff = if (r.mod(2) == 1) stepX * 0.5f else 0f
            for (c in -1..cols + 1) {
                val cx = c * stepX + xOff
                val cy = baseY
                if (cx < -0.18f || cx > 1.18f || cy < -0.18f || cy > aspect + 0.18f) continue

                val orientation = rng.nextInt(3)
                val dirSign = if (orientation == 0) 1f else -1f
                val sRadius = unitR * 0.90f

                // Isometric basis vectors: eRight (down-right 30°), eLeft (down-left 150°), eUp (0, -1)
                val eClimb = if (orientation == 2) {
                    Vec2(-cos30 * sRadius, -sin30 * sRadius)
                } else {
                    Vec2(dirSign * cos30 * sRadius, -sin30 * sRadius)
                }
                val eWidth = if (orientation == 2) {
                    Vec2(cos30 * sRadius * 0.88f, -sin30 * sRadius * 0.88f)
                } else {
                    Vec2(-dirSign * cos30 * sRadius * 0.88f, -sin30 * sRadius * 0.88f)
                }
                val riseTotal = sRadius * 0.86f
                val origin = Vec2(cx - (eClimb.x + eWidth.x) * 0.5f, cy + sRadius * 0.52f)
                val colorSeed = abs(r * 3 + c * 2)

                for (st in 0 until stepsPerFlight) {
                    val u0 = st.toFloat() / stepsPerFlight
                    val u1 = (st + 1).toFloat() / stepsPerFlight
                    val z0 = u0 * riseTotal
                    val z1 = u1 * riseTotal

                    // Riser vertical face (from z0 to z1 at u0)
                    val rB0 = Vec2(origin.x + eClimb.x * u0, origin.y + eClimb.y * u0 - z0)
                    val rB1 = Vec2(rB0.x + eWidth.x, rB0.y + eWidth.y)
                    val rT1 = Vec2(rB1.x, rB1.y - (z1 - z0))
                    val rT0 = Vec2(rB0.x, rB0.y - (z1 - z0))

                    // Tread horizontal top face (at z1 from u0 to u1)
                    val tF0 = rT0
                    val tF1 = rT1
                    val tK1 = Vec2(origin.x + eClimb.x * u1 + eWidth.x, origin.y + eClimb.y * u1 + eWidth.y - z1)
                    val tK0 = Vec2(origin.x + eClimb.x * u1, origin.y + eClimb.y * u1 - z1)

                    // Side stringer slice under step
                    val s0 = Vec2(origin.x + eClimb.x * u0, origin.y + eClimb.y * u0)
                    val s1 = Vec2(origin.x + eClimb.x * u1, origin.y + eClimb.y * u1)
                    val s2 = tK0
                    val s3 = rT0

                    val midX = (tF0.x + tK1.x) * 0.5f
                    val midY = (tF0.y + tK1.y) * 0.5f
                    val score = field.evaluate(midX.coerceIn(0f, 1f), midY.coerceIn(0f, aspect), rng)
                    val cellArea = (sRadius * sRadius * 0.42f / stepsPerFlight).coerceAtLeast(0.0001f)

                    cells.add(
                        DraftCell(score = score, area = cellArea) { threshold ->
                            listOf(
                                PolygonPrimitive(
                                    vertices = listOf(s0, s1, s2, s3),
                                    threshold = threshold,
                                    areaWeight = cellArea * 0.34f,
                                    colorIndex = colorSeed,
                                    secondaryColorIndex = colorSeed + 1,
                                    shadeFactor = 0.68f,
                                    gradientAngleRad = 1.57f,
                                    strokeSeamNorm = 0.0018f
                                ),
                                PolygonPrimitive(
                                    vertices = listOf(rB0, rB1, rT1, rT0),
                                    threshold = threshold,
                                    areaWeight = cellArea * 0.30f,
                                    colorIndex = colorSeed,
                                    secondaryColorIndex = colorSeed + 2,
                                    shadeFactor = 0.92f,
                                    gradientAngleRad = 0.52f,
                                    strokeSeamNorm = 0.0018f
                                ),
                                PolygonPrimitive(
                                    vertices = listOf(tF0, tF1, tK1, tK0),
                                    threshold = threshold,
                                    areaWeight = cellArea * 0.36f,
                                    colorIndex = colorSeed + (st % 2),
                                    secondaryColorIndex = colorSeed + 2,
                                    shadeFactor = 1.22f,
                                    gradientAngleRad = -0.52f,
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

    // 58. Kinetic Facade (Twisted Parametric Brise-Soleil Sun-Breaker Blades)
    fun buildKineticFacade(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val cols = (9 + (intricacy * 5f).roundToInt()).coerceIn(9, 14)
        val rows = (14 + (intricacy * 8f).roundToInt()).coerceIn(14, 22)
        val cellW = 1f / cols.toFloat()
        val cellH = aspect / rows.toFloat()
        val wavePhase = rng.nextFloat() * TWO_PI
        val waveFreqX = 2.8f + rng.nextFloat() * 2.2f
        val waveFreqY = 2.4f + rng.nextFloat() * 2.0f
        val cells = ArrayList<DraftCell>(cols * rows)

        for (r in 0 until rows) {
            val ny = (r + 0.5f) / rows.toFloat()
            for (c in 0 until cols) {
                val nx = (c + 0.5f) / cols.toFloat()
                val cx = (c + 0.5f) * cellW
                val cy = (r + 0.5f) * cellH

                // Parametric twist angle smoothly rotates across facade wave
                val wave = sin(nx * waveFreqX + ny * waveFreqY * PI.toFloat() * 0.6f + wavePhase)
                val openFactor = 0.24f + 0.62f * (0.5f + 0.5f * wave)
                val skewY = wave * cellH * 0.24f

                val finW = cellW * 0.84f * openFactor
                val edgeW = cellW * 0.16f * (1.15f - openFactor * 0.5f)
                val halfH = cellH * 0.44f

                val xLeft = cx - (finW + edgeW) * 0.5f
                val xMid = xLeft + finW
                val xRight = xMid + edgeW

                val frontBlade = listOf(
                    Vec2(xLeft, cy - halfH - skewY * 0.5f),
                    Vec2(xMid, cy - halfH + skewY * 0.5f),
                    Vec2(xMid, cy + halfH + skewY * 0.5f),
                    Vec2(xLeft, cy + halfH - skewY * 0.5f)
                )
                val bevelEdge = listOf(
                    Vec2(xMid, cy - halfH + skewY * 0.5f),
                    Vec2(xRight, cy - halfH * 0.88f + skewY * 0.8f),
                    Vec2(xRight, cy + halfH * 0.88f + skewY * 0.8f),
                    Vec2(xMid, cy + halfH + skewY * 0.5f)
                )

                val area = ((finW + edgeW) * halfH * 2f).coerceAtLeast(0.0001f)
                val score = field.evaluate(cx, cy, rng)
                val sunShade = (0.78f + 0.48f * openFactor).coerceIn(0.74f, 1.28f)
                val colorIdx = c / 2 + r / 3

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = frontBlade,
                                threshold = threshold,
                                areaWeight = area * 0.82f,
                                colorIndex = colorIdx,
                                secondaryColorIndex = colorIdx + 2,
                                shadeFactor = sunShade,
                                gradientAngleRad = atan2(skewY, finW),
                                strokeSeamNorm = 0.0016f
                            ),
                            PolygonPrimitive(
                                vertices = bevelEdge,
                                threshold = threshold,
                                areaWeight = area * 0.18f,
                                colorIndex = colorIdx + 1,
                                secondaryColorIndex = colorIdx + 3,
                                shadeFactor = (1.36f - sunShade * 0.48f).coerceIn(0.64f, 1.05f),
                                gradientAngleRad = 1.57f,
                                strokeSeamNorm = 0.0014f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 59. Pantheon Coffers (Recessed Radial Dome Caissons & Luminous Oculus)
    fun buildPantheonOculus(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val oculus = Vec2(
            0.5f + (rng.nextFloat() - 0.5f) * 0.10f,
            aspect * (0.46f + (rng.nextFloat() - 0.5f) * 0.10f)
        )
        val rings = (8 + (intricacy * 4f).roundToInt()).coerceIn(8, 12)
        val ribs = listOf(16, 20, 24)[rng.nextInt(3)]
        val maxR = hypot(0.62f, aspect * 0.60f)
        val oculusR = 0.075f
        val dAng = TWO_PI / ribs
        val rot0 = rng.nextFloat() * dAng
        val cells = ArrayList<DraftCell>(rings * ribs + ribs)

        fun domePt(r: Float, ang: Float) = Vec2(oculus.x + r * cos(ang), oculus.y + r * sin(ang))

        // Luminous central oculus collar segments
        for (s in 0 until ribs) {
            val a0 = rot0 + s * dAng
            val startDeg = (a0 * 180f / PI.toFloat())
            val sweepDeg = (dAng * 180f / PI.toFloat()) * 0.88f
            val midA = a0 + dAng * 0.5f
            val mx = oculus.x + oculusR * 0.7f * cos(midA)
            val my = oculus.y + oculusR * 0.7f * sin(midA)
            val area = (0.5f * dAng * (oculusR * oculusR * 0.75f)).coerceAtLeast(0.0001f)
            val score = field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    listOf(
                        ArcSectorPrimitive(
                            center = oculus,
                            innerRadius = oculusR * 0.42f,
                            outerRadius = oculusR * 0.96f,
                            startAngleDeg = startDeg,
                            sweepAngleDeg = sweepDeg,
                            threshold = threshold,
                            areaWeight = area,
                            colorIndex = s % 4,
                            secondaryColorIndex = (s % 4) + 2,
                            shadeFactor = 1.24f,
                            gradientAngleRad = midA
                        )
                    )
                }
            )
        }

        // Stepped recessed coffer caissons radiating from oculus to perimeter
        for (rg in 0 until rings) {
            val u0 = (rg.toFloat() / rings).pow(1.28f)
            val u1 = ((rg + 1).toFloat() / rings).pow(1.28f)
            val rIn = oculusR * 1.08f + u0 * (maxR - oculusR * 1.08f)
            val rOut = oculusR * 1.08f + u1 * (maxR - oculusR * 1.08f)
            val padAng = dAng * 0.06f
            val padR = (rOut - rIn) * 0.08f

            for (s in 0 until ribs) {
                val a0 = rot0 + s * dAng + padAng
                val a1 = rot0 + (s + 1) * dAng - padAng
                val outer0 = domePt(rIn + padR, a0)
                val outer1 = domePt(rIn + padR, a1)
                val outer2 = domePt(rOut - padR, a1)
                val outer3 = domePt(rOut - padR, a0)

                val cx = (outer0.x + outer1.x + outer2.x + outer3.x) * 0.25f
                val cy = (outer0.y + outer1.y + outer2.y + outer3.y) * 0.25f
                if (cx < -0.15f || cx > 1.15f || cy < -0.15f || cy > aspect + 0.15f) continue

                // Inner recessed caisson shifted slightly toward oculus for authentic 3D relief
                val insetA0 = a0 + (a1 - a0) * 0.22f
                val insetA1 = a1 - (a1 - a0) * 0.22f
                val insetR0 = (rIn + padR) + (rOut - rIn) * 0.18f
                val insetR1 = (rOut - padR) - (rOut - rIn) * 0.24f
                val inner0 = domePt(insetR0, insetA0)
                val inner1 = domePt(insetR0, insetA1)
                val inner2 = domePt(insetR1, insetA1)
                val inner3 = domePt(insetR1, insetA0)

                val area = (0.5f * (a1 - a0) * (rOut * rOut - rIn * rIn)).coerceAtLeast(0.0001f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val cSeed = rg + (s % 3)

                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = listOf(outer0, outer1, outer2, outer3),
                                threshold = threshold,
                                areaWeight = area * 0.60f,
                                colorIndex = cSeed,
                                secondaryColorIndex = cSeed + 1,
                                shadeFactor = 1.12f,
                                gradientAngleRad = (a0 + a1) * 0.5f,
                                strokeSeamNorm = 0.0020f
                            ),
                            PolygonPrimitive(
                                vertices = listOf(inner0, inner1, inner2, inner3),
                                threshold = threshold,
                                areaWeight = area * 0.40f,
                                colorIndex = cSeed + 2,
                                secondaryColorIndex = cSeed + 3,
                                shadeFactor = 0.74f,
                                gradientAngleRad = (a0 + a1) * 0.5f + 1.57f,
                                strokeSeamNorm = 0.0018f
                            )
                        )
                    }
                )
            }
        }
        return cells
    }

    // 60. Suprematist Planes (Malevich & Lissitzky Cantilevered Constructivist Vectors)
    fun buildSuprematistPlanes(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val primaryAngle = (if (rng.nextBoolean()) 1f else -1f) * (0.48f + rng.nextFloat() * 0.42f)
        val orthoAngle = primaryAngle + PI.toFloat() * 0.5f
        val beamCount = (28 + (intricacy * 16f).roundToInt()).coerceIn(28, 46)
        val arcCount = (5 + (intricacy * 3f).roundToInt()).coerceIn(5, 8)
        val cells = ArrayList<DraftCell>(beamCount + arcCount * 4)

        // Precision orbital Proun arc rings
        for (aIdx in 0 until arcCount) {
            val acx = 0.22f + rng.nextFloat() * 0.56f
            val acy = aspect * (0.20f + rng.nextFloat() * 0.60f)
            val rInner = 0.09f + aIdx * 0.055f + rng.nextFloat() * 0.03f
            val rOuter = rInner + 0.024f + rng.nextFloat() * 0.022f
            val segs = 4
            val baseDeg = rng.nextFloat() * 360f
            for (s in 0 until segs) {
                val startDeg = baseDeg + s * 90f
                val sweepDeg = 52f + rng.nextFloat() * 24f
                val midRad = ((startDeg + sweepDeg * 0.5f) * PI.toFloat() / 180f)
                val mx = acx + ((rInner + rOuter) * 0.5f) * cos(midRad)
                val my = acy + ((rInner + rOuter) * 0.5f) * sin(midRad)
                if (mx < -0.1f || mx > 1.1f || my < -0.1f || my > aspect + 0.1f) continue
                val area = ((sweepDeg / 360f) * PI.toFloat() * (rOuter * rOuter - rInner * rInner)).coerceAtLeast(0.0001f)
                val score = field.evaluate(mx.coerceIn(0f, 1f), my.coerceIn(0f, aspect), rng)
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            ArcSectorPrimitive(
                                center = Vec2(acx, acy),
                                innerRadius = rInner,
                                outerRadius = rOuter,
                                startAngleDeg = startDeg,
                                sweepAngleDeg = sweepDeg,
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = aIdx + s,
                                secondaryColorIndex = aIdx + s + 2,
                                shadeFactor = 1.06f,
                                gradientAngleRad = midRad
                            )
                        )
                    }
                )
            }
        }

        // Stratified Suprematist cantilevered beams, trapezoids & crossbars across full canvas
        val gridRows = 7
        val gridCols = 4
        var idx = 0
        while (idx < beamCount) {
            val gr = idx % gridRows
            val gc = (idx / gridRows) % gridCols
            val cx = ((gc + 0.5f) / gridCols) + (rng.nextFloat() - 0.5f) * 0.22f
            val cy = aspect * (((gr + 0.5f) / gridRows) + (rng.nextFloat() - 0.5f) * 0.14f)

            val usePrimary = idx % 3 != 0
            val ang = (if (usePrimary) primaryAngle else orthoAngle) + (rng.nextFloat() - 0.5f) * 0.08f
            val ux = cos(ang)
            val uy = sin(ang)
            val nx = -uy
            val ny = ux

            val halfLen = if (usePrimary) 0.11f + rng.nextFloat() * 0.18f else 0.07f + rng.nextFloat() * 0.12f
            val halfW0 = 0.016f + rng.nextFloat() * 0.036f
            val taperRatio = if (idx % 4 == 0) 0.58f else 1.0f
            val halfW1 = halfW0 * taperRatio
            val shear = (rng.nextFloat() - 0.5f) * halfW0 * 0.7f

            val p0 = Vec2(cx - ux * halfLen - nx * halfW0, cy - uy * halfLen - ny * halfW0)
            val p1 = Vec2(cx + ux * (halfLen + shear) - nx * halfW1, cy + uy * (halfLen + shear) - ny * halfW1)
            val p2 = Vec2(cx + ux * (halfLen - shear) + nx * halfW1, cy + uy * (halfLen - shear) + ny * halfW1)
            val p3 = Vec2(cx - ux * halfLen + nx * halfW0, cy - uy * halfLen + ny * halfW0)

            val area = (halfLen * 2f * (halfW0 + halfW1)).coerceAtLeast(0.0001f)
            val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
            val shade = 0.80f + (idx % 4) * 0.14f
            val colorSeed = idx

            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    listOf(
                        PolygonPrimitive(
                            vertices = listOf(p0, p1, p2, p3),
                            threshold = threshold,
                            areaWeight = area,
                            colorIndex = colorSeed,
                            secondaryColorIndex = colorSeed + 2,
                            shadeFactor = shade,
                            gradientAngleRad = ang,
                            strokeSeamNorm = 0.0020f
                        )
                    )
                }
            )
            idx++
        }
        return cells
    }

    // 61. Sierpiński Prism (Recursive Triangular Void & Bevel Subdivision)
    fun buildSierpinskiPrism(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val macroCols = 2
        val side = 0.62f
        val triH = side * 0.8660254f
        val rows = ((aspect / triH).roundToInt() + 2).coerceAtLeast(4)
        val maxDepth = if (intricacy >= 0.50f) 3 else 2
        val cells = ArrayList<DraftCell>(rows * macroCols * 36)

        fun subdivideGasket(a: Vec2, b: Vec2, c: Vec2, depth: Int, colorSeed: Int) {
            val cx = (a.x + b.x + c.x) / 3f
            val cy = (a.y + b.y + c.y) / 3f
            if (cx < -0.25f || cx > 1.25f || cy < -0.25f || cy > aspect + 0.25f) return

            if (depth <= 0) {
                val shrink = 0.90f
                val sa = Vec2(cx + (a.x - cx) * shrink, cy + (a.y - cy) * shrink)
                val sb = Vec2(cx + (b.x - cx) * shrink, cy + (b.y - cy) * shrink)
                val sc = Vec2(cx + (c.x - cx) * shrink, cy + (c.y - cy) * shrink)
                val area = (0.5f * abs((sb.x - sa.x) * (sc.y - sa.y) - (sc.x - sa.x) * (sb.y - sa.y)))
                    .coerceAtLeast(0.00008f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = listOf(sa, sb, sc),
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = abs(colorSeed),
                                secondaryColorIndex = abs(colorSeed) + 2,
                                shadeFactor = 0.88f + (abs(colorSeed) % 3) * 0.14f,
                                gradientAngleRad = atan2(sc.y - sa.y, sb.x - sa.x),
                                strokeSeamNorm = 0.0014f
                            )
                        )
                    }
                )
                return
            }

            val ab = Vec2((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
            val bc = Vec2((b.x + c.x) * 0.5f, (b.y + c.y) * 0.5f)
            val ca = Vec2((c.x + a.x) * 0.5f, (c.y + a.y) * 0.5f)

            // Emit subtle recessed central jewel in the Sierpiński void at top levels so mid/low black ratios stay rich
            if (depth == maxDepth) {
                val icx = (ab.x + bc.x + ca.x) / 3f
                val icy = (ab.y + bc.y + ca.y) / 3f
                val inset = 0.54f
                val ia = Vec2(icx + (ab.x - icx) * inset, icy + (ab.y - icy) * inset)
                val ib = Vec2(icx + (bc.x - icx) * inset, icy + (bc.y - icy) * inset)
                val ic = Vec2(icx + (ca.x - icx) * inset, icy + (ca.y - icy) * inset)
                val area = (0.5f * abs((ib.x - ia.x) * (ic.y - ia.y) - (ic.x - ia.x) * (ib.y - ia.y)))
                    .coerceAtLeast(0.00008f)
                val score = field.evaluate(icx.coerceIn(0f, 1f), icy.coerceIn(0f, aspect), rng) * 0.78f
                cells.add(
                    DraftCell(score = score, area = area) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = listOf(ia, ib, ic),
                                threshold = threshold,
                                areaWeight = area,
                                colorIndex = abs(colorSeed) + 3,
                                secondaryColorIndex = abs(colorSeed) + 4,
                                shadeFactor = 1.20f,
                                gradientAngleRad = -0.78f,
                                strokeSeamNorm = 0.0014f
                            )
                        )
                    }
                )
            }

            subdivideGasket(a, ab, ca, depth - 1, colorSeed + 1)
            subdivideGasket(ab, b, bc, depth - 1, colorSeed + 2)
            subdivideGasket(ca, bc, c, depth - 1, colorSeed + 3)
        }

        for (r in -1..rows) {
            val y0 = r * triH
            val y1 = (r + 1) * triH
            val shift = if (r.mod(2) == 1) side * 0.5f else 0f
            for (c in -1..macroCols + 1) {
                val x0 = c * side + shift - 0.12f
                val pA = Vec2(x0, y1)
                val pB = Vec2(x0 + side, y1)
                val pC = Vec2(x0 + side * 0.5f, y0)
                val pD = Vec2(x0 + side * 1.5f, y0)
                subdivideGasket(pA, pB, pC, maxDepth, r * 3 + c)
                subdivideGasket(pC, pB, pD, maxDepth, r * 3 + c + 2)
            }
        }
        return cells
    }

    // 62. Archimedean Snub (Exact Chiral 3^2.4.3.4 Square-Triangle Tiling)
    fun buildArchimedeanSnub(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val domainCols = (3 + (intricacy * 2f).roundToInt()).coerceIn(3, 5)
        val cellScale = 1f / domainCols.toFloat()
        val domainRows = ((aspect / cellScale).roundToInt() + 2).coerceAtLeast(6)
        val a = (sqrt(3f) - 1f) * 0.25f // 0.1830127f
        val b = (3f - sqrt(3f)) * 0.25f // 0.3169873f
        val chiralFlip = if (rng.nextBoolean()) 1f else -1f

        fun mapPt(ux: Float, uy: Float): Vec2 {
            val fx = if (chiralFlip > 0f) ux else (1f - ux)
            return Vec2(fx * cellScale + (if (chiralFlip < 0f) (1f - cellScale) else 0f), uy * cellScale)
        }

        fun vA(m: Int, n: Int) = mapPt(m + a, n + b)
        fun vB(m: Int, n: Int) = mapPt(m + 1f - b, n + a)
        fun vC(m: Int, n: Int) = mapPt(m + 1f - a, n + 1f - b)
        fun vD(m: Int, n: Int) = mapPt(m + b, n + 1f - a)

        val cells = ArrayList<DraftCell>((domainRows + 2) * (domainCols + 2) * 6)

        fun emitSnubTile(rawVerts: List<Vec2>, colorIdx: Int, shade: Float) {
            val n = rawVerts.size
            var cx = 0f
            var cy = 0f
            for (v in rawVerts) {
                cx += v.x
                cy += v.y
            }
            cx /= n
            cy /= n
            if (cx < -0.10f || cx > 1.10f || cy < -0.10f || cy > aspect + 0.10f) return

            val shrink = 0.90f
            val verts = rawVerts.map { v ->
                Vec2(cx + (v.x - cx) * shrink, cy + (v.y - cy) * shrink)
            }
            val area = if (n == 4) {
                (cellScale * cellScale * 0.268f * shrink * shrink).coerceAtLeast(0.00008f)
            } else {
                (cellScale * cellScale * 0.116f * shrink * shrink).coerceAtLeast(0.00008f)
            }
            val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    listOf(
                        PolygonPrimitive(
                            vertices = verts,
                            threshold = threshold,
                            areaWeight = area,
                            colorIndex = abs(colorIdx),
                            secondaryColorIndex = abs(colorIdx) + 2,
                            shadeFactor = shade,
                            gradientAngleRad = atan2(verts[1].y - verts[0].y, verts[1].x - verts[0].x),
                            strokeSeamNorm = 0.0014f
                        )
                    )
                }
            )
        }

        val mRange = -2..(domainCols + 2)
        val nRange = -1..(domainRows + 1)
        for (n in nRange) {
            for (m in mRange) {
                val baseColor = abs(m * 3 + n * 5)
                // Square 1 (+15° chiral tilt)
                emitSnubTile(listOf(vA(m, n), vB(m, n), vC(m, n), vD(m, n)), baseColor, 1.16f)
                // Square 2 (-15° chiral tilt)
                emitSnubTile(listOf(vC(m, n), vD(m + 1, n), vA(m + 1, n + 1), vB(m, n + 1)), baseColor + 1, 0.98f)
                // 4 Equilateral triangles completing the 3^2.4.3.4 fundamental domain
                emitSnubTile(listOf(vB(m, n), vA(m + 1, n), vC(m, n)), baseColor + 2, 0.84f)
                emitSnubTile(listOf(vA(m + 1, n), vD(m + 1, n), vC(m, n)), baseColor + 3, 0.78f)
                emitSnubTile(listOf(vD(m, n), vC(m, n), vB(m, n + 1)), baseColor + 2, 1.06f)
                emitSnubTile(listOf(vD(m, n), vB(m, n + 1), vA(m, n + 1)), baseColor + 4, 0.90f)
            }
        }
        return cells
    }

    // 63. Ammann Quasicrystal (Exact 8-Fold Silver-Ratio Square & 45° Rhombus Deflation)
    fun buildAmmannQuasicrystal(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        data class Rhomb(val p0: Vec2, val p1: Vec2, val p2: Vec2, val p3: Vec2)
        data class HalfSq(val rightV: Vec2, val leg1: Vec2, val leg2: Vec2)

        val center = Vec2(0.5f, aspect * 0.5f)
        val wheelRadius = hypot(0.66f, aspect * 0.64f) * 1.15f
        val rot0 = rng.nextFloat() * (PI.toFloat() * 0.25f)
        val sigma = sqrt(2f) - 1f // 1 / (1 + sqrt(2))

        // Seed 8-fold octagonal star wheel of 8 central 45° rhombi + 8 outer half-squares
        var rhombi = ArrayList<Rhomb>(16)
        var halfSquares = ArrayList<HalfSq>(16)

        val step45 = (PI / 4.0).toFloat()
        for (i in 0 until 8) {
            val a0 = rot0 + i * step45
            val a1 = rot0 + (i + 1) * step45
            val e0 = Vec2(wheelRadius * cos(a0), wheelRadius * sin(a0))
            val e1 = Vec2(wheelRadius * cos(a1), wheelRadius * sin(a1))
            val p1 = Vec2(center.x + e0.x, center.y + e0.y)
            val p3 = Vec2(center.x + e1.x, center.y + e1.y)
            val p2 = Vec2(center.x + e0.x + e1.x, center.y + e0.y + e1.y)
            rhombi.add(Rhomb(center, p1, p2, p3))
        }

        val levels = if (intricacy >= 0.48f) 3 else 2
        repeat(levels) {
            val nextRhombi = ArrayList<Rhomb>(rhombi.size * 3 + halfSquares.size * 2)
            val nextHalfSq = ArrayList<HalfSq>(rhombi.size * 4 + halfSquares.size * 3)

            for (rh in rhombi) {
                val e1x = rh.p1.x - rh.p0.x
                val e1y = rh.p1.y - rh.p0.y
                val e2x = rh.p3.x - rh.p0.x
                val e2y = rh.p3.y - rh.p0.y

                val a1 = Vec2(rh.p0.x + sigma * e1x, rh.p0.y + sigma * e1y)
                val a3 = Vec2(rh.p0.x + sigma * e2x, rh.p0.y + sigma * e2y)
                val m0 = Vec2(rh.p0.x + sigma * (e1x + e2x), rh.p0.y + sigma * (e1y + e2y))

                val c1 = Vec2(rh.p2.x - sigma * e1x, rh.p2.y - sigma * e1y)
                val c3 = Vec2(rh.p2.x - sigma * e2x, rh.p2.y - sigma * e2y)
                val m2 = Vec2(rh.p2.x - sigma * (e1x + e2x), rh.p2.y - sigma * (e1y + e2y))

                nextRhombi.add(Rhomb(rh.p0, a1, m0, a3))
                nextRhombi.add(Rhomb(rh.p2, c1, m2, c3))
                nextRhombi.add(Rhomb(rh.p1, m2, rh.p3, m0))

                nextHalfSq.add(HalfSq(m0, a1, rh.p1))
                nextHalfSq.add(HalfSq(m0, a3, rh.p3))
                nextHalfSq.add(HalfSq(m2, c1, rh.p3))
                nextHalfSq.add(HalfSq(m2, c3, rh.p1))
            }

            val invSqrt2 = 0.70710677f
            val hFrac = sigma * invSqrt2
            val mCoeff = 1f - invSqrt2

            for (hs in halfSquares) {
                val ux = hs.leg1.x - hs.rightV.x
                val uy = hs.leg1.y - hs.rightV.y
                val vx = hs.leg2.x - hs.rightV.x
                val vy = hs.leg2.y - hs.rightV.y

                val lA = Vec2(hs.leg1.x - sigma * ux, hs.leg1.y - sigma * uy)
                val lC = Vec2(hs.leg2.x - sigma * vx, hs.leg2.y - sigma * vy)
                val hA = Vec2(hs.leg1.x + hFrac * (hs.leg2.x - hs.leg1.x), hs.leg1.y + hFrac * (hs.leg2.y - hs.leg1.y))
                val hC = Vec2(hs.leg2.x + hFrac * (hs.leg1.x - hs.leg2.x), hs.leg2.y + hFrac * (hs.leg1.y - hs.leg2.y))
                val mPt = Vec2(hs.rightV.x + mCoeff * (ux + vx), hs.rightV.y + mCoeff * (uy + vy))

                nextRhombi.add(Rhomb(hs.leg1, lA, mPt, hA))
                nextRhombi.add(Rhomb(hs.leg2, lC, mPt, hC))
                nextHalfSq.add(HalfSq(mPt, hs.rightV, lA))
                nextHalfSq.add(HalfSq(mPt, hs.rightV, lC))
                nextHalfSq.add(HalfSq(mPt, hA, hC))
            }

            rhombi = nextRhombi
            halfSquares = nextHalfSq
        }

        // Reunite matching HalfSq pairs along shared hypotenuse midpoints into full Squares
        val cells = ArrayList<DraftCell>(rhombi.size + halfSquares.size)
        val shrink = 0.90f

        fun emitQuasiPoly(rawVerts: List<Vec2>, colorSeed: Int, shade: Float) {
            var cx = 0f
            var cy = 0f
            for (v in rawVerts) {
                cx += v.x
                cy += v.y
            }
            cx /= rawVerts.size
            cy /= rawVerts.size
            if (cx < -0.10f || cx > 1.10f || cy < -0.10f || cy > aspect + 0.10f) return

            val verts = rawVerts.map { v -> Vec2(cx + (v.x - cx) * shrink, cy + (v.y - cy) * shrink) }
            val area = if (verts.size == 4) {
                (0.5f * abs((verts[2].x - verts[0].x) * (verts[3].y - verts[1].y) -
                    (verts[3].x - verts[1].x) * (verts[2].y - verts[0].y))).coerceAtLeast(0.00008f)
            } else {
                (0.5f * abs((verts[1].x - verts[0].x) * (verts[2].y - verts[0].y) -
                    (verts[2].x - verts[0].x) * (verts[1].y - verts[0].y))).coerceAtLeast(0.00008f)
            }
            val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
            cells.add(
                DraftCell(score = score, area = area) { threshold ->
                    listOf(
                        PolygonPrimitive(
                            vertices = verts,
                            threshold = threshold,
                            areaWeight = area,
                            colorIndex = abs(colorSeed),
                            secondaryColorIndex = abs(colorSeed) + 2,
                            shadeFactor = shade,
                            gradientAngleRad = atan2(verts[1].y - verts[0].y, verts[1].x - verts[0].x),
                            strokeSeamNorm = 0.0014f
                        )
                    )
                }
            )
        }

        for ((idx, rh) in rhombi.withIndex()) {
            val angBucket = ((atan2(rh.p2.y - rh.p0.y, rh.p2.x - rh.p0.x) + PI) / step45).roundToInt()
            emitQuasiPoly(listOf(rh.p0, rh.p1, rh.p2, rh.p3), angBucket, 1.14f - (idx % 2) * 0.12f)
        }

        val pairedByHypMid = LinkedHashMap<Long, HalfSq>()
        for ((idx, hs) in halfSquares.withIndex()) {
            val mx = ((hs.leg1.x + hs.leg2.x) * 0.5f * 2000f).roundToInt().toLong()
            val my = ((hs.leg1.y + hs.leg2.y) * 0.5f * 2000f).roundToInt().toLong()
            val key = (mx shl 32) xor (my and 0xFFFFFFFFL)
            val mate = pairedByHypMid.remove(key)
            if (mate != null) {
                emitQuasiPoly(listOf(hs.rightV, hs.leg1, mate.rightV, hs.leg2), idx % 5 + 2, 0.88f)
            } else {
                pairedByHypMid[key] = hs
            }
        }
        for ((_, orphan) in pairedByHypMid) {
            emitQuasiPoly(listOf(orphan.rightV, orphan.leg1, orphan.leg2), 3, 0.84f)
        }

        return cells
    }

    // 64. Pythagoras Fractal (Recursive Right-Triangle Branching Square Canopy)
    fun buildPythagorasCanopy(
        rng: Random,
        field: SculptField,
        intricacy: Float,
        aspect: Float
    ): List<DraftCell> {
        val maxDepth = if (intricacy >= 0.50f) 6 else 5
        val alpha = 0.58f + rng.nextFloat() * 0.20f // ~33° - 45° golden branching angle
        val cosA = cos(alpha)
        val sinA = sin(alpha)
        val cells = ArrayList<DraftCell>(600)

        fun growBranch(p0: Vec2, p1: Vec2, depth: Int, treeIdx: Int) {
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y
            val sideLen = hypot(dx, dy)
            if (sideLen < 0.014f) return

            // Outward normal (left of p0 -> p1 in screen space: (dy, -dx))
            val p2 = Vec2(p1.x + dy, p1.y - dx)
            val p3 = Vec2(p0.x + dy, p0.y - dx)

            val cx = (p0.x + p1.x + p2.x + p3.x) * 0.25f
            val cy = (p0.y + p1.y + p2.y + p3.y) * 0.25f

            if (cx in -0.22f..1.22f && cy in -0.22f..(aspect + 0.22f)) {
                val shrink = 0.92f
                val sqVerts = listOf(p0, p1, p2, p3).map { v ->
                    Vec2(cx + (v.x - cx) * shrink, cy + (v.y - cy) * shrink)
                }
                val sqArea = (sideLen * sideLen * shrink * shrink).coerceAtLeast(0.00008f)
                val score = field.evaluate(cx.coerceIn(0f, 1f), cy.coerceIn(0f, aspect), rng)
                val cSeed = treeIdx + maxDepth - depth

                cells.add(
                    DraftCell(score = score, area = sqArea) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = sqVerts,
                                threshold = threshold,
                                areaWeight = sqArea,
                                colorIndex = cSeed,
                                secondaryColorIndex = cSeed + 2,
                                shadeFactor = (0.80f + 0.08f * (maxDepth - depth)).coerceIn(0.76f, 1.26f),
                                gradientAngleRad = atan2(dy, dx),
                                strokeSeamNorm = 0.0015f
                            )
                        )
                    }
                )
            }

            if (depth <= 0) return

            // Right-triangle gable apex on top edge p3 -> p2 (outward from p3->p2 is (gy, -gx))
            val gx = p2.x - p3.x
            val gy = p2.y - p3.y
            val apexOut = Vec2(
                p3.x + cosA * cosA * gx + cosA * sinA * gy,
                p3.y + cosA * cosA * gy - cosA * sinA * gx
            )

            // Emit gable right triangle
            val tcx = (p3.x + p2.x + apexOut.x) / 3f
            val tcy = (p3.y + p2.y + apexOut.y) / 3f
            if (tcx in -0.18f..1.18f && tcy in -0.18f..(aspect + 0.18f)) {
                val triArea = (0.5f * sideLen * cosA * sideLen * sinA).coerceAtLeast(0.00006f)
                val score = field.evaluate(tcx.coerceIn(0f, 1f), tcy.coerceIn(0f, aspect), rng)
                cells.add(
                    DraftCell(score = score, area = triArea) { threshold ->
                        listOf(
                            PolygonPrimitive(
                                vertices = listOf(p3, p2, apexOut),
                                threshold = threshold,
                                areaWeight = triArea,
                                colorIndex = treeIdx + depth + 1,
                                secondaryColorIndex = treeIdx + depth + 3,
                                shadeFactor = 1.16f,
                                gradientAngleRad = atan2(apexOut.y - p3.y, apexOut.x - p3.x),
                                strokeSeamNorm = 0.0014f
                            )
                        )
                    }
                )
            }

            growBranch(p3, apexOut, depth - 1, treeIdx + 1)
            growBranch(apexOut, p2, depth - 1, treeIdx + 2)
        }

        // Suppress unused local variable warning by referencing only apexOut above; remove unused apex if needed
        // Dual counter-growing Pythagoras canopies from bottom & top + mid flanks so the full viewport blooms
        val trunkSize = 0.21f
        // Bottom trunk growing upward (p0 -> p1 left-to-right so outward (dy, -dx) points UP, -Y)
        growBranch(
            Vec2(0.5f - trunkSize * 0.5f, aspect * 0.94f),
            Vec2(0.5f + trunkSize * 0.5f, aspect * 0.94f),
            maxDepth,
            0
        )
        // Top trunk growing downward (p0 -> p1 right-to-left so outward (dy, -dx) points DOWN, +Y)
        growBranch(
            Vec2(0.5f + trunkSize * 0.5f, aspect * 0.06f),
            Vec2(0.5f - trunkSize * 0.5f, aspect * 0.06f),
            maxDepth,
            3
        )
        return cells
    }
}

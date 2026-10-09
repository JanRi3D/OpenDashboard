package me.ri3d.dashboard

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Minimal SVG path-data parser (M L H V C S Q T A Z, absolute and relative) for the outlined
 * 24 x 24 icons. Arcs become cubic Béziers, so any sink with move/line/cubic/close can draw them;
 * android.graphics.Path exists on every API level, which is the API 16/17 rendering path.
 */
object SvgPath {
    interface Sink {
        fun move(x: Float, y: Float)
        fun line(x: Float, y: Float)
        fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float)
        fun close()
    }

    fun parse(d: String, out: Sink) {
        var i = 0
        var cmd = ' '
        var x = 0f; var y = 0f        // current point
        var sx = 0f; var sy = 0f      // subpath start
        var cx = 0f; var cy = 0f      // last control point (S/T reflection)
        var lastCubic = false; var lastQuad = false

        fun skip() { while (i < d.length && (d[i] == ' ' || d[i] == ',' || d[i] == '\n' || d[i] == '\t')) i++ }
        fun num(): Float {
            skip()
            val start = i
            if (i < d.length && (d[i] == '-' || d[i] == '+')) i++
            var dot = false
            while (i < d.length) {
                val c = d[i]
                if (c in '0'..'9') i++
                else if (c == '.' && !dot) { dot = true; i++ }
                else if ((c == 'e' || c == 'E') && i + 1 < d.length) {
                    i++
                    if (d[i] == '-' || d[i] == '+') i++
                } else break
            }
            require(i > start) { "number expected at $start in \"$d\"" }
            return d.substring(start, i).toFloat()
        }
        fun flag(): Boolean { skip(); return d[i++] == '1' }

        while (true) {
            skip()
            if (i >= d.length) break
            val c = d[i]
            if (c.isLetter()) { cmd = c; i++ } else require(cmd != ' ') { "command expected in \"$d\"" }
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0f
            val oy = if (rel) y else 0f
            var cubicNow = false; var quadNow = false
            when (cmd.uppercaseChar()) {
                'M' -> {
                    x = ox + num(); y = oy + num(); sx = x; sy = y
                    out.move(x, y)
                    cmd = if (rel) 'l' else 'L' // further pairs are implicit line-tos
                }
                'L' -> { x = ox + num(); y = oy + num(); out.line(x, y) }
                'H' -> { x = ox + num(); out.line(x, y) }
                'V' -> { y = oy + num(); out.line(x, y) }
                'C' -> {
                    val x1 = ox + num(); val y1 = oy + num()
                    cx = ox + num(); cy = oy + num()
                    x = ox + num(); y = oy + num()
                    out.cubic(x1, y1, cx, cy, x, y); cubicNow = true
                }
                'S' -> {
                    val x1 = if (lastCubic) 2 * x - cx else x
                    val y1 = if (lastCubic) 2 * y - cy else y
                    cx = ox + num(); cy = oy + num()
                    x = ox + num(); y = oy + num()
                    out.cubic(x1, y1, cx, cy, x, y); cubicNow = true
                }
                'Q', 'T' -> {
                    val qx: Float; val qy: Float
                    if (cmd.uppercaseChar() == 'Q') { qx = ox + num(); qy = oy + num() }
                    else { qx = if (lastQuad) 2 * x - cx else x; qy = if (lastQuad) 2 * y - cy else y }
                    val ex = ox + num(); val ey = oy + num()
                    out.cubic(x + 2f / 3 * (qx - x), y + 2f / 3 * (qy - y), ex + 2f / 3 * (qx - ex), ey + 2f / 3 * (qy - ey), ex, ey)
                    cx = qx; cy = qy; x = ex; y = ey; quadNow = true
                }
                'A' -> {
                    val rx = num(); val ry = num(); val rot = num()
                    val large = flag(); val sweep = flag()
                    val ex = ox + num(); val ey = oy + num()
                    arc(out, x, y, rx, ry, rot, large, sweep, ex, ey)
                    x = ex; y = ey
                }
                'Z' -> { out.close(); x = sx; y = sy }
                else -> throw IllegalArgumentException("unsupported command $cmd in \"$d\"")
            }
            lastCubic = cubicNow; lastQuad = quadNow
        }
    }

    /** SVG 1.1 F.6.5 endpoint-to-centre conversion, then at most 90° per cubic segment. */
    private fun arc(out: Sink, x0: Float, y0: Float, rxIn: Float, ryIn: Float, deg: Float,
                    large: Boolean, sweep: Boolean, x: Float, y: Float) {
        if (x0 == x && y0 == y) return
        var rx = abs(rxIn.toDouble()); var ry = abs(ryIn.toDouble())
        if (rx == 0.0 || ry == 0.0) { out.line(x, y); return }
        val phi = Math.toRadians(deg.toDouble())
        val cp = cos(phi); val sp = sin(phi)
        val dx2 = (x0 - x) / 2.0; val dy2 = (y0 - y) / 2.0
        val x1p = cp * dx2 + sp * dy2
        val y1p = -sp * dx2 + cp * dy2
        val lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
        if (lambda > 1) { rx *= sqrt(lambda); ry *= sqrt(lambda) }
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val coef = (if (large == sweep) -1 else 1) * sqrt(maxOf(0.0, num / den))
        val cxp = coef * rx * y1p / ry
        val cyp = -coef * ry * x1p / rx
        val ccx = cp * cxp - sp * cyp + (x0 + x) / 2.0
        val ccy = sp * cxp + cp * cyp + (y0 + y) / 2.0
        val ux = (x1p - cxp) / rx; val uy = (y1p - cyp) / ry
        val vx = (-x1p - cxp) / rx; val vy = (-y1p - cyp) / ry
        val theta = atan2(uy, ux)
        var delta = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        if (!sweep && delta > 0) delta -= 2 * Math.PI
        if (sweep && delta < 0) delta += 2 * Math.PI
        val n = maxOf(1, ceil(abs(delta) / (Math.PI / 2) - 1e-9).toInt())
        val step = delta / n
        val t = 4.0 / 3.0 * tan(step / 4)
        var a = theta
        fun px(ex: Double, ey: Double) = (ccx + rx * ex * cp - ry * ey * sp).toFloat()
        fun py(ex: Double, ey: Double) = (ccy + rx * ex * sp + ry * ey * cp).toFloat()
        for (k in 0 until n) {
            val c1 = cos(a); val s1 = sin(a)
            val b = a + step
            val c2 = cos(b); val s2 = sin(b)
            val e1x = c1 - t * s1; val e1y = s1 + t * c1
            val e2x = c2 + t * s2; val e2y = s2 - t * c2
            val last = k == n - 1
            out.cubic(px(e1x, e1y), py(e1x, e1y), px(e2x, e2y), py(e2x, e2y),
                if (last) x else px(c2, s2), if (last) y else py(c2, s2))
            a = b
        }
    }
}

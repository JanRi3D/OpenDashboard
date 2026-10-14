package me.ri3d.dashboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import me.ri3d.dashboard.VehicleState.Door
import me.ri3d.dashboard.VehicleState.Reading
import me.ri3d.dashboard.VehicleState.Status
import java.util.Locale

/**
 * The one CanService vehicle widget, in four presentations: TALL (layout A), WIDE (layout B),
 * CARD (layout C: speed gauge and gear on top) and CLOCK (the clock screen's compact row). What
 * each place shows is the driver's choice ([WidgetSlots], Settings › Vehicle widget values). Built
 * once per render, then [bind] updates it in place on every vehicle change.
 */
class VehicleWidget(private val a: MainActivity, private val kind: Int) {
    private val unit = Ui.s // layout C is built at its own scale; updates must use the same one
    private val slots = Hub.prefs.widgetSlots
    private val chip = Chip(a, 32f, 12f)
    private val via = a.caps(a.getString(R.string.via_canservice), 11f, C.FAINT)

    private fun label(size: Float = 13f) = a.caps("", size)
    private fun value(size: Float, track: Float = -0.02f) = a.title("", size, track, lineHeight = 1.1f)

    private val mainLabel = label()
    private val main = BigText(a, if (kind == TALL) 76f else if (kind == WIDE) 64f else 36f,
        if (kind == CLOCK) -0.03f else -0.05f, if (kind == CLOCK) 1.1f else 0.95f, C.TEXT)
    private val barLabel = label()
    private val barValue = if (kind == CLOCK) value(36f, -0.03f) else value(if (kind == WIDE) 28f else 24f)
    private val bar = Bar(a)
    private val belowLabel: TextView = if (kind == CLOCK) label() else a.text("", 17f, color = C.MUTED)
    private val belowValue: View = if (kind == CLOCK) value(36f, -0.03f) else a.text("", 17f, color = C.TEXT2)
    private val tileCount = when (kind) { TALL -> 4; WIDE, CARD -> 2; else -> 0 }
    private val tileLabels = List(tileCount) { label(if (kind == WIDE) 13f else 11f) }
    private val tileValues = List(tileCount) { value(if (kind == WIDE) 30f else 24f) }
    private val tileCells = ArrayList<View>()
    private val gauge = if (kind == CARD) Gauge(a) else null
    private val gearNote = a.caps("", 13f, C.FAINT)
    private val gearCells = if (kind == CARD) List(4) { a.text(GEARS[it].toString(), 20f, F.medium).apply { gravity = Gravity.CENTER } } else emptyList()

    private var data: View? = null
    private var noData: View? = null
    private val noDataText = a.para("", if (kind == WIDE) 17f else 18f, C.TEXT2, if (kind == WIDE) 1.3f else 1.4f)
    private val openButton = a.pill(a.getString(R.string.open_canservice), h = if (kind == WIDE) 48f else 56f,
        size = if (kind == WIDE) 17f else 18f, fill = C.TEXT, fg = C.BG, stroke = 0, radius = 14f) { a.launchRole(Role.VEHICLE) }

    val view: View = when (kind) {
        TALL -> tall()
        WIDE -> wide()
        CARD -> card()
        else -> clock()
    }

    init { a.onVehicle += { bind() } }

    private fun tall(): View = a.col().apply {
        background = shape(C.CARD, C.LINE, 20f)
        pad(24f)
        contentDescription = a.getString(R.string.vehicle_a11y)
        val head = add(a.row(), MATCH, 32.u)
        head.add(a.caps(a.getString(R.string.vehicle), 13f)); head.spring(); head.add(chip, WRAP, 32.u)
        spring()
        val o = add(a.col(6f))
        o.add(mainLabel); o.add(main)
        spring()
        add(barBlock(), MATCH, WRAP)
        spring()
        val grid = a.col(12f)
        for (r in 0 until 2) {
            val line = grid.add(a.row(12f), MATCH, WRAP)
            for (c in 0 until 2) {
                val i = r * 2 + c
                tileCells += line.add(a.col(6f).apply {
                    pad(16f, 14f)
                    add(tileLabels[i]); add(tileValues[i], MATCH, WRAP)
                }, 0, WRAP, 1f)
            }
        }
        data = add(grid, MATCH, WRAP)
        noData = add(a.col(14f).apply {
            background = shape(0, C.LINE2, 14f, dashed = true); pad(18f)
            add(noDataText, MATCH, WRAP); add(openButton, MATCH, 56.u)
        }, MATCH, WRAP)
        spring()
        add(via)
    }

    private fun barBlock(): View = a.col(10f).apply {
        val top = add(a.row().apply { gravity = Gravity.BOTTOM }, MATCH, WRAP)
        top.add(barLabel); top.spring(); top.add(barValue)
        add(bar, MATCH, 8.u)
        val bottom = add(a.row(), MATCH, WRAP)
        bottom.add(belowLabel); bottom.spring(); bottom.add(belowValue)
    }

    private fun wide(): View = a.row(28f).apply {
        gravity = Gravity.NO_GRAVITY
        background = shape(C.CARD, C.LINE, 20f)
        pad(28f, 24f)
        contentDescription = a.getString(R.string.vehicle_a11y)
        val c1 = add(a.col(), 190.u, MATCH)
        c1.add(a.caps(a.getString(R.string.vehicle), 13f)); c1.spring(); c1.add(chip, WRAP, 32.u); c1.spring(); c1.add(via)
        add(a.divider(), maxOf(1, 1.u), MATCH)
        val c2 = add(a.col(), 170.u, MATCH)
        c2.add(mainLabel); c2.spring(); c2.add(main)
        add(a.divider(), maxOf(1, 1.u), MATCH)
        val c3 = add(a.col(), 0, MATCH, 1f)
        val top = c3.add(a.row().apply { gravity = Gravity.BOTTOM }, MATCH, WRAP)
        top.add(barLabel); top.spring(); top.add(barValue)
        c3.spring(); c3.add(bar, MATCH, 8.u); c3.spring()
        val bottom = c3.add(a.row(), MATCH, WRAP)
        bottom.add(belowLabel); bottom.spring(); bottom.add(belowValue)
        add(a.divider(), maxOf(1, 1.u), MATCH)
        data = add(a.row(28f).apply {
            gravity = Gravity.NO_GRAVITY
            for (i in 0 until 2) tileCells += add(a.col().apply {
                add(tileLabels[i]); spring(); add(tileValues[i], MATCH, WRAP)
            }, 0, MATCH, 1f)
        }, 288.u, MATCH)
        noData = add(a.col().apply {
            add(noDataText, MATCH, WRAP); spring(); add(openButton, MATCH, 48.u)
        }, 288.u, MATCH)
    }

    private fun card(): View = a.col().apply {
        background = shape(C.CARD, C.LINE, 20f)
        pad(24f)
        contentDescription = a.getString(R.string.vehicle_a11y)
        val head = add(a.row(10f), MATCH, 44.u)
        head.add(a.caps(a.getString(R.string.vehicle), 13f), 0, WRAP, 1f)
        head.add(chip, WRAP, 32.u)
        head.add(a.iconButton(Icons.SLIDERS, a.getString(R.string.widget_values), 44f, 20f) { a.push(Screen.WIDGET) }, 44.u, 44.u)
        spring()
        add(gauge!!, 220.u, 184.u, gravity = Gravity.CENTER_HORIZONTAL)
        spring()
        data = add(a.col(32f).apply {
            add(barBlock(), MATCH, WRAP)
            add(a.col(10f).apply {
                val top = add(a.row(), MATCH, WRAP)
                top.add(a.caps(a.getString(R.string.gear), 13f)); top.spring(); top.add(gearNote)
                val seg = add(a.row(4f).apply { background = shape(0, C.LINE2, 26f); pad(4f) }, MATCH, 52.u)
                for (c in gearCells) seg.add(c, 0, MATCH, 1f)
            }, MATCH, WRAP)
            val cells = add(a.row(12f), MATCH, WRAP)
            for (i in 0 until tileCount) tileCells += cells.add(a.col(6f).apply {
                pad(16f, 14f)
                add(tileLabels[i]); add(tileValues[i], MATCH, WRAP)
            }, 0, WRAP, 1f)
        }, MATCH, WRAP)
        noData = add(a.col(14f).apply {
            background = shape(0, C.LINE2, 14f, dashed = true); pad(18f)
            add(noDataText, MATCH, WRAP); add(openButton, MATCH, 56.u)
        }, MATCH, WRAP)
        spring()
        add(via)
    }

    private fun clock(): View = a.row(44f).apply {
        gravity = Gravity.BOTTOM
        contentDescription = a.getString(R.string.vehicle_a11y)
        fun cell(l: View, v: View) = a.col(8f).apply { add(l); add(v) }
        add(cell(mainLabel, main)); add(cell(barLabel, barValue)); add(cell(belowLabel, belowValue))
        spring()
        add(via)
    }

    fun bind() = Ui.at(unit) { bindNow() }

    private fun bindNow() {
        val v = Hub.vehicle
        val now = SystemClock.elapsedRealtime()
        val st = v.status(now)
        when (st) {
            Status.LIVE -> chip.set(a.getString(R.string.state_live), C.GREEN)
            Status.DOOR_OPEN -> chip.set(a.getString(R.string.state_door), C.RED, C.RED, C.RED)
            Status.STALE -> chip.set(a.getString(R.string.state_stale), C.accent)
            Status.NO_DATA -> chip.set(a.getString(R.string.state_nodata), C.FAINT)
        }
        val open = v.openDoors()
        val doorsFresh = v.doorsFresh(now)
        val doorWarn = open.isNotEmpty() && doorsFresh

        /** Text and colour of a metric; door status is red while a door is reported open. */
        fun shown(m: Metric): Pair<String, Int> {
            val r = m.reading ?: return when {
                open.isNotEmpty() -> open.joinToString(", ") { doorName(it) }
                v.allClosed() -> a.getString(R.string.doors_closed)
                else -> "—"
            } to (if (doorWarn) C.RED else if (doorsFresh) C.TEXT else C.FAINT)
            val x = v.value(r)
            if (m == Metric.SET_TEMP) return a.setTempText(short = true) to (if (setTempFresh()) C.TEXT else C.FAINT)
            return a.getString(m.unit, if (x == null) "—" else num(x, m.decimals)) to (if (v.fresh(r, now)) C.TEXT else C.FAINT)
        }
        fun put(view: View, m: Metric, light: Int = C.TEXT) {
            val (s, c) = shown(m)
            val color = if (c == C.TEXT) light else c
            if (view is BigText) { view.text = s; view.color = color } else if (view is TextView) { view.text = s; view.setTextColor(color) }
        }

        val m0 = slots[WidgetSlots.MAIN]
        mainLabel.text = a.getString(m0.label)
        put(main, m0)
        val mb = slots[WidgetSlots.BAR]
        barLabel.text = a.getString(mb.label)
        put(barValue, mb)
        val pct = mb.reading?.let { v.value(it) }
        bar.fraction = ((pct ?: 0.0) / 100).toFloat()
        bar.color = if (mb.reading != null && v.fresh(mb.reading, now)) C.accent else C.FAINT
        val mw = slots[WidgetSlots.BELOW]
        belowLabel.text = a.getString(mw.label)
        put(belowValue, mw, if (kind == CLOCK) C.TEXT else C.TEXT2)
        for (i in 0 until tileCount) {
            val m = slots[WidgetSlots.TILE1 + i]
            tileLabels[i].text = a.getString(m.label)
            put(tileValues[i], m)
            if (kind == TALL || kind == CARD) tileCells[i].background = shape(C.RAISED, if (m == Metric.DOORS && doorWarn) C.RED else C.RAISED, 14f)
        }
        if (gauge != null) {
            gauge.set(v.value(Reading.SPEED), v.fresh(Reading.SPEED, now))
            val raw = v.value(Reading.GEAR)
            val g = gearOf(raw)
            gearNote.text = when {
                raw == null -> a.getString(R.string.gear_unknown)
                g == null -> a.getString(R.string.gear_not_mapped)
                else -> ""
            }
            val fresh = v.fresh(Reading.GEAR, now)
            for ((i, cell) in gearCells.withIndex()) {
                val on = g == GEARS[i]
                cell.setTextColor(if (on) C.BG else if (g == null) C.LINE2 else C.MUTED)
                cell.background = if (!on) null else shape(if (!fresh) C.FAINT else if (g == 'R') C.RED else C.TEXT, radius = 22f)
            }
        }

        val has = st != Status.NO_DATA
        data?.visibility = if (has) View.VISIBLE else View.GONE
        noData?.visibility = if (has) View.GONE else View.VISIBLE
        noDataText.text = a.getString(when (CanService.link) {
            CanService.Link.NOT_INSTALLED -> R.string.can_missing
            CanService.Link.REFUSED -> R.string.can_refused
            else -> R.string.can_no_data
        })
        val target = Hub.apps.target(Role.VEHICLE)
        openButton.isEnabled = target != null
        openButton.alpha = if (target != null) 1f else 0.4f
        via.text = a.getString(if (v.source != null && v.source != CAN) R.string.via_demo else R.string.via_canservice)
    }

    private fun doorName(d: Door) = a.getString(when (d) {
        Door.FRONT_LEFT -> R.string.door_fl
        Door.FRONT_RIGHT -> R.string.door_fr
        Door.REAR_LEFT -> R.string.door_rl
        Door.REAR_RIGHT -> R.string.door_rr
        Door.TRUNK -> R.string.door_trunk
        Door.HOOD -> R.string.door_hood
    })

    private fun num(x: Double, decimals: Boolean) =
        if (decimals) String.format(Locale.getDefault(), "%.1f", x) else Math.round(x).toString()

    companion object {
        const val TALL = 0; const val WIDE = 1; const val CLOCK = 2; const val CARD = 3
        const val CAN = "CanService"
        private const val GEARS = "PRND"
    }
}

/**
 * Layout C's speed gauge: a 270-degree arc with the gap at the bottom, 0-200 km/h, nine ticks, the
 * value in the middle. Changes ease out like a needle (360 ms up, 260 ms down); arcs are paths,
 * which HWUI 4.x draws reliably with round caps.
 */
private class Gauge(c: Context) : View(c) {
    private val k = Ui.s
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 8 * k; color = C.LINE
    }
    private val arc = Paint(track).apply { color = C.TEXT }
    private val tick = Paint(track).apply { strokeWidth = 2 * k }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = F.semi; textSize = 76 * k; textAlign = Paint.Align.CENTER; color = C.TEXT
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = F.mono; textSize = 13 * k; textAlign = Paint.Align.CENTER; color = C.MUTED
    }
    private val unitText = c.getString(R.string.kmh_caps)
    private val path = Path()
    private val oval = RectF()
    private var value: Double? = null
    private var shown = last
    private var anim: ValueAnimator? = null

    fun set(v: Double?, fresh: Boolean) {
        val color = if (fresh) C.TEXT else C.FAINT
        arc.color = color; number.color = color
        value = v
        if (v == null) { anim?.cancel(); invalidate(); return }
        val to = v.toFloat()
        if (Math.abs(to - shown) < 0.05f) { invalidate(); return }
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, to).apply {
            duration = if (to > shown) 360L else 260L
            interpolator = DecelerateInterpolator(1.5f) // 1 - (1 - t)^3
            addUpdateListener { shown = it.animatedValue as Float; last = shown; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = 110 * k
        val r = 92 * k
        oval.set(cx - r, cy - r, cx + r, cy + r)
        path.reset(); path.addArc(oval, 135f, 270f)
        canvas.drawPath(path, track)
        val has = value != null
        val sweep = if (has) 270f * minOf(1f, shown / 200f) else 0f
        for (i in 0..8) {
            val deg = 135f + i * 33.75f
            val rad = Math.toRadians(deg.toDouble())
            val cos = Math.cos(rad).toFloat(); val sin = Math.sin(rad).toFloat()
            val inner = r - (if (i % 2 == 0) 24 else 19) * k
            tick.color = if (has && deg <= 135f + sweep) arc.color else C.LINE2
            canvas.drawLine(cx + cos * (r - 12 * k), cy + sin * (r - 12 * k), cx + cos * inner, cy + sin * inner, tick)
        }
        if (sweep > 0f) { path.reset(); path.addArc(oval, 135f, sweep); canvas.drawPath(path, arc) }
        canvas.drawText(if (has) Math.round(shown).toString() else "—", cx, 128 * k, number)
        canvas.drawText(unitText, cx, 160 * k, unitPaint)
    }

    companion object {
        /** Last drawn speed, so a re-rendered dashboard continues from it instead of sweeping up from 0. */
        private var last = 0f
    }
}

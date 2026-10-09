package me.ri3d.dashboard

import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.TextView
import me.ri3d.dashboard.VehicleState.Door
import me.ri3d.dashboard.VehicleState.Status
import java.util.Locale

/**
 * The one CanService vehicle widget, in three presentations: TALL (layout A), WIDE (layout B)
 * and CLOCK (the clock screen's compact row). What each place shows is the driver's choice
 * ([WidgetSlots], Settings › Vehicle widget values). Built once per render, then [bind] updates
 * it in place on every vehicle change.
 */
class VehicleWidget(private val a: MainActivity, private val kind: Int) {
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
    private val tileCount = if (kind == TALL) 4 else if (kind == WIDE) 2 else 0
    private val tileLabels = List(tileCount) { label(if (kind == TALL) 11f else 13f) }
    private val tileValues = List(tileCount) { value(if (kind == WIDE) 30f else 24f) }
    private val tileCells = ArrayList<View>()

    private var data: View? = null
    private var noData: View? = null
    private val noDataText = a.para("", if (kind == WIDE) 17f else 18f, C.TEXT2, if (kind == WIDE) 1.3f else 1.4f)
    private val openButton = a.pill(a.getString(R.string.open_canservice), h = if (kind == WIDE) 48f else 56f,
        size = if (kind == WIDE) 17f else 18f, fill = C.TEXT, fg = C.BG, stroke = 0, radius = 14f) { a.launchRole(Role.VEHICLE) }

    val view: View = when (kind) {
        TALL -> tall()
        WIDE -> wide()
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

    private fun clock(): View = a.row(44f).apply {
        gravity = Gravity.BOTTOM
        contentDescription = a.getString(R.string.vehicle_a11y)
        fun cell(l: View, v: View) = a.col(8f).apply { add(l); add(v) }
        add(cell(mainLabel, main)); add(cell(barLabel, barValue)); add(cell(belowLabel, belowValue))
        spring()
        add(via)
    }

    fun bind() {
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
            if (kind == TALL) tileCells[i].background = shape(C.RAISED, if (m == Metric.DOORS && doorWarn) C.RED else C.RAISED, 14f)
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
        const val TALL = 0; const val WIDE = 1; const val CLOCK = 2
        const val CAN = "CanService"
    }
}

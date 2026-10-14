package me.ri3d.dashboard

import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import me.ri3d.dashboard.VehicleState.Reading
import java.util.Locale

// ---------------------------------------------------------------- climate

/** OEM temperature interpretation: automatic LO/HI or degrees, manual cold/warm positions. */
fun MainActivity.setTempText(short: Boolean = false): String {
    val v = Hub.vehicle
    val type = ClimateControl.integer(v.value(Reading.AC_TYPE), 0..1) ?: return "—"
    val value = ClimateControl.temperatureValue(v.value(Reading.SET_TEMP), type == 1) ?: return "—"
    if (type == 0) return getString(if (short) R.string.manual_temp_short else R.string.manual_temp, value - 18)
    return when (value) { 17 -> "LO"; 33 -> "HI"; else -> degrees(value.toDouble(), short) }
}

fun setTempFresh(): Boolean {
    val now = SystemClock.elapsedRealtime()
    return Hub.vehicle.fresh(Reading.SET_TEMP, now) && Hub.vehicle.fresh(Reading.AC_TYPE, now)
}

private fun MainActivity.degrees(x: Double?, short: Boolean = false): String {
    if (x == null) return "—"
    val n = if (x == Math.floor(x)) Math.round(x).toString() else String.format(Locale.getDefault(), "%.1f", x)
    return if (short) "$n°" else getString(R.string.unit_c, n)
}

/** Send through the gated worker; displayed values always come from subsequent car feedback. */
fun MainActivity.climate(cmd: ClimateCmd, direction: Int = 0) = CanService.command(cmd, direction) { result ->
    toast(getString(when (result) {
        ClimateResult.SENT -> R.string.climate_sent
        ClimateResult.DISABLED -> R.string.climate_disabled
        ClimateResult.DEMO -> R.string.climate_demo
        ClimateResult.UNAVAILABLE -> R.string.climate_unavailable
        ClimateResult.UNKNOWN -> R.string.climate_unknown
        ClimateResult.UNSUPPORTED -> R.string.climate_unsupported
        ClimateResult.LIMIT -> R.string.climate_limit
        ClimateResult.FAN_OFF -> R.string.climate_fan_off
        ClimateResult.BUSY -> R.string.climate_busy
        ClimateResult.FAILED -> R.string.climate_failed
    }))
}

/** Climate panel (A/C button on every dashboard): set temperature, fan, seats and the climate switches. */
fun MainActivity.climatePanel(): View = dialogBox(28f, 20f).apply {
    val v = Hub.vehicle
    val now = SystemClock.elapsedRealtime()
    val readings = Reading.CLIMATE - setOf(Reading.OUTSIDE, Reading.CABIN, Reading.AC_TYPE)
    val status = when {
        readings.none { v.value(it) != null } -> VehicleState.Status.NO_DATA
        readings.any { v.fresh(it, now) } -> VehicleState.Status.LIVE
        else -> VehicleState.Status.STALE
    }
    val has = status != VehicleState.Status.NO_DATA
    val controls = Hub.prefs.climateControls && !Hub.demo && CanService.link == CanService.Link.CONNECTED
    fun color(r: Reading) = if (v.fresh(r, now)) C.TEXT else C.FAINT
    fun level(r: Reading) = if (v.fresh(r, now)) ClimateControl.integer(v.value(r), r.min.toInt()..r.max.toInt()) else null
    fun View.control(available: Boolean = true): View = apply {
        isEnabled = controls && available
        alpha = if (isEnabled) 1f else 0.4f
    }

    val head = add(row(12f), MATCH, 56.u)
    val names = head.add(col(4f), 0, WRAP, 1f)
    names.add(text(getString(R.string.climate), 28f, F.semi, track = -0.02f))
    names.add(caps(getString(when {
        Hub.demo -> R.string.climate_demo
        !Hub.prefs.climateControls -> R.string.climate_controls_off
        else -> R.string.climate_sub
    })))
    head.add(Chip(context, 32f, 12f).apply {
        when (status) {
            VehicleState.Status.STALE -> set(getString(R.string.state_stale), C.accent)
            VehicleState.Status.NO_DATA -> set(getString(R.string.state_nodata), C.FAINT)
            else -> set(getString(R.string.state_live), C.GREEN)
        }
    }, WRAP, 32.u)
    head.add(pill(getString(R.string.close)) { closeOverlay() }, WRAP, 56.u)

    val body = add(row(12f).apply { gravity = Gravity.NO_GRAVITY }, MATCH, 0, 1f)
    fun card(w: Int, weight: Float = 0f) = body.add(col().apply { background = shape(C.RAISED, radius = 18f); pad(20f) }, w, MATCH, weight)
    fun stepper(parent: LinearLayout, value: View, less: Int, more: Int, cmd: ClimateCmd) {
        val r = parent.add(row(), MATCH, 56.u)
        r.add(iconButton(Icons.MINUS, getString(less)) { climate(cmd, -1) }.control(), 56.u, 56.u)
        r.spring(); r.add(value); r.spring()
        r.add(iconButton(Icons.PLUS, getString(more)) { climate(cmd, 1) }.control(), 56.u, 56.u)
    }

    val temp = card(300.u)
    temp.add(caps(getString(R.string.temperature), 13f))
    temp.spring()
    stepper(temp, text(setTempText(short = true), 56f, F.semi, if (setTempFresh()) C.TEXT else C.FAINT, -0.05f),
        R.string.colder, R.string.warmer, ClimateCmd.TEMP)
    temp.spring()
    temp.add(text(getString(R.string.climate_readings, degrees(v.value(Reading.CABIN)), degrees(v.value(Reading.OUTSIDE))), 15f, color = C.MUTED))

    val fan = card(300.u)
    fan.add(caps(getString(R.string.fan), 13f))
    fan.spring()
    val speed = level(Reading.FAN)
    val bars = fan.add(row(8f).apply { gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM }, MATCH, 40.u)
    for (i in 1..FAN_BARS) bars.add(View(context).apply {
        background = shape(if (speed != null && i <= speed) color(Reading.FAN) else C.LINE2, radius = 4f)
    }, 22.u, (12 + (i - 1) * 4).u)
    fan.spring()
    stepper(fan, text(when (speed) { null -> "—"; 0 -> getString(R.string.fan_off); else -> getString(R.string.fan_level, speed) },
        24f, F.semi, color(Reading.FAN), -0.02f), R.string.less_fan, R.string.more_fan, ClimateCmd.FAN_STEP)

    val seats = card(0, 1f)
    seats.add(caps(getString(R.string.seats_wheel), 13f))
    fun seatPill(label: Int, r: Reading?, max: Int, cmd: ClimateCmd, who: String) = row(8f).apply {
        val available = cmd != ClimateCmd.WHEEL_HEAT && CanService.seatSupport[cmd] == true
        val lvl = r?.takeIf { (available || Hub.demo) && v.fresh(it, now) }?.let { level(it) }
        background = pressable(0, if (lvl != null && lvl > 0) C.MUTED else C.LINE2, 24f)
        pad(14f, 0f)
        contentDescription = who + " · " + getString(label)
        if (!available) contentDescription = "$contentDescription · ${getString(R.string.climate_unsupported)}"
        add(caps(getString(label), 12f, C.TEXT2), 0, WRAP, 1f)
        val b = add(row(3f).apply { gravity = Gravity.BOTTOM })
        if (lvl == null) b.add(text("—", 16f, color = C.FAINT))
        else for (i in 1..max) b.add(View(context).apply {
            background = shape(if (i <= lvl) C.TEXT else C.LINE2, radius = 2f)
        }, 6.u, (6 + i * 4).u)
        onTap { climate(cmd) }
        control(available)
    }
    fun seatRow(name: Int, vararg pills: Triple<Int, Reading?, ClimateCmd>) {
        seats.spring()
        val r = seats.add(row(12f), MATCH, 48.u)
        r.add(text(getString(name), 17f, F.medium), 0, WRAP, 1f)
        for ((label, reading, cmd) in pills) r.add(seatPill(label, reading, if (cmd == ClimateCmd.WHEEL_HEAT) 1 else 3, cmd, getString(name)), 112.u, 48.u)
    }
    seatRow(R.string.driver, Triple(R.string.heat, Reading.SEAT_HEAT_DRIVER, ClimateCmd.SEAT_HEAT_DRIVER),
        Triple(R.string.vent, Reading.SEAT_VENT_DRIVER, ClimateCmd.SEAT_VENT_DRIVER))
    seatRow(R.string.passenger, Triple(R.string.heat, Reading.SEAT_HEAT_PASS, ClimateCmd.SEAT_HEAT_PASS),
        Triple(R.string.vent, Reading.SEAT_VENT_PASS, ClimateCmd.SEAT_VENT_PASS))
    seatRow(R.string.steering_wheel, Triple(R.string.heat, null, ClimateCmd.WHEEL_HEAT)) // no feedback signal found

    // Unknown or stale flags must not look like a confirmed off state.
    val toggles = add(row(8f), MATCH, 56.u)
    fun toggle(label: Int, on: Boolean?, cmd: ClimateCmd) = toggles.add(pill(
        if (on == null) getString(R.string.climate_value_unknown, getString(label)) else getString(label), h = 56f, padH = 10f, size = 16f,
        fill = if (on == true) C.TEXT else 0, fg = if (on == true) C.BG else if (on != null) C.TEXT else C.FAINT,
        stroke = if (on == true) C.TEXT else C.LINE2) { climate(cmd) }
        .apply { contentDescription = getString(label) }.control(), 0, 56.u, 1f)
    fun flag(r: Reading): Boolean? = if (v.fresh(r, now)) when (v.value(r)) { 0.0 -> false; 1.0 -> true; else -> null } else null
    toggle(R.string.climate, flag(Reading.CLIMATE_OFF)?.not(), ClimateCmd.POWER)
    toggle(R.string.ac_short, flag(Reading.AC), ClimateCmd.AC)
    toggle(R.string.auto, flag(Reading.AUTO), ClimateCmd.AUTO)
    val aqs = flag(Reading.AQS)
    val recirc = flag(Reading.RECIRC)
    toggle(if (aqs == true) R.string.aqs else R.string.recirculation,
        if (aqs == null || recirc == null) null else aqs || recirc, ClimateCmd.RECIRC)
    toggle(R.string.front_defrost, flag(Reading.DEFROST_FRONT), ClimateCmd.DEFROST_FRONT)
    toggle(R.string.rear_defrost, flag(Reading.DEFROST_REAR), ClimateCmd.DEFROST_REAR)

    add(para(getString(when {
        Hub.demo -> R.string.climate_demo
        !Hub.prefs.climateControls -> R.string.climate_disabled
        !has -> R.string.can_no_data
        else -> R.string.climate_note
    }), 15f), MATCH, WRAP)
}

/** Fan bars drawn; the car's real maximum (ACProp 14 BLOWERSPD_MAXMIN) is not read yet. */
private const val FAN_BARS = 7

// ---------------------------------------------------------------- phone

/** Phone panel (phone status on every dashboard): which phone, the phone app and Android Auto. */
fun MainActivity.phonePanel(): View = dialogBox(24f, 8f).apply {
    val p = Hub.phone
    val head = add(row(16f), MATCH, WRAP)
    head.margins(b = 12f)
    head.add(iconBox(56f, 14f, Icons.PHONE, 28f, C.TEXT), 56.u, 56.u)
    val names = head.add(col(6f), 0, WRAP, 1f)
    names.add(text(p.name ?: getString(if (p.connected) R.string.phone_connected else R.string.phone_not_connected), 26f, F.semi, track = -0.02f))
    val line = names.add(row(8f))
    line.add(View(context).apply { background = shape(if (p.connected) C.GREEN else C.FAINT, radius = 4f) }, 8.u, 8.u)
    line.add(caps(if (p.disconnected || p.link == PhoneLink.UNKNOWN) phoneStatus() else
        getString(if (p.connected) R.string.phone_connected_bt else R.string.phone_paired), 12f))
    fun action(label: Int, icon: String, iconColor: Int, note: String, r: Role) = add(row(14f).apply {
        background = pressable(C.RAISED, 0, 16f)
        pad(20f, 0f)
        add(IconView(context, icon, iconColor), 24.u, 24.u)
        add(text(getString(label), 18f, F.medium), 0, WRAP, 1f)
        add(caps(note))
        onTap { closeOverlay(); launchRole(r) }
    }, MATCH, 64.u)
    action(R.string.phone_app, Icons.PHONE, C.TEXT, getString(R.string.calls_contacts), Role.CALL)
    action(R.string.tile_aa, Icons.AA, if (p.connected) C.accent else C.MUTED,
        getString(if (p.disconnected) R.string.aa_connect_first else R.string.aa_tap), Role.AA)
    add(para(getString(R.string.phone_calls_note), 15f, lineHeight = 1.45f), MATCH, WRAP).margins(t = 4f, b = 4f)
    add(pill(getString(R.string.close), radius = 16f) { closeOverlay() }, MATCH, 56.u)
}

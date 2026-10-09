package me.ri3d.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import me.ri3d.dashboard.VehicleState.Door
import me.ri3d.dashboard.VehicleState.Reading

/**
 * Debug-only fixtures reproducing the artifact's sample states. Not in release builds.
 *
 *   adb shell am broadcast -a me.ri3d.dashboard.DEMO -n me.ri3d.dashboard/.DemoReceiver \
 *       --es vehicle live|door|stale|nodata  --es phone connected|paired|disconnected|none \
 *       --es call none|incoming|incoming-noctl|active|active-noctl  --es radio on|off \
 *       --es hide me.ri3d.welle,...  (or --es mode off to return to the real providers)
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.getStringExtra("mode") == "off") { Demo.off(); return }
        Hub.demo = true
        i.getStringExtra("vehicle")?.let { Demo.vehicle(it) }
        i.getStringExtra("phone")?.let { Demo.phone(it) }
        i.getStringExtra("call")?.let { Demo.call(it) }
        i.getStringExtra("radio")?.let { Demo.radio(it == "on") }
        i.getStringExtra("hide")?.let { h ->
            Hub.apps.hidden = h.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            Hub.apps.refresh()
        }
    }
}

private object Demo {
    private var vehicleMode = "nodata"
    /** Live fixtures are re-sent every 2 s so they stay fresh; "stale" simply stops sending. */
    private val feed = object : Runnable {
        override fun run() {
            write(SystemClock.elapsedRealtime())
            Hub.main.postDelayed(this, 2000)
        }
    }

    private fun write(at: Long) {
        val v = Hub.vehicle
        v.source = "Demo"
        v.put(Reading.OUTSIDE, 14.0, at); v.put(Reading.FUEL, 62.0, at); v.put(Reading.RANGE, 480.0, at)
        v.put(Reading.BATTERY, 12.6, at); v.put(Reading.COOLANT, 88.0, at); v.put(Reading.TRIP, 23.4, at)
        v.put(Reading.CABIN, 21.0, at); v.put(Reading.SET_TEMP, 21.5, at); v.put(Reading.SOC, 80.0, at)
        v.put(Reading.SPEED, 48.0, at); v.put(Reading.ODOMETER, 12840.0, at)
        for (d in Door.values()) v.putDoor(d, vehicleMode == "door" && d == Door.REAR_LEFT, at)
        Hub.vehicleChanged()
    }

    fun vehicle(mode: String) {
        vehicleMode = mode
        Hub.main.removeCallbacks(feed)
        when (mode) {
            "live", "door" -> feed.run()
            "stale" -> write(SystemClock.elapsedRealtime() - VehicleState.STALE_MS - 1000)
            else -> { Hub.vehicle.clear(); Hub.vehicle.source = null; Hub.vehicleChanged() }
        }
    }

    fun phone(mode: String) = Hub.setPhone(when (mode) {
        "connected" -> PhoneState(PhoneLink.CONNECTED)
        "paired" -> PhoneState(PhoneLink.PAIRED)
        "disconnected" -> PhoneState(PhoneLink.DISCONNECTED)
        else -> PhoneState(PhoneLink.NOT_PAIRED)
    }, fromEvent = true)

    private class Control(full: Boolean) : CallControl {
        override val canAnswer = full
        override val canDecline = full
        override val canEnd = full
        override val canMute = full
        override fun answer() = Hub.setCall(CallState(CallPhase.ACTIVE, Hub.call.caller, Hub.call.detail, muted = false))
        override fun decline() = Hub.setCall(CallState())
        override fun end() = Hub.setCall(CallState())
        override fun setMuted(muted: Boolean) =
            Hub.setCall(CallState(CallPhase.ACTIVE, Hub.call.caller, Hub.call.detail, muted = muted))
    }

    fun call(mode: String) {
        val full = !mode.endsWith("-noctl")
        Hub.callControl = if (mode == "none") null else Control(full)
        Hub.setCall(when (mode.removeSuffix("-noctl")) {
            "incoming" -> CallState(CallPhase.INCOMING, "Caller name", "Mobile")
            // Recovered while already active: start time unknown, so no duration is shown.
            "active" -> CallState(CallPhase.ACTIVE, "Caller name", "Mobile", muted = if (full) false else null)
            else -> CallState()
        })
    }

    private const val COUNT = 6
    private var preset = 3

    private class Radio : RadioControl {
        override val name = "Demo"
        private fun set(p: Int, playing: Boolean) {
            val r = Hub.radio ?: return
            preset = p
            Hub.radio = RadioInfo(r.source, "Station $p", "Programme text", Hub.app.getString(R.string.preset_of, p, COUNT), playing)
            Hub.changed(Hub.RADIO)
        }
        override fun prev() { val r = Hub.radio ?: return; set(if (preset <= 1) COUNT else preset - 1, r.playing == true) }
        override fun next() { val r = Hub.radio ?: return; set(if (preset >= COUNT) 1 else preset + 1, r.playing == true) }
        override fun toggle() { val r = Hub.radio ?: return; set(preset, r.playing != true) }
    }

    fun radio(on: Boolean) {
        if (on) {
            preset = 3
            Hub.radio = RadioInfo("DAB+ · WELLE", "Station name", "Programme text", Hub.app.getString(R.string.preset_of, 3, COUNT), true)
            Hub.radioControl = Radio()
            Hub.changed(Hub.RADIO)
        } else {
            val d = Hub.demo
            Hub.demo = false; Welle.bind(); Hub.demo = d
            Hub.changed(Hub.RADIO)
        }
    }

    fun off() {
        Hub.main.removeCallbacks(feed)
        Hub.demo = false
        Hub.vehicle.clear(); Hub.vehicle.source = null; Hub.vehicleChanged()
        Hub.callControl = null; Hub.setCall(CallState())
        Hub.apps.hidden = emptySet()
        Welle.bind(); Bluetooth.query(); Hub.apps.refresh()
        Hub.changed(Hub.RADIO)
    }
}

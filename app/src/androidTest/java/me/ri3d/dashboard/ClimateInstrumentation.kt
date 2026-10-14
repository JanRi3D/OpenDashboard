package me.ri3d.dashboard

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Parcel
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Platform-only integration checks, including real API16 Parcel encoding and actual button clicks. */
class ClimateInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        try {
            preferenceDefaultAndPersistence()
            binderContract()
            panelButtons()
            result.putString("stream", "PASS: climate preference, Binder/Parcelable contract, all 14 supported panel buttons, gates and failure paths\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: ${android.util.Log.getStackTraceString(error)}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun preferenceDefaultAndPersistence() {
        val c = object : ContextWrapper(targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("climate-instrumentation", mode)
        }
        val sp = c.getSharedPreferences("", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        try {
            check(!Prefs(c).climateControls) { "Controls must start off" }
            Prefs(c).climateControls = true
            check(Prefs(c).climateControls) { "Opt-in must survive Prefs recreation" }
            Prefs(c).climateControls = false
            check(!Prefs(c).climateControls)
        } finally { sp.edit().clear().commit() }
    }

    private class Car : Binder() {
        val writes: MutableList<Any> = Collections.synchronizedList(ArrayList())
        val climate = Collections.synchronizedMap(mutableMapOf(27 to 1f, 17 to 21f, 12 to 3f,
            3 to 0f, 4 to 1f, 2 to 1f, 9 to 0f, 1 to 0f, 0 to 0f, 21 to 0f))
        var vehicleType = 1
        var refuseWrites = false
        var exceptionOnWrite = false
        var shortReply = false
        @Volatile var pauseOnNextClimateRead: (() -> Unit)? = null
        val child = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(CanClimatePort.AC)
                check(flags == 0)
                if (shortReply) { reply!!.writeNoException(); return true }
                when (code) {
                    4 -> {
                        pauseOnNextClimateRead?.let { pauseOnNextClimateRead = null; it() }
                        reply!!.writeNoException(); reply.writeFloat(climate[data.readInt()] ?: Float.NaN)
                    }
                    3 -> {
                        if (refuseWrites) return false
                        if (exceptionOnWrite) { reply!!.writeException(SecurityException("Test refusal")); return true }
                        writes.add(data.readInt() to data.readInt())
                        check(data.dataAvail() == 0)
                        reply!!.writeNoException()
                    }
                    else -> error("Unexpected climate transaction $code")
                }
                return true
            }
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface(CanClimatePort.CANBOX)
            check(flags == 0)
            if (code == 15 && refuseWrites) return false
            reply!!.writeNoException()
            if (shortReply) return true
            when (code) {
                1 -> reply.writeStrongBinder(child)
                6 -> { data.readInt(); reply.writeFloat(0f) }
                7 -> { data.readInt(); reply.writeFloat(if (vehicleType == 63) 1f else 0f) }
                10 -> reply.writeInt(when (data.readInt()) { 0 -> vehicleType; 27 -> 0; 5, 6, 9, 10 -> 1; else -> Int.MIN_VALUE })
                11 -> reply.writeFloat(0f)
                15 -> {
                    check(data.readInt() == 2) { "Heat and ventilation must be one two-entry map" }
                    val values = HashMap<Int, Float>()
                    repeat(2) {
                        check(data.readInt() == 4) { "Key must use VAL_PARCELABLE, not enum Java serialization" }
                        check(data.readString() == "com.adayo.canproxy.bean.VehicleSettingType")
                        val id = data.readInt() // the full OEM enum ordinal
                        check(data.readInt() == 7) { "Map value must use VAL_FLOAT" }
                        values[id] = data.readFloat()
                    }
                    check(data.dataAvail() == 0)
                    writes.add(values)
                }
                else -> error("Unexpected Canbox transaction $code")
            }
            return true
        }
    }

    private fun binderContract() {
        val car = Car()
        val port = CanClimatePort(car)
        val control = ClimateControl(port) { null }
        check(port.config(123) == null) { "Config error sentinel must remain unknown" }
        check(port.climate(123) == null) { "NaN must remain unknown" }
        check(control.send(ClimateCmd.TEMP, 1) == ClimateResult.SENT)
        check(car.writes.removeAt(0) == (10 to 4))
        for ((type, cmd, expected) in listOf(
            Triple(1, ClimateCmd.SEAT_HEAT_DRIVER, mapOf(14 to 2f, 15 to 1f)),
            Triple(1, ClimateCmd.SEAT_VENT_PASS, mapOf(55 to 1f, 56 to 2f)),
            Triple(63, ClimateCmd.SEAT_HEAT_DRIVER, mapOf(93 to 2f, 97 to 1f)),
            Triple(63, ClimateCmd.SEAT_VENT_PASS, mapOf(94 to 1f, 98 to 2f)))) {
            car.vehicleType = type
            check(control.send(cmd) == ClimateResult.SENT)
            check(car.writes.removeAt(0) == expected) { "Wrong OEM seat parcel for $cmd / $type" }
        }
        car.refuseWrites = true
        check(control.send(ClimateCmd.TEMP, -1) == ClimateResult.FAILED)
        check(control.send(ClimateCmd.SEAT_HEAT_DRIVER) == ClimateResult.FAILED)
        car.refuseWrites = false; car.exceptionOnWrite = true
        check(control.send(ClimateCmd.TEMP, -1) == ClimateResult.FAILED)
        check(car.writes.isEmpty()) { "Failures must not replay requests" }
        car.shortReply = true
        check(port.config(0) == null) { "Truncated config reply must remain unknown, not become zero" }
        check(port.climate(3) == null) { "Truncated climate reply must remain unknown" }
        check(port.seat(49) == null) { "Truncated seat reply must remain unknown" }
    }

    private fun find(root: View, description: String): View? {
        if (root.contentDescription?.toString() == description) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), description)?.let { return it }
        return null
    }

    private fun texts(root: View): List<String> {
        val own = if (root is TextView) listOf(root.text.toString()) else emptyList()
        return if (root is ViewGroup) own + (0 until root.childCount).flatMap { texts(root.getChildAt(it)) } else own
    }

    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private fun panelButtons() {
        // Substitute only the hardware connection in the test process. All Views, gates, command
        // planning, worker scheduling and Parcel code remain production code. No car is contacted.
        val car = Car()
        val oldOn = Hub.prefs.climateControls
        val oldDemo = Hub.demo
        val fields = listOf("canbox", "climatePort", "active", "link", "seatSupport").associateWith {
            CanService::class.java.getDeclaredField(it).apply { isAccessible = true }
        }
        val saved = fields.mapValues { it.value.get(CanService) }
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        waitForIdleSync()
        try {
            onMain {
                CanService.setActive(false)
                fields.getValue("canbox").set(CanService, car)
                fields.getValue("climatePort").set(CanService, CanClimatePort(car))
                fields.getValue("active").set(CanService, true)
                fields.getValue("link").set(CanService, CanService.Link.CONNECTED)
                fields.getValue("seatSupport").set(CanService, SeatControl.values().associate { it.cmd to true })
                Hub.demo = false; Hub.prefs.climateControls = false
                activity.push(Screen.SETTINGS)
            }
            waitForIdleSync()
            onMain {
                val toggle = find(activity.window.decorView, activity.getString(R.string.climate_controls)) ?: error("Missing Settings switch")
                toggle.performClick()
                check(Hub.prefs.climateControls)
                activity.openOverlay(Overlay.Climate)
            }
            waitForIdleSync()
            val cases = listOf(
                "Colder" to (10 to 2), "Warmer" to (10 to 4), "Less fan" to (11 to 3), "More fan" to (11 to 5),
                "Climate" to (0 to 0), "A/C" to (2 to 0), "Auto" to (1 to 0), "Recirculation" to (3 to 1),
                "Front defrost" to (5 to 1), "Rear defrost" to (6 to 1),
                "Driver · Heat" to mapOf(14 to 2f, 15 to 1f), "Driver · Vent" to mapOf(14 to 1f, 15 to 2f),
                "Passenger · Heat" to mapOf(55 to 2f, 56 to 1f), "Passenger · Vent" to mapOf(55 to 1f, 56 to 2f))
            for ((label, expected) in cases) {
                car.writes.clear()
                onMain {
                    val button = find(activity.dialogLayer, label) ?: error("Missing button $label")
                    check(button.isEnabled) { "Disabled button $label" }
                    button.performClick()
                }
                val until = SystemClock.elapsedRealtime() + 2500
                while (car.writes.isEmpty() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(20)
                check(car.writes.toList() == listOf(expected)) { "$label sent ${car.writes}, expected $expected" }
                SystemClock.sleep(800) // production debounce + state refresh
                waitForIdleSync()
            }
            onMain {
                CanService.setActive(false)
                Hub.vehicle.clear()
                val now = SystemClock.elapsedRealtime()
                Hub.vehicle.put(VehicleState.Reading.FUEL, 50.0, now)
                Hub.vehicle.put(VehicleState.Reading.AC, 2.0, now)
                Hub.vehicle.put(VehicleState.Reading.AUTO, 1.0, now - VehicleState.STALE_MS)
                val panel = activity.climatePanel()
                check("A/C · —" in texts(panel)) { "Invalid AC flag must show unknown" }
                check("Auto · —" in texts(panel)) { "Stale Auto flag must show unknown" }
                Hub.vehicle.clear()
                Hub.vehicle.put(VehicleState.Reading.FUEL, 50.0, now)
                check(activity.getString(R.string.state_nodata) in texts(activity.climatePanel())) { "Body data must not make the climate panel live" }
                Hub.vehicle.put(VehicleState.Reading.AC, 1.0, now - VehicleState.STALE_MS)
                check(activity.getString(R.string.state_stale) in texts(activity.climatePanel()))
            }
            for (reason in listOf(ClimateResult.DISABLED, ClimateResult.DEMO, ClimateResult.UNAVAILABLE)) {
                car.writes.clear()
                val latch = CountDownLatch(1)
                onMain {
                    Hub.prefs.climateControls = reason != ClimateResult.DISABLED
                    Hub.demo = reason == ClimateResult.DEMO
                    if (reason == ClimateResult.UNAVAILABLE) CanService.setActive(false)
                    CanService.command(ClimateCmd.TEMP, 1) { check(it == reason); latch.countDown() }
                }
                check(latch.await(2, TimeUnit.SECONDS))
                check(car.writes.isEmpty())
            }
            for (closePanel in listOf(true, false)) {
                car.writes.clear()
                val readEntered = CountDownLatch(1)
                val releaseRead = CountDownLatch(1)
                val finished = CountDownLatch(1)
                var outcome: ClimateResult? = null
                onMain {
                    Hub.demo = false; Hub.prefs.climateControls = true
                    fields.getValue("active").set(CanService, true) // no polling scheduled
                    car.pauseOnNextClimateRead = {
                        readEntered.countDown()
                        check(releaseRead.await(3, TimeUnit.SECONDS)) { "Timed out waiting for lifecycle transition" }
                    }
                    CanService.command(ClimateCmd.TEMP, 1) { outcome = it; finished.countDown() }
                }
                try {
                    check(readEntered.await(2, TimeUnit.SECONDS)) { "Command did not reach its fresh read" }
                    onMain {
                        if (closePanel) { activity.closeOverlay(); activity.openOverlay(Overlay.Climate) }
                        else { CanService.setActive(false); CanService.setActive(true) }
                    }
                } finally { releaseRead.countDown() }
                check(finished.await(2, TimeUnit.SECONDS))
                check(outcome == ClimateResult.UNAVAILABLE) { "Old tap survived close/reopen or pause/resume: $outcome" }
                check(car.writes.isEmpty()) { "Cancelled action must never write" }
                onMain { CanService.setActive(false) }
                waitForIdleSync()
            }
            onMain {
                Hub.vehicle.clear()
                val now = SystemClock.elapsedRealtime()
                for (invalid in listOf(0.25, 0.5)) {
                    Hub.vehicle.put(VehicleState.Reading.FAN, invalid, now)
                    val shown = texts(activity.climatePanel())
                    check(activity.getString(R.string.fan_level, 1) !in shown && activity.getString(R.string.fan_off) !in shown) {
                        "Fractional fan feedback must not be rounded into a known level"
                    }
                }
                Hub.vehicle.put(VehicleState.Reading.FAN, 3.0, now - VehicleState.STALE_MS)
                check(activity.getString(R.string.fan_level, 3) !in texts(activity.climatePanel())) { "Stale fan must show unknown" }
            }
        } finally {
            onMain {
                CanService.setActive(false)
                for ((name, field) in fields) field.set(CanService, saved[name])
                Hub.prefs.climateControls = oldOn; Hub.demo = oldDemo
                Hub.vehicle.clear(); activity.closeOverlay(); activity.finish()
            }
        }
    }
}

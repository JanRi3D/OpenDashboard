package me.ri3d.dashboard

import org.junit.Assert.*
import org.junit.Test

/** Literal request expectations from AC.apk and canservice.apk, not calculated by the planner. */
class ClimateControlTest {
    private class Car : ClimatePort {
        val ac = mutableMapOf(27 to 1.0, 17 to 21.0, 12 to 3.0, 1 to 0.0, 9 to 0.0)
        val cfg = mutableMapOf(0 to 1, 5 to 1, 6 to 1, 9 to 1, 10 to 1, 27 to 0)
        val seats = mutableMapOf(49 to 0.0, 50 to 0.0, 56 to 0.0, 57 to 0.0,
            94 to 1.0, 98 to 1.0, 95 to 1.0, 99 to 1.0)
        val writes = arrayListOf<Any>()
        var accepted = true
        var onRead: () -> Unit = {}
        override fun climate(id: Int): Double? { onRead(); return ac[id] }
        override fun config(id: Int): Int? { onRead(); return cfg[id] }
        override fun seat(id: Int): Double? { onRead(); return seats[id] }
        override fun sendClimate(id: Int, value: Int): Boolean { writes.add(id to value); return accepted }
        override fun sendSeats(values: Map<String, Float>): Boolean { writes.add(values); return accepted }
        fun send(cmd: ClimateCmd, direction: Int = 0) = ClimateControl(this) { null }.send(cmd, direction)
    }

    @Test fun switchesUseTheirOwnFreshReadAndPowerInvertsOffMode() {
        val cases = listOf(Triple(ClimateCmd.POWER, 3, 0), Triple(ClimateCmd.AUTO, 4, 1),
            Triple(ClimateCmd.AC, 2, 2), Triple(ClimateCmd.DEFROST_FRONT, 0, 5), Triple(ClimateCmd.DEFROST_REAR, 21, 6))
        for ((cmd, read, write) in cases) for (state in 0..1) {
            val car = Car().apply { ac[read] = state.toDouble() }
            assertEquals(ClimateResult.SENT, car.send(cmd))
            assertEquals(listOf(write to if (cmd == ClimateCmd.POWER) state else 1 - state), car.writes)
        }
    }

    @Test fun disabledDemoAndDisconnectedNeverReadOrWrite() {
        for (reason in listOf(ClimateResult.DISABLED, ClimateResult.DEMO, ClimateResult.UNAVAILABLE)) {
            val car = Car().apply { onRead = { fail("Blocked action must not read hardware") } }
            assertEquals(reason, ClimateControl(car) { reason }.send(ClimateCmd.TEMP, 1))
            assertTrue(car.writes.isEmpty())
        }
    }

    @Test fun revokingControlsDuringReadCancelsTheWrite() {
        val car = Car()
        var stop: ClimateResult? = null
        car.onRead = { stop = ClimateResult.DISABLED }
        assertEquals(ClimateResult.DISABLED, ClimateControl(car) { stop }.send(ClimateCmd.TEMP, 1))
        assertTrue(car.writes.isEmpty())
    }

    @Test fun unknownAndMalformedFeedbackNeverBecomesAnOffState() {
        for (value in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 2.0, 0.5)) {
            val car = Car().apply { if (value == null) ac.remove(2) else ac[2] = value }
            assertEquals(ClimateResult.UNKNOWN, car.send(ClimateCmd.AC))
            assertTrue(car.writes.isEmpty())
        }
    }

    @Test fun automaticTemperatureStepsThroughLoAndHi() {
        val cases = listOf(
            Triple(21.0, -1, 10 to 2), Triple(21.0, 1, 10 to 4),
            Triple(18.0, -1, 7 to 1), Triple(32.0, 1, 7 to 2),
            Triple(-40.0, 1, 10 to 0), Triple(87.0, -1, 10 to 14),
            Triple(17.0, 1, 10 to 0), Triple(33.0, -1, 10 to 14))
        for ((value, dir, expected) in cases) {
            val car = Car().apply { ac[17] = value }
            assertEquals(ClimateResult.SENT, car.send(ClimateCmd.TEMP, dir))
            assertEquals(listOf(expected), car.writes)
        }
        for ((value, dir) in listOf(-40.0 to -1, 87.0 to 1)) {
            val car = Car().apply { ac[17] = value }
            assertEquals(ClimateResult.LIMIT, car.send(ClimateCmd.TEMP, dir))
            assertTrue(car.writes.isEmpty())
        }
    }

    @Test fun temperatureUsesOemWholeDegreeStepsAndRecognizesRawHi() {
        for ((raw, dir, expected) in listOf(Triple(87.5, -1, 10 to 14),
            Triple(21.5, 1, 10 to 4), Triple(21.5, -1, 10 to 2))) {
            val car = Car().apply { ac[17] = raw }
            assertEquals(ClimateResult.SENT, car.send(ClimateCmd.TEMP, dir))
            assertEquals(listOf(expected), car.writes)
        }
    }

    @Test fun manualTemperatureUsesPositionsAndNeverSendsLoHi() {
        for ((value, dir, expected) in listOf(Triple(18.0, 1, 1), Triple(31.0, -1, 12))) {
            val car = Car().apply { ac[27] = 0.0; ac[17] = value }
            assertEquals(ClimateResult.SENT, car.send(ClimateCmd.TEMP, dir))
            assertEquals(listOf(10 to expected), car.writes)
        }
        for ((value, dir) in listOf(18.0 to -1, 31.0 to 1)) {
            val car = Car().apply { ac[27] = 0.0; ac[17] = value }
            assertEquals(ClimateResult.LIMIT, car.send(ClimateCmd.TEMP, dir))
            assertTrue(car.writes.isEmpty())
        }
        val unknown = Car().apply { ac.remove(27) }
        assertEquals(ClimateResult.UNKNOWN, unknown.send(ClimateCmd.TEMP, 1))
        assertTrue(unknown.writes.isEmpty())
    }

    @Test fun fanDirectionsDifferAndOnlyDownIsBlockedAtZero() {
        for ((dir, expected) in listOf(-1 to 3, 1 to 5)) {
            val car = Car()
            assertEquals(ClimateResult.SENT, car.send(ClimateCmd.FAN_STEP, dir))
            assertEquals(listOf(11 to expected), car.writes)
        }
        val off = Car().apply { ac[12] = 0.0 }
        assertEquals(ClimateResult.FAN_OFF, off.send(ClimateCmd.FAN_STEP, -1))
        assertTrue(off.writes.isEmpty())
        assertEquals(ClimateResult.SENT, off.send(ClimateCmd.FAN_STEP, 1))
        assertEquals(listOf(11 to 5), off.writes)
    }

    @Test fun seatHeatAndVentilationAreOnePairedRequest() {
        val car = Car().apply { seats[49] = 1.0; seats[50] = 3.0 }
        assertEquals(ClimateResult.SENT, car.send(ClimateCmd.SEAT_HEAT_DRIVER))
        assertEquals(listOf(mapOf("ICE3_CMD_CTRLDRISEATHEAT_LEVEL" to 3f, "ICE3_CMD_CTRLDRISEATVTL_LEVEL" to 1f)), car.writes)
        val vent = Car().apply { seats[57] = 3.0 }
        assertEquals(ClimateResult.SENT, vent.send(ClimateCmd.SEAT_VENT_PASS))
        assertEquals(listOf(mapOf("ICE3_CMD_CTRLPASSSEATHEAT_LEVEL" to 1f, "ICE3_CMD_CTRLPASSSEATVTL_LEVEL" to 1f)), vent.writes)
    }

    @Test fun ice15ModelCodesUseModernFeedbackAndNames() {
        for (type in listOf(63, 66, 67, 68, 70, 71, 74)) {
            val car = Car().apply { cfg[0] = type; seats[99] = 2.0; seats[57] = 3.0 }
            assertEquals(ClimateResult.SENT, car.send(ClimateCmd.SEAT_VENT_PASS))
            assertEquals(listOf(mapOf("ICE15_Set_FRSHeat_Lvl" to 1f, "ICE15_Set_FRSVtl_Lvl" to 3f)), car.writes)
        }
    }

    @Test fun unavailableSeatsUnknownVehicleAndInvalidFeedbackNeverSend() {
        val cars = listOf(Car().apply { cfg[5] = 0 }, Car().apply { cfg.remove(5) },
            Car().apply { cfg.remove(0) }, Car().apply { cfg[0] = Int.MIN_VALUE },
            Car().apply { seats[49] = 4.0 }, Car().apply { cfg[0] = 63; seats[94] = 0.0 })
        for (car in cars) {
            assertNotEquals(ClimateResult.SENT, car.send(ClimateCmd.SEAT_HEAT_DRIVER))
            assertTrue(car.writes.isEmpty())
        }
    }

    @Test fun recirculationWithoutAqsTogglesBinaryState() {
        for ((value, expected) in listOf(0.0 to 1, 1.0 to 0)) {
            val car = Car().apply { ac[9] = value }
            assertEquals(ClimateResult.SENT, car.send(ClimateCmd.RECIRC))
            assertEquals(listOf(3 to expected), car.writes)
        }
    }

    @Test fun recirculationWithAqsCyclesOutsideInsideAndAutomatic() {
        for ((inlet, aqs, expected) in listOf(Triple(0.0, 0.0, 3 to 1),
            Triple(1.0, 0.0, 16 to 2), Triple(1.0, 1.0, 3 to 0))) {
            val car = Car().apply { cfg[27] = 1; ac[9] = inlet; ac[1] = aqs }
            assertEquals(ClimateResult.SENT, car.send(ClimateCmd.RECIRC))
            assertEquals(listOf(expected), car.writes)
        }
    }

    @Test fun unknownWheelCommandNeverWritesAndFailuresAreReported() {
        val car = Car()
        assertEquals(ClimateResult.UNSUPPORTED, car.send(ClimateCmd.WHEEL_HEAT))
        assertTrue(car.writes.isEmpty())
        car.accepted = false
        assertEquals(ClimateResult.FAILED, car.send(ClimateCmd.TEMP, 1))
        assertEquals(1, car.writes.size) // no retries of a toggle or seat request
    }
}

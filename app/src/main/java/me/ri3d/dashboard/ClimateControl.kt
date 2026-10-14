package me.ri3d.dashboard

/** Fresh reads and the two OEM write operations; implemented by the Binder connection. */
interface ClimatePort {
    fun climate(id: Int): Double?
    fun config(id: Int): Int?
    fun seat(id: Int): Double?
    fun sendClimate(id: Int, value: Int): Boolean
    fun sendSeats(values: Map<String, Float>): Boolean
}

enum class ClimateResult { SENT, DISABLED, DEMO, UNAVAILABLE, UNKNOWN, UNSUPPORTED, LIMIT, FAN_OFF, BUSY, FAILED }

/** Decoded OEM behavior. The gate is checked again after reads, immediately before a write. */
class ClimateControl(private val port: ClimatePort, private val blocked: () -> ClimateResult?) {
    fun send(cmd: ClimateCmd, direction: Int = 0): ClimateResult {
        blocked()?.let { return it }
        return when (cmd) {
            ClimateCmd.POWER -> toggle(3, 0, offMode = true)
            ClimateCmd.AUTO -> toggle(4, 1)
            ClimateCmd.AC -> toggle(2, 2)
            ClimateCmd.DEFROST_FRONT -> toggle(0, 5)
            ClimateCmd.DEFROST_REAR -> toggle(21, 6)
            ClimateCmd.RECIRC -> recirculation()
            ClimateCmd.TEMP -> temperature(direction)
            ClimateCmd.FAN_STEP -> fan(direction)
            ClimateCmd.SEAT_HEAT_DRIVER, ClimateCmd.SEAT_VENT_DRIVER,
            ClimateCmd.SEAT_HEAT_PASS, ClimateCmd.SEAT_VENT_PASS -> seat(cmd)
            ClimateCmd.WHEEL_HEAT, ClimateCmd.TEMP_HILO -> ClimateResult.UNSUPPORTED
        }
    }

    private fun toggle(read: Int, write: Int, offMode: Boolean = false): ClimateResult {
        val state = integer(port.climate(read), 0..1) ?: return ClimateResult.UNKNOWN
        return write(write, if (offMode) state else 1 - state)
    }

    private fun recirculation(): ClimateResult {
        val inlet = integer(port.climate(9), 0..1) ?: return ClimateResult.UNKNOWN
        val equipped = port.config(27)?.takeIf { it in 0..1 } ?: return ClimateResult.UNKNOWN
        if (equipped == 0) return write(3, 1 - inlet)
        val aqs = integer(port.climate(1), 0..1) ?: return ClimateResult.UNKNOWN
        return when {
            aqs == 1 -> write(3, 0)
            inlet == 0 -> write(3, 1)
            else -> write(16, 2) // outside -> recirculate -> AQS -> outside, like the OEM app
        }
    }

    private fun temperature(direction: Int): ClimateResult {
        if (direction != -1 && direction != 1) return ClimateResult.UNSUPPORTED
        val type = integer(port.climate(27), 0..1) ?: return ClimateResult.UNKNOWN
        val raw = port.climate(17) ?: return ClimateResult.UNKNOWN
        val current = temperatureValue(raw, type == 1)
        if (current == null) return ClimateResult.UNKNOWN
        val next = current + direction
        if (next !in (if (type == 1) 17..33 else 18..31)) return ClimateResult.LIMIT
        return when {
            type == 1 && next == 17 -> write(7, 1)
            type == 1 && next == 33 -> write(7, 2)
            else -> write(10, next - 18)
        }
    }

    private fun fan(direction: Int): ClimateResult {
        if (direction != -1 && direction != 1) return ClimateResult.UNSUPPORTED
        // OEM fan-up works even from off; down checks the current speed first.
        if (direction < 0) {
            val speed = integer(port.climate(12), 0..15) ?: return ClimateResult.UNKNOWN
            if (speed == 0) return ClimateResult.FAN_OFF
        }
        return write(11, if (direction > 0) 5 else 3)
    }

    private fun seat(cmd: ClimateCmd): ClimateResult {
        val spec = SeatControl.of(cmd) ?: return ClimateResult.UNSUPPORTED
        val vehicle = port.config(0)?.takeIf { it >= 0 } ?: return ClimateResult.UNKNOWN
        val equipped = port.config(spec.config)?.takeIf { it in 0..1 } ?: return ClimateResult.UNKNOWN
        if (equipped != 1) return ClimateResult.UNSUPPORTED
        val modern = SeatControl.modern(vehicle)
        val level = spec.level(port.seat(spec.readId(modern)), modern) ?: return ClimateResult.UNKNOWN
        val next = (level + 1) % 4
        val pair = SeatControl.values().filter { it.driver == spec.driver }
            .associate { it.key(modern) to if (it == spec) (next + 1).toFloat() else 1f }
        blocked()?.let { return it }
        return if (port.sendSeats(pair)) ClimateResult.SENT else ClimateResult.FAILED
    }

    private fun write(id: Int, value: Int): ClimateResult {
        blocked()?.let { return it }
        return if (port.sendClimate(id, value)) ClimateResult.SENT else ClimateResult.FAILED
    }

    companion object {
        /** OEM getDataIntValue truncates the half-degree raw signal, including 87.5 -> HI. */
        fun temperatureValue(raw: Double?, automatic: Boolean): Int? = when {
            raw == null || raw.isNaN() || raw.isInfinite() -> null
            automatic && raw == -40.0 -> 17
            automatic && raw in 87.0..87.5 -> 33
            raw >= 18.0 && raw < (if (automatic) 34.0 else 32.0) -> raw.toInt()
            automatic && raw >= 17.0 && raw < 18.0 -> 17
            else -> null
        }

        /** Reject NaN, fractional codes and sentinels instead of silently turning them into zero. */
        fun integer(value: Double?, range: IntRange): Int? = value?.takeIf {
            it >= range.first && it <= range.last && it == Math.floor(it)
        }?.toInt()
    }
}

/** Feedback, equipment and serialized enum names from the OEM front-seat controls. */
enum class SeatControl(val cmd: ClimateCmd, val config: Int, val driver: Boolean,
                       private val oldRead: Int, private val newRead: Int,
                       private val oldKey: String, private val newKey: String) {
    DRIVER_HEAT(ClimateCmd.SEAT_HEAT_DRIVER, 5, true, 49, 94,
        "ICE3_CMD_CTRLDRISEATHEAT_LEVEL", "ICE15_Set_FLSHeat_Lvl"),
    DRIVER_VENT(ClimateCmd.SEAT_VENT_DRIVER, 9, true, 50, 98,
        "ICE3_CMD_CTRLDRISEATVTL_LEVEL", "ICE15_Set_FLSVtl_Lvl"),
    PASS_HEAT(ClimateCmd.SEAT_HEAT_PASS, 6, false, 56, 95,
        "ICE3_CMD_CTRLPASSSEATHEAT_LEVEL", "ICE15_Set_FRSHeat_Lvl"),
    PASS_VENT(ClimateCmd.SEAT_VENT_PASS, 10, false, 57, 99,
        "ICE3_CMD_CTRLPASSSEATVTL_LEVEL", "ICE15_Set_FRSVtl_Lvl");

    fun readId(modern: Boolean) = if (modern) newRead else oldRead
    fun key(modern: Boolean) = if (modern) newKey else oldKey
    fun level(raw: Double?, modern: Boolean): Int? =
        ClimateControl.integer(raw, if (modern) 1..4 else 0..3)?.let { if (modern) it - 1 else it }

    companion object {
        fun of(cmd: ClimateCmd) = values().firstOrNull { it.cmd == cmd }
        fun modern(vehicleType: Int) = vehicleType in setOf(63, 66, 67, 68, 70, 71, 74)
    }
}

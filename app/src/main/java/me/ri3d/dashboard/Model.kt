package me.ri3d.dashboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap

/** The four mapped quick-launch roles plus the CanService app behind the vehicle widget. */
enum class Role(val key: String) {
    RADIO("radio"), AA("aa"), CALL("call"), FILES("files"), VEHICLE("vehicle");

    companion object {
        val QUICK = listOf(RADIO, AA, CALL, FILES)
        fun of(key: String) = values().firstOrNull { it.key == key }
    }
}

/**
 * Local persistence. Favorites are stored per layout as ordered keys: a role key
 * ("radio", "aa", "call", "files") or an app component ("pkg/.Activity").
 */
class Prefs(c: Context) {
    private val sp: SharedPreferences = c.getSharedPreferences("dashboard", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()
    var layoutB: Boolean
        get() = sp.getBoolean("layoutB", false)
        set(v) = sp.edit().putBoolean("layoutB", v).apply()
    var widget: Boolean
        get() = sp.getBoolean("widget", true)
        set(v) = sp.edit().putBoolean("widget", v).apply()
    /** Clock screen after this many seconds idle; 0 = never. */
    var idleSec: Int
        get() = sp.getInt("idle", 300)
        set(v) = sp.edit().putInt("idle", v).apply()
    var h24: Boolean
        get() = sp.getBoolean("h24", true)
        set(v) = sp.edit().putBoolean("h24", v).apply()
    var autoAA: Boolean
        get() = sp.getBoolean("autoAA", true)
        set(v) = sp.edit().putBoolean("autoAA", v).apply()
    var accent: Int
        get() = sp.getInt("accent", 0)
        set(v) = sp.edit().putInt("accent", v).apply()
    /** What each place of the vehicle widget shows (see [WidgetSlots]). */
    var widgetSlots: List<Metric>
        get() = WidgetSlots.parse(sp.getString("widgetSlots", null))
        set(v) = sp.edit().putString("widgetSlots", WidgetSlots.format(v)).apply()

    fun favorites(b: Boolean): List<String> {
        val raw = sp.getString(if (b) "favB" else "favA", null) ?: return DEFAULT
        return if (raw.isEmpty()) emptyList() else raw.split('\n')
    }

    fun setFavorites(b: Boolean, keys: List<String>) =
        sp.edit().putString(if (b) "favB" else "favA", keys.joinToString("\n")).apply()

    /** Explicit launch target for a role (component string), or null for auto-discovery. */
    fun mapping(r: Role): String? = sp.getString("map." + r.key, null)
    fun setMapping(r: Role, component: String?) = sp.edit().putString("map." + r.key, component).apply()

    /** Last known label of a target, so a removed app can still be named. */
    fun label(key: String): String? = sp.getString("label.$key", null)
    fun setLabel(key: String, label: String) {
        if (label(key) != label) sp.edit().putString("label.$key", label).apply()
    }

    companion object {
        val DEFAULT = Role.QUICK.map { it.key }
        fun max(b: Boolean) = if (b) 4 else 5
    }
}

/** Pure favorites operations; layout capacity is enforced here (unit-tested). */
object Favorites {
    fun add(list: List<String>, key: String, max: Int): List<String> =
        if (key in list || list.size >= max) list else list + key

    fun remove(list: List<String>, key: String) = list.filter { it != key }

    fun replace(list: List<String>, index: Int, key: String): List<String> =
        if (index !in list.indices || (key in list && list[index] != key)) list
        else list.mapIndexed { i, k -> if (i == index) key else k }

    fun move(list: List<String>, index: Int, dir: Int): List<String> {
        val j = index + dir
        if (index !in list.indices || j !in list.indices) return list
        val next = list.toMutableList()
        next[index] = list[j]; next[j] = list[index]
        return next
    }
}

// ---------------------------------------------------------------- vehicle

/**
 * Vehicle readings with availability and freshness per value. NaN = never reported or
 * rejected as malformed (a missing value is not zero). Door state is tracked per door with a
 * "known" mask, so an unreported door is never shown as closed.
 */
class VehicleState {
    enum class Reading(val min: Double, val max: Double) {
        OUTSIDE(-60.0, 80.0), CABIN(-40.0, 85.0), SET_TEMP(0.0, 40.0), FUEL(0.0, 100.0), SOC(0.0, 100.0),
        RANGE(0.0, 3000.0), SPEED(0.0, 300.0), ODOMETER(0.0, 2000000.0),
        BATTERY(0.0, 40.0), COOLANT(-60.0, 160.0), TRIP(0.0, 1000000.0)
    }
    enum class Door { FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT, TRUNK, HOOD }
    enum class Status { LIVE, DOOR_OPEN, STALE, NO_DATA }

    private val values = DoubleArray(Reading.values().size) { Double.NaN }
    private val at = LongArray(Reading.values().size)
    var doorsKnown = 0; private set
    var doorsOpen = 0; private set
    private var doorsAt = 0L
    /** Who supplies the data ("CanService" or the debug demo); null = no provider connected. */
    var source: String? = null

    fun put(r: Reading, v: Double?, now: Long) {
        val ok = v != null && !v.isNaN() && v >= r.min && v <= r.max
        values[r.ordinal] = if (ok) v!! else Double.NaN
        at[r.ordinal] = now
    }

    fun putDoor(d: Door, open: Boolean?, now: Long) {
        val bit = 1 shl d.ordinal
        if (open == null) { doorsKnown = doorsKnown and bit.inv(); doorsOpen = doorsOpen and bit.inv() }
        else { doorsKnown = doorsKnown or bit; doorsOpen = if (open) doorsOpen or bit else doorsOpen and bit.inv() }
        doorsAt = now
    }

    fun clear() {
        values.fill(Double.NaN); at.fill(0L); doorsKnown = 0; doorsOpen = 0; doorsAt = 0L
    }

    fun value(r: Reading): Double? = values[r.ordinal].takeUnless { it.isNaN() }
    fun fresh(r: Reading, now: Long) = value(r) != null && now - at[r.ordinal] < STALE_MS
    fun doorsFresh(now: Long) = doorsKnown != 0 && now - doorsAt < STALE_MS

    /** Doors reported open, in a fixed order; empty if none (or unknown). */
    fun openDoors(): List<Door> = Door.values().filter { doorsOpen and (1 shl it.ordinal) != 0 }
    /** True only when all four side doors are reported and none is open. */
    fun allClosed(): Boolean {
        val four = (1 shl 4) - 1
        return doorsKnown and four == four && doorsOpen == 0
    }

    fun status(now: Long): Status {
        val anyEver = values.any { !it.isNaN() } || doorsKnown != 0
        if (!anyEver) return Status.NO_DATA
        val anyFresh = Reading.values().any { fresh(it, now) } || doorsFresh(now)
        if (!anyFresh) return Status.STALE
        if (doorsFresh(now) && doorsOpen != 0) return Status.DOOR_OPEN
        return Status.LIVE
    }

    /** Milliseconds until the next fresh value turns stale (for one delayed refresh), or -1. */
    fun nextExpiry(now: Long): Long {
        var best = Long.MAX_VALUE
        for (r in Reading.values()) if (fresh(r, now)) best = minOf(best, at[r.ordinal] + STALE_MS - now)
        if (doorsFresh(now)) best = minOf(best, doorsAt + STALE_MS - now)
        return if (best == Long.MAX_VALUE) -1 else best
    }

    companion object {
        /** CanService logged a frame about every 3 s on the head unit; three missed frames = stale. */
        const val STALE_MS = 10_000L
    }
}

/**
 * What a place in the vehicle widget can show. [reading] null = door status; [percent] values
 * may drive the bar. Labels: [label] short (on the widget), [title] long (in the chooser).
 */
enum class Metric(val key: String, val reading: VehicleState.Reading?, val label: Int, val title: Int,
                  val unit: Int, val decimals: Boolean = false, val percent: Boolean = false) {
    OUTSIDE("outside", VehicleState.Reading.OUTSIDE, R.string.outside, R.string.mt_outside, R.string.unit_c),
    CABIN("cabin", VehicleState.Reading.CABIN, R.string.m_cabin, R.string.mt_cabin, R.string.unit_c),
    SET_TEMP("settemp", VehicleState.Reading.SET_TEMP, R.string.m_settemp, R.string.mt_settemp, R.string.unit_c, decimals = true),
    FUEL("fuel", VehicleState.Reading.FUEL, R.string.fuel, R.string.mt_fuel, R.string.unit_pct, percent = true),
    SOC("soc", VehicleState.Reading.SOC, R.string.m_soc, R.string.mt_soc, R.string.unit_pct, percent = true),
    RANGE("range", VehicleState.Reading.RANGE, R.string.range, R.string.mt_range, R.string.unit_km),
    SPEED("speed", VehicleState.Reading.SPEED, R.string.m_speed, R.string.mt_speed, R.string.unit_kmh),
    ODOMETER("odometer", VehicleState.Reading.ODOMETER, R.string.m_odometer, R.string.mt_odometer, R.string.unit_km),
    DOORS("doors", null, R.string.doors, R.string.mt_doors, 0),
    BATTERY("battery", VehicleState.Reading.BATTERY, R.string.battery, R.string.mt_battery, R.string.unit_v, decimals = true),
    COOLANT("coolant", VehicleState.Reading.COOLANT, R.string.coolant, R.string.mt_coolant, R.string.unit_c),
    TRIP("trip", VehicleState.Reading.TRIP, R.string.trip, R.string.mt_trip, R.string.unit_km, decimals = true);

    companion object {
        fun of(key: String) = values().firstOrNull { it.key == key }
    }
}

/**
 * The widget's places: large value, bar, the line below the bar and four tiles. Layout B shows
 * the first three and tiles 1-2, the clock screen the first three. Stored as comma-separated keys.
 */
object WidgetSlots {
    const val MAIN = 0
    const val BAR = 1
    const val BELOW = 2
    const val TILE1 = 3
    const val COUNT = 7
    val DEFAULT = listOf(Metric.OUTSIDE, Metric.FUEL, Metric.RANGE, Metric.DOORS, Metric.CABIN, Metric.SPEED, Metric.ODOMETER)

    fun allowed(slot: Int, m: Metric) = when (slot) {
        BAR -> m.percent
        MAIN, BELOW -> m.reading != null
        else -> true
    }

    /** Unknown keys or values not allowed in a place fall back to that place's default. */
    fun parse(raw: String?): List<Metric> {
        val keys = raw?.split(',') ?: return DEFAULT
        return List(COUNT) { i -> keys.getOrNull(i)?.let { Metric.of(it) }?.takeIf { allowed(i, it) } ?: DEFAULT[i] }
    }

    fun format(slots: List<Metric>) = slots.joinToString(",") { it.key }
}

// ---------------------------------------------------------------- phone and calls

enum class PhoneLink { UNKNOWN, NO_BLUETOOTH, NO_PERMISSION, OFF, NOT_PAIRED, PAIRED, CONNECTED, DISCONNECTED }

class PhoneState(val link: PhoneLink) {
    val connected get() = link == PhoneLink.CONNECTED
    /** Known to have no phone connected (as opposed to unknown). */
    val disconnected get() = link == PhoneLink.OFF || link == PhoneLink.NOT_PAIRED || link == PhoneLink.DISCONNECTED
}

enum class CallPhase { NONE, INCOMING, ACTIVE }

/**
 * Hands-free call as reported by a provider. [activeSince] is SystemClock.elapsedRealtime() of
 * the confirmed INCOMING -> ACTIVE transition; 0 = start unknown (no duration is shown).
 * [muted] null = microphone state not reported.
 */
class CallState(val phase: CallPhase = CallPhase.NONE, val caller: String? = null, val detail: String? = null,
                val activeSince: Long = 0L, val muted: Boolean? = null)

/** Verified call commands; null fields in Hub mean the command is not available. */
interface CallControl {
    val canAnswer: Boolean
    val canDecline: Boolean
    val canEnd: Boolean
    val canMute: Boolean
    fun answer()
    fun decline()
    fun end()
    fun setMuted(muted: Boolean)
}

// ---------------------------------------------------------------- radio

/** Radio metadata; every null field is "not reported by the provider". [position] e.g. "Station 2 of 12". */
class RadioInfo(val source: String? = null, val station: String? = null, val programme: String? = null,
                val position: String? = null, val playing: Boolean? = null, val art: Bitmap? = null)

interface RadioControl {
    val name: String
    fun prev()
    fun next()
    fun toggle()
}

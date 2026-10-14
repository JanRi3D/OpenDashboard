package me.ri3d.dashboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import me.ri3d.dashboard.VehicleState.Door
import me.ri3d.dashboard.VehicleState.Reading

/**
 * The head unit's CanService (signal catalog in docs/INTEGRATIONS.md §4):
 * package `com.adayo.canservice`, bind action `com.adayo.can.canservice.action`, binder
 * `com.adayo.canproxy.binder.service.ICanboxInterface`. Indexed read = interface token,
 * int signal ID, transact(code, flags 0), readException, readFloat; failures read as NaN.
 * The service has no callback in the catalog, so values are polled every 2 s on a background
 * thread while a screen is visible, and only those the screens show ([Hub.wanted]; doors always,
 * for the door-open warning); VehicleState marks them stale if polling stops working. Climate writes
 * require explicit Settings opt-in, an active panel and a live connection; commands are never retried.
 */
object CanService {
    const val PKG = "com.adayo.canservice"
    private const val ACTION = "com.adayo.can.canservice.action"
    private const val CANBOX = "com.adayo.canproxy.binder.service.ICanboxInterface"

    private const val TX_BODY_DETAILS = 6 // BodyDetailsInfo, float
    private const val TX_SPEED = 11       // vehicle speed, no ID argument, float

    /** BodyDetailsInfo signal IDs (catalog names in comments). */
    private val BODY = mapOf(
        Reading.FUEL to 13,       // FUEL_LEVEL_VALUE, 0-100 %
        Reading.RANGE to 12,      // IC2_STATUS_ENDURMILEAGEVALUE, km
        Reading.ODOMETER to 10,   // ODOMETER_VALUE, km
        Reading.SOC to 108,       // BMS_SOC, %
        Reading.GEAR to 1)        // SHIFT_LEVER_POSITION, raw code (see gearOf)
    /** Climate (IACInterface) IDs = ACProp ordinals; labels are inferred in the catalog. */
    private val CLIMATE = mapOf(
        Reading.OUTSIDE to 16,
        Reading.SET_TEMP to 17,
        Reading.AC_TYPE to 27,
        Reading.AQS to 1,
        Reading.CABIN to 38,
        Reading.DEFROST_FRONT to 0,  // AC1_STATUS_FRONTDEFROST_ACTVINACTV
        Reading.AC to 2,             // AC1_STATUS_ACREQ_ACTVINACTV, 1 = on
        Reading.CLIMATE_OFF to 3,    // AC1_STATUS_OFFMODE_ACTVINACTV, 1 = system off
        Reading.AUTO to 4,           // AC1_STATUS_AUTOMODE_ACTVINACTV
        Reading.RECIRC to 9,         // AC1_STATUS_AIRINLET_MODE
        Reading.FAN to 12,           // AC1_STATUS_BLOWERSPEED_LVL
        Reading.DEFROST_REAR to 21)  // BCM1_STATUS_DEFOGREAR_ONOFF
    /** Seat equipment and feedback family are selected from the current vehicle configuration. */
    private val SEATS = mapOf(
        Reading.SEAT_HEAT_DRIVER to SeatControl.DRIVER_HEAT,
        Reading.SEAT_VENT_DRIVER to SeatControl.DRIVER_VENT,
        Reading.SEAT_HEAT_PASS to SeatControl.PASS_HEAT,
        Reading.SEAT_VENT_PASS to SeatControl.PASS_VENT)
    /** Widget values CanService can supply; 12 V battery, coolant and trip are OBD-only. */
    val SUPPORTED = setOf(Metric.OUTSIDE, Metric.CABIN, Metric.SET_TEMP, Metric.FUEL, Metric.SOC,
        Metric.RANGE, Metric.SPEED, Metric.ODOMETER, Metric.DOORS)

    private val DOORS = linkedMapOf(
        Door.FRONT_LEFT to 7,     // BCM_DRIVERDOORST (left-hand drive)
        Door.FRONT_RIGHT to 6,    // BCM_PASSDOORST
        Door.REAR_LEFT to 5,      // BCM_RLDOORST
        Door.REAR_RIGHT to 4,     // BCM_RRDOORST
        Door.TRUNK to 3,          // BCM_LUGGAGEDOORST
        Door.HOOD to 2)           // BCM_FRONT_MACHINE_COVER

    private const val POLL_MS = 2000L

    enum class Link { NOT_INSTALLED, REFUSED, CONNECTING, CONNECTED, DISCONNECTED }

    @Volatile var link = Link.NOT_INSTALLED; private set
    var refusal: String? = null; private set
    /** Last raw door values ("FL 0 · FR 0 …"), shown in Apps & integrations to check the encoding in the car. */
    var rawDoors = ""; private set
    /** Last raw gear lever code, shown there too so P/R/N/D can be recorded on a drive. */
    var rawGear = "—"; private set
    /** null = configuration unknown; false = the car reports this equipment absent. */
    var seatSupport: Map<ClimateCmd, Boolean?> = emptyMap(); private set
    var climateBusy = false; private set

    @Volatile private var canbox: IBinder? = null
    @Volatile private var climatePort: CanClimatePort? = null
    private var bound = false
    @Volatile private var active = false
    @Volatile private var commandEpoch = 0L
    private val worker = Handler(HandlerThread("canservice").apply { start() }.looper)

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            canbox = service; climatePort = CanClimatePort(service); seatSupport = emptyMap()
            setLink(Link.CONNECTED)
            schedule(0)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // The binding stays and Android reconnects when CanService restarts; if it was
            // force-stopped instead, the binding is gone, so rebind after a while.
            canbox = null; climatePort = null; seatSupport = emptyMap()
            setLink(Link.DISCONNECTED)
            Hub.main.removeCallbacks(rebind)
            Hub.main.postDelayed(rebind, 30_000)
        }
    }

    private val rebind: Runnable = Runnable {
        if (link != Link.DISCONNECTED) return@Runnable
        try { Hub.app.unbindService(connection) } catch (e: IllegalArgumentException) { }
        bound = false
        start(Hub.app)
    }

    /** Binds once; called at start-up, after package changes and on screen-on. Idempotent. */
    fun start(c: Context) {
        if (bound) return
        val intent = Intent(ACTION).setPackage(PKG)
        try {
            bound = c.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            refusal = null
            setLink(if (bound) Link.CONNECTING else Link.NOT_INSTALLED)
        } catch (e: SecurityException) { // the service demands a permission this app does not hold
            refusal = e.message
            setLink(Link.REFUSED)
        }
    }

    /** Polls only while a screen is visible (activity resumed). */
    fun setActive(on: Boolean) {
        if (!on) cancelPendingClimateCommands()
        active = on
        if (on) schedule(0) else worker.removeCallbacks(poll)
    }

    /** Main-thread lifecycle boundary: a pending tap belongs only to the panel session that made it. */
    fun cancelPendingClimateCommands() { commandEpoch++ }

    /** Poll now (e.g. the climate panel just opened and needs its values). */
    fun refresh() { if (active) schedule(0) }

    /** Called on the main thread; fresh reads and the single write run on the polling worker. */
    fun command(cmd: ClimateCmd, direction: Int = 0, done: (ClimateResult) -> Unit) {
        val b = canbox
        val port = climatePort
        val epoch = commandEpoch
        fun blocked(): ClimateResult? = when {
            Hub.demo -> ClimateResult.DEMO
            !Hub.prefs.climateControls -> ClimateResult.DISABLED
            epoch != commandEpoch || !active || !Hub.climateOpen || b == null || b !== canbox || port == null || port !== climatePort -> ClimateResult.UNAVAILABLE
            else -> null
        }
        blocked()?.let { done(it); return }
        if (climateBusy) { done(ClimateResult.BUSY); return }
        climateBusy = true
        worker.post {
            val result = try { ClimateControl(port!!, ::blocked).send(cmd, direction) }
                catch (e: Exception) { ClimateResult.FAILED }
            Hub.main.post {
                done(result)
                // Allow the car's reported state to catch up; never queue or replay rapid taps.
                Hub.main.postDelayed({ climateBusy = false; refresh() }, if (result == ClimateResult.SENT) 700L else 0L)
            }
        }
    }

    private fun schedule(delay: Long) {
        worker.removeCallbacks(poll)
        if (active && canbox != null) worker.postDelayed(poll, delay)
    }

    private fun setLink(l: Link) {
        Hub.main.post {
            link = l
            // Gone for good (uninstalled or refused): old values must not linger as "stale".
            if ((l == Link.NOT_INSTALLED || l == Link.REFUSED) && !Hub.demo) { Hub.vehicle.clear(); Hub.vehicle.source = null }
            Hub.vehicleChanged()
        }
    }

    private val poll = Runnable {
        val b = canbox
        val port = climatePort
        if (b != null) {
            val wanted = Hub.wanted()
            val values = HashMap<Reading, Float>()
            val support = HashMap<ClimateCmd, Boolean?>()
            for ((r, id) in BODY) if (r in wanted) values[r] = read(b, CANBOX, TX_BODY_DETAILS, id)
            if (SEATS.keys.any { it in wanted }) {
                val type = port?.config(0)?.takeIf { it >= 0 }
                val modern = type?.let { SeatControl.modern(it) }
                for ((r, seat) in SEATS) if (r in wanted) {
                    val equipped = port?.config(seat.config)?.takeIf { it in 0..1 }
                    support[seat.cmd] = equipped?.let { it == 1 }
                    values[r] = if (modern != null && equipped == 1)
                        seat.level(port?.seat(seat.readId(modern)), modern)?.toFloat() ?: Float.NaN
                    else Float.NaN
                }
            }
            if (CLIMATE.keys.any { it in wanted }) {
                for ((r, id) in CLIMATE) if (r in wanted) values[r] = port?.climate(id)?.toFloat() ?: Float.NaN
            }
            if (Reading.SPEED in wanted) values[Reading.SPEED] = read(b, CANBOX, TX_SPEED, null)
            val doors = DOORS.mapValues { read(b, CANBOX, TX_BODY_DETAILS, it.value) }
            Hub.main.post {
                if (b === canbox && port === climatePort && !Hub.demo) {
                    if (support.isNotEmpty()) seatSupport = support
                    publish(values, doors)
                }
            }
        }
        schedule(POLL_MS)
    }

    /** Runs on the main thread, like every VehicleState write. */
    private fun publish(values: Map<Reading, Float>, doors: Map<Door, Float>) {
        if (Hub.demo) return
        val now = SystemClock.elapsedRealtime()
        val v = Hub.vehicle
        v.source = VehicleWidget.CAN
        // NaN (failed read) leaves the previous value to age into "stale" instead of erasing it.
        for ((r, x) in values) if (!x.isNaN()) v.put(r, x.toDouble(), now)
        for ((door, x) in doors) if (!x.isNaN()) v.putDoor(door, doorOpen(x), now)
        rawDoors = doors.entries.joinToString(" · ") { (d, x) -> doorLabel(d) + " " + if (x.isNaN()) "—" else fmt(x) }
        values[Reading.GEAR]?.let { rawGear = if (it.isNaN()) "—" else fmt(it) }
        Hub.vehicleChanged()
    }

    /**
     * Door status encoding is not in the catalog. 0 = closed, 1 = open is the usual BCM
     * convention and is assumed here; any other value counts as unknown, never as closed.
     * Verify in the car with the raw values in Settings › Apps & integrations.
     */
    fun doorOpen(x: Float): Boolean? = when (x) {
        0f -> false
        1f -> true
        else -> null
    }

    private fun doorLabel(d: Door) = when (d) {
        Door.FRONT_LEFT -> "FL"; Door.FRONT_RIGHT -> "FR"; Door.REAR_LEFT -> "RL"
        Door.REAR_RIGHT -> "RR"; Door.TRUNK -> "boot"; Door.HOOD -> "bonnet"
    }

    private fun fmt(x: Float) = if (x == Math.floor(x.toDouble()).toFloat()) x.toInt().toString() else x.toString()

    /** One float read; [id] null for the standalone reads without a signal-ID argument (speed). */
    private fun read(b: IBinder, descriptor: String, code: Int, id: Int?): Float {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            if (id != null) data.writeInt(id)
            if (!b.transact(code, data, reply, 0)) return Float.NaN
            reply.readException()
            reply.readFloat()
        } catch (e: Exception) {
            Float.NaN
        } finally {
            data.recycle(); reply.recycle()
        }
    }
}

/**
 * Climate and seat write commands found in the head unit's own climate app (docs/CANSERVICE-CATALOG.md,
 * "Write commands"). [ClimateControl] uses IACInterface tx 3, or a paired heat/ventilation map
 * via ICanboxInterface tx 15 for seats. The IDs below are the ICE3 defaults; seat commands select
 * their family from ConfigInfo. Steering-wheel heat is deliberately unsupported.
 */
enum class ClimateCmd(val canbox: Boolean, val id: Int) {
    POWER(false, 0), AUTO(false, 1), AC(false, 2), RECIRC(false, 3), DEFROST_FRONT(false, 5), DEFROST_REAR(false, 6),
    TEMP_HILO(false, 7), TEMP(false, 10), FAN_STEP(false, 11),
    SEAT_HEAT_DRIVER(true, 14), SEAT_VENT_DRIVER(true, 15), SEAT_HEAT_PASS(true, 55), SEAT_VENT_PASS(true, 56),
    WHEEL_HEAT(true, 120)
}

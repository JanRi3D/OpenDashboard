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
 * thread while a screen is visible, and only those the widget shows (doors always, for the
 * door-open warning); VehicleState marks them stale if polling stops working.
 */
object CanService {
    const val PKG = "com.adayo.canservice"
    private const val ACTION = "com.adayo.can.canservice.action"
    private const val CANBOX = "com.adayo.canproxy.binder.service.ICanboxInterface"
    private const val AC = "com.adayo.canproxy.binder.service.IACInterface"

    private const val TX_BODY_DETAILS = 6 // BodyDetailsInfo, float
    private const val TX_AC_BINDER = 1    // child IACInterface binder
    private const val TX_SPEED = 11       // vehicle speed, no ID argument, float
    private const val TX_AC_GET = 4       // IACInterface indexed float

    /** BodyDetailsInfo signal IDs (catalog names in comments). */
    private val BODY = mapOf(
        Reading.FUEL to 13,       // FUEL_LEVEL_VALUE, 0-100 %
        Reading.RANGE to 12,      // IC2_STATUS_ENDURMILEAGEVALUE, km
        Reading.ODOMETER to 10,   // ODOMETER_VALUE, km
        Reading.SOC to 108)       // BMS_SOC, %
    /** Climate (IACInterface) IDs; labels are inferred in the catalog. */
    private val CLIMATE = mapOf(
        Reading.OUTSIDE to 16,
        Reading.SET_TEMP to 17,
        Reading.CABIN to 38)
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

    @Volatile private var canbox: IBinder? = null
    @Volatile private var ac: IBinder? = null
    private var bound = false
    private var active = false
    private val worker = Handler(HandlerThread("canservice").apply { start() }.looper)

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            canbox = service; ac = null
            setLink(Link.CONNECTED)
            schedule(0)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // The binding stays and Android reconnects when CanService restarts; if it was
            // force-stopped instead, the binding is gone, so rebind after a while.
            canbox = null; ac = null
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
        active = on
        if (on) schedule(0) else worker.removeCallbacks(poll)
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
        if (b != null) {
            val wanted = Hub.prefs.widgetSlots.mapNotNull { it.reading }.toSet()
            val values = HashMap<Reading, Float>()
            for ((r, id) in BODY) if (r in wanted) values[r] = read(b, CANBOX, TX_BODY_DETAILS, id)
            if (CLIMATE.keys.any { it in wanted }) {
                val climate = acBinder(b)
                for ((r, id) in CLIMATE) if (r in wanted) values[r] = climate?.let { read(it, AC, TX_AC_GET, id) } ?: Float.NaN
            }
            if (Reading.SPEED in wanted) values[Reading.SPEED] = read(b, CANBOX, TX_SPEED, null)
            val doors = DOORS.mapValues { read(b, CANBOX, TX_BODY_DETAILS, it.value) }
            Hub.main.post { publish(values, doors) }
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

    private fun acBinder(b: IBinder): IBinder? {
        ac?.let { if (it.isBinderAlive) return it }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(CANBOX)
            b.transact(TX_AC_BINDER, data, reply, 0)
            reply.readException()
            reply.readStrongBinder().also { ac = it }
        } catch (e: Exception) { // RemoteException, SecurityException, malformed reply
            null
        } finally {
            data.recycle(); reply.recycle()
        }
    }

    /** One float read; [id] null for the standalone reads without a signal-ID argument (speed). */
    private fun read(b: IBinder, descriptor: String, code: Int, id: Int?): Float {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            if (id != null) data.writeInt(id)
            b.transact(code, data, reply, 0)
            reply.readException()
            reply.readFloat()
        } catch (e: Exception) {
            Float.NaN
        } finally {
            data.recycle(); reply.recycle()
        }
    }
}

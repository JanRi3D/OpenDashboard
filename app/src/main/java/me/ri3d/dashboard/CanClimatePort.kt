package me.ri3d.dashboard

import android.os.IBinder
import android.os.Parcel
import com.adayo.canproxy.bean.VehicleSettingType

/** Exact Binder wire format from the supplied AIDL stubs; called only on CanService's worker. */
class CanClimatePort(private val canbox: IBinder) : ClimatePort {
    private var ac: IBinder? = null

    private fun climateBinder(): IBinder? {
        ac?.let { if (it.isBinderAlive) return it }
        return transact(canbox, CANBOX, 1, {}, { it.readStrongBinder() }, replyBytes = 4).also { ac = it }
    }

    override fun climate(id: Int): Double? = climateBinder()?.let { binder ->
        transact(binder, AC, 4, { it.writeInt(id) }, { it.readFloat().toDouble() }, replyBytes = 4)
    }?.takeUnless { it.isNaN() || it.isInfinite() }

    override fun seat(id: Int): Double? =
        transact(canbox, CANBOX, 7, { it.writeInt(id) }, { it.readFloat().toDouble() }, replyBytes = 4)
            ?.takeUnless { it.isNaN() || it.isInfinite() }

    override fun config(id: Int): Int? =
        transact(canbox, CANBOX, 10, { it.writeInt(id) }, { it.readInt() }, replyBytes = 4)?.takeUnless { it == Int.MIN_VALUE }

    override fun sendClimate(id: Int, value: Int): Boolean {
        val binder = climateBinder() ?: return false
        return transact(binder, AC, 3, { it.writeInt(id); it.writeInt(value) }, { true }) == true
    }

    override fun sendSeats(values: Map<String, Float>): Boolean {
        // Parcel.writeMap uses Parcelable enum keys, and the OEM receiver casts each value to Float.
        val wire = values.mapKeys { VehicleSettingType.valueOf(it.key) }
        return transact(canbox, CANBOX, 15, { it.writeMap(wire) }, { true }) == true
    }

    private fun <T> transact(b: IBinder, descriptor: String, code: Int,
                             args: (Parcel) -> Unit, result: (Parcel) -> T, replyBytes: Int = 0): T? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            args(data)
            if (!b.transact(code, data, reply, 0) || reply.dataAvail() < 4) null
            else {
                reply.readException()
                // Android 4.x returns zero for some reads beyond the end of a Parcel.
                if (reply.dataAvail() < replyBytes) null else result(reply)
            }
        } catch (e: Exception) {
            null // disconnect, refusal or malformed reply; never retry a write
        } finally {
            data.recycle(); reply.recycle()
        }
    }

    companion object {
        const val CANBOX = "com.adayo.canproxy.binder.service.ICanboxInterface"
        const val AC = "com.adayo.canproxy.binder.service.IACInterface"
    }
}

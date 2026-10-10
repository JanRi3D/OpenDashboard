package me.ri3d.dashboard

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.KeyEvent

/**
 * Phone link through the public Bluetooth API (API 5+): bonded devices, adapter state and ACL
 * connect/disconnect broadcasts. Android 4.x has no public "is this device connected" call, so
 * at start-up a paired phone is reported as PAIRED (connection unknown) until an event arrives.
 */
object Bluetooth {
    private var adapter: BluetoothAdapter? = null
    private val connected = HashSet<String>()
    private var sawDisconnect = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            @Suppress("DEPRECATION") // the typed overload is API 33
            val d: BluetoothDevice? = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            when (i.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> if (d != null && isPhone(d)) { connected.add(d.address); publish(true) }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (d != null && (connected.remove(d.address) || isPhone(d))) {
                    sawDisconnect = true; publish(true)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> { if (adapter?.isEnabled != true) connected.clear(); publish(true) }
                else -> publish(false)
            }
        }
    }

    fun start(c: Context) {
        @Suppress("DEPRECATION") // BluetoothManager.getAdapter() is API 18
        adapter = try { BluetoothAdapter.getDefaultAdapter() } catch (e: RuntimeException) { null }
        if (adapter != null) {
            c.registerReceiver(receiver, IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED); addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            })
        }
        publish(false)
    }

    fun query() = publish(false)

    private fun isPhone(d: BluetoothDevice): Boolean = try {
        val cls = d.bluetoothClass
        cls == null || cls.majorDeviceClass == BluetoothClass.Device.Major.PHONE
    } catch (e: SecurityException) { false }

    private fun state(): PhoneState {
        val a = adapter ?: return PhoneState(PhoneLink.NO_BLUETOOTH)
        return try {
            if (!a.isEnabled) return PhoneState(PhoneLink.OFF)
            val phones = a.bondedDevices.orEmpty().filter { isPhone(it) }
            when {
                phones.any { it.address in connected } -> PhoneState(PhoneLink.CONNECTED)
                a.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED ||
                    a.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED ->
                    PhoneState(PhoneLink.CONNECTED)
                phones.isEmpty() -> PhoneState(PhoneLink.NOT_PAIRED)
                sawDisconnect -> PhoneState(PhoneLink.DISCONNECTED)
                else -> PhoneState(PhoneLink.PAIRED)
            }
        } catch (e: SecurityException) { // BLUETOOTH_CONNECT not granted (API 31+)
            PhoneState(PhoneLink.NO_PERMISSION)
        }
    }

    private fun publish(fromEvent: Boolean) {
        if (Hub.demo) return
        Hub.setPhone(state(), fromEvent)
    }
}

/**
 * WELLE radio commands. Evidence: WELLE's manifest exports `me.ri3d.welle.MediaButtonReceiver`
 * for `android.intent.action.MEDIA_BUTTON` without a permission; its source maps
 * MEDIA_NEXT / MEDIA_PREVIOUS / MEDIA_PLAY_PAUSE to RadioService next / prev / togglePlay
 * (next/prev step through the current source's station list).
 *
 * Now playing: WELLE builds with RadioService.ACTION_STATE send a sticky broadcast
 * `me.ri3d.welle.STATE` (station, text, playing, index/count, preset, art URI + version, the
 * picture served read-only by WELLE's ArtProvider). Older WELLE builds send nothing (their
 * MediaSession is API 21+), so station, picture and play state then stay unknown.
 */
object Welle : RadioControl {
    private const val RECEIVER = "me.ri3d.welle.MediaButtonReceiver"
    private const val ACTION_STATE = "me.ri3d.welle.STATE"
    override val name = "WELLE"
    private var listening = false
    private var last: Intent? = null
    private var artKey = ""
    private var art: Bitmap? = null

    private val states = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { last = i; show() }
    }

    private fun show() {
        val i = last
        if (Hub.demo || Hub.radioControl !== this || i == null) return
        val a = Hub.app
        val count = i.getIntExtra("count", 0)
        val index = i.getIntExtra("index", 0)
        val preset = i.getIntExtra("preset", 0)
        val text = i.getStringExtra("text")?.trim().orEmpty().ifEmpty { i.getStringExtra("status")?.trim().orEmpty() }
        loadArt(i.getStringExtra("art").orEmpty(), i.getLongExtra("artVersion", 0L))
        Hub.radio = RadioInfo(
            source = a.getString(if (i.getStringExtra("source") == "web") R.string.radio_source_web else R.string.radio_source_dab, name),
            station = if (count == 0) a.getString(R.string.radio_no_stations) else i.getStringExtra("station")?.trim()?.ifEmpty { null },
            programme = if (count == 0) a.getString(R.string.radio_no_stations_hint, name) else text.ifEmpty { null },
            position = when {
                index <= 0 || count <= 0 -> null
                preset > 0 -> a.getString(R.string.preset_station_of, preset, index, count)
                else -> a.getString(R.string.station_of, index, count)
            },
            playing = i.getBooleanExtra("playing", false),
            art = art)
        Hub.changed(Hub.RADIO)
    }

    /** Loads the station picture off the main thread when its URI or version changes. */
    private fun loadArt(uri: String, version: Long) {
        val key = "$uri|$version"
        if (key == artKey) return
        artKey = key
        art = null
        if (uri.isEmpty()) return
        Thread({
            val b = decode(Uri.parse(uri))
            Hub.main.post { if (artKey == key && b != null) { art = b; show() } }
        }, "welle-art").start()
    }

    /** Bounded decode: about twice the 132-unit art box at most, whatever WELLE stored. */
    private fun decode(uri: Uri): Bitmap? = try {
        val cr = Hub.app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        var s = cr.openInputStream(uri)
        try { BitmapFactory.decodeStream(s, null, bounds) } finally { s?.close() }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 264) sample *= 2
        s = cr.openInputStream(uri)
        try { BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = sample }) } finally { s?.close() }
    } catch (e: Exception) { // provider missing or file gone: no picture
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Re-evaluated after every app refresh and mapping change; the demo may replace it. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // the flag exists from API 33 and is passed there
    fun bind() {
        if (Hub.demo) return
        val target = Hub.apps.target(Role.RADIO)
        val ok = target?.pkg == AppIndex.WELLE && try {
            Hub.app.packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(AppIndex.WELLE), 0)
                .any { it.activityInfo?.name == RECEIVER }
        } catch (e: RuntimeException) { false }
        Hub.radioControl = if (ok) this else null
        Hub.radio = null
        if (ok && !listening) {
            listening = true
            // The current sticky state (if WELLE has run since boot) is delivered right away.
            val f = IntentFilter(ACTION_STATE)
            if (Build.VERSION.SDK_INT >= 33) Hub.app.registerReceiver(states, f, Context.RECEIVER_EXPORTED)
            else Hub.app.registerReceiver(states, f)
        }
        show()
    }

    private fun send(code: Int) {
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            try {
                Hub.app.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(ComponentName(AppIndex.WELLE, RECEIVER))
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, code)))
            } catch (e: RuntimeException) { return }
        }
    }

    override fun prev() = send(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    override fun next() = send(KeyEvent.KEYCODE_MEDIA_NEXT)
    override fun toggle() = send(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
}

/**
 * Next turn from OpenAuto. OpenAuto builds with the navigation channel pass on what Android Auto sends
 * an instrument cluster as the sticky broadcast `me.ri3d.openauto.NAV`: `status` (active / rerouting /
 * inactive), `road`, `maneuver` + `direction` (aasdk enums), `image` (Maps' PNG arrow), `meters`,
 * `distance` (displayed value x 1000) + `unit`. Older builds send nothing: no turns are shown.
 */
object OpenAutoNav {
    private const val ACTION = "me.ri3d.openauto.NAV"
    private var last: Intent? = null
    private var png: ByteArray? = null
    private var image: Bitmap? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { last = i; show() }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // the flag exists from API 33 and is passed there
    fun start(c: Context) {
        val f = IntentFilter(ACTION) // the current sticky state (a route already running) arrives right away
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED) else c.registerReceiver(receiver, f)
    }

    /** Also re-run when the setting changes. */
    fun show() {
        val i = last
        val status = i?.getStringExtra("status")
        val g = if (i == null || !Hub.prefs.guidance || (status != "active" && status != "rerouting")) null else Guidance(
            rerouting = status == "rerouting",
            road = i.getStringExtra("road")?.trim()?.ifEmpty { null },
            distance = Guidance.distance(i.getIntExtra("meters", -1), i.getIntExtra("distance", 0), i.getIntExtra("unit", 0)),
            glyph = Guidance.glyph(i.getIntExtra("maneuver", 0), i.getIntExtra("direction", 0)),
            image = decode(i.getByteArrayExtra("image")))
        // Distance events come every second; screens re-render only when what they show changes.
        if (g == Hub.guidance) return
        Hub.guidance = g
        Hub.changed(Hub.GUIDANCE)
    }

    /** Decoded once per new picture; anything that is not a small image is ignored. */
    private fun decode(b: ByteArray?): Bitmap? {
        if (b == null || b.isEmpty()) return null
        if (b.contentEquals(png)) return image
        png = b
        image = try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(b, 0, b.size, o)
            // Opaque as sent; with alpha the screens can turn its black into transparency (turnIcon).
            if (o.outWidth in 1..1024 && o.outHeight in 1..1024)
                BitmapFactory.decodeByteArray(b, 0, b.size)?.copy(Bitmap.Config.ARGB_8888, false)?.apply { setHasAlpha(true) }
            else null
        } catch (e: OutOfMemoryError) {
            null
        }
        return image
    }
}

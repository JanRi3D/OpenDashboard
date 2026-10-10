package me.ri3d.dashboard

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        F.load(this)
        Hub.init(this)
    }
}

/**
 * The one authoritative application state shared by every screen: preferences, installed
 * apps and the provider states. Providers write here; the activity listens and re-renders.
 * All writes happen on the main thread.
 */
object Hub {
    const val CONFIG = 1; const val APPS = 2; const val VEHICLE = 4; const val PHONE = 8
    const val CALL = 16; const val RADIO = 32; const val GUIDANCE = 64

    lateinit var app: Application; private set
    lateinit var prefs: Prefs; private set
    @SuppressLint("StaticFieldLeak") // holds the Application context only
    lateinit var apps: AppIndex; private set
    val main = Handler(Looper.getMainLooper())

    val vehicle = VehicleState()
    var phone = PhoneState(PhoneLink.UNKNOWN); private set
    var call = CallState(); private set
    /** null = no verified call commands (production on this head unit). */
    var callControl: CallControl? = null
    /** null = no provider reports metadata (WELLE on Android 4.x). */
    var radio: RadioInfo? = null
    var radioControl: RadioControl? = null
    /** null = no route running in Android Auto (or the setting is off). */
    var guidance: Guidance? = null
    /** Set by the debug-only demo fixtures; real providers stay quiet while it is on. */
    var demo = false

    /** Screen stack; survives activity recreation, not process death. */
    val nav = ArrayList<Screen>()

    private val listeners = ArrayList<(Int) -> Unit>()
    private var pending = 0
    private val dispatch = Runnable {
        val k = pending; pending = 0
        for (l in listeners.toList()) l(k)
    }

    fun init(a: Application) {
        app = a
        prefs = Prefs(a)
        apps = AppIndex(a)
        apps.refresh()
        Bluetooth.start(a)
        CanService.start(a)
        OpenAutoNav.start(a)
        // Installs, removals and updates: refresh so "Not installed" tiles recover by themselves.
        val pkgs = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val onPackages = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { apps.refresh(); CanService.start(c) }
        }
        a.registerReceiver(onPackages, pkgs)
        val media = IntentFilter().apply {
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE); addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
            addAction(Intent.ACTION_SCREEN_ON) // head unit wake: re-read providers
        }
        a.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                apps.refresh(); Bluetooth.query(); CanService.start(c)
            }
        }, media)
    }

    fun listen(l: (Int) -> Unit) { listeners.add(l) }
    fun unlisten(l: (Int) -> Unit) { listeners.remove(l) }

    /** Coalesces changes into one main-thread dispatch. */
    fun changed(kind: Int) {
        pending = pending or kind
        main.removeCallbacks(dispatch)
        main.post(dispatch)
    }

    // ------------------------------------------------------------ dashboard configuration

    val layoutB get() = prefs.layoutB
    val max get() = Prefs.max(layoutB)
    fun favorites(): List<String> = prefs.favorites(layoutB)
    fun setFavorites(keys: List<String>) { prefs.setFavorites(layoutB, keys.take(max)); changed(CONFIG) }

    fun isOnDashboard(e: AppEntry) = favorites().any { apps.resolve(it) == e.key }
    fun isWidgetApp(e: AppEntry) = e.key == apps.targetKey(Role.VEHICLE)

    /** Add or remove an app from the current layout's quick slots. */
    fun toggleFavorite(e: AppEntry) {
        val favs = favorites()
        val existing = favs.firstOrNull { apps.resolve(it) == e.key }
        setFavorites(if (existing != null) Favorites.remove(favs, existing) else Favorites.add(favs, apps.favoriteKeyFor(e), max))
    }

    /** Reset restores this layout's default quick apps and the widget, nothing else. */
    fun resetLayout() {
        prefs.setFavorites(layoutB, Prefs.DEFAULT)
        prefs.widget = true
        changed(CONFIG)
    }

    // ------------------------------------------------------------ provider writes

    fun setPhone(p: PhoneState, fromEvent: Boolean) {
        val was = phone
        phone = p
        changed(PHONE)
        // Auto-start Android Auto once per observed disconnected -> connected transition.
        if (fromEvent && !was.connected && p.connected) maybeStartAndroidAuto()
    }

    private fun maybeStartAndroidAuto() {
        if (!prefs.autoAA || !prefs.onboarded) return
        if (call.phase != CallPhase.NONE) return // calls take priority
        val now = SystemClock.elapsedRealtime()
        if (now - lastAutoStart < 30_000) return // duplicate connection events
        lastAutoStart = now
        if (!apps.launch(app, apps.targetKey(Role.AA))) {
            Toast.makeText(app, app.getString(R.string.aa_auto_missing), Toast.LENGTH_LONG).show()
        }
    }
    private var lastAutoStart = -60_000L

    fun setCall(c: CallState) {
        val was = call
        // Duration runs from the confirmed INCOMING -> ACTIVE transition on the monotonic clock.
        call = if (c.phase == CallPhase.ACTIVE && c.activeSince == 0L && was.phase == CallPhase.INCOMING)
            CallState(c.phase, c.caller, c.detail, SystemClock.elapsedRealtime(), c.muted)
        else if (c.phase == CallPhase.ACTIVE && c.activeSince == 0L && was.phase == CallPhase.ACTIVE)
            CallState(c.phase, c.caller, c.detail, was.activeSince, c.muted)
        else c
        changed(CALL)
    }

    fun vehicleChanged() {
        changed(VEHICLE)
        main.removeCallbacks(expiry)
        val ms = vehicle.nextExpiry(SystemClock.elapsedRealtime())
        if (ms >= 0) main.postDelayed(expiry, ms + 50)
    }
    private val expiry = Runnable { vehicleChanged() }
}

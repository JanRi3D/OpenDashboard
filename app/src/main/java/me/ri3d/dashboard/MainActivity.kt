package me.ri3d.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { WELCOME, DASH, APPS, CLOCK, SETTINGS, INTEGRATIONS, EDIT, WIDGET }

sealed class Overlay {
    class Hold(val index: Int) : Overlay()
    /** Replace tile [index], or (index < 0) choose the launch target of [role]. */
    class Picker(val index: Int, val role: Role? = null) : Overlay()
    class Missing(val index: Int) : Overlay()
    /** Choose what a place of the vehicle widget shows. */
    class MetricPicker(val slot: Int) : Overlay()
}

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var screenLayer: FrameLayout
    lateinit var dialogLayer: FrameLayout
    private lateinit var callLayer: FrameLayout
    private lateinit var toastLayer: FrameLayout

    var overlay: Overlay? = null
        private set
    /** Partial updates registered by the current screen. */
    val onTime = ArrayList<() -> Unit>()
    val onVehicle = ArrayList<() -> Unit>()
    val onTick = ArrayList<() -> Unit>()

    // Per-screen view state that must survive re-renders.
    var appsOnlyDash = false
    var appsQuery = ""
    var welcomeB = false

    private var resumed = false
    private var idlePending = false

    val screen: Screen get() = Hub.nav.lastOrNull() ?: Screen.DASH

    // ------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Hub.nav.isEmpty()) {
            Hub.nav.add(if (Hub.prefs.onboarded) Screen.DASH else Screen.WELCOME)
            welcomeB = Hub.prefs.layoutB
        }
        root = object : FrameLayout(this) {
            override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
                super.onSizeChanged(w, h, ow, oh)
                val cw = w - paddingLeft - paddingRight
                val ch = h - paddingTop - paddingBottom
                if (cw > 0 && ch > 0) { Ui.setWindow(cw, ch); post { render() } }
            }
        }
        root.setBackgroundColor(C.BG)
        screenLayer = root.add(frame(), MATCH, MATCH)
        dialogLayer = root.add(frame(), MATCH, MATCH)
        callLayer = root.add(frame(), MATCH, MATCH)
        toastLayer = root.add(frame(), MATCH, MATCH)
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 28) avoidCutout()
        if (Build.VERSION.SDK_INT >= 33) registerBack()
    }

    @TargetApi(28)
    private fun avoidCutout() {
        root.setOnApplyWindowInsetsListener { v, insets ->
            val c = insets.displayCutout
            v.setPadding(c?.safeInsetLeft ?: 0, c?.safeInsetTop ?: 0, c?.safeInsetRight ?: 0, c?.safeInsetBottom ?: 0)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        Hub.listen(hubListener)
        Hub.apps.refresh()
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Bluetooth.query()
    }

    override fun onStop() {
        Hub.unlisten(hubListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        fullscreen()
        registerReceiver(timeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
        })
        updateTime()
        restartIdle()
        tick.run()
        CanService.setActive(true)
    }

    override fun onPause() {
        CanService.setActive(false)
        resumed = false
        unregisterReceiver(timeReceiver)
        Hub.main.removeCallbacks(idle); idlePending = false
        Hub.main.removeCallbacks(tick)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fullscreen()
    }

    /** Home pressed while OpenDashboard is the launcher: back to the dashboard. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) && Hub.prefs.onboarded) {
            overlay = null
            Hub.nav.clear(); Hub.nav.add(Screen.DASH)
            render()
        }
    }

    /** Dialog first, then the screen stack; true if consumed. */
    private fun handleBack(): Boolean {
        when {
            overlay != null -> closeOverlay()
            Hub.nav.size > 1 -> back()
            isDefaultHome() -> Unit // the launcher has nowhere to go back to
            else -> return false
        }
        return true
    }

    @Deprecated("API 16-32 back key; API 33+ uses the OnBackInvokedCallback registered in onCreate.")
    @SuppressLint("GestureBackNavigation") // gestures on 33+ go through registerBack()
    override fun onBackPressed() {
        @Suppress("DEPRECATION") if (!handleBack()) super.onBackPressed()
    }

    @TargetApi(33)
    private fun registerBack() {
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            if (!handleBack()) moveTaskToBack(true)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        restartIdle()
    }

    private fun isDefaultHome(): Boolean = try {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName
    } catch (e: RuntimeException) { false }

    fun homeAppLabel(): String? = try {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.let { if (it.activityInfo.packageName == "android") null else it.loadLabel(packageManager).toString() }
    } catch (e: RuntimeException) { null }

    /** API 16-18: the theme's FLAG_FULLSCREEN hides the status bar; 19+: sticky immersive (swipe reveals the bars). */
    @Suppress("DEPRECATION")
    private fun fullscreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else if (Build.VERSION.SDK_INT >= 19) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    // ------------------------------------------------------------ rendering

    private val hubListener: (Int) -> Unit = { k ->
        val deps = when (screen) {
            Screen.DASH, Screen.CLOCK -> Hub.CONFIG or Hub.APPS or Hub.PHONE or Hub.CALL or Hub.RADIO
            Screen.APPS, Screen.EDIT -> Hub.CONFIG or Hub.APPS
            Screen.WIDGET -> Hub.CONFIG
            Screen.SETTINGS -> Hub.CONFIG
            Screen.INTEGRATIONS -> Hub.CONFIG or Hub.APPS or Hub.PHONE or Hub.VEHICLE or Hub.RADIO
            Screen.WELCOME -> Hub.APPS or Hub.VEHICLE
        }
        if (k and deps != 0) render()
        else {
            if (k and Hub.VEHICLE != 0) for (f in onVehicle) f()
            if (k and Hub.CALL != 0) renderCall()
            if (k and (Hub.APPS or Hub.CONFIG) != 0 && overlay != null) renderOverlay()
        }
    }

    fun render() {
        if (root.width == 0) return
        onTime.clear(); onVehicle.clear(); onTick.clear()
        screenLayer.removeAllViews()
        screenLayer.add(when (screen) {
            Screen.WELCOME -> welcome()
            Screen.DASH -> dashboard()
            Screen.APPS -> appsScreen()
            Screen.CLOCK -> clockScreen()
            Screen.SETTINGS -> settingsScreen()
            Screen.INTEGRATIONS -> integrationsScreen()
            Screen.EDIT -> editor()
            Screen.WIDGET -> widgetScreen()
        }, MATCH, MATCH)
        renderOverlay()
        renderCall()
        updateTime()
        for (f in onVehicle) f()
        if (!idleAllowed()) { Hub.main.removeCallbacks(idle); idlePending = false } else if (!idlePending) restartIdle()
        Hub.main.removeCallbacks(tick); tick.run()
    }

    fun renderOverlay() {
        dialogLayer.removeAllViews()
        val o = overlay ?: return
        dialogLayer.add(View(this).apply { setBackgroundColor(C.SCRIM); onTap { closeOverlay() } }, MATCH, MATCH)
        val (v, w, h) = when (o) {
            is Overlay.Hold -> Triple(holdMenu(o.index), 520.u, WRAP)
            is Overlay.Picker -> Triple(picker(o), 960.u, minOf(620f, Ui.H - 40).u)
            is Overlay.Missing -> Triple(missingDialog(o.index), 560.u, WRAP)
            is Overlay.MetricPicker -> Triple(metricPicker(o.slot), minOf(960f, Ui.W - 40).u, WRAP)
        }
        v.isClickable = true // swallow taps so they do not reach the scrim
        dialogLayer.add(v, w, h, gravity = Gravity.CENTER)
    }

    fun renderCall() {
        callLayer.removeAllViews()
        if (Hub.call.phase != CallPhase.INCOMING) return
        callLayer.add(View(this).apply { setBackgroundColor(C.SCRIM); isClickable = true }, MATCH, MATCH)
        val card = callLayer.add(incomingCard(), minOf(860f, Ui.W - 40).u, 156.u, gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        card.margins(b = 32f)
    }

    fun openOverlay(o: Overlay) {
        overlay = o
        renderOverlay()
        restartIdle()
    }

    fun closeOverlay() {
        overlay = null
        renderOverlay()
        restartIdle()
    }

    // ------------------------------------------------------------ navigation

    fun push(s: Screen) {
        overlay = null
        if (screen != s) Hub.nav.add(s)
        render()
    }

    fun back() {
        overlay = null
        if (Hub.nav.size > 1) Hub.nav.removeAt(Hub.nav.size - 1)
        render()
    }

    /** Return to the dashboard of the selected layout, dropping everything above it. */
    fun home() {
        overlay = null
        Hub.nav.clear(); Hub.nav.add(Screen.DASH)
        render()
    }

    val previous: Screen? get() = if (Hub.nav.size > 1) Hub.nav[Hub.nav.size - 2] else null

    // ------------------------------------------------------------ idle clock

    private fun idleAllowed() = resumed && screen == Screen.DASH && overlay == null &&
        Hub.call.phase == CallPhase.NONE && Hub.prefs.idleSec > 0

    private val idle = Runnable {
        idlePending = false
        if (idleAllowed()) push(Screen.CLOCK)
    }

    /** Only user interaction (or entering an idle-capable state) restarts the countdown. */
    fun restartIdle() {
        Hub.main.removeCallbacks(idle)
        idlePending = idleAllowed()
        if (idlePending) Hub.main.postDelayed(idle, Hub.prefs.idleSec * 1000L)
    }

    // ------------------------------------------------------------ time

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updateTime()
    }

    private fun updateTime() { for (f in onTime) f() }

    /** 1 s ticks only while a running call duration is on screen. */
    private val tick = object : Runnable {
        override fun run() {
            if (onTick.isEmpty() || !resumed) return
            for (f in onTick) f()
            val now = SystemClock.elapsedRealtime()
            Hub.main.postDelayed(this, 1000 - (now - Hub.call.activeSince) % 1000)
        }
    }

    fun timeText(): Pair<String, String> {
        val d = Date()
        return if (Hub.prefs.h24) SimpleDateFormat("HH:mm", Locale.getDefault()).format(d) to ""
        else SimpleDateFormat("h:mm", Locale.getDefault()).format(d) to SimpleDateFormat("a", Locale.getDefault()).format(d)
    }

    fun dateText(long: Boolean): String =
        SimpleDateFormat(getString(if (long) R.string.date_long else R.string.date_short), Locale.getDefault()).format(Date())

    // ------------------------------------------------------------ feedback and launching

    private val hideToast = Runnable { toastLayer.removeAllViews() }

    fun toast(msg: CharSequence) {
        toastLayer.removeAllViews()
        val t = text(msg, 18f, F.medium, C.BG).apply {
            gravity = Gravity.CENTER
            background = shape(C.TEXT, radius = 28f)
            pad(28f, 0f)
        }
        toastLayer.add(t, WRAP, 56.u, gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).margins(b = 36f)
        Hub.main.removeCallbacks(hideToast)
        Hub.main.postDelayed(hideToast, 1800)
    }

    fun launchRole(r: Role) {
        val key = Hub.apps.targetKey(r)
        val e = Hub.apps.find(key)
        if (e == null) { toast(getString(R.string.not_installed_named, missingName(r))); return }
        open(e)
    }

    fun open(e: AppEntry) {
        if (Hub.apps.launch(this, e.key)) toast(getString(R.string.opening, e.label))
        else toast(getString(R.string.cannot_open, e.label))
    }

    /** Name for a role whose app is absent: last known label, else the expected app. */
    fun missingName(r: Role): String {
        val key = Hub.apps.targetKey(r)
        return key?.let { Hub.apps.find(it)?.label ?: Hub.prefs.label(it) } ?: getString(when (r) {
            Role.RADIO -> R.string.app_welle
            Role.AA -> R.string.app_openauto
            Role.CALL -> R.string.app_phone
            Role.FILES -> R.string.app_files
            Role.VEHICLE -> R.string.app_canservice
        })
    }

    /** Mouse right-click opens the tile menu like a long press (API 14 button state; 23+ context click). */
    @SuppressLint("ClickableViewAccessibility") // taps still go through the normal click listener
    fun View.onSecondary(f: () -> Unit) {
        setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN && e.buttonState and MotionEvent.BUTTON_SECONDARY != 0) { f(); true } else false
        }
        if (Build.VERSION.SDK_INT >= 23) setOnContextClickListener { f(); true }
    }
}

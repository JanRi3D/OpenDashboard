package me.ri3d.dashboard

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Layout C ("Cards"): a shortcut rail, the WELLE radio column and three cards (vehicle, favorites
 * one per page, next turn or clock). Drawn for 1920 x 720 units and scaled as a whole to the window
 * ([Ui.cardsScale]), so it is best on the head unit's 8:3 screen. An active call floats at the bottom.
 */
fun MainActivity.cardsDashboard(): View = frame().apply {
    val k = Ui.cardsScale(Ui.W, Ui.H)
    add(Ui.at(Ui.s * k) { cards() }, (1920 * k).u, (720 * k).u, gravity = Gravity.CENTER)
    if (Hub.call.phase == CallPhase.ACTIVE) add(callBar(), WRAP, 64.u, gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).margins(b = 32f)
}

private fun MainActivity.cards(): View = row().apply {
    gravity = Gravity.NO_GRAVITY
    pad(32f, 28f)
    add(rail(), 104.u, MATCH)
    add(divider(), maxOf(1, 1.u), MATCH).margins(l = 24f)
    add(radioPanel(240f), 384.u, MATCH).margins(l = 32f)
    val cards = add(row(20f).apply { gravity = Gravity.NO_GRAVITY }, 0, MATCH, 1f)
    cards.margins(l = 32f)
    cards.add(VehicleWidget(this@cards, VehicleWidget.CARD).view, 0, MATCH, 1f)
    cards.add(favoritesCard(), 0, MATCH, 1f)
    cards.add(navCard(), 0, MATCH, 1f)
}

// ---------------------------------------------------------------- rail

private fun MainActivity.rail(): View = col().apply {
    add(col(6f).apply {
        gravity = Gravity.CENTER
        background = pressable(0, radius = 18f)
        contentDescription = getString(R.string.clock)
        val time = add(BigText(context, 30f, -0.04f, 1f, C.TEXT))
        val day = add(caps("", 11f))
        onTime += {
            val (t, s) = timeText(); time.text = t; time.suffix = s
            day.text = SimpleDateFormat(getString(R.string.date_rail), Locale.getDefault()).format(Date())
        }
        onTap { push(Screen.CLOCK) }
    }, MATCH, 72.u)
    spring()
    add(frame().apply {
        add(railItem(Icons.PHONE, getString(R.string.app_phone)) { openOverlay(Overlay.Phone) }, MATCH, MATCH)
        add(View(context).apply { background = shape(if (Hub.phone.connected) C.GREEN else C.FAINT, radius = 4f) }, 8.u, 8.u,
            gravity = Gravity.TOP or Gravity.END).margins(t = 18f, r = 30f)
    }, MATCH, 96.u)
    spring()
    add(railItem(Icons.SLIDERS, getString(R.string.settings)) { push(Screen.SETTINGS) }, MATCH, 96.u)
    spring()
    add(col(10f).apply {
        gravity = Gravity.CENTER
        background = pressable(0, radius = 18f)
        contentDescription = getString(R.string.climate)
        val value = add(text("", 26f, F.semi, track = -0.02f))
        add(caps(getString(R.string.ac_short), 11f))
        onVehicle += { value.text = setTempText(short = true); value.setTextColor(if (setTempFresh()) C.TEXT else C.FAINT) }
        onTap { openOverlay(Overlay.Climate) }
    }, MATCH, 96.u)
    spring()
    add(railItem(Icons.GRID, getString(R.string.apps)) { appsOnlyDash = false; push(Screen.APPS) }, MATCH, 96.u)
}

private fun MainActivity.railItem(icon: String, label: String, onClick: () -> Unit): View = col(10f).apply {
    gravity = Gravity.CENTER
    background = pressable(0, radius = 18f)
    add(IconView(context, icon, C.TEXT), 28.u, 28.u)
    add(text(label, 15f, F.medium, C.TEXT2))
    onTap(onClick)
}

// ---------------------------------------------------------------- favorites card

/** One favorite per page (swipe or tap the dots), then an Add page while slots are free. */
private fun MainActivity.favoritesCard(): View = col(12f).apply {
    background = shape(C.CARD, C.LINE, 20f)
    pad(24f)
    val favs = Hub.favorites()
    val max = Hub.max
    val head = add(row(10f), MATCH, 44.u)
    head.add(caps(getString(R.string.favorites), 13f), 0, WRAP, 1f)
    head.add(caps(getString(R.string.fav_count, favs.size, max), 12f, C.FAINT))
    head.add(iconButton(Icons.EDIT, getString(R.string.edit_dashboard), 44f, 20f) { push(Screen.EDIT) }, 44.u, 44.u)

    val pages = favs.mapIndexed { i, k -> favoritePage(i, tileInfo(k)) } +
        if (favs.size < max) listOf(addPage(max - favs.size)) else emptyList()
    val dots = row()
    val on = pages.map { shape(C.TEXT, radius = 4f) }
    val off = pages.map { shape(C.LINE2, radius = 4f) }
    val dotViews = ArrayList<View>()
    fun showPage(p: Int) { for ((i, d) in dotViews.withIndex()) d.background = if (i == p) on[i] else off[i] }
    val pager = add(Pager(context, favPage.coerceIn(0, pages.size - 1)) { p -> favPage = p; showPage(p) }, MATCH, 0, 1f)
    for (p in pages) pager.add(p, MATCH, MATCH)
    dots.gravity = Gravity.CENTER
    if (pages.size > 1) for (i in pages.indices) dots.add(frame().apply {
        contentDescription = getString(R.string.page_n, i + 1, pages.size)
        dotViews += add(View(context), 8.u, 8.u, gravity = Gravity.CENTER)
        onTap { pager.snap(i) }
    }, 24.u, 24.u)
    showPage(pager.page)
    add(dots, MATCH, 24.u)
}

private fun MainActivity.favoritePage(i: Int, t: TileInfo): View = col(28f).apply {
    gravity = Gravity.CENTER
    fun View.actions(): View = apply {
        onTap { tileClicked(i, t) }
        setOnLongClickListener { openOverlay(Overlay.Hold(i)); true } // a consumed long press never also launches
        onSecondary { openOverlay(Overlay.Hold(i)) }
    }
    val iconColor = if (t.accent) C.accent else if (t.installed) C.TEXT else C.MUTED
    add(iconBox(144f, 32f, t.glyph, 72f, iconColor, appIcon = t.icon, stroke = if (t.accent) C.accent else 0).actions().apply {
        contentDescription = t.title
    }, 144.u, 144.u)
    val texts = add(col(8f), MATCH, WRAP)
    texts.add(text(t.title, 30f, F.semi, track = -0.02f).apply { gravity = Gravity.CENTER }, MATCH, WRAP)
    texts.add(text(t.line, 17f, color = if (t.installed) C.TEXT2 else C.accent).apply { gravity = Gravity.CENTER }, MATCH, WRAP)
    texts.add(caps(t.sub).apply { gravity = Gravity.CENTER }, MATCH, WRAP)
    add(pill(getString(if (t.installed) R.string.open else R.string.fix), padH = 40f).actions(), WRAP, 56.u)
}

private fun MainActivity.addPage(free: Int): View = col(28f).apply {
    gravity = Gravity.CENTER
    val toApps = { appsOnlyDash = false; push(Screen.APPS) }
    add(iconBox(144f, 32f, Icons.PLUS, 40f, C.TEXT2, fill = 0, dashed = true, stroke = C.LINE2).apply {
        contentDescription = getString(R.string.add_app)
        onTap(toApps)
    }, 144.u, 144.u)
    val texts = add(col(8f).apply { gravity = Gravity.CENTER_HORIZONTAL })
    texts.add(text(getString(R.string.add_app), 26f, F.semi, track = -0.02f))
    texts.add(caps(resources.getQuantityString(R.plurals.slots_free, free, free)))
    add(pill(getString(R.string.choose_app), padH = 32f, onClick = toApps), WRAP, 56.u)
}

// ---------------------------------------------------------------- navigation / clock card

/** Next turn while Android Auto runs a route (tap: back to Android Auto), otherwise a clock. */
private fun MainActivity.navCard(): View = col().apply {
    background = shape(C.CARD, C.LINE, 20f)
    pad(24f)
    val g = Hub.guidance
    val head = add(row(10f), MATCH, 44.u)
    head.add(caps(getString(if (g != null) R.string.navigation else R.string.clock), 13f), 0, WRAP, 1f)
    head.add(iconButton(Icons.SLIDERS, getString(R.string.settings), 44f, 20f) { push(Screen.SETTINGS) }, 44.u, 44.u)
    if (g != null) {
        val body = add(col(28f).apply {
            gravity = Gravity.CENTER
            background = pressable(0, radius = 16f)
            onTap { launchRole(Role.AA) }
        }, MATCH, 0, 1f)
        body.add(iconBox(128f, 24f, if (g.image == null) g.glyph else null, 72f, C.accent, appIcon = turnIcon(g)), 128.u, 128.u)
        val t = body.add(col(10f).apply { gravity = Gravity.CENTER_HORIZONTAL }, MATCH, WRAP)
        t.add(title(if (g.rerouting) getString(R.string.rerouting) else g.distance ?: getString(R.string.tile_aa), 64f, -0.04f))
        t.add(text(g.road ?: getString(R.string.follow_route), 24f, color = C.TEXT2).apply { gravity = Gravity.CENTER }, MATCH, WRAP)
        add(caps(getString(R.string.aa_tap_open), 12f, C.FAINT).apply { gravity = Gravity.CENTER }, MATCH, WRAP)
    } else {
        val body = add(col(16f).apply {
            gravity = Gravity.CENTER
            background = pressable(0, radius = 16f)
            onTap { push(Screen.CLOCK) }
        }, MATCH, 0, 1f)
        val time = body.add(BigText(context, 120f, -0.06f, 0.9f, C.TEXT))
        val date = body.add(text("", 24f, color = C.MUTED))
        onTime += { val (t, s) = timeText(); time.text = t; time.suffix = s; date.text = dateText(true) }
        add(divider(), MATCH, maxOf(1, 1.u))
        add(phoneChip(), WRAP, 44.u, gravity = Gravity.CENTER_HORIZONTAL).margins(t = 24f)
    }
}

// ---------------------------------------------------------------- pager

/**
 * Swipeable pages: 16-unit touch slop, a page turns after 55 units of drag, then snaps with a
 * quintic ease-out. Taps still reach the page's buttons; the dots are the non-swipe way to turn.
 */
class Pager(c: Context, start: Int, private val onPage: (Int) -> Unit) : FrameLayout(c) {
    var page = start; private set
    private val slop = 16 * Ui.s
    private val commit = 55 * Ui.s
    private var downX = 0f
    private var dragging = false
    private var offset = 0f
    private var anim: ValueAnimator? = null

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        place()
    }

    private fun place() { for (i in 0 until childCount) getChildAt(i).translationX = (i - page) * width + offset }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; dragging = false; anim?.end() }
            MotionEvent.ACTION_MOVE -> if (!dragging && Math.abs(e.x - downX) > slop) {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility") // a swipe is not a click; the dots turn pages for accessibility
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; anim?.end() }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && Math.abs(e.x - downX) > slop) dragging = true
                if (dragging) { offset = (e.x - downX).coerceIn(-width.toFloat(), width.toFloat()); place() }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                snap(when { offset <= -commit -> page + 1; offset >= commit -> page - 1; else -> page })
            }
        }
        return true
    }

    fun snap(to: Int) {
        val target = to.coerceIn(0, maxOf(0, childCount - 1))
        offset += (target - page) * width // the pages stay where they are on screen, then glide
        if (target != page) { page = target; onPage(target) }
        anim?.cancel()
        anim = ValueAnimator.ofFloat(offset, 0f).apply {
            duration = 750
            interpolator = TimeInterpolator { t -> val u = t - 1; u * u * u * u * u + 1 }
            addUpdateListener { offset = it.animatedValue as Float; place() }
            start()
        }
    }
}

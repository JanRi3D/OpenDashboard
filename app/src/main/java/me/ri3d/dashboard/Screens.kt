package me.ri3d.dashboard

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

// ---------------------------------------------------------------- shared bits

private fun MainActivity.backButton(label: String, onClick: () -> Unit): View =
    pill(label, Icons.BACK, padH = 0f, iconSize = 24f, gap = 8f, onClick = onClick).apply { pad(16f, 0f, 22f, 0f) }

private fun MainActivity.titleBlock(name: String, sub: String?): LinearLayout = col(6f).apply {
    add(title(name, 40f, -0.03f))
    if (sub != null) add(caps(sub))
}

private fun MainActivity.card(): LinearLayout = col().apply { background = shape(C.CARD, C.LINE, 20f) }

/** Back target label: "Dashboard" when that is where Back goes. */
private fun MainActivity.backLabel() =
    getString(if (previous == Screen.DASH || previous == null) R.string.dashboard else R.string.back)

// ---------------------------------------------------------------- clock screen

fun MainActivity.clockScreen(): View = col(28f).apply {
    pad(40f, 32f, 40f, 40f)
    val head = add(row(), MATCH, 56.u)
    if (Hub.call.phase == CallPhase.ACTIVE) head.add(callBar(), WRAP, 64.u) else head.add(phoneChip(), WRAP, 44.u)
    head.spring()
    head.add(pill(getString(R.string.dashboard), Icons.GRID) { home() }, WRAP, 56.u)

    val body = add(row().apply { gravity = Gravity.NO_GRAVITY }, MATCH, 0, 1f)
    val left = body.add(col(), 0, MATCH, 1f)
    left.pad(0f, 0f, 48f, 0f)
    val top = left.add(col(18f))
    val time = top.add(BigText(context, 220f, -0.06f, 0.86f, C.TEXT))
    val date = top.add(text("", 26f, color = C.MUTED))
    onTime += { val (t, s) = timeText(); time.text = t; time.suffix = s; date.text = dateText(true) }
    left.spring()
    val g = Hub.guidance
    if (g != null) left.add(guidanceRow(g), MATCH, WRAP) // a running route takes the vehicle values' place
    else if (Hub.prefs.widget) left.add(VehicleWidget(this@clockScreen, VehicleWidget.CLOCK).view, MATCH, WRAP)
    left.spring()
    val quick = left.add(row(16f), MATCH, 88.u)
    fun quickButton(label: Int, icon: String, color: Int, r: Role) = quick.add(row(14f).apply {
        background = pressable(C.CARD, C.LINE, 20f)
        pad(22f, 0f)
        add(IconView(context, icon, color), 30.u, 30.u)
        add(text(getString(label), 20f, F.medium))
        onTap { launchRole(r) }
    }, 0, MATCH, 1f)
    quickButton(R.string.tile_radio, Icons.RADIO, C.TEXT, Role.RADIO)
    quickButton(R.string.tile_aa, Icons.AA, C.accent, Role.AA)
    quickButton(R.string.tile_call, Icons.PHONE, C.TEXT, Role.CALL)

    body.add(divider(), maxOf(1, 1.u), MATCH)
    body.add(radioPanel(), 420.u, MATCH)
}

/** Next turn on the clock screen: Maps' arrow, distance and road; tap returns to Android Auto. */
private fun MainActivity.guidanceRow(g: Guidance): View = row(24f).apply {
    add(iconBox(112f, 20f, if (g.image == null) g.glyph else null, 64f, C.accent, appIcon = turnIcon(g)), 112.u, 112.u)
    val t = add(col(6f), 0, WRAP, 1f)
    t.add(title(if (g.rerouting) getString(R.string.rerouting) else g.distance ?: getString(R.string.tile_aa), 56f, -0.03f, lineHeight = 1f))
    t.add(text(g.road ?: getString(R.string.follow_route), 24f, color = C.TEXT2))
    onTap { launchRole(Role.AA) }
}

private fun MainActivity.radioPanel(): View = col().apply {
    pad(48f, 0f, 0f, 0f)
    val info = Hub.radio
    val ctl = Hub.radioControl
    val target = Hub.apps.target(Role.RADIO)
    val top = add(col(20f))
    top.add(frame().apply {
        background = shape(C.CARD, C.LINE, 16f)
        val art = info?.art
        if (art != null) add(ImageView(context).apply { setImageBitmap(art); scaleType = ImageView.ScaleType.FIT_CENTER }, MATCH, MATCH).pad(8f)
        else add(IconView(context, Icons.RADIO, C.FAINT, 1.25f), 52.u, 52.u, gravity = Gravity.CENTER)
        if (target != null) onTap { open(target) }
    }, 132.u, 132.u)
    val texts = top.add(col(8f))
    val appName = target?.label ?: missingName(Role.RADIO)
    texts.add(caps(info?.source ?: appName, 13f))
    texts.add(text(info?.station ?: getString(if (target == null) R.string.not_installed else R.string.radio_no_station),
        34f, F.semi, track = -0.02f).apply { setSingleLine(false); maxLines = 2 })
    texts.add(para(when {
        info != null -> info.programme ?: ""
        target == null -> getString(R.string.radio_missing, appName)
        ctl == null -> getString(R.string.radio_no_control, appName)
        else -> getString(R.string.radio_no_metadata, appName)
    }, if (info != null) 21f else 17f))
    spring()
    val bottom = add(col(22f), MATCH, WRAP)
    bottom.add(caps(info?.position ?: getString(R.string.preset_unknown), 13f))
    val controls = bottom.add(row(), MATCH, 100.u)
    fun round(size: Float, icon: String, filled: Boolean, desc: Int, f: () -> Unit) = frame().apply {
        background = if (filled) pressable(C.TEXT, 0, size / 2, pressedFill = C.TEXT2) else pressable(0, C.LINE2, size / 2)
        contentDescription = getString(desc)
        add(IconView(context, icon, if (filled) C.BG else C.TEXT, 2f, filled = true), (if (filled) 36f else 32f).u,
            (if (filled) 36f else 32f).u, gravity = Gravity.CENTER)
        if (ctl != null) onTap(f) else alpha = 0.35f
    }
    val c = ctl
    controls.add(round(92f, Icons.PREV, false, R.string.prev_station) { c?.prev(); sent(c) }, 92.u, 92.u)
    controls.spring()
    val playIcon = when (info?.playing) { true -> Icons.PAUSE; false -> Icons.PLAY; null -> Icons.PLAY_PAUSE }
    controls.add(round(100f, playIcon, true, R.string.play_pause) { c?.toggle(); sent(c) }, 100.u, 100.u)
    controls.spring()
    controls.add(round(92f, Icons.SKIP, false, R.string.next_station) { c?.next(); sent(c) }, 92.u, 92.u)
}

/** Commands to WELLE are fire-and-forget: say they were sent, never that they took effect. */
private fun MainActivity.sent(c: RadioControl?) {
    if (c != null && Hub.radio == null) toast(getString(R.string.sent_to, c.name))
}

// ---------------------------------------------------------------- apps

fun MainActivity.appsScreen(): View = col(20f).apply {
    pad(32f, 28f)
    val favs = Hub.favorites()
    val head = add(row(), MATCH, 64.u)
    val left = head.add(row(24f))
    left.add(backButton(backLabel()) { back() }, WRAP, 56.u)
    left.add(titleBlock(getString(R.string.apps), getString(R.string.quick_open, favs.size, Hub.max)))
    head.spring()
    val right = head.add(row(12f))
    val search = right.add(row(12f).apply {
        background = shape(0, C.LINE2, 28f); pad(20f, 0f)
        add(IconView(context, Icons.SEARCH, C.MUTED), 22.u, 22.u)
    }, 300.u, 56.u)
    val input = search.add(EditText(context).apply {
        setText(appsQuery)
        hint = getString(R.string.search_apps)
        setHintTextColor(C.FAINT); setTextColor(C.TEXT)
        typeface = F.regular
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 18f * Ui.s)
        background = null; setPadding(0, 0, 0, 0)
        setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setSelection(appsQuery.length)
    }, 0, MATCH, 1f)
    right.add(segmented(listOf(getString(R.string.all_apps), getString(R.string.on_dashboard)), if (appsOnlyDash) 1 else 0) {
        appsOnlyDash = it == 1; render()
    }, WRAP, 56.u)

    val body = add(frame(), MATCH, 0, 1f)
    fun fill() {
        body.removeAllViews()
        val q = appsQuery.trim().lowercase()
        val apps = Hub.apps.list.filter { e ->
            (!appsOnlyDash || Hub.isOnDashboard(e) || (Hub.isWidgetApp(e) && Hub.prefs.widget)) &&
                (q.isEmpty() || e.label.lowercase().contains(q))
        }
        if (apps.isEmpty()) {
            body.add(col(10f).apply {
                gravity = Gravity.CENTER
                background = shape(0, C.LINE2, 20f, dashed = true)
                add(text(getString(if (Hub.apps.loaded) R.string.no_match else R.string.apps_loading), 24f, F.semi, track = -0.02f))
                if (Hub.apps.loaded) add(text(getString(R.string.no_match_hint), 17f, color = C.MUTED))
            }, MATCH, MATCH)
            return
        }
        val cols = maxOf(4, ((Ui.W - 64 + 16) / 205).toInt()) // 6 at 1280 wide, 9 at 1920
        val rowH = (Ui.H - 56 - 64 - 20 - 16) / 2
        body.add(scrollGrid(apps.map { appCard(it) }, cols, 16f, rowH), MATCH, MATCH)
    }
    input.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { appsQuery = s?.toString() ?: ""; fill() }
    })
    fill()
}

private fun MainActivity.appCard(e: AppEntry): View = col(12f).apply {
    background = shape(C.CARD, C.LINE, 20f)
    pad(16f)
    val on = Hub.isOnDashboard(e)
    val widgetApp = Hub.isWidgetApp(e)
    val glyph = glyphFor(e)
    val main = add(col(14f).apply {
        add(iconBox(72f, 18f, glyph, 34f, if (on || widgetApp) C.accent else C.TEXT2, appIcon = if (glyph == null) e.icon else null), 72.u, 72.u)
        val t = add(col(4f))
        t.add(text(e.label, 20f, F.medium, track = -0.01f))
        t.add(caps(Hub.apps.category(context, e), 11f))
        contentDescription = e.label
        onTap { open(e) }
    }, MATCH, WRAP)
    main.background = pressable(0, 0, 12f)
    spring()
    val full = Hub.favorites().size >= Hub.max
    val accent = C.accent
    val (label, fill, fg, stroke) = when {
        widgetApp -> Quad(getString(if (Hub.prefs.widget) R.string.shown_as_widget else R.string.widget_hidden_short), C.RAISED, C.TEXT2, C.LINE2)
        on -> Quad(getString(R.string.on_dashboard), accent, C.BG, accent)
        full -> Quad(getString(R.string.dashboard_full), 0, C.FAINT, C.LINE)
        else -> Quad(getString(R.string.add_to_dashboard), 0, C.TEXT, C.LINE2)
    }
    val locked = widgetApp || (!on && full)
    add(text(label, 15f, F.medium, fg).apply {
        gravity = Gravity.CENTER
        background = if (locked) shape(fill, stroke, 22f) else pressable(fill, stroke, 22f, pressedFill = if (on) C.TEXT2 else C.PRESSED)
        pad(8f, 0f)
        if (!locked) onTap { Hub.toggleFavorite(e) }
    }, MATCH, 44.u)
}

private data class Quad(val a: String, val b: Int, val c: Int, val d: Int)

// ---------------------------------------------------------------- settings

/** Miniature of layout A or B: Settings uses gap/radius 6 and padding 10, first start 8 and 14. */
private fun MainActivity.layoutPreview(b: Boolean, large: Boolean = false): LinearLayout {
    val gap = if (large) 8f else 6f
    val box = if (b) col(gap) else row(gap).apply { gravity = Gravity.NO_GRAVITY }
    box.background = shape(C.BG, C.LINE, if (large) 14f else 12f)
    box.pad(if (large) 14f else 10f)
    fun cell(accent: Boolean = false, dashed: Boolean = false) = View(this).apply {
        background = when {
            accent -> shape(C.LINE, C.accent, gap)
            dashed -> shape(0, C.LINE2, gap, dashed = true)
            else -> shape(C.LINE, radius = gap)
        }
    }
    if (!b) {
        box.add(cell(), 0, MATCH, 0.47f)
        box.add(grid(listOf(cell(), cell(true), cell(), cell(), cell(), cell(dashed = true)), 3, 2, gap), 0, MATCH, 1f)
    } else {
        box.add(cell(), MATCH, 0, 0.39f)
        box.add(grid(listOf(cell(), cell(true), cell(), cell(), cell()), 5, 1, gap), MATCH, 0, 1f)
    }
    return box
}

fun MainActivity.settingsScreen(): View = col(20f).apply {
    pad(32f, 28f)
    val p = Hub.prefs
    val head = add(row(24f), MATCH, 64.u)
    head.add(backButton(getString(R.string.dashboard)) { home() }, WRAP, 56.u)
    head.add(title(getString(R.string.settings), 40f, -0.03f))

    val body = add(row(20f).apply { gravity = Gravity.NO_GRAVITY }, MATCH, 0, 1f)
    val layout = body.add(card().apply { pad(24f); dividerDrawable = gapDrawable(18f); showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE }, 600.u, MATCH)
    layout.add(caps(getString(R.string.dashboard_layout), 13f))
    val pick = layout.add(row(16f).apply { gravity = Gravity.NO_GRAVITY }, MATCH, WRAP)
    for (b in listOf(false, true)) {
        val on = p.layoutB == b
        pick.add(col(14f).apply {
            background = pressable(C.RAISED, if (on) C.accent else C.LINE, 18f)
            pad(14f)
            add(layoutPreview(b), MATCH, 150.u)
            val names = add(col(4f), MATCH, WRAP)
            val titleRow = names.add(row(8f), MATCH, WRAP)
            titleRow.add(text(getString(if (b) R.string.layout_b else R.string.layout_a), 20f, F.semi, track = -0.02f), 0, WRAP, 1f)
            if (on) titleRow.add(caps(getString(R.string.in_use), 11f, C.accent))
            names.add(text(getString(if (b) R.string.layout_b_short else R.string.layout_a_short), 15f, color = C.MUTED))
            onTap { if (p.layoutB != b) { p.layoutB = b; Hub.changed(Hub.CONFIG) } }
        }, 0, WRAP, 1f)
    }
    fun link(icon: String, label: String, f: () -> Unit) = row(12f).apply {
        background = pressable(0, C.LINE2, 16f); pad(22f, 0f)
        add(IconView(context, icon, C.TEXT), 24.u, 24.u)
        add(text(label, 18f, F.medium), 0, WRAP, 1f)
        add(IconView(context, Icons.NEXT, C.MUTED), 22.u, 22.u)
        onTap(f)
    }
    layout.add(link(Icons.EDIT, getString(if (p.layoutB) R.string.edit_tiles_b else R.string.edit_tiles_a)) { push(Screen.EDIT) }, MATCH, 64.u)
    layout.add(text(getString(R.string.hold_hint), 15f, color = C.MUTED).apply { setSingleLine(false) })
    // Side by side so the card still fits a 720-unit-high screen.
    val more = layout.add(row(12f), MATCH, 64.u)
    more.add(link(Icons.CAR, getString(R.string.widget_values_short)) { push(Screen.WIDGET) }, 0, MATCH, 1f)
    more.add(link(Icons.SLIDERS, getString(R.string.integrations_short)) { push(Screen.INTEGRATIONS) }, 0, MATCH, 1f)

    val general = body.add(card().apply { pad(24f, 0f) }, 0, MATCH, 1f)
    general.add(caps(getString(R.string.general), 13f).apply { gravity = Gravity.CENTER_VERTICAL }, MATCH, 56.u)
    fun setting(title: Int, sub: String, control: View, w: Int = WRAP, h: Int = WRAP) {
        general.add(divider(), MATCH, maxOf(1, 1.u))
        val r = general.add(row(20f), MATCH, 0, 1f)
        val t = r.add(col(4f), 0, WRAP, 1f)
        t.add(text(getString(title), 20f, F.medium))
        t.add(text(sub, 15f, color = C.MUTED))
        r.add(control, w, h)
    }
    val idleOptions = intArrayOf(60, 300, 0)
    setting(R.string.clock_screen, getString(R.string.clock_screen_sub),
        segmented(listOf(getString(R.string.idle_1), getString(R.string.idle_5), getString(R.string.idle_never)),
            idleOptions.indexOf(p.idleSec).coerceAtLeast(0), 18f) { p.idleSec = idleOptions[it]; Hub.changed(Hub.CONFIG) }, WRAP, 56.u)
    setting(R.string.vehicle_widget, getString(R.string.vehicle_widget_sub),
        switch(p.widget, getString(R.string.vehicle_widget)) { p.widget = !p.widget; Hub.changed(Hub.CONFIG) }, 72.u, 44.u)
    val sample = if (p.h24) timeText().first else timeText().let { it.first + " " + it.second }
    setting(R.string.clock_24, getString(R.string.clock_24_sub, sample),
        switch(p.h24, getString(R.string.clock_24)) { p.h24 = !p.h24; Hub.changed(Hub.CONFIG) }, 72.u, 44.u)
    setting(R.string.auto_aa, getString(R.string.auto_aa_sub),
        switch(p.autoAA, getString(R.string.auto_aa)) { p.autoAA = !p.autoAA; Hub.changed(Hub.CONFIG) }, 72.u, 44.u)
    setting(R.string.guidance, getString(R.string.guidance_sub),
        switch(p.guidance, getString(R.string.guidance)) { p.guidance = !p.guidance; OpenAutoNav.show(); Hub.changed(Hub.CONFIG) }, 72.u, 44.u)
    val names = intArrayOf(R.string.accent_amber, R.string.accent_blue, R.string.accent_green, R.string.accent_white)
    setting(R.string.accent, getString(R.string.accent_sub), row(8f).apply {
        for (i in C.ACCENTS.indices) add(frame().apply {
            background = shape(0, if (i == p.accent) C.TEXT else 0, 26f, strokeW = 2f)
            contentDescription = getString(names[i])
            add(View(context).apply { background = shape(C.ACCENTS[i], radius = 20f) }, 40.u, 40.u, gravity = Gravity.CENTER)
            onTap { p.accent = i; Hub.changed(Hub.CONFIG) }
        }, 52.u, 52.u)
    })
}

// ---------------------------------------------------------------- integrations

fun MainActivity.integrationsScreen(): View = col(20f).apply {
    pad(32f, 28f)
    val head = add(row(24f), MATCH, 64.u)
    head.add(backButton(getString(R.string.settings)) { back() }, WRAP, 56.u)
    head.add(title(getString(R.string.integrations), 40f, -0.03f))
    val body = add(row(20f).apply { gravity = Gravity.NO_GRAVITY }, MATCH, 0, 1f)

    val targets = body.add(card().apply { pad(24f, 0f) }, 0, MATCH, 1f)
    targets.add(caps(getString(R.string.launch_targets), 13f).apply { gravity = Gravity.CENTER_VERTICAL }, MATCH, 56.u)
    for (r in Role.values()) {
        targets.add(divider(), MATCH, maxOf(1, 1.u))
        val line = targets.add(row(20f), MATCH, 0, 1f)
        val t = line.add(col(4f), 0, WRAP, 1f)
        t.add(text(roleName(r), 20f, F.medium))
        val e = Hub.apps.target(r)
        val auto = Hub.prefs.mapping(r) == null
        t.add(text(when {
            e != null -> if (auto) getString(R.string.target_auto, e.label) else e.label
            Hub.apps.targetKey(r) != null -> getString(R.string.target_missing, missingName(r))
            else -> getString(R.string.target_none)
        }, 15f, color = if (e != null) C.MUTED else C.accent))
        line.add(pill(getString(R.string.change), h = 48f, padH = 20f, size = 16f) { openOverlay(Overlay.Picker(-1, r)) }, WRAP, 48.u)
    }

    val status = body.add(card().apply { pad(24f, 0f) }, 0, MATCH, 1f)
    status.add(caps(getString(R.string.status), 13f).apply { gravity = Gravity.CENTER_VERTICAL }, MATCH, 56.u)
    fun item(ok: Boolean, title: Int, sub: String) {
        status.add(divider(), MATCH, maxOf(1, 1.u))
        val line = status.add(row(16f), MATCH, 0, 1f)
        line.add(View(context).apply { background = shape(if (ok) C.GREEN else C.FAINT, radius = 5f) }, 10.u, 10.u)
        val t = line.add(col(4f), 0, WRAP, 1f)
        t.add(text(getString(title), 20f, F.medium))
        t.add(para(sub, 15f, lineHeight = 1.25f).apply { maxLines = 2 })
    }
    val v = Hub.vehicle
    val vs = v.status(android.os.SystemClock.elapsedRealtime())
    val vehicleLive = vs == VehicleState.Status.LIVE || vs == VehicleState.Status.DOOR_OPEN
    item(vehicleLive, R.string.st_vehicle, when {
        vs != VehicleState.Status.NO_DATA && v.source != VehicleWidget.CAN -> getString(R.string.st_vehicle_demo)
        vehicleLive -> getString(R.string.st_vehicle_live, CanService.rawDoors)
        vs == VehicleState.Status.STALE -> getString(R.string.st_vehicle_stale)
        else -> when (CanService.link) {
            CanService.Link.CONNECTED -> getString(R.string.st_vehicle_connected)
            CanService.Link.CONNECTING -> getString(R.string.st_vehicle_connecting)
            CanService.Link.DISCONNECTED -> getString(R.string.st_vehicle_disconnected)
            CanService.Link.REFUSED -> getString(R.string.st_vehicle_refused, CanService.refusal ?: "")
            CanService.Link.NOT_INSTALLED -> getString(R.string.st_vehicle_none, CanService.PKG)
        }
    })
    item(Hub.radioControl != null, R.string.st_radio, when {
        Hub.radio != null -> getString(if (Hub.demo) R.string.st_calls_demo else R.string.st_radio_meta)
        Hub.radioControl != null -> getString(R.string.st_radio_keys)
        else -> getString(R.string.st_radio_none, missingName(Role.RADIO))
    })
    val ph = Hub.phone
    item(ph.connected, R.string.st_phone, getString(when (ph.link) {
        PhoneLink.CONNECTED -> R.string.st_phone_connected
        PhoneLink.PAIRED -> R.string.st_phone_paired
        PhoneLink.NO_BLUETOOTH -> R.string.st_phone_nobt
        PhoneLink.NO_PERMISSION -> R.string.st_phone_perm
        else -> R.string.st_phone_other
    }))
    val cc = Hub.callControl
    item(cc != null, R.string.st_calls, getString(if (cc != null) R.string.st_calls_demo else R.string.st_calls_none))
    val home = homeAppLabel()
    item(home == getString(R.string.app_name), R.string.st_home,
        if (home == getString(R.string.app_name)) getString(R.string.st_home_yes) else getString(R.string.st_home_no, home ?: getString(R.string.st_home_unset)))
}

// ---------------------------------------------------------------- editors

fun MainActivity.editor(): View = col(20f).apply {
    pad(32f, 28f)
    val b = Hub.layoutB
    val favs = Hub.favorites()
    val max = Hub.max
    val head = add(row(12f), MATCH, 64.u)
    head.add(titleBlock(getString(R.string.edit_dashboard), getString(if (b) R.string.slots_used_b else R.string.slots_used_a, favs.size, max)))
    head.spring()
    head.add(pill(getString(R.string.reset)) { Hub.resetLayout(); toast(getString(R.string.reset_done)) }, WRAP, 56.u)
    head.add(pill(getString(R.string.done), padH = 32f, fill = C.TEXT, fg = C.BG, stroke = 0) { home() }, WRAP, 56.u)

    val tiles = favs.mapIndexed { i, k -> editTile(i, k, favs) }
    val empties = (favs.size until max).map { editEmpty() }
    val cells = tiles + empties + editApps()
    if (!b) {
        val body = add(row(20f).apply { gravity = Gravity.NO_GRAVITY }, MATCH, 0, 1f)
        body.add(editWidget(false), 380.u, MATCH)
        body.add(grid(cells, 3, 2, 20f), 0, MATCH, 1f)
    } else {
        add(editWidget(true), MATCH, 168.u)
        add(grid(cells, 5, 1, 16f), MATCH, 0, 1f)
    }
}

private fun MainActivity.editWidget(wide: Boolean): View {
    val p = Hub.prefs
    if (p.widget) {
        val f = FrameLayout(this)
        f.add(VehicleWidget(this, if (wide) VehicleWidget.WIDE else VehicleWidget.TALL).view, MATCH, MATCH)
        val bar = row().apply {
            background = shape(C.BG, C.LINE2, 16f)
            pad(18f, 0f, 10f, 0f)
            add(text(getString(if (wide) R.string.widget_short else R.string.vehicle_widget), 17f, F.medium), 0, WRAP, 1f)
            add(pill(getString(R.string.values), h = 48f, padH = 16f, size = 16f, radius = 12f) { push(Screen.WIDGET) }, WRAP, 48.u)
            add(View(context), 8.u, 1)
            add(pill(getString(R.string.hide), h = 48f, padH = 16f, size = 16f, radius = 12f) { p.widget = false; Hub.changed(Hub.CONFIG) }, WRAP, 48.u)
        }
        if (wide) f.add(bar, 300.u, MATCH, gravity = Gravity.END).margins(0f, 16f, 16f, 16f)
        else f.add(bar, MATCH, 68.u, gravity = Gravity.BOTTOM).margins(16f, 0f, 16f, 16f)
        return f
    }
    val showButton = pill(getString(R.string.show_widget), h = 56f, padH = 28f, fill = C.TEXT, fg = C.BG, stroke = 0, radius = 14f) {
        p.widget = true; Hub.changed(Hub.CONFIG)
    }
    return if (wide) row(20f).apply {
        background = shape(0, C.LINE2, 20f, dashed = true); pad(28f, 0f)
        add(IconView(context, Icons.CAR_OUTLINE, C.MUTED, 1.5f), 40.u, 40.u)
        val t = add(col(6f), 0, WRAP, 1f)
        t.add(text(getString(R.string.widget_hidden), 22f, F.semi, track = -0.02f))
        t.add(text(getString(R.string.widget_hidden_sub), 16f, color = C.MUTED))
        add(showButton, WRAP, 56.u)
    } else col(16f).apply {
        gravity = Gravity.CENTER
        background = shape(0, C.LINE2, 20f, dashed = true); pad(24f)
        add(IconView(context, Icons.CAR_OUTLINE, C.MUTED, 1.5f), 40.u, 40.u)
        add(text(getString(R.string.widget_hidden), 22f, F.semi, track = -0.02f))
        add(para(getString(R.string.widget_hidden_sub), 16f).apply { gravity = Gravity.CENTER })
        add(showButton, WRAP, 56.u)
    }
}

private fun MainActivity.editTile(i: Int, key: String, favs: List<String>): View = col().apply {
    val b = Hub.layoutB
    val t = tileInfo(key)
    background = shape(C.CARD, C.accent, 20f, dashed = true)
    if (b) pad(18f, 22f, 18f, 18f) else pad(18f)
    val box = if (b) 64f else 56f
    add(iconBox(box, if (b) 16f else 14f, t.glyph, if (b) 32f else 28f, C.TEXT, appIcon = t.icon), box.u, box.u)
    spring()
    val texts = add(col(6f), MATCH, WRAP)
    texts.add(title(t.title, if (b) 26f else 24f, -0.02f, lineHeight = 1.1f), MATCH, WRAP)
    texts.add(caps(t.entry?.label ?: t.missingName))
    spring()
    val controls = add(row(8f), MATCH, 44.u)
    fun arrow(icon: String, enabled: Boolean, desc: Int, f: () -> Unit) = frame().apply {
        background = if (enabled) pressable(0, C.LINE2, 22f) else shape(0, C.LINE, 22f)
        contentDescription = getString(desc, t.title)
        add(IconView(context, icon, if (enabled) C.TEXT else C.LINE2, 2f), 20.u, 20.u, gravity = Gravity.CENTER)
        if (enabled) onTap(f)
    }
    controls.add(arrow(Icons.BACK, i > 0, R.string.move_left) { Hub.setFavorites(Favorites.move(favs, i, -1)) }, 44.u, 44.u)
    controls.add(pill(getString(R.string.remove), h = 44f, padH = 0f, size = 15f, fill = C.RAISED, stroke = 0, radius = 22f) {
        Hub.setFavorites(Favorites.remove(favs, key))
    }.apply { contentDescription = getString(R.string.remove_named, t.title) }, 0, 44.u, 1f)
    controls.add(arrow(Icons.NEXT, i < favs.size - 1, R.string.move_right) { Hub.setFavorites(Favorites.move(favs, i, 1)) }, 44.u, 44.u)
}

private fun MainActivity.editEmpty(): View = col().apply {
    val b = Hub.layoutB
    background = pressable(0, C.LINE2, 20f, dashed = true)
    if (b) pad(18f, 22f, 18f, 18f) else pad(18f)
    val box = if (b) 64f else 56f
    add(iconBox(box, if (b) 16f else 14f, Icons.PLUS, if (b) 28f else 26f, C.TEXT2, fill = 0, dashed = true, stroke = C.LINE2), box.u, box.u)
    spring()
    val texts = add(col(6f))
    texts.add(text(getString(R.string.add_app), if (b) 26f else 24f, F.semi, track = -0.02f))
    texts.add(caps(getString(R.string.free_slot)))
    onTap { appsOnlyDash = false; push(Screen.APPS) }
}

private fun MainActivity.editApps(): View = col().apply {
    val b = Hub.layoutB
    background = shape(C.CARD_DIM, C.LINE, 20f)
    if (b) pad(18f, 22f, 18f, 18f) else pad(18f)
    val box = if (b) 64f else 56f
    add(iconBox(box, if (b) 16f else 14f, Icons.GRID, if (b) 32f else 28f, C.MUTED), box.u, box.u)
    spring()
    val texts = add(col(6f))
    texts.add(text(getString(R.string.apps), if (b) 26f else 24f, F.semi, C.TEXT2, -0.02f))
    texts.add(caps(getString(R.string.always_here)))
}

// ---------------------------------------------------------------- first start

fun MainActivity.welcome(): View = col(28f).apply {
    pad(56f, 44f)
    val head = add(col(12f))
    head.add(caps(getString(R.string.app_name), 13f))
    head.add(title(getString(R.string.pick_dashboard), 52f, -0.04f))
    head.add(text(getString(R.string.pick_dashboard_sub), 20f, color = C.MUTED))
    val choices = add(row(24f).apply { gravity = Gravity.NO_GRAVITY }, MATCH, 0, 1f)
    for (b in listOf(false, true)) {
        val on = welcomeB == b
        choices.add(col(18f).apply {
            background = pressable(C.CARD, if (on) C.accent else C.LINE, 22f)
            pad(18f)
            add(layoutPreview(b, large = true), MATCH, 0, 1f)
            val r = add(row(14f), MATCH, WRAP)
            r.add(frame().apply {
                background = shape(0, if (on) C.accent else C.LINE2, 14f, strokeW = 2f)
                add(View(context).apply { background = shape(if (on) C.accent else 0, radius = 7f) }, 14.u, 14.u, gravity = Gravity.CENTER)
            }, 28.u, 28.u)
            val t = r.add(col(4f))
            t.add(text(getString(if (b) R.string.layout_b else R.string.layout_a), 24f, F.semi, track = -0.02f))
            t.add(text(getString(if (b) R.string.layout_b_long else R.string.layout_a_long), 16f, color = C.MUTED))
            onTap { welcomeB = b; render() }
        }, 0, MATCH, 1f)
    }
    val foot = add(row(12f), MATCH, 64.u)
    val can = Hub.apps.canServicePkg
    val live = Hub.vehicle.status(android.os.SystemClock.elapsedRealtime()) != VehicleState.Status.NO_DATA
    foot.add(Chip(context).apply {
        set(getString(when { live -> R.string.can_live; can != null -> R.string.can_found; else -> R.string.can_not_found }),
            if (live) C.GREEN else C.FAINT)
    }, WRAP, 44.u)
    val found = Role.QUICK.count { Hub.apps.target(it) != null }
    foot.add(Chip(context).apply {
        set(if (!Hub.apps.loaded) getString(R.string.apps_loading) else resources.getQuantityString(R.plurals.quick_found, found, found, Role.QUICK.size),
            if (found == Role.QUICK.size) C.GREEN else C.FAINT)
    }, WRAP, 44.u)
    foot.spring()
    val start = foot.add(pill(getString(if (welcomeB) R.string.start_b else R.string.start_a), null, h = 64f, padH = 32f, size = 19f,
        fill = C.TEXT, fg = C.BG, stroke = 0) {
        Hub.prefs.layoutB = welcomeB
        Hub.prefs.onboarded = true
        home()
    }, WRAP, 64.u)
    start.add(IconView(context, Icons.ARROW, C.BG, 2f), 22.u, 22.u)
}

// ---------------------------------------------------------------- vehicle widget values

/** The driver chooses what each place of the vehicle widget shows; the preview updates live. */
fun MainActivity.widgetScreen(): View = col(20f).apply {
    pad(32f, 28f)
    val head = add(row(24f), MATCH, 64.u)
    head.add(backButton(backLabel()) { back() }, WRAP, 56.u)
    head.add(titleBlock(getString(R.string.widget_values), getString(R.string.widget_values_sub)), 0, WRAP, 1f)
    head.add(pill(getString(R.string.reset)) {
        Hub.prefs.widgetSlots = WidgetSlots.DEFAULT
        Hub.changed(Hub.CONFIG)
        toast(getString(R.string.reset_done))
    }, WRAP, 56.u)

    val body = add(row(20f).apply { gravity = Gravity.NO_GRAVITY }, MATCH, 0, 1f)
    body.add(VehicleWidget(this@widgetScreen, VehicleWidget.TALL).view, 380.u, MATCH)
    val list = body.add(card().apply { pad(24f, 0f) }, 0, MATCH, 1f)
    val slots = Hub.prefs.widgetSlots
    for (i in 0 until WidgetSlots.COUNT) {
        if (i > 0) list.add(divider(), MATCH, maxOf(1, 1.u))
        val line = list.add(row(20f), MATCH, 0, 1f)
        val t = line.add(col(4f), 0, WRAP, 1f)
        t.add(text(getString(slotName(i)), 19f, F.medium))
        val m = slots[i]
        val ok = Hub.demo || m in CanService.SUPPORTED
        t.add(text(if (ok) getString(m.title) else getString(R.string.metric_needs_obd, getString(m.title)), 15f,
            color = if (ok) C.MUTED else C.accent))
        line.add(pill(getString(R.string.change), h = 48f, padH = 20f, size = 16f) { openOverlay(Overlay.MetricPicker(i)) }, WRAP, 48.u)
    }
}

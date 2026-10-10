package me.ri3d.dashboard

import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Locale

/** What a dashboard tile shows for a favorite key. */
class TileInfo(val key: String, val role: Role?, val entry: AppEntry?, val title: String, val line: String, val sub: String,
               val glyph: String?, val icon: Drawable?, val accent: Boolean, val missingName: String, val bigIcon: Boolean = false) {
    val installed get() = entry != null
}

fun MainActivity.tileInfo(key: String): TileInfo {
    val role = Role.of(key)
    val apps = Hub.apps
    val phone = Hub.phone
    if (role == null) {
        val e = apps.find(key)
        val name = e?.label ?: Hub.prefs.label(key) ?: key.substringBefore('/')
        return TileInfo(key, null, e, name, if (e == null) getString(R.string.not_installed) else apps.category(this, e),
            getString(R.string.sub_app), if (e?.icon == null) Icons.APP else null, e?.icon, false, name)
    }
    val e = apps.target(role)
    val missing = missingName(role)
    val label = e?.label ?: missing
    val notInstalled = getString(R.string.not_installed)
    return when (role) {
        Role.RADIO -> {
            val r = Hub.radio
            val art = r?.art?.let { BitmapDrawable(resources, it) }
            TileInfo(key, role, e, getString(R.string.tile_radio),
                if (e == null) notInstalled else r?.station ?: getString(R.string.radio_tap),
                r?.source ?: label, if (art == null) Icons.RADIO else null, art, false, missing)
        }
        Role.AA -> Hub.guidance?.takeIf { e != null }?.let { g ->
            // Route running: the tile becomes the next turn.
            TileInfo(key, role, e, if (g.rerouting) getString(R.string.rerouting) else g.distance ?: getString(R.string.tile_aa),
                g.road ?: getString(R.string.follow_route), getString(R.string.tile_aa),
                if (g.image == null) g.glyph else null, turnIcon(g), true, missing, bigIcon = true)
        } ?: TileInfo(key, role, e, getString(R.string.tile_aa),
            when { e == null -> notInstalled; phone.disconnected -> getString(R.string.aa_connect); else -> getString(R.string.aa_tap) },
            if (phone.connected) getString(R.string.phone_connected) else label,
            Icons.AA, null, e != null && !phone.disconnected, missing)
        Role.CALL -> TileInfo(key, role, e, getString(R.string.tile_call),
            when {
                e == null -> notInstalled
                phone.disconnected -> getString(if (Hub.layoutB) R.string.call_connect_short else R.string.call_connect)
                else -> getString(R.string.call_recents)
            },
            if (label.contains("bluetooth", true)) label else getString(R.string.sub_bluetooth, label),
            Icons.PHONE, null, false, missing)
        Role.FILES -> TileInfo(key, role, e, getString(R.string.tile_files),
            if (e == null) notInstalled else getString(R.string.files_line), label, Icons.FOLDER, null, false, missing)
        Role.VEHICLE -> TileInfo(key, role, e, label, getString(R.string.cat_vehicle), label, Icons.CAR, null, false, missing)
    }
}

/** Maps' arrow is white on opaque black: brightness becomes opacity, drawn in the accent like the fallback glyphs. */
fun MainActivity.turnIcon(g: Guidance): Drawable? = g.image?.let {
    BitmapDrawable(resources, it).apply {
        val a = C.accent
        colorFilter = ColorMatrixColorFilter(floatArrayOf(
            0f, 0f, 0f, 0f, Color.red(a).toFloat(),
            0f, 0f, 0f, 0f, Color.green(a).toFloat(),
            0f, 0f, 0f, 0f, Color.blue(a).toFloat(),
            0.3f, 0.59f, 0.11f, 0f, 0f))
    }
}

// ---------------------------------------------------------------- header

fun MainActivity.phoneChip(): Chip = Chip(this).apply {
    val p = Hub.phone
    set(getString(when (p.link) {
        PhoneLink.CONNECTED -> R.string.phone_connected
        PhoneLink.PAIRED -> R.string.phone_paired
        PhoneLink.DISCONNECTED -> R.string.phone_disconnected
        PhoneLink.NOT_PAIRED -> R.string.phone_none
        PhoneLink.OFF -> R.string.phone_bt_off
        PhoneLink.NO_BLUETOOTH -> R.string.phone_no_bt
        PhoneLink.NO_PERMISSION, PhoneLink.UNKNOWN -> R.string.phone_unknown
    }), if (p.connected) C.GREEN else C.FAINT)
}

/** Active call: replaces the phone status area (dashboards and clock). */
fun MainActivity.callBar(): View = row(16f).apply {
    val call = Hub.call
    val ctl = Hub.callControl
    background = shape(C.CARD, C.GREEN, 32f)
    pad(22f, 0f, 8f, 0f)
    add(View(context).apply { background = shape(C.GREEN, radius = 5f) }, 10.u, 10.u)
    val info = add(col(2f), WRAP, WRAP)
    info.minimumWidth = 140.u
    info.add(text(call.caller ?: getString(R.string.call_unknown), 18f, F.medium))
    val time = info.add(text(getString(R.string.call_in_progress), 12f, F.mono, C.MUTED, 0.08f))
    if (call.activeSince > 0) onTick += {
        val sec = (SystemClock.elapsedRealtime() - call.activeSince) / 1000
        time.text = if (sec >= 3600) String.format(Locale.US, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
        else String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
    }
    val muted = call.muted
    if (ctl != null && ctl.canMute && muted != null) {
        add(pill(getString(if (muted) R.string.unmute else R.string.mute), Icons.MIC, h = 48f, padH = 18f, size = 16f,
            fill = if (muted) C.TEXT else 0, fg = if (muted) C.BG else C.TEXT, iconSize = 20f, gap = 8f) { ctl.setMuted(!muted) },
            WRAP, 48.u)
    }
    if (ctl != null && ctl.canEnd) {
        add(pill(getString(R.string.end), null, h = 48f, padH = 22f, size = 16f, fill = C.RED, fg = C.BG, stroke = 0, face = F.semi, gap = 8f) { ctl.end() }
            .also { it.addView(IconView(context, Icons.PHONE, C.BG, 2f).apply { rotation = 135f }, 0, LinearLayout.LayoutParams(20.u, 20.u)) },
            WRAP, 48.u)
    } else {
        // Commands not verified: hand over to the head unit's phone app.
        add(pill(getString(R.string.open_phone_app), Icons.PHONE, h = 48f, padH = 18f, size = 16f, iconSize = 20f, gap = 8f) { launchRole(Role.CALL) },
            WRAP, 48.u)
    }
}

fun MainActivity.dashHeader(): View = row().apply {
    val left = add(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; dividerDrawable = gapDrawable(18f); showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE })
    val time = left.add(BigText(context, 56f, -0.04f, 1f, C.TEXT))
    val date = left.add(text("", 20f, color = C.MUTED))
    onTime += { val (t, s) = timeText(); time.text = t; time.suffix = s; date.text = dateText(false) }
    spring()
    if (Hub.call.phase == CallPhase.ACTIVE) { add(callBar(), WRAP, 64.u); return@apply }
    val right = add(row(12f))
    right.add(phoneChip(), WRAP, 44.u)
    right.add(pill(getString(R.string.clock), Icons.CLOCK) { push(Screen.CLOCK) }, WRAP, 56.u)
    right.add(frame().apply {
        background = pressable(0, C.LINE2, 28f)
        contentDescription = getString(R.string.settings)
        add(IconView(context, Icons.SLIDERS, C.TEXT), 24.u, 24.u, gravity = Gravity.CENTER)
        onTap { push(Screen.SETTINGS) }
    }, 56.u, 56.u)
}

// ---------------------------------------------------------------- tiles

fun MainActivity.tileView(i: Int, t: TileInfo): View = col().apply {
    val b = Hub.layoutB
    background = pressable(C.CARD, if (t.accent) C.accent else C.LINE, 20f)
    if (b) pad(22f, 24f) else pad(22f)
    val iconColor = if (t.accent) C.accent else if (t.installed) C.TEXT else C.MUTED
    val box = if (t.bigIcon) 120f else if (b) 72f else 64f
    add(iconBox(box, if (b) 18f else 16f, t.glyph, box / 2, iconColor, appIcon = t.icon), box.u, box.u)
    spring()
    val texts = add(col(if (b) 8f else 6f), MATCH, WRAP)
    texts.add(title(t.title, if (b) 30f else 28f, -0.02f, lineHeight = 1.1f), MATCH, WRAP)
    texts.add(text(t.line, 17f, color = if (t.installed) C.TEXT2 else C.accent))
    texts.add(caps(t.sub))
    contentDescription = t.title
    onTap { tileClicked(i, t) }
    setOnLongClickListener { openOverlay(Overlay.Hold(i)); true } // a consumed long press never also launches
    onSecondary { openOverlay(Overlay.Hold(i)) }
}

fun MainActivity.tileClicked(i: Int, t: TileInfo) {
    val e = t.entry
    if (e == null) openOverlay(Overlay.Missing(i)) else open(e)
}

fun MainActivity.appsTile(): View = col().apply {
    val b = Hub.layoutB
    background = pressable(C.CARD, C.LINE, 20f)
    if (b) pad(22f, 24f) else pad(22f)
    val box = if (b) 72f else 64f
    add(iconBox(box, if (b) 18f else 16f, Icons.GRID, if (b) 36f else 32f, C.TEXT), box.u, box.u)
    spring()
    val texts = add(col(if (b) 8f else 6f), MATCH, WRAP)
    texts.add(title(getString(R.string.apps), if (b) 30f else 28f, -0.02f, lineHeight = 1.1f), MATCH, WRAP)
    texts.add(text(getString(R.string.apps_line), 17f, color = C.TEXT2))
    val n = Hub.apps.list.size
    texts.add(caps(if (Hub.apps.loaded) resources.getQuantityString(R.plurals.app_count, n, n) else getString(R.string.apps_loading)))
    onTap { appsOnlyDash = false; push(Screen.APPS) }
}

fun MainActivity.addTile(free: Int): View = col().apply {
    val b = Hub.layoutB
    background = pressable(0, C.LINE2, 20f, dashed = true)
    if (b) pad(22f, 24f) else pad(22f)
    val box = if (b) 72f else 64f
    add(iconBox(box, if (b) 18f else 16f, Icons.PLUS, if (b) 32f else 30f, C.TEXT2, fill = 0, dashed = true, stroke = C.LINE2), box.u, box.u)
    spring()
    val texts = add(col(if (b) 8f else 6f), MATCH, WRAP)
    texts.add(title(getString(R.string.add_to_dashboard), if (b) 26f else 24f, -0.02f, lineHeight = 1.1f), MATCH, WRAP)
    texts.add(caps(if (b) getString(R.string.free_slot) else resources.getQuantityString(R.plurals.slots_free, free, free)))
    onTap { appsOnlyDash = false; push(Screen.APPS) }
}

/** Equal-cell grid that fills its parent (cells.size <= cols * rows). */
fun MainActivity.grid(cells: List<View>, cols: Int, rows: Int, gap: Float): LinearLayout = col(gap).apply {
    for (r in 0 until rows) {
        val line = add(row(gap).apply { gravity = Gravity.NO_GRAVITY }, MATCH, 0, 1f)
        for (c in 0 until cols) line.add(cells.getOrNull(r * cols + c) ?: View(context), 0, MATCH, 1f)
    }
}

/** Grid with fixed row height inside a vertical scroll view (apps, picker). */
fun MainActivity.scrollGrid(cells: List<View>, cols: Int, gap: Float, rowH: Float): ScrollView = ScrollView(this).apply {
    isVerticalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    val body = add(col(gap), MATCH, WRAP)
    var i = 0
    while (i < cells.size) {
        val line = body.add(row(gap).apply { gravity = Gravity.NO_GRAVITY }, MATCH, rowH.u)
        for (c in 0 until cols) line.add(cells.getOrNull(i + c) ?: View(context), 0, MATCH, 1f)
        i += cols
    }
}

// ---------------------------------------------------------------- dashboards

fun MainActivity.dashboard(): View = col(20f).apply {
    pad(32f, 28f)
    val b = Hub.layoutB
    add(dashHeader(), MATCH, 64.u)
    val favs = Hub.favorites()
    val max = Hub.max
    val tiles = favs.mapIndexed { i, k -> tileView(i, tileInfo(k)) }
    val empties = (favs.size until max).map { addTile(max - favs.size) }
    val widget = Hub.prefs.widget
    if (!b) {
        val body = add(row(20f).apply { gravity = Gravity.NO_GRAVITY }, MATCH, 0, 1f)
        if (widget) body.add(customizable(VehicleWidget(this@dashboard, VehicleWidget.TALL).view), 380.u, MATCH)
        body.add(grid(tiles + appsTile() + empties, 3, 2, 20f), 0, MATCH, 1f)
    } else {
        if (widget) add(customizable(VehicleWidget(this@dashboard, VehicleWidget.WIDE).view), MATCH, 168.u)
        add(grid(tiles + empties + appsTile(), 5, 1, 16f), MATCH, 0, 1f)
    }
}

/** Holding the widget (or right-clicking it) opens the chooser for its values. */
private fun MainActivity.customizable(v: View): View = v.apply {
    setOnLongClickListener { push(Screen.WIDGET); true }
    onSecondary { push(Screen.WIDGET) }
}

// ---------------------------------------------------------------- overlays

private fun MainActivity.dialogBox(pad: Float, gap: Float): LinearLayout = col(gap).apply {
    background = shape(C.CARD, C.LINE2, 24f)
    pad(pad)
}

private fun MainActivity.menuRow(label: Int, icon: String, onClick: () -> Unit): View = row(14f).apply {
    background = pressable(C.RAISED, 0, 16f)
    pad(20f, 0f)
    add(IconView(context, icon, C.TEXT), 24.u, 24.u)
    add(text(getString(label), 18f, F.medium))
    onTap(onClick)
}

fun MainActivity.holdMenu(i: Int): View = dialogBox(24f, 8f).apply {
    val key = Hub.favorites().getOrNull(i) ?: return@apply
    val t = tileInfo(key)
    val head = add(row(16f), MATCH, WRAP)
    head.add(iconBox(56f, 14f, t.glyph, 28f, C.TEXT, appIcon = t.icon), 56.u, 56.u)
    val names = head.add(col(4f))
    names.add(text(t.title, 26f, F.semi, track = -0.02f))
    names.add(caps(getString(R.string.opens, t.entry?.label ?: t.missingName)))
    head.margins(b = 12f)
    add(menuRow(R.string.replace, Icons.SWAP) { openOverlay(Overlay.Picker(i)) }, MATCH, 64.u)
    add(menuRow(R.string.remove_from_dashboard, Icons.REMOVE) {
        Hub.setFavorites(Favorites.remove(Hub.favorites(), key))
        closeOverlay()
        toast(getString(R.string.removed, t.title))
    }, MATCH, 64.u)
    add(menuRow(R.string.edit_dashboard, Icons.EDIT) { push(Screen.EDIT) }, MATCH, 64.u)
    add(pill(getString(R.string.cancel), radius = 16f) { closeOverlay() }, MATCH, 56.u).margins(t = 4f)
}

fun MainActivity.picker(o: Overlay.Picker): View = dialogBox(24f, 20f).apply {
    val favs = Hub.favorites()
    val role = o.role
    val currentKey = if (role != null) Hub.apps.targetKey(role) else favs.getOrNull(o.index)?.let { Hub.apps.resolve(it) }
    val title = if (role != null) getString(R.string.choose_target, roleName(role))
        else getString(R.string.replace_title, favs.getOrNull(o.index)?.let { tileInfo(it).title } ?: "")
    val head = add(row(), MATCH, 56.u)
    val names = head.add(col(4f))
    names.add(text(title, 28f, F.semi, track = -0.02f))
    names.add(caps(getString(if (role != null) R.string.choose_target_hint else R.string.pick_hint)))
    head.spring()
    if (role != null && Hub.prefs.mapping(role) != null) {
        head.add(pill(getString(R.string.use_default), h = 56f) { Hub.prefs.setMapping(role, null); Welle.bind(); closeOverlay(); Hub.changed(Hub.CONFIG) }, WRAP, 56.u)
        head.add(View(context), 12.u, 1)
    }
    head.add(pill(getString(R.string.cancel)) { closeOverlay() }, WRAP, 56.u)

    val onDash = favs.map { Hub.apps.resolve(it) }.toSet()
    val cells = Hub.apps.list.map { e ->
        val current = e.key == currentKey
        val taken = role == null && !current && e.key in onDash
        val off = current || taken
        row(14f).apply {
            background = if (off) shape(C.RAISED, if (current) C.accent else C.LINE, 18f) else pressable(C.RAISED, C.LINE, 18f)
            pad(16f, 0f)
            val g = glyphFor(e)
            add(iconBox(56f, 14f, g, 28f, if (taken) C.FAINT else C.TEXT, fill = C.CARD, appIcon = if (g == null) e.icon else null)
                .apply { if (g == null && taken) alpha = 0.4f }, 56.u, 56.u)
            val t = add(col(4f), 0, WRAP, 1f)
            t.add(text(e.label, 19f, F.medium, if (taken) C.FAINT else C.TEXT))
            t.add(caps(when {
                current -> getString(if (role != null) R.string.current_target else R.string.this_tile)
                taken -> getString(R.string.on_dashboard)
                else -> Hub.apps.category(context, e)
            }, 11f))
            if (!off) onTap {
                if (role != null) {
                    Hub.prefs.setMapping(role, e.key); Welle.bind(); closeOverlay(); Hub.changed(Hub.CONFIG)
                } else {
                    // Replace in place: same slot, same count; name, icon, category and target follow the app.
                    Hub.setFavorites(Favorites.replace(favs, o.index, Hub.apps.favoriteKeyFor(e)))
                    closeOverlay()
                    toast(getString(R.string.now_on_dashboard, e.label))
                }
            }
        }
    }
    if (cells.isEmpty()) add(para(getString(R.string.no_apps_found), 18f), MATCH, 0, 1f)
    else add(scrollGrid(cells, 3, 12f, (minOf(620f, Ui.H - 40) - 48 - 56 - 20 - 3 * 12) / 4), MATCH, 0, 1f) // four rows visible
}

/** Values a place can show: what CanService supplies first; OBD-only values listed but disabled. */
fun MainActivity.metricPicker(slot: Int): View = dialogBox(24f, 20f).apply {
    val current = Hub.prefs.widgetSlots[slot]
    val head = add(row(), MATCH, 56.u)
    val names = head.add(col(4f), 0, WRAP, 1f)
    names.add(text(getString(R.string.slot_shows, getString(slotName(slot))), 28f, F.semi, track = -0.02f))
    names.add(caps(getString(R.string.slot_hint)))
    head.add(pill(getString(R.string.cancel)) { closeOverlay() }, WRAP, 56.u)
    val choices = Metric.values().filter { WidgetSlots.allowed(slot, it) }
        .sortedBy { if (Hub.demo || it in CanService.SUPPORTED) 0 else 1 }
    val cells = choices.map { m ->
        val on = m == current
        val ok = Hub.demo || m in CanService.SUPPORTED
        col(4f).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = if (on || !ok) shape(C.RAISED, if (on) C.accent else C.LINE, 18f) else pressable(C.RAISED, C.LINE, 18f)
            pad(18f, 0f)
            add(text(getString(m.title), 19f, F.medium, if (ok) C.TEXT else C.FAINT))
            add(caps(when {
                on -> getString(R.string.current_target)
                !ok -> getString(R.string.needs_obd)
                m.reading == null -> getString(R.string.doors_hint)
                else -> getString(m.unit, "").trim()
            }, 11f))
            if (ok && !on) onTap {
                Hub.prefs.widgetSlots = Hub.prefs.widgetSlots.toMutableList().also { it[slot] = m }
                closeOverlay()
                Hub.changed(Hub.CONFIG)
            }
        }
    }
    add(scrollGrid(cells, 3, 12f, 84f), MATCH, WRAP)
}

fun slotName(slot: Int) = when (slot) {
    WidgetSlots.MAIN -> R.string.slot_main
    WidgetSlots.BAR -> R.string.slot_bar
    WidgetSlots.BELOW -> R.string.slot_below
    WidgetSlots.TILE1 -> R.string.slot_tile1
    WidgetSlots.TILE1 + 1 -> R.string.slot_tile2
    WidgetSlots.TILE1 + 2 -> R.string.slot_tile3
    else -> R.string.slot_tile4
}

fun MainActivity.missingDialog(i: Int): View = dialogBox(28f, 16f).apply {
    val key = Hub.favorites().getOrNull(i) ?: return@apply
    val t = tileInfo(key)
    add(iconBox(64f, 16f, t.glyph ?: Icons.APP, 32f, C.MUTED), 64.u, 64.u)
    add(text(getString(R.string.missing_title, t.missingName), 30f, F.semi, track = -0.02f).apply { setSingleLine(false); maxLines = 2 })
    add(para(getString(R.string.missing_body, getString(R.string.installer_name)), 18f, lineHeight = 1.45f), MATCH, WRAP)
    val buttons = add(row(12f), MATCH, 60.u)
    buttons.margins(t = 8f)
    buttons.add(pill(getString(R.string.choose_another), h = 60f, fill = C.TEXT, fg = C.BG, stroke = 0, radius = 16f) { openOverlay(Overlay.Picker(i)) }, 0, 60.u, 1f)
    buttons.add(pill(getString(R.string.ok), h = 60f, radius = 16f) { closeOverlay() }, 120.u, 60.u)
}

fun MainActivity.incomingCard(): View = row(22f).apply {
    val call = Hub.call
    val ctl = Hub.callControl
    background = shape(C.CARD, C.LINE2, 28f)
    pad(28f, 0f)
    isClickable = true
    add(frame().apply {
        background = shape(C.RAISED, radius = 42f)
        add(IconView(context, Icons.PERSON, C.TEXT2, 1.5f), 40.u, 40.u, gravity = Gravity.CENTER)
    }, 84.u, 84.u)
    val t = add(col(6f), 0, WRAP, 1f)
    t.add(caps(getString(R.string.incoming_call), 13f, C.GREEN))
    t.add(text(call.caller ?: getString(R.string.call_unknown), 34f, F.semi, track = -0.02f))
    t.add(text(call.detail ?: getString(R.string.call_no_number), 17f, color = C.MUTED))
    fun big(label: Int, color: Int, rotate: Boolean, f: () -> Unit) =
        pill(getString(label), null, h = 76f, padH = 30f, size = 20f, fill = color, fg = C.BG, stroke = 0, face = F.semi) { f() }.also {
            it.addView(IconView(context, Icons.PHONE, C.BG, 2f).apply { if (rotate) rotation = 135f }, 0, LinearLayout.LayoutParams(26.u, 26.u))
        }
    if (ctl != null && ctl.canDecline) add(big(R.string.decline, C.RED, true) { ctl.decline() }, WRAP, 76.u)
    if (ctl != null && ctl.canAnswer) add(big(R.string.accept, C.GREEN, false) { ctl.answer() }, WRAP, 76.u)
    if (ctl == null || (!ctl.canAnswer && !ctl.canDecline)) {
        val hint = add(col(8f))
        hint.add(text(getString(R.string.answer_elsewhere), 17f, color = C.MUTED))
        hint.add(pill(getString(R.string.open_phone_app), Icons.PHONE, h = 56f) { launchRole(Role.CALL) }, WRAP, 56.u)
    }
}

fun roleGlyph(r: Role) = when (r) {
    Role.RADIO -> Icons.RADIO
    Role.AA -> Icons.AA
    Role.CALL -> Icons.PHONE
    Role.FILES -> Icons.FOLDER
    Role.VEHICLE -> Icons.CAR
}

/** Reference style for the known role apps (outlined glyph); every other app shows its real icon. */
fun glyphFor(e: AppEntry): String? =
    Hub.apps.quickRoleOf(e)?.let { roleGlyph(it) } ?: if (Hub.isWidgetApp(e)) Icons.CAR else if (e.icon == null) Icons.APP else null

fun MainActivity.roleName(r: Role): String = getString(when (r) {
    Role.RADIO -> R.string.tile_radio
    Role.AA -> R.string.tile_aa
    Role.CALL -> R.string.tile_call
    Role.FILES -> R.string.tile_files
    Role.VEHICLE -> R.string.role_vehicle
})

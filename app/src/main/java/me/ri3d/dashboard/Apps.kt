package me.ri3d.dashboard

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import java.text.Collator

class AppEntry(val cn: ComponentName, val label: String, val icon: Drawable?) {
    val key: String = cn.flattenToShortString()
    val pkg: String get() = cn.packageName
}

/**
 * Installed launchable apps (MAIN/LAUNCHER, read on a background thread) and the role
 * targets. Defaults come from the packages found in the supplied projects and device capture;
 * every role can be remapped in Settings › Apps & integrations.
 */
class AppIndex(private val ctx: Context) {
    @Volatile var list: List<AppEntry> = emptyList(); private set
    @Volatile var loaded = false; private set
    /** Package that looks like CanService (installed, launchable or not); null = not found. */
    @Volatile var canServicePkg: String? = null; private set
    private var dialComponent: String? = null
    /** Debug fixtures can hide packages to exercise "Not installed" without uninstalling. */
    @Volatile var hidden: Set<String> = emptySet()
    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var again = false

    fun refresh() {
        if (running) { again = true; return }
        running = true
        Thread({
            val result = try { load() } catch (e: RuntimeException) { null } // package manager died: keep the old list
            main.post {
                running = false
                if (result != null) { list = result; loaded = true; rememberLabels(); Welle.bind(); Hub.changed(Hub.APPS) }
                if (again) { again = false; refresh() }
            }
        }, "app-index").start()
    }

    private fun load(): List<AppEntry> {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val own = ctx.packageName
        val skip = hidden
        val out = ArrayList<AppEntry>()
        for (ri in pm.queryIntentActivities(launcher, 0)) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == own || ai.packageName in skip) continue
            val icon = try { ri.loadIcon(pm) } catch (e: RuntimeException) { null }
            out.add(AppEntry(ComponentName(ai.packageName, ai.name), ri.loadLabel(pm).toString().trim(), icon))
        }
        val collator = Collator.getInstance()
        out.sortWith(Comparator { a, b -> collator.compare(a.label, b.label) })

        dialComponent = pm.resolveActivity(Intent(Intent.ACTION_DIAL), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.takeIf { it.packageName != "android" && it.packageName !in skip } // "android" = chooser
            ?.let { ComponentName(it.packageName, it.name).flattenToShortString() }
        @SuppressLint("QueryPermissionsNeeded") // API 30+ visibility does not apply to the API 17 head unit
        val installed = pm.getInstalledApplications(0)
        canServicePkg = installed.map { it.packageName }
            .firstOrNull { it !in skip && CAN_RE.containsMatchIn(it) }
        return out
    }

    private fun rememberLabels() {
        val p = Hub.prefs
        for (b in listOf(false, true)) for (k in p.favorites(b)) {
            val role = Role.of(k)
            val key = if (role != null) targetKey(role) else k
            find(key)?.let { p.setLabel(key!!, it.label) }
        }
        for (r in Role.values()) targetKey(r)?.let { k -> find(k)?.let { p.setLabel(k, it.label) } }
    }

    fun find(key: String?): AppEntry? = if (key == null) null else list.firstOrNull { it.key == key }

    private fun launcherOf(pkg: String): AppEntry? = list.firstOrNull { it.pkg == pkg }
    private fun firstMatch(re: Regex): AppEntry? = list.firstOrNull { re.containsMatchIn(it.pkg) || re.containsMatchIn(it.label) }

    /** Component key a role opens: the explicit mapping, else the discovered default. */
    fun targetKey(r: Role): String? = Hub.prefs.mapping(r) ?: defaultTarget(r)?.key

    fun target(r: Role): AppEntry? = find(targetKey(r))

    private fun defaultTarget(r: Role): AppEntry? = when (r) {
        Role.RADIO -> launcherOf(WELLE)
        Role.AA -> launcherOf(OPENAUTO)
        Role.CALL -> dialComponent?.let { d -> find(d) ?: launcherOf(ComponentName.unflattenFromString(d)!!.packageName) }
            ?: firstMatch(PHONE_RE)
        Role.FILES -> launcherOf(ES_FILE_EXPLORER) ?: firstMatch(FILES_RE)
        Role.VEHICLE -> canServicePkg?.let { launcherOf(it) } ?: firstMatch(CAN_RE)
    }

    /** Role whose target is this app (the vehicle role is the widget, not a tile). */
    fun quickRoleOf(e: AppEntry): Role? = Role.QUICK.firstOrNull { targetKey(it) == e.key }

    /** The favorite key that represents this app on a dashboard. */
    fun favoriteKeyFor(e: AppEntry): String = quickRoleOf(e)?.key ?: e.key

    /** Component a favorite key resolves to (role keys go through the mapping). */
    fun resolve(favKey: String): String? = Role.of(favKey)?.let { targetKey(it) } ?: favKey

    /** Display category; Android 4.x has no category API, so this is a role/package heuristic. */
    fun category(c: Context, e: AppEntry): String {
        quickRoleOf(e)?.let { return c.getString(roleCategory(it)) }
        if (e.key == targetKey(Role.VEHICLE)) return c.getString(R.string.cat_vehicle)
        val s = e.pkg + " " + e.label
        for ((re, res) in CATEGORIES) if (re.containsMatchIn(s)) return c.getString(res)
        return c.getString(R.string.cat_app)
    }

    fun launch(c: Context, key: String?): Boolean {
        val e = find(key) ?: return false
        return try {
            c.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(e.cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            true
        } catch (x: RuntimeException) { // ActivityNotFound, SecurityException (not exported)
            false
        }
    }

    companion object {
        const val WELLE = "me.ri3d.welle"
        const val OPENAUTO = "me.ri3d.openauto"
        /** ES File Explorer: seen running on the head unit (capture 20261003-143028). */
        const val ES_FILE_EXPLORER = "com.estrongs.android.pop"
        val CAN_RE = Regex("(^|\\.)can(service|bus|box)?(\\.|$)|canservice|can service|canbus", RegexOption.IGNORE_CASE)
        val PHONE_RE = Regex("dialer|phone|telefon|btphone|bt\\.?call|handsfree|hands-free", RegexOption.IGNORE_CASE)
        val FILES_RE = Regex("file ?(manager|explorer|browser)|filemanager|fileexplorer|explorer|\\bfiles\\b", RegexOption.IGNORE_CASE)
        private val CATEGORIES = listOf(
            Regex("music|audio|player|radio|podcast", RegexOption.IGNORE_CASE) to R.string.cat_media,
            Regex("video|movie|tube", RegexOption.IGNORE_CASE) to R.string.cat_media,
            Regex("gallery|photo|camera|picture", RegexOption.IGNORE_CASE) to R.string.cat_photos,
            Regex("equali[sz]er|\\beq\\b|sound|dsp", RegexOption.IGNORE_CASE) to R.string.cat_sound,
            Regex("bluetooth|setting|\\.bt\\b", RegexOption.IGNORE_CASE) to R.string.cat_system,
            Regex("browser|chrome|firefox|web", RegexOption.IGNORE_CASE) to R.string.cat_web,
            Regex("map|navi|gps", RegexOption.IGNORE_CASE) to R.string.cat_navigation,
            Regex("file|explorer", RegexOption.IGNORE_CASE) to R.string.cat_files,
            Regex("dial|phone|contact|people|mms|message", RegexOption.IGNORE_CASE) to R.string.cat_calling,
            Regex("clock|calendar|calculator|recorder", RegexOption.IGNORE_CASE) to R.string.cat_tools,
        )

        fun roleCategory(r: Role) = when (r) {
            Role.RADIO -> R.string.cat_radio
            Role.AA -> R.string.cat_aa
            Role.CALL -> R.string.cat_calling
            Role.FILES -> R.string.cat_files
            Role.VEHICLE -> R.string.cat_vehicle
        }
    }
}

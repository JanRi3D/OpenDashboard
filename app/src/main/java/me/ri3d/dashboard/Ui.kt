package me.ri3d.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Palette from the reference (hex values in docs/HANDOFF.md). */
object C {
    const val BG = 0xFF050506.toInt()
    const val CARD = 0xFF0E0F11.toInt()
    const val CARD_DIM = 0xFF0A0B0C.toInt()
    const val RAISED = 0xFF15171A.toInt()
    const val PRESSED = 0xFF202328.toInt()
    const val LINE = 0xFF1F2125.toInt()
    const val LINE2 = 0xFF2C2F34.toInt()
    const val TEXT = 0xFFF4F5F7.toInt()
    const val TEXT2 = 0xFFD5D7DB.toInt()
    const val MUTED = 0xFF9EA1A8.toInt()
    const val FAINT = 0xFF7B7F87.toInt()
    const val GREEN = 0xFF30D158.toInt()
    const val RED = 0xFFFF453A.toInt()
    const val SCRIM = 0xB8050506.toInt()
    val ACCENTS = intArrayOf(0xFFFF9F0A.toInt(), 0xFF4DA3FF.toInt(), 0xFF30D158.toInt(), 0xFFF4F5F7.toInt())
    val accent get() = ACCENTS[Hub.prefs.accent.coerceIn(0, ACCENTS.size - 1)]
}

/** Bundled Geist / Geist Mono static instances (res/font needs API 26). */
object F {
    lateinit var regular: Typeface
    lateinit var medium: Typeface
    lateinit var semi: Typeface
    lateinit var mono: Typeface
    lateinit var monoMedium: Typeface
    fun load(c: Context) {
        fun t(n: String) = Typeface.createFromAsset(c.assets, "fonts/$n.ttf")
        regular = t("Geist-Regular"); medium = t("Geist-Medium"); semi = t("Geist-SemiBold")
        mono = t("GeistMono-Regular"); monoMedium = t("GeistMono-Medium")
    }
}

/**
 * Design units: the reference is drawn for 1280 x 720. One unit is s pixels with
 * s = min(width / 1280, height / 720), so the layout fits the real window without stretching;
 * extra width (the 8:3 head unit is 1920 x 720) goes to the flexible columns.
 */
object Ui {
    var s = 1f
    var W = 1280f // window width in units
    var H = 720f
    fun setWindow(w: Int, h: Int) {
        s = minOf(w / 1280f, h / 720f)
        W = w / s; H = h / s
    }
    fun px(u: Float) = Math.round(u * s)
    fun px(u: Int) = Math.round(u * s)
}

val Int.u get() = Ui.px(this)
val Float.u get() = Ui.px(this)
const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

// ---------------------------------------------------------------- backgrounds

fun shape(fill: Int, stroke: Int = 0, radius: Float = 0f, dashed: Boolean = false, strokeW: Float = 1f): Drawable =
    Box(fill, stroke, radius * Ui.s, maxOf(1, strokeW.u).toFloat(), dashed)

/**
 * Rounded box with an optional (dashed) border. GradientDrawable's 1 px stroke is drawn from a
 * texture at a half-pixel offset by HWUI on Android 4.x and smears over two pixels; here the
 * stroke is converted to a fill path (Paint.getFillPath) whose bounds sit on whole pixels.
 */
class Box(fill: Int, private val stroke: Int, private val radius: Float, private val strokeW: Float, private val dashed: Boolean) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stroke }
    private val rect = RectF()
    private val ring = Path()

    override fun onBoundsChange(b: Rect) {
        rect.set(b)
        ring.reset()
        if (stroke == 0) return
        val w = strokeW
        if (!dashed) {
            // Outer contour clockwise, inner counter-clockwise: the non-zero fill leaves a w-wide ring.
            ring.addRoundRect(RectF(b), radius, radius, Path.Direction.CW)
            val r = maxOf(0f, radius - w)
            ring.addRoundRect(RectF(b.left + w, b.top + w, b.right - w, b.bottom - w), r, r, Path.Direction.CCW)
            return
        }
        // Dashes as filled quads along the centre line (stroked or getFillPath() paths do not render
        // crisply, or at all, on HWUI 4.1); straight runs land on whole pixels.
        val h = w / 2
        val r = maxOf(0f, radius - h)
        val centre = Path().apply { addRoundRect(RectF(b.left + h, b.top + h, b.right - h, b.bottom - h), r, r, Path.Direction.CW) }
        val pm = PathMeasure(centre, true)
        val len = pm.length
        val dash = 4 * Ui.s; val gap = 3 * Ui.s // close to Chrome's 1 px dashed border
        val pos = FloatArray(2); val tan = FloatArray(2)
        val left = ArrayList<Float>(); val right = ArrayList<Float>()
        var d = 0f
        while (d < len) {
            val end = minOf(d + dash, len)
            left.clear(); right.clear()
            var t = d
            while (true) {
                pm.getPosTan(t, pos, tan)
                left.add(pos[0] - tan[1] * h); left.add(pos[1] + tan[0] * h)
                right.add(pos[0] + tan[1] * h); right.add(pos[1] - tan[0] * h)
                if (t >= end) break
                t = minOf(t + 1f, end)
            }
            ring.moveTo(left[0], left[1])
            for (i in 2 until left.size step 2) ring.lineTo(left[i], left[i + 1])
            for (i in right.size - 2 downTo 0 step 2) ring.lineTo(right[i], right[i + 1])
            ring.close()
            d += dash + gap
        }
    }

    override fun draw(canvas: Canvas) {
        if (fillPaint.color != 0) canvas.drawRoundRect(rect, radius, radius, fillPaint)
        if (stroke != 0) canvas.drawPath(ring, strokePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in API 29") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Background with a pressed state (lighter fill), for anything tappable. */
fun pressable(fill: Int, stroke: Int = 0, radius: Float = 0f, dashed: Boolean = false, pressedFill: Int = C.PRESSED): Drawable =
    StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), shape(pressedFill, stroke, radius, dashed))
        addState(intArrayOf(), shape(fill, stroke, radius, dashed))
    }

// ---------------------------------------------------------------- layout helpers

fun gapDrawable(gap: Float) = GradientDrawable().apply { setColor(0); setSize(gap.u, gap.u) }

fun Context.col(gap: Float = 0f): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    if (gap > 0) { dividerDrawable = gapDrawable(gap); showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE }
}

fun Context.row(gap: Float = 0f): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    if (gap > 0) { dividerDrawable = gapDrawable(gap); showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE }
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.frame(): FrameLayout = FrameLayout(this)

/** Adds [v] with LayoutParams; sizes are in pixels already (use .u) or MATCH/WRAP. */
fun <T : View> ViewGroup.add(v: T, w: Int = WRAP, h: Int = WRAP, weight: Float = 0f, gravity: Int = -1): T {
    val lp: ViewGroup.LayoutParams = when (this) {
        is LinearLayout -> LinearLayout.LayoutParams(w, h, weight).also { if (gravity != -1) it.gravity = gravity }
        is FrameLayout -> FrameLayout.LayoutParams(w, h).also { if (gravity != -1) it.gravity = gravity }
        else -> ViewGroup.LayoutParams(w, h)
    }
    addView(v, lp)
    return v
}

/** Flexible spacer (CSS justify-content: space-between). */
fun LinearLayout.spring(): View = add(View(context), if (orientation == LinearLayout.HORIZONTAL) 0 else 1,
    if (orientation == LinearLayout.HORIZONTAL) 1 else 0, 1f)

fun View.pad(h: Float, v: Float = h): View { setPadding(h.u, v.u, h.u, v.u); return this }
fun View.pad(l: Float, t: Float, r: Float, b: Float): View { setPadding(l.u, t.u, r.u, b.u); return this }
fun View.margins(l: Float = 0f, t: Float = 0f, r: Float = 0f, b: Float = 0f) {
    (layoutParams as ViewGroup.MarginLayoutParams).setMargins(l.u, t.u, r.u, b.u)
}

fun View.onTap(f: () -> Unit): View { isClickable = true; setOnClickListener { f() }; return this }

// ---------------------------------------------------------------- text

/** Single-line label; [size] in units, [track] in em (letter spacing needs API 21). */
fun Context.text(s: CharSequence, size: Float, face: Typeface = F.regular, color: Int = C.TEXT, track: Float = 0f): TextView =
    TextView(this).apply {
        text = s
        typeface = face
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size * Ui.s)
        includeFontPadding = false
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.END
        if (track != 0f && Build.VERSION.SDK_INT >= 21) letterSpacing = track
    }

/** Small uppercase technical label (Geist Mono, +0.08 em). */
fun Context.caps(s: CharSequence, size: Float = 12f, color: Int = C.MUTED, track: Float = 0.08f): TextView =
    text(s, size, F.mono, color, track).apply { isAllCaps = true }

/** Wrapping paragraph. */
fun Context.para(s: CharSequence, size: Float, color: Int = C.MUTED, lineHeight: Float = 1.4f): TextView =
    text(s, size, F.regular, color).apply {
        setSingleLine(false); maxLines = 4
        // CSS line-height: absolute line pitch = size * lineHeight (Android would multiply the font's own spacing).
        setLineSpacing(size * Ui.s * lineHeight - paint.fontSpacing, 1f)
    }

// ---------------------------------------------------------------- composite widgets

/** Rounded button: optional icon + label; [solid] = filled light pill. */
fun Context.pill(label: CharSequence?, icon: String? = null, h: Float = 56f, padH: Float = 24f, size: Float = 18f,
                 fill: Int = 0, fg: Int = C.TEXT, stroke: Int = C.LINE2, radius: Float = h / 2, iconSize: Float = 22f,
                 face: Typeface = F.medium, gap: Float = 10f, onClick: (() -> Unit)? = null): LinearLayout =
    row(gap).apply {
        gravity = Gravity.CENTER
        background = pressable(fill, stroke, radius, pressedFill = if (fill == C.TEXT) C.TEXT2 else C.PRESSED)
        pad(padH, 0f)
        minimumHeight = h.u
        if (icon != null) add(IconView(context, icon, fg), iconSize.u, iconSize.u)
        if (label != null) add(text(label, size, face, fg))
        if (onClick != null) onTap(onClick)
    }

/** Status chip with a dot (phone status, vehicle state). */
class Chip(c: Context, private val h: Float = 44f, size: Float = 13f, track: Float = 0.06f) : LinearLayout(c) {
    private val dot = View(c)
    val label = c.caps("", size, C.TEXT2, track)
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        dividerDrawable = gapDrawable(if (h < 40) 8f else 10f); showDividers = SHOW_DIVIDER_MIDDLE
        pad(if (h < 40) 12f else 16f, 0f)
        minimumHeight = h.u
        add(dot, 8.u, 8.u); add(label)
        set("", C.FAINT)
    }
    fun set(text: CharSequence, dotColor: Int, border: Int = C.LINE, textColor: Int = C.TEXT2) {
        label.text = text; label.setTextColor(textColor)
        dot.background = shape(dotColor, radius = 4f)
        background = shape(0, border, h / 2)
    }
}

/** Dark rounded square holding a glyph or a real app icon. */
fun Context.iconBox(size: Float, radius: Float, glyph: String?, glyphSize: Float, color: Int,
                    fill: Int = C.RAISED, appIcon: Drawable? = null, dashed: Boolean = false, stroke: Int = 0): FrameLayout =
    frame().apply {
        background = shape(fill, stroke, radius, dashed)
        if (appIcon != null) {
            add(ImageView(context).apply { setImageDrawable(appIcon); scaleType = ImageView.ScaleType.FIT_CENTER },
                (size * 0.66f).u, (size * 0.66f).u, gravity = Gravity.CENTER)
        } else if (glyph != null) {
            add(IconView(context, glyph, color), glyphSize.u, glyphSize.u, gravity = Gravity.CENTER)
        }
    }

/** On/off switch, 72 x 44 units. */
fun Context.switch(on: Boolean, label: CharSequence, onToggle: () -> Unit): FrameLayout = frame().apply {
    background = shape(if (on) C.accent else C.LINE2, radius = 22f)
    pad(4f)
    contentDescription = label
    add(View(context).apply { background = shape(C.TEXT, radius = 18f) }, 36.u, 36.u,
        gravity = (if (on) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL)
    onTap(onToggle)
}

/** Segmented control (All apps / On dashboard, 1 min / 5 min / Never). */
fun Context.segmented(options: List<CharSequence>, selected: Int, padH: Float = 20f, onPick: (Int) -> Unit): LinearLayout =
    row(4f).apply {
        background = shape(0, C.LINE2, 28f)
        pad(4f)
        options.forEachIndexed { i, o ->
            val on = i == selected
            add(pill(o, h = 46f, padH = padH, size = 16f, fill = if (on) C.TEXT else 0, fg = if (on) C.BG else C.MUTED,
                stroke = 0) { onPick(i) }, WRAP, 46.u)
        }
    }

// ---------------------------------------------------------------- custom drawing

/** Outlined (or filled) 24 x 24 icon from SVG path data; the path is scaled, not the canvas, so it stays sharp on HWUI 4.x. */
class IconView(c: Context, d: String, color: Int, private val stroke: Float = 1.75f, private val filled: Boolean = false) : View(c) {
    private val src = cache.getOrPut(d) { toPath(d) }
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        this.color = color
    }
    var color: Int
        get() = paint.color
        set(v) { paint.color = v; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val k = minOf(w, h) / 24f
        path.reset()
        path.addPath(src, Matrix().apply { setScale(k, k); postTranslate((w - 24 * k) / 2, (h - 24 * k) / 2) })
        // Whole-pixel path bounds: HWUI 4.x draws path textures at the bounds' position, unfiltered only there.
        val b = RectF(); path.computeBounds(b, true)
        val half = stroke * k / 2
        path.offset(Math.round(b.left - half) - (b.left - half), Math.round(b.top - half) - (b.top - half))
        paint.strokeWidth = stroke * k
    }

    override fun onDraw(canvas: Canvas) { canvas.drawPath(path, paint) }

    companion object {
        private val cache = HashMap<String, Path>()
        fun toPath(d: String) = Path().also { p ->
            SvgPath.parse(d, object : SvgPath.Sink {
                override fun move(x: Float, y: Float) = p.moveTo(x, y)
                override fun line(x: Float, y: Float) = p.lineTo(x, y)
                override fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = p.cubicTo(x1, y1, x2, y2, x, y)
                override fun close() = p.close()
            })
        }
    }
}

/**
 * Single-line display text with CSS-like line height and letter spacing on every API level
 * (TextView letter spacing is API 21+). Used for the clocks and the big vehicle values.
 * [suffix] (AM/PM) is drawn at a third of the size on the same baseline.
 */
class BigText(c: Context, size: Float, private val track: Float, private val lineHeight: Float, color: Int,
              face: Typeface = F.semi) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = face; textSize = size * Ui.s; this.color = color
    }
    private val small = Paint(paint).apply { textSize = size * Ui.s * 0.32f; typeface = F.medium }
    private val base = paint.textSize
    /** Shrink (to 80 %) and then ellipsize when wider than the space given (long app names). */
    var fit = false
    private var shown = ""
    var text = ""
        set(v) { if (field != v) { field = v; shown = v; requestLayout(); invalidate() } }
    var suffix = ""
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }
    var color: Int
        get() = paint.color
        set(v) { paint.color = v; small.color = v; invalidate() }

    private fun spacing() = track * paint.textSize
    private fun mainWidth(s: String = shown): Float {
        var w = 0f
        for (ch in s) w += paint.measureText(ch.toString()) + spacing()
        return if (s.isEmpty()) 0f else w - spacing()
    }
    private fun totalWidth() = mainWidth() + if (suffix.isEmpty()) 0f else paint.textSize * 0.1f + small.measureText(suffix)
    private fun baselineY(): Float {
        val fm = paint.fontMetrics
        val box = paint.textSize * lineHeight
        return (box - (fm.descent - fm.ascent)) / 2 - fm.ascent
    }

    override fun getBaseline() = Math.round(baselineY())

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val avail = MeasureSpec.getSize(wSpec).toFloat()
        if (fit && MeasureSpec.getMode(wSpec) != MeasureSpec.UNSPECIFIED) {
            paint.textSize = base; shown = text
            val natural = mainWidth(text)
            if (natural > avail) paint.textSize = base * maxOf(0.8f, avail / natural)
            while (shown.length > 1 && mainWidth() > avail) shown = shown.dropLast(2) + "…"
        }
        setMeasuredDimension(resolveSize(Math.ceil(totalWidth().toDouble()).toInt(), wSpec),
            resolveSize(Math.round(paint.textSize * lineHeight), hSpec))
    }

    override fun onDraw(canvas: Canvas) {
        var x = 0f
        val y = baselineY()
        for (ch in shown) {
            val s = ch.toString()
            canvas.drawText(s, x, y, paint)
            x += paint.measureText(s) + spacing()
        }
        if (suffix.isNotEmpty()) canvas.drawText(suffix, x - spacing() + paint.textSize * 0.1f, y, small)
    }
}

/** Large single-line title with exact tracking on every API level. */
fun Context.title(s: String, size: Float, track: Float, color: Int = C.TEXT, lineHeight: Float = 1f): BigText =
    BigText(this, size, track, lineHeight, color).apply { text = s; fit = true }

/** Thin progress bar (fuel). */
class Bar(c: Context) : View(c) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.LINE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var color: Int
        get() = fill.color
        set(v) { fill.color = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val rad = height / 2f
        r.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(r, rad, rad, track)
        if (fraction > 0f) { r.right = width * fraction; canvas.drawRoundRect(r, rad, rad, fill) }
    }
}

/** 1-unit divider line. */
fun Context.divider(color: Int = C.LINE): View = View(this).apply { background = ColorDrawable(color) }

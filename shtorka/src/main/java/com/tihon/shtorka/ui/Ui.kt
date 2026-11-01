package com.tihon.shtorka.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.tihon.shtorka.R
import kotlin.math.roundToInt

fun Context.dp(v: Number): Float = v.toFloat() * resources.displayMetrics.density
fun View.dp(v: Number): Float = context.dp(v)
fun View.dpi(v: Number): Int = context.dp(v).roundToInt()

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** Резиновое растяжение, как у UIScrollView: чем дальше, тем туже. */
fun rubber(x: Float, limit: Float): Float =
    if (x <= 0f) 0f else limit * (1f - 1f / (1f + x / limit))

fun withAlpha(color: Int, alpha: Float): Int =
    (color and 0x00FFFFFF) or ((Color.alpha(color) * clamp01(alpha)).roundToInt() shl 24)

fun blend(from: Int, to: Int, t: Float): Int {
    val f = clamp01(t)
    fun ch(s: Int) = (((from shr s) and 0xFF) + ((((to shr s) and 0xFF) - ((from shr s) and 0xFF)) * f)).roundToInt()
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

/** Цвета системы iOS в тёмном оформлении. */
object Palette {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val BLUE = 0xFF0A84FF.toInt()
    const val GREEN = 0xFF30D158.toInt()
    const val ORANGE = 0xFFFF9F0A.toInt()
    const val RED = 0xFFFF453A.toInt()
    const val PURPLE = 0xFF5E5CE6.toInt()
    const val YELLOW = 0xFFFFD60A.toInt()
    const val GRAY = 0xFF8E8E93.toInt()
    const val GLYPH_ON_WHITE = 0xFF6E6E73.toInt()

    /** Плашка модуля поверх размытого фона. */
    const val GLASS = 0x30FFFFFF
    /** Неактивный кружок внутри плашки. */
    const val CIRCLE_OFF = 0x38FFFFFF
    const val TEXT_SECONDARY = 0x99FFFFFF.toInt()
}

object Fonts {
    var regular: Typeface = Typeface.DEFAULT; private set
    var medium: Typeface = Typeface.DEFAULT; private set
    var semibold: Typeface = Typeface.DEFAULT_BOLD; private set
    var bold: Typeface = Typeface.DEFAULT_BOLD; private set

    fun init(ctx: Context) {
        fun load(id: Int, fallback: Typeface) =
            runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: fallback
        regular = load(R.font.inter_regular, Typeface.DEFAULT)
        medium = load(R.font.inter_medium, Typeface.DEFAULT)
        semibold = load(R.font.inter_semibold, Typeface.DEFAULT_BOLD)
        bold = load(R.font.inter_bold, Typeface.DEFAULT_BOLD)
    }
}

fun Context.text(
    sizeSp: Float,
    face: Typeface = Fonts.semibold,
    color: Int = Palette.WHITE,
    lines: Int = 1,
    gravity: Int = Gravity.START,
    tracking: Float = -0.01f,
): TextView = TextView(this).apply {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    typeface = face
    setTextColor(color)
    letterSpacing = tracking
    includeFontPadding = false
    this.gravity = gravity
    // maxLines, а не singleLine: singleLine включает горизонтальную прокрутку,
    // и с gravity=CENTER текст уезжает за край.
    maxLines = lines
    ellipsize = android.text.TextUtils.TruncateAt.END
    // WRAP_CONTENT по умолчанию: иначе FrameLayout выдаст MATCH_PARENT, и при смене
    // текста TextView не попросит перемерить себя — ширина останется старой.
    layoutParams = android.view.ViewGroup.LayoutParams(
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}

/** Подобрать размер шрифта, чтобы строка влезла в ширину (как adjustsFontSizeToFitWidth). */
fun TextView.fitWidth(maxWidth: Int, maxSp: Float, minSp: Float) {
    if (maxWidth <= 0) return
    val p = android.text.TextPaint(paint)
    val dm = resources.displayMetrics
    var sp = maxSp
    val t = text?.toString().orEmpty()
    while (sp > minSp) {
        p.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm)
        if (p.measureText(t) <= maxWidth) break
        sp -= 0.5f
    }
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.coerceAtLeast(minSp))
}

/**
 * Стеклянная плашка: полупрозрачная заливка и тонкий светлый ободок,
 * который ярче сверху — как блики на модулях iOS.
 */
class Glass(ctx: Context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(1f)
        shader = LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(0x66FFFFFF, 0x14FFFFFF, 0x0AFFFFFF, 0x24FFFFFF),
            floatArrayOf(0f, 0.35f, 0.75f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val m = Matrix()
    private val r = RectF()

    fun draw(c: Canvas, rect: RectF, radius: Float, color: Int = Palette.GLASS, rimAlpha: Float = 1f) {
        fill.color = color
        c.drawRoundRect(rect, radius, radius, fill)
        if (rimAlpha <= 0.01f) return
        val half = rim.strokeWidth / 2f
        r.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)
        m.setScale(1f, rect.height())
        m.postTranslate(0f, rect.top)
        rim.shader.setLocalMatrix(m)
        rim.alpha = (255 * clamp01(rimAlpha)).roundToInt()
        c.drawRoundRect(r, radius - half, radius - half, rim)
    }
}

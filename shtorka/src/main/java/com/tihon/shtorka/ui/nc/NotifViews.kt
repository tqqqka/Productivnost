package com.tihon.shtorka.ui.nc

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.tihon.shtorka.R
import com.tihon.shtorka.system.NotifRepo
import com.tihon.shtorka.ui.ArtView
import com.tihon.shtorka.ui.Fonts
import com.tihon.shtorka.ui.Glass
import com.tihon.shtorka.ui.GlyphButton
import com.tihon.shtorka.ui.Haptics
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.dp
import com.tihon.shtorka.ui.dpi
import com.tihon.shtorka.ui.rubber
import com.tihon.shtorka.ui.text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

const val CARD_FILL = 0x2EFFFFFF

/** «сейчас», «5 мин назад», «2 ч назад», «вчера», «12 окт.» */
fun ago(t: Long, now: Long = System.currentTimeMillis()): String {
    val d = (now - t).coerceAtLeast(0)
    val min = d / 60_000
    return when {
        min < 1 -> "сейчас"
        min < 60 -> "$min мин назад"
        min < 60 * 12 -> "${min / 60} ч назад"
        min < 60 * 48 -> "вчера"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
    }
}

/** Карточка уведомления. Свайп в сторону — смахнуть, тап — открыть. */
@SuppressLint("ViewConstructor")
class NotifCard(ctx: Context) : LinearLayout(ctx) {
    var item: NotifRepo.Item? = null
        private set
    var onOpen: ((NotifRepo.Item) -> Unit)? = null
    var onDismiss: ((NotifRepo.Item) -> Unit)? = null
    var swiping = false
        private set

    private val glass = Glass(ctx)
    private val box = RectF()
    private val iconBox = FrameLayout(ctx)
    private val icon = ArtView(ctx).apply { radiusFraction = 0.24f; setPlaceholder(R.drawable.g_bell) }
    private val badge = ArtView(ctx).apply { radiusFraction = 0.24f; setPlaceholder(R.drawable.g_bell) }
    private val title: TextView = ctx.text(15f, Fonts.semibold)
    private val time: TextView = ctx.text(13f, Fonts.medium, Palette.TEXT_SECONDARY)
    private val body: TextView = ctx.text(15f, Fonts.regular, 0xEBFFFFFF.toInt(), lines = 4).apply {
        setLineSpacing(0f, 1.08f)
    }
    private val thumb = ArtView(ctx).apply { radiusFraction = 0.18f }

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var decided = false
    private var vt: VelocityTracker? = null
    private val springX = SpringAnimation(this, DynamicAnimation.TRANSLATION_X, 0f).apply {
        spring.stiffness = 600f
        spring.dampingRatio = 0.75f
    }

    init {
        orientation = HORIZONTAL
        setWillNotDraw(false)
        val p = dpi(14)
        setPadding(p, p, p, p)
        gravity = Gravity.TOP

        val s = dpi(38)
        iconBox.addView(icon, FrameLayout.LayoutParams(s, s))
        iconBox.addView(badge, FrameLayout.LayoutParams(dpi(18), dpi(18), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, -dpi(4), -dpi(4))
        })
        iconBox.clipChildren = false
        addView(iconBox, LayoutParams(s, s).apply { marginEnd = dpi(12); topMargin = dpi(1) })

        val col = LinearLayout(ctx).apply { orientation = VERTICAL }
        val row = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(time, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(8) })
        col.addView(row)
        col.addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(2) })
        addView(col, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(thumb, LayoutParams(dpi(42), dpi(42)).apply { marginStart = dpi(10) })
    }

    fun bind(it: NotifRepo.Item) {
        item = it
        val appIcon = NotifRepo.appIcon(context, it.pkg)
        val large = runCatching { it.largeIcon?.loadDrawable(context) }.getOrNull()
        if (it.isMessage && large != null) {
            // Сообщение: аватар собеседника и маленький значок приложения.
            icon.circle = true
            icon.setDrawable(large)
            badge.setDrawable(appIcon)
            badge.visibility = VISIBLE
            thumb.visibility = GONE
        } else {
            icon.circle = false
            icon.setDrawable(appIcon)
            badge.visibility = GONE
            thumb.visibility = if (large != null) VISIBLE else GONE
            thumb.setDrawable(large)
        }
        title.text = it.title
        time.text = ago(it.time)
        body.text = it.text
        body.visibility = if (it.text.isBlank()) GONE else VISIBLE
    }

    override fun onDraw(c: Canvas) {
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        glass.draw(c, box, dp(22), CARD_FILL, 0.8f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val it = item ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY
                decided = false
                swiping = false
                springX.cancel()
                vt?.recycle()
                vt = VelocityTracker.obtain()
                vt?.addMovement(e)
                animate().scaleX(0.97f).scaleY(0.97f).setDuration(120).start()
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!decided && (abs(dx) > slop || abs(dy) > slop)) {
                    decided = true
                    if (abs(dx) > abs(dy) * 1.2f) {
                        swiping = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }
                if (swiping) {
                    translationX = if (it.clearable) dx else rubber(abs(dx), dp(40)) * Math.signum(dx)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(180).start()
                vt?.addMovement(e)
                vt?.computeCurrentVelocity(1000)
                val vx = vt?.xVelocity ?: 0f
                vt?.recycle(); vt = null
                if (swiping) {
                    val dx = translationX
                    val fling = abs(vx) > 1400 && Math.signum(vx) == Math.signum(dx)
                    if (it.clearable && (abs(dx) > width * 0.4f || fling)) {
                        Haptics.tick(this)
                        animate().translationX(Math.signum(dx) * width * 1.15f).alpha(0f).setDuration(200)
                            .withEndAction { onDismiss?.invoke(it) }.start()
                    } else {
                        springX.animateToFinalPosition(0f)
                    }
                } else if (!decided && e.actionMasked == MotionEvent.ACTION_UP) {
                    onOpen?.invoke(it)
                }
                swiping = false
            }
        }
        return true
    }
}

/** Краешки карточек под верхней — стопка уведомлений одного приложения. */
class StackTail(ctx: Context) : View(ctx) {
    var layers = 1
    private val glass = Glass(ctx)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val step = dp(9)
        for (i in layers downTo 1) {
            val inset = dp(14) * i
            r.set(inset, -dp(30), w - inset, step * i)
            c.save()
            c.clipRect(0f, step * (i - 1), w, step * i)
            glass.draw(c, r, dp(18), if (i == 1) 0x24FFFFFF else 0x18FFFFFF, 0.5f)
            c.restore()
        }
    }
}

/** Группа уведомлений одного приложения: стопка или развёрнутый список. */
@SuppressLint("ViewConstructor")
class NotifGroup(ctx: Context, val pkg: String, private val host: NotificationCenterView) : LinearLayout(ctx) {
    private var items: List<NotifRepo.Item> = emptyList()
    private val cards = HashMap<String, NotifCard>()
    private val tail = StackTail(ctx)
    private val header = LinearLayout(ctx)
    private val headerTitle: TextView = ctx.text(20f, Fonts.bold)
    private val less = TextView(ctx)
    private val clear = GlyphButton(ctx, R.drawable.g_close, 0.5f, Palette.WHITE)

    val expanded get() = host.isExpanded(pkg)
    val swiping get() = cards.values.any { it.swiping }

    init {
        orientation = VERTICAL
        clipChildren = false
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(headerTitle, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        less.apply {
            text = "Свернуть"
            typeface = Fonts.semibold
            textSize = 13f
            setTextColor(Palette.WHITE)
            val ph = dpi(12)
            setPadding(ph, dpi(6), ph, dpi(6))
            background = pill()
            setOnClickListener { host.setExpanded(pkg, false) }
        }
        header.addView(less)
        clear.background = circleBg()
        clear.onTap = { host.dismiss(items) }
        header.addView(clear, LayoutParams(dpi(30), dpi(30)).apply { marginStart = dpi(8) })
        header.setPadding(dpi(6), 0, 0, dpi(10))
    }

    private fun pill() = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dp(20)
        setColor(0x33FFFFFF)
    }

    private fun circleBg() = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(0x33FFFFFF)
    }

    fun bind(list: List<NotifRepo.Item>) {
        items = list
        headerTitle.text = list.firstOrNull()?.app.orEmpty()
        val wanted = ArrayList<View>()
        val stacked = !expanded && list.size > 1
        if (expanded && list.size > 1) wanted += header
        val shown = if (expanded) list else list.take(1)
        cards.keys.filter { it !in list.map { i -> i.key } }.forEach { cards.remove(it) }
        shown.forEach { item ->
            val card = cards.getOrPut(item.key) { NotifCard(context) }
            card.bind(item)
            card.onOpen = { i -> if (!expanded && items.size > 1) host.setExpanded(pkg, true) else host.open(i) }
            card.onDismiss = { i -> if (!expanded && items.size > 1) host.dismiss(items) else host.dismiss(listOf(i)) }
            card.translationX = 0f
            card.alpha = 1f
            wanted += card
        }
        if (stacked) {
            tail.layers = if (list.size > 2) 2 else 1
            wanted += tail
        }
        // Убираем лишнее, ставим нужное по порядку — без пересоздания карточек.
        for (i in childCount - 1 downTo 0) {
            val v = getChildAt(i)
            if (v !in wanted) removeViewAt(i)
        }
        wanted.forEachIndexed { i, v ->
            if (indexOfChild(v) != i) {
                if (v.parent != null) removeView(v)
                val lp = when (v) {
                    tail -> LayoutParams(LayoutParams.MATCH_PARENT, dpi(9) * tail.layers)
                    header -> LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    else -> LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        if (i > 0 && expanded) topMargin = dpi(8)
                    }
                }
                addView(v, i, lp)
            } else if (v === tail) {
                v.layoutParams = v.layoutParams.apply { height = dpi(9) * tail.layers }
            }
        }
        tail.invalidate()
    }
}

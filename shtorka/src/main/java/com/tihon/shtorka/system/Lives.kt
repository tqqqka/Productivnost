package com.tihon.shtorka.system

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.tihon.shtorka.ui.Palette

/** Что может показывать островок. Приоритет — как iPhone выбирает главную активность. */
enum class Kind(val priority: Int, val transient: Boolean = false) {
    CALL_INCOMING(100), CALL(90), RECORDING(80), TIMER(70), NAVIGATION(60), STOPWATCH(55), MUSIC(50), PROGRESS(40),
    BANNER(0, true), CHARGING(0, true), LOW_BATTERY(0, true), RINGER(0, true), UNLOCK(0, true), HEADPHONES(0, true),
    DONE(0, true),
}

/** Кнопка в раскрытом островке: действие уведомления или своё. */
class Act(
    val title: String,
    val intent: PendingIntent?,
    val role: Role = Role.NORMAL,
    val run: (() -> Unit)? = null,
) {
    enum class Role { NORMAL, ACCEPT, DECLINE }
}

/** Одна «живая активность» — то, что iPhone называет Live Activity. */
class Live(
    val kind: Kind,
    val key: String,
    val pkg: String = "",
    val title: String = "",
    val text: String = "",
    val appIcon: Drawable? = null,
    val largeIcon: Drawable? = null,
    val art: Bitmap? = null,
    val accent: Int = Palette.WHITE,
    /** База времени в шкале elapsedRealtime; 0 — без времени. */
    val chronoBase: Long = 0L,
    val countDown: Boolean = false,
    /** 0..1 или -1. */
    val progress: Float = -1f,
    val indeterminate: Boolean = false,
    /** Короткое значение в компактном виде: «300 м», «4:59», «62%». */
    val value: String? = null,
    /** Навигация: стрелка манёвра и оставшееся время. */
    val navIcon: Drawable? = null,
    val eta: String? = null,
    val actions: List<Act> = emptyList(),
    val intent: PendingIntent? = null,
    val playing: Boolean = false,
    val level: Int = -1,
    val durationMs: Long = 0L,
) {
    /** Для сравнения «изменилось ли что-то видимое». */
    fun signature() = "$kind|$key|$title|$text|$value|$eta|${(progress * 100).toInt()}|$indeterminate|$chronoBase|$countDown|$playing|$level|${actions.size}|${art?.generationId}"
}

/** Текст, картинки и время из собственной вёрстки уведомления (RemoteViews). */
private class Scan(val texts: List<String>, val images: List<Drawable>, val chronoBase: Long, val countDown: Boolean)

/**
 * Живые активности из текущих уведомлений: звонки, таймеры и секундомеры,
 * запись экрана, навигация, загрузки. Музыка приходит отдельно, из MediaRepo.
 */
object LiveRepo {
    private val byKey = LinkedHashMap<String, Live>()
    private val listeners = LinkedHashSet<() -> Unit>()

    /** Загрузка закончилась — островок покажет галочку. */
    var onFinished: ((Live) -> Unit)? = null

    fun add(l: () -> Unit) { listeners += l }
    fun remove(l: () -> Unit) { listeners -= l }
    private fun changed() = listeners.toList().forEach { it() }

    val lives: List<Live> get() = byKey.values.toList()

    fun setAll(ctx: Context, all: Array<StatusBarNotification>) {
        byKey.keys.filter { !it.startsWith("demo:") }.forEach { byKey.remove(it) }
        all.forEach { sbn -> runCatching { classify(ctx, sbn) }.getOrNull()?.let { byKey[it.key] = it } }
        changed()
    }

    fun posted(ctx: Context, sbn: StatusBarNotification) {
        val l = runCatching { classify(ctx, sbn) }.getOrNull()
        val prev = byKey[sbn.key]
        if (l == null) {
            if (byKey.remove(sbn.key) != null) {
                finished(prev)
                changed()
            }
            return
        }
        byKey[l.key] = l
        if (prev == null || prev.signature() != l.signature()) changed()
    }

    fun removed(key: String) {
        val prev = byKey.remove(key) ?: return
        finished(prev)
        changed()
    }

    private fun finished(prev: Live?) {
        if (prev != null && prev.kind == Kind.PROGRESS && prev.progress >= 0.9f) onFinished?.invoke(prev)
    }

    fun putDemo(l: Live) { byKey[l.key] = l; changed() }

    // ---------- разбор уведомлений ----------

    private val DIALERS = setOf(
        "com.samsung.android.incallui", "com.samsung.android.dialer", "com.android.incallui",
        "com.google.android.dialer", "com.android.server.telecom", "com.android.dialer",
    )
    private val CLOCKS = setOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock")
    private val NAV = setOf(
        "com.google.android.apps.maps", "ru.yandex.yandexnavi", "ru.yandex.yandexmaps",
        "com.waze", "ru.dublgis.dgismobile", "com.here.app.maps", "com.mapswithme.maps.pro", "app.organicmaps",
    )
    private val RECORDERS = setOf(
        "com.samsung.android.app.smartcapture", "com.samsung.android.app.screenrecorder",
        "com.sec.android.app.screenrecorder", "com.android.systemui.screenrecord",
    )
    private val RECORD_WORDS = listOf("запись экрана", "идёт запись", "идет запись", "screen record", "recording screen")
    private val STOPWATCH_WORDS = listOf("секундомер", "stopwatch")
    private val TIME = Regex("""(?<![\d:])\d{1,2}:\d{2}(:\d{2})?(?![\d:])""")
    /** «300 м», «1,2 км», «0.5 mi» — без \b, он не понимает кириллицу. */
    private val DIST = Regex("""\d+(?:[.,]\d+)?\s?(?:км|м|km|mi|ft|yd|m)(?![\p{L}])""")
    private val ETA = Regex("""\d+\s?(?:ч|час|мин|h|hr|min)(?![\p{L}])(?:\s?\d+\s?(?:мин|min)(?![\p{L}]))?""")

    private val ANSWER = listOf("ответ", "принять", "answer", "accept")
    private val DECLINE = listOf("отклон", "сброс", "заверш", "decline", "reject", "hang up", "end call")

    private val icons = LruCache<String, Drawable>(32)

    fun appIcon(ctx: Context, pkg: String): Drawable? {
        if (pkg.isEmpty()) return null
        icons.get(pkg)?.let { return it }
        val d = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return null
        icons.put(pkg, d)
        return d
    }

    private fun Bundle.str(k: String) = getCharSequence(k)?.toString().orEmpty()

    private fun texts(ex: Bundle): List<String> {
        val out = ArrayList<String>()
        listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_INFO_TEXT, Notification.EXTRA_SUMMARY_TEXT,
        ).forEach { k -> ex.str(k).takeIf { it.isNotBlank() }?.let { out += it } }
        ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { out += it.toString() }
        return out
    }

    /** Разворачивает RemoteViews уведомления и собирает тексты, картинки и секундомер. */
    @Suppress("DEPRECATION")
    private fun scan(ctx: Context, n: Notification): Scan? {
        val rv = n.bigContentView ?: n.contentView ?: n.headsUpContentView ?: return null
        val v = runCatching { rv.apply(ctx, FrameLayout(ctx)) }.getOrNull() ?: return null
        val texts = ArrayList<String>()
        val images = ArrayList<Drawable>()
        var base = 0L
        var down = false
        fun walk(x: View) {
            if (x.visibility != View.VISIBLE) return
            when (x) {
                is Chronometer -> { base = x.base; down = x.isCountDown }
                is TextView -> x.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts += it }
                is ImageView -> x.drawable?.takeIf { it.intrinsicWidth > 0 }?.let { images += it }
                is ViewGroup -> for (i in 0 until x.childCount) walk(x.getChildAt(i))
            }
        }
        walk(v)
        return Scan(texts, images, base, down)
    }

    @Suppress("DEPRECATION")
    fun classify(ctx: Context, sbn: StatusBarNotification): Live? {
        val pkg = sbn.packageName
        if (pkg == ctx.packageName) return null
        val n = sbn.notification
        val ex = n.extras ?: Bundle()
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null

        val ongoing = n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_NO_CLEAR) != 0
        var title = ex.str(Notification.EXTRA_TITLE).ifBlank { ex.str(Notification.EXTRA_TITLE_BIG) }
        var text = ex.str(Notification.EXTRA_TEXT).ifBlank { ex.str(Notification.EXTRA_SUB_TEXT) }
        val allText = texts(ex).toMutableList()
        val lower = allText.joinToString(" ").lowercase()
        var chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        var countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        val pMax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val p = ex.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val callType = ex.getInt("android.callType", 0)
        val isCall = n.category == Notification.CATEGORY_CALL || pkg in DIALERS || callType != 0
        val isClock = pkg in CLOCKS
        val isNav = n.category == Notification.CATEGORY_NAVIGATION || pkg in NAV
        val isRec = pkg in RECORDERS || RECORD_WORDS.any { lower.contains(it) }
        var timeText = allText.firstNotNullOfOrNull { TIME.find(it)?.value }
        var base = if (n.`when` > 0) SystemClock.elapsedRealtime() + (n.`when` - System.currentTimeMillis()) else 0L

        // Своя вёрстка (часы Samsung, Яндекс/Google Карты): читаем её напрямую.
        var images: List<Drawable> = emptyList()
        if ((isClock || isRec || isNav) && (!chrono || isNav)) {
            scan(ctx, n)?.let { s ->
                allText += s.texts
                images = s.images
                if (s.chronoBase != 0L && !chrono) { chrono = true; base = s.chronoBase; countDown = s.countDown }
                if (timeText == null) timeText = s.texts.firstNotNullOfOrNull { TIME.find(it)?.value }
            }
        }
        // Время в будущем — это обратный отсчёт, даже если флаг не поставили.
        if (chrono && base > SystemClock.elapsedRealtime() + 1500) countDown = true

        val actions = ArrayList<Act>()
        n.actions?.forEach { a ->
            val t = a.title?.toString().orEmpty()
            val lt = t.lowercase()
            val role = when {
                ANSWER.any { lt.contains(it) } -> Act.Role.ACCEPT
                DECLINE.any { lt.contains(it) } -> Act.Role.DECLINE
                else -> Act.Role.NORMAL
            }
            if (t.isNotBlank()) actions += Act(t, a.actionIntent, role)
        }
        val answerPi = ex.getParcelable<PendingIntent>("android.answerIntent")
        val declinePi = ex.getParcelable<PendingIntent>("android.declineIntent")
        val hangUpPi = ex.getParcelable<PendingIntent>("android.hangUpIntent")

        val kind = when {
            isCall && (callType == 1 || (callType == 0 && !chrono &&
                (n.fullScreenIntent != null || actions.any { it.role == Act.Role.ACCEPT }))) -> Kind.CALL_INCOMING
            isCall && (ongoing || callType == 2) -> Kind.CALL
            isClock && (ongoing || chrono || timeText != null) ->
                if (STOPWATCH_WORDS.any { lower.contains(it) } || (chrono && !countDown)) Kind.STOPWATCH else Kind.TIMER
            !ongoing -> return null
            isRec -> Kind.RECORDING
            isNav -> Kind.NAVIGATION
            chrono -> if (countDown) Kind.TIMER else Kind.STOPWATCH
            pMax > 0 || indeterminate -> Kind.PROGRESS
            else -> return null
        }

        val callActs = when (kind) {
            Kind.CALL_INCOMING -> listOfNotNull(
                (declinePi ?: actions.firstOrNull { it.role == Act.Role.DECLINE }?.intent)?.let { Act("Отклонить", it, Act.Role.DECLINE) },
                (answerPi ?: actions.firstOrNull { it.role == Act.Role.ACCEPT }?.intent)?.let { Act("Ответить", it, Act.Role.ACCEPT) },
            )
            Kind.CALL -> listOfNotNull(
                (hangUpPi ?: actions.firstOrNull { it.role == Act.Role.DECLINE }?.intent)?.let { Act("Завершить", it, Act.Role.DECLINE) },
            )
            else -> null
        }

        val accent = when (kind) {
            Kind.CALL, Kind.CALL_INCOMING -> Palette.GREEN
            Kind.TIMER, Kind.STOPWATCH -> Palette.ORANGE
            Kind.RECORDING -> Palette.RED
            Kind.NAVIGATION -> Palette.WHITE
            else -> n.color.takeIf { it != 0 && it != Notification.COLOR_DEFAULT }?.let { it or 0xFF000000.toInt() } ?: Palette.BLUE
        }
        val useChrono = chrono || (kind == Kind.CALL && timeText == null)
        val large = runCatching { n.getLargeIcon()?.loadDrawable(ctx) }.getOrNull()

        // Навигация: метры, оставшееся время, стрелка манёвра.
        var value: String? = null
        var eta: String? = null
        var navIcon: Drawable? = null
        if (kind == Kind.NAVIGATION) {
            value = allText.firstNotNullOfOrNull { DIST.find(it)?.value?.replace(Regex("""(\d)(\p{L})"""), "$1 $2") }
            eta = allText.firstNotNullOfOrNull { t -> ETA.find(t)?.value }?.takeIf { it != value }
                ?: allText.firstNotNullOfOrNull { TIME.find(it)?.value }
            // У Google Карт стрелка — это маленький значок уведомления.
            navIcon = large ?: images.firstOrNull() ?: runCatching { n.smallIcon?.loadDrawable(ctx) }.getOrNull()
            val instruction = allText.firstOrNull { t -> t.length > 3 && DIST.find(t)?.value != t.trim() && ETA.find(t)?.value != t.trim() }
            if (title.isBlank() || title == value) title = instruction ?: title
            if (text.isBlank()) text = allText.firstOrNull { it != title && it != value && it != eta && it.length > 3 }.orEmpty()
        } else {
            value = when (kind) {
                Kind.PROGRESS -> if (pMax > 0 && !indeterminate) "${(p * 100 / pMax).coerceIn(0, 100)}%" else null
                else -> timeText
            }
        }

        return Live(
            kind = kind,
            key = sbn.key,
            pkg = pkg,
            title = title.ifBlank { appLabel(ctx, pkg) },
            text = text,
            appIcon = appIcon(ctx, pkg),
            largeIcon = large,
            accent = accent,
            chronoBase = if (useChrono) base else 0L,
            countDown = countDown,
            progress = if (pMax > 0 && !indeterminate) (p / pMax.toFloat()).coerceIn(0f, 1f) else -1f,
            indeterminate = kind == Kind.PROGRESS && (indeterminate || pMax <= 0),
            value = value,
            navIcon = navIcon,
            eta = eta,
            actions = callActs ?: actions.take(3),
            intent = n.contentIntent ?: n.fullScreenIntent,
        )
    }

    private val labels = LruCache<String, String>(32)

    fun appLabel(ctx: Context, pkg: String): String {
        if (pkg.isEmpty()) return ""
        labels.get(pkg)?.let { return it }
        val pm = ctx.packageManager
        val l = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        labels.put(pkg, l)
        return l
    }

    /** Обычное уведомление → баннер, выезжающий из островка. */
    fun banner(ctx: Context, sbn: StatusBarNotification): Live? {
        val pkg = sbn.packageName
        if (pkg == ctx.packageName) return null
        val n = sbn.notification
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) != 0) return null
        val ex = n.extras ?: return null
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null
        var title = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        var text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        if (title.isBlank()) { title = text; text = "" }
        val large = runCatching { n.getLargeIcon()?.loadDrawable(ctx) }.getOrNull()
        return Live(
            kind = Kind.BANNER,
            key = "banner:" + sbn.key,
            pkg = pkg,
            title = title.trim(),
            text = text.trim(),
            appIcon = appIcon(ctx, pkg),
            largeIcon = large,
            value = appLabel(ctx, pkg),
            intent = n.contentIntent,
            durationMs = 5000,
        )
    }

    /** Для экрана «Что видит островок»: сырые поля текущих уведомлений. */
    @Suppress("DEPRECATION")
    fun dump(ctx: Context, all: Array<StatusBarNotification>): String = buildString {
        val now = System.currentTimeMillis()
        all.filter { it.packageName != ctx.packageName }.sortedByDescending { it.postTime }.take(25).forEach { sbn ->
            val n = sbn.notification
            val ex = n.extras ?: Bundle()
            val kind = runCatching { classify(ctx, sbn)?.kind?.name }.getOrNull() ?: "—"
            val flags = listOfNotNull(
                "ongoing".takeIf { n.flags and Notification.FLAG_ONGOING_EVENT != 0 },
                "fgs".takeIf { n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 },
                "noclear".takeIf { n.flags and Notification.FLAG_NO_CLEAR != 0 },
            ).joinToString(",")
            append("▸ ").append(appLabel(ctx, sbn.packageName)).append(" (").append(sbn.packageName).append(")\n")
            append("  островок: ").append(kind).append(" · cat=").append(n.category ?: "-").append(" · ").append(flags.ifEmpty { "-" }).append('\n')
            texts(ex).take(4).forEach { append("  «").append(it.take(60)).append("»\n") }
            if (ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) {
                append("  chrono ").append(if (ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)) "↓" else "↑")
                    .append(" when=").append((n.`when` - now) / 1000).append("с\n")
            }
            if (ex.getInt(Notification.EXTRA_PROGRESS_MAX) > 0 || ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) {
                append("  progress ").append(ex.getInt(Notification.EXTRA_PROGRESS)).append('/').append(ex.getInt(Notification.EXTRA_PROGRESS_MAX)).append('\n')
            }
            if (n.contentView != null || n.bigContentView != null) {
                val s = runCatching { scan(ctx, n) }.getOrNull()
                append("  своя вёрстка: ").append(s?.texts?.take(4)?.joinToString(" | ") { it.take(30) } ?: "не читается")
                if (s != null && s.chronoBase != 0L) append(" · chrono")
                append('\n')
            }
            n.actions?.takeIf { it.isNotEmpty() }?.let { a -> append("  кнопки: ").append(a.joinToString { it.title }).append('\n') }
            append('\n')
        }
        if (isEmpty()) append("Уведомлений нет — или не выдан доступ к уведомлениям.")
    }
}

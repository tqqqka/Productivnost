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
import com.tihon.shtorka.ui.Palette

/** Что может показывать островок. Приоритет — как iPhone выбирает главную активность. */
enum class Kind(val priority: Int, val transient: Boolean = false) {
    CALL_INCOMING(100), CALL(90), RECORDING(80), TIMER(70), NAVIGATION(60), STOPWATCH(55), MUSIC(50), PROGRESS(40),
    BANNER(0, true), CHARGING(0, true), LOW_BATTERY(0, true), RINGER(0, true), UNLOCK(0, true), HEADPHONES(0, true),
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
    /** База для Chronometer в шкале elapsedRealtime; 0 — без секундомера. */
    val chronoBase: Long = 0L,
    val countDown: Boolean = false,
    /** 0..1 или -1. */
    val progress: Float = -1f,
    /** Короткое значение справа в компактном виде («300 м», «04:59»). */
    val value: String? = null,
    val actions: List<Act> = emptyList(),
    val intent: PendingIntent? = null,
    val playing: Boolean = false,
    val level: Int = -1,
    val durationMs: Long = 0L,
    val icon: Int = 0,
)

/**
 * Живые активности из текущих уведомлений: звонки, таймеры и секундомеры,
 * запись экрана, навигация, загрузки. Музыка приходит отдельно, из MediaRepo.
 */
object LiveRepo {
    private val byKey = LinkedHashMap<String, Live>()
    private val listeners = LinkedHashSet<() -> Unit>()

    fun add(l: () -> Unit) { listeners += l }
    fun remove(l: () -> Unit) { listeners -= l }
    private fun changed() = listeners.toList().forEach { it() }

    val lives: List<Live> get() = byKey.values.toList()

    fun setAll(ctx: Context, all: Array<StatusBarNotification>) {
        byKey.keys.filter { !it.startsWith("demo:") }.forEach { byKey.remove(it) }
        all.forEach { sbn -> classify(ctx, sbn)?.let { byKey[it.key] = it } }
        changed()
    }

    fun posted(ctx: Context, sbn: StatusBarNotification) {
        val l = classify(ctx, sbn)
        if (l == null) {
            if (byKey.remove(sbn.key) != null) changed()
        } else {
            byKey[l.key] = l
            changed()
        }
    }

    fun removed(key: String) {
        if (byKey.remove(key) != null) changed()
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
        "com.waze", "ru.dublgis.dgismobile", "com.here.app.maps",
    )
    private val RECORDERS = setOf(
        "com.samsung.android.app.smartcapture", "com.samsung.android.app.screenrecorder",
        "com.sec.android.app.screenrecorder", "com.android.systemui.screenrecord",
    )
    private val RECORD_WORDS = listOf("запись экрана", "идёт запись", "идет запись", "screen record", "recording screen")
    private val STOPWATCH_WORDS = listOf("секундомер", "stopwatch")
    private val TIME = Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b""")

    private val ANSWER = listOf("ответ", "принять", "answer", "accept")
    private val DECLINE = listOf("отклон", "сброс", "заверш", "decline", "reject", "hang up", "end call")

    private val icons = LruCache<String, Drawable>(32)

    fun appIcon(ctx: Context, pkg: String): Drawable? {
        icons.get(pkg)?.let { return it }
        val d = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return null
        icons.put(pkg, d)
        return d
    }

    private fun Bundle.str(k: String) = getCharSequence(k)?.toString().orEmpty()

    @Suppress("DEPRECATION")
    fun classify(ctx: Context, sbn: StatusBarNotification): Live? {
        val pkg = sbn.packageName
        if (pkg == ctx.packageName) return null
        val n = sbn.notification
        val ex = n.extras ?: Bundle()
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null

        val ongoing = n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0
        val title = ex.str(Notification.EXTRA_TITLE).ifBlank { ex.str(Notification.EXTRA_TITLE_BIG) }
        val text = ex.str(Notification.EXTRA_TEXT).ifBlank { ex.str(Notification.EXTRA_SUB_TEXT) }
        val lower = (title + " " + text).lowercase()
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        val pMax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val p = ex.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val callType = ex.getInt("android.callType", 0)
        val isCall = n.category == Notification.CATEGORY_CALL || pkg in DIALERS || callType != 0
        val timeText = TIME.find(title)?.value ?: TIME.find(text)?.value

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
        // CallStyle (Android 12+) хранит кнопки в extras.
        val answerPi = ex.getParcelable<PendingIntent>("android.answerIntent")
        val declinePi = ex.getParcelable<PendingIntent>("android.declineIntent")
        val hangUpPi = ex.getParcelable<PendingIntent>("android.hangUpIntent")

        val kind = when {
            isCall && (callType == 1 || (callType == 0 && !chrono &&
                (n.fullScreenIntent != null || actions.any { it.role == Act.Role.ACCEPT }))) -> Kind.CALL_INCOMING
            isCall && (ongoing || callType == 2) -> Kind.CALL
            !ongoing -> return null
            pkg in RECORDERS || RECORD_WORDS.any { lower.contains(it) } -> Kind.RECORDING
            pkg in CLOCKS -> if (STOPWATCH_WORDS.any { lower.contains(it) } || (chrono && !countDown)) Kind.STOPWATCH else Kind.TIMER
            n.category == Notification.CATEGORY_NAVIGATION || pkg in NAV -> Kind.NAVIGATION
            chrono -> if (countDown) Kind.TIMER else Kind.STOPWATCH
            pMax > 0 && !indeterminate -> Kind.PROGRESS
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
        val useChrono = chrono || (kind == Kind.CALL && timeText == null && n.`when` > 0)
        val base = if (useChrono && n.`when` > 0) SystemClock.elapsedRealtime() + (n.`when` - System.currentTimeMillis()) else 0L
        val value = when (kind) {
            Kind.NAVIGATION -> title.takeIf { it.isNotBlank() }
            Kind.PROGRESS -> "${(p * 100 / pMax.coerceAtLeast(1)).coerceIn(0, 100)}%"
            else -> timeText
        }
        val large = runCatching { n.getLargeIcon()?.loadDrawable(ctx) }.getOrNull()

        return Live(
            kind = kind,
            key = sbn.key,
            pkg = pkg,
            title = title.ifBlank { appLabel(ctx, pkg) },
            text = text,
            appIcon = appIcon(ctx, pkg),
            largeIcon = large,
            accent = accent,
            chronoBase = base,
            countDown = countDown,
            progress = if (pMax > 0 && !indeterminate) (p / pMax.toFloat()).coerceIn(0f, 1f) else -1f,
            value = value,
            actions = callActs ?: actions.take(3),
            intent = n.contentIntent ?: n.fullScreenIntent,
        )
    }

    private val labels = LruCache<String, String>(32)

    fun appLabel(ctx: Context, pkg: String): String {
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
}

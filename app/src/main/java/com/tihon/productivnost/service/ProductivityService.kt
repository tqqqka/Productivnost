package com.tihon.productivnost.service

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.tihon.productivnost.core.FocusSession
import com.tihon.productivnost.core.PlantKind
import com.tihon.productivnost.core.Reminders
import com.tihon.productivnost.core.SessionOutcome
import com.tihon.productivnost.core.SessionPhase
import com.tihon.productivnost.core.Sessions
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.core.StudySession
import com.tihon.productivnost.core.TaskRepeat
import com.tihon.productivnost.feature.pushups.PushupActivity
import com.tihon.productivnost.util.Apps
import com.tihon.productivnost.util.Fmt
import com.tihon.productivnost.util.Permissions
import com.tihon.productivnost.ui.theme.P
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Наблюдаемое снаружи состояние сервиса. */
object ServiceState {
    val running = MutableStateFlow(false)
    val foregroundApp = MutableStateFlow<String?>(null)
    val liveActiveMs = MutableStateFlow(0L)
}

/**
 * Фоновый мозг приложения.
 *
 * Каждый тик сервис узнаёт, пользуется ли человек телефоном и каким приложением,
 * и на основании этого показывает напоминания, растит дерево, ставит заслонку
 * поверх запрещённых приложений и считает экранное время.
 */
class ProductivityService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        private const val GRACE_MS = 2_200L
        private const val FLUSH_MS = 10_000L

        fun start(context: Context) {
            val i = Intent(context, ProductivityService::class.java).setAction(ACTION_START)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, ProductivityService::class.java).setAction(ACTION_STOP))
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private lateinit var overlays: OverlayHost
    private lateinit var power: PowerManager
    private var keyguard: KeyguardManager? = null

    private var neutral: Set<String> = emptySet()
    private var lastTick = 0L
    private var lastFlush = 0L
    private var pendingActive = 0L
    private val pendingApps = HashMap<String, Long>()

    private var lastKnownFg: String? = null
    private var violationPkg: String? = null
    private var violationSince = 0L
    private var blockingPkg: String? = null
    private var snoozeUntil = 0L
    private var snoozed: (() -> Unit)? = null
    private var lastNotifyAt = 0L
    private var shownOutcome: SessionOutcome? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifications.ensureChannels(this)
        overlays = OverlayHost(this)
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        neutral = Apps.neutralPackages(this)
        Sfx.preload(this)
        startForegroundSafely(Notifications.ongoing(this, "Продуктивность", "Слежу за экранным временем"))
        ServiceState.running.value = true
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        flushUsage()
        overlays.hideAll()
        ServiceState.running.value = false
        super.onDestroy()
    }

    private fun startForegroundSafely(n: android.app.Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    Notifications.ID_SERVICE, n,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    else 0,
                )
            } else {
                startForeground(Notifications.ID_SERVICE, n)
            }
        }
    }

    private fun startLoop() {
        loop?.cancel()
        loop = scope.launch {
            lastTick = System.currentTimeMillis()
            while (isActive) {
                runCatching { tick() }
                delay(cadence())
            }
        }
    }

    private fun cadence(): Long {
        val busy = Store.focus.value != null || Store.study.value != null || Store.limits.value.isNotEmpty()
        if (!isInteractive()) return 4_000L
        return if (busy) 800L else 2_500L
    }

    private fun isInteractive(): Boolean =
        power.isInteractive && keyguard?.isKeyguardLocked != true

    // ------------------------------------------------------------------- тик

    private fun tick() {
        val now = System.currentTimeMillis()
        val delta = (now - lastTick).coerceIn(0L, 30_000L)
        lastTick = now
        Store.rolloverIfNeeded()

        val interactive = isInteractive()
        val fg = if (interactive && Permissions.hasUsageAccess(this)) {
            Apps.foregroundPackage(this) ?: lastKnownFg
        } else null
        lastKnownFg = fg
        ServiceState.foregroundApp.value = fg

        val usingApp = interactive && fg != null && fg !in neutral
        if (interactive) {
            pendingActive += delta
            if (usingApp && fg != null) pendingApps[fg] = (pendingApps[fg] ?: 0L) + delta
        }
        ServiceState.liveActiveMs.value = Store.usage.value.activeUseMs + pendingActive
        if (now - lastFlush > FLUSH_MS) flushUsage()

        handleOutcome(interactive)
        val handledSession = handleSessions(now, interactive, fg)
        if (!handledSession) handleLimits(now, interactive, fg)

        if (interactive) {
            handleSnooze(now)
            handleSleepWindow(now)
            handleTasks(now)
            handleIntervalReminders(now)
        }

        if (now - lastNotifyAt > 4_000L) {
            lastNotifyAt = now
            updateNotification(now)
        }
    }

    private fun handleOutcome(interactive: Boolean) {
        val outcome = Store.outcome.value
        if (outcome == null) {
            shownOutcome = null
            return
        }
        if (shownOutcome === outcome || !interactive) return
        shownOutcome = outcome
        showOutcomeOverlay(outcome)
    }

    private fun flushUsage() {
        if (pendingActive == 0L && pendingApps.isEmpty()) return
        val active = pendingActive
        val apps = HashMap(pendingApps)
        pendingActive = 0L
        pendingApps.clear()
        lastFlush = System.currentTimeMillis()
        Store.updateUsage { u ->
            val merged = u.perApp.toMutableMap()
            apps.forEach { (k, v) -> merged[k] = (merged[k] ?: 0L) + v }
            u.copy(activeUseMs = u.activeUseMs + active, perApp = merged)
        }
    }

    // ------------------------------------------------------------- напоминания

    /** @return false, если экран занят другим оверлеем и напоминание не показано. */
    private fun showReminder(
        icon: String, title: String, body: String,
        accent: androidx.compose.ui.graphics.Color = P.leaf,
        sound: Int = 0,
        allowSnooze: Boolean = true,
        onDone: () -> Unit = {},
    ): Boolean {
        if (overlays.isShowing(OverlayHost.Slot.REMINDER) || overlays.isShowing(OverlayHost.Slot.BLOCK)) return false
        Sfx.play(this, if (sound == 0) Sfx.CALM else sound)
        Sfx.vibrate(this)
        val dismiss = {
            overlays.hide(OverlayHost.Slot.REMINDER)
            onDone()
        }
        overlays.show(OverlayHost.Slot.REMINDER, focusable = true, onBack = dismiss) {
            ReminderOverlay(
                icon = icon,
                title = title,
                body = body,
                accent = accent,
                secondaryLabel = if (allowSnooze) "Позже, через 5 минут" else null,
                onPrimary = dismiss,
                onSecondary = {
                    overlays.hide(OverlayHost.Slot.REMINDER)
                    onDone()
                    snoozeUntil = System.currentTimeMillis() + 5 * 60_000L
                    snoozed = { showReminder(icon, title, body, accent, sound, allowSnooze = false) }
                },
            )
        }
        return true
    }

    private fun handleSnooze(now: Long) {
        val s = snoozed ?: return
        if (now < snoozeUntil) return
        snoozed = null
        snoozeUntil = 0L
        s()
    }

    private fun handleIntervalReminders(now: Long) {
        val settings = Store.settings.value
        if (!settings.remindersEnabled) return
        if (Store.focus.value != null || Store.study.value != null) return
        val intervalMs = settings.intervalMinutes.coerceAtLeast(5) * 60_000L
        val active = Store.usage.value.activeUseMs + pendingActive
        val u = Store.usage.value
        if (active < (u.remindersFired + 1) * intervalMs) return
        val idx = u.remindersFired % Reminders.interval.size
        val r = Reminders.interval[idx]
        if (showReminder(r.icon, r.title, r.body, accent = P.leaf)) {
            Store.updateUsage { it.copy(remindersFired = it.remindersFired + 1) }
        }
    }

    private fun handleSleepWindow(now: Long) {
        val s = Store.settings.value
        if (!s.sleepEnabled) return
        val t = LocalTime.now()
        val minute = t.hour * 60 + t.minute
        if (minute < s.sleepFromMinute || minute >= s.sleepToMinute) return
        val every = s.sleepEveryMinutes.coerceAtLeast(5)
        val slot = (minute - s.sleepFromMinute) / every
        if (Store.usage.value.lastSleepSlot == slot) return
        val left = s.sleepToMinute - minute
        val shown = showReminder(
            icon = "🌙",
            title = "Пора спать",
            body = if (left > 10) "До отбоя $left мин. Отложи телефон и дай глазам отдохнуть."
            else "Отбой уже совсем скоро. Экран выключается, ты ложишься.",
            accent = P.violet,
            sound = Sfx.NIGHT,
            allowSnooze = false,
        )
        if (shown) Store.updateUsage { it.copy(lastSleepSlot = slot) }
    }

    private fun handleTasks(now: Long) {
        val today = Store.today()
        val t = LocalTime.now()
        val minute = t.hour * 60 + t.minute
        val task = Store.tasks.value.firstOrNull { task ->
            task.enabled &&
                task.lastFiredDay != today &&
                task.dueToday(today) &&
                minute >= task.minuteOfDay &&
                minute - task.minuteOfDay <= 180
        } ?: return
        val shown = showReminder(
            icon = task.icon,
            title = task.title,
            body = task.subtitle,
            accent = P.sky,
            allowSnooze = true,
            onDone = {
                if (task.repeat == TaskRepeat.ONCE) {
                    Store.updateTasks { list -> list.map { if (it.id == task.id) it.copy(done = true) else it } }
                }
            },
        )
        if (shown) {
            Store.updateTasks { list ->
                list.map { if (it.id == task.id) it.copy(lastFiredDay = today) else it }
            }
            Notifications.notify(
                this, Notifications.ID_REMINDER,
                Notifications.reminder(this, task.title, task.subtitle),
            )
        }
    }

    // ------------------------------------------------------------------ сессии

    /** @return true, если сессия уже показала заслонку и лимиты можно не проверять. */
    private fun handleSessions(now: Long, interactive: Boolean, fg: String?): Boolean {
        val focus = Store.focus.value
        if (focus != null) return handleFocus(focus, now, interactive, fg)
        val study = Store.study.value
        if (study != null) return handleStudy(study, now, interactive, fg)
        if (blockingPkg != null) {
            overlays.hide(OverlayHost.Slot.BLOCK)
            blockingPkg = null
        }
        return false
    }

    private fun handleFocus(session: FocusSession, now: Long, interactive: Boolean, fg: String?): Boolean {
        if (now >= session.startedAt + session.durationMs) {
            finishFocus(session, now)
            return false
        }
        val allowed = Store.settings.value.allowedApps
        if (interactive && fg != null && fg !in neutral && fg !in allowed) {
            if (violationPkg == fg) {
                if (now - violationSince >= GRACE_MS) {
                    failFocus(session, fg, now)
                    return false
                }
            } else {
                violationPkg = fg
                violationSince = now
            }
        } else if (violationPkg != null && fg != violationPkg) {
            violationPkg = null
        }
        return true
    }

    private fun finishFocus(session: FocusSession, now: Long) {
        violationPkg = null
        SessionController.finishFocusNow(this)
        Sfx.vibrate(this, longArrayOf(0, 30, 90, 30, 90, 40))
        val minutes = (session.durationMs / 60_000L).toInt()
        Notifications.notify(
            this, Notifications.ID_REMINDER,
            Notifications.reminder(
                this, "Дерево выросло",
                "${Fmt.trees(session.trees)} в саду. ${Fmt.duration(minutes)} без телефона.",
            ),
        )
    }

    private fun failFocus(session: FocusSession, pkg: String, now: Long) {
        Store.setFocus(null)
        violationPkg = null
        val label = Apps.label(this, pkg)
        val outcome = SessionOutcome(
            success = false,
            kind = PlantKind.FOCUS,
            plants = 0,
            minutes = ((now - session.startedAt) / 60_000L).toInt(),
            reason = "Открыто «$label» — росток засох",
            blamePackage = pkg,
            seed = session.seed, species = session.species, at = now,
        )
        Store.setOutcome(outcome)
        Sfx.play(this, Sfx.WITHER, 0.55f)
        Sfx.vibrate(this, longArrayOf(0, 60, 120, 60))
    }

    private fun handleStudy(session: StudySession, now: Long, interactive: Boolean, fg: String?): Boolean {
        var s = session
        var switched = false
        var guard = 0
        while (guard++ < 64) {
            val total = if (s.phase == SessionPhase.WORK) s.workMs else s.breakMs
            if (total <= 0L || now < s.phaseStartedAt + total) break
            s = if (s.phase == SessionPhase.WORK) {
                s.copy(
                    phase = SessionPhase.BREAK,
                    phaseStartedAt = s.phaseStartedAt + total,
                    completedBlocks = s.completedBlocks + 1,
                )
            } else {
                s.copy(phase = SessionPhase.WORK, phaseStartedAt = s.phaseStartedAt + total)
            }
            switched = true
        }
        if (switched) {
            Store.setStudy(s)
            Sfx.play(this, if (s.phase == SessionPhase.WORK) Sfx.CALM else Sfx.GROWN, 0.55f)
            Sfx.vibrate(this)
            if (interactive) {
                val work = s.phase == SessionPhase.WORK
                showReminder(
                    icon = if (work) "📚" else "☕",
                    title = if (work) "Снова за работу" else "Перерыв",
                    body = if (work) "${Fmt.minutes(s.workMs)} концентрации. Приложения снова закрыты."
                    else "${Fmt.minutes(s.breakMs)} свободы. Можно листать что угодно.",
                    accent = if (work) P.sky else P.amber,
                    allowSnooze = false,
                )
            }
        }

        if (s.phase != SessionPhase.WORK) {
            if (blockingPkg != null) {
                overlays.hide(OverlayHost.Slot.BLOCK); blockingPkg = null
            }
            return false
        }

        val allowed = Store.settings.value.allowedApps
        if (interactive && fg != null && fg !in neutral && fg !in allowed) {
            if (blockingPkg != fg) {
                blockingPkg = fg
                showStudyBlock(fg, s)
            }
            return true
        }
        if (blockingPkg != null) {
            overlays.hide(OverlayHost.Slot.BLOCK); blockingPkg = null
        }
        return false
    }

    private fun showStudyBlock(pkg: String, session: StudySession) {
        Sfx.vibrate(this, longArrayOf(0, 40, 80, 40))
        overlays.show(OverlayHost.Slot.BLOCK, focusable = true, onBack = { goHome() }) {
            BlockOverlay(
                title = "Идёт сессия учёбы",
                appLabel = Apps.label(this, pkg),
                body = "Это приложение сейчас закрыто. Открой его в перерыв — он уже близко.",
                accent = P.sky,
                canExtend = false,
                onHome = { goHome() },
                onEndSession = { finishStudy() },
            )
        }
    }

    private fun finishStudy() {
        overlays.hide(OverlayHost.Slot.BLOCK)
        blockingPkg = null
        SessionController.finishStudy(this)
    }

    private fun showOutcomeOverlay(outcome: SessionOutcome) {
        overlays.hide(OverlayHost.Slot.BLOCK)
        val dismiss = {
            overlays.hide(OverlayHost.Slot.REMINDER)
            Store.setOutcome(null)
        }
        overlays.show(OverlayHost.Slot.REMINDER, focusable = true, onBack = dismiss) {
            OutcomeOverlay(
                success = outcome.success,
                title = if (outcome.success) {
                    if (outcome.kind == PlantKind.STUDY) "Сессия закрыта" else "Дерево выросло"
                } else {
                    if (outcome.kind == PlantKind.STUDY) "Сессия без урожая" else "Росток засох"
                },
                body = outcome.reason,
                plants = outcome.plants,
                onOk = dismiss,
            )
        }
    }

    // ------------------------------------------------------------------ лимиты

    private fun handleLimits(now: Long, interactive: Boolean, fg: String?) {
        if (!interactive || fg == null || fg in neutral) {
            if (blockingPkg != null) {
                overlays.hide(OverlayHost.Slot.BLOCK); blockingPkg = null
            }
            return
        }
        val remaining = Store.remainingFor(fg)?.minus(pendingApps[fg] ?: 0L)
        if (remaining == null || remaining > 0) {
            if (blockingPkg != null) {
                overlays.hide(OverlayHost.Slot.BLOCK); blockingPkg = null
            }
            return
        }
        if (blockingPkg == fg) return
        blockingPkg = fg
        flushUsage()
        Sfx.play(this, Sfx.WITHER, 0.4f)
        Sfx.vibrate(this, longArrayOf(0, 40, 80, 40))
        val label = Apps.label(this, fg)
        val perPushup = Store.settings.value.minutesPerPushup
        overlays.show(OverlayHost.Slot.BLOCK, focusable = true, onBack = { goHome() }) {
            BlockOverlay(
                title = "Лимит исчерпан",
                appLabel = label,
                body = "На сегодня время в этом приложении закончилось. " +
                    "Можно продлить: одно отжимание — $perPushup минуты.",
                accent = P.amber,
                canExtend = true,
                onHome = { goHome() },
                onExtend = {
                    overlays.hide(OverlayHost.Slot.BLOCK)
                    blockingPkg = null
                    startActivity(PushupActivity.intent(this, fg))
                },
            )
        }
    }

    private fun goHome() {
        overlays.hide(OverlayHost.Slot.BLOCK)
        overlays.hide(OverlayHost.Slot.REMINDER)
        blockingPkg = null
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // ------------------------------------------------------------ уведомление

    private fun updateNotification(now: Long) {
        val focus = Store.focus.value
        val study = Store.study.value
        val n = when {
            focus != null -> {
                val left = Sessions.focusRemaining(focus, now)
                Notifications.ongoing(
                    this, "Дерево растёт",
                    "Осталось ${Fmt.clock(left)} · ${Fmt.trees(focus.trees)}",
                    (Sessions.focusProgress(focus, now) * 100).toInt(),
                )
            }
            study != null -> {
                val left = Sessions.studyPhaseRemaining(study, now)
                val phase = if (study.phase == SessionPhase.WORK) "Работа" else "Перерыв"
                Notifications.ongoing(
                    this, "Сессия учёбы · $phase",
                    "Осталось ${Fmt.clock(left)} · блоков: ${study.completedBlocks}",
                    (Sessions.studyPhaseProgress(study, now) * 100).toInt(),
                )
            }
            else -> {
                val active = Store.usage.value.activeUseMs + pendingActive
                Notifications.ongoing(this, "Продуктивность", "Сегодня в телефоне ${Fmt.minutes(active)}")
            }
        }
        startForegroundSafely(n)
    }
}

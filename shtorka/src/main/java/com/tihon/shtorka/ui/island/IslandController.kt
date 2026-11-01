package com.tihon.shtorka.ui.island

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Surface
import android.view.View
import android.view.WindowManager
import com.tihon.shtorka.core.Prefs
import com.tihon.shtorka.service.IslandService
import com.tihon.shtorka.system.Act
import com.tihon.shtorka.system.Controls
import com.tihon.shtorka.system.Kind
import com.tihon.shtorka.system.Launch
import com.tihon.shtorka.system.Live
import com.tihon.shtorka.system.LiveRepo
import com.tihon.shtorka.system.MediaRepo
import com.tihon.shtorka.ui.Haptics
import com.tihon.shtorka.ui.Palette
import com.tihon.shtorka.ui.Screen
import com.tihon.shtorka.ui.overlayParams
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Мозг островка — как Dynamic Island решает, что показать:
 *  • главная активность в островке, вторая — «капелькой» справа;
 *  • короткие события (зарядка, звонок/беззвучный, разблокировка, наушники,
 *    уведомления) ненадолго вытесняют главную;
 *  • входящий звонок раскрывается сам;
 *  • тап открывает приложение, долгое нажатие раскрывает, свайп вбок прячет.
 */
class IslandController(private val svc: IslandService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    val view = IslandView(svc, this)

    /** Android 14+: окно на всю ширину, неподвижно — касания режет touchable region. */
    private val fullWidth = Build.VERSION.SDK_INT >= 34
    private val lp = overlayParams(1, 1).apply {
        flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        title = "Island"
    }
    private var attached = false

    private var expandedKey: String? = null
    private val hidden = HashMap<String, String>()
    private var transient: Live? = null
    private val queue = ArrayDeque<Live>()
    private var foreground: String? = null
    private var screenOn = true
    private var lastPlayingAt = 0L
    private var lastBatteryLevel = -1

    private val endTransient = Runnable { nextTransient() }
    private val listener: () -> Unit = { onData() }
    private val pausedCheck = Runnable { update() }

    fun attach() {
        if (attached) return
        geometry()
        view.visibility = View.INVISIBLE
        place(intArrayOf(1, 1))
        runCatching { wm.addView(view, lp); attached = true }
        LiveRepo.add(listener)
        LiveRepo.onFinished = { l -> onDownloadDone(l) }
        MediaRepo.add(listener)
        screenOn = svc.getSystemService(PowerManager::class.java).isInteractive
        if (MediaRepo.now?.playing == true) lastPlayingAt = SystemClock.uptimeMillis()
        update(animate = false)
    }

    fun detach() {
        if (!attached) return
        LiveRepo.remove(listener)
        LiveRepo.onFinished = null
        MediaRepo.remove(listener)
        main.removeCallbacksAndMessages(null)
        runCatching { wm.removeViewImmediate(view) }
        attached = false
    }

    fun applyPrefs() {
        geometry()
        update(animate = false)
    }

    fun onConfigChanged() {
        geometry()
        expandedKey = null
        update(animate = false)
    }

    private fun geometry() {
        view.notch = Screen.notch(svc)
        view.screenW = Screen.size(svc).x
    }

    // ---------- что показывать ----------

    private fun allowed(l: Live) = when (l.kind) {
        Kind.CALL, Kind.CALL_INCOMING -> Prefs.calls
        Kind.TIMER, Kind.STOPWATCH -> Prefs.timers
        Kind.RECORDING -> Prefs.recording
        Kind.NAVIGATION -> Prefs.navigation
        Kind.PROGRESS -> Prefs.downloads
        Kind.MUSIC -> Prefs.music
        else -> true
    }

    private val audio = svc.getSystemService(android.media.AudioManager::class.java)
    private val recheckMusic = Runnable { update() }

    /**
     * Музыка — только если её реально слышно. Некоторые приложения держат сессию
     * в состоянии «играю», хотя звука нет, — тогда островок показывал пустую обложку.
     */
    private fun musicLive(): Live? {
        val n = MediaRepo.now ?: return null
        val audible = n.playing && runCatching { audio.isMusicActive }.getOrDefault(true)
        val now = SystemClock.uptimeMillis()
        if (audible) lastPlayingAt = now
        if (n.playing && !audible && now - stateChangedAt < 6000) {
            // звук мог ещё не начаться — проверим чуть позже (недолго)
            main.removeCallbacks(recheckMusic)
            main.postDelayed(recheckMusic, 1200)
        }
        if (!audible && (lastPlayingAt == 0L || now - lastPlayingAt > PAUSE_KEEP_MS)) return null
        return Live(
            kind = Kind.MUSIC, key = "music", pkg = n.pkg, title = n.title, text = n.artist,
            art = n.art, accent = n.accent, playing = audible,
            intent = n.controller.sessionActivity,
        )
    }

    /** Сколько полезного в активности: при дублях (часы шлют два уведомления) берём лучшее. */
    private fun richness(l: Live) = (if (l.chronoBase != 0L) 4 else 0) + (if (!l.value.isNullOrBlank()) 2 else 0) + (if (l.actions.isNotEmpty()) 1 else 0)

    private fun sig(l: Live) = l.kind.name + "|" + l.title

    private fun lives(): List<Live> {
        val all = LiveRepo.lives + listOfNotNull(musicLive())
        hidden.keys.retainAll(all.map { it.key }.toSet())
        return all.filter { l ->
            allowed(l) &&
                hidden[l.key] != sig(l) &&
                !(Prefs.hideInOwnerApp && l.pkg == foreground && l.kind != Kind.CALL_INCOMING)
        }
            // одно приложение — одна активность каждого вида
            .groupBy { it.pkg + "|" + it.kind }.values.map { g -> g.maxBy { richness(it) } }
            .sortedByDescending { it.kind.priority }
    }

    fun canShowIdle() = screenOn && Screen.rotation(svc) == Surface.ROTATION_0

    private var lastSig: String? = null

    fun update(animate: Boolean = true) {
        if (!attached) return
        if (!screenOn || Screen.rotation(svc) != Surface.ROTATION_0) {
            expandedKey = null
            lastSig = null
            view.render(null, null, false, animate = false)
            return
        }
        val ls = lives()
        val incoming = ls.firstOrNull { it.kind == Kind.CALL_INCOMING }
        val t = transient
        val primary = incoming ?: t ?: ls.firstOrNull()
        val secondary = ls.firstOrNull { it !== primary && it.key != primary?.key }
        if (expandedKey != null && ls.none { it.key == expandedKey }) expandedKey = null
        val expanded = primary != null && (
            primary.kind == Kind.CALL_INCOMING || primary.kind == Kind.BANNER || primary.kind == Kind.HEADPHONES ||
                primary.key == expandedKey)
        // Ничего видимого не изменилось (частые обновления уведомлений) — не трогаем островок.
        val sig = "${primary?.signature()}#${secondary?.signature()}#$expanded#${Prefs.size}#${Prefs.idlePill}"
        if (animate && sig == lastSig) return
        lastSig = sig
        view.render(primary, secondary, expanded, animate)
    }

    private var stateChangedAt = 0L
    private var lastPlayingState: Boolean? = null

    private fun onData() {
        val n = MediaRepo.now
        if (n?.playing != lastPlayingState) {
            lastPlayingState = n?.playing
            stateChangedAt = SystemClock.uptimeMillis()
        }
        main.removeCallbacks(pausedCheck)
        if (n != null && !n.playing) main.postDelayed(pausedCheck, PAUSE_KEEP_MS + 50)
        update()
    }

    // ---------- короткие события ----------

    fun show(l: Live) {
        if (!attached || !screenOn) return
        val cur = transient
        if (cur == null) {
            startTransient(l)
        } else if (cur.kind == l.kind) {
            transient = l
            main.removeCallbacks(endTransient)
            main.postDelayed(endTransient, l.durationMs)
            update()
        } else {
            queue.removeAll { it.kind == l.kind }
            queue.addLast(l)
        }
    }

    private fun startTransient(l: Live) {
        transient = l
        main.removeCallbacks(endTransient)
        main.postDelayed(endTransient, l.durationMs)
        if (l.kind == Kind.CHARGING || l.kind == Kind.RINGER) Haptics.tick(view)
        update()
    }

    private fun nextTransient() {
        main.removeCallbacks(endTransient)
        transient = null
        val n = queue.removeFirstOrNull()
        if (n != null) startTransient(n) else update()
    }

    fun onScreen(on: Boolean) {
        screenOn = on
        if (!on) {
            expandedKey = null
            transient = null
            queue.clear()
            main.removeCallbacks(endTransient)
        }
        update(animate = on)
    }

    fun onCharging(plugged: Boolean) {
        if (!plugged || !Prefs.charging) return
        main.postDelayed({
            val b = Controls.battery(svc)
            show(Live(Kind.CHARGING, "charging", level = b.level, accent = Palette.GREEN, durationMs = 3200))
        }, 600)
    }

    fun onBattery(level: Int, charging: Boolean) {
        val prev = lastBatteryLevel
        lastBatteryLevel = level
        if (prev < 0 || charging || !Prefs.lowBattery) return
        val crossed = listOf(20, 10, 5).firstOrNull { level <= it && prev > it } ?: return
        show(Live(Kind.LOW_BATTERY, "low", level = level, accent = Palette.RED, title = "Низкий заряд", durationMs = 3600, value = "$crossed"))
    }

    fun onRinger() {
        if (Prefs.ringer) show(Live(Kind.RINGER, "ringer", durationMs = 2200))
    }

    fun onUnlock() {
        if (!Prefs.unlock) return
        // «Разблокировано» при входе по отпечатку бывает раньше «экран включён».
        screenOn = true
        show(Live(Kind.UNLOCK, "unlock", durationMs = 1500))
    }

    fun onHeadphones(name: String, level: Int) {
        if (!Prefs.headphones) return
        show(Live(Kind.HEADPHONES, "headphones", title = name, text = "Подключено", level = level, accent = Palette.GREEN, durationMs = 3600))
    }

    fun onHeadphonesBattery(name: String, level: Int) {
        if (transient?.kind == Kind.HEADPHONES) onHeadphones(name, level)
    }

    private fun onDownloadDone(l: Live) {
        if (!Prefs.downloads) return
        show(Live(Kind.DONE, "done", pkg = l.pkg, title = l.title, appIcon = l.appIcon, accent = Palette.GREEN, durationMs = 1800, intent = l.intent))
    }

    fun onBanner(l: Live) {
        if (!Prefs.banners) return
        if (Prefs.hideInOwnerApp && l.pkg == foreground) return
        if (!svc.getSystemService(PowerManager::class.java).isInteractive) return
        show(l)
    }

    fun onForeground(pkg: String) {
        if (pkg == foreground) return
        foreground = pkg
        if (Prefs.hideInOwnerApp) update()
    }

    // ---------- касания ----------

    private fun current(): Live? = view.primary

    fun onTap() {
        val l = current() ?: return
        if (l.kind.transient && l.kind != Kind.BANNER && l.kind != Kind.DONE) { nextTransient(); return }
        openApp(l)
        if (l.kind == Kind.BANNER || l.kind == Kind.DONE) nextTransient()
        collapse()
    }

    fun onLongPress() {
        val l = current() ?: return
        if (l.kind.transient) return
        Haptics.heavy(view)
        expandedKey = l.key
        update()
    }

    fun onSwipeAway() {
        val l = current() ?: return
        if (l.kind.transient) { nextTransient(); return }
        hidden[l.key] = sig(l)
        Haptics.tick(view)
        update()
    }

    fun onSwipeUp() {
        val l = current() ?: return
        when {
            l.kind == Kind.BANNER -> nextTransient()
            l.kind == Kind.CALL_INCOMING -> Unit
            else -> collapse()
        }
    }

    fun onOutside() {
        val l = current() ?: return
        if (l.kind == Kind.CALL_INCOMING || l.kind == Kind.BANNER) return
        collapse()
    }

    fun onBubbleTap() {
        view.secondary?.let { openApp(it) }
    }

    fun onBubbleLongPress() {
        val s = view.secondary ?: return
        Haptics.heavy(view)
        expandedKey = s.key
        hidden.remove(s.key)
        // поменять местами: вторая становится главной, раскрытой
        transient = null
        update()
    }

    fun collapse() {
        if (expandedKey == null) return
        expandedKey = null
        update()
    }

    fun runAction(a: Act) {
        a.run?.invoke() ?: Launch.send(svc, a.intent)
        if (current()?.kind == Kind.BANNER) nextTransient()
    }

    fun openOutput() {
        Launch.start(svc, Launch.bluetoothSettings())
        collapse()
    }

    private fun openApp(l: Live) {
        if (l.kind == Kind.MUSIC) { MediaRepo.open(svc); return }
        if (l.pkg.isNotEmpty()) Launch.openApp(svc, l.pkg, l.intent)
    }

    // ---------- окно ----------

    /** Перед ростом — окно должно вместить форму. */
    fun growWindow(need: IntArray) {
        val w = if (fullWidth) view.screenW else need[0]
        if (w > lp.width || need[1] > lp.height) place(intArrayOf(max(w, if (fullWidth) 0 else lp.width), max(need[1], lp.height)))
    }

    /** После анимации — по размеру формы, чтобы не перехватывать лишние касания. */
    fun fitWindow(need: IntArray) = place(need)

    private fun place(need: IntArray) {
        val w = if (fullWidth) view.screenW.coerceAtLeast(1) else need[0]
        val h = need[1].coerceAtLeast(1)
        val x = if (fullWidth) 0 else (view.notch.exactCenterX() - w / 2f).roundToInt()
        if (lp.width == w && lp.height == h && lp.x == x) return
        lp.width = w
        lp.height = h
        lp.x = x
        lp.y = 0
        if (attached) runCatching { wm.updateViewLayout(view, lp) }
    }

    // ---------- показ без событий (кнопки в настройках) ----------

    fun demo(kind: Kind) {
        if (!attached) return
        val now = SystemClock.elapsedRealtime()
        when (kind) {
            Kind.CALL_INCOMING -> {
                val key = "demo:call"
                lateinit var answer: () -> Unit
                val decline = { LiveRepo.removed(key) }
                answer = {
                    LiveRepo.putDemo(
                        Live(Kind.CALL, key, "", title = "Мама", text = "мобильный", accent = Palette.GREEN,
                            chronoBase = SystemClock.elapsedRealtime(), actions = listOf(Act("Завершить", null, Act.Role.DECLINE, decline))),
                    )
                    main.postDelayed({ LiveRepo.removed(key) }, 15000)
                }
                LiveRepo.putDemo(
                    Live(Kind.CALL_INCOMING, key, "", title = "Мама", accent = Palette.GREEN,
                        actions = listOf(Act("Отклонить", null, Act.Role.DECLINE, decline), Act("Ответить", null, Act.Role.ACCEPT) { answer() })),
                )
            }
            Kind.TIMER -> {
                LiveRepo.putDemo(Live(Kind.TIMER, "demo:timer", "", title = "Таймер", text = "Паста",
                    accent = Palette.ORANGE, chronoBase = now + 5 * 60_000, countDown = true))
                main.postDelayed({ LiveRepo.removed("demo:timer") }, 20000)
            }
            Kind.BANNER -> show(Live(Kind.BANNER, "demo:banner", "", title = "Мама",
                text = "Ты где? Ужин стынет, приходи скорее домой", value = "Сообщения", durationMs = 5000))
            Kind.CHARGING -> show(Live(Kind.CHARGING, "charging", level = Controls.battery(svc).level, accent = Palette.GREEN, durationMs = 3200))
            Kind.UNLOCK -> show(Live(Kind.UNLOCK, "unlock", durationMs = 1400))
            Kind.HEADPHONES -> show(Live(Kind.HEADPHONES, "headphones", title = "Наушники", text = "Подключено", level = 80, durationMs = 3600))
            Kind.RINGER -> show(Live(Kind.RINGER, "ringer", durationMs = 2200))
            Kind.LOW_BATTERY -> show(Live(Kind.LOW_BATTERY, "low", level = 10, accent = Palette.RED, durationMs = 3600))
            Kind.RECORDING -> {
                LiveRepo.putDemo(Live(Kind.RECORDING, "demo:rec", "", title = "Запись экрана", accent = Palette.RED, chronoBase = now))
                main.postDelayed({ LiveRepo.removed("demo:rec") }, 15000)
            }
            Kind.NAVIGATION -> {
                LiveRepo.putDemo(Live(Kind.NAVIGATION, "demo:nav", "", title = "Поверните направо", text = "ул. Ленина",
                    value = "300 м", eta = "12 мин", navIcon = androidx.core.content.ContextCompat.getDrawable(svc, com.tihon.shtorka.R.drawable.g_navigation)))
                main.postDelayed({ LiveRepo.removed("demo:nav") }, 15000)
            }
            Kind.PROGRESS -> {
                // Загрузка за 6 секунд, в конце — галочка.
                val icon = LiveRepo.appIcon(svc, svc.packageName)
                for (i in 0..20) main.postDelayed({
                    val p = i / 20f
                    LiveRepo.putDemo(Live(Kind.PROGRESS, "demo:dl", "", title = "Загрузка", text = "video.mp4",
                        appIcon = icon, accent = Palette.BLUE, progress = p, value = "${(p * 100).toInt()}%"))
                }, i * 300L)
                main.postDelayed({ LiveRepo.removed("demo:dl") }, 21 * 300L)
            }
            else -> Unit
        }
    }

    companion object {
        /** Сколько островок держит музыку на паузе, прежде чем спрятаться. */
        const val PAUSE_KEEP_MS = 10_000L
    }
}

package com.tihon.shtorka.system

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Нажимает плитку в системной панели быстрых настроек. Так переключаются
 * авиарежим, мобильные данные, геолокация и прочее, что Android не даёт
 * менять сторонним приложениям. Панель открывается под нашей шторкой,
 * поэтому её почти не видно; после нажатия она сразу закрывается.
 */
class QsClicker(private val svc: AccessibilityService) {
    private val h = Handler(Looper.getMainLooper())
    var busy = false
        private set

    /**
     * @param keys начала подписей плитки (в нижнем регистре, рус. и англ.)
     * @param collapseAfter закрыть ли панель после нажатия (для плиток, которые
     *   сами открывают окно — «Запись экрана», Smart View, — не нужно)
     */
    fun click(keys: List<String>, collapseAfter: Boolean = true, done: (Boolean) -> Unit) {
        if (busy) { done(false); return }
        busy = true
        if (!svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) {
            busy = false
            done(false)
            return
        }
        val start = SystemClock.uptimeMillis()
        val attempt = object : Runnable {
            override fun run() {
                val node = find(keys)
                if (node != null) {
                    val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    h.postDelayed({ confirm(SystemClock.uptimeMillis(), ok, collapseAfter, done) }, 300)
                    return
                }
                if (SystemClock.uptimeMillis() - start > 1800) {
                    collapse()
                    busy = false
                    done(false)
                    return
                }
                h.postDelayed(this, 110)
            }
        }
        h.postDelayed(attempt, 220)
    }

    /** Samsung спрашивает подтверждение, например при выключении мобильных данных. */
    private fun confirm(since: Long, clicked: Boolean, collapseAfter: Boolean, done: (Boolean) -> Unit) {
        val btn = findExact(CONFIRM)
        if (btn != null) btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (btn == null && SystemClock.uptimeMillis() - since < 500) {
            h.postDelayed({ confirm(since, clicked, collapseAfter, done) }, 120)
            return
        }
        if (collapseAfter) h.postDelayed({ collapse() }, 120)
        busy = false
        done(clicked)
    }

    private fun collapse() {
        if (Build.VERSION.SDK_INT >= 31) {
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else if (shadeOpen()) {
            // «Назад» при закрытой панели ушёл бы в приложение — поэтому только если она открыта.
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    private fun systemUiRoots(): List<AccessibilityNodeInfo> = runCatching {
        svc.windows.mapNotNull { w -> w.root?.takeIf { it.packageName == SYSTEM_UI } }
    }.getOrDefault(emptyList())

    private fun shadeOpen(): Boolean = runCatching {
        val half = svc.resources.displayMetrics.heightPixels / 2
        svc.windows.any { w ->
            val r = Rect()
            w.getBoundsInScreen(r)
            w.root?.packageName == SYSTEM_UI && r.height() > half
        }
    }.getOrDefault(false)

    private fun find(keys: List<String>): AccessibilityNodeInfo? {
        for (root in systemUiRoots()) {
            val hit = walk(root) { n ->
                val d = n.contentDescription?.toString()?.lowercase()?.trim()
                val t = n.text?.toString()?.lowercase()?.trim()
                keys.any { k -> d?.startsWith(k) == true || t?.startsWith(k) == true }
            } ?: continue
            return clickable(hit)
        }
        return null
    }

    private fun findExact(words: List<String>): AccessibilityNodeInfo? {
        for (root in systemUiRoots()) {
            val hit = walk(root) { n ->
                val t = n.text?.toString()?.lowercase()?.trim()
                t != null && t in words
            } ?: continue
            return clickable(hit)
        }
        return null
    }

    private fun clickable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = n
        var depth = 0
        while (cur != null && depth < 5) {
            if (cur.isClickable && cur.isEnabled) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    private fun walk(root: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < 900) {
            val n = queue.removeFirst()
            seen++
            if (n.isVisibleToUser && match(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    companion object {
        private const val SYSTEM_UI = "com.android.systemui"
        private val CONFIRM = listOf("выключить", "отключить", "turn off", "ок", "ok")

        val AIRPLANE = listOf("авиарежим", "режим полета", "режим полёта", "автономный режим", "airplane", "aeroplane", "flight mode")
        val DATA = listOf("мобильные данные", "мобильный интернет", "передача данных", "mobile data", "cellular data")
        val WIFI = listOf("wi-fi", "wifi", "wlan")
        val BLUETOOTH = listOf("bluetooth")
        val HOTSPOT = listOf("мобильная точка доступа", "точка доступа", "мобильная точка", "mobile hotspot", "hotspot")
        val LOCATION = listOf("местоположение", "геоданные", "геолокация", "location")
        val DARK = listOf("темный режим", "тёмный режим", "темная тема", "тёмная тема", "dark mode", "dark theme")
        val POWER_SAVE = listOf("энергосбережение", "экономия энергии", "экономия заряда", "power saving", "battery saver", "power saver")
        val RECORD = listOf("запись экрана", "screen recorder", "screen record")
        val EYE_COMFORT = listOf("комфорт для глаз", "защита зрения", "ночной режим", "eye comfort", "night light")
        val ROTATION = listOf("автоповорот", "книжная", "поворот экрана", "auto rotate", "portrait")
        val MIRROR = listOf("smart view", "трансляция", "дублирование экрана", "screen cast", "cast")
    }
}

package com.tihon.shtorka.core

import android.os.Handler
import android.os.Looper

/**
 * Простейшая шина «что-то в системе поменялось»: Wi-Fi, Bluetooth, батарея,
 * громкость, режим звонка... Открытые панели в ответ просто перечитывают
 * состояние — это дёшево, а подписок на каждое поле не нужно.
 * Работает только на главном потоке.
 */
object Bus {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()
    private var scheduled = false

    fun add(l: () -> Unit) { listeners += l }
    fun remove(l: () -> Unit) { listeners -= l }

    /** Склеивает пачку событий в одно обновление на следующем кадре. */
    fun fire() {
        if (scheduled) return
        scheduled = true
        main.post {
            scheduled = false
            listeners.toList().forEach { it() }
        }
    }

    /** Повторить обновление через паузы — системные переключатели меняются не сразу. */
    fun fireLater(vararg delaysMs: Long) {
        delaysMs.forEach { main.postDelayed({ fire() }, it) }
    }
}

/** Живые экземпляры служб — всё работает в одном процессе. */
object Hub {
    var service: com.tihon.shtorka.service.ShadeService? = null
    var listener: com.tihon.shtorka.service.NotifListener? = null
}

package com.tihon.shtorka.core

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Настройки приложения. Служба слушает изменения и применяет их на лету. */
object Prefs {
    lateinit var sp: SharedPreferences
        private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("shtorka", Context.MODE_PRIVATE)
    }

    /** Свайп справа от капли — Пункт управления. */
    var controlCenter by flag("control_center", true)

    /** Свайп слева от капли — Центр уведомлений. */
    var notificationCenter by flag("notification_center", true)

    /** Островок у капли камеры. */
    var island by flag("island", true)
    var islandMusic by flag("island_music", true)
    var islandCharging by flag("island_charging", true)
    var islandRinger by flag("island_ringer", true)

    /** Нижний ряд: энергосбережение, тёмная тема, запись экрана, будильник. */
    var extraRow by flag("extra_row", true)

    var haptics by flag("haptics", true)

    private fun flag(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            sp.edit().putBoolean(key, value).apply()
        }
    }
}

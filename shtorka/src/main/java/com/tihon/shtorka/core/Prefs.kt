package com.tihon.shtorka.core

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Настройки островка. Служба слушает изменения и применяет их на лету. */
object Prefs {
    lateinit var sp: SharedPreferences
        private set

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("island", Context.MODE_PRIVATE)
    }

    // Что показывать
    var music by flag("music", true)
    var calls by flag("calls", true)
    var timers by flag("timers", true)
    var recording by flag("recording", true)
    var navigation by flag("navigation", true)
    var downloads by flag("downloads", true)
    var banners by flag("banners", true)
    var charging by flag("charging", true)
    var lowBattery by flag("low_battery", true)
    var ringer by flag("ringer", true)
    var unlock by flag("unlock", true)
    var headphones by flag("headphones", true)

    // Поведение
    /** Чёрная пилюля в покое, как на iPhone (иначе прячется в каплю). */
    var idlePill by flag("idle_pill", false)
    /** Не показывать активность, когда открыто её же приложение — как в iOS. */
    var hideInOwnerApp by flag("hide_in_app", true)
    var haptics by flag("haptics", true)

    /** 0 — маленький, 1 — обычный, 2 — крупный. */
    var size: Int
        get() = sp.getInt("size", 1)
        set(v) { sp.edit().putInt("size", v).apply() }

    val sizeScale: Float get() = when (size) { 0 -> 0.82f; 2 -> 1.18f; else -> 1f }

    private fun flag(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            sp.edit().putBoolean(key, value).apply()
        }
    }
}

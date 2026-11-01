package com.tihon.shtorka.ui.cc

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import com.tihon.shtorka.core.Bus
import com.tihon.shtorka.core.Hub
import com.tihon.shtorka.system.Controls
import com.tihon.shtorka.system.Launch
import com.tihon.shtorka.system.QsClicker
import com.tihon.shtorka.system.Torch

/** Что делает каждая кнопка. Сначала прямой вызов, потом плитка, потом настройки. */
object Actions {
    private val svc get() = Hub.service

    private fun viaTile(keys: List<String>, fallback: Intent?, collapse: Boolean = true) {
        val s = svc ?: return
        s.qs.click(keys, collapse) { ok ->
            if (!ok && fallback != null) s.launch(fallback)
            Bus.fireLater(250, 800, 1600, 3000)
        }
    }

    fun wifi(ctx: Context) {
        if (Controls.setWifi(ctx, !Controls.wifiOn(ctx))) Bus.fireLater(300, 1200, 2800)
        else viaTile(QsClicker.WIFI, Launch.wifiPanel())
    }

    fun wifiSettings() { svc?.launch(Launch.wifiPanel()) }

    fun bluetooth(ctx: Context) {
        if (Controls.setBt(ctx, !Controls.btOn(ctx))) Bus.fireLater(300, 1200, 2800)
        else viaTile(QsClicker.BLUETOOTH, Launch.bluetoothSettings())
    }

    fun bluetoothSettings() { svc?.launch(Launch.bluetoothSettings()) }

    fun airplane() = viaTile(QsClicker.AIRPLANE, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))

    fun data() = viaTile(QsClicker.DATA, Launch.dataPanel())

    fun hotspot() = viaTile(QsClicker.HOTSPOT, Intent(Settings.ACTION_WIRELESS_SETTINGS))

    fun location() = viaTile(QsClicker.LOCATION, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    fun rotation(ctx: Context) {
        val s = svc ?: return
        if (Controls.setRotationLocked(ctx, !Controls.rotationLocked(ctx), s.displayRotation())) Bus.fire()
        else viaTile(QsClicker.ROTATION, Launch.writeSettings(ctx))
    }

    fun dnd(ctx: Context) {
        if (Controls.setDnd(ctx, !Controls.dndOn(ctx))) Bus.fireLater(200, 900)
        else dndSettings()
    }

    fun dndSettings() { svc?.launch(Intent("android.settings.ZEN_MODE_SETTINGS")) }

    fun torch() { Torch.toggle() }

    fun darkMode() = viaTile(QsClicker.DARK, Intent(Settings.ACTION_DISPLAY_SETTINGS))

    fun powerSave() = viaTile(QsClicker.POWER_SAVE, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))

    fun eyeComfort() = viaTile(QsClicker.EYE_COMFORT, Intent("android.settings.NIGHT_DISPLAY_SETTINGS"))

    fun autoBrightness(ctx: Context) {
        if (Controls.setAutoBrightness(ctx, !Controls.autoBrightness(ctx))) Bus.fire()
        else svc?.launch(Launch.writeSettings(ctx))
    }

    fun ringer(ctx: Context) {
        val m = Controls.ringerMode(ctx)
        Controls.setRingerMode(ctx, if (m == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL)
        Bus.fire()
    }

    /** Плитки, которые сами открывают окно: сначала закрываем шторку, потом жмём. */
    private fun openTile(keys: List<String>, fallback: Intent?, failText: String) {
        val s = svc ?: return
        s.panels.close(true)
        s.main.postDelayed({
            s.qs.click(keys, collapseAfter = false) { ok ->
                if (!ok) {
                    if (fallback == null || !Launch.start(s, fallback)) Toast.makeText(s, failText, Toast.LENGTH_LONG).show()
                }
            }
        }, 420)
    }

    fun record() = openTile(QsClicker.RECORD, null, "Добавь плитку «Запись экрана» в быстрые настройки Samsung")

    fun mirroring() = openTile(QsClicker.MIRROR, Intent(Settings.ACTION_CAST_SETTINGS), "Не нашёл Smart View")

    fun timer() { svc?.launchWith { Launch.timer(it) } }
    fun alarm() { svc?.launchWith { Launch.alarms(it) } }
    fun calculator() { svc?.launchWith { Launch.calculator(it) } }
    fun camera() { svc?.launchWith { Launch.camera(it) } }
}

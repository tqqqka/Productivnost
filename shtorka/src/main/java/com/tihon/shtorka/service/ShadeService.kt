package com.tihon.shtorka.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.ContentObserver
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.tihon.shtorka.core.Bus
import com.tihon.shtorka.core.Hub
import com.tihon.shtorka.core.Prefs
import com.tihon.shtorka.system.Backdrop
import com.tihon.shtorka.system.Launch
import com.tihon.shtorka.system.MediaRepo
import com.tihon.shtorka.system.QsClicker
import com.tihon.shtorka.system.Torch
import com.tihon.shtorka.ui.PanelHost
import com.tihon.shtorka.ui.Screen
import com.tihon.shtorka.ui.island.IslandController

/**
 * Сердце приложения. Система запускает включённую службу специальных
 * возможностей сама — после каждой перезагрузки, без нашего участия.
 */
class ShadeService : AccessibilityService() {
    lateinit var panels: PanelHost; private set
    lateinit var island: IslandController; private set
    lateinit var qs: QsClicker; private set
    lateinit var backdrop: Backdrop; private set
    val main = Handler(Looper.getMainLooper())
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!started) return
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    panels.close(false)
                    island.onScreenOff()
                }
                Intent.ACTION_POWER_CONNECTED -> island.onCharging(true)
                Intent.ACTION_POWER_DISCONNECTED -> island.onCharging(false)
                AudioManager.RINGER_MODE_CHANGED_ACTION -> if (!isInitialStickyBroadcast) island.onRinger()
                Intent.ACTION_CONFIGURATION_CHANGED -> Unit
            }
            Bus.fire()
        }
    }

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = Bus.fire()
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (started) {
            panels.applyPrefs()
            island.applyPrefs()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (started) return
        Hub.service = this
        Torch.init(this)
        qs = QsClicker(this)
        backdrop = Backdrop(this)
        panels = PanelHost(this)
        panels.attach()
        island = IslandController(this)
        island.attach()

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            @Suppress("DEPRECATION")
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        registerReceiver(receiver, f)
        val cr = contentResolver
        listOf(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            Settings.Global.getUriFor("mobile_data"),
            Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
        ).forEach { runCatching { cr.registerContentObserver(it, false, observer) } }
        Prefs.sp.registerOnSharedPreferenceChangeListener(prefsListener)
        MediaRepo.start(this)
        started = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!started) return
        panels.onConfigChanged()
        island.onConfigChanged()
        Bus.fire()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (!started) return
        started = false
        runCatching { unregisterReceiver(receiver) }
        runCatching { contentResolver.unregisterContentObserver(observer) }
        Prefs.sp.unregisterOnSharedPreferenceChangeListener(prefsListener)
        panels.detach()
        island.detach()
        Torch.release()
        if (Hub.service === this) Hub.service = null
    }

    /** Запустить экран и убрать шторку. Окно ещё видно в момент запуска — так Android разрешает старт из фона. */
    fun launch(intent: Intent): Boolean {
        val ok = Launch.start(this, intent)
        if (ok) panels.close(true)
        return ok
    }

    fun launchWith(block: (Context) -> Boolean) {
        if (block(this)) panels.close(true)
    }

    fun displayRotation() = Screen.rotation(this)

    /** Для кнопки «Показать островок» в настройках. */
    fun demoIsland() {
        island.onCharging(true)
    }
}

package com.tihon.shtorka.service

import android.accessibilityservice.AccessibilityService
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.tihon.shtorka.core.Hub
import com.tihon.shtorka.core.Prefs
import com.tihon.shtorka.system.Controls
import com.tihon.shtorka.system.MediaRepo
import com.tihon.shtorka.ui.island.IslandController

/**
 * Служба специальных возможностей: только её окно может лежать поверх
 * строки состояния и капли. Включённую службу Android сам запускает после
 * каждой перезагрузки — отсюда автозапуск островка.
 */
class IslandService : AccessibilityService() {
    lateinit var island: IslandController; private set
    val main = Handler(Looper.getMainLooper())
    private var started = false
    private var imePackages: Set<String> = emptySet()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!started) return
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> island.onScreen(false)
                Intent.ACTION_SCREEN_ON -> island.onScreen(true)
                Intent.ACTION_USER_PRESENT -> island.onUnlock()
                Intent.ACTION_POWER_CONNECTED -> island.onCharging(true)
                Intent.ACTION_POWER_DISCONNECTED -> island.onCharging(false)
                Intent.ACTION_BATTERY_CHANGED -> {
                    val b = Controls.battery(c, i)
                    island.onBattery(b.level, b.plugged)
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> if (!isInitialStickyBroadcast) island.onRinger()
                BluetoothDevice.ACTION_ACL_CONNECTED -> device(i)?.let { d ->
                    // Заряд не берём из кэша системы — там бывает устаревшее «100%».
                    // Свежий придёт отдельным событием через секунду-две после подключения.
                    if (isAudio(d)) island.onHeadphones(name(d), -1)
                }
                ACTION_BT_BATTERY -> device(i)?.let { d ->
                    val lvl = i.getIntExtra(EXTRA_BT_BATTERY, -1)
                    if (lvl in 0..100) island.onHeadphonesBattery(name(d), lvl)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun device(i: Intent): BluetoothDevice? = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    private fun isAudio(d: BluetoothDevice) = runCatching {
        d.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
    }.getOrDefault(true)

    private fun name(d: BluetoothDevice) = runCatching { d.name }.getOrNull() ?: "Наушники"

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (started) island.applyPrefs()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (started) return
        Hub.service = this
        imePackages = runCatching {
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
        island = IslandController(this)
        island.attach()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(ACTION_BT_BATTERY)
        }
        registerReceiver(receiver, f)
        Prefs.sp.registerOnSharedPreferenceChangeListener(prefsListener)
        MediaRepo.start(this)
        started = true
    }

    /** Какое приложение на экране: iPhone прячет активность, когда открыто её приложение. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!started || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || pkg in imePackages) return
        island.onForeground(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (started) island.onConfigChanged()
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
        Prefs.sp.unregisterOnSharedPreferenceChangeListener(prefsListener)
        island.detach()
        if (Hub.service === this) Hub.service = null
    }

    companion object {
        private const val ACTION_BT_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BT_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}

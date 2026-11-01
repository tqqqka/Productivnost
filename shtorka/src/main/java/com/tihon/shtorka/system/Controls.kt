package com.tihon.shtorka.system

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.telephony.TelephonyManager
import com.tihon.shtorka.core.Hub
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Чтение и переключение системных состояний. Всё, что Android разрешает
 * менять напрямую, меняется здесь; остальное — через [QsClicker].
 */
object Controls {

    // ---------- Wi-Fi ----------

    private fun wifi(ctx: Context) = ctx.applicationContext.getSystemService(WifiManager::class.java)

    fun wifiOn(ctx: Context) = runCatching { wifi(ctx)?.isWifiEnabled == true }.getOrDefault(false)

    /** Работает благодаря targetSdk 28. Возвращает false, если система отказала. */
    @Suppress("DEPRECATION")
    fun setWifi(ctx: Context, on: Boolean): Boolean =
        runCatching { wifi(ctx)?.setWifiEnabled(on) == true }.getOrDefault(false)

    @Suppress("DEPRECATION")
    fun wifiConnected(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }.getOrDefault(false)

    /** Имя сети — только если выдано разрешение на геолокацию. */
    @Suppress("DEPRECATION")
    fun wifiName(ctx: Context): String? = runCatching {
        val ssid = wifi(ctx)?.connectionInfo?.ssid?.trim('"')
        if (ssid.isNullOrBlank() || ssid == "<unknown ssid>" || ssid == "0x") null else ssid
    }.getOrNull()

    // ---------- Bluetooth ----------

    private fun bt(ctx: Context) = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    fun btOn(ctx: Context) = runCatching { bt(ctx)?.isEnabled == true }.getOrDefault(false)

    @Suppress("DEPRECATION")
    fun setBt(ctx: Context, on: Boolean): Boolean = runCatching {
        val a = bt(ctx) ?: return false
        if (on) a.enable() else a.disable()
    }.getOrDefault(false)

    // ---------- Связь ----------

    fun airplaneOn(ctx: Context) =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    private fun tm(ctx: Context) = ctx.getSystemService(TelephonyManager::class.java)

    fun dataOn(ctx: Context): Boolean {
        runCatching { return tm(ctx).isDataEnabled }
        return Settings.Global.getInt(ctx.contentResolver, "mobile_data", 1) == 1
    }

    fun hasSim(ctx: Context) = runCatching { tm(ctx).simState == TelephonyManager.SIM_STATE_READY }.getOrDefault(false)

    fun carrier(ctx: Context): String = runCatching {
        val t = tm(ctx)
        t.networkOperatorName.takeIf { !it.isNullOrBlank() } ?: t.simOperatorName ?: ""
    }.getOrDefault("")

    /** 0..4 */
    fun signalLevel(ctx: Context): Int =
        if (Build.VERSION.SDK_INT >= 28) runCatching { tm(ctx).signalStrength?.level ?: 0 }.getOrDefault(0) else 3

    fun locationOn(ctx: Context): Boolean = runCatching {
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    /** Скрытый метод, но для старого targetSdk обычно доступен. */
    fun hotspotOn(ctx: Context): Boolean = runCatching {
        val wm = wifi(ctx) ?: return false
        wm.javaClass.getMethod("isWifiApEnabled").invoke(wm) as Boolean
    }.getOrDefault(false)

    // ---------- Экран ----------

    fun canWriteSettings(ctx: Context) = Settings.System.canWrite(ctx)

    fun rotationLocked(ctx: Context) =
        Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0

    fun setRotationLocked(ctx: Context, locked: Boolean, currentRotation: Int): Boolean {
        if (!canWriteSettings(ctx)) return false
        return runCatching {
            if (locked) Settings.System.putInt(ctx.contentResolver, Settings.System.USER_ROTATION, currentRotation)
            Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 0 else 1)
        }.getOrDefault(false)
    }

    private const val GAMMA = 2.2f

    /** Положение ползунка 0..1. Яркость воспринимается нелинейно, как в системном ползунке. */
    fun brightness(ctx: Context): Float {
        val v = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return ((v - 1) / 254f).coerceIn(0f, 1f).pow(1f / GAMMA)
    }

    fun setBrightness(ctx: Context, slider: Float): Boolean {
        if (!canWriteSettings(ctx)) return false
        val v = (1 + 254 * slider.coerceIn(0f, 1f).pow(GAMMA)).roundToInt()
        return runCatching { Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v) }.getOrDefault(false)
    }

    fun autoBrightness(ctx: Context) = Settings.System.getInt(
        ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0,
    ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    fun setAutoBrightness(ctx: Context, on: Boolean): Boolean {
        if (!canWriteSettings(ctx)) return false
        return runCatching {
            Settings.System.putInt(
                ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
        }.getOrDefault(false)
    }

    fun darkMode(ctx: Context) =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun powerSave(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isPowerSaveMode

    // ---------- Звук ----------

    private fun am(ctx: Context) = ctx.getSystemService(AudioManager::class.java)

    fun volume(ctx: Context): Float {
        val am = am(ctx)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun volumeSteps(ctx: Context) = am(ctx).getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    fun setVolume(ctx: Context, v: Float) {
        val am = am(ctx)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (v.coerceIn(0f, 1f) * max).roundToInt()
        if (target != am.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
        }
    }

    fun ringerMode(ctx: Context) = am(ctx).ringerMode

    fun setRingerMode(ctx: Context, mode: Int): Boolean = runCatching { am(ctx).ringerMode = mode }.isSuccess

    // ---------- Не беспокоить ----------

    fun dndOn(ctx: Context): Boolean {
        val f = ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    fun setDnd(ctx: Context, on: Boolean): Boolean {
        Hub.listener?.let {
            runCatching {
                it.requestInterruptionFilter(
                    if (on) NotificationListenerService.INTERRUPTION_FILTER_PRIORITY
                    else NotificationListenerService.INTERRUPTION_FILTER_ALL,
                )
            }.onSuccess { return true }
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
            )
            return true
        }
        return false
    }

    // ---------- Батарея ----------

    data class Battery(val level: Int, val charging: Boolean, val plugged: Boolean)

    fun battery(ctx: Context): Battery {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val pct = if (level >= 0 && scale > 0) level * 100 / scale
        else ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return Battery(pct.coerceIn(0, 100), charging && plugged, plugged)
    }
}

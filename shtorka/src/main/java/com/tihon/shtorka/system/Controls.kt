package com.tihon.shtorka.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import kotlin.math.roundToInt

/** Батарея, режим звонка, заряд наушников. */
object Controls {

    data class Battery(val level: Int, val charging: Boolean, val plugged: Boolean)

    fun battery(ctx: Context, sticky: Intent? = null): Battery {
        val i = sticky ?: ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val pct = if (level >= 0 && scale > 0) level * 100 / scale
        else ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return Battery(pct.coerceIn(0, 100), charging && plugged, plugged)
    }

    fun ringerMode(ctx: Context) = ctx.getSystemService(AudioManager::class.java).ringerMode

    fun volume(ctx: Context): Float {
        val am = ctx.getSystemService(AudioManager::class.java)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat()
    }

    fun setVolume(ctx: Context, v: Float) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val target = (v.coerceIn(0f, 1f) * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt()
        if (target != am.getStreamVolume(AudioManager.STREAM_MUSIC)) runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

}

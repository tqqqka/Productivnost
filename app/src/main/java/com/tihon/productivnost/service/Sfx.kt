package com.tihon.productivnost.service

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tihon.productivnost.R
import com.tihon.productivnost.core.Store

/** Спокойные звуки напоминаний. Загружаются лениво и переиспользуются. */
object Sfx {

    val CALM = R.raw.chime_calm
    val NIGHT = R.raw.chime_night
    val GROWN = R.raw.chime_grown
    val WITHER = R.raw.chime_wither

    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>()
    private val loaded = HashSet<Int>()

    private fun pool(): SoundPool = pool ?: SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
        .also { sp ->
            sp.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded.add(sampleId) }
            pool = sp
        }

    fun preload(context: Context) {
        listOf(CALM, NIGHT, GROWN, WITHER).forEach { res ->
            ids.getOrPut(res) { pool().load(context.applicationContext, res, 1) }
        }
    }

    fun play(context: Context, res: Int, volume: Float = 0.65f) {
        if (Store.isReady && !Store.settings.value.soundEnabled) return
        val sp = pool()
        val id = ids.getOrPut(res) { sp.load(context.applicationContext, res, 1) }
        if (id in loaded) {
            sp.play(id, volume, volume, 1, 0, 1f)
        } else {
            // Первый вызов может прийти до окончания загрузки — доигрываем по готовности.
            sp.setOnLoadCompleteListener { p, sampleId, status ->
                if (status == 0) {
                    loaded.add(sampleId)
                    if (sampleId == id) p.play(id, volume, volume, 1, 0, 1f)
                }
            }
        }
    }

    fun vibrate(context: Context, pattern: LongArray = longArrayOf(0, 28, 120, 28)) {
        if (Store.isReady && !Store.settings.value.vibrationEnabled) return
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }
}

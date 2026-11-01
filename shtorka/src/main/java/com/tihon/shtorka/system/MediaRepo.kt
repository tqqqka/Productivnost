package com.tihon.shtorka.system

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.palette.graphics.Palette
import com.tihon.shtorka.service.NotifListener
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Что сейчас играет. Сессии отдаёт MediaSessionManager — для этого нужен
 * доступ к уведомлениям (тот же, что и для Центра уведомлений).
 */
object MediaRepo {

    class Now(
        val controller: MediaController,
        val pkg: String,
        val title: String,
        val artist: String,
        val art: Bitmap?,
        val accent: Int,
        val playing: Boolean,
        val duration: Long,
        private val position: Long,
        private val positionAt: Long,
        private val speed: Float,
    ) {
        fun position(): Long {
            if (!playing) return position
            val p = position + ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong()
            return if (duration > 0) p.coerceIn(0, duration) else max(0, p)
        }
    }

    var now: Now? = null
        private set

    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()
    fun add(l: () -> Unit) { listeners += l }
    fun remove(l: () -> Unit) { listeners -= l }
    private fun notifyListeners() = listeners.toList().forEach { it() }

    private var msm: MediaSessionManager? = null
    private var comp: ComponentName? = null
    private var controllers: List<MediaController> = emptyList()
    private var started = false

    private var artSource: Bitmap? = null
    private var artScaled: Bitmap? = null
    private var accent: Int = 0xFFFFFFFF.toInt()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = recompute()
        override fun onMetadataChanged(metadata: MediaMetadata?) = recompute()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        setControllers(list ?: emptyList())
    }

    /** Можно звать сколько угодно раз: без доступа к уведомлениям просто ничего не делает. */
    fun start(ctx: Context) {
        if (started) return
        val m = ctx.applicationContext.getSystemService(MediaSessionManager::class.java) ?: return
        val c = ComponentName(ctx, NotifListener::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, c, main)
            msm = m
            comp = c
            started = true
            setControllers(m.getActiveSessions(c))
        } catch (_: SecurityException) {
            started = false
        }
    }

    fun stop() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = emptyList()
        started = false
        now = null
    }

    private fun refreshSessions() {
        val m = msm ?: return
        runCatching { setControllers(m.getActiveSessions(comp)) }
            .onFailure { setControllers(controllers.filter { c -> c.playbackState != null }) }
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        list.forEach { runCatching { it.registerCallback(callback, main) } }
        recompute()
    }

    private fun usable(c: MediaController): Boolean {
        val s = c.playbackState?.state ?: return false
        if (s == PlaybackState.STATE_NONE || s == PlaybackState.STATE_STOPPED || s == PlaybackState.STATE_ERROR) return false
        val md = c.metadata ?: return false
        return !md.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() ||
            !md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isNullOrBlank()
    }

    private fun isPlaying(c: MediaController): Boolean {
        val s = c.playbackState?.state
        return s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING ||
            s == PlaybackState.STATE_FAST_FORWARDING || s == PlaybackState.STATE_REWINDING
    }

    private fun recompute() {
        val c = controllers.firstOrNull { usable(it) && isPlaying(it) } ?: controllers.firstOrNull { usable(it) }
        if (c == null) {
            if (now != null) { now = null; notifyListeners() }
            return
        }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val src = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (src !== artSource) {
            artSource = src
            artScaled = src?.let { scaled(it) }
            accent = 0xFFFFFFFF.toInt()
            artScaled?.let { bmp ->
                Palette.from(bmp).maximumColorCount(12).generate { p ->
                    if (artSource !== src || p == null) return@generate
                    val sw = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch
                    accent = sw?.rgb ?: 0xFFFFFFFF.toInt()
                    recompute()
                }
            }
        }
        val st = c.playbackState
        now = Now(
            controller = c,
            pkg = c.packageName,
            title = title,
            artist = artist,
            art = artScaled,
            accent = accent,
            playing = isPlaying(c),
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            position = st?.position ?: 0L,
            positionAt = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed?.takeIf { it != 0f } ?: 1f,
        )
        notifyListeners()
    }

    private fun scaled(b: Bitmap): Bitmap? = runCatching {
        val maxSide = 360
        val big = max(b.width, b.height)
        if (big <= maxSide) return@runCatching b.copy(Bitmap.Config.ARGB_8888, false)
        val k = maxSide / big.toFloat()
        Bitmap.createScaledBitmap(b, (b.width * k).roundToInt(), (b.height * k).roundToInt(), true)
    }.getOrNull()

    // ---------- Команды ----------

    fun playPause() {
        val n = now ?: return
        if (n.playing) n.controller.transportControls.pause() else n.controller.transportControls.play()
    }

    fun next() { now?.controller?.transportControls?.skipToNext() }
    fun prev() { now?.controller?.transportControls?.skipToPrevious() }
    fun seekTo(ms: Long) { now?.controller?.transportControls?.seekTo(ms) }

    /** Открыть приложение, которое играет. */
    fun open(ctx: Context): Boolean {
        val n = now ?: return false
        n.controller.sessionActivity?.let { pi ->
            if (runCatching { pi.send() }.isSuccess) return true
        }
        return Launch.appLaunch(ctx, n.pkg)?.let { Launch.start(ctx, it) } ?: false
    }
}

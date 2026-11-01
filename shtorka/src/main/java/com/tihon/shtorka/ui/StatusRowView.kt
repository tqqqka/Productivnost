package com.tihon.shtorka.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import android.view.View
import com.tihon.shtorka.R
import com.tihon.shtorka.system.Controls

/**
 * Своя строка состояния: наша шторка лежит поверх системной, поэтому
 * оператор, сеть и батарея рисуются здесь — как в Пункте управления iPhone.
 */
class StatusRowView(ctx: Context) : View(ctx) {
    var sidePadding = dp(28)
    /** Центр капли: значки не должны под неё заезжать. */
    var notchLeft = -1f
    var notchRight = -1f

    private var carrier = ""
    private var signal = 0
    private var sim = false
    private var wifi = false
    private var airplane = false
    private var battery = 100
    private var charging = false
    private var saver = false

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.semibold
        textSize = ctx.dp(15)
        color = Palette.WHITE
        letterSpacing = -0.01f
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.semibold
        textSize = ctx.dp(13.5f)
        color = Palette.WHITE
    }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wifiIcon = ContextCompat.getDrawable(ctx, R.drawable.g_wifi)!!.mutate().also { it.setTint(Palette.WHITE) }
    private val planeIcon = ContextCompat.getDrawable(ctx, R.drawable.g_airplane)!!.mutate().also { it.setTint(Palette.WHITE) }
    private val boltIcon = ContextCompat.getDrawable(ctx, R.drawable.g_bolt)!!.mutate().also { it.setTint(Palette.BLACK) }

    fun refresh() {
        val c = context
        carrier = Controls.carrier(c)
        sim = Controls.hasSim(c)
        signal = Controls.signalLevel(c)
        wifi = Controls.wifiConnected(c)
        airplane = Controls.airplaneOn(c)
        val b = Controls.battery(c)
        battery = b.level
        charging = b.charging || b.plugged
        saver = Controls.powerSave(c)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cy = height / 2f
        val baseline = cy - (text.descent() + text.ascent()) / 2
        var x = sidePadding
        // слева: самолёт или оператор
        if (airplane) {
            val s = dp(15)
            planeIcon.setBounds(x.toInt(), (cy - s / 2).toInt(), (x + s).toInt(), (cy + s / 2).toInt())
            planeIcon.draw(c)
            x += s + dp(6)
        }
        val limit = if (notchLeft > 0) notchLeft - dp(8) else width / 2f - dp(30)
        val name = when {
            airplane -> ""
            carrier.isNotBlank() -> carrier
            !sim -> "Нет SIM"
            else -> ""
        }
        if (name.isNotEmpty()) {
            val n = text.breakText(name, true, (limit - x).coerceAtLeast(0f), null)
            c.drawText(name, 0, n, x, baseline, text)
        }

        // справа налево: батарея, процент, Wi-Fi, сеть
        var r = width - sidePadding
        val bw = dp(26)
        val bh = dp(12.5f)
        val fill = when {
            charging -> Palette.GREEN
            saver -> Palette.YELLOW
            battery <= 20 -> Palette.RED
            else -> Palette.WHITE
        }
        Glyphs.battery(c, r - bw, cy, bw, bh, battery, fill, p)
        if (charging) {
            val s = dp(10)
            val bx = r - bw + bw * 0.45f - s / 2
            boltIcon.setBounds(bx.toInt(), (cy - s / 2).toInt(), (bx + s).toInt(), (cy + s / 2).toInt())
            boltIcon.draw(c)
        }
        r -= bw + dp(5)
        val pct = "$battery%"
        val pw = small.measureText(pct)
        c.drawText(pct, r - pw, cy - (small.descent() + small.ascent()) / 2, small)
        r -= pw + dp(7)
        if (wifi) {
            val s = dp(17)
            wifiIcon.setBounds((r - s).toInt(), (cy - s / 2).toInt(), r.toInt(), (cy + s / 2).toInt())
            wifiIcon.draw(c)
            r -= s + dp(5)
        }
        if (sim && !airplane) {
            val h = dp(11)
            val w = Glyphs.signalWidth(h)
            Glyphs.signal(c, r - w, cy + h / 2, h, signal, Palette.WHITE, 0x59FFFFFF, p)
        }
    }
}

package com.tihon.productivnost.feature.pushups

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Счётчик отжиманий по скелету из ML Kit.
 *
 * Считаем угол в локте: рука выпрямлена — верхняя точка, согнута — нижняя.
 * Повторение засчитывается на возврате вверх, поэтому «зависание» внизу
 * не накручивает счётчик.
 */
class PushupCounter {

    enum class Stage { UNKNOWN, UP, DOWN }

    var stage = Stage.UNKNOWN
        private set
    var reps = 0
        private set
    /** 0f — рука выпрямлена, 1f — грудь у пола. */
    var depth = 0f
        private set
    var visible = false
        private set
    var lastRepAt = 0L
        private set

    private var downAt = 0L

    fun reset() {
        stage = Stage.UNKNOWN; reps = 0; depth = 0f; visible = false; downAt = 0L
    }

    fun accept(pose: Pose, now: Long): Boolean {
        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val le = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val re = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        val angles = ArrayList<Double>(2)
        if (ok(ls) && ok(le) && ok(lw)) angles += angle(ls!!, le!!, lw!!)
        if (ok(rs) && ok(re) && ok(rw)) angles += angle(rs!!, re!!, rw!!)

        visible = angles.isNotEmpty()
        if (!visible) return false

        val a = angles.average()
        depth = (((165.0 - a) / 80.0).coerceIn(0.0, 1.0)).toFloat()

        var counted = false
        when {
            a > 152.0 -> {
                if (stage == Stage.DOWN && now - downAt > 250L) {
                    reps++
                    lastRepAt = now
                    counted = true
                }
                stage = Stage.UP
            }
            a < 98.0 -> {
                if (stage != Stage.DOWN) downAt = now
                stage = Stage.DOWN
            }
        }
        return counted
    }

    private fun ok(l: PoseLandmark?) = l != null && l.inFrameLikelihood > 0.5f

    private fun angle(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Double {
        val r = Math.toDegrees(
            (atan2((c.position.y - b.position.y).toDouble(), (c.position.x - b.position.x).toDouble()) -
                atan2((a.position.y - b.position.y).toDouble(), (a.position.x - b.position.x).toDouble())),
        )
        var ang = abs(r)
        if (ang > 180.0) ang = 360.0 - ang
        return ang
    }
}

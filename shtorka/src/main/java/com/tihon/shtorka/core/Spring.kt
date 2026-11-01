package com.tihon.shtorka.core

import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * Пружина над одним числом — как в UIKit: анимация подхватывает скорость
 * пальца и может немного «перелететь» цель.
 */
class Spring(
    initial: Float = 0f,
    private val minVisibleChange: Float = 0.0005f,
    private val onUpdate: (Float) -> Unit,
) {
    private val holder = FloatValueHolder(initial)
    private var endAction: ((Boolean) -> Unit)? = null
    private val anim = SpringAnimation(holder).apply {
        spring = SpringForce()
        minimumVisibleChange = this@Spring.minVisibleChange
        addUpdateListener { _, v, _ -> onUpdate(v) }
        addEndListener { _, canceled, _, _ ->
            val a = endAction
            endAction = null
            a?.invoke(canceled)
        }
    }

    val value: Float get() = holder.value
    val isRunning: Boolean get() = anim.isRunning
    val target: Float get() = anim.spring.finalPosition

    /** Мгновенно поставить значение (палец ведёт), без анимации. */
    fun snap(v: Float) {
        if (anim.isRunning) anim.cancel()
        holder.value = v
        onUpdate(v)
    }

    fun animateTo(
        target: Float,
        velocity: Float = 0f,
        stiffness: Float = 420f,
        damping: Float = 0.82f,
        onEnd: ((canceled: Boolean) -> Unit)? = null,
    ) {
        if (anim.isRunning) {
            // Продолжаем текущее движение к новой цели, не теряя скорость.
            endAction?.let { prev -> endAction = null; prev(true) }
            anim.spring.stiffness = stiffness
            anim.spring.dampingRatio = damping
            endAction = onEnd
            anim.animateToFinalPosition(target)
            return
        }
        if (holder.value == target && velocity == 0f) {
            onUpdate(target)
            onEnd?.invoke(false)
            return
        }
        anim.spring.stiffness = stiffness
        anim.spring.dampingRatio = damping
        anim.spring.finalPosition = target
        endAction = onEnd
        anim.setStartValue(holder.value)
        anim.setStartVelocity(velocity)
        anim.start()
    }

    fun cancel() {
        if (anim.isRunning) anim.cancel()
    }

    companion object {
        const val PIXELS = DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
    }
}

package com.example.chatapp.ui.animation

import android.view.animation.Interpolator
import kotlin.math.cos
import kotlin.math.pow

/**
 * 自定义回弹插值器
 * 使动画在结束时有弹性效果
 */
class BounceInterpolator(private val amplitude: Double = 0.1, private val frequency: Double = 10.0) : Interpolator {

    override fun getInterpolation(time: Float): Float {
        // 时间为0到1之间
        return (-1.0 * Math.E.pow(-time / amplitude) * cos(frequency * time) + 1).toFloat()
    }
}
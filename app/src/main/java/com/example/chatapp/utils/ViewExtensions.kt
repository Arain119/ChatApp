package com.example.chatapp.utils

import android.view.View
import com.example.chatapp.utils.HapticUtils

/**
 * 设置带震动反馈的点击监听器
 * @param isStrong 是否使用强震动
 * @param onClick 点击处理函数
 */
fun View.setOnClickListenerWithHaptic(isStrong: Boolean = false, onClick: (View) -> Unit) {
    this.setOnClickListener { view ->
        // 执行震动反馈
        HapticUtils.performViewHapticFeedback(view, isStrong)

        // 调用原始点击处理
        onClick(view)
    }
}

/**
 * 设置带震动反馈的长按监听器
 * @param isStrong 是否使用强震动
 * @param onLongClick 长按处理函数
 */
fun View.setOnLongClickListenerWithHaptic(isStrong: Boolean = true, onLongClick: (View) -> Boolean) {
    this.setOnLongClickListener { view ->
        // 执行震动反馈，长按通常使用较强的震动
        HapticUtils.performHapticFeedback(view.context, isStrong)

        // 调用原始长按处理
        onLongClick(view)
    }
}
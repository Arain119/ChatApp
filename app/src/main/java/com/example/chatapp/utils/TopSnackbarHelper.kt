package com.example.chatapp.ui.util

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import com.example.chatapp.R
import com.google.android.material.snackbar.Snackbar

/**
 * 顶部Snackbar辅助类 - 用于在屏幕顶部显示Snackbar
 */
object TopSnackbarHelper {

    /**
     * 在屏幕顶部显示Snackbar
     */
    fun showTopSnackbar(
        view: View,
        message: String,
        duration: Int,
        actionText: String? = null,
        action: (() -> Unit)? = null
    ): Snackbar {
        val snackbar = Snackbar.make(view, message, duration)

        // 设置动作按钮
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action() }
            snackbar.setActionTextColor(ContextCompat.getColor(view.context, R.color.primary))
        }

        // 获取Snackbar的View
        val snackbarView = snackbar.view

        // 如果父容器是CoordinatorLayout，需要修改布局参数
        val params = snackbarView.layoutParams
        when (params) {
            is CoordinatorLayout.LayoutParams -> {
                params.gravity = Gravity.TOP
                params.setMargins(
                    params.leftMargin,
                    params.topMargin + 100, // 增加顶部边距，确保在Toolbar下方
                    params.rightMargin,
                    params.bottomMargin
                )
            }
            is FrameLayout.LayoutParams -> {
                params.gravity = Gravity.TOP
                params.setMargins(
                    params.leftMargin,
                    params.topMargin + 100, // 增加顶部边距，确保在Toolbar下方
                    params.rightMargin,
                    params.bottomMargin
                )
            }
            is ViewGroup.MarginLayoutParams -> {
                params.setMargins(
                    params.leftMargin,
                    params.topMargin + 100, // 增加顶部边距，确保在Toolbar下方
                    params.rightMargin,
                    params.bottomMargin
                )
            }
        }
        snackbarView.layoutParams = params

        // 设置背景颜色为更突出的颜色
        snackbarView.setBackgroundColor(ContextCompat.getColor(view.context, R.color.primary_light))

        snackbar.show()
        return snackbar
    }
}
package com.example.chatapp.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.HapticFeedbackConstants

/**
 * 震动反馈工具类
 * 提供全局统一的震动反馈功能
 */
object HapticUtils {

    private const val TAG = "HapticUtils"

    /**
     * 执行震动反馈
     * @param context 上下文
     * @param isStrong 是否需要强震动
     */
    fun performHapticFeedback(context: Context, isStrong: Boolean = false) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 对于 Android 12 及以上版本
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                // 对于 Android 12 以下版本
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            // 执行震动 - 根据强度选择不同的震动模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 对于 Android 8.0 及以上版本
                if (isStrong) {
                    // 强震动 - 短促有力效果 (20毫秒，高强度255)
                    vibrator.vibrate(VibrationEffect.createOneShot(20, 255))
                } else {
                    // 普通震动 - 更短促的效果 (15毫秒，中高强度200)
                    vibrator.vibrate(VibrationEffect.createOneShot(15, 200))
                }
            } else {
                // 对于较旧版本
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (isStrong) 25 else 15) // 减少持续时间使震动更短促
            }
        } catch (e: Exception) {
            // 忽略震动失败，不影响主功能
            Log.e(TAG, "震动执行失败: ${e.message}", e)
        }
    }

    /**
     * 在视图上执行震动反馈
     * 使用Android原生的触觉反馈
     * 适用于按钮等UI元素
     */
    fun performViewHapticFeedback(view: View, isStrong: Boolean = false) {
        try {
            // 选择适当的反馈常量
            val feedbackConstant = if (isStrong) {
                // 强震动 - 使用CLOCK_TICK提供更清脆的感觉
                HapticFeedbackConstants.CLOCK_TICK
            } else {
                // 普通震动 - 使用KEYBOARD_TAP提供更短促的感觉
                HapticFeedbackConstants.KEYBOARD_TAP
            }

            // 临时启用触觉反馈
            view.isHapticFeedbackEnabled = true

            // 执行触觉反馈 - 设置标志位增强反馈效果
            view.performHapticFeedback(
                feedbackConstant,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (e: Exception) {
            // 忽略失败，不影响主功能
            Log.e(TAG, "视图触觉反馈失败: ${e.message}", e)
        }
    }

    /**
     * 执行双重震动反馈
     * 适用于重要操作确认
     */
    fun performDoubleHapticFeedback(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 创建双击效果
                val timings = longArrayOf(0, 15, 30, 15)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                // 旧版本使用简单的双击效果
                val pattern = longArrayOf(0, 15, 30, 15)
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "双重震动执行失败: ${e.message}", e)
        }
    }
}
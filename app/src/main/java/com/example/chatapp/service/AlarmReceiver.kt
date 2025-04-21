package com.example.chatapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * 闹钟接收器
 * 接收闹钟广播并触发闹钟提醒
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "接收到闹钟广播: ${intent.action}")

        // 处理启动完成广播
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            // 设备重启后重新调度所有闹钟
            Log.d(TAG, "设备已重启，重新调度所有闹钟")
            rescheduleAlarmsAfterReboot(context)
            return
        }

        // 处理自定义闹钟触发广播
        if (intent.action == "com.example.chatapp.ACTION_TRIGGER_ALARM") {
            // 提取闹钟信息
            val alarmId = intent.getStringExtra("ALARM_ID") ?: return
            val title = intent.getStringExtra("ALARM_TITLE") ?: "闹钟提醒"
            val description = intent.getStringExtra("ALARM_DESCRIPTION") ?: ""
            val alarmTime = intent.getLongExtra("ALARM_TIME", System.currentTimeMillis())
            val isOneTime = intent.getBooleanExtra("IS_ONE_TIME", true)

            Log.d(TAG, "触发闹钟: ID=$alarmId, 标题=$title")

            // 获取WakeLock，确保设备唤醒
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ChatApp:AlarmReceiverWakeLock"
            )
            wakeLock.acquire(60000) // 持有60秒

            try {
                // 启动前台服务显示闹钟通知
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra("ALARM_ID", alarmId)
                    putExtra("ALARM_TITLE", title)
                    putExtra("ALARM_DESCRIPTION", description)
                    putExtra("ALARM_TIME", alarmTime)
                    putExtra("IS_ONE_TIME", isOneTime)
                }

                // 在Android 1O及以上版本使用startForegroundService
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } finally {
                // 确保WakeLock被释放
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }
    }

    /**
     * 设备重启后重新调度所有闹钟
     */
    private fun rescheduleAlarmsAfterReboot(context: Context) {
        val intent = Intent(context, AlarmRescheduleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
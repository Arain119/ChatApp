package com.example.chatapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 闹钟关闭接收器
 * 处理关闭闹钟的广播
 */
class AlarmDismissReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmDismissReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "接收到闹钟关闭广播")

        // 提取闹钟ID和通知ID
        val alarmId = intent.getStringExtra("ALARM_ID") ?: return
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)

        // 停止闹钟服务
        val serviceIntent = Intent(context, AlarmService::class.java)
        context.stopService(serviceIntent)

        // 取消通知
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(notificationId)
        }

        Log.d(TAG, "闹钟已关闭: ID=$alarmId")
    }
}
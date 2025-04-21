package com.example.chatapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.chatapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟重新调度服务
 * 设备重启后重新调度所有闹钟
 */
class AlarmRescheduleService : Service() {

    companion object {
        private const val TAG = "AlarmRescheduleService"
        private const val CHANNEL_ID = "alarm_reschedule_channel"
        private const val NOTIFICATION_ID = 10002
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 创建前台服务通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("重新调度闹钟")
            .setContentText("正在恢复您的闹钟设置...")
            .setSmallIcon(R.drawable.ic_notification) // 确保资源存在
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // 指定前台服务类型并启动前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 重新调度所有激活的闹钟
        serviceScope.launch {
            try {
                val alarmManager = AlarmManager(applicationContext)
                alarmManager.rescheduleAllActiveAlarms()
                Log.d(TAG, "闹钟重新调度完成")
            } catch (e: Exception) {
                Log.e(TAG, "闹钟重新调度失败", e)
            } finally {
                // 完成后停止服务
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "闹钟重新调度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于在设备重启后重新调度闹钟的通知渠道"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
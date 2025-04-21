package com.example.chatapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import com.example.chatapp.ui.AlarmFullScreenActivity
import android.app.Service
import android.graphics.Color
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.chatapp.MainActivity
import com.example.chatapp.R
import com.example.chatapp.data.db.ChatDatabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闹钟服务
 * 前台服务，显示闹钟通知并播放声音
 */
class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"
        private const val CHANNEL_ID = "alarm_channel"
        private const val CHANNEL_NAME = "闹钟提醒"
        private const val NOTIFICATION_ID = 10001
    }

    private lateinit var notificationManager: NotificationManager
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmService onCreate()")

        try {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createNotificationChannel()

            // 获取Vibrator服务
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            // 获取PowerManager WakeLock
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ChatApp:AlarmWakeLock"
            )
        } catch (e: Exception) {
            Log.e(TAG, "AlarmService初始化失败", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "闹钟服务已启动")

        try {
            // 提取闹钟信息
            val alarmId = intent?.getStringExtra("ALARM_ID") ?: return START_NOT_STICKY
            val title = intent.getStringExtra("ALARM_TITLE") ?: "闹钟提醒"
            val description = intent.getStringExtra("ALARM_DESCRIPTION") ?: ""
            val alarmTime = intent.getLongExtra("ALARM_TIME", System.currentTimeMillis())
            val isOneTime = intent.getBooleanExtra("IS_ONE_TIME", true)

            // 创建通知并启动前台服务
            val notification = createAlarmNotification(alarmId, title, description, alarmTime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // 如果是一次性闹钟，更新数据库
            if (isOneTime) {
                CoroutineScope(Dispatchers.IO).launch {
                    val dbHelper = ChatDatabaseHelper.getInstance(applicationContext)
                    dbHelper.updateAlarmActiveState(alarmId, false)
                }
            }

            // 播放闹钟声音和振动
            playAlarmSound()
            startVibration()

            // 获取WakeLock来确保设备在显示通知时唤醒
            wakeLock?.acquire(60 * 1000L) // 最多持有60秒
        } catch (e: Exception) {
            Log.e(TAG, "处理闹钟服务失败", e)
            // 在异常情况下显示基本通知，确保服务不被终止
            val fallbackNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("闹钟提醒")
                .setContentText("闹钟时间到")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            startForeground(NOTIFICATION_ID, fallbackNotification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放资源
        stopAlarmSound()
        stopVibration()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
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
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH // 使用HIGH级别确保通知能发出声音
            ).apply {
                description = "用于显示闹钟提醒的通知渠道"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 500, 500) // 强振动模式

                // 锁屏可见性
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC

                // 设置铃声
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )

                // 设置渠道绕过免打扰模式
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }

                // 确保通知显示在锁屏上
                setShowBadge(true)

                // 设置通知重要性为闹钟
                importance = NotificationManager.IMPORTANCE_HIGH
            }

            // 确保以最高优先级发送通知
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建闹钟通知
     */
    private fun createAlarmNotification(
        alarmId: String,
        title: String,
        description: String,
        alarmTime: Long
    ): Notification {
        // 格式化时间
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeString = timeFormat.format(Date(alarmTime))

        // 创建闹钟活动的Intent
        val fullScreenIntent = Intent(this, AlarmFullScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_TITLE", title)
            putExtra("ALARM_TIME", timeString)
            putExtra("FROM_ALARM_NOTIFICATION", true)
        }

        // 使用高优先级PendingIntent
        val pendingFullScreenIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(), // 使用时间戳确保每次创建的PendingIntent都不同
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建关闭闹钟的PendingIntent
        val dismissIntent = Intent(this, AlarmDismissReceiver::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("NOTIFICATION_ID", NOTIFICATION_ID)
        }
        val pendingDismissIntent = PendingIntent.getBroadcast(
            this,
            System.currentTimeMillis().toInt() + 1, // 确保唯一
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 构建增强版通知
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (description.isNotEmpty()) "$timeString $description" else timeString)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX) // 确保最高优先级
            .setCategory(NotificationCompat.CATEGORY_ALARM) // 设置为闹钟类别
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 确保锁屏可见
            .setContentIntent(pendingFullScreenIntent)
            .setFullScreenIntent(pendingFullScreenIntent, true) // 设置全屏意图
            .setOngoing(true) // 确保通知不能被滑动删除
            .addAction(R.drawable.ic_close, "关闭", pendingDismissIntent)
            .setAutoCancel(false)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // 使用默认的声音、震动和灯光
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) // 确保使用闹钟声音
            .setVibrate(longArrayOf(0, 500, 500, 500, 500, 500)) // 添加强力震动模式
            .setLights(Color.RED, 1000, 1000) // 添加呼吸灯效果
            .build()
    }

    /**
     * 播放闹钟声音
     */
    private fun playAlarmSound() {
        try {
            // 使用自定义音乐资源
            val alarmUri = Uri.parse("android.resource://" + packageName + "/" + R.raw.tassel)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放闹钟声音失败", e)
            // 失败时回退到系统默认铃声
            try {
                val defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, defaultAlarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "回退到系统铃声也失败", e)
            }
        }
    }

    /**
     * 停止闹钟声音
     */
    private fun stopAlarmSound() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

    /**
     * 开始振动
     */
    private fun startVibration() {
        if (vibrator?.hasVibrator() == true) {
            // 振动模式：1秒开，1秒关，重复
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0) // 0表示重复从索引0开始
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0) // 0表示重复从索引0开始
            }
        }
    }

    /**
     * 停止振动
     */
    private fun stopVibration() {
        vibrator?.cancel()
    }
}
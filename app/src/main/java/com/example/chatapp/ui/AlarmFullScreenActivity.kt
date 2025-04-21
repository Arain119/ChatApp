package com.example.chatapp.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.chatapp.R
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.service.AlarmDismissReceiver

class AlarmFullScreenActivity : AppCompatActivity() {
    // 复制第一个文档中的完整代码
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var alarmId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置窗口标志，确保在锁屏上显示
        setupWindowFlags()

        setContentView(R.layout.activity_alarm_fullscreen)

        // 获取闹钟信息
        alarmId = intent.getStringExtra("ALARM_ID")
        val title = intent.getStringExtra("ALARM_TITLE") ?: "闹钟提醒"
        val timeString = intent.getStringExtra("ALARM_TIME") ?: ""

        // 设置UI内容
        findViewById<TextView>(R.id.alarmTitleText).text = title
        findViewById<TextView>(R.id.alarmTimeText).text = timeString

        // 获取AI名称并设置
        val settingsManager = SettingsManager(this)
        val aiName = try {
            // 尝试从SettingsManager获取AI名称
            val method = settingsManager.javaClass.getMethod("getAiName")
            method.invoke(settingsManager) as? String ?: "ChatGPT"
        } catch (e: Exception) {
            "ChatGPT" // 默认值
        }
        findViewById<TextView>(R.id.aiNameText).text = aiName

        // 设置关闭按钮
        findViewById<CardView>(R.id.dismissButtonContainer).setOnClickListener {
            dismissAlarm()
        }

        // 播放闹钟声音和震动
        playAlarmSound()
        startVibration()
    }

    private fun setupWindowFlags() {
        // 在锁屏上显示，让屏幕点亮
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            // 解锁屏幕
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // 设置窗口为全屏
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // 设置为高优先级窗口
        window.attributes.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }

    private fun playAlarmSound() {
        try {
            // 使用闹钟铃声
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmFullScreenActivity, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator?.hasVibrator() == true) {
            // 强力振动模式：500ms开，500ms关，重复
            val pattern = longArrayOf(0, 500, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0) // 0表示重复
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0) // 0表示重复
            }
        }
    }

    private fun dismissAlarm() {
        // 停止声音和震动
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        // 发送关闭闹钟的广播
        alarmId?.let { id ->
            val intent = Intent(this, AlarmDismissReceiver::class.java).apply {
                putExtra("ALARM_ID", id)
                putExtra("NOTIFICATION_ID", 10001) // 使用与AlarmService中相同的ID
            }
            sendBroadcast(intent)
        }

        // 关闭活动
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保资源被释放
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
    }
}
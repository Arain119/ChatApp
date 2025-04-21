package com.example.chatapp.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 日记定时器
 * 负责设置每晚9点的日记生成定时任务
 */
class DiaryScheduler {

    companion object {
        private const val TAG = "DiaryScheduler"
        private const val DIARY_ALARM_REQUEST_CODE = 9900
        const val ACTION_GENERATE_DIARY = "com.example.chatapp.ACTION_GENERATE_DIARY"

        /**
         * 在应用启动时调用此方法设置定时任务
         */
        fun scheduleDailyDiary(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // 创建用于接收闹钟的PendingIntent
            val intent = Intent(context, DiaryAlarmReceiver::class.java).apply {
                action = ACTION_GENERATE_DIARY
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DIARY_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 设置每天晚上9点触发
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 21) // 21点 = 晚上9点
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)

            // 如果当前时间已经过了今天的9点，设置为明天的9点
            if (calendar.timeInMillis < System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            // 根据Android版本使用不同的方法设置重复闹钟
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )

                // 通过日志记录下一次触发时间
                Log.d(TAG, "日记生成定时任务已设置，将于 ${calendar.time} 触发")
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            // 在某些设备上，需要重启设备后才能确保闹钟继续工作，所以我们还需要设置BOOT_COMPLETED广播接收器
            Log.d(TAG, "日记生成定时任务已设置，每晚9点将自动生成日记")
        }
    }
}

/**
 * 日记闹钟接收器
 * 接收定时闹钟广播并触发日记生成
 */
class DiaryAlarmReceiver : BroadcastReceiver() {

    private val TAG = "DiaryAlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DiaryScheduler.ACTION_GENERATE_DIARY) {
            Log.d(TAG, "收到日记生成闹钟广播，开始生成日记")

            // 启动日记生成
            val appScope = CoroutineScope(Dispatchers.IO)
            appScope.launch {
                val diaryService = DiaryService(context)
                diaryService.generateAndPublishDailyDiary()

                // 重新设置明天的闹钟
                DiaryScheduler.scheduleDailyDiary(context)
            }
        }
    }
}

/**
 * 启动时设置闹钟的广播接收器
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 设备重启后重新设置闹钟
            DiaryScheduler.scheduleDailyDiary(context)
        }
    }
}
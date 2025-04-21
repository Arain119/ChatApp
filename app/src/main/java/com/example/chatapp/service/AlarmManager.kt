package com.example.chatapp.service

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.chatapp.data.db.AlarmEntity
import com.example.chatapp.data.db.ChatDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 闹钟管理器
 * 负责闹钟的设置、取消和管理
 */
class AlarmManager(private val context: Context) {

    companion object {
        private const val TAG = "AlarmManager"
        private const val ALARM_REQUEST_CODE_PREFIX = 10000  // 请求码前缀，避免与其他PendingIntent冲突
    }

    private val dbHelper = ChatDatabaseHelper.getInstance(context)
    private val systemAlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val alarmIntentAnalyzer = AlarmIntentAnalyzer()

    /**
     * 从用户输入分析并设置闹钟
     * @param userInput 用户输入文本
     * @return Pair<Boolean, String> 是否成功设置闹钟及消息
     */
    suspend fun analyzeAndSetAlarm(userInput: String): Pair<Boolean, String> {
        // 分析用户输入
        val alarmIntent = alarmIntentAnalyzer.analyzeText(userInput)

        return when (alarmIntent.type) {
            AlarmIntentAnalyzer.AlarmIntentType.SET -> {
                if (alarmIntent.parameters != null) {
                    setAlarm(alarmIntent.parameters)
                } else {
                    Pair(false, "")
                }
            }
            AlarmIntentAnalyzer.AlarmIntentType.CANCEL -> {
                cancelAlarmsByDescription(alarmIntent.timeDescription)
            }
            else -> {
                Log.d(TAG, "未检测到有效的闹钟意图")
                Pair(false, "")
            }
        }
    }

    /**
     * 设置闹钟
     */
    suspend fun setAlarm(parameters: AlarmIntentAnalyzer.AlarmParameters): Pair<Boolean, String> {
        // 检查时间是否有效
        if (parameters.triggerTimeMillis <= System.currentTimeMillis()) {
            return Pair(false, "闹钟时间已过，无法设置。")
        }

        // 创建闹钟实体
        val alarmEntity = AlarmEntity(
            triggerTime = parameters.triggerTimeMillis,
            title = parameters.title,
            description = parameters.description,
            isOneTime = parameters.isOneTime,
            repeatDays = parameters.repeatDays
        )

        // 保存闹钟到数据库
        dbHelper.insertAlarm(alarmEntity)

        // 设置闹钟
        scheduleAlarm(alarmEntity)

        // 格式化时间为易读格式
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = parameters.triggerTimeMillis

        // 检查并打印当前时间与触发时间的差异，用于调试
        val currentTime = System.currentTimeMillis()
        val diffInSeconds = (parameters.triggerTimeMillis - currentTime) / 1000
        Log.d(TAG, "闹钟将在${diffInSeconds}秒后触发，参数：时=${parameters.hour}, 分=${parameters.minute}")

        // 使用calendar中的值
        val hourStr = calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val minuteStr = calendar.get(Calendar.MINUTE).toString().padStart(2, '0')
        val timeStr = "$hourStr:$minuteStr"

        return Pair(true, "已设置\"${parameters.title}\"的闹钟，将在${timeStr}提醒您。")
    }

    /**
     * 根据描述取消闹钟
     */
    private suspend fun cancelAlarmsByDescription(timeDescription: String?): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            val alarms = dbHelper.getAllActiveAlarms()

            if (alarms.isEmpty()) {
                return@withContext Pair(true, "当前没有设置闹钟。")
            }

            // 如果是删除所有闹钟
            if (timeDescription == "ALL" || timeDescription.isNullOrEmpty()) {
                Log.d(TAG, "正在删除所有闹钟")
                var count = 0

                for (alarm in alarms) {
                    cancelAlarm(alarm.id)
                    count++
                }

                return@withContext if (count > 0) {
                    Pair(true, "已删除全部${count}个闹钟。")
                } else {
                    Pair(true, "当前没有设置闹钟。")
                }
            }

            // 按时间描述筛选闹钟
            val matchingAlarms = alarms.filter { alarm ->
                matchesTimeDescription(alarm, timeDescription)
            }

            if (matchingAlarms.isEmpty()) {
                return@withContext Pair(true, "没有找到符合条件的闹钟。")
            }

            // 取消匹配的闹钟
            for (alarm in matchingAlarms) {
                cancelAlarm(alarm.id)
            }

            Pair(true, "已取消${matchingAlarms.size}个闹钟。")
        }
    }

    /**
     * 检查闹钟是否匹配时间描述
     */
    private fun matchesTimeDescription(alarm: AlarmEntity, timeDescription: String): Boolean {
        if (timeDescription.isEmpty()) return true  // 空描述匹配所有闹钟

        val calendar = Calendar.getInstance().apply {
            timeInMillis = alarm.triggerTime
        }

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // 检查时间部分
        if (timeDescription.contains(Regex("\\d+[:：]\\d+"))) {
            val timeRegex = "(\\d+)[:：](\\d+)".toRegex()
            val match = timeRegex.find(timeDescription)
            if (match != null) {
                val specifiedHour = match.groupValues[1].toInt()
                val specifiedMinute = match.groupValues[2].toInt()

                // 检查时间是否匹配
                if (hour != specifiedHour || minute != specifiedMinute) {
                    return false
                }
            }
        }

        // 检查时间段
        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_YEAR)
        val alarmDay = calendar.get(Calendar.DAY_OF_YEAR)

        when {
            // 检查是否匹配"明天"
            timeDescription.contains("明天") && (alarmDay - today) != 1 -> return false

            // 检查是否匹配"后天"
            timeDescription.contains("后天") && (alarmDay - today) != 2 -> return false

            // 检查时间段
            timeDescription.contains("上午") && hour !in 5..11 -> return false
            timeDescription.contains("中午") && hour !in 11..13 -> return false
            timeDescription.contains("下午") && hour !in 13..18 -> return false
            timeDescription.contains("晚上") && (hour < 18 || hour > 23) -> return false
        }

        return true
    }

    /**
     * 获取所有闹钟
     */
    suspend fun getAllAlarms(): List<AlarmEntity> {
        return dbHelper.getAllAlarms()
    }

    /**
     * 获取所有激活的闹钟
     */
    suspend fun getAllActiveAlarms(): List<AlarmEntity> {
        return dbHelper.getAllActiveAlarms()
    }

    /**
     * 根据ID获取闹钟
     */
    suspend fun getAlarmById(alarmId: String): AlarmEntity? {
        return dbHelper.getAlarmById(alarmId)
    }

    /**
     * 取消闹钟
     */
    suspend fun cancelAlarm(alarmId: String): Boolean {
        val alarm = dbHelper.getAlarmById(alarmId) ?: return false

        // 取消系统闹钟
        val alarmIntent = createAlarmIntent(alarm)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            generateRequestCode(alarm.id),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        systemAlarmManager.cancel(pendingIntent)

        // 更新数据库
        dbHelper.updateAlarmActiveState(alarmId, false)
        Log.d(TAG, "取消闹钟成功: ID=${alarm.id}, 时间=${alarm.triggerTime}, 标题=${alarm.title}")
        return true
    }

    /**
     * 删除闹钟
     */
    suspend fun deleteAlarm(alarmId: String): Boolean {
        // 先取消闹钟
        cancelAlarm(alarmId)

        // 从数据库删除
        dbHelper.deleteAlarm(alarmId)
        return true
    }

    /**
     * 重新调度所有激活的闹钟，用于应用启动时恢复闹钟
     */
    suspend fun rescheduleAllActiveAlarms() {
        val activeAlarms = dbHelper.getAllActiveAlarms()
        for (alarm in activeAlarms) {
            // 如果是一次性闹钟且时间已过，则跳过
            if (alarm.isOneTime && alarm.triggerTime < System.currentTimeMillis()) {
                continue
            }
            scheduleAlarm(alarm)
        }
        Log.d(TAG, "已重新调度 ${activeAlarms.size} 个激活的闹钟")
    }

    /**
     * 调度闹钟
     */
    private fun scheduleAlarm(alarm: AlarmEntity) {
        val alarmIntent = createAlarmIntent(alarm)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            generateRequestCode(alarm.id),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 设置精确闹钟，确保准时触发
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            systemAlarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarm.triggerTime,
                pendingIntent
            )
        } else {
            systemAlarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                alarm.triggerTime,
                pendingIntent
            )
        }

        Log.d(TAG, "闹钟已设置: ID=${alarm.id}, 时间=${alarm.triggerTime}, 标题=${alarm.title}")
    }

    /**
     * 创建闹钟Intent
     */
    private fun createAlarmIntent(alarm: AlarmEntity): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.chatapp.ACTION_TRIGGER_ALARM"
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_TITLE", alarm.title)
            putExtra("ALARM_DESCRIPTION", alarm.description)
            putExtra("ALARM_TIME", alarm.triggerTime)
            putExtra("IS_ONE_TIME", alarm.isOneTime)
        }
    }

    /**
     * 生成请求码，用于区分不同的PendingIntent
     */
    private fun generateRequestCode(alarmId: String): Int {
        return ALARM_REQUEST_CODE_PREFIX + alarmId.hashCode()
    }

    /**
     * 检查精确闹钟权限 (Android 12+)
     */
    fun checkExactAlarmPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // 引导用户到设置界面开启精确闹钟权限
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                activity.startActivity(intent)
            }
        }
    }
}
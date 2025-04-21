package com.example.chatapp.service

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.math.min

/**
 * 闹钟意图分析器
 * 分析用户输入以识别闹钟意图和参数
 */
class AlarmIntentAnalyzer {

    companion object {
        private const val TAG = "AlarmIntentAnalyzer"
    }

    /**
     * 闹钟意图类型枚举
     */
    enum class AlarmIntentType {
        NONE,    // 无闹钟意图
        SET,     // 设置闹钟
        CANCEL   // 取消闹钟
    }

    /**
     * 闹钟意图结果类
     */
    data class AlarmIntent(
        val type: AlarmIntentType,
        val parameters: AlarmParameters? = null,
        val timeDescription: String? = null  // 用于匹配要取消的闹钟
    )

    /**
     * 分析用户输入文本，提取闹钟意图
     * @return AlarmIntent 包含意图类型和相关参数
     */
    fun analyzeText(inputText: String): AlarmIntent {
        // 转换为小写以进行更好的匹配
        val text = inputText.lowercase(Locale.getDefault())

        // 检查是否包含取消闹钟的意图
        if (containsCancelAlarmIntent(text)) {
            val timeDescription = extractTimeDescriptionForCancel(text)
            return AlarmIntent(AlarmIntentType.CANCEL, null, timeDescription)
        }

        // 检查是否包含设置闹钟的意图
        if (!containsAlarmIntent(text)) {
            return AlarmIntent(AlarmIntentType.NONE)
        }

        Log.d(TAG, "检测到闹钟意图: $text")

        // 提取时间
        val timeInfo = extractTimeInfo(text)
        if (timeInfo.isEmpty()) {
            Log.d(TAG, "未找到有效时间信息")
            return AlarmIntent(AlarmIntentType.NONE)
        }

        // 提取事件/标题
        val title = extractTitle(text, timeInfo)

        // 创建闹钟参数
        val parameters = createAlarmParameters(timeInfo, title ?: "闹钟提醒")
        return AlarmIntent(AlarmIntentType.SET, parameters)
    }

    /**
     * 检查文本是否包含取消闹钟的意图
     */
    private fun containsCancelAlarmIntent(text: String): Boolean {
        val cancelKeywords = listOf(
            "取消闹钟", "删除闹钟", "不要叫我", "不要喊我", "不用提醒",
            "不用叫我", "关闭闹钟", "停止闹钟", "取消提醒", "删除所有闹钟",
            "关闭所有闹钟", "取消全部闹钟", "删掉闹钟", "不用再提醒"
        )

        for (keyword in cancelKeywords) {
            if (text.contains(keyword)) {
                Log.d(TAG, "检测到取消闹钟意图: $text 包含关键词 '$keyword'")
                return true
            }
        }

        // 复杂模式匹配
        val cancelPatterns = listOf(
            ".*不要.*(叫|喊|提醒).*",
            ".*(取消|关闭|停止|删除).*(闹钟|提醒).*",
            ".*明天.*(不用|不需要).*(叫|喊|提醒).*"
        )

        // 检查正则匹配
        for (pattern in cancelPatterns) {
            if (Pattern.compile(pattern).matcher(text).matches()) {
                Log.d(TAG, "检测到取消闹钟意图: $text 匹配模式 '$pattern'")
                return true
            }
        }

        return false
    }

    /**
     * 检查文本是否包含设置闹钟的意图
     */
    private fun containsAlarmIntent(text: String): Boolean {
        val alarmIntentKeywords = listOf(
            "设置闹钟", "设个闹钟", "定闹钟", "定个闹钟", "闹钟", "设置提醒", "提醒我", "定时提醒",
            "叫我", "到时提醒", "到时候提醒", "到点提醒", "设定闹钟", "订好闹钟","喊我",
            "设置个闹钟", "提醒一下", "到时叫我", "到时候叫我", "到点叫我", "叫醒我", "醒我"
        )

        for (keyword in alarmIntentKeywords) {
            if (text.contains(keyword)) {
                return true
            }
        }

        // 使用正则表达式检查更复杂的意图模式
        val complexIntentPatterns = listOf(
            ".*([今明后]天|星期[一二两三四五六日天]|周[一二三四五六日天]|[0-9]+[点时:：][0-9]*|[0-9零一二两三四五六七八九十]+分钟?后|[0-9零一二两三四五六七八九十]+小时后).*(提醒|叫|醒).*",
            ".*([0-9]+[点时:：][0-9]*).*(提醒|叫|醒|闹钟).*",
            ".*(提醒|叫|醒).*(在|于|到).*(([今明后]天|星期[一二三四五六日天]|周[一二三四五六日天])|([0-9]+[点时:：][0-9]*))"
        )

        for (pattern in complexIntentPatterns) {
            if (Pattern.compile(pattern).matcher(text).matches()) {
                return true
            }
        }

        return false
    }

    /**
     * 提取时间信息
     */
    private fun extractTimeInfo(text: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val calendar = Calendar.getInstance()

        // 记录是否是相对时间
        var isRelative = false

        // 检查相对时间模式（x分钟后，x小时后）
        val relativeMinutesPattern = Pattern.compile("([0-9零一二两三四五六七八九十]+)\\s*分钟后")
        val relativeHoursPattern = Pattern.compile("([0-9零一二两三四五六七八九十]+)\\s*小时后")

        val minutesMatcher = relativeMinutesPattern.matcher(text)
        val hoursMatcher = relativeHoursPattern.matcher(text)

        // 处理相对时间
        if (minutesMatcher.find()) {
            // 获取分钟数并转换
            val minutesStr = minutesMatcher.group(1) ?: "0"
            val minutes = if (minutesStr.matches(Regex("[0-9]+"))) {
                minutesStr.toIntOrNull() ?: 0
            } else {
                chineseNumberToInt(minutesStr)
            }

            calendar.add(Calendar.MINUTE, minutes)
            isRelative = true

            // 记录相对时间信息
            result["relative_minutes"] = minutes
            result["relative_time"] = true

            // 记录相对时间的日志
            Log.d(TAG, "检测到相对时间: $minutes 分钟后, 时间将被设为 ${
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
            }")

        } else if (hoursMatcher.find()) {
            // 获取小时数并转换（如果是中文数字）
            val hoursStr = hoursMatcher.group(1) ?: "0"
            val hours = if (hoursStr.matches(Regex("[0-9]+"))) {
                hoursStr.toIntOrNull() ?: 0
            } else {
                chineseNumberToInt(hoursStr)
            }

            calendar.add(Calendar.HOUR_OF_DAY, hours)
            isRelative = true

            // 记录相对时间信息
            result["relative_hours"] = hours
            result["relative_time"] = true

            // 记录相对时间的日志
            Log.d(TAG, "检测到相对时间: $hours 小时后, 时间将被设为 ${
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
            }")

        } else {
            // 处理绝对时间
            var dayOffset = 0  // 0=今天, 1=明天, 2=后天
            var hour = calendar.get(Calendar.HOUR_OF_DAY)
            var minute = calendar.get(Calendar.MINUTE)

            // 提取日期信息（今天、明天、后天、周几）
            if (text.contains("明天")) {
                dayOffset = 1
            } else if (text.contains("后天")) {
                dayOffset = 2
            }

            // 提取周几信息
            val weekdayMap = mapOf(
                "周一" to Calendar.MONDAY, "周二" to Calendar.TUESDAY, "周三" to Calendar.WEDNESDAY,
                "周四" to Calendar.THURSDAY, "周五" to Calendar.FRIDAY, "周六" to Calendar.SATURDAY,
                "周日" to Calendar.SUNDAY, "周天" to Calendar.SUNDAY,
                "星期一" to Calendar.MONDAY, "星期二" to Calendar.TUESDAY, "星期三" to Calendar.WEDNESDAY,
                "星期四" to Calendar.THURSDAY, "星期五" to Calendar.FRIDAY, "星期六" to Calendar.SATURDAY,
                "星期日" to Calendar.SUNDAY, "星期天" to Calendar.SUNDAY
            )

            for ((key, value) in weekdayMap) {
                if (text.contains(key)) {
                    val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
                    var daysToAdd = (value - currentDay)
                    if (daysToAdd <= 0) daysToAdd += 7  // 如果是当天或已过的天数，则设置为下周
                    calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                    dayOffset = -1  // 使用特殊值表示已经处理了周几
                    break
                }
            }

            // 如果是今明后天，添加日期偏移
            if (dayOffset >= 0) {
                calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
            }

            // 提取具体时间
            var timeFound = false

            // 匹配 "x点" 或 "x点y分" 或 "x:y" 或 "x：y" - 同时支持中文数字和阿拉伯数字
            val timePattern = Pattern.compile("([0-9零一二两三四五六七八九十]+)[点时:：]([0-9零一二两三四五六七八九十]+)?")
            val timeMatcher = timePattern.matcher(text)

            if (timeMatcher.find()) {
                // 获取小时数并转换
                val hourStr = timeMatcher.group(1) ?: ""
                hour = if (hourStr.matches(Regex("[0-9]+"))) {
                    hourStr.toIntOrNull() ?: hour
                } else {
                    chineseNumberToInt(hourStr)
                }

                // 检查是否指定了分钟
                val minuteGroup = timeMatcher.group(2)
                minute = if (minuteGroup != null && minuteGroup.isNotEmpty()) {
                    if (minuteGroup.matches(Regex("[0-9]+"))) {
                        minuteGroup.toIntOrNull() ?: 0
                    } else {
                        chineseNumberToInt(minuteGroup)
                    }
                } else {
                    0  // 如果只说了"x点"，则分钟设为0
                }

                timeFound = true

                // 记录原始解析的时间
                Log.d(TAG, "原始解析时间: ${hour}:${minute}")
            }

            // 根据时间段描述词调整小时数
            if (timeFound) {
                // 处理"下午"、"晚上"等时间段修饰词
                if (hour in 1..12 && !text.contains("早上") && !text.contains("上午") &&
                    (text.contains("下午") || text.contains("晚上") || text.contains("傍晚") ||
                            text.contains("午后") || text.contains("晚") || (hour < 8 && !text.contains("早") && !text.contains("凌晨")))) {

                    Log.d(TAG, "检测到下午/晚上时间: $hour 点，调整为 ${hour + 12} 点")
                    hour += 12
                    if (hour >= 24) hour = 12  // 特殊情况：中午12点
                }
            } else {
                // 如果没有找到具体时间，根据时间段描述词设置默认时间
                if (text.contains("早上") || text.contains("上午")) {
                    hour = 8
                    minute = 0
                } else if (text.contains("中午")) {
                    hour = 12
                    minute = 0
                } else if (text.contains("下午")) {
                    hour = 15
                    minute = 0
                } else if (text.contains("晚上") || text.contains("夜里") || text.contains("夜晚")) {
                    hour = 20
                    minute = 0
                }
            }

            // 设置绝对时间
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)

            // 输出最终设置的时间
            Log.d(TAG, "最终设置时间为: ${
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.time)
            }")

            // 如果设置的绝对时间已经过去，则调整到明天
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                Log.d(TAG, "设置的时间已过去，调整到明天: ${
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.time)
                }")
            }
        }

        // 将最终时间戳和日期格式保存到结果中
        result["calendar"] = calendar
        result["timestamp"] = calendar.timeInMillis

        // 计算闹钟将在多少时间后触发
        val secondsToAlarm = (calendar.timeInMillis - System.currentTimeMillis()) / 1000
        result["seconds_to_alarm"] = secondsToAlarm

        // 格式化时间
        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.time)
        result["formatted_time"] = formattedTime

        // 添加日志，显示闹钟的详细信息
        Log.d(TAG, "闹钟将在${secondsToAlarm}秒后触发，参数：" +
                "时=${calendar.get(Calendar.HOUR_OF_DAY)}, " +
                "分=${calendar.get(Calendar.MINUTE)}")

        return result
    }

    /**
     * 将中文数字转换为阿拉伯数字
     */
    private fun chineseNumberToInt(chineseNumber: String): Int {
        val numMap = mapOf(
            "零" to 0, "一" to 1, "二" to 2, "两" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10
        )

        // 单个中文数字
        if (chineseNumber.length == 1 && numMap.containsKey(chineseNumber)) {
            return numMap[chineseNumber] ?: 0
        }

        // 处理"十"开头的数字，如"十五"表示15
        if (chineseNumber.startsWith("十")) {
            if (chineseNumber.length == 1) {
                return 10
            }
            val restNum = chineseNumberToInt(chineseNumber.substring(1))
            return 10 + restNum
        }

        // 处理"数字+十+数字"的形式，如"一十五"表示15
        if (chineseNumber.contains("十")) {
            val parts = chineseNumber.split("十", limit = 2)
            val tens = chineseNumberToInt(parts[0]) * 10
            val ones = if (parts.size > 1 && parts[1].isNotEmpty()) {
                chineseNumberToInt(parts[1])
            } else {
                0
            }
            return tens + ones
        }

        // 处理单个数字
        return numMap[chineseNumber] ?: 0
    }

    /**
     * 提取闹钟标题/事件
     */
    private fun extractTitle(text: String, timeInfo: Map<String, Any>): String? {
        // 定义可能表示闹钟事件的前后缀词
        val prefixes = listOf("提醒我", "记得", "别忘了", "提醒一下", "待会", "到时候", "记得提醒", "提示我", "喊我","到时")
        val suffixes = listOf("的事", "这件事", "这个事情", "的事情", "的任务")

        // 尝试找到事件描述
        for (prefix in prefixes) {
            if (text.contains(prefix)) {
                val parts = text.split(prefix, limit = 2)
                if (parts.size == 2 && parts[1].isNotEmpty()) {
                    // 进一步处理后半部分，移除无关词语
                    var eventText = parts[1].trim()

                    // 移除时间相关词汇
                    val timePatterns = listOf(
                        "早上", "上午", "中午", "下午", "晚上", "凌晨", "今天", "明天", "后天",
                        "这个星期", "下个星期", "这周", "下周", "一会儿", "一会",
                        "\\d+点", "\\d+:\\d+", "\\d+：\\d+", "\\d+分钟后", "\\d+小时后"
                    )

                    for (pattern in timePatterns) {
                        eventText = eventText.replace(Regex(pattern), "")
                    }

                    // 移除后缀
                    for (suffix in suffixes) {
                        eventText = eventText.replace(suffix, "")
                    }

                    // 清理空格和常见符号
                    eventText = eventText.trim().replace(Regex("[,，.。!！?？]$"), "")

                    if (eventText.isNotEmpty()) {
                        // 限制长度
                        return eventText.substring(0, min(eventText.length, 50))
                    }
                }
            }
        }

        // 如果没有找到明确的事件描述，使用时间作为默认描述
        val formattedTime = timeInfo["formatted_time"] as? String ?:
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        return "闹钟 - $formattedTime"
    }

    /**
     * 提取时间描述信息，用于匹配要取消的闹钟
     */
    fun extractTimeDescriptionForCancel(text: String): String {
        var timeDescription = ""

        // 检查是否是删除全部闹钟
        if (text.contains("所有") || text.contains("全部")) {
            return "ALL"  // 特殊标记，表示删除所有闹钟
        }

        // 检查时间相关词汇
        val timePatterns = mapOf(
            "早上|早晨|上午" to "上午",
            "中午" to "中午",
            "下午|午后" to "下午",
            "晚上|夜晚|夜里" to "晚上",
            "明天" to "明天",
            "后天" to "后天"
        )

        for ((pattern, description) in timePatterns) {
            if (text.contains(Regex(pattern))) {
                timeDescription += "$description "
            }
        }

        // 提取具体时间
        val timeRegex = "([0-9]+)[点时:：]([0-9]+)?".toRegex()
        val match = timeRegex.find(text)
        if (match != null) {
            val hour = match.groupValues[1]
            val minute = if (match.groupValues.size > 2 && match.groupValues[2].isNotEmpty())
                match.groupValues[2]
            else "00"

            // 如果包含"下午"/"晚上"等词，且时间是1-12点，则转换为24小时制
            val hourInt = hour.toIntOrNull() ?: 0
            val formattedHour = if (hourInt in 1..12 &&
                (timeDescription.contains("下午") || timeDescription.contains("晚上"))) {
                (hourInt + 12).toString()
            } else {
                hour
            }

            timeDescription += "$formattedHour:$minute"
        }

        return timeDescription.trim()
    }

    /**
     * 创建闹钟参数
     */
    private fun createAlarmParameters(timeInfo: Map<String, Any>, title: String): AlarmParameters {
        val calendar = timeInfo["calendar"] as Calendar
        val timestamp = timeInfo["timestamp"] as Long

        return AlarmParameters(
            triggerTimeMillis = timestamp,
            title = title,
            description = "",
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
            isOneTime = true,
            repeatDays = ""
        )
    }

    /**
     * 闹钟参数数据类
     */
    data class AlarmParameters(
        val triggerTimeMillis: Long,
        val title: String,
        val description: String = "",
        val hour: Int,
        val minute: Int,
        val isOneTime: Boolean = true,
        val repeatDays: String = ""
    )
}
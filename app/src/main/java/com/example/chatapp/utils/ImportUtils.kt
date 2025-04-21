package com.example.chatapp.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.chatapp.data.db.MessageEntity
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 聊天记录导入工具类
 */
class ImportUtils {
    companion object {
        private const val TAG = "ImportUtils"

        // 日期时间格式
        private val DATE_FORMATS = arrayOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy/MM/dd HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss"
        )

        /**
         * 从URI导入聊天记录
         * @param context 上下文
         * @param uri 文件URI
         * @param chatId 目标聊天ID
         * @return 导入的消息列表
         */
        fun importChatHistory(context: Context, uri: Uri, chatId: String): List<MessageEntity> {
            try {
                // 获取文件名和扩展名
                val fileName = getFileName(context, uri)
                val extension = fileName.substringAfterLast('.', "").lowercase()

                // 根据扩展名选择不同的导入方式
                return when (extension) {
                    "json" -> importFromJson(context, uri, chatId)
                    "txt" -> importFromTxt(context, uri, chatId)
                    else -> throw IOException("不支持的文件格式: $extension")
                }
            } catch (e: Exception) {
                Log.e(TAG, "导入聊天记录失败: ${e.message}", e)
                throw e
            }
        }

        /**
         * 从JSON文件导入
         */
        private fun importFromJson(context: Context, uri: Uri, chatId: String): List<MessageEntity> {
            val jsonString = readTextFromUri(context, uri)
            val messages = mutableListOf<MessageEntity>()

            try {
                // 尝试标准JSON格式
                val jsonElement = JsonParser.parseString(jsonString)

                when {
                    // 如果是数组格式
                    jsonElement.isJsonArray -> {
                        val jsonArray = jsonElement.asJsonArray
                        for (i in 0 until jsonArray.size()) {
                            val messageObject = jsonArray.get(i).asJsonObject
                            parseJsonMessage(messageObject, chatId)?.let { messages.add(it) }
                        }
                    }
                    // 如果是单个对象，可能包含messages数组
                    jsonElement.isJsonObject -> {
                        val jsonObject = jsonElement.asJsonObject
                        if (jsonObject.has("messages") && jsonObject.get("messages").isJsonArray) {
                            val messagesArray = jsonObject.getAsJsonArray("messages")
                            for (i in 0 until messagesArray.size()) {
                                val messageObject = messagesArray.get(i).asJsonObject
                                parseJsonMessage(messageObject, chatId)?.let { messages.add(it) }
                            }
                        } else {
                            // 尝试解析单个消息
                            parseJsonMessage(jsonObject, chatId)?.let { messages.add(it) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析JSON失败: ${e.message}", e)
                throw e
            }

            // 确保消息按时间戳排序
            return messages.sortedBy { it.timestamp }
        }

        /**
         * 解析JSON消息对象
         */
        private fun parseJsonMessage(jsonObject: JsonObject, chatId: String): MessageEntity? {
            try {
                // 尝试提取各个字段，支持多种可能的字段名
                val id = jsonObject.getStringOrNull("id") ?: UUID.randomUUID().toString()

                // 解析消息内容
                val content = jsonObject.getStringOrNull("content")
                    ?: jsonObject.getStringOrNull("text")
                    ?: jsonObject.getStringOrNull("message")
                    ?: ""

                // 解析消息类型
                val typeStr = jsonObject.getStringOrNull("type")
                    ?: jsonObject.getStringOrNull("role")
                    ?: ""
                val type = when (typeStr.lowercase()) {
                    "user", "0" -> 0
                    "ai", "assistant", "bot", "1" -> 1
                    else -> if (jsonObject.has("isUser") && !jsonObject.get("isUser").asBoolean) 1 else 0
                }

                // 解析时间戳
                val timestamp = parseTimestamp(jsonObject) ?: Date()

                // 解析图片数据
                val imageData = jsonObject.getStringOrNull("imageData")

                // 解析内容类型
                val contentTypeValue = jsonObject.getIntOrNull("contentType") ?: 0

                return MessageEntity(
                    id = id,
                    chatId = chatId,
                    content = content,
                    type = type,
                    timestamp = timestamp,
                    isError = false,
                    imageData = imageData,
                    contentType = contentTypeValue
                )
            } catch (e: Exception) {
                Log.e(TAG, "解析消息对象失败: ${e.message}", e)
                return null
            }
        }

        /**
         * 从文本文件导入
         */
        private fun importFromTxt(context: Context, uri: Uri, chatId: String): List<MessageEntity> {
            val text = readTextFromUri(context, uri)
            val messages = mutableListOf<MessageEntity>()

            // 按行分割
            val lines = text.lines()

            // 解析规则：假设每行都是一条消息，通过前缀区分用户和AI
            var currentType = 0 // 默认为用户消息
            var currentContent = StringBuilder()
            var lastTimestamp: Date? = null

            // 添加调试日志
            Log.d(TAG, "开始解析TXT文件，共 ${lines.size} 行")

            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) continue

                // 检查行是否表示新消息
                when {
                    // 带方括号格式：[YYYY-MM-DD HH:MM:SS] 用户: 内容
                    trimmedLine.matches(Regex("\\[\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\]\\s+用户:.*")) ||
                            trimmedLine.matches(Regex("\\[\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\]\\s+User:.*")) -> {
                        // 如果已经有内容，保存之前的消息
                        if (currentContent.isNotEmpty()) {
                            saveMessage(messages, currentContent.toString(), currentType, lastTimestamp, chatId)
                            currentContent.clear()
                        }
                        currentType = 0 // 用户消息
                        val parts = trimmedLine.split("用户:", "User:", limit = 2)
                        if (parts.size > 1) {
                            currentContent.append(parts[1].trim())
                        } else {
                            currentContent.append(trimmedLine.substringAfter(']').substringAfter(':').trim())
                        }
                        lastTimestamp = extractTimestampWithBrackets(trimmedLine) ?: Date()

                        // 记录日志
                        Log.d(TAG, "识别到带方括号用户消息，时间戳: ${lastTimestamp}, 内容: ${currentContent.toString().take(20)}...")
                    }

                    // 带方括号格式：[YYYY-MM-DD HH:MM:SS] AI: 内容
                    trimmedLine.matches(Regex("\\[\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\]\\s+AI:.*")) ||
                            trimmedLine.matches(Regex("\\[\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\]\\s+Assistant:.*")) -> {
                        // 如果已经有内容，保存之前的消息
                        if (currentContent.isNotEmpty()) {
                            saveMessage(messages, currentContent.toString(), currentType, lastTimestamp, chatId)
                            currentContent.clear()
                        }
                        currentType = 1 // AI消息
                        val parts = trimmedLine.split("AI:", "Assistant:", limit = 2)
                        if (parts.size > 1) {
                            currentContent.append(parts[1].trim())
                        } else {
                            currentContent.append(trimmedLine.substringAfter(']').substringAfter(':').trim())
                        }
                        lastTimestamp = extractTimestampWithBrackets(trimmedLine) ?: Date()

                        // 记录日志
                        Log.d(TAG, "识别到带方括号AI消息，时间戳: ${lastTimestamp}, 内容: ${currentContent.toString().take(20)}...")
                    }

                    // 传统格式，没有方括号
                    trimmedLine.startsWith("用户:") || trimmedLine.startsWith("User:") -> {
                        // 如果已经有内容，保存之前的消息
                        if (currentContent.isNotEmpty()) {
                            saveMessage(messages, currentContent.toString(), currentType, lastTimestamp, chatId)
                            currentContent.clear()
                        }
                        currentType = 0 // 用户消息
                        currentContent.append(trimmedLine.substringAfter(':').trim())
                        lastTimestamp = extractTimestamp(trimmedLine) ?: Date()
                    }

                    trimmedLine.startsWith("AI:") || trimmedLine.startsWith("Assistant:") ||
                            trimmedLine.startsWith("ChatGPT:") -> {
                        // 如果已经有内容，保存之前的消息
                        if (currentContent.isNotEmpty()) {
                            saveMessage(messages, currentContent.toString(), currentType, lastTimestamp, chatId)
                            currentContent.clear()
                        }
                        currentType = 1 // AI消息
                        currentContent.append(trimmedLine.substringAfter(':').trim())
                        lastTimestamp = extractTimestamp(trimmedLine) ?: Date()
                    }

                    else -> {
                        // 继续当前消息内容
                        if (currentContent.isNotEmpty()) {
                            currentContent.append("\n")
                        }
                        currentContent.append(trimmedLine)
                    }
                }
            }

            // 保存最后一条消息
            if (currentContent.isNotEmpty()) {
                saveMessage(messages, currentContent.toString(), currentType, lastTimestamp, chatId)
            }

            // 记录导入结果
            Log.d(TAG, "TXT文件解析完成，共导入 ${messages.size} 条消息")

            // 确保消息按时间戳排序
            return messages.sortedBy { it.timestamp }
        }

        /**
         * 提取带方括号的时间戳
         * 例如：[2025-04-01 14:20:58] 用户: 内容
         */
        private fun extractTimestampWithBrackets(text: String): Date? {
            // 匹配方括号中的日期时间格式
            val bracketPattern = Regex("\\[(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\]")
            val match = bracketPattern.find(text)

            if (match != null) {
                val dateStr = match.groupValues[1] // 提取括号内的日期部分
                // 解析日期
                try {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    return dateFormat.parse(dateStr)
                } catch (e: ParseException) {
                    Log.w(TAG, "无法解析带方括号的时间戳: $dateStr", e)
                }
            }

            return null
        }

        /**
         * 保存消息到列表
         */
        private fun saveMessage(
            messages: MutableList<MessageEntity>,
            content: String,
            type: Int,
            timestamp: Date?,
            chatId: String
        ) {
            messages.add(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    content = content,
                    type = type,
                    timestamp = timestamp ?: Date(),
                    isError = false,
                    contentType = 0 // 默认为TEXT
                )
            )
        }

        /**
         * 从URI读取文本内容
         */
        private fun readTextFromUri(context: Context, uri: Uri): String {
            val stringBuilder = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stringBuilder.append(line).append('\n')
                    }
                }
            }
            return stringBuilder.toString()
        }

        /**
         * 获取文件名
         */
        private fun getFileName(context: Context, uri: Uri): String {
            var result = "unknown"
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            result = it.getString(nameIndex)
                        }
                    }
                }
            }
            if (result == "unknown") {
                result = uri.path?.substringAfterLast('/') ?: "unknown"
            }
            return result
        }

        /**
         * 解析JSON对象中的时间戳
         */
        private fun parseTimestamp(jsonObject: JsonObject): Date? {
            // 尝试各种可能的时间戳字段
            val possibleFields = listOf("timestamp", "time", "date", "createdAt", "created_at")

            for (field in possibleFields) {
                if (jsonObject.has(field)) {
                    val value = jsonObject.get(field)

                    // 如果是数字（Unix时间戳）
                    if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                        val timestamp = value.asLong
                        // 检查是否为毫秒级时间戳
                        return if (timestamp > 1000000000000L) {
                            Date(timestamp)
                        } else {
                            // 假设是秒级时间戳
                            Date(timestamp * 1000)
                        }
                    }

                    // 如果是字符串（日期时间格式）
                    if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                        val dateStr = value.asString
                        // 尝试多种日期格式
                        for (format in DATE_FORMATS) {
                            try {
                                val dateFormat = SimpleDateFormat(format, Locale.getDefault())
                                return dateFormat.parse(dateStr)
                            } catch (e: ParseException) {
                                // 继续尝试下一种格式
                            }
                        }
                    }
                }
            }

            // 如果没有找到有效的时间戳，返回null
            return null
        }

        /**
         * 从文本中提取时间戳
         */
        private fun extractTimestamp(text: String): Date? {
            // 尝试匹配常见的日期时间格式
            val datePatterns = arrayOf(
                Regex("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}"),
                Regex("\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}"),
                Regex("\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}")
            )

            for (pattern in datePatterns) {
                val matchResult = pattern.find(text)
                if (matchResult != null) {
                    val dateStr = matchResult.value
                    // 尝试解析日期
                    for (format in DATE_FORMATS) {
                        try {
                            val dateFormat = SimpleDateFormat(format, Locale.getDefault())
                            return dateFormat.parse(dateStr)
                        } catch (e: ParseException) {
                            // 继续尝试下一种格式
                        }
                    }
                }
            }

            return null
        }

        /**
         * 安全地从JsonObject获取字符串
         */
        private fun JsonObject.getStringOrNull(key: String): String? {
            return if (this.has(key) && !this.get(key).isJsonNull) {
                try {
                    this.get(key).asString
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        /**
         * 安全地从JsonObject获取整数
         */
        private fun JsonObject.getIntOrNull(key: String): Int? {
            return if (this.has(key) && !this.get(key).isJsonNull) {
                try {
                    this.get(key).asInt
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }
}

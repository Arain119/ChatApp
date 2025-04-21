package com.example.chatapp.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.chatapp.MainActivity
import com.example.chatapp.R
import com.example.chatapp.api.ApiClient
import com.example.chatapp.api.ChatGptRequest
import com.example.chatapp.api.ChatMessage
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.data.db.ChatDatabaseHelper
import com.example.chatapp.data.db.MessageEntity
import com.example.chatapp.profile.UserProfileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 主动消息服务
 * 负责检测用户活跃状态，基于用户画像提供个性化推荐，并在适当时机主动发送消息
 */
class ProactiveMessageService(private val context: Context) {
    private val TAG = "ProactiveMessageService"
    private val dbHelper = ChatDatabaseHelper.getInstance(context)
    private val userProfileSystem = UserProfileSystem(context)
    private val settingsManager = SettingsManager(context)

    /**
     * 分析用户活跃时间段
     * 返回小时数（0-23）表示用户最活跃的时间段
     */
    suspend fun analyzeUserActiveTimeSlots(chatId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                // 获取该聊天的所有消息
                val messages = dbHelper.getMessagesForChatList(chatId)

                // 如果消息不足，返回默认值（晚上8点）
                if (messages.size < 5) {
                    return@withContext 20
                }

                // 统计每个小时的消息数量
                val hourCountMap = mutableMapOf<Int, Int>()

                messages.forEach { message ->
                    // 只统计用户消息
                    if (message.type == 0) {
                        val calendar = Calendar.getInstance()
                        calendar.time = message.timestamp
                        val hour = calendar.get(Calendar.HOUR_OF_DAY)

                        // 增加该小时的计数
                        hourCountMap[hour] = (hourCountMap[hour] ?: 0) + 1
                    }
                }

                // 找出消息最多的小时
                val mostActiveHour = hourCountMap.entries
                    .sortedByDescending { it.value }
                    .firstOrNull()?.key ?: 20 // 默认晚上8点

                Log.d(TAG, "用户最活跃时间段: $mostActiveHour 点, 消息数: ${hourCountMap[mostActiveHour]}")
                mostActiveHour
            } catch (e: Exception) {
                Log.e(TAG, "分析用户活跃时间失败: ${e.message}", e)
                20 // 发生错误时默认为晚上8点
            }
        }
    }

    /**
     * 检查是否应该发送主动消息
     * 条件1: 距离上次对话已经过去设定的时间间隔
     * 条件2: 现在是用户一天中最活跃的时间段
     */
    suspend fun shouldSendProactiveMessage(chatId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 检查设置是否启用了主动消息
                if (!settingsManager.proactiveMessagesEnabled) {
                    return@withContext false
                }

                // 获取最后一条消息的时间
                val messages = dbHelper.getMessagesForChatList(chatId)
                if (messages.isEmpty()) {
                    return@withContext false
                }

                val lastMessage = messages.last()
                val lastMessageTime = lastMessage.timestamp
                val currentTime = Date()

                // 计算时间差（小时）
                val hoursDifference = TimeUnit.MILLISECONDS.toHours(
                    currentTime.time - lastMessageTime.time
                )

                // 获取设置中的时间间隔，默认12小时
                val timeInterval = settingsManager.proactiveMessagesInterval

                // 条件1: 检查是否已经过去指定时间
                val timeCondition = hoursDifference >= timeInterval

                // 条件2: 检查现在是否是用户最活跃的时间段
                val mostActiveHour = analyzeUserActiveTimeSlots(chatId)
                val calendar = Calendar.getInstance()
                val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

                // 当前小时与最活跃时段相差不超过1小时视为在活跃时间段
                val timeSlotCondition = Math.abs(currentHour - mostActiveHour) <= 1

                val shouldSend = timeCondition && timeSlotCondition

                Log.d(TAG, "主动消息检查: 时间条件=$timeCondition, 时间段条件=$timeSlotCondition, 结果=$shouldSend")

                shouldSend
            } catch (e: Exception) {
                Log.e(TAG, "检查是否应发送主动消息失败: ${e.message}", e)
                false
            }
        }
    }

    /**
     * 提取用户标签为JSON格式
     */
    private fun extractUserTagsAsJson(userProfile: UserProfileSystem.UserProfile?): JSONObject {
        val tagsJson = JSONObject()

        if (userProfile == null) {
            return tagsJson
        }

        try {
            // 按类别组织标签
            val tagsByCategory = userProfile.tags.groupBy { it.category }

            // 处理各类别标签
            tagsByCategory.forEach { (category, tags) ->
                // 过滤出高置信度的标签（大于0.5）
                val highConfidenceTags = tags.filter { it.confidence > 0.5 }
                    .sortedByDescending { it.confidence }
                    .take(5) // 每类最多5个

                if (highConfidenceTags.isNotEmpty()) {
                    val categoryTagsArray = JSONArray()

                    highConfidenceTags.forEach { tag ->
                        val tagObj = JSONObject()
                        tagObj.put("name", tag.tagName)
                        tagObj.put("confidence", tag.confidence)
                        // 添加一个简单的证据示例（如果有的话）
                        if (tag.evidence.isNotEmpty()) {
                            val shortEvidence = if (tag.evidence.length > 50) {
                                tag.evidence.substring(0, 50) + "..."
                            } else {
                                tag.evidence
                            }
                            tagObj.put("evidence", shortEvidence)
                        }
                        categoryTagsArray.put(tagObj)
                    }

                    tagsJson.put(category, categoryTagsArray)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "提取用户标签为JSON格式失败: ${e.message}", e)
        }

        return tagsJson
    }

    /**
     * 从最近消息中提取用户可能感兴趣的话题
     */
    private fun extractUserTopics(messages: List<MessageEntity>): List<String> {
        val topics = mutableSetOf<String>()
        val userMessages = messages.filter { it.type == 0 }.map { it.content }

        // 一些简单的规则来提取话题
        // 实际项目中可以使用更复杂的NLP方法
        val topicPatterns = listOf(
            "喜欢.+?(?=[，。？！]|$)".toRegex(),
            "想.+?(?=[，。？！]|$)".toRegex(),
            "关于.+?(?=[，。？！]|$)".toRegex(),
            "最近.+?(?=[，。？！]|$)".toRegex()
        )

        userMessages.forEach { message ->
            topicPatterns.forEach { pattern ->
                pattern.findAll(message).forEach { matchResult ->
                    topics.add(matchResult.value)
                }
            }
        }

        return topics.toList().take(5)
    }

    /**
     * 提取用户最新对话关键点
     */
    private fun extractRecentConversationHighlights(messages: List<MessageEntity>): String {
        // 如果消息太少，直接返回空
        if (messages.size < 3) {
            return ""
        }

        // 获取最近5轮对话（最多10条消息）
        val recentMessages = messages.takeLast(10)
        val sb = StringBuilder()

        // 对话中的关键点
        val userMessages = recentMessages.filter { it.type == 0 }.takeLast(3)
        val aiMessages = recentMessages.filter { it.type == 1 }.takeLast(2)

        if (userMessages.isNotEmpty()) {
            sb.append("最近用户提到: \n")
            userMessages.forEach { message ->
                // 截取长消息
                val content = if (message.content.length > 100) {
                    message.content.substring(0, 100) + "..."
                } else {
                    message.content
                }
                sb.append("- $content\n")
            }
        }

        if (aiMessages.isNotEmpty()) {
            sb.append("\n最近AI回复重点: \n")
            aiMessages.forEach { message ->
                // 提取AI回复中可能的关键点（通常在开头或结尾）
                val content = if (message.content.length > 80) {
                    val firstPart = message.content.substring(0, 40)
                    val lastPart = message.content.substring(message.content.length - 40)
                    "$firstPart... $lastPart"
                } else {
                    message.content
                }
                sb.append("- $content\n")
            }
        }

        return sb.toString()
    }

    /**
     * 生成主动消息
     */
    suspend fun generateProactiveMessage(chatId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // 获取聊天会话信息，包括AI人设
                val chatInfo = dbHelper.getChatById(chatId)
                val aiPersona = chatInfo?.aiPersona ?: ""

                // 获取用户画像
                val userProfile = userProfileSystem.loadUserProfile(chatId)

                // 提取用户标签为JSON格式
                val userTagsJson = extractUserTagsAsJson(userProfile)

                // 获取最近的一些消息作为上下文
                val recentMessages = dbHelper.getMessagesForChatList(chatId).takeLast(10)

                // 提取用户最近提到的话题
                val userTopics = extractUserTopics(recentMessages)

                // 提取最近对话重点
                val conversationHighlights = extractRecentConversationHighlights(recentMessages)

                // 构建提示词
                val prompt = buildProactiveMessagePrompt(
                    aiPersona,
                    userProfile?.summary ?: "",
                    userTagsJson.toString(),
                    userTopics,
                    conversationHighlights
                )

                // 调用API生成消息
                val messages = listOf(
                    ChatMessage("system", prompt),
                    ChatMessage("user", "请生成一条主动消息，包含基于用户画像的个性化推荐，引发新的对话")
                )

                val request = ChatGptRequest(
                    model = settingsManager.modelType, // 使用用户选择的模型
                    messages = messages,
                    temperature = 0.8
                )

                val response = ApiClient.apiService.sendMessage(
                    ApiClient.getAuthHeader(),
                    request
                )

                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!.choices[0].message.content.toString()
                    Log.d(TAG, "生成主动消息成功: ${result.take(50)}...")
                    result
                } else {
                    Log.e(TAG, "生成主动消息API请求失败: ${response.code()}")
                    getDefaultProactiveMessage(userProfile, aiPersona)
                }
            } catch (e: Exception) {
                Log.e(TAG, "生成主动消息失败: ${e.message}", e)
                getDefaultProactiveMessage(null, "")
            }
        }
    }

    /**
     * 构建主动消息提示词
     */
    private fun buildProactiveMessagePrompt(
        aiPersona: String,
        userProfileSummary: String,
        userTagsJson: String,
        userTopics: List<String>,
        conversationHighlights: String
    ): String {
        // 当前日期时间
        val calendar = Calendar.getInstance()
        val dateStr = "${calendar.get(Calendar.YEAR)}年${calendar.get(Calendar.MONTH) + 1}月${calendar.get(Calendar.DAY_OF_MONTH)}日"
        val timeStr = "${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)}"

        // 季节信息
        val seasonInfo = when (calendar.get(Calendar.MONTH)) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "冬季"
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "春季"
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "夏季"
            else -> "秋季"
        }

        // 工作日/周末信息
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        val dayTypeInfo = if (isWeekend) "周末" else "工作日"

        // 构建带AI人设的提示词
        val basePrompt = """
            你是一个主动联系用户的AI助手，目前是 $dateStr $timeStr（$dayTypeInfo）。
            你需要生成一条自然、有吸引力的消息，主动发起新的对话，并根据用户画像提供个性化推荐。
        """.trimIndent()

        // 添加AI人设信息（如果有）
        val personaPrompt = if (aiPersona.isNotEmpty()) {
            """
            ## AI人设:
            $aiPersona
            
            请在保持以上人设的同时，生成一条符合这个身份和风格的主动消息。
            """.trimIndent()
        } else {
            ""
        }

        // 用户相关信息
        val userInfoPrompt = """
            ## 用户画像摘要:
            $userProfileSummary
            
            ## 用户标签详情:
            $userTagsJson
            
            ## 用户最近提到的话题:
            ${userTopics.joinToString(", ")}
            
            ## 最近对话重点:
            $conversationHighlights
        """.trimIndent()

        // 指南部分
        val guidelinePrompt = """
            ## 指南:
            1. 基于用户的兴趣标签和背景知识推荐个性化的话题或内容
            2. 如果用户有特定的知识背景或沟通风格，适当调整你的表达方式
            3. 结合当前的时间（$dayTypeInfo）等因素
            4. 保持简短自然，不超过2-3句话
            5. 不要提及这是一个"推荐"或是被程序触发的主动消息
            
            请直接输出一条适合的主动消息，不需要解释或说明。确保消息自然、个性化，让用户感到你真的关心他们。
        """.trimIndent()

        // 组合完整提示词
        val combinedPrompt = if (personaPrompt.isNotEmpty()) {
            "$basePrompt\n\n$personaPrompt\n\n$userInfoPrompt\n\n$guidelinePrompt"
        } else {
            "$basePrompt\n\n$userInfoPrompt\n\n$guidelinePrompt"
        }

        return combinedPrompt
    }

    /**
     * 默认的主动消息
     * 在没有用户画像时使用，或者API请求失败时使用
     */
    private fun getDefaultProactiveMessage(userProfile: UserProfileSystem.UserProfile?, aiPersona: String): String {
        // 检查是否有AI人设
        val hasPersona = aiPersona.isNotEmpty()

        // 如果有用户画像但API失败，尝试基于标签构建简单消息
        if (userProfile != null) {
            // 提取高置信度兴趣标签
            val interestTags = userProfile.getTagsByCategory(UserProfileSystem.TAG_CATEGORY_INTEREST)
                .filter { it.confidence > 0.7 }
                .sortedByDescending { it.confidence }
                .take(2)

            if (interestTags.isNotEmpty()) {
                // 基于最高置信度的兴趣构建消息
                val topInterest = interestTags.first().tagName

                // 根据是否有人设调整消息风格
                val message = when (topInterest) {
                    "技术" -> if (hasPersona) "关于技术领域，最近有研究什么新项目吗？" else "嘿，最近有没有研究什么新的技术或编程项目？我记得你对这方面挺感兴趣的。"
                    "阅读" -> if (hasPersona) "有什么好书最近吸引了你的注意力吗？很想听听你的阅读体验。" else "刚想到你喜欢阅读，最近有没有读到什么有趣的书或文章？很想听听你的分享。"
                    "电影" -> if (hasPersona) "说起来，你最近看了什么电影吗？有没有值得推荐的？" else "不知道你最近有没有看什么好电影？作为电影爱好者，你的推荐总是很有见地。"
                    "音乐" -> if (hasPersona) "最近有什么新发现的好歌吗？我很好奇你的音乐品味。" else "突然想问问，最近有没有发现什么好听的新歌？很好奇你现在的音乐品味。"
                    "游戏" -> if (hasPersona) "关于游戏，最近有什么新的体验吗？" else "嘿，最近有玩什么有趣的游戏吗？一直记得你是个游戏爱好者。"
                    "体育" -> if (hasPersona) "健身计划进行得如何？有什么新的运动方式吗？" else "你的健身计划进行得怎么样？最近有尝试什么新的运动方式吗？"
                    "旅行" -> if (hasPersona) "有没有什么地方你一直想去但还没有机会去的？" else "不知道你有没有计划什么新的旅行？或者有什么想去但还没去过的地方？"
                    "美食" -> if (hasPersona) "最近尝试了什么美食吗？有推荐的好店或食谱吗？" else "突然想到你对美食的热爱，最近有没有尝试什么好吃的或者新的食谱？"
                    else -> if (hasPersona) "关于${topInterest}，最近有什么新的发现吗？" else "嘿，关于${topInterest}，最近有什么新的体验或想法吗？我记得你对这个挺感兴趣的。"
                }

                return message
            }
        }

        // 没有用户画像或无法提取兴趣时的默认消息
        val standardMessages = listOf(
            "最近有什么新发现吗？我们有一阵子没聊了。",
            "看到一个有趣的话题想和你聊聊。",
            "今天天气不错，你有什么计划吗？",
            "我刚刚想到一个问题，不知道你是怎么看的...",
            "好久不见！最近工作还顺利吗？",
            "我这几天学到了一些新东西，很想分享给你。",
            "突然想起来问问你的近况，一切都好吗？"
        )

        // 如果有人设，使用略微更正式的消息
        val personaMessages = listOf(
            "有段时间没联系了，近况如何？",
            "最近有什么想法或体验值得分享的吗？",
            "想到一个可能会感兴趣的话题，方便聊聊吗？",
            "不知道最近工作和生活的平衡如何？",
            "有没有什么新的发现或学习让你印象深刻？",
            "突然想到你，不知道最近过得怎么样？"
        )

        return if (hasPersona) personaMessages.random() else standardMessages.random()
    }

    /**
     * 发送主动消息到指定聊天
     */
    suspend fun sendProactiveMessage(chatId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 检查是否应该发送主动消息
                if (!shouldSendProactiveMessage(chatId)) {
                    return@withContext false
                }

                // 生成消息内容
                val messageContent = generateProactiveMessage(chatId)

                // 创建消息实体
                val messageEntity = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    content = messageContent,
                    type = 1, // AI消息
                    timestamp = Date(),
                    isError = false,
                    contentType = 0 // 纯文本
                )

                // 保存到数据库
                dbHelper.insertMessage(messageEntity)

                // 更新会话的最后更新时间
                withContext(Dispatchers.IO) {
                    val chat = dbHelper.getChatById(chatId)
                    if (chat != null) {
                        dbHelper.updateChat(chat.copy(updatedAt = Date()))
                    }
                }

                // 触发通知
                sendNotification(chatId, messageContent)

                Log.d(TAG, "成功发送主动消息到聊天: $chatId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "发送主动消息失败: ${e.message}", e)
                false
            }
        }
    }

    /**
     * 发送通知
     */
    private fun sendNotification(chatId: String, messageContent: String) {
        try {
            // 获取聊天信息
            val chat = runCatching {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    dbHelper.getChatById(chatId)
                }
            }.getOrNull() ?: return

            // 创建通知
            val notificationManager = NotificationManagerCompat.from(context)

            // 创建通知渠道（Android 8.0+需要）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "proactive_messages",
                    "主动消息",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                channel.description = "AI助手发送的主动消息"
                notificationManager.createNotificationChannel(channel)
            }

            // 创建打开对话的Intent
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("CHAT_ID", chatId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 构建通知
            val builder = NotificationCompat.Builder(context, "proactive_messages")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(chat.title)
                .setContentText(messageContent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            // 发送通知
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(chatId.hashCode(), builder.build())
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送通知失败: ${e.message}", e)
        }
    }
}
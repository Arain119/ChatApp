package com.example.chatapp.profile

import android.content.Context
import android.util.Log
import com.example.chatapp.api.ChatMessage
import com.example.chatapp.api.MemoryApiClient
import com.example.chatapp.data.db.ChatDatabaseHelper
import com.example.chatapp.data.db.UserProfileEntity
import com.example.chatapp.data.db.UserTagEntity
import com.hankcs.hanlp.HanLP
import com.hankcs.hanlp.seg.common.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 标签化用户画像系统
 * 通过标签和自学习机制，让AI更加个性化，越来越懂用户
 */
class UserProfileSystem(private val context: Context) {

    private val TAG = "UserProfileSystem"
    private val dbHelper = ChatDatabaseHelper.getInstance(context)
    private val memoryApiClient = MemoryApiClient(context)
    private val tagAnalyzer = TagAnalyzer(context)

    // 缓存最后一次分析的消息ID，用于增量分析
    private val lastAnalyzedMessageIds = mutableMapOf<String, String>()

    // 情感分析缓存，记录用户情感变化趋势
    private val emotionTrends = mutableMapOf<String, List<Pair<String, Float>>>()

    // 标签分类常量
    companion object {
        // 标签类别
        const val TAG_CATEGORY_INTEREST = "interest"      // 兴趣爱好
        const val TAG_CATEGORY_KNOWLEDGE = "knowledge"    // 知识背景
        const val TAG_CATEGORY_PERSONALITY = "personality" // 性格特点
        const val TAG_CATEGORY_EMOTION = "emotion"        // 情感倾向
        const val TAG_CATEGORY_COMMUNICATION = "communication" // 沟通风格
        const val TAG_CATEGORY_PREFERENCE = "preference"  // 偏好
        const val TAG_CATEGORY_GOAL = "goal"              // 目标
        const val TAG_CATEGORY_VALUE = "value"            // 价值观
        const val TAG_CATEGORY_SUBCULTURE = "subculture"  // 亚文化
        const val TAG_CATEGORY_TOPIC = "topic"            // 话题兴趣

        // 新增MBTI类别
        const val TAG_CATEGORY_MBTI = "mbti"              // MBTI人格
        const val TAG_CATEGORY_MBTI_EI = "mbti_ei"        // 外向/内向
        const val TAG_CATEGORY_MBTI_SN = "mbti_sn"        // 感觉/直觉
        const val TAG_CATEGORY_MBTI_TF = "mbti_tf"        // 思考/感受
        const val TAG_CATEGORY_MBTI_JP = "mbti_jp"        // 判断/感知

        // 用户画像更新频率阈值
        const val UPDATE_THRESHOLD_NORMAL = 10   // 一般情况下每10轮对话更新一次
        const val UPDATE_THRESHOLD_HIGH = 5      // 新用户或有重大变化时每5轮更新一次

        // 最小置信度阈值
        const val MIN_CONFIDENCE_THRESHOLD = 0.35f  // 低于此值的标签将被忽略
    }

    /**
     * 用户标签数据类
     * @param tagName 标签名称
     * @param category 标签类别
     * @param confidence 置信度(0.0-1.0)
     * @param evidence 支持此标签的证据(对话片段)
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    data class UserTag(
        val tagName: String,
        val category: String,
        var confidence: Float,
        var evidence: String = "",
        val createdAt: Date = Date(),
        var updatedAt: Date = Date()
    ) {
        /**
         * 合并两个标签，取较高的置信度和更新的时间
         */
        fun merge(other: UserTag): UserTag {
            return this.copy(
                confidence = max(this.confidence, other.confidence),
                evidence = if (this.evidence.length > other.evidence.length) this.evidence else other.evidence,
                updatedAt = if (this.updatedAt.after(other.updatedAt)) this.updatedAt else other.updatedAt
            )
        }

        /**
         * 调整置信度
         * @param adjustment 调整值(-1.0到1.0)
         * @return 调整后的新标签对象
         */
        fun adjustConfidence(adjustment: Float): UserTag {
            val newConfidence = min(1.0f, max(0.0f, confidence + adjustment))
            return this.copy(
                confidence = newConfidence,
                updatedAt = Date()
            )
        }

        /**
         * 转换为数据库实体
         */
        fun toEntity(chatId: String): UserTagEntity {
            return UserTagEntity(
                chatId = chatId,
                tagName = tagName,
                category = category,
                confidence = confidence,
                evidence = evidence,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }

        /**
         * 从数据库实体创建标签
         */
        companion object {
            fun fromEntity(entity: UserTagEntity): UserTag {
                return UserTag(
                    tagName = entity.tagName,
                    category = entity.category,
                    confidence = entity.confidence,
                    evidence = entity.evidence,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
            }
        }
    }

    /**
     * 用户画像数据类
     * @param tags 用户标签列表
     * @param summary 用户画像摘要(文本描述)
     * @param version 版本号
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     * @param interestTrends 兴趣变化趋势
     * @param emotionTrends 情感变化趋势
     */
    data class UserProfile(
        val tags: MutableList<UserTag> = mutableListOf(),
        var summary: String = "",
        var version: Int = 1,
        val createdAt: Date = Date(),
        var updatedAt: Date = Date(),
        var interestTrends: MutableMap<String, List<Pair<Date, Float>>> = mutableMapOf(),
        var emotionTrends: MutableList<Pair<Date, String>> = mutableListOf()
    ) {
        /**
         * 添加或更新标签
         * @param tag 要添加或更新的标签
         */
        fun addOrUpdateTag(tag: UserTag) {
            val existingIndex = tags.indexOfFirst { it.tagName == tag.tagName && it.category == tag.category }
            if (existingIndex >= 0) {
                // 如果标签已存在，则合并
                tags[existingIndex] = tags[existingIndex].merge(tag)
            } else {
                // 否则添加新标签
                tags.add(tag)
            }
            updatedAt = Date()
        }

        /**
         * 移除置信度低于阈值的标签
         * @param threshold 置信度阈值
         */
        fun pruneByConfidence(threshold: Float = MIN_CONFIDENCE_THRESHOLD) {
            tags.removeAll { it.confidence < threshold }
        }

        /**
         * 获取特定类别的标签
         * @param category 标签类别
         * @return 该类别的所有标签
         */
        fun getTagsByCategory(category: String): List<UserTag> {
            return tags.filter { it.category == category }
        }

        /**
         * 获取置信度最高的N个标签
         * @param n 返回标签数量
         * @return 置信度最高的N个标签
         */
        fun getTopTags(n: Int): List<UserTag> {
            return tags.sortedByDescending { it.confidence }.take(n)
        }

        /**
         * 记录情感标签变化
         * @param emotion 情感标签
         */
        fun recordEmotion(emotion: String) {
            emotionTrends.add(Pair(Date(), emotion))
            // 只保留最近30条记录
            if (emotionTrends.size > 30) {
                emotionTrends = emotionTrends.takeLast(30).toMutableList()
            }
        }

        /**
         * 记录兴趣标签变化
         * @param interest 兴趣名称
         * @param confidence 当前置信度
         */
        fun recordInterestTrend(interest: String, confidence: Float) {
            val trends = interestTrends.getOrPut(interest) { emptyList() }
            val newTrend = trends + Pair(Date(), confidence)
            // 只保留最近10个数据点
            interestTrends[interest] = newTrend.takeLast(10)
        }

        /**
         * 分析用户兴趣变化趋势
         * @return 兴趣变化趋势分析结果
         */
        fun analyzeInterestTrends(): Map<String, String> {
            val result = mutableMapOf<String, String>()

            for ((interest, trends) in interestTrends) {
                if (trends.size < 3) continue // 需要至少3个数据点进行趋势分析

                val firstValue = trends.first().second
                val lastValue = trends.last().second
                val delta = lastValue - firstValue

                val trend = when {
                    delta > 0.2f -> "上升"
                    delta < -0.2f -> "下降"
                    else -> "稳定"
                }

                result[interest] = trend
            }

            return result
        }

        /**
         * 分析用户最近情感状态
         * @return 主导情感及其频率
         */
        fun analyzeDominantEmotion(): Pair<String, Float>? {
            if (emotionTrends.isEmpty()) return null

            // 统计最近10条情感记录
            val recentEmotions = emotionTrends.takeLast(10)
            val emotionCounts = recentEmotions.groupBy { it.second }
                .mapValues { it.value.size.toFloat() / recentEmotions.size }

            // 找出出现频率最高的情感
            return emotionCounts.maxByOrNull { it.value }?.toPair()
        }

        /**
         * 将用户画像转换为AI可理解的系统提示
         * @return 格式化的系统提示
         */
        fun toSystemPrompt(): String {
            val sb = StringBuilder("用户画像：\n")

            // 添加摘要
            if (summary.isNotEmpty()) {
                sb.append(summary).append("\n\n")
            }

            // 按类别组织标签
            val tagsByCategory = tags.groupBy { it.category }

            // MBTI信息
            val mbtiTags = tagsByCategory[TAG_CATEGORY_MBTI]
            if (mbtiTags != null && mbtiTags.isNotEmpty()) {
                val topMbti = mbtiTags.maxByOrNull { it.confidence }
                if (topMbti != null && topMbti.confidence >= MIN_CONFIDENCE_THRESHOLD) {
                    sb.append("MBTI类型：").append(topMbti.tagName).append("\n")
                }
            }

            // MBTI四个维度
            val mbtiDimensions = StringBuilder()
            val eiTags = tagsByCategory[TAG_CATEGORY_MBTI_EI]?.maxByOrNull { it.confidence }
            if (eiTags != null && eiTags.confidence >= MIN_CONFIDENCE_THRESHOLD) {
                mbtiDimensions.append(eiTags.tagName).append("/")
            } else {
                mbtiDimensions.append("?/")
            }

            val snTags = tagsByCategory[TAG_CATEGORY_MBTI_SN]?.maxByOrNull { it.confidence }
            if (snTags != null && snTags.confidence >= MIN_CONFIDENCE_THRESHOLD) {
                mbtiDimensions.append(snTags.tagName).append("/")
            } else {
                mbtiDimensions.append("?/")
            }

            val tfTags = tagsByCategory[TAG_CATEGORY_MBTI_TF]?.maxByOrNull { it.confidence }
            if (tfTags != null && tfTags.confidence >= MIN_CONFIDENCE_THRESHOLD) {
                mbtiDimensions.append(tfTags.tagName).append("/")
            } else {
                mbtiDimensions.append("?/")
            }

            val jpTags = tagsByCategory[TAG_CATEGORY_MBTI_JP]?.maxByOrNull { it.confidence }
            if (jpTags != null && jpTags.confidence >= MIN_CONFIDENCE_THRESHOLD) {
                mbtiDimensions.append(jpTags.tagName)
            } else {
                mbtiDimensions.append("?")
            }

            if (mbtiDimensions.toString() != "?/?/?/?") {
                sb.append("MBTI倾向：").append(mbtiDimensions.toString()).append("\n")
            }

            // 情感状态
            val dominantEmotion = analyzeDominantEmotion()
            if (dominantEmotion != null) {
                sb.append("当前情感倾向：${dominantEmotion.first} (置信度: ${(dominantEmotion.second * 100).toInt()}%)\n")
            }

            // 兴趣爱好
            if (tagsByCategory.containsKey(TAG_CATEGORY_INTEREST)) {
                sb.append("兴趣爱好：")
                val interestTags = tagsByCategory[TAG_CATEGORY_INTEREST]!!
                    .sortedByDescending { it.confidence }
                    .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }

                // 兴趣趋势分析
                val trends = analyzeInterestTrends()

                sb.append(interestTags.joinToString(", ") {
                    val trend = trends[it.tagName]
                    if (trend != null) {
                        if (it.confidence > 0.7) "${it.tagName}(高${trend})" else "${it.tagName}(${trend})"
                    } else {
                        if (it.confidence > 0.7) "${it.tagName}(高)" else it.tagName
                    }
                })
                sb.append("\n")
            }

            // 知识背景
            if (tagsByCategory.containsKey(TAG_CATEGORY_KNOWLEDGE)) {
                sb.append("知识背景：")
                val knowledgeTags = tagsByCategory[TAG_CATEGORY_KNOWLEDGE]!!
                    .sortedByDescending { it.confidence }
                    .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }

                sb.append(knowledgeTags.joinToString(", ") {
                    if (it.confidence > 0.7) "${it.tagName}(专业)" else it.tagName
                })
                sb.append("\n")
            }

            // 性格特点
            if (tagsByCategory.containsKey(TAG_CATEGORY_PERSONALITY)) {
                sb.append("性格特点：")
                val personalityTags = tagsByCategory[TAG_CATEGORY_PERSONALITY]!!
                    .sortedByDescending { it.confidence }
                    .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }
                    .take(5)  // 最多5个性格特点

                sb.append(personalityTags.joinToString(", ") { it.tagName })
                sb.append("\n")
            }

            // 沟通风格
            if (tagsByCategory.containsKey(TAG_CATEGORY_COMMUNICATION)) {
                sb.append("沟通偏好：")
                val commTags = tagsByCategory[TAG_CATEGORY_COMMUNICATION]!!
                    .sortedByDescending { it.confidence }
                    .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }
                    .take(3)  // 最多3个沟通风格

                sb.append(commTags.joinToString(", ") { it.tagName })
                sb.append("\n")
            }

            // 亚文化
            if (tagsByCategory.containsKey(TAG_CATEGORY_SUBCULTURE)) {
                sb.append("文化偏好：")
                val subculTags = tagsByCategory[TAG_CATEGORY_SUBCULTURE]!!
                    .sortedByDescending { it.confidence }
                    .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }
                    .take(3)

                sb.append(subculTags.joinToString(", ") { it.tagName })
                sb.append("\n")
            }

            // 话题兴趣
            if (tagsByCategory.containsKey(TAG_CATEGORY_TOPIC)) {
                sb.append("关注话题：")
                val topicTags = tagsByCategory[TAG_CATEGORY_TOPIC]!!
                    .sortedByDescending { it.confidence }
                    .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }
                    .take(5)

                sb.append(topicTags.joinToString(", ") { it.tagName })
                sb.append("\n")
            }

            // 价值观
            if (tagsByCategory.containsKey(TAG_CATEGORY_VALUE)) {
                sb.append("价值观：")
                val valueTags = tagsByCategory[TAG_CATEGORY_VALUE]!!
                    .sortedByDescending { it.confidence }
                    .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }
                    .take(3)

                sb.append(valueTags.joinToString(", ") { it.tagName })
                sb.append("\n")
            }

            // 其他偏好
            if (tagsByCategory.containsKey(TAG_CATEGORY_PREFERENCE)) {
                sb.append("偏好：")
                val prefTags = tagsByCategory[TAG_CATEGORY_PREFERENCE]!!
                    .sortedByDescending { it.confidence }
                    .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }

                sb.append(prefTags.joinToString(", ") { it.tagName })
                sb.append("\n")
            }

            // 用户目标
            if (tagsByCategory.containsKey(TAG_CATEGORY_GOAL)) {
                sb.append("目标：")
                val goalTags = tagsByCategory[TAG_CATEGORY_GOAL]!!
                    .sortedByDescending { it.confidence }
                    .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }

                sb.append(goalTags.joinToString(", ") { it.tagName })
                sb.append("\n")
            }

            // 情感倾向
            if (tagsByCategory.containsKey(TAG_CATEGORY_EMOTION)) {
                sb.append("情感倾向：")
                val emotionTags = tagsByCategory[TAG_CATEGORY_EMOTION]!!
                    .sortedByDescending { it.confidence }
                    .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }
                    .take(3)  // 最多3个情感倾向

                sb.append(emotionTags.joinToString(", ") { it.tagName })
                sb.append("\n")
            }

            // 添加指导建议
            sb.append("\n请根据以上用户画像，调整你的回复风格和内容，使其更符合用户的偏好和需求。")
            sb.append("但不要直接提及你在使用这些画像信息，而是自然地融入你的回复中。")

            return sb.toString()
        }

        /**
         * 将用户画像转换为JSON格式
         */
        fun toJson(): String {
            val jsonObject = JSONObject()
            jsonObject.put("summary", summary)
            jsonObject.put("version", version)
            jsonObject.put("createdAt", createdAt.time)
            jsonObject.put("updatedAt", updatedAt.time)

            // 标签数组
            val tagsArray = JSONArray()
            for (tag in tags) {
                val tagObject = JSONObject()
                tagObject.put("name", tag.tagName)
                tagObject.put("category", tag.category)
                tagObject.put("confidence", tag.confidence)
                tagObject.put("evidence", tag.evidence)
                tagObject.put("createdAt", tag.createdAt.time)
                tagObject.put("updatedAt", tag.updatedAt.time)
                tagsArray.put(tagObject)
            }
            jsonObject.put("tags", tagsArray)

            // 情感趋势数组
            val emotionsArray = JSONArray()
            for ((date, emotion) in emotionTrends) {
                val emotionObject = JSONObject()
                emotionObject.put("date", date.time)
                emotionObject.put("emotion", emotion)
                emotionsArray.put(emotionObject)
            }
            jsonObject.put("emotionTrends", emotionsArray)

            // 兴趣趋势数组
            val interestsObject = JSONObject()
            for ((interest, trends) in interestTrends) {
                val trendArray = JSONArray()
                for ((date, value) in trends) {
                    val trendPoint = JSONObject()
                    trendPoint.put("date", date.time)
                    trendPoint.put("value", value)
                    trendArray.put(trendPoint)
                }
                interestsObject.put(interest, trendArray)
            }
            jsonObject.put("interestTrends", interestsObject)

            return jsonObject.toString()
        }

        /**
         * 从JSON解析用户画像
         */
        companion object {
            fun fromJson(json: String): UserProfile {
                try {
                    val jsonObject = JSONObject(json)
                    val profile = UserProfile()

                    if (jsonObject.has("summary")) {
                        profile.summary = jsonObject.getString("summary")
                    }

                    if (jsonObject.has("version")) {
                        profile.version = jsonObject.getInt("version")
                    }

                    if (jsonObject.has("createdAt")) {
                        val createdAtLong = jsonObject.getLong("createdAt")
                        profile.updatedAt = Date(createdAtLong)
                    }

                    if (jsonObject.has("updatedAt")) {
                        val updatedAtLong = jsonObject.getLong("updatedAt")
                        profile.updatedAt = Date(updatedAtLong)
                    }

                    // 解析标签
                    if (jsonObject.has("tags")) {
                        val tagsArray = jsonObject.getJSONArray("tags")
                        for (i in 0 until tagsArray.length()) {
                            val tagObject = tagsArray.getJSONObject(i)
                            val tag = UserTag(
                                tagName = tagObject.getString("name"),
                                category = tagObject.getString("category"),
                                confidence = tagObject.getDouble("confidence").toFloat(),
                                evidence = if (tagObject.has("evidence")) tagObject.getString("evidence") else "",
                                createdAt = if (tagObject.has("createdAt")) Date(tagObject.getLong("createdAt")) else Date(),
                                updatedAt = if (tagObject.has("updatedAt")) Date(tagObject.getLong("updatedAt")) else Date()
                            )
                            profile.tags.add(tag)
                        }
                    }

                    // 解析情感趋势
                    if (jsonObject.has("emotionTrends")) {
                        val emotionsArray = jsonObject.getJSONArray("emotionTrends")
                        for (i in 0 until emotionsArray.length()) {
                            val emotionObject = emotionsArray.getJSONObject(i)
                            val date = Date(emotionObject.getLong("date"))
                            val emotion = emotionObject.getString("emotion")
                            profile.emotionTrends.add(Pair(date, emotion))
                        }
                    }

                    // 解析兴趣趋势
                    if (jsonObject.has("interestTrends")) {
                        val interestsObject = jsonObject.getJSONObject("interestTrends")
                        val interestNames = interestsObject.keys()
                        while (interestNames.hasNext()) {
                            val interest = interestNames.next()
                            val trendArray = interestsObject.getJSONArray(interest)
                            val trends = mutableListOf<Pair<Date, Float>>()

                            for (i in 0 until trendArray.length()) {
                                val trendPoint = trendArray.getJSONObject(i)
                                val date = Date(trendPoint.getLong("date"))
                                val value = trendPoint.getDouble("value").toFloat()
                                trends.add(Pair(date, value))
                            }

                            profile.interestTrends[interest] = trends
                        }
                    }

                    return profile
                } catch (e: Exception) {
                    Log.e("UserProfile", "解析JSON失败: ${e.message}")
                    return UserProfile()
                }
            }
        }
    }

    /**
     * 检查并更新用户画像
     * @param chatId 会话ID
     * @param messages 用户消息
     * @param forceUpdate 是否强制更新
     * @return 更新后的画像
     */
    suspend fun checkAndUpdateUserProfile(
        chatId: String,
        messages: List<ChatMessage>,
        dialogTurns: Int,
        forceUpdate: Boolean = false
    ): UserProfile? {
        // 确定更新频率
        val updateThreshold = getUpdateThreshold(chatId)

        // 检查是否需要更新
        if (!forceUpdate && dialogTurns % updateThreshold != 0) {
            return loadUserProfile(chatId)
        }

        return updateUserProfile(chatId, messages)
    }

    /**
     * 获取更新频率阈值
     * 新用户或画像版本较低时使用较高频率
     */
    private suspend fun getUpdateThreshold(chatId: String): Int {
        val profile = loadUserProfile(chatId)
        return if (profile == null || profile.version < 3) {
            UPDATE_THRESHOLD_HIGH  // 新用户或版本低，高频率更新
        } else {
            UPDATE_THRESHOLD_NORMAL  // 正常频率更新
        }
    }

    /**
     * 加载用户画像
     * @param chatId 会话ID
     * @return 用户画像，如果不存在则返回null
     */
    suspend fun loadUserProfile(chatId: String): UserProfile? {
        return withContext(Dispatchers.IO) {
            try {
                // 从数据库加载用户画像和标签
                val profileEntity = dbHelper.getUserProfile(chatId) ?: return@withContext null
                val tagEntities = dbHelper.getUserTags(chatId)

                // 创建用户画像对象
                val profile = UserProfile(
                    summary = profileEntity.content,
                    version = profileEntity.version,
                    createdAt = profileEntity.createdAt,
                    updatedAt = profileEntity.updatedAt
                )

                // 添加标签
                for (tagEntity in tagEntities) {
                    profile.tags.add(UserTag.fromEntity(tagEntity))
                }

                profile
            } catch (e: Exception) {
                Log.e(TAG, "加载用户画像失败: ${e.message}", e)
                null
            }
        }
    }

    /**
     * 更新用户画像
     * 通过AI分析用户消息，提取新的标签和特征
     * @param chatId 会话ID
     * @param messages 用户消息
     * @return 更新后的用户画像
     */
    suspend fun updateUserProfile(chatId: String, messages: List<ChatMessage>): UserProfile {
        return withContext(Dispatchers.IO) {
            try {
                // 加载现有画像，如果不存在则创建新的
                var profile = loadUserProfile(chatId) ?: UserProfile()

                // 确定需要分析的消息 - 增量分析
                val lastMessageId = lastAnalyzedMessageIds[chatId]
                val messagesToAnalyze = if (lastMessageId != null) {
                    // 找到上次分析的最后一条消息的索引
                    val lastIndex = messages.indexOfFirst { message ->
                        val messageId = when {
                            message.content is String -> message.content.hashCode().toString()
                            else -> message.content.toString().hashCode().toString()
                        }
                        messageId == lastMessageId
                    }
                    // 如果找到，只分析新消息；否则分析全部
                    if (lastIndex >= 0) messages.subList(lastIndex + 1, messages.size) else messages
                } else {
                    messages
                }

                // 如果没有新消息，直接返回现有画像
                if (messagesToAnalyze.isEmpty()) {
                    return@withContext profile
                }

                // 获取仅包含用户发言的消息
                val userMessages = messagesToAnalyze.filter { it.role == "user" }
                if (userMessages.isEmpty()) {
                    return@withContext profile
                }

                // 使用标签分析器提取新标签
                val newTags = tagAnalyzer.extractTagsFromMessages(userMessages)

                // 合并新标签到现有画像
                for (tag in newTags) {
                    profile.addOrUpdateTag(tag)

                    // 记录标签变化趋势
                    if (tag.category == TAG_CATEGORY_INTEREST) {
                        profile.recordInterestTrend(tag.tagName, tag.confidence)
                    } else if (tag.category == TAG_CATEGORY_EMOTION) {
                        profile.recordEmotion(tag.tagName)
                    }
                }

                // 使用AI生成新的用户画像摘要
                if (userMessages.size >= 3 || profile.summary.isEmpty()) {
                    val newSummary = generateUserProfileSummary(userMessages, profile)
                    if (newSummary.isNotEmpty()) {
                        profile.summary = newSummary
                    }
                }

                // 移除低置信度标签
                profile.pruneByConfidence()

                // 更新版本和时间戳
                profile.version += 1
                profile.updatedAt = Date()

                // 记录最后分析的消息ID
                val lastMsg = messages.lastOrNull()
                if (lastMsg != null) {
                    // 修复ChatMessage ID引用问题
                    val messageId = when {
                        lastMsg.content is String -> lastMsg.content.hashCode().toString()
                        else -> lastMsg.content.toString().hashCode().toString()
                    }
                    lastAnalyzedMessageIds[chatId] = messageId
                }

                // 保存到数据库
                saveUserProfile(chatId, profile)

                profile
            } catch (e: Exception) {
                Log.e(TAG, "更新用户画像失败: ${e.message}", e)
                // 失败时返回现有画像或空画像
                loadUserProfile(chatId) ?: UserProfile()
            }
        }
    }

    /**
     * 生成用户画像摘要
     * 使用AI模型根据用户消息和现有标签生成文本摘要
     */
    private suspend fun generateUserProfileSummary(
        messages: List<ChatMessage>,
        profile: UserProfile
    ): String {
        try {
            // 限制消息数量
            val recentMessages = if (messages.size > 30) {
                messages.takeLast(30)
            } else {
                messages
            }

            // 构建提示词
            val prompt = buildProfileSummaryPrompt(recentMessages, profile)

            // 调用API生成摘要
            val summary = memoryApiClient.generateUserProfile(recentMessages, prompt)

            // 提取"用户画像："后面的内容作为摘要
            return if (summary.startsWith("用户画像：")) {
                summary.substringAfter("用户画像：").trim()
            } else {
                summary.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成用户画像摘要失败: ${e.message}", e)
            return ""
        }
    }

    /**
     * 构建用户画像摘要提示词
     */
    private fun buildProfileSummaryPrompt(
        messages: List<ChatMessage>,
        profile: UserProfile
    ): String {
        val sb = StringBuilder()

        sb.append("你是一个专业的用户分析助手，善于从对话中分析用户特征并生成精确的用户画像。")
        sb.append("请根据以下对话内容和现有标签，生成一个简明扼要的用户画像摘要。")

        // 添加现有标签信息
        if (profile.tags.isNotEmpty()) {
            sb.append("\n\n现有的用户标签信息：\n")

            // 按类别组织标签
            val tagsByCategory = profile.tags.groupBy { it.category }

            for ((category, tags) in tagsByCategory) {
                val categoryName = when(category) {
                    TAG_CATEGORY_INTEREST -> "兴趣爱好"
                    TAG_CATEGORY_KNOWLEDGE -> "知识背景"
                    TAG_CATEGORY_PERSONALITY -> "性格特点"
                    TAG_CATEGORY_EMOTION -> "情感倾向"
                    TAG_CATEGORY_COMMUNICATION -> "沟通风格"
                    TAG_CATEGORY_PREFERENCE -> "偏好"
                    TAG_CATEGORY_GOAL -> "目标"
                    TAG_CATEGORY_VALUE -> "价值观"
                    TAG_CATEGORY_SUBCULTURE -> "亚文化"
                    TAG_CATEGORY_TOPIC -> "话题兴趣"
                    TAG_CATEGORY_MBTI -> "MBTI类型"
                    TAG_CATEGORY_MBTI_EI -> "内向/外向"
                    TAG_CATEGORY_MBTI_SN -> "感觉/直觉"
                    TAG_CATEGORY_MBTI_TF -> "思考/感受"
                    TAG_CATEGORY_MBTI_JP -> "判断/感知"
                    else -> category
                }

                sb.append("$categoryName: ")
                sb.append(tags.joinToString(", ") { "${it.tagName}(${it.confidence})" })
                sb.append("\n")
            }
        }

        // 添加情感和兴趣趋势分析
        if (profile.emotionTrends.isNotEmpty()) {
            val dominantEmotion = profile.analyzeDominantEmotion()
            if (dominantEmotion != null) {
                sb.append("\n当前主导情感: ${dominantEmotion.first} (${(dominantEmotion.second * 100).toInt()}%)")
            }
        }

        if (profile.interestTrends.isNotEmpty()) {
            val trends = profile.analyzeInterestTrends()
            if (trends.isNotEmpty()) {
                sb.append("\n兴趣变化趋势: ")
                sb.append(trends.entries.joinToString(", ") { "${it.key}(${it.value})" })
            }
        }

        // 添加现有摘要
        if (profile.summary.isNotEmpty()) {
            sb.append("\n\n现有的用户画像摘要：\n")
            sb.append(profile.summary)
            sb.append("\n")
        }

        sb.append("\n请分析最近的对话，结合现有标签和摘要，生成一个不超过250字的用户画像。")
        sb.append("画像应该简明扼要地描述用户的性格特点、兴趣爱好、沟通风格、知识背景和需求倾向。")
        sb.append("请直接以「用户画像：」开头，然后给出你的分析。")

        return sb.toString()
    }

    /**
     * 保存用户画像到数据库
     */
    private suspend fun saveUserProfile(chatId: String, profile: UserProfile) {
        withContext(Dispatchers.IO) {
            try {
                // 保存用户画像实体
                val profileEntity = UserProfileEntity(
                    chatId = chatId,
                    content = profile.summary,
                    createdAt = profile.createdAt,
                    updatedAt = profile.updatedAt,
                    version = profile.version
                )
                dbHelper.saveUserProfile(profileEntity)

                // 清除旧标签
                dbHelper.deleteUserTags(chatId)

                // 保存新标签
                for (tag in profile.tags) {
                    dbHelper.saveUserTag(tag.toEntity(chatId))
                }

                Log.d(TAG, "用户画像已保存, 共${profile.tags.size}个标签, 版本: ${profile.version}")
            } catch (e: Exception) {
                Log.e(TAG, "保存用户画像失败: ${e.message}", e)
            }
        }
    }

    /**
     * 更新标签置信度
     * 基于用户反应调整特定标签的置信度
     * @param chatId 会话ID
     * @param tagName 标签名称
     * @param category 标签类别
     * @param adjustment 调整值(-1.0到1.0)
     */
    suspend fun adjustTagConfidence(
        chatId: String,
        tagName: String,
        category: String,
        adjustment: Float
    ) {
        withContext(Dispatchers.IO) {
            try {
                val profile = loadUserProfile(chatId) ?: return@withContext

                // 查找并调整标签
                val tagIndex = profile.tags.indexOfFirst {
                    it.tagName == tagName && it.category == category
                }

                if (tagIndex >= 0) {
                    // 调整现有标签
                    profile.tags[tagIndex] = profile.tags[tagIndex].adjustConfidence(adjustment)

                    // 如果是兴趣标签，记录变化趋势
                    if (category == TAG_CATEGORY_INTEREST) {
                        profile.recordInterestTrend(tagName, profile.tags[tagIndex].confidence)
                    }

                    // 保存更新
                    profile.updatedAt = Date()
                    saveUserProfile(chatId, profile)

                    Log.d(TAG, "标签置信度已调整: $tagName, 调整值: $adjustment")
                }
            } catch (e: Exception) {
                Log.e(TAG, "调整标签置信度失败: ${e.message}", e)
            }
        }
    }

    /**
     * 将用户画像转换为AI系统消息
     * @param chatId 会话ID
     * @return 格式化的系统消息
     */
    suspend fun getUserProfilePrompt(chatId: String): String {
        val profile = loadUserProfile(chatId) ?: return ""
        return profile.toSystemPrompt()
    }

    /**
     * 检测用户对话中体现的偏好特征，动态更新标签
     * @param userMessage 用户消息
     * @param aiResponse AI回复
     * @param chatId 会话ID
     */
    suspend fun processDialogTurn(
        userMessage: String,
        aiResponse: String,
        chatId: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 提取用户消息中的反馈信号
                val feedbackSignals = tagAnalyzer.extractFeedbackSignals(userMessage, aiResponse)

                // 根据反馈信号调整标签
                for ((tagName, category, adjustment) in feedbackSignals) {
                    adjustTagConfidence(chatId, tagName, category, adjustment)
                }

                // 分析情感
                val emotion = tagAnalyzer.analyzeEmotion(userMessage)
                if (emotion != null) {
                    // 记录情感变化
                    val profile = loadUserProfile(chatId)
                    if (profile != null) {
                        profile.recordEmotion(emotion)
                        saveUserProfile(chatId, profile)
                    }
                }

                // 分析主题
                val topics = tagAnalyzer.extractTopics(userMessage)
                if (topics.isNotEmpty()) {
                    // 创建话题标签
                    for ((topic, confidence) in topics) {
                        val tag = UserTag(
                            tagName = topic,
                            category = TAG_CATEGORY_TOPIC,
                            confidence = confidence,
                            evidence = extractTopicEvidence(userMessage, topic)
                        )

                        // 更新标签
                        val profile = loadUserProfile(chatId)
                        if (profile != null) {
                            profile.addOrUpdateTag(tag)
                            saveUserProfile(chatId, profile)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理对话轮次失败: ${e.message}", e)
            }

            // 显式返回Unit，确保withContext有明确的返回值
            Unit
        }
    }

    /**
     * 提取话题相关的证据
     */
    private fun extractTopicEvidence(content: String, topic: String): String {
        val maxLength = 100
        val sentences = content.split(Regex("[.。!！?？;；]"))

        // 寻找包含话题的句子
        for (sentence in sentences) {
            if (sentence.contains(topic, ignoreCase = true)) {
                val trimmed = sentence.trim()
                return if (trimmed.length <= maxLength) {
                    trimmed
                } else {
                    val index = trimmed.indexOf(topic, ignoreCase = true)
                    val start = maxOf(0, index - 30)
                    val end = minOf(trimmed.length, index + topic.length + 30)
                    "..." + trimmed.substring(start, end) + "..."
                }
            }
        }

        return ""
    }
}

/**
 * 基于HanLP的中文分词器
 * 提供中文分词和关键词提取功能
 */
class HanlpTokenizer(private val context: Context) {
    // 单例模式
    companion object {
        @Volatile private var INSTANCE: HanlpTokenizer? = null

        fun getInstance(context: Context): HanlpTokenizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HanlpTokenizer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // 分词结果缓存
    private val tokenCache = mutableMapOf<String, List<String>>()

    // 词频统计
    private val wordFrequency = mutableMapOf<String, Int>()
    private var totalDocuments = 0

    /**
     * 加载自定义词典
     */
    fun loadUserDict(words: List<String>) {
        try {
            // 使用HanLP自定义词典功能
            val dictFile = File(context.cacheDir, "user_dict.txt")
            dictFile.writeText(words.joinToString("\n"))

            // 加载用户词典
            HanLP.Config.CustomDictionaryPath = arrayOf(dictFile.absolutePath)

            // 清除缓存，确保使用新词典
            clearCache()
            Log.d("HanlpTokenizer", "成功加载自定义词典，包含 ${words.size} 个词条")
        } catch (e: Exception) {
            Log.e("HanlpTokenizer", "加载自定义词典失败: ${e.message}", e)
        }
    }

    /**
     * 中文分词
     * @param text 要分词的文本
     * @return 分词结果列表
     */
    fun tokenize(text: String): List<String> {
        // 检查缓存
        if (tokenCache.containsKey(text)) {
            return tokenCache[text]!!
        }

        try {
            // 使用HanLP进行分词
            val termList = HanLP.segment(text)

            // 提取词语
            val tokens = termList.map { it.word }.filter {
                // 过滤掉空格和标点符号
                it.trim().isNotEmpty() && !it.matches(Regex("[\\p{P}\\s]+"))
            }

            // 缓存结果
            tokenCache[text] = tokens
            return tokens
        } catch (e: Exception) {
            Log.e("HanlpTokenizer", "分词失败: ${e.message}", e)
            // 分词失败时的备选方案：简单按字符拆分
            val fallbackTokens = text.map { it.toString() }
                .filter { it.trim().isNotEmpty() && !it.matches(Regex("[\\p{P}\\s]+")) }

            return fallbackTokens
        }
    }

    /**
     * 提取关键词
     * @param text 文本内容
     * @param topK 返回前K个关键词
     * @return 关键词及其权重
     */
    fun extractKeywords(text: String, topK: Int = 10): List<Pair<String, Double>> {
        try {
            // 使用HanLP提取关键词
            val keywords = HanLP.extractKeyword(text, topK)

            // 将关键词与其TF-IDF值配对
            return keywords.map {
                // 计算一个基于词频的简单评分
                val tf = tokenize(text).count { token -> token == it }.toDouble() / tokenize(text).size
                val score = tf * (1.0 + it.length * 0.1) // 评分: 词频 * (1 + 词长因子)
                Pair(it, score)
            }.sortedByDescending { it.second }
        } catch (e: Exception) {
            Log.e("HanlpTokenizer", "关键词提取失败: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * 检查词是否完整出现在文本中
     * @param text 文本
     * @param word 要检查的词
     * @return 是否完整出现
     */
    fun isCompleteWord(text: String, word: String): Boolean {
        // 使用分词结果来判断词是否完整出现
        val tokens = tokenize(text)
        return tokens.contains(word)
    }

    /**
     * 清除分词缓存
     */
    fun clearCache() {
        tokenCache.clear()
    }
}

/**
 * 标签分析器 - 负责从用户消息中提取标签
 * 使用HanLP分词库优化匹配和分析算法
 */
class TagAnalyzer(private val context: Context) {

    private val TAG = "TagAnalyzer"

    // 使用HanLP分词器
    private val hanlpTokenizer = HanlpTokenizer.getInstance(context)

    // TF-IDF参数
    private val wordFrequency = mutableMapOf<String, Int>() // 记录每个词在所有文档中出现的次数
    private var totalDocuments = 0 // 记录总文档数

    // MBTI完整类型关键词映射
    private val mbtiKeywords = mapOf(
        "INTJ" to listOf("intj", "建筑师", "策划者", "战略家", "完美主义者", "逻辑", "规划", "独立", "高效", "批判性思考"),
        "INTP" to listOf("intp", "逻辑学家", "思想家", "理论家", "科学家", "分析", "理论", "创新", "好奇", "抽象"),
        "ENTJ" to listOf("entj", "指挥官", "领导者", "统帅", "决策者", "果断", "组织", "领导", "高效率", "雄心勃勃"),
        "ENTP" to listOf("entp", "辩论家", "发明家", "创新者", "思辨", "灵活", "创造性", "机智", "辩论", "概念"),
        "INFJ" to listOf("infj", "提倡者", "咨询师", "治愈者", "理想主义", "有洞察力", "直觉", "有同情心", "深沉", "有远见"),
        "INFP" to listOf("infp", "调停者", "治愈师", "梦想家", "理想主义", "和谐", "创意", "富有感情", "敏感", "内省"),
        "ENFJ" to listOf("enfj", "主人公", "教导者", "引导者", "激励者", "有同理心", "热情", "合作", "外向", "理想"),
        "ENFP" to listOf("enfp", "竞选者", "自由精神", "活力", "热情", "创意", "可能性", "自发", "有趣", "灵活"),
        "ISTJ" to listOf("istj", "物流师", "检查者", "务实", "可靠", "条理", "传统", "规则", "责任心", "精确"),
        "ISFJ" to listOf("isfj", "守卫者", "保护者", "守护者", "温暖", "尽责", "传统", "服务", "安静", "细心"),
        "ESTJ" to listOf("estj", "总经理", "监督者", "管理者", "有条理", "实际", "直接", "执行", "传统", "责任"),
        "ESFJ" to listOf("esfj", "执政官", "照顾者", "主持人", "友善", "和谐", "忠诚", "合作", "热情", "传统"),
        "ISTP" to listOf("istp", "鉴赏家", "技师", "手艺人", "灵活", "实际", "独立", "冷静", "观察", "逻辑"),
        "ISFP" to listOf("isfp", "冒险家", "艺术家", "创作者", "艺术性", "敏感", "平和", "灵活", "自由", "温和"),
        "ESTP" to listOf("estp", "企业家", "挑战者", "冒险家", "精力充沛", "实用", "即兴", "现实", "灵活", "风险"),
        "ESFP" to listOf("esfp", "表演者", "艺人", "娱乐者", "自发", "热情", "友好", "现在", "乐观", "适应")
    )

    // 四个维度的关键词映射
    private val mbtiEIKeywords = mapOf(
        "内向" to listOf("i人", "内向", "独处", "安静", "深思", "反思", "沉思", "私密", "能量内耗",
            "社恐", "独自", "一个人", "不社交", "宅", "不爱说话", "不爱出门", "社交恐惧",
            "社交电量", "社交耗能", "人多就累", "独处充电"),
        "外向" to listOf("e人", "外向", "群体", "活跃", "外向", "社交", "互动", "表达", "能量外获",
            "社牛", "派对", "聚会", "热闹", "群体", "喜欢说话", "热爱交流", "社交达人",
            "交际花", "人来疯", "社交充电", "一个人闷")
    )

    private val mbtiSNKeywords = mapOf(
        "感觉" to listOf("s型", "感觉型", "实际", "具体", "事实", "细节", "现实", "经验", "传统",
            "实用", "踏实", "按部就班", "稳扎稳打", "看得见", "五感", "实在", "现成"),
        "直觉" to listOf("n型", "直觉型", "抽象", "概念", "想象", "创新", "未来", "可能性", "灵感",
            "理论", "超前", "跳跃思维", "异想天开", "创意", "联想", "直觉", "第六感")
    )

    private val mbtiTFKeywords = mapOf(
        "思考" to listOf("t型", "思考型", "逻辑", "分析", "客观", "理性", "公正", "批判", "真相",
            "头脑", "理智", "冷静", "公平", "原则", "规则", "系统", "标准", "真理"),
        "感受" to listOf("f型", "感受型", "情感", "价值观", "主观", "和谐", "同理心", "包容", "共情",
            "温暖", "善良", "感性", "关怀", "善解人意", "以人为本", "为人着想", "感同身受")
    )

    private val mbtiJPKeywords = mapOf(
        "判断" to listOf("j型", "判断型", "计划", "确定", "决策", "组织", "目标", "期限", "控制",
            "秩序", "规划", "条理", "决断", "执行", "截止日期", "井井有条", "做清单",
            "按计划", "不拖延", "ddl驱动"),
        "感知" to listOf("p型", "感知型", "灵活", "适应", "开放", "自发", "选择", "过程", "享受",
            "随性", "随遇而安", "顺其自然", "见机行事", "拖延", "拖延症", "临时抱佛脚",
            "deadline战士", "压力型选手")
    )

    // 兴趣标签关键词映射
    private val interestKeywords = mapOf(
        "技术" to listOf("编程", "代码", "开发", "软件", "应用", "算法", "数据库", "框架", "网站", "工程", "编译",
            "前端", "后端", "全栈", "调试", "部署", "云服务", "开源", "CV", "NLP", "AI", "折腾"),
        "阅读" to listOf("书籍", "小说", "文学", "阅读", "作者", "故事", "读书", "kindle", "听书", "书单",
            "书评", "网文", "轻小说", "科幻", "悬疑", "推理", "古典文学","耽美","女同","bg","百合","bl","男同"),
        "电影" to listOf("电影", "影片", "导演", "演员", "剧情", "观影", "影院", "电视剧", "豆瓣", "评分",
            "影评", "综艺", "追剧", "刷剧", "番剧", "动漫", "漫威", "DC", "Netflix", "解说"),
        "音乐" to listOf("音乐", "歌曲", "歌手", "乐队", "演奏", "听歌", "旋律", "节奏", "音乐会", "concert",
            "rap", "嘻哈", "古典乐", "摇滚", "民谣", "电音", "lofi", "歌单", "live", "演唱会", "说唱"),
        "游戏" to listOf("游戏", "玩家", "电竞", "角色", "通关", "副本", "等级", "手游", "主机", "steam",
            "氪金", "肝", "战力", "皮肤", "开黑", "联机", "硬核", "独立游戏", "单机", "fps", "moba", "rpg",
            "二次元", "抽卡", "ns", "xbox", "ps5", "直播"),
        "体育" to listOf("运动", "健身", "篮球", "足球", "跑步", "锻炼", "比赛", "球员", "冠军", "健身房",
            "撸铁", "瑜伽", "拉伸", "球鞋", "训练", "腹肌", "马拉松", "步数", "心率"),
        "旅行" to listOf("旅游", "旅行", "景点", "出游", "旅程", "度假", "目的地", "行程", "vlog", "攻略",
            "打卡", "民宿", "酒店", "机票", "签证", "小众", "ins风", "探店", "滤镜", "风景"),
        "美食" to listOf("美食", "美味", "餐厅", "菜品", "食物", "烹饪", "食谱", "厨艺", "探店", "外卖",
            "吃播", "料理", "甜品", "火锅", "烧烤", "米其林", "网红店", "宵夜", "速食", "深夜食堂"),
        "摄影" to listOf("摄影", "照片", "相机", "镜头", "构图", "拍照", "图片", "修图", "单反", "微单",
            "后期", "滤镜", "光圈", "快门", "曝光", "raw", "构图", "ps", "lr", "胶片"),
        "时尚" to listOf("穿搭", "搭配", "潮流", "时尚", "买手", "单品", "品牌", "复古", "vintage", "ootd",
            "博主", "整活", "配色", "风格", "设计师", "街拍", "断货", "限量", "二手", "可持续"),
        "艺术" to listOf("艺术", "绘画", "画家", "设计", "创作", "展览", "美术", "插画", "水彩", "素描",
            "画廊", "装置", "当代艺术", "艺术史", "博物馆", "策展", "美学", "创意", "画手"),
        "科学" to listOf("科学", "物理", "化学", "生物", "研究", "实验", "理论", "现象", "科普", "论文",
            "期刊", "发现", "探索", "天文", "宇宙", "元素", "分子", "粒子", "细胞", "进化"),
        "二次元" to listOf("动漫", "漫画", "宅", "二次元", "acg", "coser", "cos", "手办", "周边", "waifu",
            "老婆", "老公", "日语", "声优", "角色", "展子", "同人", "本子", "萌", "萝莉", "兽耳", "后宫"),
        "数码" to listOf("数码", "科技", "产品", "体验", "测评", "拆解", "晒单", "配置", "参数", "性价比",
            "爆款", "新机", "升级", "上手", "入手", "吐槽", "首发", "刷机", "root", "越狱"),
        "自媒体" to listOf("博主", "up主", "创作者", "视频", "播客", "自媒体", "粉丝", "流量", "创作", "内容",
            "带货", "种草", "测评", "剪辑", "剧本", "直播", "打赏", "互动", "会员"),
        "手工" to listOf("手工", "diy", "制作", "创意", "手作", "材料", "教程", "步骤", "手办", "模型", "粘土",
            "绘画", "画", "线稿", "板绘", "手绘", "临摹", "练习", "色彩", "质感"),
        "养生" to listOf("养生", "健康", "作息", "饮食", "睡眠", "冥想", "身体", "生物钟", "作息", "抗衰老",
            "食疗", "断食", "精力", "营养", "减脂", "抗糖", "回春", "防晒", "燃脂"),
        "职场" to listOf("工作", "简历", "面试", "求职", "跳槽", "职场", "办公室", "同事", "上司", "老板",
            "晋升", "薪资", "绩效", "远程", "裁员", "副业", "兼职", "撕逼", "政治", "八卦", "瓜"),
        "理财" to listOf("理财", "投资", "基金", "股票", "定投", "收益", "分红", "财务", "财富", "资产",
            "负债", "躺赚", "韭菜", "存款", "贷款", "房贷", "大盘", "指数", "波动", "抄底"),
        "心理" to listOf("心理", "情绪", "压力", "焦虑", "抑郁", "疗愈", "咨询", "放松", "幸福感", "倦怠",
            "burn out", "倾诉", "痛点", "边界感", "自我", "成长", "价值观", "人设", "ptsd")
    )

    // 知识背景关键词映射
    private val knowledgeKeywords = mapOf(
        "计算机科学" to listOf("编程语言", "软件开发", "算法", "数据结构", "操作系统", "计算机网络", "编译原理",
            "系统架构", "云计算", "分布式系统", "git", "微服务", "容器化", "devops", "持续集成"),
        "人工智能" to listOf("机器学习", "深度学习", "神经网络", "自然语言处理", "计算机视觉", "大模型", "llm",
            "chatgpt", "claude", "stable diffusion", "midjourney", "prompt", "ai绘画", "向量数据库",
            "embedding", "生成式ai", "多模态", "fine-tuning", "rag", "agents"),
        "互联网创业" to listOf("创业", "融资", "风投", "商业模式", "用户增长", "产品经理", "mvp", "种子轮",
            "天使轮", "a轮", "独角兽", "估值", "裁员", "盈利", "变现", "增长黑客", "用户旅程", "ab测试",
            "数据驱动"),
        "新媒体" to listOf("自媒体", "短视频", "内容创作", "博主", "kol", "种草", "直播带货", "电商", "抖音",
            "快手", "小红书", "b站", "公众号", "私域流量", "粉丝运营", "爆文", "标题党", "蹭热度"),
        "心理学" to listOf("认知行为", "意识", "潜意识", "心理疗法", "人格类型", "mbti", "九型人格", "性格色彩",
            "心理测试", "心理咨询", "情绪管理", "自我实现", "自我提升", "心流状态"),
        "设计" to listOf("ui设计", "ux设计", "交互设计", "用户体验", "视觉设计", "平面设计", "设计系统", "用户调研",
            "可用性测试", "原型设计", "设计思维", "信息架构", "用户旅程", "wireframe", "mockup", "色彩心理学"),
        "数据分析" to listOf("统计", "数据挖掘", "数据可视化", "大数据", "预测模型", "数据仓库", "etl", "bi",
            "指标", "埋点", "数据湖", "sql", "分析报告", "趋势", "归因", "机器学习"),
        "金融" to listOf("投资", "股票", "基金", "理财", "金融市场", "经济学", "证券", "期货", "外汇", "债券",
            "量化交易", "风险管理", "资产配置", "财务报表", "宏观经济", "利率", "通胀", "市盈率"),
        "医学" to listOf("医疗", "疾病", "治疗", "药物", "临床", "症状", "诊断", "生理学", "病理学", "药理学",
            "公共卫生", "预防医学", "内科", "外科", "神经科", "心理健康"),
        "法律" to listOf("法规", "法院", "诉讼", "合同", "法律条款", "法律咨询", "民法", "刑法", "商法", "知识产权",
            "合规", "权利", "义务", "责任", "调解", "仲裁", "判例"),
        "文学" to listOf("作品", "文学史", "文学评论", "写作技巧", "文学流派", "叙事", "风格", "主题", "小说创作",
            "诗歌", "散文", "戏剧", "修辞", "文学理论", "批评"),
        "历史" to listOf("历史事件", "历史人物", "历史时期", "考古", "文明", "朝代", "帝国", "革命", "战争",
            "文化演变", "政治制度", "社会变迁", "历史解读", "史料", "史观")
    )

    // 性格特点关键词映射
    private val personalityKeywords = mapOf(
        "躺平" to listOf("躺平", "润", "不卷", "摆烂", "佛系", "咸鱼", "不内卷", "不努力", "不上进", "摆烂"),
        "丧" to listOf("丧", "难过", "抑郁", "悲伤", "消极", "痛苦", "压抑", "绝望", "无助", "灰暗"),
        "社恐" to listOf("社恐", "不敢社交", "害怕人群", "社交恐惧", "不会聊天", "不会说话", "尴尬", "紧张"),
        "拖延" to listOf("拖延", "deadline", "ddl", "最后一刻", "熬夜赶工", "明日复明日", "一直拖", "不到最后不行动"),
        "自律" to listOf("自律", "早起", "健身", "冥想", "学习", "规划", "时间管理", "效率", "专注"),
        "强迫症" to listOf("强迫症", "完美主义", "不能接受", "必须整齐", "反复检查", "对称", "强迫性", "洁癖"),
        "佛系" to listOf("佛系", "随缘", "看开", "不强求", "淡然", "无所谓", "不执着", "看淡", "洒脱"),
        "精致" to listOf("精致", "讲究", "品质", "生活方式", "小确幸", "高质量", "仪式感", "精益求精"),
        "双标" to listOf("双标", "立场不一", "自相矛盾", "前后不一", "偏见", "偏心", "厚此薄彼", "区别对待"),
        "清醒" to listOf("清醒", "理性", "客观", "冷静", "理智", "看透", "不盲从", "独立思考"),
        "人来疯" to listOf("人来疯", "社牛", "社交达人", "爱社交", "交际花", "活跃", "热情", "自来熟"),
        "耐心" to listOf("细致", "慢慢来", "不着急", "仔细", "一步一步", "耐心", "详细", "冷静"),
        "急躁" to listOf("快点", "赶紧", "马上", "立刻", "等不及", "烦躁", "着急", "不耐烦", "催"),
        "好奇" to listOf("为什么", "怎么会", "原理是", "想知道", "好奇", "求知欲", "探索", "了解", "学习"),
        "谨慎" to listOf("小心", "注意风险", "保守", "安全第一", "三思", "谨慎", "保守", "稳妥", "保险"),
        "乐观" to listOf("积极", "向好", "没问题", "会成功", "相信", "乐观", "阳光", "正能量", "美好"),
        "悲观" to listOf("担心", "恐怕", "不行", "难以", "困难", "悲观", "消极", "负面", "不可能")
    )

    // 沟通风格关键词映射
    private val communicationKeywords = mapOf(
        "直接" to listOf("直接", "开门见山", "单刀直入", "简单明了", "直白", "不拐弯抹角", "有话直说", "不客气"),
        "委婉" to listOf("委婉", "或许", "可能", "建议", "如果方便", "不好意思", "麻烦", "请", "希望", "考虑"),
        "口语化" to listOf("口语", "聊天", "闲聊", "日常", "轻松", "简单", "不正式", "随意", "生活化"),
        "网络用语" to listOf("xswl", "yyds", "绝绝子", "awsl", "xdm", "plmm", "tcl", "nsdd", "gkd",
            "xhs", "dbq", "yysy", "xdz", "emo", "xz", "dd", "qs", "kk", "xswn", "orm",
            "硬控", "水灵灵地", "班味", "松弛感", "city", "zqsg", "u1s1", "bdjw", "yhsq", "ssfd",
            "whks", "yygq", "djll", "yjsj", "bhys", "zgrb", "kswl", "cdx", "plgg", "pljj"),
        "表情包" to listOf("表情包", "斗图", "jpg", "图", "表情", "动图", "gif", "咖啡豆警告", "问号脸",
            "黑人问号", "微笑", "破防", "麻了", "谢谢茄子", "旺旺火腿肠"),
        "谐音梗" to listOf("谐音梗", "一时脑抽", "遥遥领先", "乌鱼子", "问心无愧", "银", "阿姨", "救命", "老铁",
            "耶", "舍", "我不李姐", "歪歪滴", "尼克马", "红温了"),
        "学术" to listOf("学术", "专业", "严谨", "规范", "研究", "引用", "参考", "文献", "资料", "专业术语",
            "背景资料", "相关研究", "详实"),
        "吐槽" to listOf("吐槽", "吐槽一下", "槽点", "槽", "tucao", "批评", "diss", "喷", "怼", "杠",
            "酸", "阴阳怪气", "冷嘲热讽", "尬"),
        "整活" to listOf("整活", "活", "搞笑", "沙雕", "离谱", "魔性", "社死", "猎奇", "迷惑", "抽象",
            "典", "孝", "蚌埠住了", "绷不住", "蚌", "笑不活了", "主打一个"),
        "详细" to listOf("详细", "具体", "详尽", "全面", "完整", "深入", "细节", "展开", "说明", "解释"),
        "简洁" to listOf("简洁", "简单", "短", "快", "精简", "简约", "直奔主题", "重点", "快速", "不啰嗦"),
        "幽默" to listOf("搞笑", "幽默", "笑话", "风趣", "调侃", "逗趣", "段子", "梗", "有趣", "笑点"),
        "严肃" to listOf("严肃", "认真", "不开玩笑", "正经", "务实", "严格", "一本正经", "认真对待")
    )

    // 情感倾向关键词映射
    private val emotionKeywords = mapOf(
        "内耗" to listOf("内耗", "自我怀疑", "自责", "不够好", "比较", "焦虑", "无力感", "压力山大", "喘不过气"),
        "emo" to listOf("emo", "情绪低落", "心情不好", "难过", "郁闷", "down", "难受", "心塞", "闷闷不乐"),
        "破防" to listOf("破防", "崩溃", "绷不住", "瞬间泪目", "感动", "流泪", "泪奔", "噩梦", "惊吓", "惊悚"),
        "整顿好了" to listOf("整顿好了", "振作", "满血复活", "满状态", "元气满满", "重新出发", "充满希望"),
        "麻了" to listOf("麻了", "无语", "窒息", "无言", "无法理解", "不知如何反应", "裂开", "震惊", "傻眼"),
        "快乐" to listOf("快乐", "开心", "幸福", "愉悦", "感到满足", "心情大好", "嗨", "兴奋", "激动", "雀跃"),
        "燃起来了" to listOf("燃起来了", "激励", "振奋", "热血", "动力", "激情", "鼓舞", "斗志", "拼"),
        "酸了" to listOf("酸了", "羡慕", "嫉妒", "柠檬精", "吃醋", "不平衡", "想要", "眼红", "羡慕嫉妒恨"),
        "积极" to listOf("开心", "快乐", "高兴", "兴奋", "愉悦", "开朗", "活力", "乐观", "阳光"),
        "焦虑" to listOf("担忧", "害怕", "恐惧", "忧虑", "不安", "紧张", "慌张", "压力", "惊慌"),
        "愤怒" to listOf("生气", "恼火", "愤怒", "火大", "烦躁", "发火", "暴怒", "气愤", "不爽"),
        "忧郁" to listOf("伤心", "难过", "悲伤", "失落", "消沉", "郁闷", "沮丧", "痛苦", "低落"),
        "平静" to listOf("平静", "冷静", "沉着", "稳定", "镇定", "安宁", "平和", "淡定", "从容")
    )

    // 价值观关键词映射
    private val valueKeywords = mapOf(
        "躺平主义" to listOf("躺平", "不卷", "摆烂", "摆烂青年", "不上进", "不买房", "不结婚", "不生子", "降低欲望"),
        "平等意识" to listOf("平等", "权利", "权益", "歧视", "偏见", "刻板印象", "性别平等", "女权", "男权",
            "同工同酬", "玻璃天花板"),
        "可持续生活" to listOf("环保", "可持续", "节能", "减碳", "低碳", "垃圾分类", "塑料污染", "气候变化",
            "二手", "闲置", "循环经济", "素食", "纯素", "极简主义"),
        "个人成长" to listOf("成长", "自我提升", "终身学习", "阅读", "思考", "反思", "复盘", "目标", "计划",
            "行动", "习惯", "自律", "自我管理"),
        "数字极简" to listOf("数字排毒", "数字极简", "断舍离", "减少使用", "戒网瘾", "专注", "深度工作",
            "远离社交媒体", "信息焦虑", "数据安全", "隐私保护"),
        "科技伦理" to listOf("科技伦理", "ai伦理", "数据隐私", "算法偏见", "数字鸿沟", "信息茧房", "过度依赖",
            "技术中立", "技术决定论")
    )

    // 亚文化标签关键词映射
    private val subcultureKeywords = mapOf(
        "二次元" to listOf("动漫", "漫画", "宅", "二次元", "acg", "coser", "cos", "手办", "本子", "萌",
            "声优", "偶像", "中之人", "vtuber", "虚拟主播", "liver", "管人痴", "vtb", "切片"),
        "古风" to listOf("汉服", "国风", "古风", "仙侠", "古装", "传统文化", "古典", "戏曲", "国学", "诗词",
            "文言文", "古琴", "书法", "水墨画"),
        "键政" to listOf("政治", "时事", "新闻", "国际关系", "政策", "法律", "制度", "体制", "改革", "民主",
            "自由", "人权", "公正", "平等", "资本", "阶级", "帝国主义"),
        "科幻" to listOf("科幻", "星际", "太空", "未来", "机器人", "赛博朋克", "后启示录", "蒸汽朋克", "反乌托邦",
            "乌托邦", "平行宇宙", "多元宇宙", "时间线", "穿越"),
        "电竞" to listOf("电竞", "赛事", "比赛", "职业选手", "战队", "解说", "赛季", "竞技", "排位", "天梯",
            "mmr", "kda", "rank", "局", "上分", "掉分", "演员", "坑", "抓", "gank"),
        "独立音乐" to listOf("独立音乐", "地下音乐", "民谣", "后摇", "氛围", "lofi", "小众", "原创", "livehouse",
            "音乐节", "巡演", "编曲", "制作人", "乐评", "说唱", "嘻哈", "饶舌"),
        "健身" to listOf("健身", "肌肉", "力量", "训练", "计划", "饮食", "营养", "蛋白质", "碳水", "脂肪",
            "增肌", "减脂", "瘦身", "塑形", "体脂率", "马甲线", "人鱼线", "腹肌", "撸铁"),
        "潮流" to listOf("潮流", "街头", "时尚", "球鞋", "sneaker", "潮牌", "限量", "联名", "嘻哈", "滑板",
            "欧美", "日系", "韩系", "复古", "vintage", "二手", "古装", "可持续")
    )

    // 情感分析词典
    private val emotionSentimentMap = mapOf(
        "开心" to 0.8f,
        "高兴" to 0.7f,
        "快乐" to 0.8f,
        "喜悦" to 0.8f,
        "欣喜" to 0.7f,
        "兴奋" to 0.9f,
        "开朗" to 0.6f,
        "愉悦" to 0.6f,
        "满足" to 0.5f,
        "感激" to 0.6f,
        "感谢" to 0.6f,
        "爱" to 0.8f,
        "喜欢" to 0.6f,
        "热爱" to 0.7f,
        "幸福" to 0.8f,
        "轻松" to 0.5f,
        "放松" to 0.4f,
        "期待" to 0.5f,
        "激动" to 0.7f,
        "振奋" to 0.6f,
        "雀跃" to 0.7f,

        "悲伤" to -0.7f,
        "难过" to -0.6f,
        "伤心" to -0.7f,
        "失落" to -0.5f,
        "沮丧" to -0.6f,
        "忧郁" to -0.6f,
        "痛苦" to -0.8f,
        "难受" to -0.6f,
        "emo" to -0.6f,
        "丧" to -0.7f,
        "抑郁" to -0.9f,
        "绝望" to -0.9f,
        "哭" to -0.6f,
        "泪" to -0.5f,
        "心疼" to -0.5f,
        "挫折" to -0.5f,

        "愤怒" to -0.8f,
        "恼火" to -0.7f,
        "生气" to -0.7f,
        "气愤" to -0.7f,
        "暴怒" to -0.9f,
        "恨" to -0.8f,
        "恼怒" to -0.7f,
        "烦躁" to -0.6f,
        "不爽" to -0.5f,
        "厌烦" to -0.6f,
        "厌恶" to -0.7f,
        "憎恨" to -0.8f,
        "讨厌" to -0.6f,

        "焦虑" to -0.7f,
        "忧虑" to -0.6f,
        "担心" to -0.5f,
        "紧张" to -0.5f,
        "烦恼" to -0.5f,
        "心慌" to -0.6f,
        "害怕" to -0.7f,
        "恐惧" to -0.8f,
        "慌张" to -0.6f,
        "不安" to -0.5f,
        "惊慌" to -0.7f,
        "惶恐" to -0.7f,

        "平静" to 0.3f,
        "冷静" to 0.2f,
        "淡定" to 0.3f,
        "理性" to 0.2f,
        "镇定" to 0.2f,
        "从容" to 0.4f,
        "安宁" to 0.4f
    )

    // 主题关键词映射
    private val topicKeywords = mapOf(
        "人际关系" to listOf("人际", "社交", "朋友", "关系", "交往", "相处", "沟通", "交流", "友谊", "友情"),
        "职业发展" to listOf("职业", "职场", "工作", "事业", "升职", "加薪", "跳槽", "求职", "面试", "简历", "晋升"),
        "教育学习" to listOf("学习", "教育", "学校", "课程", "培训", "考试", "考研", "考公", "留学", "证书", "技能"),
        "情感生活" to listOf("恋爱", "爱情", "感情", "情侣", "表白", "单恋", "暗恋", "失恋", "分手", "挽回"),
        "婚姻家庭" to listOf("婚姻", "家庭", "结婚", "离婚", "夫妻", "子女", "婆媳", "亲子", "家人", "彩礼"),
        "身心健康" to listOf("健康", "医疗", "身体", "心理", "疾病", "治疗", "保健", "锻炼", "运动", "饮食"),
        "休闲娱乐" to listOf("娱乐", "休闲", "爱好", "兴趣", "电影", "音乐", "游戏", "旅游", "度假", "放松"),
        "住房生活" to listOf("住房", "房子", "房价", "租房", "买房", "装修", "家居", "物业", "搬家", "地段"),
        "金融理财" to listOf("理财", "投资", "金融", "存款", "贷款", "基金", "股票", "房贷", "信用卡", "保险"),
        "时事政治" to listOf("政治", "时事", "新闻", "国际", "国内", "社会", "法律", "政策", "民生", "热点"),
        "科技数码" to listOf("科技", "数码", "互联网", "手机", "电脑", "软件", "人工智能", "AI", "智能", "物联网"),
        "环境保护" to listOf("环保", "环境", "气候", "污染", "碳中和", "低碳", "可持续", "生态", "节能", "减排"),
        "个人成长" to listOf("成长", "自我提升", "个人发展", "目标", "规划", "习惯", "时间管理", "效率", "自律", "意志力")
    )

    /**
     * 提取MBTI标签
     * @param content 要分析的文本内容
     * @return MBTI相关标签列表
     */
    fun extractMBTITags(content: String): List<UserProfileSystem.UserTag> {
        val tags = mutableListOf<UserProfileSystem.UserTag>()
        val lowerContent = content.lowercase()

        // 分词处理内容
        val tokens = hanlpTokenizer.tokenize(lowerContent)

        // 尝试匹配完整的MBTI类型
        for ((mbtiType, keywords) in mbtiKeywords) {
            var matchCount = 0
            val matchedKeywords = mutableListOf<String>()

            for (keyword in keywords) {
                if (tokens.contains(keyword) || lowerContent.contains(keyword)) {
                    matchCount++
                    matchedKeywords.add(keyword)
                }
            }

            // 如果匹配了多个关键词，添加MBTI标签
            if (matchCount >= 2) {
                val confidence = (0.3f + matchCount * 0.05f).coerceAtMost(0.9f)
                val tag = UserProfileSystem.UserTag(
                    tagName = mbtiType,
                    category = UserProfileSystem.TAG_CATEGORY_MBTI,
                    confidence = confidence,
                    evidence = extractEvidence(content, matchedKeywords)
                )
                tags.add(tag)
            }
        }

        // 匹配四个维度
        extractMBTIDimensionTags(tokens, lowerContent, mbtiEIKeywords, UserProfileSystem.TAG_CATEGORY_MBTI_EI, tags, content)
        extractMBTIDimensionTags(tokens, lowerContent, mbtiSNKeywords, UserProfileSystem.TAG_CATEGORY_MBTI_SN, tags, content)
        extractMBTIDimensionTags(tokens, lowerContent, mbtiTFKeywords, UserProfileSystem.TAG_CATEGORY_MBTI_TF, tags, content)
        extractMBTIDimensionTags(tokens, lowerContent, mbtiJPKeywords, UserProfileSystem.TAG_CATEGORY_MBTI_JP, tags, content)

        return tags
    }

    /**
     * 辅助函数：提取MBTI维度标签
     * @param tokens 分词结果
     * @param lowerContent 小写处理后的内容
     * @param dimensionKeywords 维度关键词映射
     * @param category 标签类别
     * @param tags 标签列表（用于添加结果）
     * @param originalContent 原始内容（用于提取证据）
     */
    private fun extractMBTIDimensionTags(
        tokens: List<String>,
        lowerContent: String,
        dimensionKeywords: Map<String, List<String>>,
        category: String,
        tags: MutableList<UserProfileSystem.UserTag>,
        originalContent: String
    ) {
        for ((dimension, keywords) in dimensionKeywords) {
            var matchCount = 0
            val matchedKeywords = mutableListOf<String>()

            for (keyword in keywords) {
                if (tokens.contains(keyword) || lowerContent.contains(keyword)) {
                    matchCount++
                    matchedKeywords.add(keyword)
                }
            }

            // 如果匹配了多个关键词，添加维度标签
            if (matchCount >= 2) {
                val confidence = (0.3f + matchCount * 0.05f).coerceAtMost(0.85f)
                val tag = UserProfileSystem.UserTag(
                    tagName = dimension,
                    category = category,
                    confidence = confidence,
                    evidence = extractEvidence(originalContent, matchedKeywords)
                )
                tags.add(tag)
            }
        }
    }

    /**
     * 从消息列表中提取用户标签
     * @param messages 用户消息列表
     * @return 提取的标签列表
     */
    fun extractTagsFromMessages(messages: List<ChatMessage>): List<UserProfileSystem.UserTag> {
        val tags = mutableListOf<UserProfileSystem.UserTag>()

        // 合并所有消息内容
        val allContent = messages.joinToString(" ") {
            when (it.content) {
                is String -> it.content
                else -> it.content.toString()
            }
        }

        // 更新文档计数
        totalDocuments++

        // 使用HanLP提取关键词
        val keywords = HanLP.extractKeyword(allContent, 30)
        val extractedKeywords = keywords.map {
            it.lowercase() to (hanlpTokenizer.tokenize(allContent).count { token -> token == it }.toFloat() /
                    hanlpTokenizer.tokenize(allContent).size)
        }.toMap()

        Log.d(TAG, "HanLP提取关键词: ${keywords.take(10).joinToString(", ")}")

        // 使用通用标签提取函数处理各类标签
        tags.addAll(extractTagsGeneric(allContent, interestKeywords, UserProfileSystem.TAG_CATEGORY_INTEREST, 0.3f, 0.9f, extractedKeywords))
        tags.addAll(extractTagsGeneric(allContent, knowledgeKeywords, UserProfileSystem.TAG_CATEGORY_KNOWLEDGE, 0.2f, 0.8f, extractedKeywords))
        tags.addAll(extractTagsGeneric(allContent, personalityKeywords, UserProfileSystem.TAG_CATEGORY_PERSONALITY, 0.2f, 0.8f, extractedKeywords))
        tags.addAll(extractTagsGeneric(allContent, communicationKeywords, UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.3f, 0.9f, extractedKeywords))
        tags.addAll(extractTagsGeneric(allContent, emotionKeywords, UserProfileSystem.TAG_CATEGORY_EMOTION, 0.2f, 0.8f, extractedKeywords))
        tags.addAll(extractTagsGeneric(allContent, valueKeywords, UserProfileSystem.TAG_CATEGORY_VALUE, 0.2f, 0.8f, extractedKeywords))
        tags.addAll(extractTagsGeneric(allContent, subcultureKeywords, UserProfileSystem.TAG_CATEGORY_SUBCULTURE, 0.3f, 0.9f, extractedKeywords))
        tags.addAll(extractTagsGeneric(allContent, topicKeywords, UserProfileSystem.TAG_CATEGORY_TOPIC, 0.3f, 0.85f, extractedKeywords))

        // 添加MBTI标签提取
        tags.addAll(extractMBTITags(allContent))

        return tags
    }

    /**
     * 通用标签提取函数
     */
    private fun extractTagsGeneric(
        content: String,
        keywordsMap: Map<String, List<String>>,
        category: String,
        baseConfidence: Float = 0.3f,
        maxConfidence: Float = 0.9f,
        extractedKeywords: Map<String, Float> = emptyMap(),
        confidenceMultiplier: Float = 0.7f
    ): List<UserProfileSystem.UserTag> {
        val tags = mutableListOf<UserProfileSystem.UserTag>()
        val lowerContent = content.lowercase()

        // 分词处理内容
        val tokens = hanlpTokenizer.tokenize(lowerContent)
        // 更新词频统计
        tokens.forEach { token ->
            wordFrequency[token] = (wordFrequency[token] ?: 0) + 1
        }

        // 按标签类型处理
        for ((tagName, keywords) in keywordsMap) {
            var matchCount = 0
            var weightedMatchScore = 0.0f
            val matchedKeywords = mutableListOf<String>()

            for (keyword in keywords) {
                val keywordLower = keyword.lowercase()

                // 使用HanLP判断是否匹配
                val isMatch = tokens.contains(keywordLower) ||
                        hanlpTokenizer.isCompleteWord(lowerContent, keywordLower)

                if (isMatch) {
                    matchCount++
                    matchedKeywords.add(keyword)

                    // 使用HanLP提取的关键词权重，如果有的话
                    val keywordWeight = extractedKeywords[keywordLower]?.let {
                        it * 3.0f  // 提升HanLP认为重要的词的权重
                    } ?: run {
                        // 否则计算TF-IDF值作为权重
                        val tf = tokens.count { it == keywordLower }.toFloat() / tokens.size
                        val idf = if (wordFrequency[keywordLower] != null && wordFrequency[keywordLower]!! > 0) {
                            kotlin.math.log10(totalDocuments.toFloat() / wordFrequency[keywordLower]!!)
                        } else {
                            1.0f
                        }

                        tf * idf * (1.0f + (keyword.length - 2) * 0.1f).coerceAtLeast(0.5f)
                    }

                    weightedMatchScore += keywordWeight
                }
            }

            if (matchCount > 0) {
                // 计算置信度: 使用加权匹配分数
                val normalizedScore = weightedMatchScore / keywords.size
                val confidence = minOf(baseConfidence + normalizedScore * confidenceMultiplier, maxConfidence)

                // 创建标签
                val tag = UserProfileSystem.UserTag(
                    tagName = tagName,
                    category = category,
                    confidence = confidence,
                    evidence = extractEvidence(content, matchedKeywords)
                )

                tags.add(tag)
            }
        }

        return tags
    }

    /**
     * 从内容中提取支持标签的证据
     */
    private fun extractEvidence(content: String, keywords: List<String>): String {
        val maxLength = 100
        val sentences = content.split(Regex("[.。!！?？;；\n]"))

        for (sentence in sentences) {
            for (keyword in keywords) {
                if (sentence.lowercase().contains(keyword.lowercase())) {
                    // 找到包含关键词的句子，截取适当长度作为证据
                    val trimmedSentence = sentence.trim()
                    return if (trimmedSentence.length <= maxLength) {
                        trimmedSentence
                    } else {
                        // 如果句子太长，截取关键词周围的部分
                        val index = trimmedSentence.lowercase().indexOf(keyword.lowercase())
                        val start = maxOf(0, index - 40)
                        val end = minOf(trimmedSentence.length, index + keyword.length + 40)
                        "..." + trimmedSentence.substring(start, end) + "..."
                    }
                }
            }
        }

        return ""
    }

    /**
     * 从对话中提取反馈信号，用于动态调整标签置信度
     * @return 三元组列表(标签名, 类别, 调整值)
     */
    fun extractFeedbackSignals(
        userMessage: String,
        aiResponse: String
    ): List<Triple<String, String, Float>> {
        val signals = mutableListOf<Triple<String, String, Float>>()
        val lowerMessage = userMessage.lowercase()

        // 使用HanLP分词处理文本，更准确地识别关键词
        val tokens = hanlpTokenizer.tokenize(lowerMessage)
        val extractedKeywords = HanLP.extractKeyword(lowerMessage, 10)
            .map { it.lowercase() }

        // 检测沟通风格偏好
        val communicationPreferences = mapOf(
            "更简洁" to Triple("简洁", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.1f),
            "更详细" to Triple("详细", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.1f),
            "直接点" to Triple("直接", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.1f),
            "专业点" to Triple("正式", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.1f),
            "简单点" to Triple("口语化", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.1f),
            "轻松点" to Triple("口语化", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.1f),
            "幽默点" to Triple("幽默", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.1f),
            "整活" to Triple("整活", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.15f),
            "抽象" to Triple("整活", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.15f),
            "别整活" to Triple("严肃", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.15f),
            "严肃点" to Triple("严肃", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.1f)
        )

        // 检查沟通偏好表达，使用更准确的匹配
        for ((pattern, triple) in communicationPreferences) {
            // 使用词边界检查，确保完整匹配
            if (pattern in tokens || lowerMessage.contains(pattern)) {
                signals.add(triple)
            }
        }

        // 检查网络语言和表情包使用
        val networkLanguagePatterns = listOf(
            "xswl", "yyds", "绝绝子", "awsl", "dbq", "emo", "蚌埠住了", "绷不住", "破防"
        )

        var networkLanguageCount = tokens.count { it in networkLanguagePatterns }

        if (networkLanguageCount > 0) {
            signals.add(Triple("网络用语", UserProfileSystem.TAG_CATEGORY_COMMUNICATION, 0.1f))
        }

        // 检查是否表达了特定领域的关注，使用HanLP提取的关键词
        for ((interest, keywords) in interestKeywords) {
            var matchCount = 0
            var totalWeight = 0.0f

            for (keyword in keywords) {
                val keywordLower = keyword.lowercase()
                if (keywordLower in tokens || keywordLower in extractedKeywords) {
                    matchCount++

                    // 关键词权重 - 在HanLP提取的关键词中权重更高
                    val weight = if (keywordLower in extractedKeywords) 1.5f else 1.0f
                    totalWeight += weight
                }
            }

            if (matchCount >= 2 || totalWeight >= 2.0f) {
                // 需要多个关键词匹配或较高的总权重来增强兴趣标签
                val confidence = minOf(0.1f + (totalWeight * 0.05f), 0.3f)
                signals.add(Triple(interest, UserProfileSystem.TAG_CATEGORY_INTEREST, confidence))
            }
        }

        // 检查亚文化表达，使用更精确的匹配
        for ((subculture, keywords) in subcultureKeywords) {
            var matchCount = 0
            var specialTermCount = 0 // 特殊术语计数

            for (keyword in keywords) {
                val keywordLower = keyword.lowercase()

                // 优先检查HanLP分词结果和提取的关键词
                val isMatch = keywordLower in tokens || keywordLower in extractedKeywords

                if (isMatch) {
                    matchCount++
                    if (keyword.length >= 3 || keywordLower in extractedKeywords) {
                        specialTermCount++
                    }
                }
            }

            if (matchCount >= 2 || specialTermCount >= 1) {
                // 多个关键词匹配或有特殊术语出现，提高亚文化标签置信度
                val baseConfidence = 0.12f
                val specialTermBonus = specialTermCount * 0.03f
                val confidence = minOf(baseConfidence + specialTermBonus, 0.25f)
                signals.add(Triple(subculture, UserProfileSystem.TAG_CATEGORY_SUBCULTURE, confidence))
            }
        }

        return signals
    }

    /**
     * 分析文本情感
     * @param text 要分析的文本
     * @return 主要情感类别，如无法确定则返回null
     */
    fun analyzeEmotion(text: String): String? {
        // 使用HanLP分词器处理文本
        val tokens = hanlpTokenizer.tokenize(text.lowercase())

        // 提取关键词，可能包含重要的情感词
        val keywords = hanlpTokenizer.extractKeywords(text, 10)
            .map { it.first.lowercase() }

        // 结合分词和关键词的结果
        val allTokens = (tokens + keywords).distinct()

        // 情感分数映射
        val emotionScores = mutableMapOf<String, Float>()

        // 累计情感词分数
        for ((emotion, keywords) in emotionKeywords) {
            var score = 0.0f
            for (keyword in keywords) {
                val keywordLower = keyword.lowercase()
                if (keywordLower in allTokens) {
                    score += 0.3f  // 基础分数

                    // 检查情感强度修饰词
                    val intensifiers = listOf("非常", "很", "极其", "太", "真", "好", "特别", "格外")
                    for (intensifier in intensifiers) {
                        if (text.contains("$intensifier$keyword")) {
                            score += 0.2f  // 强度修饰加分
                            break
                        }
                    }
                }
            }

            // 考虑情感词的情感极性
            val emotionWords = allTokens.filter { emotionSentimentMap.containsKey(it) }
            for (word in emotionWords) {
                val sentiment = emotionSentimentMap[word] ?: continue
                // 根据情感极性调整分数
                when {
                    emotion == "快乐" && sentiment > 0.5f -> score += 0.2f
                    emotion == "忧郁" && sentiment < -0.5f -> score += 0.2f
                    emotion == "愤怒" && sentiment < -0.6f -> score += 0.2f
                    emotion == "焦虑" && sentiment < -0.4f -> score += 0.2f
                    emotion == "平静" && abs(sentiment) < 0.4f -> score += 0.2f
                }
            }

            if (score > 0) {
                emotionScores[emotion] = score
            }
        }

        // 情感极性分析结果
        var positiveScore = 0.0f
        var negativeScore = 0.0f

        for (word in allTokens) {
            val sentiment = emotionSentimentMap[word] ?: continue
            if (sentiment > 0) {
                positiveScore += sentiment
            } else if (sentiment < 0) {
                negativeScore += abs(sentiment)  // 取绝对值
            }
        }

        // 如果有明显的情感极性，添加基础情感类别
        if (positiveScore > 0.5f && positiveScore > negativeScore * 1.5f) {
            emotionScores["积极"] = positiveScore
        } else if (negativeScore > 0.5f && negativeScore > positiveScore * 1.5f) {
            if (text.contains("担心") || text.contains("害怕") ||
                text.contains("紧张") || text.contains("焦虑")) {
                emotionScores["焦虑"] = negativeScore
            } else if (text.contains("生气") || text.contains("愤怒") ||
                text.contains("烦躁") || text.contains("不爽")) {
                emotionScores["愤怒"] = negativeScore
            } else {
                emotionScores["忧郁"] = negativeScore
            }
        }

        // 返回得分最高的情感
        return emotionScores.maxByOrNull { it.value }?.key
    }

    /**
     * 提取文本中的主题
     * @param text 要分析的文本
     * @return 主题及其置信度列表
     */
    fun extractTopics(text: String): List<Pair<String, Float>> {
        val topics = mutableListOf<Pair<String, Float>>()
        val lowerText = text.lowercase()

        // 使用HanLP分词和关键词提取
        val tokens = hanlpTokenizer.tokenize(lowerText)
        val extractedKeywords = hanlpTokenizer.extractKeywords(text, 15)
            .map { it.first.lowercase() }

        // 优先考虑HanLP提取的关键词
        val priorityTokens = (tokens + extractedKeywords).distinct()

        // 统计主题关键词
        for ((topic, keywords) in topicKeywords) {
            var score = 0.0f
            var keywordCount = 0
            val matchedKeywords = mutableSetOf<String>()

            for (keyword in keywords) {
                val keywordLower = keyword.lowercase()

                // 优先检查HanLP提取的关键词中是否含有该关键词
                val inExtractedKeywords = keywordLower in extractedKeywords
                // 其次检查分词结果
                val inTokens = keywordLower in tokens

                if (inExtractedKeywords || inTokens) {
                    keywordCount++
                    matchedKeywords.add(keyword)

                    // HanLP关键词权重更高
                    val keywordWeight = if (inExtractedKeywords) 2.0f else 1.0f
                    score += keywordWeight
                }
            }

            // 分析主题相关度
            if (keywordCount >= 2 || score >= 2.0f) {
                // 计算置信度，考虑多因素
                val lengthScore = minOf(text.length / 200.0f, 1.0f) * 0.2f // 文本长度加分
                val keywordRatio = keywordCount.toFloat() / keywords.size // 关键词覆盖率
                val confidence = minOf(0.3f + score * 0.1f + lengthScore + keywordRatio * 0.2f, 0.9f)

                topics.add(Pair(topic, confidence))
            }
        }

        // 仅返回置信度较高的前5个主题
        return topics.sortedByDescending { it.second }.take(5)
    }
}

/**
 * 数据库助手的扩展
 * 用于标签存储
 */
class UserTagDatabaseHelper(private val dbHelper: ChatDatabaseHelper) {

    /**
     * 保存用户标签
     */
    fun saveUserTag(tagEntity: UserTagEntity) {
        val db = dbHelper.writableDatabase

        val values = android.content.ContentValues().apply {
            put("chat_id", tagEntity.chatId)
            put("tag_name", tagEntity.tagName)
            put("category", tagEntity.category)
            put("confidence", tagEntity.confidence)
            put("evidence", tagEntity.evidence)
            put("created_at", tagEntity.createdAt.time)
            put("updated_at", tagEntity.updatedAt.time)
        }

        // 尝试更新，如果不存在则插入
        val rowsAffected = db.update(
            "user_tags",
            values,
            "chat_id = ? AND tag_name = ? AND category = ?",
            arrayOf(tagEntity.chatId, tagEntity.tagName, tagEntity.category)
        )

        if (rowsAffected == 0) {
            db.insert("user_tags", null, values)
        }
    }

    /**
     * 获取用户标签
     */
    fun getUserTags(chatId: String): List<UserTagEntity> {
        val tags = mutableListOf<UserTagEntity>()
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            "user_tags",
            null,
            "chat_id = ?",
            arrayOf(chatId),
            null,
            null,
            "confidence DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val tagEntity = UserTagEntity(
                    chatId = it.getString(it.getColumnIndexOrThrow("chat_id")),
                    tagName = it.getString(it.getColumnIndexOrThrow("tag_name")),
                    category = it.getString(it.getColumnIndexOrThrow("category")),
                    confidence = it.getFloat(it.getColumnIndexOrThrow("confidence")),
                    evidence = it.getString(it.getColumnIndexOrThrow("evidence")),
                    createdAt = Date(it.getLong(it.getColumnIndexOrThrow("created_at"))),
                    updatedAt = Date(it.getLong(it.getColumnIndexOrThrow("updated_at")))
                )
                tags.add(tagEntity)
            }
        }

        return tags
    }

    /**
     * 删除用户标签
     */
    fun deleteUserTags(chatId: String) {
        val db = dbHelper.writableDatabase
        db.delete("user_tags", "chat_id = ?", arrayOf(chatId))
    }
}

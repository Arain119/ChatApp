package com.example.chatapp.feedback

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.chatapp.data.db.ChatDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * 反馈仓库
 * 负责存储和检索反馈数据
 */
class FeedbackRepository(private val context: Context) {

    private val TAG = "FeedbackRepository"
    private val dbHelper = ChatDatabaseHelper.getInstance(context)

    /**
     * 保存反馈到数据库
     */
    suspend fun saveFeedback(feedback: FeedbackEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase

            val values = ContentValues().apply {
                put("id", feedback.id)
                put("chat_id", feedback.chatId)
                put("message_id", feedback.messageId)
                put("user_message_id", feedback.userMessageId)
                put("feedback_type", feedback.feedbackType)
                put("confidence", feedback.confidence)
                put("content", feedback.content)
                put("aspects", feedback.aspects)
                put("keywords", feedback.keywords)
                put("timestamp", feedback.timestamp.time)
                put("processed", if (feedback.processed) 1 else 0)
            }

            val result = db.insertWithOnConflict(
                "user_feedback",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )

            Log.d(TAG, "保存反馈: ${feedback.feedbackType}, 结果: ${result != -1L}")
            return@withContext result != -1L
        } catch (e: Exception) {
            Log.e(TAG, "保存反馈失败: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * 获取特定聊天的所有反馈
     */
    suspend fun getFeedbackForChat(chatId: String): List<FeedbackEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FeedbackEntity>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "user_feedback",
                null,
                "chat_id = ?",
                arrayOf(chatId),
                null,
                null,
                "timestamp DESC"
            )

            cursor.use {
                while (it.moveToNext()) {
                    results.add(it.toFeedbackEntity())
                }
            }

            Log.d(TAG, "获取聊天反馈: chatId=$chatId, 数量=${results.size}")
            return@withContext results
        } catch (e: Exception) {
            Log.e(TAG, "获取聊天反馈失败: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * 获取特定消息的反馈
     */
    suspend fun getFeedbackForMessage(messageId: String): FeedbackEntity? = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "user_feedback",
                null,
                "message_id = ?",
                arrayOf(messageId),
                null,
                null,
                null,
                "1" // 限制只返回一个结果
            )

            var result: FeedbackEntity? = null
            cursor.use {
                if (it.moveToFirst()) {
                    result = it.toFeedbackEntity()
                }
            }

            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "获取消息反馈失败: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * 获取用户反馈统计
     */
    suspend fun getUserFeedbackStats(chatId: String): UserFeedbackStats = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase

            // 获取正负面反馈数量
            var positiveCount = 0
            var negativeCount = 0

            val countQuery = """
                SELECT 
                    COUNT(CASE WHEN feedback_type = 'POSITIVE' THEN 1 END) as positive_count,
                    COUNT(CASE WHEN feedback_type = 'NEGATIVE' THEN 1 END) as negative_count
                FROM user_feedback 
                WHERE chat_id = ?
            """

            db.rawQuery(countQuery, arrayOf(chatId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    positiveCount = cursor.getInt(0)
                    negativeCount = cursor.getInt(1)
                }
            }

            // 获取各方面的评分
            val aspectScores = getAspectScores(db, chatId)

            // 获取偏好的风格
            val preferredStyles = getPreferredStyles(db, chatId)

            // 获取需要避免的特征
            val avoidedFeatures = getAvoidedFeatures(db, chatId)

            return@withContext UserFeedbackStats(
                chatId = chatId,
                positiveCount = positiveCount,
                negativeCount = negativeCount,
                aspectScores = aspectScores,
                preferredStyles = preferredStyles,
                avoidedFeatures = avoidedFeatures
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取用户反馈统计失败: ${e.message}", e)
            return@withContext UserFeedbackStats(
                chatId = chatId,
                positiveCount = 0,
                negativeCount = 0,
                aspectScores = emptyMap(),
                preferredStyles = emptyList(),
                avoidedFeatures = emptyList()
            )
        }
    }

    /**
     * 获取各方面的评分
     */
    private fun getAspectScores(db: SQLiteDatabase, chatId: String): Map<String, Float> {
        val aspectScores = mutableMapOf<String, Float>()
        val aspectCounts = mutableMapOf<String, Int>()

        try {
            val query = """
                SELECT aspects, feedback_type, confidence 
                FROM user_feedback 
                WHERE chat_id = ?
            """

            db.rawQuery(query, arrayOf(chatId)).use { cursor ->
                while (cursor.moveToNext()) {
                    val aspects = cursor.getString(0).split(",")
                    val feedbackType = cursor.getString(1)
                    val confidence = cursor.getFloat(2)

                    // 根据反馈类型确定分数
                    val score = when (feedbackType) {
                        "POSITIVE" -> confidence
                        "NEGATIVE" -> -confidence
                        else -> 0f
                    }

                    // 累计每个方面的分数和计数
                    for (aspect in aspects) {
                        if (aspect.isBlank()) continue

                        val currentScore = aspectScores.getOrDefault(aspect, 0f) + score
                        aspectScores[aspect] = currentScore
                        aspectCounts[aspect] = aspectCounts.getOrDefault(aspect, 0) + 1
                    }
                }
            }

            // 计算平均分并归一化
            val normalizedScores = mutableMapOf<String, Float>()
            for ((aspect, score) in aspectScores) {
                val count = aspectCounts[aspect] ?: 1
                val avgScore = score / count

                // 将-1到1的范围归一化到0到1
                normalizedScores[aspect] = (avgScore + 1) / 2
            }

            return normalizedScores
        } catch (e: Exception) {
            Log.e(TAG, "获取方面评分失败: ${e.message}", e)
            return emptyMap()
        }
    }

    /**
     * 获取偏好的语言风格
     */
    private fun getPreferredStyles(db: SQLiteDatabase, chatId: String): List<String> {
        val styleScores = mutableMapOf<String, Int>()

        try {
            // 从正面反馈中提取偏好风格
            val query = """
                SELECT keywords, content
                FROM user_feedback 
                WHERE chat_id = ? AND feedback_type = 'POSITIVE'
            """

            db.rawQuery(query, arrayOf(chatId)).use { cursor ->
                while (cursor.moveToNext()) {
                    val keywords = cursor.getString(0).split(",")
                    val content = cursor.getString(1).lowercase()

                    // 关键词匹配
                    for (keyword in keywords) {
                        when (keyword.trim().lowercase()) {
                            "详细" -> styleScores["详细"] = (styleScores["详细"] ?: 0) + 1
                            "简洁" -> styleScores["简洁"] = (styleScores["简洁"] ?: 0) + 1
                            "专业" -> styleScores["专业"] = (styleScores["专业"] ?: 0) + 1
                            "通俗" -> styleScores["通俗"] = (styleScores["通俗"] ?: 0) + 1
                            "易懂" -> styleScores["易懂"] = (styleScores["易懂"] ?: 0) + 1
                        }
                    }

                    // 内容匹配
                    if (content.contains("详细") || content.contains("全面")) {
                        styleScores["详细"] = (styleScores["详细"] ?: 0) + 1
                    }
                    if (content.contains("简洁") || content.contains("简短")) {
                        styleScores["简洁"] = (styleScores["简洁"] ?: 0) + 1
                    }
                    if (content.contains("专业") || content.contains("术语")) {
                        styleScores["专业"] = (styleScores["专业"] ?: 0) + 1
                    }
                    if (content.contains("通俗") || content.contains("易懂")) {
                        styleScores["通俗"] = (styleScores["通俗"] ?: 0) + 1
                    }
                    if (content.contains("生动") || content.contains("有趣")) {
                        styleScores["生动"] = (styleScores["生动"] ?: 0) + 1
                    }
                    if (content.contains("条理") || content.contains("清晰")) {
                        styleScores["条理清晰"] = (styleScores["条理清晰"] ?: 0) + 1
                    }
                }
            }

            // 排序并返回前三个风格
            return styleScores.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }
        } catch (e: Exception) {
            Log.e(TAG, "获取偏好风格失败: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * 获取需要避免的特征
     */
    private fun getAvoidedFeatures(db: SQLiteDatabase, chatId: String): List<String> {
        val featureScores = mutableMapOf<String, Int>()

        try {
            // 从负面反馈中提取需要避免的特征
            val query = """
                SELECT keywords, content
                FROM user_feedback 
                WHERE chat_id = ? AND feedback_type = 'NEGATIVE'
            """

            db.rawQuery(query, arrayOf(chatId)).use { cursor ->
                while (cursor.moveToNext()) {
                    val keywords = cursor.getString(0).split(",")
                    val content = cursor.getString(1).lowercase()

                    // 关键词匹配
                    for (keyword in keywords) {
                        when (keyword.trim().lowercase()) {
                            "啰嗦" -> featureScores["啰嗦"] = (featureScores["啰嗦"] ?: 0) + 1
                            "复杂" -> featureScores["复杂"] = (featureScores["复杂"] ?: 0) + 1
                            "不准确" -> featureScores["不准确"] = (featureScores["不准确"] ?: 0) + 1
                            "不理解" -> featureScores["难以理解"] = (featureScores["难以理解"] ?: 0) + 1
                        }
                    }

                    // 内容匹配
                    if (content.contains("太长") || content.contains("太多")) {
                        featureScores["冗长"] = (featureScores["冗长"] ?: 0) + 1
                    }
                    if (content.contains("太短") || content.contains("不够详细")) {
                        featureScores["过于简略"] = (featureScores["过于简略"] ?: 0) + 1
                    }
                    if (content.contains("看不懂") || content.contains("太专业")) {
                        featureScores["术语过多"] = (featureScores["术语过多"] ?: 0) + 1
                    }
                    if (content.contains("不对") || content.contains("错误")) {
                        featureScores["事实错误"] = (featureScores["事实错误"] ?: 0) + 1
                    }
                    if (content.contains("举例") || content.contains("例子")) {
                        featureScores["缺少示例"] = (featureScores["缺少示例"] ?: 0) + 1
                    }
                }
            }

            // 排序并返回前三个特征
            return featureScores.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }
        } catch (e: Exception) {
            Log.e(TAG, "获取需避免特征失败: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * 从Cursor转换为FeedbackEntity
     */
    private fun Cursor.toFeedbackEntity(): FeedbackEntity {
        return FeedbackEntity(
            id = getString(getColumnIndexOrThrow("id")),
            chatId = getString(getColumnIndexOrThrow("chat_id")),
            messageId = getString(getColumnIndexOrThrow("message_id")),
            userMessageId = getString(getColumnIndexOrThrow("user_message_id")),
            feedbackType = getString(getColumnIndexOrThrow("feedback_type")),
            confidence = getFloat(getColumnIndexOrThrow("confidence")),
            content = getString(getColumnIndexOrThrow("content")),
            aspects = getString(getColumnIndexOrThrow("aspects")),
            keywords = getString(getColumnIndexOrThrow("keywords")),
            timestamp = Date(getLong(getColumnIndexOrThrow("timestamp"))),
            processed = getInt(getColumnIndexOrThrow("processed")) == 1
        )
    }
}
package com.example.chatapp.feedback

import android.content.Context
import android.util.Log
import androidx.collection.LruCache
import com.example.chatapp.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

/**
 * 反馈管理器
 * 整合反馈分析、存储和优化
 */
class FeedbackManager(private val context: Context) {

    private val TAG = "FeedbackManager"
    private val feedbackAnalyzer = FeedbackAnalyzer(context)
    private val feedbackRepository = FeedbackRepository(context)
    private val feedbackOptimizer = FeedbackOptimizer(context)

    // 缓存最近的AI消息，用于反馈分析
    private val recentAiMessages = LruCache<String, Message>(20)

    /**
     * 分析并处理用户消息中的反馈
     */
    suspend fun processUserMessage(
        userMessage: Message,
        chatId: String
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            // 获取该聊天最近的AI消息
            val recentAiMessage = recentAiMessages.get(chatId) ?: return@withContext false

            // 分析用户消息是否包含反馈
            val feedbackResult = feedbackAnalyzer.analyzeFeedback(
                userMessage.content,
                recentAiMessage.content
            )

            // 如果反馈类型是中性或置信度低，则不处理
            if (feedbackResult.type == FeedbackType.NEUTRAL || feedbackResult.confidence < 0.4f) {
                return@withContext false
            }

            Log.d(TAG, "检测到反馈: ${feedbackResult.type}, 置信度: ${feedbackResult.confidence}")

            // 创建反馈实体
            val feedbackEntity = FeedbackEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                messageId = recentAiMessage.id,
                userMessageId = userMessage.id,
                feedbackType = feedbackResult.type.name,
                confidence = feedbackResult.confidence,
                content = feedbackResult.content,
                aspects = feedbackResult.aspects.joinToString(","),
                keywords = feedbackResult.keywords.joinToString(","),
                timestamp = Date()
            )

            // 保存到数据库
            val saveSuccess = feedbackRepository.saveFeedback(feedbackEntity)

            // 日志记录
            Log.d(TAG, "保存反馈${if (saveSuccess) "成功" else "失败"}: " +
                    "${feedbackResult.type}, 方面: ${feedbackResult.aspects}")

            return@withContext saveSuccess
        } catch (e: Exception) {
            Log.e(TAG, "处理用户反馈失败: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * 记录AI消息，用于后续反馈分析
     */
    fun recordAiMessage(aiMessage: Message, chatId: String) {
        recentAiMessages.put(chatId, aiMessage)
        Log.d(TAG, "记录AI消息: chatId=$chatId, messageId=${aiMessage.id}")
    }

    /**
     * 根据用户反馈生成优化的提示
     */
    suspend fun getOptimizedPrompt(chatId: String): String {
        return try {
            feedbackOptimizer.generateOptimizedPrompt(chatId)
        } catch (e: Exception) {
            Log.e(TAG, "获取优化提示失败: ${e.message}", e)
            "请尽力提供有用的回答。"
        }
    }

    /**
     * 根据用户反馈统计优化回复
     */
    suspend fun optimizeResponse(
        chatId: String,
        originalResponse: String,
        userQuery: String
    ): String {
        return try {
            feedbackOptimizer.optimizeResponse(chatId, originalResponse, userQuery)
        } catch (e: Exception) {
            Log.e(TAG, "优化回复失败: ${e.message}", e)
            originalResponse
        }
    }

    /**
     * 获取用户反馈统计
     */
    suspend fun getUserFeedbackStats(chatId: String): UserFeedbackStats {
        return try {
            feedbackRepository.getUserFeedbackStats(chatId)
        } catch (e: Exception) {
            Log.e(TAG, "获取用户反馈统计失败: ${e.message}", e)
            UserFeedbackStats(
                chatId, 0, 0, emptyMap(), emptyList(), emptyList()
            )
        }
    }

    /**
     * 获取显式反馈
     * 用于显式反馈按钮，如点赞/踩
     */
    suspend fun submitExplicitFeedback(
        chatId: String,
        aiMessageId: String,
        userMessageId: String,
        isPositive: Boolean,
        aspect: String = "整体表现"
    ): Boolean {
        try {
            // 创建反馈实体
            val feedbackEntity = FeedbackEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                messageId = aiMessageId,
                userMessageId = userMessageId,  // 使用AI消息ID作为用户消息ID，因为这是显式反馈
                feedbackType = if (isPositive) FeedbackType.POSITIVE.name else FeedbackType.NEGATIVE.name,
                confidence = 1.0f,  // 显式反馈具有最高置信度
                content = if (isPositive) "用户显式点赞" else "用户显式点踩",
                aspects = aspect,
                keywords = if (isPositive) "喜欢,赞" else "不喜欢,踩",
                timestamp = Date()
            )

            // 保存到数据库
            return feedbackRepository.saveFeedback(feedbackEntity)
        } catch (e: Exception) {
            Log.e(TAG, "提交显式反馈失败: ${e.message}", e)
            return false
        }
    }
}
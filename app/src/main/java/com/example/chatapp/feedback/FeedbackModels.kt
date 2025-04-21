package com.example.chatapp.feedback

import java.util.Date
import java.util.UUID

/**
 * 反馈类型枚举
 */
enum class FeedbackType {
    POSITIVE,    // 正面反馈(奖励)
    NEGATIVE,    // 负面反馈(惩罚)
    NEUTRAL      // 中性或无反馈
}

/**
 * 反馈分析结果
 */
data class FeedbackResult(
    val type: FeedbackType,
    val confidence: Float,         // 置信度(0-1)
    val aspects: List<String>,     // 反馈涉及的方面(风格、信息、逻辑等)
    val keywords: List<String>,    // 反馈中的关键词
    val content: String            // 原始反馈文本
)

/**
 * 用户反馈实体 - 数据库模型
 */
data class FeedbackEntity(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String,                // 关联的聊天ID
    val messageId: String,             // 被反馈的AI消息ID
    val userMessageId: String,         // 包含反馈的用户消息ID
    val feedbackType: String,          // 反馈类型: 积极、消极、中性
    val confidence: Float,             // 置信度(0-1)
    val content: String,               // 反馈内容
    val aspects: String,               // 反馈涉及的方面
    val keywords: String,              // 关键词
    val timestamp: Date = Date(),      // 反馈时间
    val processed: Boolean = false     // 是否已处理
)

/**
 * 用户反馈统计
 */
data class UserFeedbackStats(
    val chatId: String,                // 关联的聊天ID
    val positiveCount: Int,            // 正面反馈总数
    val negativeCount: Int,            // 负面反馈总数
    val aspectScores: Map<String, Float>, // 各方面的评分(0-1)
    val preferredStyles: List<String>, // 偏好的语言风格
    val avoidedFeatures: List<String>  // 不喜欢的特征
)
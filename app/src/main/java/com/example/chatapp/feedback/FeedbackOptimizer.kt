package com.example.chatapp.feedback

import android.content.Context
import android.util.Log
import com.example.chatapp.api.ApiClient
import com.example.chatapp.api.ChatGptRequest
import com.example.chatapp.api.ChatMessage
import com.example.chatapp.data.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 反馈优化器
 * 基于用户反馈优化AI回复
 */
class FeedbackOptimizer(private val context: Context) {

    private val TAG = "FeedbackOptimizer"
    private val feedbackRepository = FeedbackRepository(context)

    /**
     * 生成针对特定用户优化的系统提示
     */
    suspend fun generateOptimizedPrompt(chatId: String): String {
        try {
            // 获取用户反馈统计
            val feedbackStats = feedbackRepository.getUserFeedbackStats(chatId)

            // 如果没有足够的反馈数据，返回默认提示
            if (feedbackStats.positiveCount + feedbackStats.negativeCount < 3) {
                return "请尽力提供有用的回答。"
            }

            // 构建优化的提示
            return buildPromptFromStats(feedbackStats)
        } catch (e: Exception) {
            Log.e(TAG, "生成优化提示失败: ${e.message}", e)
            return "请尽力提供有用的回答。"
        }
    }

    /**
     * 根据反馈统计构建优化提示
     */
    private fun buildPromptFromStats(stats: UserFeedbackStats): String {
        val sb = StringBuilder()

        // 基础定义
        sb.append("请根据用户反馈不断优化回答。")

        // 根据反馈比例添加总体评价
        val totalFeedbacks = stats.positiveCount + stats.negativeCount
        if (totalFeedbacks > 0) {
            val positiveRatio = stats.positiveCount.toFloat() / totalFeedbacks

            when {
                positiveRatio > 0.8 -> sb.append("用户对你的回答整体满意度很高。")
                positiveRatio < 0.5 -> sb.append("用户认为你的回答有一定的改进空间。")
                else -> sb.append("用户对你的回答满意度中等。")
            }
        }

        // 添加语言风格偏好
        if (stats.preferredStyles.isNotEmpty()) {
            sb.append("\n\n请使用以下用户喜欢的表达风格:")
            stats.preferredStyles.forEach { style ->
                sb.append("\n- $style")
            }
        }

        // 添加需要避免的特征
        if (stats.avoidedFeatures.isNotEmpty()) {
            sb.append("\n\n避免以下用户不喜欢的表达方式:")
            stats.avoidedFeatures.forEach { feature ->
                sb.append("\n- $feature")
            }
        }

        // 添加各方面的具体指导
        sb.append("\n\n根据用户反馈，请注意以下几点:")

        for ((aspect, score) in stats.aspectScores) {
            when (aspect) {
                "准确性" -> {
                    if (score > 0.7) {
                        sb.append("\n- 用户很看重信息的准确性，确保提供精确的信息和事实。")
                    } else if (score < 0.4) {
                        sb.append("\n- 用户反馈信息准确性不足，请特别注意核对事实和数据。")
                    }
                }
                "表达方式" -> {
                    if (score > 0.7) {
                        sb.append("\n- 用户喜欢你的表达方式，保持清晰易懂的解释。")
                    } else if (score < 0.4) {
                        sb.append("\n- 用户反馈表达不够清晰，请使用更通俗的语言并增加示例。")
                    }
                }
                "有用性" -> {
                    if (score > 0.7) {
                        sb.append("\n- 用户重视实用性，继续提供可直接应用的建议和解决方案。")
                    } else if (score < 0.4) {
                        sb.append("\n- 用户期待更实用的内容，确保回答有实际应用价值。")
                    }
                }
                "语言风格" -> {
                    if (score > 0.7) {
                        sb.append("\n- 用户喜欢你的语言风格，保持当前风格。")
                    } else if (score < 0.4) {
                        sb.append("\n- 用户对语言风格有意见，尝试更自然友好的表达。")
                    }
                }
                "简洁性" -> {
                    if (score > 0.7) {
                        sb.append("\n- 用户喜欢简洁的回答，避免冗余内容，直接切入重点。")
                    } else if (score < 0.4) {
                        sb.append("\n- 用户反馈回答过于冗长，请更加简洁明了。")
                    }
                }
                "整体表现" -> {
                    if (score > 0.7) {
                        sb.append("\n- 用户对你的整体表现满意，保持当前水平。")
                    } else if (score < 0.4) {
                        sb.append("\n- 用户对你的整体表现期待更多，请提升回答质量。")
                    }
                }
            }
        }

        // 确保回复符合人设
        sb.append("\n\n请始终确保回复符合人设。")

        return sb.toString()
    }

    /**
     * 根据用户反馈优化回复
     */
    suspend fun optimizeResponse(
        chatId: String,
        originalResponse: String,
        userQuery: String
    ): String = withContext(Dispatchers.IO) {
        try {
            // 获取反馈统计
            val feedbackStats = feedbackRepository.getUserFeedbackStats(chatId)

            // 如果反馈数据不足，直接返回原始回复
            if (feedbackStats.positiveCount + feedbackStats.negativeCount < 3) {
                return@withContext originalResponse
            }

            // 如果原始回复较短，直接返回，避免过度优化短回复
            if (originalResponse.length < 50) {
                return@withContext originalResponse
            }

            // 构建优化提示
            val optimizationPrompt = """
                根据用户偏好优化以下回复:
                
                用户问题: $userQuery
                
                原始回复: $originalResponse
                
                用户偏好:
                ${formatUserPreferences(feedbackStats)}
                
                请保留原始回复的核心信息，但调整表达方式和风格，使回复更符合用户偏好。
                不要添加任何元数据或说明，直接给出优化后的回复。
            """.trimIndent()

            // 调用API优化
            val optimizedResponse = callApiForOptimization(optimizationPrompt)

            // 如果API调用失败或返回空，使用原始回复
            if (optimizedResponse.isNullOrBlank()) {
                return@withContext originalResponse
            }

            return@withContext optimizedResponse
        } catch (e: Exception) {
            Log.e(TAG, "优化回复失败: ${e.message}", e)
            return@withContext originalResponse
        }
    }

    /**
     * 格式化用户偏好信息
     */
    private fun formatUserPreferences(stats: UserFeedbackStats): String {
        val sb = StringBuilder()

        // 添加偏好风格
        if (stats.preferredStyles.isNotEmpty()) {
            sb.append("偏好风格: ${stats.preferredStyles.joinToString(", ")}\n")
        }

        // 添加需要避免的特征
        if (stats.avoidedFeatures.isNotEmpty()) {
            sb.append("避免特征: ${stats.avoidedFeatures.joinToString(", ")}\n")
        }

        // 添加各方面评分
        sb.append("各方面评分:\n")
        for ((aspect, score) in stats.aspectScores) {
            sb.append("- $aspect: $score\n")
        }

        return sb.toString()
    }

    /**
     * 调用API优化回复
     */
    private suspend fun callApiForOptimization(prompt: String): String? {
        try {
            // 创建API请求消息
            val messages = listOf(
                ChatMessage("system", "你是一个根据用户偏好优化回复的专家。"),
                ChatMessage("user", prompt)
            )

            // 构建请求
            val request = ChatGptRequest(
                model = ApiConfig.getMemoryModelName(context),
                messages = messages,
                temperature = 0.7,
                maxTokens = 1000
            )

            // 发送请求
            val response = ApiClient.apiService.sendMessage(
                ApiClient.getAuthHeader(),
                request
            )

            // 处理响应
            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body()!!
                val assistantMessage = responseBody.choices.firstOrNull()?.message?.content

                // 返回优化后的回复
                return assistantMessage?.toString()
            } else {
                Log.e(TAG, "API请求失败: ${response.code()} ${response.message()}")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "API调用异常: ${e.message}", e)
            return null
        }
    }
}
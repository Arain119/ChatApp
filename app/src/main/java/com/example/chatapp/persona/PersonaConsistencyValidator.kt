package com.example.chatapp.persona

import android.content.Context
import android.util.Log
import com.example.chatapp.api.ApiClient
import com.example.chatapp.api.ChatGptRequest
import com.example.chatapp.api.ChatMessage
import com.example.chatapp.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 人设一致性验证器
 * 确保AI回复符合角色设定
 */
class PersonaConsistencyValidator(private val context: Context) {
    private val TAG = "PersonaConsistencyValidator"
    private val settingsManager = SettingsManager(context)

    /**
     * 验证AI回复的人设一致性
     * @param userMessage 用户消息
     * @param aiResponse 初始AI回复
     * @param persona 当前人设
     * @return 修正后的回复
     */
    suspend fun validateAndCorrect(
        userMessage: String,
        aiResponse: String,
        persona: String
    ): String = withContext(Dispatchers.IO) {
        try {
            // 快速检查 - 基于规则的启发式验证
            val quickResult = performQuickValidation(aiResponse, persona)

            // 如果快速检查通过，直接返回原始回复
            if (quickResult.isConsistent) {
                return@withContext aiResponse
            }

            // 否则进行深度验证和修正
            val correctionResult = performDeepValidationAndCorrection(
                userMessage,
                aiResponse,
                persona,
                quickResult.issues
            )

            // 如果修正成功，返回修正后的回复，否则返回原始回复
            return@withContext correctionResult ?: aiResponse

        } catch (e: Exception) {
            Log.e(TAG, "人设一致性验证失败: ${e.message}", e)
            return@withContext aiResponse // 出错时返回原始回复
        }
    }

    /**
     * 执行快速验证
     * 基于规则的验证
     */
    private fun performQuickValidation(
        aiResponse: String,
        persona: String
    ): ValidationResult {
        val issues = mutableListOf<String>()
        val lowerResponse = aiResponse.lowercase()

        // 检查是否承认自己是AI
        if (lowerResponse.contains("我是ai") ||
            lowerResponse.contains("作为ai") ||
            lowerResponse.contains("我是人工智能") ||
            lowerResponse.contains("作为人工智能") ||
            lowerResponse.contains("我是一个语言模型") ||
            lowerResponse.contains("我没有个人") ||
            lowerResponse.contains("作为一个虚拟助手")) {
            issues.add("AI身份泄露")
        }

        // 检查语气是否符合人设
        val aiName = settingsManager.aiName
        if (aiName.isNotEmpty() && !lowerResponse.contains(aiName.lowercase()) &&
            (lowerResponse.contains("抱歉") || lowerResponse.contains("我无法"))) {
            issues.add("标准AI回避语")
        }

        // 检查是否使用了过于机械的表达
        if (lowerResponse.contains("我被设计为") ||
            lowerResponse.contains("我被编程为") ||
            lowerResponse.contains("我的功能不包括")) {
            issues.add("机械表达")
        }

        return ValidationResult(
            isConsistent = issues.isEmpty(),
            issues = issues
        )
    }

    /**
     * 执行深度验证和修正
     */
    private suspend fun performDeepValidationAndCorrection(
        userMessage: String,
        aiResponse: String,
        persona: String,
        issues: List<String>
    ): String? {
        // 构建验证和修正提示
        val correctionPrompt = """
        # 人设一致性验证与修正
        
        ## 原始人设
        $persona
        
        ## 用户消息
        $userMessage
        
        ## AI回复
        $aiResponse
        
        ## 检测到的问题
        ${issues.joinToString("\n") { "- $it" }}
        
        请重写上述回复，确保符合角色设定，避免以上问题。保持大致相同的信息内容，但使用与角色一致的语气、态度和知识背景。
        修正后的回复：
        """.trimIndent()

        // 构建API请求
        val messages = listOf(
            ChatMessage("system", "你是一个专注于修正角色扮演一致性的助手。只输出修正后的回复，不要解释或添加其他内容。"),
            ChatMessage("user", correctionPrompt)
        )

        // 使用SettingsManager获取模型类型
        val modelType = settingsManager.modelType
        val request = ChatGptRequest(
            model = modelType,  // 使用用户设置的模型类型
            messages = messages,
            temperature = 0.9
        )

        // 发送API请求 - 使用聊天API的Key
        try {
            val response = ApiClient.apiService.sendMessage(
                ApiClient.getAuthHeader(), // 使用默认的聊天API验证
                request
            )

            if (response.isSuccessful && response.body() != null) {
                val correctedResponse = response.body()?.choices?.firstOrNull()?.message?.content?.toString()

                if (!correctedResponse.isNullOrEmpty()) {
                    Log.d(TAG, "人设一致性修正完成，修正以下问题: ${issues.joinToString()}")
                    return correctedResponse
                }
            }

            Log.e(TAG, "人设修正请求失败: ${response.code()}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "人设修正请求异常: ${e.message}", e)
            return null
        }
    }

    /**
     * 验证结果数据类
     */
    data class ValidationResult(
        val isConsistent: Boolean,
        val issues: List<String>
    )
}

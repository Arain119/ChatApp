package com.example.chatapp.persona

import android.content.Context
import android.util.Log
import com.example.chatapp.api.ApiClient
import com.example.chatapp.api.ChatGptRequest
import com.example.chatapp.api.ChatMessage
import com.example.chatapp.data.ApiConfig
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.data.db.ChatDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 人设提示工程器
 * 优化人设提示词
 */
class PersonaPromptEngineer(private val context: Context) {
    private val TAG = "PersonaPromptEngineer"
    private val dbHelper = ChatDatabaseHelper.getInstance(context)
    private val settingsManager = SettingsManager(context)

    // 存储成功的提示模式和权重
    private val promptPatternScores = ConcurrentHashMap<String, Float>()

    // 人设长度限制
    companion object {
        private const val MAX_PERSONA_LENGTH = 2000 // 最大人设长度
    }

    /**
     * 为手动编辑的人设进行增强
     */
    suspend fun enhanceManualPersona(basePersona: String): String = withContext(Dispatchers.IO) {
        if (basePersona.length > 150) {
            // 人设已经足够详细，不需要增强
            return@withContext basePersona
        }

        try {
            // 构建人设增强提示，添加明确的长度限制
            val enhancementPrompt = """
            请增强以下角色设定，使其更加生动、详细和有深度，添加必要的背景故事、性格特点和表达风格：
            
            $basePersona
            
            请按以下格式提供完整角色设定：
            1. 角色简介：[简短描述角色的身份和背景]
            2. 个性特点：[详细描述角色的性格、习惯和行为模式]
            3. 交流风格：[描述角色的说话方式、用词习惯和语气特点]
            4. 背景故事：[提供能够支撑人设的背景故事和经历]
            5. 核心价值观：[描述角色的信念和价值观]
            
            总字数控制在400-650字之间，确保简明而有深度。
            仅输出增强后的完整角色设定，不要包含前导说明或分析。
            """.trimIndent()

            // 构建API请求
            val messages = listOf(
                ChatMessage("system", "你是一个专业的角色设定增强专家，擅长将简单的角色描述转化为丰富、立体的角色设定。"),
                ChatMessage("user", enhancementPrompt)
            )

            // 使用记忆模型
            val modelType = ApiConfig.getMemoryModelName(context)
            val request = ChatGptRequest(
                model = modelType, // 使用记忆模型
                messages = messages,
                temperature = 0.7 // 适当的创造力
            )

            // 发送API请求
            val apiKey = ApiConfig.getMemoryApiKey(context)
            val response = ApiClient.apiService.sendMessage(
                "Bearer $apiKey",
                request
            )

            if (response.isSuccessful && response.body() != null) {
                val enhancedPersona = response.body()?.choices?.firstOrNull()?.message?.content?.toString()

                if (!enhancedPersona.isNullOrEmpty()) {
                    // 检查长度，如果超过限制则截断
                    val finalPersona = if (enhancedPersona.length > MAX_PERSONA_LENGTH) {
                        Log.d(TAG, "人设长度(${enhancedPersona.length})超过限制($MAX_PERSONA_LENGTH)，进行截断")
                        enhancedPersona.substring(0, MAX_PERSONA_LENGTH - 100) +
                                "\n\n[由于长度限制，部分内容已省略]"
                    } else {
                        enhancedPersona
                    }

                    Log.d(TAG, "手动人设增强成功，长度从 ${basePersona.length} 增加到 ${finalPersona.length}")
                    return@withContext finalPersona
                }
            }

            Log.e(TAG, "手动人设增强请求失败: ${response.code()}")
            return@withContext basePersona // 失败时返回原始人设
        } catch (e: Exception) {
            Log.e(TAG, "手动人设增强失败: ${e.message}", e)
            return@withContext basePersona // 出错时返回原始人设
        }
    }

    /**
     * 创建初始人设
     */
    suspend fun createInitialPersona(basePersona: String): String = withContext(Dispatchers.IO) {
        // 如果基础人设已经很详细，直接返回
        if (basePersona.length > 150) {
            return@withContext basePersona
        }

        try {
            // 构建初始人设提示，添加明确的长度限制
            val initialPrompt = """
            请基于以下简短的角色描述，创建一个完整的、有深度的角色设定：
            
            $basePersona
            
            请确保角色设定包含以下元素：
            1. 基本身份和背景
            2. 详细的性格特点
            3. 独特的说话方式和语言习惯
            4. 价值观和行为准则
            5. 个人经历或背景故事
            
            角色设定应当具有一致性、深度和个性，同时避免过度复杂。
            总字数控制在400-650字之间，确保简明而有深度。
            只输出完整的角色设定，不要包含解释或其他内容。
            """.trimIndent()

            // 构建API请求
            val messages = listOf(
                ChatMessage("system", "你是一个专业的角色设定创作专家，擅长将简单的角色描述转化为丰富、立体的角色设定。"),
                ChatMessage("user", initialPrompt)
            )

            // 使用记忆模型
            val modelType = ApiConfig.getMemoryModelName(context)
            val request = ChatGptRequest(
                model = modelType, // 使用记忆模型
                messages = messages,
                temperature = 0.8 // 适当的创造力
            )

            // 发送API请求 - 使用记忆API密钥
            val apiKey = ApiConfig.getMemoryApiKey(context)
            val response = ApiClient.apiService.sendMessage(
                "Bearer $apiKey",
                request
            )

            if (response.isSuccessful && response.body() != null) {
                val enhancedPersona = response.body()?.choices?.firstOrNull()?.message?.content?.toString()

                if (!enhancedPersona.isNullOrEmpty()) {
                    // 检查长度，如果超过限制则截断
                    val finalPersona = if (enhancedPersona.length > MAX_PERSONA_LENGTH) {
                        Log.d(TAG, "人设长度(${enhancedPersona.length})超过限制($MAX_PERSONA_LENGTH)，进行截断")
                        enhancedPersona.substring(0, MAX_PERSONA_LENGTH - 100) +
                                "\n\n[由于长度限制，部分内容已省略]"
                    } else {
                        enhancedPersona
                    }

                    Log.d(TAG, "初始人设创建成功，长度从 ${basePersona.length} 增加到 ${finalPersona.length}")
                    return@withContext finalPersona
                }
            }

            Log.e(TAG, "初始人设创建请求失败: ${response.code()}")
            return@withContext basePersona // 失败时返回原始人设
        } catch (e: Exception) {
            Log.e(TAG, "初始人设创建失败: ${e.message}", e)
            return@withContext basePersona // 出错时返回原始人设
        }
    }

    /**
     * 基于会话历史智能优化人设提示
     */
    suspend fun optimizePersonaPrompt(
        chatId: String,
        basePersona: String
    ): String = withContext(Dispatchers.Default) {
        try {
            // 获取人设记忆
            val personaMemories = dbHelper.getPersonaMemoriesForChat(chatId)

            // 如果记忆太少，返回原始人设
            if (personaMemories.isEmpty()) return@withContext basePersona

            // 提取记忆内容用于增强
            val memoryContents = personaMemories
                .sortedByDescending { it.importance }
                .take(10) // 限制使用的记忆数量
                .joinToString("\n") { "- ${it.content}" }

            // 构建人设优化提示，添加明确的长度限制
            val optimizationPrompt = """
            请基于以下原始角色设定和从对话中提取的角色特征，创建一个完善的角色设定：
            
            ## 原始角色设定
            $basePersona
            
            ## 从对话中提取的角色特征
            $memoryContents
            
            请创建一个整合了原始设定和提取特征的完整角色设定，使角色更加丰满、一致和生动。
            增强版角色设定应保留原设定的核心特征，同时自然融入新发现的特征和行为模式。
            总字数不要超过800字，确保简明扼要但内容完整。
            只输出完整的角色设定，不要包含解释或其他内容。
            """.trimIndent()

            // 构建API请求
            val messages = listOf(
                ChatMessage("system", "你是一个专业的角色设定优化专家，擅长整合角色设定与实际对话中展现的特征。"),
                ChatMessage("user", optimizationPrompt)
            )

            // 使用记忆模型
            val modelType = ApiConfig.getMemoryModelName(context)
            val request = ChatGptRequest(
                model = modelType, // 使用记忆模型
                messages = messages,
                temperature = 0.5
            )

            // 发送API请求 - 使用记忆API密钥
            val apiKey = ApiConfig.getMemoryApiKey(context)
            val response = ApiClient.apiService.sendMessage(
                "Bearer $apiKey",
                request
            )

            if (response.isSuccessful && response.body() != null) {
                val optimizedPersona = response.body()?.choices?.firstOrNull()?.message?.content?.toString()

                if (!optimizedPersona.isNullOrEmpty()) {
                    // 如果增强结果太短或有问题，返回原始人设
                    if (optimizedPersona.length < basePersona.length * 0.8 ||
                        optimizedPersona.contains("作为AI") ||
                        optimizedPersona.contains("我无法") ||
                        optimizedPersona.contains("无法提供")) {
                        Log.d(TAG, "优化人设质量不佳，使用原始人设")
                        return@withContext basePersona
                    }

                    // 检查长度，如果超过限制则截断
                    val finalPersona = if (optimizedPersona.length > MAX_PERSONA_LENGTH) {
                        Log.d(TAG, "优化人设长度(${optimizedPersona.length})超过限制($MAX_PERSONA_LENGTH)，进行截断")
                        optimizedPersona.substring(0, MAX_PERSONA_LENGTH - 100) +
                                "\n\n[由于长度限制，部分内容已省略]"
                    } else {
                        optimizedPersona
                    }

                    Log.d(TAG, "人设提示成功优化，长度从 ${basePersona.length} 增加到 ${finalPersona.length}")
                    return@withContext finalPersona
                }
            }

            Log.e(TAG, "优化人设请求失败: ${response.code()}")
            return@withContext basePersona

        } catch (e: Exception) {
            Log.e(TAG, "人设提示优化失败: ${e.message}", e)
            return@withContext basePersona
        }
    }

    /**
     * 从导入聊天记录中提取的特征生成完整人设
     * @param chatId 聊天ID
     * @param basePersona 原始人设
     * @return 生成的完整人设
     */
    suspend fun generatePersonaFromImported(chatId: String, basePersona: String = ""): String = withContext(Dispatchers.IO) {
        try {
            // 获取所有提取的人设记忆
            val personaMemories = dbHelper.getPersonaMemoriesForChat(chatId)

            if (personaMemories.isEmpty()) {
                Log.d(TAG, "没有找到人设记忆，无法生成人设")
                return@withContext basePersona
            }

            // 如果记忆过多，只取最重要的一部分
            val MAX_MEMORIES_TO_USE = 25 // 生成人设时使用的最大记忆数量
            val memoriesToUse = if (personaMemories.size > MAX_MEMORIES_TO_USE) {
                Log.d(TAG, "记忆过多(${personaMemories.size})，只使用最重要的${MAX_MEMORIES_TO_USE}条")
                personaMemories.sortedByDescending { it.importance }.take(MAX_MEMORIES_TO_USE)
            } else {
                personaMemories
            }

            // 提取记忆内容，按重要性排序
            val traitContents = memoriesToUse.joinToString("\n") {
                "- ${it.content}"
            }

            // 构建生成提示
            val generationPrompt = """
        基于以下从聊天记录中提取的角色特征，创建一个完整、连贯的角色设定：
        
        ## 提取的特征:
        $traitContents
        
        ${if (basePersona.isNotEmpty()) "## 原始人设参考:\n$basePersona\n" else ""}
        
        请创建一个详细的角色设定，包括：
        1. 角色简介：简短描述角色的身份和背景
        2. 个性特点：详细描述角色的性格、习惯和行为模式
        3. 交流风格：描述角色的说话方式、用词习惯和语气特点
        4. 核心价值观：描述角色的信念和价值观
        
        角色设定应自然地融合所有提取的特征，创造一个连贯、有深度的角色形象。
        请将设定控制在1000字以内，重点突出最关键的特征。
        仅输出完整的角色设定，不要包含前导说明或分析。
        """.trimIndent()

            // 构建API请求
            val messages = listOf(
                ChatMessage("system", "你是一个专业的角色设定创作专家，擅长根据角色特征创建立体、生动的角色设定。"),
                ChatMessage("user", generationPrompt)
            )

            // 使用记忆模型
            val modelName = ApiConfig.getMemoryModelName(context)
            val request = ChatGptRequest(
                model = modelName,
                messages = messages,
                temperature = 0.7
            )

            // 发送API请求
            val apiKey = ApiConfig.getMemoryApiKey(context)
            val response = ApiClient.apiService.sendMessage(
                "Bearer $apiKey",
                request
            )

            if (response.isSuccessful && response.body() != null) {
                val generatedPersona = response.body()?.choices?.firstOrNull()?.message?.content?.toString()

                if (!generatedPersona.isNullOrEmpty()) {
                    // 检查生成结果质量
                    if (generatedPersona.contains("作为AI") ||
                        generatedPersona.contains("我无法") ||
                        generatedPersona.contains("无法提供") ||
                        generatedPersona.length < 200) {
                        Log.d(TAG, "生成人设质量不佳，使用原始人设或简单整合")

                        // 如果有原始人设，直接返回
                        if (basePersona.isNotEmpty()) {
                            return@withContext basePersona
                        }

                        // 否则尝试简单整合
                        return@withContext "根据聊天记录提取的角色特征：\n\n${
                            memoriesToUse.take(15).joinToString("\n") {
                                "- ${it.content}"
                            }
                        }"
                    }

                    Log.d(TAG, "成功从导入记录生成人设，长度: ${generatedPersona.length} 字符")
                    return@withContext generatedPersona
                }
            }

            Log.e(TAG, "从导入记录生成人设失败: ${response.code()}")

            // 失败时如果有原始人设则返回，否则返回一个简单整合
            if (basePersona.isNotEmpty()) {
                return@withContext basePersona
            } else {
                return@withContext "根据聊天记录提取的角色特征：\n\n${
                    memoriesToUse.take(15).joinToString("\n") {
                        "- ${it.content}"
                    }
                }"
            }

        } catch (e: Exception) {
            Log.e(TAG, "从导入记录生成人设失败: ${e.message}", e)
            return@withContext basePersona
        }
    }
}

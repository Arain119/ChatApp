package com.example.chatapp.persona

import android.content.Context
import android.util.Log
import com.example.chatapp.api.ChatGptRequest
import com.example.chatapp.api.ChatGptResponse
import com.example.chatapp.api.ChatMessage
import com.example.chatapp.data.ApiConfig
import com.example.chatapp.data.db.ChatDatabaseHelper
import com.example.chatapp.data.db.PersonaMemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.delay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * 人设记忆系统 - 负责提取和管理角色相关的记忆
 */
class PersonaMemorySystem(private val context: Context) {
    private val TAG = "PersonaMemorySystem"
    private val dbHelper = ChatDatabaseHelper.getInstance(context)

    // 增加记忆数量限制常量
    companion object {
        // 每个会话保留的最大记忆数量
        private const val MAX_MEMORIES_PER_CHAT = 40
    }

    // 重试配置
    private val MAX_RETRIES = 2  // 最大重试次数
    private val RETRY_DELAY_MS = 1000L // 初始重试延迟

    // API客户端配置
    private var retrofitInstance: Retrofit? = null
    private var apiServiceInstance: MemoryApiService? = null

    // API服务接口
    private interface MemoryApiService {
        @POST("chat/completions")
        suspend fun generateMemory(
            @Header("Authorization") authorization: String,
            @Body request: ChatGptRequest
        ): Response<ChatGptResponse>
    }

    // 初始化API客户端 - 使用ApiConfig中的配置
    private fun getApiService(timeoutSeconds: Int = 60): MemoryApiService {
        if (apiServiceInstance == null) {
            val apiUrl = ApiConfig.getMemoryApiUrl(context)
            Log.d(TAG, "初始化记忆API服务 - URL: $apiUrl")

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()

            retrofitInstance = Retrofit.Builder()
                .baseUrl(apiUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiServiceInstance = retrofitInstance!!.create(MemoryApiService::class.java)
        }

        return apiServiceInstance!!
    }

    /**
     * 插入人设记忆并维持记忆数量限制
     */
    suspend fun insertPersonaMemory(memory: PersonaMemoryEntity) = withContext(Dispatchers.IO) {
        // 先保存新记忆
        dbHelper.insertPersonaMemory(memory)
        Log.d(TAG, "插入人设记忆: chatId=${memory.chatId}, 内容=${memory.content.take(50)}...")

        // 检查并维护记忆数量限制
        maintainMemoryLimit(memory.chatId)
    }

    /**
     * 确保记忆数量不超过限制
     */
    private suspend fun maintainMemoryLimit(chatId: String) {
        // 获取当前所有记忆
        val allMemories = dbHelper.getPersonaMemoriesForChat(chatId)

        // 如果记忆数量超过限制，删除多余记忆
        if (allMemories.size > MAX_MEMORIES_PER_CHAT) {
            // 按重要性和时间排序，优先保留重要的和新的记忆
            val memoriesToKeep = allMemories
                .sortedWith(
                    compareByDescending<PersonaMemoryEntity> { it.importance }
                        .thenByDescending { it.timestamp }
                )
                .take(MAX_MEMORIES_PER_CHAT)

            // 找出要删除的记忆
            val memoriesToDelete = allMemories - memoriesToKeep.toSet()

            // 删除多余记忆
            for (toDelete in memoriesToDelete) {
                dbHelper.deletePersonaMemory(toDelete.id)
            }

            Log.d(TAG, "已删除 ${memoriesToDelete.size} 条记忆以保持数量限制，当前保留 ${memoriesToKeep.size} 条")
        }
    }

    /**
     * 分析消息并提取人设相关的记忆
     */
    suspend fun analyzeAndExtractPersonaMemories(
        messages: List<ChatMessage>,
        chatId: String
    ) = withContext(Dispatchers.IO) {
        if (messages.size < 4) return@withContext // 消息太少，不进行分析

        try {
            // 构建人设分析请求 - 改进提示，明确限制输出数量和避免重复
            val analysisPrompt = """
            分析以下对话，提取与AI角色相关的重要信息（最多3-5条不重复的发现）：
            
            ${messages.joinToString("\n") { "${it.role}: ${it.content}" }}
            
            提取：
            1. 角色透露的个人信息、喜好或背景
            2. 角色与用户建立的关系
            3. 角色展示的情感或态度
            
            仅提取与角色本身相关的信息，忽略通用知识。
            以JSON格式返回：{"memories":[{"content":"...", "importance":1-10, "type":"character_trait"}]}
            确保输出简洁，避免重复内容。如果没有相关信息，返回空数组。
            """.trimIndent()

            // 构建API请求
            val requestMessages = listOf(
                ChatMessage("system", "你是一个专注于从对话中提取角色特征的分析助手，请简明扼要地回答。"),
                ChatMessage("user", analysisPrompt)
            )

            // 使用配置的记忆模型
            val modelName = ApiConfig.getMemoryModelName(context)
            val request = ChatGptRequest(
                model = modelName,
                messages = requestMessages,
                temperature = 0.3 // 低温度使输出更加确定性
            )

            // 初始化重试变量
            var retryCount = 0

            while (retryCount <= MAX_RETRIES) {
                try {
                    // 获取配置的API密钥
                    val apiKey = ApiConfig.getMemoryApiKey(context)

                    if (retryCount > 0) {
                        Log.d(TAG, "API请求重试中，尝试次数：${retryCount}/${MAX_RETRIES}")
                    }

                    // 使用内置API服务发送请求
                    val apiService = getApiService(30) // 使用30秒超时
                    val response = apiService.generateMemory(
                        "Bearer $apiKey",
                        request
                    )

                    if (response.isSuccessful && response.body() != null) {
                        // 解析响应内容
                        val responseText = response.body()?.choices?.firstOrNull()?.message?.content?.toString()
                        if (!responseText.isNullOrEmpty()) {
                            // 解析JSON响应
                            val memories = parseMemoriesFromResponse(responseText)

                            if (memories.isNotEmpty()) {
                                // 存储提取的人设记忆 - 正常对话场景，使用常规处理
                                for (memory in memories) {
                                    val memoryEntity = PersonaMemoryEntity(
                                        id = UUID.randomUUID().toString(),
                                        chatId = chatId,
                                        content = memory.content,
                                        importance = memory.importance,
                                        timestamp = Date(),
                                        type = memory.type
                                    )
                                    insertPersonaMemory(memoryEntity) // 使用新方法保存并维护记忆数量
                                }
                                Log.d(TAG, "提取并保存了 ${memories.size} 条人设记忆")
                            } else {
                                Log.d(TAG, "未从响应中提取到有效记忆")
                            }

                            // 成功处理，退出循环
                            break
                        }
                    } else {
                        val errorCode = response.code()
                        val errorBody = response.errorBody()?.string() ?: "未知错误"
                        Log.e(TAG, "分析API请求失败: $errorCode - $errorBody")

                        // 判断是否应该重试 - 只有在服务器繁忙时才重试
                        if (errorCode == 503 || errorBody.contains("too busy") || errorBody.contains("System is too busy")) {
                            if (retryCount < MAX_RETRIES) {
                                retryCount++
                                val delayTime = RETRY_DELAY_MS * (1 shl (retryCount - 1))
                                Log.d(TAG, "服务忙，${delayTime}ms后重试(${retryCount}/${MAX_RETRIES})")
                                delay(delayTime)
                                continue
                            }
                        }

                        // 达到最大重试次数或非服务忙错误，记录并跳过本次操作
                        Log.d(TAG, "API请求失败，将等待下次再试")
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "人设记忆提取失败: ${e.message}", e)

                    // 增加重试次数
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        val delayTime = RETRY_DELAY_MS * (1 shl (retryCount - 1))
                        Log.d(TAG, "发生异常，${delayTime}ms后重试(${retryCount}/${MAX_RETRIES})")
                        delay(delayTime)
                    } else {
                        // 超过重试次数，记录并跳过本次操作
                        Log.d(TAG, "重试失败，将等待下次再试")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "人设记忆整体处理失败: ${e.message}", e)
            // 不做额外处理，等待下次再试
        }
    }

    /**
     * 从导入的聊天记录中分析和提取AI人设特征
     * 为了处理可能的大量记忆，采用分批处理和智能聚合策略
     * @param messages 导入的消息列表
     * @param chatId 会话ID
     * @return 是否成功提取特征
     */
    suspend fun analyzeImportedChatForPersona(
        messages: List<ChatMessage>,
        chatId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 只选择AI消息进行分析
            val aiMessages = messages.filter { it.role == "assistant" }

            if (aiMessages.isEmpty()) {
                Log.d(TAG, "没有发现AI消息，无法分析")
                return@withContext false
            }

            // 计划采用分批处理策略
            val totalMessages = aiMessages.size
            Log.d(TAG, "开始分析导入的聊天记录，共 $totalMessages 条AI消息")

            // 根据消息数量决定批次大小和总批次
            val batchSize = when {
                totalMessages > 100 -> 30
                totalMessages > 50 -> 20
                else -> 15
            }

            // 计算需要的批次
            val batchCount = (totalMessages + batchSize - 1) / batchSize
            Log.d(TAG, "将分 $batchCount 批处理，每批 $batchSize 条消息")

            // 采用多批次处理策略
            val allTraits = mutableListOf<MemoryItem>()
            var successfulBatches = 0

            // 多批次并行处理可能导致大量API调用，这里选择串行处理
            for (batchIndex in 0 until batchCount) {
                val start = batchIndex * batchSize
                val end = minOf(start + batchSize, totalMessages)

                // 构建当前批次的消息
                val batchMessages = aiMessages.subList(start, end)
                Log.d(TAG, "处理第 ${batchIndex + 1}/$batchCount 批, 包含 ${batchMessages.size} 条消息")

                // 分析当前批次
                val batchTraits = analyzeMessageBatchForTraits(batchMessages, batchIndex, batchCount)

                if (batchTraits.isNotEmpty()) {
                    allTraits.addAll(batchTraits)
                    successfulBatches++
                    Log.d(TAG, "第 ${batchIndex + 1} 批处理成功，提取 ${batchTraits.size} 个特征")
                } else {
                    Log.w(TAG, "第 ${batchIndex + 1} 批处理未能提取有效特征")
                    // 即使失败也继续处理后续批次
                }

                // 添加延迟避免API限流
                if (batchIndex < batchCount - 1) {
                    delay(1000)
                }
            }

            // 对大量特征执行聚合和去重
            if (allTraits.size > 20) {
                Log.d(TAG, "提取了 ${allTraits.size} 个原始特征，执行聚合处理...")
                val consolidatedTraits = consolidateTraits(allTraits)
                allTraits.clear()
                allTraits.addAll(consolidatedTraits)
                Log.d(TAG, "聚合后剩余 ${allTraits.size} 个特征")
            }

            // 保存最终特征到数据库
            if (allTraits.isNotEmpty()) {
                // 按重要性排序
                val sortedTraits = allTraits.sortedByDescending { it.importance }

                // 只保存最重要的MAX_MEMORIES_PER_CHAT条特征
                val traitsToSave = sortedTraits.take(MAX_MEMORIES_PER_CHAT)

                // 保存特征
                for (trait in traitsToSave) {
                    val memoryEntity = PersonaMemoryEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        content = trait.content,
                        importance = trait.importance,
                        timestamp = Date(),
                        type = "imported_trait"
                    )
                    // 直接使用dbHelper插入，不需要维护上限(因为已经限制了数量)
                    dbHelper.insertPersonaMemory(memoryEntity)
                    Log.d(TAG, "保存导入人设特征: ${trait.content}, 重要性: ${trait.importance}")
                }

                Log.d(TAG, "完成导入，保存了 ${traitsToSave.size} 条人设特征")

                return@withContext true
            } else {
                Log.w(TAG, "所有批次处理后未能提取有效特征")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "分析导入聊天记录整体失败: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * 分析单批次消息，提取人设特征
     */
    private suspend fun analyzeMessageBatchForTraits(
        batchMessages: List<ChatMessage>,
        batchIndex: Int,
        totalBatches: Int
    ): List<MemoryItem> {
        // 构建深度分析提示
        val analysisPrompt = """
        分析以下AI回复，提取体现的角色特征、说话风格和个性特点：
        
        ${batchMessages.joinToString("\n\n") { "AI: ${it.content}" }}
        
        提取：
        1. 角色的身份和背景信息
        2. 性格特点和价值观
        3. 表达风格和语言习惯
        4. 专业知识领域或特长
        5. 情感表达方式
        
        这是第${batchIndex + 1}批分析，共${totalBatches}批。请提取这批消息特有的特征，不要重复之前批次的内容。
        以JSON格式返回：{"persona_traits":[{"content":"...", "importance":1-10, "type":"character_trait"}]}
        每个特征应当详细具体，不要使用笼统的描述。
        提取5-10个明显的特征。如果没有找到新的特征，返回空数组。
        """.trimIndent()

        // 构建API请求
        val requestMessages = listOf(
            ChatMessage("system", "你是一个专注于分析角色特点的AI分析师，善于从对话中捕捉角色特征。请只返回指定格式的JSON数据，不要包含任何解释。"),
            ChatMessage("user", analysisPrompt)
        )

        // 使用配置的记忆模型
        val modelName = ApiConfig.getMemoryModelName(context)
        val request = ChatGptRequest(
            model = modelName,
            messages = requestMessages,
            temperature = 0.3
        )

        // 获取API密钥
        val apiKey = ApiConfig.getMemoryApiKey(context)

        // 初始化重试变量
        var retryCount = 0
        var result = emptyList<MemoryItem>()

        while (retryCount <= MAX_RETRIES) {
            try {
                // 发送API请求
                val apiService = getApiService(40) // 延长超时时间
                val response = apiService.generateMemory(
                    "Bearer $apiKey",
                    request
                )

                if (response.isSuccessful && response.body() != null) {
                    val responseText = response.body()?.choices?.firstOrNull()?.message?.content?.toString()

                    if (!responseText.isNullOrEmpty()) {
                        // 解析提取的人设特征
                        val traits = parsePersonaTraitsFromResponse(responseText)
                        if (traits.isNotEmpty()) {
                            result = traits
                            break
                        } else {
                            Log.d(TAG, "批次${batchIndex + 1}：未找到有效特征，可能是该批消息缺乏特点")
                            break // 即使没有找到特征也认为是成功的
                        }
                    }
                } else {
                    val errorCode = response.code()
                    val errorBody = response.errorBody()?.string() ?: "未知错误"
                    Log.e(TAG, "批次${batchIndex + 1}分析失败: $errorCode - $errorBody")

                    // 判断是否应该重试
                    if (errorCode == 503 || errorBody.contains("too busy")) {
                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            val delayTime = RETRY_DELAY_MS * (1 shl (retryCount - 1))
                            Log.d(TAG, "服务忙，${delayTime}ms后重试(${retryCount}/${MAX_RETRIES})")
                            delay(delayTime)
                            continue
                        }
                    }

                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "批次${batchIndex + 1}分析异常: ${e.message}", e)

                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    val delayTime = RETRY_DELAY_MS * (1 shl (retryCount - 1))
                    delay(delayTime)
                } else {
                    break
                }
            }
        }

        return result
    }

    /**
     * 聚合和去重特征列表，解决大量导入特征的问题
     */
    private suspend fun consolidateTraits(traits: List<MemoryItem>): List<MemoryItem> {
        // 如果特征数量不多，无需聚合
        if (traits.size <= 20) return traits

        try {
            // 构建特征聚合提示
            val consolidationPrompt = """
            分析以下角色特征列表，合并相似项，移除重复项，保留最重要和最具特色的特征：
            
            ${traits.joinToString("\n") { "- ${it.content} (重要性: ${it.importance})" }}
            
            请执行以下操作：
            1. 合并描述相似概念的特征
            2. 移除完全重复的特征
            3. 为每个特征分配1-10的重要性评分
            4. 保留最具特色和辨识度的特征
            
            以JSON格式返回15-20个最重要的整合特征：
            {"consolidated_traits":[{"content":"合并后的特征描述", "importance":1-10}]}
            """.trimIndent()

            // 构建API请求
            val messages = listOf(
                ChatMessage("system", "你是一个专注于概念聚合和分类的AI分析师，善于识别相似性并提炼核心信息。请只返回指定格式的JSON数据。"),
                ChatMessage("user", consolidationPrompt)
            )

            // 使用配置的记忆模型
            val modelName = ApiConfig.getMemoryModelName(context)
            val request = ChatGptRequest(
                model = modelName,
                messages = messages,
                temperature = 0.3
            )

            // 获取API密钥
            val apiKey = ApiConfig.getMemoryApiKey(context)

            // 尝试API请求
            val apiService = getApiService(60) // 延长超时
            val response = apiService.generateMemory(
                "Bearer $apiKey",
                request
            )

            if (response.isSuccessful && response.body() != null) {
                val responseText = response.body()?.choices?.firstOrNull()?.message?.content?.toString()

                if (!responseText.isNullOrEmpty()) {
                    try {
                        // 验证JSON格式
                        val jsonResponse = ensureValidJson(responseText, "consolidated_traits")
                        val jsonObject = JSONObject(jsonResponse)
                        val traitsArray = jsonObject.optJSONArray("consolidated_traits") ?: JSONArray()

                        val result = mutableListOf<MemoryItem>()
                        for (i in 0 until traitsArray.length()) {
                            val traitObject = traitsArray.getJSONObject(i)
                            result.add(MemoryItem(
                                content = traitObject.getString("content"),
                                importance = traitObject.optInt("importance", 5),
                                type = "consolidated_trait"
                            ))
                        }

                        // 如果整合结果非空，返回整合结果
                        if (result.isNotEmpty()) {
                            Log.d(TAG, "特征聚合成功，从 ${traits.size} 个减少到 ${result.size} 个")
                            return result
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "特征聚合结果解析失败: ${e.message}")
                    }
                }
            }

            // 如果API整合失败，执行本地简单整合（取重要性最高的20个）
            Log.d(TAG, "API特征聚合失败，执行本地简单去重")
            return removeDuplicateMemories(traits).sortedByDescending { it.importance }.take(20)

        } catch (e: Exception) {
            Log.e(TAG, "特征聚合处理失败: ${e.message}")
            // 如果聚合过程失败，返回原始列表中重要性最高的20个
            return traits.sortedByDescending { it.importance }.take(20)
        }
    }

    /**
     * 解析API响应中的人设特征 - 增强的容错处理
     */
    private fun parsePersonaTraitsFromResponse(response: String): List<MemoryItem> {
        return try {
            // 尝试从返回的JSON中提取特征数组
            val validJsonResponse = ensureValidJson(response, "persona_traits")

            val jsonObject = JSONObject(validJsonResponse)
            val traitsArray = jsonObject.optJSONArray("persona_traits") ?: JSONArray()

            // 从数组中提取记忆项，最多15个
            val result = mutableListOf<MemoryItem>()
            for (i in 0 until minOf(traitsArray.length(), 15)) {
                try {
                    val traitObject = traitsArray.getJSONObject(i)
                    result.add(
                        MemoryItem(
                            content = traitObject.getString("content"),
                            importance = traitObject.optInt("importance", 5),
                            type = traitObject.optString("type", "character_trait")
                        )
                    )
                } catch (e: Exception) {
                    // 跳过解析失败的单个特征
                    Log.w(TAG, "解析单个人设特征失败，跳过: ${e.message}")
                }
            }

            // 去重
            removeDuplicateMemories(result)
        } catch (e: Exception) {
            Log.e(TAG, "解析导入人设特征失败: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 解析API响应中的记忆 - 改进的错误处理和容错解析
     */
    private fun parseMemoriesFromResponse(response: String): List<MemoryItem> {
        return try {
            // 确保有效的JSON并提取memories数组
            val validJsonResponse = ensureValidJson(response, "memories")

            val jsonObject = JSONObject(validJsonResponse)
            val memoriesArray = jsonObject.optJSONArray("memories") ?: JSONArray()

            // 从数组中提取记忆项，最多10个以防大量重复
            val result = mutableListOf<MemoryItem>()
            for (i in 0 until minOf(memoriesArray.length(), 10)) {
                try {
                    val memoryObject = memoriesArray.getJSONObject(i)
                    result.add(MemoryItem(
                        content = memoryObject.getString("content"),
                        importance = memoryObject.optInt("importance", 5),
                        type = memoryObject.optString("type", "character_trait")
                    ))
                } catch (e: Exception) {
                    // 跳过解析失败的单个记忆
                    Log.w(TAG, "解析单个记忆项失败，跳过: ${e.message}")
                }
            }

            // 去重
            removeDuplicateMemories(result)
        } catch (e: Exception) {
            Log.e(TAG, "解析记忆失败: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 确保JSON有效的辅助方法
     */
    private fun ensureValidJson(response: String, arrayKey: String): String {
        return try {
            // 尝试解析为JSON对象
            JSONObject(response)
            response
        } catch (e: Exception) {
            Log.w(TAG, "收到无效JSON，尝试修复: ${e.message}")

            // 尝试清理和提取有效的JSON部分
            try {
                // 尝试找到JSON对象的起始和结束
                val jsonPattern = """\{.*\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val match = jsonPattern.find(response)

                if (match != null) {
                    val extractedJson = match.value
                    // 验证提取的部分是否为有效JSON
                    try {
                        JSONObject(extractedJson)
                        return extractedJson
                    } catch (e2: Exception) {
                        Log.e(TAG, "提取的JSON仍然无效: ${e2.message}")
                    }
                }
            } catch (e2: Exception) {
                Log.e(TAG, "JSON提取失败: ${e2.message}")
            }

            // 返回最小有效结构作为后备方案
            """{"$arrayKey":[]}"""
        }
    }

    /**
     * 去除重复记忆的辅助方法
     */
    private fun removeDuplicateMemories(memories: List<MemoryItem>): List<MemoryItem> {
        return memories.distinctBy { "${it.content.take(50).lowercase()}_${it.type}" }
    }

    /**
     * 记忆项数据类
     */
    data class MemoryItem(
        val content: String,
        val importance: Int,
        val type: String
    )

    /**
     * 整合人设记忆，合并相似记忆，移除矛盾记忆
     */
    suspend fun consolidatePersonaMemories(chatId: String) = withContext(Dispatchers.IO) {
        try {
            // 获取所有人设记忆
            val memories = dbHelper.getPersonaMemoriesForChat(chatId)

            if (memories.size <= 5) return@withContext // 记忆太少，不需要整合

            // 构建记忆整合请求
            val consolidationPrompt = """
            分析以下角色相关记忆，找出重复、矛盾或可合并的项：
            
            ${memories.joinToString("\n") { "${it.id}: ${it.content}" }}
            
            请整合这些记忆，移除重复项，解决矛盾，合并相关内容。
            以JSON格式返回：{"consolidatedMemories":[{"ids":["id1","id2"], "newContent":"...", "importance":1-10, "type":"character_trait"}], "removeIds":["id3"]}
            """.trimIndent()

            // 构建API请求
            val messages = listOf(
                ChatMessage("system", "你是一个专注于整理和优化角色记忆的助手，请简明扼要地回答。"),
                ChatMessage("user", consolidationPrompt)
            )

            // 使用配置的记忆模型
            val modelName = ApiConfig.getMemoryModelName(context)
            val request = ChatGptRequest(
                model = modelName,
                messages = messages,
                temperature = 0.3
            )

            // 初始化重试变量
            var retryCount = 0

            while (retryCount <= MAX_RETRIES) {
                try {
                    // 获取配置的API密钥
                    val apiKey = ApiConfig.getMemoryApiKey(context)

                    if (retryCount > 0) {
                        Log.d(TAG, "整合API请求重试中，尝试次数：${retryCount}/${MAX_RETRIES}")
                    }

                    // 使用内置API服务发送请求
                    val apiService = getApiService(40) // 稍长的超时时间
                    val response = apiService.generateMemory(
                        "Bearer $apiKey",
                        request
                    )

                    if (response.isSuccessful && response.body() != null) {
                        // 解析响应内容
                        val responseText = response.body()?.choices?.firstOrNull()?.message?.content?.toString()
                        if (!responseText.isNullOrEmpty()) {
                            // 处理整合结果
                            try {
                                // 确保有效的JSON
                                val validJsonResponse = ensureValidJson(responseText, "consolidatedMemories")
                                val jsonObject = JSONObject(validJsonResponse)

                                // 处理需要合并的记忆
                                val consolidatedArray = jsonObject.optJSONArray("consolidatedMemories") ?: JSONArray()
                                var consolidatedCount = 0

                                for (i in 0 until consolidatedArray.length()) {
                                    try {
                                        val item = consolidatedArray.getJSONObject(i)
                                        val idsArray = item.optJSONArray("ids") ?: continue
                                        val idsToConsolidate = mutableListOf<String>()

                                        for (j in 0 until idsArray.length()) {
                                            idsToConsolidate.add(idsArray.getString(j))
                                        }

                                        if (idsToConsolidate.isNotEmpty()) {
                                            // 删除旧记忆
                                            idsToConsolidate.forEach { id ->
                                                dbHelper.deletePersonaMemory(id)
                                            }

                                            // 添加新合并记忆
                                            val newMemory = PersonaMemoryEntity(
                                                id = UUID.randomUUID().toString(),
                                                chatId = chatId,
                                                content = item.getString("newContent"),
                                                importance = item.optInt("importance", 5),
                                                timestamp = Date(),
                                                type = item.optString("type", "consolidated")
                                            )
                                            insertPersonaMemory(newMemory) // 使用维护数量限制的方法
                                            consolidatedCount++
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "处理单个合并记忆失败: ${e.message}")
                                    }
                                }

                                // 处理需要删除的记忆
                                val removeIdsArray = jsonObject.optJSONArray("removeIds") ?: JSONArray()
                                var removedCount = 0

                                for (i in 0 until removeIdsArray.length()) {
                                    try {
                                        dbHelper.deletePersonaMemory(removeIdsArray.getString(i))
                                        removedCount++
                                    } catch (e: Exception) {
                                        Log.e(TAG, "删除记忆失败: ${e.message}")
                                    }
                                }

                                Log.d(TAG, "人设记忆整合完成: 合并了 $consolidatedCount 组记忆, 删除了 $removedCount 条记忆")

                                // 整合后检查并确保总数限制
                                maintainMemoryLimit(chatId)
                            } catch (e: Exception) {
                                Log.e(TAG, "解析整合结果失败: ${e.message}", e)
                            }

                            // 成功处理，退出循环
                            break
                        }
                    } else {
                        val errorCode = response.code()
                        val errorBody = response.errorBody()?.string() ?: "未知错误"
                        Log.e(TAG, "整合API请求失败: $errorCode - $errorBody")

                        // 判断是否应该重试 - 只有在服务器繁忙时才重试
                        if (errorCode == 503 || errorBody.contains("too busy") || errorBody.contains("System is too busy")) {
                            if (retryCount < MAX_RETRIES) {
                                retryCount++
                                val delayTime = RETRY_DELAY_MS * (1 shl (retryCount - 1))
                                Log.d(TAG, "服务忙，${delayTime}ms后重试(${retryCount}/${MAX_RETRIES})")
                                delay(delayTime)
                                continue
                            }
                        }

                        // 达到最大重试次数或非服务忙错误，放弃本次操作
                        Log.d(TAG, "整合API请求失败，将等待下次再试")
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "人设记忆整合失败: ${e.message}", e)

                    // 增加重试次数
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        val delayTime = RETRY_DELAY_MS * (1 shl (retryCount - 1))
                        Log.d(TAG, "发生异常，${delayTime}ms后重试(${retryCount}/${MAX_RETRIES})")
                        delay(delayTime)
                    } else {
                        // 超过重试次数，放弃本次操作
                        Log.d(TAG, "重试失败，将等待下次再试")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "人设记忆整合处理失败: ${e.message}", e)
            // 不做额外处理，等待下次再试
        }
    }

    /**
     * 主动清理记忆 - 确保记忆数量在限制范围内
     * 可以在对话开始或结束时调用
     */
    suspend fun cleanupExcessiveMemories(chatId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始主动清理记忆")
            maintainMemoryLimit(chatId)
        } catch (e: Exception) {
            Log.e(TAG, "清理过多记忆失败: ${e.message}", e)
        }
    }
}

package com.example.chatapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.api.ChatMessage
import com.example.chatapp.api.MemoryApiClient
import com.example.chatapp.data.db.ChatDatabaseHelper
import com.example.chatapp.data.db.ChatEntity
import com.example.chatapp.data.db.MemoryEntity
import com.example.chatapp.data.db.MessageEntity
import com.example.chatapp.persona.PersonaMemorySystem
import com.example.chatapp.persona.PersonaPromptEngineer
import com.example.chatapp.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

/**
 * 聊天历史记录视图模型
 */
class ChatHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ChatHistoryViewModel"
    private val repository = ChatRepository(application)
    private val dbHelper = ChatDatabaseHelper.getInstance(application)
    private val memoryApiClient = MemoryApiClient(application)

    // 添加人设相关组件
    private val personaMemorySystem = PersonaMemorySystem(application)
    private val personaPromptEngineer = PersonaPromptEngineer(application)

    // 状态标志，用于导入时是否应该生成记忆
    private var shouldGenerateMemories = false

    // 人设优化标志
    private var shouldOptimizePersona = true // 默认启用人设优化

    // 所有聊天列表
    private val _allChats = MutableStateFlow<List<ChatEntity>>(emptyList())
    val allChats: StateFlow<List<ChatEntity>> = _allChats.asStateFlow()

    // 搜索结果
    private val _searchResults = MutableStateFlow<List<ChatDatabaseHelper.MessageWithChat>>(emptyList())
    val searchResults: StateFlow<List<ChatDatabaseHelper.MessageWithChat>> = _searchResults.asStateFlow()

    // 获取活跃和归档的聊天列表流
    val allActiveChats: Flow<List<ChatEntity>> = repository.allActiveChats
    val allArchivedChats: Flow<List<ChatEntity>> = repository.allArchivedChats

    init {
        // 初始化时加载所有聊天
        loadAllChats()
    }

    /**
     * 加载所有聊天记录
     */
    fun loadAllChats() {
        viewModelScope.launch {
            try {
                // 聊天记录流
                combine(
                    repository.allActiveChats,
                    repository.allArchivedChats
                ) { active, archived ->
                    // 按更新时间排序
                    (active + archived).sortedByDescending { it.updatedAt }
                }.collect { chats ->
                    _allChats.value = chats
                    Log.d(TAG, "加载所有聊天记录: ${chats.size} 条")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载聊天记录失败: ${e.message}", e)
            }
        }
    }

    /**
     * 搜索消息
     */
    fun searchMessages(query: String) {
        viewModelScope.launch {
            try {
                val results = repository.searchMessages(query)
                _searchResults.value = results
                Log.d(TAG, "搜索消息，关键词: $query, 结果: ${results.size} 条")
            } catch (e: Exception) {
                Log.e(TAG, "搜索消息失败: ${e.message}", e)
            }
        }
    }

    /**
     * 删除聊天
     */
    suspend fun deleteChat(chatId: String) {
        repository.deleteChat(chatId)
    }

    /**
     * 获取聊天详情
     */
    suspend fun getChatById(chatId: String): ChatEntity? {
        return repository.getChatDetails(chatId)
    }

    /**
     * 更新聊天标题
     */
    suspend fun updateChatTitle(chatId: String, newTitle: String) {
        repository.updateChatTitle(chatId, newTitle)
    }

    /**
     * 归档/取消归档会话
     */
    suspend fun toggleChatArchiveStatus(chatId: String, isArchived: Boolean) {
        repository.toggleChatArchiveStatus(chatId, isArchived)
    }

    /**
     * 获取聊天记忆列表
     */
    fun getChatMemories(chatId: String): Flow<List<MemoryEntity>> {
        // 使用dbHelper直接获取记忆，而不是通过repository
        return dbHelper.getMemoriesForChat(chatId)
    }

    /**
     * 删除记忆
     */
    suspend fun deleteMemory(memoryId: String) {
        repository.deleteMemory(memoryId)
    }

    /**
     * 获取与用户输入相关的记忆
     */
    suspend fun getRelevantMemories(userInput: String): List<MemoryEntity> {
        return repository.getRelevantMemories(userInput)
    }

    /**
     * 获取聊天的所有消息
     * @param chatId 聊天ID
     * @return 消息列表
     */
    suspend fun getChatMessages(chatId: String): List<MessageEntity> {
        return repository.getChatMessages(chatId)
    }

    /**
     * 设置是否应该生成记忆
     */
    fun setShouldGenerateMemories(generate: Boolean) {
        shouldGenerateMemories = generate
    }

    /**
     * 设置是否应该优化人设
     */
    fun setShouldOptimizePersona(optimize: Boolean) {
        shouldOptimizePersona = optimize
    }

    /**
     * 导入消息
     * @param messages 要导入的消息列表
     * @return 成功导入的消息数量
     */
    suspend fun importMessages(messages: List<MessageEntity>): Int {
        return withContext(Dispatchers.IO) {
            try {
                if (messages.isEmpty()) return@withContext 0

                // 获取聊天ID（所有消息应该是同一个聊天ID）
                val chatId = messages.first().chatId

                // 分批处理，每批次处理10条消息（模拟正常对话，每五轮生成一次记忆）
                val messageGroups = messages.chunked(10)

                var totalInserted = 0

                // 逐批插入消息并生成记忆
                for (group in messageGroups) {
                    // 插入这批消息
                    for (message in group) {
                        dbHelper.insertMessage(message)
                        totalInserted++
                    }

                    // 如果需要生成记忆，则每10条消息（5轮对话）生成一次
                    if (shouldGenerateMemories && group.size >= 2) {
                        generateMemoryForMessages(group, chatId)
                    }
                }

                // 更新会话的更新时间
                val chat = dbHelper.getChatById(chatId)
                if (chat != null) {
                    dbHelper.updateChat(chat.copy(updatedAt = Date()))

                    // 如果需要优化人设，在导入完成后进行分析和优化
                    if (shouldOptimizePersona && messages.size >= 6) {
                        analyzeAndOptimizePersona(messages, chatId, chat)
                    }
                }

                return@withContext totalInserted
            } catch (e: Exception) {
                Log.e(TAG, "导入消息失败: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * 为一组消息生成记忆
     * @param messages 消息组
     * @param chatId 聊天ID
     */
    private suspend fun generateMemoryForMessages(messages: List<MessageEntity>, chatId: String) {
        try {
            // 转换为API消息格式
            val apiMessages = messages.map { message ->
                ChatMessage(
                    role = if (message.type == 0) "user" else "assistant",
                    content = message.content
                )
            }

            // 调用API生成增强记忆
            val memoryResult = memoryApiClient.generateEnhancedMemory(apiMessages)

            // 创建记忆实体
            val memory = MemoryEntity(
                chatId = chatId,
                content = memoryResult.summary,
                timestamp = Date(),
                startMessageId = messages.first().id,
                endMessageId = messages.last().id,
                category = memoryResult.category,
                importance = memoryResult.importance,
                keywords = memoryResult.keywords
            )

            // 保存记忆到数据库
            dbHelper.insertMemory(memory)

            Log.d(TAG, "为导入消息生成记忆: ${memory.content}")
        } catch (e: Exception) {
            Log.e(TAG, "为导入消息生成记忆失败: ${e.message}", e)
        }
    }

    /**
     * 分析消息并优化人设
     * @param messages 所有导入的消息
     * @param chatId 会话ID
     * @param chat 会话实体
     */
    private suspend fun analyzeAndOptimizePersona(
        messages: List<MessageEntity>,
        chatId: String,
        chat: ChatEntity
    ) {
        try {
            Log.d(TAG, "开始分析导入的消息并优化人设: 消息数量=${messages.size}")

            // 提取与人设相关的记忆
            // 转换为API消息格式
            val apiMessages = messages.map { message ->
                ChatMessage(
                    role = if (message.type == 0) "user" else "assistant",
                    content = message.content
                )
            }

            // 调用人设记忆系统提取关键特征
            personaMemorySystem.analyzeAndExtractPersonaMemories(
                messages = apiMessages,
                chatId = chatId
            )

            Log.d(TAG, "已从导入消息中提取人设记忆")

            // 优化人设提示词
            // 如果聊天已经有人设，进行优化；如果没有，则创建初始人设
            if (!chat.aiPersona.isNullOrEmpty()) {
                val optimizedPersona = personaPromptEngineer.optimizePersonaPrompt(
                    chatId = chatId,
                    basePersona = chat.aiPersona
                )

                // 如果优化后的人设与原人设不同且长度有显著增加，则更新
                if (optimizedPersona != chat.aiPersona &&
                    optimizedPersona.length > chat.aiPersona.length * 1.1) {

                    // 更新聊天人设
                    dbHelper.updateChat(chat.copy(aiPersona = optimizedPersona))
                    Log.d(TAG, "基于导入聊天记录优化了人设，长度从 ${chat.aiPersona.length} 字符增加到 ${optimizedPersona.length} 字符")
                } else {
                    Log.d(TAG, "导入聊天未产生显著的人设优化")
                }
            } else {
                // 尝试根据导入的消息创建一个基本人设
                // 先从AI消息中提取可能的人设特征
                val aiMessages = messages.filter { it.type == 1 }.map { it.content }
                if (aiMessages.isNotEmpty()) {
                    // 提取AI的一些基本特征作为初始人设
                    val basicPersona = generateBasicPersonaFromMessages(aiMessages)

                    // 使用人设优化器完善初始人设
                    val enhancedPersona = personaPromptEngineer.createInitialPersona(basicPersona)

                    // 更新聊天人设
                    if (enhancedPersona.isNotEmpty()) {
                        dbHelper.updateChat(chat.copy(aiPersona = enhancedPersona))
                        Log.d(TAG, "基于导入聊天记录创建了新人设，长度: ${enhancedPersona.length} 字符")
                    }
                } else {
                    Log.d(TAG, "导入的消息中没有AI回复，无法创建初始人设")
                }
            }

            // 整合人设记忆，减少冗余
            personaMemorySystem.consolidatePersonaMemories(chatId)

        } catch (e: Exception) {
            Log.e(TAG, "分析消息并优化人设失败: ${e.message}", e)
        }
    }

    /**
     * 从AI消息中生成基本人设描述
     */
    private fun generateBasicPersonaFromMessages(aiMessages: List<String>): String {
        // 提取AI消息中的自我描述特征，生成基本人设
        val selfDescriptions = aiMessages.filter {
            it.contains("我喜欢") || it.contains("我是") || it.contains("我能") ||
                    it.contains("我可以") || it.contains("我擅长")
        }

        // 如果找到自我描述，构建基本人设
        return if (selfDescriptions.isNotEmpty()) {
            val traits = selfDescriptions.take(5)
            "你具有以下特点:\n" +
                    traits.joinToString("\n") { "- ${it.take(100)}" }
        } else {
            // 默认基本人设
            "你的回答应该简洁、清晰且有帮助。"
        }
    }
}

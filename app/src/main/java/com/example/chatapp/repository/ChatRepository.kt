package com.example.chatapp.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.chatapp.api.ApiClient
import com.example.chatapp.api.ChatGptRequest
import com.example.chatapp.api.ChatMessage
import com.example.chatapp.data.ContentType
import com.example.chatapp.api.MemoryApiClient
import com.example.chatapp.data.Message
import com.example.chatapp.data.MessageCacheManager
import com.example.chatapp.utils.ImageProcessor
import com.example.chatapp.api.MultimodalHelper
import com.example.chatapp.utils.DocumentProcessor
import com.example.chatapp.data.MessagePagingManager
import com.example.chatapp.data.MessageType
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.data.db.ChatDatabaseHelper
import com.example.chatapp.data.db.ChatEntity
import com.example.chatapp.data.db.MemoryEntity
import com.example.chatapp.data.db.MessageEntity
import com.example.chatapp.data.db.PersonaMemoryEntity
import com.example.chatapp.data.db.UserProfileEntity
import com.example.chatapp.persona.PersonaContextManager
import com.example.chatapp.persona.PersonaConsistencyValidator
import com.example.chatapp.persona.PersonaMemorySystem
import com.example.chatapp.persona.PersonaPromptEngineer
import com.example.chatapp.profile.UserProfileSystem
import com.example.chatapp.service.MemoryRelevanceService
import com.example.chatapp.feedback.FeedbackManager
import com.example.chatapp.utils.ImportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Date
import java.util.UUID
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

/**
 * 聊天仓库类，处理消息的发送和接收，以及数据的持久化
 */
class ChatRepository(private val context: Context) {
    private val TAG = "ChatRepository"

    // 数据库助手
    private val dbHelper = ChatDatabaseHelper.getInstance(context)

    // API客户端
    private val memoryApiClient = MemoryApiClient(context)

    // 记忆相关性服务
    private val memoryRelevanceService = MemoryRelevanceService()

    // 分页管理器
    private val pagingManager = MessagePagingManager(context)

    // 缓存管理器
    private val messageCache = MessageCacheManager.getInstance()

    // 用户画像系统
    private val userProfileSystem = UserProfileSystem(context)

    // 反馈管理器
    private val feedbackManager = FeedbackManager(context)

    // 设置管理器
    private val settingsManager = SettingsManager(context)

    // 人设增强组件
    private val personaContextManager = PersonaContextManager(context)
    private val personaMemorySystem = PersonaMemorySystem(context)
    private val personaConsistencyValidator = PersonaConsistencyValidator(context)
    private val personaPromptEngineer = PersonaPromptEngineer(context)

    // 当前活动会话ID
    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    // 消息列表状态流
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // 会话列表状态流
    val allActiveChats: Flow<List<ChatEntity>> = dbHelper.getAllActiveChats()
    val allArchivedChats: Flow<List<ChatEntity>> = dbHelper.getAllArchivedChats()

    // 分页加载状态
    val loadingState = pagingManager.loadingState
    val hasMoreOlderMessages = pagingManager.hasMoreOlder
    val hasMoreNewerMessages = pagingManager.hasMoreNewer

    // 历史消息记录，用于API请求
    private val chatHistory = mutableListOf<ChatMessage>()

    // 记忆生成相关字段
    private var messageCountSinceLastMemory = 0
    private var lastProcessedMessageIds = mutableMapOf<String, String>() // chatId -> lastMessageId

    // 人设记忆相关计数器
    private var messageCountSinceLastPersonaAnalysis = 0

    // 持久化存储的键前缀
    private val MEMORY_COUNTER_PREFS = "memory_counters"
    private val MEMORY_COUNTER_KEY_PREFIX = "counter_"
    private val LAST_PROCESSED_MSG_KEY_PREFIX = "last_msg_"
    private val PERSONA_COUNTER_KEY_PREFIX = "persona_counter_"

    // 导入分析状态
    private val _importAnalysisState = MutableStateFlow(false)
    val importAnalysisState: StateFlow<Boolean> = _importAnalysisState.asStateFlow()

    // 上下文管理相关常量和字段
    companion object {
        // 上下文窗口大小: 保留最近的30轮对话
        private const val MAX_CONTEXT_WINDOW_SIZE = 30

        // 用户画像更新频率: 每10轮对话更新一次
        private const val USER_PROFILE_UPDATE_INTERVAL = 10

        // 人设记忆分析频率: 每6条消息(3轮对话)
        private const val PERSONA_MEMORY_ANALYSIS_INTERVAL = 6
    }

    // 对话轮数计数器
    private val dialogTurnsCounter = mutableMapOf<String, Int>() // chatId -> 对话轮数

    // 用户画像缓存
    private val userProfiles = mutableMapOf<String, UserProfileSystem.UserProfile>()

    init {
        // 初始化时尝试从SharedPreferences加载上次的会话ID
        _currentChatId.value = loadLastChatId()
    }

    /**
     * 获取当前用户设置的模型
     */
    private fun getCurrentModelFromSettings(): String {
        // 从应用设置中获取
        return settingsManager.modelType
    }

    /**
     * 获取指定会话使用的模型，如果提供了覆盖模型则使用覆盖模型
     */
    private suspend fun getCurrentModelForChat(chatId: String, overrideModel: String? = null): String {
        if (overrideModel != null) {
            return overrideModel
        }

        // 从会话信息中获取模型
        val chat = dbHelper.getChatById(chatId)
        if (chat != null && chat.modelType.isNotEmpty()) {
            return chat.modelType
        }

        // 如果会话没有指定模型，则使用用户设置的默认模型
        return getCurrentModelFromSettings()
    }

    /**
     * 将数据库内容类型转换为应用层内容类型
     */
    private fun mapContentType(contentTypeOrdinal: Int): ContentType {
        return when(contentTypeOrdinal) {
            1 -> ContentType.IMAGE
            2 -> ContentType.IMAGE_WITH_TEXT
            3 -> ContentType.DOCUMENT
            else -> ContentType.TEXT
        }
    }

    /**
     * 将MessageEntity转换为应用层Message
     */
    private fun convertEntityToMessage(entity: MessageEntity): Message {
        return Message(
            id = entity.id,
            content = entity.content,
            type = if (entity.type == 0) MessageType.USER else MessageType.AI,
            timestamp = entity.timestamp,
            isProcessing = false,
            imageData = entity.imageData,
            contentType = mapContentType(entity.contentType),
            documentSize = entity.documentSize,
            documentType = entity.documentType
        )
    }

    /**
     * 保存当前会话ID到SharedPreferences
     */
    private fun saveCurrentChatId(chatId: String?) {
        val prefs = context.getSharedPreferences("chat_app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("current_chat_id", chatId).commit()
        Log.d(TAG, "保存当前会话ID: $chatId")
    }

    /**
     * 从SharedPreferences加载上次的会话ID
     */
    fun loadLastChatId(): String? {
        val prefs = context.getSharedPreferences("chat_app_prefs", Context.MODE_PRIVATE)
        val chatId = prefs.getString("current_chat_id", null)
        Log.d(TAG, "加载上次的会话ID: $chatId")
        return chatId
    }

    /**
     * 保存记忆计数器状态
     */
    private fun saveMemoryCounterState(chatId: String) {
        val prefs = context.getSharedPreferences(MEMORY_COUNTER_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(MEMORY_COUNTER_KEY_PREFIX + chatId, messageCountSinceLastMemory)
            .putString(LAST_PROCESSED_MSG_KEY_PREFIX + chatId, lastProcessedMessageIds[chatId])
            .putInt(PERSONA_COUNTER_KEY_PREFIX + chatId, messageCountSinceLastPersonaAnalysis)
            .apply()
        Log.d(TAG, "保存记忆计数器状态: chatId=$chatId, counter=$messageCountSinceLastMemory, personaCounter=$messageCountSinceLastPersonaAnalysis, lastMsgId=${lastProcessedMessageIds[chatId]}")
    }

    /**
     * 加载记忆计数器状态
     */
    private fun loadMemoryCounterState(chatId: String) {
        val prefs = context.getSharedPreferences(MEMORY_COUNTER_PREFS, Context.MODE_PRIVATE)
        messageCountSinceLastMemory = prefs.getInt(MEMORY_COUNTER_KEY_PREFIX + chatId, 0)
        messageCountSinceLastPersonaAnalysis = prefs.getInt(PERSONA_COUNTER_KEY_PREFIX + chatId, 0)
        val lastMsgId = prefs.getString(LAST_PROCESSED_MSG_KEY_PREFIX + chatId, null)
        if (lastMsgId != null) {
            lastProcessedMessageIds[chatId] = lastMsgId
        } else {
            lastProcessedMessageIds.remove(chatId)
        }
        Log.d(TAG, "加载记忆计数器状态: chatId=$chatId, counter=$messageCountSinceLastMemory, personaCounter=$messageCountSinceLastPersonaAnalysis, lastMsgId=$lastMsgId")
    }

    /**
     * 创建新会话
     */
    suspend fun createNewChat(title: String = "新对话", modelType: String, aiPersona: String): String {
        return withContext(Dispatchers.IO) {
            val chatId = UUID.randomUUID().toString()
            val currentTime = Date()

            val newChat = ChatEntity(
                id = chatId,
                title = title,
                createdAt = currentTime,
                updatedAt = currentTime,
                modelType = modelType,
                aiPersona = aiPersona
            )

            dbHelper.insertChat(newChat)

            // 设置为当前会话
            _currentChatId.value = chatId

            // 保存到SharedPreferences
            saveCurrentChatId(chatId)

            // 清空内存中的消息和聊天历史
            _messages.value = emptyList()
            chatHistory.clear()

            // 重置记忆相关计数器
            messageCountSinceLastMemory = 0
            messageCountSinceLastPersonaAnalysis = 0
            lastProcessedMessageIds.remove(chatId)

            // 初始化并保存新会话的记忆计数器状态
            saveMemoryCounterState(chatId)

            // 重置对话轮数计数器
            dialogTurnsCounter[chatId] = 0

            // 重置分页状态
            pagingManager.reset()

            // 加载AI人设
            if (aiPersona.isNotEmpty()) {
                // 使用人设优化器增强人设提示词
                val enhancedPersona = personaPromptEngineer.createInitialPersona(aiPersona)
                sendSystemMessage(enhancedPersona)
            }

            chatId
        }
    }

    /**
     * 切换当前会话
     */
    suspend fun switchChat(chatId: String) {
        withContext(Dispatchers.IO) {
            try {
                val chat = dbHelper.getChatById(chatId)
                if (chat == null) {
                    Log.e(TAG, "尝试切换到不存在的会话: $chatId")
                    return@withContext
                }

                // 若有当前会话，先保存其状态
                _currentChatId.value?.let { oldChatId ->
                    saveMemoryCounterState(oldChatId)

                    // 清除旧会话的缓存
                    messageCache.clearChatCache(oldChatId)
                }

                _currentChatId.value = chatId

                // 保存到SharedPreferences
                saveCurrentChatId(chatId)

                // 重置分页状态
                pagingManager.reset()

                // 使用分页加载初始消息
                val initialMessages = pagingManager.initialLoad(chatId)

                // 缓存加载的消息
                messageCache.cacheMessages(chatId, initialMessages)

                // 更新内存中的消息列表
                _messages.value = initialMessages

                // 重建聊天历史，用于API请求
                chatHistory.clear()

                // 如果有人设，添加系统消息
                if (chat.aiPersona.isNotEmpty()) {
                    chatHistory.add(ChatMessage("system", chat.aiPersona))
                }

                // 加载用户画像
                val profile = userProfileSystem.loadUserProfile(chatId)
                if (profile != null) {
                    userProfiles[chatId] = profile

                    // 更新聊天历史中的用户画像
                    updateUserProfileInChatHistory(profile.toSystemPrompt())
                    Log.d(TAG, "已加载用户画像: ${profile.summary.take(50)}...")
                }

                // 添加所有消息到聊天历史，但受上下文窗口限制
                rebuildChatHistoryWithLoadedMessages(initialMessages)

                // 加载该会话的记忆计数器状态
                loadMemoryCounterState(chatId)

                // 初始化对话轮数计数器（如果不存在）
                if (!dialogTurnsCounter.containsKey(chatId)) {
                    // 计算大致的对话轮数（用户消息和AI消息各算0.5轮）
                    dialogTurnsCounter[chatId] = initialMessages.size / 2
                }
            } catch (e: Exception) {
                Log.e(TAG, "切换会话时出错: ${e.message}", e)
            }
        }
    }

    /**
     * 使用加载的消息重建聊天历史
     */
    private fun rebuildChatHistoryWithLoadedMessages(messages: List<Message>) {
        // 确保聊天历史在上下文窗口大小内
        val maxMsgCount = MAX_CONTEXT_WINDOW_SIZE * 2

        val messagesToAdd = if (messages.size > maxMsgCount) {
            // 只取最近的maxMsgCount条消息
            messages.subList(messages.size - maxMsgCount, messages.size)
        } else {
            messages
        }

        // 添加消息到历史记录
        for (message in messagesToAdd) {
            val role = if (message.type == MessageType.USER) "user" else "assistant"
            chatHistory.add(ChatMessage(role, message.content))
        }

        Log.d(TAG, "重建聊天历史记录，应用上下文窗口限制后保留 ${messagesToAdd.size} 条消息")
    }

    /**
     * 加载更多较旧的消息
     */
    suspend fun loadMoreOlderMessages() {
        val chatId = _currentChatId.value ?: return

        val olderMessages = pagingManager.loadOlderMessages(chatId)
        if (olderMessages.isNotEmpty()) {
            // 缓存加载的消息
            messageCache.cacheMessages(chatId, olderMessages)

            // 更新消息列表 - 在前面添加较旧的消息
            val currentList = _messages.value.toMutableList()
            currentList.addAll(0, olderMessages)
            _messages.value = currentList

            // 更新聊天历史 - 将较旧消息加入到API请求中
            if (shouldUpdateChatHistory(olderMessages)) {
                updateChatHistoryWithOlderMessages(olderMessages)
            }
        }
    }

    /**
     * 加载更多较新的消息
     */
    suspend fun loadMoreNewerMessages() {
        val chatId = _currentChatId.value ?: return

        val newerMessages = pagingManager.loadNewerMessages(chatId)
        if (newerMessages.isNotEmpty()) {
            // 缓存加载的消息
            messageCache.cacheMessages(chatId, newerMessages)

            // 更新消息列表 - 在后面添加较新的消息
            val currentList = _messages.value.toMutableList()
            currentList.addAll(newerMessages)
            _messages.value = currentList

            // 更新聊天历史
            updateChatHistoryWithNewerMessages(newerMessages)
        }
    }

    /**
     * 判断是否应该更新聊天历史
     */
    private fun shouldUpdateChatHistory(messages: List<Message>): Boolean {
        // 如果消息中包含重要的系统提示或最近的交互，则更新聊天历史
        // 待完善
        return true
    }

    /**
     * 更新聊天历史以包含较旧的消息
     */
    private fun updateChatHistoryWithOlderMessages(olderMessages: List<Message>) {
        // 删除较旧的消息，保持聊天历史在合理大小内
        ensureContextWindowSize()

        // 在聊天历史开头添加较旧的消息
        val messagesToAdd = olderMessages.map { message ->
            val role = if (message.type == MessageType.USER) "user" else "assistant"
            ChatMessage(role, message.content)
        }

        chatHistory.addAll(0, messagesToAdd)

        // 再次确保不超过窗口大小
        ensureContextWindowSize()
    }

    /**
     * 更新聊天历史以包含较新的消息
     */
    private fun updateChatHistoryWithNewerMessages(newerMessages: List<Message>) {
        // 删除较旧的消息，保持聊天历史在合理大小内
        ensureContextWindowSize()

        // 在聊天历史末尾添加较新的消息
        val messagesToAdd = newerMessages.map { message ->
            val role = if (message.type == MessageType.USER) "user" else "assistant"
            ChatMessage(role, message.content)
        }

        chatHistory.addAll(messagesToAdd)

        // 再次确保不超过窗口大小
        ensureContextWindowSize()
    }

    /**
     * 直接插入消息到数据库并更新UI
     * 用于系统消息和特殊情况（如闹钟确认）
     */
    suspend fun insertMessageDirectly(message: MessageEntity) {
        withContext(Dispatchers.IO) {
            // 保存到数据库
            dbHelper.insertMessage(message)

            // 如果是当前聊天，添加到内存中的消息列表
            if (message.chatId == _currentChatId.value) {
                val uiMessage = convertEntityToMessage(message)

                // 添加到缓存
                messageCache.cacheMessage(message.chatId, uiMessage)

                // 更新分页管理器状态
                pagingManager.handleNewMessage(uiMessage)

                withContext(Dispatchers.Main) {
                    val currentList = _messages.value.toMutableList()
                    currentList.add(uiMessage)
                    _messages.value = currentList
                }
            }

            // 更新会话最后更新时间
            val chat = dbHelper.getChatById(message.chatId) ?: return@withContext
            dbHelper.updateChat(chat.copy(updatedAt = Date()))
        }
    }

    /**
     * 更新消息内容
     * @param messageId 消息ID
     * @param chatId 聊天ID
     * @param newContent 新的消息内容
     * @return 更新是否成功
     */
    suspend fun updateMessageContent(messageId: String, chatId: String, newContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 从数据库获取原消息
            val originalMessage = dbHelper.getMessageById(messageId, chatId)
            if (originalMessage != null) {
                // 创建更新后的消息实体，保留其他属性不变
                val updatedMessage = MessageEntity(
                    id = originalMessage.id,
                    chatId = originalMessage.chatId,
                    content = newContent,
                    type = originalMessage.type,
                    timestamp = originalMessage.timestamp,
                    isError = originalMessage.isError,
                    imageData = originalMessage.imageData,
                    contentType = originalMessage.contentType,
                    documentSize = originalMessage.documentSize,
                    documentType = originalMessage.documentType
                )

                // 更新数据库
                dbHelper.insertMessage(updatedMessage)

                // 更新内存中的消息列表
                withContext(Dispatchers.Main) {
                    val currentList = _messages.value.toMutableList()
                    val index = currentList.indexOfFirst { it.id == messageId }
                    if (index != -1) {
                        // 替换消息内容但保留其他属性
                        val updatedUIMessage = currentList[index].copy(content = newContent)
                        currentList[index] = updatedUIMessage
                        _messages.value = currentList

                        // 更新缓存
                        messageCache.cacheMessage(chatId, updatedUIMessage)
                    }
                }

                // 更新聊天历史中的消息内容
                updateMessageInChatHistory(originalMessage.type == 0, originalMessage.content, newContent)

                Log.d(TAG, "消息已更新: ID=$messageId")
                return@withContext true
            } else {
                Log.e(TAG, "更新消息失败: 未找到消息 ID=$messageId")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新消息失败: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * 更新聊天历史中的消息
     * @param isUserMessage 是否为用户消息
     * @param oldContent 原消息内容
     * @param newContent 新消息内容
     */
    private fun updateMessageInChatHistory(isUserMessage: Boolean, oldContent: String, newContent: String) {
        // 在chatHistory中找到并更新匹配的消息
        val role = if (isUserMessage) "user" else "assistant"
        val index = chatHistory.indexOfFirst { it.role == role && it.content == oldContent }
        if (index != -1) {
            chatHistory[index] = ChatMessage(role, newContent)
        }
    }

    /**
     * 根据上下文窗口大小限制重建聊天历史
     */
    private fun rebuildChatHistoryWithContextWindow(messageEntities: List<MessageEntity>) {
        // 如果消息数量超过窗口大小的两倍（因为每轮有用户+AI两条消息），则只保留最近的消息
        val maxMsgCount = MAX_CONTEXT_WINDOW_SIZE * 2

        val entitiesToAdd = if (messageEntities.size > maxMsgCount) {
            // 只取最近的maxMsgCount条消息
            messageEntities.subList(messageEntities.size - maxMsgCount, messageEntities.size)
        } else {
            messageEntities
        }

        // 添加消息到历史记录
        for (message in entitiesToAdd) {
            val role = if (message.type == 0) "user" else "assistant"
            chatHistory.add(ChatMessage(role, message.content))
        }

        Log.d(TAG, "重建聊天历史记录，应用上下文窗口限制后保留 ${entitiesToAdd.size} 条消息")
    }

    /**
     * 加载当前会话的消息
     */
    suspend fun loadCurrentChatMessages() {
        val chatId = _currentChatId.value ?: return

        try {
            Log.d(TAG, "尝试加载当前会话: $chatId")
            switchChat(chatId)
        } catch (e: Exception) {
            Log.e(TAG, "加载当前会话消息失败: ${e.message}", e)
        }
    }

    /**
     * 获取会话详情
     */
    suspend fun getChatDetails(chatId: String): ChatEntity? {
        return dbHelper.getChatById(chatId)
    }

    /**
     * 更新会话标题
     */
    suspend fun updateChatTitle(chatId: String, newTitle: String) {
        dbHelper.updateChatTitle(chatId, newTitle)
    }

    /**
     * 归档/取消归档会话
     */
    suspend fun toggleChatArchiveStatus(chatId: String, isArchived: Boolean) {
        withContext(Dispatchers.IO) {
            dbHelper.updateChatArchiveStatus(chatId, isArchived)

            // 如果归档当前会话，需要清空当前会话
            if (isArchived && _currentChatId.value == chatId) {
                _currentChatId.value = null
                _messages.value = emptyList()
                chatHistory.clear()
                saveCurrentChatId(null)

                // 清除该会话的缓存
                messageCache.clearChatCache(chatId)

                // 重置分页状态
                pagingManager.reset()
            }
        }
    }

    /**
     * 删除会话及其消息
     */
    suspend fun deleteChat(chatId: String) {
        withContext(Dispatchers.IO) {
            dbHelper.deleteChatWithMessages(chatId)

            // 如果删除当前会话，需要清空当前会话
            if (_currentChatId.value == chatId) {
                _currentChatId.value = null
                _messages.value = emptyList()
                chatHistory.clear()
                saveCurrentChatId(null)

                // 重置分页状态
                pagingManager.reset()
            }

            // 清除该会话的记忆处理状态
            lastProcessedMessageIds.remove(chatId)

            // 清除该会话的持久化记忆状态
            val prefs = context.getSharedPreferences(MEMORY_COUNTER_PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(MEMORY_COUNTER_KEY_PREFIX + chatId)
                .remove(LAST_PROCESSED_MSG_KEY_PREFIX + chatId)
                .remove(PERSONA_COUNTER_KEY_PREFIX + chatId)
                .apply()

            // 清除该会话的对话轮数计数器
            dialogTurnsCounter.remove(chatId)

            // 清除该会话的用户画像缓存
            userProfiles.remove(chatId)

            // 清除该会话的消息缓存
            messageCache.clearChatCache(chatId)

            // 删除数据库中的用户画像和标签
            dbHelper.deleteUserProfile(chatId)
            dbHelper.deleteUserTags(chatId)

            // 删除人设记忆
            dbHelper.deleteAllPersonaMemoriesForChat(chatId)
        }
    }

    /**
     * 根据关键字搜索消息
     */
    suspend fun searchMessages(query: String): List<ChatDatabaseHelper.MessageWithChat> {
        return withContext(Dispatchers.IO) {
            // 先在缓存中搜索
            val cachedResults = searchInCache(query)

            // 如果缓存中有足够的结果，使用缓存结果
            if (cachedResults.size >= 10) {
                return@withContext cachedResults
            }

            // 否则在数据库中搜索
            val dbResults = dbHelper.searchMessages(query)

            // 对结果进行去重
            val finalResults = mutableListOf<ChatDatabaseHelper.MessageWithChat>()
            finalResults.addAll(cachedResults)

            for (result in dbResults) {
                if (!finalResults.any { it.message.id == result.message.id }) {
                    finalResults.add(result)
                }
            }

            finalResults
        }
    }

    /**
     * 在缓存中搜索消息
     */
    private suspend fun searchInCache(query: String): List<ChatDatabaseHelper.MessageWithChat> {
        val results = mutableListOf<ChatDatabaseHelper.MessageWithChat>()
        val lowerQuery = query.lowercase()

        // 获取所有聊天会话
        val allChats = withContext(Dispatchers.IO) {
            try {
                val activeChats = allActiveChats.first()
                val archivedChats = allArchivedChats.first()
                activeChats + archivedChats
            } catch (e: Exception) {
                Log.e(TAG, "获取会话列表失败: ${e.message}", e)
                emptyList<ChatEntity>()
            }
        }

        // 对每个会话，检查缓存的消息
        for (chat in allChats) {
            val cachedMessages = messageCache.getCachedMessagesForChat(chat.id)

            // 在缓存的消息中搜索
            for (message in cachedMessages) {
                if (message.content.lowercase().contains(lowerQuery)) {
                    // 转换为MessageWithChat格式
                    val messageEntity = MessageEntity(
                        id = message.id,
                        chatId = chat.id,
                        content = message.content,
                        type = if (message.type == MessageType.USER) 0 else 1,
                        timestamp = message.timestamp,
                        isError = false,
                        imageData = message.imageData,
                        contentType = when(message.contentType) {
                            ContentType.IMAGE -> 1
                            ContentType.IMAGE_WITH_TEXT -> 2
                            ContentType.DOCUMENT -> 3
                            else -> 0
                        },
                        documentSize = message.documentSize,
                        documentType = message.documentType
                    )

                    val resultItem = ChatDatabaseHelper.MessageWithChat(
                        message = messageEntity,
                        chat = chat
                    )

                    results.add(resultItem)
                }
            }
        }

        return results
    }

    /**
     * 添加消息到列表和数据库
     */
    private suspend fun addMessage(message: Message) {
        val currentList = _messages.value.toMutableList()
        currentList.add(message)
        _messages.value = currentList

        // 更新分页管理器状态
        pagingManager.handleNewMessage(message)

        // 添加到缓存
        val currentChatId = _currentChatId.value ?: return
        messageCache.cacheMessage(currentChatId, message)

        // 保存到数据库
        val messageEntity = MessageEntity(
            id = message.id,
            chatId = currentChatId,
            content = message.content,
            type = if (message.type == MessageType.USER) 0 else 1,
            timestamp = message.timestamp,
            isError = false,
            imageData = message.imageData,
            contentType = when(message.contentType) {
                ContentType.IMAGE -> 1
                ContentType.IMAGE_WITH_TEXT -> 2
                ContentType.DOCUMENT -> 3
                else -> 0
            },
            documentSize = message.documentSize,
            documentType = message.documentType
        )

        withContext(Dispatchers.IO) {
            dbHelper.insertMessage(messageEntity)

            // 更新会话最后更新时间
            val chat = dbHelper.getChatById(currentChatId) ?: return@withContext
            dbHelper.updateChat(chat.copy(updatedAt = Date()))
        }
    }

    /**
     * 更新消息状态
     */
    private suspend fun updateMessage(id: String, updatedMessage: Message) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            currentList[index] = updatedMessage
            _messages.value = currentList

            // 确保有活动会话
            val chatId = _currentChatId.value ?: return

            // 更新缓存
            messageCache.cacheMessage(chatId, updatedMessage)

            // 更新数据库
            val messageEntity = MessageEntity(
                id = updatedMessage.id,
                chatId = chatId,
                content = updatedMessage.content,
                type = if (updatedMessage.type == MessageType.USER) 0 else 1,
                timestamp = updatedMessage.timestamp,
                isError = false,
                imageData = updatedMessage.imageData,
                contentType = when(updatedMessage.contentType) {
                    ContentType.IMAGE -> 1
                    ContentType.IMAGE_WITH_TEXT -> 2
                    ContentType.DOCUMENT -> 3
                    else -> 0
                },
                documentSize = updatedMessage.documentSize,
                documentType = updatedMessage.documentType
            )

            withContext(Dispatchers.IO) {
                dbHelper.insertMessage(messageEntity)
            }
        }
    }

    /**
     * 发送系统消息设置人设
     */
    suspend fun sendSystemMessage(content: String) = withContext(Dispatchers.IO) {
        val currentChatId = _currentChatId.value

        // 尝试获取基于用户反馈的优化提示
        val optimizedPrompt = if (currentChatId != null) {
            try {
                val feedbackPrompt = feedbackManager.getOptimizedPrompt(currentChatId)
                if (feedbackPrompt.isNotEmpty() && feedbackPrompt != "请尽力提供有用的回答。") {
                    "$content\n\n基于用户反馈的优化指南：$feedbackPrompt"
                } else {
                    content
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取优化提示失败: ${e.message}", e)
                content
            }
        } else {
            content
        }

        // 添加系统消息到聊天历史
        chatHistory.add(ChatMessage("system", optimizedPrompt))
    }

    /**
     * 处理并发送包含图像的消息
     */
    suspend fun sendImageMessage(imageUri: Uri, caption: String = "", model: String? = null): Result<Message> = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始处理图像消息: $caption, URI: $imageUri")

        try {
            // 确保有活动会话
            val currentChatId = _currentChatId.value
            if (currentChatId == null) {
                // 获取当前用户设置的模型或使用传入的模型
                val userModel = model ?: getCurrentModelFromSettings()
                // 如果没有活动会话，创建一个新会话
                val chatId = createNewChat("图像对话", userModel, "")
                Log.d(TAG, "创建新会话: $chatId, 使用模型: $userModel")
            }

            // 初始化图像处理器
            val imageProcessor = ImageProcessor(context)

            // 处理图像，转换为Base64
            val base64Image = imageProcessor.processImage(imageUri)
            if (base64Image == null) {
                return@withContext Result.failure(Exception("图像处理失败"))
            }

            // 直接使用用户输入作为caption
            val finalCaption = caption

            // 根据是否有标题确定内容类型
            val contentType = if (finalCaption.isNotEmpty()) {
                ContentType.IMAGE_WITH_TEXT
            } else {
                ContentType.IMAGE
            }

            // 创建用户消息
            val userMessage = Message(
                content = finalCaption,
                type = MessageType.USER,
                imageData = base64Image,
                contentType = contentType
            )

            withContext(Dispatchers.Main) {
                addMessage(userMessage)
            }

            // 创建多模态请求所需的消息
            val multimodalMessage = MultimodalHelper.createMultimodalUserMessage(
                text = finalCaption,
                images = listOf(base64Image)
            )

            // 添加到聊天历史
            val tempChatHistory = chatHistory.toMutableList()
            chatHistory.clear()

            // 如果有系统消息，保留它们
            tempChatHistory.filter { it.role == "system" }.forEach {
                chatHistory.add(it)
            }

            // 添加系统指令
            chatHistory.add(ChatMessage("system", "请尽力提供有用的回答。"))

            // 添加多模态消息
            chatHistory.add(multimodalMessage)

            // 添加AI消息占位（显示加载状态）
            val aiMessagePlaceholder = Message(
                content = "",
                type = MessageType.AI,
                isProcessing = true
            )

            withContext(Dispatchers.Main) {
                addMessage(aiMessagePlaceholder)
            }

            try {
                // 使用会话的模型类型或用户选择的模型
                val chatModel = getCurrentModelForChat(currentChatId ?: _currentChatId.value!!, model)

                // 构建请求
                val request = ChatGptRequest(
                    model = chatModel, // 使用聊天模型
                    messages = chatHistory,
                    temperature = 0.9
                )

                Log.d(TAG, "发送多模态请求: ${request.model}, 消息数量: ${request.messages.size}")

                // 发送请求
                val response = withTimeoutOrNull(120000) {
                    ApiClient.apiService.sendMessage(
                        ApiClient.getAuthHeader(),
                        request
                    )
                }

                if (response == null) {
                    // 请求超时
                    throw SocketTimeoutException("请求超时，请稍后重试")
                }

                Log.d(TAG, "收到响应: ${response.code()} ${response.message()}")

                if (response.isSuccessful && response.body() != null) {
                    val chatGptResponse = response.body()
                    Log.d(TAG, "响应内容: 状态码=${chatGptResponse?.`object`}, 模型=${chatGptResponse?.model}")

                    val aiResponse = chatGptResponse?.choices?.firstOrNull()?.message?.content?.toString()
                        ?: "抱歉，未能生成对图像的描述。"

                    // 添加到聊天历史
                    chatHistory.add(ChatMessage("assistant", aiResponse))

                    // 更新AI消息（替换占位）
                    val aiMessage = Message(
                        id = aiMessagePlaceholder.id,
                        content = aiResponse,
                        type = MessageType.AI,
                        isProcessing = false,
                        contentType = ContentType.TEXT // AI回复通常是纯文本
                    )

                    withContext(Dispatchers.Main) {
                        updateMessage(aiMessagePlaceholder.id, aiMessage)
                    }

                    // 保存到数据库
                    val chatId = _currentChatId.value ?: return@withContext Result.failure(Exception("没有活动会话"))

                    // 获取用户消息,并确保图片数据被保存
                    val userMessageEntity = MessageEntity(
                        id = userMessage.id,
                        chatId = chatId,
                        content = userMessage.content,
                        type = if (userMessage.type == MessageType.USER) 0 else 1,
                        timestamp = userMessage.timestamp,
                        isError = false,
                        imageData = base64Image,
                        contentType = when(userMessage.contentType) {
                            ContentType.IMAGE -> 1
                            ContentType.IMAGE_WITH_TEXT -> 2
                            ContentType.DOCUMENT -> 3
                            else -> 0
                        },
                        documentSize = userMessage.documentSize,
                        documentType = userMessage.documentType
                    )

                    dbHelper.insertMessage(userMessageEntity)

                    // 处理对话轮次，学习用户偏好
                    processDialogTurn(caption, aiResponse, chatId)

                    // 记录AI消息，用于后续反馈分析
                    feedbackManager.recordAiMessage(aiMessage, chatId)

                    // 检查并生成记忆
                    checkAndGenerateMemory(currentChatId!!)

                    // 检查并更新用户画像
                    checkAndUpdateUserProfile(currentChatId)

                    // 分析并提取人设记忆
                    analyzeAndExtractPersonaMemories(currentChatId)

                    return@withContext Result.success(aiMessage)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "未知错误"
                    Log.e(TAG, "API错误: ${response.code()} - $errorBody")

                    // 更新AI消息为错误消息
                    val errorMessage = Message(
                        id = aiMessagePlaceholder.id,
                        content = "请求失败 (${response.code()}): ${response.message()}\n请检查网络或API设置",
                        type = MessageType.AI,
                        isProcessing = false
                    )

                    withContext(Dispatchers.Main) {
                        updateMessage(aiMessagePlaceholder.id, errorMessage)
                    }

                    return@withContext Result.failure(Exception("API错误: ${response.code()} - $errorBody"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "发送图像消息异常: ${e.message}", e)
                handleError(aiMessagePlaceholder.id, "发生错误: ${e.message ?: "未知错误"}")
                return@withContext Result.failure(e)
            } finally {
                // 恢复原始聊天历史
                chatHistory.clear()
                chatHistory.addAll(tempChatHistory)
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理图像消息失败: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * 处理并发送包含文档的消息
     * @param fileUri 文档URI
     * @param model 模型类型
     * @param customMessage 自定义消息对象，用于提供额外的文档信息
     */
    suspend fun sendDocumentMessage(fileUri: Uri, model: String? = null, customMessage: Message? = null): Result<Message> = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始处理文档消息, URI: $fileUri")

        try {
            // 确保有活动会话
            val currentChatId = _currentChatId.value
            if (currentChatId == null) {
                // 获取当前用户设置的模型或使用传入的模型
                val userModel = model ?: getCurrentModelFromSettings()
                // 如果没有活动会话，创建一个新会话
                val chatId = createNewChat("文档分析", userModel, "")
                Log.d(TAG, "创建新会话: $chatId, 使用模型: $userModel")
            }

            // 初始化文档处理器
            val documentProcessor = DocumentProcessor(context)

            // 获取文件名
            val fileName = documentProcessor.getFileName(fileUri)

            // 使用自定义消息或创建默认的文档消息
            val userMessage = if (customMessage != null) {
                // 使用提供的自定义消息
                customMessage
            } else {
                // 创建默认文档消息
                Message(
                    content = "请分析文档: $fileName",
                    type = MessageType.USER,
                    contentType = ContentType.DOCUMENT
                )
            }

            withContext(Dispatchers.Main) {
                addMessage(userMessage)
            }

            // 添加AI消息占位（显示加载状态）
            val aiMessagePlaceholder = Message(
                content = "",
                type = MessageType.AI,
                isProcessing = true
            )

            withContext(Dispatchers.Main) {
                addMessage(aiMessagePlaceholder)
            }

            // 处理文档，提取文本
            val documentText = documentProcessor.extractTextFromDocument(fileUri)
            if (documentText == null) {
                handleError(aiMessagePlaceholder.id, "文档处理失败")
                return@withContext Result.failure(Exception("文档处理失败"))
            }

            Log.d(TAG, "文档文本提取成功，长度: ${documentText.length}")

            // 准备发送给API的提示信息
            val promptText = """
            请用中文分析以下文档内容并提供摘要。文档类型: ${documentProcessor.getDocumentType(fileUri)}
            文档名称: $fileName
            
            文档内容:
            $documentText
            
            请提供:
            1. 文档要点摘要
            2. 主要内容分析
            3. 如有必要，指出文档中的关键信息
        """.trimIndent()

            // 添加到聊天历史
            chatHistory.add(ChatMessage("user", promptText))

            try {
                // 使用会话的模型类型或用户选择的模型
                val chatModel = getCurrentModelForChat(currentChatId ?: _currentChatId.value!!, model)

                // 构建请求
                val request = ChatGptRequest(
                    model = chatModel, // 使用动态模型
                    messages = chatHistory,
                    temperature = 0.7 // 降低温度以获得更精确的回复
                )

                Log.d(TAG, "发送文档分析请求: ${request.model}")

                // 发送请求
                val response = withTimeoutOrNull(120000) {
                    ApiClient.apiService.sendMessage(
                        ApiClient.getAuthHeader(),
                        request
                    )
                }

                if (response == null) {
                    // 请求超时
                    throw SocketTimeoutException("请求超时，请稍后重试")
                }

                Log.d(TAG, "收到响应: ${response.code()} ${response.message()}")

                if (response.isSuccessful && response.body() != null) {
                    val chatGptResponse = response.body()

                    val aiResponse = chatGptResponse?.choices?.firstOrNull()?.message?.content?.toString()
                        ?: "抱歉，未能分析文档内容。"

                    // 添加到聊天历史
                    chatHistory.add(ChatMessage("assistant", aiResponse))

                    // 更新AI消息（替换占位）
                    val aiMessage = Message(
                        id = aiMessagePlaceholder.id,
                        content = aiResponse,
                        type = MessageType.AI,
                        isProcessing = false
                    )

                    withContext(Dispatchers.Main) {
                        updateMessage(aiMessagePlaceholder.id, aiMessage)
                    }

                    // 记录AI消息，用于后续反馈分析
                    val chatId = _currentChatId.value
                    if (chatId != null) {
                        feedbackManager.recordAiMessage(aiMessage, chatId)
                    }

                    // 处理对话轮次，学习用户偏好
                    processDialogTurn(userMessage.content, aiResponse, currentChatId!!)

                    // 处理完成后生成记忆和更新用户画像
                    checkAndGenerateMemory(currentChatId)
                    checkAndUpdateUserProfile(currentChatId)

                    // 分析并提取人设记忆
                    analyzeAndExtractPersonaMemories(currentChatId)

                    return@withContext Result.success(aiMessage)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "未知错误"
                    Log.e(TAG, "API错误: ${response.code()} - $errorBody")

                    // 更新AI消息为错误消息
                    val errorMessage = Message(
                        id = aiMessagePlaceholder.id,
                        content = "文档分析失败 (${response.code()}): ${response.message()}\n请检查网络或API设置",
                        type = MessageType.AI,
                        isProcessing = false
                    )

                    withContext(Dispatchers.Main) {
                        updateMessage(aiMessagePlaceholder.id, errorMessage)
                    }

                    return@withContext Result.failure(Exception("API错误: ${response.code()} - $errorBody"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "发送文档消息异常: ${e.message}", e)
                handleError(aiMessagePlaceholder.id, "发生错误: ${e.message ?: "未知错误"}")
                return@withContext Result.failure(e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理文档消息失败: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * 导入并分析聊天记录，提取人设信息
     * @param uri 聊天记录文件URI
     * @param chatId 目标聊天ID
     * @return 导入的消息列表
     */
    suspend fun importAndAnalyzeChat(uri: Uri, chatId: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        try {
            // 设置正在分析的状态
            _importAnalysisState.value = true

            // 导入聊天记录
            val importedMessages = ImportUtils.importChatHistory(context, uri, chatId)
            Log.d(TAG, "成功导入 ${importedMessages.size} 条聊天记录")

            // 保存导入的消息
            dbHelper.insertMessages(importedMessages)

            // 将消息转换为API格式，准备分析
            val apiMessages = importedMessages.map { message ->
                ChatMessage(
                    role = if (message.type == 0) "user" else "assistant",
                    content = message.content
                )
            }

            // 启动异步分析AI回复
            GlobalScope.launch {
                try {
                    // 分析AI消息，提取人设特征
                    val success = personaMemorySystem.analyzeImportedChatForPersona(
                        messages = apiMessages,
                        chatId = chatId
                    )

                    if (success) {
                        delay(2000) // 等待人设记忆提取完成

                        // 提取人设记忆并合成人设
                        val personaMemories = dbHelper.getPersonaMemoriesForChat(chatId)
                        if (personaMemories.isNotEmpty()) {
                            // 使用人设提示工程师生成完整的人设
                            val currentChat = dbHelper.getChatById(chatId)
                            val currentPersona = currentChat?.aiPersona ?: ""

                            val generatedPersona = personaPromptEngineer.generatePersonaFromImported(
                                chatId = chatId,
                                basePersona = currentPersona
                            )

                            // 如果生成了有效的人设，更新会话
                            if (generatedPersona.isNotEmpty() &&
                                (currentPersona.isEmpty() || generatedPersona.length > currentPersona.length * 1.2)) {
                                val chat = dbHelper.getChatById(chatId)
                                if (chat != null) {
                                    dbHelper.updateChat(chat.copy(aiPersona = generatedPersona))
                                    Log.d(TAG, "已从导入记录中提取并更新人设")
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "人设提取未能得到足够特征")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "分析导入聊天记录失败: ${e.message}", e)
                } finally {
                    // 完成分析，更新状态
                    _importAnalysisState.value = false
                }
            }

            // 转换并返回导入的消息
            val messages = importedMessages.map { convertEntityToMessage(it) }

            // 如果当前是活动会话，重新加载消息
            if (_currentChatId.value == chatId) {
                // 清除缓存
                messageCache.clearChatCache(chatId)
                // 重置分页状态
                pagingManager.reset()
                // 加载消息
                switchChat(chatId)
            }

            return@withContext Result.success(messages)
        } catch (e: Exception) {
            _importAnalysisState.value = false
            Log.e(TAG, "导入并分析聊天记录失败: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * 删除指定ID的消息及其后续所有消息
     */
    suspend fun deleteMessagesById(messageIds: List<String>) = withContext(Dispatchers.IO) {
        try {
            val chatId = _currentChatId.value ?: throw Exception("没有活动会话")

            // 从数据库中删除这些消息
            for (messageId in messageIds) {
                val message = dbHelper.getMessageById(messageId, chatId)
                if (message != null) {
                    dbHelper.deleteMessage(message)
                }
            }

            // 清除缓存中的这些消息
            for (messageId in messageIds) {
                val cachedMessage = messageCache.getMessage(chatId, messageId)
                if (cachedMessage != null) {
                    messageCache.clearChatCache(chatId)
                    break // 清除整个会话缓存更高效
                }
            }

            // 重置分页状态
            pagingManager.reset()

            // 重新加载当前会话的消息
            loadCurrentChatMessages()

            // 清理聊天历史中的相应消息，重新加载所有消息
            Log.d(TAG, "已删除 ${messageIds.size} 条消息，重新加载会话")

        } catch (e: Exception) {
            Log.e(TAG, "删除消息失败: ${e.message}", e)
            throw e
        }
    }

    /**
     * 删除单条消息
     * @param message 要删除的消息实体
     */
    suspend fun deleteMessage(messageEntity: MessageEntity) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始删除消息: ${messageEntity.id}")

                // 从数据库删除消息
                dbHelper.deleteMessage(messageEntity)

                // 更新内存中的消息列表
                val currentMessages = _messages.value.toMutableList()
                val messageToRemove = currentMessages.find { it.id == messageEntity.id }

                if (messageToRemove != null) {
                    currentMessages.remove(messageToRemove)
                    withContext(Dispatchers.Main) {
                        _messages.value = currentMessages
                    }

                    // 从缓存中删除
                    messageCache.clearChatCache(messageEntity.chatId)

                    // 从聊天历史中删除(用于API请求)
                    val index = chatHistory.indexOfFirst {
                        (it.role == "user" && messageEntity.type == 0) ||
                                (it.role == "assistant" && messageEntity.type == 1) &&
                                it.content == messageEntity.content
                    }
                    if (index != -1) {
                        chatHistory.removeAt(index)
                    }
                }

                // 更新会话最后更新时间
                val chatId = messageEntity.chatId
                val chat = dbHelper.getChatById(chatId) ?: return@withContext
                dbHelper.updateChat(chat.copy(updatedAt = Date()))

                Log.d(TAG, "消息 ${messageEntity.id} 已删除")
            } catch (e: Exception) {
                Log.e(TAG, "删除消息失败: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * 获取聊天记忆
     */
    fun getMemoriesForChat(chatId: String): Flow<List<MemoryEntity>> {
        return dbHelper.getMemoriesForChat(chatId)
    }

    /**
     * 删除指定记忆
     */
    suspend fun deleteMemory(memoryId: String) {
        dbHelper.deleteMemory(memoryId)
    }

    /**
     * 获取与用户输入相关的记忆
     */
    suspend fun getRelevantMemories(userInput: String, limit: Int = 3): List<MemoryEntity> {
        val chatId = _currentChatId.value ?: return emptyList()
        val allMemories = dbHelper.getMemoriesForChatAsList(chatId)
        return memoryRelevanceService.findRelevantMemories(userInput, allMemories, limit)
    }

    /**
     * 构建记忆提示信息
     */
    private fun buildMemoryPrompt(memories: List<MemoryEntity>): String {
        val sb = StringBuilder("以下是此前对话中的重要信息，请参考这些信息回答用户的问题：\n\n")

        memories.forEachIndexed { index, memory ->
            // 为分类和重要性添加标签
            val importanceTag = when {
                memory.importance >= 8 -> "[重要]"
                memory.importance >= 5 -> ""
                else -> "[参考]"
            }

            // 特别标记情感和关系类记忆
            val categoryTag = when (memory.category) {
                "情感关系" -> "[情感]"
                "伴侣婚姻" -> "[婚恋]"
                "家庭亲子" -> "[家庭]"
                "社交社区" -> "[社交]"
                else -> memory.category.takeIf { it.isNotEmpty() }?.let { "[$it]" } ?: ""
            }

            // 添加带有分类和重要性标记的记忆
            sb.append("${index + 1}. $importanceTag$categoryTag ${memory.content}\n")
        }

        sb.append("\n请在回答中自然地利用这些信息，尤其是关于用户情感和关系的内容，不要明确提及你在使用历史记忆。")
        return sb.toString()
    }

    /**
     * 检查并生成记忆
     */
    private suspend fun checkAndGenerateMemory(chatId: String) {
        // 增加消息计数
        messageCountSinceLastMemory += 2  // 用户消息和AI回复各算一条

        // 每5轮对话(即10条消息)生成一次记忆
        if (messageCountSinceLastMemory >= 10) {
            Log.d(TAG, "已达到记忆生成阈值，准备生成记忆")

            // 获取所有消息
            val allMessages = dbHelper.getMessagesForChatList(chatId)

            // 确保有足够的消息
            if (allMessages.size >= 6) {
                // 获取上次处理的消息ID
                val lastMsgId = lastProcessedMessageIds[chatId]

                // 找出未处理的消息范围
                val startIndex = if (lastMsgId != null) {
                    val lastIndex = allMessages.indexOfFirst { it.id == lastMsgId }
                    if (lastIndex != -1) lastIndex + 1 else 0
                } else {
                    // 如果是第一次处理，获取最后10条消息
                    val startPos = maxOf(0, allMessages.size - 10)
                    startPos
                }

                if (startIndex < allMessages.size) {
                    // 获取需要总结的消息
                    val messagesToSummarize = allMessages.subList(startIndex, allMessages.size)

                    if (messagesToSummarize.isNotEmpty()) {
                        Log.d(TAG, "开始生成记忆，处理 ${messagesToSummarize.size} 条消息")

                        // 生成记忆
                        val memoryResult = generateMemory(chatId, messagesToSummarize)

                        if (memoryResult.isSuccess) {
                            // 记录最后处理的消息ID
                            lastProcessedMessageIds[chatId] = allMessages.last().id

                            // 重置消息计数
                            messageCountSinceLastMemory = 0

                            // 保存更新后的记忆状态
                            saveMemoryCounterState(chatId)

                            Log.d(TAG, "记忆生成成功，重置计数器")
                        } else {
                            Log.e(TAG, "记忆生成失败: ${memoryResult.exceptionOrNull()?.message}")
                        }
                    }
                }
            }
        }

        // 保存计数器状态，确保不会因为应用崩溃而丢失计数
        saveMemoryCounterState(chatId)
    }

    /**
     * 生成记忆
     */
    private suspend fun generateMemory(chatId: String, messages: List<MessageEntity>): Result<MemoryEntity> {
        return withContext(Dispatchers.IO) {
            try {
                // 转换为API消息格式
                val apiMessages = messages.map { message ->
                    ChatMessage(
                        role = if (message.type == 0) "user" else "assistant",
                        content = message.content
                    )
                }

                Log.d(TAG, "调用API生成增强记忆")

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

                Log.d(TAG, "增强记忆已保存: 内容='${memory.content}', 类别='${memory.category}', 重要性=${memory.importance}, 关键词=${memory.keywords}")

                Result.success(memory)
            } catch (e: Exception) {
                Log.e(TAG, "生成记忆失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 分析并提取人设记忆
     */
    private suspend fun analyzeAndExtractPersonaMemories(chatId: String) {
        // 增加消息计数
        messageCountSinceLastPersonaAnalysis += 2  // 用户消息和AI回复各算一条

        // 每3轮对话分析人设记忆
        if (messageCountSinceLastPersonaAnalysis >= PERSONA_MEMORY_ANALYSIS_INTERVAL) {
            Log.d(TAG, "已达到人设记忆分析阈值，准备提取人设记忆")

            // 异步处理人设记忆提取，不阻塞主流程
            GlobalScope.launch {
                try {
                    // 获取最近的消息
                    val allMessages = dbHelper.getMessagesForChatList(chatId)
                    if (allMessages.size < 4) return@launch

                    // 转换为API消息格式
                    val recentMessages = allMessages.takeLast(10).map { message ->
                        ChatMessage(
                            role = if (message.type == 0) "user" else "assistant",
                            content = message.content
                        )
                    }

                    // 提取人设记忆
                    personaMemorySystem.analyzeAndExtractPersonaMemories(
                        messages = recentMessages,
                        chatId = chatId
                    )

                    // 重置计数器
                    messageCountSinceLastPersonaAnalysis = 0

                    // 保存更新后的状态
                    saveMemoryCounterState(chatId)

                    // 每隔5次人设记忆提取，尝试整合记忆
                    val totalMessageCount = dbHelper.getMessageCountForChat(chatId)
                    if (totalMessageCount % 30 == 0) {
                        personaMemorySystem.consolidatePersonaMemories(chatId)

                        // 尝试优化人设提示
                        val aiPersona = dbHelper.getChatById(chatId)?.aiPersona
                        if (!aiPersona.isNullOrEmpty()) {
                            val optimizedPersona = personaPromptEngineer.optimizePersonaPrompt(
                                chatId,
                                aiPersona
                            )

                            // 如果人设有明显改进，更新会话中的人设
                            if (optimizedPersona.length > aiPersona.length * 1.2) {
                                val chat = dbHelper.getChatById(chatId)
                                if (chat != null) {
                                    dbHelper.updateChat(chat.copy(aiPersona = optimizedPersona))
                                    Log.d(TAG, "人设已自动优化，长度从 ${aiPersona.length} 增加到 ${optimizedPersona.length}")
                                }
                            }
                        }
                    }

                    Log.d(TAG, "人设记忆分析完成，重置计数器")
                } catch (e: Exception) {
                    Log.e(TAG, "人设记忆分析失败: ${e.message}", e)

                    // 即使失败也重置计数器，以避免卡住
                    messageCountSinceLastPersonaAnalysis = 0
                    saveMemoryCounterState(chatId)
                }
            }
        } else {
            // 每次都保存计数器状态，确保不会因为应用崩溃而丢失计数
            saveMemoryCounterState(chatId)
        }
    }

    /**
     * 检查并更新用户画像
     */
    private suspend fun checkAndUpdateUserProfile(chatId: String) {
        // 获取当前对话轮数
        val turns = dialogTurnsCounter.getOrPut(chatId) { 0 }

        // 增加对话轮数
        dialogTurnsCounter[chatId] = turns + 1

        try {
            // 获取所有消息
            val allMessages = dbHelper.getMessagesForChatList(chatId)

            // 转换为API消息格式
            val apiMessages = allMessages.map { message ->
                ChatMessage(
                    role = if (message.type == 0) "user" else "assistant",
                    content = message.content
                )
            }

            // 使用新的用户画像系统检查并更新
            val profile = userProfileSystem.checkAndUpdateUserProfile(
                chatId = chatId,
                messages = apiMessages,
                dialogTurns = turns + 1,
                forceUpdate = false
            )

            // 如果有更新的画像，更新聊天历史中的系统消息
            if (profile != null) {
                // 更新缓存
                userProfiles[chatId] = profile

                // 更新聊天历史
                updateUserProfileInChatHistory(profile.toSystemPrompt())
                Log.d(TAG, "用户画像已更新：${profile.summary.take(50)}...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新用户画像失败: ${e.message}", e)
        }
    }

    /**
     * 处理消息对，提取用户偏好反馈
     * 在每轮对话结束后调用
     */
    private suspend fun processDialogTurn(userMessage: String, aiResponse: String, chatId: String) {
        try {
            userProfileSystem.processDialogTurn(userMessage, aiResponse, chatId)
        } catch (e: Exception) {
            Log.e(TAG, "处理对话轮次失败: ${e.message}", e)
        }
    }

    /**
     * 更新聊天历史中的用户画像
     */
    private fun updateUserProfileInChatHistory(profileContent: String) {
        // 移除现有的用户画像（如果有）
        val profileIndex = chatHistory.indexOfFirst {
            it.role == "system" && it.content is String && (it.content as String).startsWith("用户画像：")
        }

        if (profileIndex != -1) {
            chatHistory.removeAt(profileIndex)
        }

        // 添加新的用户画像
        val systemMsgIndex = chatHistory.indexOfFirst {
            it.role == "system" && (it.content is String) && !(it.content as String).startsWith("用户画像：")
        }

        if (systemMsgIndex != -1) {
            // 在第一个非画像系统消息之后插入
            chatHistory.add(systemMsgIndex + 1, ChatMessage("system", profileContent))
        } else {
            // 没有系统消息，直接添加在最前面
            chatHistory.add(0, ChatMessage("system", profileContent))
        }
    }

    /**
     * 限制聊天历史长度，保持在上下文窗口大小内
     */
    private fun ensureContextWindowSize() {
        // 找出所有系统消息
        val systemMessages = chatHistory.filter { it.role == "system" }

        // 获取所有非系统消息
        val nonSystemMessages = chatHistory.filter { it.role != "system" }

        // 如果非系统消息超过上下文窗口限制，则移除最老的消息
        if (nonSystemMessages.size > MAX_CONTEXT_WINDOW_SIZE * 2) {
            Log.d(TAG, "聊天历史超出上下文窗口大小，进行裁剪")

            // 只保留最近的非系统消息
            val messagesToKeep = nonSystemMessages.takeLast(MAX_CONTEXT_WINDOW_SIZE * 2)

            // 清空当前聊天历史
            chatHistory.clear()

            // 重新添加系统消息和最近的非系统消息
            chatHistory.addAll(systemMessages)
            chatHistory.addAll(messagesToKeep)

            Log.d(TAG, "裁剪后的聊天历史: ${chatHistory.size} 条消息，其中系统消息 ${systemMessages.size} 条")
        }
    }

    /**
     * 删除多条消息
     */
    suspend fun deleteMessages(messages: List<MessageEntity>) {
        withContext(Dispatchers.IO) {
            try {
                val chatId = _currentChatId.value ?: return@withContext

                // 在数据库操作前，先从内存中的消息列表中移除
                val currentList = _messages.value.toMutableList()
                val messageIds = messages.map { it.id }
                currentList.removeAll { it.id in messageIds }
                _messages.value = currentList

                // 从聊天历史中移除相应的消息
                for (i in chatHistory.size - 1 downTo 0) {
                    if (i < chatHistory.size) {  // 防止越界
                        val chatMsg = chatHistory[i]
                        // 在API历史中，我们通过内容匹配(因为API消息没有ID)
                        // 这不是100%准确，但在大多数情况下足够了
                        if (messages.any { it.content == chatMsg.content }) {
                            chatHistory.removeAt(i)
                        }
                    }
                }

                // 从缓存中删除消息
                for (messageId in messageIds) {
                    messageCache.clearChatCache(chatId) // 清除整个缓存更简单
                    break
                }

                // 从数据库中删除
                val db = dbHelper.writableDatabase
                db.beginTransaction()
                try {
                    for (entity in messages) {
                        dbHelper.deleteMessage(entity)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }

                // 更新会话时间
                val chat = dbHelper.getChatById(chatId)
                if (chat != null) {
                    dbHelper.updateChat(chat.copy(updatedAt = Date()))
                }

                Log.d(TAG, "已删除 ${messages.size} 条消息")
            } catch (e: Exception) {
                Log.e(TAG, "删除消息失败: ${e.message}", e)
            }
        }
    }

    /**
     * 恢复之前的消息列表
     */
    suspend fun restoreMessages(messages: List<Message>) {
        withContext(Dispatchers.IO) {
            val chatId = _currentChatId.value ?: return@withContext

            // 先删除当前所有消息
            dbHelper.deleteAllMessagesForChat(chatId)

            // 转换为消息实体
            val messageEntities = messages.map { msg ->
                MessageEntity(
                    id = msg.id,
                    chatId = chatId,
                    content = msg.content,
                    type = if (msg.type == MessageType.USER) 0 else 1,
                    timestamp = msg.timestamp,
                    isError = false,
                    imageData = msg.imageData,
                    contentType = when(msg.contentType) {
                        ContentType.IMAGE -> 1
                        ContentType.IMAGE_WITH_TEXT -> 2
                        ContentType.DOCUMENT -> 3
                        else -> 0
                    },
                    documentSize = msg.documentSize,
                    documentType = msg.documentType
                )
            }

            // 批量插入恢复的消息
            dbHelper.insertMessages(messageEntities)

            // 更新内存中的消息列表
            _messages.value = messages

            // 重新缓存消息
            messageCache.clearChatCache(chatId)
            messageCache.cacheMessages(chatId, messages)

            // 重置分页状态
            pagingManager.reset()

            // 更新聊天历史
            chatHistory.clear()
            if (messageEntities.isNotEmpty()) {
                rebuildChatHistoryWithContextWindow(messageEntities)
            }
        }
    }

    /**
     * 发送用户消息并获取AI回复
     */
    suspend fun sendMessage(content: String, model: String = "gpt-4o-mini-ca"): Result<Message> = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始发送消息: $content, 使用模型: $model")

        // 检查是否是文档分析格式
        val documentPattern = Pattern.compile("分析文档：(.*?)(\\.txt)?$")
        val matcher = documentPattern.matcher(content)

        // 内容和类型预处理
        val contentType: ContentType
        val finalContent: String

        if (matcher.find()) {
            // 是文档格式，提取标题
            finalContent = matcher.group(1)
            contentType = ContentType.DOCUMENT
            Log.d(TAG, "检测到文档格式消息，提取标题: $finalContent")
        } else {
            // 普通消息
            finalContent = content
            contentType = ContentType.TEXT
        }

        // 确保有活动会话
        val currentChatId = _currentChatId.value
        if (currentChatId == null) {
            // 如果没有活动会话，创建一个新会话
            val chatId = createNewChat("新对话", model, "")
            Log.d(TAG, "创建新会话: $chatId, 使用模型: $model")
        }

        // 获取相关记忆
        val chatId = _currentChatId.value ?: return@withContext Result.failure(Exception("没有活动会话"))
        val allMemories = dbHelper.getMemoriesForChatAsList(chatId)
        val relevantMemories = memoryRelevanceService.findRelevantMemories(content, allMemories)

        Log.d(TAG, "找到相关记忆 ${relevantMemories.size} 条")

        // 获取相关人设记忆
        val personaMemories = dbHelper.getPersonaMemoriesForChat(chatId)
        Log.d(TAG, "找到人设记忆 ${personaMemories.size} 条")

        // 添加用户消息
        val userMessage = Message(
            content = finalContent,
            type = MessageType.USER,
            contentType = contentType  // 使用确定的内容类型
        )

        withContext(Dispatchers.Main) {
            addMessage(userMessage)
        }

        // 处理潜在的反馈
        try {
            val result = feedbackManager.processUserMessage(userMessage, chatId)
            if (result) {
                Log.d(TAG, "已处理用户消息中的反馈")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理用户反馈失败: ${e.message}", e)
        }

        // 如果是文档类型，直接返回，不需要AI回复
        if (contentType == ContentType.DOCUMENT) {
            return@withContext Result.success(userMessage)
        }

        // 添加到聊天历史
        chatHistory.add(ChatMessage("user", content))

        // 确保聊天历史在上下文窗口大小内
        ensureContextWindowSize()

        // 如果有相关记忆，添加系统消息
        val memoryPrompt = if (relevantMemories.isNotEmpty()) {
            buildMemoryPrompt(relevantMemories)
        } else ""

        if (memoryPrompt.isNotEmpty()) {
            Log.d(TAG, "添加记忆提示: ${memoryPrompt.take(100)}...")
            chatHistory.add(ChatMessage("system", memoryPrompt))
        }

        // 添加AI消息占位（显示加载状态）
        val aiMessagePlaceholder = Message(
            content = "",
            type = MessageType.AI,
            isProcessing = true
        )

        withContext(Dispatchers.Main) {
            addMessage(aiMessagePlaceholder)
        }

        try {
            // 获取当前会话的人设
            val chatDetails = dbHelper.getChatById(chatId)
            val aiPersona = chatDetails?.aiPersona ?: ""

            // 使用人设上下文管理器增强聊天历史
            val historyForRequest = if (aiPersona.isNotEmpty()) {
                // 如果有人设，使用上下文增强
                personaContextManager.enhanceWithPersonaContext(
                    messages = chatHistory,
                    chatId = chatId,
                    personaBase = aiPersona,
                    personaMemories = personaMemories
                )
            } else {
                // 否则使用原始聊天历史
                chatHistory
            }

            // 构建请求
            val request = ChatGptRequest(
                model = model,
                messages = historyForRequest,
                temperature = 1.1
            )

            Log.d(TAG, "发送请求: ${request.model}, 消息数量: ${request.messages.size}")

            // 使用超时
            val response = withTimeoutOrNull(120000) {
                ApiClient.apiService.sendMessage(
                    ApiClient.getAuthHeader(),
                    request
                )
            }

            if (response == null) {
                // 请求超时
                throw SocketTimeoutException("请求超时，请稍后重试")
            }

            Log.d(TAG, "收到响应: ${response.code()} ${response.message()}")

            if (response.isSuccessful && response.body() != null) {
                val chatGptResponse = response.body()
                Log.d(TAG, "响应内容: 状态码=${chatGptResponse?.`object`}, 模型=${chatGptResponse?.model}")

                var aiResponse = chatGptResponse?.choices?.firstOrNull()?.message?.content?.toString()
                    ?: "抱歉，未能生成回复。"

                // 如果有人设，验证并修正人设一致性
                if (aiPersona.isNotEmpty() && settingsManager.strictPersonaMode) {
                    val correctedResponse = personaConsistencyValidator.validateAndCorrect(
                        userMessage = content,
                        aiResponse = aiResponse,
                        persona = aiPersona
                    )

                    if (correctedResponse != aiResponse) {
                        Log.d(TAG, "AI回复已经过人设一致性修正")
                        aiResponse = correctedResponse
                    }
                }

                // 尝试根据用户反馈优化响应
                try {
                    val optimizedResponse = feedbackManager.optimizeResponse(
                        chatId,
                        aiResponse,
                        content
                    )
                    if (optimizedResponse != aiResponse) {
                        Log.d(TAG, "已根据用户反馈优化回复")
                        aiResponse = optimizedResponse
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "优化响应失败: ${e.message}", e)
                }

                // 添加到聊天历史
                chatHistory.add(ChatMessage("assistant", aiResponse))

                // 如果有添加记忆提示，需要在回复后移除，避免影响后续对话
                if (memoryPrompt.isNotEmpty()) {
                    // 移除记忆提示
                    chatHistory.removeIf { it.role == "system" && it.content == memoryPrompt }
                }

                // 更新AI消息（替换占位）
                val aiMessage = Message(
                    id = aiMessagePlaceholder.id,
                    content = aiResponse,
                    type = MessageType.AI,
                    isProcessing = false
                )

                withContext(Dispatchers.Main) {
                    updateMessage(aiMessagePlaceholder.id, aiMessage)
                }

                // 记录AI消息，用于后续反馈分析
                feedbackManager.recordAiMessage(aiMessage, chatId)

                // 更新聊天标题（如果是第一条消息）
                withContext(Dispatchers.IO) {
                    val currentChatId = _currentChatId.value ?: return@withContext
                    val messageCount = dbHelper.getMessageCountForChat(currentChatId)

                    // 如果只有2条消息（用户和AI各一条），则使用用户消息作为标题
                    if (messageCount <= 2) {
                        var title = content
                        if (title.length > 20) {
                            title = title.substring(0, 20) + "..."
                        }
                        updateChatTitle(currentChatId, title)
                    }

                    // 处理对话轮次，学习用户偏好
                    processDialogTurn(content, aiResponse, currentChatId)

                    // 检查并生成记忆
                    checkAndGenerateMemory(currentChatId)

                    // 检查并更新用户画像
                    checkAndUpdateUserProfile(currentChatId)

                    // 分析并提取人设记忆
                    analyzeAndExtractPersonaMemories(currentChatId)
                }

                return@withContext Result.success(aiMessage)
            } else {
                val errorBody = response.errorBody()?.string() ?: "未知错误"
                Log.e(TAG, "API错误: ${response.code()} - $errorBody")

                // 更新AI消息为错误消息
                val errorMessage = Message(
                    id = aiMessagePlaceholder.id,
                    content = "请求失败 (${response.code()}): ${response.message()}\n请检查网络或API设置",
                    type = MessageType.AI,
                    isProcessing = false
                )

                withContext(Dispatchers.Main) {
                    updateMessage(aiMessagePlaceholder.id, errorMessage)
                }

                return@withContext Result.failure(Exception("API错误: ${response.code()} - $errorBody"))
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "连接超时: ${e.message}", e)
            handleError(aiMessagePlaceholder.id, "连接超时，请检查网络并重试")
            return@withContext Result.failure(e)
        } catch (e: UnknownHostException) {
            Log.e(TAG, "无法解析主机: ${e.message}", e)
            handleError(aiMessagePlaceholder.id, "无法连接到服务器，请检查网络连接")
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "发送消息异常: ${e.message}", e)
            handleError(aiMessagePlaceholder.id, "发生错误: ${e.message ?: "未知错误"}")
            return@withContext Result.failure(e)
        }
    }

    /**
     * 处理错误，更新UI
     */
    private suspend fun handleError(placeholderId: String, errorMessage: String) {
        withContext(Dispatchers.Main) {
            updateMessage(
                placeholderId,
                Message(
                    id = placeholderId,
                    content = errorMessage,
                    type = MessageType.AI,
                    isProcessing = false
                )
            )
        }
    }

    /**
     * 获取聊天的所有消息
     * @param chatId 聊天ID
     * @return 消息列表
     */
    suspend fun getChatMessages(chatId: String): List<MessageEntity> {
        return dbHelper.getMessagesForChatList(chatId)
    }

    /**
     * 提交显式反馈(用于显式反馈按钮，如点赞/踩)
     */
    suspend fun submitExplicitFeedback(
        messageId: String,
        isPositive: Boolean,
        aspect: String = "整体表现"
    ): Boolean {
        val chatId = _currentChatId.value ?: return false
        return feedbackManager.submitExplicitFeedback(
            chatId,
            messageId,
            UUID.randomUUID().toString(),  // 生成一个虚拟的用户消息ID
            isPositive,
            aspect
        )
    }

    /**
     * 获取特定聊天的所有人设记忆
     * @param chatId 聊天ID
     * @return 人设记忆列表
     */
    suspend fun getPersonaMemoriesForChat(chatId: String): List<PersonaMemoryEntity> {
        return dbHelper.getPersonaMemoriesForChat(chatId)
    }

    /**
     * 手动编辑并保存人设
     * @param chatId 聊天ID
     * @param newPersona 新的人设内容
     * @return 是否成功
     */
    suspend fun updatePersona(chatId: String, newPersona: String): Boolean {
        return try {
            // 获取当前聊天实体
            val chat = dbHelper.getChatById(chatId) ?: return false

            // 使用人设优化器完善人设
            val enhancedPersona = personaPromptEngineer.enhanceManualPersona(newPersona)

            // 更新聊天实体
            val updatedChat = chat.copy(aiPersona = enhancedPersona)
            dbHelper.updateChat(updatedChat)

            // 如果是当前会话，更新聊天历史中的人设
            if (chatId == _currentChatId.value) {
                // 移除现有的人设（如果有）
                val personaIndex = chatHistory.indexOfFirst {
                    it.role == "system" && it.content is String &&
                            !(it.content as String).startsWith("用户画像：") &&
                            !(it.content as String).startsWith("以下是此前对话中的重要信息")
                }

                if (personaIndex != -1) {
                    chatHistory.removeAt(personaIndex)
                }

                // 添加新的人设消息
                chatHistory.add(0, ChatMessage("system", enhancedPersona))

                Log.d(TAG, "聊天历史中的人设已更新")
            }

            Log.d(TAG, "人设已更新: chatId=$chatId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新人设失败: ${e.message}", e)
            false
        }
    }

    /**
     * 跟踪导入分析的状态
     * @return 当前是否正在进行导入分析
     */
    fun isImportAnalysisInProgress(): Boolean {
        return _importAnalysisState.value
    }
}

package com.example.chatapp.data

import android.content.Context
import android.util.Log
import com.example.chatapp.data.db.ChatDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MessagePagingManager(private val context: Context) {
    private val dbHelper = ChatDatabaseHelper.getInstance(context)
    private val TAG = "MessagePagingManager"

    companion object {
        const val PAGE_SIZE = 30 // 每页加载的消息数量
        const val INITIAL_LOAD_SIZE = 50 // 初始加载的消息数量
    }

    // 当前加载状态
    enum class LoadingState {
        IDLE, LOADING_NEWER, LOADING_OLDER, REFRESHING
    }

    private val _loadingState = MutableStateFlow(LoadingState.IDLE)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    private val _hasMoreOlder = MutableStateFlow(true)
    val hasMoreOlder: StateFlow<Boolean> = _hasMoreOlder.asStateFlow()

    private val _hasMoreNewer = MutableStateFlow(false)
    val hasMoreNewer: StateFlow<Boolean> = _hasMoreNewer.asStateFlow()

    // 跟踪第一个和最后一个加载的消息ID
    private var firstLoadedMessageId: String? = null
    private var lastLoadedMessageId: String? = null

    // 跟踪已加载的消息总数
    private var loadedMessageCount = 0
    private var totalMessageCount = 0

    // 缓存最近加载的消息ID
    private val loadedMessageIds = mutableListOf<String>()

    // 初始化加载 - 加载最新的消息
    suspend fun initialLoad(chatId: String): List<Message> = withContext(Dispatchers.IO) {
        _loadingState.value = LoadingState.REFRESHING

        try {
            // 获取该会话的总消息数
            totalMessageCount = dbHelper.getMessageCountForChat(chatId)
            Log.d(TAG, "会话 $chatId 的总消息数: $totalMessageCount")

            // 计算偏移量以加载最新的一批消息
            val offset = maxOf(0, totalMessageCount - INITIAL_LOAD_SIZE)

            // 加载最新的一批消息
            val messages = dbHelper.getMessagesForChatPaged(chatId, INITIAL_LOAD_SIZE, offset)
            Log.d(TAG, "初始加载 ${messages.size} 条消息，偏移量: $offset")

            // 转换为应用层消息
            val result = messages.map { entity ->
                Message(
                    id = entity.id,
                    content = entity.content,
                    type = if (entity.type == 0) MessageType.USER else MessageType.AI,
                    timestamp = entity.timestamp,
                    isProcessing = false,
                    imageData = entity.imageData,
                    contentType = when(entity.contentType) {
                        1 -> ContentType.IMAGE
                        2 -> ContentType.IMAGE_WITH_TEXT
                        3 -> ContentType.DOCUMENT
                        else -> ContentType.TEXT
                    },
                    // 添加这两行
                    documentSize = entity.documentSize,
                    documentType = entity.documentType
                )
            }

            // 更新加载状态
            if (result.isNotEmpty()) {
                firstLoadedMessageId = result.first().id
                lastLoadedMessageId = result.last().id
                loadedMessageCount = result.size

                // 缓存已加载的消息ID
                loadedMessageIds.clear()
                loadedMessageIds.addAll(result.map { it.id })

                Log.d(TAG, "设置首条消息ID: $firstLoadedMessageId, 末条消息ID: $lastLoadedMessageId")
            }

            // 更新是否有更多较旧消息的标志
            _hasMoreOlder.value = offset > 0
            _hasMoreNewer.value = false // 初始加载是最新的消息

            Log.d(TAG, "是否有更多旧消息: ${_hasMoreOlder.value}, 是否有更多新消息: ${_hasMoreNewer.value}")

            _loadingState.value = LoadingState.IDLE
            result
        } catch (e: Exception) {
            Log.e(TAG, "初始加载消息失败: ${e.message}", e)
            _loadingState.value = LoadingState.IDLE
            emptyList()
        }
    }

    // 加载更多较旧的消息
    suspend fun loadOlderMessages(chatId: String): List<Message> = withContext(Dispatchers.IO) {
        if (_loadingState.value != LoadingState.IDLE || !_hasMoreOlder.value || firstLoadedMessageId == null) {
            Log.d(TAG, "跳过加载旧消息: 当前状态=${_loadingState.value}, 是否有旧消息=${_hasMoreOlder.value}, 首条消息ID=$firstLoadedMessageId")
            return@withContext emptyList<Message>()
        }

        _loadingState.value = LoadingState.LOADING_OLDER
        Log.d(TAG, "开始加载较旧消息，基于消息ID: $firstLoadedMessageId")

        try {
            // 加载第一条已加载消息之前的消息
            val olderMessages = dbHelper.getMessagesBefore(chatId, firstLoadedMessageId!!, PAGE_SIZE)
            Log.d(TAG, "加载到 ${olderMessages.size} 条较旧消息")

            // 转换为应用层消息
            val result = olderMessages.map { entity ->
                Message(
                    id = entity.id,
                    content = entity.content,
                    type = if (entity.type == 0) MessageType.USER else MessageType.AI,
                    timestamp = entity.timestamp,
                    isProcessing = false,
                    imageData = entity.imageData,  // 确保包含图片数据
                    contentType = when(entity.contentType) {  // 正确处理所有内容类型
                        1 -> ContentType.IMAGE
                        2 -> ContentType.IMAGE_WITH_TEXT
                        3 -> ContentType.DOCUMENT
                        else -> ContentType.TEXT
                    }
                )
            }

            // 更新加载状态
            if (result.isNotEmpty()) {
                firstLoadedMessageId = result.first().id
                loadedMessageCount += result.size

                // 缓存已加载的消息ID
                loadedMessageIds.addAll(0, result.map { it.id })

                Log.d(TAG, "更新首条消息ID: $firstLoadedMessageId, 已加载消息数: $loadedMessageCount")
            }

            // 检查是否还有更多较旧的消息
            _hasMoreOlder.value = loadedMessageCount < totalMessageCount
            Log.d(TAG, "更新是否有更多旧消息: ${_hasMoreOlder.value}")

            _loadingState.value = LoadingState.IDLE
            result
        } catch (e: Exception) {
            Log.e(TAG, "加载较旧消息失败: ${e.message}", e)
            _loadingState.value = LoadingState.IDLE
            emptyList()
        }
    }

    // 加载更多较新的消息
    suspend fun loadNewerMessages(chatId: String): List<Message> = withContext(Dispatchers.IO) {
        if (_loadingState.value != LoadingState.IDLE || !_hasMoreNewer.value || lastLoadedMessageId == null) {
            Log.d(TAG, "跳过加载新消息: 当前状态=${_loadingState.value}, 是否有新消息=${_hasMoreNewer.value}, 末条消息ID=$lastLoadedMessageId")
            return@withContext emptyList<Message>()
        }

        _loadingState.value = LoadingState.LOADING_NEWER
        Log.d(TAG, "开始加载较新消息，基于消息ID: $lastLoadedMessageId")

        try {
            // 加载最后一条已加载消息之后的消息
            val newerMessages = dbHelper.getMessagesAfter(chatId, lastLoadedMessageId!!, PAGE_SIZE)
            Log.d(TAG, "加载到 ${newerMessages.size} 条较新消息")

            // 转换为应用层消息
            val result = newerMessages.map { entity ->
                Message(
                    id = entity.id,
                    content = entity.content,
                    type = if (entity.type == 0) MessageType.USER else MessageType.AI,
                    timestamp = entity.timestamp,
                    isProcessing = false,
                    imageData = entity.imageData,  // 确保包含图片数据
                    contentType = when(entity.contentType) {  // 正确处理所有内容类型
                        1 -> ContentType.IMAGE
                        2 -> ContentType.IMAGE_WITH_TEXT
                        3 -> ContentType.DOCUMENT
                        else -> ContentType.TEXT
                    }
                )
            }

            // 更新加载状态
            if (result.isNotEmpty()) {
                lastLoadedMessageId = result.last().id
                loadedMessageCount += result.size

                // 缓存已加载的消息ID
                loadedMessageIds.addAll(result.map { it.id })

                Log.d(TAG, "更新末条消息ID: $lastLoadedMessageId, 已加载消息数: $loadedMessageCount")
            }

            // 检查是否还有更多较新的消息
            _hasMoreNewer.value = loadedMessageCount < totalMessageCount
            Log.d(TAG, "更新是否有更多新消息: ${_hasMoreNewer.value}")

            _loadingState.value = LoadingState.IDLE
            result
        } catch (e: Exception) {
            Log.e(TAG, "加载较新消息失败: ${e.message}", e)
            _loadingState.value = LoadingState.IDLE
            emptyList()
        }
    }

    // 处理新消息添加
    fun handleNewMessage(message: Message) {
        // 在内存中更新最后加载的消息ID
        lastLoadedMessageId = message.id
        loadedMessageCount++
        totalMessageCount++

        // 缓存新消息ID
        loadedMessageIds.add(message.id)

        // 重置标志
        _hasMoreNewer.value = false

        Log.d(TAG, "处理新消息: id=${message.id}, 更新已加载消息数: $loadedMessageCount, 总消息数: $totalMessageCount")
    }

    // 重置分页状态
    fun reset() {
        firstLoadedMessageId = null
        lastLoadedMessageId = null
        loadedMessageCount = 0
        totalMessageCount = 0
        _hasMoreOlder.value = true
        _hasMoreNewer.value = false
        _loadingState.value = LoadingState.IDLE
        loadedMessageIds.clear()

        Log.d(TAG, "重置分页状态")
    }
}
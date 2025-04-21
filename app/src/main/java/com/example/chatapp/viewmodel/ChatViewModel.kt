package com.example.chatapp.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.api.ApiClient
import com.example.chatapp.api.ChatGptRequest
import com.example.chatapp.api.ChatMessage
import com.example.chatapp.data.ContentType
import com.example.chatapp.data.Message
import com.example.chatapp.data.MessageType
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.data.db.MessageEntity
import com.example.chatapp.repository.ChatRepository
import com.example.chatapp.service.AlarmManager
import com.example.chatapp.service.SearchResult
import com.example.chatapp.service.WebSearchService
import com.example.chatapp.utils.DocumentProcessor
import com.example.chatapp.utils.HapticUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.Date

/**
 * 聊天视图模型，管理UI状态和处理用户操作
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ChatViewModel"

    // 使用带Context的构造函数初始化仓库
    val repository = ChatRepository(application)

    // 初始化网络搜索服务
    private val webSearchService = WebSearchService()

    // 初始化闹钟管理器
    private val alarmManager = AlarmManager(application)

    // 初始化文档处理器
    private val documentProcessor = DocumentProcessor(application)

    // 消息列表状态
    val messages = repository.messages

    // 网络搜索状态
    private val _isWebSearchEnabled = MutableStateFlow(false)
    val isWebSearchEnabled: StateFlow<Boolean> = _isWebSearchEnabled.asStateFlow()

    // 搜索结果状态
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    // 存储原始消息的列表，用于恢复
    private val _originalMessages = MutableStateFlow<List<Message>>(emptyList())
    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    // 存储正在编辑的消息ID和chatId
    private val _editingMessageId = MutableStateFlow<String?>(null)
    private val _editingChatId = MutableStateFlow<String?>(null)

    // 存储正在编辑的完整消息对象
    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage.asStateFlow()

    // SharedPreferences 用于持久化设置
    private val preferences = application.getSharedPreferences("chat_preferences", Context.MODE_PRIVATE)
    private val settingsManager = SettingsManager(application)

    // 标题生成相关 - 添加标志位，追踪是否需要为当前会话生成标题
    private var pendingTitleGeneration = false

    // 添加标题生成器对象 - 使用GPT版本
    private val titleGenerator = GptTitleGenerator()

    // 加载状态
    val loadingState = repository.loadingState
    val hasMoreOlderMessages = repository.hasMoreOlderMessages
    val hasMoreNewerMessages = repository.hasMoreNewerMessages

    // 是否正在加载更多
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // 搜索任务引用
    private var currentSearchJob: Job? = null

    init {
        // 初始化时加载保存的设置
        loadSettings()

        // 如果联网功能已开启，添加系统消息通知AI
        if (_isWebSearchEnabled.value) {
            enableWebSearchFeature()
        }

        // 重新调度所有闹钟
        rescheduleAllAlarms()
    }

    /**
     * 发送图像消息
     * @param imageUri 图像URI
     * @param caption 图像标题/描述
     */
    fun sendImageMessage(imageUri: Uri, caption: String = "") {
        viewModelScope.launch {
            try {
                // 使用Repository处理图像消息
                // 传递当前选择的模型
                val result = repository.sendImageMessage(imageUri, caption, getModelType())

                result.onSuccess {
                    Log.d(TAG, "图像处理成功")

                    // 如果需要生成标题
                    if (pendingTitleGeneration) {
                        pendingTitleGeneration = false

                        // 延迟一小段时间确保消息已保存到数据库
                        delay(500)

                        // 获取当前会话ID
                        val chatId = repository.currentChatId.value
                        if (chatId != null) {
                            generateAndUpdateTitle(chatId, "图像对话")
                        }
                    }
                }

                result.onFailure { throwable ->
                    Log.e(TAG, "图像处理失败: ${throwable.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送图像消息异常: ${e.message}", e)
            }
        }
    }

    /**
     * 获取当前正在编辑的完整消息对象
     */
    fun getEditingMessage(): Message? {
        val messageId = _editingMessageId.value ?: return null
        val chatId = _editingChatId.value ?: return null

        // 从当前消息列表中查找
        return messages.value.find { it.id == messageId }
    }

    /**
     * 更新带图片的消息
     */
    fun updateImageMessage(messageId: String, newContent: String, imageData: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "更新带图片的消息: ID=$messageId")

                // 删除原消息及其后续消息
                val messageToDelete = messages.value.find { it.id == messageId }
                if (messageToDelete != null) {
                    deleteMessageAndFollowing(messageToDelete)

                    // 确保删除操作完成
                    delay(300)
                }

                // 从Base64还原为图片文件
                val tempFile = File(getApplication<Application>().cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
                val imageBytes = android.util.Base64.decode(imageData, android.util.Base64.DEFAULT)
                FileOutputStream(tempFile).use { it.write(imageBytes) }

                // 使用带图片的发送方法
                sendImageMessage(Uri.fromFile(tempFile), newContent)

                Log.d(TAG, "带图片的消息已更新")
            } catch (e: Exception) {
                Log.e(TAG, "更新带图片的消息失败: ${e.message}", e)
            }
        }
    }

    /**
     * 更新纯文本消息
     */
    fun updateTextMessage(messageId: String, newContent: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "更新纯文本消息: ID=$messageId")

                // 删除原消息及其后续消息
                val messageToDelete = messages.value.find { it.id == messageId }
                if (messageToDelete != null) {
                    deleteMessageAndFollowing(messageToDelete)

                    // 确保删除操作完成
                    delay(300)
                }

                // 发送新的纯文本消息
                sendMessage(newContent)

                Log.d(TAG, "纯文本消息已更新")
            } catch (e: Exception) {
                Log.e(TAG, "更新纯文本消息失败: ${e.message}", e)
            }
        }
    }

    /**
     * 发送文档消息
     * @param fileUri 文档URI
     */
    fun sendDocumentMessage(fileUri: Uri) {
        viewModelScope.launch {
            try {
                // 获取文件名
                val fileName = documentProcessor.getFileName(fileUri)

                // 获取文件大小和类型信息
                val fileSize = getFileSize(fileUri)
                val formattedSize = formatFileSize(fileSize)
                val fileType = documentProcessor.getDocumentType(fileUri)
                val fileExtension = getFileExtension(fileName)

                Log.d(TAG, "文档信息: $fileName, 大小=$formattedSize, 类型=$fileType")

                // 创建文档消息，明确设置文档大小和类型信息
                val message = Message(
                    content = "请分析文档: $fileName",
                    type = MessageType.USER,
                    contentType = ContentType.DOCUMENT,
                    documentSize = formattedSize,
                    documentType = fileExtension.uppercase()
                )

                // 使用Repository处理文档消息
                val result = repository.sendDocumentMessage(fileUri, getModelType(), message)

                result.onSuccess {
                    Log.d(TAG, "文档处理成功")

                    // 如果需要生成标题
                    if (pendingTitleGeneration) {
                        pendingTitleGeneration = false

                        // 延迟一小段时间确保消息已保存到数据库
                        delay(500)

                        // 获取当前会话ID
                        val chatId = repository.currentChatId.value
                        if (chatId != null) {
                            generateAndUpdateTitle(chatId, "文档分析")
                        }
                    }
                }

                result.onFailure { throwable ->
                    Log.e(TAG, "文档处理失败: ${throwable.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送文档消息异常: ${e.message}", e)
            }
        }
    }

    /**
     * 获取文件大小
     */
    private fun getFileSize(uri: Uri): Long {
        try {
            val context = getApplication<Application>()
            val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            val fileSize = fileDescriptor?.statSize ?: 0L
            fileDescriptor?.close()
            return fileSize
        } catch (e: Exception) {
            Log.e(TAG, "获取文件大小失败: ${e.message}", e)
            return 0L
        }
    }

    /**
     * 格式化文件大小为可读形式
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * 从文件名中提取文件扩展名
     */
    private fun getFileExtension(filename: String): String {
        return if (filename.contains(".")) {
            filename.substringAfterLast('.', "")
        } else {
            "TXT"
        }
    }

    /**
     * 更新图片消息
     * @param messageId 消息ID
     * @param newCaption 新的图片标题
     * @param imageUri 图片URI
     */
    fun updateImageMessage(messageId: String, newCaption: String, imageUri: Uri) {
        viewModelScope.launch {
            try {
                // 首先删除原消息及其后续消息
                val messageToDelete = messages.value.find { it.id == messageId }
                if (messageToDelete != null) {
                    deleteMessageAndFollowing(messageToDelete)

                    // 确保删除操作完成
                    delay(300)
                }

                // 发送新的图片消息
                sendImageMessage(imageUri, newCaption)

                // 重置编辑状态
                setEditingMode(false)

                Log.d(TAG, "图片消息已更新")
            } catch (e: Exception) {
                Log.e(TAG, "更新图片消息失败: ${e.message}", e)
            }
        }
    }

    /**
     * 加载更多较旧的消息
     */
    fun loadMoreOlderMessages() {
        if (_isLoadingMore.value || !hasMoreOlderMessages.value) return

        viewModelScope.launch {
            try {
                _isLoadingMore.value = true
                repository.loadMoreOlderMessages()
            } catch (e: Exception) {
                Log.e(TAG, "加载旧消息失败: ${e.message}", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * 加载更多较新的消息
     */
    fun loadMoreNewerMessages() {
        if (_isLoadingMore.value || !hasMoreNewerMessages.value) return

        viewModelScope.launch {
            try {
                _isLoadingMore.value = true
                repository.loadMoreNewerMessages()
            } catch (e: Exception) {
                Log.e(TAG, "加载新消息失败: ${e.message}", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * 加载最后活动的会话
     */
    fun loadLastActiveChat() {
        viewModelScope.launch {
            try {
                // 先尝试加载当前会话ID
                val currentChatId = repository.loadLastChatId()

                if (currentChatId != null) {
                    // 加载会话详情检查是否存在
                    val chatDetails = repository.getChatDetails(currentChatId)

                    if (chatDetails != null) {
                        // 会话存在，切换到该会话
                        Log.d(TAG, "恢复上次会话: $currentChatId")
                        repository.switchChat(currentChatId)
                    } else {
                        // 会话不存在，创建新会话
                        Log.d(TAG, "上次会话不存在，创建新会话")
                        createNewChat()
                    }
                } else {
                    // 没有保存的会话ID，创建新会话
                    Log.d(TAG, "没有保存的会话ID，创建新会话")
                    createNewChat()
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载上次会话出错: ${e.message}", e)
                // 出错时创建新会话
                createNewChat()
            }
        }
    }

    /**
     * 分析并设置闹钟（如果识别到）
     * @return Pair<Boolean, String> 第一个值表示是否成功设置闹钟，第二个值为反馈消息
     */
    suspend fun analyzeAndSetAlarmIfNeeded(message: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                alarmManager.analyzeAndSetAlarm(message)
            } catch (e: Exception) {
                Log.e(TAG, "闹钟设置失败: ${e.message}", e)
                Pair(false, "")
            }
        }
    }

    /**
     * 重新调度所有闹钟
     */
    private fun rescheduleAllAlarms() {
        viewModelScope.launch {
            try {
                alarmManager.rescheduleAllActiveAlarms()
                Log.d(TAG, "所有闹钟已重新调度")
            } catch (e: Exception) {
                Log.e(TAG, "重新调度闹钟失败: ${e.message}", e)
            }
        }
    }

    /**
     * 发送用户消息
     */
    fun sendMessage(content: String) {
        viewModelScope.launch {
            // 发送消息
            val result = repository.sendMessage(content, getModelType())

            result.onSuccess {
                // 消息发送成功，检查是否需要生成标题
                if (pendingTitleGeneration) {
                    // 重置标记，防止多次生成
                    pendingTitleGeneration = false

                    // 延迟一小段时间确保消息已保存到数据库
                    delay(500)

                    // 获取当前会话ID
                    val chatId = repository.currentChatId.value
                    if (chatId != null) {
                        generateAndUpdateTitle(chatId, content)
                    }
                }
            }

            result.onFailure { throwable ->
                Log.e(TAG, "发送消息失败: ${throwable.message}")
            }
        }
    }

    /**
     * 发送自定义AI消息
     */
    fun sendCustomAiMessage(content: String) {
        viewModelScope.launch {
            try {
                // 获取当前会话ID
                val chatId = repository.currentChatId.value ?: return@launch

                // 创建消息实体
                val messageEntity = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    content = content,
                    type = 1, // AI消息类型
                    timestamp = Date(),
                    isError = false
                )

                // 将消息保存到数据库
                repository.insertMessageDirectly(messageEntity)

                Log.d(TAG, "已发送自定义AI消息: $content")
            } catch (e: Exception) {
                Log.e(TAG, "发送自定义AI消息失败: ${e.message}", e)
            }
        }
    }

    /**
     * 切换当前会话
     */
    fun switchChat(chatId: String) {
        viewModelScope.launch {
            try {
                repository.switchChat(chatId)
            } catch (e: Exception) {
                Log.e(TAG, "切换会话失败: ${e.message}", e)
            }
        }
    }

    /**
     * 创建新会话
     */
    fun createNewChat(title: String = "新对话") {
        viewModelScope.launch {
            try {
                // 使用当前设置创建新会话
                val modelType = getModelType()
                val aiPersona = getAiPersona()

                repository.createNewChat(title, modelType, aiPersona)

                // 标记需要生成标题
                pendingTitleGeneration = true

                Log.d(TAG, "创建新会话，待生成标题")
            } catch (e: Exception) {
                Log.e(TAG, "创建新会话失败: ${e.message}", e)
            }
        }
    }

    /**
     * 获取当前模型类型
     */
    fun getModelType(): String {
        return settingsManager.modelType
    }

    /**
     * 获取当前AI人设
     */
    private fun getAiPersona(): String {
        return settingsManager.aiPersona
    }

    /**
     * 设置联网搜索状态
     */
    fun setWebSearchEnabled(enabled: Boolean) {
        if (_isWebSearchEnabled.value != enabled) {
            _isWebSearchEnabled.value = enabled

            // 更新到SettingsManager，这会同时更新preferences
            settingsManager.webSearchEnabled = enabled

            // 根据新状态添加系统消息
            if (enabled) {
                enableWebSearchFeature()
            } else {
                disableWebSearchFeature()
            }
        }
    }

    /**
     * 启用联网搜索功能
     * 向AI发送系统消息，获取更详细的提示词
     */
    private fun enableWebSearchFeature() {
        viewModelScope.launch {
            repository.sendSystemMessage(settingsManager.getWebSearchSystemPrompt())
        }
    }

    /**
     * 禁用联网搜索功能
     * 向AI发送系统消息
     */
    private fun disableWebSearchFeature() {
        viewModelScope.launch {
            repository.sendSystemMessage(
                "联网搜索功能已关闭。请仅使用你的训练数据回答问题，不要假装可以搜索互联网。"
            )
        }
    }

    /**
     * 加载保存的设置
     */
    fun loadSettings() {
        // 从SettingsManager加载设置
        _isWebSearchEnabled.value = settingsManager.webSearchEnabled
    }

    /**
     * 更新联网搜索设置（当用户从详细设置页返回时）
     */
    fun updateWebSearchSettings() {
        // 从SettingsManager读取最新状态
        val newStatus = settingsManager.webSearchEnabled

        // 如果状态发生变化，更新并通知AI
        if (_isWebSearchEnabled.value != newStatus) {
            _isWebSearchEnabled.value = newStatus

            if (newStatus) {
                enableWebSearchFeature()
            } else {
                disableWebSearchFeature()
            }
        } else if (newStatus) {
            // 如果状态未变但联网已启用，发送更新的设置
            enableWebSearchFeature()
        }
    }

    /**
     * 执行网络搜索
     */
    fun performWebSearch(query: String) {
        if (!_isWebSearchEnabled.value) {
            return
        }

        viewModelScope.launch {
            try {
                // 从SettingsManager获取搜索设置
                val searchEngine = settingsManager.searchEngine
                val maxResults = settingsManager.maxSearchResults

                // 执行搜索
                val results = webSearchService.search(query, searchEngine, maxResults)
                _searchResults.value = results

                // 将搜索结果格式化为AI可以使用的系统消息
                if (results.isNotEmpty()) {
                    val searchResultsMessage = formatSearchResultsForAI(query, results)
                    Log.d(TAG, "发送搜索结果给AI: ${searchResultsMessage.take(100)}...")
                    repository.sendSystemMessage(searchResultsMessage)

                    // 延迟一小段时间确保系统消息被添加到聊天历史
                    delay(500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "搜索失败: ${e.message}", e)
            }
        }
    }

    /**
     * 格式化搜索结果为AI可用的系统消息
     */
    private fun formatSearchResultsForAI(query: String, results: List<SearchResult>): String {
        val sb = StringBuilder()
        sb.append("以下是关于\"$query\"的在线搜索结果，请参考这些信息回答用户的问题：\n\n")

        results.forEachIndexed { index, result ->
            sb.append("${index + 1}. ${result.title}\n")
            sb.append("   URL: ${result.url}\n")
            sb.append("   摘要: ${result.description}\n\n")
        }

        sb.append("请使用以上信息为用户提供准确的回答，并引用信息来源。")
        return sb.toString()
    }

    /**
     * 开始编辑消息
     */
    fun startEditing(message: Message) {
        viewModelScope.launch {
            try {
                // 添加详细日志记录消息的关键信息
                Log.d(TAG, "开始编辑消息: ID=${message.id}, 类型=${message.contentType}, " +
                        "包含图片数据=${message.imageData != null}, " +
                        "图片数据长度=${message.imageData?.length ?: 0}")

                _isEditing.value = true

                // 保存当前消息状态用于恢复
                _originalMessages.value = messages.value.toList()

                // 保存正在编辑的消息ID和chatId
                _editingMessageId.value = message.id
                _editingChatId.value = repository.currentChatId.value

                // 保存完整的消息对象，包括图片数据
                _editingMessage.value = message

                Log.d(TAG, "消息保存完成，准备编辑。消息类型=${message.contentType}")
            } catch (e: Exception) {
                Log.e(TAG, "开始编辑消息时出错: ${e.message}", e)
                // 出错时重置编辑状态
                resetEditingState()
            }
        }
    }

    /**
     * 更新消息内容
     */
    fun updateMessage(newContent: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "处理编辑后的消息: $newContent")

                // 获取完整的原始消息
                val originalMessage = _editingMessage.value
                val messageId = _editingMessageId.value
                val chatId = _editingChatId.value

                if (messageId != null && chatId != null) {
                    // 必要的日志以便调试
                    if (originalMessage != null) {
                        Log.d(TAG, "原始消息详情: ID=${originalMessage.id}, " +
                                "类型=${originalMessage.contentType}, " +
                                "包含图片数据=${originalMessage.imageData != null}, " +
                                "图片数据长度=${originalMessage.imageData?.length ?: 0}")
                    } else {
                        Log.e(TAG, "错误: 原始消息为null")
                    }

                    // 查找要删除的消息
                    val currentMessages = messages.value
                    val messageIndex = currentMessages.indexOfFirst { it.id == messageId }

                    if (messageIndex >= 0) {
                        val messageToDelete = currentMessages[messageIndex]
                        Log.d(TAG, "找到要删除的消息，索引: $messageIndex, 类型: ${messageToDelete.contentType}")

                        // 删除该消息及其后续所有消息
                        deleteMessageAndFollowing(messageToDelete)

                        // 等待删除操作完成
                        delay(500)
                        Log.d(TAG, "消息删除完成，准备发送新消息")

                        // 重置编辑状态，必须在发送前重置
                        val wasImageMessage = originalMessage?.contentType == ContentType.IMAGE ||
                                originalMessage?.contentType == ContentType.IMAGE_WITH_TEXT
                        val imageData = originalMessage?.imageData

                        resetEditingState()

                        // 根据原消息类型选择正确的发送方法
                        if (wasImageMessage && imageData != null) {
                            Log.d(TAG, "检测到图片消息，准备重新发送图片。图片数据长度: ${imageData.length}")
                            // 将Base64图片数据转换为临时文件并发送
                            val imageFile = createTempFileFromBase64(imageData)
                            if (imageFile != null) {
                                Log.d(TAG, "成功创建临时图片文件: ${imageFile.absolutePath}")
                                val imageUri = Uri.fromFile(imageFile)
                                sendImageMessage(imageUri, newContent)
                            } else {
                                Log.e(TAG, "创建临时图片文件失败，降级为普通文本消息")
                                sendMessage(newContent)
                            }
                        } else {
                            // 纯文本消息或文档
                            Log.d(TAG, "发送普通文本消息: $newContent")
                            sendMessage(newContent)
                        }

                        Log.d(TAG, "编辑操作完成")
                    } else {
                        Log.e(TAG, "未找到要编辑的消息: $messageId")
                        resetEditingState()
                    }
                } else {
                    Log.e(TAG, "缺少编辑消息所需的ID信息: messageId=$messageId, chatId=$chatId")
                    resetEditingState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新消息失败: ${e.message}", e)
                resetEditingState()
            }
        }
    }

    /**
     * 重置编辑状态
     */
    private fun resetEditingState() {
        _isEditing.value = false
        _editingMessageId.value = null
        _editingChatId.value = null
        _editingMessage.value = null
    }

    /**
     * 将Base64图片数据转换为临时文件
     */
    private suspend fun createTempFileFromBase64(base64Image: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始将Base64图片数据转换为文件，数据长度: ${base64Image.length}")

                // 移除Base64前缀，如果有的话
                val base64Data = if (base64Image.contains(",")) {
                    base64Image.split(",")[1]
                } else {
                    base64Image
                }

                // 解码Base64数据
                val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                Log.d(TAG, "Base64解码完成，解码后字节数: ${imageBytes.size}")

                // 创建临时文件
                val context = getApplication<Application>()
                val tempFile = File.createTempFile("edited_image_", ".jpg", context.cacheDir)

                // 写入图片数据
                FileOutputStream(tempFile).use { fos ->
                    fos.write(imageBytes)
                    fos.flush()
                }

                Log.d(TAG, "成功创建临时图片文件: ${tempFile.absolutePath}, 文件大小: ${tempFile.length()}")

                // 验证图片文件是否有效
                val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                if (bitmap != null) {
                    Log.d(TAG, "图片验证成功，尺寸: ${bitmap.width}x${bitmap.height}")
                    bitmap.recycle()
                } else {
                    Log.e(TAG, "图片验证失败，无法解码")
                    return@withContext null
                }

                return@withContext tempFile
            } catch (e: Exception) {
                Log.e(TAG, "创建临时图片文件失败: ${e.message}", e)
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    /**
     * 获取当前正在编辑的消息ID
     */
    fun getEditingMessageId(): String? {
        return _editingMessageId.value
    }

    /**
     * 直接设置编辑模式状态
     */
    fun setEditingMode(editing: Boolean) {
        viewModelScope.launch {
            _isEditing.value = editing
            if (!editing) {
                _editingMessageId.value = null
                _editingChatId.value = null
                _editingMessage.value = null
            }
        }
    }

    /**
     * 取消编辑，恢复原始消息
     */
    fun cancelEditing() {
        if (_isEditing.value) {
            viewModelScope.launch {
                _isEditing.value = false

                // 重置编辑状态
                _editingMessageId.value = null
                _editingChatId.value = null
                _editingMessage.value = null

                // 恢复原始消息列表
                val originalList = _originalMessages.value

                // 通知数据库同步更新
                repository.restoreMessages(originalList)
            }
        }
    }

    /**
     * 删除单条消息
     */
    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            try {
                val chatId = repository.currentChatId.value ?: return@launch
                Log.d(TAG, "准备删除消息 ID:${message.id}")

                // 创建消息实体
                val messageEntity = MessageEntity(
                    id = message.id,
                    chatId = chatId,
                    content = message.content,
                    type = if (message.type == MessageType.USER) 0 else 1,
                    timestamp = message.timestamp,
                    isError = false,
                    imageData = message.imageData,
                    contentType = message.contentType.ordinal,
                    documentSize = message.documentSize,
                    documentType = message.documentType
                )

                // 从数据库中删除消息
                repository.deleteMessage(messageEntity)

                // 显示删除成功提示
                Toast.makeText(getApplication(), "消息已删除", Toast.LENGTH_SHORT).show()

                // 震动反馈
                HapticUtils.performHapticFeedback(getApplication())

                // 重新加载消息列表刷新UI
                repository.loadCurrentChatMessages()
            } catch (e: Exception) {
                Log.e(TAG, "删除消息失败: ${e.message}", e)
                Toast.makeText(getApplication(), "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 删除消息及其后续的所有消息
     */
    fun deleteMessageAndFollowing(message: Message) {
        viewModelScope.launch {
            try {
                val chatId = repository.currentChatId.value ?: return@launch
                Log.d(TAG, "准备删除消息 ID:${message.id} 及其后续消息")

                // 获取当前消息列表
                val currentMessages = messages.value.toMutableList()
                val index = currentMessages.indexOfFirst { it.id == message.id }

                if (index >= 0) {
                    // 获取要删除的消息
                    val messagesToDelete = currentMessages.subList(index, currentMessages.size)
                    Log.d(TAG, "找到 ${messagesToDelete.size} 条要删除的消息")

                    // 转换为消息实体
                    val messageEntities = messagesToDelete.map { msg ->
                        MessageEntity(
                            id = msg.id,
                            chatId = chatId,
                            content = msg.content,
                            type = if (msg.type == MessageType.USER) 0 else 1,
                            timestamp = msg.timestamp,
                            isError = false,
                            imageData = msg.imageData,
                            contentType = msg.contentType.ordinal,
                            documentSize = msg.documentSize,
                            documentType = msg.documentType
                        )
                    }

                    // 从数据库中删除这些消息
                    repository.deleteMessages(messageEntities)

                    // 强制加载消息刷新UI
                    delay(100)
                    repository.loadCurrentChatMessages()

                    Log.d(TAG, "已删除消息及其后续消息，总计 ${messagesToDelete.size} 条")
                } else {
                    Log.e(TAG, "未找到要删除的消息: ${message.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除消息失败: ${e.message}", e)
            }
        }
    }

    /**
     * 生成并更新聊天标题
     */
    private fun generateAndUpdateTitle(chatId: String, firstMessage: String) {
        viewModelScope.launch {
            try {
                // 生成标题
                val title = titleGenerator.generateTitle(firstMessage)

                // 更新标题
                repository.updateChatTitle(chatId, title)

                Log.d(TAG, "已生成标题: $title")
            } catch (e: Exception) {
                Log.e(TAG, "生成标题失败: ${e.message}", e)

                // 如果AI生成失败，使用备用方法生成
                val fallbackTitle = generateFallbackTitle(firstMessage)
                repository.updateChatTitle(chatId, fallbackTitle)

                Log.d(TAG, "使用备用方法生成标题: $fallbackTitle")
            }
        }
    }

    /**
     * 备用的标题生成方法（当API请求失败时使用）
     */
    private fun generateFallbackTitle(message: String): String {
        // 清理消息内容
        val cleanMessage = message.trim()
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")

        // 如果消息很短，直接使用
        if (cleanMessage.length <= 20) {
            return cleanMessage
        }

        // 尝试提取问题
        val questionPattern = Regex("[^。？！.?!]*[？?]")
        val questions = questionPattern.findAll(cleanMessage)

        for (question in questions) {
            val text = question.value.trim()
            if (text.length in 5..25) {
                return text
            }
        }

        // 截取前20个字符作为标题并添加省略号
        return cleanMessage.substring(0, 20) + "..."
    }

    /**
     * GPT标题生成器 - 使用API生成智能标题
     */
    inner class GptTitleGenerator {
        /**
         * 使用GPT API生成聊天标题
         * @param message 用户的第一条消息
         * @return 生成的标题，如果生成失败则返回默认标题
         */
        suspend fun generateTitle(message: String): String {
            // 消息为空或太短时使用默认标题
            if (message.isBlank() || message.length < 3) {
                return "新对话"
            }

            return withContext(Dispatchers.IO) {
                try {
                    // 构建用于生成标题的提示
                    val titlePrompt = buildTitlePrompt(message)

                    // 构建API请求
                    val messages = listOf(
                        ChatMessage("system", "你是一个专门用来提取对话主题并生成简短标题的助手。你的回答应该只包含标题，不包含任何其他内容。"),
                        ChatMessage("user", titlePrompt)
                    )

                    val request = ChatGptRequest(
                        model = getModelType(), // 使用当前设置的模型
                        messages = messages,
                        temperature = 0.3 // 低温度使输出更加确定性和精确
                    )

                    // 发送API请求
                    val response = ApiClient.apiService.sendMessage(
                        ApiClient.getAuthHeader(),
                        request
                    )

                    if (response.isSuccessful && response.body() != null) {
                        // 提取API返回的标题
                        val title = response.body()!!.choices[0].message.content.toString().trim()

                        // 处理可能的异常情况
                        if (title.length > 30) {
                            // 标题过长，截取
                            title.substring(0, 30) + "..."
                        } else if (title.startsWith("标题：") || title.startsWith("Title:")) {
                            // 删除前缀
                            title.substringAfter("：").substringAfter(":").trim()
                        } else if (title.contains("\"") || title.contains("")) {
                            // 删除引号
                            title.replace("\"", "").replace(""", "").replace(""", "").trim()
                        } else {
                            title
                        }
                    } else {
                        // API请求失败，使用备用生成方法
                        Log.w(TAG, "API生成标题失败，使用本地生成: ${response.code()}")
                        generateFallbackTitle(message)
                    }
                } catch (e: Exception) {
                    // 异常处理，使用备用方法生成
                    Log.e(TAG, "AI标题生成异常: ${e.message}", e)
                    generateFallbackTitle(message)
                }
            }
        }

        /**
         * 构建标题生成提示
         */
        private fun buildTitlePrompt(message: String): String {
            // 为了限制token消耗，可能需要裁剪长消息
            val truncatedMessage = if (message.length > 500) {
                message.substring(0, 500) + "..."
            } else {
                message
            }

            return """
            请为以下用户消息生成一个简短、精确的对话标题。标题应不超过15个字符：

            用户消息：$truncatedMessage

            要求：
            1. 提炼核心主题或问题
            2. 标题必须简短清晰
            3. 不要使用"关于..."、"请求..."等开头词
            4. 不要使用标点符号
            5. 直接输出标题，不要有任何其他内容
            """.trimIndent()
        }
    }
}

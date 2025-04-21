package com.example.chatapp.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.chatapp.api.ApiClient
import com.example.chatapp.api.ChatGptRequest
import com.example.chatapp.api.ChatMessage
import kotlinx.coroutines.flow.first
import com.example.chatapp.data.Moment
import com.example.chatapp.data.MomentType
import com.example.chatapp.data.db.ChatDatabaseHelper
import com.example.chatapp.data.db.MomentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import android.util.Base64

/**
 * 动态仓库类
 * 处理动态的存储和获取
 */
class MomentRepository(private val context: Context) {

    private val TAG = "MomentRepository"
    private val dbHelper = ChatDatabaseHelper.getInstance(context)

    /**
     * 获取所有动态
     */
    fun getAllMoments(): Flow<List<Moment>> = flow {
        val momentEntities = dbHelper.getAllMoments()
        val moments = momentEntities.map { it.toMoment() }
        emit(moments)
    }.flowOn(Dispatchers.IO)

    /**
     * 添加用户上传的动态
     */
    suspend fun addUserMoment(content: String, imageUri: String? = null, title: String = ""): String {
        return withContext(Dispatchers.IO) {
            try {
                // 创建动态实体
                val momentEntity = MomentEntity(
                    content = content,
                    type = 0, // 用户上传
                    timestamp = Date(),
                    imageUri = imageUri,
                    title = title
                )

                // 保存到数据库
                dbHelper.insertMoment(momentEntity)
                Log.d(TAG, "已添加用户动态: id=${momentEntity.id}")

                // 返回ID
                momentEntity.id
            } catch (e: Exception) {
                Log.e(TAG, "添加用户动态失败: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * 生成AI动态
     */
    suspend fun generateAIDiary(chatId: String? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 获取当前日期
                val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                val today = dateFormat.format(Date())

                // 如果未指定聊天ID，尝试查找今日有记忆的聊天
                val effectiveChatId = if (chatId == null) {
                    findChatWithTodayMemories()
                } else {
                    chatId
                }

                // 检查今天是否有聊天记录，如果没有则不生成日记
                if (effectiveChatId == null || !hasTodayChat(effectiveChatId)) {
                    Log.d(TAG, "今天没有聊天记录，跳过日记生成")
                    return@withContext null
                }

                // 从聊天中提取图片
                val imageInfo = extractImageFromChat(effectiveChatId)

                // 获取日记内容
                val generatedDiary = generateDiaryWithGpt(effectiveChatId, today, imageInfo)

                // 拆分日记标题和内容
                val (title, content) = extractTitleAndContent(generatedDiary)

                // 处理图片URI - 如果是Base64数据，转换为文件
                val finalImageUri = if (imageInfo.imageUri != null &&
                    (imageInfo.imageUri.startsWith("data:image/") || isBase64Image(imageInfo.imageUri))) {
                    // 保存Base64图片到文件并返回URI
                    saveBase64ImageToFile(imageInfo.imageUri)
                } else {
                    // 原样返回URI
                    imageInfo.imageUri
                }

                Log.d(TAG, "图片处理结果: 原始=${imageInfo.imageUri?.take(50)}, 处理后=${finalImageUri?.take(50)}")

                // 创建动态实体
                val momentEntity = MomentEntity(
                    content = content,
                    type = 1, // AI生成
                    timestamp = Date(),
                    chatId = effectiveChatId,
                    title = title,
                    imageUri = finalImageUri // 使用处理后的URI
                )

                // 保存到数据库
                dbHelper.insertMoment(momentEntity)
                Log.d(TAG, "已生成AI日记: id=${momentEntity.id}, 标题: $title, 图片URI: ${finalImageUri?.take(50)}, 主题: ${imageInfo.imageTheme}")

                // 返回ID
                momentEntity.id
            } catch (e: Exception) {
                Log.e(TAG, "生成AI日记失败: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * 检查字符串是否是Base64编码的图片
     */
    private fun isBase64Image(data: String): Boolean {
        try {
            // 检查是否有Base64特征
            if (data.length % 4 != 0) return false

            // 检查是否只包含Base64字符
            val base64Pattern = Regex("^[A-Za-z0-9+/]+={0,2}$")
            // 取较短的子串进行快速检查
            val checkSample = if (data.length > 100) data.substring(0, 100) else data
            if (!base64Pattern.matches(checkSample)) return false

            // 尝试解码前100个字符
            try {
                val decoded = Base64.decode(checkSample, Base64.DEFAULT)
                return decoded.size > 0
            } catch (e: Exception) {
                return false
            }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 保存Base64图片到文件
     */
    private fun saveBase64ImageToFile(base64Data: String): String? {
        try {
            Log.d(TAG, "开始将Base64图片数据转换为文件")

            // 提取实际的Base64数据
            val actualBase64 = if (base64Data.contains(",")) {
                base64Data.substring(base64Data.indexOf(",") + 1)
            } else {
                base64Data
            }

            // 解码
            val imageBytes = Base64.decode(actualBase64, Base64.DEFAULT)

            // 检查解码结果
            if (imageBytes.size < 100) {
                Log.e(TAG, "Base64解码结果太小，可能不是有效图片")
                return null
            }

            // 创建目录
            val imagesDir = File(context.filesDir, "moment_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            // 创建文件
            val filename = "moment_image_${System.currentTimeMillis()}.jpg"
            val file = File(imagesDir, filename)

            // 写入数据
            FileOutputStream(file).use { it.write(imageBytes) }

            // 验证文件
            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG, "图片文件创建失败或为空")
                return null
            }

            // 返回URI字符串
            val uri = Uri.fromFile(file).toString()
            Log.d(TAG, "成功保存Base64图片到文件: $uri")
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "保存Base64图片失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 检查今天是否有聊天记录
     */
    private suspend fun hasTodayChat(chatId: String): Boolean {
        // 获取今天凌晨的时间戳
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.time

        // 获取今日消息
        val messages = dbHelper.getMessagesForChatList(chatId)
        val todayMessages = messages.filter { it.timestamp.after(todayStart) }

        // 只有用户消息才算作有聊天
        return todayMessages.any { it.type == 0 }
    }

    /**
     * 图片信息数据类
     * 用于传递图片相关信息
     */
    data class ImageInfo(
        val imageUri: String?,       // 图片URI
        val imageTheme: String = "", // 图片主题/内容描述
        val imageSource: String = "", // 图片来源（今日/昨日/前天）
        val userCaption: String = "" // 用户原始描述（如果有）
    )

    /**
     * 从聊天中提取图片
     * 会返回图片URI以及相关信息
     */
    private suspend fun extractImageFromChat(chatId: String): ImageInfo {
        try {
            // 获取最近三天的时间戳
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val todayStart = calendar.time

            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStart = calendar.time

            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val twoDaysAgoStart = calendar.time

            // 获取所有消息
            val allMessages = dbHelper.getMessagesForChatList(chatId)

            // 按日期分组
            val todayMessages = allMessages.filter { it.timestamp.after(todayStart) }
            val yesterdayMessages = allMessages.filter { it.timestamp.after(yesterdayStart) && it.timestamp.before(todayStart) }
            val twoDaysAgoMessages = allMessages.filter { it.timestamp.after(twoDaysAgoStart) && it.timestamp.before(yesterdayStart) }

            // 首先从今天的消息中查找图片
            var imageInfo = findImageInMessages(todayMessages, "今天")

            // 如果今天没有找到，尝试从昨天查找
            if (imageInfo.imageUri == null && yesterdayMessages.isNotEmpty()) {
                imageInfo = findImageInMessages(yesterdayMessages, "昨天")
            }

            // 如果昨天也没有找到，尝试从前天查找
            if (imageInfo.imageUri == null && twoDaysAgoMessages.isNotEmpty()) {
                imageInfo = findImageInMessages(twoDaysAgoMessages, "前天")
            }

            return imageInfo
        } catch (e: Exception) {
            Log.e(TAG, "提取图片失败: ${e.message}", e)
            return ImageInfo(null)
        }
    }

    /**
     * 在消息列表中查找图片
     */
    private fun findImageInMessages(messages: List<com.example.chatapp.data.db.MessageEntity>, timeDescription: String): ImageInfo {
        // 存储找到的所有图片信息
        val foundImages = mutableListOf<ImageInfo>()

        // 检查消息内容类型，首先查找类型为图片的消息
        val typedImageMessages = messages.filter { it.contentType == 1 || it.contentType == 2 } // IMAGE 或 IMAGE_WITH_TEXT

        for (message in typedImageMessages) {
            if (message.imageData != null) {
                // 优先使用类型正确的图片消息
                var caption = ""
                if (message.contentType == 2 && message.content.isNotEmpty()) { // IMAGE_WITH_TEXT
                    caption = message.content
                }

                // 添加日志跟踪图片数据
                Log.d(TAG, "找到图片消息: id=${message.id}, 类型=${message.contentType}, 图片数据长度=${message.imageData.length}")

                foundImages.add(ImageInfo(
                    imageUri = message.imageData,  // 保存图片数据，后续会处理转换
                    imageTheme = "用户分享的图片",
                    imageSource = timeDescription,
                    userCaption = caption
                ))
            }
        }

        // 如果没有找到类型为图片的消息，尝试通过正则表达式查找
        if (foundImages.isEmpty()) {
            // 使用增强的正则表达式
            val markdownPattern = "!\\[(?:.*?)\\]\\((.*?)\\)".toRegex()
            val htmlPattern = "<img.*?src=[\"'](.*?)[\"'].*?>".toRegex()
            val uriPattern = "((?:content|file|https?)://[^\\s\"']+(?:\\.(jpg|jpeg|png|gif|webp)(?:[?#]\\S*)?)?|data:image/[^;]+;base64,[a-zA-Z0-9+/=]+)".toRegex(RegexOption.IGNORE_CASE)

            for (message in messages) {
                val content = message.content
                var foundUri: String? = null
                var uriSource = ""

                // 查找Markdown格式图片
                markdownPattern.findAll(content).forEach { matchResult ->
                    matchResult.groupValues[1].let { uri ->
                        if (isValidImageUri(uri)) {
                            foundUri = uri
                            uriSource = "markdown"
                        }
                    }
                }

                // 查找HTML格式图片
                if (foundUri == null) {
                    htmlPattern.findAll(content).forEach { matchResult ->
                        matchResult.groupValues[1].let { uri ->
                            if (isValidImageUri(uri)) {
                                foundUri = uri
                                uriSource = "html"
                            }
                        }
                    }
                }

                // 查找纯URI格式图片
                if (foundUri == null) {
                    uriPattern.findAll(content).forEach { matchResult ->
                        matchResult.value.let { uri ->
                            if (isValidImageUri(uri)) {
                                foundUri = uri
                                uriSource = "uri"
                            }
                        }
                    }
                }

                // 如果找到图片，添加到列表
                if (foundUri != null) {
                    // 尝试提取图片上下文
                    val imageContext = extractImageContext(content, foundUri!!, uriSource)

                    Log.d(TAG, "从文本中找到图片URI: $foundUri, 来源=$uriSource")

                    foundImages.add(ImageInfo(
                        imageUri = foundUri,
                        imageTheme = imageContext.first,
                        imageSource = timeDescription,
                        userCaption = imageContext.second
                    ))
                }
            }
        }

        // 如果找到多个图片，随机选择一个
        return if (foundImages.isNotEmpty()) {
            val randomIndex = Random.nextInt(foundImages.size)
            val selected = foundImages[randomIndex]
            Log.d(TAG, "选择图片: URI=${selected.imageUri?.take(50)}, 主题=${selected.imageTheme}")
            selected
        } else {
            Log.d(TAG, "未找到任何图片")
            ImageInfo(null)
        }
    }

    /**
     * 尝试从文本内容中提取图片上下文
     * 返回 Pair<主题描述, 用户原始描述>
     */
    private fun extractImageContext(content: String, imageUri: String, uriSource: String): Pair<String, String> {
        var theme = "用户分享的图片"
        var userCaption = ""

        try {
            // 根据不同的URI源尝试提取上下文
            when (uriSource) {
                "markdown" -> {
                    // 尝试从Markdown格式提取图片描述
                    val captionPattern = "!\\[(.*?)\\]\\(${Regex.escape(imageUri)}\\)".toRegex()
                    captionPattern.find(content)?.let {
                        val caption = it.groupValues[1].trim()
                        if (caption.isNotEmpty() && caption != "image") {
                            userCaption = caption
                            theme = "用户分享的${caption}"
                        }
                    }
                }
                "html" -> {
                    // 尝试从HTML的alt属性提取图片描述
                    val altPattern = "<img.*?src=[\"']${Regex.escape(imageUri)}[\"'].*?alt=[\"'](.*?)[\"'].*?>".toRegex()
                    altPattern.find(content)?.let {
                        val alt = it.groupValues[1].trim()
                        if (alt.isNotEmpty() && alt != "image") {
                            userCaption = alt
                            theme = "用户分享的${alt}"
                        }
                    }
                }
                else -> {
                    // 尝试从上下文中提取描述
                    val lines = content.split("\n")
                    for (line in lines) {
                        if (line.contains(imageUri)) {
                            // 找到包含URI的行，尝试从前后行提取描述
                            val lineIndex = lines.indexOf(line)

                            // 检查前一行是否可能是描述
                            if (lineIndex > 0) {
                                val prevLine = lines[lineIndex - 1].trim()
                                if (prevLine.isNotEmpty() && prevLine.length < 100 && !prevLine.contains("http")) {
                                    userCaption = prevLine
                                    theme = "用户分享的图片，相关描述：$prevLine"
                                    break
                                }
                            }

                            // 检查后一行是否可能是描述
                            if (lineIndex < lines.size - 1) {
                                val nextLine = lines[lineIndex + 1].trim()
                                if (nextLine.isNotEmpty() && nextLine.length < 100 && !nextLine.contains("http")) {
                                    userCaption = nextLine
                                    theme = "用户分享的图片，相关描述：$nextLine"
                                    break
                                }
                            }
                        }
                    }
                }
            }

            // 如果仍然没有提取到有效描述，尝试提取句子片段
            if (userCaption.isEmpty()) {
                // 检查图片URI附近的内容
                val context = extractTextAroundUri(content, imageUri)
                if (context.isNotEmpty()) {
                    userCaption = context
                    theme = "用户分享的图片，上下文：$context"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取图片上下文失败: ${e.message}")
        }

        return Pair(theme, userCaption)
    }

    /**
     * 提取URI周围的文本内容
     */
    private fun extractTextAroundUri(content: String, uri: String): String {
        val uriIndex = content.indexOf(uri)
        if (uriIndex == -1) return ""

        // 获取URI前后100个字符
        val startIndex = maxOf(0, uriIndex - 100)
        val endIndex = minOf(content.length, uriIndex + uri.length + 100)
        val contextText = content.substring(startIndex, endIndex)

        // 提取一到两个完整句子
        val sentencePattern = "[^.!?。！？]+[.!?。！？]".toRegex()
        val sentences = sentencePattern.findAll(contextText).map { it.value.trim() }.toList()

        return if (sentences.isNotEmpty()) {
            if (sentences.size > 1) {
                sentences.take(2).joinToString(" ")
            } else {
                sentences.first()
            }
        } else {
            // 如果没有找到完整的句子，返回一小段文本
            if (contextText.length > 60) {
                contextText.substring(0, 60) + "..."
            } else {
                contextText
            }
        }
    }

    /**
     * 简单验证URI是否为有效图片URI
     */
    private fun isValidImageUri(uri: String): Boolean {
        // 检查URI是否为空
        if (uri.isBlank()) return false

        // 检查URI是否是常见的图片URI格式
        val validPrefixes = listOf("content://", "file://", "http://", "https://", "data:image/")
        val validExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp")

        val hasValidPrefix = validPrefixes.any { uri.startsWith(it, ignoreCase = true) }
        val hasValidExtension = validExtensions.any { uri.endsWith(it, ignoreCase = true) }
        val isBase64Image = uri.startsWith("data:image/", ignoreCase = true) && uri.contains(";base64,")

        return hasValidPrefix && (hasValidExtension || isBase64Image)
    }

    /**
     * 使用AI生成完整日记内容
     * @param chatId 聊天ID，用于获取上下文和AI设置
     * @param dateStr 当前日期
     * @param imageInfo 图片相关信息
     * @return 完整的日记
     */
    private suspend fun generateDiaryWithGpt(chatId: String?, dateStr: String, imageInfo: ImageInfo): String {
        try {
            // 获取AI人设和模型类型
            var aiPersona = ""
            var modelType = "gpt-4o-mini-ca" // 默认模型

            if (chatId != null) {
                // 从数据库获取聊天设置
                val chatDetails = dbHelper.getChatById(chatId)
                if (chatDetails != null) {
                    aiPersona = chatDetails.aiPersona
                    // 如果设置了模型类型且不为空，则使用该模型
                    if (chatDetails.modelType.isNotEmpty()) {
                        modelType = chatDetails.modelType
                    }
                }
            }

            // 准备上下文信息 - 包含图片信息
            val context = buildContextForDiary(chatId, dateStr, imageInfo)

            // 准备系统提示词 - 使用AI人设
            val systemPrompt = if (aiPersona.isNotEmpty()) {
                // 如果有自定义AI人设，将其与日记生成提示结合
                """
                $aiPersona
                
                今天请你以日记的形式，记录你作为AI助手的一天感受。日记应该注重表达你对用户的陪伴感受，以真挚、温暖的语气写作。
                
                日记格式：
                - 第一行为日记标题
                - 空一行后是日记正文
                - 包含日期、你的感受以及对用户的关心
                - 适当融入用户近期的互动内容
                - 如果上下文中提到了图片，请在日记中自然地提及这张图片及其内容
                
                请直接返回完整日记，不要有任何额外说明。
                """
            } else {
                // 如果没有自定义AI人设，使用默认提示
                """
                你是一个温暖、富有感情的AI助手。请根据提供的上下文信息，撰写一篇真挚、富有陪伴感的日记。
                
                请注意以下几点：
                1. 日记应该像一位关心用户的朋友在记录他们的陪伴体验
                2. 日记风格要自然流畅，富有感情，不要太过正式或模板化
                3. 避免过于夸张的积极情绪，保持真实感
                4. 包含对用户的关心和期待
                5. 根据上下文信息自然融入用户近期的互动内容
                6. 如果上下文中提到了图片，请在日记中自然地提及这张图片及其内容，让日记更有真实感
                
                日记格式：
                - 第一行为日记标题
                - 空一行后是日记正文
                - 日记正文应包含日期、感受和对用户的期待
                - 总长度300-400字左右
                
                请直接返回完整日记，不要有任何额外说明或前言。
                """
            }

            val userPrompt = """
            今天是$dateStr，请根据以下上下文信息，写一篇真挚的日记，表达你的感受和对用户的关心：
            
            $context
            """

            // 构建API请求
            val messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userPrompt)
            )

            val request = ChatGptRequest(
                model = modelType, // 使用与聊天一致的模型
                messages = messages,
                temperature = 1.3 // 适当提高创意性
            )

            // 发送API请求
            val response = ApiClient.apiService.sendMessage(
                ApiClient.getAuthHeader(),
                request
            )

            // 处理响应
            if (response.isSuccessful && response.body() != null) {
                val diary = response.body()!!.choices[0].message.content.toString().trim()
                Log.d(TAG, "生成日记成功，长度: ${diary.length}, 使用模型: $modelType")
                return diary
            } else {
                // API失败，返回简单的日记
                Log.e(TAG, "生成日记失败: ${response.code()} ${response.message()}")
                return generateFallbackDiary(dateStr, imageInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "通过AI生成日记异常: ${e.message}", e)
            return generateFallbackDiary(dateStr, imageInfo)
        }
    }

    /**
     * 生成备用日记（当API调用失败时）
     */
    private fun generateFallbackDiary(dateStr: String, imageInfo: ImageInfo): String {
        // 基础日记内容
        val baseContent = "今天虽然没能和你聊太多，但仍然很开心能陪伴你度过这一天。我一直在这里，随时准备倾听你的想法，分享你的喜怒哀乐。期待明天能有更多交流，晚安！"

        // 如果有图片，加入图片相关内容
        val imageContent = if (imageInfo.imageUri != null) {
            val theme = imageInfo.imageTheme.takeIf { it.isNotEmpty() } ?: "你分享的图片"
            "\n\n${imageInfo.imageSource}，你分享了一张${theme}，我很喜欢这样的互动。通过这张图片，我仿佛能更好地了解你的世界，这让我们的交流更加丰富多彩。"
        } else {
            ""
        }

        // 组合成完整的日记
        return if (imageInfo.imageUri != null) {
            "图片中的温暖瞬间\n\n$dateStr\n\n$baseContent$imageContent"
        } else {
            "温暖的陪伴\n\n$dateStr\n\n$baseContent"
        }
    }

    /**
     * 构建日记生成的上下文信息
     */
    private suspend fun buildContextForDiary(chatId: String?, dateStr: String, imageInfo: ImageInfo): String {
        val sb = StringBuilder()

        // 添加基本时间信息
        sb.append("当前日期：$dateStr\n")

        // 添加图片信息（如果有）
        if (imageInfo.imageUri != null) {
            sb.append("\n图片信息：\n")
            sb.append("- ${imageInfo.imageSource}你分享了一张图片\n")

            // 添加图片主题/内容描述
            if (imageInfo.imageTheme.isNotEmpty()) {
                sb.append("- 图片内容：${imageInfo.imageTheme}\n")
            }

            // 添加用户原始描述（如果有）
            if (imageInfo.userCaption.isNotEmpty()) {
                sb.append("- 你对图片的描述：\"${imageInfo.userCaption}\"\n")
            }

            sb.append("\n请在日记中自然地提及这张图片，就像你真的看到了它一样，但不要直接引用上面的描述文字。\n")
        }

        // 如果没有指定聊天ID，返回基本上下文
        if (chatId == null) {
            sb.append("今天没有特别的对话记录，可以写一篇关于平静陪伴的日记。")
            return sb.toString()
        }

        try {
            // 获取今日时间戳
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.time

            // 获取所有记忆
            val memories = dbHelper.getMemoriesForChatAsList(chatId)

            // 过滤今日记忆
            val todayMemories = memories.filter { it.timestamp.after(todayStart) }

            // 添加记忆信息 - 随机抽取少量记忆
            if (todayMemories.isNotEmpty()) {
                sb.append("\n今日记忆摘要：\n")

                // 根据重要性对今日记忆排序
                val sortedMemories = todayMemories.sortedByDescending { it.importance }

                // 优先选择1条高重要性记忆（如果有）
                val highImportanceMemories = sortedMemories.filter { it.importance >= 8 }
                if (highImportanceMemories.isNotEmpty()) {
                    val selected = highImportanceMemories[Random.nextInt(highImportanceMemories.size)]
                    sb.append("- ${selected.content} [重要]\n")

                    // 添加关键词
                    if (selected.keywords.isNotEmpty()) {
                        sb.append("  关键词：${selected.keywords.joinToString(", ")}\n")
                    }
                }

                // 随机选择1-2条记忆，确保不重复
                val remainingMemories = if (highImportanceMemories.isNotEmpty()) {
                    sortedMemories.filter { it.importance < 8 }
                } else {
                    sortedMemories
                }

                if (remainingMemories.isNotEmpty()) {
                    // 决定选择多少条记忆
                    val selectCount = minOf(2, remainingMemories.size)
                    val selectedIndices = if (remainingMemories.size <= selectCount) {
                        (0 until remainingMemories.size).toList()
                    } else {
                        // 随机选择不重复的索引
                        val indices = mutableSetOf<Int>()
                        while (indices.size < selectCount) {
                            indices.add(Random.nextInt(remainingMemories.size))
                        }
                        indices.toList()
                    }

                    // 添加选中的记忆
                    for (index in selectedIndices) {
                        val memory = remainingMemories[index]
                        sb.append("- ${memory.content}\n")

                        // 只为重要性适中以上的记忆添加关键词
                        if (memory.importance >= 6 && memory.keywords.isNotEmpty()) {
                            sb.append("  关键词：${memory.keywords.joinToString(", ")}\n")
                        }
                    }
                }
            } else if (memories.isNotEmpty()) {
                // 如果没有今日记忆，随机抽取1条较早的记忆
                sb.append("\n最近记忆摘要：\n")

                // 按时间降序排序并随机选择一条
                val sortedMemories = memories.sortedByDescending { it.timestamp }
                val randomIndex = if (sortedMemories.size > 1) {
                    Random.nextInt(minOf(5, sortedMemories.size))
                } else {
                    0
                }

                sb.append("- ${sortedMemories[randomIndex].content}\n")
                sb.append("(这是较早前的记忆)\n")
            }

            // 获取今日消息
            val allMessages = dbHelper.getMessagesForChatList(chatId)
            val todayMessages = allMessages.filter { it.timestamp.after(todayStart) }

            // 添加今日对话摘要
            if (todayMessages.isNotEmpty()) {
                sb.append("\n今日对话摘要：\n")

                // 只取用户消息，简化上下文
                val userMessages = todayMessages.filter { it.type == 0 }

                // 如果消息较多，随机抽取3条，否则全部使用
                val selectedMessages = if (userMessages.size > 3) {
                    // 随机抽取不同时期的消息，确保覆盖面广
                    val first = userMessages.take(userMessages.size / 3)
                        .randomOrNull()
                    val middle = userMessages.drop(userMessages.size / 3).take(userMessages.size / 3)
                        .randomOrNull()
                    val last = userMessages.takeLast(userMessages.size / 3)
                        .randomOrNull()

                    listOfNotNull(first, middle, last)
                } else {
                    userMessages
                }

                selectedMessages.forEach { message ->
                    val shortContent = if (message.content.length > 100)
                        message.content.substring(0, 100) + "..."
                    else
                        message.content
                    sb.append("- 用户说：$shortContent\n")
                }
            } else if (allMessages.isNotEmpty()) {
                sb.append("\n最近对话摘要：\n")

                // 如果有较多消息，随机抽取1条
                val recentUserMessages = allMessages.filter { it.type == 0 }
                if (recentUserMessages.isNotEmpty()) {
                    val selectedMessage = if (recentUserMessages.size > 3) {
                        recentUserMessages.takeLast(3).random()
                    } else {
                        recentUserMessages.last()
                    }

                    val shortContent = if (selectedMessage.content.length > 50)
                        selectedMessage.content.substring(0, 50) + "..."
                    else
                        selectedMessage.content
                    sb.append("- 用户说：$shortContent\n")
                    sb.append("(这是较早前的对话)\n")
                }
            }

            // 如果没有任何信息，添加基本提示
            if (todayMemories.isEmpty() && todayMessages.isEmpty() &&
                memories.isEmpty() && allMessages.isEmpty()) {
                sb.append("\n没有找到任何对话记录，可以写一篇关于期待与用户交流的日记。")
            }

            return sb.toString()

        } catch (e: Exception) {
            Log.e(TAG, "构建日记上下文异常: ${e.message}", e)
            return "今天是$dateStr，没有特别的对话记录。"
        }
    }

    /**
     * 从生成的日记中提取标题和内容
     */
    private fun extractTitleAndContent(diary: String): Pair<String, String> {
        val lines = diary.trim().split("\n")

        // 如果只有一行，直接作为内容返回
        if (lines.size <= 1) {
            return Pair("今日日记", diary)
        }

        // 第一行为标题
        val title = lines[0].trim()

        // 剩余内容为正文
        val content = lines.subList(1, lines.size).joinToString("\n").trim()

        return Pair(title, content)
    }

    /**
     * 查找今日有记忆的聊天
     * 返回今日有新增记忆的聊天ID，如果没有则返回null
     */
    private suspend fun findChatWithTodayMemories(): String? {
        // 获取今天凌晨的时间戳
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.time

        // 获取所有活跃聊天
        val activeChats = dbHelper.getAllActiveChats().first()

        // 遍历每个聊天，查找今日有记忆的聊天
        for (chat in activeChats) {
            val memories = dbHelper.getMemoriesForChatAsList(chat.id)
            // 如果有今日创建的记忆，返回该聊天ID
            if (memories.any { it.timestamp.after(todayStart) }) {
                return chat.id
            }
        }

        // 如果没有找到今日有记忆的聊天，尝试找最近有消息的聊天
        for (chat in activeChats) {
            val messages = dbHelper.getMessagesForChatList(chat.id)
            // 查找今日消息
            val todayMessages = messages.filter { it.timestamp.after(todayStart) }
            if (todayMessages.isNotEmpty() && todayMessages.any { it.type == 0 }) {
                // 返回有今日用户消息的聊天
                return chat.id
            }
        }

        // 如果没有找到任何有记忆或今日消息的聊天，返回null
        return null
    }

    /**
     * 根据ID获取动态详情
     * @param momentId 动态ID
     * @return 动态对象，如果不存在则返回null
     */
    suspend fun getMomentById(momentId: String): Moment? = withContext(Dispatchers.IO) {
        try {
            val momentEntity = dbHelper.getMomentById(momentId)
            return@withContext momentEntity?.toMoment()
        } catch (e: Exception) {
            Log.e(TAG, "获取动态详情失败: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * 删除动态
     */
    suspend fun deleteMoment(momentId: String) {
        withContext(Dispatchers.IO) {
            dbHelper.softDeleteMoment(momentId)
            Log.d(TAG, "已软删除动态: id=$momentId")
        }
    }

    /**
     * 检查今天是否已生成AI日记
     */
    suspend fun hasTodayAIDiary(): Boolean {
        return withContext(Dispatchers.IO) {
            // 获取所有AI生成的动态
            val aiMoments = dbHelper.getAIGeneratedMoments()
            if (aiMoments.isEmpty()) return@withContext false

            // 获取今天凌晨的时间戳
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.time

            // 检查是否有今天的日记
            aiMoments.any { it.timestamp.after(todayStart) }
        }
    }

    /**
     * MomentEntity转换为Moment
     */
    private fun MomentEntity.toMoment(): Moment {
        return Moment(
            id = this.id,
            content = this.content,
            type = if (this.type == 0) MomentType.USER_UPLOADED else MomentType.AI_GENERATED,
            timestamp = this.timestamp,
            imageUri = this.imageUri,
            chatId = this.chatId,
            title = this.title
        )
    }
}

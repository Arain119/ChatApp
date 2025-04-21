package com.example.chatapp.ui

import android.content.Context
import android.net.Uri
import com.bumptech.glide.request.RequestOptions
import android.os.Build
import android.text.Layout
import android.text.method.LinkMovementMethod
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import androidx.core.content.ContextCompat
import com.example.chatapp.R
import com.example.chatapp.data.ContentType
import com.example.chatapp.data.Message
import com.example.chatapp.data.MessageType
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.utils.HapticUtils
import com.example.chatapp.utils.MarkdownFormatter
import java.text.SimpleDateFormat
import java.util.Locale
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import java.io.File

/**
 * 消息适配器，用于显示聊天消息
 */
class MessageAdapter(
    private val settingsManager: SettingsManager,
    private val onLongClick: ((Message, View) -> Unit)? = null,
    private val onCopyClick: ((String) -> Unit)? = null,
    private val onRegenerateClick: ((Message) -> Unit)? = null,
    private val onEditClick: ((Message) -> Unit)? = null,
    private val onDeleteClick: ((Message) -> Unit)? = null,
    private val onLoadMore: ((Boolean) -> Unit)? = null,
    private val onFeedbackClick: ((Message, Boolean) -> Unit)? = null,
    private val onDocumentClick: ((String) -> Unit)? = null,
    private val onImageClick: ((String) -> Unit)? = null
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()), SettingsManager.AvatarChangeListener {

    // 视图类型
    companion object {
        const val VIEW_TYPE_USER = 1
        const val VIEW_TYPE_AI = 2
        const val VIEW_TYPE_LOADING_HEADER = 3
        const val VIEW_TYPE_LOADING_FOOTER = 4
        const val VIEW_TYPE_DOCUMENT = 5
        private const val TAG = "MessageAdapter"
    }

    // 初始化时注册观察者
    init {
        settingsManager.addAvatarChangeListener(this)
    }

    // 实现观察者接口方法，当头像改变时刷新适配器
    override fun onAvatarChanged(isUserAvatar: Boolean) {
        Log.d(TAG, "收到头像变更通知: isUserAvatar=$isUserAvatar")
        notifyDataSetChanged()
    }

    // 在不再需要时移除监听器
    fun releaseResources() {
        settingsManager.removeAvatarChangeListener(this)
    }

    // 是否显示加载指示器
    var showLoadingHeader = false
        private set
    var showLoadingFooter = false
        private set

    // 使用函数而非静态字段以避免Locale警告
    private fun getDateFormat() = SimpleDateFormat("HH:mm", Locale.getDefault())

    // 设置加载指示器状态
    fun setLoadingState(showHeader: Boolean, showFooter: Boolean) {
        val oldShowHeader = showLoadingHeader
        val oldShowFooter = showLoadingFooter

        showLoadingHeader = showHeader
        showLoadingFooter = showFooter

        Log.d(TAG, "设置加载状态: header=$showHeader, footer=$showFooter")

        // 更新加载指示器视图
        if (oldShowHeader != showHeader) {
            if (showHeader) {
                notifyItemInserted(0)
            } else {
                notifyItemRemoved(0)
            }
        }

        if (oldShowFooter != showFooter) {
            if (showFooter) {
                notifyItemInserted(itemCount - 1)
            } else {
                notifyItemRemoved(itemCount - 1)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        // 处理加载指示器
        if (showLoadingHeader && position == 0) {
            return VIEW_TYPE_LOADING_HEADER
        }

        if (showLoadingFooter && position == itemCount - 1) {
            return VIEW_TYPE_LOADING_FOOTER
        }

        // 调整真实数据位置
        val dataPosition = if (showLoadingHeader) position - 1 else position
        val message = getItem(dataPosition)

        // 根据内容类型返回视图类型
        return when {
            message.contentType == ContentType.DOCUMENT -> VIEW_TYPE_DOCUMENT
            message.type == MessageType.USER -> VIEW_TYPE_USER
            message.type == MessageType.AI -> VIEW_TYPE_AI
            else -> VIEW_TYPE_USER  // 默认作为用户消息处理
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_user, parent, false)
                UserMessageViewHolder(view, onLongClick, onCopyClick, onEditClick, onDeleteClick, this::getDateFormat)
            }
            VIEW_TYPE_AI -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_gpt, parent, false)
                AiMessageViewHolder(view, onLongClick, onCopyClick, onRegenerateClick, onDeleteClick, onFeedbackClick)
            }
            VIEW_TYPE_LOADING_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_loading, parent, false)
                LoadingViewHolder(view, true)
            }
            VIEW_TYPE_LOADING_FOOTER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_loading, parent, false)
                LoadingViewHolder(view, false)
            }
            VIEW_TYPE_DOCUMENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_document, parent, false)
                DocumentViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // 处理加载指示器
        if (holder is LoadingViewHolder) {
            holder.bind()
            return
        }

        // 调整真实数据位置
        val dataPosition = if (showLoadingHeader) position - 1 else position
        val message = getItem(dataPosition)
        val isLastItem = position == itemCount - 1 || (showLoadingFooter && position == itemCount - 2)

        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AiMessageViewHolder -> holder.bind(message, isLastItem)
            is DocumentViewHolder -> holder.bind(message)
        }
    }

    /**
     * 文档视图ViewHolder
     */
    inner class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val documentIcon: ImageView = itemView.findViewById(R.id.documentIcon)
        private val documentTitle: TextView = itemView.findViewById(R.id.documentTitle)
        private val documentInfo: TextView = itemView.findViewById(R.id.documentInfo)
        private val documentHint: TextView = itemView.findViewById(R.id.documentHint)
        private val userAvatarView: ImageView = itemView.findViewById(R.id.userAvatar)

        fun bind(message: Message) {
            // 提取文档名称，去除前缀和后缀
            var displayTitle = message.content
            var fileExtension = ""

            // 去除"请分析文档:"前缀（处理中英文冒号）
            if (displayTitle.contains("请分析文档:")) {
                displayTitle = displayTitle.substringAfter("请分析文档:").trim()
            } else if (displayTitle.contains("请分析文档：")) {
                displayTitle = displayTitle.substringAfter("请分析文档：").trim()
            }

            // 通用方法去除所有常见文件后缀并获取文件类型
            val commonExtensions = arrayOf(
                ".txt", ".pdf", ".doc", ".docx",
                ".xls", ".xlsx", ".ppt", ".pptx",
                ".csv", ".json", ".xml", ".html"
            )

            for (ext in commonExtensions) {
                if (displayTitle.endsWith(ext, ignoreCase = true)) {
                    fileExtension = ext.substring(1).toUpperCase(Locale.ROOT) // 移除点号并转为大写
                    displayTitle = displayTitle.substring(0, displayTitle.length - ext.length)
                    break
                }
            }

            // 设置加粗文本
            documentTitle.text = displayTitle
            documentTitle.setTypeface(documentTitle.typeface, android.graphics.Typeface.BOLD)

            // 使用消息对象中的文档信息
            val fileSize = message.documentSize ?: "未知大小"
            val fileType = message.documentType ?: fileExtension.ifEmpty { "TXT" }

            // 显示文件信息
            documentInfo.text = "$fileSize | $fileType"

            // 设置用户头像
            loadUserAvatar(userAvatarView)

            // 添加点击效果并启动文档查看器
            itemView.setOnClickListener {
                // 添加轻微震动反馈
                HapticUtils.performViewHapticFeedback(itemView, false)

                // 添加轻微点击动画
                itemView.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        itemView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()

                        // 调用onDocumentClick回调
                        onDocumentClick?.invoke(displayTitle)
                    }
                    .start()
            }

            // 添加进入动画
            addEnterAnimation()
        }

        private fun addEnterAnimation() {
            itemView.alpha = 0f
            itemView.translationX = 50f
            itemView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(300)
                .start()
        }
    }

    /**
     * 长文本处理工具方法
     */
    private fun setupLongTextDisplay(textView: TextView) {
        // 确保行间距合适
        textView.setLineSpacing(0f, 1.2f)

        // 确保文本可以换行和自动调整
        textView.maxLines = Int.MAX_VALUE
        textView.isSingleLine = false

        // 在API 23+上设置更好的断字和换行策略
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 更改为简单换行策略，尽可能填充每行空间
            textView.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
            textView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
        }

        // 确保文本可选择
        textView.setTextIsSelectable(true)

        // 设置链接可点击
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    /**
     * 加载视图ViewHolder
     */
    inner class LoadingViewHolder(
        itemView: View,
        private val isHeader: Boolean
    ) : RecyclerView.ViewHolder(itemView) {
        private val progressBar: ProgressBar = itemView.findViewById(R.id.loadingProgressBar)
        private val loadingText: TextView = itemView.findViewById(R.id.loadingText)

        init {
            // 设置加载时触发加载更多
            itemView.tag = if (isHeader) "header" else "footer"
            itemView.post {
                onLoadMore?.invoke(isHeader)
            }
        }

        fun bind() {
            loadingText.text = if (isHeader) "加载更多历史消息..." else "加载更多新消息..."
        }
    }

    /**
     * 加载用户头像的统一方法
     */
    private fun loadUserAvatar(imageView: ImageView) {
        try {
            val userAvatarUri = settingsManager.userAvatarUri
            Log.d(TAG, "加载用户头像: $userAvatarUri")

            if (userAvatarUri != null) {
                // 验证文件是否存在
                val uriObj = Uri.parse(userAvatarUri)
                if (uriObj.scheme == "file") {
                    val file = File(uriObj.path ?: "")
                    if (!file.exists() || file.length() == 0L) {
                        Log.e(TAG, "头像文件不存在或为空: $userAvatarUri")
                        imageView.setImageResource(R.drawable.default_user_avatar)
                        return
                    }
                    Log.d(TAG, "头像文件存在: ${file.absolutePath}, 大小: ${file.length()} 字节")
                }

                // 加载头像，禁用缓存
                Glide.with(imageView.context)
                    .load(uriObj)
                    .apply(RequestOptions.circleCropTransform())
                    .apply(RequestOptions.skipMemoryCacheOf(true))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(imageView)
            } else {
                // 没有设置自定义头像，使用默认头像
                imageView.setImageResource(R.drawable.default_user_avatar)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载用户头像失败: ${e.message}", e)
            imageView.setImageResource(R.drawable.default_user_avatar)
        }
    }

    /**
     * 加载AI头像的统一方法
     */
    private fun loadAiAvatar(imageView: ImageView) {
        try {
            val aiAvatarUri = settingsManager.aiAvatarUri
            Log.d(TAG, "加载AI头像: $aiAvatarUri")

            if (aiAvatarUri != null) {
                // 验证文件是否存在
                val uriObj = Uri.parse(aiAvatarUri)
                if (uriObj.scheme == "file") {
                    val file = File(uriObj.path ?: "")
                    if (!file.exists() || file.length() == 0L) {
                        Log.e(TAG, "AI头像文件不存在或为空: $aiAvatarUri")
                        imageView.setImageResource(R.drawable.default_ai_avatar)
                        return
                    }
                    Log.d(TAG, "AI头像文件存在: ${file.absolutePath}, 大小: ${file.length()} 字节")
                }

                // 加载头像，禁用缓存
                Glide.with(imageView.context)
                    .load(uriObj)
                    .apply(RequestOptions.circleCropTransform())
                    .apply(RequestOptions.skipMemoryCacheOf(true))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(imageView)
            } else {
                // 没有设置自定义头像，使用默认头像
                imageView.setImageResource(R.drawable.default_ai_avatar)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载AI头像失败: ${e.message}", e)
            imageView.setImageResource(R.drawable.default_ai_avatar)
        }
    }

    /**
     * 用户消息ViewHolder
     */
    inner class UserMessageViewHolder(
        itemView: View,
        private val onLongClick: ((Message, View) -> Unit)?,
        private val onCopyClick: ((String) -> Unit)?,
        private val onEditClick: ((Message) -> Unit)?,
        private val onDeleteClick: ((Message) -> Unit)?,
        private val dateFormatProvider: () -> SimpleDateFormat
    ) : RecyclerView.ViewHolder(itemView) {
        val userAvatarView: ImageView = itemView.findViewById(R.id.userAvatar)
        private val contentTextView: TextView = itemView.findViewById(R.id.userMessageText)
        private val messageImageView: ImageView = itemView.findViewById(R.id.userMessageImage)
        private val messageImageInTextView: ImageView = itemView.findViewById(R.id.userMessageImageInText)
        private val timeStampView: TextView = itemView.findViewById(R.id.timeStamp)
        private val imageTimeStampView: TextView = itemView.findViewById(R.id.imageTimeStamp)
        private val messageActions: LinearLayout = itemView.findViewById(R.id.messageActions)
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        private val userMessageContainer: LinearLayout = itemView.findViewById(R.id.userMessageContainer)

        // 图片操作按钮区域
        private val imageMessageActions: LinearLayout = itemView.findViewById(R.id.imageMessageActions)
        private val imageCopyButton: ImageButton = itemView.findViewById(R.id.imageCopyButton)
        private val imageEditButton: ImageButton = itemView.findViewById(R.id.imageEditButton)
        private val imageDeleteButton: ImageButton = itemView.findViewById(R.id.imageDeleteButton)

        fun bind(message: Message) {
            // 添加日志以便调试
            Log.d(TAG, "绑定用户消息: 类型=${message.contentType}, 内容=${message.content.take(20)}...")

            // 加载用户头像
            loadUserAvatar(userAvatarView)

            // 设置时间文本（两个时间戳都设置相同的时间）
            val timeText = dateFormatProvider().format(message.timestamp)
            timeStampView.text = timeText
            imageTimeStampView.text = timeText

            // 根据内容类型显示不同视图
            when (message.contentType) {
                ContentType.TEXT -> {
                    // 纯文本消息
                    MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content)
                    contentTextView.visibility = View.VISIBLE
                    messageImageView.visibility = View.GONE
                    messageImageInTextView.visibility = View.GONE
                    userMessageContainer.visibility = View.VISIBLE

                    // 确保长文本能正确显示
                    setupLongTextDisplay(contentTextView)

                    // 显示常规时间戳，隐藏图片下方时间戳和图片操作区
                    timeStampView.visibility = View.VISIBLE
                    imageTimeStampView.visibility = View.GONE
                    imageMessageActions.visibility = View.GONE
                }
                ContentType.IMAGE -> {
                    // 纯图片消息，隐藏消息容器
                    contentTextView.visibility = View.GONE
                    userMessageContainer.visibility = View.GONE
                    messageImageInTextView.visibility = View.GONE

                    // 显示外部图片
                    messageImageView.visibility = View.VISIBLE
                    displayImage(message.imageData, messageImageView)

                    // 图片点击事件
                    messageImageView.setOnClickListener {
                        // 添加震动反馈
                        HapticUtils.performViewHapticFeedback(messageImageView, false)

                        // 调用图片点击回调
                        message.imageData?.let { imageData ->
                            onImageClick?.invoke(imageData)
                        }

                        // 隐藏操作区
                        imageMessageActions.visibility = View.GONE
                    }

                    // 隐藏常规时间戳和操作区，显示图片下方时间戳
                    timeStampView.visibility = View.GONE
                    imageTimeStampView.visibility = View.VISIBLE
                    messageActions.visibility = View.GONE
                }
                ContentType.IMAGE_WITH_TEXT -> {
                    // 图片+文本消息，检查是否有实际的文本内容
                    val hasRealContent = message.content.isNotEmpty() &&
                            !message.content.startsWith("请用中文分析这张图片:") &&
                            !message.content.startsWith("请用中文分析这张图片：")

                    if (hasRealContent) {
                        // 有实际内容，显示消息容器和文本
                        MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content)
                        contentTextView.visibility = View.VISIBLE
                        userMessageContainer.visibility = View.VISIBLE
                        messageImageView.visibility = View.GONE
                        setupLongTextDisplay(contentTextView)

                        // 显示内部图片（在气泡内）
                        messageImageInTextView.visibility = View.VISIBLE
                        displayImage(message.imageData, messageImageInTextView)

                        // 显示常规时间戳，隐藏图片下方时间戳和图片操作区
                        timeStampView.visibility = View.VISIBLE
                        imageTimeStampView.visibility = View.GONE
                        imageMessageActions.visibility = View.GONE

                        // 内部图片点击事件
                        messageImageInTextView.setOnClickListener {
                            // 添加震动反馈
                            HapticUtils.performViewHapticFeedback(messageImageInTextView, false)

                            // 调用图片点击回调
                            message.imageData?.let { imageData ->
                                onImageClick?.invoke(imageData)
                            }

                            // 如果操作按钮显示，则隐藏
                            if (messageActions.visibility == View.VISIBLE) {
                                messageActions.visibility = View.GONE
                            }
                        }
                    } else {
                        // 没有实际内容，处理方式与纯图片消息相同
                        contentTextView.visibility = View.GONE
                        userMessageContainer.visibility = View.GONE
                        messageImageInTextView.visibility = View.GONE

                        // 显示外部图片
                        messageImageView.visibility = View.VISIBLE
                        displayImage(message.imageData, messageImageView)

                        // 图片点击事件
                        messageImageView.setOnClickListener {
                            // 添加震动反馈
                            HapticUtils.performViewHapticFeedback(messageImageView, false)

                            // 调用图片点击回调
                            message.imageData?.let { imageData ->
                                onImageClick?.invoke(imageData)
                            }

                            // 如果操作按钮显示，则隐藏
                            if (imageMessageActions.visibility == View.VISIBLE) {
                                imageMessageActions.visibility = View.GONE
                            }
                        }

                        // 隐藏常规时间戳和操作区，显示图片下方时间戳
                        timeStampView.visibility = View.GONE
                        imageTimeStampView.visibility = View.VISIBLE
                        messageActions.visibility = View.GONE
                    }
                }
                ContentType.DOCUMENT -> {
                    // 用户消息不应该出现文档类型，但为了穷尽处理，将其显示为普通文本
                    MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content)
                    contentTextView.visibility = View.VISIBLE
                    messageImageView.visibility = View.GONE
                    messageImageInTextView.visibility = View.GONE
                    userMessageContainer.visibility = View.VISIBLE
                    setupLongTextDisplay(contentTextView)

                    // 显示常规时间戳，隐藏图片下方时间戳和图片操作区
                    timeStampView.visibility = View.VISIBLE
                    imageTimeStampView.visibility = View.GONE
                    imageMessageActions.visibility = View.GONE
                }
            }

            // 初始化操作区为隐藏状态
            messageActions.visibility = View.GONE
            imageMessageActions.visibility = View.GONE

            // 设置复制按钮点击事件
            copyButton.setOnClickListener {
                // 添加震动反馈
                HapticUtils.performViewHapticFeedback(copyButton)

                onCopyClick?.invoke(message.content)
                // 点击后隐藏操作区
                messageActions.visibility = View.GONE
            }

            // 设置编辑按钮点击事件
            editButton.setOnClickListener {
                // 添加震动反馈
                HapticUtils.performViewHapticFeedback(editButton)

                onEditClick?.invoke(message)
                // 点击后隐藏操作区
                messageActions.visibility = View.GONE
            }

            // 设置删除按钮点击事件
            deleteButton.setOnClickListener {
                // 添加震动反馈
                HapticUtils.performViewHapticFeedback(deleteButton)

                Log.d(TAG, "用户消息删除按钮被点击 message=${message.id}")
                onDeleteClick?.invoke(message)
                // 点击后隐藏操作区
                messageActions.visibility = View.GONE
            }

            // 设置图片操作按钮事件
            imageCopyButton.setOnClickListener {
                // 添加震动反馈
                HapticUtils.performViewHapticFeedback(imageCopyButton)

                onCopyClick?.invoke(message.content)
                // 点击后隐藏操作区
                imageMessageActions.visibility = View.GONE
            }

            imageEditButton.setOnClickListener {
                // 添加震动反馈
                HapticUtils.performViewHapticFeedback(imageEditButton)

                onEditClick?.invoke(message)
                // 点击后隐藏操作区
                imageMessageActions.visibility = View.GONE
            }

            imageDeleteButton.setOnClickListener {
                // 添加震动反馈
                HapticUtils.performViewHapticFeedback(imageDeleteButton)

                Log.d(TAG, "图片消息删除按钮被点击 message=${message.id}")
                onDeleteClick?.invoke(message)
                // 点击后隐藏操作区
                imageMessageActions.visibility = View.GONE
            }

            // 设置长按监听 - 修改为只调用回调，不自行处理按钮显示
            itemView.setOnLongClickListener {
                // 添加震动反馈
                HapticUtils.performHapticFeedback(itemView.context)

                // 调用外部长按处理方法，传递消息和视图
                onLongClick?.invoke(message, itemView)
                true
            }

            // 确保消息文本也能接收长按事件
            contentTextView.setOnLongClickListener {
                itemView.performLongClick()
                true
            }

            // 确保图片也能接收长按事件
            messageImageView.setOnLongClickListener {
                itemView.performLongClick()
                true
            }

            messageImageInTextView.setOnLongClickListener {
                itemView.performLongClick()
                true
            }

            // 单击消息效果
            contentTextView.setOnClickListener {
                // 轻微震动反馈
                HapticUtils.performViewHapticFeedback(contentTextView, false)

                // 如果操作按钮显示，则隐藏
                if (messageActions.visibility == View.VISIBLE) {
                    messageActions.visibility = View.GONE
                }
                if (imageMessageActions.visibility == View.VISIBLE) {
                    imageMessageActions.visibility = View.GONE
                }
            }

            // 添加进入动画效果
            addEnterAnimation()
        }

        private fun addEnterAnimation() {
            itemView.alpha = 0f
            itemView.translationX = 50f
            itemView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(300)
                .start()
        }
    }

    /**
     * AI消息ViewHolder
     */
    inner class AiMessageViewHolder(
        itemView: View,
        private val onLongClick: ((Message, View) -> Unit)?,
        private val onCopyClick: ((String) -> Unit)?,
        private val onRegenerateClick: ((Message) -> Unit)?,
        private val onDeleteClick: ((Message) -> Unit)?, // 删除回调
        private val onFeedbackClick: ((Message, Boolean) -> Unit)? // 反馈回调
    ) : RecyclerView.ViewHolder(itemView) {
        val aiAvatarView: ImageView = itemView.findViewById(R.id.aiAvatar)
        private val contentTextView: TextView = itemView.findViewById(R.id.gptMessageText)
        private val messageImageView: ImageView = itemView.findViewById(R.id.aiMessageImage)
        private val timeStampView: TextView = itemView.findViewById(R.id.timeStamp)
        private val loadingIndicator: View = itemView.findViewById(R.id.loadingIndicator)
        private val messageActions: View = itemView.findViewById(R.id.messageActions)
        private val copyButton: ImageButton? = itemView.findViewById(R.id.copyButton)
        private val shareButton: ImageButton? = itemView.findViewById(R.id.shareButton)
        private val regenerateButton: ImageButton? = itemView.findViewById(R.id.regenerateButton)
        private val deleteButton: ImageButton? = itemView.findViewById(R.id.deleteButton) // 删除按钮

        // 反馈按钮 - messageActions的子视图
        private val thumbUpButton: ImageButton = itemView.findViewById(R.id.thumbUpButton)
        private val thumbDownButton: ImageButton = itemView.findViewById(R.id.thumbDownButton)

        // 跟踪当前反馈状态
        private var currentFeedbackState: Boolean? = null // null=未反馈，true=赞，false=踩

        fun bind(message: Message, isLastItem: Boolean) {
            // 加载AI头像
            loadAiAvatar(aiAvatarView)

            // 确保容器和加载指示器互斥显示
            val messageContainer = itemView.findViewById<LinearLayout>(R.id.aiMessageContainer)

            if (message.isProcessing) {
                // 显示加载指示器
                loadingIndicator.visibility = View.VISIBLE
                // 隐藏消息容器（这可以避免出现空气泡）
                messageContainer.visibility = View.GONE
                // 隐藏其他元素
                messageActions.visibility = View.GONE
                timeStampView.visibility = View.GONE
            } else {
                // 隐藏加载指示器
                loadingIndicator.visibility = View.GONE
                // 显示消息容器
                messageContainer.visibility = View.VISIBLE

                // 根据内容类型显示不同视图
                when (message.contentType) {
                    ContentType.TEXT -> {
                        // 应用新的Markdown格式化
                        MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content)
                        contentTextView.visibility = View.VISIBLE
                        messageImageView.visibility = View.GONE

                        // 确保长文本能正确显示
                        setupLongTextDisplay(contentTextView)
                    }
                    ContentType.IMAGE -> {
                        contentTextView.visibility = View.GONE
                        messageImageView.visibility = View.VISIBLE
                        displayImage(message.imageData, messageImageView)

                        // 添加图片点击事件
                        messageImageView.setOnClickListener {
                            // 震动反馈
                            HapticUtils.performViewHapticFeedback(messageImageView, false)

                            // 调用图片点击回调
                            message.imageData?.let { imageData ->
                                onImageClick?.invoke(imageData)
                            }

                            // 如果操作按钮显示，则隐藏
                            if (messageActions.visibility == View.VISIBLE) {
                                messageActions.visibility = View.GONE
                            }
                        }
                    }
                    ContentType.IMAGE_WITH_TEXT -> {
                        // 应用新的Markdown格式化
                        MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content)
                        contentTextView.visibility = View.VISIBLE

                        // 确保长文本能正确显示
                        setupLongTextDisplay(contentTextView)

                        messageImageView.visibility = View.VISIBLE
                        displayImage(message.imageData, messageImageView)

                        // 添加图片点击事件
                        messageImageView.setOnClickListener {
                            // 震动反馈
                            HapticUtils.performViewHapticFeedback(messageImageView, false)

                            // 调用图片点击回调
                            message.imageData?.let { imageData ->
                                onImageClick?.invoke(imageData)
                            }

                            // 如果操作按钮显示，则隐藏
                            if (messageActions.visibility == View.VISIBLE) {
                                messageActions.visibility = View.GONE
                            }
                        }
                    }
                    ContentType.DOCUMENT -> {
                        // AI回复也不应该是文档类型，但为了穷尽处理，显示为普通文本
                        MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content)
                        contentTextView.visibility = View.VISIBLE
                        messageImageView.visibility = View.GONE
                        setupLongTextDisplay(contentTextView)
                    }
                }

                // 设置时间戳
                timeStampView.text = getDateFormat().format(message.timestamp)
                timeStampView.visibility = View.VISIBLE

                // 初始隐藏消息操作区
                messageActions.visibility = View.GONE

                // 设置反馈按钮点击事件
                setupFeedbackButtons(message)

                // 设置复制按钮点击事件
                copyButton?.setOnClickListener {
                    // 震动反馈
                    HapticUtils.performViewHapticFeedback(copyButton)

                    onCopyClick?.invoke(message.content)
                    messageActions.visibility = View.GONE  // 点击后隐藏操作区
                }

                // 设置分享按钮点击事件
                shareButton?.setOnClickListener {
                    // 震动反馈
                    HapticUtils.performViewHapticFeedback(shareButton)

                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, message.content)
                        type = "text/plain"
                    }
                    val shareIntent = android.content.Intent.createChooser(sendIntent, "分享消息")
                    itemView.context.startActivity(shareIntent)
                    messageActions.visibility = View.GONE  // 点击后隐藏操作区
                }

                // 设置重新生成按钮点击事件
                regenerateButton?.setOnClickListener {
                    // 震动反馈
                    HapticUtils.performViewHapticFeedback(regenerateButton, true)

                    onRegenerateClick?.invoke(message)
                    messageActions.visibility = View.GONE  // 点击后隐藏操作区
                }

                // 设置删除按钮点击事件
                deleteButton?.setOnClickListener {
                    // 震动反馈
                    HapticUtils.performViewHapticFeedback(deleteButton, true)

                    Log.d(TAG, "AI消息删除按钮被点击 message=${message.id}")
                    onDeleteClick?.invoke(message)
                    messageActions.visibility = View.GONE  // 点击后隐藏操作区
                }

                // 设置长按监听
                itemView.setOnLongClickListener {
                    // 添加震动反馈
                    HapticUtils.performHapticFeedback(itemView.context)

                    // 通知外部处理按钮显示/隐藏
                    onLongClick?.invoke(message, itemView)
                    true
                }

                // 消息点击事件
                contentTextView.setOnClickListener {
                    // 轻微震动反馈
                    HapticUtils.performViewHapticFeedback(contentTextView, false)

                    // 如果操作按钮显示，则隐藏
                    if (messageActions.visibility == View.VISIBLE) {
                        messageActions.visibility = View.GONE
                    }
                }

                // 确保消息文本区域也能接收长按事件
                contentTextView.setOnLongClickListener {
                    // 添加震动反馈
                    HapticUtils.performHapticFeedback(itemView.context)

                    // 将长按事件委托给整个item的长按处理
                    itemView.performLongClick()
                }

                // 确保图片也能接收长按事件
                messageImageView.setOnLongClickListener {
                    itemView.performLongClick()
                    true
                }

                // 消息完成后的动画
                if (isLastItem) {
                    addEnterAnimation()
                }
            }
        }

        /**
         * 设置反馈按钮逻辑
         */
        private fun setupFeedbackButtons(message: Message) {
            // 设置点赞按钮
            thumbUpButton.setOnClickListener {
                // 震动反馈
                HapticUtils.performViewHapticFeedback(thumbUpButton, false)

                // 动画效果
                animateFeedbackButton(thumbUpButton)

                // 处理反馈状态
                if (currentFeedbackState == true) {
                    // 已点赞，取消点赞
                    resetButtonState()
                    currentFeedbackState = null
                } else {
                    // 设置为点赞状态
                    updateButtonState(true)
                    currentFeedbackState = true
                    // 调用回调
                    onFeedbackClick?.invoke(message, true)
                }
            }

            // 设置点踩按钮
            thumbDownButton.setOnClickListener {
                // 震动反馈
                HapticUtils.performViewHapticFeedback(thumbDownButton, false)

                // 动画效果
                animateFeedbackButton(thumbDownButton)

                // 处理反馈状态
                if (currentFeedbackState == false) {
                    // 已点踩，取消点踩
                    resetButtonState()
                    currentFeedbackState = null
                } else {
                    // 设置为点踩状态
                    updateButtonState(false)
                    currentFeedbackState = false
                    // 调用回调
                    onFeedbackClick?.invoke(message, false)
                }
            }
        }

        /**
         * 更新按钮状态
         */
        private fun updateButtonState(isPositive: Boolean) {
            if (isPositive) {
                // 设置点赞高亮
                thumbUpButton.setColorFilter(ContextCompat.getColor(itemView.context, R.color.feedback_positive))
                thumbDownButton.setColorFilter(ContextCompat.getColor(itemView.context, R.color.text_secondary))
            } else {
                // 设置点踩高亮
                thumbUpButton.setColorFilter(ContextCompat.getColor(itemView.context, R.color.text_secondary))
                thumbDownButton.setColorFilter(ContextCompat.getColor(itemView.context, R.color.feedback_negative))
            }
        }

        /**
         * 重置按钮状态
         */
        private fun resetButtonState() {
            thumbUpButton.setColorFilter(ContextCompat.getColor(itemView.context, R.color.text_secondary))
            thumbDownButton.setColorFilter(ContextCompat.getColor(itemView.context, R.color.text_secondary))
        }

        /**
         * 按钮点击动画
         */
        private fun animateFeedbackButton(button: ImageButton) {
            // 创建按钮缩放动画
            val anim = ValueAnimator.ofFloat(1f, 1.3f, 1f)
            anim.duration = 300
            anim.interpolator = AccelerateDecelerateInterpolator()
            anim.addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                button.scaleX = scale
                button.scaleY = scale
            }
            anim.start()
        }

        private fun addEnterAnimation() {
            itemView.alpha = 0f
            itemView.translationX = -50f
            itemView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(300)
                .start()
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)

        // 当加载视图出现在屏幕上时，触发加载更多
        if (holder is LoadingViewHolder) {
            onLoadMore?.invoke(holder.itemView.tag == "header")
        }
    }

    /**
     * 显示Base64编码的图片
     */
    private fun displayImage(base64Image: String?, imageView: ImageView) {
        if (base64Image.isNullOrEmpty()) {
            imageView.visibility = View.GONE
            return
        }

        try {
            // 解码Base64图片为字节数组
            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)

            // 使用Glide加载并应用更大的圆角
            Glide.with(imageView.context)
                .load(imageBytes)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .transform(jp.wasabeef.glide.transformations.RoundedCornersTransformation(
                    dpToPx(imageView.context, 16f), 0,  // 增大圆角到16dp
                    jp.wasabeef.glide.transformations.RoundedCornersTransformation.CornerType.ALL
                ))
                .into(imageView)

            // 确保ImageView本身也有圆角边框
            imageView.background = ContextCompat.getDrawable(imageView.context, R.drawable.rounded_image_background)
            imageView.clipToOutline = true

            imageView.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "图片显示失败: ${e.message}")
            imageView.visibility = View.GONE
        }
    }

    // dp转px
    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * 强制刷新所有项
     */
    fun forceRefreshAll() {
        notifyDataSetChanged()
    }

    /**
     * 消息差异回调
     */
    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem &&
                    oldItem.imageData == newItem.imageData &&
                    oldItem.contentType == newItem.contentType
        }
    }
}

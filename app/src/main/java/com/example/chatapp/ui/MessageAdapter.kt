package com.example.chatapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.method.LinkMovementMethod
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView // 保留 ImageView 以便在 displayImage 中使用，但 ViewHolder 中的引用改为 ShapeableImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout // 确保导入 ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.chatapp.R
import com.example.chatapp.data.ContentType
import com.example.chatapp.data.Message
import com.example.chatapp.data.MessageType
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.utils.HapticUtils
import com.example.chatapp.utils.MarkdownFormatter
import com.google.android.material.imageview.ShapeableImageView // 导入 ShapeableImageView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 消息适配器，用于显示聊天消息
 */
class MessageAdapter(
    private val settingsManager: SettingsManager,
    private val onLongClick: ((message: Message, actionsViewToShow: View) -> Unit)? = null,
    private val onCopyClick: ((String) -> Unit)? = null,
    private val onRegenerateClick: ((Message) -> Unit)? = null,
    private val onEditClick: ((Message) -> Unit)? = null,
    private val onDeleteClick: ((Message) -> Unit)? = null,
    private val onLoadMore: ((Boolean) -> Unit)? = null,
    private val onFeedbackClick: ((Message, Boolean) -> Unit)? = null,
    private val onDocumentClick: ((String) -> Unit)? = null,
    private val onImageClick: ((String) -> Unit)? = null
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()), SettingsManager.AvatarChangeListener {

    companion object {
        const val VIEW_TYPE_USER = 1
        const val VIEW_TYPE_AI = 2
        const val VIEW_TYPE_LOADING_HEADER = 3
        const val VIEW_TYPE_LOADING_FOOTER = 4
        const val VIEW_TYPE_DOCUMENT = 5
        private const val TAG = "MessageAdapter"
    }

    init {
        settingsManager.addAvatarChangeListener(this)
    }

    override fun onAvatarChanged(isUserAvatar: Boolean) {
        Log.d(TAG, "收到头像变更通知: isUserAvatar=$isUserAvatar")
        // 考虑更精细的刷新，例如 notifyItemChanged(position) 如果能确定具体位置
        notifyDataSetChanged()
    }

    fun releaseResources() {
        settingsManager.removeAvatarChangeListener(this)
    }

    var showLoadingHeader = false
        private set
    var showLoadingFooter = false
        private set

    private fun getDateFormat() = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun setLoadingState(showHeader: Boolean, showFooter: Boolean) {
        val oldShowHeader = showLoadingHeader
        val oldShowFooter = showLoadingFooter
        showLoadingHeader = showHeader
        showLoadingFooter = showFooter

        if (oldShowHeader != showHeader) {
            if (showHeader) notifyItemInserted(0) else notifyItemRemoved(0)
        }
        if (oldShowFooter != showFooter) {
            val pos = if (super.getItemCount() > 0) itemCount -1 else 0 // itemCount 包含 header/footer
            if (showFooter) notifyItemInserted(pos) else notifyItemRemoved(pos)
        }
    }

    override fun getItemCount(): Int {
        var count = super.getItemCount()
        if (showLoadingHeader) count++
        if (showLoadingFooter) count++
        return count
    }

    override fun getItemViewType(position: Int): Int {
        if (showLoadingHeader && position == 0) return VIEW_TYPE_LOADING_HEADER
        if (showLoadingFooter && position == itemCount - 1) return VIEW_TYPE_LOADING_FOOTER

        val dataPosition = if (showLoadingHeader) position - 1 else position
        // 安全检查，防止 dataPosition 越界
        if (dataPosition < 0 || dataPosition >= super.getItemCount()) {
            Log.e(TAG, "Invalid data position in getItemViewType: $dataPosition, super.getItemCount(): ${super.getItemCount()}")
            // 返回一个默认类型或抛出异常，具体取决于你的错误处理策略
            return VIEW_TYPE_USER // 或者其他合适的默认/错误类型
        }
        val message = getItem(dataPosition)
        return when {
            message.contentType == ContentType.DOCUMENT -> VIEW_TYPE_DOCUMENT
            message.type == MessageType.USER -> VIEW_TYPE_USER
            message.type == MessageType.AI -> VIEW_TYPE_AI
            else -> VIEW_TYPE_USER // 默认
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> UserMessageViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_user, parent, false),
                onLongClick, onCopyClick, onEditClick, onDeleteClick, this::getDateFormat
            )
            VIEW_TYPE_AI -> AiMessageViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_message_gpt, parent, false),
                onLongClick, onCopyClick, onRegenerateClick, onDeleteClick, onFeedbackClick
            )
            VIEW_TYPE_LOADING_HEADER -> LoadingViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_loading, parent, false), true
            )
            VIEW_TYPE_LOADING_FOOTER -> LoadingViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_loading, parent, false), false
            )
            VIEW_TYPE_DOCUMENT -> DocumentViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false)
            )
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is LoadingViewHolder) {
            holder.bind()
            return
        }
        val dataPosition = if (showLoadingHeader) position - 1 else position
        // 安全检查
        if (dataPosition < 0 || dataPosition >= super.getItemCount()) {
            Log.e(TAG, "onBindViewHolder - Invalid data position: $dataPosition, super.getItemCount(): ${super.getItemCount()}")
            return
        }
        val message = getItem(dataPosition)
        val isLastItem = dataPosition == super.getItemCount() - 1

        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AiMessageViewHolder -> holder.bind(message, isLastItem)
            is DocumentViewHolder -> holder.bind(message)
        }
    }

    inner class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val documentTitle: TextView = itemView.findViewById(R.id.documentTitle)
        private val documentInfo: TextView = itemView.findViewById(R.id.documentInfo)
        private val userAvatarView: ShapeableImageView = itemView.findViewById(R.id.userAvatar) // 改为 ShapeableImageView

        fun bind(message: Message) {
            var displayTitle = message.content
            var fileExtension = ""

            if (displayTitle.contains("请分析文档:")) {
                displayTitle = displayTitle.substringAfter("请分析文档:").trim()
            } else if (displayTitle.contains("请分析文档：")) {
                displayTitle = displayTitle.substringAfter("请分析文档：").trim()
            }

            val commonExtensions = arrayOf(".txt", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".csv", ".json", ".xml", ".html")
            for (ext in commonExtensions) {
                if (displayTitle.endsWith(ext, ignoreCase = true)) {
                    fileExtension = ext.substring(1).toUpperCase(Locale.ROOT)
                    displayTitle = displayTitle.substring(0, displayTitle.length - ext.length)
                    break
                }
            }

            documentTitle.text = displayTitle
            documentTitle.setTypeface(documentTitle.typeface, android.graphics.Typeface.BOLD)
            val fileSize = message.documentSize ?: "未知大小"
            val fileType = message.documentType ?: fileExtension.ifEmpty { "TXT" }
            documentInfo.text = "$fileSize | $fileType"
            loadUserAvatar(userAvatarView)

            itemView.setOnClickListener {
                HapticUtils.performViewHapticFeedback(itemView, false)
                itemView.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                    itemView.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    onDocumentClick?.invoke(displayTitle)
                }.start()
            }
            addEnterAnimation(itemView)
        }
    }

    private fun setupLongTextDisplay(textView: TextView) {
        textView.setLineSpacing(0f, 1.2f)
        textView.maxLines = Int.MAX_VALUE
        textView.isSingleLine = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            textView.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
            textView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
        }
        textView.setTextIsSelectable(true)
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    inner class LoadingViewHolder(itemView: View, private val isHeader: Boolean) : RecyclerView.ViewHolder(itemView) {
        private val loadingText: TextView = itemView.findViewById(R.id.loadingText)
        init {
            itemView.tag = if (isHeader) "header" else "footer"
            itemView.post { onLoadMore?.invoke(isHeader) }
        }
        fun bind() {
            loadingText.text = if (isHeader) "加载更多历史消息..." else "加载更多新消息..."
        }
    }

    private fun loadUserAvatar(imageView: ShapeableImageView) { // 参数类型改为 ShapeableImageView
        try {
            val userAvatarUri = settingsManager.userAvatarUri
            if (userAvatarUri != null) {
                val uriObj = Uri.parse(userAvatarUri)
                if (uriObj.scheme == "file") {
                    val file = File(uriObj.path ?: "")
                    if (!file.exists() || file.length() == 0L) {
                        imageView.setImageResource(R.drawable.default_user_avatar)
                        return
                    }
                }
                Glide.with(imageView.context).load(uriObj)
                    // .apply(RequestOptions.circleCropTransform()) // ShapeableImageView 会处理形状
                    .skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE)
                    .error(R.drawable.default_user_avatar)
                    .into(imageView)
            } else {
                imageView.setImageResource(R.drawable.default_user_avatar)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载用户头像失败: ${e.message}", e)
            imageView.setImageResource(R.drawable.default_user_avatar)
        }
    }

    private fun loadAiAvatar(imageView: ShapeableImageView) { // 参数类型改为 ShapeableImageView
        try {
            val aiAvatarUri = settingsManager.aiAvatarUri
            if (aiAvatarUri != null) {
                val uriObj = Uri.parse(aiAvatarUri)
                if (uriObj.scheme == "file") {
                    val file = File(uriObj.path ?: "")
                    if (!file.exists() || file.length() == 0L) {
                        imageView.setImageResource(R.drawable.default_ai_avatar)
                        return
                    }
                }
                Glide.with(imageView.context).load(uriObj)
                    // .apply(RequestOptions.circleCropTransform()) // ShapeableImageView 会处理形状
                    .skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE)
                    .error(R.drawable.default_ai_avatar)
                    .into(imageView)
            } else {
                imageView.setImageResource(R.drawable.default_ai_avatar)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载AI头像失败: ${e.message}", e)
            imageView.setImageResource(R.drawable.default_ai_avatar)
        }
    }

    inner class UserMessageViewHolder(
        itemView: View,
        private val onLongClick: ((Message, View) -> Unit)?,
        private val onCopyClick: ((String) -> Unit)?,
        private val onEditClick: ((Message) -> Unit)?,
        private val onDeleteClick: ((Message) -> Unit)?,
        private val dateFormatProvider: () -> SimpleDateFormat
    ) : RecyclerView.ViewHolder(itemView) {
        val userAvatarView: ShapeableImageView = itemView.findViewById(R.id.userAvatar)
        private val contentTextView: TextView = itemView.findViewById(R.id.userMessageText)
        private val messageImageView: ShapeableImageView = itemView.findViewById(R.id.userMessageImage)
        private val imageCaptionTextView: TextView = itemView.findViewById(R.id.imageCaptionTextView)
        private val timeStampView: TextView = itemView.findViewById(R.id.timeStamp)
        private val imageTimeStampView: TextView = itemView.findViewById(R.id.imageTimeStamp)
        private val messageActions: LinearLayout = itemView.findViewById(R.id.messageActions)
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        private val userMessageContainer: LinearLayout = itemView.findViewById(R.id.userMessageContainer)
        private val imageMessageActions: LinearLayout = itemView.findViewById(R.id.imageMessageActions)
        private val imageCopyButton: ImageButton = itemView.findViewById(R.id.imageCopyButton)
        private val imageEditButton: ImageButton = itemView.findViewById(R.id.imageEditButton)
        private val imageDeleteButton: ImageButton = itemView.findViewById(R.id.imageDeleteButton)

        fun bind(message: Message) {
            loadUserAvatar(userAvatarView)
            val timeText = dateFormatProvider().format(message.timestamp)

            userMessageContainer.visibility = View.GONE
            contentTextView.visibility = View.GONE
            messageImageView.visibility = View.GONE
            imageCaptionTextView.visibility = View.GONE
            timeStampView.visibility = View.GONE
            imageTimeStampView.visibility = View.GONE
            messageActions.visibility = View.GONE
            imageMessageActions.visibility = View.GONE

            when (message.contentType) {
                ContentType.TEXT -> {
                    userMessageContainer.visibility = View.VISIBLE
                    contentTextView.visibility = View.VISIBLE
                    MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content)
                    setupLongTextDisplay(contentTextView)
                    timeStampView.text = timeText
                    timeStampView.visibility = View.VISIBLE
                    val params = messageActions.layoutParams as ConstraintLayout.LayoutParams
                    params.topToBottom = R.id.userMessageContainer
                    params.endToEnd = R.id.userMessageContainer
                    messageActions.layoutParams = params
                    setupTextLongPress(message, itemView, contentTextView, userMessageContainer)
                }
                ContentType.IMAGE -> {
                    messageImageView.visibility = View.VISIBLE
                    displayImage(message.imageData, messageImageView)
                    imageTimeStampView.text = timeText
                    imageTimeStampView.visibility = View.VISIBLE
                    val params = imageMessageActions.layoutParams as ConstraintLayout.LayoutParams
                    params.topToBottom = R.id.userMessageImage
                    params.endToEnd = R.id.userMessageImage
                    imageMessageActions.layoutParams = params
                    messageActions.visibility = View.GONE // 纯图片不显示文本操作
                    setupImageOnlyLongPress(message, itemView, messageImageView)

                    val tsParams = imageTimeStampView.layoutParams as ConstraintLayout.LayoutParams
                    tsParams.topToBottom = R.id.imageMessageActions
                    tsParams.endToEnd = R.id.userMessageImage
                    imageTimeStampView.layoutParams = tsParams
                }
                ContentType.IMAGE_WITH_TEXT -> {
                    messageImageView.visibility = View.VISIBLE
                    displayImage(message.imageData, messageImageView)
                    // 对于图片+文字，统一使用 timeStampView (原本用于纯文本的) 来显示时间戳
                    // 并且让它显示在文字描述下方
                    timeStampView.text = timeText
                    timeStampView.visibility = View.VISIBLE
                    imageTimeStampView.visibility = View.GONE // 隐藏专门为图片准备的时间戳视图

                    if (message.content.isNotEmpty()) {
                        imageCaptionTextView.visibility = View.VISIBLE
                        MarkdownFormatter.applyMarkdownToTextView(imageCaptionTextView, message.content)
                        setupLongTextDisplay(imageCaptionTextView)

                        val actionParams = messageActions.layoutParams as ConstraintLayout.LayoutParams
                        actionParams.topToBottom = R.id.imageCaptionTextView
                        actionParams.endToEnd = R.id.imageCaptionTextView
                        messageActions.layoutParams = actionParams
                        imageMessageActions.visibility = View.GONE // 有文字，不显示图片专属操作

                        val tsParams = timeStampView.layoutParams as ConstraintLayout.LayoutParams
                        tsParams.topToBottom = R.id.messageActions
                        tsParams.endToEnd = R.id.messageActions
                        timeStampView.layoutParams = tsParams

                        setupImageWithCaptionLongPress(message, itemView, imageCaptionTextView)
                    } else { // 如果没有文字说明，则行为类似纯图片
                        imageCaptionTextView.visibility = View.GONE
                        messageActions.visibility = View.GONE

                        val actionParams = imageMessageActions.layoutParams as ConstraintLayout.LayoutParams
                        actionParams.topToBottom = R.id.userMessageImage
                        actionParams.endToEnd = R.id.userMessageImage
                        imageMessageActions.layoutParams = actionParams // 显示图片操作按钮

                        imageTimeStampView.text = timeText // 此时使用图片的时间戳视图
                        imageTimeStampView.visibility = View.VISIBLE
                        val tsParams = imageTimeStampView.layoutParams as ConstraintLayout.LayoutParams
                        tsParams.topToBottom = R.id.imageMessageActions
                        tsParams.endToEnd = R.id.userMessageImage
                        imageTimeStampView.layoutParams = tsParams

                        setupImageOnlyLongPress(message, itemView, messageImageView)
                    }
                }
                ContentType.DOCUMENT -> { // 已有 DocumentViewHolder，此处作为 UserMessageViewHolder 的兼容
                    userMessageContainer.visibility = View.VISIBLE
                    contentTextView.visibility = View.VISIBLE
                    MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content) // 假设文档消息也可能包含markdown
                    setupLongTextDisplay(contentTextView)
                    timeStampView.text = timeText
                    timeStampView.visibility = View.VISIBLE
                    val params = messageActions.layoutParams as ConstraintLayout.LayoutParams
                    params.topToBottom = R.id.userMessageContainer
                    params.endToEnd = R.id.userMessageContainer
                    messageActions.layoutParams = params
                    setupTextLongPress(message, itemView, contentTextView, userMessageContainer)
                }
            }

            copyButton.setOnClickListener { handleAction(it) { onCopyClick?.invoke(message.content) } }
            editButton.setOnClickListener { handleAction(it) { onEditClick?.invoke(message) } }
            deleteButton.setOnClickListener { handleAction(it) { onDeleteClick?.invoke(message) } }

            imageCopyButton.setOnClickListener { handleAction(it) { onCopyClick?.invoke(message.content) } }
            imageEditButton.setOnClickListener { handleAction(it) { onEditClick?.invoke(message) } }
            imageDeleteButton.setOnClickListener { handleAction(it) { onDeleteClick?.invoke(message) } }

            addEnterAnimation(itemView)
        }

        private fun handleAction(view: View, action: () -> Unit) {
            HapticUtils.performViewHapticFeedback(view)
            action()
            hideAllActionMenus()
        }

        private fun hideAllActionMenus() {
            messageActions.visibility = View.GONE
            imageMessageActions.visibility = View.GONE
        }

        private fun setupTextLongPress(message: Message, itemView: View, vararg viewsToListen: View) {
            val longClickListener = View.OnLongClickListener {
                HapticUtils.performHapticFeedback(itemView.context)
                onLongClick?.invoke(message, messageActions)
                true
            }
            viewsToListen.forEach { it.setOnLongClickListener(longClickListener) }
            itemView.setOnLongClickListener(longClickListener)

            val clickListener = View.OnClickListener {
                HapticUtils.performViewHapticFeedback(it, false)
                hideAllActionMenus()
            }
            viewsToListen.forEach { it.setOnClickListener(clickListener) }
        }

        private fun setupImageOnlyLongPress(message: Message, itemView: View, imageView: ShapeableImageView) {
            val longClickListener = View.OnLongClickListener {
                HapticUtils.performHapticFeedback(itemView.context)
                onLongClick?.invoke(message, imageMessageActions)
                true
            }
            imageView.setOnLongClickListener(longClickListener)
            itemView.setOnLongClickListener(longClickListener) // 允许长按整个item来触发图片操作

            imageView.setOnClickListener {
                HapticUtils.performViewHapticFeedback(it, false)
                hideAllActionMenus()
                message.imageData?.let { onImageClick?.invoke(it) }
            }
        }

        private fun setupImageWithCaptionLongPress(message: Message, itemView: View, captionView: TextView) {
            val longClickListener = View.OnLongClickListener {
                HapticUtils.performHapticFeedback(itemView.context)
                onLongClick?.invoke(message, messageActions) // 长按文字说明，显示通用文本操作
                true
            }
            captionView.setOnLongClickListener(longClickListener)
            itemView.setOnLongClickListener(longClickListener) // 允许长按整个item来触发文本操作

            captionView.setOnClickListener {
                HapticUtils.performViewHapticFeedback(it, false)
                hideAllActionMenus()
            }
            messageImageView.setOnClickListener { // 图片点击仍然预览
                HapticUtils.performViewHapticFeedback(it, false)
                hideAllActionMenus()
                message.imageData?.let { onImageClick?.invoke(it) }
            }
        }
    }

    inner class AiMessageViewHolder(
        itemView: View,
        private val onLongClick: ((Message, View) -> Unit)?,
        private val onCopyClick: ((String) -> Unit)?,
        private val onRegenerateClick: ((Message) -> Unit)?,
        private val onDeleteClick: ((Message) -> Unit)?,
        private val onFeedbackClick: ((Message, Boolean) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        val aiAvatarView: ShapeableImageView = itemView.findViewById(R.id.aiAvatar) // 改为 ShapeableImageView
        private val contentTextView: TextView = itemView.findViewById(R.id.gptMessageText)
        private val messageImageView: ShapeableImageView = itemView.findViewById(R.id.aiMessageImage) // 改为 ShapeableImageView
        private val timeStampView: TextView = itemView.findViewById(R.id.timeStamp)
        private val loadingIndicator: View = itemView.findViewById(R.id.loadingIndicator)
        private val messageActions: View = itemView.findViewById(R.id.messageActions)
        private val copyButton: ImageButton? = itemView.findViewById(R.id.copyButton)
        private val shareButton: ImageButton? = itemView.findViewById(R.id.shareButton)
        private val regenerateButton: ImageButton? = itemView.findViewById(R.id.regenerateButton)
        private val deleteButton: ImageButton? = itemView.findViewById(R.id.deleteButton)
        private val thumbUpButton: ImageButton = itemView.findViewById(R.id.thumbUpButton)
        private val thumbDownButton: ImageButton = itemView.findViewById(R.id.thumbDownButton)
        private var currentFeedbackState: Boolean? = null

        fun bind(message: Message, isLastItem: Boolean) {
            loadAiAvatar(aiAvatarView)
            val messageContainer = itemView.findViewById<LinearLayout>(R.id.aiMessageContainer)

            if (message.isProcessing) {
                loadingIndicator.visibility = View.VISIBLE
                messageContainer.visibility = View.GONE
                messageActions.visibility = View.GONE
                timeStampView.visibility = View.GONE
                animateTypingIndicator(loadingIndicator)
            } else {
                loadingIndicator.visibility = View.GONE
                messageContainer.visibility = View.VISIBLE
                timeStampView.text = getDateFormat().format(message.timestamp)
                timeStampView.visibility = View.VISIBLE
                messageActions.visibility = View.GONE

                when (message.contentType) {
                    ContentType.TEXT -> {
                        MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content)
                        contentTextView.visibility = View.VISIBLE
                        messageImageView.visibility = View.GONE
                        setupLongTextDisplay(contentTextView)
                    }
                    ContentType.IMAGE, ContentType.IMAGE_WITH_TEXT -> {
                        MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content)
                        contentTextView.visibility = if (message.content.isNotEmpty()) View.VISIBLE else View.GONE
                        setupLongTextDisplay(contentTextView)
                        messageImageView.visibility = View.VISIBLE
                        displayImage(message.imageData, messageImageView)
                        messageImageView.setOnClickListener {
                            HapticUtils.performViewHapticFeedback(messageImageView, false)
                            message.imageData?.let { onImageClick?.invoke(it) }
                            messageActions.visibility = View.GONE
                        }
                    }
                    ContentType.DOCUMENT -> { // Should be handled by DocumentViewHolder
                        MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content)
                        contentTextView.visibility = View.VISIBLE
                        messageImageView.visibility = View.GONE
                        setupLongTextDisplay(contentTextView)
                    }
                }

                setupFeedbackButtons(message)
                copyButton?.setOnClickListener { handleAction(it) { onCopyClick?.invoke(message.content) } }
                shareButton?.setOnClickListener { handleAction(it) { shareAiMessage(message.content) } }
                regenerateButton?.setOnClickListener { handleAction(it) { onRegenerateClick?.invoke(message) } }
                deleteButton?.setOnClickListener { handleAction(it) { onDeleteClick?.invoke(message) } }

                val longClickTarget = if (message.contentType == ContentType.IMAGE && message.content.isEmpty()) messageImageView else contentTextView
                itemView.setOnLongClickListener {
                    HapticUtils.performHapticFeedback(itemView.context)
                    onLongClick?.invoke(message, messageActions)
                    true
                }
                longClickTarget.setOnLongClickListener { itemView.performLongClick() }
                contentTextView.setOnClickListener {
                    HapticUtils.performViewHapticFeedback(contentTextView, false)
                    if (messageActions.visibility == View.VISIBLE) messageActions.visibility = View.GONE
                }
                // 图片点击事件已在 displayImage 中处理，如果需要长按图片也显示操作菜单，可以额外设置
                messageImageView.setOnClickListener { // 点击图片预览
                    HapticUtils.performViewHapticFeedback(messageImageView, false)
                    if (messageActions.visibility == View.VISIBLE) messageActions.visibility = View.GONE
                    message.imageData?.let { onImageClick?.invoke(it) }
                }


                if (isLastItem) addEnterAnimation(itemView)
            }
        }

        private fun handleAction(view: View, action: () -> Unit) {
            HapticUtils.performViewHapticFeedback(view)
            action()
            messageActions.visibility = View.GONE
        }

        private fun shareAiMessage(content: String) {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, content)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "分享AI回复")
            itemView.context.startActivity(shareIntent)
        }

        private fun setupFeedbackButtons(message: Message) {
            currentFeedbackState = null // 重置状态
            resetButtonState() // 重置按钮视觉
            thumbUpButton.setOnClickListener {
                HapticUtils.performViewHapticFeedback(thumbUpButton, false)
                animateFeedbackButton(thumbUpButton)
                if (currentFeedbackState == true) { // 如果已经是赞同，再次点击取消
                    resetButtonState()
                    currentFeedbackState = null
                    // 可选: 通知ViewModel取消反馈 onFeedbackClick?.invoke(message, null)
                } else {
                    updateButtonState(true)
                    currentFeedbackState = true
                    onFeedbackClick?.invoke(message, true)
                }
            }
            thumbDownButton.setOnClickListener {
                HapticUtils.performViewHapticFeedback(thumbDownButton, false)
                animateFeedbackButton(thumbDownButton)
                if (currentFeedbackState == false) { // 如果已经是反对，再次点击取消
                    resetButtonState()
                    currentFeedbackState = null
                    // 可选: 通知ViewModel取消反馈 onFeedbackClick?.invoke(message, null)
                } else {
                    updateButtonState(false)
                    currentFeedbackState = false
                    onFeedbackClick?.invoke(message, false)
                }
            }
        }

        private fun updateButtonState(isPositive: Boolean) {
            val positiveColor = ContextCompat.getColor(itemView.context, R.color.feedback_positive)
            val negativeColor = ContextCompat.getColor(itemView.context, R.color.feedback_negative)
            val defaultColor = ContextCompat.getColor(itemView.context, R.color.text_secondary)
            thumbUpButton.setColorFilter(if (isPositive) positiveColor else defaultColor)
            thumbDownButton.setColorFilter(if (!isPositive) negativeColor else defaultColor)
        }

        private fun resetButtonState() {
            val defaultColor = ContextCompat.getColor(itemView.context, R.color.text_secondary)
            thumbUpButton.setColorFilter(defaultColor)
            thumbDownButton.setColorFilter(defaultColor)
        }

        private fun animateFeedbackButton(button: ImageButton) {
            ValueAnimator.ofFloat(1f, 1.3f, 1f).apply {
                duration = 300
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val scale = animator.animatedValue as Float
                    button.scaleX = scale
                    button.scaleY = scale
                }
                start()
            }
        }

        private fun animateTypingIndicator(view: View) {
            val dot1 = view.findViewById<TextView>(R.id.dot1)
            val dot2 = view.findViewById<TextView>(R.id.dot2)
            val dot3 = view.findViewById<TextView>(R.id.dot3)
            val animDot1 = createDotAnimation(dot1)
            val animDot2 = createDotAnimation(dot2)
            val animDot3 = createDotAnimation(dot3)

            val set = AnimatorSet()
            set.playTogether(animDot1, animDot2, animDot3)
            animDot2.startDelay = 150
            animDot3.startDelay = 300
            set.start()
        }

        private fun createDotAnimation(dot: TextView): ObjectAnimator {
            return ObjectAnimator.ofFloat(dot, "alpha", 0.3f, 1.0f).apply {
                duration = 500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
        }
    }

    // 在 MessageAdapter.addEnterAnimation 中
    private fun addEnterAnimation(itemView: View) {
        itemView.post {
            itemView.alpha = 0f
            val startX = if (itemView.layoutDirection == View.LAYOUT_DIRECTION_RTL) -50f else 50f
            itemView.translationX = startX
            itemView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(300)
                .start()
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is LoadingViewHolder) {
            onLoadMore?.invoke(holder.itemView.tag == "header")
        }
    }

    private fun displayImage(base64Image: String?, imageView: ShapeableImageView) {
        if (base64Image.isNullOrEmpty()) {
            imageView.visibility = View.GONE
            return
        }
        try {
            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
            Glide.with(imageView.context)
                .load(imageBytes)
                .skipMemoryCache(true) // 考虑是否真的需要，可能会影响性能
                .diskCacheStrategy(DiskCacheStrategy.NONE) // 考虑是否真的需要
                // 移除了 RoundedCornersTransformation，因为 ShapeableImageView 会处理圆角
                .error(R.drawable.ic_broken_image)
                .into(imageView)
            // 确保 ShapeableImageView 的 app:shapeAppearanceOverlay 已在XML中设置
            imageView.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "图片显示失败: ${e.message}")
            imageView.setImageResource(R.drawable.ic_broken_image)
            imageView.visibility = View.VISIBLE
        }
    }

    // dpToPx 方法不再需要，因为圆角大小在 XML 中通过 dp 定义
    // private fun dpToPx(context: Context, dp: Float): Int {
    //     return (dp * context.resources.displayMetrics.density).toInt()
    // }

    fun forceRefreshAll() {
        notifyDataSetChanged()
    }

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

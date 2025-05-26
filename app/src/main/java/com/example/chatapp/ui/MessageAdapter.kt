package com.example.chatapp.ui

import android.animation.ObjectAnimator // 新增导入
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent // 新增导入
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import android.animation.AnimatorSet // 确保导入 AnimatorSet

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
            val pos = if (super.getItemCount() > 0) itemCount -1 else 0
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
        if (dataPosition < 0 || dataPosition >= super.getItemCount()) {
            Log.e(TAG, "Invalid data position: $dataPosition, super.getItemCount(): ${super.getItemCount()}")
            return VIEW_TYPE_USER
        }
        val message = getItem(dataPosition)
        return when {
            message.contentType == ContentType.DOCUMENT -> VIEW_TYPE_DOCUMENT
            message.type == MessageType.USER -> VIEW_TYPE_USER
            message.type == MessageType.AI -> VIEW_TYPE_AI
            else -> VIEW_TYPE_USER
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
        private val userAvatarView: ImageView = itemView.findViewById(R.id.userAvatar)

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

    private fun loadUserAvatar(imageView: ImageView) {
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
                    .apply(RequestOptions.circleCropTransform())
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

    private fun loadAiAvatar(imageView: ImageView) {
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
                    .apply(RequestOptions.circleCropTransform())
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
        val userAvatarView: ImageView = itemView.findViewById(R.id.userAvatar)
        private val contentTextView: TextView = itemView.findViewById(R.id.userMessageText)
        private val messageImageView: ImageView = itemView.findViewById(R.id.userMessageImage)
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
                    val params = messageActions.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
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
                    val params = imageMessageActions.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                    params.topToBottom = R.id.userMessageImage
                    params.endToEnd = R.id.userMessageImage
                    imageMessageActions.layoutParams = params
                    setupImageOnlyLongPress(message, itemView, messageImageView)
                }
                ContentType.IMAGE_WITH_TEXT -> {
                    messageImageView.visibility = View.VISIBLE
                    displayImage(message.imageData, messageImageView)
                    imageTimeStampView.text = timeText
                    imageTimeStampView.visibility = View.VISIBLE

                    if (message.content.isNotEmpty()) {
                        imageCaptionTextView.visibility = View.VISIBLE
                        MarkdownFormatter.applyMarkdownToTextView(imageCaptionTextView, message.content)
                        setupLongTextDisplay(imageCaptionTextView)
                        val params = messageActions.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                        params.topToBottom = R.id.imageCaptionTextView
                        params.endToEnd = R.id.imageCaptionTextView
                        messageActions.layoutParams = params
                        setupImageWithCaptionLongPress(message, itemView, imageCaptionTextView)
                    } else {
                        val params = imageMessageActions.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                        params.topToBottom = R.id.userMessageImage
                        params.endToEnd = R.id.userMessageImage
                        imageMessageActions.layoutParams = params
                        setupImageOnlyLongPress(message, itemView, messageImageView)
                    }
                }
                ContentType.DOCUMENT -> { // Should be handled by DocumentViewHolder, but as a fallback
                    userMessageContainer.visibility = View.VISIBLE
                    contentTextView.visibility = View.VISIBLE
                    MarkdownFormatter.applyMarkdownToTextView(contentTextView, message.content)
                    setupLongTextDisplay(contentTextView)
                    timeStampView.text = timeText
                    timeStampView.visibility = View.VISIBLE
                    val params = messageActions.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
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

        private fun setupImageOnlyLongPress(message: Message, itemView: View, imageView: ImageView) {
            val longClickListener = View.OnLongClickListener {
                HapticUtils.performHapticFeedback(itemView.context)
                onLongClick?.invoke(message, imageMessageActions)
                true
            }
            imageView.setOnLongClickListener(longClickListener)
            itemView.setOnLongClickListener(longClickListener) // Allow long press on the whole item
            imageView.setOnClickListener {
                HapticUtils.performViewHapticFeedback(it, false)
                hideAllActionMenus()
                message.imageData?.let { onImageClick?.invoke(it) }
            }
        }

        private fun setupImageWithCaptionLongPress(message: Message, itemView: View, captionView: TextView) {
            val longClickListener = View.OnLongClickListener {
                HapticUtils.performHapticFeedback(itemView.context)
                onLongClick?.invoke(message, messageActions) // Show general actions for text part
                true
            }
            captionView.setOnLongClickListener(longClickListener)
            itemView.setOnLongClickListener(longClickListener) // Allow long press on the whole item to target text actions
            captionView.setOnClickListener {
                HapticUtils.performViewHapticFeedback(it, false)
                hideAllActionMenus()
            }
            messageImageView.setOnClickListener { // Image click still previews
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
        val aiAvatarView: ImageView = itemView.findViewById(R.id.aiAvatar)
        private val contentTextView: TextView = itemView.findViewById(R.id.gptMessageText)
        private val messageImageView: ImageView = itemView.findViewById(R.id.aiMessageImage)
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
                    ContentType.DOCUMENT -> {
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
                messageImageView.setOnClickListener {
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
            currentFeedbackState = null
            resetButtonState()
            thumbUpButton.setOnClickListener {
                HapticUtils.performViewHapticFeedback(thumbUpButton, false)
                animateFeedbackButton(thumbUpButton)
                if (currentFeedbackState == true) {
                    resetButtonState()
                    currentFeedbackState = null
                } else {
                    updateButtonState(true)
                    currentFeedbackState = true
                    onFeedbackClick?.invoke(message, true)
                }
            }
            thumbDownButton.setOnClickListener {
                HapticUtils.performViewHapticFeedback(thumbDownButton, false)
                animateFeedbackButton(thumbDownButton)
                if (currentFeedbackState == false) {
                    resetButtonState()
                    currentFeedbackState = null
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

            animDot1.start()
            animDot2.startDelay = 150 // Corrected: set startDelay on the animator object
            animDot2.start()
            animDot3.startDelay = 300 // Corrected: set startDelay on the animator object
            animDot3.start()
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

    private fun addEnterAnimation(itemView: View) {
        itemView.alpha = 0f
        val startX = if (itemView.layoutDirection == View.LAYOUT_DIRECTION_RTL) -50f else 50f
        itemView.translationX = startX
        itemView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(300)
            .start()
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is LoadingViewHolder) {
            onLoadMore?.invoke(holder.itemView.tag == "header")
        }
    }

    private fun displayImage(base64Image: String?, imageView: ImageView) {
        if (base64Image.isNullOrEmpty()) {
            imageView.visibility = View.GONE
            return
        }
        try {
            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
            Glide.with(imageView.context)
                .load(imageBytes)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .transform(jp.wasabeef.glide.transformations.RoundedCornersTransformation(
                    dpToPx(imageView.context, 16f), 0,
                    jp.wasabeef.glide.transformations.RoundedCornersTransformation.CornerType.ALL
                ))
                .error(R.drawable.ic_broken_image)
                .into(imageView)
            imageView.background = ContextCompat.getDrawable(imageView.context, R.drawable.rounded_image_background)
            imageView.clipToOutline = true
            imageView.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "图片显示失败: ${e.message}")
            imageView.setImageResource(R.drawable.ic_broken_image)
            imageView.visibility = View.VISIBLE
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

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

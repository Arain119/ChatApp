package com.example.chatapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.R
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.data.db.ChatEntity
import com.example.chatapp.data.db.MemoryEntity
import com.example.chatapp.utils.ExportUtils
import com.example.chatapp.utils.HapticUtils
import com.example.chatapp.utils.ImportUtils
import com.example.chatapp.viewmodel.ChatHistoryViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class ChatDetailFragment : BaseSettingsSubFragment(),
    ExportOptionsDialog.ExportOptionsListener,
    ImportOptionsDialog.ImportOptionsListener {

    private lateinit var viewModel: ChatHistoryViewModel
    private var chatId: String? = null
    private lateinit var settingsManager: SettingsManager

    // UI组件
    private lateinit var titleEditText: EditText
    private lateinit var createTimeTextView: TextView
    private lateinit var updateTimeTextView: TextView
    private lateinit var modelTypeTextView: TextView
    private lateinit var editTitleIcon: ImageView

    // 卡片视图引用
    private lateinit var infoCardView: CardView
    private lateinit var memoriesCardView: CardView
    private lateinit var actionsCardView: CardView

    // 记忆相关UI组件
    private lateinit var memoriesRecyclerView: RecyclerView
    private lateinit var memoriesAdapter: MemoryAdapter
    private lateinit var emptyMemoriesText: TextView

    // 动画状态标志
    private var animationsInitialized = false
    private var isEditingTitle = false

    // 基本信息
    private var chatEntity: ChatEntity? = null
    private var originalTitle: String = ""

    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleImportFile(uri)
            }
        }
    }

    // 初始化参数
    companion object {
        private const val ARG_CHAT_ID = "chatId"
        private const val TAG = "ChatDetailFragment"

        fun newInstance(chatId: String): ChatDetailFragment {
            val fragment = ChatDetailFragment()
            val args = Bundle()
            args.putString(ARG_CHAT_ID, chatId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun getTitle(): String = "聊天详情"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(ChatHistoryViewModel::class.java)
        settingsManager = SettingsManager(requireContext())

        // 获取参数
        chatId = arguments?.getString(ARG_CHAT_ID)
        if (chatId == null) {
            // 尝试使用 requireContext() 获取上下文
            context?.let { showErrorSnackbar(it, "无法加载聊天详情，参数错误") }
            notifyNavigationBack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 直接通过ID初始化卡片视图引用
        infoCardView = view.findViewById(R.id.info_card_view)
        memoriesCardView = view.findViewById(R.id.memories_card_view)
        actionsCardView = view.findViewById(R.id.actions_card_view)

        // 初始化视图
        titleEditText = view.findViewById(R.id.chat_title_edit)
        createTimeTextView = view.findViewById(R.id.chat_create_time)
        updateTimeTextView = view.findViewById(R.id.chat_update_time)
        modelTypeTextView = view.findViewById(R.id.chat_model_type)
        editTitleIcon = view.findViewById(R.id.edit_title_icon)

        // 初始化记忆相关视图
        memoriesRecyclerView = view.findViewById(R.id.memories_recycler_view)
        emptyMemoriesText = view.findViewById(R.id.empty_memories_text)

        // 设置元素进入动画
        prepareAnimations()

        // 设置编辑标题相关交互
        setupTitleEditing()

        // 设置操作按钮点击事件和动画
        setupActionButtons(view)

        // 设置记忆列表
        setupMemoriesList()

        // 加载聊天详情
        loadChatDetails()

        // 加载记忆列表
        loadMemories()
    }

    // 准备所有动画效果
    private fun prepareAnimations() {
        if (animationsInitialized) return

        // 设置卡片初始状态
        infoCardView.alpha = 0f
        infoCardView.translationY = 100f

        memoriesCardView.alpha = 0f
        memoriesCardView.translationY = 100f

        actionsCardView.alpha = 0f
        actionsCardView.translationY = 100f

        // 延迟执行入场动画
        Handler(Looper.getMainLooper()).postDelayed({
            startEntranceAnimations()
        }, 100)

        animationsInitialized = true
    }

    // 执行元素入场动画
    private fun startEntranceAnimations() {
        // 信息卡片的动画
        val infoCardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(infoCardView, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(infoCardView, "translationY", 100f, 0f)
            )
            duration = 400
            interpolator = DecelerateInterpolator()
        }

        // 记忆卡片的动画
        val memoriesCardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(memoriesCardView, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(memoriesCardView, "translationY", 100f, 0f)
            )
            duration = 400
            interpolator = DecelerateInterpolator()
            startDelay = 100
        }

        // 操作按钮卡片的动画
        val actionsCardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(actionsCardView, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(actionsCardView, "translationY", 100f, 0f)
            )
            duration = 400
            interpolator = DecelerateInterpolator()
            startDelay = 200
        }

        // 依次执行动画
        AnimatorSet().apply {
            playSequentially(infoCardAnim, memoriesCardAnim, actionsCardAnim)
            start()
        }
    }

    // 设置标题编辑相关交互
    private fun setupTitleEditing() {
        // 保存原始标题，用于取消编辑时恢复
        originalTitle = titleEditText.text.toString()

        // 设置编辑图标动画和点击事件
        editTitleIcon.setOnClickListener {
            // 震动反馈
            HapticUtils.performViewHapticFeedback(it)

            // 执行按钮按压效果
            animateEditIconPress(it as ImageView)

            // 启用编辑模式
            if (!isEditingTitle) {
                startTitleEditing()
            } else {
                finishTitleEditing(true)
            }
        }

        // 监听编辑框焦点变化
        titleEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isEditingTitle) {
                startTitleEditing()
            } else if (!hasFocus && isEditingTitle) {
                finishTitleEditing(true)
            }
        }
    }

    // 执行编辑图标按压动画
    private fun animateEditIconPress(icon: ImageView) {
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(icon, "scaleX", 1f, 0.8f),
                ObjectAnimator.ofFloat(icon, "scaleY", 1f, 0.8f)
            )
            duration = 100
        }

        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(icon, "scaleX", 0.8f, 1f),
                ObjectAnimator.ofFloat(icon, "scaleY", 0.8f, 1f)
            )
            duration = 200
            interpolator = OvershootInterpolator(2f)
        }

        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp)
            start()
        }
    }

    // 启动标题编辑模式
    private fun startTitleEditing() {
        isEditingTitle = true

        // 保存原始标题，用于取消编辑时恢复
        originalTitle = titleEditText.text.toString()

        // 改变图标
        editTitleIcon.setImageResource(R.drawable.ic_check)
        editTitleIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.success))

        // 设置焦点并显示键盘
        titleEditText.requestFocus()
        showKeyboard(titleEditText)

        // 可以添加微妙的动画效果而不是高光
        titleEditText.animate()
            .scaleX(1.01f)
            .scaleY(1.01f)
            .setDuration(300)
            .start()
    }

    // 完成标题编辑
    private fun finishTitleEditing(save: Boolean) {
        if (!isEditingTitle) return

        isEditingTitle = false

        // 恢复图标
        editTitleIcon.setImageResource(R.drawable.ic_edit)
        editTitleIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_secondary))

        // 取消编辑时恢复原始标题
        if (!save) {
            titleEditText.setText(originalTitle)
        }

        // 隐藏键盘
        hideKeyboard(titleEditText)

        // 移除焦点
        titleEditText.clearFocus()

        // 恢复正常大小的动画效果
        titleEditText.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(300)
            .start()

        // 如果保存且标题已更改
        if (save && titleEditText.text.toString() != originalTitle) {
            val newTitle = titleEditText.text.toString().trim()
            if (newTitle.isNotEmpty()) {
                saveTitle(newTitle)
            } else {
                // 标题为空，恢复原始标题
                titleEditText.setText(originalTitle)
                showErrorSnackbar(requireContext(),"标题不能为空")
            }
        }
    }

    // 设置操作按钮点击事件和动画
    private fun setupActionButtons(view: View) {
        // 为每个操作按钮添加点击动画
        val buttons = listOf(
            view.findViewById<View>(R.id.continue_chat_button),
            view.findViewById<View>(R.id.export_chat_button),
            view.findViewById<View>(R.id.import_chat_button),
            view.findViewById<View>(R.id.delete_chat_button)
        )

        buttons.forEach { button ->
            button.setOnClickListener { clickedButton ->
                // 执行按钮按压动画
                animateButtonPress(clickedButton)

                // 震动反馈
                HapticUtils.performViewHapticFeedback(clickedButton)

                // 延迟执行实际操作，让动画有时间完成
                Handler(Looper.getMainLooper()).postDelayed({
                    when (clickedButton.id) {
                        R.id.continue_chat_button -> continueChat()
                        R.id.export_chat_button -> exportChatHistory()
                        R.id.import_chat_button -> importChatHistory()
                        R.id.delete_chat_button -> confirmDeleteChat()
                    }
                }, 150)
            }
        }
    }

    // 执行按钮按压动画
    private fun animateButtonPress(button: View) {
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.97f),
                ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.97f)
            )
            duration = 100
        }

        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 0.97f, 1f),
                ObjectAnimator.ofFloat(button, "scaleY", 0.97f, 1f)
            )
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
        }

        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp)
            start()
        }
    }

    // 设置记忆列表
    private fun setupMemoriesList() {
        // Item 动画可以保留
        val customItemAnimator = DefaultItemAnimator().apply {
            addDuration = 300
            removeDuration = 300
            changeDuration = 300
            moveDuration = 300
        }

        // 初始化 Adapter，将监听器指向 Fragment 中的方法
        memoriesAdapter = MemoryAdapter(
            onItemClick = { memory -> showFullMemoryContentDialog(memory) }, // 处理单击事件
            onLongClick = { memory ->
                // 长按删除逻辑
                HapticUtils.performHapticFeedback(requireContext())
                showMemoryDeleteDialog(memory)
            }
        )

        memoriesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        memoriesRecyclerView.itemAnimator = customItemAnimator
        memoriesRecyclerView.adapter = memoriesAdapter
    }

    /**
     * 根据当前主题创建动态渐变背景
     */
    private fun createThemeGradientBackground(): GradientDrawable {
        // 获取当前主题颜色
        val currentTheme = settingsManager.colorTheme

        // 根据主题选择颜色
        val (startColor, endColor) = when (currentTheme) {
            "green" -> Pair(
                ContextCompat.getColor(requireContext(), R.color.theme_green),
                ContextCompat.getColor(requireContext(), R.color.primary)
            )
            "purple" -> Pair(
                ContextCompat.getColor(requireContext(), R.color.theme_purple),
                ContextCompat.getColor(requireContext(), R.color.primary_light)
            )
            "orange" -> Pair(
                ContextCompat.getColor(requireContext(), R.color.theme_orange),
                ContextCompat.getColor(requireContext(), R.color.accent)
            )
            "pink" -> Pair(
                ContextCompat.getColor(requireContext(), R.color.theme_pink),
                ContextCompat.getColor(requireContext(), R.color.primary_light)
            )
            else -> Pair(
                ContextCompat.getColor(requireContext(), R.color.primary),
                ContextCompat.getColor(requireContext(), R.color.primary_light)
            )
        }

        // 设置透明度
        val startColorWithAlpha = ColorUtils.setAlphaComponent(startColor, 40)
        val endColorWithAlpha = ColorUtils.setAlphaComponent(endColor, 25)

        // 创建渐变
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(startColorWithAlpha, endColorWithAlpha)
        ).apply {
            // 设置上部圆角，与卡片完全匹配
            cornerRadii = floatArrayOf(
                24f, 24f,  // 左上角X,Y半径
                24f, 24f,  // 右上角X,Y半径
                0f, 0f,    // 右下角X,Y半径
                0f, 0f     // 左下角X,Y半径
            )
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
    }

    // 显示键盘
    private fun showKeyboard(view: View) {
        // 检查 context 是否可用
        context?.let { ctx ->
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // 隐藏键盘
    private fun hideKeyboard(view: View) {
        context?.let { ctx ->
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun loadChatDetails() {
        val chatIdSafe = chatId ?: return

        // 加载聊天详情
        lifecycleScope.launch {
            try {
                val chat = viewModel.getChatById(chatIdSafe)
                if (chat != null) {
                    chatEntity = chat
                    // 更新UI
                    updateUIWithChatDetails(chat)
                } else {
                    context?.let { showErrorSnackbar(it, "无法加载聊天详情，聊天ID无效") }
                    notifyNavigationBack()
                }
            } catch (e: Exception) {
                context?.let { showErrorSnackbar(it, "加载聊天详情失败: ${e.message}") }
                notifyNavigationBack()
            }
        }
    }

    /**
     * 显示包含完整记忆内容的对话框
     */
    private fun showFullMemoryContentDialog(memory: MemoryEntity) {
        context?.let { ctx ->
            // 创建自定义视图
            val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_memory_detail, null)

            // 初始化视图组件
            val headerTitle = dialogView.findViewById<TextView>(R.id.memory_header_title)
            val headerDate = dialogView.findViewById<TextView>(R.id.memory_header_date)
            val contentText = dialogView.findViewById<TextView>(R.id.memory_content_text)
            val mainCard = dialogView.findViewById<MaterialCardView>(R.id.memory_main_card)
            val categoryChip = dialogView.findViewById<Chip>(R.id.memory_category_chip)
            val headerView = dialogView.findViewById<ConstraintLayout>(R.id.memory_header)

            // 创建并配置对话框
            val dialog = MaterialAlertDialogBuilder(ctx, R.style.ThemeOverlay_App_TransparentDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            // 显示对话框
            dialog.show()

            // 设置对话框宽度
            dialog.window?.let { window ->
                window.setBackgroundDrawableResource(android.R.color.transparent)
                window.setLayout(
                    (resources.displayMetrics.widthPixels * 0.92).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                window.attributes.windowAnimations = R.style.DialogAnimationSlideUp
            }

            // 使用post确保视图已测量，可以获取实际的圆角值
            mainCard.post {
                // 动态创建主题渐变背景，确保与卡片圆角匹配
                val cardRadius = mainCard.radius

                // 获取当前主题颜色
                val currentTheme = settingsManager.colorTheme

                // 根据主题选择颜色
                val (startColor, endColor) = when (currentTheme) {
                    "green" -> Pair(
                        ContextCompat.getColor(requireContext(), R.color.theme_green),
                        ContextCompat.getColor(requireContext(), R.color.primary)
                    )
                    "purple" -> Pair(
                        ContextCompat.getColor(requireContext(), R.color.theme_purple),
                        ContextCompat.getColor(requireContext(), R.color.primary_light)
                    )
                    "orange" -> Pair(
                        ContextCompat.getColor(requireContext(), R.color.theme_orange),
                        ContextCompat.getColor(requireContext(), R.color.accent)
                    )
                    "pink" -> Pair(
                        ContextCompat.getColor(requireContext(), R.color.theme_pink),
                        ContextCompat.getColor(requireContext(), R.color.primary_light)
                    )
                    else -> Pair(
                        ContextCompat.getColor(requireContext(), R.color.primary),
                        ContextCompat.getColor(requireContext(), R.color.primary_light)
                    )
                }

                // 设置透明度
                val startColorWithAlpha = ColorUtils.setAlphaComponent(startColor, 40)
                val endColorWithAlpha = ColorUtils.setAlphaComponent(endColor, 25)

                // 创建与卡片完全匹配的渐变背景
                val gradientDrawable = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(startColorWithAlpha, endColorWithAlpha)
                ).apply {
                    cornerRadii = floatArrayOf(
                        cardRadius, cardRadius,  // 左上角X,Y半径
                        cardRadius, cardRadius,  // 右上角X,Y半径
                        0f, 0f,  // 右下角X,Y半径
                        0f, 0f   // 左下角X,Y半径
                    )
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                }

                // 应用动态创建的背景
                headerView.background = gradientDrawable
            }

            // 设置标签背景与卡片相同
            categoryChip.chipBackgroundColor = ContextCompat.getColorStateList(
                requireContext(),
                R.color.card_background
            )

            // 设置记忆内容
            contentText.text = memory.content

            // 设置类别
            categoryChip.text = memory.category

            // 格式化并设置日期
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            headerDate.text = dateFormat.format(memory.timestamp)

            // 点击对话框外部或按返回键关闭
            dialog.setCanceledOnTouchOutside(true)

            // 长按对话框内容复制到剪贴板
            contentText.setOnLongClickListener {
                // 震动反馈
                HapticUtils.performHapticFeedback(requireContext())

                // 复制内容到剪贴板
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("记忆内容", memory.content)
                clipboard.setPrimaryClip(clip)

                // 显示提示
                showSuccessSnackbar(ctx, "内容已复制到剪贴板")

                // 返回true表示已处理长按事件
                true
            }

            // 添加触觉反馈
            HapticUtils.performHapticFeedback(ctx)

            // 准备动画初始状态
            prepareDialogAnimationState(mainCard)

            // 开始入场动画
            startDialogEntranceAnimation(
                mainCard = mainCard,
                categoryChip = categoryChip
            )
        }
    }

    /**
     * 准备对话框动画的初始状态
     */
    private fun prepareDialogAnimationState(mainCard: View) {
        // 设置初始状态
        mainCard.alpha = 0f
        mainCard.scaleX = 0.9f
        mainCard.scaleY = 0.9f
    }

    /**
     * 开始对话框入场动画序列
     */
    private fun startDialogEntranceAnimation(
        mainCard: View,
        categoryChip: View
    ) {
        // 主卡片动画
        val cardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(mainCard, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(mainCard, "scaleX", 0.9f, 1f),
                ObjectAnimator.ofFloat(mainCard, "scaleY", 0.9f, 1f)
            )
            duration = 300
            interpolator = DecelerateInterpolator(1.2f)
        }

        // 类别标签动画
        categoryChip.alpha = 0f
        categoryChip.translationX = 50f

        val chipAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(categoryChip, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(categoryChip, "translationX", 50f, 0f)
            )
            duration = 250
            interpolator = DecelerateInterpolator()
            startDelay = 200
        }

        // 组合所有动画并开始播放
        AnimatorSet().apply {
            playSequentially(cardAnim, chipAnim)
            start()
        }
    }

    // 显示删除记忆对话框
    private fun showMemoryDeleteDialog(memory: MemoryEntity) {
        context?.let { ctx ->
            MaterialAlertDialogBuilder(ctx, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setTitle("删除记忆")
                .setMessage("确定要删除这条记忆吗？此操作不可恢复。")
                .setPositiveButton("删除") { _, _ ->
                    // 震动反馈
                    HapticUtils.performHapticFeedback(ctx, true)
                    deleteMemory(memory.id)
                }
                .setNegativeButton("取消", null)
                .setBackground(ContextCompat.getDrawable(ctx, R.drawable.dialog_rounded_bg))
                .show()
        }
    }

    // 删除记忆
    private fun deleteMemory(memoryId: String) {
        lifecycleScope.launch {
            try {
                viewModel.deleteMemory(memoryId)
                // 成功反馈
                context?.let { showSuccessSnackbar(it, "记忆已删除") }
                // 记忆删除后自动刷新列表
                loadMemories()
            } catch (e: Exception) {
                context?.let { showErrorSnackbar(it, "删除失败: ${e.message}") }
            }
        }
    }

    // 加载记忆列表
    private fun loadMemories() {
        val chatIdSafe = chatId ?: return

        lifecycleScope.launch {
            try {
                viewModel.getChatMemories(chatIdSafe).collect { memories ->
                    // 更新适配器数据
                    memoriesAdapter.submitList(memories)

                    // 更新空状态视图 - 使用简化的逻辑
                    val isEmpty = memories.isEmpty()

                    if (isEmpty) {
                        // 如果RecyclerView可见，先隐藏
                        if (memoriesRecyclerView.visibility == View.VISIBLE) {
                            memoriesRecyclerView.animate()
                                .alpha(0f)
                                .setDuration(300)
                                .withEndAction {
                                    memoriesRecyclerView.visibility = View.GONE

                                    // 显示空状态文本
                                    emptyMemoriesText.alpha = 0f
                                    emptyMemoriesText.visibility = View.VISIBLE
                                    emptyMemoriesText.animate()
                                        .alpha(1f)
                                        .setDuration(300)
                                        .start()
                                }
                                .start()
                        } else {
                            // RecyclerView已经隐藏，直接显示空状态
                            memoriesRecyclerView.visibility = View.GONE
                            emptyMemoriesText.visibility = View.VISIBLE
                            // 确保完全不透明
                            emptyMemoriesText.alpha = 1f
                        }
                    } else {
                        // 如果空状态文本可见，先隐藏
                        if (emptyMemoriesText.visibility == View.VISIBLE) {
                            emptyMemoriesText.animate()
                                .alpha(0f)
                                .setDuration(300)
                                .withEndAction {
                                    emptyMemoriesText.visibility = View.GONE

                                    // 显示RecyclerView
                                    memoriesRecyclerView.alpha = 0f
                                    memoriesRecyclerView.visibility = View.VISIBLE
                                    memoriesRecyclerView.animate()
                                        .alpha(1f)
                                        .setDuration(300)
                                        .start()
                                }
                                .start()
                        } else {
                            // 空状态文本已经隐藏，直接显示RecyclerView
                            emptyMemoriesText.visibility = View.GONE
                            memoriesRecyclerView.visibility = View.VISIBLE
                            // 确保完全不透明
                            memoriesRecyclerView.alpha = 1f
                        }
                    }
                }
            } catch (e: Exception) {
                context?.let { showErrorSnackbar(it, "加载记忆失败: ${e.message}") }
            }
        }
    }

    private fun updateUIWithChatDetails(chat: ChatEntity) {
        // 更新标题
        titleEditText.setText(chat.title)
        originalTitle = chat.title

        // 更新时间信息
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // 使用动画更新时间字段
        animateTextChange(createTimeTextView, dateFormat.format(chat.createdAt))
        animateTextChange(updateTimeTextView, dateFormat.format(chat.updatedAt))
        animateTextChange(modelTypeTextView, chat.modelType)
    }

    // 使用动画更新文本
    private fun animateTextChange(textView: TextView, newText: String) {
        textView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                textView.text = newText
                textView.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun saveTitle(newTitle: String) {
        val chatIdSafe = chatId ?: return

        lifecycleScope.launch {
            try {
                viewModel.updateChatTitle(chatIdSafe, newTitle)
                context?.let { showSuccessSnackbar(it, "标题已更新") }
                // 更新原始标题，以便下次编辑使用
                originalTitle = newTitle
            } catch (e: Exception) {
                context?.let { showErrorSnackbar(it, "更新标题失败: ${e.message}") }
                // 恢复原始标题
                titleEditText.setText(originalTitle)
            }
        }
    }

    private fun confirmDeleteChat() {
        val chatIdSafe = chatId ?: return
        context?.let { ctx -> // 确保 context 可用
            // 自定义对话框动画
            val dialogBuilder = MaterialAlertDialogBuilder(ctx, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setTitle("删除聊天")
                .setMessage("确定要删除这个聊天吗？此操作不可恢复。")
                .setPositiveButton("删除") { _, _ ->
                    // 震动反馈
                    HapticUtils.performHapticFeedback(ctx, true)

                    // 动画显示加载指示器
                    showLoadingIndicator("正在删除聊天...")

                    lifecycleScope.launch {
                        try {
                            viewModel.deleteChat(chatIdSafe)

                            // 隐藏加载指示器
                            hideLoadingIndicator()

                            // 成功反馈
                            context?.let { showSuccessSnackbar(it, "聊天已删除") }

                            // 执行退出动画
                            executeExitAnimation {
                                notifyNavigationBack()
                            }
                        } catch (e: Exception) {
                            // 隐藏加载指示器
                            hideLoadingIndicator()

                            context?.let { showErrorSnackbar(it, "删除聊天失败: ${e.message}") }
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .setBackground(ContextCompat.getDrawable(ctx, R.drawable.dialog_rounded_bg))

            // 显示对话框
            val dialog = dialogBuilder.create()
            // 确保 window 不为 null
            dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
            dialog.show()
        }
    }

    private fun continueChat() {
        val chatIdSafe = chatId ?: return

        // 执行退出动画
        executeExitAnimation {
            // 通知Activity继续聊天
            activity?.let { act ->
                if (act is ChatHistoryFragment.ChatSelectionListener) {
                    act.onChatSelected(chatIdSafe)
                }
            }

            // 返回上级
            notifyNavigationBack()
        }
    }

    private fun exportChatHistory() {
        context?.let { ctx ->
            // 震动反馈
            HapticUtils.performHapticFeedback(ctx)

            // 显示导出选项对话框
            val exportOptionsDialog = ExportOptionsDialog.newInstance()
            exportOptionsDialog.setExportOptionsListener(this)

            // 设置动画样式
            exportOptionsDialog.setStyle(
                androidx.fragment.app.DialogFragment.STYLE_NORMAL,
                R.style.TransparentBottomSheetDialog
            )

            // 使用 childFragmentManager
            exportOptionsDialog.show(childFragmentManager, ExportOptionsDialog.TAG)
        }
    }

    private fun importChatHistory() {
        context?.let { ctx ->
            // 震动反馈
            HapticUtils.performHapticFeedback(ctx)

            // 显示导入选项对话框
            val importOptionsDialog = ImportOptionsDialog.newInstance()
            importOptionsDialog.setImportOptionsListener(this)

            // 设置动画样式
            importOptionsDialog.setStyle(
                androidx.fragment.app.DialogFragment.STYLE_NORMAL,
                R.style.TransparentBottomSheetDialog
            )

            // 使用 childFragmentManager
            importOptionsDialog.show(childFragmentManager, ImportOptionsDialog.TAG)
        }
    }

    // 处理导入文件选择
    private fun openFileSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/json",
                "text/plain",
                "text/json" // 可以添加 text/json
            ))
        }
        try {
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            context?.let { showErrorSnackbar(it, "无法打开文件选择器: ${e.message}") }
        }
    }

    // 处理导入文件
    private fun handleImportFile(uri: Uri) {
        val chatIdSafe = chatId ?: return
        context?.let { ctx -> // 确保 context 可用
            lifecycleScope.launch {
                try {
                    // 显示加载指示器
                    showLoadingIndicator("正在导入聊天记录...")

                    // 在后台线程中导入文件
                    val importedMessages = withContext(Dispatchers.IO) {
                        ImportUtils.importChatHistory(ctx, uri, chatIdSafe)
                    }

                    if (importedMessages.isEmpty()) {
                        // 隐藏加载指示器
                        hideLoadingIndicator()
                        showErrorSnackbar(ctx, "没有可导入的消息")
                        return@launch
                    }

                    // 将导入的消息保存到数据库
                    val count = viewModel.importMessages(importedMessages)

                    // 隐藏加载指示器
                    hideLoadingIndicator()

                    // 刷新记忆列表
                    loadMemories()

                    // 显示成功提示
                    showSuccessSnackbar(ctx, "成功导入 $count 条消息")

                } catch (e: Exception) {
                    // 隐藏加载指示器
                    hideLoadingIndicator()
                    showErrorSnackbar(ctx, "导入失败: ${e.message}")
                }
            }
        }
    }

    // ExportOptionsListener 实现
    override fun onExportOptionSelected(format: String, includeTimestamp: Boolean) {
        val chatIdSafe = chatId ?: return
        val chatTitleSafe = chatEntity?.title ?: "未命名聊天"
        context?.let { ctx -> // 确保 context 可用
            lifecycleScope.launch {
                try {
                    // 显示加载指示器
                    showLoadingIndicator("正在导出聊天记录...")

                    // 获取聊天消息
                    val messages = viewModel.getChatMessages(chatIdSafe)

                    if (messages.isEmpty()) {
                        // 隐藏加载指示器
                        hideLoadingIndicator()
                        showErrorSnackbar(ctx, "没有可导出的消息")
                        return@launch
                    }

                    // 导出聊天记录
                    val fileUri = withContext(Dispatchers.IO) {
                        ExportUtils.exportChatHistory(
                            ctx,
                            messages,
                            chatTitleSafe,
                            format,
                            includeTimestamp
                        )
                    }

                    // 隐藏加载指示器
                    hideLoadingIndicator()

                    if (fileUri != null) {
                        // 询问是否分享
                        val dialogBuilder = MaterialAlertDialogBuilder(ctx, R.style.ThemeOverlay_App_MaterialAlertDialog)
                            .setTitle("导出成功")
                            .setMessage("聊天记录已成功导出为${format.uppercase()}格式。您想要分享这个文件吗？")
                            .setPositiveButton("分享") { _, _ ->
                                ExportUtils.shareExportedFile(ctx, fileUri, format)
                            }
                            .setNegativeButton("关闭", null)
                            .setBackground(ContextCompat.getDrawable(ctx, R.drawable.dialog_rounded_bg))

                        // 显示对话框
                        val dialog = dialogBuilder.create()
                        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
                        dialog.show()
                    } else {
                        showErrorSnackbar(ctx, "导出失败，请重试")
                    }
                } catch (e: Exception) {
                    // 隐藏加载指示器
                    hideLoadingIndicator()
                    showErrorSnackbar(ctx, "导出失败: ${e.message}")
                }
            }
        }
    }

    // ImportOptionsListener 实现
    override fun onImportOptionSelected(generateMemories: Boolean) {
        // 先打开文件选择器
        openFileSelector()

        // 将生成记忆的标志传递给ViewModel
        viewModel.setShouldGenerateMemories(generateMemories)
    }

    // 显示带动画的错误提示
    private fun showErrorSnackbar(context: Context, message: String) {
        // 确保 view 可用
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG)
                .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                .setBackgroundTint(ContextCompat.getColor(context, R.color.error))
                .setTextColor(ContextCompat.getColor(context, R.color.white))
                .show()
        }
    }

    // 显示带动画的成功提示
    private fun showSuccessSnackbar(context: Context, message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT)
                .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                .setBackgroundTint(ContextCompat.getColor(context, R.color.success))
                .setTextColor(ContextCompat.getColor(context, R.color.white))
                .show()
        }
    }

    // 显示加载指示器
    private fun showLoadingIndicator(message: String) {
        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }

    // 隐藏加载指示器
    private fun hideLoadingIndicator() {
        // 如果使用 Toast，则无需隐藏
    }

    // 执行退出动画
    private fun executeExitAnimation(onComplete: () -> Unit) {
        // 信息卡片的动画
        val infoCardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(infoCardView, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(infoCardView, "translationY", 0f, -50f)
            )
            duration = 300
        }

        // 记忆卡片的动画
        val memoriesCardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(memoriesCardView, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(memoriesCardView, "translationY", 0f, -50f)
            )
            duration = 300
            startDelay = 50
        }

        // 操作按钮卡片的动画
        val actionsCardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(actionsCardView, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(actionsCardView, "translationY", 0f, -50f)
            )
            duration = 300
            startDelay = 100
        }

        // 执行动画序列
        AnimatorSet().apply {
            playTogether(infoCardAnim, memoriesCardAnim, actionsCardAnim)

            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })

            start()
        }
    }

    override fun onPause() {
        super.onPause()
        // 如果在编辑状态，保存更改
        if (isEditingTitle) {
            finishTitleEditing(true)
        }
    }
}

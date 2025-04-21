package com.example.chatapp

import android.animation.Animator
import android.view.MotionEvent
import com.example.chatapp.data.ContentType
import com.example.chatapp.viewmodel.FeedbackViewModel
import com.example.chatapp.ui.DocumentViewerActivity
import android.animation.AnimatorListenerAdapter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.animation.AnimatorSet
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.example.chatapp.utils.DocumentProcessor
import android.animation.ObjectAnimator
import android.content.Intent
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.ClipData
import android.graphics.drawable.GradientDrawable
import android.content.ClipboardManager
import android.view.ViewGroup
import android.widget.Button
import android.view.Gravity
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import com.example.chatapp.data.MessagePagingManager
import com.example.chatapp.data.SettingsManager
import android.util.Log
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.data.Message
import com.example.chatapp.data.MessageType
import com.example.chatapp.service.AlarmIntentAnalyzer
import com.example.chatapp.service.AlarmManager
import com.example.chatapp.ui.ChatHistoryFragment
import com.example.chatapp.ui.ImageViewerActivity
import com.example.chatapp.ui.MessageAdapter
import com.example.chatapp.ui.MoreOptionsBottomSheet
import com.example.chatapp.ui.MomentsActivity
import com.example.chatapp.utils.HapticUtils
import com.example.chatapp.viewmodel.ChatViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern
import org.json.JSONObject
import java.io.IOException
import android.view.inputmethod.InputMethodManager
import android.graphics.Typeface
import android.util.TypedValue
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), MoreOptionsBottomSheet.MoreOptionsListener, ChatHistoryFragment.ChatSelectionListener {

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: MessageAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var inputEditText: TextInputEditText
    private lateinit var sendButton: ImageButton
    private lateinit var moreButton: ImageButton
    private var currentSearchJob: Job? = null
    private var currentSnackbar: Snackbar? = null

    // 添加Toolbar和SettingsManager引用
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var settingsManager: SettingsManager

    // 添加标题文本视图
    private lateinit var titleTextView: TextView

    // 渐变遮罩视图
    private lateinit var topGradientMask: View
    private lateinit var bottomGradientMask: View

    // 位置相关
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private var locationPermissionRequested = false
    private var cachedLocationInfo: String? = null
    private var geocodedAddressCache: String? = null

    // 闹钟管理器
    private lateinit var alarmManager: AlarmManager
    private lateinit var alarmIntentAnalyzer: AlarmIntentAnalyzer

    // 标题栏长按处理相关
    private var titleLongPressStartTime: Long = 0
    private val TITLE_LONG_PRESS_DURATION = 150L  // 长按时间阈值，单位毫秒
    private var isTitleAnimating = false

    // 权限请求码
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    // 位置更新最小时间和距离
    private val MIN_TIME_BETWEEN_UPDATES: Long = 60000 // 1分钟
    private val MIN_DISTANCE_CHANGE_FOR_UPDATES: Float = 50f // 50米

    // 添加状态恢复字段
    private var savedChatIdToRestore: String? = null
    private var pendingMoreOptionsOpen = false
    private var stateRestored = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 初始化 SettingsManager 以在设置主题前获取主题设置
        settingsManager = SettingsManager(this)

        // 在调用 super.onCreate 之前应用主题
        applyColorTheme()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 初始化ViewModel
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        // 初始化闹钟管理器
        alarmManager = AlarmManager(applicationContext)

        // 初始化闹钟意图分析器
        alarmIntentAnalyzer = AlarmIntentAnalyzer()

        // 初始化Toolbar并设置标题
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 获取渐变遮罩视图引用
        topGradientMask = findViewById(R.id.topGradientMask)
        bottomGradientMask = findViewById(R.id.bottomGradientMask)

        // 初始化位置服务
        initLocationServices()

        // 初始化UI组件
        setupUI()

        // 设置自定义标题和长按监听
        setupTitleLongPress()

        // 设置输入框提示文字行为
        setupInputHint()

        // 设置发送按钮点击事件
        setupSendButton()

        // 设置更多按钮点击事件
        setupMoreButton()

        // 观察消息列表
        observeMessages()

        // 设置消息交互
        setupMessageInteractions()

        // 设置滚动监听器，实现渐变效果和分页加载
        setupScrollListener()

        // 观察编辑状态
        observeEditingState()

        // 观察加载状态
        observeLoadingState()

        // 检查是否有需要恢复的状态
        if (intent.hasExtra("RESTORE_CHAT_ID")) {
            savedChatIdToRestore = intent.getStringExtra("RESTORE_CHAT_ID")
            Log.d(TAG, "需要恢复会话ID: $savedChatIdToRestore")
        }

        // 恢复滚动位置（如果是主题变更导致的重启）
        restoreScrollPositionIfNeeded()

        // 加载上次活动的会话或创建新会话
        viewModel.loadLastActiveChat()

        // 初始化闹钟管理器
        initAlarmManager()

        // 检查通知权限
        checkNotificationPermission()

        // 检查精确闹钟权限
        alarmManager.checkExactAlarmPermission(this)
    }

    /**
     * 应用保存的颜色主题
     */
    private fun applyColorTheme() {
        try {
            // 获取当前主题样式资源ID
            val themeResId = settingsManager.getThemeColorResId()

            // 设置活动主题
            setTheme(themeResId)

            // 获取主题颜色
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            val themeColor = typedValue.data

            // 设置状态栏和导航栏颜色
            window.statusBarColor = themeColor
            window.navigationBarColor = themeColor

            Log.d(TAG, "应用主题颜色: ${settingsManager.colorTheme}, 资源ID: $themeResId")
        } catch (e: Exception) {
            Log.e(TAG, "应用主题颜色失败: ${e.message}", e)
        }
    }

    /**
     * 如果是主题变更导致的重启，恢复之前的滚动位置
     */
    private fun restoreScrollPositionIfNeeded() {
        val scrollPosition = intent.getIntExtra("SCROLL_POSITION", -1)
        if (scrollPosition >= 0) {
            recyclerView.post {
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPosition(scrollPosition)
            }
        }
    }

    /**
     * 检查是否有待处理的主题变更
     */
    private fun checkPendingThemeChanges() {
        val prefs = getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
        val hasPendingChange = prefs.getBoolean("theme_pending_change", false)

        if (hasPendingChange) {
            // 清除标记
            prefs.edit().putBoolean("theme_pending_change", false).apply()

            // 使用Handler延迟执行，确保UI已完全初始化
            Handler(Looper.getMainLooper()).postDelayed({
                // 保存当前状态
                val currentChatId = viewModel.repository.currentChatId.value
                val scrollPosition = (recyclerView.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0

                // 保存状态到Intent，用于重建后恢复
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("RESTORE_CHAT_ID", currentChatId)
                intent.putExtra("SCROLL_POSITION", scrollPosition)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

                // 完全重启Activity
                finish()
                startActivity(intent)
                overridePendingTransition(0, 0) // 无动画
            }, 500) // 短暂延迟确保UI稳定
        }
    }

    /**
     * 恢复保存的状态
     */
    private fun restoreSavedState() {
        if (stateRestored) return  // 防止重复恢复

        stateRestored = true

        savedChatIdToRestore?.let { chatId ->
            Log.d(TAG, "正在恢复会话: $chatId")
            viewModel.switchChat(chatId)

            // 如果有滚动位置，也恢复它
            if (intent.hasExtra("SCROLL_POSITION")) {
                val position = intent.getIntExtra("SCROLL_POSITION", 0)
                recyclerView.post {
                    try {
                        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPosition(position)
                    } catch (e: Exception) {
                        Log.e(TAG, "恢复滚动位置失败: ${e.message}")
                    }
                }
            }

            // 清除已恢复的ID
            savedChatIdToRestore = null
        }

        // 应用其他待恢复的UI状态
        if (pendingMoreOptionsOpen) {
            // 延迟执行，确保UI已完全初始化
            Handler(Looper.getMainLooper()).postDelayed({
                showMoreOptionsBottomSheet()
                pendingMoreOptionsOpen = false
            }, 300)
        }
    }

    /**
     * 观察编辑状态
     */
    private fun observeEditingState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isEditing.collect { isEditing ->
                    // 当编辑状态变为false（取消编辑）且Snackbar仍在显示时，关闭Snackbar
                    if (!isEditing && currentSnackbar != null) {
                        currentSnackbar?.dismiss()
                        currentSnackbar = null
                    }
                }
            }
        }
    }

    /**
     * 观察加载状态
     */
    private fun observeLoadingState() {
        lifecycleScope.launch {
            // 同时收集加载状态和是否有更多消息
            combine(
                viewModel.loadingState,
                viewModel.hasMoreOlderMessages,
                viewModel.hasMoreNewerMessages,
                viewModel.isLoadingMore
            ) { loadingState, hasMoreOlder, hasMoreNewer, isLoadingMore ->
                Triple(
                    hasMoreOlder && isLoadingMore &&
                            loadingState == MessagePagingManager.LoadingState.LOADING_OLDER,
                    hasMoreNewer && isLoadingMore &&
                            loadingState == MessagePagingManager.LoadingState.LOADING_NEWER,
                    loadingState
                )
            }.collect { (showHeaderLoading, showFooterLoading, loadingState) ->
                // 更新适配器加载状态
                (adapter as? MessageAdapter)?.setLoadingState(
                    showHeader = showHeaderLoading,
                    showFooter = showFooterLoading
                )

                // 根据加载状态显示或隐藏加载指示器
                when (loadingState) {
                    MessagePagingManager.LoadingState.REFRESHING -> {
                        // 显示全屏加载
                        showFullscreenLoading(true)
                    }
                    else -> {
                        // 隐藏全屏加载
                        showFullscreenLoading(false)
                    }
                }
            }
        }
    }

    /**
     * 显示或隐藏全屏加载
     */
    private fun showFullscreenLoading(show: Boolean) {
        val loadingView = findViewById<View>(R.id.fullscreenLoading) ?: return

        if (show) {
            loadingView.visibility = View.VISIBLE
            loadingView.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        } else {
            loadingView.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    loadingView.visibility = View.GONE
                }
                .start()
        }
    }

    /**
     * 图片选择回调
     */
    override fun onImageSelected(imageUri: Uri) {
        // 显示一个对话框让用户输入对图片的描述
        showImageCaptionDialog(imageUri)
    }

    /**
     * 显示图片描述对话框
     */
    private fun showImageCaptionDialog(imageUri: Uri) {
        try {
            // 创建使用自定义样式的底部弹出对话框
            val bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)

            // 设置窗口背景为透明
            bottomSheetDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val dialogView = layoutInflater.inflate(R.layout.dialog_image_caption, null)

            // 初始化视图元素
            val captionEditText = dialogView.findViewById<TextInputEditText>(R.id.captionEditText)
            val imagePreview = dialogView.findViewById<ImageView>(R.id.imagePreview)
            val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
            val btnSend = dialogView.findViewById<Button>(R.id.btnSend)

            // 圆角尺寸
            val cornerRadius = resources.getDimensionPixelSize(R.dimen.corner_radius)

            // 使用标准的Glide RoundedCorners变换，并正确设置变换顺序
            Glide.with(this)
                .load(imageUri)
                .transform(CenterCrop(), RoundedCorners(cornerRadius))
                .into(imagePreview)

            // 应用圆角背景到整个对话框
            dialogView.setBackgroundResource(R.drawable.rounded_content_background)

            // 设置按钮圆角背景
            btnSend.setBackgroundResource(R.drawable.rounded_button_background)
            btnCancel.setBackgroundResource(R.drawable.rounded_white_button_background)

            // 设置按钮文字颜色
            btnSend.setTextColor(ContextCompat.getColor(this, R.color.white))
            btnCancel.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

            // 设置按钮内边距
            val padding = resources.getDimensionPixelSize(R.dimen.button_padding)
            btnSend.setPadding(padding, padding, padding, padding)
            btnCancel.setPadding(padding, padding, padding, padding)

            // 使用触摸监听器代替点击监听器，实现按下立即触发动画
            btnSend.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下时立即执行动画和触觉反馈
                        startButtonPressAnimation(view as Button)
                        HapticUtils.performHapticFeedback(this@MainActivity, false)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 手指抬起时，检查是否在按钮区域内
                        val isInBounds = event.x >= 0 && event.x <= view.width &&
                                event.y >= 0 && event.y <= view.height

                        if (isInBounds) {
                            // 执行释放动画
                            startButtonReleaseAnimation(view as Button) {
                                // 动画完成后执行操作
                                val caption = captionEditText.text.toString().trim()
                                viewModel.sendImageMessage(imageUri, caption)
                                Toast.makeText(this@MainActivity, "正在处理图片...", Toast.LENGTH_SHORT).show()
                                bottomSheetDialog.dismiss()
                            }
                        } else {
                            // 如果手指在按钮区域外抬起，只执行释放动画
                            startButtonReleaseAnimation(view as Button)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // 操作取消时重置按钮状态
                        startButtonReleaseAnimation(view as Button)
                        true
                    }
                    else -> false
                }
            }

            // 取消按钮触摸监听器
            btnCancel.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下时立即执行动画和轻微触觉反馈
                        startButtonPressAnimation(view as Button)
                        HapticUtils.performHapticFeedback(this@MainActivity, false)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val isInBounds = event.x >= 0 && event.x <= view.width &&
                                event.y >= 0 && event.y <= view.height

                        if (isInBounds) {
                            // 执行释放动画并关闭对话框
                            startButtonReleaseAnimation(view as Button) {
                                bottomSheetDialog.dismiss()
                            }
                        } else {
                            // 如果手指在按钮区域外抬起，只执行释放动画
                            startButtonReleaseAnimation(view as Button)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        startButtonReleaseAnimation(view as Button)
                        true
                    }
                    else -> false
                }
            }

            // 设置对话框内容
            bottomSheetDialog.setContentView(dialogView)

            // 显示对话框
            bottomSheetDialog.show()

            // 获取底部表单视图并配置
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                // 设置背景为透明
                bottomSheet.setBackgroundResource(android.R.color.transparent)

                // 设置高度
                val layoutParams = bottomSheet.layoutParams
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                bottomSheet.layoutParams = layoutParams

                // 移除底部阴影
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = false
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "显示图片对话框失败: ${e.message}", e)
            Toast.makeText(this, "无法显示图片对话框，直接发送图片", Toast.LENGTH_SHORT).show()
            viewModel.sendImageMessage(imageUri)
        }
    }

    /**
     * 按钮按下动画效果
     */
    private fun startButtonPressAnimation(button: Button) {
        // 保存原始状态，使用tag存储原始文字颜色，确保释放时能正确恢复
        button.tag = button.currentTextColor

        // 检查按钮类型，使用文字颜色而非背景判断
        if (button.currentTextColor == ContextCompat.getColor(this, R.color.white)) {
            // 主按钮（白色文字）：降低亮度
            button.alpha = 0.8f
        } else {
            // 次要按钮（非白色文字）：改变文字颜色为主题色
            button.setTextColor(ContextCompat.getColor(this, R.color.primary))
        }

        // 缩小动画效果
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.9f),
                ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.9f)
            )
            duration = 50 // 快速缩小
            interpolator = AccelerateInterpolator(1.5f)
        }

        scaleDown.start()
    }

    /**
     * 按钮释放动画效果
     */
    private fun startButtonReleaseAnimation(button: Button, onAnimationEnd: (() -> Unit)? = null) {
        // 恢复按钮不透明度
        button.alpha = 1.0f

        // 恢复原始文字颜色，从tag中读取
        val originalTextColor = button.tag as? Int
        if (originalTextColor != null) {
            button.setTextColor(originalTextColor)
        } else {
            // 如果没有保存原始颜色，根据按钮类型设置默认颜色
            if (button.text.toString().lowercase() == "取消") {
                button.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            } else {
                button.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
        }

        // 创建弹回动画
        val animatorSet = AnimatorSet()

        // 第一阶段：快速回弹超过原始大小
        val bounce = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 0.9f, 1.05f),
                ObjectAnimator.ofFloat(button, "scaleY", 0.9f, 1.05f)
            )
            duration = 100
            interpolator = DecelerateInterpolator(1.5f)
        }

        // 第二阶段：稳定回到原始大小
        val settle = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 1.05f, 1.0f),
                ObjectAnimator.ofFloat(button, "scaleY", 1.05f, 1.0f)
            )
            duration = 100
            interpolator = OvershootInterpolator(2.0f)
        }

        // 组合动画序列
        animatorSet.playSequentially(bounce, settle)

        // 添加动画完成监听器
        if (onAnimationEnd != null) {
            animatorSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onAnimationEnd.invoke()
                }
            })
        }

        // 启动动画序列
        animatorSet.start()
    }

    /**
     * 文件选择回调
     */
    override fun onFileSelected(fileUri: Uri) {
        lifecycleScope.launch {
            try {
                // 显示加载动画
                val loadingView = findViewById<View>(R.id.fullscreenLoading)
                loadingView?.visibility = View.VISIBLE

                // 震动反馈
                HapticUtils.performHapticFeedback(this@MainActivity, true)

                // 获取文件名，用于显示提示
                val documentProcessor = DocumentProcessor(this@MainActivity)
                val fileName = documentProcessor.getFileName(fileUri)

                // 显示处理提示
                Toast.makeText(this@MainActivity, "正在处理文档: $fileName...", Toast.LENGTH_SHORT).show()

                // 保存文档URI到本地存储，方便后续查看
                saveDocumentUri(fileUri, fileName)

                // 发送文档消息
                viewModel.sendDocumentMessage(fileUri)

                // 隐藏加载动画
                loadingView?.visibility = View.GONE
            } catch (e: Exception) {
                Log.e(TAG, "处理文档失败: ${e.message}", e)

                // 错误震动反馈
                HapticUtils.performHapticFeedback(this@MainActivity, true)

                // 显示错误信息
                Toast.makeText(this@MainActivity, "文档处理失败: ${e.message}", Toast.LENGTH_SHORT).show()

                // 隐藏加载动画
                findViewById<View>(R.id.fullscreenLoading)?.visibility = View.GONE
            }
        }
    }

    /**
     * 保存文档URI到本地存储
     */
    private suspend fun saveDocumentUri(fileUri: Uri, fileName: String) {
        withContext(Dispatchers.IO) {
            try {
                // 获取当前会话ID
                val chatId = viewModel.repository.currentChatId.value ?: return@withContext

                // 创建文档目录
                val documentsDir = java.io.File(getExternalFilesDir(null), "documents")
                if (!documentsDir.exists()) {
                    documentsDir.mkdirs()
                }

                // 创建本地文件
                val targetFile = java.io.File(documentsDir, "${chatId}_${System.currentTimeMillis()}_${fileName}")

                // 复制文件内容
                contentResolver.openInputStream(fileUri)?.use { input ->
                    java.io.FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // 记录文档URI和文件名的映射关系到SharedPreferences
                val prefs = getSharedPreferences("document_mappings", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("${chatId}_${fileName}", targetFile.absolutePath)
                    apply()
                }

                Log.d(TAG, "已保存文档: ${targetFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "保存文档失败: ${e.message}", e)
            }
        }
    }

    /**
     * 获取保存的文档URI
     */
    fun getDocumentUri(chatId: String, fileName: String): Uri? {
        val prefs = getSharedPreferences("document_mappings", Context.MODE_PRIVATE)
        val filePath = prefs.getString("${chatId}_${fileName}", null)

        if (filePath != null) {
            val file = java.io.File(filePath)
            if (file.exists()) {
                return Uri.fromFile(file)
            }
        }

        // 如果从SharedPreferences找不到，尝试在文档目录中查找
        val documentsDir = java.io.File(getExternalFilesDir(null), "documents")
        if (documentsDir.exists() && documentsDir.isDirectory) {
            // 查找包含会话ID和文件名的文件
            val files = documentsDir.listFiles { file ->
                file.name.contains(chatId) && file.name.contains(fileName)
            }

            if (files != null && files.isNotEmpty()) {
                // 找到匹配的文件，返回其URI
                return Uri.fromFile(files[0])
            }
        }

        // 如果找不到文件，创建一个临时文件
        try {
            val tempContent = """
            找不到原始文档: $fileName
            
            可能的原因:
            1. 文档已被删除或移动
            2. 应用重新安装导致文档映射丢失
            
            建议:
            请重新上传文档或联系我们
        """.trimIndent()

            val tempFile = java.io.File(cacheDir, "temp_${fileName}.txt")
            tempFile.writeText(tempContent)
            return Uri.fromFile(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "创建临时文件失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 处理文档
     */
    private fun processDocument(fileUri: Uri) {
        lifecycleScope.launch {
            try {
                // 发送文档消息
                viewModel.sendDocumentMessage(fileUri)

                // 隐藏加载动画
                findViewById<View>(R.id.fullscreenLoading)?.visibility = View.GONE
            } catch (e: Exception) {
                Log.e(TAG, "处理文档过程中出错: ${e.message}", e)

                // 显示错误信息
                Toast.makeText(this@MainActivity, "文档处理错误: ${e.message}", Toast.LENGTH_SHORT).show()

                // 隐藏加载动画
                findViewById<View>(R.id.fullscreenLoading)?.visibility = View.GONE
            }
        }
    }

    /**
     * 初始化闹钟管理器
     */
    private fun initAlarmManager() {
        // 初始化闹钟管理器
        lifecycleScope.launch {
            try {
                alarmManager.rescheduleAllActiveAlarms()
                Log.d("MainActivity", "闹钟已重新调度")
            } catch (e: Exception) {
                Log.e("MainActivity", "重新调度闹钟失败: ${e.message}", e)
            }
        }
    }

    /**
     * 检查通知权限
     */
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                // 请求通知权限
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100 // 请求码
                )
            }
        }
    }

    /**
     * 更新标题栏中的AI名称
     */
    private fun updateAIName() {
        // 重新设置自定义标题和长按监听
        setupTitleLongPress()
    }

    /**
     * 设置滚动监听器，检测何时加载更多
     */
    private fun setupScrollListener() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // 获取当前滚动状态
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                // 下滑到顶部附近时，加载更多较旧的消息
                if (firstVisibleItem <= 3 && dy < 0 && viewModel.hasMoreOlderMessages.value) {
                    viewModel.loadMoreOlderMessages()
                }

                // 上滑到底部附近时，加载更多较新的消息
                if (lastVisibleItem >= totalItemCount - 3 && dy > 0 && viewModel.hasMoreNewerMessages.value) {
                    viewModel.loadMoreNewerMessages()
                }

                // 计算当前滚动位置
                val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

                // 根据滚动程度调整渐变遮罩的透明度，增强效果
                val topMask = findViewById<View>(R.id.topGradientMask)
                val bottomMask = findViewById<View>(R.id.bottomGradientMask)

                if (firstVisiblePosition <= 1) {
                    // 靠近顶部，增强顶部遮罩不透明度
                    topMask.alpha = 1.0f
                } else if (firstVisiblePosition == 2) {
                    // 轻微滚动就使顶部完全不透明
                    topMask.alpha = 1.0f
                } else {
                    // 远离顶部，保持完全不透明
                    topMask.alpha = 1.0f
                }

                // 底部渐变效果保持不变
                if (lastVisiblePosition >= adapter.itemCount - 2) {
                    bottomMask.alpha = 0.7f
                } else {
                    bottomMask.alpha = 1.0f
                }

                // 原有的滚动效果保留
                if (Math.abs(dy) > 20) {
                    val alpha = 0.9f - Math.min(Math.abs(dy) / 1000f, 0.3f)
                    recyclerView.alpha = alpha
                    recyclerView.animate().alpha(1f).setDuration(150).start()
                }
            }
        })
    }

    /**
     * 初始化位置服务
     */
    private fun initLocationServices() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 检查位置权限并请求
        checkAndRequestLocationPermissions()

        // 在后台线程中预取位置信息
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 尝试获取IP位置信息作为备选
                val ipLocationInfo = getLocationFromIP()
                if (ipLocationInfo.isNotEmpty()) {
                    cachedLocationInfo = ipLocationInfo
                }
            } catch (e: Exception) {
                // 安静地处理失败
            }
        }
    }

    /**
     * 检查并请求位置权限
     */
    private fun checkAndRequestLocationPermissions() {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // 如果没有任何位置权限且未请求过，则请求权限
        if (!hasFineLocationPermission && !hasCoarseLocationPermission && !locationPermissionRequested) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            locationPermissionRequested = true
        } else if (hasFineLocationPermission || hasCoarseLocationPermission) {
            // 如果有任一位置权限，启动位置更新
            startLocationUpdates()
        }
    }

    /**
     * 启动位置更新
     */
    private fun startLocationUpdates() {
        // 检查是否有位置权限
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return  // 如果没有权限，直接返回
        }

        try {
            // 获取最佳位置提供者
            val criteria = android.location.Criteria()
            criteria.accuracy = android.location.Criteria.ACCURACY_FINE  // 高精度
            criteria.isAltitudeRequired = false
            criteria.isBearingRequired = false
            criteria.isCostAllowed = true
            criteria.powerRequirement = android.location.Criteria.POWER_MEDIUM

            // 获取最佳提供者
            val provider = locationManager.getBestProvider(criteria, true)

            if (provider != null) {
                // 首先尝试获取最后已知位置
                val lastKnownLocation = locationManager.getLastKnownLocation(provider)
                if (lastKnownLocation != null) {
                    currentLocation = lastKnownLocation
                    // 尝试地理编码位置以获取地址
                    lifecycleScope.launch(Dispatchers.IO) {
                        geocodeLocation(lastKnownLocation)
                    }
                }

                // 注册位置更新
                locationManager.requestLocationUpdates(
                    provider,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES,
                    locationListener
                )
            } else {
                // 如果没有可用的提供者，尝试使用GPS和网络提供者
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_TIME_BETWEEN_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES,
                        locationListener
                    )
                }

                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BETWEEN_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES,
                        locationListener
                    )
                }
            }
        } catch (e: Exception) {
            // 记录错误
            e.printStackTrace()
        }
    }

    /**
     * 位置更新监听器
     */
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // 更新当前位置
            currentLocation = location

            // 地理编码获取详细地址
            lifecycleScope.launch(Dispatchers.IO) {
                geocodeLocation(location)
            }
        }

        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    /**
     * 地理编码位置坐标为地址
     */
    private suspend fun geocodeLocation(location: Location) {
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    return@withContext
                }

                val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                var addresses: List<Address>? = null

                try {
                    // 尝试从坐标获取地址信息
                    addresses = geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1  // 最多返回一个地址
                    )
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    val sb = StringBuilder()

                    // 中国特定格式
                    if (Locale.getDefault().country == "CN" ||
                        Locale.getDefault().language == "zh") {
                        // 省/市/区
                        if (address.adminArea != null) sb.append(address.adminArea)
                        if (address.locality != null) sb.append(address.locality)
                        if (address.subLocality != null) sb.append(address.subLocality)
                        // 街道/门牌
                        if (address.thoroughfare != null) sb.append(address.thoroughfare)
                        if (address.featureName != null &&
                            address.featureName != address.thoroughfare) {
                            sb.append(address.featureName)
                        }
                    } else {
                        // 国际格式
                        for (i in 0 until address.maxAddressLineIndex) {
                            sb.append(address.getAddressLine(i)).append(", ")
                        }
                        if (address.locality != null) {
                            sb.append(address.locality).append(", ")
                        }
                        if (address.adminArea != null) {
                            sb.append(address.adminArea).append(", ")
                        }
                        if (address.countryName != null) {
                            sb.append(address.countryName)
                        }
                    }

                    if (sb.isNotEmpty()) {
                        geocodedAddressCache = sb.toString()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 使用IP获取位置信息 (ipinfo.io)
     */
    private suspend fun getLocationFromIP(): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://ipinfo.io/json")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonObject = JSONObject(response.toString())
                val city = jsonObject.optString("city", "")
                val region = jsonObject.optString("region", "")
                val country = jsonObject.optString("country", "")

                if (city.isNotEmpty()) {
                    if (country == "CN") {
                        "$region$city" // 中国地址格式
                    } else {
                        "$city, $region, $country" // 国际地址格式
                    }
                } else {
                    "" // 如果没有城市信息，返回空字符串
                }
            } catch (e: Exception) {
                // 在生产环境中应该记录错误
                ""
            }
        }
    }

    // 添加 onResume 方法，确保应用从后台恢复时也能正确显示消息
    override fun onResume() {
        super.onResume()

        // 检查位置权限并请求
        checkAndRequestLocationPermissions()

        // 当应用恢复时，确保消息列表正确显示
        lifecycleScope.launch {
            val chatId = viewModel.repository.currentChatId.value
            if (chatId != null) {
                // 如果有当前会话ID，确保消息已加载
                viewModel.repository.loadCurrentChatMessages()
            }
        }

        // 刷新AI名称
        updateAIName()

        // 检查是否有待处理的主题变更
        checkPendingThemeChanges()

        // 延迟执行状态恢复，确保UI完全准备好
        if (!stateRestored && savedChatIdToRestore != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                restoreSavedState()
            }, 500)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            // 检查是否授予了任何位置权限
            if (grantResults.isNotEmpty() &&
                (grantResults[0] == PackageManager.PERMISSION_GRANTED ||
                        grantResults.size > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                // 开始位置更新
                startLocationUpdates()
            }
        }
    }

    private fun setupUI() {
        recyclerView = findViewById(R.id.recyclerView)
        inputEditText = findViewById(R.id.inputEditText)
        sendButton = findViewById(R.id.sendButton)
        moreButton = findViewById(R.id.moreButton)

        // 设置RecyclerView的LayoutManager
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true  // 从底部开始显示
        }
    }

    /**
     * 设置标题栏点击和长按监听
     */
    private fun setupTitleLongPress() {
        // 获取Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)

        // 创建自定义TextView作为标题
        titleTextView = TextView(this).apply {
            // 使用反射或调用方法获取AI名称
            text = if (::settingsManager.isInitialized) {
                try {
                    val method = settingsManager.javaClass.getMethod("getAiName")
                    method.invoke(settingsManager) as? String ?: "ChatGPT"
                } catch (e: Exception) {
                    "ChatGPT"
                }
            } else {
                "ChatGPT"
            }

            textSize = 24f
            typeface = Typeface.create(typeface, Typeface.BOLD) // 设置加粗
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))

            // 设置内边距
            val padding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
            ).toInt()
            setPadding(padding, padding, padding, padding)

            // 使其可点击
            isClickable = true
            isFocusable = true
        }

        // 标记按压状态
        var isTitlePressed = false

        // 设置触摸监听器，实现缩放动效和长按/短按区分
        titleTextView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录按下时间和状态
                    titleLongPressStartTime = System.currentTimeMillis()
                    isTitlePressed = true

                    // 按下动画效果
                    startTitlePressAnimation(titleTextView)

                    // 提供即时触觉反馈
                    HapticUtils.performHapticFeedback(this@MainActivity, false)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isTitlePressed) {
                        // 计算按下持续时间
                        val pressDuration = System.currentTimeMillis() - titleLongPressStartTime
                        val isInBounds = event.x >= 0 && event.x <= view.width &&
                                event.y >= 0 && event.y <= view.height

                        // 执行释放动画
                        startTitleReleaseAnimation(titleTextView)
                        isTitlePressed = false

                        // 如果是在按钮区域内抬起，且不在动画进行中
                        if (isInBounds && !isTitleAnimating) {
                            view.postDelayed({
                                // 长按超过阈值，执行翻转动画
                                if (pressDuration >= TITLE_LONG_PRESS_DURATION) {
                                    animateAdvanced3DFlip()
                                }
                            }, 150)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isTitlePressed) {
                        startTitleReleaseAnimation(titleTextView)
                        isTitlePressed = false
                    }
                    true
                }
                else -> false
            }
        }

        // 清空并设置自定义标题视图
        toolbar.removeAllViews()
        toolbar.addView(titleTextView, androidx.appcompat.widget.Toolbar.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.START
        ))

        // 设置空标题，让我们的自定义视图作为主标题
        supportActionBar?.title = ""
    }

    /**
     * 执行标题按下动画效果
     */
    private fun startTitlePressAnimation(titleView: TextView) {
        // 缩小动画
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(titleView, "scaleX", 1f, 0.65f),
                ObjectAnimator.ofFloat(titleView, "scaleY", 1f, 0.65f)
            )
            duration = 80
            interpolator = AccelerateInterpolator(2.0f)
        }

        // 改变文字颜色为主题色
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        titleView.setTextColor(primaryColor)

        // 启动动画
        scaleDown.start()
    }

    /**
     * 执行标题释放动画效果
     */
    private fun startTitleReleaseAnimation(titleView: TextView) {
        // 恢复原来的文字颜色
        val textPrimaryColor = ContextCompat.getColor(this, R.color.text_primary)

        // 创建带回弹效果的动画
        val animatorSet = AnimatorSet()

        // 第一阶段：快速回弹超过原始大小
        val bounce = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(titleView, "scaleX", 0.65f, 1.2f),
                ObjectAnimator.ofFloat(titleView, "scaleY", 0.65f, 1.2f)
            )
            duration = 150
            interpolator = DecelerateInterpolator(1.5f)
        }

        // 第二阶段：稳定回到原始大小
        val settle = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(titleView, "scaleX", 1.2f, 1.0f),
                ObjectAnimator.ofFloat(titleView, "scaleY", 1.2f, 1.0f)
            )
            duration = 150
            interpolator = OvershootInterpolator(3.0f)
        }

        // 合并动画序列
        animatorSet.playSequentially(bounce, settle)

        // 在第一阶段开始时恢复颜色
        bounce.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                titleView.setTextColor(textPrimaryColor)
            }
        })

        // 启动动画序列
        animatorSet.start()
    }

    /**
     * 执行3D卡片翻转动画
     */
    private fun animateAdvanced3DFlip() {
        if (isTitleAnimating) return
        isTitleAnimating = true

        // 震动反馈
        HapticUtils.performHapticFeedback(applicationContext, true)

        // 获取根视图
        val rootView = findViewById<View>(R.id.main)

        // 3D效果
        rootView.cameraDistance = 12000f * resources.displayMetrics.density

        // 添加位移元素，使动画更加立体
        val translateX = ObjectAnimator.ofFloat(rootView, "translationX", 0f, -50f)
        translateX.duration = 300
        translateX.interpolator = AccelerateInterpolator(1.2f)

        // 调整翻转角度和速度曲线
        val rotationY = ObjectAnimator.ofFloat(rootView, "rotationY", 0f, -100f)
        rotationY.duration = 350
        rotationY.interpolator = AccelerateInterpolator(1.4f)

        // 缩小和淡出动画
        val scaleOutX = ObjectAnimator.ofFloat(rootView, "scaleX", 1.0f, 0.7f)
        val scaleOutY = ObjectAnimator.ofFloat(rootView, "scaleY", 1.0f, 0.7f)
        val alphaOut = ObjectAnimator.ofFloat(rootView, "alpha", 1.0f, 0.4f)

        // 组合所有动画
        val animSetOut = AnimatorSet()
        animSetOut.playTogether(rotationY, translateX, scaleOutX, scaleOutY, alphaOut)

        // 设置动画监听器
        animSetOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                // 在适当的时机启动MomentsActivity
                val intent = Intent(this@MainActivity, MomentsActivity::class.java)
                intent.putExtra("FROM_3D_FLIP", true)
                startActivity(intent)
                overridePendingTransition(0, 0)
            }

            override fun onAnimationEnd(animation: Animator) {
                // 动画结束后，重置状态
                rootView.setLayerType(View.LAYER_TYPE_NONE, null)
                rootView.rotationY = 0f
                rootView.translationX = 0f
                rootView.scaleX = 1.0f
                rootView.scaleY = 1.0f
                rootView.alpha = 1.0f

                isTitleAnimating = false
            }
        })

        animSetOut.start()
    }

    private fun setupInputHint() {
        val textInputLayout = findViewById<TextInputLayout>(R.id.textInputLayout)

        // 监听焦点变化
        inputEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 当获得焦点时，使用动画延迟隐藏悬浮提示
                inputEditText.postDelayed({
                    // 将TextInputLayout的hint设为null，彻底移除提示
                    textInputLayout.hint = null

                    // 为了防止系统恢复提示，将EditText的hint也设为空
                    inputEditText.hint = ""
                }, 200)
            }
        }

        // 处理点击事件，确保点击时也会触发焦点效果
        inputEditText.setOnClickListener {
            // 如果已经有焦点但提示还在，强制移除提示
            if (inputEditText.hasFocus() && textInputLayout.hint != null) {
                textInputLayout.hint = null
                inputEditText.hint = ""
            }
        }
    }

    private fun setupSendButton() {
        // 加载发送动画
        val sendAnimation = AnimationUtils.loadAnimation(this, R.anim.send_button_animation)

        sendButton.setOnClickListener {
            val message = inputEditText.text.toString().trim()
            if (message.isNotEmpty()) {
                // 添加震动反馈
                HapticUtils.performViewHapticFeedback(sendButton)

                // 播放发送按钮动画
                sendButton.startAnimation(sendAnimation)

                // 清空输入框
                inputEditText.text?.clear()

                // 使用协程处理消息发送
                handleMessageSending(message)
            }
        }
    }

    /**
     * 处理消息发送的顺序逻辑
     */
    private fun handleMessageSending(message: String) {
        // 清空输入框
        inputEditText.text?.clear()

        lifecycleScope.launch {
            try {
                // 如果处于编辑模式
                if (viewModel.isEditing.value) {
                    Log.d(TAG, "编辑模式：处理消息更新")

                    // 获取当前正在编辑的消息
                    val editingMessageId = viewModel.getEditingMessageId()
                    if (editingMessageId != null) {
                        // 获取完整的原始消息
                        val originalMessage = viewModel.getEditingMessage()

                        if (originalMessage != null) {
                            // 检查原始消息是否包含图片
                            if (originalMessage.contentType == ContentType.IMAGE ||
                                originalMessage.contentType == ContentType.IMAGE_WITH_TEXT) {

                                Log.d(TAG, "正在更新带图片的消息")

                                // 为新的带图片消息创建临时文件
                                if (originalMessage.imageData != null) {
                                    // 调用专用的更新图片消息方法
                                    viewModel.updateImageMessage(editingMessageId, message, originalMessage.imageData)
                                } else {
                                    Log.e(TAG, "图片数据为空，只能更新文本内容")
                                    viewModel.updateTextMessage(editingMessageId, message)
                                }
                            } else {
                                // 纯文本消息的更新
                                viewModel.updateTextMessage(editingMessageId, message)
                            }
                        } else {
                            Log.e(TAG, "找不到正在编辑的原始消息")
                            // 降级处理：使用普通的消息发送流程
                            sendNormalMessage(message)
                        }
                    } else {
                        Log.e(TAG, "无法获取编辑消息ID")
                        // 降级处理：使用普通的消息发送流程
                        sendNormalMessage(message)
                    }

                    // 重置编辑状态
                    viewModel.setEditingMode(false)

                    // 关闭编辑提示
                    currentSnackbar?.dismiss()
                    currentSnackbar = null

                    // 恢复发送按钮功能
                    setupSendButton()

                    return@launch // 提前返回，避免执行普通消息发送逻辑
                }

                // 非编辑模式：执行普通消息发送逻辑
                sendNormalMessage(message)

            } catch (e: Exception) {
                Log.e(TAG, "处理消息时出错: ${e.message}", e)
                // 确保即使出错也重置编辑状态
                viewModel.setEditingMode(false)

                // 显示错误提示
                Toast.makeText(this@MainActivity, "消息处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 发送普通消息的逻辑，从handleMessageSending中提取
     */
    private fun sendNormalMessage(message: String) {
        Log.d(TAG, "发送普通消息: $message")

        lifecycleScope.launch {
            currentSearchJob?.cancel()

            currentSearchJob = launch {
                // 闹钟分析
                val alarmResult = analyzeAndSetAlarmIfNeeded(message)
                if (alarmResult.first) {
                    viewModel.sendCustomAiMessage(alarmResult.second)
                }

                // 网络搜索增强
                if (viewModel.isWebSearchEnabled.value) {
                    val enhancedQuery = enhanceQueryWithContextInfo(message)
                    viewModel.performWebSearch(enhancedQuery)
                    delay(1500)
                }

                // 发送用户消息
                viewModel.sendMessage(message)

                // 滚动到底部
                recyclerView.post {
                    recyclerView.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }

    /**
     * 分析并设置/取消闹钟
     */
    private suspend fun analyzeAndSetAlarmIfNeeded(message: String): Pair<Boolean, String> {
        return try {
            // 直接使用AlarmManager的公共方法
            alarmManager.analyzeAndSetAlarm(message)
        } catch (e: Exception) {
            Log.e("MainActivity", "闹钟操作失败: ${e.message}", e)
            Pair(false, "")
        }
    }

    /**
     * 根据消息内容智能地增强查询
     */
    private fun enhanceQueryWithContextInfo(message: String): String {
        // 转为小写便于比较
        val lowerMsg = message.lowercase(Locale.getDefault())

        // 检查是否询问天气
        val weatherPattern = Pattern.compile("(天气|气温|下雨|温度|热不热|冷不冷|霾)")
        val weatherMatcher = weatherPattern.matcher(lowerMsg)

        // 检查是否询问时间或日期
        val timePattern = Pattern.compile("(几点|时间|日期|今天|现在|星期|周几|月份|年份)")
        val timeMatcher = timePattern.matcher(lowerMsg)

        // 检查是否询问位置
        val locationPattern = Pattern.compile("(在哪|位置|地点|地址|这是哪|哪里|附近)")
        val locationMatcher = locationPattern.matcher(lowerMsg)

        val enhancedQuery = StringBuilder(message)

        // 如果询问了时间或日期，添加详细的时间信息
        if (timeMatcher.find()) {
            // 获取详细时间，包括时区
            val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss E", Locale.CHINA)
            val currentTime = dateFormat.format(Date())
            val timeZone = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT, Locale.CHINA)
            enhancedQuery.append(" (当前时间: $currentTime, 时区: $timeZone)")
        }

        // 如果询问了位置或天气，添加位置信息
        if (locationMatcher.find() || (weatherMatcher.find() && !message.contains("哪里") && !message.contains("在"))) {
            var locationInfo = ""

            // 优先使用地理编码的详细地址
            if (geocodedAddressCache != null) {
                locationInfo = "位置: $geocodedAddressCache"
            }
            // 其次使用精确位置坐标
            else if (currentLocation != null) {
                locationInfo = "纬度: ${currentLocation!!.latitude}, 经度: ${currentLocation!!.longitude}"
            }
            // 最后使用IP定位作为备选
            else if (cachedLocationInfo != null) {
                locationInfo = "大致位置: $cachedLocationInfo"
            }

            if (locationInfo.isNotEmpty()) {
                enhancedQuery.append(" ($locationInfo)")
            }
        }

        // 返回增强后的查询
        return enhancedQuery.toString()
    }

    /**
     * 设置更多按钮点击事件
     */
    private fun setupMoreButton() {
        // 使用触摸监听器代替点击监听器，实现按下立即触发动画
        moreButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下时立即执行动画
                    startMoreButtonPressAnimation()
                    // 提供即时的触觉反馈
                    HapticUtils.performHapticFeedback(this@MainActivity, false)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 手指抬起时，如果在按钮区域内，则显示更多选项
                    val isInBounds = event.x >= 0 && event.x <= view.width &&
                            event.y >= 0 && event.y <= view.height

                    if (isInBounds) {
                        // 执行释放动画（带回弹效果）
                        startMoreButtonReleaseAnimation {
                            // 动画完成后显示底部表单
                            val bottomSheet = MoreOptionsBottomSheet()
                            bottomSheet.show(supportFragmentManager, "MoreOptionsBottomSheet")
                        }
                    } else {
                        // 如果手指在按钮区域外抬起，只执行释放动画
                        startMoreButtonReleaseAnimation()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // 操作取消时恢复按钮状态
                    startMoreButtonReleaseAnimation()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 执行更多按钮按下动画
     */
    private fun startMoreButtonPressAnimation() {
        // 立即改变图标颜色为主题色
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        moreButton.setColorFilter(primaryColor, PorterDuff.Mode.SRC_IN)

        // 创建缩放动画 - 加速版
        val scaleDown = AnimatorSet().apply {
            playTogether(
                // 缩小到65%，比原来更小以增强效果
                ObjectAnimator.ofFloat(moreButton, "scaleX", 1f, 0.65f),
                ObjectAnimator.ofFloat(moreButton, "scaleY", 1f, 0.65f)
            )
            // 减少持续时间使动画更快
            duration = 80  // 从120ms减少到80ms
            // 使用更激进的加速插值器
            interpolator = AccelerateInterpolator(2.0f)  // 加大加速力度
        }

        // 启动缩放动画
        scaleDown.start()
    }

    /**
     * 执行更多按钮释放动画
     * @param onAnimationEnd 动画完成后的回调
     */
    private fun startMoreButtonReleaseAnimation(onAnimationEnd: (() -> Unit)? = null) {
        // 还原图标颜色为次要文本颜色
        val secondaryColor = ContextCompat.getColor(this, R.color.text_secondary)

        // 创建带回弹效果的动画序列
        val animatorSet = AnimatorSet()

        // 第一阶段：快速反弹超过原始大小
        val bounce = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(moreButton, "scaleX", 0.65f, 1.2f),
                ObjectAnimator.ofFloat(moreButton, "scaleY", 0.65f, 1.2f)
            )
            duration = 150  // 快速反弹
            interpolator = DecelerateInterpolator(1.5f)  // 减速结束
        }

        // 第二阶段：稳定回到原始大小
        val settle = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(moreButton, "scaleX", 1.2f, 1.0f),
                ObjectAnimator.ofFloat(moreButton, "scaleY", 1.2f, 1.0f)
            )
            duration = 150
            interpolator = OvershootInterpolator(3.0f)  // 增加过冲效果
        }

        // 合并动画序列
        animatorSet.playSequentially(bounce, settle)

        // 在第一阶段开始时恢复颜色
        bounce.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                moreButton.setColorFilter(secondaryColor, PorterDuff.Mode.SRC_IN)
            }
        })

        // 添加整体动画完成监听器
        if (onAnimationEnd != null) {
            animatorSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                    onAnimationEnd.invoke()
                }
            })
        }

        // 启动动画序列
        animatorSet.start()
    }

    /**
     * 显示更多选项底部抽屉
     */
    private fun showMoreOptionsBottomSheet() {
        // 添加按钮动画效果
        val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            moreButton,
            PropertyValuesHolder.ofFloat("scaleX", 0.9f),
            PropertyValuesHolder.ofFloat("scaleY", 0.9f)
        )
        scaleDown.duration = 100

        val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
            moreButton,
            PropertyValuesHolder.ofFloat("scaleX", 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f)
        )
        scaleUp.duration = 100

        val animSequence = AnimatorSet()
        animSequence.playSequentially(scaleDown, scaleUp)
        animSequence.start()

        // 动画完成后显示底部表单
        animSequence.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 震动反馈
                HapticUtils.performHapticFeedback(this@MainActivity)

                val bottomSheet = MoreOptionsBottomSheet()
                bottomSheet.show(supportFragmentManager, "MoreOptionsBottomSheet")
            }
        })
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { messages ->
                    adapter.submitList(messages)

                    // 找到所有加载指示器并添加动画
                    messages.filter { it.isProcessing }.forEach { _ ->
                        val lastItemPosition = adapter.itemCount - 1
                        val lastViewHolder = recyclerView.findViewHolderForAdapterPosition(lastItemPosition)
                        if (lastViewHolder != null && lastViewHolder is MessageAdapter.AiMessageViewHolder) {
                            val loadingView = lastViewHolder.itemView.findViewById<View>(R.id.loadingIndicator)
                            animateTypingIndicator(loadingView)
                        }
                    }
                }
            }
        }
    }

    private fun animateTypingIndicator(view: View) {
        val dot1 = view.findViewById<TextView>(R.id.dot1)
        val dot2 = view.findViewById<TextView>(R.id.dot2)
        val dot3 = view.findViewById<TextView>(R.id.dot3)

        // 创建动画
        val animDot1 = createDotAnimation(dot1)
        val animDot2 = createDotAnimation(dot2)
        val animDot3 = createDotAnimation(dot3)

        // 设置延迟，让动画呈现波浪效果
        animDot1.start()
        animDot2.startDelay = 150
        animDot2.start()
        animDot3.startDelay = 300
        animDot3.start()
    }

    private fun createDotAnimation(dot: TextView): ObjectAnimator {
        // 创建单个动画，并应用重复属性
        val scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 0.8f, 1.2f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        val scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 0.8f, 1.2f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        val alpha = ObjectAnimator.ofFloat(dot, "alpha", 0.3f, 1.0f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        // 创建动画集合
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.interpolator = AccelerateDecelerateInterpolator()

        // 返回一个已经配置好的动画
        return ObjectAnimator.ofFloat(dot, "alpha", 0.3f, 1.0f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
        }
    }

    /**
     * 显示全屏图片
     */
    private fun showFullScreenImage(base64Image: String) {
        try {
            // 创建Intent启动图片查看器Activity
            val intent = Intent(this, ImageViewerActivity::class.java)
            intent.putExtra("IMAGE_DATA", base64Image)
            startActivity(intent)

            // 添加过渡动画
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        } catch (e: Exception) {
            Log.e(TAG, "打开图片查看器失败: ${e.message}", e)
            Toast.makeText(this, "无法打开图片查看器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMessageInteractions() {
        // 初始化FeedbackViewModel
        val feedbackViewModel = ViewModelProvider(this)[FeedbackViewModel::class.java]

        // 初始化adapter并设置所有回调，确保删除功能正常工作
        adapter = MessageAdapter(
            settingsManager,
            onLongClick = { message, view -> showMessageActions(message, view) },
            onCopyClick = { copyMessageToClipboard(it) },
            onRegenerateClick = { regenerateAiResponse(it) },
            onEditClick = { editUserMessage(it) },
            onDeleteClick = { showDeleteConfirmation(it) },
            onLoadMore = { isOlder ->
                if (isOlder) {
                    viewModel.loadMoreOlderMessages()
                } else {
                    viewModel.loadMoreNewerMessages()
                }
            },
            onFeedbackClick = { message, isPositive ->
                // 处理反馈点击
                val chatId = viewModel.repository.currentChatId.value ?: return@MessageAdapter
                // 提交反馈并显示提示
                feedbackViewModel.submitFeedback(chatId, message.id, isPositive)

                // 显示反馈提示 - 可选
                val feedbackMsg = if(isPositive) "感谢您的反馈，我们将继续提升AI回复质量" else "感谢您的反馈，我们将改进AI回复"
                Toast.makeText(this, feedbackMsg, Toast.LENGTH_SHORT).show()
            },
            onDocumentClick = { documentTitle ->
                try {
                    // 使用当前会话ID
                    val chatId = viewModel.repository.currentChatId.value
                    if (chatId != null) {
                        // 获取文档URI
                        val documentUri = getDocumentUri(chatId, documentTitle)
                        if (documentUri != null) {
                            // 启动文档查看器Activity
                            val intent = Intent(this, DocumentViewerActivity::class.java)
                            intent.putExtra("EXTRA_DOCUMENT_URI", documentUri.toString())
                            intent.putExtra("EXTRA_DOCUMENT_TITLE", documentTitle)
                            startActivity(intent)
                        } else {
                            Toast.makeText(this, "找不到文档文件", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "无法确定当前会话", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "打开文档失败: ${e.message}", e)
                    Toast.makeText(this, "打开文档失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onImageClick = { imageData ->
                // 处理图片点击事件
                showFullScreenImage(imageData)
            }
        )
        recyclerView.adapter = adapter
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmation(message: Message) {
        try {
            // 创建使用自定义样式的底部弹出对话框
            val bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)

            // 设置窗口背景为透明
            bottomSheetDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirmation, null)

            // 初始化按钮
            val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
            val btnDelete = dialogView.findViewById<Button>(R.id.btnDelete)

            // 设置按钮触摸事件
            btnDelete.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下时立即执行动画和触觉反馈
                        startButtonPressAnimation(view as Button)
                        HapticUtils.performHapticFeedback(this@MainActivity, true)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 手指抬起时，检查是否在按钮区域内
                        val isInBounds = event.x >= 0 && event.x <= view.width &&
                                event.y >= 0 && event.y <= view.height

                        if (isInBounds) {
                            // 执行释放动画
                            startButtonReleaseAnimation(view as Button) {
                                // 执行删除操作
                                viewModel.deleteMessage(message)
                                bottomSheetDialog.dismiss()
                            }
                        } else {
                            // 如果手指在按钮区域外抬起，只执行释放动画
                            startButtonReleaseAnimation(view as Button)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // 操作取消时重置按钮状态
                        startButtonReleaseAnimation(view as Button)
                        true
                    }
                    else -> false
                }
            }

            btnCancel.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下时立即执行动画和轻微触觉反馈
                        startButtonPressAnimation(view as Button)
                        HapticUtils.performHapticFeedback(this@MainActivity, false)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val isInBounds = event.x >= 0 && event.x <= view.width &&
                                event.y >= 0 && event.y <= view.height

                        if (isInBounds) {
                            // 执行释放动画并关闭对话框
                            startButtonReleaseAnimation(view as Button) {
                                bottomSheetDialog.dismiss()
                            }
                        } else {
                            // 如果手指在按钮区域外抬起，只执行释放动画
                            startButtonReleaseAnimation(view as Button)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        startButtonReleaseAnimation(view as Button)
                        true
                    }
                    else -> false
                }
            }

            // 设置对话框内容
            bottomSheetDialog.setContentView(dialogView)

            // 显示对话框
            bottomSheetDialog.show()

            // 获取底部表单视图并配置
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                // 设置背景为透明
                bottomSheet.setBackgroundResource(android.R.color.transparent)

                // 设置高度
                val layoutParams = bottomSheet.layoutParams
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                bottomSheet.layoutParams = layoutParams

                // 移除底部阴影
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示删除确认对话框失败: ${e.message}", e)
            // 出错时仍然尝试使用原始对话框
            showSimpleDeleteConfirmation(message)
        }
    }

    /**
     * 为按钮添加缩放动画效果
     */
    private fun applyButtonScaleAnimation(view: View) {
        val animatorSet = AnimatorSet()

        // 第一阶段：快速缩小
        val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat("scaleX", 0.6f),
            PropertyValuesHolder.ofFloat("scaleY", 0.6f)
        ).apply {
            duration = 120  // 设置到120毫秒
            interpolator = AccelerateInterpolator(1.5f)
        }

        // 第二阶段：弹回并略微超过原始大小（回弹效果）
        val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat("scaleX", 1.15f),
            PropertyValuesHolder.ofFloat("scaleY", 1.15f)
        ).apply {
            duration = 200  // 增加到200毫秒
            interpolator = DecelerateInterpolator(1.3f)
        }

        // 第三阶段：稳定回到原始大小
        val scaleNormal = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat("scaleX", 1.0f),
            PropertyValuesHolder.ofFloat("scaleY", 1.0f)
        ).apply {
            duration = 130  // 设置为130毫秒
            interpolator = OvershootInterpolator(0.7f)
        }

        // 按顺序播放动画
        animatorSet.playSequentially(scaleDown, scaleUp, scaleNormal)
        animatorSet.start()
    }

    // 备用的简单确认对话框方法
    private fun showSimpleDeleteConfirmation(message: Message) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialogTheme)
        builder.setTitle("删除消息")
        builder.setMessage("确定要删除这条消息吗？此操作不可撤销。")

        builder.setPositiveButton("删除") { dialog, _ ->
            viewModel.deleteMessage(message)
            dialog.dismiss()
        }

        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.delete_red)
        )
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
    }

    /**
     * 处理AI回复重新生成
     */
    private fun regenerateAiResponse(message: Message) {
        // 显示进度提示
        Toast.makeText(this, "正在重新生成回复...", Toast.LENGTH_SHORT).show()

        // 添加震动反馈
        HapticUtils.performHapticFeedback(this, true)

        lifecycleScope.launch {
            try {
                // 获取该AI回复之前的用户消息
                val messages = viewModel.messages.value
                val messageIndex = messages.indexOf(message)

                if (messageIndex > 0 && messageIndex < messages.size) {
                    // 寻找之前最近的用户消息
                    var userMessageIndex = messageIndex - 1
                    while (userMessageIndex >= 0) {
                        if (messages[userMessageIndex].type == MessageType.USER) {
                            // 找到了用户消息，重新发送它
                            val userMessage = messages[userMessageIndex]

                            // 删除当前的AI响应及其之后的所有消息
                            viewModel.deleteMessageAndFollowing(message)

                            // 稍作延迟，确保删除操作完成
                            delay(300)

                            // 重新发送用户消息获取新的回复
                            viewModel.sendMessage(userMessage.content)
                            break
                        }
                        userMessageIndex--
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "重新生成失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 处理用户消息重新编辑
     */
    private fun editUserMessage(message: Message) {
        // 检查是否是图片消息
        if (message.contentType == ContentType.IMAGE || message.contentType == ContentType.IMAGE_WITH_TEXT) {
            if (message.imageData != null) {
                // 创建图片编辑对话框
                showImageEditDialog(message)
                return
            }
        }

        // 非图片消息或图片数据为空时，使用原来的编辑流程
        // 添加详细日志来诊断问题
        Log.d(TAG, "开始编辑普通消息: ID=${message.id}, 类型=${message.contentType}")

        // 启动编辑模式
        viewModel.startEditing(message)

        // 将消息内容设置到输入框
        inputEditText.setText(message.content)

        // 给输入框焦点
        inputEditText.requestFocus()

        // 显示键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(inputEditText, InputMethodManager.SHOW_IMPLICIT)

        // 添加震动反馈
        HapticUtils.performHapticFeedback(this)

        // 显示取消编辑提示
        showCancelEditSnackbar()
    }

    /**
     * 图片编辑对话框
     */
    private fun showImageEditDialog(message: Message) {
        try {
            // 首先检查imageData是否为空
            val imageData = message.imageData
            if (imageData == null) {
                Log.e(TAG, "图片数据为空，无法编辑")
                Toast.makeText(this, "图片数据丢失，无法编辑", Toast.LENGTH_SHORT).show()
                // 降级为普通编辑
                fallbackToRegularEdit(message)
                return
            }

            Log.d(TAG, "显示图片编辑对话框: ID=${message.id}, 数据长度=${imageData.length}")

            // 启动编辑模式
            viewModel.startEditing(message)

            // 创建底部弹出对话框
            val bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
            bottomSheetDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val dialogView = layoutInflater.inflate(R.layout.dialog_image_caption, null)

            // 初始化视图元素
            val captionEditText = dialogView.findViewById<TextInputEditText>(R.id.captionEditText)
            val imagePreview = dialogView.findViewById<ImageView>(R.id.imagePreview)
            val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
            val btnSend = dialogView.findViewById<Button>(R.id.btnSend)

            // 设置已有标题
            captionEditText.setText(message.content)

            // 解码Base64图片并显示 - 现在我们确定imageData不为空
            val imageBytes = android.util.Base64.decode(imageData, android.util.Base64.DEFAULT)
            val cornerRadius = resources.getDimensionPixelSize(R.dimen.corner_radius)

            // 加载图片到预览
            Glide.with(this)
                .load(imageBytes)
                .transform(CenterCrop(), RoundedCorners(cornerRadius))
                .into(imagePreview)

            // 修改按钮文本
            btnSend.text = "更新"

            // 应用圆角背景到整个对话框
            dialogView.setBackgroundResource(R.drawable.rounded_content_background)

            // 设置按钮圆角背景和文字颜色
            btnSend.setBackgroundResource(R.drawable.rounded_button_background)
            btnCancel.setBackgroundResource(R.drawable.rounded_white_button_background)
            btnSend.setTextColor(ContextCompat.getColor(this, R.color.white))
            btnCancel.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

            // 使用触摸监听器代替点击监听器，实现按下立即触发动画
            btnSend.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下时立即执行动画和触觉反馈
                        startButtonPressAnimation(view as Button)
                        HapticUtils.performHapticFeedback(this@MainActivity, false)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 手指抬起时，检查是否在按钮区域内
                        val isInBounds = event.x >= 0 && event.x <= view.width &&
                                event.y >= 0 && event.y <= view.height

                        if (isInBounds) {
                            // 执行释放动画
                            startButtonReleaseAnimation(view as Button) {
                                // 动画完成后执行操作
                                val newCaption = captionEditText.text.toString().trim()

                                // 更新图片消息 - 使用已经验证非空的imageData
                                lifecycleScope.launch {
                                    try {
                                        viewModel.updateImageMessage(message.id, newCaption, imageData)
                                        Toast.makeText(this@MainActivity, "消息已更新", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "更新图片消息失败: ${e.message}", e)
                                        Toast.makeText(this@MainActivity, "更新失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                bottomSheetDialog.dismiss()
                            }
                        } else {
                            // 如果手指在按钮区域外抬起，只执行释放动画
                            startButtonReleaseAnimation(view as Button)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // 操作取消时重置按钮状态
                        startButtonReleaseAnimation(view as Button)
                        true
                    }
                    else -> false
                }
            }

            // 取消按钮触摸监听器
            btnCancel.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下时立即执行动画和轻微触觉反馈
                        startButtonPressAnimation(view as Button)
                        HapticUtils.performHapticFeedback(this@MainActivity, false)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val isInBounds = event.x >= 0 && event.x <= view.width &&
                                event.y >= 0 && event.y <= view.height

                        if (isInBounds) {
                            // 执行释放动画并取消编辑
                            startButtonReleaseAnimation(view as Button) {
                                viewModel.cancelEditing()
                                bottomSheetDialog.dismiss()
                            }
                        } else {
                            // 如果手指在按钮区域外抬起，只执行释放动画
                            startButtonReleaseAnimation(view as Button)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        startButtonReleaseAnimation(view as Button)
                        true
                    }
                    else -> false
                }
            }

            // 配置对话框
            bottomSheetDialog.setContentView(dialogView)

            // 对话框关闭时取消编辑模式
            bottomSheetDialog.setOnDismissListener {
                if (viewModel.isEditing.value) {
                    viewModel.cancelEditing()
                }
            }

            // 显示对话框
            bottomSheetDialog.show()

            // 配置底部表单
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                // 设置背景为透明
                bottomSheet.setBackgroundResource(android.R.color.transparent)

                // 设置高度
                val layoutParams = bottomSheet.layoutParams
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                bottomSheet.layoutParams = layoutParams

                // 设置展开状态
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示图片编辑对话框失败: ${e.message}", e)
            // 降级为普通编辑
            fallbackToRegularEdit(message)
        }
    }

    /**
     * 降级为普通编辑模式
     */
    private fun fallbackToRegularEdit(message: Message) {
        viewModel.startEditing(message)
        inputEditText.setText(message.content)
        inputEditText.requestFocus()
        showCancelEditSnackbar()
        Toast.makeText(this, "图片编辑模式加载失败，已切换到文本编辑", Toast.LENGTH_SHORT).show()
    }

    /**
     * 将Base64图片数据转换为临时文件
     */
    private suspend fun createTempFileFromBase64(base64Image: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                // 解码Base64数据
                val imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)

                // 创建临时文件
                val tempFile = File.createTempFile("edited_image_", ".jpg", cacheDir)

                // 写入图片数据
                FileOutputStream(tempFile).use { it.write(imageBytes) }

                return@withContext tempFile
            } catch (e: Exception) {
                Log.e(TAG, "创建临时图片文件失败: ${e.message}", e)
                return@withContext null
            }
        }
    }

    /**
     * 显示取消编辑提示
     */
    private fun showCancelEditSnackbar() {
        // 清除之前的提示
        currentSnackbar?.dismiss()

        val rootView = findViewById<View>(R.id.main)
        val snackbar = Snackbar.make(
            rootView,
            "编辑模式：修改完成后点击发送，或点击此处取消编辑",
            Snackbar.LENGTH_INDEFINITE
        )

        snackbar.setAction("取消") {
            // 添加震动反馈
            HapticUtils.performViewHapticFeedback(it)
            cancelEditing()
        }

        // 获取Snackbar的View并应用样式
        val snackbarView = snackbar.view

        // 设置文本样式
        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.textSize = 14f
        textView.maxLines = 2

        // 设置按钮样式
        val actionView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
        actionView.textSize = 14f
        actionView.setPadding(8, 0, 8, 0)
        actionView.minHeight = 48.dpToPx(this)

        // 创建圆角背景
        val backgroundDrawable = GradientDrawable()
        backgroundDrawable.cornerRadius = 16f.dpToPx(this).toFloat() // 16dp圆角
        backgroundDrawable.setColor(ContextCompat.getColor(this, R.color.primary))

        // 应用圆角背景
        snackbarView.background = backgroundDrawable

        // 设置位置和大小
        val topMargin = 220
        val params = snackbarView.layoutParams
        when (params) {
            is CoordinatorLayout.LayoutParams -> {
                params.gravity = Gravity.TOP
                params.setMargins(16, topMargin, 16, 0)
            }
            is FrameLayout.LayoutParams -> {
                params.gravity = Gravity.TOP
                params.setMargins(16, topMargin, 16, 0)
            }
            is ViewGroup.MarginLayoutParams -> {
                params.setMargins(16, topMargin, 16, 0)
            }
        }
        snackbarView.layoutParams = params
        snackbarView.minimumHeight = 56.dpToPx(this)

        // 存储引用
        currentSnackbar = snackbar

        // 更新发送按钮点击事件，使用新的处理流程
        sendButton.setOnClickListener {
            val messageText = inputEditText.text.toString().trim()
            if (messageText.isNotEmpty()) {
                handleMessageSending(messageText)
            }
        }

        snackbar.show()
    }

    /**
     * 像素转换助手函数，支持Float
     */
    private fun Float.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    /**
     * 主题重建方法
     */
    private fun recreateForThemeChange() {
        try {
            // 保存当前状态
            val currentScrollPosition = (recyclerView.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
            val currentChatId = viewModel.repository.currentChatId.value

            // 使用更简单的方式重建Activity
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("SCROLL_POSITION", currentScrollPosition)
            intent.putExtra("RESTORE_CHAT_ID", currentChatId)

            // 启动新Activity并使用淡入淡出动画
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

            // 结束当前Activity
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } catch (e: Exception) {
            Log.e(TAG, "主题重建失败: ${e.message}", e)
            // 降级处理：使用系统recreate方法
            recreate()
        }
    }

    /**
     * Activity生命周期方法
     * 处理应用退出时的编辑状态恢复
     */
    override fun onPause() {
        super.onPause()

        // 停止位置更新
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(locationListener)
        }

        // 检查编辑状态，如果正在编辑则恢复原始消息
        if (viewModel.isEditing.value) {
            lifecycleScope.launch {
                // 取消编辑，恢复原始消息
                viewModel.cancelEditing()

                // 关闭提示
                currentSnackbar?.dismiss()
                currentSnackbar = null
            }
        }
    }

    /**
     * 取消编辑
     */
    private fun cancelEditing() {
        viewModel.cancelEditing()
        inputEditText.text?.clear()

        // 隐藏键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(inputEditText.windowToken, 0)

        // 关闭Snackbar
        currentSnackbar?.dismiss()
        currentSnackbar = null

        // 恢复正常的发送按钮功能
        setupSendButton()
    }

    /**
     * 显示消息操作菜单
     * 为所有消息类型添加操作按钮动画效果
     */
    private fun showMessageActions(message: Message, view: View) {
        // 添加震动反馈
        HapticUtils.performViewHapticFeedback(view)

        try {
            // 查找操作按钮区域，考虑消息类型和内容类型
            val messageActions = when {
                // 用户消息 + 图片类型
                message.type == MessageType.USER &&
                        (message.contentType == ContentType.IMAGE ||
                                message.contentType == ContentType.IMAGE_WITH_TEXT) -> {
                    // 尝试查找图片消息操作区
                    view.findViewById<LinearLayout>(R.id.imageMessageActions)
                }
                // 普通用户消息或AI消息
                else -> {
                    view.findViewById<LinearLayout>(R.id.messageActions)
                }
            }

            if (messageActions != null) {
                Log.d(TAG, "${message.type}消息长按: 切换操作按钮显示")

                // 获取当前可见性状态
                val visible = messageActions.visibility != View.VISIBLE

                if (visible) {
                    // 显示操作按钮，带动画效果
                    showActionsWithAnimation(messageActions, view)
                } else {
                    // 隐藏操作按钮，带动画效果
                    hideActionsWithAnimation(messageActions, view)
                }
            } else {
                Log.e(TAG, "未找到消息操作按钮视图，消息类型: ${message.type}, 内容类型: ${message.contentType}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示消息操作按钮失败: ${e.message}", e)
        }
    }

    /**
     * 显示操作按钮
     */
    private fun showActionsWithAnimation(messageActions: LinearLayout, parentView: View) {
        // 设置初始状态
        messageActions.visibility = View.VISIBLE
        messageActions.alpha = 0f
        messageActions.scaleX = 0.85f
        messageActions.scaleY = 0.85f

        // 记录所有操作按钮
        val actionButtons = mutableListOf<View>()
        for (i in 0 until messageActions.childCount) {
            val child = messageActions.getChildAt(i)
            if (child is ImageButton || child is Button || child is ImageView) {
                actionButtons.add(child)
                // 设置按钮初始状态
                child.alpha = 0f
                child.translationY = 20f
            }
        }

        // 创建容器的动画
        val containerAnim = AnimatorSet()
        containerAnim.playTogether(
            ObjectAnimator.ofFloat(messageActions, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(messageActions, "scaleX", 0.85f, 1f),
            ObjectAnimator.ofFloat(messageActions, "scaleY", 0.85f, 1f)
        )
        containerAnim.duration = 180
        containerAnim.interpolator = OvershootInterpolator(1.2f)

        // 创建按钮依次出现的动画
        val buttonAnimators = mutableListOf<Animator>()
        actionButtons.forEachIndexed { index, button ->
            val buttonAnim = AnimatorSet()
            buttonAnim.playTogether(
                ObjectAnimator.ofFloat(button, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(button, "translationY", 20f, 0f)
            )
            buttonAnim.startDelay = 30L * index  // 依次出现的延迟
            buttonAnim.duration = 220
            buttonAnim.interpolator = DecelerateInterpolator(1.2f)
            buttonAnimators.add(buttonAnim)

            // 为每个按钮添加触摸动画
            setupButtonTouchAnimation(button)
        }

        // 创建完整的动画序列
        val fullAnimation = AnimatorSet()
        fullAnimation.playSequentially(
            containerAnim,
            AnimatorSet().apply { playTogether(buttonAnimators) }
        )
        fullAnimation.start()

        // 5. 添加点击外部区域隐藏按钮的监听器
        parentView.setOnClickListener {
            hideActionsWithAnimation(messageActions, parentView)
        }
    }

    /**
     * 隐藏操作按钮
     */
    private fun hideActionsWithAnimation(messageActions: LinearLayout, parentView: View) {
        // 创建淡出动画
        val fadeOut = AnimatorSet()
        fadeOut.playTogether(
            ObjectAnimator.ofFloat(messageActions, "alpha", messageActions.alpha, 0f),
            ObjectAnimator.ofFloat(messageActions, "scaleX", messageActions.scaleX, 0.85f),
            ObjectAnimator.ofFloat(messageActions, "scaleY", messageActions.scaleY, 0.85f)
        )
        fadeOut.duration = 150
        fadeOut.interpolator = AccelerateInterpolator()

        // 动画结束后隐藏视图
        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                messageActions.visibility = View.GONE
                // 移除点击监听器
                parentView.setOnClickListener(null)
            }
        })

        fadeOut.start()
    }

    /**
     * 为操作按钮设置触摸反馈动画
     */
    private fun setupButtonTouchAnimation(button: View) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下时的动画
                    v.animate()
                        .scaleX(0.85f)
                        .scaleY(0.85f)
                        .setDuration(100)
                        .setInterpolator(AccelerateInterpolator())
                        .start()

                    // 如果是ImageButton，添加颜色变化
                    if (v is ImageButton || v is ImageView) {
                        val primaryColor = ContextCompat.getColor(this, R.color.primary)
                        if (v is ImageButton) {
                            v.setColorFilter(primaryColor, PorterDuff.Mode.SRC_IN)
                        } else if (v is ImageView) {
                            v.colorFilter = PorterDuffColorFilter(primaryColor, PorterDuff.Mode.SRC_IN)
                        }
                    }

                    // 添加轻微触觉反馈
                    HapticUtils.performHapticFeedback(this, false)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 释放时的动画
                    val isInBounds = event.x >= 0 && event.x <= v.width &&
                            event.y >= 0 && event.y <= v.height

                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .withEndAction {
                            // 恢复颜色
                            if (v is ImageButton) {
                                v.clearColorFilter()
                            } else if (v is ImageView) {
                                v.clearColorFilter()
                            }

                            // 如果在按钮区域内释放，触发点击事件
                            if (isInBounds) {
                                v.performClick()
                            }
                        }
                        .start()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // 取消触摸时恢复按钮状态
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()

                    // 恢复颜色
                    if (v is ImageButton) {
                        v.clearColorFilter()
                    } else if (v is ImageView) {
                        v.clearColorFilter()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 复制消息到剪贴板
     */
    private fun copyMessageToClipboard(content: String) {
        // 添加震动反馈
        HapticUtils.performHapticFeedback(this)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Message", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    /**
     * 联网搜索切换回调
     */
    override fun onWebSearchToggled(enabled: Boolean) {
        // 更新ViewModel中的联网搜索状态
        viewModel.setWebSearchEnabled(enabled)
    }

    /**
     * 设置更改回调
     */
    override fun onSettingsChanged() {
        // 刷新页面或更新设置
        viewModel.loadSettings()

        // 更新AI名称
        updateAIName()

        // 刷新适配器以应用新的头像设置
        refreshMessageDisplay()
    }

    /**
     * 刷新消息显示
     */
    private fun refreshMessageDisplay() {
        // 初始化FeedbackViewModel
        val feedbackViewModel = ViewModelProvider(this)[FeedbackViewModel::class.java]

        // 创建适配器而不是只调用notifyDataSetChanged
        adapter = MessageAdapter(
            settingsManager,
            onLongClick = { message, view -> showMessageActions(message, view) },
            onCopyClick = { copyMessageToClipboard(it) },
            onRegenerateClick = { regenerateAiResponse(it) },
            onEditClick = { editUserMessage(it) },
            onDeleteClick = { showDeleteConfirmation(it) },
            onLoadMore = { isOlder ->
                if (isOlder) {
                    viewModel.loadMoreOlderMessages()
                } else {
                    viewModel.loadMoreNewerMessages()
                }
            },
            onFeedbackClick = { message, isPositive ->
                // 处理反馈点击
                val chatId = viewModel.repository.currentChatId.value ?: return@MessageAdapter
                feedbackViewModel.submitFeedback(chatId, message.id, isPositive)
            },
            onDocumentClick = { documentTitle ->
                try {
                    // 使用当前会话ID
                    val chatId = viewModel.repository.currentChatId.value
                    if (chatId != null) {
                        // 获取文档URI
                        val documentUri = getDocumentUri(chatId, documentTitle)
                        if (documentUri != null) {
                            // 启动文档查看器Activity
                            val intent = Intent(this, DocumentViewerActivity::class.java)
                            intent.putExtra("EXTRA_DOCUMENT_URI", documentUri.toString())
                            intent.putExtra("EXTRA_DOCUMENT_TITLE", documentTitle)
                            startActivity(intent)
                        } else {
                            Toast.makeText(this, "找不到文档文件", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "无法确定当前会话", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "打开文档失败: ${e.message}", e)
                    Toast.makeText(this, "打开文档失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onImageClick = { imageData ->
                // 处理图片点击事件
                showFullScreenImage(imageData)
            }
        )
        recyclerView.adapter = adapter

        // 重新加载消息
        lifecycleScope.launch {
            val messages = viewModel.messages.value
            adapter.submitList(messages)
        }

        // 滚动到底部
        recyclerView.post {
            if (adapter.itemCount > 0) {
                recyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    /**
     * 处理聊天记录选择
     */
    override fun onChatSelected(chatId: String) {
        // 切换到选定的聊天
        lifecycleScope.launch {
            viewModel.switchChat(chatId)

            // 切换聊天时也需要更新标题
            updateAIName()
        }

        // 可以添加一些UI反馈，例如显示Toast提示
        Toast.makeText(this, "已切换到选定的对话", Toast.LENGTH_SHORT).show()
    }

    /**
     * 处理新建聊天请求
     */
    override fun onNewChatRequested() {
        // 创建新的聊天
        viewModel.createNewChat()

        // 显示提示
        Toast.makeText(this, "已创建新对话", Toast.LENGTH_SHORT).show()

        // 滚动到最新消息
        if (adapter.itemCount > 0) {
            recyclerView.scrollToPosition(adapter.itemCount - 1)
        }

        // 更新AI名称
        updateAIName()
    }

    /**
     * 处理返回按键事件，用于支持子页面返回逻辑
     */
    override fun onBackPressed() {
        // 如果处于编辑模式，优先处理取消编辑
        if (viewModel.isEditing.value) {
            cancelEditing()
            return
        }

        // 查找底部菜单Fragment
        val bottomSheet = supportFragmentManager.findFragmentByTag("MoreOptionsBottomSheet") as? MoreOptionsBottomSheet

        if (bottomSheet != null && bottomSheet.isVisible) {
            // 使用一个本地变量接收处理结果，确保日志正确记录
            val handled = bottomSheet.onBackPressed()
            if (handled) {
                // 如果已处理，则拦截事件，不再往下传递
                return
            }
        }

        // 如果没有特殊处理，则执行默认行为
        super.onBackPressed()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

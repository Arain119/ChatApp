package com.example.chatapp.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.chatapp.R
import com.example.chatapp.data.SettingsManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MoreOptionsBottomSheet : BottomSheetDialogFragment(), SettingsFragment.SettingsListener {

    private val TAG = "MoreOptionsBottomSheet"
    private var isSettingsExpanded = false
    private var isNetworkEnabled = false // 追踪联网状态
    private var isSubpageOpen = false // 追踪是否有子页面打开

    // 视图引用
    private lateinit var settingsTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var buttonsContainer: ViewGroup
    private lateinit var dragHandle: View
    private lateinit var settingsButton: ImageButton
    private lateinit var networkButton: ImageButton
    private lateinit var networkText: TextView
    private lateinit var networkContainer: View
    private lateinit var settingsContentContainer: FrameLayout
    private lateinit var imagesButton: ImageButton
    private lateinit var filesButton: ImageButton
    private lateinit var imagesText: TextView
    private lateinit var filesText: TextView

    // 用于存储底部表单中的根视图
    private lateinit var bottomSheetFrame: FrameLayout
    private lateinit var rootView: View

    // 保存原始位置和样式信息
    private var originalSettingsX = 0f
    private var originalSettingsY = 0f
    private var originalSettingsSize = 0f
    private var originalTextColor = 0
    private var originalNetworkButtonY = 0f
    private var originalNetworkButtonSize = 0

    // 设置Fragment
    private var settingsFragment: SettingsFragment? = null

    // 设置管理器
    private lateinit var settingsManager: SettingsManager

    companion object {
        private const val REQUEST_SELECT_IMAGE = 1003
        private const val REQUEST_SELECT_FILE = 1004
        private const val SUBPAGE_BACKSTACK_NAME = "settings_subpage"
    }

    interface MoreOptionsListener {
        fun onImageSelected(imageUri: Uri)
        fun onFileSelected(fileUri: Uri)
        fun onWebSearchToggled(enabled: Boolean)
        fun onSettingsChanged()
    }

    private var listener: MoreOptionsListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MoreOptionsListener) {
            listener = context
        } else {
            throw ClassCastException("$context must implement MoreOptionsListener")
        }

        // 初始化设置管理器
        settingsManager = SettingsManager(context)
    }

    /**
     * 执行震动反馈
     * @param isStrong 是否为强震动
     */
    private fun performHapticFeedback(isStrong: Boolean = false) {
        try {
            val context = context ?: return

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 对于 Android 12 及以上版本
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                // 对于 Android 12 以下版本
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            // 执行震动
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 对于 Android 8.0 及以上版本
                if (isStrong) {
                    // 强震动
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                } else {
                    // 普通震动
                    vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                // 对于较旧版本
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (isStrong) 80 else 40)
            }
        } catch (e: Exception) {
            // 忽略震动失败，不影响主功能
            Log.e(TAG, "震动执行失败: ${e.message}", e)
        }
    }

    // 应用自定义圆角样式
    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme
    }

    // 确保底部表单有圆角
    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        Log.d(TAG, "onCreateDialog: 创建底部表单对话框")
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            Log.d(TAG, "对话框显示，应用动画效果")
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            bottomSheetFrame = bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)!!

            // 保存对底部表单根视图的引用，用于后续定位
            bottomSheetFrame.let {
                val behavior = BottomSheetBehavior.from(it)

                // 应用回弹动画效果
                applyOpeningAnimation(it)

                behavior.apply {
                    // 不允许折叠状态
                    skipCollapsed = true

                    // 明确启用拖动功能
                    isDraggable = true

                    // 设置折叠时的高度
                    peekHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)

                    // 默认展开
                    state = BottomSheetBehavior.STATE_EXPANDED

                    // 添加状态变化监听器，处理关闭动画和拖动检测
                    addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                        private var wasExpanded = true // 默认为展开状态

                        override fun onStateChanged(bottomSheet: View, newState: Int) {
                            Log.d(TAG, "状态变化: $newState")
                            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                                dismiss()
                            } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                                // 如果状态变为展开且之前不是展开状态，需要恢复联网图标
                                if (!wasExpanded && isNetworkEnabled) {
                                    createFloatingNetworkButton()
                                    Log.d(TAG, "底部表单回到展开状态，恢复联网图标")
                                }
                                wasExpanded = true
                            }
                            // 添加折叠状态处理
                            else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                                // 在折叠状态下可以执行特定操作
                                Log.d(TAG, "底部表单已折叠")
                                wasExpanded = false
                            }
                            else {
                                wasExpanded = false
                            }
                        }

                        override fun onSlide(bottomSheet: View, slideOffset: Float) {
                            // 只有在明显拖动时才隐藏联网图标 (slideOffset < 0.9)
                            if (slideOffset < 0.9 && isNetworkEnabled) {
                                hideFloatingNetworkButton(true)
                                Log.d(TAG, "检测到拖动，隐藏联网图标: slideOffset=$slideOffset")
                            }

                            // 根据滑动偏移量调整透明度或其他视觉反馈
                            dragHandle.alpha = 0.5f + (slideOffset * 0.5f)
                        }
                    })
                }

                // 设置背景为圆角drawable
                it.setBackgroundResource(R.drawable.rounded_top_corners)

                // 确保触摸事件能正确传递
                it.setOnTouchListener(null)
            }
        }

        return dialog
    }

    /**
     * 应用打开动画效果
     */
    private fun applyOpeningAnimation(view: View) {
        Log.d(TAG, "应用增强回弹动画效果")

        // 回弹
        val overshootInterpolator = android.view.animation.OvershootInterpolator(2.8f)

        // 初始下移距离
        view.translationY = 150f

        // 增加额外的视觉效果
        view.scaleY = 0.92f  // 初始垂直方向稍微压缩

        // 添加多个属性变化
        view.animate()
            .translationY(0f)    // 上移到目标位置
            .scaleY(1.02f)      // 稍微过量拉伸
            .setDuration(450)   // 稍微减少持续时间
            .setInterpolator(overshootInterpolator)
            .withEndAction {
                // 添加额外的"弹性收缩"动画，形成二次回弹
                view.animate()
                    .scaleY(1.0f)  // 回到正常尺寸
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    /**
     * 应用关闭动画效果
     */
    override fun dismiss() {
        // 如果有子页面正在打开，先关闭它
        if (isSubpageOpen && activity?.supportFragmentManager?.backStackEntryCount ?: 0 > 0) {
            activity?.supportFragmentManager?.popBackStack()
            return
        }

        // 如果设置已展开，先折叠再关闭
        if (isSettingsExpanded) {
            collapseSettings()
            return
        }

        // 立即隐藏浮动按钮
        hideFloatingNetworkButton(true)

        Log.d(TAG, "执行关闭动画")
        val bottomSheet = dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let {
            it.animate()
                .translationY(it.height.toFloat())
                .setDuration(300)
                .withEndAction {
                    // 动画结束后调用原始dismiss方法
                    Log.d(TAG, "关闭动画完成，调用super.dismiss()")
                    super.dismiss()
                }
                .start()
        } ?: super.dismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView: 创建视图")
        rootView = inflater.inflate(R.layout.bottom_sheet_more_options, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: 视图创建完成")

        // 查找重要视图
        settingsTextView = view.findViewById(R.id.settings_text)
        titleTextView = view.findViewById(R.id.title_text)
        buttonsContainer = view.findViewById(R.id.buttonsContainer)
        dragHandle = view.findViewById(R.id.dragHandle)
        settingsButton = view.findViewById(R.id.settingsButton)
        networkButton = view.findViewById(R.id.networkButton)
        networkText = view.findViewById(R.id.network_text)
        networkContainer = view.findViewById(R.id.networkContainer)
        settingsContentContainer = view.findViewById(R.id.settingsContentContainer)
        imagesButton = view.findViewById(R.id.imagesButton)
        filesButton = view.findViewById(R.id.filesButton)
        imagesText = view.findViewById(R.id.images_text)
        filesText = view.findViewById(R.id.files_text)

        val typedValue = TypedValue()
        context?.theme?.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        originalTextColor = typedValue.data
        settingsTextView.setTextColor(originalTextColor)

        // 在布局完成后获取原始位置信息
        settingsTextView.post {
            // 保存设置文本的原始位置和大小，用于动画恢复
            originalSettingsX = settingsTextView.x
            originalSettingsY = settingsTextView.y
            originalSettingsSize = settingsTextView.textSize

            // 保存网络按钮原始位置和尺寸
            originalNetworkButtonY = networkButton.y
            originalNetworkButtonSize = networkButton.width

            Log.d(TAG, "保存原始位置: x=$originalSettingsX, y=$originalSettingsY, size=$originalSettingsSize")
        }

        // 设置按钮动画效果
        setupButtonAnimations()

        // 检查并恢复网络状态
        view.post {
            checkAndRestoreNetworkState()
        }

        // 监听Activity的回退栈变化，处理子页面返回
        activity?.supportFragmentManager?.addOnBackStackChangedListener {
            val backStackEntryCount = activity?.supportFragmentManager?.backStackEntryCount ?: 0
            if (backStackEntryCount == 0 && isSubpageOpen) {
                // 子页面已关闭
                isSubpageOpen = false
                dialog?.show() // 显示底部菜单
            }
        }
    }

    /**
     * 设置按钮动画效果
     */
    private fun setupButtonAnimations() {
        // 获取所有按钮和对应的文本
        val buttonPairs = listOf(
            Pair(settingsButton, settingsTextView),
            Pair(imagesButton, imagesText),
            Pair(filesButton, filesText),
            Pair(networkButton, networkText)
        )

        // 定义颜色
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)

        // 存储每个按钮的原始文本颜色
        val originalTextColors = mutableMapOf<TextView, Int>()

        // 记录每个按钮的原始文本颜色
        buttonPairs.forEach { (_, text) ->
            originalTextColors[text] = text.currentTextColor
        }

        // 为每个按钮设置触摸动画
        buttonPairs.forEach { (button, text) ->
            button.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下时缩小
                        view.animate()
                            .scaleX(0.85f)
                            .scaleY(0.85f)
                            .setDuration(100)
                            .start()

                        // 立即变为主题色
                        button.setColorFilter(primaryColor)
                        text.setTextColor(primaryColor)

                        // 添加轻微震动
                        performHapticFeedback(false)

                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 如果手指在按钮区域内抬起，执行动作
                        val isInBounds = event.x >= 0 && event.x <= view.width &&
                                event.y >= 0 && event.y <= view.height

                        // 先恢复视觉状态
                        val originalTextColor = originalTextColors[text] ?: ContextCompat.getColor(requireContext(), R.color.text_primary)

                        // 创建弹性恢复动画
                        val bounceBack = AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(view, "scaleX", 0.85f, 1.05f),
                                ObjectAnimator.ofFloat(view, "scaleY", 0.85f, 1.05f)
                            )
                            duration = 150
                            interpolator = DecelerateInterpolator(1.5f)
                        }

                        val settle = AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(view, "scaleX", 1.05f, 1.0f),
                                ObjectAnimator.ofFloat(view, "scaleY", 1.05f, 1.0f)
                            )
                            duration = 100
                            interpolator = OvershootInterpolator(2.0f)
                        }

                        val sequence = AnimatorSet()
                        sequence.playSequentially(bounceBack, settle)

                        // 动画开始时立即恢复颜色，确保颜色一定会恢复
                        bounceBack.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                button.clearColorFilter()
                                text.setTextColor(originalTextColor)
                            }
                        })

                        // 执行动画，如果在区域内抬起，动画结束后执行相应功能
                        if (isInBounds) {
                            sequence.addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    // 动画完成后执行点击操作
                                    view.performClick()
                                }
                            })
                        }

                        sequence.start()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // 操作取消，立即恢复状态
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start()

                        // 恢复颜色
                        button.clearColorFilter()
                        val originalTextColor = originalTextColors[text] ?: ContextCompat.getColor(requireContext(), R.color.text_primary)
                        text.setTextColor(originalTextColor)

                        true
                    }
                    else -> false
                }
            }
        }

        // 设置按钮点击事件处理
        settingsButton.setOnClickListener {
            if (!isSettingsExpanded) {
                expandSettings()
            } else {
                collapseSettings()
            }
        }

        imagesButton.setOnClickListener {
            selectImage()
        }

        filesButton.setOnClickListener {
            selectFile()
        }

        networkButton.setOnClickListener {
            toggleNetworkFeature()
        }
    }

    /**
     * 切换联网功能状态
     */
    private fun toggleNetworkFeature() {
        // 切换前先确保完全清理现有状态
        if (isNetworkEnabled) {
            // 如果当前是启用状态，先完全禁用
            completeDisableNetworkFeature()
        } else {
            // 如果当前是禁用状态，启用联网功能
            completeEnableNetworkFeature()
        }
    }

    /**
     * 启用联网功能，确保状态和UI同步
     */
    private fun completeEnableNetworkFeature() {
        // 先更新状态
        isNetworkEnabled = true

        // 更新SettingsManager中的状态
        settingsManager.webSearchEnabled = true

        // 通知Activity联网状态改变
        listener?.onWebSearchToggled(true)

        // 然后应用UI变化
        enableNetworkFeature()
    }

    /**
     * 禁用联网功能，确保状态和UI同步
     */
    private fun completeDisableNetworkFeature() {
        // 查找当前的浮动按钮
        val decorView = dialog?.window?.decorView as? ViewGroup
        val floatingButton = decorView?.findViewById<ImageButton>(R.id.floating_network_button)

        if (floatingButton != null) {
            // 添加淡出动画
            floatingButton.animate()
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    // 动画完成后移除按钮
                    decorView.removeView(floatingButton)

                    // 显示原始按钮并添加淡入动画
                    networkText.alpha = 0f
                    networkButton.alpha = 0f

                    networkText.animate()
                        .alpha(1f)
                        .setDuration(250)
                        .start()

                    networkButton.animate()
                        .alpha(1f)
                        .setDuration(250)
                        .start()

                    // 更新状态
                    isNetworkEnabled = false

                    // 更新SettingsManager中的状态
                    settingsManager.webSearchEnabled = false

                    // 通知Activity联网状态改变
                    listener?.onWebSearchToggled(false)

                    // 恢复图标原始颜色
                    networkButton.clearColorFilter()

                    // 恢复其他按钮的原始位置
                    resetButtonPositions()
                }
                .start()
        } else {
            // 按钮不存在，直接更新状态
            isNetworkEnabled = false
            settingsManager.webSearchEnabled = false
            listener?.onWebSearchToggled(false)
            networkButton.clearColorFilter()
            resetButtonPositions()
        }

        Log.d(TAG, "联网功能已完全禁用，使用淡入淡出动画")
    }

    /**
     * 重置按钮位置辅助函数
     */
    private fun resetButtonPositions() {
        val settingsContainer = buttonsContainer.getChildAt(0) as ViewGroup
        val imagesContainer = buttonsContainer.getChildAt(1) as ViewGroup
        val filesContainer = buttonsContainer.getChildAt(2) as ViewGroup

        // 使用动画恢复原始位置
        settingsContainer.animate()
            .translationX(0f)
            .setDuration(250)
            .start()

        imagesContainer.animate()
            .translationX(0f)
            .setDuration(250)
            .start()

        filesContainer.animate()
            .translationX(0f)
            .setDuration(250)
            .start()
    }

    /**
     * 启用联网功能的UI变化
     */
    private fun enableNetworkFeature() {
        // 隐藏原始联网按钮及文本
        networkText.animate()
            .alpha(0f)
            .setDuration(200)
            .start()

        networkButton.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                // 原始按钮淡出后创建新的浮动按钮
                createFloatingNetworkButton()
            }
            .start()

        // 重新分配其余图标的空间
        val settingsContainer = buttonsContainer.getChildAt(0) as ViewGroup
        val imagesContainer = buttonsContainer.getChildAt(1) as ViewGroup
        val filesContainer = buttonsContainer.getChildAt(2) as ViewGroup

        // 计算三等分的宽度
        val containerWidth = buttonsContainer.width
        val equalWidth = containerWidth / 3f

        // 调整位置
        val settingsX = (equalWidth - settingsContainer.width) / 2
        val imagesX = equalWidth + (equalWidth - imagesContainer.width) / 2
        val filesX = 2 * equalWidth + (equalWidth - filesContainer.width) / 2

        // 应用位置动画
        settingsContainer.animate()
            .translationX(settingsX)
            .setDuration(300)
            .start()

        imagesContainer.animate()
            .translationX(imagesX - imagesContainer.x)
            .setDuration(300)
            .start()

        filesContainer.animate()
            .translationX(filesX - filesContainer.x)
            .setDuration(300)
            .start()

        Log.d(TAG, "联网功能UI已更新为启用状态")
    }

    /**
     * 创建浮动在标题栏右侧的网络按钮
     */
    private fun createFloatingNetworkButton() {
        // 如果设置面板已展开或Fragment已分离，不创建浮动按钮
        if (isSettingsExpanded || !isAdded) {
            Log.d(TAG, "设置面板已展开或Fragment已分离，不创建浮动按钮")
            return
        }

        // 安全检查
        val decorView = dialog?.window?.decorView as? ViewGroup ?: return
        val existingButton = decorView.findViewById<ImageButton>(R.id.floating_network_button)
        if (existingButton != null) {
            // 如果按钮已存在，先移除它
            decorView.removeView(existingButton)
            Log.d(TAG, "移除已存在的浮动按钮")
        }

        // 创建新的ImageButton
        val floatingButton = ImageButton(context ?: return).apply {
            id = R.id.floating_network_button
            setImageResource(R.drawable.ic_network)
            background = null

            // 设置蓝色滤镜
            val blueColor = ContextCompat.getColor(context, R.color.primary)
            setColorFilter(blueColor)

            // 设置尺寸
            val targetSize = resources.getDimensionPixelSize(R.dimen.network_icon_small_size)
            layoutParams = ViewGroup.LayoutParams(targetSize, targetSize)

            // 使用触摸监听器实现按下反馈
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下时缩小
                        view.animate()
                            .scaleX(0.85f)
                            .scaleY(0.85f)
                            .setDuration(100)
                            .start()

                        // 震动反馈
                        performHapticFeedback(true)

                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 判断是否在按钮区域内抬起
                        val isInBounds = event.x >= 0 && event.x <= view.width &&
                                event.y >= 0 && event.y <= view.height

                        // 弹性恢复
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setInterpolator(OvershootInterpolator(2.0f))
                            .setDuration(250)
                            .withEndAction {
                                // 只有在区域内抬起才执行功能
                                if (isInBounds) {
                                    toggleNetworkFeature()
                                }
                            }
                            .start()

                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // 恢复正常大小
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start()

                        true
                    }
                    else -> false
                }
            }

            // 初始化为透明
            alpha = 0f
        }

        // 将按钮添加到对话框的DecorView
        decorView.addView(floatingButton)

        // 使用ViewTreeObserver确保视图已完全布局
        if (!titleTextView.isAttachedToWindow) {
            Log.d(TAG, "titleTextView已分离，跳过布局")
            return
        }

        titleTextView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // 检查Fragment是否仍然附加到上下文
                if (!isAdded) {
                    titleTextView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    return
                }

                // 移除监听器，确保只执行一次
                titleTextView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // 获取标题的全局位置
                val titleLocation = IntArray(2)
                titleTextView.getLocationInWindow(titleLocation)

                // 计算屏幕宽度
                val screenWidth = resources.displayMetrics.widthPixels

                // 确保右侧边距一致
                val endMargin = resources.getDimensionPixelSize(R.dimen.network_icon_end_margin)

                // 精确计算X坐标，确保位置正确
                floatingButton.x = screenWidth - floatingButton.layoutParams.width - endMargin.toFloat()

                // 计算Y坐标，与标题中心对齐
                val titleCenterY = titleLocation[1] + titleTextView.height / 2f
                floatingButton.y = titleCenterY - floatingButton.layoutParams.height / 2f

                Log.d(TAG, "设置浮动按钮位置: x=${floatingButton.x}, y=${floatingButton.y}")

                // 添加淡入动画
                floatingButton.animate()
                    .alpha(1f)
                    .setDuration(250)
                    .start()
            }
        })

        Log.d(TAG, "浮动网络按钮已创建，等待布局完成")
    }

    /**
     * 隐藏浮动联网按钮
     * @param immediate 是否立即隐藏（拖动时为true，正常操作为false）
     */
    private fun hideFloatingNetworkButton(immediate: Boolean = false) {
        val decorView = dialog?.window?.decorView as? ViewGroup ?: return
        val floatingButton = decorView.findViewById<ImageButton>(R.id.floating_network_button) ?: return

        // 设置动画时长 - 拖动时使用更短的时间
        val duration = if (immediate) 50L else 200L

        // 如果需要立即隐藏，直接移除按钮
        if (immediate) {
            decorView.removeView(floatingButton)
            Log.d(TAG, "立即移除浮动网络按钮")
            return
        }

        // 否则使用动画
        floatingButton.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                // 确保动画完成后按钮确实被移除
                if (decorView.indexOfChild(floatingButton) >= 0) {
                    decorView.removeView(floatingButton)
                    Log.d(TAG, "动画完成后移除浮动网络按钮")
                }
            }
            .start()
    }

    /**
     * 检查并恢复网络状态
     */
    private fun checkAndRestoreNetworkState() {
        // 如果Fragment未附加到上下文，直接返回
        if (!isAdded) return

        // 从SettingsManager获取联网状态，确保与设置页面保持一致
        val savedNetworkEnabled = settingsManager.webSearchEnabled

        // 更新状态
        isNetworkEnabled = savedNetworkEnabled
        Log.d(TAG, "恢复网络状态: $isNetworkEnabled")

        // 先清除可能存在的浮动按钮
        val decorView = dialog?.window?.decorView as? ViewGroup ?: return
        val floatingButton = decorView.findViewById<ImageButton>(R.id.floating_network_button)
        decorView.removeView(floatingButton)

        // 如果联网功能已启用，创建浮动按钮并调整其他按钮位置
        if (isNetworkEnabled) {
            // 隐藏原始按钮
            networkText.alpha = 0f
            networkButton.alpha = 0f

            // 延迟创建浮动按钮，确保视图完全布局
            view?.post {
                if (!isAdded) return@post  // 检查Fragment是否仍附加

                // 只有在非设置展开状态下才创建浮动按钮
                if (!isSettingsExpanded) {
                    createFloatingNetworkButton()
                }

                // 调整其他按钮的位置
                val settingsContainer = buttonsContainer.getChildAt(0) as? ViewGroup ?: return@post
                val imagesContainer = buttonsContainer.getChildAt(1) as? ViewGroup ?: return@post
                val filesContainer = buttonsContainer.getChildAt(2) as? ViewGroup ?: return@post

                // 计算三等分的宽度
                val containerWidth = buttonsContainer.width
                val equalWidth = containerWidth / 3f

                // 调整位置
                val settingsX = (equalWidth - settingsContainer.width) / 2
                val imagesX = equalWidth + (equalWidth - imagesContainer.width) / 2
                val filesX = 2 * equalWidth + (equalWidth - filesContainer.width) / 2

                // 应用位置
                settingsContainer.translationX = settingsX
                imagesContainer.translationX = imagesX - imagesContainer.x
                filesContainer.translationX = filesX - filesContainer.x
            }
        } else {
            // 确保原始按钮可见
            networkText.alpha = 1f
            networkButton.alpha = 1f
            networkButton.clearColorFilter()

            // 确保按钮位置正确
            val settingsContainer = buttonsContainer.getChildAt(0) as ViewGroup
            val imagesContainer = buttonsContainer.getChildAt(1) as ViewGroup
            val filesContainer = buttonsContainer.getChildAt(2) as ViewGroup

            settingsContainer.translationX = 0f
            imagesContainer.translationX = 0f
            filesContainer.translationX = 0f
        }
    }

    /**
     * 展开设置面板的动画
     */
    private fun expandSettings() {
        Log.d(TAG, "展开设置面板")
        isSettingsExpanded = true

        // 执行震动反馈
        performHapticFeedback()

        // 获取底部表单视图
        val bottomSheet = dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            // 移除圆角
            sheet.background = ResourcesCompat.getDrawable(resources, R.color.background, null)

            // 隐藏标题文本，避免和子页面标题重叠
            titleTextView.animate()
                .alpha(0f)
                .setDuration(200)
                .start()

            // 淡出和移动其他按钮
            for (i in 0 until buttonsContainer.childCount) {
                val child = buttonsContainer.getChildAt(i)
                // 跳过设置按钮的容器
                if (i == 0) continue

                child.animate()
                    .translationX(200f)
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .start()
            }

            // 隐藏设置图标
            settingsButton.animate()
                .alpha(0f)
                .setDuration(200)
                .start()

            // 隐藏拖动把手
            dragHandle.animate()
                .alpha(0f)
                .setDuration(200)
                .start()

            // 隐藏浮动联网按钮
            hideFloatingNetworkButton()

            // 展开底部表单至全屏，使用动态计算
            val windowHeight = getScreenHeight()
            val statusBarHeight = getStatusBarHeight()
            val targetHeight = windowHeight - statusBarHeight

            // 创建高度动画
            val heightAnimator = ValueAnimator.ofInt(sheet.height, targetHeight)
            heightAnimator.duration = 400
            heightAnimator.interpolator = DecelerateInterpolator()
            heightAnimator.addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                val params = sheet.layoutParams
                params.height = value
                sheet.layoutParams = params
            }

            // 获取屏幕宽度
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels

            // 动态调整标题位置
            val leftMargin = resources.getDimensionPixelSize(R.dimen.toolbar_title_margin_start).toFloat()
            val topMargin = resources.getDimensionPixelSize(R.dimen.toolbar_title_margin_top).toFloat() - 10f

            // 设置最大宽度，防止标题文本被截断
            settingsTextView.maxWidth = (screenWidth - 2 * leftMargin).toInt()

            // 创建文字动画
            val textAnimatorX = ObjectAnimator.ofFloat(settingsTextView, "x", settingsTextView.x, leftMargin)
            val textAnimatorY = ObjectAnimator.ofFloat(settingsTextView, "y", settingsTextView.y, topMargin)

            // 文字大小动画
            val targetTextSize = resources.getDimension(R.dimen.toolbar_title_text_size) * 1.2f // 放大20%
            val textSizeAnimator = ValueAnimator.ofFloat(settingsTextView.textSize, targetTextSize)
            textSizeAnimator.addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                settingsTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, value)
            }

            // 播放所有动画
            val animSet = AnimatorSet()
            animSet.playTogether(heightAnimator, textAnimatorX, textAnimatorY, textSizeAnimator)
            animSet.duration = 400
            animSet.start()

            // 动画结束后设置文字加粗并使用主题颜色
            animSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    settingsTextView.setTypeface(settingsTextView.typeface, android.graphics.Typeface.BOLD)

                    // 使用主题属性设置文本颜色
                    val typedValue = TypedValue()
                    context?.theme?.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                    settingsTextView.setTextColor(typedValue.data)

                    // 显示设置内容视图
                    showSettingsContent()

                    // 回调通知设置已更改
                    listener?.onSettingsChanged()
                }
            })
        }
    }

    /**
     * 显示设置内容
     * 监听设置Fragment的子页面打开事件
     */
    private fun showSettingsContent() {
        // 如果设置内容容器不可见，设置为可见
        settingsContentContainer.visibility = View.VISIBLE

        // 如果设置Fragment尚未创建，创建并添加
        if (settingsFragment == null) {
            settingsFragment = SettingsFragment()

            // 使用标题作为Fragment的标题
            settingsFragment?.arguments = Bundle().apply {
                putBoolean("useExternalTitle", true)
            }

            // 设置子页面打开的监听器
            settingsFragment?.setSubpageOpenListener(object : SettingsFragment.SubpageOpenListener {
                override fun onSubpageOpen(fragment: BaseSettingsSubFragment) {
                    // 当子页面打开时，使用全屏方式显示它
                    openSubpageFullscreen(fragment)
                }
            })

            childFragmentManager.beginTransaction()
                .replace(R.id.settingsContentContainer, settingsFragment!!)
                .commit()
        }

        // 添加淡入动画
        settingsContentContainer.alpha = 0f
        settingsContentContainer.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    /**
     * 打开子页面到Activity的内容区域
     */
    private fun openSubpageFullscreen(fragment: BaseSettingsSubFragment) {
        // 先隐藏BottomSheet对话框
        dialog?.hide()

        // 标记子页面已打开
        isSubpageOpen = true

        // 震动反馈
        performHapticFeedback()

        // 设置子页面的导航回调，以便返回时显示BottomSheet
        fragment.setNavigationCallback(object : BaseSettingsSubFragment.NavigationCallback {
            override fun navigateBack() {
                // 子页面要返回时，弹出Activity回退栈
                performHapticFeedback() // 添加返回时的震动
                activity?.supportFragmentManager?.popBackStack(SUBPAGE_BACKSTACK_NAME, 0)
            }
        })

        // 使用Activity的FragmentManager添加Fragment到Activity的内容区域
        activity?.supportFragmentManager?.beginTransaction()
            ?.setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            ?.replace(android.R.id.content, fragment)
            ?.addToBackStack(SUBPAGE_BACKSTACK_NAME)
            ?.commit()
    }

    /**
     * 更新设置标题
     */
    override fun onTitleChanged(title: String) {
        // 更新设置面板的标题
        settingsTextView.text = title

        // 如果已经展开，确保标题正确显示
        if (isSettingsExpanded) {
            // 确保有足够的宽度显示完整标题
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val leftMargin = resources.getDimensionPixelSize(R.dimen.toolbar_title_margin_start).toFloat()
            settingsTextView.maxWidth = (screenWidth - 2 * leftMargin).toInt()

            // 请求重新布局
            settingsTextView.post {
                settingsTextView.requestLayout()
            }
        }
    }

    /**
     * 处理返回键事件，返回 true 表示事件已被处理
     */
    fun onBackPressed(): Boolean {
        Log.d(TAG, "onBackPressed called, isSubpageOpen=$isSubpageOpen, isSettingsExpanded=$isSettingsExpanded")

        // 执行震动反馈
        performHapticFeedback()

        // 优先处理子页面返回
        if (isSubpageOpen) {
            val backStackCount = activity?.supportFragmentManager?.backStackEntryCount ?: 0
            if (backStackCount > 0) {
                activity?.supportFragmentManager?.popBackStack()
                return true
            }
        }

        // 其次处理设置面板折叠
        if (isSettingsExpanded) {
            collapseSettings()
            return true
        }

        // 关闭底部菜单
        dismiss()
        return true
    }

    /**
     * 收起设置面板的动画
     */
    private fun collapseSettings() {
        Log.d(TAG, "收起设置面板")
        isSettingsExpanded = false

        // 执行震动反馈
        performHapticFeedback()

        // 隐藏设置内容视图
        settingsContentContainer.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                settingsContentContainer.visibility = View.GONE
            }
            .start()

        // 获取底部表单视图
        val bottomSheet = dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            // 恢复圆角
            sheet.background = ResourcesCompat.getDrawable(resources, R.drawable.rounded_top_corners, null)

            // 显示标题文本
            titleTextView.animate()
                .alpha(1f)
                .setDuration(200)
                .start()

            // 淡入并移动回其他按钮
            for (i in 0 until buttonsContainer.childCount) {
                val child = buttonsContainer.getChildAt(i)
                // 跳过设置按钮的容器
                if (i == 0) continue

                child.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }

            // 显示设置图标
            settingsButton.animate()
                .alpha(1f)
                .setDuration(200)
                .start()

            // 显示拖动把手
            dragHandle.animate()
                .alpha(1f)
                .setDuration(200)
                .start()

            // 折叠底部表单至原始高度
            val targetHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_height)

            // 创建高度动画
            val heightAnimator = ValueAnimator.ofInt(sheet.height, targetHeight)
            heightAnimator.duration = 300
            heightAnimator.interpolator = DecelerateInterpolator()
            heightAnimator.addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                val params = sheet.layoutParams
                params.height = value
                sheet.layoutParams = params
            }

            // 设置文字动画
            // 使用保存的原始位置
            val textAnimatorX = ObjectAnimator.ofFloat(settingsTextView, "x", settingsTextView.x, originalSettingsX)
            val textAnimatorY = ObjectAnimator.ofFloat(settingsTextView, "y", settingsTextView.y, originalSettingsY)

            // 文字大小动画
            val textSizeAnimator = ValueAnimator.ofFloat(settingsTextView.textSize, originalSettingsSize)
            textSizeAnimator.addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                settingsTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, value)
            }

            // 播放所有动画
            val animSet = AnimatorSet()
            animSet.playTogether(heightAnimator, textAnimatorX, textAnimatorY, textSizeAnimator)
            animSet.duration = 300
            animSet.start()

            // 动画结束后恢复文字样式和颜色
            animSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 恢复原始字体样式
                    settingsTextView.setTypeface(settingsTextView.typeface, android.graphics.Typeface.NORMAL)

                    // 恢复原始文本颜色
                    settingsTextView.setTextColor(originalTextColor)

                    // 恢复浮动联网按钮(如果联网功能开启)
                    if (isNetworkEnabled && isAdded) {
                        // 先检查Fragment是否仍然附加到上下文，避免崩溃
                        try {
                            createFloatingNetworkButton()
                        } catch (e: Exception) {
                            Log.e(TAG, "恢复浮动按钮失败: ${e.message}")
                        }
                    }
                }
            })
        }
    }

    /**
     * 获取屏幕高度
     */
    private fun getScreenHeight(): Int {
        val context = context ?: return 0
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val metrics = android.util.DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)
        return metrics.heightPixels
    }

    /**
     * 获取状态栏高度
     */
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_SELECT_IMAGE)
    }

    private fun selectFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // 使用正确的MIME类型，修复崩溃问题
        }
        startActivityForResult(intent, REQUEST_SELECT_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_SELECT_IMAGE -> {
                    data?.data?.let { uri ->
                        // 选择图片成功，执行震动反馈
                        performHapticFeedback(true)
                        listener?.onImageSelected(uri)
                        dismiss() // 重要！选择后关闭
                    }
                }
                REQUEST_SELECT_FILE -> {
                    data?.data?.let { uri ->
                        // 选择文件成功，执行震动反馈
                        performHapticFeedback(true)
                        listener?.onFileSelected(uri)
                        dismiss() // 重要！选择后关闭
                    }
                }
            }
        }
    }

    /**
     * 设置保存回调
     */
    override fun onSettingsSaved() {
        // 设置更改后同步本地状态
        isNetworkEnabled = settingsManager.webSearchEnabled

        // 通知Activity设置已更改
        listener?.onSettingsChanged()

        // 日志记录
        Log.d(TAG, "设置已更改，通知MainActivity刷新UI")

        // 延迟一下确保设置已保存
        view?.postDelayed({
            if (isAdded) {  // 检查Fragment是否仍然附加
                // 重新检查网络状态
                checkAndRestoreNetworkState()
            }
        }, 300)
    }

    // 清理浮动按钮，避免内存泄漏
    override fun onDestroyView() {
        super.onDestroyView()

        // 如果有浮动按钮，移除它，避免内存泄漏
        val decorView = dialog?.window?.decorView as? ViewGroup
        val floatingButton = decorView?.findViewById<ImageButton>(R.id.floating_network_button)
        decorView?.removeView(floatingButton)
    }

    override fun onDetach() {
        super.onDetach()
        listener = null // 避免内存泄漏
    }
}

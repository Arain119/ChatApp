package com.example.chatapp.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.chatapp.R
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.data.db.MessageEntity
import com.example.chatapp.utils.HapticUtils
import com.example.chatapp.viewmodel.ChatViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 用户个人主页Fragment
 * 显示用户信息和token消耗统计
 */
class UserProfileFragment : BaseSettingsSubFragment() {
    private val TAG = "UserProfileFragment"

    // ViewModel和设置管理器
    private lateinit var viewModel: ChatViewModel
    private lateinit var settingsManager: SettingsManager

    // UI组件
    private lateinit var userAvatarView: ShapeableImageView
    private lateinit var aiAvatarView: ShapeableImageView
    private lateinit var usernameText: TextView
    private lateinit var totalTokensText: TextView
    private lateinit var monthlyTokensText: TextView
    private lateinit var tokenChart: BarChart
    private lateinit var apiManagementCard: MaterialCardView

    // 卡片引用 - 用于动画
    private lateinit var avatarCard: MaterialCardView
    private lateinit var statsCard: MaterialCardView
    private lateinit var chartCard: MaterialCardView

    // 胶囊式切换按钮
    private lateinit var weekToggle: TextView
    private lateinit var monthToggle: TextView
    private lateinit var chartTitleText: TextView

    // 当前显示模式 (true = 月视图, false = 周视图)
    private var isMonthView = false

    // 头像编辑按钮
    private lateinit var userAvatarEditButton: View
    private lateinit var aiAvatarEditButton: View

    // 添加一个字段保存当前的统计数据
    private lateinit var currentStats: TokenStatistics

    // 动画已执行状态标记
    private var entranceAnimationsApplied = false

    // 头像选择结果处理
    private val userAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // 启动裁剪活动，而不是直接更新头像
                val intent = Intent(requireContext(), CropActivity::class.java).apply {
                    putExtra(CropActivity.EXTRA_SOURCE_URI, uri)
                    putExtra(CropActivity.EXTRA_IS_USER_AVATAR, true)
                }
                cropLauncher.launch(intent)
            }
        }
    }

    private val aiAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // 启动裁剪活动，而不是直接更新头像
                val intent = Intent(requireContext(), CropActivity::class.java).apply {
                    putExtra(CropActivity.EXTRA_SOURCE_URI, uri)
                    putExtra(CropActivity.EXTRA_IS_USER_AVATAR, false)
                }
                cropLauncher.launch(intent)
            }
        }
    }

    // 裁剪结果处理
    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val croppedUri = data.getParcelableExtra<Uri>(CropActivity.RESULT_CROPPED_URI)
            val isUserAvatar = data.getBooleanExtra(CropActivity.RESULT_IS_USER_AVATAR, true)

            if (croppedUri != null) {
                if (isUserAvatar) {
                    updateUserAvatar(croppedUri)
                } else {
                    updateAiAvatar(croppedUri)
                }
            }
        }
    }

    override fun getTitle(): String = "个人主页"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 延迟动画以等待视图完全绘制
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        // 初始化ViewModel和设置管理器
        viewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
        settingsManager = SettingsManager(requireContext())

        // 初始化UI组件
        initViews(view)

        // 设置点击事件
        setupClickListeners()

        // 加载头像
        loadAvatars()

        // 加载用户姓名
        usernameText.text = settingsManager.userName

        // 设置初始透明度为0，准备渐入动画
        prepareViewsForAnimation()

        // 加载Token统计
        loadTokenStatistics()

        // 应用进入动画
        view.post {
            if (!entranceAnimationsApplied) {
                applyEntranceAnimations()
                entranceAnimationsApplied = true
            }
        }
    }

    /**
     * 准备各元素用于动画
     */
    private fun prepareViewsForAnimation() {
        // 设置所有卡片初始状态
        avatarCard.alpha = 0f
        avatarCard.translationY = -50f

        statsCard.alpha = 0f
        statsCard.translationY = 50f

        apiManagementCard.alpha = 0f
        apiManagementCard.translationY = 50f

        chartCard.alpha = 0f
        chartCard.translationY = 50f

        // 准备头像容器的初始状态
        userAvatarView.scaleX = 0.8f
        userAvatarView.scaleY = 0.8f
        userAvatarView.alpha = 0f

        aiAvatarView.scaleX = 0.8f
        aiAvatarView.scaleY = 0.8f
        aiAvatarView.alpha = 0f

        userAvatarEditButton.alpha = 0f
        aiAvatarEditButton.alpha = 0f
    }

    private fun initViews(view: View) {
        // 初始化卡片组件引用
        avatarCard = view.findViewById(R.id.avatarCard)
        statsCard = view.findViewById(R.id.statsCard)
        chartCard = view.findViewById(R.id.chartCard)

        userAvatarView = view.findViewById(R.id.userAvatar)
        aiAvatarView = view.findViewById(R.id.aiAvatar)
        usernameText = view.findViewById(R.id.usernameText)
        totalTokensText = view.findViewById(R.id.totalTokensText)
        monthlyTokensText = view.findViewById(R.id.monthlyTokensText)
        tokenChart = view.findViewById(R.id.tokenChart)
        apiManagementCard = view.findViewById(R.id.apiManagementCard)  // 初始化API管理卡片

        // 获取头像编辑按钮
        userAvatarEditButton = view.findViewById(R.id.userAvatarEdit)
        aiAvatarEditButton = view.findViewById(R.id.aiAvatarEdit)

        // 胶囊式切换按钮引用
        weekToggle = view.findViewById(R.id.weekToggle)
        monthToggle = view.findViewById(R.id.monthToggle)
        chartTitleText = view.findViewById(R.id.chartTitleText)

        // 默认选中周视图
        setToggleState(true, false)

        // 添加用户名编辑视觉提示
        usernameText.setCompoundDrawablesWithIntrinsicBounds(
            null, null,
            ContextCompat.getDrawable(requireContext(), R.drawable.ic_edit_small),
            null
        )
        usernameText.compoundDrawablePadding = 8

        // 配置柱状图
        setupBarChart()
    }

    private fun setupClickListeners() {
        // 只有编辑按钮触发选择头像，而不是整个头像区域
        userAvatarEditButton.setOnClickListener {
            animateButtonPress(it)
            selectUserAvatar()
        }

        // AI头像编辑按钮点击事件
        aiAvatarEditButton.setOnClickListener {
            animateButtonPress(it)
            selectAiAvatar()
        }

        // 添加用户名编辑点击事件
        usernameText.setOnClickListener {
            animateTextPress(it)
            showEditUsernameDialog()
        }

        // API管理卡片点击事件
        apiManagementCard.setOnClickListener {
            // 使用挤压动画效果
            animateCardPress(apiManagementCard) {
                // 动画完成后执行跳转
                val intent = Intent(requireContext(), ApiManagementActivity::class.java)
                startActivity(intent)
                // 添加过渡动画
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }

        // 设置胶囊式切换按钮点击事件
        weekToggle.setOnClickListener {
            if (isMonthView) {
                isMonthView = false
                setToggleState(true, false)
                chartTitleText.text = "最近七天Token消耗"
                updateBarChart(currentStats.dailyTokens)

                // 添加按钮动画效果
                animateToggleButton(weekToggle)

                // 添加震动反馈
                HapticUtils.performHapticFeedback(requireContext())
            }
        }

        monthToggle.setOnClickListener {
            if (!isMonthView) {
                isMonthView = true
                setToggleState(false, true)
                chartTitleText.text = "近一月Token消耗"
                updateBarChart(currentStats.monthDailyTokens)

                // 添加按钮动画效果
                animateToggleButton(monthToggle)

                // 添加震动反馈
                HapticUtils.performHapticFeedback(requireContext())
            }
        }
    }

    /**
     * 为API管理卡片添加挤压动画效果
     */
    private fun animateCardPress(card: MaterialCardView, action: () -> Unit) {
        // 添加震动反馈
        HapticUtils.performViewHapticFeedback(card)

        // 创建动画集
        val animatorSet = AnimatorSet()

        // 缩小动画
        val scaleDownX = ObjectAnimator.ofFloat(card, "scaleX", 1f, 0.95f)
        val scaleDownY = ObjectAnimator.ofFloat(card, "scaleY", 1f, 0.95f)
        val pressElevation = ObjectAnimator.ofFloat(card, "cardElevation",
            card.cardElevation, card.cardElevation * 0.7f)

        // 恢复动画
        val scaleUpX = ObjectAnimator.ofFloat(card, "scaleX", 0.95f, 1.02f)
        val scaleUpY = ObjectAnimator.ofFloat(card, "scaleY", 0.95f, 1.02f)
        val releaseElevation = ObjectAnimator.ofFloat(card, "cardElevation",
            card.cardElevation * 0.7f, card.cardElevation * 1.2f)

        // 恢复到原始大小
        val normalizeX = ObjectAnimator.ofFloat(card, "scaleX", 1.02f, 1f)
        val normalizeY = ObjectAnimator.ofFloat(card, "scaleY", 1.02f, 1f)
        val normalizeElevation = ObjectAnimator.ofFloat(card, "cardElevation",
            card.cardElevation * 1.2f, card.cardElevation)

        // 配置缩小动画
        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY, pressElevation)
        scaleDown.duration = 100
        scaleDown.interpolator = AccelerateInterpolator()

        // 配置弹起动画
        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY, releaseElevation)
        scaleUp.duration = 150
        scaleUp.interpolator = DecelerateInterpolator()

        // 配置归一化动画
        val normalize = AnimatorSet()
        normalize.playTogether(normalizeX, normalizeY, normalizeElevation)
        normalize.duration = 200
        normalize.interpolator = OvershootInterpolator(0.8f)

        // 设置动画顺序
        animatorSet.playSequentially(scaleDown, scaleUp, normalize)

        // 执行动画并在适当时机执行动作
        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                action.invoke()
            }
        })

        animatorSet.start()
    }

    /**
     * 文本按压动画效果
     */
    private fun animateTextPress(view: View) {
        val scaleDown = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.92f).apply {
            duration = 100
            interpolator = AccelerateInterpolator()
        }
        val scaleUp = ObjectAnimator.ofFloat(view, "scaleX", 0.92f, 1.05f).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
        }
        val scaleNormal = ObjectAnimator.ofFloat(view, "scaleX", 1.05f, 1f).apply {
            duration = 200
            interpolator = OvershootInterpolator(1.2f)
        }

        // 创建Y轴缩放
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.92f).apply {
            duration = 100
            interpolator = AccelerateInterpolator()
        }
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.92f, 1.05f).apply {
            duration = 150
            interpolator = DecelerateInterpolator()
        }
        val scaleNormalY = ObjectAnimator.ofFloat(view, "scaleY", 1.05f, 1f).apply {
            duration = 200
            interpolator = OvershootInterpolator(1.2f)
        }

        // 创建动画集合
        val animSetX = AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp, scaleNormal)
        }
        val animSetY = AnimatorSet().apply {
            playSequentially(scaleDownY, scaleUpY, scaleNormalY)
        }

        // 同时播放X和Y轴的动画
        AnimatorSet().apply {
            playTogether(animSetX, animSetY)
            start()
        }

        // 添加震动反馈
        HapticUtils.performHapticFeedback(requireContext(), false)
    }

    /**
     * 按钮按压动画
     */
    private fun animateButtonPress(view: View) {
        // 缩小动画
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.85f),
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.85f),
                ObjectAnimator.ofFloat(view, "alpha", 1f, 0.7f)
            )
            duration = 80
            interpolator = AccelerateInterpolator()
        }

        // 弹回动画
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 0.85f, 1.05f),
                ObjectAnimator.ofFloat(view, "scaleY", 0.85f, 1.05f),
                ObjectAnimator.ofFloat(view, "alpha", 0.7f, 1f)
            )
            duration = 160
            interpolator = DecelerateInterpolator()
        }

        // 归一化动画
        val normalize = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1.05f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 1.05f, 1f)
            )
            duration = 100
            interpolator = OvershootInterpolator(1.5f)
        }

        // 按顺序执行动画
        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp, normalize)
            start()
        }

        // 添加震动反馈
        HapticUtils.performHapticFeedback(requireContext(), false)
    }

    /**
     * 设置切换按钮状态
     */
    private fun setToggleState(weekSelected: Boolean, monthSelected: Boolean) {
        if (weekSelected) {
            weekToggle.setBackgroundResource(R.drawable.pill_toggle_selected)
            weekToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.toggle_text_color))
            weekToggle.elevation = resources.getDimensionPixelSize(R.dimen.toggle_elevation).toFloat()
        } else {
            weekToggle.background = null
            weekToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.toggle_text_color_unselected))
            weekToggle.elevation = 0f
        }

        if (monthSelected) {
            monthToggle.setBackgroundResource(R.drawable.pill_toggle_selected)
            monthToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.toggle_text_color))
            monthToggle.elevation = resources.getDimensionPixelSize(R.dimen.toggle_elevation).toFloat()
        } else {
            monthToggle.background = null
            monthToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.toggle_text_color_unselected))
            monthToggle.elevation = 0f
        }
    }

    /**
     * 切换按钮动画效果
     */
    private fun animateToggleButton(button: View) {
        // 先快速按下
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.9f),
                ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.9f)
            )
            duration = 100
            interpolator = AccelerateInterpolator()
        }

        // 再弹回
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 0.9f, 1.15f),
                ObjectAnimator.ofFloat(button, "scaleY", 0.9f, 1.15f)
            )
            duration = 200
            interpolator = DecelerateInterpolator()
        }

        // 最后恢复正常大小
        val normalize = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 1.15f, 1f),
                ObjectAnimator.ofFloat(button, "scaleY", 1.15f, 1f)
            )
            duration = 150
            interpolator = OvershootInterpolator(2f)
        }

        // 按顺序执行动画
        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp, normalize)
            start()
        }
    }

    /**
     * 显示用户名编辑对话框
     */
    private fun showEditUsernameDialog() {
        // 创建自定义对话框
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_edit_username)

        // 设置窗口属性
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            // 添加动画
            attributes.windowAnimations = R.style.DialogAnimation
        }

        // 获取对话框控件
        val titleText = dialog.findViewById<TextView>(R.id.dialogTitle)
        val input = dialog.findViewById<EditText>(R.id.editText)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)
        val saveButton = dialog.findViewById<Button>(R.id.saveButton)

        // 设置标题和初始用户名
        titleText.text = "设置用户名"
        input.setText(usernameText.text)
        input.filters = arrayOf(InputFilter.LengthFilter(15))  // 限制最大长度

        // 动画方式显示对话框控件
        titleText.alpha = 0f
        input.alpha = 0f
        cancelButton.alpha = 0f
        saveButton.alpha = 0f

        dialog.setOnShowListener {
            // 对话框显示后依次执行元素动画
            titleText.animate().alpha(1f).translationY(0f).setDuration(300).start()
            input.animate().alpha(1f).translationY(0f).setDuration(300).setStartDelay(100).start()
            cancelButton.animate().alpha(1f).translationY(0f).setDuration(300).setStartDelay(200).start()
            saveButton.animate().alpha(1f).translationY(0f).setDuration(300).setStartDelay(300).start()
        }

        // 设置按钮点击监听
        cancelButton.setOnClickListener {
            animateButtonPress(it)
            dialog.dismiss()
        }

        saveButton.setOnClickListener {
            animateButtonPress(it)
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                // 更新UI和保存设置
                updateUsername(newName)
            }
            dialog.dismiss()
        }

        // 显示对话框并自动弹出键盘
        dialog.show()
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    /**
     * 更新用户名
     */
    private fun updateUsername(newName: String) {
        // 保存旧名字用于动画
        val oldName = usernameText.text.toString()

        // 保存到设置
        settingsManager.userName = newName

        // 执行更新动画
        val previousWidth = usernameText.width

        // 先淡出
        usernameText.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(200)
            .withEndAction {
                // 更新文本
                usernameText.text = newName

                // 确保文本测量完成后再执行动画
                usernameText.post {
                    // 淡入动画
                    usernameText.animate()
                        .alpha(1f)
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(250)
                        .withEndAction {
                            // 从轻微过大恢复正常大小
                            usernameText.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(150)
                                .setInterpolator(OvershootInterpolator(1.2f))
                                .start()
                        }
                        .start()
                }
            }
            .start()

        // 显示提示
        Toast.makeText(requireContext(), "用户名已更新", Toast.LENGTH_SHORT).show()
    }

    private fun selectUserAvatar() {
        val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        userAvatarLauncher.launch(intent)
    }

    private fun selectAiAvatar() {
        val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        aiAvatarLauncher.launch(intent)
    }

    /**
     * 更新用户头像
     */
    private fun updateUserAvatar(uri: Uri) {
        // 保存用户头像URI(持久化)
        val savedUri = settingsManager.saveImageUriPermanently(requireContext(), uri, true)

        if (savedUri != null) {
            // 先添加消失动画
            userAvatarView.animate()
                .scaleX(0f)
                .scaleY(0f)
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    // 消失后加载新头像
                    Glide.with(this)
                        .load(Uri.parse(savedUri))
                        .apply(RequestOptions.circleCropTransform())
                        .skipMemoryCache(true) // 跳过内存缓存
                        .into(userAvatarView)

                    // 然后添加出现动画
                    userAvatarView.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction {
                            // 最后轻微收缩到正常大小
                            userAvatarView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(150)
                                .setInterpolator(OvershootInterpolator(1.2f))
                                .start()
                        }
                        .start()
                }
                .start()

            // 添加一个成功通知动画 - 使头像编辑按钮闪烁
            ObjectAnimator.ofFloat(userAvatarEditButton, "alpha", 1f, 0.4f, 1f).apply {
                duration = 600
                repeatCount = 1
                start()
            }

            Toast.makeText(requireContext(), "用户头像已更新", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "头像保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新AI头像
     */
    private fun updateAiAvatar(uri: Uri) {
        // 保存AI头像URI(持久化)
        val savedUri = settingsManager.saveImageUriPermanently(requireContext(), uri, false)

        if (savedUri != null) {
            // 先添加消失动画
            aiAvatarView.animate()
                .scaleX(0f)
                .scaleY(0f)
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    // 消失后加载新头像
                    Glide.with(this)
                        .load(Uri.parse(savedUri))
                        .apply(RequestOptions.circleCropTransform())
                        .skipMemoryCache(true) // 跳过内存缓存
                        .into(aiAvatarView)

                    // 然后添加出现动画
                    aiAvatarView.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .alpha(1f)
                        .setDuration(300)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction {
                            // 最后轻微收缩到正常大小
                            aiAvatarView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(150)
                                .setInterpolator(OvershootInterpolator(1.2f))
                                .start()
                        }
                        .start()
                }
                .start()

            // 添加一个成功通知动画
            ObjectAnimator.ofFloat(aiAvatarEditButton, "alpha", 1f, 0.4f, 1f).apply {
                duration = 600
                repeatCount = 1
                start()
            }

            Toast.makeText(requireContext(), "AI头像已更新", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "AI头像已更新: $savedUri")
        } else {
            Toast.makeText(requireContext(), "头像保存失败", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "头像保存失败")
        }
    }

    private fun loadAvatars() {
        // 加载用户头像
        val userAvatarUri = settingsManager.userAvatarUri
        if (userAvatarUri != null) {
            Log.d(TAG, "加载用户头像: $userAvatarUri")
            Glide.with(this)
                .load(Uri.parse(userAvatarUri))
                .apply(RequestOptions.circleCropTransform())
                .skipMemoryCache(true)  // 跳过内存缓存
                .placeholder(R.drawable.default_user_avatar)
                .into(userAvatarView)
        }

        // 加载AI头像
        val aiAvatarUri = settingsManager.aiAvatarUri
        if (aiAvatarUri != null) {
            Log.d(TAG, "加载AI头像: $aiAvatarUri")
            Glide.with(this)
                .load(Uri.parse(aiAvatarUri))
                .apply(RequestOptions.circleCropTransform())
                .skipMemoryCache(true)  // 跳过内存缓存
                .placeholder(R.drawable.default_ai_avatar)
                .into(aiAvatarView)
        }
    }

    private fun setupBarChart() {
        // 图表样式配置
        with(tokenChart) {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setExtraOffsets(10f, 10f, 10f, 10f)

            // 禁用缩放和拖动
            setPinchZoom(false)
            setScaleEnabled(false)
            setTouchEnabled(false)

            // 配置X轴
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                textSize = 10f
                axisLineColor = ContextCompat.getColor(context, R.color.divider)
                axisLineWidth = 1.5f
            }

            // 配置左Y轴
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(context, R.color.divider)
                gridLineWidth = 0.5f
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                textSize = 10f
                axisLineColor = ContextCompat.getColor(context, R.color.divider)
                axisLineWidth = 1.5f
                setDrawZeroLine(true)
                zeroLineColor = ContextCompat.getColor(context, R.color.divider)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value > 1000) {
                            String.format("%.1fK", value / 1000)
                        } else {
                            value.toInt().toString()
                        }
                    }
                }
            }

            // 禁用右Y轴
            axisRight.isEnabled = false
        }
    }

    private fun loadTokenStatistics() {
        lifecycleScope.launch {
            // 获取当前会话ID
            val chatId = viewModel.repository.currentChatId.value

            if (chatId != null) {
                // 获取会话消息
                val messages = withContext(Dispatchers.IO) {
                    viewModel.repository.getChatMessages(chatId)
                }

                // 计算token统计数据
                val tokenStats = calculateTokenStats(messages)

                // 更新UI
                updateTokenStatisticsUI(tokenStats, messages)
            } else {
                // 没有活动会话，显示空数据
                updateTokenStatisticsUI(TokenStatistics(), emptyList())
            }
        }
    }

    /**
     * Token统计数据类
     */
    data class TokenStatistics(
        val totalTokens: Int = 0,
        val monthlyTokens: Int = 0,
        val dailyTokens: Map<String, Int> = emptyMap(),
        val monthDailyTokens: Map<String, Int> = emptyMap()
    )

    /**
     * 计算Token统计数据
     */
    private fun calculateTokenStats(messages: List<MessageEntity>): TokenStatistics {
        var totalTokens = 0
        var monthlyTokens = 0
        val dailyTokens = mutableMapOf<String, Int>()
        val monthDailyTokens = mutableMapOf<String, Int>() // 近一月每日数据

        // 获取当前日期和时间
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        // 日期格式化器
        val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())

        // 获取最近7天的日期
        val last7Days = (0 until 7).map { daysAgo ->
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
            dateFormat.format(calendar.time)
        }.reversed()

        // 获取最近30天的日期
        val last30Days = (0 until 30).map { daysAgo ->
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
            dateFormat.format(calendar.time)
        }.reversed()

        // 初始化统计数据
        last7Days.forEach { date ->
            dailyTokens[date] = 0
        }

        last30Days.forEach { date ->
            monthDailyTokens[date] = 0
        }

        // 获取30天前的时间戳
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val thirtyDaysAgo = calendar.timeInMillis

        // 遍历消息计算token
        messages.forEach { message ->
            // 估算token数量
            val content = message.content
            val englishTokens = content.count { it in 'a'..'z' || it in 'A'..'Z' || it == ' ' } / 4
            val chineseTokens = content.count { it.code > 127 }
            val messageTokens = englishTokens + chineseTokens

            // 累加总token
            totalTokens += messageTokens

            // 检查消息日期
            val messageDate = Date(message.timestamp.time)
            val messageCalendar = Calendar.getInstance()
            messageCalendar.time = messageDate

            // 如果是当前月的消息，累加到月度token
            if (messageCalendar.get(Calendar.MONTH) == currentMonth &&
                messageCalendar.get(Calendar.YEAR) == currentYear) {
                monthlyTokens += messageTokens
            }

            // 格式化消息日期
            val messageDateStr = dateFormat.format(messageDate)

            // 检查是否在最近7天内
            if (messageDateStr in dailyTokens) {
                dailyTokens[messageDateStr] = dailyTokens[messageDateStr]!! + messageTokens
            }

            // 检查是否在最近30天内
            if (message.timestamp.time >= thirtyDaysAgo && messageDateStr in monthDailyTokens) {
                monthDailyTokens[messageDateStr] = monthDailyTokens[messageDateStr]!! + messageTokens
            }
        }

        return TokenStatistics(
            totalTokens = totalTokens,
            monthlyTokens = monthlyTokens,
            dailyTokens = dailyTokens,
            monthDailyTokens = monthDailyTokens
        )
    }

    /**
     * 更新Token统计UI
     */
    private fun updateTokenStatisticsUI(stats: TokenStatistics, messages: List<MessageEntity>) {
        // 保存当前统计数据
        currentStats = stats

        // 用0初始化文本，准备动画
        totalTokensText.text = "0"
        monthlyTokensText.text = "0"

        // 更新总token数
        animateTextCounter(totalTokensText, 0, stats.totalTokens, 1800)

        // 更新本月token数
        animateTextCounter(monthlyTokensText, 0, stats.monthlyTokens, 1500)

        // 根据当前视图模式更新柱状图数据
        updateBarChart(if (isMonthView) stats.monthDailyTokens else stats.dailyTokens)
    }

    /**
     * 增强版数字增长动画
     */
    private fun animateTextCounter(textView: TextView, start: Int, end: Int, duration: Long = 1500) {
        val animator = ValueAnimator.ofInt(start, end).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()

            addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Int
                textView.text = formatNumber(animatedValue)
            }

            // 添加初始和结束缩放动画
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    // 开始时的缩小动画
                    textView.scaleX = 0.8f
                    textView.scaleY = 0.8f
                    textView.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(300)
                        .start()
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 结束时的弹跳动画
                    textView.animate()
                        .scaleX(1.1f).scaleY(1.1f)
                        .setDuration(200)
                        .withEndAction {
                            textView.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(150)
                                .setInterpolator(OvershootInterpolator(1.5f))
                                .start()
                        }
                        .start()
                }
            })
        }

        animator.start()
    }

    /**
     * 更新柱状图数据
     */
    private fun updateBarChart(dailyData: Map<String, Int>) {
        // 准备柱状图数据
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        // 遍历按日期排序的数据
        dailyData.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
            entries.add(BarEntry(index.toFloat(), entry.value.toFloat()))

            // 对于月视图，我们可能需要精简标签以避免拥挤
            val label = if (isMonthView) {
                // 只显示每5天的完整日期，其他日期只显示日期部分
                if (index % 5 == 0) entry.key else entry.key.split("-").last()
            } else {
                entry.key
            }

            labels.add(label)
        }

        // 创建数据集
        val dataSet = BarDataSet(entries, if (isMonthView) "每日Token (月)" else "每日Token (周)")

        // 配置数据集样式
        dataSet.apply {
            color = ContextCompat.getColor(requireContext(), R.color.primary)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            valueTextSize = 10f
            highLightColor = ContextCompat.getColor(requireContext(), R.color.primary_dark)
            highLightAlpha = 200

            // 对于月视图，可能需要禁用值显示以避免拥挤
            setDrawValues(!isMonthView || entries.size <= 15)

            // 设置值格式化器，高于1000的显示为1.2K格式
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 1000) {
                        String.format("%.1fK", value / 1000)
                    } else {
                        value.toInt().toString()
                    }
                }
            }
        }

        // 创建柱状图数据
        val barData = BarData(dataSet)
        barData.barWidth = if (isMonthView) 0.4f else 0.6f

        // 设置X轴标签
        tokenChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            // 对于月视图，可能需要调整标签显示间隔
            if (isMonthView) {
                labelCount = 6  // 显示大约6个标签
                granularity = (labels.size / 6f).coerceAtLeast(1f)  // 最小间隔为1
            } else {
                labelCount = labels.size  // 显示所有标签
                granularity = 1f
            }
        }

        // 更新图表
        tokenChart.data = barData

        // 添加增强的渐变动画效果
        tokenChart.animateY(1500, com.github.mikephil.charting.animation.Easing.EaseOutQuart)

        // 刷新图表
        tokenChart.invalidate()
    }

    /**
     * 格式化数字为易读形式（添加千位分隔符）
     */
    private fun formatNumber(number: Int): String {
        return String.format("%,d", number)
    }

    /**
     * 应用入场动画
     */
    private fun applyEntranceAnimations() {
        // 卡片动画
        animateCardEntrance(avatarCard, 0L)
        animateCardEntrance(statsCard, 250L)
        animateCardEntrance(apiManagementCard, 400L)
        animateCardEntrance(chartCard, 550L)

        // 头像动画
        animateAvatarEntrance(userAvatarView, userAvatarEditButton, 500L)
        animateAvatarEntrance(aiAvatarView, aiAvatarEditButton, 700L)

        // 用户名文本动画
        usernameText.alpha = 0f
        usernameText.translationY = -20f
        usernameText.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * 卡片入场动画
     */
    private fun animateCardEntrance(card: MaterialCardView, startDelay: Long) {
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(startDelay)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 添加卡片阴影动画
        ObjectAnimator.ofFloat(card, "cardElevation", 0f, card.cardElevation).apply {
            duration = 800
            this.startDelay = startDelay + 100  // 明确使用this引用ObjectAnimator的属性
            start()
        }
    }

    /**
     * 头像入场动画
     */
    private fun animateAvatarEntrance(avatarView: ShapeableImageView, editButton: View, startDelay: Long) {
        // 头像缩放和透明度动画
        val scaleX = ObjectAnimator.ofFloat(avatarView, "scaleX", 0.8f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(avatarView, "scaleY", 0.8f, 1.1f, 1f)
        val alpha = ObjectAnimator.ofFloat(avatarView, "alpha", 0f, 1f)

        val animSet = AnimatorSet()
        animSet.playTogether(scaleX, scaleY, alpha)
        animSet.duration = 700
        animSet.startDelay = startDelay
        animSet.interpolator = DecelerateInterpolator()
        animSet.start()

        // 编辑按钮出现动画
        editButton.animate()
            .alpha(1f)
            .setDuration(300)
            .setStartDelay(startDelay + 400)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}
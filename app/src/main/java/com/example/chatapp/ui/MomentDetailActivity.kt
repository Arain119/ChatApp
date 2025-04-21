package com.example.chatapp.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.chatapp.R
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.repository.MomentRepository
import com.example.chatapp.utils.HapticUtils
import com.example.chatapp.utils.TextFormatter
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "MomentDetailActivity"

/**
 * 动态详情页面
 */
class MomentDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MOMENT_ID = "moment_id"

        fun start(context: Context, momentId: String) {
            val intent = Intent(context, MomentDetailActivity::class.java)
            intent.putExtra(EXTRA_MOMENT_ID, momentId)
            context.startActivity(intent)
        }
    }

    private lateinit var momentRepository: MomentRepository
    private lateinit var settingsManager: SettingsManager
    private var currentMomentId: String? = null

    // 视图引用
    private lateinit var shareButton: ImageButton
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var rootView: View
    private lateinit var momentImage: ImageView
    private lateinit var aiAvatar: ShapeableImageView
    private lateinit var aiName: TextView
    private lateinit var momentTitle: TextView
    private lateinit var momentTime: TextView
    private lateinit var momentContent: TextView
    private lateinit var topGradientMask: View
    private lateinit var toolbarContent: View
    private lateinit var contentScrollView: androidx.core.widget.NestedScrollView
    private lateinit var contentContainer: View
    private lateinit var imageCardContainer: View

    // 动画标志
    private var hasInitializedAnimations = false

    // 圆角大小设置
    private val cornerRadius = 16

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moment_detail)

        // 启用沉浸式边缘体验
        enableEdgeToEdge()

        // 初始化视图引用
        initViewReferences()

        // 初始化设置和Repository
        settingsManager = SettingsManager(this)
        momentRepository = MomentRepository(this)

        // 获取动态ID
        currentMomentId = intent.getStringExtra(EXTRA_MOMENT_ID)
        if (currentMomentId == null) {
            Toast.makeText(this, "动态不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 初始化工具栏
        setupToolbar()

        // 设置状态栏和导航栏的插入处理
        setupEdgeToEdgeInsets()

        // 预先隐藏所有将要动画显示的元素
        if (savedInstanceState == null) {
            prepareForEntranceAnimations()
        }

        // 加载动态详情
        loadMomentDetail()

        // 设置图片交互
        setupImageInteraction()
    }

    /**
     * 将dp值转换为对应的像素值
     */
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    /**
     * 初始化视图引用
     */
    private fun initViewReferences() {
        toolbar = findViewById(R.id.toolbar)
        rootView = findViewById(R.id.root_container)
        toolbarContent = findViewById(R.id.toolbarContent)
        contentScrollView = findViewById(R.id.contentScrollView)
        contentContainer = findViewById(R.id.contentContainer)
        shareButton = findViewById(R.id.shareButton)
        momentImage = findViewById(R.id.momentImage)
        aiAvatar = findViewById(R.id.aiAvatar)
        aiName = findViewById(R.id.aiName)
        momentTitle = findViewById(R.id.momentTitle)
        momentTime = findViewById(R.id.momentTime)
        momentContent = findViewById(R.id.momentContent)
        topGradientMask = findViewById(R.id.topGradientMask)
        imageCardContainer = findViewById(R.id.imageCardContainer)

        // 确保momentImage是ShapeableImageView类型
        if (momentImage is ShapeableImageView) {
            // 动态配置ShapeableImageView的圆角
            val shapeableImageView = momentImage as ShapeableImageView
            val shapeAppearanceModel = shapeableImageView.shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(cornerRadius.dpToPx().toFloat())
                .build()
            shapeableImageView.shapeAppearanceModel = shapeAppearanceModel
        }
    }

    /**
     * 准备入场动画
     */
    private fun prepareForEntranceAnimations() {
        // 设置初始状态为隐藏
        contentContainer.alpha = 0f
        contentContainer.translationY = 50f

        aiAvatar.alpha = 0f
        aiAvatar.scaleX = 0.7f
        aiAvatar.scaleY = 0.7f

        aiName.alpha = 0f
        aiName.translationX = -25f

        shareButton.alpha = 0f
        shareButton.translationX = 25f

        // 内容详情初始状态
        momentTitle.alpha = 0f
        momentTitle.translationY = 15f

        momentTime.alpha = 0f
        momentTime.translationY = 12f

        momentContent.alpha = 0f
        momentContent.translationY = 20f

        // 图片容器初始化
        imageCardContainer.alpha = 0f
        imageCardContainer.scaleX = 0.97f
        imageCardContainer.scaleY = 0.97f
    }

    /**
     * 启用沉浸式边缘体验
     */
    private fun enableEdgeToEdge() {
        // 设置内容延伸到系统栏下方
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        // 确保状态栏图标使用暗色（适用于浅色背景）
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = true

        // 设置半透明状态栏
        window.statusBarColor = Color.argb(128, 255, 255, 255)
    }

    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        setSupportActionBar(toolbar)

        // 隐藏默认标题和返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowHomeEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // 设置分享按钮点击效果
        shareButton.setOnClickListener {
            // 添加震动反馈
            HapticUtils.performViewHapticFeedback(it)

            // 添加动画效果
            animateShareButton()

            // 延迟分享操作
            it.postDelayed({
                shareMoment()
            }, 120)
        }
    }

    /**
     * 视图按压动画
     */
    private fun animateViewPress(view: View) {
        val scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.92f)
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.92f)
        val scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 0.92f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.92f, 1f)

        scaleDownX.duration = 60
        scaleDownY.duration = 60
        scaleUpX.duration = 100
        scaleUpY.duration = 100

        scaleUpX.interpolator = OvershootInterpolator(2.5f)
        scaleUpY.interpolator = OvershootInterpolator(2.5f)

        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY)

        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY)

        val sequence = AnimatorSet()
        sequence.playSequentially(scaleDown, scaleUp)
        sequence.start()
    }

    /**
     * 分享按钮动画效果
     */
    private fun animateShareButton() {
        // 旋转动画
        val rotate = ObjectAnimator.ofFloat(shareButton, "rotation", 0f, 360f)
        rotate.duration = 350
        rotate.interpolator = AccelerateDecelerateInterpolator()

        // 缩放动画
        val scaleDown = ObjectAnimator.ofFloat(shareButton, "scaleX", 1f, 0.75f)
        val scaleUp = ObjectAnimator.ofFloat(shareButton, "scaleX", 0.75f, 1f)
        scaleDown.duration = 100
        scaleUp.duration = 250
        scaleUp.interpolator = OvershootInterpolator(3.5f)

        val scaleDownY = ObjectAnimator.ofFloat(shareButton, "scaleY", 1f, 0.75f)
        val scaleUpY = ObjectAnimator.ofFloat(shareButton, "scaleY", 0.75f, 1f)
        scaleDownY.duration = 100
        scaleUpY.duration = 250
        scaleUpY.interpolator = OvershootInterpolator(3.5f)

        // 组合动画
        val scaleSequence = AnimatorSet()
        scaleSequence.playTogether(scaleDown, scaleDownY)

        val scaleUpSequence = AnimatorSet()
        scaleUpSequence.playTogether(scaleUp, scaleUpY)

        val sequence = AnimatorSet()
        sequence.playSequentially(scaleSequence, scaleUpSequence)

        // 同时执行旋转
        val all = AnimatorSet()
        all.playTogether(rotate, sequence)
        all.start()
    }

    /**
     * 设置边缘到边缘的插入处理
     */
    private fun setupEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(topGradientMask) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        // 为内容区域添加边距处理
        ViewCompat.setOnApplyWindowInsetsListener(contentScrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom + systemBars.bottom)
            insets
        }
    }

    /**
     * 判断是否为Base64编码的图片
     */
    private fun isBase64Image(data: String?): Boolean {
        if (data == null) return false

        // 快速判断：检查是否包含Base64数据URI前缀
        if (data.startsWith("data:image/")) {
            return true
        }

        // 检查是否直接是Base64字符串 (无前缀)
        try {
            // 检查是否符合Base64编码特征
            if (data.length % 4 != 0) return false

            // 检查是否只包含Base64字符
            val base64Pattern = Regex("^[A-Za-z0-9+/]+={0,2}$")
            // 取较短的子串进行快速检查
            val checkSample = if (data.length > 100) data.substring(0, 100) else data
            if (!base64Pattern.matches(checkSample)) return false

            // 尝试解码前100个字符，看是否成功
            val decoded = Base64.decode(checkSample, Base64.DEFAULT)
            return decoded.size > 0
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 加载Base64编码的图片
     */
    private fun loadBase64Image(base64Data: String) {
        try {
            // 提取实际的Base64数据
            val actualBase64 = if (base64Data.contains(",")) {
                base64Data.substring(base64Data.indexOf(",") + 1)
            } else {
                base64Data
            }

            // 解码
            val imageBytes = Base64.decode(actualBase64, Base64.DEFAULT)

            // 使用Glide加载字节数组，添加圆角变换
            Glide.with(this)
                .load(imageBytes)
                .centerCrop() // 确保图片填充整个视图区域
                .transform(RoundedCorners(cornerRadius.dpToPx())) // 添加圆角变换
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(180))
                .error(R.drawable.ic_broken_image)
                .into(momentImage)

            // 显示图片容器
            imageCardContainer.visibility = View.VISIBLE
            Log.d(TAG, "Base64图片加载成功，数据长度: ${imageBytes.size}")
        } catch (e: Exception) {
            Log.e(TAG, "加载Base64图片失败: ${e.message}", e)
            imageCardContainer.visibility = View.GONE
        }
    }

    /**
     * 加载动态详情
     */
    private fun loadMomentDetail() {
        lifecycleScope.launch {
            try {
                val momentId = currentMomentId ?: return@launch
                val moment = momentRepository.getMomentById(momentId)

                if (moment != null) {
                    // 显示标题 - 使用TextFormatter格式化
                    if (moment.title.isNotEmpty()) {
                        TextFormatter.applyFormattingToTextView(momentTitle, moment.title)
                    } else {
                        // 提取第一行内容作为标题
                        val firstLine = moment.content.split("\n").firstOrNull() ?: "动态详情"
                        TextFormatter.applyFormattingToTextView(momentTitle, firstLine)
                    }

                    // 显示时间
                    momentTime.text = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                        .format(moment.timestamp)

                    // 显示内容 - 使用TextFormatter格式化
                    TextFormatter.applyFormattingToTextView(momentContent, moment.content)

                    // 设置内容可选择
                    momentContent.setTextIsSelectable(true)

                    // 显示图片(如果有)
                    if (!moment.imageUri.isNullOrEmpty()) {
                        try {
                            Log.d(TAG, "加载图片: imageUri=${moment.imageUri.take(100)}")

                            // 检查是否是Base64图片数据
                            if (isBase64Image(moment.imageUri)) {
                                // 加载Base64图片
                                loadBase64Image(moment.imageUri)
                            } else {
                                // 标准URI加载
                                Glide.with(this@MomentDetailActivity)
                                    .load(Uri.parse(moment.imageUri))
                                    .centerCrop() // 确保图片填充整个视图区域
                                    .transform(RoundedCorners(cornerRadius.dpToPx())) // 添加圆角变换
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .transition(DrawableTransitionOptions.withCrossFade(180))
                                    .error(R.drawable.ic_broken_image)
                                    .into(momentImage)

                                // 显示图片容器
                                imageCardContainer.visibility = View.VISIBLE
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "加载图片失败: ${e.message}", e)
                            imageCardContainer.visibility = View.GONE
                        }
                    } else {
                        imageCardContainer.visibility = View.GONE
                    }

                    // 根据动态类型设置头像和名称
                    if (moment.type.ordinal == 1) { // AI生成
                        // 设置AI头像
                        val aiAvatarUri = settingsManager.aiAvatarUri
                        if (aiAvatarUri != null && aiAvatarUri.isNotEmpty()) {
                            Glide.with(this@MomentDetailActivity)
                                .load(Uri.parse(aiAvatarUri))
                                .transition(DrawableTransitionOptions.withCrossFade(180))
                                .error(R.drawable.default_ai_avatar)
                                .into(aiAvatar)
                        } else {
                            aiAvatar.setImageResource(R.drawable.default_ai_avatar)
                        }

                        // 设置AI名称
                        aiName.text = settingsManager.aiName ?: "ChatGPT"
                    } else { // 用户上传
                        // 设置用户头像
                        val userAvatarUri = settingsManager.userAvatarUri
                        if (userAvatarUri != null && userAvatarUri.isNotEmpty()) {
                            Glide.with(this@MomentDetailActivity)
                                .load(Uri.parse(userAvatarUri))
                                .transition(DrawableTransitionOptions.withCrossFade(180))
                                .error(R.drawable.default_user_avatar)
                                .into(aiAvatar)
                        } else {
                            aiAvatar.setImageResource(R.drawable.default_user_avatar)
                        }

                        // 设置用户名称
                        aiName.text = settingsManager.userName ?: "我"
                    }

                    // 数据加载完成后，立即执行入场动画
                    if (!hasInitializedAnimations) {
                        // 使用短暂延迟确保UI已完全准备好
                        rootView.post {
                            startEntranceAnimations()
                            hasInitializedAnimations = true
                        }
                    }
                } else {
                    Toast.makeText(this@MomentDetailActivity,
                        "动态不存在或已被删除", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MomentDetailActivity,
                    "加载动态详情失败: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "加载动态详情失败", e)
            }
        }
    }

    /**
     * 开始入场动画序列
     */
    private fun startEntranceAnimations() {
        // 内容区域动画
        val contentFadeIn = ObjectAnimator.ofFloat(contentContainer, "alpha", 0f, 1f)
        val contentSlideUp = ObjectAnimator.ofFloat(contentContainer, "translationY", 50f, 0f)
        contentFadeIn.duration = 300
        contentSlideUp.duration = 400
        contentSlideUp.interpolator = DecelerateInterpolator(1.2f)

        val contentAnim = AnimatorSet()
        contentAnim.playTogether(contentFadeIn, contentSlideUp)

        // 头像动画
        val avatarFadeIn = ObjectAnimator.ofFloat(aiAvatar, "alpha", 0f, 1f)
        val avatarScaleX = ObjectAnimator.ofFloat(aiAvatar, "scaleX", 0.7f, 1f)
        val avatarScaleY = ObjectAnimator.ofFloat(aiAvatar, "scaleY", 0.7f, 1f)
        avatarFadeIn.duration = 250
        avatarScaleX.duration = 400
        avatarScaleY.duration = 400
        avatarScaleX.interpolator = OvershootInterpolator(2.5f)
        avatarScaleY.interpolator = OvershootInterpolator(2.5f)

        val avatarAnim = AnimatorSet()
        avatarAnim.playTogether(avatarFadeIn, avatarScaleX, avatarScaleY)

        // 名称动画
        val nameFadeIn = ObjectAnimator.ofFloat(aiName, "alpha", 0f, 1f)
        val nameSlideIn = ObjectAnimator.ofFloat(aiName, "translationX", -25f, 0f)
        nameFadeIn.duration = 250
        nameSlideIn.duration = 350
        nameSlideIn.interpolator = DecelerateInterpolator(1f)

        val nameAnim = AnimatorSet()
        nameAnim.playTogether(nameFadeIn, nameSlideIn)

        // 分享按钮动画
        val shareFadeIn = ObjectAnimator.ofFloat(shareButton, "alpha", 0f, 1f)
        val shareSlideIn = ObjectAnimator.ofFloat(shareButton, "translationX", 25f, 0f)
        shareFadeIn.duration = 250
        shareSlideIn.duration = 350
        shareSlideIn.interpolator = DecelerateInterpolator(1f)

        val shareAnim = AnimatorSet()
        shareAnim.playTogether(shareFadeIn, shareSlideIn)

        // 标题、时间和内容动画
        val titleFadeIn = ObjectAnimator.ofFloat(momentTitle, "alpha", 0f, 1f)
        val titleSlideUp = ObjectAnimator.ofFloat(momentTitle, "translationY", 15f, 0f)
        titleFadeIn.duration = 250
        titleSlideUp.duration = 350
        titleSlideUp.interpolator = DecelerateInterpolator(1f)

        val timeFadeIn = ObjectAnimator.ofFloat(momentTime, "alpha", 0f, 1f)
        val timeSlideUp = ObjectAnimator.ofFloat(momentTime, "translationY", 12f, 0f)
        timeFadeIn.duration = 250
        timeSlideUp.duration = 300
        timeSlideUp.interpolator = DecelerateInterpolator(1f)

        val contentTextFadeIn = ObjectAnimator.ofFloat(momentContent, "alpha", 0f, 1f)
        val contentTextSlideUp = ObjectAnimator.ofFloat(momentContent, "translationY", 20f, 0f)
        contentTextFadeIn.duration = 400
        contentTextSlideUp.duration = 500
        contentTextSlideUp.interpolator = DecelerateInterpolator(1f)

        val titleAnim = AnimatorSet()
        titleAnim.playTogether(titleFadeIn, titleSlideUp)

        val timeAnim = AnimatorSet()
        timeAnim.playTogether(timeFadeIn, timeSlideUp)

        val contentTextAnim = AnimatorSet()
        contentTextAnim.playTogether(contentTextFadeIn, contentTextSlideUp)

        // 创建动画集合
        val allAnimations = AnimatorSet()

        // 图片动画
        if (imageCardContainer.visibility == View.VISIBLE) {
            val imageFadeIn = ObjectAnimator.ofFloat(imageCardContainer, "alpha", 0f, 1f)
            val imageScaleX = ObjectAnimator.ofFloat(imageCardContainer, "scaleX", 0.97f, 1f)
            val imageScaleY = ObjectAnimator.ofFloat(imageCardContainer, "scaleY", 0.97f, 1f)
            imageFadeIn.duration = 400
            imageScaleX.duration = 500
            imageScaleY.duration = 500

            val imageAnim = AnimatorSet()
            imageAnim.playTogether(imageFadeIn, imageScaleX, imageScaleY)

            // 所有动画更快地并行执行
            allAnimations.playTogether(
                contentAnim,
                avatarAnim,
                nameAnim,
                shareAnim,
                AnimatorSet().apply {
                    // 内容元素按照视觉重要性依次显示
                    playSequentially(
                        titleAnim,
                        AnimatorSet().apply {
                            startDelay = 50
                            play(timeAnim)
                        },
                        AnimatorSet().apply {
                            startDelay = 50
                            play(contentTextAnim)
                        },
                        AnimatorSet().apply {
                            startDelay = 50
                            play(imageAnim)
                        }
                    )
                    startDelay = 50
                }
            )
        } else {
            // 无图片时的动画流程
            allAnimations.playTogether(
                contentAnim,
                avatarAnim,
                nameAnim,
                shareAnim,
                AnimatorSet().apply {
                    // 内容元素按照视觉重要性依次显示
                    playSequentially(
                        titleAnim,
                        AnimatorSet().apply {
                            startDelay = 50
                            play(timeAnim)
                        },
                        AnimatorSet().apply {
                            startDelay = 70
                            play(contentTextAnim)
                        }
                    )
                    startDelay = 50
                }
            )
        }

        allAnimations.start()
    }

    /**
     * 设置图片交互效果
     */
    private fun setupImageInteraction() {
        momentImage.setOnClickListener { view ->
            // 添加触觉反馈
            HapticUtils.performViewHapticFeedback(view)

            // 添加图片预览放大效果
            animateImagePreview(view as ImageView)
        }
    }

    /**
     * 图片预览放大动画
     */
    private fun animateImagePreview(imageView: ImageView) {
        // 记录原始状态
        val originalScaleX = imageView.scaleX
        val originalScaleY = imageView.scaleY
        val originalElevation = ViewCompat.getElevation(imageView)

        // 设置高层级
        ViewCompat.setElevation(imageView, 10f)

        // 创建放大动画
        val scaleUpX = ObjectAnimator.ofFloat(imageView, "scaleX", originalScaleX, 1.03f)
        val scaleUpY = ObjectAnimator.ofFloat(imageView, "scaleY", originalScaleY, 1.03f)
        scaleUpX.duration = 180
        scaleUpY.duration = 180
        scaleUpX.interpolator = DecelerateInterpolator(1.3f)
        scaleUpY.interpolator = DecelerateInterpolator(1.3f)

        // 创建缩小动画
        val scaleDownX = ObjectAnimator.ofFloat(imageView, "scaleX", 1.03f, originalScaleX)
        val scaleDownY = ObjectAnimator.ofFloat(imageView, "scaleY", 1.03f, originalScaleY)
        scaleDownX.duration = 200
        scaleDownY.duration = 200
        scaleDownX.interpolator = OvershootInterpolator(1.3f)
        scaleDownY.interpolator = OvershootInterpolator(1.3f)

        // 创建序列
        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY)

        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY)

        // 执行序列
        val sequence = AnimatorSet()
        sequence.playSequentially(scaleUp, scaleDown)

        // 动画结束后恢复原始高度
        sequence.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                ViewCompat.setElevation(imageView, originalElevation)
            }
        })

        sequence.start()
    }

    /**
     * 分享动态内容
     */
    private fun shareMoment() {
        lifecycleScope.launch {
            try {
                val momentId = currentMomentId ?: return@launch
                val moment = momentRepository.getMomentById(momentId)

                if (moment != null) {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "text/plain"

                    // 分享原始文本，不带格式
                    val title = moment.title
                    val content = moment.content
                    val shareText = "$title\n\n$content"

                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
                    startActivity(Intent.createChooser(shareIntent, "分享动态"))
                }
            } catch (e: Exception) {
                Toast.makeText(this@MomentDetailActivity,
                    "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

package com.example.chatapp.ui
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.chatapp.R
import com.example.chatapp.data.Moment
import android.view.ContextThemeWrapper
import com.example.chatapp.repository.MomentRepository
import com.example.chatapp.utils.HapticUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.util.UUID

class MomentsActivity : AppCompatActivity(), MomentAdapter.OnMomentClickListener {
    private lateinit var momentsRecyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var addMomentFab: FloatingActionButton
    private lateinit var adapter: MomentAdapter
    private lateinit var momentRepository: MomentRepository
    private var isAnimating = false
    // 标题视图引用
    private lateinit var titleTextView: TextView
    // 按钮动画状态标记
    private var isTitlePressed = false
    // 标记是否需要执行卡片动画
    private var needsCardAnimation = true
    // 防止动画重复执行的标志
    private var hasAnimatedEmptyView = false
    // 页面状态标记
    private enum class PageState { LOADING, EMPTY, CONTENT }
    private var currentState = PageState.LOADING

    // 图片选择结果
    private var selectedImageUri: Uri? = null
    // 图片选择启动器
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            showSelectedImage()
        }
    }
    // 添加动态对话框
    private var addMomentDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moments)

        // 获取根视图
        val rootView = findViewById<View>(android.R.id.content)
        // 设置透视投影中心点
        rootView.cameraDistance = 10000f * resources.displayMetrics.density

        // 初始化仓库
        momentRepository = MomentRepository(this)

        // 初始化视图
        initViews()

        // 设置Toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        // 移除返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowHomeEnabled(false)
        // 清除标题，使用自定义标题
        supportActionBar?.title = ""

        // 自定义标题设置
        setupCustomTitle()

        // 自动生成AI日记（如果今天还没有）
        generateDailyAIDiaryIfNeeded()

        // 设置标题长按事件
        setupTitleLongPress()

        // 检查是否是从3D翻转动画启动的
        val fromFlip = intent.getBooleanExtra("FROM_3D_FLIP", false)
        if (fromFlip) {
            // 初始设置MomentsActivity为从右侧翻入的状态
            rootView.rotationY = 90f
            rootView.scaleX = 0.8f
            rootView.scaleY = 0.8f
            rootView.alpha = 0.5f
            // 设置硬件加速
            rootView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            // 延迟一小段时间开始翻入动画，等待MainActivity的翻出动画进行
            rootView.postDelayed({
                // 创建翻入动画
                val rotationYIn = ObjectAnimator.ofFloat(rootView, "rotationY", 90f, 0f)
                rotationYIn.duration = 400
                rotationYIn.interpolator = DecelerateInterpolator(1.5f)
                // 添加放大和淡入动画
                val scaleInX = ObjectAnimator.ofFloat(rootView, "scaleX", 0.8f, 1.0f)
                val scaleInY = ObjectAnimator.ofFloat(rootView, "scaleY", 0.8f, 1.0f)
                val alphaIn = ObjectAnimator.ofFloat(rootView, "alpha", 0.5f, 1.0f)
                // 组合动画
                val animSetIn = AnimatorSet()
                animSetIn.playTogether(rotationYIn, scaleInX, scaleInY, alphaIn)
                // 设置翻入动画结束监听器
                animSetIn.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // 动画结束后清理状态
                        rootView.setLayerType(View.LAYER_TYPE_NONE, null)
                        // 延迟执行子元素动画
                        Handler(Looper.getMainLooper()).postDelayed({
                            // 入场序列动画，不包含标题动画
                            animateUIElements()
                        }, 100)
                    }
                })
                // 开始翻入动画
                animSetIn.start()
            }, 150) // 延迟150ms，等待MainActivity的动画进行到一半左右
        } else {
            // 非翻转入场时，直接执行常规入场动画
            rootView.alpha = 0f
            rootView.translationY = 50f
            rootView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator(1.2f))
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // 入场序列动画，不包含标题动画
                        animateUIElements()
                    }
                })
                .start()
        }

        // 加载动态
        loadMoments()
    }

    /**
     * 设置自定义标题，实现加粗样式和点击缩放效果
     */
    private fun setupCustomTitle() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        // 创建自定义TextView作为标题
        titleTextView = TextView(this).apply {
            text = "动态"
            textSize = 24f
            typeface = Typeface.create(typeface, Typeface.BOLD) // 设置加粗
            setTextColor(ContextCompat.getColor(this@MomentsActivity, R.color.text_primary))
            // 设置内边距
            val padding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
            ).toInt()
            setPadding(padding, padding, padding, padding)
            // 设置触摸监听器，实现缩放动效
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 按下时触发动画和反馈
                        startTitlePressAnimation()
                        // 提供即时触觉反馈
                        HapticUtils.performHapticFeedback(this@MomentsActivity, false)
                        isTitlePressed = true
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 手指抬起时的处理
                        if (isTitlePressed) {
                            val isInBounds = event.x >= 0 && event.x <= view.width &&
                                    event.y >= 0 && event.y <= view.height
                            startTitleReleaseAnimation()
                            // 如果在按钮区域内抬起，执行返回动画
                            if (isInBounds) {
                                // 延迟一小段时间让动画效果完成
                                view.postDelayed({
                                    animateBackTo3DFlip() // 执行返回主页面动画
                                }, 150)
                            }
                            isTitlePressed = false
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // 取消操作
                        if (isTitlePressed) {
                            startTitleReleaseAnimation()
                            isTitlePressed = false
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        // 清空并设置自定义标题视图
        toolbar.removeAllViews()
        // 使用START对齐而不是CENTER对齐
        toolbar.addView(titleTextView, Toolbar.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.START
        ))
    }

    /**
     * 执行标题按下动画效果
     */
    private fun startTitlePressAnimation() {
        // 缩小动画
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(titleTextView, "scaleX", 1f, 0.65f),
                ObjectAnimator.ofFloat(titleTextView, "scaleY", 1f, 0.65f)
            )
            // 减少持续时间使动画更快
            duration = 80
            // 使用加速插值器
            interpolator = AccelerateInterpolator(2.0f)
        }
        // 改变文字颜色为主题色
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        titleTextView.setTextColor(primaryColor)
        // 启动动画
        scaleDown.start()
    }

    /**
     * 执行标题释放动画效果，带回弹效果
     */
    private fun startTitleReleaseAnimation() {
        // 恢复原来的文字颜色
        val textPrimaryColor = ContextCompat.getColor(this, R.color.text_primary)
        // 创建带回弹效果的动画
        val animatorSet = AnimatorSet()
        // 第一阶段：快速回弹超过原始大小
        val bounce = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(titleTextView, "scaleX", 0.65f, 1.2f),
                ObjectAnimator.ofFloat(titleTextView, "scaleY", 0.65f, 1.2f)
            )
            duration = 150
            interpolator = DecelerateInterpolator(1.5f)
        }
        // 第二阶段：稳定回到原始大小
        val settle = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(titleTextView, "scaleX", 1.2f, 1.0f),
                ObjectAnimator.ofFloat(titleTextView, "scaleY", 1.2f, 1.0f)
            )
            duration = 150
            interpolator = OvershootInterpolator(3.0f)
        }
        // 合并动画序列
        animatorSet.playSequentially(bounce, settle)
        // 在第一阶段开始时恢复颜色
        bounce.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                titleTextView.setTextColor(textPrimaryColor)
            }
        })
        // 启动动画序列
        animatorSet.start()
    }

    /**
     * 入场时为UI元素添加序列动画
     */
    private fun animateUIElements() {
        // FAB动画 - 旋转入场
        addMomentFab.apply {
            visibility = View.INVISIBLE
            postDelayed({
                visibility = View.VISIBLE
                scaleX = 0f
                scaleY = 0f
                rotation = -45f
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotation(0f)
                    .setDuration(500)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }, 300)
        }
    }

    /**
     * 为空状态视图添加入场动画
     */
    private fun animateEmptyView() {
        // 防止重复执行
        if (hasAnimatedEmptyView) return
        hasAnimatedEmptyView = true

        val image = emptyView.findViewById<ImageView>(R.id.emptyImage)
        val title = emptyView.findViewById<View>(R.id.emptyTitle)
        val subtitle = emptyView.findViewById<View>(R.id.emptySubtitle)

        // 重置初始状态
        image?.apply {
            alpha = 0f
            scaleX = 0.5f
            scaleY = 0.5f
        }
        title?.apply {
            alpha = 0f
            translationY = 50f
        }
        subtitle?.apply {
            alpha = 0f
            translationY = 50f
        }

        // 图标动画
        image?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(600)
            ?.setInterpolator(OvershootInterpolator(1.5f))
            ?.start()

        // 标题动画
        title?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setDuration(500)
            ?.setStartDelay(200)
            ?.setInterpolator(DecelerateInterpolator(1.2f))
            ?.start()

        // 副标题动画
        subtitle?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setDuration(500)
            ?.setStartDelay(300)
            ?.setInterpolator(DecelerateInterpolator(1.2f))
            ?.start()
    }

    /**
     * 设置标题栏长按监听，返回聊天页面
     */
    private fun setupTitleLongPress() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        var titleLongPressStartTime: Long = 0
        val TITLE_LONG_PRESS_DURATION = 150L // 长按时间阈值，单位毫秒

        toolbar.setOnLongClickListener {
            // 记录长按开始时间
            titleLongPressStartTime = System.currentTimeMillis()
            HapticUtils.performHapticFeedback(this) // 执行震动反馈
            true
        }

        toolbar.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // 记录按下时间
                    titleLongPressStartTime = System.currentTimeMillis()
                    false
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    // 计算按下持续时间
                    val pressDuration = System.currentTimeMillis() - titleLongPressStartTime
                    if (pressDuration >= TITLE_LONG_PRESS_DURATION && !isAnimating) {
                        // 长按时间超过阈值，执行翻转动画
                        animateBackTo3DFlip()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    /**
     * 执行返回主页面的3D翻转动画
     */
    private fun animateBackTo3DFlip() {
        if (isAnimating) return
        isAnimating = true

        // 执行强震动反馈
        HapticUtils.performHapticFeedback(this, true)

        // 获取根视图
        val rootView = findViewById<View>(android.R.id.content)

        // 设置硬件加速来提高性能和效果
        rootView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // 设置透视投影中心点
        rootView.cameraDistance = 10000f * resources.displayMetrics.density

        // 创建卡片翻出动画
        val rotationY = ObjectAnimator.ofFloat(rootView, "rotationY", 0f, 90f)
        rotationY.duration = 400
        rotationY.interpolator = AccelerateDecelerateInterpolator()

        // 添加缩小和淡出动画
        val scaleOutX = ObjectAnimator.ofFloat(rootView, "scaleX", 1.0f, 0.8f)
        val scaleOutY = ObjectAnimator.ofFloat(rootView, "scaleY", 1.0f, 0.8f)
        val alphaOut = ObjectAnimator.ofFloat(rootView, "alpha", 1.0f, 0.5f)

        // 组合动画
        val animSetOut = AnimatorSet()
        animSetOut.playTogether(rotationY, scaleOutX, scaleOutY, alphaOut)

        // 设置动画结束监听器
        animSetOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 动画结束后返回主页面
                finish()
                overridePendingTransition(0, 0)
                // 重置标志
                isAnimating = false
            }
        })

        // 开始翻出动画
        animSetOut.start()
    }

    /**
     * 初始化视图
     */
    private fun initViews() {
        momentsRecyclerView = findViewById(R.id.momentsRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        addMomentFab = findViewById(R.id.addMomentFab)

        // 设置初始状态
        emptyView.visibility = View.GONE // 明确设置初始状态为不可见
        momentsRecyclerView.visibility = View.GONE // 内容区也先隐藏

        // 隐藏FAB，让它可以执行入场动画
        addMomentFab.visibility = View.INVISIBLE

        // 设置RecyclerView
        momentsRecyclerView.layoutManager = LinearLayoutManager(this)

        // 使用现有的适配器构造函数
        adapter = MomentAdapter(
            this,
            onCopyClick = { copyToClipboard(it) },
            onShareClick = { shareContent(it) },
            onMoreClick = { moment, view -> showMoreOptions(moment, view) },
            onMomentClick = this
        )

        momentsRecyclerView.adapter = adapter

        // 添加滚动监听，实现滚动效果增强和FAB隐藏/显示
        setupScrollListener()

        // 设置添加按钮
        addMomentFab.setOnClickListener { view ->
            // 执行触觉反馈
            HapticUtils.performViewHapticFeedback(view)
            // 添加缩放动画
            animateViewPress(view)
            // 延迟执行，让动画有时间完成
            view.postDelayed({
                showAddMomentDialog()
            }, 200)
        }
    }

    /**
     * 为所有卡片同时添加顺序滑入动画
     */
    private fun animateAllCards() {
        if (!needsCardAnimation) return

        // 获取RecyclerView中当前的子视图数量
        val childCount = momentsRecyclerView.childCount

        // 如果没有子视图，不执行动画
        if (childCount == 0) {
            // 如果没有卡片，检查是否为数据加载问题，延迟再试
            Handler(Looper.getMainLooper()).postDelayed({
                if (momentsRecyclerView.childCount > 0 && needsCardAnimation) {
                    animateAllCards()
                }
            }, 200)
            return
        }

        // 获取所有可见卡片的ViewHolder
        val layoutManager = momentsRecyclerView.layoutManager as LinearLayoutManager
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        // 如果不可见，不执行动画
        if (firstVisible < 0 || lastVisible < 0) return

        // 记录动画已执行
        needsCardAnimation = false

        // 为每个卡片设置动画
        for (i in firstVisible..lastVisible) {
            val itemView = layoutManager.findViewByPosition(i) ?: continue

            // 重置视图状态
            itemView.alpha = 0f
            itemView.translationX = itemView.width.toFloat()

            // 计算延迟时间
            val delay = (i - firstVisible) * 100L

            // 创建并执行动画
            itemView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(350)
                .setStartDelay(delay)
                .setInterpolator(DecelerateInterpolator(1.2f))
                .start()
        }
    }

    /**
     * 设置RecyclerView的滚动监听器
     */
    private fun setupScrollListener() {
        momentsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // 当滚动向下时，缩小并隐藏FAB
                if (dy > 15 && addMomentFab.isShown) {
                    addMomentFab.animate()
                        .scaleX(0f)
                        .scaleY(0f)
                        .setDuration(200)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                addMomentFab.visibility = View.INVISIBLE
                            }
                        })
                        .start()
                }
                // 当滚动向上时，显示并放大FAB
                else if (dy < -15 && !addMomentFab.isShown) {
                    addMomentFab.visibility = View.VISIBLE
                    addMomentFab.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .setListener(null)
                        .start()
                }

                // 根据滚动添加视差效果
                if (Math.abs(dy) > 20) {
                    val alpha = 0.9f - Math.min(Math.abs(dy) / 1000f, 0.2f)
                    recyclerView.alpha = alpha
                    recyclerView.animate().alpha(1f).setDuration(150).start()
                }
            }
        })
    }

    /**
     * 重置动画状态，用于重新加载时
     */
    private fun resetAnimationState() {
        needsCardAnimation = true
        // 当用户刷新或重新加载页面时重置empty状态
        if (currentState == PageState.EMPTY) {
            hasAnimatedEmptyView = false
        }
    }

    /**
     * 通用的视图按压动画效果
     */
    private fun animateViewPress(view: View) {
        // 创建缩放动画
        val scaleDown = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f)
        val scaleDownY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f)
        val scaleUp = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f)
        val scaleUpY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f)

        // 第一阶段动画：缩小
        val downAnim = ObjectAnimator.ofPropertyValuesHolder(view, scaleDown, scaleDownY)
        downAnim.duration = 100
        downAnim.interpolator = AccelerateDecelerateInterpolator()

        // 第二阶段动画：弹回
        val upAnim = ObjectAnimator.ofPropertyValuesHolder(view, scaleUp, scaleUpY)
        upAnim.duration = 200
        upAnim.interpolator = OvershootInterpolator(2f)

        // 组合动画
        val sequence = AnimatorSet()
        sequence.playSequentially(downAnim, upAnim)
        sequence.start()
    }

    /**
     * 加载动态数据
     */
    private fun loadMoments() {
        // 更新页面状态为加载中
        updatePageState(PageState.LOADING)

        // 重置动画状态
        resetAnimationState()

        lifecycleScope.launch {
            momentRepository.getAllMoments().collect { moments ->
                adapter.submitList(moments)

                // 根据列表是否为空更新页面状态
                if (moments.isEmpty()) {
                    // 更新为空状态
                    updatePageState(PageState.EMPTY)
                } else {
                    // 更新为内容状态
                    updatePageState(PageState.CONTENT)

                    // 延迟执行卡片动画，确保卡片已经布局完成
                    Handler(Looper.getMainLooper()).postDelayed({
                        animateAllCards()
                    }, 100)
                }
            }
        }
    }

    /**
     * 更新页面状态，管理视图显示和动画
     */
    private fun updatePageState(newState: PageState) {
        // 如果状态没有改变，不处理
        if (currentState == newState) return

        // 更新当前状态
        currentState = newState

        when (newState) {
            PageState.LOADING -> {
                // 加载状态
                emptyView.visibility = View.GONE
                momentsRecyclerView.visibility = View.GONE
            }
            PageState.EMPTY -> {
                // 空状态
                emptyView.visibility = View.VISIBLE
                momentsRecyclerView.visibility = View.GONE

                // 执行空视图动画，但防止重复执行
                if (!hasAnimatedEmptyView) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        animateEmptyView()
                    }, 100)
                }
            }
            PageState.CONTENT -> {
                // 内容状态
                emptyView.visibility = View.GONE
                momentsRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun generateDailyAIDiaryIfNeeded() {
        lifecycleScope.launch {
            try {
                // 检查今天是否已生成AI日记
                val hasDiary = momentRepository.hasTodayAIDiary()
                if (!hasDiary) {
                    // 生成今日AI日记
                    momentRepository.generateAIDiary()
                    // 刷新列表
                    loadMoments()
                }
            } catch (e: Exception) {
                // 出错时忽略，不影响主功能
            }
        }
    }

    private fun showMoreOptions(moment: Moment, anchorView: View) {
        // 执行触觉反馈
        HapticUtils.performViewHapticFeedback(anchorView)

        // 使用专用的主题
        val wrapper = ContextThemeWrapper(this, R.style.AppTheme_PopupOverlay)

        // 创建弹出菜单并设置位置偏移
        val popup = PopupMenu(wrapper, anchorView, Gravity.END)
        popup.menuInflater.inflate(R.menu.moment_options_menu, popup.menu)

        // 尝试强制显示图标
        try {
            val fieldMPopup = PopupMenu::class.java.getDeclaredField("mPopup")
            fieldMPopup.isAccessible = true
            val mPopup = fieldMPopup.get(popup)

            // 强制显示图标
            mPopup.javaClass
                .getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                .invoke(mPopup, true)

            // 尝试设置宽度
            try {
                mPopup.javaClass
                    .getDeclaredMethod("setWidth", Int::class.java)
                    .invoke(mPopup, resources.getDimensionPixelSize(R.dimen.popup_menu_width))
            } catch (e: Exception) {
                // 忽略宽度设置错误
            }
        } catch (e: Exception) {
            // 部分设备可能不支持，忽略错误
        }

        // 设置菜单项点击监听
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_copy -> {
                    copyToClipboard(moment.content)
                    true
                }
                R.id.menu_share -> {
                    shareContent(moment.content)
                    true
                }
                R.id.menu_delete -> {
                    showDeleteConfirmation(moment)
                    true
                }
                else -> false
            }
        }

        // 显示菜单
        popup.show()
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmation(moment: Moment) {
        // 创建使用自定义样式的底部弹出对话框
        val dialog = Dialog(this, R.style.BottomSheetDialogTheme).apply {
            setContentView(R.layout.dialog_delete_confirmation)
            window?.apply {
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
                setGravity(Gravity.BOTTOM) // 确保从底部显示
                attributes.windowAnimations = R.style.BottomSheetAnimation // 添加动画
            }
        }

        // 获取对话框视图
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)
        val btnDelete = dialog.findViewById<Button>(R.id.btnDelete)

        // 设置按钮点击事件
        btnDelete.setOnClickListener { view ->
            // 添加震动反馈
            HapticUtils.performViewHapticFeedback(view, true)
            // 应用增强的缩放动画效果
            animateViewPress(view)
            // 延迟执行删除操作，让动画有时间完成
            view.postDelayed({
                // 执行删除操作
                deleteMoment(moment.id)
                dialog.dismiss()
            }, 200)
        }

        btnCancel.setOnClickListener { view ->
            // 添加震动反馈
            HapticUtils.performViewHapticFeedback(view, false)
            // 应用增强的缩放动画效果
            animateViewPress(view)
            // 延迟关闭对话框，让动画有时间完成
            view.postDelayed({
                dialog.dismiss()
            }, 200)
        }

        // 显示对话框
        dialog.show()
    }

    private fun showAddMomentDialog() {
        // 创建对话框
        addMomentDialog = Dialog(this, R.style.BottomSheetDialogTheme).apply {
            setContentView(R.layout.dialog_add_moment)
            window?.apply {
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
                setGravity(Gravity.BOTTOM) // 确保从底部显示
                attributes.windowAnimations = R.style.BottomSheetAnimation // 添加动画
            }
        }

        // 获取对话框视图
        val dialog = addMomentDialog!!
        val titleEditText = dialog.findViewById<EditText>(R.id.titleEditText)
        val contentEditText = dialog.findViewById<EditText>(R.id.contentEditText)
        val selectImageButton = dialog.findViewById<MaterialButton>(R.id.selectImageButton)
        val selectedImagePreview = dialog.findViewById<ImageView>(R.id.selectedImagePreview)
        val removeImageButton = dialog.findViewById<ImageButton>(R.id.removeImageButton)
        val confirmButton = dialog.findViewById<Button>(R.id.confirmButton)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)

        // 重置图片选择状态
        selectedImageUri = null
        selectedImagePreview.visibility = View.GONE
        removeImageButton.visibility = View.GONE

        // 设置按钮点击事件
        selectImageButton.setOnClickListener { view ->
            // 触觉反馈
            HapticUtils.performViewHapticFeedback(view)
            // 按钮动画
            animateViewPress(view)
            view.postDelayed({
                pickImage.launch("image/*")
            }, 200)
        }

        removeImageButton.setOnClickListener { view ->
            // 触觉反馈
            HapticUtils.performViewHapticFeedback(view)
            // 图片缩小淡出动画
            selectedImagePreview.animate()
                .scaleX(0f)
                .scaleY(0f)
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(AccelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        selectedImageUri = null
                        selectedImagePreview.visibility = View.GONE
                        removeImageButton.visibility = View.GONE
                        // 重置图片属性以便下次显示
                        selectedImagePreview.scaleX = 1f
                        selectedImagePreview.scaleY = 1f
                        selectedImagePreview.alpha = 1f
                    }
                })
                .start()
        }

        confirmButton.setOnClickListener { view ->
            val title = titleEditText.text.toString().trim()
            val content = contentEditText.text.toString().trim()

            if (content.isEmpty()) {
                // 添加输入框震动效果
                val shake = ObjectAnimator.ofFloat(contentEditText, "translationX", 0f, -15f, 15f, -10f, 10f, -5f, 5f, 0f)
                shake.duration = 500
                shake.start()
                // 显示错误提示
                Toast.makeText(this, "请输入动态内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 触觉反馈
            HapticUtils.performViewHapticFeedback(view)
            // 按钮动画
            animateViewPress(view)

            // 处理图片
            val finalImageUri = if (selectedImageUri != null) {
                // 复制图片到应用内部存储
                saveImageToInternalStorage(selectedImageUri!!)
            } else {
                null
            }

            view.postDelayed({
                // 添加动态
                addUserMoment(title, content, finalImageUri)
                // 关闭对话框
                dialog.dismiss()
            }, 200)
        }

        cancelButton.setOnClickListener { view ->
            // 触觉反馈
            HapticUtils.performViewHapticFeedback(view, false)
            // 按钮动画
            animateViewPress(view)
            view.postDelayed({
                dialog.dismiss()
            }, 200)
        }

        // 为对话框中的元素添加入场动画
        dialog.window?.decorView?.findViewById<View>(android.R.id.content)?.let {
            animateDialogContent(it)
        }

        // 显示对话框
        dialog.show()
    }

    /**
     * 为对话框内容添加动画
     */
    private fun animateDialogContent(dialogContent: View) {
        // 延迟执行，确保对话框已显示
        Handler(Looper.getMainLooper()).postDelayed({
            // 为每个直接子View添加动画
            if (dialogContent is ViewGroup) {
                val count = dialogContent.childCount
                for (i in 0 until count) {
                    val child = dialogContent.getChildAt(i)
                    // 设置初始状态
                    child.alpha = 0f
                    child.translationY = 50f
                    // 创建动画
                    child.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(400)
                        .setStartDelay((i * 50).toLong()) // 错开时间实现序列动画
                        .setInterpolator(DecelerateInterpolator(1.2f))
                        .start()
                }
            }
        }, 50)
    }

    private fun showSelectedImage() {
        val dialog = addMomentDialog ?: return
        val selectedImagePreview = dialog.findViewById<ImageView>(R.id.selectedImagePreview)
        val removeImageButton = dialog.findViewById<ImageButton>(R.id.removeImageButton)

        if (selectedImageUri != null) {
            // 先设置为不可见，以便添加动画
            selectedImagePreview.visibility = View.VISIBLE
            selectedImagePreview.alpha = 0f
            selectedImagePreview.scaleX = 0.8f
            selectedImagePreview.scaleY = 0.8f
            removeImageButton.visibility = View.VISIBLE
            removeImageButton.alpha = 0f

            // 加载图片
            Glide.with(this)
                .load(selectedImageUri)
                .centerCrop()
                .into(selectedImagePreview)

            // 图片淡入放大动画
            selectedImagePreview.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator(1.2f))
                .start()

            // 移除按钮淡入动画
            removeImageButton.animate()
                .alpha(1f)
                .setDuration(400)
                .setStartDelay(200)
                .start()
        } else {
            selectedImagePreview.visibility = View.GONE
            removeImageButton.visibility = View.GONE
        }
    }

    private fun saveImageToInternalStorage(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val fileName = "moment_image_${UUID.randomUUID()}.jpg"
        val outputFile = getFileStreamPath(fileName)

        try {
            FileOutputStream(outputFile).use { output ->
                inputStream?.copyTo(output)
            }
            return Uri.fromFile(outputFile).toString()
        } catch (e: Exception) {
            Toast.makeText(this, "保存图片失败", Toast.LENGTH_SHORT).show()
            return ""
        } finally {
            inputStream?.close()
        }
    }

    private fun addUserMoment(title: String, content: String, imageUri: String?) {
        lifecycleScope.launch {
            try {
                momentRepository.addUserMoment(content, imageUri, title)
                // 重置卡片动画状态，让新添加的项也有动画
                needsCardAnimation = true
                // 刷新列表
                loadMoments()
                // 成功提示动画
                showSuccessToast("动态发布成功")
            } catch (e: Exception) {
                Toast.makeText(this@MomentsActivity, "动态发布失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteMoment(momentId: String) {
        lifecycleScope.launch {
            try {
                momentRepository.deleteMoment(momentId)
                // 重置卡片动画状态
                needsCardAnimation = true
                // 刷新列表
                loadMoments()
                // 成功提示动画
                showSuccessToast("动态已删除")
            } catch (e: Exception) {
                Toast.makeText(this@MomentsActivity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 显示带动画的成功提示
     */
    private fun showSuccessToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        // 显示提示
        toast.show()
        // 触觉反馈
        HapticUtils.performHapticFeedback(this)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("动态内容", text)
        clipboard.setPrimaryClip(clip)
        // 显示带动画的提示
        showSuccessToast("已复制到剪贴板")
        // 触觉反馈
        HapticUtils.performHapticFeedback(this)
    }

    private fun shareContent(text: String) {
        // 触觉反馈
        HapticUtils.performHapticFeedback(this)

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }

        // 添加过渡动画
        startActivity(Intent.createChooser(sendIntent, "分享动态"))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    // 实现OnMomentClickListener接口的方法
    override fun onMomentClick(moment: Moment) {
        // 触觉反馈
        HapticUtils.performHapticFeedback(this)
        // 打开动态详情页面
        MomentDetailActivity.start(this, moment.id)
        // 添加过渡动画
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    // 重写返回键处理，使用自定义3D翻转返回动画
    override fun onBackPressed() {
        animateBackTo3DFlip()
    }

    companion object {
        fun start(activity: Activity) {
            val intent = Intent(activity, MomentsActivity::class.java)
            activity.startActivity(intent)
            // 不要使用自定义过渡动画，让前一个活动的动画完成
            activity.overridePendingTransition(0, 0)
        }
    }
}

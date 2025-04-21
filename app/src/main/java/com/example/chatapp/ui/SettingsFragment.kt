package com.example.chatapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import com.example.chatapp.R
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.utils.HapticUtils
import com.google.android.material.card.MaterialCardView

/**
 * 统一的设置页面Fragment
 */
class SettingsFragment : Fragment(), BaseSettingsSubFragment.NavigationCallback {

    private val TAG = "SettingsFragment"
    private lateinit var settingsManager: SettingsManager

    // 视图引用
    private var usernameText: TextView? = null
    private var modelValueText: TextView? = null
    private var personaValueText: TextView? = null
    private var webSearchValueText: TextView? = null
    private var titleText: TextView? = null
    private var backIcon: ImageView? = null
    private var settingsContainer: ViewGroup? = null

    // 主动消息相关视图
    private var proactiveMessagesValueText: TextView? = null

    // 标题控制标志
    private var useExternalTitle = false
    private var useStandaloneMode = false
    private var currentTitle = "设置"

    // 实例变量
    private var isHandlingBackNavigation = false
    private var isAnimationRunning = false

    // 卡片引用列表，用于动画
    private val settingCards = mutableListOf<View>()

    // 回调接口
    interface SettingsListener {
        fun onSettingsSaved()
        fun onTitleChanged(title: String)
    }

    /**
     * 子页面打开监听器接口
     */
    interface SubpageOpenListener {
        fun onSubpageOpen(fragment: BaseSettingsSubFragment)
    }

    // 回调实例
    private var listener: SettingsListener? = null

    // 子页面打开监听器
    private var subpageOpenListener: SubpageOpenListener? = null

    // 导航回调
    private var navigationCallback: BaseSettingsSubFragment.NavigationCallback? = null

    // 当前显示的子Fragment
    private var currentSubFragment: BaseSettingsSubFragment? = null

    // 图片选择结果处理
    private val userImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // 保存URI并更新UI
                settingsManager.userAvatarUri = uri.toString()
                Toast.makeText(requireContext(), "头像已更新", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            useExternalTitle = it.getBoolean("useExternalTitle", false)
            useStandaloneMode = it.getBoolean("useStandaloneMode", false)
        }
    }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        if (context is SettingsListener) {
            listener = context
        } else if (parentFragment is SettingsListener) {
            listener = parentFragment as SettingsListener
        }

        // 初始化设置管理器
        settingsManager = SettingsManager(context)
    }

    /**
     * 设置独立模式
     */
    fun setUseStandaloneMode(standalone: Boolean) {
        useStandaloneMode = standalone
    }

    /**
     * 设置设置监听器
     */
    fun setSettingsListener(listener: SettingsListener) {
        this.listener = listener
    }

    /**
     * 设置子页面打开监听器
     */
    fun setSubpageOpenListener(listener: SubpageOpenListener) {
        this.subpageOpenListener = listener
    }

    /**
     * 设置导航回调
     */
    fun setNavigationCallback(callback: BaseSettingsSubFragment.NavigationCallback) {
        this.navigationCallback = callback
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 根据标志选择不同的布局文件
        return if (useStandaloneMode) {
            // 使用全屏独立布局
            inflater.inflate(R.layout.fragment_settings_fullscreen, container, false)
        } else if (useExternalTitle) {
            // 没有标题的布局，用于底部菜单
            inflater.inflate(R.layout.settings_content, container, false)
        } else {
            // 带标题的布局，用于独立Fragment
            inflater.inflate(R.layout.card_style_settings_layout, container, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 延迟动画以等待视图完全绘制
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        // 初始化视图引用
        initViews(view)

        // 设置返回按钮点击事件
        backIcon?.setOnClickListener {
            // 添加按钮动画效果
            animateButtonPress(it)

            // 添加触觉反馈
            HapticUtils.performViewHapticFeedback(it)

            if (childFragmentManager.backStackEntryCount > 0) {
                handleBackPressed()
            } else {
                if (navigationCallback != null) {
                    navigationCallback?.navigateBack()
                } else {
                    activity?.onBackPressed()
                }
            }
        }

        // 在独立模式下，手动加载设置内容到容器中
        if (useStandaloneMode && settingsContainer != null) {
            // 加载设置内容到独立模式的容器中
            val settingsContentView = layoutInflater.inflate(R.layout.settings_content, settingsContainer, false)
            settingsContainer?.addView(settingsContentView)

            // 重新初始化设置项引用
            usernameText = settingsContentView.findViewById(R.id.username_text)
            modelValueText = settingsContentView.findViewById(R.id.model_value)
            personaValueText = settingsContentView.findViewById(R.id.persona_value)
            webSearchValueText = settingsContentView.findViewById(R.id.web_search_value)

            // 添加主动消息选项
            addProactiveMessagesOption(settingsContentView)

            // 收集设置卡片
            collectSettingCards(settingsContentView)
        } else {
            // 收集设置卡片
            collectSettingCards(view)
        }

        // 更新UI显示
        updateUIFromSettings()

        // 设置点击事件
        setupClickListeners(view)

        // 更新标题
        updateTitle(currentTitle)

        // 添加入场动画
        startEntranceAnimations()
    }

    /**
     * 收集所有设置卡片用于动画
     */
    private fun collectSettingCards(rootView: View) {
        settingCards.clear()

        // 查找并保存所有MaterialCardView或CardView的引用
        if (rootView is ViewGroup) {
            findAllSettingCards(rootView)
        }

        Log.d(TAG, "收集到 ${settingCards.size} 张设置卡片")
    }

    /**
     * 递归查找所有卡片
     */
    private fun findAllSettingCards(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is CardView || child is MaterialCardView) {
                settingCards.add(child)
            } else if (child is ViewGroup) {
                findAllSettingCards(child)
            }
        }
    }

    /**
     * 开始入场动画序列
     */
    private fun startEntranceAnimations() {
        if (settingCards.isEmpty() || isAnimationRunning) return

        isAnimationRunning = true

        // 为所有卡片设置初始状态
        settingCards.forEach { card ->
            card.alpha = 0f
            card.translationY = 80f
            card.scaleX = 0.95f
            card.scaleY = 0.95f
        }

        // 顺序播放入场动画
        settingCards.forEachIndexed { index, card ->
            val translateY = ObjectAnimator.ofFloat(card, "translationY", 80f, 0f)
            val alpha = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f)
            val scaleX = ObjectAnimator.ofFloat(card, "scaleX", 0.95f, 1f)
            val scaleY = ObjectAnimator.ofFloat(card, "scaleY", 0.95f, 1f)

            val animSet = AnimatorSet()
            animSet.playTogether(translateY, alpha, scaleX, scaleY)
            animSet.duration = 400
            animSet.interpolator = DecelerateInterpolator(1.2f)
            animSet.startDelay = 100L + (index * 100)

            animSet.start()

            // 最后一个动画结束时重置标志
            if (index == settingCards.size - 1) {
                animSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        isAnimationRunning = false
                    }
                })
            }
        }
    }

    /**
     * 专门用于导航到主动消息设置的方法
     */
    fun navigateToProactiveMessageSettings() {
        // 启动Activity
        val intent = Intent(context, ProactiveMessageSettingsActivity::class.java)
        startActivity(intent)

        // 添加过渡动画
        activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    /**
     * 添加主动消息选项
     */
    private fun addProactiveMessagesOption(containerView: View) {
        try {
            // 找到web_search_item的父容器
            val parentContainer = containerView.findViewById<ViewGroup>(R.id.web_search_item)?.parent as? ViewGroup

            if (parentContainer != null) {
                // 创建主动消息设置项
                val proactiveLayout = layoutInflater.inflate(R.layout.item_proactive_messages, null)

                // 找到分隔线并在其后添加主动消息项
                val dividerIndex = getLastDividerIndex(parentContainer)

                // 添加分隔线
                val divider = View(context)
                divider.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1 // 1dp高度
                )
                divider.setBackgroundResource(R.color.divider)

                // 设置左边距
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
                )
                layoutParams.setMargins(dpToPx(56), 0, 0, 0)
                divider.layoutParams = layoutParams

                // 添加分隔线和主动消息项
                parentContainer.addView(divider)
                parentContainer.addView(proactiveLayout)

                // 保存引用
                proactiveMessagesValueText = proactiveLayout.findViewById(R.id.proactive_messages_value)

                // 设置点击事件
                proactiveLayout.setOnClickListener {
                    // 添加触觉反馈
                    HapticUtils.performViewHapticFeedback(it)

                    // 添加卡片按压效果
                    animateItemPress(it)

                    // 延迟执行导航
                    it.postDelayed({
                        navigateToProactiveMessageSettings()
                    }, 200)
                }
            } else {
                Log.e(TAG, "找不到容器以添加主动消息项")
            }
        } catch (e: Exception) {
            Log.e(TAG, "添加主动消息选项失败: ${e.message}", e)
        }
    }

    /**
     * 获取最后一个分隔线的索引
     */
    private fun getLastDividerIndex(container: ViewGroup): Int {
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            if (child is View && child.layoutParams.height == 1) {
                return i
            }
        }
        return -1
    }

    /**
     * 将dp转为px
     */
    private fun dpToPx(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private fun initViews(view: View) {
        try {
            titleText = view.findViewById(R.id.settings_title)
            backIcon = view.findViewById(R.id.back_icon)
            settingsContainer = view.findViewById(R.id.settings_container)

            usernameText = view.findViewById(R.id.username_text)
            modelValueText = view.findViewById(R.id.model_value)
            personaValueText = view.findViewById(R.id.persona_value)
            webSearchValueText = view.findViewById(R.id.web_search_value)

            // 确保标题文本使用正确的颜色（使用theme属性）
            titleText?.let {
                it.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))

                // 添加标题缩放效果
                animateTitleEntrance(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化视图出错: ${e.message}")
        }
    }

    /**
     * 标题入场动画
     */
    private fun animateTitleEntrance(titleView: TextView) {
        titleView.alpha = 0f
        titleView.translationY = -30f

        val alpha = ObjectAnimator.ofFloat(titleView, "alpha", 0f, 1f)
        val translateY = ObjectAnimator.ofFloat(titleView, "translationY", -30f, 0f)

        val animSet = AnimatorSet()
        animSet.playTogether(alpha, translateY)
        animSet.duration = 500
        animSet.interpolator = DecelerateInterpolator()
        animSet.start()
    }

    /**
     * 设置项目按压动画
     */
    private fun animateItemPress(view: View) {
        // 适用于CardView内部的点击项
        val scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.97f)
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.97f)
        val scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 0.97f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.97f, 1f)

        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY)
        scaleDown.duration = 100

        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY)
        scaleUp.duration = 300
        scaleUp.interpolator = OvershootInterpolator(2f)

        val sequence = AnimatorSet()
        sequence.playSequentially(scaleDown, scaleUp)
        sequence.start()
    }

    /**
     * 按钮按压动画
     */
    private fun animateButtonPress(button: View) {
        val scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.85f)
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.85f)
        val scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 0.85f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.85f, 1f)

        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY)
        scaleDown.duration = 80

        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY)
        scaleUp.duration = 150
        scaleUp.interpolator = OvershootInterpolator(3f)

        val sequence = AnimatorSet()
        sequence.playSequentially(scaleDown, scaleUp)
        sequence.start()
    }

    /**
     * 卡片按压动画
     */
    private fun animateCardPress(card: View) {
        val parentCard = if (card is CardView || card is MaterialCardView) {
            card
        } else {
            // 查找父卡片
            var parent = card.parent
            while (parent != null && parent !is CardView && parent !is MaterialCardView) {
                parent = parent.parent
            }
            parent as? View
        }

        parentCard?.let {
            val scaleDownX = ObjectAnimator.ofFloat(it, "scaleX", 1f, 0.98f)
            val scaleDownY = ObjectAnimator.ofFloat(it, "scaleY", 1f, 0.98f)
            val scaleUpX = ObjectAnimator.ofFloat(it, "scaleX", 0.98f, 1f)
            val scaleUpY = ObjectAnimator.ofFloat(it, "scaleY", 0.98f, 1f)

            val scaleDown = AnimatorSet()
            scaleDown.playTogether(scaleDownX, scaleDownY)
            scaleDown.duration = 100

            val scaleUp = AnimatorSet()
            scaleUp.playTogether(scaleUpX, scaleUpY)
            scaleUp.duration = 300
            scaleUp.interpolator = OvershootInterpolator(1.5f)

            val sequence = AnimatorSet()
            sequence.playSequentially(scaleDown, scaleUp)
            sequence.start()
        }
    }

    private fun updateUIFromSettings() {
        usernameText?.text = "个人主页"

        // 更新模型显示
        modelValueText?.text = settingsManager.modelType

        // 更新人设显示
        val persona = settingsManager.aiPersona
        personaValueText?.text = if (persona.isNotEmpty()) {
            if (persona.length > 20) persona.substring(0, 20) + "..." else persona
        } else {
            "未设置"
        }

        // 更新联网设置显示
        webSearchValueText?.text = if (settingsManager.webSearchEnabled) {
            // 显示搜索引擎名称
            when (settingsManager.searchEngine) {
                SettingsManager.SEARCH_ENGINE_GOOGLE -> "Google"
                SettingsManager.SEARCH_ENGINE_BING -> "Bing"
                SettingsManager.SEARCH_ENGINE_DUCKDUCKGO -> "DuckDuckGo"
                else -> "已启用"
            }
        } else {
            "已禁用"
        }

        // 更新主动回复设置显示
        proactiveMessagesValueText?.text = if (settingsManager.proactiveMessagesEnabled) {
            "${settingsManager.proactiveMessagesInterval}小时"
        } else {
            "已禁用"
        }
    }

    private fun setupClickListeners(view: View) {
        // 用户信息项点击
        view.findViewById<View>(R.id.user_profile_item)?.setOnClickListener {
            HapticUtils.performViewHapticFeedback(it)
            animateCardPress(it)
            it.postDelayed({ navigateToSubScreen(UserProfileFragment()) }, 150)
        }

        // 外观和主题点击
        view.findViewById<View>(R.id.appearance_item)?.setOnClickListener {
            HapticUtils.performViewHapticFeedback(it)
            animateItemPress(it)
            it.postDelayed({ navigateToSubScreen(AppearanceFragment()) }, 150)
        }

        // AI模型点击
        view.findViewById<View>(R.id.ai_model_item)?.setOnClickListener {
            HapticUtils.performViewHapticFeedback(it)
            animateItemPress(it)
            it.postDelayed({ navigateToSubScreen(ModelSelectionFragment()) }, 150)
        }

        // 网络搜索点击
        view.findViewById<View>(R.id.web_search_item)?.setOnClickListener {
            HapticUtils.performViewHapticFeedback(it)
            animateItemPress(it)
            it.postDelayed({ navigateToSubScreen(WebSearchSettingsFragment()) }, 150)
        }

        // AI人设点击
        view.findViewById<View>(R.id.ai_persona_item)?.setOnClickListener {
            HapticUtils.performViewHapticFeedback(it)
            animateItemPress(it)
            it.postDelayed({ navigateToSubScreen(PersonaFragment()) }, 150)
        }

        // 聊天记录点击
        view.findViewById<View>(R.id.chat_history_item)?.setOnClickListener {
            HapticUtils.performViewHapticFeedback(it)
            animateItemPress(it)
            it.postDelayed({ navigateToSubScreen(ChatHistoryFragment()) }, 150)
        }

        // 关于点击
        view.findViewById<View>(R.id.about_item)?.setOnClickListener {
            HapticUtils.performViewHapticFeedback(it)
            animateItemPress(it)
            it.postDelayed({ navigateToSubScreen(AboutFragment()) }, 150)
        }
    }

    private fun selectUserAvatar() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        userImageLauncher.launch(intent)
    }

    /**
     * 导航到子页面 - 公开方法供子Fragment或Activity调用
     */
    fun navigateToSubScreen(fragment: BaseSettingsSubFragment) {
        // 检查是否有全屏子页面监听器
        if (subpageOpenListener != null) {
            // 通知监听器打开子页面
            subpageOpenListener?.onSubpageOpen(fragment)
            return
        }

        // 当没有监听器时使用
        // 保存当前子Fragment引用
        currentSubFragment = fragment

        // 设置导航回调
        fragment.setNavigationCallback(this)

        // 更新标题，在独立模式下直接修改标题栏
        if (useStandaloneMode) {
            titleText?.text = fragment.getTitle()
        } else {
            // 隐藏标题
            titleText?.visibility = View.GONE

            // 隐藏所有直接子视图，除了设置容器
            val rootView = view as? ViewGroup
            if (rootView != null) {
                for (i in 0 until rootView.childCount) {
                    val child = rootView.getChildAt(i)
                    if (child.id != R.id.settings_container) {
                        child.visibility = View.GONE
                    }
                }
            }
        }

        try {
            // 确保有有效的Fragment管理器和容器视图
            if (isAdded && settingsContainer != null) {
                // 使用Fragment事务并添加动画
                childFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_in_right, // 进入动画
                        R.anim.slide_out_left,  // 退出动画
                        R.anim.slide_in_left,   // 返回时进入动画
                        R.anim.slide_out_right  // 返回时退出动画
                    )
                    .replace(R.id.settings_container, fragment)
                    .addToBackStack(null)
                    .commit()
            } else {
                Log.e(TAG, "Fragment未添加或容器视图为空，无法导航")
                Toast.makeText(context, "无法导航到设置页面", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "导航错误: ${e.message}", e)
            Toast.makeText(context, "导航错误: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 实现导航回调接口，处理返回
     */
    override fun navigateBack() {
        // 避免再次调用子Fragment的方法
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()

            // 标记当前操作，避免循环调用
            isHandlingBackNavigation = true

            if (!useStandaloneMode) {
                // 仅在非独立模式下恢复隐藏的视图
                // 恢复所有被隐藏的视图
                val rootView = view as? ViewGroup
                if (rootView != null) {
                    for (i in 0 until rootView.childCount) {
                        rootView.getChildAt(i).visibility = View.VISIBLE
                    }
                }

                // 恢复标题
                titleText?.visibility = View.VISIBLE
            } else {
                // 在独立模式下，恢复标题文本
                titleText?.text = "设置"
            }

            // 清除当前子Fragment引用
            currentSubFragment = null

            // 更新UI以反映可能的更改
            updateUIFromSettings()

            // 通知设置变更
            listener?.onSettingsSaved()

            // 重置标记
            isHandlingBackNavigation = false
        } else if (navigationCallback != null) {
            // 如果没有子页面，且有导航回调，则调用它
            navigationCallback?.navigateBack()
        }
    }

    /**
     * 更新标题
     */
    private fun updateTitle(title: String) {
        currentTitle = title

        // 如果使用外部标题，通知父级更新标题
        if (useExternalTitle) {
            listener?.onTitleChanged(title)
        } else if (useStandaloneMode) {
            // 在独立模式下，直接设置标题文本
            titleText?.text = title
        } else {
            // 否则直接更新本地标题视图
            titleText?.text = title
        }
    }

    /**
     * 处理返回按钮点击
     * @return true 如果事件被处理，false 则继续传递
     */
    fun handleBackPressed(): Boolean {
        Log.d(TAG, "handleBackPressed: backStackCount=${childFragmentManager.backStackEntryCount}, currentSubFragment=${currentSubFragment?.javaClass?.simpleName}")

        // 检查是否有子Fragment在回退栈中
        if (childFragmentManager.backStackEntryCount > 0) {
            // 确保不会二次处理
            if (!isHandlingBackNavigation) {
                isHandlingBackNavigation = true

                try {
                    // 直接弹出回退栈
                    childFragmentManager.popBackStack()

                    if (!useStandaloneMode) {
                        // 仅在非独立模式下恢复隐藏的视图
                        // 恢复所有被隐藏的视图
                        val rootView = view as? ViewGroup
                        if (rootView != null) {
                            for (i in 0 until rootView.childCount) {
                                rootView.getChildAt(i).visibility = View.VISIBLE
                            }
                        }

                        // 恢复标题
                        titleText?.visibility = View.VISIBLE
                    } else {
                        // 在独立模式下，恢复标题文本
                        titleText?.text = "设置"
                    }

                    // 清除当前子Fragment引用
                    currentSubFragment = null

                    // 更新UI以反映可能的更改
                    updateUIFromSettings()

                    // 通知设置变更
                    listener?.onSettingsSaved()

                    return true
                } finally {
                    // 确保状态被正确重置，即使发生异常
                    isHandlingBackNavigation = false
                }
            }
        } else if (navigationCallback != null) {
            // 如果没有子页面，而是在全屏模式
            navigationCallback?.navigateBack()
            return true
        }

        return false
    }

    override fun onResume() {
        super.onResume()
        // 在恢复时更新设置显示，确保反映最新状态
        updateUIFromSettings()

        // 确保状态正确
        isHandlingBackNavigation = false
    }

    override fun onPause() {
        super.onPause()

        // 确保在暂停时重置状态
        isHandlingBackNavigation = false
    }

    override fun onDetach() {
        super.onDetach()

        // 确保在分离时重置状态
        isHandlingBackNavigation = false

        listener = null
        navigationCallback = null
        subpageOpenListener = null
    }
}
package com.example.chatapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import com.example.chatapp.R
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.utils.HapticUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * 联网搜索详细设置Fragment
 */
class WebSearchSettingsFragment : BaseSettingsSubFragment() {
    private lateinit var settingsManager: SettingsManager

    // UI组件
    private lateinit var webSearchSwitch: SwitchMaterial
    private lateinit var searchEngineGroup: RadioGroup
    private lateinit var googleRadio: MaterialRadioButton
    private lateinit var bingRadio: MaterialRadioButton
    private lateinit var duckduckgoRadio: MaterialRadioButton

    // 深度选择按钮
    private lateinit var depthButtons: List<TextView>

    // 结果数选择按钮
    private lateinit var resultsButtons: List<TextView>

    // 数值显示文本
    private lateinit var searchDepthValueText: TextView
    private lateinit var maxResultsValueText: TextView

    // 卡片视图
    private lateinit var webSearchCard: MaterialCardView
    private lateinit var searchEngineCard: MaterialCardView
    private lateinit var searchDepthCard: MaterialCardView
    private lateinit var maxResultsCard: MaterialCardView

    // 提示卡片
    private lateinit var infoCard: MaterialCardView
    private lateinit var infoText: TextView

    // 标题和副标题
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView

    // 记录之前选中的索引，用于动画处理
    private var previousDepthIndex = 2  // 默认值3对应索引2
    private var previousResultsIndex = 2  // 默认值6对应索引2

    // 动画是否正在进行中
    private var isAnimating = false

    override fun getTitle(): String = "联网搜索设置"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_web_search_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 延迟动画以等待视图完全绘制
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        // 初始化视图
        initViews(view)

        // 确保初始状态所有UI元素都不可见
        prepareViewsForAnimation()

        // 设置卡片3D效果
        setupCardElevationEffect()

        // 设置初始状态
        updateUIFromSettings()

        // 设置点击事件和监听器
        setupListeners()

        // 添加标题和副标题动画
        animateTitleEntry()

        // 延迟执行卡片入场动画，确保标题动画先完成
        view.postDelayed({
            // 添加入场动画
            animateCardsEntry()
        }, 250)
    }

    /**
     * 准备所有视图的初始状态以进行动画
     */
    private fun prepareViewsForAnimation() {
        // 隐藏所有卡片
        val cards = listOf(webSearchCard, searchEngineCard, searchDepthCard, maxResultsCard, infoCard)
        cards.forEach { card ->
            card.visibility = View.INVISIBLE
            card.alpha = 0f
        }

        // 隐藏标题和副标题
        titleText.visibility = View.INVISIBLE
        subtitleText.visibility = View.INVISIBLE
        titleText.alpha = 0f
        subtitleText.alpha = 0f
    }

    private fun initViews(view: View) {
        // 标题和副标题
        titleText = view.findViewById(R.id.search_settings_title)
        subtitleText = view.findViewById(R.id.search_settings_subtitle)

        // 卡片视图
        webSearchCard = view.findViewById(R.id.web_search_card)
        searchEngineCard = view.findViewById(R.id.search_engine_card)
        searchDepthCard = view.findViewById(R.id.search_depth_card)
        maxResultsCard = view.findViewById(R.id.max_results_card)

        // 初始化提示卡片
        infoCard = view.findViewById(R.id.info_card)
        infoText = view.findViewById(R.id.info_text)

        // 基础控件
        webSearchSwitch = view.findViewById(R.id.web_search_switch)
        searchEngineGroup = view.findViewById(R.id.search_engine_group)
        googleRadio = view.findViewById(R.id.google_radio)
        bingRadio = view.findViewById(R.id.bing_radio)
        duckduckgoRadio = view.findViewById(R.id.duckduckgo_radio)

        // 数值显示
        searchDepthValueText = view.findViewById(R.id.search_depth_value)
        maxResultsValueText = view.findViewById(R.id.max_results_value)

        // 深度选择按钮
        depthButtons = listOf(
            view.findViewById(R.id.depth_1),
            view.findViewById(R.id.depth_2),
            view.findViewById(R.id.depth_3),
            view.findViewById(R.id.depth_4),
            view.findViewById(R.id.depth_5)
        )

        // 结果数选择按钮
        resultsButtons = listOf(
            view.findViewById(R.id.results_2),
            view.findViewById(R.id.results_4),
            view.findViewById(R.id.results_6),
            view.findViewById(R.id.results_8),
            view.findViewById(R.id.results_10)
        )

        // 设置控件样式
        setupControlStyles()
    }

    private fun setupControlStyles() {
        // 设置Switch颜色
        webSearchSwitch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary))
        webSearchSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary_light))

        // 设置RadioButton颜色
        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                ContextCompat.getColor(requireContext(), R.color.primary),
                ContextCompat.getColor(requireContext(), R.color.text_secondary)
            )
        )

        googleRadio.buttonTintList = colorStateList
        bingRadio.buttonTintList = colorStateList
        duckduckgoRadio.buttonTintList = colorStateList
    }

    /**
     * 设置卡片的3D立体效果
     */
    private fun setupCardElevationEffect() {
        val cards = listOf(webSearchCard, searchEngineCard, searchDepthCard, maxResultsCard, infoCard)

        // 保存每个卡片的原始阴影值
        val originalElevations = mutableMapOf<MaterialCardView, Float>()
        cards.forEach { card ->
            originalElevations[card] = card.cardElevation
        }

        cards.forEach { card ->
            card.setOnTouchListener { view, event ->
                // 检查联网状态（当卡片被禁用时不响应触摸效果）
                if (isAnimating ||
                    (view != webSearchCard && view != infoCard && !webSearchSwitch.isChecked)) {
                    return@setOnTouchListener false
                }

                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        isAnimating = true

                        // 保存当前状态用于动画结束后重置
                        view.tag = view.elevation

                        // 按下时创建动画集合
                        val pressAnimatorSet = AnimatorSet()

                        // 创建缩放动画
                        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.97f)
                        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.97f)
                        val scaleAnim = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY)

                        // 创建高度动画
                        val currentElevation = originalElevations[card] ?: card.cardElevation
                        val elevateAnim = ObjectAnimator.ofFloat(view, "cardElevation",
                            currentElevation, currentElevation + 4f)

                        // 添加轻微移动效果
                        val translateY = ObjectAnimator.ofFloat(view, "translationY", 0f, 2f)

                        // 组合并播放动画
                        pressAnimatorSet.playTogether(scaleAnim, elevateAnim, translateY)
                        pressAnimatorSet.duration = 100
                        pressAnimatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                isAnimating = false
                            }
                        })
                        pressAnimatorSet.start()

                        view.isPressed = true
                        false
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        isAnimating = true

                        // 释放时创建动画集合
                        val releaseAnimatorSet = AnimatorSet()

                        // 创建缩放动画
                        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, view.scaleX, 1f)
                        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, view.scaleY, 1f)
                        val scaleAnim = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY)

                        // 创建高度动画 - 始终恢复到原始阴影值
                        val originalElevation = originalElevations[card] ?: card.cardElevation
                        val elevateAnim = ObjectAnimator.ofFloat(view, "cardElevation",
                            view.elevation, originalElevation)

                        // 恢复位置
                        val translateY = ObjectAnimator.ofFloat(view, "translationY", view.translationY, 0f)

                        // 组合并播放动画
                        releaseAnimatorSet.playTogether(scaleAnim, elevateAnim, translateY)
                        releaseAnimatorSet.duration = 250
                        releaseAnimatorSet.interpolator = OvershootInterpolator(1.1f)
                        releaseAnimatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                isAnimating = false

                                // 确保阴影值被重置为原始值
                                (view as? MaterialCardView)?.let { cardView ->
                                    cardView.cardElevation = originalElevations[cardView] ?: cardView.cardElevation
                                }
                            }
                        })
                        releaseAnimatorSet.start()

                        view.isPressed = false
                        false
                    }
                    else -> false
                }
            }
        }
    }

    private fun updateUIFromSettings() {
        // 设置开关状态
        webSearchSwitch.isChecked = settingsManager.webSearchEnabled

        // 设置搜索引擎选择
        when (settingsManager.searchEngine) {
            SettingsManager.SEARCH_ENGINE_GOOGLE -> googleRadio.isChecked = true
            SettingsManager.SEARCH_ENGINE_BING -> bingRadio.isChecked = true
            SettingsManager.SEARCH_ENGINE_DUCKDUCKGO -> duckduckgoRadio.isChecked = true
        }

        // 设置搜索深度
        val searchDepth = settingsManager.searchDepth
        searchDepthValueText.text = searchDepth.toString()
        previousDepthIndex = searchDepth - 1
        updateDepthSelection(previousDepthIndex, false) // 初始化时不需要动画

        // 设置最大结果数
        val maxResults = settingsManager.maxSearchResults
        maxResultsValueText.text = maxResults.toString()
        previousResultsIndex = (maxResults / 2) - 1 // 索引映射: 2->0, 4->1, 6->2, 8->3, 10->4
        updateResultsSelection(previousResultsIndex, false) // 初始化时不需要动画

        // 控制其他设置项的启用状态
        updateControlsEnabledState(webSearchSwitch.isChecked)
    }

    private fun updateControlsEnabledState(enabled: Boolean) {
        // 设置卡片状态
        val targetAlpha = if (enabled) 1.0f else 0.5f
        val targetElevation = if (enabled) 2f else 0f

        // 为每个卡片和内部控件应用动画效果
        animateCardState(searchEngineCard, enabled, targetAlpha, targetElevation)
        animateCardState(searchDepthCard, enabled, targetAlpha, targetElevation)
        animateCardState(maxResultsCard, enabled, targetAlpha, targetElevation)

        // 为提示卡片设置动画效果
        animateInfoCardState(infoCard, infoText, enabled)

        // 设置各组件的启用状态
        searchEngineGroup.isEnabled = enabled
        googleRadio.isEnabled = enabled
        bingRadio.isEnabled = enabled
        duckduckgoRadio.isEnabled = enabled

        // 设置深度和结果数按钮状态
        depthButtons.forEach { it.isEnabled = enabled }
        resultsButtons.forEach { it.isEnabled = enabled }

        // 调整文本颜色
        val textColor = if (enabled)
            ContextCompat.getColor(requireContext(), R.color.primary)
        else
            ContextCompat.getColor(requireContext(), R.color.text_hint)

        searchDepthValueText.setTextColor(textColor)
        maxResultsValueText.setTextColor(textColor)
    }

    private fun animateInfoCardState(infoCard: MaterialCardView, infoText: TextView, enabled: Boolean) {
        // 创建提示卡片背景色动画
        val originalCardColor = ContextCompat.getColor(requireContext(), R.color.info_card_background)
        val targetCardColor = if (enabled)
            originalCardColor
        else
            ContextCompat.getColor(requireContext(), R.color.info_card_background_disabled)

        // 为卡片应用动画效果
        val alphaAnimator = ObjectAnimator.ofFloat(infoCard, "alpha", infoCard.alpha, if (enabled) 1.0f else 0.7f)
        alphaAnimator.duration = 300

        // 创建文本颜色动画
        val originalTextColor = ContextCompat.getColor(requireContext(), R.color.info_text_color)
        val targetTextColor = if (enabled)
            originalTextColor
        else
            ContextCompat.getColor(requireContext(), R.color.info_text_color_disabled)

        // 设置卡片背景色
        infoCard.setCardBackgroundColor(targetCardColor)

        // 设置文本颜色
        infoText.setTextColor(targetTextColor)

        // 添加阴影动画处理
        val targetElevation = if (enabled) 2f else 0f
        val elevationAnimator = ObjectAnimator.ofFloat(infoCard, "cardElevation",
            infoCard.cardElevation, targetElevation)
        elevationAnimator.duration = 300

        // 添加轻微缩放效果
        val scaleX = ObjectAnimator.ofFloat(infoCard, "scaleX", infoCard.scaleX, if (enabled) 1.0f else 0.98f)
        val scaleY = ObjectAnimator.ofFloat(infoCard, "scaleY", infoCard.scaleY, if (enabled) 1.0f else 0.98f)

        // 组合动画
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(alphaAnimator, elevationAnimator, scaleX, scaleY)
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }

    private fun animateCardState(card: MaterialCardView, enabled: Boolean, targetAlpha: Float, targetElevation: Float) {
        // 创建透明度动画
        val alphaAnimator = ObjectAnimator.ofFloat(card, "alpha", card.alpha, targetAlpha)
        alphaAnimator.duration = 300

        // 创建高度动画
        val elevationAnimator = ObjectAnimator.ofFloat(card, "cardElevation", card.cardElevation, targetElevation)
        elevationAnimator.duration = 300

        // 添加轻微缩放效果
        val scaleX = ObjectAnimator.ofFloat(card, "scaleX", card.scaleX, if (enabled) 1.0f else 0.98f)
        val scaleY = ObjectAnimator.ofFloat(card, "scaleY", card.scaleY, if (enabled) 1.0f else 0.98f)

        // 组合动画
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(alphaAnimator, elevationAnimator, scaleX, scaleY)
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()

        // 设置内部控件的启用状态
        updateChildrenEnabledState(card, enabled)
    }

    private fun updateChildrenEnabledState(viewGroup: ViewGroup, enabled: Boolean) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ViewGroup) {
                updateChildrenEnabledState(child, enabled)
            } else {
                child.isEnabled = enabled
                if (child !is SwitchMaterial) { // 避免改变Switch的Alpha
                    // 使用动画淡入淡出
                    child.animate()
                        .alpha(if (enabled) 1.0f else 0.5f)
                        .setDuration(300)
                        .start()
                }
            }
        }
    }

    private fun setupListeners() {
        // 联网开关监听
        webSearchSwitch.setOnCheckedChangeListener { _, isChecked ->
            // 添加触觉反馈
            HapticUtils.performViewHapticFeedback(webSearchSwitch, false)

            // 添加开关翻转动画
            animateSwitchToggle(webSearchSwitch)

            // 应用状态变化动画
            updateControlsEnabledState(isChecked)

            // 保存设置
            settingsManager.webSearchEnabled = isChecked

            // 通知设置变更
            showCustomToast(if (isChecked) "已启用联网搜索" else "已禁用联网搜索")
        }

        // 搜索引擎选择监听
        searchEngineGroup.setOnCheckedChangeListener { _, checkedId ->
            // 添加触觉反馈
            HapticUtils.performHapticFeedback(requireContext(), false)

            // 应用动画效果
            animateRadioSelection(checkedId)

            val searchEngine = when (checkedId) {
                R.id.google_radio -> SettingsManager.SEARCH_ENGINE_GOOGLE
                R.id.bing_radio -> SettingsManager.SEARCH_ENGINE_BING
                R.id.duckduckgo_radio -> SettingsManager.SEARCH_ENGINE_DUCKDUCKGO
                else -> SettingsManager.SEARCH_ENGINE_GOOGLE
            }
            settingsManager.searchEngine = searchEngine
        }

        // 设置深度按钮点击监听
        depthButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                // 跳过点击已选中按钮的情况
                if (index == previousDepthIndex) return@setOnClickListener

                // 添加触觉反馈
                HapticUtils.performViewHapticFeedback(button, false)

                // 更新UI选择状态，带动画效果
                updateDepthSelection(index, true)

                // 显示数值变化动画
                animateValueChange(searchDepthValueText,
                    searchDepthValueText.text.toString(),
                    (index + 1).toString())

                // 保存设置
                settingsManager.searchDepth = index + 1

                // 更新上一次选择的索引
                previousDepthIndex = index
            }
        }

        // 设置结果数按钮点击监听
        resultsButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                // 跳过点击已选中按钮的情况
                if (index == previousResultsIndex) return@setOnClickListener

                // 添加触觉反馈
                HapticUtils.performViewHapticFeedback(button, false)

                // 更新UI选择状态，带动画效果
                updateResultsSelection(index, true)

                // 显示数值变化动画
                val value = (index + 1) * 2  // 2, 4, 6, 8, 10
                animateValueChange(maxResultsValueText,
                    maxResultsValueText.text.toString(),
                    value.toString())

                // 保存设置
                settingsManager.maxSearchResults = value

                // 更新上一次选择的索引
                previousResultsIndex = index
            }
        }
    }

    /**
     * 开关翻转动画
     */
    private fun animateSwitchToggle(switch: SwitchMaterial) {
        // 创建缩放动画替代旋转
        val scaleDown = ObjectAnimator.ofFloat(switch, "scaleX", 1f, 0.8f)
        val scaleUp = ObjectAnimator.ofFloat(switch, "scaleX", 0.8f, 1.2f)
        val scaleNormal = ObjectAnimator.ofFloat(switch, "scaleX", 1.2f, 1f)

        val scaleDownY = ObjectAnimator.ofFloat(switch, "scaleY", 1f, 0.8f)
        val scaleUpY = ObjectAnimator.ofFloat(switch, "scaleY", 0.8f, 1.2f)
        val scaleNormalY = ObjectAnimator.ofFloat(switch, "scaleY", 1.2f, 1f)

        val sequence = AnimatorSet()
        sequence.playSequentially(
            AnimatorSet().apply { playTogether(scaleDown, scaleDownY) },
            AnimatorSet().apply { playTogether(scaleUp, scaleUpY) },
            AnimatorSet().apply { playTogether(scaleNormal, scaleNormalY) }
        )
        sequence.duration = 400
        sequence.interpolator = OvershootInterpolator(1.5f)
        sequence.start()
    }

    /**
     * 更新深度选择UI状态
     * @param selectedIndex 选中的索引
     * @param animate 是否需要动画效果
     */
    private fun updateDepthSelection(selectedIndex: Int, animate: Boolean) {
        depthButtons.forEachIndexed { index, button ->
            if (index == selectedIndex) {
                // 设置为选中状态
                button.background = ContextCompat.getDrawable(requireContext(), R.drawable.pill_toggle_selected)
                button.setTextColor(ContextCompat.getColor(requireContext(), R.color.toggle_text_color))
                button.setTypeface(button.typeface, android.graphics.Typeface.BOLD)

                // 如果需要动画效果
                if (animate) {
                    animateButtonSelection(button)
                }
            } else {
                // 其他按钮恢复未选中状态
                button.background = null
                button.setTextColor(ContextCompat.getColor(requireContext(), R.color.toggle_text_color_unselected))
                button.setTypeface(button.typeface, android.graphics.Typeface.NORMAL)

                // 如果正在取消之前选中按钮的状态，且需要动画
                if (animate && index == previousDepthIndex) {
                    animateButtonDeselection(button)
                }
            }
        }
    }

    /**
     * 更新结果数选择UI状态
     * @param selectedIndex 选中的索引
     * @param animate 是否需要动画效果
     */
    private fun updateResultsSelection(selectedIndex: Int, animate: Boolean) {
        resultsButtons.forEachIndexed { index, button ->
            if (index == selectedIndex) {
                // 设置为选中状态
                button.background = ContextCompat.getDrawable(requireContext(), R.drawable.pill_toggle_selected)
                button.setTextColor(ContextCompat.getColor(requireContext(), R.color.toggle_text_color))
                button.setTypeface(button.typeface, android.graphics.Typeface.BOLD)

                // 如果需要动画效果
                if (animate) {
                    animateButtonSelection(button)
                }
            } else {
                // 其他按钮恢复未选中状态
                button.background = null
                button.setTextColor(ContextCompat.getColor(requireContext(), R.color.toggle_text_color_unselected))
                button.setTypeface(button.typeface, android.graphics.Typeface.NORMAL)

                // 如果正在取消之前选中按钮的状态，且需要动画
                if (animate && index == previousResultsIndex) {
                    animateButtonDeselection(button)
                }
            }
        }
    }

    /**
     * 为选中的按钮添加动画效果
     */
    private fun animateButtonSelection(button: TextView) {
        // 创建缩放动画
        val scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 1.2f, 1f)
        val translateY = ObjectAnimator.ofFloat(button, "translationY", 0f, -4f, 0f, 2f, 0f)

        // 创建动画集合
        val animSet = AnimatorSet()
        animSet.playTogether(scaleX, scaleY, translateY)
        animSet.duration = 400
        animSet.interpolator = OvershootInterpolator(2.5f)
        animSet.start()
    }

    /**
     * 为取消选中的按钮添加动画效果
     */
    private fun animateButtonDeselection(button: TextView) {
        // 创建淡出效果
        val alpha = ObjectAnimator.ofFloat(button, "alpha", 1f, 0.5f, 1f)

        // 创建轻微缩放效果
        val scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.9f, 1f)
        val scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.9f, 1f)

        // 创建动画集合
        val animSet = AnimatorSet()
        animSet.playTogether(alpha, scaleX, scaleY)
        animSet.duration = 300
        animSet.interpolator = AccelerateDecelerateInterpolator()
        animSet.start()
    }

    /**
     * 数值变化动画
     */
    private fun animateValueChange(textView: TextView, oldValue: String, newValue: String) {
        // 如果值相同，不执行动画
        if (oldValue == newValue) return

        // 保存原始尺寸和位置
        val originalScale = textView.scaleX

        // 创建一个更复杂的动画序列
        val animSet = AnimatorSet()

        // 缩小并向上移动轻微抖动
        val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            textView,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 0.6f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 0.6f),
            PropertyValuesHolder.ofFloat("translationY", 0f, -10f, -5f, -8f)
        )
        scaleDown.duration = 250

        // 放大并淡入
        val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
            textView,
            PropertyValuesHolder.ofFloat("scaleX", 0.6f, 1.2f),
            PropertyValuesHolder.ofFloat("scaleY", 0.6f, 1.2f),
            PropertyValuesHolder.ofFloat("translationY", -8f, 5f),
            PropertyValuesHolder.ofFloat("alpha", 0.5f, 1f)
        )
        scaleUp.duration = 200

        // 恢复到正常大小
        val scaleFinal = ObjectAnimator.ofPropertyValuesHolder(
            textView,
            PropertyValuesHolder.ofFloat("scaleX", 1.2f, originalScale),
            PropertyValuesHolder.ofFloat("scaleY", 1.2f, originalScale),
            PropertyValuesHolder.ofFloat("translationY", 5f, 0f)
        )
        scaleFinal.duration = 150
        scaleFinal.interpolator = OvershootInterpolator(2f)

        // 创建动画序列
        val sequence = AnimatorSet()
        sequence.playSequentially(scaleDown, scaleUp, scaleFinal)

        // 在适当的时机更新文本
        scaleDown.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: android.animation.Animator) {
                textView.alpha = 0.5f
            }

            override fun onAnimationEnd(animation: android.animation.Animator) {
                // 在缩小动画结束时更新文本值
                textView.text = newValue
            }
        })

        // 开始动画
        sequence.start()
    }

    private fun animateRadioSelection(checkedId: Int) {
        val selectedRadio = view?.findViewById<MaterialRadioButton>(checkedId) ?: return

        // 创建属性动画集
        val propertyValuesHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.8f, 1.1f, 1f)
        val propertyValuesHolder2 = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.8f, 1.1f, 1f)

        // 添加轻微上下移动效果替代左右移动
        val propertyValuesHolder3 = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, 3f, -3f, 0f)

        // 添加透明度变化增强反馈
        val propertyValuesHolder4 = PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.7f, 1f)

        // 创建综合动画
        val animator = ObjectAnimator.ofPropertyValuesHolder(
            selectedRadio,
            propertyValuesHolder,
            propertyValuesHolder2,
            propertyValuesHolder3,
            propertyValuesHolder4
        )

        // 设置动画属性
        animator.duration = 450
        animator.interpolator = OvershootInterpolator(1.5f)
        animator.start()
    }

    /**
     * 标题和副标题入场动画
     */
    private fun animateTitleEntry() {
        // 设置初始状态
        titleText.visibility = View.INVISIBLE
        subtitleText.visibility = View.INVISIBLE

        titleText.alpha = 0f
        titleText.translationY = -50f

        subtitleText.alpha = 0f
        subtitleText.translationY = -30f

        // 标题显示与动画
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            titleText.visibility = View.VISIBLE

            // 创建标题动画
            val titleAnim = AnimatorSet()
            val titleAlpha = ObjectAnimator.ofFloat(titleText, "alpha", 0f, 1f)
            val titleTranslate = ObjectAnimator.ofFloat(titleText, "translationY", -50f, 0f)
            titleAnim.playTogether(titleAlpha, titleTranslate)
            titleAnim.duration = 500
            titleAnim.interpolator = DecelerateInterpolator(1.5f)
            titleAnim.start()
        }

        // 副标题显示与动画
        handler.postDelayed({
            subtitleText.visibility = View.VISIBLE

            // 创建副标题动画
            val subtitleAnim = AnimatorSet()
            val subtitleAlpha = ObjectAnimator.ofFloat(subtitleText, "alpha", 0f, 1f)
            val subtitleTranslate = ObjectAnimator.ofFloat(subtitleText, "translationY", -30f, 0f)
            subtitleAnim.playTogether(subtitleAlpha, subtitleTranslate)
            subtitleAnim.duration = 500
            subtitleAnim.interpolator = DecelerateInterpolator(1.2f)
            subtitleAnim.start()
        }, 150)
    }

    /**
     * 卡片入场动画
     */
    private fun animateCardsEntry() {
        // 卡片列表
        val cards = listOf(webSearchCard, searchEngineCard, searchDepthCard, maxResultsCard, infoCard)

        // 设置初始状态
        cards.forEachIndexed { index, card ->
            card.visibility = View.INVISIBLE
            card.alpha = 0f
            card.translationY = 100f
            card.scaleX = 0.85f
            card.scaleY = 0.85f
            // 添加水平偏移替代旋转
            card.translationX = if (index % 2 == 0) -15f else 15f
        }

        // 为每个卡片应用动画，在动画开始前将卡片设为可见
        cards.forEachIndexed { index, card ->
            // 创建延迟显示的Runnable
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({
                // 在动画开始前设置为可见
                card.visibility = View.VISIBLE

                // 创建动画集
                val animSet = AnimatorSet()

                // 透明度动画
                val alphaAnim = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f)

                // 平移动画 - 垂直方向
                val translateYAnim = ObjectAnimator.ofFloat(card, "translationY", 100f, 0f)

                // 平移动画 - 水平方向
                val translateXAnim = ObjectAnimator.ofFloat(card, "translationX",
                    if (index % 2 == 0) -15f else 15f, 0f)

                // 缩放动画 - 带有弹性效果
                val scaleXAnim = ObjectAnimator.ofFloat(card, "scaleX", 0.85f, 1.03f, 1f)
                val scaleYAnim = ObjectAnimator.ofFloat(card, "scaleY", 0.85f, 1.03f, 1f)

                // 组合动画
                animSet.playTogether(alphaAnim, translateYAnim, translateXAnim, scaleXAnim, scaleYAnim)
                animSet.duration = 600
                animSet.interpolator = OvershootInterpolator(0.9f)

                // 开始动画
                animSet.start()
            }, 100L + (index * 150))
        }
    }

    private fun showCustomToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
    }
}
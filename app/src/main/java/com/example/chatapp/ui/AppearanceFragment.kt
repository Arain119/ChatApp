package com.example.chatapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.animation.ArgbEvaluator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.view.doOnPreDraw
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.example.chatapp.R
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.utils.HapticUtils

/**
 * 外观和主题Fragment
 */
class AppearanceFragment : BaseSettingsSubFragment() {
    private lateinit var settingsManager: SettingsManager

    // 主题模式相关视图
    private lateinit var lightCheck: ImageView
    private lateinit var darkCheck: ImageView
    private lateinit var systemCheck: ImageView

    // 颜色主题相关视图
    private lateinit var colorBlueCheck: ImageView
    private lateinit var colorGreenCheck: ImageView
    private lateinit var colorPurpleCheck: ImageView
    private lateinit var colorOrangeCheck: ImageView
    private lateinit var colorPinkCheck: ImageView

    // 所有主题描述视图
    private lateinit var lightDescription: TextView
    private lateinit var darkDescription: TextView
    private lateinit var systemDescription: TextView

    // 卡片引用
    private lateinit var themeOptionsCard: MaterialCardView
    private lateinit var colorPickerCard: MaterialCardView
    private lateinit var tipsCard: MaterialCardView

    // 颜色选择器卡片引用
    private lateinit var blueCard: MaterialCardView
    private lateinit var greenCard: MaterialCardView
    private lateinit var purpleCard: MaterialCardView
    private lateinit var orangeCard: MaterialCardView
    private lateinit var pinkCard: MaterialCardView

    // 当前选中的颜色主题
    private var currentColorTheme: String = "blue"
    // 记录当前展开的描述视图
    private var currentExpandedDescription: TextView? = null

    override fun getTitle(): String = "外观和主题"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_appearance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 延迟动画以等待视图完全绘制
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        // 初始化视图引用
        initViewReferences(view)

        // 获取当前主题模式
        val currentThemeMode = settingsManager.themeMode

        // 获取当前颜色主题
        currentColorTheme = settingsManager.colorTheme

        // 设置各个主题选项的点击事件
        setupThemeModeOptions(view)

        // 设置颜色主题选项的点击事件
        setupColorThemeOptions()

        // 根据当前主题设置显示状态
        updateThemeModeUI(currentThemeMode)
        updateColorThemeUI(currentColorTheme)

        // 设置主题模式选项的展开效果
        setupExpandableOptions()

        // 设置卡片动画效果
        setupCardAnimations()
    }

    private fun initViewReferences(view: View) {
        // 主题模式选中图标
        lightCheck = view.findViewById(R.id.light_check)
        darkCheck = view.findViewById(R.id.dark_check)
        systemCheck = view.findViewById(R.id.system_check)

        // 颜色主题选中图标
        colorBlueCheck = view.findViewById(R.id.color_blue_check)
        colorGreenCheck = view.findViewById(R.id.color_green_check)
        colorPurpleCheck = view.findViewById(R.id.color_purple_check)
        colorOrangeCheck = view.findViewById(R.id.color_orange_check)
        colorPinkCheck = view.findViewById(R.id.color_pink_check)

        // 所有主题模式的描述视图
        lightDescription = view.findViewById(R.id.light_description)
        darkDescription = view.findViewById(R.id.dark_description)
        systemDescription = view.findViewById(R.id.system_description)

        // 卡片引用
        themeOptionsCard = view.findViewById(R.id.theme_options_card)
        colorPickerCard = view.findViewById(R.id.color_picker_card)
        tipsCard = view.findViewById(R.id.tips_card)

        // 颜色选择器卡片
        blueCard = view.findViewById(R.id.color_blue)
        greenCard = view.findViewById(R.id.color_green)
        purpleCard = view.findViewById(R.id.color_purple)
        orangeCard = view.findViewById(R.id.color_orange)
        pinkCard = view.findViewById(R.id.color_pink)

        // 移除颜色选择器卡片的背景边框
        removeCardBorders()
    }

    /**
     * 移除颜色选择器卡片的边框
     */
    private fun removeCardBorders() {
        // 使用自定义透明背景替换默认前景
        val transparentDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(android.graphics.Color.TRANSPARENT)
        }

        // 应用透明背景到所有颜色卡片的前景
        blueCard.foreground = transparentDrawable
        greenCard.foreground = transparentDrawable
        purpleCard.foreground = transparentDrawable
        orangeCard.foreground = transparentDrawable
        pinkCard.foreground = transparentDrawable

        // 点击效果
        blueCard.isClickable = true
        greenCard.isClickable = true
        purpleCard.isClickable = true
        orangeCard.isClickable = true
        pinkCard.isClickable = true

        blueCard.isFocusable = true
        greenCard.isFocusable = true
        purpleCard.isFocusable = true
        orangeCard.isFocusable = true
        pinkCard.isFocusable = true
    }

    private fun setupCardAnimations() {
        // 初始状态
        themeOptionsCard.alpha = 0f
        themeOptionsCard.translationY = 100f

        colorPickerCard.alpha = 0f
        colorPickerCard.translationY = 100f

        tipsCard.alpha = 0f
        tipsCard.translationY = 100f

        // 添加标题动画
        val title = view?.findViewById<TextView>(R.id.title)
        val subtitle = view?.findViewById<TextView>(R.id.subtitle)

        title?.alpha = 0f
        title?.translationY = 50f
        title?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setDuration(400)
            ?.setInterpolator(DecelerateInterpolator(1.5f))
            ?.start()

        subtitle?.alpha = 0f
        subtitle?.translationY = 50f
        subtitle?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setDuration(400)
            ?.setStartDelay(100)
            ?.setInterpolator(DecelerateInterpolator(1.5f))
            ?.start()

        // 给卡片添加进入动画，延迟依次进入
        themeOptionsCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(200)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()

        colorPickerCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(300)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()

        tipsCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(400)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()

        // 添加颜色卡片的弹出动画
        val colorCards = listOf(blueCard, greenCard, purpleCard, orangeCard, pinkCard)
        colorCards.forEachIndexed { index, card ->
            card.scaleX = 0.6f
            card.scaleY = 0.6f
            card.alpha = 0f

            card.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(400)
                .setStartDelay(500L + (index * 100))
                .setInterpolator(OvershootInterpolator(2f))
                .start()
        }
    }

    private fun setupThemeModeOptions(view: View) {
        view.findViewById<View>(R.id.theme_light).setOnClickListener {
            // 添加按下动画
            animateOptionPress(it)
            // 延迟执行以完成动画
            it.postDelayed({
                updateThemeMode(SettingsManager.THEME_LIGHT)
                // 触发震动反馈
                HapticUtils.performViewHapticFeedback(it)
            }, 100)
        }

        view.findViewById<View>(R.id.theme_dark).setOnClickListener {
            // 添加按下动画
            animateOptionPress(it)
            // 延迟执行以完成动画
            it.postDelayed({
                updateThemeMode(SettingsManager.THEME_DARK)
                // 触发震动反馈
                HapticUtils.performViewHapticFeedback(it)
            }, 100)
        }

        view.findViewById<View>(R.id.theme_system).setOnClickListener {
            // 添加按下动画
            animateOptionPress(it)
            // 延迟执行以完成动画
            it.postDelayed({
                updateThemeMode(SettingsManager.THEME_SYSTEM)
                // 触发震动反馈
                HapticUtils.performViewHapticFeedback(it)
            }, 100)
        }
    }

    /**
     * 选项按下动画
     */
    private fun animateOptionPress(view: View) {
        val scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.97f)
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.97f)
        val scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 0.97f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.97f, 1f)

        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY)
        scaleDown.duration = 100

        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY)
        scaleUp.duration = 200
        scaleUp.startDelay = 100
        scaleUp.interpolator = OvershootInterpolator(2f)

        val sequence = AnimatorSet()
        sequence.playSequentially(scaleDown, scaleUp)
        sequence.start()
    }

    private fun setupColorThemeOptions() {
        // 蓝色主题
        blueCard.setOnClickListener {
            // 添加脉冲动画
            animateColorCardPulse(blueCard)
            // 延迟执行以完成动画
            it.postDelayed({
                updateColorTheme("blue")
                HapticUtils.performViewHapticFeedback(it)
            }, 200)
        }

        // 绿色主题
        greenCard.setOnClickListener {
            // 添加脉冲动画
            animateColorCardPulse(greenCard)
            // 延迟执行以完成动画
            it.postDelayed({
                updateColorTheme("green")
                HapticUtils.performViewHapticFeedback(it)
            }, 200)
        }

        // 紫色主题
        purpleCard.setOnClickListener {
            // 添加脉冲动画
            animateColorCardPulse(purpleCard)
            // 延迟执行以完成动画
            it.postDelayed({
                updateColorTheme("purple")
                HapticUtils.performViewHapticFeedback(it)
            }, 200)
        }

        // 橙色主题
        orangeCard.setOnClickListener {
            // 添加脉冲动画
            animateColorCardPulse(orangeCard)
            // 延迟执行以完成动画
            it.postDelayed({
                updateColorTheme("orange")
                HapticUtils.performViewHapticFeedback(it)
            }, 200)
        }

        // 粉色主题
        pinkCard.setOnClickListener {
            // 添加脉冲动画
            animateColorCardPulse(pinkCard)
            // 延迟执行以完成动画
            it.postDelayed({
                updateColorTheme("pink")
                HapticUtils.performViewHapticFeedback(it)
            }, 200)
        }
    }

    /**
     * 颜色卡片脉冲动画
     */
    private fun animateColorCardPulse(card: MaterialCardView) {
        // 脉冲效果
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "scaleX", 1f, 0.85f),
                ObjectAnimator.ofFloat(card, "scaleY", 1f, 0.85f)
            )
            duration = 120
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "scaleX", 0.85f, 1.1f),
                ObjectAnimator.ofFloat(card, "scaleY", 0.85f, 1.1f)
            )
            duration = 200
            interpolator = DecelerateInterpolator()
        }

        val scaleNormal = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "scaleX", 1.1f, 1f),
                ObjectAnimator.ofFloat(card, "scaleY", 1.1f, 1f)
            )
            duration = 150
            interpolator = OvershootInterpolator(3f)
        }

        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp, scaleNormal)
            start()
        }
    }

    private fun updateThemeModeUI(themeMode: Int) {
        // 重置所有选中状态
        lightCheck.visibility = View.GONE
        darkCheck.visibility = View.GONE
        systemCheck.visibility = View.GONE

        // 设置当前选中状态
        when (themeMode) {
            SettingsManager.THEME_LIGHT -> lightCheck.visibility = View.VISIBLE
            SettingsManager.THEME_DARK -> darkCheck.visibility = View.VISIBLE
            else -> systemCheck.visibility = View.VISIBLE
        }

        // 使用动画显示选中图标
        val checkView = when (themeMode) {
            SettingsManager.THEME_LIGHT -> lightCheck
            SettingsManager.THEME_DARK -> darkCheck
            else -> systemCheck
        }

        checkView.alpha = 0f
        checkView.scaleX = 0f
        checkView.scaleY = 0f

        checkView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(3f))
            .start()
    }

    private fun updateColorThemeUI(colorTheme: String) {
        // 重置所有选中状态
        colorBlueCheck.visibility = View.INVISIBLE
        colorGreenCheck.visibility = View.INVISIBLE
        colorPurpleCheck.visibility = View.INVISIBLE
        colorOrangeCheck.visibility = View.INVISIBLE
        colorPinkCheck.visibility = View.INVISIBLE

        // 设置当前选中状态
        val checkView = when (colorTheme) {
            "blue" -> colorBlueCheck
            "green" -> colorGreenCheck
            "purple" -> colorPurpleCheck
            "orange" -> colorOrangeCheck
            "pink" -> colorPinkCheck
            else -> colorBlueCheck
        }

        checkView.visibility = View.VISIBLE

        // 添加选中动画
        checkView.alpha = 0f
        checkView.scaleX = 0f
        checkView.scaleY = 0f

        checkView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(3f))
            .start()

        // 更新高亮状态
        updateCardHighlight(colorTheme)
    }

    /**
     * 更新卡片高亮
     * 使用阴影来代替边框效果，适用于深色和浅色模式
     */
    private fun updateCardHighlight(colorTheme: String) {
        // 获取所有颜色卡片
        val allCards = listOf(
            Pair(blueCard, "blue"),
            Pair(greenCard, "green"),
            Pair(purpleCard, "purple"),
            Pair(orangeCard, "orange"),
            Pair(pinkCard, "pink")
        )

        // 为每个卡片设置适当的阴影动画
        allCards.forEach { (card, theme) ->
            val newElevation = if (theme == colorTheme) 12f else 4f
            val currentElevation = card.cardElevation

            // 创建阴影动画
            if (currentElevation != newElevation) {
                ValueAnimator.ofFloat(currentElevation, newElevation).apply {
                    duration = 300
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { animator ->
                        card.cardElevation = animator.animatedValue as Float
                    }
                    start()
                }

                // 如果是选中的卡片，添加额外的缩放动画
                if (theme == colorTheme) {
                    card.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(300)
                        .setInterpolator(OvershootInterpolator(2f))
                        .withEndAction {
                            card.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(200)
                                .setInterpolator(DecelerateInterpolator())
                                .start()
                        }
                        .start()
                }
            }
        }
    }

    private fun setupExpandableOptions() {
        // 浅色模式点击展开详情
        view?.findViewById<View>(R.id.theme_light)?.setOnLongClickListener {
            toggleDescriptionVisibility(lightDescription)
            HapticUtils.performHapticFeedback(requireContext())
            true
        }

        // 深色模式点击展开详情
        view?.findViewById<View>(R.id.theme_dark)?.setOnLongClickListener {
            toggleDescriptionVisibility(darkDescription)
            HapticUtils.performHapticFeedback(requireContext())
            true
        }

        // 跟随系统点击展开详情
        view?.findViewById<View>(R.id.theme_system)?.setOnLongClickListener {
            toggleDescriptionVisibility(systemDescription)
            HapticUtils.performHapticFeedback(requireContext())
            true
        }
    }

    private fun toggleDescriptionVisibility(descriptionView: TextView) {
        // 如果当前有展开的描述且不是当前这个，先收起它
        if (currentExpandedDescription != null && currentExpandedDescription != descriptionView) {
            currentExpandedDescription?.animate()
                ?.alpha(0f)
                ?.setDuration(200)
                ?.withEndAction { currentExpandedDescription?.visibility = View.GONE }
                ?.start()
        }

        // 切换描述文本的可见性，带动画效果
        if (descriptionView.visibility == View.VISIBLE) {
            descriptionView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    descriptionView.visibility = View.GONE
                    currentExpandedDescription = null
                }
                .start()
        } else {
            descriptionView.alpha = 0f
            descriptionView.visibility = View.VISIBLE
            descriptionView.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
            currentExpandedDescription = descriptionView
        }
    }

    private fun updateThemeMode(themeMode: Int) {
        settingsManager.themeMode = themeMode

        // 更新UI
        updateThemeModeUI(themeMode)

        // 应用主题
        val mode = when (themeMode) {
            SettingsManager.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsManager.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        // 显示确认动画和提示
        showThemeChangeConfirmation(themeMode)

        // 通知设置已更改
        notifyNavigationBack()
    }

    /**
     * 显示主题变更确认动画和提示
     */
    private fun showThemeChangeConfirmation(themeMode: Int) {
        val themeName = when (themeMode) {
            SettingsManager.THEME_LIGHT -> "浅色"
            SettingsManager.THEME_DARK -> "深色"
            else -> "跟随系统"
        }

        // 显示toast提示
        Toast.makeText(requireContext(), "已切换到${themeName}模式", Toast.LENGTH_SHORT).show()

        // 添加主题卡片确认动画
        themeOptionsCard.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(150)
            .withEndAction {
                themeOptionsCard.animate()
                    .scaleX(1.02f)
                    .scaleY(1.02f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(3f))
                    .withEndAction {
                        themeOptionsCard.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun updateColorTheme(colorTheme: String) {
        // 如果选的主题与当前相同，不执行操作
        if (this.currentColorTheme == colorTheme) {
            return
        }

        // 保存设置
        settingsManager.colorTheme = colorTheme
        this.currentColorTheme = colorTheme

        // 更新UI
        updateColorThemeUI(colorTheme)

        // 显示颜色变更动画
        animateColorPickerCardChange(getColorFromTheme(colorTheme))

        // 提示用户重启应用以应用新主题
        Toast.makeText(requireContext(), "颜色主题已更改，重启应用后生效", Toast.LENGTH_SHORT).show()

        // 通知设置变更
        notifyNavigationBack()
    }

    /**
     * 颜色选择器卡片变更动画
     */
    private fun animateColorPickerCardChange(newColor: Int) {
        // 获取当前主题颜色
        val currentColor = ContextCompat.getColor(requireContext(), R.color.primary)

        // 创建颜色渐变动画
        val colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, newColor, currentColor)
        colorAnim.duration = 800 // 动画持续800毫秒
        colorAnim.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
        }

        // 添加卡片脉冲动画
        val scaleDown = ObjectAnimator.ofFloat(colorPickerCard, "scaleY", 1f, 0.96f).apply {
            duration = 150
        }

        val scaleUp = ObjectAnimator.ofFloat(colorPickerCard, "scaleY", 0.96f, 1.03f).apply {
            duration = 300
            interpolator = OvershootInterpolator(2f)
        }

        val scaleNormal = ObjectAnimator.ofFloat(colorPickerCard, "scaleY", 1.03f, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
        }

        val animSequence = AnimatorSet()
        animSequence.playSequentially(scaleDown, scaleUp, scaleNormal)
        animSequence.start()
        colorAnim.start()
    }

    private fun getColorFromTheme(colorTheme: String): Int {
        return when (colorTheme) {
            "green" -> ContextCompat.getColor(requireContext(), R.color.theme_green)
            "purple" -> ContextCompat.getColor(requireContext(), R.color.theme_purple)
            "orange" -> ContextCompat.getColor(requireContext(), R.color.theme_orange)
            "pink" -> ContextCompat.getColor(requireContext(), R.color.theme_pink)
            else -> ContextCompat.getColor(requireContext(), R.color.primary)
        }
    }
}
package com.example.chatapp.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.chatapp.R
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.utils.HapticUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class ProactiveMessageSettingsActivity : AppCompatActivity() {

    private val TAG = "ProactiveMessages"
    private lateinit var settingsManager: SettingsManager

    // 视图引用
    private lateinit var enableSwitch: SwitchMaterial
    private lateinit var saveButton: MaterialButton
    private lateinit var enableCard: MaterialCardView
    private lateinit var intervalCard: MaterialCardView

    // 新增滑动轴组件
    private lateinit var intervalSlider: Slider
    private lateinit var intervalValueText: TextView

    // 设置值
    private var isEnabled = true
    private var selectedInterval = 12 // 默认12小时

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_proactive_message_settings)

        // 初始化SettingsManager
        settingsManager = SettingsManager(this)

        // 初始化视图
        initViews()

        // 从设置中加载当前值
        loadCurrentSettings()

        // 设置监听器
        setupListeners()

        // 应用入场动画
        playEntranceAnimation()
    }

    private fun initViews() {
        // 初始化卡片容器
        enableCard = findViewById(R.id.enableCard)
        intervalCard = findViewById(R.id.intervalCard)

        // 初始化主开关
        enableSwitch = findViewById(R.id.enableSwitch)

        // 初始化滑动轴和值显示文本
        intervalSlider = findViewById(R.id.intervalSlider)
        intervalValueText = findViewById(R.id.intervalValueText)

        // 初始化保存按钮
        saveButton = findViewById(R.id.saveButton)
    }

    private fun playEntranceAnimation() {
        // 确保所有元素一开始时透明度为0
        val views = listOf(enableCard, intervalCard, saveButton)
        views.forEach { it.alpha = 0f }

        // 依次播放淡入和上移动画
        views.forEachIndexed { index, view ->
            view.translationY = 50f // 开始时稍微下移

            // 延迟执行动画，递增延迟
            view.postDelayed({
                // 淡入动画
                val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                    duration = 500
                    interpolator = DecelerateInterpolator()
                }

                // 上移动画
                val slideUp = ObjectAnimator.ofFloat(view, "translationY", 50f, 0f).apply {
                    duration = 500
                    interpolator = DecelerateInterpolator()
                }

                // 组合动画
                AnimatorSet().apply {
                    playTogether(fadeIn, slideUp)
                    start()
                }
            }, 100L * index) // 每个视图之间间隔100毫秒
        }
    }

    private fun loadCurrentSettings() {
        try {
            // 加载主开关状态
            isEnabled = settingsManager.proactiveMessagesEnabled
            enableSwitch.isChecked = isEnabled

            // 加载间隔设置
            selectedInterval = settingsManager.proactiveMessagesInterval

            // 设置滑动轴的当前值
            intervalSlider.value = selectedInterval.toFloat()

            // 更新显示文本
            updateIntervalValueText(selectedInterval)

            // 更新UI启用状态
            updateUIEnabledState(isEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "加载设置失败: ${e.message}", e)
            // 出错时使用默认值
            isEnabled = true
            selectedInterval = 12

            // 设置默认值到UI
            intervalSlider.value = selectedInterval.toFloat()
            updateIntervalValueText(selectedInterval)
        }
    }

    private fun setupListeners() {
        // 开关监听器
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            // 添加震动反馈
            HapticUtils.performViewHapticFeedback(enableSwitch)

            isEnabled = isChecked
            updateUIEnabledState(isEnabled)

            // 添加滑动切换动画
            animateSwitchChange(isChecked)
        }

        // 滑动轴监听器
        intervalSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                // 提供轻微震动反馈
                HapticUtils.performViewHapticFeedback(intervalSlider, false)
            }

            // 更新所选值
            selectedInterval = value.toInt()

            // 更新显示文本
            updateIntervalValueText(selectedInterval)
        }

        // 滑动轴触摸监听器，在滑动结束时提供反馈
        intervalSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // 开始滑动时的操作
            }

            override fun onStopTrackingTouch(slider: Slider) {
                // 结束滑动时提供震动反馈
                HapticUtils.performViewHapticFeedback(slider)

                // 播放轻微的脉冲动画
                playSliderValueChangeAnimation()
            }
        })

        // 保存按钮点击监听器
        saveButton.setOnClickListener {
            playEnhancedButtonAnimation(it) {
                saveSettings()
            }
        }
    }

    /**
     * 更新间隔值显示文本
     */
    private fun updateIntervalValueText(hours: Int) {
        intervalValueText.text = "${hours}小时"
    }

    /**
     * 播放滑动值变化动画
     */
    private fun playSliderValueChangeAnimation() {
        // 为值文本应用缩放动画
        val scaleX = ObjectAnimator.ofFloat(intervalValueText, "scaleX", 1f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(intervalValueText, "scaleY", 1f, 1.2f, 1f)

        val animSet = AnimatorSet()
        animSet.playTogether(scaleX, scaleY)
        animSet.duration = 300
        animSet.interpolator = OvershootInterpolator()
        animSet.start()

        // 同时改变文本颜色
        val originalTextColor = intervalValueText.currentTextColor
        val highlightColor = ContextCompat.getColor(this, R.color.primary)

        val colorAnim = ObjectAnimator.ofArgb(
            intervalValueText,
            "textColor",
            originalTextColor,
            highlightColor,
            originalTextColor
        )
        colorAnim.duration = 400
        colorAnim.start()
    }

    private fun updateUIEnabledState(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.5f

        // 设置滑动轴的启用状态
        intervalSlider.isEnabled = enabled

        // 更新滑动轴和文本的alpha值
        intervalSlider.alpha = alpha
        intervalValueText.alpha = alpha

        // 通过动画改变间隔卡片的区域透明度
        val animDuration = 300L
        ObjectAnimator.ofFloat(intervalCard, "alpha", intervalCard.alpha, alpha).apply {
            duration = animDuration
            start()
        }
    }

    private fun animateSwitchChange(isChecked: Boolean) {
        // 根据开关状态设置间隔卡片的显示动画
        val animDuration = 300L

        if (isChecked) {
            // 先改变透明度再改变高度，显示间隔卡片区域
            ObjectAnimator.ofFloat(intervalCard, "alpha", 0.5f, 1.0f).apply {
                duration = animDuration
                start()
            }
        } else {
            // 降低透明度表示禁用
            ObjectAnimator.ofFloat(intervalCard, "alpha", 1.0f, 0.5f).apply {
                duration = animDuration
                start()
            }
        }
    }

    private fun saveSettings() {
        try {
            // 保存设置
            settingsManager.proactiveMessagesEnabled = isEnabled
            settingsManager.proactiveMessagesInterval = selectedInterval

            // 显示成功提示
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()

            // 直接触发退出并应用滑动动画
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)

        } catch (e: Exception) {
            Log.e(TAG, "保存设置失败: ${e.message}", e)
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playExitAnimation(completion: () -> Unit) {
        // 视图淡出和下滑动画
        val views = listOf(saveButton, intervalCard, enableCard)

        // 减少动画持续时间
        val animDuration = 100L

        // 所有视图同时开始动画
        views.forEach { view ->
            // 淡出动画
            val fadeOut = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
                duration = animDuration
                interpolator = AccelerateInterpolator()
            }

            // 下滑动画
            val slideDown = ObjectAnimator.ofFloat(view, "translationY", 0f, 30f).apply { // 减少滑动距离
                duration = animDuration
                interpolator = AccelerateInterpolator()
            }

            // 组合动画
            AnimatorSet().apply {
                playTogether(fadeOut, slideDown)
                start()
            }
        }

        saveButton.postDelayed({
            completion()
        }, 150L)
    }

    /**
     * 按钮动画
     */
    private fun playEnhancedButtonAnimation(view: View, action: () -> Unit) {
        // 添加震动反馈
        HapticUtils.performViewHapticFeedback(view)

        // 第一阶段：快速缩小
        val scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 0.92f)
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 0.92f)
        scaleDownX.duration = 120
        scaleDownY.duration = 120
        scaleDownX.interpolator = AccelerateDecelerateInterpolator()
        scaleDownY.interpolator = AccelerateDecelerateInterpolator()

        // 第二阶段：弹性恢复
        val scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 1.03f)
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 1.03f)
        scaleUpX.duration = 250
        scaleUpY.duration = 250
        scaleUpX.interpolator = OvershootInterpolator(3f)
        scaleUpY.interpolator = OvershootInterpolator(3f)

        // 第三阶段：回到原始尺寸
        val normalizeX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f)
        val normalizeY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f)
        normalizeX.duration = 120
        normalizeY.duration = 120

        // 同时添加淡入淡出效果
        val fadeOut = ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.8f)
        fadeOut.duration = 120

        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0.8f, 1.0f)
        fadeIn.duration = 250

        // 播放序列动画
        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY, fadeOut)

        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY, fadeIn)

        val normalize = AnimatorSet()
        normalize.playTogether(normalizeX, normalizeY)

        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(scaleDown, scaleUp, normalize)
        animatorSet.start()

        // 动画结束后执行操作
        view.postDelayed(action, 400)
    }

    override fun onBackPressed() {
        // 应用淡出退出动画
        playExitAnimation {
            super.onBackPressed()
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
        }
    }
}

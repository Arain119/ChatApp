package com.example.chatapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.doOnPreDraw
import com.example.chatapp.R
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.utils.HapticUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * AI人设Fragment
 */
class PersonaFragment : BaseSettingsSubFragment() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var nameCard: MaterialCardView
    private lateinit var personaCard: MaterialCardView
    private lateinit var tipsCard: MaterialCardView
    private lateinit var saveButton: MaterialButton

    override fun getTitle(): String = "AI人设"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_persona, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 延迟动画以等待视图完全绘制
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        // 获取视图引用
        nameCard = view.findViewById(R.id.nameCard)
        personaCard = view.findViewById(R.id.personaCard)
        tipsCard = view.findViewById(R.id.tipsCard)
        saveButton = view.findViewById(R.id.save_button)

        // 获取当前人设文本和AI姓名
        val currentPersona = settingsManager.aiPersona
        val currentAiName = settingsManager.aiName

        // 获取输入字段
        val personaInputField = view.findViewById<EditText>(R.id.persona_input)
        val aiNameInputField = view.findViewById<EditText>(R.id.ai_name_input)

        // 设置现有值
        personaInputField.setText(currentPersona)
        aiNameInputField.setText(currentAiName)

        // 为UI元素添加动画
        animatePageElements()

        // 设置卡片交互动画
        setupCardInteractions()

        // 设置保存按钮点击事件
        saveButton.setOnClickListener {
            // 添加触觉反馈
            HapticUtils.performViewHapticFeedback(it)

            // 添加按钮按下动画
            animateButtonPress(saveButton)

            // 延迟执行保存操作，让动画有时间完成
            saveButton.postDelayed({
                val newPersona = personaInputField.text.toString().trim()
                val newAiName = aiNameInputField.text.toString().trim()

                // 如果AI姓名为空，设置为默认值
                settingsManager.aiPersona = newPersona
                settingsManager.aiName = if (newAiName.isEmpty()) "ChatGPT" else newAiName

                Toast.makeText(requireContext(), "AI设置已保存", Toast.LENGTH_SHORT).show()

                // 先保存变更，再返回
                notifyNavigationBack()
            }, 200)
        }
    }

    /**
     * 为页面元素添加入场动画
     */
    private fun animatePageElements() {
        // 获取标题文本
        val titleText = view?.findViewById<TextView>(R.id.persona_title)

        // 标题文本动画
        titleText?.let {
            it.alpha = 0f
            it.translationY = -50f

            ObjectAnimator.ofFloat(it, "alpha", 0f, 1f).apply {
                duration = 600
                interpolator = DecelerateInterpolator()
                start()
            }

            ObjectAnimator.ofFloat(it, "translationY", -50f, 0f).apply {
                duration = 600
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        // 卡片初始状态设置
        val cards = listOf(nameCard, personaCard, tipsCard)
        cards.forEach { card ->
            card.alpha = 0f
            card.translationY = 100f
        }

        // 依次添加卡片动画
        cards.forEachIndexed { index, card ->
            val translateY = ObjectAnimator.ofFloat(card, "translationY", 100f, 0f)
            val alpha = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f)

            val animSet = AnimatorSet()
            animSet.playTogether(translateY, alpha)
            animSet.duration = 500
            animSet.interpolator = DecelerateInterpolator(1.5f)
            animSet.startDelay = 300L + (index * 150) // 错开动画开始时间
            animSet.start()
        }

        // 保存按钮动画
        saveButton.alpha = 0f
        saveButton.scaleX = 0.8f
        saveButton.scaleY = 0.8f

        val buttonAnimSet = AnimatorSet()
        val buttonAlpha = ObjectAnimator.ofFloat(saveButton, "alpha", 0f, 1f)
        val buttonScaleX = ObjectAnimator.ofFloat(saveButton, "scaleX", 0.8f, 1f)
        val buttonScaleY = ObjectAnimator.ofFloat(saveButton, "scaleY", 0.8f, 1f)

        buttonAnimSet.playTogether(buttonAlpha, buttonScaleX, buttonScaleY)
        buttonAnimSet.duration = 500
        buttonAnimSet.interpolator = OvershootInterpolator(1.2f)
        buttonAnimSet.startDelay = 700L // 最后出现
        buttonAnimSet.start()
    }

    /**
     * 设置卡片交互动画
     */
    private fun setupCardInteractions() {
        // 为卡片添加触摸反馈
        val cards = listOf(nameCard, personaCard, tipsCard)

        cards.forEach { card ->
            card.setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        // 轻微缩小效果
                        ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.98f).apply {
                            duration = 150
                            interpolator = DecelerateInterpolator()
                            start()
                        }
                        ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.98f).apply {
                            duration = 150
                            interpolator = DecelerateInterpolator()
                            start()
                        }
                        // 添加轻微阴影增加效果
                        (view as? MaterialCardView)?.cardElevation = 6f
                        true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        // 恢复原始大小
                        ObjectAnimator.ofFloat(view, "scaleX", 0.98f, 1f).apply {
                            duration = 200
                            interpolator = OvershootInterpolator(1.5f)
                            start()
                        }
                        ObjectAnimator.ofFloat(view, "scaleY", 0.98f, 1f).apply {
                            duration = 200
                            interpolator = OvershootInterpolator(1.5f)
                            start()
                        }
                        // 恢复原始阴影
                        (view as? MaterialCardView)?.cardElevation = 2f
                        false
                    }
                    else -> false
                }
            }
        }
    }

    /**
     * 为按钮添加按压动画
     */
    private fun animateButtonPress(button: View) {
        val scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.92f)
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.92f)
        val scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 0.92f, 1.05f)
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.92f, 1.05f)
        val scaleNormalX = ObjectAnimator.ofFloat(button, "scaleX", 1.05f, 1f)
        val scaleNormalY = ObjectAnimator.ofFloat(button, "scaleY", 1.05f, 1f)

        // 按下动画
        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY)
        scaleDown.duration = 100

        // 弹起超过1的动画
        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY)
        scaleUp.duration = 100
        scaleUp.interpolator = DecelerateInterpolator()

        // 恢复到1的动画
        val scaleNormal = AnimatorSet()
        scaleNormal.playTogether(scaleNormalX, scaleNormalY)
        scaleNormal.duration = 100
        scaleNormal.interpolator = OvershootInterpolator()

        // 按顺序播放动画
        val sequence = AnimatorSet()
        sequence.playSequentially(scaleDown, scaleUp, scaleNormal)
        sequence.start()
    }
}
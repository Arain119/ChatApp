package com.example.chatapp.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.animation.doOnEnd
import androidx.core.view.doOnPreDraw
import com.example.chatapp.R
import com.example.chatapp.data.ApiConfig
import com.example.chatapp.utils.HapticUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ApiManagementActivity : AppCompatActivity() {

    // 聊天模型API输入字段
    private lateinit var chatApiUrlInput: TextInputEditText
    private lateinit var chatApiKeyInput: TextInputEditText

    // 记忆模型API输入字段
    private lateinit var memoryApiUrlInput: TextInputEditText
    private lateinit var memoryApiKeyInput: TextInputEditText
    private lateinit var memoryModelNameInput: TextInputEditText

    // 卡片视图
    private lateinit var chatApiCard: MaterialCardView
    private lateinit var memoryApiCard: MaterialCardView

    // 标题文本
    private lateinit var titleText: TextView

    // 提示文本
    private lateinit var hintText: TextView

    // 保存按钮和容器
    private lateinit var saveButton: MaterialButton
    private lateinit var buttonContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_management)

        // 延迟动画执行，等待视图完全绘制
        postponeEnterTransition()

        // 设置工具栏
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // 禁用标题显示

        // 初始化视图
        initViews()

        // 加载现有设置
        loadSettings()

        // 设置保存按钮点击事件
        saveButton.setOnClickListener {
            // 保存按钮动画效果
            animateSaveButton(it)
            // 带有延迟的保存操作，让动画有时间完成
            it.postDelayed({ saveSettings() }, 200)
        }

        // 开始动画
        findViewById<View>(android.R.id.content).doOnPreDraw {
            startPostponedEnterTransition()
            startEntranceAnimations()
        }
    }

    private fun initViews() {
        // 输入字段
        chatApiUrlInput = findViewById(R.id.chat_api_url)
        chatApiKeyInput = findViewById(R.id.chat_api_key)
        memoryApiUrlInput = findViewById(R.id.memory_api_url)
        memoryApiKeyInput = findViewById(R.id.memory_api_key)
        memoryModelNameInput = findViewById(R.id.memory_model_name)

        // 卡片和标题
        chatApiCard = findViewById(R.id.chat_api_card)
        memoryApiCard = findViewById(R.id.memory_api_card)
        titleText = findViewById(R.id.title_text)
        hintText = findViewById(R.id.hint_text)

        // 按钮和容器
        saveButton = findViewById(R.id.save_button)
        buttonContainer = findViewById(R.id.button_container)

        // 设置动画初始状态
        chatApiCard.alpha = 0f
        chatApiCard.translationY = 100f

        memoryApiCard.alpha = 0f
        memoryApiCard.translationY = 100f

        buttonContainer.alpha = 0f
        buttonContainer.translationY = 100f

        titleText.alpha = 0f
        titleText.translationY = -50f
    }

    private fun loadSettings() {
        // 加载聊天模型API设置
        chatApiUrlInput.setText(ApiConfig.getChatApiUrl(this))
        chatApiKeyInput.setText(ApiConfig.getChatApiKey(this))

        // 加载记忆模型API设置
        memoryApiUrlInput.setText(ApiConfig.getMemoryApiUrl(this))
        memoryApiKeyInput.setText(ApiConfig.getMemoryApiKey(this))
        memoryModelNameInput.setText(ApiConfig.getMemoryModelName(this))
    }

    private fun saveSettings() {
        // 获取并保存聊天模型API设置
        val chatApiUrl = chatApiUrlInput.text.toString().trim()
        val chatApiKey = chatApiKeyInput.text.toString().trim()

        // 获取并保存记忆模型API设置
        val memoryApiUrl = memoryApiUrlInput.text.toString().trim()
        val memoryApiKey = memoryApiKeyInput.text.toString().trim()
        val memoryModelName = memoryModelNameInput.text.toString().trim()

        // 验证输入
        if (chatApiUrl.isEmpty() || memoryApiUrl.isEmpty() || memoryModelName.isEmpty()) {
            Toast.makeText(this, "请填写所有必填字段", Toast.LENGTH_SHORT).show()
            // 对空字段添加震动动画
            if (chatApiUrl.isEmpty()) shakeView(findViewById(R.id.chat_api_url_layout))
            if (memoryApiUrl.isEmpty()) shakeView(findViewById(R.id.memory_api_url_layout))
            if (memoryModelName.isEmpty()) shakeView(findViewById(R.id.memory_model_name_layout))
            return
        }

        // 保存设置
        ApiConfig.saveChatApiSettings(this, chatApiUrl, chatApiKey)
        ApiConfig.saveMemoryApiSettings(this, memoryApiUrl, memoryApiKey, memoryModelName)

        // 提供震动反馈
        HapticUtils.performViewHapticFeedback(saveButton)

        // 显示成功提示并带有动画
        showSuccessToast()

        // 关闭当前页面
        finishWithAnimation()
    }

    /**
     * 开始入场动画
     */
    private fun startEntranceAnimations() {
        // 标题文本动画
        val titleAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(titleText, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(titleText, "translationY", -50f, 0f)
            )
            duration = 500
            interpolator = DecelerateInterpolator()
        }

        // 聊天API卡片动画
        val chatCardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(chatApiCard, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(chatApiCard, "translationY", 100f, 0f)
            )
            duration = 600
            interpolator = DecelerateInterpolator(1.5f)
            startDelay = 200L
        }

        // 记忆API卡片动画
        val memoryCardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(memoryApiCard, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(memoryApiCard, "translationY", 100f, 0f)
            )
            duration = 600
            interpolator = DecelerateInterpolator(1.5f)
            startDelay = 300L
        }

        // 按钮容器动画
        val buttonAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(buttonContainer, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(buttonContainer, "translationY", 100f, 0f)
            )
            duration = 600
            interpolator = DecelerateInterpolator(1.5f)
            startDelay = 400L
        }

        // 提示文本渐入
        val hintAnim = ObjectAnimator.ofFloat(hintText, "alpha", 0f, 1f).apply {
            duration = 800
            startDelay = 600L
        }

        // 播放所有动画
        AnimatorSet().apply {
            playTogether(titleAnim, chatCardAnim, memoryCardAnim, buttonAnim, hintAnim)
            start()
        }

        // 输入框内部元素动画
        animateInputFields()
    }

    /**
     * 输入框内部元素动画
     */
    private fun animateInputFields() {
        // 获取所有输入布局
        val inputLayouts = listOf(
            findViewById<TextInputLayout>(R.id.chat_api_url_layout),
            findViewById<TextInputLayout>(R.id.chat_api_key_layout),
            findViewById<TextInputLayout>(R.id.memory_api_url_layout),
            findViewById<TextInputLayout>(R.id.memory_api_key_layout),
            findViewById<TextInputLayout>(R.id.memory_model_name_layout)
        )

        // 为每个输入框添加微妙的动画
        inputLayouts.forEachIndexed { index, layout ->
            // 初始状态
            layout.alpha = 0f
            layout.scaleX = 0.9f
            layout.scaleY = 0.9f

            // 创建动画
            val inputAnim = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(layout, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(layout, "scaleX", 0.9f, 1f),
                    ObjectAnimator.ofFloat(layout, "scaleY", 0.9f, 1f)
                )
                duration = 500
                interpolator = OvershootInterpolator(0.8f)
                startDelay = 500L + (index * 100L) // 错开时间
                start()
            }
        }
    }

    /**
     * 保存按钮动画
     */
    private fun animateSaveButton(button: View) {
        // 添加触觉反馈
        HapticUtils.performViewHapticFeedback(button)

        // 按下缩放效果
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.92f),
                ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.92f)
            )
            duration = 100
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 释放弹起效果
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(button, "scaleX", 0.92f, 1f),
                ObjectAnimator.ofFloat(button, "scaleY", 0.92f, 1f)
            )
            duration = 200
            interpolator = OvershootInterpolator(3f)
        }

        // 播放动画序列
        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp)
            start()
        }
    }

    /**
     * 错误提示动画
     * 对空字段进行震动效果
     */
    private fun shakeView(view: View) {
        // 添加触觉反馈
        HapticUtils.performViewHapticFeedback(view, true)

        // 创建水平震动动画
        val shakeAnim = ObjectAnimator.ofFloat(view, "translationX", 0f, -15f, 15f, -10f, 10f, -5f, 5f, 0f).apply {
            duration = 500
            start()
        }

        // 高亮错误提示
        if (view is TextInputLayout) {
            view.error = "必填字段"
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2000
                addUpdateListener {
                    // 动画过程中的效果
                }
                doOnEnd {
                    // 动画结束后清除错误
                    view.error = null
                }
                start()
            }
        }
    }

    /**
     * 显示成功提示，带有动画效果
     */
    private fun showSuccessToast() {
        val toast = Toast.makeText(this, "API设置已保存", Toast.LENGTH_SHORT)
        toast.show()

        // 为保存按钮添加成功动画
        val successAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(saveButton, "scaleX", 1f, 1.1f, 1f),
                ObjectAnimator.ofFloat(saveButton, "scaleY", 1f, 1.1f, 1f),
                ObjectAnimator.ofFloat(saveButton, "alpha", 1f, 0.7f, 1f)
            )
            duration = 800
            interpolator = OvershootInterpolator(2f)
            start()
        }
    }

    /**
     * 带动画效果的退出
     */
    private fun finishWithAnimation() {
        // 创建所有视图的退出动画
        val exitAnim = AnimatorSet()

        // 为每个视图添加退出动画
        val animations = mutableListOf<Animator>().apply {
            // 按钮先退出
            add(ObjectAnimator.ofFloat(buttonContainer, "alpha", 1f, 0f))
            add(ObjectAnimator.ofFloat(buttonContainer, "translationY", 0f, 50f))

            // 然后是卡片
            add(ObjectAnimator.ofFloat(memoryApiCard, "alpha", 1f, 0f))
            add(ObjectAnimator.ofFloat(memoryApiCard, "translationY", 0f, 50f))

            add(ObjectAnimator.ofFloat(chatApiCard, "alpha", 1f, 0f))
            add(ObjectAnimator.ofFloat(chatApiCard, "translationY", 0f, 50f))

            // 最后是标题
            add(ObjectAnimator.ofFloat(titleText, "alpha", 1f, 0f))
            add(ObjectAnimator.ofFloat(titleText, "translationY", 0f, -50f))
        }

        // 播放退出动画
        exitAnim.apply {
            playTogether(animations)
            duration = 300
            doOnEnd {
                super.finish()
                overridePendingTransition(0, 0)
            }
            start()
        }
    }

    /**
     * 覆盖返回操作，添加退出动画
     */
    override fun onBackPressed() {
        finishWithAnimation()
    }
}
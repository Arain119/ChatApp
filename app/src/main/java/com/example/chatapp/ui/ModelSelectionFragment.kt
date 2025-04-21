package com.example.chatapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import com.example.chatapp.R
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.utils.HapticUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

/**
 * AI模型选择Fragment
 */
class ModelSelectionFragment : BaseSettingsSubFragment() {
    private lateinit var settingsManager: SettingsManager
    private var modelOptionsContainer: LinearLayout? = null

    // TAG常量
    companion object {
        private const val TAG = "ModelSelectionFragment"
    }

    override fun getTitle(): String = "AI模型选择"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_model_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 延迟动画以等待视图完全绘制
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        modelOptionsContainer = view.findViewById(R.id.modelOptionsContainer)

        // 获取当前选择的模型
        val currentModel = settingsManager.modelType

        // 设置模型卡片的圆角涟漪效果
        setupModelCards(view)

        // 加载自定义模型
        loadCustomModels()

        // 设置选中状态
        updateModelSelection(currentModel)

        // 添加主动消息设置卡片
        addProactiveMessageSettingsCard(view)

        // 添加入场动画
        animateUI(view)
    }

    /**
     * 整体UI的入场动画
     */
    private fun animateUI(view: View) {
        try {
            // 查找标题文本
            val titleTexts = findTextViewsInContainer(view, 2)
            val title = titleTexts.getOrNull(0)

            // 查找主卡片
            val mainCard = findFirstMaterialCardView(view)

            // 查找底部描述文本
            val bottomTexts = findBottomTextViews(view)
            val description = bottomTexts.lastOrNull()

            // 仅设置标题动画
            title?.let {
                it.alpha = 0f
                it.translationY = -50f

                ObjectAnimator.ofFloat(it, "alpha", 0f, 1f).apply {
                    duration = 600
                    interpolator = DecelerateInterpolator(1.5f)
                    start()
                }

                ObjectAnimator.ofFloat(it, "translationY", -50f, 0f).apply {
                    duration = 600
                    interpolator = DecelerateInterpolator(1.5f)
                    start()
                }
            }

            // 主卡片动画
            mainCard?.let {
                it.alpha = 0f
                it.translationY = 100f
                it.scaleX = 0.95f
                it.scaleY = 0.95f

                val cardAnim = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(it, "alpha", 0f, 1f),
                        ObjectAnimator.ofFloat(it, "translationY", 100f, 0f),
                        ObjectAnimator.ofFloat(it, "scaleX", 0.95f, 1f),
                        ObjectAnimator.ofFloat(it, "scaleY", 0.95f, 1f)
                    )
                    duration = 800
                    startDelay = 300
                    interpolator = DecelerateInterpolator(1.3f)
                }
                cardAnim.start()

                // 开始卡片内元素的动画
                animateCardContents(it)
            }

            // 底部说明文本动画
            description?.let {
                it.alpha = 0f

                ObjectAnimator.ofFloat(it, "alpha", 0f, 1f).apply {
                    duration = 500
                    startDelay = 1000
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "动画初始化失败: ${e.message}", e)
        }
    }

    /**
     * 查找容器中的前N个TextView
     */
    private fun findTextViewsInContainer(container: View, count: Int): List<TextView> {
        val result = mutableListOf<TextView>()

        // 如果容器本身是TextView，添加它
        if (container is TextView) {
            result.add(container)
        }

        // 如果容器是ViewGroup，递归查找子视图
        if (container is ViewGroup) {
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)

                if (child is TextView) {
                    result.add(child)
                    if (result.size >= count) break
                } else if (child is ViewGroup) {
                    // 递归查找，但限制深度以提高效率
                    val childTexts = findTextViewsInContainer(child, count - result.size)
                    result.addAll(childTexts)
                    if (result.size >= count) break
                }
            }
        }

        return result.take(count)
    }

    /**
     * 查找第一个MaterialCardView
     */
    private fun findFirstMaterialCardView(container: View): MaterialCardView? {
        // 如果容器本身是MaterialCardView，返回它
        if (container is MaterialCardView) {
            return container
        }

        // 如果容器是ViewGroup，递归查找子视图
        if (container is ViewGroup) {
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)

                if (child is MaterialCardView) {
                    return child
                } else if (child is ViewGroup) {
                    val result = findFirstMaterialCardView(child)
                    if (result != null) return result
                }
            }
        }

        return null
    }

    /**
     * 查找底部的TextView
     */
    private fun findBottomTextViews(container: View): List<TextView> {
        val result = mutableListOf<TextView>()

        // 从最底部的LinearLayout中查找TextView
        if (container is ViewGroup) {
            // 获取根LinearLayout的最后几个子视图
            val childCount = container.childCount
            if (childCount > 0) {
                for (i in (childCount - 1) downTo 0) {
                    val child = container.getChildAt(i)

                    if (child is TextView) {
                        result.add(child)
                    } else if (child is ViewGroup) {
                        result.addAll(findTextViewsInContainer(child, 2))
                    }

                    if (result.size >= 2) break
                }
            }
        }

        return result
    }

    /**
     * 为卡片内部元素添加顺序动画
     */
    private fun animateCardContents(cardView: View) {
        // 获取模型卡片
        val gpt4oMiniCard = view?.findViewById<View>(R.id.model_gpt4o_mini)
        val gpt4oCard = view?.findViewById<View>(R.id.model_gpt4o)
        val addModelButton = view?.findViewById<View>(R.id.add_model_button)

        // 创建卡片列表 - 只包含模型卡片，不包括标题和子标题
        val cards = mutableListOf<View>()
        if (gpt4oMiniCard != null) cards.add(gpt4oMiniCard)
        if (gpt4oCard != null) cards.add(gpt4oCard)

        // 添加自定义模型卡片
        modelOptionsContainer?.let { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                // 仅添加布局元素，跳过分隔线
                if (child.layoutParams.height > 2) {
                    cards.add(child)
                }
            }
        }

        // 添加"添加模型"按钮
        if (addModelButton != null) cards.add(addModelButton)

        // 设置初始状态
        cards.forEach { card ->
            card.alpha = 0f
            card.translationX = 100f
        }

        // 依次为每个卡片添加动画
        cards.forEachIndexed { index, card ->
            val fadeIn = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f)
            val slideIn = ObjectAnimator.ofFloat(card, "translationX", 100f, 0f)

            val animSet = AnimatorSet()
            animSet.playTogether(fadeIn, slideIn)
            animSet.duration = 500
            animSet.startDelay = 400L + (index * 100)
            animSet.interpolator = DecelerateInterpolator(1.2f)
            animSet.start()
        }
    }

    /**
     * 设置模型卡片的圆角涟漪效果和点击事件
     */
    private fun setupModelCards(rootView: View) {
        // 获取卡片引用
        val gpt4oMiniCard = rootView.findViewById<View>(R.id.model_gpt4o_mini)
        val gpt4oCard = rootView.findViewById<View>(R.id.model_gpt4o)
        val addModelButton = rootView.findViewById<View>(R.id.add_model_button)

        // 设置卡片列表
        val modelCards = listOf(gpt4oMiniCard, gpt4oCard, addModelButton)

        // 应用圆角涟漪效果和点击事件
        modelCards.forEach { card ->
            // 应用圆角涟漪背景
            card.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_model_ripple)

            // 确保正确剪裁
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                card.clipToOutline = true
            }

            // 应用卡片按压效果
            applyCardPressEffect(card)
        }

        // 设置点击事件
        gpt4oMiniCard.setOnClickListener { v ->
            val currentModel = settingsManager.modelType
            val newModel = SettingsManager.MODEL_GPT4O_MINI
            val shouldVibrate = currentModel != newModel

            handleButtonClickWithAnimation(v, shouldVibrate) {
                updateModel(newModel)
            }
        }

        gpt4oCard.setOnClickListener { v ->
            val currentModel = settingsManager.modelType
            val newModel = SettingsManager.MODEL_GPT4O
            val shouldVibrate = currentModel != newModel

            handleButtonClickWithAnimation(v, shouldVibrate) {
                updateModel(newModel)
            }
        }

        addModelButton.setOnClickListener { v ->
            handleButtonClickWithAnimation(v) {
                showAddModelDialog()
            }
        }
    }

    /**
     * 添加卡片按压效果
     */
    private fun applyCardPressEffect(card: View?) {
        card?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 添加突变动画效果
                    val scaleDown = AnimatorSet()
                    scaleDown.playTogether(
                        ObjectAnimator.ofFloat(v, "scaleX", 0.97f),
                        ObjectAnimator.ofFloat(v, "scaleY", 0.97f),
                        ObjectAnimator.ofFloat(v, "translationZ", 0f, 8f),
                        ObjectAnimator.ofFloat(v, "alpha", 1f, 0.9f)
                    )
                    scaleDown.duration = 150
                    scaleDown.interpolator = AccelerateInterpolator(1.5f)
                    scaleDown.start()

                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 恢复原状，使用OvershootInterpolator实现轻微弹跳效果
                    val scaleUp = AnimatorSet()
                    scaleUp.playTogether(
                        ObjectAnimator.ofFloat(v, "scaleX", 1f),
                        ObjectAnimator.ofFloat(v, "scaleY", 1f),
                        ObjectAnimator.ofFloat(v, "translationZ", 8f, 0f),
                        ObjectAnimator.ofFloat(v, "alpha", 0.9f, 1f)
                    )
                    scaleUp.duration = 300
                    scaleUp.interpolator = OvershootInterpolator(1.2f)
                    scaleUp.start()
                    false
                }
                else -> false
            }
        }
    }

    /**
     * 添加主动消息设置卡片
     */
    private fun addProactiveMessageSettingsCard(view: View) {
        try {
            // 创建主动消息设置卡片视图
            val cardView = layoutInflater.inflate(R.layout.card_proactive_message_settings, null)

            // 应用圆角涟漪背景
            cardView.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_model_ripple)

            // 确保正确剪裁
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cardView.clipToOutline = true
            }

            // 为卡片添加按压效果
            applyCardPressEffect(cardView)

            // 设置卡片点击事件
            cardView.setOnClickListener { v ->
                handleButtonClickWithAnimation(v) {
                    navigateToProactiveMessageSettings()
                }
            }

            // 设置卡片初始状态
            cardView.alpha = 0f
            cardView.translationY = 50f

            // 将卡片添加到布局中
            val container = view.findViewById<ViewGroup>(R.id.additionalOptionsContainer)

            if (container != null) {
                container.addView(cardView)

                // 添加动画效果
                cardView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(600)
                    .setStartDelay(1200)
                    .setInterpolator(DecelerateInterpolator(1.3f))
                    .start()
            } else {
                Log.e(TAG, "找不到添加卡片的容器")
            }

        } catch (e: Exception) {
            Log.e(TAG, "添加主动消息设置卡片失败: ${e.message}")
        }
    }

    /**
     * 导航到主动消息设置页面
     */
    private fun navigateToProactiveMessageSettings() {
        try {
            // 创建并启动Activity
            val intent = Intent(requireContext(), ProactiveMessageSettingsActivity::class.java)
            startActivity(intent)

            // 添加过渡动画
            requireActivity().overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        } catch (e: Exception) {
            Log.e(TAG, "启动主动消息设置Activity失败: ${e.message}", e)
            Toast.makeText(requireContext(), "无法打开主动消息设置", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 使用动画处理按钮点击
     */
    private fun handleButtonClickWithAnimation(view: View, shouldVibrate: Boolean = true, action: () -> Unit) {
        // 添加震动反馈
        if (shouldVibrate) {
            try {
                HapticUtils.performViewHapticFeedback(view, false)
            } catch (e: Exception) {
                // 忽略可能的错误
            }
        }

        // 创建动画集
        val animatorSet = AnimatorSet()

        // 第一阶段：快速缩小
        val scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 0.96f)
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 0.96f)
        val alphaDown = ObjectAnimator.ofFloat(view, "alpha", 0.9f)
        val elevationUp = ObjectAnimator.ofFloat(view, "translationZ", 12f)

        scaleDownX.duration = 100
        scaleDownY.duration = 100
        alphaDown.duration = 100
        elevationUp.duration = 100

        // 第二阶段：反弹恢复
        val scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f)
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f)
        val alphaUp = ObjectAnimator.ofFloat(view, "alpha", 1.0f)
        val elevationDown = ObjectAnimator.ofFloat(view, "translationZ", 0f)

        scaleUpX.duration = 250
        scaleUpY.duration = 250
        alphaUp.duration = 250
        elevationDown.duration = 250
        scaleUpX.interpolator = OvershootInterpolator(2.5f)
        scaleUpY.interpolator = OvershootInterpolator(2.5f)

        // 播放序列动画
        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY, alphaDown, elevationUp)

        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY, alphaUp, elevationDown)

        animatorSet.playSequentially(scaleDown, scaleUp)
        animatorSet.start()

        // 动画结束后执行操作
        view.postDelayed(action, 350)
    }

    /**
     * 更新模型设置并展示动画
     */
    private fun updateModel(model: String) {
        // 保存旧模型，用于动画
        val oldModel = settingsManager.modelType

        // 如果选择了同一个模型，仅展示动画
        if (oldModel == model) {
            // 仅展示确认动画
            showSelectionConfirmationAnimation(getViewForModel(model))
            return
        }

        // 更新模型设置
        settingsManager.modelType = model

        // 更新选中状态并展示过渡动画
        updateModelSelectionWithAnimation(oldModel, model)

        // 返回主设置页面
        view?.postDelayed({
            notifyNavigationBack()
        }, 500) // 延迟返回，让动画有时间完成
    }

    /**
     * 获取指定模型对应的视图
     */
    private fun getViewForModel(model: String): View? {
        return when (model) {
            SettingsManager.MODEL_GPT4O_MINI -> view?.findViewById(R.id.model_gpt4o_mini)
            SettingsManager.MODEL_GPT4O -> view?.findViewById(R.id.model_gpt4o)
            else -> {
                // 查找自定义模型视图
                var customView: View? = null
                modelOptionsContainer?.let { container ->
                    for (i in 0 until container.childCount) {
                        val child = container.getChildAt(i)
                        // 仅检查非分隔线元素
                        if (child.layoutParams.height > 2 && child.tag == model) {
                            customView = child
                            break
                        }
                    }
                }
                customView
            }
        }
    }

    /**
     * 展示选择确认动画
     */
    private fun showSelectionConfirmationAnimation(view: View?) {
        view ?: return

        // 找到检查标记
        val checkView = when (view.id) {
            R.id.model_gpt4o_mini -> view.findViewById<ImageView>(R.id.gpt4o_mini_check)
            R.id.model_gpt4o -> view.findViewById<ImageView>(R.id.gpt4o_check)
            else -> view.findViewById<ImageView>(R.id.custom_model_check)
        }

        checkView ?: return

        // 保存原始状态
        val originalScale = checkView.scaleX
        val originalAlpha = checkView.alpha

        // 创建闪烁动画
        val scaleUp = ObjectAnimator.ofFloat(checkView, "scaleX", originalScale, 1.5f).apply {
            duration = 200
        }
        val scaleUp2 = ObjectAnimator.ofFloat(checkView, "scaleY", originalScale, 1.5f).apply {
            duration = 200
        }
        val scaleDown = ObjectAnimator.ofFloat(checkView, "scaleX", 1.5f, originalScale).apply {
            duration = 200
        }
        val scaleDown2 = ObjectAnimator.ofFloat(checkView, "scaleY", 1.5f, originalScale).apply {
            duration = 200
        }

        // 组合动画
        val scaleUpAnim = AnimatorSet().apply {
            playTogether(scaleUp, scaleUp2)
        }
        val scaleDownAnim = AnimatorSet().apply {
            playTogether(scaleDown, scaleDown2)
        }

        val animSequence = AnimatorSet().apply {
            playSequentially(scaleUpAnim, scaleDownAnim)
        }
        animSequence.start()
    }

    /**
     * 使用动画更新模型选择状态
     */
    private fun updateModelSelectionWithAnimation(oldModel: String, newModel: String) {
        // 获取对应的视图
        val oldView = getViewForModel(oldModel)
        val newView = getViewForModel(newModel)

        // 清除所有选中状态标记
        val rootView = view ?: return
        val gpt4oMiniCheck = rootView.findViewById<ImageView>(R.id.gpt4o_mini_check)
        val gpt4oCheck = rootView.findViewById<ImageView>(R.id.gpt4o_check)

        gpt4oMiniCheck?.visibility = View.GONE
        gpt4oCheck?.visibility = View.GONE

        // 清除自定义模型的选中状态
        modelOptionsContainer?.let { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child.layoutParams.height > 2) { // 非分隔线
                    child.findViewById<ImageView>(R.id.custom_model_check)?.visibility = View.GONE
                }
            }
        }

        // 更新卡片样式
        oldView?.let { resetCardStyle(it) }
        newView?.let { setSelectedCardStyle(it) }

        // 使用动画显示新的选中状态
        val checkView = when {
            newModel == SettingsManager.MODEL_GPT4O_MINI -> gpt4oMiniCheck
            newModel == SettingsManager.MODEL_GPT4O -> gpt4oCheck
            else -> newView?.findViewById<ImageView>(R.id.custom_model_check)
        }

        checkView?.let {
            // 设置初始状态
            it.visibility = View.VISIBLE
            it.alpha = 0f
            it.scaleX = 0f
            it.scaleY = 0f
            it.rotation = -30f  // 添加旋转初始状态

            // 创建增强动画序列
            // 第一阶段：快速放大并旋转
            val phase1 = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(it, "alpha", 0f, 1f).apply {
                        duration = 200
                    },
                    ObjectAnimator.ofFloat(it, "scaleX", 0f, 1.4f).apply {
                        duration = 200
                    },
                    ObjectAnimator.ofFloat(it, "scaleY", 0f, 1.4f).apply {
                        duration = 200
                    },
                    ObjectAnimator.ofFloat(it, "rotation", -30f, 10f).apply {
                        duration = 200
                    }
                )
            }

            // 第二阶段：弹性收缩并完成旋转
            val phase2 = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(it, "scaleX", 1.4f, 1f).apply {
                        duration = 300
                    },
                    ObjectAnimator.ofFloat(it, "scaleY", 1.4f, 1f).apply {
                        duration = 300
                    },
                    ObjectAnimator.ofFloat(it, "rotation", 10f, 0f).apply {
                        duration = 300
                    }
                )
                interpolator = OvershootInterpolator(3f)
            }

            // 组合并播放动画
            val completeAnimation = AnimatorSet()
            completeAnimation.playSequentially(phase1, phase2)
            completeAnimation.start()
        }

        // 为整个卡片添加轻微的亮度闪烁动画
        newView?.let { view ->
            val brightnessUp = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.85f, 1f)
            brightnessUp.duration = 400
            brightnessUp.interpolator = DecelerateInterpolator()
            brightnessUp.start()
        }
    }

    /**
     * 更新模型选择状态
     */
    private fun updateModelSelection(selectedModel: String) {
        // 清除所有选中状态
        val rootView = view ?: return

        val gpt4oMiniCheck = rootView.findViewById<ImageView>(R.id.gpt4o_mini_check)
        val gpt4oCheck = rootView.findViewById<ImageView>(R.id.gpt4o_check)

        gpt4oMiniCheck?.visibility = View.GONE
        gpt4oCheck?.visibility = View.GONE

        // 清除自定义模型的选中状态
        modelOptionsContainer?.let { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child.layoutParams.height > 2) { // 非分隔线
                    child.findViewById<ImageView>(R.id.custom_model_check)?.visibility = View.GONE
                }
            }
        }

        // 高亮显示所选模型项
        highlightSelectedCard(rootView, selectedModel)

        // 设置当前选中状态
        when (selectedModel) {
            SettingsManager.MODEL_GPT4O_MINI -> {
                gpt4oMiniCheck?.visibility = View.VISIBLE
            }
            SettingsManager.MODEL_GPT4O -> {
                gpt4oCheck?.visibility = View.VISIBLE
            }
            else -> {
                // 检查是否是自定义模型
                modelOptionsContainer?.let { container ->
                    for (i in 0 until container.childCount) {
                        val child = container.getChildAt(i)
                        if (child.layoutParams.height > 2) { // 非分隔线
                            val modelName = child.tag as? String
                            if (modelName == selectedModel) {
                                child.findViewById<ImageView>(R.id.custom_model_check)?.visibility = View.VISIBLE
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 高亮显示所选卡片
     */
    private fun highlightSelectedCard(rootView: View, selectedModel: String) {
        // 重置所有卡片样式
        val mini = rootView.findViewById<View>(R.id.model_gpt4o_mini)
        val full = rootView.findViewById<View>(R.id.model_gpt4o)

        resetCardStyle(mini)
        resetCardStyle(full)

        // 重置所有自定义模型卡片
        modelOptionsContainer?.let { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child.layoutParams.height > 2) { // 非分隔线
                    resetCardStyle(child)
                }
            }
        }

        // 设置选中卡片样式
        when (selectedModel) {
            SettingsManager.MODEL_GPT4O_MINI -> {
                setSelectedCardStyle(mini)
            }
            SettingsManager.MODEL_GPT4O -> {
                setSelectedCardStyle(full)
            }
            else -> {
                // 检查自定义模型
                modelOptionsContainer?.let { container ->
                    for (i in 0 until container.childCount) {
                        val child = container.getChildAt(i)
                        if (child.layoutParams.height > 2) { // 非分隔线
                            val modelName = child.tag as? String
                            if (selectedModel == modelName) {
                                setSelectedCardStyle(child)
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 重置卡片样式
     */
    private fun resetCardStyle(card: View?) {
        card?.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_model_ripple)
        card?.elevation = 0f
    }

    /**
     * 设置选中卡片样式
     */
    private fun setSelectedCardStyle(card: View?) {
        card?.background = ContextCompat.getDrawable(requireContext(), R.drawable.model_selected_background)
        // 增加轻微阴影效果
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card?.elevation = 4f
        }
    }

    /**
     * 显示添加模型对话框
     */
    private fun showAddModelDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_model, null)
        builder.setView(dialogView)

        // 创建圆角对话框
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 获取输入框
        val modelNameInput = dialogView.findViewById<TextInputEditText>(R.id.model_name_input)

        // 获取按钮
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnAdd = dialogView.findViewById<MaterialButton>(R.id.btnAdd)

        // 设置按钮点击动画
        applyButtonAnimation(btnCancel)
        applyButtonAnimation(btnAdd)

        // 设置对话框入场动画
        dialogView.alpha = 0f
        dialogView.scaleX = 0.9f
        dialogView.scaleY = 0.9f

        dialog.setOnShowListener {
            val animatorSet = AnimatorSet()
            animatorSet.playTogether(
                ObjectAnimator.ofFloat(dialogView, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(dialogView, "scaleX", 0.9f, 1f),
                ObjectAnimator.ofFloat(dialogView, "scaleY", 0.9f, 1f)
            )
            animatorSet.duration = 300
            animatorSet.interpolator = DecelerateInterpolator(1.5f)
            animatorSet.start()

            // 自动显示键盘
            modelNameInput.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(modelNameInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        // 设置取消按钮
        btnCancel.setOnClickListener { v ->
            // 添加震动反馈
            try {
                HapticUtils.performViewHapticFeedback(v, false)
            } catch (e: Exception) {
                // 忽略错误
            }

            // 播放动画
            playButtonAnimation(v) {
                // 对话框退出动画
                val animatorSet = AnimatorSet()
                animatorSet.playTogether(
                    ObjectAnimator.ofFloat(dialogView, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(dialogView, "scaleX", 1f, 0.9f),
                    ObjectAnimator.ofFloat(dialogView, "scaleY", 1f, 0.9f)
                )
                animatorSet.duration = 200
                animatorSet.interpolator = AccelerateInterpolator(1.5f)
                animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        dialog.dismiss()
                    }
                })
                animatorSet.start()
            }
        }

        // 设置添加按钮
        btnAdd.setOnClickListener { v ->
            // 添加震动反馈
            try {
                HapticUtils.performViewHapticFeedback(v, false)
            } catch (e: Exception) {
                // 忽略错误
            }

            val modelName = modelNameInput.text.toString().trim()
            if (!TextUtils.isEmpty(modelName)) {
                playButtonAnimation(v) {
                    // 成功添加动画
                    val successAnimSet = AnimatorSet()
                    successAnimSet.playTogether(
                        ObjectAnimator.ofFloat(dialogView, "alpha", 1f, 0f),
                        ObjectAnimator.ofFloat(dialogView, "scaleX", 1f, 1.05f),
                        ObjectAnimator.ofFloat(dialogView, "scaleY", 1f, 1.05f)
                    )
                    successAnimSet.duration = 200
                    successAnimSet.interpolator = AccelerateInterpolator()
                    successAnimSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            addCustomModel(modelName)
                            dialog.dismiss()
                        }
                    })
                    successAnimSet.start()
                }
            } else {
                // 显示错误动画
                val shakeAnimation = ObjectAnimator.ofFloat(modelNameInput, "translationX",
                    0f, -15f, 15f, -10f, 10f, -5f, 5f, 0f)
                shakeAnimation.duration = 500
                shakeAnimation.start()

                modelNameInput.error = "模型名称不能为空"
                // 轻微震动提示错误
                try {
                    HapticUtils.performHapticFeedback(requireContext())
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
        }

        // 显示对话框
        dialog.show()
    }

    /**
     * 应用按钮动画效果
     */
    private fun applyButtonAnimation(button: View) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val scaleDown = AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(v, "scaleX", 0.95f),
                            ObjectAnimator.ofFloat(v, "scaleY", 0.95f),
                            ObjectAnimator.ofFloat(v, "alpha", 0.9f)
                        )
                        duration = 100
                        interpolator = AccelerateInterpolator()
                    }
                    scaleDown.start()
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val scaleUp = AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(v, "scaleX", 1f),
                            ObjectAnimator.ofFloat(v, "scaleY", 1f),
                            ObjectAnimator.ofFloat(v, "alpha", 1f)
                        )
                        duration = 200
                        interpolator = OvershootInterpolator(1.5f)
                    }
                    scaleUp.start()
                    false
                }
                else -> false
            }
        }
    }

    /**
     * 播放按钮动画，然后执行操作
     */
    private fun playButtonAnimation(button: View, action: () -> Unit) {
        // 第一阶段：快速缩小
        val scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 0.9f)
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 0.9f)
        val alphaDown = ObjectAnimator.ofFloat(button, "alpha", 0.8f)
        scaleDownX.duration = 100
        scaleDownY.duration = 100
        alphaDown.duration = 100

        // 第二阶段：反弹恢复
        val scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 1.0f)
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 1.0f)
        val alphaUp = ObjectAnimator.ofFloat(button, "alpha", 1.0f)
        scaleUpX.duration = 250
        scaleUpY.duration = 250
        alphaUp.duration = 250
        scaleUpX.interpolator = OvershootInterpolator(3f)
        scaleUpY.interpolator = OvershootInterpolator(3f)

        // 播放序列动画
        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY, alphaDown)

        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY, alphaUp)

        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(scaleDown, scaleUp)
        animatorSet.start()

        // 动画结束后执行操作
        button.postDelayed(action, 350)
    }

    /**
     * 添加自定义模型
     */
    private fun addCustomModel(modelName: String) {
        // 保存自定义模型
        val customModels = settingsManager.getCustomModels().toMutableList()

        // 检查模型是否已存在
        if (!customModels.contains(modelName) &&
            modelName != SettingsManager.MODEL_GPT4O_MINI &&
            modelName != SettingsManager.MODEL_GPT4O) {

            customModels.add(modelName)
            settingsManager.saveCustomModels(customModels)

            // 添加到UI
            addModelToUI(modelName)

            // 自动选择新添加的模型
            updateModel(modelName)
        } else {
            // 显示错误提示
            Toast.makeText(requireContext(), "模型已存在", Toast.LENGTH_SHORT).show()

            // 轻微震动提示错误
            try {
                HapticUtils.performHapticFeedback(requireContext())
            } catch (e: Exception) {
                // 忽略错误
            }
        }
    }

    private fun loadCustomModels() {
        val customModels = settingsManager.getCustomModels()
        for (model in customModels) {
            addModelToUI(model)
        }
    }

    /**
     * 添加模型到UI
     */
    private fun addModelToUI(modelName: String) {
        // 使用布局文件加载视图
        val modelItemView = layoutInflater.inflate(R.layout.item_custom_model, null)

        // 设置模型名称
        modelItemView.findViewById<TextView>(R.id.model_name)?.text = modelName

        // 保存模型名称到tag
        modelItemView.tag = modelName

        // 应用圆角涟漪背景
        modelItemView.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_model_ripple)

        // 确保正确剪裁
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            modelItemView.clipToOutline = true
        }

        // 应用卡片按压效果
        applyCardPressEffect(modelItemView)

        // 设置点击事件
        modelItemView.setOnClickListener { v ->
            val currentModel = settingsManager.modelType
            val shouldVibrate = currentModel != modelName

            handleButtonClickWithAnimation(v, shouldVibrate) {
                updateModel(modelName)
            }
        }

        // 设置长按删除
        modelItemView.setOnLongClickListener { v ->
            // 添加震动反馈
            try {
                HapticUtils.performHapticFeedback(requireContext(), true)
            } catch (e: Exception) {
                // 忽略错误
            }

            // 缩放动画
            v.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(150)
                .withEndAction {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()

                    // 显示删除对话框
                    showDeleteModelDialog(modelName, modelItemView)
                }
                .start()

            true
        }

        // 设置动画初始状态
        modelItemView.alpha = 0f
        modelItemView.translationX = 100f

        // 添加到容器
        modelOptionsContainer?.let { container ->
            // 添加分隔线
            if (container.childCount > 0) {
                val divider = View(requireContext())
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (resources.displayMetrics.density * 0.5f).toInt())
                divider.layoutParams = params
                divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))

                // 设置左边距
                params.marginStart = (resources.displayMetrics.density * 72).toInt()
                params.marginEnd = (resources.displayMetrics.density * 20).toInt()
                divider.layoutParams = params

                container.addView(divider)
            }

            container.addView(modelItemView)

            // 添加入场动画
            modelItemView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(500)
                .setInterpolator(DecelerateInterpolator(1.2f))
                .start()
        }
    }

    /**
     * 显示删除模型对话框
     */
    private fun showDeleteModelDialog(modelName: String, modelView: View) {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_model, null)
        builder.setView(dialogView)

        // 创建圆角对话框
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 设置模型名称
        dialogView.findViewById<TextView>(R.id.modelNameText)?.text = modelName

        // 获取按钮
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDelete)

        // 设置按钮动画
        applyButtonAnimation(btnCancel)
        applyButtonAnimation(btnDelete)

        // 设置对话框入场动画
        dialogView.alpha = 0f
        dialogView.scaleX = 0.9f
        dialogView.scaleY = 0.9f

        dialog.setOnShowListener {
            val animatorSet = AnimatorSet()
            animatorSet.playTogether(
                ObjectAnimator.ofFloat(dialogView, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(dialogView, "scaleX", 0.9f, 1f),
                ObjectAnimator.ofFloat(dialogView, "scaleY", 0.9f, 1f)
            )
            animatorSet.duration = 300
            animatorSet.interpolator = DecelerateInterpolator(1.5f)
            animatorSet.start()
        }

        // 设置取消按钮
        btnCancel.setOnClickListener { v ->
            // 添加震动反馈
            try {
                HapticUtils.performViewHapticFeedback(v, false)
            } catch (e: Exception) {
                // 忽略错误
            }

            // 播放动画
            playButtonAnimation(v) {
                // 对话框退出动画
                val animatorSet = AnimatorSet()
                animatorSet.playTogether(
                    ObjectAnimator.ofFloat(dialogView, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(dialogView, "scaleX", 1f, 0.9f),
                    ObjectAnimator.ofFloat(dialogView, "scaleY", 1f, 0.9f)
                )
                animatorSet.duration = 200
                animatorSet.interpolator = AccelerateInterpolator(1.5f)
                animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        dialog.dismiss()
                    }
                })
                animatorSet.start()
            }
        }

        // 设置删除按钮
        btnDelete.setOnClickListener { v ->
            // 添加震动反馈
            try {
                HapticUtils.performViewHapticFeedback(v, true) // 强震动
            } catch (e: Exception) {
                // 忽略错误
            }

            // 播放动画
            playButtonAnimation(v) {
                // 对话框确认动画
                val animatorSet = AnimatorSet()
                animatorSet.playTogether(
                    ObjectAnimator.ofFloat(dialogView, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(dialogView, "scaleX", 1f, 0.9f),
                    ObjectAnimator.ofFloat(dialogView, "scaleY", 1f, 0.9f)
                )
                animatorSet.duration = 200
                animatorSet.interpolator = AccelerateInterpolator(1.5f)
                animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        deleteCustomModel(modelName, modelView)
                        dialog.dismiss()
                    }
                })
                animatorSet.start()
            }
        }

        // 显示对话框
        dialog.show()
    }

    /**
     * 删除自定义模型
     */
    private fun deleteCustomModel(modelName: String, modelView: View) {
        // 从存储中删除
        val customModels = settingsManager.getCustomModels().toMutableList()
        customModels.remove(modelName)
        settingsManager.saveCustomModels(customModels)

        // 优雅地从UI中移除
        val initialDelay = 100L

        // 创建闪烁动画
        val initialFlash = ObjectAnimator.ofFloat(modelView, "alpha", 1f, 0.3f, 1f)
        initialFlash.duration = 300
        initialFlash.startDelay = initialDelay
        initialFlash.start()

        // 创建缩放和淡出动画
        modelView.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .translationX(100f)
            .setDuration(400)
            .setStartDelay(initialDelay + 300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                // 找到该视图的前一个View
                modelOptionsContainer?.let { container ->
                    val index = container.indexOfChild(modelView)
                    if (index > 0) {
                        val previousView = container.getChildAt(index - 1)
                        // 如果前一个是分隔线，也添加动画移除它
                        if (previousView.layoutParams.height <= 2) {
                            previousView.animate()
                                .alpha(0f)
                                .setDuration(200)
                                .withEndAction {
                                    container.removeView(previousView)
                                }
                                .start()
                        }
                    }

                    // 延迟一点再移除主视图，让动画有时间完成
                    modelView.postDelayed({
                        container.removeView(modelView)
                    }, 100)
                }
            }
            .start()

        // 如果当前选中的是被删除的模型，切换到默认模型并展示动画
        if (settingsManager.modelType == modelName) {
            // 延迟切换，让删除动画有时间完成
            modelView.postDelayed({
                updateModel(SettingsManager.MODEL_GPT4O_MINI)
            }, 700)
        }
    }
}

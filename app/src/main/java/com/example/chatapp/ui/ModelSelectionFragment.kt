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
 * AI模型选择Fragment - 重构版本
 */
class ModelSelectionFragment : BaseSettingsSubFragment() {
    private lateinit var settingsManager: SettingsManager
    private var modelOptionsContainer: LinearLayout? = null

    companion object {
        private const val TAG = "ModelSelectionFragment"
    }

    // 模型数据类
    data class ModelItem(
        val id: String,
        val name: String,
        val description: String,
        val isCustom: Boolean,
        var view: View? = null,
        var checkView: ImageView? = null
    )

    // 所有模型项的统一管理
    private val allModelItems = mutableListOf<ModelItem>()
    private val settingCards = mutableListOf<View>()

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

        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        modelOptionsContainer = view.findViewById(R.id.modelOptionsContainer)

        // 统一初始化所有模型项
        initializeAllModels(view)

        // 获取当前选择的模型
        val currentModel = settingsManager.modelType

        // 设置选中状态
        updateModelSelection(currentModel)

        // 添加主动消息设置卡片
        addProactiveMessageSettingsCard(view)

        // 添加入场动画
        animateUI(view)
    }

    /**
     * 初始化所有模型项
     */
    private fun initializeAllModels(rootView: View) {
        allModelItems.clear()
        settingCards.clear()

        // 1. 添加默认模型项
        addDefaultModel(
            SettingsManager.MODEL_GPT4O_MINI,
            "GPT-4o Mini",
            "快速高效的AI模型",
            rootView.findViewById(R.id.model_gpt4o_mini),
            rootView.findViewById(R.id.gpt4o_mini_check)
        )

        addDefaultModel(
            SettingsManager.MODEL_GPT4O,
            "GPT-4o",
            "强大的AI模型",
            rootView.findViewById(R.id.model_gpt4o),
            rootView.findViewById(R.id.gpt4o_check)
        )

        // 2. 添加自定义模型项
        val customModels = settingsManager.getCustomModels()
        for (modelName in customModels) {
            addCustomModel(modelName)
        }

        // 3. 添加"添加模型"按钮
        val addModelButton = rootView.findViewById<View>(R.id.add_model_button)
        if (addModelButton != null) {
            setupAddModelButton(addModelButton)
            settingCards.add(addModelButton)
        }

        Log.d(TAG, "初始化完成，共 ${allModelItems.size} 个模型项，${settingCards.size} 个动画卡片")
    }

    /**
     * 添加默认模型项
     */
    private fun addDefaultModel(
        id: String,
        name: String,
        description: String,
        view: View?,
        checkView: ImageView?
    ) {
        if (view != null) {
            val modelItem = ModelItem(id, name, description, false, view, checkView)
            allModelItems.add(modelItem)

            // 应用统一样式和点击事件
            applyUnifiedModelStyle(view, modelItem)

            // 添加到动画列表
            settingCards.add(view)

            Log.d(TAG, "添加默认模型: $name")
        }
    }

    /**
     * 添加自定义模型项
     */
    private fun addCustomModel(modelName: String, playAnimation: Boolean = false) {
        // 创建视图
        val modelItemView = layoutInflater.inflate(R.layout.item_custom_model, null)

        // 设置模型名称
        modelItemView.findViewById<TextView>(R.id.model_name)?.text = modelName

        // 获取选中状态视图
        val checkView = modelItemView.findViewById<ImageView>(R.id.custom_model_check)

        // 创建模型项数据
        val modelItem = ModelItem(modelName, modelName, "自定义模型", true, modelItemView, checkView)
        allModelItems.add(modelItem)

        // 保存模型名称到tag
        modelItemView.tag = modelName

        // 应用统一样式和点击事件
        applyUnifiedModelStyle(modelItemView, modelItem)

        // 添加到容器
        modelOptionsContainer?.let { container ->
            // 添加分隔线
            if (container.childCount > 0) {
                val divider = createDivider()
                container.addView(divider)
            }

            // 创建与默认模型完全相同的布局参数
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            // 检查默认模型的布局参数，尝试复制相同的设置
            val defaultModelView = view?.findViewById<View>(R.id.model_gpt4o)
            if (defaultModelView != null && defaultModelView.layoutParams is ViewGroup.MarginLayoutParams) {
                val defaultParams = defaultModelView.layoutParams as ViewGroup.MarginLayoutParams
                layoutParams.setMargins(
                    defaultParams.leftMargin,
                    defaultParams.topMargin,
                    defaultParams.rightMargin,
                    defaultParams.bottomMargin
                )
            }

            modelItemView.layoutParams = layoutParams

            container.addView(modelItemView)

            // 添加到动画列表
            settingCards.add(modelItemView)

            // 如果需要播放动画
            if (playAnimation) {
                // 设置动画初始状态
                modelItemView.alpha = 0f
                modelItemView.translationX = 100f

                // 添加入场动画
                modelItemView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(500)
                    .setInterpolator(DecelerateInterpolator(1.2f))
                    .start()
            }
        }

        Log.d(TAG, "添加自定义模型: $modelName")
    }

    /**
     * 应用统一的模型样式和行为 - 修正版本
     */
    private fun applyUnifiedModelStyle(view: View, modelItem: ModelItem) {
        // 统一应用样式 - 所有模型都使用相同的样式
        view.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_model_ripple)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.clipToOutline = true
            view.elevation = 0f // 统一设置初始elevation
        }

        // 应用按压效果
        applyCardPressEffect(view)

        // 设置点击事件
        view.setOnClickListener { v ->
            val currentModel = settingsManager.modelType
            val shouldVibrate = currentModel != modelItem.id

            handleButtonClickWithAnimation(v, shouldVibrate) {
                selectModel(modelItem.id)
            }
        }

        // 为自定义模型添加长按删除
        if (modelItem.isCustom) {
            view.setOnLongClickListener { v ->
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

                        showDeleteModelDialog(modelItem)
                    }
                    .start()

                true
            }
        }
    }

    /**
     * 选择模型
     */
    private fun selectModel(modelId: String) {
        val oldModel = settingsManager.modelType

        // 如果选择了同一个模型，仅展示动画
        if (oldModel == modelId) {
            showSelectionConfirmationAnimation(modelId)
            return
        }

        // 更新模型设置
        settingsManager.modelType = modelId

        // 更新选中状态并展示过渡动画
        updateModelSelectionWithAnimation(oldModel, modelId)

        // 返回主设置页面
        view?.postDelayed({
            notifyNavigationBack()
        }, 500)
    }

    /**
     * 更新模型选择状态 - 统一处理
     */
    private fun updateModelSelection(selectedModelId: String) {
        Log.d(TAG, "更新模型选择状态: $selectedModelId")

        // 清除所有选中状态
        for (modelItem in allModelItems) {
            modelItem.checkView?.visibility = View.GONE
            resetCardStyle(modelItem.view)
        }

        // 设置选中状态
        val selectedItem = allModelItems.find { it.id == selectedModelId }
        if (selectedItem != null) {
            selectedItem.checkView?.visibility = View.VISIBLE
            setSelectedCardStyle(selectedItem.view)
            Log.d(TAG, "模型 ${selectedItem.name} 已设置为选中状态")
        } else {
            Log.w(TAG, "未找到模型: $selectedModelId")
        }
    }

    /**
     * 使用动画更新模型选择状态
     */
    private fun updateModelSelectionWithAnimation(oldModelId: String, newModelId: String) {
        // 获取对应的模型项
        val oldItem = allModelItems.find { it.id == oldModelId }
        val newItem = allModelItems.find { it.id == newModelId }

        // 清除所有选中状态
        for (modelItem in allModelItems) {
            modelItem.checkView?.visibility = View.GONE
        }

        // 更新卡片样式
        oldItem?.view?.let { resetCardStyle(it) }
        newItem?.view?.let { setSelectedCardStyle(it) }

        // 使用动画显示新的选中状态
        newItem?.checkView?.let { checkView ->
            // 设置初始状态
            checkView.visibility = View.VISIBLE
            checkView.alpha = 0f
            checkView.scaleX = 0f
            checkView.scaleY = 0f
            checkView.rotation = -30f

            // 创建增强动画序列
            val phase1 = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(checkView, "alpha", 0f, 1f).apply { duration = 200 },
                    ObjectAnimator.ofFloat(checkView, "scaleX", 0f, 1.4f).apply { duration = 200 },
                    ObjectAnimator.ofFloat(checkView, "scaleY", 0f, 1.4f).apply { duration = 200 },
                    ObjectAnimator.ofFloat(checkView, "rotation", -30f, 10f).apply { duration = 200 }
                )
            }

            val phase2 = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(checkView, "scaleX", 1.4f, 1f).apply { duration = 300 },
                    ObjectAnimator.ofFloat(checkView, "scaleY", 1.4f, 1f).apply { duration = 300 },
                    ObjectAnimator.ofFloat(checkView, "rotation", 10f, 0f).apply { duration = 300 }
                )
                interpolator = OvershootInterpolator(3f)
            }

            val completeAnimation = AnimatorSet()
            completeAnimation.playSequentially(phase1, phase2)
            completeAnimation.start()
        }

        // 为整个卡片添加闪烁动画
        newItem?.view?.let { view ->
            val brightnessUp = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.85f, 1f)
            brightnessUp.duration = 400
            brightnessUp.interpolator = DecelerateInterpolator()
            brightnessUp.start()
        }
    }

    /**
     * 展示选择确认动画
     */
    private fun showSelectionConfirmationAnimation(modelId: String) {
        val modelItem = allModelItems.find { it.id == modelId }
        val checkView = modelItem?.checkView ?: return

        // 保存原始状态
        val originalScale = checkView.scaleX

        // 创建闪烁动画
        val scaleUp = ObjectAnimator.ofFloat(checkView, "scaleX", originalScale, 1.5f).apply { duration = 200 }
        val scaleUp2 = ObjectAnimator.ofFloat(checkView, "scaleY", originalScale, 1.5f).apply { duration = 200 }
        val scaleDown = ObjectAnimator.ofFloat(checkView, "scaleX", 1.5f, originalScale).apply { duration = 200 }
        val scaleDown2 = ObjectAnimator.ofFloat(checkView, "scaleY", 1.5f, originalScale).apply { duration = 200 }

        val scaleUpAnim = AnimatorSet().apply { playTogether(scaleUp, scaleUp2) }
        val scaleDownAnim = AnimatorSet().apply { playTogether(scaleDown, scaleDown2) }

        val animSequence = AnimatorSet().apply { playSequentially(scaleUpAnim, scaleDownAnim) }
        animSequence.start()
    }

    /**
     * 重置卡片样式 - 修正版本
     */
    private fun resetCardStyle(view: View?) {
        view?.let {
            it.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_model_ripple)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                it.elevation = 0f
            }
        }
    }

    /**
     * 设置选中卡片样式 - 修正版本
     */
    private fun setSelectedCardStyle(view: View?) {
        view?.let {
            it.background = ContextCompat.getDrawable(requireContext(), R.drawable.model_selected_background)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                it.elevation = 4f

                // 为钩子图标也设置阴影效果
                val checkView = if (it.tag != null) {
                    // 自定义模型
                    it.findViewById<ImageView>(R.id.custom_model_check)
                } else {
                    // 默认模型 - 需要根据具体的ID查找
                    it.findViewById<ImageView>(R.id.gpt4o_mini_check)
                        ?: it.findViewById<ImageView>(R.id.gpt4o_check)
                }

                checkView?.let { check ->
                    if (check.visibility == View.VISIBLE) {
                        check.elevation = 6f // 比卡片稍高一点
                    }
                }
            }
        }
    }

    /**
     * 应用卡片按压效果 - 修正版本
     */
    private fun applyCardPressEffect(card: View) {
        card.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val scaleDown = AnimatorSet()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        scaleDown.playTogether(
                            ObjectAnimator.ofFloat(v, "scaleX", 0.97f),
                            ObjectAnimator.ofFloat(v, "scaleY", 0.97f),
                            ObjectAnimator.ofFloat(v, "alpha", 1f, 0.9f),
                            ObjectAnimator.ofFloat(v, "elevation", v.elevation, v.elevation + 2f)
                        )
                    } else {
                        scaleDown.playTogether(
                            ObjectAnimator.ofFloat(v, "scaleX", 0.97f),
                            ObjectAnimator.ofFloat(v, "scaleY", 0.97f),
                            ObjectAnimator.ofFloat(v, "alpha", 1f, 0.9f)
                        )
                    }

                    scaleDown.duration = 150
                    scaleDown.interpolator = AccelerateInterpolator(1.5f)
                    scaleDown.start()
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val scaleUp = AnimatorSet()
                    val targetElevation = if (isViewSelected(v)) 4f else 0f

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        scaleUp.playTogether(
                            ObjectAnimator.ofFloat(v, "scaleX", 1f),
                            ObjectAnimator.ofFloat(v, "scaleY", 1f),
                            ObjectAnimator.ofFloat(v, "alpha", 0.9f, 1f),
                            ObjectAnimator.ofFloat(v, "elevation", v.elevation, targetElevation)
                        )
                    } else {
                        scaleUp.playTogether(
                            ObjectAnimator.ofFloat(v, "scaleX", 1f),
                            ObjectAnimator.ofFloat(v, "scaleY", 1f),
                            ObjectAnimator.ofFloat(v, "alpha", 0.9f, 1f)
                        )
                    }

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
     * 检查视图是否处于选中状态
     */
    private fun isViewSelected(view: View): Boolean {
        val drawable = view.background ?: return false
        val selectedDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.model_selected_background)
        return drawable.constantState == selectedDrawable?.constantState
    }

    /**
     * 设置添加模型按钮
     */
    private fun setupAddModelButton(addModelButton: View) {
        // 应用样式
        addModelButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_model_ripple)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addModelButton.clipToOutline = true
            addModelButton.elevation = 0f
        }

        // 应用按压效果
        applyCardPressEffect(addModelButton)

        // 设置点击事件
        addModelButton.setOnClickListener { v ->
            handleButtonClickWithAnimation(v) {
                showAddModelDialog()
            }
        }
    }

    /**
     * 使用动画处理按钮点击
     */
    private fun handleButtonClickWithAnimation(view: View, shouldVibrate: Boolean = true, action: () -> Unit) {
        if (shouldVibrate) {
            try {
                HapticUtils.performViewHapticFeedback(view, false)
            } catch (e: Exception) {
                // 忽略可能的错误
            }
        }

        val scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 0.96f)
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 0.96f)
        val alphaDown = ObjectAnimator.ofFloat(view, "alpha", 0.9f)

        scaleDownX.duration = 100
        scaleDownY.duration = 100
        alphaDown.duration = 100

        val scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f)
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f)
        val alphaUp = ObjectAnimator.ofFloat(view, "alpha", 1.0f)

        scaleUpX.duration = 250
        scaleUpY.duration = 250
        alphaUp.duration = 250
        scaleUpX.interpolator = OvershootInterpolator(2.5f)
        scaleUpY.interpolator = OvershootInterpolator(2.5f)

        val scaleDown = AnimatorSet()
        val scaleUp = AnimatorSet()
        val targetElevation = if (isViewSelected(view)) 4f else 0f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val elevationUp = ObjectAnimator.ofFloat(view, "elevation", view.elevation + 2f)
            elevationUp.duration = 100

            val elevationDown = ObjectAnimator.ofFloat(view, "elevation", targetElevation)
            elevationDown.duration = 250

            scaleDown.playTogether(scaleDownX, scaleDownY, alphaDown, elevationUp)
            scaleUp.playTogether(scaleUpX, scaleUpY, alphaUp, elevationDown)
        } else {
            scaleDown.playTogether(scaleDownX, scaleDownY, alphaDown)
            scaleUp.playTogether(scaleUpX, scaleUpY, alphaUp)
        }

        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(scaleDown, scaleUp)
        animatorSet.start()

        view.postDelayed(action, 350)
    }

    /**
     * 创建分隔线
     */
    private fun createDivider(): View {
        val divider = View(requireContext())
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.density * 0.5f).toInt()
        )
        divider.layoutParams = params
        divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))

        params.marginStart = (resources.displayMetrics.density * 72).toInt()
        params.marginEnd = (resources.displayMetrics.density * 20).toInt()
        divider.layoutParams = params

        return divider
    }

    /**
     * 显示添加模型对话框
     */
    private fun showAddModelDialog() {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_model, null)
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val modelNameInput = dialogView.findViewById<TextInputEditText>(R.id.model_name_input)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnAdd = dialogView.findViewById<MaterialButton>(R.id.btnAdd)

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

            modelNameInput.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(modelNameInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        btnCancel.setOnClickListener {
            try {
                HapticUtils.performViewHapticFeedback(it, false)
            } catch (e: Exception) {}
            dialog.dismiss()
        }

        btnAdd.setOnClickListener {
            try {
                HapticUtils.performViewHapticFeedback(it, false)
            } catch (e: Exception) {}

            val modelName = modelNameInput.text.toString().trim()
            if (!TextUtils.isEmpty(modelName)) {
                addNewCustomModel(modelName)
                dialog.dismiss()
            } else {
                modelNameInput.error = "模型名称不能为空"
                try {
                    HapticUtils.performHapticFeedback(requireContext())
                } catch (e: Exception) {}
            }
        }

        dialog.show()
    }

    /**
     * 添加新的自定义模型
     */
    private fun addNewCustomModel(modelName: String) {
        val customModels = settingsManager.getCustomModels().toMutableList()

        if (!customModels.contains(modelName) &&
            modelName != SettingsManager.MODEL_GPT4O_MINI &&
            modelName != SettingsManager.MODEL_GPT4O) {

            customModels.add(modelName)
            settingsManager.saveCustomModels(customModels)

            // 添加到UI
            addCustomModel(modelName, true)

            // 自动选择新添加的模型
            selectModel(modelName)
        } else {
            Toast.makeText(requireContext(), "模型已存在", Toast.LENGTH_SHORT).show()
            try {
                HapticUtils.performHapticFeedback(requireContext())
            } catch (e: Exception) {}
        }
    }

    /**
     * 显示删除模型对话框
     */
    private fun showDeleteModelDialog(modelItem: ModelItem) {
        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_model, null)
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.modelNameText)?.text = modelItem.name

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDelete)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            deleteCustomModel(modelItem)
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * 删除自定义模型
     */
    private fun deleteCustomModel(modelItem: ModelItem) {
        // 从数据中删除
        allModelItems.remove(modelItem)
        settingCards.remove(modelItem.view)

        val customModels = settingsManager.getCustomModels().toMutableList()
        customModels.remove(modelItem.id)
        settingsManager.saveCustomModels(customModels)

        // 从UI中移除
        modelItem.view?.let { view ->
            view.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .translationX(100f)
                .setDuration(400)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    modelOptionsContainer?.let { container ->
                        val index = container.indexOfChild(view)
                        if (index > 0) {
                            val previousView = container.getChildAt(index - 1)
                            if (previousView.layoutParams.height <= 2) {
                                container.removeView(previousView)
                            }
                        }
                        container.removeView(view)
                    }
                }
                .start()
        }

        // 如果当前选中的是被删除的模型，切换到默认模型
        if (settingsManager.modelType == modelItem.id) {
            selectModel(SettingsManager.MODEL_GPT4O_MINI)
        }
    }

    /**
     * 整体UI的入场动画
     */
    private fun animateUI(view: View) {
        try {
            val titleTexts = findTextViewsInContainer(view, 2)
            val title = titleTexts.getOrNull(0)
            val mainCard = findFirstMaterialCardView(view)
            val bottomTexts = findBottomTextViews(view)
            val description = bottomTexts.lastOrNull()

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

                animateCardContents()
            }

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
     * 为卡片内部元素添加顺序动画
     */
    private fun animateCardContents() {
        // 设置初始状态
        settingCards.forEach { card ->
            card.alpha = 0f
            card.translationX = 100f
        }

        // 依次为每个卡片添加动画
        settingCards.forEachIndexed { index, card ->
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
     * 查找容器中的前N个TextView
     */
    private fun findTextViewsInContainer(container: View, count: Int): List<TextView> {
        val result = mutableListOf<TextView>()

        if (container is TextView) {
            result.add(container)
        }

        if (container is ViewGroup) {
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)

                if (child is TextView) {
                    result.add(child)
                    if (result.size >= count) break
                } else if (child is ViewGroup) {
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
        if (container is MaterialCardView) {
            return container
        }

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

        if (container is ViewGroup) {
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
     * 添加主动消息设置卡片
     */
    private fun addProactiveMessageSettingsCard(view: View) {
        try {
            val cardView = layoutInflater.inflate(R.layout.card_proactive_message_settings, null)

            cardView.background = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_model_ripple)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cardView.clipToOutline = true
                cardView.elevation = 0f
            }

            applyCardPressEffect(cardView)

            cardView.setOnClickListener { v ->
                handleButtonClickWithAnimation(v) {
                    navigateToProactiveMessageSettings()
                }
            }

            cardView.alpha = 0f
            cardView.translationY = 50f

            val container = view.findViewById<ViewGroup>(R.id.additionalOptionsContainer)

            if (container != null) {
                container.addView(cardView)

                cardView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(600)
                    .setStartDelay(1200)
                    .setInterpolator(DecelerateInterpolator(1.3f))
                    .start()
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
            val intent = Intent(requireContext(), ProactiveMessageSettingsActivity::class.java)
            startActivity(intent)
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } catch (e: Exception) {
            Log.e(TAG, "启动主动消息设置Activity失败: ${e.message}", e)
            Toast.makeText(requireContext(), "无法打开主动消息设置", Toast.LENGTH_SHORT).show()
        }
    }
}
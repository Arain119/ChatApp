package com.example.chatapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.chatapp.R
import com.example.chatapp.utils.HapticUtils

/**
 * 隐私政策专用Activity
 */
class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置状态栏颜色与背景一致
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = ContextCompat.getColor(this, R.color.background)

        // 设置状态栏图标为深色
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        setContentView(R.layout.activity_privacy_policy)

        // 设置隐私政策文本
        val policyTextView = findViewById<TextView>(R.id.privacy_policy_text)
        policyTextView.text = PRIVACY_POLICY_TEXT

        // 设置返回按钮容器
        val backIcon = findViewById<ImageView>(R.id.back_icon)
        val backButton = if (backIcon.parent is FrameLayout) {
            backIcon.parent as FrameLayout
        } else {
            backIcon // 如果没有容器，就直接使用图标
        }

        // 移除涟漪效果
        backButton.background = null

        // 设置返回图标点击事件
        backButton.setOnClickListener {
            // 触觉反馈
            HapticUtils.performViewHapticFeedback(it)

            // 执行按钮动画
            animateBackButton(backButton) {
                // 动画完成后执行退出动画和结束活动
                finishWithAnimation()
            }
        }

        // 添加入场动画
        animateEntrance()
    }

    /**
     * 入场动画效果
     */
    private fun animateEntrance() {
        // 获取内容卡片
        val contentCard = findViewById<View>(R.id.content_card) ?: return

        // 设置初始状态
        contentCard.translationY = 100f
        contentCard.alpha = 0f

        // 创建动画
        val translateY = ObjectAnimator.ofFloat(contentCard, "translationY", 100f, 0f)
        val alpha = ObjectAnimator.ofFloat(contentCard, "alpha", 0f, 1f)

        // 组合动画
        val animSet = AnimatorSet()
        animSet.playTogether(translateY, alpha)
        animSet.duration = 300
        animSet.interpolator = AccelerateDecelerateInterpolator()
        animSet.start()
    }

    /**
     * 返回按钮动画效果
     */
    private fun animateBackButton(button: View, onComplete: () -> Unit) {
        // 创建按钮缩放动画
        val scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.7f)  // 从0.85改为0.7
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.7f)  // 从0.85改为0.7
        val scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 0.7f, 1.15f)  // 增加反弹效果从1.0到1.15
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.7f, 1.15f)  // 增加反弹效果从1.0到1.15
        val scaleFinalX = ObjectAnimator.ofFloat(button, "scaleX", 1.15f, 1.0f)  // 回到原始大小
        val scaleFinalY = ObjectAnimator.ofFloat(button, "scaleY", 1.15f, 1.0f)  // 回到原始大小

        // 组合缩放动画
        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY)
        scaleDown.duration = 120

        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY)
        scaleUp.duration = 180
        scaleUp.interpolator = OvershootInterpolator(3f)

        val scaleFinal = AnimatorSet()
        scaleFinal.playTogether(scaleFinalX, scaleFinalY)
        scaleFinal.duration = 100

        // 添加旋转效果
        val rotate = ObjectAnimator.ofFloat(button, "rotation", 0f, -5f, 5f, 0f)
        rotate.duration = 300
        rotate.interpolator = OvershootInterpolator(2f)

        // 按顺序执行动画
        val sequence = AnimatorSet()
        sequence.playSequentially(scaleDown, scaleUp, scaleFinal)
        sequence.playTogether(rotate)  // 旋转动画与缩放同时进行

        // 添加动画完成监听器
        sequence.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete()
            }
        })

        sequence.start()
    }

    /**
     * 退出动画并结束活动
     */
    private fun finishWithAnimation() {
        // 执行退出动画
        val rootView = findViewById<View>(android.R.id.content)

        // 创建缩放+淡出动画组合
        val scaleX = ObjectAnimator.ofFloat(rootView, "scaleX", 1f, 0.95f)
        val scaleY = ObjectAnimator.ofFloat(rootView, "scaleY", 1f, 0.95f)
        val fadeOut = ObjectAnimator.ofFloat(rootView, "alpha", 1f, 0f)

        val animSet = AnimatorSet()
        animSet.playTogether(scaleX, scaleY, fadeOut)
        animSet.duration = 250

        // 设置动画完成后的操作
        animSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // 调用finish并设置过渡动画
                finish()
                overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
            }
        })

        animSet.start()
    }

    /**
     * 重写返回键按下行为，应用相同的动画效果
     */
    override fun onBackPressed() {
        // 获取返回按钮
        val backIcon = findViewById<ImageView>(R.id.back_icon)
        val backButton = if (backIcon.parent is FrameLayout) {
            backIcon.parent as FrameLayout
        } else {
            backIcon
        }

        // 执行动画
        animateBackButton(backButton) {
            finishWithAnimation()
        }
    }

    companion object {
        // 隐私政策文本内容
        private const val PRIVACY_POLICY_TEXT = """
Arain（"我们"或"开发者"）重视并保护您的隐私。本隐私政策旨在告知您我们如何收集、使用、披露和保护您在使用Alice（"应用"）时的个人信息。

1. 收集的信息

我们可能收集以下类型的信息：

1.1 您提供的信息
- 用户输入的文本内容
- 上传的图片和文件
- 应用设置和偏好

1.2 自动收集的信息
- 设备信息及详细位置信息（设备型号、操作系统版本）
- 应用使用统计信息以及数据信息
- 崩溃报告和性能数据

2. 信息使用

我们使用收集的信息：
- 提供、维护和改进应用功能
- 处理您的请求和对话
- 个性化您的使用体验
- 存储您的聊天记录和设置
- 提高应用性能和稳定性

3. 人工智能服务

我们的应用使用第三方AI服务（如OpenAI的API）处理您的请求。请注意：
- 您输入的内容将发送到这些第三方服务进行处理
- 这些第三方服务有自己的隐私政策和数据处理条款
- 我们不控制这些第三方如何处理您的数据

4. 数据存储和安全

- 如果不需使用其它服务，您的所有数据将存储在您设备的本地数据库中
- 联网功能会将您的查询内容发送到相应的服务提供商
- 我们采取合理措施保护您的数据，但无法保证100%的安全性

5. 第三方服务

我们的应用使用以下第三方服务：
- OpenAI API 或类似服务（用于AI对话）
- 搜索引擎（如有启用联网功能）

6. 您的权利

您有权：
- 查看、删除您的聊天历史
- 更改应用设置和偏好
- 卸载应用并清除所有本地数据

7. 政策变更

我们可能会不时更新本隐私政策。我们会在应用中通知您任何重大变更。

8. 联系我们

如有任何问题或疑虑，请通过以下方式联系我们：
电子邮件：shuzhongwubieyi@outlook.com
        """
    }
}
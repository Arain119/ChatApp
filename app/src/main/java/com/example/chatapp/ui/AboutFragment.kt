package com.example.chatapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.doOnPreDraw
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.example.chatapp.R
import com.example.chatapp.utils.HapticUtils

/**
 * 关于页面Fragment
 */
class AboutFragment : BaseSettingsSubFragment(), BaseSettingsSubFragment.NavigationCallback {

    override fun getTitle(): String = "关于"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 延迟动画以等待视图完全绘制
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        // 设置应用版本
        val versionText = view.findViewById<TextView>(R.id.app_version)
        versionText.text = "版本：1.7"

        // 设置应用图标动画
        val appIcon = view.findViewById<ShapeableImageView>(R.id.app_icon)
        animateAppIcon(appIcon)

        // 设置邮箱点击事件
        val feedbackCard = view.findViewById<MaterialCardView>(R.id.feedback_card)
        feedbackCard.setOnClickListener {
            // 添加触觉反馈
            HapticUtils.performViewHapticFeedback(it)
            // 添加卡片按下效果
            animateCardPress(feedbackCard)
            // 延迟执行操作，让动画完成
            feedbackCard.postDelayed({ sendFeedbackEmail() }, 200)
        }

        // 设置隐私政策点击事件
        val privacyCard = view.findViewById<MaterialCardView>(R.id.privacy_policy_card)
        privacyCard.setOnClickListener {
            // 添加触觉反馈
            HapticUtils.performViewHapticFeedback(it)
            // 添加卡片按下效果
            animateCardPress(privacyCard)
            // 延迟执行操作，让动画完成
            privacyCard.postDelayed({ showPrivacyPolicyScreen() }, 200)
        }

        // 为所有卡片添加进入动画
        animateCards(view)
    }

    /**
     * 发送反馈邮件
     */
    private fun sendFeedbackEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:shuzhongwubieyi@outlook.com")
            putExtra(Intent.EXTRA_SUBJECT, "Alice 反馈")
            putExtra(Intent.EXTRA_TEXT, "应用版本：1.7\n\n我想反馈的问题/建议：\n\n")
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "未找到邮件应用，请手动发送邮件至：shuzhongwubieyi@outlook.com",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * 显示隐私政策
     */
    private fun showPrivacyPolicyScreen() {
        // 创建隐私政策Fragment
        activity?.let { fragmentActivity ->
            // 启动单独的活动来显示隐私政策
            val intent = Intent(fragmentActivity, PrivacyPolicyActivity::class.java)
            startActivity(intent)
            // 添加过渡动画
            fragmentActivity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    /**
     * 为应用图标添加动画
     */
    private fun animateAppIcon(icon: ShapeableImageView) {
        // 初始缩放为0
        icon.scaleX = 0f
        icon.scaleY = 0f
        icon.alpha = 0f

        // 创建缩放和透明度动画
        val scaleX = ObjectAnimator.ofFloat(icon, "scaleX", 0f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(icon, "scaleY", 0f, 1.1f, 1f)
        val alpha = ObjectAnimator.ofFloat(icon, "alpha", 0f, 1f)

        // 创建动画集合
        val animSet = AnimatorSet()
        animSet.playTogether(scaleX, scaleY, alpha)
        animSet.duration = 800
        animSet.interpolator = OvershootInterpolator(1.5f)
        animSet.startDelay = 300
        animSet.start()
    }

    /**
     * 卡片按下动画效果
     */
    private fun animateCardPress(card: MaterialCardView) {
        val scaleDownX = ObjectAnimator.ofFloat(card, "scaleX", 1f, 0.95f)
        val scaleDownY = ObjectAnimator.ofFloat(card, "scaleY", 1f, 0.95f)
        val scaleUpX = ObjectAnimator.ofFloat(card, "scaleX", 0.95f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(card, "scaleY", 0.95f, 1f)

        val scaleDown = AnimatorSet()
        scaleDown.playTogether(scaleDownX, scaleDownY)
        scaleDown.duration = 100

        val scaleUp = AnimatorSet()
        scaleUp.playTogether(scaleUpX, scaleUpY)
        scaleUp.duration = 200
        scaleUp.interpolator = OvershootInterpolator(2f)

        val sequence = AnimatorSet()
        sequence.playSequentially(scaleDown, scaleUp)
        sequence.start()
    }

    /**
     * 为所有卡片添加入场动画
     */
    private fun animateCards(view: View) {
        // 获取所有卡片
        val cards = listOf(
            view.findViewById<View>(R.id.feedback_card),
            view.findViewById<View>(R.id.privacy_policy_card),
            view.findViewById<View>(R.id.copyright_card)  // 添加版权信息卡片
        )

        // 为每个卡片设置初始状态
        cards.forEach { card ->
            card.translationY = 100f
            card.alpha = 0f
        }

        // 依次为每个卡片添加动画
        cards.forEachIndexed { index, card ->
            val translateY = ObjectAnimator.ofFloat(card, "translationY", 100f, 0f)
            val alpha = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f)

            val animSet = AnimatorSet()
            animSet.playTogether(translateY, alpha)
            animSet.duration = 500
            animSet.interpolator = DecelerateInterpolator(1.5f)
            animSet.startDelay = 300L + (index * 100)
            animSet.start()
        }
    }

    // 实现NavigationCallback接口
    override fun navigateBack() {
        // 处理从子Fragment返回到当前Fragment的逻辑
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
        } else {
            // 使用父类的方法通知往上级返回
            super.notifyNavigationBack()
        }
    }
}
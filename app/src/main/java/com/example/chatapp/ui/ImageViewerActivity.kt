package com.example.chatapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.chatapp.R
import com.example.chatapp.utils.HapticUtils
import com.example.chatapp.utils.setOnClickListenerWithHaptic
import com.github.chrisbanes.photoview.PhotoView

class ImageViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        // 设置全屏模式
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        // 获取传递的图片数据
        val imageData = intent.getStringExtra("IMAGE_DATA")

        // 找到PhotoView图片控件
        val photoView = findViewById<PhotoView>(R.id.photoView)

        // 添加关闭按钮监听 - 使用带震动反馈的点击监听器
        val closeButton = findViewById<ImageView>(R.id.closeButton)
        closeButton.setOnClickListenerWithHaptic(isStrong = true) {
            // 添加按钮动画效果
            animateCloseButton(closeButton)
        }

        // 加载图片
        if (imageData != null) {
            try {
                val imageBytes = Base64.decode(imageData, Base64.DEFAULT)
                photoView.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size))
            } catch (e: Exception) {
                // 显示错误消息
                photoView.setImageResource(R.drawable.ic_warning)
            }
        } else {
            // 没有数据，显示错误图标
            photoView.setImageResource(R.drawable.ic_warning)
        }
    }

    /**
     * 为关闭按钮添加动画效果
     */
    private fun animateCloseButton(button: ImageView) {
        // 创建一组动画效果
        val animatorSet = AnimatorSet()

        // 第一阶段：快速缩小
        val scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.7f)
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.7f)
        val rotate = ObjectAnimator.ofFloat(button, "rotation", 0f, 45f)

        scaleDownX.duration = 150
        scaleDownY.duration = 150
        rotate.duration = 150

        // 使用加速差值器使动画更加生动
        scaleDownX.interpolator = AccelerateInterpolator()
        scaleDownY.interpolator = AccelerateInterpolator()
        rotate.interpolator = AccelerateInterpolator()

        // 第二阶段：弹回并稍微放大
        val scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 0.7f, 1.2f)
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.7f, 1.2f)
        val rotateMore = ObjectAnimator.ofFloat(button, "rotation", 45f, 90f)

        scaleUpX.duration = 200
        scaleUpY.duration = 200
        rotateMore.duration = 200

        // 使用回弹插值器使动画更有弹性
        scaleUpX.interpolator = OvershootInterpolator(2f)
        scaleUpY.interpolator = OvershootInterpolator(2f)

        // 按顺序播放动画
        animatorSet.play(scaleDownX).with(scaleDownY).with(rotate)
        animatorSet.play(scaleUpX).with(scaleUpY).with(rotateMore).after(rotate)

        // 设置监听器，在动画完成后关闭Activity
        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // 添加淡出效果
                val fadeOut = ObjectAnimator.ofFloat(button, "alpha", 1f, 0f)
                fadeOut.duration = 150
                fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // 完成所有动画后关闭Activity
                        finish()
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    }
                })
                fadeOut.start()
            }
        })

        // 启动动画
        animatorSet.start()
    }

    override fun onBackPressed() {
        // 执行强震动反馈 - 返回键被按下时
        HapticUtils.performHapticFeedback(this, true)

        // 找到关闭按钮并触发其动画
        val closeButton = findViewById<ImageView>(R.id.closeButton)
        animateCloseButton(closeButton)
    }
}

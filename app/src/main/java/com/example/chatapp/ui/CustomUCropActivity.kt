package com.example.chatapp.ui

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.chatapp.R
import com.example.chatapp.utils.HapticUtils
import com.yalantis.ucrop.UCropActivity

/**
 * 自定义UCrop活动
 */
class CustomUCropActivity : UCropActivity() {

    // 声明确认和取消按钮
    private lateinit var confirmButton: ImageButton
    private lateinit var cancelButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        // 设置全屏和透明状态栏
        forceHideSystemBars()

        super.onCreate(savedInstanceState)

        // 延迟执行以确保布局加载完成
        window.decorView.post {
            // 查找并隐藏工具栏
            try {
                // 首先尝试通过ID查找工具栏
                val toolbarId = resources.getIdentifier("toolbar", "id", packageName)
                if (toolbarId != 0) {
                    findViewById<View>(toolbarId)?.apply {
                        visibility = View.GONE
                        layoutParams?.height = 0
                        requestLayout()
                    }
                }

                // 备用方法：遍历视图层次结构查找工具栏
                val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
                findAndHideToolbar(rootView)

                // 强制刷新布局
                rootView.invalidate()
                rootView.requestLayout()

                // 添加自定义确认按钮
                addConfirmButton()
            } catch (e: Exception) {
                Log.e("CustomUCropActivity", "隐藏工具栏失败: ${e.message}")
            }
        }
    }

    /**
     * 添加自定义确认和取消按钮
     */
    private fun addConfirmButton() {
        // 获取根布局
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        val contentLayout = rootView.getChildAt(0) as? ViewGroup ?: rootView

        // 创建包装FrameLayout
        val frameLayout: FrameLayout
        if (contentLayout is FrameLayout) {
            frameLayout = contentLayout
        } else {
            frameLayout = FrameLayout(this)

            // 获取内容视图的索引
            val contentIndex = rootView.indexOfChild(contentLayout)

            // 从原始父视图中移除内容视图
            rootView.removeView(contentLayout)

            // 添加内容视图到FrameLayout
            frameLayout.addView(contentLayout, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

            // 将FrameLayout添加到根视图
            rootView.addView(frameLayout, contentIndex, contentLayout.layoutParams)
        }

        // 创建确认按钮尺寸
        val buttonSize = dpToPx(56)

        // 添加确认按钮
        confirmButton = ImageButton(this).apply {
            // 设置对勾图标
            setImageResource(R.drawable.ic_check)

            // 设置完全透明背景
            setBackgroundResource(android.R.color.transparent)

            // 设置半透明白色图标
            imageTintList = android.content.res.ColorStateList.valueOf(Color.argb(180, 255, 255, 255))

            // 图标尺寸
            scaleX = 1.2f
            scaleY = 1.2f

            // 设置按钮点击事件
            setOnClickListener {
                try {
                    // 提供触觉反馈
                    HapticUtils.performHapticFeedback(this@CustomUCropActivity)

                    // 调用UCropActivity的裁剪完成方法
                    cropAndSaveImage()
                } catch (e: Exception) {
                    Log.e("CustomUCropActivity", "确认裁剪失败: ${e.message}")
                    // 使用父类已有的方法处理错误
                    setResultError(e)
                    finish()
                }
            }
        }

        // 确认按钮布局参数
        val confirmParams = FrameLayout.LayoutParams(
            buttonSize, // 按钮宽度
            buttonSize  // 按钮高度
        ).apply {
            gravity = Gravity.TOP or Gravity.END // 顶部右侧
            topMargin = dpToPx(16) // 顶部边距
            rightMargin = dpToPx(16) // 右侧边距
        }

        // 添加取消按钮
        cancelButton = ImageButton(this).apply {
            // 设置叉图标
            setImageResource(R.drawable.ic_close)

            // 设置完全透明背景
            setBackgroundResource(android.R.color.transparent)

            // 设置半透明白色图标
            imageTintList = android.content.res.ColorStateList.valueOf(Color.argb(180, 255, 255, 255))

            // 图标尺寸
            scaleX = 1.2f
            scaleY = 1.2f

            // 设置按钮点击事件
            setOnClickListener {
                // 提供触觉反馈
                HapticUtils.performHapticFeedback(this@CustomUCropActivity)

                // 取消操作并关闭Activity
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        // 取消按钮布局参数
        val cancelParams = FrameLayout.LayoutParams(
            buttonSize, // 按钮宽度
            buttonSize  // 按钮高度
        ).apply {
            gravity = Gravity.TOP or Gravity.START // 顶部左侧
            topMargin = dpToPx(16) // 顶部边距
            leftMargin = dpToPx(16) // 左侧边距
        }

        // 添加两个按钮到布局中
        frameLayout.addView(confirmButton, confirmParams)
        frameLayout.addView(cancelButton, cancelParams)
    }

    /**
     * dp转px工具方法
     */
    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    /**
     * 隐藏系统栏
     */
    private fun forceHideSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /**
     * 递归查找并隐藏工具栏
     */
    private fun findAndHideToolbar(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)

            // 检查是否是Toolbar或ActionBar
            val className = child.javaClass.name.lowercase()
            if (className.contains("toolbar") || className.contains("actionbar")) {
                child.visibility = View.GONE

                // 将高度设为0
                val params = child.layoutParams
                params.height = 0
                child.layoutParams = params

                Log.d("CustomUCropActivity", "找到并隐藏了工具栏: $className")
                return
            }

            // 通过ID名称检查
            if (child.id != View.NO_ID) {
                try {
                    val idName = resources.getResourceEntryName(child.id).lowercase()
                    if (idName.contains("toolbar") || idName.contains("action_bar")) {
                        child.visibility = View.GONE

                        // 将高度设为0
                        val params = child.layoutParams
                        params.height = 0
                        child.layoutParams = params

                        Log.d("CustomUCropActivity", "找到并隐藏了工具栏，ID: $idName")
                        return
                    }
                } catch (e: Exception) {
                    // 资源ID不存在，忽略
                }
            }

            // 递归检查子视图
            if (child is ViewGroup) {
                findAndHideToolbar(child)
            }
        }
    }
}
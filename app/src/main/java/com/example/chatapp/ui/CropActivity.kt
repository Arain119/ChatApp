package com.example.chatapp.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.chatapp.R
import com.example.chatapp.utils.HapticUtils
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.UUID

/**
 * 图片裁剪活动，基于UCrop实现用户自定义裁剪区域
 */
class CropActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CropActivity"

        // 传入参数的键
        const val EXTRA_SOURCE_URI = "source_uri"
        const val EXTRA_IS_USER_AVATAR = "is_user_avatar"

        // 返回参数的键
        const val RESULT_CROPPED_URI = "cropped_uri"
        const val RESULT_IS_USER_AVATAR = "is_user_avatar"
    }

    private var isUserAvatar: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在setContentView之前设置全屏
        forceHideSystemBars()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)

        // 获取传入参数
        val sourceUri = intent.getParcelableExtra<Uri>(EXTRA_SOURCE_URI)
        isUserAvatar = intent.getBooleanExtra(EXTRA_IS_USER_AVATAR, true)

        if (sourceUri == null) {
            Log.e(TAG, "无效的源图片URI")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // 开始裁剪
        startCrop(sourceUri)
    }

    /**
     * 隐藏系统栏
     */
    private fun forceHideSystemBars() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    )
        }

        if (Build.VERSION.SDK_INT >= 30) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * 确保窗口焦点变化时保持全屏
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            forceHideSystemBars()
        }
    }

    /**
     * 开始裁剪
     */
    private fun startCrop(sourceUri: Uri) {
        // 创建目标Uri
        val destinationUri = Uri.fromFile(
            File(cacheDir, "cropped_${UUID.randomUUID()}.jpg")
        )

        // 配置UCrop Options
        val options = UCrop.Options().apply {
            // 保留底部控制栏
            setHideBottomControls(false)

            // 隐藏裁剪框和网格
            setShowCropFrame(false)
            setShowCropGrid(false)

            setCircleDimmedLayer(true)

            // 隐藏顶部工具栏
            setToolbarColor(Color.TRANSPARENT)
            setToolbarWidgetColor(Color.TRANSPARENT)
            setToolbarTitle("")

            // 设置主题色
            setActiveControlsWidgetColor(resources.getColor(R.color.primary, theme))
            setStatusBarColor(Color.TRANSPARENT)
            setRootViewBackgroundColor(Color.BLACK)
        }

        // 创建UCrop对象
        val uCrop = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1000, 1000)
            .withOptions(options)

        // 获取UCrop Intent并修改目标Activity
        val intent = uCrop.getIntent(this)
        intent.setClass(this, CustomUCropActivity::class.java)

        // 使用标准方式启动Activity
        startActivityForResult(intent, UCrop.REQUEST_CROP)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == UCrop.REQUEST_CROP) {
            if (resultCode == RESULT_OK && data != null) {
                // 获取裁剪后的图片Uri
                val resultUri = UCrop.getOutput(data)

                // 添加震动反馈
                HapticUtils.performHapticFeedback(this)

                // 返回裁剪结果
                val resultIntent = Intent().apply {
                    putExtra(RESULT_CROPPED_URI, resultUri)
                    putExtra(RESULT_IS_USER_AVATAR, isUserAvatar)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else if (resultCode == UCrop.RESULT_ERROR && data != null) {
                // 处理错误
                val error = UCrop.getError(data)
                Log.e(TAG, "裁剪错误: ${error?.message}")
                setResult(Activity.RESULT_CANCELED)
                finish()
            } else {
                // 用户取消或data为null
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // 处理返回按钮点击
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
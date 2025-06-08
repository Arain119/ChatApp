package com.example.chatapp.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.chatapp.R
import com.example.chatapp.utils.HapticUtils

/**
 * 权限请求活动
 * 引导用户开启所有必要的权限
 */
class PermissionRequestActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
        private const val REQUEST_SCHEDULE_EXACT_ALARM = 101
        private const val REQUEST_OVERLAY_PERMISSION = 102

        // 启动此活动的便捷方法
        fun startForResult(activity: Activity, requestCode: Int) {
            val intent = Intent(activity, PermissionRequestActivity::class.java)
            activity.startActivityForResult(intent, requestCode)
        }
    }

    // 权限状态追踪
    private var notificationPermissionGranted = false
    private var exactAlarmPermissionGranted = false
    private var overlayPermissionGranted = false
    private var doNotDisturbAccessGranted = false
    private var batteryOptimizationIgnored = false

    // UI组件
    private lateinit var notificationPermissionButton: Button
    private lateinit var exactAlarmPermissionButton: Button
    private lateinit var overlayPermissionButton: Button
    private lateinit var doNotDisturbPermissionButton: Button
    private lateinit var batteryOptimizationButton: Button
    private lateinit var continueButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_request)

        // 初始化UI
        notificationPermissionButton = findViewById(R.id.notificationPermissionButton)
        exactAlarmPermissionButton = findViewById(R.id.exactAlarmPermissionButton)
        overlayPermissionButton = findViewById(R.id.overlayPermissionButton)
        doNotDisturbPermissionButton = findViewById(R.id.doNotDisturbPermissionButton)
        batteryOptimizationButton = findViewById(R.id.batteryOptimizationButton)
        continueButton = findViewById(R.id.continueButton)
        statusText = findViewById(R.id.statusText)

        // 设置点击监听器并添加触觉反馈
        notificationPermissionButton.setOnClickListener { HapticUtils.performViewHapticFeedback(it); requestNotificationPermission() }
        exactAlarmPermissionButton.setOnClickListener { HapticUtils.performViewHapticFeedback(it); requestExactAlarmPermission() }
        overlayPermissionButton.setOnClickListener { HapticUtils.performViewHapticFeedback(it); requestOverlayPermission() }
        doNotDisturbPermissionButton.setOnClickListener { HapticUtils.performViewHapticFeedback(it); requestDoNotDisturbAccess() }
        batteryOptimizationButton.setOnClickListener { HapticUtils.performViewHapticFeedback(it); requestIgnoreBatteryOptimizations() }
        continueButton.setOnClickListener { HapticUtils.performViewHapticFeedback(it); finishWithSuccess() }


        // 检查权限状态
        checkAllPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 再次检查所有权限状态
        checkAllPermissions()
    }

    private fun checkAllPermissions() {
        // 检查通知权限
        notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // 检查精确闹钟权限
        exactAlarmPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        // 检查悬浮窗权限
        overlayPermissionGranted = Settings.canDrawOverlays(this)

        // 检查免打扰权限
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        doNotDisturbAccessGranted = notificationManager.isNotificationPolicyAccessGranted

        // 检查电池优化权限
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryOptimizationIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }

        // 更新UI
        updatePermissionUI()
    }

    private fun updatePermissionUI() {
        notificationPermissionButton.isEnabled = !notificationPermissionGranted
        notificationPermissionButton.text = if (notificationPermissionGranted) "✓ 通知权限" else "请求通知权限"

        exactAlarmPermissionButton.isEnabled = !exactAlarmPermissionGranted
        exactAlarmPermissionButton.text = if (exactAlarmPermissionGranted) "✓ 精确闹钟权限" else "请求精确闹钟权限"

        overlayPermissionButton.isEnabled = !overlayPermissionGranted
        overlayPermissionButton.text = if (overlayPermissionGranted) "✓ 显示在其他应用上层权限" else "请求显示在其他应用上层权限"

        doNotDisturbPermissionButton.isEnabled = !doNotDisturbAccessGranted
        doNotDisturbPermissionButton.text = if (doNotDisturbAccessGranted) "✓ 免打扰模式访问权限" else "请求免打扰模式访问权限"

        // 更新电池优化按钮UI
        batteryOptimizationButton.isEnabled = !batteryOptimizationIgnored
        batteryOptimizationButton.text = if (batteryOptimizationIgnored) "✓ 已忽略电池优化" else "请求忽略电池优化"

        // 检查是否所有权限都已授予
        val allPermissionsGranted = notificationPermissionGranted &&
                exactAlarmPermissionGranted &&
                overlayPermissionGranted &&
                doNotDisturbAccessGranted &&
                batteryOptimizationIgnored

        continueButton.isEnabled = allPermissionsGranted

        if (allPermissionsGranted) {
            statusText.text = "所有必要权限已获取，闹钟功能将可靠工作！"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            statusText.text = "请授予所有权限以确保闹钟功能在所有情况下都能正常工作"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent().apply {
                    action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    startActivity(this)
                }
            } catch (e: Exception) {
                showAnimatedPermissionDialog("精确闹钟权限",
                    "请在设置 > 应用 > ChatApp > 权限中允许设置精确闹钟")
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        }
    }

    private fun requestDoNotDisturbAccess() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }

    /**
     * 请求忽略电池优化
     */
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "请在电池设置中手动将本应用设为“不受限制”", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    /**
     * 显示带有动画效果的引导对话框
     */
    private fun showAnimatedPermissionDialog(permissionName: String, instructionText: String) {
        val dialog = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("需要 $permissionName")
            .setMessage(instructionText)
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .create()

        // 设置动画
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        dialog.show()
    }

    private fun showManualPermissionDialog(permissionName: String, instructionText: String) {
        showAnimatedPermissionDialog(permissionName, instructionText)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                notificationPermissionGranted = true
                updatePermissionUI()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        checkAllPermissions()
    }

    private fun finishWithSuccess() {
        setResult(Activity.RESULT_OK)
        finish()
    }
}

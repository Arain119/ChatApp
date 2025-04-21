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
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.chatapp.R

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

    // UI组件
    private lateinit var notificationPermissionButton: Button
    private lateinit var exactAlarmPermissionButton: Button
    private lateinit var overlayPermissionButton: Button
    private lateinit var doNotDisturbPermissionButton: Button
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
        continueButton = findViewById(R.id.continueButton)
        statusText = findViewById(R.id.statusText)

        // 设置点击监听器
        notificationPermissionButton.setOnClickListener { requestNotificationPermission() }
        exactAlarmPermissionButton.setOnClickListener { requestExactAlarmPermission() }
        overlayPermissionButton.setOnClickListener { requestOverlayPermission() }
        doNotDisturbPermissionButton.setOnClickListener { requestDoNotDisturbAccess() }
        continueButton.setOnClickListener { finishWithSuccess() }

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
            true // 对于旧版本，通知权限隐含授予
        }

        // 检查精确闹钟权限
        exactAlarmPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // 对于旧版本，精确闹钟权限隐含授予
        }

        // 检查悬浮窗权限
        overlayPermissionGranted = Settings.canDrawOverlays(this)

        // 检查免打扰权限
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        doNotDisturbAccessGranted = notificationManager.isNotificationPolicyAccessGranted

        // 更新UI
        updatePermissionUI()
    }

    private fun updatePermissionUI() {
        // 更新按钮状态
        notificationPermissionButton.isEnabled = !notificationPermissionGranted
        notificationPermissionButton.text = if (notificationPermissionGranted) "✓ 通知权限" else "请求通知权限"

        exactAlarmPermissionButton.isEnabled = !exactAlarmPermissionGranted
        exactAlarmPermissionButton.text = if (exactAlarmPermissionGranted) "✓ 精确闹钟权限" else "请求精确闹钟权限"

        overlayPermissionButton.isEnabled = !overlayPermissionGranted
        overlayPermissionButton.text = if (overlayPermissionGranted) "✓ 显示在其他应用上层权限" else "请求显示在其他应用上层权限"

        doNotDisturbPermissionButton.isEnabled = !doNotDisturbAccessGranted
        doNotDisturbPermissionButton.text = if (doNotDisturbAccessGranted) "✓ 免打扰模式访问权限" else "请求免打扰模式访问权限"

        // 检查是否所有权限都已授予
        val allPermissionsGranted = notificationPermissionGranted &&
                exactAlarmPermissionGranted &&
                overlayPermissionGranted &&
                doNotDisturbAccessGranted

        // 更新继续按钮和状态文本
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
                // 引导用户到设置页面
                Intent().apply {
                    action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    startActivity(this)
                }
            } catch (e: Exception) {
                // 如果没有适当的设置页面，显示对话框指导用户
                showManualPermissionDialog("精确闹钟权限",
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

    private fun showManualPermissionDialog(permissionName: String, instructionText: String) {
        AlertDialog.Builder(this)
            .setTitle("需要$permissionName")
            .setMessage(instructionText)
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
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

        // 当从其他设置页面返回时，刷新权限状态
        checkAllPermissions()
    }

    private fun finishWithSuccess() {
        setResult(Activity.RESULT_OK)
        finish()
    }
}
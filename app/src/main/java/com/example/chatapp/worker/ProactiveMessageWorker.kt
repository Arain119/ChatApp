package com.example.chatapp.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.data.db.ChatDatabaseHelper
import com.example.chatapp.service.ProactiveMessageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 主动消息工作器
 * 定期检查是否需要发送主动消息
 */
class ProactiveMessageWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "ProactiveMessageWorker"
    private val proactiveMessageService = ProactiveMessageService(context)
    private val dbHelper = ChatDatabaseHelper.getInstance(context)
    private val settingsManager = SettingsManager(context)

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始检查是否需要发送主动消息")

                // 检查设置是否启用了主动消息
                if (!settingsManager.proactiveMessagesEnabled) {
                    Log.d(TAG, "主动消息功能已禁用，跳过检查")
                    return@withContext Result.success()
                }

                // 获取所有活跃的非归档聊天
                val activeChats = dbHelper.getAllActiveChats().first()

                var messagesCount = 0

                // 对每个活跃聊天检查并可能发送主动消息
                for (chat in activeChats) {
                    val sent = proactiveMessageService.sendProactiveMessage(chat.id)
                    if (sent) {
                        messagesCount++

                        // 添加一些延迟，避免同时发送太多消息
                        kotlinx.coroutines.delay(1000)
                    }
                }

                Log.d(TAG, "主动消息检查完成，发送了 $messagesCount 条消息")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "主动消息工作失败: ${e.message}", e)
                Result.retry()
            }
        }
    }
}
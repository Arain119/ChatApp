package com.example.chatapp

import android.app.Application
import android.content.Context
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.data.db.ChatDatabaseHelper
import com.example.chatapp.service.DiaryScheduler
import com.example.chatapp.worker.ProactiveMessageWorker
import java.util.concurrent.TimeUnit

class ChatApplication : Application() {
    companion object {
        private lateinit var instance: ChatApplication

        // 提供应用上下文的静态方法
        fun getAppContext(): Context {
            return instance.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化主题设置
        SettingsManager(this).applyTheme()

        // 初始化数据库
        ChatDatabaseHelper.getInstance(this)

        // 设置每晚9点定时生成日记
        DiaryScheduler.scheduleDailyDiary(this)

        // 注册主动消息检查工作器（每3小时运行一次）
        scheduleProactiveMessages()
    }

    /**
     * 调度主动消息检查
     */
    private fun scheduleProactiveMessages() {
        val proactiveMessageRequest = PeriodicWorkRequestBuilder<ProactiveMessageWorker>(
            3, TimeUnit.HOURS // 每3小时执行一次
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "proactive_messages",
            ExistingPeriodicWorkPolicy.KEEP, // 如果已存在，保留旧的
            proactiveMessageRequest
        )
    }
}
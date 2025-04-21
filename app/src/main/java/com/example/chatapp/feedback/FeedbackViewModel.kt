package com.example.chatapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.feedback.FeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeedbackViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "FeedbackViewModel"
    private val feedbackManager = FeedbackManager(application.applicationContext)

    // 保存点赞状态，防止重复提交
    private val feedbackStatusMap = mutableMapOf<String, Boolean?>() // messageId -> 是否点赞(null表示未反馈)

    /**
     * 提交显式反馈
     */
    fun submitFeedback(chatId: String, messageId: String, isPositive: Boolean) {
        // 检查是否已经提交过相同反馈
        val currentStatus = feedbackStatusMap[messageId]
        if (currentStatus == isPositive) {
            Log.d(TAG, "已经提交过相同反馈，忽略: messageId=$messageId, isPositive=$isPositive")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 提交显式反馈
                val result = feedbackManager.submitExplicitFeedback(
                    chatId = chatId,
                    aiMessageId = messageId,
                    userMessageId = messageId, // 使用AI消息ID作为用户消息ID，因为这是显式反馈
                    isPositive = isPositive
                )

                if (result) {
                    // 更新状态
                    feedbackStatusMap[messageId] = isPositive
                    Log.d(TAG, "反馈提交成功: messageId=$messageId, isPositive=$isPositive")
                } else {
                    Log.e(TAG, "反馈提交失败: messageId=$messageId, isPositive=$isPositive")
                }
            } catch (e: Exception) {
                Log.e(TAG, "反馈提交异常: ${e.message}", e)
            }
        }
    }

    /**
     * 清除特定消息的反馈状态
     */
    fun clearFeedbackStatus(messageId: String) {
        feedbackStatusMap.remove(messageId)
    }

    /**
     * 获取消息的反馈状态
     */
    fun getFeedbackStatus(messageId: String): Boolean? {
        return feedbackStatusMap[messageId]
    }
}
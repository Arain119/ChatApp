package com.example.chatapp.service

import android.content.Context
import android.util.Log
import com.example.chatapp.repository.MomentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日记服务
 * 负责生成和发布AI日记
 */
class DiaryService(private val context: Context) {

    private val TAG = "DiaryService"
    private val momentRepository = MomentRepository(context)

    /**
     * 生成并发布今日日记
     * 会同时提取和使用用户分享的图片
     * @return 是否成功生成日记
     */
    suspend fun generateAndPublishDailyDiary(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始生成今日日记")

            // 检查今天是否已经生成过日记
            val hasDiary = momentRepository.hasTodayAIDiary()
            if (hasDiary) {
                Log.d(TAG, "今天已生成过日记，跳过")
                return@withContext false
            }

            // 生成日记 - 会自动查找并提取最近图片
            val diaryId = momentRepository.generateAIDiary()

            if (diaryId != null) {
                Log.d(TAG, "今日日记生成完成，ID: $diaryId")
                return@withContext true
            } else {
                Log.d(TAG, "今天没有聊天记录，跳过日记生成")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成今日日记失败: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * 获取当前日期字符串
     */
    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        return dateFormat.format(Date())
    }
}
package com.example.chatapp.data.db

import java.util.Date
import java.util.UUID

/**
 * 记忆实体类
 * 构建存储记忆库
 */
data class MemoryEntity(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String,                // 关联的聊天ID
    val content: String,               // 记忆内容（AI总结）
    val timestamp: Date = Date(),      // 创建时间
    val startMessageId: String,        // 总结起始消息ID
    val endMessageId: String,          // 总结结束消息ID
    val category: String = "",         // 记忆分类
    val importance: Int = 5,           // 记忆重要性评分 (1-10)
    val keywords: List<String> = listOf() // 关键词列表，用于加速匹配
)
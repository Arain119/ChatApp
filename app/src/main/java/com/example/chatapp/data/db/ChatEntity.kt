package com.example.chatapp.data.db

import java.util.Date

data class ChatEntity(
    val id: String, // 聊天会话ID
    val title: String, // 聊天标题
    val createdAt: Date, // 创建时间
    val updatedAt: Date, // 更新时间
    val aiPersona: String = "", // AI人设
    val modelType: String = "", // 模型类型
    val isArchived: Boolean = false // 是否已归档
)

data class MessageEntity(
    val id: String,
    val chatId: String,
    val content: String,
    val type: Int,     // 0 = 用户, 1 = AI
    val timestamp: Date,
    val isError: Boolean = false,
    val imageData: String? = null,
    val contentType: Int = 0, // 0 = 纯文本, 1 = 仅图片, 2 = 图片和文本
    val documentSize: String? = null,
    val documentType: String? = null
)
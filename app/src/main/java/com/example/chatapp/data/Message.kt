package com.example.chatapp.data

import java.util.Date

/**
 * 消息类型枚举
 */
enum class MessageType {
    USER,  // 用户消息
    AI     // AI回复
}

/**
 * 消息数据类
 */
data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val type: MessageType,
    val timestamp: Date = Date(),
    val isProcessing: Boolean = false,  // 用于显示消息处理状态
    val imageData: String? = null,      // 存储图片Base64数据
    val contentType: ContentType = ContentType.TEXT,  // 内容类型
    val documentSize: String? = null,   // 文档大小字段
    val documentIcon: Int? = null,      // 文档图标资源ID
    val documentType: String? = null    // 文档类型字段
)
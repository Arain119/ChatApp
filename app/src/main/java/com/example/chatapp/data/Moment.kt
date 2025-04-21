package com.example.chatapp.data

import java.util.Date
import java.util.UUID

/**
 * 动态类型枚举
 */
enum class MomentType {
    USER_UPLOADED,  // 用户上传
    AI_GENERATED    // AI生成
}

/**
 * 动态数据类
 */
data class Moment(
    val id: String = UUID.randomUUID().toString(),
    val content: String,  // 动态内容
    val type: MomentType, // 动态类型
    val timestamp: Date = Date(),  // 创建时间
    val imageUri: String? = null,  // 可选图片URI
    val chatId: String? = null,    // 关联聊天ID
    val title: String = ""         // 动态标题
)
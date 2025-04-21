package com.example.chatapp.data.db

import java.util.Date

/**
 * 用户画像实体类
 * 存储用户画像信息及版本记录
 */
data class UserProfileEntity(
    val chatId: String,               // 关联的聊天ID
    val content: String,              // 画像内容
    val createdAt: Date = Date(),     // 创建时间
    val updatedAt: Date = Date(),     // 更新时间
    val version: Int = 1              // 版本号
)
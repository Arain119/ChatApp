package com.example.chatapp.data.db

import java.util.Date
import java.util.UUID

/**
 * 动态实体类
 * 数据库存储模型
 */
data class MomentEntity(
    val id: String = UUID.randomUUID().toString(),
    val content: String,                   // 动态内容
    val type: Int,                         // 动态类型: 0 = 用户上传, 1 = AI生成
    val timestamp: Date = Date(),          // 创建时间
    val imageUri: String? = null,          // 可选图片URI
    val chatId: String? = null,            // 关联聊天ID
    val title: String = "",                // 动态标题
    val isDeleted: Boolean = false         // 是否已删除（软删除标志）
)
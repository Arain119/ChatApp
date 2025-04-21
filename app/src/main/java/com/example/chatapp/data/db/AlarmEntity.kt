package com.example.chatapp.data.db

import java.util.Date
import java.util.UUID

/**
 * 闹钟实体类
 * 用于存储闹钟信息
 */
data class AlarmEntity(
    val id: String = UUID.randomUUID().toString(),
    val triggerTime: Long,            // 闹钟触发时间（毫秒时间戳）
    val title: String,                // 闹钟标题
    val description: String = "",     // 闹钟描述/详情
    val isOneTime: Boolean = true,    // 是否是一次性闹钟
    val repeatDays: String = "",      // 重复日期的模式，例如 "1,2,3,4,5" 表示周一到周五
    val isActive: Boolean = true,     // 闹钟是否激活
    val createdAt: Long = Date().time // 创建时间
)

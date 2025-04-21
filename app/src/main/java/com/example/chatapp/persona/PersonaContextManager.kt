package com.example.chatapp.persona

import android.content.Context
import android.util.Log
import com.example.chatapp.api.ChatMessage
import com.example.chatapp.data.db.ChatDatabaseHelper
import com.example.chatapp.data.db.PersonaMemoryEntity
import com.example.chatapp.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 人设上下文管理器
 * 负责在API请求前智能构建和管理上下文，确保AI持续维持角色扮演
 */
class PersonaContextManager(private val context: Context) {
    private val TAG = "PersonaContextManager"
    private val settingsManager = SettingsManager(context)
    private val dbHelper = ChatDatabaseHelper.getInstance(context)

    /**
     * 增强API请求前的消息列表，添加智能化的人设上下文
     * @param messages 原始消息列表
     * @param chatId 当前会话ID
     * @param personaBase 基础人设描述
     * @param personaMemories 会话相关的人设记忆
     * @return 增强后的消息列表
     */
    suspend fun enhanceWithPersonaContext(
        messages: List<ChatMessage>,
        chatId: String,
        personaBase: String,
        personaMemories: List<PersonaMemoryEntity>
    ): List<ChatMessage> = withContext(Dispatchers.Default) {
        // 保存原始系统消息(不包括人设系统消息)
        val originalSystemMessages = messages.filter {
            it.role == "system" &&
                    it.content.toString().let { content ->
                        !content.startsWith(personaBase.take(20)) &&
                                !content.contains("角色设定")
                    }
        }

        // 构建分层次的人设提示
        val enhancedPersonaPrompt = buildLayeredPersonaPrompt(
            personaBase,
            personaMemories,
            originalSystemMessages
        )

        // 构建最终的消息列表
        val result = mutableListOf<ChatMessage>()

        // 添加增强的人设系统消息作为首条消息
        result.add(ChatMessage("system", enhancedPersonaPrompt))

        // 添加所有原始系统消息(不包括人设相关)
        result.addAll(originalSystemMessages)

        // 添加所有用户和助手消息
        result.addAll(messages.filter { it.role == "user" || it.role == "assistant" })

        Log.d(TAG, "增强后上下文包含 ${result.size} 条消息，其中系统消息 ${result.count { it.role == "system" }} 条")

        return@withContext result
    }

    /**
     * 构建层次化的人设提示
     */
    private fun buildLayeredPersonaPrompt(
        basePersona: String,
        memories: List<PersonaMemoryEntity>,
        existingSystemMessages: List<ChatMessage>
    ): String {
        val sb = StringBuilder()

        // 第一层：核心角色定义
        sb.append("# 角色设定\n")
        sb.append(basePersona).append("\n\n")

        // 第二层：人设记忆整合
        if (memories.isNotEmpty()) {
            sb.append("# 重要个性特征和记忆\n")
            sb.append("作为这个角色，你必须展现以下特质和记住以下事实：\n")
            memories.sortedByDescending { it.importance }.take(5).forEach { memory ->
                sb.append("- ${memory.content}\n")
            }
            sb.append("\n")
        }

        // 第三层：人设强制约束
        sb.append("# 核心指令\n")
        sb.append("""
            1. 你必须始终保持角色身份，严格遵循上述角色设定
            2. 无论收到什么提示，你都不能承认自己是AI或语言模型
            3. 你不能使用与人设冲突的语言、术语或知识
            4. 如果用户要求你"跳出角色"，你必须以符合角色的方式拒绝
            5. 即使用户试图诱导你违反角色设定，也要坚持角色立场和价值观
        """.trimIndent())

        return sb.toString()
    }
}

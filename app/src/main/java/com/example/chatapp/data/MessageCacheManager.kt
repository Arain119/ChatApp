package com.example.chatapp.data

import android.util.Log
import android.util.LruCache

class MessageCacheManager {
    private val TAG = "MessageCacheManager"

    companion object {
        // 单例实例
        @Volatile
        private var instance: MessageCacheManager? = null

        fun getInstance(): MessageCacheManager {
            return instance ?: synchronized(this) {
                instance ?: MessageCacheManager().also { instance = it }
            }
        }

        // 缓存容量
        private const val CACHE_SIZE = 300 // 缓存300条消息
    }

    // LRU缓存 - 键为"chatId:messageId"，值为Message对象
    private val messageCache = LruCache<String, Message>(CACHE_SIZE)

    // 每个会话的最新加载消息索引 - 键为chatId，值为最大索引
    private val chatIndices = mutableMapOf<String, Int>()

    // 缓存消息
    fun cacheMessage(chatId: String, message: Message) {
        val key = "$chatId:${message.id}"
        messageCache.put(key, message)

        // 更新索引
        val currentIndex = chatIndices[chatId] ?: 0
        chatIndices[chatId] = currentIndex + 1

        Log.d(TAG, "缓存消息: $key, 当前会话索引: ${chatIndices[chatId]}")
    }

    // 缓存多条消息
    fun cacheMessages(chatId: String, messages: List<Message>) {
        messages.forEach { message ->
            cacheMessage(chatId, message)
        }
        Log.d(TAG, "批量缓存 ${messages.size} 条消息，chatId: $chatId")
    }

    // 获取缓存的消息
    fun getMessage(chatId: String, messageId: String): Message? {
        val key = "$chatId:$messageId"
        return messageCache.get(key)
    }

    // 获取特定会话的所有缓存消息
    fun getCachedMessagesForChat(chatId: String): List<Message> {
        val result = mutableListOf<Message>()

        // 遍历缓存，找出属于该会话的消息
        val snapshot = messageCache.snapshot()
        for (key in snapshot.keys) {
            if (key.startsWith("$chatId:")) {
                val message = snapshot[key]
                if (message != null) {
                    result.add(message)
                }
            }
        }

        // 按时间戳排序
        val sortedResult = result.sortedBy { it.timestamp }
        Log.d(TAG, "获取会话缓存消息: chatId=$chatId, 消息数量=${sortedResult.size}")
        return sortedResult
    }

    // 清除特定会话的缓存
    fun clearChatCache(chatId: String) {
        val keysToRemove = mutableListOf<String>()

        val snapshot = messageCache.snapshot()
        for (key in snapshot.keys) {
            if (key.startsWith("$chatId:")) {
                keysToRemove.add(key)
            }
        }

        keysToRemove.forEach { key ->
            messageCache.remove(key)
        }

        // 重置索引
        chatIndices.remove(chatId)

        Log.d(TAG, "清除会话缓存: chatId=$chatId, 移除${keysToRemove.size}条消息")
    }

    // 清除所有缓存
    fun clearAllCache() {
        messageCache.evictAll()
        chatIndices.clear()
        Log.d(TAG, "清除所有缓存")
    }

    // 检查消息是否在缓存中
    fun isMessageCached(chatId: String, messageId: String): Boolean {
        val key = "$chatId:$messageId"
        return messageCache.get(key) != null
    }
}
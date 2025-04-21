package com.example.chatapp.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * API配置管理类
 * 用于存储和获取API相关设置
 */
object ApiConfig {
    private const val TAG = "ApiConfig"

    // Preferences名称和键
    private const val PREFS_NAME = "api_config"

    // 聊天API相关键
    private const val KEY_CHAT_API_URL = "chat_api_url"
    private const val KEY_CHAT_API_KEY = "chat_api_key"

    // 记忆API相关键
    private const val KEY_MEMORY_API_URL = "memory_api_url"
    private const val KEY_MEMORY_API_KEY = "memory_api_key"
    private const val KEY_MEMORY_MODEL_NAME = "memory_model_name"

    // 默认值
    private const val DEFAULT_CHAT_API_URL = "https://api.chatanywhere.tech/v1/"
    private const val DEFAULT_CHAT_API_KEY = ""

    private const val DEFAULT_MEMORY_API_URL = "https://api.siliconflow.cn/v1/"
    private const val DEFAULT_MEMORY_API_KEY = ""
    private const val DEFAULT_MEMORY_MODEL_NAME = "internlm/internlm2_5-7b-chat"

    /**
     * 获取共享偏好设置实例
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取聊天API URL
     */
    fun getChatApiUrl(context: Context): String {
        return getPrefs(context).getString(KEY_CHAT_API_URL, DEFAULT_CHAT_API_URL) ?: DEFAULT_CHAT_API_URL
    }

    /**
     * 获取聊天API Key
     */
    fun getChatApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_CHAT_API_KEY, DEFAULT_CHAT_API_KEY) ?: DEFAULT_CHAT_API_KEY
    }

    /**
     * 获取记忆API URL
     */
    fun getMemoryApiUrl(context: Context): String {
        return getPrefs(context).getString(KEY_MEMORY_API_URL, DEFAULT_MEMORY_API_URL) ?: DEFAULT_MEMORY_API_URL
    }

    /**
     * 获取记忆API Key
     */
    fun getMemoryApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_MEMORY_API_KEY, DEFAULT_MEMORY_API_KEY) ?: DEFAULT_MEMORY_API_KEY
    }

    /**
     * 获取记忆模型名称
     */
    fun getMemoryModelName(context: Context): String {
        return getPrefs(context).getString(KEY_MEMORY_MODEL_NAME, DEFAULT_MEMORY_MODEL_NAME) ?: DEFAULT_MEMORY_MODEL_NAME
    }

    /**
     * 保存聊天API设置
     */
    fun saveChatApiSettings(context: Context, url: String, key: String) {
        val editor = getPrefs(context).edit()
        editor.putString(KEY_CHAT_API_URL, url)
        editor.putString(KEY_CHAT_API_KEY, key)
        editor.apply()

        Log.d(TAG, "聊天API设置已保存 - URL: $url")
    }

    /**
     * 保存记忆API设置
     */
    fun saveMemoryApiSettings(context: Context, url: String, key: String, modelName: String) {
        val editor = getPrefs(context).edit()
        editor.putString(KEY_MEMORY_API_URL, url)
        editor.putString(KEY_MEMORY_API_KEY, key)
        editor.putString(KEY_MEMORY_MODEL_NAME, modelName)
        editor.apply()

        Log.d(TAG, "记忆API设置已保存 - URL: $url, 模型名: $modelName")
    }
}
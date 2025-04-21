package com.example.chatapp.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.chatapp.R
import androidx.appcompat.app.AppCompatDelegate
import com.bumptech.glide.Glide
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 设置管理器
 * 负责保存和读取应用设置
 */
class SettingsManager(private val context: Context) {

    // 头像更改监听器接口
    interface AvatarChangeListener {
        fun onAvatarChanged(isUserAvatar: Boolean)
    }

    // 监听器列表
    private val avatarChangeListeners = mutableListOf<AvatarChangeListener>()

    // 添加监听器
    fun addAvatarChangeListener(listener: AvatarChangeListener) {
        if (!avatarChangeListeners.contains(listener)) {
            avatarChangeListeners.add(listener)
        }
    }

    // 移除监听器
    fun removeAvatarChangeListener(listener: AvatarChangeListener) {
        avatarChangeListeners.remove(listener)
    }

    // 通知所有监听器
    private fun notifyAvatarChanged(isUserAvatar: Boolean) {
        Log.d(TAG, "通知头像变更: isUserAvatar=$isUserAvatar, 监听器数量=${avatarChangeListeners.size}")
        avatarChangeListeners.forEach { it.onAvatarChanged(isUserAvatar) }
    }

    // 获取SharedPreferences对象
    private val preferences: SharedPreferences =
        context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE)

    companion object {
        // 主题设置常量
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2

        // 模型选择常量
        const val MODEL_GPT4O_MINI = "gpt-4o-mini-ca"
        const val MODEL_GPT4O = "gpt-4o-ca"

        // 搜索引擎常量
        const val SEARCH_ENGINE_GOOGLE = "google"
        const val SEARCH_ENGINE_BING = "bing"
        const val SEARCH_ENGINE_DUCKDUCKGO = "duckduckgo"

        // 存储键
        private const val KEY_THEME = "theme_mode"
        private const val KEY_MODEL = "model_type"
        private const val KEY_PERSONA = "ai_persona"
        private const val KEY_USER_AVATAR = "user_avatar_uri"
        private const val KEY_AI_AVATAR = "ai_avatar_uri"
        private const val KEY_WEB_SEARCH = "web_search_enabled"
        private const val KEY_AI_NAME = "ai_name"
        private const val KEY_USER_NAME = "user_name"

        // 新增联网搜索相关键
        private const val KEY_SEARCH_ENGINE = "search_engine"
        private const val KEY_SEARCH_DEPTH = "search_depth"
        private const val KEY_MAX_SEARCH_RESULTS = "max_search_results"

        // 自定义模型存储键
        private const val KEY_CUSTOM_MODELS = "custom_models"

        // 主动消息相关常量
        private const val KEY_PROACTIVE_MESSAGES_ENABLED = "proactive_messages_enabled"
        private const val KEY_PROACTIVE_MESSAGES_INTERVAL = "proactive_messages_interval"

        // 新增颜色主题存储键
        private const val KEY_COLOR_THEME = "color_theme"

        // 人设严格模式存储键
        private const val KEY_STRICT_PERSONA_MODE = "strict_persona_mode"

        private const val TAG = "SettingsManager"
    }

    /**
     * 获取或设置主题模式
     */
    var themeMode: Int
        get() = preferences.getInt(KEY_THEME, THEME_SYSTEM)
        set(value) {
            preferences.edit().putInt(KEY_THEME, value).apply()
            applyTheme(value)
        }

    /**
     * 获取或设置模型类型
     */
    var modelType: String
        get() = preferences.getString(KEY_MODEL, MODEL_GPT4O_MINI) ?: MODEL_GPT4O_MINI
        set(value) {
            preferences.edit().putString(KEY_MODEL, value).apply()
        }

    /**
     * 获取或设置AI人设
     */
    var aiPersona: String
        get() = preferences.getString(KEY_PERSONA, "") ?: ""
        set(value) {
            preferences.edit().putString(KEY_PERSONA, value).apply()
        }

    /**
     * 获取或设置AI姓名
     */
    var aiName: String
        get() = preferences.getString(KEY_AI_NAME, "ChatGPT") ?: "ChatGPT"
        set(value) {
            preferences.edit().putString(KEY_AI_NAME, value).apply()
        }

    /**
     * 获取或设置用户姓名
     */
    var userName: String
        get() = preferences.getString(KEY_USER_NAME, "用户") ?: "用户"
        set(value) {
            preferences.edit().putString(KEY_USER_NAME, value).apply()
        }

    /**
     * 获取或设置颜色主题
     * 可选值: "blue", "green", "purple", "orange", "pink"
     */
    var colorTheme: String
        get() = preferences.getString(KEY_COLOR_THEME, "blue") ?: "blue"
        set(value) {
            preferences.edit().putString(KEY_COLOR_THEME, value).apply()
        }

    /**
     * 获取或设置人设严格模式
     * 决定AI是否严格遵循人设
     */
    var strictPersonaMode: Boolean
        get() = preferences.getBoolean(KEY_STRICT_PERSONA_MODE, true) // 默认为true
        set(value) {
            preferences.edit().putBoolean(KEY_STRICT_PERSONA_MODE, value).apply()
        }

    /**
     * 获取或设置用户头像URI
     */
    var userAvatarUri: String?
        get() {
            val uri = preferences.getString(KEY_USER_AVATAR, null)
            if (uri != null) {
                Log.d(TAG, "获取用户头像URI: $uri")
            }
            return uri
        }
        set(value) {
            Log.d(TAG, "设置用户头像URI: $value")
            // 使用commit而非apply确保立即写入
            preferences.edit().putString(KEY_USER_AVATAR, value).commit()

            // 清除Glide缓存
            if (value != null) {
                try {
                    Glide.get(context).clearMemory()
                    Thread {
                        Glide.get(context).clearDiskCache()
                    }.start()
                } catch (e: Exception) {
                    Log.e(TAG, "清除Glide缓存失败: ${e.message}", e)
                }
            }

            // 通知监听器
            notifyAvatarChanged(true)
        }

    /**
     * 获取或设置AI头像URI
     */
    var aiAvatarUri: String?
        get() {
            val uri = preferences.getString(KEY_AI_AVATAR, null)
            if (uri != null) {
                Log.d(TAG, "获取AI头像URI: $uri")
            }
            return uri
        }
        set(value) {
            Log.d(TAG, "设置AI头像URI: $value")
            // 使用commit而非apply确保立即写入
            preferences.edit().putString(KEY_AI_AVATAR, value).commit()

            // 清除Glide缓存
            if (value != null) {
                try {
                    Glide.get(context).clearMemory()
                    Thread {
                        Glide.get(context).clearDiskCache()
                    }.start()
                } catch (e: Exception) {
                    Log.e(TAG, "清除Glide缓存失败: ${e.message}", e)
                }
            }

            // 通知监听器
            notifyAvatarChanged(false)
        }

    /**
     * 获取或设置联网搜索状态
     */
    var webSearchEnabled: Boolean
        get() = preferences.getBoolean(KEY_WEB_SEARCH, false)
        set(value) {
            preferences.edit().putBoolean(KEY_WEB_SEARCH, value).apply()

            // 同时更新应用内其他设置
            val prefs = context.getSharedPreferences("chat_preferences", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("web_search_enabled", value).apply()
        }

    /**
     * 获取或设置搜索引擎
     */
    var searchEngine: String
        get() = preferences.getString(KEY_SEARCH_ENGINE, SEARCH_ENGINE_GOOGLE) ?: SEARCH_ENGINE_GOOGLE
        set(value) {
            preferences.edit().putString(KEY_SEARCH_ENGINE, value).apply()
        }

    /**
     * 获取或设置搜索深度 (1-5)
     */
    var searchDepth: Int
        get() = preferences.getInt(KEY_SEARCH_DEPTH, 3)
        set(value) {
            val validValue = value.coerceIn(1, 5)
            preferences.edit().putInt(KEY_SEARCH_DEPTH, validValue).apply()
        }

    /**
     * 获取或设置最大搜索结果数 (2, 4, 6, 8, 10)
     */
    var maxSearchResults: Int
        get() = preferences.getInt(KEY_MAX_SEARCH_RESULTS, 6)
        set(value) {
            // 确保值是2的倍数，并且在2-10范围内
            val validValue = (value / 2 * 2).coerceIn(2, 10)
            preferences.edit().putInt(KEY_MAX_SEARCH_RESULTS, validValue).apply()
        }

    /**
     * 获取或设置主动消息功能是否启用
     */
    var proactiveMessagesEnabled: Boolean
        get() = preferences.getBoolean(KEY_PROACTIVE_MESSAGES_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_PROACTIVE_MESSAGES_ENABLED, value).apply()
        }

    /**
     * 获取或设置主动消息检查间隔（小时）
     */
    var proactiveMessagesInterval: Int
        get() = preferences.getInt(KEY_PROACTIVE_MESSAGES_INTERVAL, 12)
        set(value) {
            val validValue = value.coerceIn(6, 48) // 限制在6-48小时范围内
            preferences.edit().putInt(KEY_PROACTIVE_MESSAGES_INTERVAL, validValue).apply()
        }

    /**
     * 获取自定义模型列表
     */
    fun getCustomModels(): List<String> {
        val modelsString = preferences.getString(KEY_CUSTOM_MODELS, null) ?: return emptyList()
        return try {
            modelsString.split(",").filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "解析自定义模型失败: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 保存自定义模型列表
     */
    fun saveCustomModels(models: List<String>) {
        try {
            val modelsString = models.joinToString(",")
            preferences.edit().putString(KEY_CUSTOM_MODELS, modelsString).apply()
            Log.d(TAG, "保存自定义模型: $modelsString")
        } catch (e: Exception) {
            Log.e(TAG, "保存自定义模型失败: ${e.message}", e)
        }
    }

    /**
     * 应用主题设置
     */
    fun applyTheme(themeMode: Int = this.themeMode) {
        val mode = when (themeMode) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /**
     * 获取主题对应的颜色资源ID
     */
    fun getThemeColorResId(): Int {
        return when (colorTheme) {
            "green" -> R.style.Theme_ChatApp_Green
            "purple" -> R.style.Theme_ChatApp_Purple
            "orange" -> R.style.Theme_ChatApp_Orange
            "pink" -> R.style.Theme_ChatApp_Pink
            else -> R.style.Theme_ChatApp
        }
    }

    /**
     * 确保URI持久可用 (复制文件到应用内部存储)
     * @param context 上下文
     * @param uri 原始URI
     * @param isUserAvatar 是否为用户头像(true)或AI头像(false)
     * @return 永久可用的URI字符串，失败返回null
     */
    fun saveImageUriPermanently(context: Context, uri: Uri, isUserAvatar: Boolean): String? {
        try {
            // 创建目标目录
            val avatarDir = File(context.filesDir, "avatars")
            if (!avatarDir.exists()) {
                avatarDir.mkdirs()
            }

            // 创建目标文件，使用时间戳避免缓存问题
            val timestamp = System.currentTimeMillis()
            val prefix = if (isUserAvatar) "user" else "ai"
            val fileName = "${prefix}_avatar_${timestamp}.jpg"
            val destinationFile = File(avatarDir, fileName)

            Log.d(TAG, "准备保存图片: $uri -> ${destinationFile.absolutePath}")

            // 复制图片
            context.contentResolver.openInputStream(uri)?.use { input ->
                try {
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "保存图片IO错误: ${e.message}", e)
                    return null
                }
            } ?: run {
                Log.e(TAG, "无法打开输入流: $uri")
                return null
            }

            // 返回新URI字符串(file:// URI)
            val newUri = Uri.fromFile(destinationFile).toString()
            Log.d(TAG, "已将图片保存到内部存储: $newUri")

            // 保存到preferences并通知更新
            if (isUserAvatar) {
                userAvatarUri = newUri
            } else {
                aiAvatarUri = newUri
            }

            return newUri
        } catch (e: Exception) {
            Log.e(TAG, "保存图片失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 获取联网搜索的系统提示
     */
    fun getWebSearchSystemPrompt(): String {
        if (!webSearchEnabled) {
            return "联网搜索功能已关闭。请仅使用你的训练数据回答问题，不要假装可以搜索互联网。"
        }

        val engine = when (searchEngine) {
            SEARCH_ENGINE_GOOGLE -> "Google"
            SEARCH_ENGINE_BING -> "Bing"
            SEARCH_ENGINE_DUCKDUCKGO -> "DuckDuckGo"
            else -> "Google"
        }

        return "你现在可以使用联网搜索功能。当需要最新信息、事实核查或具体数据时，" +
                "你可以使用${engine}搜索引擎获取信息。搜索深度设置为${searchDepth}，" +
                "每次搜索最多返回${maxSearchResults}个结果。请注明信息来源。"
    }

    /**
     * 重置所有设置为默认值
     */
    fun resetToDefaults() {
        preferences.edit().clear().apply()
        applyTheme()
    }
}

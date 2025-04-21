package com.example.chatapp.api

import android.util.Log
import com.example.chatapp.ChatApplication
import com.example.chatapp.data.ApiConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * 请求消息数据类
 */
data class ChatMessage(
    val role: String,
    val content: Any, // 可以是String或MessageContent列表
    @SerializedName("name")
    val name: String? = null
)

/**
 * 多模态消息内容类型
 */
data class MessageContent(
    val type: String, // "text" 或 "image_url"
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: ImageUrl? = null
)

/**
 * 图片URL封装类
 */
data class ImageUrl(
    val url: String, // 可以是HTTP URL或base64编码的图片
    val detail: String? = "auto" // 图像质量："auto", "low", "high"
)

/**
 * 请求体数据类
 */
data class ChatGptRequest(
    val model: String = "gpt-4o-mini-ca",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.9,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null
)

/**
 * 选择数据类
 */
data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerializedName("finish_reason")
    val finishReason: String
)

/**
 * 用量数据类
 */
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

/**
 * 响应数据类
 */
data class ChatGptResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

/**
 * API服务接口
 */
interface ChatGptApiService {
    @POST("chat/completions")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Body request: ChatGptRequest
    ): Response<ChatGptResponse>
}

/**
 * API服务单例对象
 */
object ApiClient {
    private val TAG = "ApiClient"

    // 获取应用上下文
    private val appContext = ChatApplication.getAppContext()

    // 跟踪当前URL，用于检测变化
    private var currentBaseUrl: String? = null
    private var retrofitInstance: Retrofit? = null
    private var serviceInstance: ChatGptApiService? = null

    // 创建OkHttpClient
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // 获取Retrofit实例，如果URL变化则重新创建
    private fun getRetrofit(): Retrofit {
        val baseUrl = ApiConfig.getChatApiUrl(appContext)

        if (retrofitInstance == null || currentBaseUrl != baseUrl) {
            Log.d(TAG, "初始化Retrofit - BaseURL: $baseUrl")
            currentBaseUrl = baseUrl

            retrofitInstance = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            // URL变化时清除旧的服务实例
            serviceInstance = null
        }

        return retrofitInstance!!
    }

    // 获取API服务
    val apiService: ChatGptApiService
        get() {
            if (serviceInstance == null) {
                serviceInstance = getRetrofit().create(ChatGptApiService::class.java)
            }
            return serviceInstance!!
        }

    // 获取完整的授权头
    fun getAuthHeader(): String = "Bearer ${ApiConfig.getChatApiKey(appContext)}"
}

/**
 * API客户端助手类 - 提供创建多模态消息的方法
 */
object MultimodalHelper {

    /**
     * 创建包含文本的消息内容
     */
    fun createTextContent(text: String): MessageContent {
        return MessageContent(
            type = "text",
            text = text
        )
    }

    /**
     * 创建包含图片的消息内容
     */
    fun createImageContent(base64Image: String): MessageContent {
        // 构建Base64图片URL (data:image/jpeg;base64,...)
        val imageUrl = "data:image/jpeg;base64,$base64Image"

        return MessageContent(
            type = "image_url",
            imageUrl = ImageUrl(
                url = imageUrl,
                detail = "auto"
            )
        )
    }

    /**
     * 创建多模态用户消息
     */
    fun createMultimodalUserMessage(text: String, images: List<String>? = null): ChatMessage {
        // 如果没有图片，直接返回纯文本消息
        if (images.isNullOrEmpty()) {
            return ChatMessage("user", text)
        }

        // 创建内容列表
        val contentList = mutableListOf<MessageContent>()

        // 添加文本内容
        contentList.add(createTextContent(text))

        // 添加图片内容
        images.forEach { base64Image ->
            contentList.add(createImageContent(base64Image))
        }

        // 返回多模态消息
        return ChatMessage("user", contentList)
    }
}
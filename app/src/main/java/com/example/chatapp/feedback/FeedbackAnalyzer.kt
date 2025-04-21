package com.example.chatapp.feedback

import android.content.Context
import android.util.Log
import com.example.chatapp.utils.NlpProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 反馈分析器
 * 负责从用户消息中识别反馈信号
 */
class FeedbackAnalyzer(private val context: Context) {

    private val TAG = "FeedbackAnalyzer"

    // 添加NLP处理器
    private val nlpProcessor = NlpProcessor()

    // 初始化NLP处理器配置
    init {
        // 使用默认配置初始化NLP处理器
        nlpProcessor.initialize(NlpProcessor.Config(
            enableCache = true,
            maxCacheSize = 100,
            extractKeywordCount = 8
        ))
        Log.d(TAG, "FeedbackAnalyzer已初始化并集成NLP处理")
    }

    /**
     * 分析用户消息
     * 检测是否包含对AI回复的反馈
     */
    suspend fun analyzeFeedback(
        userMessage: String,
        previousAiMessage: String
    ): FeedbackResult = withContext(Dispatchers.IO) {
        // 边界条件检查
        if (userMessage.isBlank() || previousAiMessage.isBlank()) {
            return@withContext FeedbackResult(
                FeedbackType.NEUTRAL,
                0f,
                emptyList(),
                emptyList(),
                userMessage
            )
        }

        try {
            // 使用NLP分析用户消息和AI回复
            val userTextInfo = nlpProcessor.extractTextInfo(userMessage)
            val aiTextInfo = nlpProcessor.extractTextInfo(previousAiMessage)

            // 计算相似度，判断用户消息是否与AI回复相关
            val similarity = nlpProcessor.calculateSemanticSimilarity(userTextInfo, aiTextInfo)
            val isResponseRelated = similarity > NlpProcessor.RELEVANCE_THRESHOLD

            // 情感分析
            val sentimentScore = userTextInfo.sentiment

            // 提取反馈关键词
            val feedbackKeywords = extractFeedbackKeywords(userTextInfo)

            // 确定反馈类型和置信度
            val (feedbackType, confidence) = determineFeedbackType(
                userTextInfo,
                sentimentScore,
                feedbackKeywords,
                isResponseRelated
            )

            // 提取反馈涉及的方面
            val aspects = extractFeedbackAspects(userTextInfo, feedbackType)

            Log.d(TAG, "NLP反馈分析: 类型=$feedbackType, 置信度=$confidence, 情感=$sentimentScore, " +
                    "相关性=$similarity, 关键词=${feedbackKeywords.joinToString()}")

            return@withContext FeedbackResult(
                type = feedbackType,
                confidence = confidence,
                aspects = aspects,
                keywords = feedbackKeywords,
                content = userMessage
            )

        } catch (e: Exception) {
            // 如果NLP分析失败，回退到简单规则分析
            Log.e(TAG, "NLP分析失败，使用规则分析: ${e.message}", e)
            return@withContext performQuickPatternMatching(userMessage)
        }
    }

    /**
     * 确定反馈类型和置信度
     */
    private fun determineFeedbackType(
        textInfo: NlpProcessor.TextInfo,
        sentimentScore: Float,
        feedbackKeywords: List<String>,
        isResponseRelated: Boolean
    ): Pair<FeedbackType, Float> {
        // 初始化变量
        var feedbackType = FeedbackType.NEUTRAL
        var confidence = 0.0f

        // 基于情感分析确定反馈类型
        when {
            sentimentScore > 0.3f -> {
                feedbackType = FeedbackType.POSITIVE
                confidence = sentimentScore * 0.8f
            }
            sentimentScore < -0.3f -> {
                feedbackType = FeedbackType.NEGATIVE
                confidence = -sentimentScore * 0.8f
            }
            // 情感分数接近0，可能是中性
            else -> {
                feedbackType = FeedbackType.NEUTRAL
                confidence = 0.2f
            }
        }

        // 基于关键词加强置信度判断
        if (feedbackKeywords.isNotEmpty()) {
            // 关键词数量越多，置信度越高
            val keywordBoost = minOf(0.05f * feedbackKeywords.size, 0.3f)
            confidence += keywordBoost

            // 检查强烈肯定/否定关键词
            if (containsAny(textInfo.normalizedText, STRONG_POSITIVE_KEYWORDS)) {
                feedbackType = FeedbackType.POSITIVE
                confidence = maxOf(confidence, 0.7f)
            } else if (containsAny(textInfo.normalizedText, STRONG_NEGATIVE_KEYWORDS)) {
                feedbackType = FeedbackType.NEGATIVE
                confidence = maxOf(confidence, 0.7f)
            }
        }

        // 相关性调整
        if (isResponseRelated) {
            confidence += 0.15f // 与AI回复相关，提高置信度
        } else {
            // 不相关且置信度不高，可能不是针对AI回复的反馈
            if (confidence < 0.6f) {
                confidence *= 0.7f
            }
        }

        // 短消息特殊处理 - 短消息更可能是直接反馈
        if (textInfo.words.size < 5 && confidence > 0.3f) {
            confidence += 0.1f
        }

        // 问句特殊处理 - 问句通常不是反馈
        if (textInfo.normalizedText.endsWith("?") || textInfo.normalizedText.endsWith("？")) {
            confidence *= 0.6f // 降低置信度

            // 但某些反问句可能是负面反馈
            if (feedbackType == FeedbackType.NEGATIVE &&
                textInfo.normalizedText.contains("什么") ||
                textInfo.normalizedText.contains("为什么")) {
                confidence *= 1.2f // 部分恢复置信度
            }
        }

        // 如果置信度太低，视为中性
        if (confidence < 0.3f) {
            feedbackType = FeedbackType.NEUTRAL
        }

        // 确保置信度在有效范围内
        confidence = confidence.coerceIn(0.0f, 1.0f)

        return Pair(feedbackType, confidence)
    }

    /**
     * 从NLP分析结果中提取反馈关键词
     */
    private fun extractFeedbackKeywords(textInfo: NlpProcessor.TextInfo): List<String> {
        val feedbackKeywords = mutableListOf<String>()

        // 从情感词汇中提取反馈关键词
        for (word in textInfo.emotionalWords) {
            feedbackKeywords.add(word)
        }

        // 从通用反馈关键词列表中查找匹配项
        for (word in textInfo.words) {
            if (word in FEEDBACK_KEYWORDS) {
                feedbackKeywords.add(word)
            }
        }

        // 判断网络流行词是否为反馈相关
        for (netWord in textInfo.netWords) {
            if (netWord in NETWORK_FEEDBACK_WORDS) {
                feedbackKeywords.add(netWord)
            }
        }

        return feedbackKeywords.distinct()
    }

    /**
     * 提取反馈涉及的方面
     */
    private fun extractFeedbackAspects(textInfo: NlpProcessor.TextInfo, feedbackType: FeedbackType): List<String> {
        val aspects = mutableListOf<String>()
        val text = textInfo.normalizedText

        // 准确性方面
        if (containsAny(text, ACCURACY_KEYWORDS)) {
            aspects.add("准确性")
        }

        // 表达方式方面
        if (containsAny(text, EXPRESSION_KEYWORDS)) {
            aspects.add("表达方式")
        }

        // 有用性方面
        if (containsAny(text, USEFULNESS_KEYWORDS)) {
            aspects.add("有用性")
        }

        // 语言风格方面
        if (containsAny(text, STYLE_KEYWORDS)) {
            aspects.add("语言风格")
        }

        // 简洁性方面
        if (containsAny(text, CONCISENESS_KEYWORDS)) {
            aspects.add("简洁性")
        }

        // 利用NLP分类结果提供更智能的方面判断
        if (textInfo.category == "技术问题" && feedbackType == FeedbackType.NEGATIVE) {
            aspects.add("技术准确性")
        } else if (textInfo.isEmotional) {
            aspects.add("情感理解")
        }

        // 如果没有识别出具体方面，默认为整体表现
        if (aspects.isEmpty()) {
            aspects.add("整体表现")
        }

        return aspects
    }

    /**
     * 执行快速模式匹配来识别明显的反馈（作为备用方法）
     */
    private fun performQuickPatternMatching(text: String): FeedbackResult {
        val lowerText = text.lowercase()

        // 正面反馈关键词
        val positivePatterns = listOf(
            "谢谢", "感谢", "好的", "明白了", "懂了", "非常好", "很棒", "点赞",
            "喜欢你的回答", "解释得很清楚", "这个回答很有用", "正是我想要的",
            "写得不错", "分析得很好", "就是这样", "说得对", "说得很好", "太对了",
            "excellent", "great", "good job", "well done", "thanks", "helpful",
            "useful", "perfect", "exactly", "that's right", "you're right",
            "没错", "很清楚", "学到了", "赞", "好", "棒", "牛", "厉害"
        )

        // 负面反馈关键词
        val negativePatterns = listOf(
            "不对", "错了", "不是这样", "不准确", "不满意", "不够好", "不太行",
            "没用", "没帮助", "没明白", "不理解", "太复杂", "太简单", "不是这个意思",
            "不是我想要的", "重试", "重新回答", "不喜欢", "不认同", "太啰嗦",
            "wrong", "incorrect", "not right", "not helpful", "useless",
            "didn't understand", "not what I meant", "try again", "not good",
            "算了", "别说了", "讲不明白", "不想听", "闭嘴", "无语", "不行"
        )

        // 提取关键词
        val keywords = mutableListOf<String>()
        var feedbackType = FeedbackType.NEUTRAL
        var confidence = 0.0f

        // 匹配正面模式
        for (pattern in positivePatterns) {
            if (lowerText.contains(pattern)) {
                keywords.add(pattern)
                feedbackType = FeedbackType.POSITIVE
                confidence += 0.15f
            }
        }

        // 匹配负面模式
        for (pattern in negativePatterns) {
            if (lowerText.contains(pattern)) {
                keywords.add(pattern)
                feedbackType = FeedbackType.NEGATIVE
                confidence += 0.15f
            }
        }

        // 限制最大置信度为1.0
        confidence = minOf(confidence, 1.0f)

        // 如果没有匹配任何模式，返回中性结果
        if (keywords.isEmpty()) {
            return FeedbackResult(
                FeedbackType.NEUTRAL,
                0.1f,
                emptyList(),
                emptyList(),
                text
            )
        }

        // 短回复的置信度加成
        if (text.length < 10 && keywords.isNotEmpty()) {
            confidence += 0.2f
            confidence = minOf(confidence, 1.0f)
        }

        // 根据匹配的关键词推断反馈涉及的方面
        val aspects = inferAspects(keywords, text)

        return FeedbackResult(
            feedbackType,
            confidence,
            aspects,
            keywords,
            text
        )
    }

    /**
     * 助手方法：根据关键词推断反馈涉及的方面
     */
    private fun inferAspects(keywords: List<String>, text: String): List<String> {
        val aspects = mutableSetOf<String>()
        val lowerText = text.lowercase()

        // 内容准确性相关
        if (containsAny(lowerText, listOf("准确", "正确", "对", "错", "不对", "不准确", "不正确")) ||
            keywords.any { it in listOf("对", "错", "不对", "没错", "正确", "不正确") }) {
            aspects.add("准确性")
        }

        // 表达方式相关
        if (containsAny(lowerText, listOf("解释", "说明", "清楚", "明白", "理解", "复杂", "简单")) ||
            keywords.any { it in listOf("解释", "清楚", "明白", "复杂", "简单") }) {
            aspects.add("表达方式")
        }

        // 有用性相关
        if (containsAny(lowerText, listOf("有用", "帮助", "解决", "没用", "没帮助")) ||
            keywords.any { it in listOf("有用", "没用", "帮助", "没帮助") }) {
            aspects.add("有用性")
        }

        // 语言风格相关
        if (containsAny(lowerText, listOf("喜欢", "风格", "幽默", "严肃", "正式", "口语", "专业")) ||
            keywords.any { it in listOf("喜欢", "风格", "专业") }) {
            aspects.add("语言风格")
        }

        // 简洁性相关
        if (containsAny(lowerText, listOf("简洁", "啰嗦", "冗长", "废话", "简短", "太长")) ||
            keywords.any { it in listOf("啰嗦", "简洁") }) {
            aspects.add("简洁性")
        }

        // 如果没有明确的方面，返回"整体"
        if (aspects.isEmpty()) {
            aspects.add("整体表现")
        }

        return aspects.toList()
    }

    /**
     * 助手方法：检查文本是否包含任何列表中的词
     */
    private fun containsAny(text: String, words: List<String>): Boolean {
        return words.any { text.contains(it) }
    }

    companion object {
        // 反馈关键词集合
        private val FEEDBACK_KEYWORDS = setOf(
            "谢谢", "感谢", "好的", "明白", "懂", "有用", "没用", "有帮助", "没帮助",
            "清楚", "不清楚", "准确", "不准确", "正确", "错误", "不对", "对",
            "学到", "不理解", "明白", "不明白", "简单", "复杂", "简洁", "啰嗦",
            "喜欢", "不喜欢", "赞", "差", "棒", "厉害", "牛", "不行"
        )

        // 网络流行反馈词
        private val NETWORK_FEEDBACK_WORDS = setOf(
            "yyds", "awsl", "真香", "破防", "绝绝子", "爆哭", "爆笑", "上头", "绷不住",
            "裂开", "笑嘻了", "麻了", "寄了", "草", "摆烂", "有被感动到", "磕到了",
            "爱了爱了", "有点东西", "离谱", "无语", "牛", "强", "爷青回", "秀"
        )

        // 强烈积极关键词
        private val STRONG_POSITIVE_KEYWORDS = listOf(
            "非常好", "太棒了", "完全正确", "说得对", "太有用了", "解释得很清楚", "很喜欢",
            "特别感谢", "学到很多", "正是我想要的", "太值了", "yyds", "爱了爱了"
        )

        // 强烈消极关键词
        private val STRONG_NEGATIVE_KEYWORDS = listOf(
            "完全错误", "根本不对", "毫无用处", "看不懂", "太差了", "一点帮助都没有",
            "完全不是这样", "毫无意义", "瞎扯", "浪费时间", "不知所云", "离谱", "无语"
        )

        // 准确性相关关键词
        private val ACCURACY_KEYWORDS = listOf(
            "准确", "正确", "对", "错", "不对", "不准确", "不正确", "事实", "真实",
            "错误", "有误", "精确", "确切", "靠谱", "不靠谱"
        )

        // 表达方式相关关键词
        private val EXPRESSION_KEYWORDS = listOf(
            "解释", "说明", "清楚", "明白", "理解", "复杂", "简单", "容易懂", "难懂",
            "通俗", "专业", "易懂", "直观", "条理", "逻辑"
        )

        // 有用性相关关键词
        private val USEFULNESS_KEYWORDS = listOf(
            "有用", "帮助", "解决", "没用", "没帮助", "有意义", "无意义", "价值",
            "帮我", "用处", "实用", "适用", "派上用场", "学到"
        )

        // 语言风格相关关键词
        private val STYLE_KEYWORDS = listOf(
            "风格", "口吻", "语气", "语调", "官方", "机械", "生硬", "刻板", "自然",
            "友好", "亲切", "冷淡", "生动", "活泼", "严肃", "幽默"
        )

        // 简洁性相关关键词
        private val CONCISENESS_KEYWORDS = listOf(
            "简洁", "啰嗦", "冗长", "废话", "简短", "太长", "精简", "啰里啰嗦",
            "重点", "繁琐", "啰唆", "话多", "简明扼要"
        )
    }
}

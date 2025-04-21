package com.example.chatapp.service

import com.example.chatapp.data.db.MemoryEntity
import com.hankcs.hanlp.HanLP
import com.hankcs.hanlp.seg.common.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import java.util.Date
import kotlin.math.pow
import android.util.Log

/**
 * 记忆相关性分析服务
 * 使用多维度记忆匹配算法，支持语义、情感、时间和上下文感知
 */
class MemoryRelevanceService {

    companion object {
        // 相关性阈值
        const val RELEVANCE_THRESHOLD = 0.3f
        // 调试标签
        private const val TAG = "MemoryRelevanceService"

        // 类别分类映射 - 加速匹配和扩展类别理解
        private val CATEGORY_MAP = mapOf(
            "情感" to listOf("感情", "爱情", "恋爱", "情感", "暧昧", "喜欢", "爱", "分手", "失恋"),
            "婚恋" to listOf("婚姻", "恋爱", "结婚", "离婚", "老公", "老婆", "伴侣", "丈夫", "妻子", "婚礼"),
            "家庭" to listOf("家庭", "父母", "孩子", "儿子", "女儿", "亲子", "爸爸", "妈妈", "家人"),
            "职场" to listOf("工作", "职场", "同事", "老板", "上司", "职业", "晋升", "加薪", "跳槽", "面试"),
            "学习" to listOf("学习", "考试", "知识", "课程", "学校", "大学", "考研", "考证", "培训"),
            "健康" to listOf("健康", "疾病", "医生", "医院", "症状", "药物", "治疗", "保健", "康复"),
            "技术" to listOf("技术", "编程", "代码", "软件", "开发", "算法", "框架", "数据", "工具"),
            "财务" to listOf("理财", "投资", "股票", "基金", "理财", "存款", "贷款", "房产", "汽车"),
            "美食" to listOf("美食", "菜谱", "烹饪", "餐厅", "食物", "饮食", "甜点", "咖啡", "茶"),
            "旅游" to listOf("旅游", "旅行", "出行", "景点", "酒店", "度假", "攻略", "机票", "签证"),
            "娱乐" to listOf("娱乐", "电影", "音乐", "游戏", "明星", "综艺", "电视", "演唱会", "爱好")
        )

        // 情感相关类别集合 - 用于情感相关查询优化匹配
        private val EMOTIONAL_CATEGORIES = setOf(
            "情感", "脱单攻略", "伴侣婚姻", "家庭亲子", "家庭", "婚恋", "社交", "破防时刻", "emo治愈"
        )

        // 停用词表 - 提前定义以加速处理
        private val STOP_WORDS = setOf(
            "的", "了", "和", "是", "在", "我", "有", "你", "他", "她", "它", "这", "那", "都", "就",
            "也", "要", "会", "到", "可以", "可能", "应该", "没有", "这个", "那个", "什么", "怎么",
            "如何", "为什么", "哪里", "谁", "何时", "怎样", "多少", "几", "啊", "呢", "吧", "呀", "哦",
            "哈", "呵", "嗯", "哼", "嘿", "喂", "嗨", "哟", "唉", "咦", "啧", "感觉", "觉得", "认为",
            "想", "不想", "有点", "非常", "太", "一点", "稍微", "喜欢", "害怕", "时间", "世界", "生活",
            "比如", "例如", "其实", "确实", "只是", "一般", "正常", "必须", "建议", "不要", "一直",
            "完全", "事情", "东西", "情况", "经验", "方面", "方法", "the", "a", "an", "and", "or",
            "but", "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does",
            "did", "will", "would", "can", "could", "should", "i", "you", "he", "she", "it", "we",
            "they", "my", "your", "his", "her", "its", "our", "their", "this", "that", "these", "those"
        )

        // 情感词汇表 - 用于增强情感分析准确性
        private val EMOTIONAL_KEYWORDS = setOf(
            "喜欢", "爱", "开心", "快乐", "幸福", "享受", "满意", "感谢", "感动", "感激", "欣赏", "赞美",
            "高兴", "愉快", "欢喜", "惊喜", "兴奋", "激动", "成就", "满足", "尊重", "信任", "佩服",
            "敬佩", "欣慰", "温馨", "温暖", "甜蜜", "绝绝子", "yyds", "磕死我了", "爱了爱了", "太棒了",
            "赞", "有被感动到", "破防了", "真好", "好起来了", "太喜欢了", "永远爱", "真香", "心动",
            "讨厌", "恨", "伤心", "难过", "痛苦", "焦虑", "担心", "害怕", "恐惧", "生气", "愤怒",
            "失望", "沮丧", "悲伤", "怨恨", "委屈", "嫉妒", "孤独", "寂寞", "无奈", "疲惫", "厌倦",
            "烦躁", "压力", "紧张", "不安", "忧郁", "后悔", "emo了", "崩溃", "太难了", "心态崩了",
            "心碎", "太难受了", "麻了", "好烦啊", "扎心了", "泪目", "呜呜", "想哭", "太痛了", "心态爆炸",
            "焯", "绝望", "离谱", "晕倒", "不行了", "无语", "烦死了", "寄了", "躺平了"
        )

        // 网络流行词汇表 - 优化识别网络表达方式
        private val NETWORK_WORDS = setOf(
            "yyds", "awsl", "xswl", "xdm", "plmm", "tcl", "nsdd", "gkd", "emo", "绝绝子",
            "真下头", "笑死", "破防", "社死", "锁死", "蚌埠住了", "爆金币", "绷不住", "超绝", "冲",
            "冲鸭", "打call", "达咩", "抽象", "特种兵", "夺冠", "二创", "乏了", "芙蓉王", "干饭人",
            "高低", "高质量男性", "呱呱", "逛", "海鲜市场", "好家伙", "好似", "ikun", "上岸", "大馋丫头",
            "牛魔王", "烤猪蹄", "你说的对", "搞笑女", "社牛", "是我不够努力", "我不服", "全给你们懂完了",
            "拉垮", "绝了", "下饭", "磕到了", "小丑竟是我", "上头", "安排", "急了", "米线", "纯良",
            "寄了", "摆烂", "躺平", "内卷", "后浪", "35岁", "破防", "内娱", "流量", "蹲一个", "恰饭",
            "凡尔赛", "冲浪", "奥利给", "内卷", "绝绝子", "懂王", "反转", "爹味", "家人们", "整蛊"
        )
    }

    /**
     * 查找相关记忆
     * 使用两阶段检索策略：快速预筛选 + 精细相似度计算
     *
     * @param userInput 用户输入文本
     * @param memories 记忆列表
     * @param limit 最大返回结果数量
     * @return 相关性排序后的记忆列表
     */
    suspend fun findRelevantMemories(userInput: String, memories: List<MemoryEntity>, limit: Int = 3): List<MemoryEntity> = withContext(Dispatchers.Default) {
        if (memories.isEmpty() || userInput.length < 3) {
            return@withContext emptyList()
        }

        // 记录开始时间，用于性能监控
        val startTime = System.currentTimeMillis()

        // 预处理用户输入 - 提取关键信息
        val userInputInfo = extractTextInfo(userInput)

        // 查询意图分析
        val possibleCategories = predictCategories(userInput)
        val isEmotionalQuery = isEmotionalQuery(userInput)
        val isTimeQuery = containsTimeReference(userInput)
        val isRecentQuery = containsRecentReference(userInput)

        Log.d(TAG, "查询意图分析: 情感=${isEmotionalQuery}, 时间=${isTimeQuery}, 最近=${isRecentQuery}, 分类=${possibleCategories}")

        // 快速预筛选候选记忆
        val candidateMemories = preFilterMemories(memories, possibleCategories, isEmotionalQuery, isTimeQuery, isRecentQuery)

        Log.d(TAG, "智能预筛选: 从${memories.size}条记忆中筛选出${candidateMemories.size}条候选记忆")

        // 如果预筛选后没有结果，直接返回空列表
        if (candidateMemories.isEmpty()) {
            return@withContext emptyList()
        }

        // 多维度相似度计算
        val scoredMemories = candidateMemories.map { memory ->
            // 提取记忆内容的文本信息
            val memoryTextInfo = extractTextInfo(memory.content)

            // 计算关键词快速匹配得分
            val keywordScore = calculateKeywordMatch(userInputInfo.keywords, memoryTextInfo.keywords)

            // 计算情感语义匹配得分
            val emotionalScore = if (isEmotionalQuery) {
                calculateEmotionalMatch(userInputInfo, memoryTextInfo)
            } else 0f

            // 计算语义相似度
            val similarityScore = calculateOptimizedSimilarity(userInputInfo, memoryTextInfo)

            // 计算时间衰减因子
            val timeDecayFactor = when {
                isRecentQuery -> calculateRecentTimeBoost(memory.timestamp)
                isTimeQuery -> 1.0f  // 时间查询不衰减
                else -> calculateTimeDecayFactor(memory.timestamp)
            }

            // 计算分类匹配得分
            val categoryScore = if (memory.category in possibleCategories) 0.15f else 0f

            // 计算重要性权重
            val importanceFactor = 1.0f + ((memory.importance - 5) / 10.0f)

            // 计算关键词匹配加成
            val keywordBoost = if (hasExactKeywordMatch(userInputInfo.words, memory.keywords)) 0.2f else 0f

            // 计算情感类别加成
            val emotionalCategoryBoost = if (isEmotionalQuery && memory.category in EMOTIONAL_CATEGORIES) 0.1f else 0f

            // 综合得分计算
            val finalScore = (
                    similarityScore * 0.5f +  // 基础语义相似度 (50%)
                            keywordScore * 0.2f +     // 关键词匹配 (20%)
                            emotionalScore * 0.10f +  // 情感匹配 (10%)
                            categoryScore +           // 分类匹配 (15%)
                            keywordBoost +            // 精确关键词匹配加成
                            emotionalCategoryBoost    // 情感类别加成
                    ) * importanceFactor * timeDecayFactor

            Pair(memory, finalScore)
        }

        // 过滤和排序结果
        val filteredResult = scoredMemories
            .filter { it.second >= RELEVANCE_THRESHOLD }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

        // 记录执行时间，用于性能监控
        val endTime = System.currentTimeMillis()
        Log.d(TAG, "记忆检索完成: 耗时=${endTime-startTime}ms, 匹配${filteredResult.size}条记忆")

        return@withContext filteredResult
    }

    /**
     * 智能预筛选记忆
     */
    private fun preFilterMemories(
        memories: List<MemoryEntity>,
        categories: Set<String>,
        isEmotional: Boolean,
        isTimeQuery: Boolean,
        isRecentQuery: Boolean
    ): List<MemoryEntity> {
        // 如果记忆数量较少，直接返回全部
        if (memories.size <= 10) {
            return memories
        }

        // 根据不同查询类型使用不同筛选策略
        val candidateMemories = when {
            // 时间相关查询 - 优先最近记忆
            isRecentQuery -> {
                memories.sortedByDescending { it.timestamp }.take(memories.size / 2)
            }
            // 情感相关查询 - 优先情感类别和高重要性
            isEmotional -> {
                // 先获取情感相关类别的记忆
                val emotionalMemories = memories.filter { it.category in EMOTIONAL_CATEGORIES }

                if (emotionalMemories.size >= 5) {
                    // 如果情感相关记忆足够，按重要性排序
                    emotionalMemories.sortedByDescending { it.importance }
                } else {
                    // 否则补充其他记忆，但将情感记忆排在前面
                    val otherMemories = memories.filter { it.category !in EMOTIONAL_CATEGORIES }
                        .sortedByDescending { it.importance }
                        .take(10)

                    (emotionalMemories + otherMemories).distinctBy { it.id }
                }
            }
            // 有明确类别的查询 - 优先选择匹配类别
            categories.isNotEmpty() -> {
                // 先获取类别匹配的记忆
                val categoryMatches = memories.filter { it.category in categories }

                if (categoryMatches.size >= 5) {
                    // 如果类别匹配记忆足够，按重要性排序
                    categoryMatches.sortedByDescending { it.importance }
                } else {
                    // 否则补充其他记忆，但将类别匹配记忆排在前面
                    val otherMemories = memories.filter { it.category !in categories }
                        .sortedByDescending { it.importance }
                        .take(10)

                    (categoryMatches + otherMemories).distinctBy { it.id }
                }
            }
            // 其他一般查询 - 综合考虑重要性和时间
            else -> {
                // 综合重要性和时间因素 - 创建复合得分
                memories.map { memory ->
                    val timeFactor = 1.0f / (1 + (System.currentTimeMillis() - memory.timestamp.time) / (1000 * 60 * 60 * 24 * 30f))
                    val importanceFactor = memory.importance / 10f
                    val hybridScore = importanceFactor * 0.7f + timeFactor * 0.3f

                    Pair(memory, hybridScore)
                }.sortedByDescending { it.second }
                    .take(memories.size / 3) // 取前三分之一作为候选
                    .map { it.first }
            }
        }

        // 确保返回足够的候选数量
        return if (candidateMemories.size < 5 && memories.size > 5) {
            // 如果候选太少，补充一些高重要性的记忆
            val additionalMemories = memories
                .filter { it !in candidateMemories }
                .sortedByDescending { it.importance }
                .take(5 - candidateMemories.size)

            (candidateMemories + additionalMemories).distinctBy { it.id }
        } else {
            candidateMemories
        }
    }

    /**
     * 检测是否包含最近时间引用
     */
    private fun containsRecentReference(text: String): Boolean {
        val recentPatterns = arrayOf(
            "(最近|近期|这几天|这段时间|上次|刚刚|之前|前几天|不久前)",
            "(上周|上个月|上次聊过|以前说过|前面提到)"
        )

        for (pattern in recentPatterns) {
            if (Regex(pattern).find(text) != null) {
                return true
            }
        }
        return false
    }

    /**
     * 检测是否包含时间引用
     */
    private fun containsTimeReference(text: String): Boolean {
        val timePatterns = arrayOf(
            "(昨天|今天|明天|后天|周末|下周|上周|上个月|下个月|年初|年底|春节|新年)",
            "(几点|时间|日期|几月|星期|哪天|早上|中午|下午|晚上|凌晨)",
            "(时间表|行程|计划|安排|日程|提醒|闹钟|定时|提醒)",
            "(多久|多长时间|几个月|几年|多少天)"
        )

        for (pattern in timePatterns) {
            if (Regex(pattern).find(text) != null) {
                return true
            }
        }
        return false
    }

    /**
     * 计算针对最近查询的时间提升因子
     */
    private fun calculateRecentTimeBoost(timestamp: Date): Float {
        val now = System.currentTimeMillis()
        val ageInDays = (now - timestamp.time) / (1000 * 60 * 60 * 24f)

        // 对于"最近"查询，最近7天记忆有显著提升，30天内有轻微提升
        return when {
            ageInDays <= 7 -> 1.5f
            ageInDays <= 30 -> 1.2f
            else -> 1.0f
        }
    }

    /**
     * 检查是否有精确的关键词匹配
     */
    private fun hasExactKeywordMatch(userWords: List<String>, memoryKeywords: List<String>): Boolean {
        for (word in userWords) {
            if (word.length > 1 && memoryKeywords.any { it.equals(word, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    /**
     * 计算情感匹配得分
     */
    private fun calculateEmotionalMatch(userInfo: TextInfo, memoryInfo: TextInfo): Float {
        // 如果任一方没有情感词汇，返回0
        if (userInfo.emotionalWords.isEmpty() || memoryInfo.emotionalWords.isEmpty()) {
            return 0f
        }

        // 计算情感词汇重合度
        var matches = 0
        for (userWord in userInfo.emotionalWords) {
            for (memoryWord in memoryInfo.emotionalWords) {
                if (userWord == memoryWord ||
                    userWord.contains(memoryWord) ||
                    memoryWord.contains(userWord)) {
                    matches++
                    break
                }
            }
        }

        // 计算得分，带加权
        val baseScore = matches.toFloat() / maxOf(1, userInfo.emotionalWords.size)
        return baseScore * (1f + 0.1f * memoryInfo.emotionalWords.size) // 情感词汇越丰富分数越高
    }

    /**
     * 检查是否为情感/伴侣关系相关查询
     */
    private fun isEmotionalQuery(text: String): Boolean {
        val lowerText = text.lowercase()

        // 快速检查 - 使用预定义的情感词汇表
        for (keyword in EMOTIONAL_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                return true
            }
        }

        // 检查特定的情感/伴侣关系表达模式
        val emotionalPatterns = arrayOf(
            // 基础情感模式
            "我.*?他", "他.*?我", "我.*?她", "她.*?我",
            "我.*?爱", "爱.*?我", "我.*?喜欢", "喜欢.*?我",
            "我们.*?关系", "他不.*?我", "她不.*?我", "不爱我",

            // 情感困惑模式
            "怎么.*?挽回", "怎么.*?追", "怎么.*?表白",
            "我该.*?怎么办", "我应该.*?怎么办",
            "我想.*?(结婚|离婚)", "我.*?(结婚|离婚).*?想",

            // 年轻人情感表达模式
            "暗恋.*?表白", "表白.*?成功", "挽回.*?可能", "前任.*?复合",
            "我.*?被.*?拒绝", "我.*?有.*?好感", "喜欢.*?不敢", "单相思",
            "怎么判断.*?喜欢我", "如何判断.*?爱我", "怎么知道.*?心动",
            "暧昧期", "追.*?套路", "不联系", "聊天.*?话题"
        )

        for (pattern in emotionalPatterns) {
            if (Regex(pattern).find(lowerText) != null) {
                return true
            }
        }

        return false
    }

    /**
     * 多维度优化版相似度计算
     */
    private fun calculateOptimizedSimilarity(text1: TextInfo, text2: TextInfo): Float {
        // 词袋模型余弦相似度 (基础相似度) - 30%
        val bagOfWordsScore = calculateCosineSimilarity(text1.words, text2.words)

        // 关键词匹配得分 - 30%
        val keywordScore = calculateKeywordSimilarity(text1.keywords, text2.keywords)

        // 实体匹配得分 - 15%
        val entityScore = calculateEntityMatch(text1.entities, text2.entities)

        // 专业术语/数字匹配得分 - 10%
        val specialTermScore = calculateSpecialTermMatch(text1, text2)

        // 网络流行词匹配得分 - 5%
        val netWordsScore = calculateNetWordsMatch(text1.netWords, text2.netWords)

        // 综合评分 - 优化权重
        return bagOfWordsScore * 0.30f +
                keywordScore * 0.30f +
                entityScore * 0.15f +
                specialTermScore * 0.10f +
                netWordsScore * 0.05f
    }

    /**
     * 专业术语和数字匹配得分
     */
    private fun calculateSpecialTermMatch(text1: TextInfo, text2: TextInfo): Float {
        // 结合数字匹配和专业术语匹配
        val numberScore = calculateNumberMatch(text1.numbers, text2.numbers)

        // 专业术语匹配
        val specialTerms1 = text1.words.filter { it.length > 3 && it[0].isUpperCase() }
        val specialTerms2 = text2.words.filter { it.length > 3 && it[0].isUpperCase() }

        val termScore = if (specialTerms1.isNotEmpty() && specialTerms2.isNotEmpty()) {
            var matches = 0
            for (term1 in specialTerms1) {
                for (term2 in specialTerms2) {
                    if (term1 == term2 || term1.contains(term2) || term2.contains(term1)) {
                        matches++
                        break
                    }
                }
            }
            matches.toFloat() / maxOf(specialTerms1.size, specialTerms2.size)
        } else {
            0f
        }

        // 返回综合得分
        return (numberScore * 0.5f + termScore * 0.5f)
    }

    /**
     * 根据用户输入预测可能的记忆分类
     */
    private fun predictCategories(text: String): Set<String> {
        val result = mutableSetOf<String>()
        val lowerText = text.lowercase()

        // 尝试使用HanLP提取关键词
        try {
            val keywords = HanLP.extractKeyword(text, 8) // 提取更多关键词提高匹配概率

            // 对每个关键词检查所有分类
            for (keyword in keywords) {
                for ((category, categoryWords) in CATEGORY_MAP) {
                    if (categoryWords.any {
                            it == keyword ||
                                    it.contains(keyword) ||
                                    keyword.contains(it)
                        }) {
                        result.add(category)
                    }
                }
            }

            // 如果通过关键词没有找到分类，使用备用方法
            if (result.isEmpty()) {
                return predictCategoriesByPatterns(text)
            }

            return result

        } catch (e: Exception) {
            // 如果HanLP处理出错，使用备用方法
            return predictCategoriesByPatterns(text)
        }
    }

    /**
     * 通过模式匹配预测分类 - 备用方法
     */
    private fun predictCategoriesByPatterns(text: String): Set<String> {
        val result = mutableSetOf<String>()
        val lowerText = text.lowercase()

        // 模式定义方式
        val categoryPatterns = mapOf(
            "技术问题" to "(代码|编程|函数|算法|程序|bug|开发|框架|编译|库|api|接口|调试|部署|报错|git|服务器|云服务|github|npm|docker)",
            "AI与科技" to "(ai|人工智能|大模型|chatgpt|gpt|claude|midjourney|stable diffusion|prompt|生成式|训练|机器学习|神经网络|大语言模型|llm|深度学习)",
            "职场发展" to "(工作|职位|简历|上班|同事|老板|项目|工资|薪资|加班|裁员|晋升|职场|跳槽|面试|offer|内卷|35岁|绩效|kpi|pua|icu)",
            "副业创收" to "(副业|兼职|赚钱|创业|收入|项目|被动收入|躺赚|小生意|创收|商机|销售|客源|电商|直播带货|淘宝|拼多多|抖音|快手|知乎|小红书)",
            "日常生活" to "(日常|平时|每天|习惯|早起|晚睡|通勤|上班|下班|公交|地铁|吃饭|睡觉|逛街|购物|做饭|洗衣|收拾|打扫|卫生|家务|周末|休息|娱乐|爱好)",
            "健康医疗" to "(生病|医生|医院|症状|药物|健康|疼痛|治疗|睡眠|身体不适|体检|保健|疾病|肠胃|失眠|焦虑|抑郁|精神|心理|咨询|医保)",
            "美食探店" to "(美食|餐厅|菜品|食物|烹饪|食谱|厨艺|探店|外卖|火锅|烧烤|甜品|饮料|网红店|必吃|打卡|试吃|测评|值得|安利|推荐|好吃|好喝|回购)",
            "旅行出行" to "(旅游|旅行|景点|出游|旅程|度假|目的地|行程|攻略|机票|酒店|民宿|签证|景区|打卡|拍照|vlog|景色|风景|地铁|公交|高铁|动车|飞机)",
            "居家生活" to "(家居|家装|装修|家具|收纳|整理|清洁|打扫|懒人|神器|生活|日用|家电|电器|锅碗瓢盆|床上用品|沙发|茶几|电视|洗衣机|冰箱|空调|扫地机器人)",
            "个人信息" to "(个人信息|名字|年龄|性别|出生|住址|地址|联系方式|电话|邮箱|身份证|护照|学历|学位|经历|工作|职业|收入|存款|我是|我的|我|本人)",
            "情感" to "(爱情|恋爱|喜欢|暗恋|失恋|约会|表白|恋人|感情|脱单|单身|爱上|感动|分手|复合|挽回|吸引力|自由恋爱|姐弟恋|双向奔赴|情侣|男女)",
            "学习教育" to "(学习|教育|知识|课程|学校|大学|课堂|考试|作业|论文|研究|专业|老师|同学|笔记|复习|教材|课本|题目|题库|习题|考点|重点)",
            "金融理财" to "(理财|投资|股票|基金|保险|债券|房产|存款|贷款|信用卡|消费|支出|预算|支付宝|微信支付|股市|牛市|熊市|回调|收益|收入|盈亏|股市|大盘)"
        )

        // 检查每种分类模式
        for ((category, pattern) in categoryPatterns) {
            if (Regex(pattern).find(lowerText) != null) {
                result.add(category)
            }
        }

        // 如果没有匹配任何分类，返回"其他"
        if (result.isEmpty()) {
            result.add("其他")
        }

        return result
    }

    /**
     * 计算关键词匹配得分
     */
    private fun calculateKeywordMatch(words1: List<String>, words2: List<String>): Float {
        if (words1.isEmpty() || words2.isEmpty()) return 0f

        // 统计匹配关键词数量，区分精确匹配和部分匹配
        var exactMatches = 0
        var partialMatches = 0

        // 创建处理过的词集合，避免重复计算
        val processedWords = mutableSetOf<String>()

        for (word1 in words1) {
            if (word1 in processedWords) continue

            for (word2 in words2) {
                if (word1.equals(word2, ignoreCase = true)) {
                    // 精确匹配，得分更高
                    exactMatches++
                    processedWords.add(word1)
                    break
                } else if (word1.contains(word2, ignoreCase = true) ||
                    word2.contains(word1, ignoreCase = true)) {
                    // 部分匹配
                    partialMatches++
                    processedWords.add(word1)
                    break
                }
            }
        }

        // 归一化为0-1的分数，精确匹配的权重更高
        val totalMatches = exactMatches * 1.5f + partialMatches
        val maxPossibleMatches = minOf(words1.size, words2.size * 2)

        return if (maxPossibleMatches > 0) {
            (totalMatches / maxPossibleMatches).coerceAtMost(1.0f)
        } else {
            0f
        }
    }

    /**
     * 提取文本的语义信息
     */
    private fun extractTextInfo(text: String): TextInfo {
        try {
            // 规范化文本
            val normalizedText = text.lowercase().trim()

            // 使用HanLP分词
            val terms = HanLP.segment(normalizedText)
            val words = terms.map { it.word }

            // 提取命名实体
            val entities = terms
                .filter {
                    it.nature.startsWith("nr") || // 人名
                            it.nature.startsWith("ns") || // 地名
                            it.nature.startsWith("nt") || // 机构名
                            it.nature.startsWith("nz")    // 其他专名
                }
                .map { it.word }

            // 提取数字
            val numbers = terms
                .filter { it.nature.startsWith("m") } // 数词
                .map { it.word }

            // 提取关键词
            val keywords = try {
                HanLP.extractKeyword(normalizedText, 8)
            } catch (e: Exception) {
                // 备用关键词提取
                extractKeywordsFallback(normalizedText)
            }

            // 提取情感词汇
            val emotionalWords = extractEmotionalWords(normalizedText)

            // 提取网络流行词
            val netWords = identifyNetworkWords(normalizedText)

            return TextInfo(
                normalizedText = normalizedText,
                words = words,
                segmentedTerms = terms,
                entities = entities,
                numbers = numbers,
                keywords = keywords,
                emotionalWords = emotionalWords,
                netWords = netWords
            )
        } catch (e: Exception) {
            // 如果HanLP处理失败，使用备用方法
            return extractTextInfoFallback(text)
        }
    }

    /**
     * 备用文本信息提取方法（不依赖HanLP）
     */
    private fun extractTextInfoFallback(text: String): TextInfo {
        // 分词和清洗
        val normalizedText = text.lowercase().trim()

        // 简单分词
        val words = normalizedText.split(" ", "，", "。", "？", "！", "、")
            .filter { it.isNotEmpty() }

        // 提取数字
        val numbers = extractNumbers(normalizedText)

        // 提取实体
        val entities = extractEntitiesFallback(normalizedText)

        // 提取关键词
        val keywords = extractKeywordsFallback(normalizedText)

        // 提取情感词汇
        val emotionalWords = extractEmotionalWords(normalizedText)

        // 提取网络流行词
        val netWords = identifyNetworkWords(normalizedText)

        return TextInfo(
            normalizedText = normalizedText,
            words = words,
            segmentedTerms = emptyList(), // 无分词结果
            entities = entities,
            numbers = numbers,
            keywords = keywords,
            emotionalWords = emotionalWords,
            netWords = netWords
        )
    }

    /**
     * 备用关键词提取方法
     */
    private fun extractKeywordsFallback(text: String): List<String> {
        val wordCounts = mutableMapOf<String, Int>()

        val words = text.split(" ", "，", "。", "？", "！", "、").filter { it.isNotEmpty() && it.length > 1 && it !in STOP_WORDS }

        words.forEach { word ->
            wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
        }

        return wordCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }

    /**
     * 提取网络流行词
     */
    private fun identifyNetworkWords(text: String): List<String> {
        val lowerText = text.lowercase()
        return NETWORK_WORDS.filter { lowerText.contains(it.lowercase()) }
    }

    /**
     * 提取情感词汇
     */
    private fun extractEmotionalWords(text: String): List<String> {
        val lowerText = text.lowercase()
        return EMOTIONAL_KEYWORDS.filter { lowerText.contains(it) }
    }

    /**
     * 计算关键词相似度
     */
    private fun calculateKeywordSimilarity(keywords1: List<String>, keywords2: List<String>): Float {
        if (keywords1.isEmpty() || keywords2.isEmpty()) return 0f

        // 计算关键词匹配数量，区分精确匹配和部分匹配
        var exactMatches = 0
        var partialMatches = 0
        val processedKeywords = mutableSetOf<String>()

        for (k1 in keywords1) {
            if (k1 in processedKeywords) continue

            for (k2 in keywords2) {
                if (k1 == k2) {
                    // 精确匹配权重高
                    exactMatches++
                    processedKeywords.add(k1)
                    break
                } else if (k1.contains(k2) || k2.contains(k1)) {
                    // 部分匹配
                    partialMatches++
                    processedKeywords.add(k1)
                    break
                }
            }
        }

        // 计算相似度得分
        val totalMatches = exactMatches * 1.5f + partialMatches
        val maxPossibleMatches = minOf(keywords1.size, keywords2.size)
        val matchRatio = totalMatches / maxPossibleMatches

        // 非线性映射，使高匹配度更突出
        return (matchRatio * 0.6f) + (matchRatio * matchRatio * 0.4f)
    }

    /**
     * 计算网络流行词匹配得分
     */
    private fun calculateNetWordsMatch(words1: List<String>, words2: List<String>): Float {
        if (words1.isEmpty() || words2.isEmpty()) return 0f

        // 计算匹配的网络词数量
        var matches = 0
        val processedWords = mutableSetOf<String>() // 避免重复计算

        for (w1 in words1) {
            if (w1 in processedWords) continue

            for (w2 in words2) {
                if (w1 == w2 || w1.contains(w2) || w2.contains(w1)) {
                    matches++
                    processedWords.add(w1)
                    break
                }
            }
        }

        // 网络流行词匹配权重更高，给予额外加成
        val matchRatio = matches.toFloat() / maxOf(1, minOf(words1.size, words2.size))

        // 带加成系数的得分公式
        return matchRatio * (1.0f + (0.15f * matches)) // 匹配越多，加成越高
    }

    /**
     * 计算实体匹配得分
     */
    private fun calculateEntityMatch(entities1: List<String>, entities2: List<String>): Float {
        if (entities1.isEmpty() || entities2.isEmpty()) return 0f

        // 计算实体匹配数量
        var exactMatches = 0
        var partialMatches = 0

        for (e1 in entities1) {
            var found = false
            for (e2 in entities2) {
                if (e1 == e2) {
                    // 完全相同，得分高
                    exactMatches++
                    found = true
                    break
                } else if (e1.contains(e2) || e2.contains(e1)) {
                    // 部分包含，得分略低
                    if (!found) { // 只有没找到精确匹配时才算部分匹配
                        partialMatches++
                        found = true
                    }
                }
            }
        }

        // 精确匹配有更高权重
        val totalScore = (exactMatches * 1.5f + partialMatches) / maxOf(1, maxOf(entities1.size, entities2.size))
        return totalScore.coerceAtMost(1.0f)
    }

    /**
     * 计算数字匹配得分
     */
    private fun calculateNumberMatch(numbers1: List<String>, numbers2: List<String>): Float {
        if (numbers1.isEmpty() || numbers2.isEmpty()) return 0f

        // 计算数字匹配数量，考虑数值相似性
        var exactMatches = 0
        var similarMatches = 0

        for (n1 in numbers1) {
            var found = false
            for (n2 in numbers2) {
                if (n1 == n2) {
                    // 精确匹配
                    exactMatches++
                    found = true
                    break
                } else {
                    // 检查数值相似度
                    try {
                        val num1 = n1.toDouble()
                        val num2 = n2.toDouble()
                        // 如果数值接近，也算相似匹配
                        if (Math.abs(num1 - num2) / maxOf(num1, num2) < 0.2) {
                            if (!found) {
                                similarMatches++
                                found = true
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略转换错误
                    }
                }
            }
        }

        // 精确匹配有更高权重
        val totalScore = (exactMatches * 1.5f + similarMatches * 0.8f) / maxOf(1, maxOf(numbers1.size, numbers2.size))
        return totalScore.coerceAtMost(1.0f)
    }

    /**
     * 计算词袋模型的余弦相似度
     */
    private fun calculateCosineSimilarity(words1: List<String>, words2: List<String>): Float {
        // 如果任一文本为空，返回0相似度
        if (words1.isEmpty() || words2.isEmpty()) return 0f

        // 创建词频映射
        val freqMap1 = words1.groupingBy { it }.eachCount()
        val freqMap2 = words2.groupingBy { it }.eachCount()

        // 计算所有唯一词
        val allWords = freqMap1.keys.union(freqMap2.keys)

        // 计算点积
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (word in allWords) {
            // 应用TF-IDF思想：通用词权重降低，特殊词权重提高
            val weight = if (isCommonWord(word)) 0.5 else 1.8

            val freq1 = freqMap1[word]?.toDouble() ?: 0.0
            val freq2 = freqMap2[word]?.toDouble() ?: 0.0

            // 应用权重
            val weighted1 = freq1 * weight
            val weighted2 = freq2 * weight

            dotProduct += weighted1 * weighted2
            norm1 += weighted1 * weighted1
            norm2 += weighted2 * weighted2
        }

        // 避免除以零
        if (norm1 <= 0.0 || norm2 <= 0.0) return 0f

        // 计算余弦相似度
        return (dotProduct / (sqrt(norm1) * sqrt(norm2))).toFloat()
    }

    /**
     * 备用方法：提取实体
     */
    private fun extractEntitiesFallback(text: String): List<String> {
        val entities = mutableListOf<String>()

        // 提取中文专有名词
        val chineseEntityPattern = Regex("[\\u4e00-\\u9fa5]{2,}(?:公司|大学|学院|研究所|集团|平台|系统|框架|协议|标准|品牌|型号|软件|应用|游戏|电影|剧集|明星|艺人|网红|博主|up主|直播|店|商场|超市)")
        chineseEntityPattern.findAll(text).forEach {
            entities.add(it.value)
        }

        // 提取英文专有名词（大写开头的连续单词）
        val englishEntityPattern = Regex("\\b[A-Z][a-zA-Z]*(?:\\s+[A-Z][a-zA-Z]*)*\\b")
        englishEntityPattern.findAll(text).forEach {
            entities.add(it.value)
        }

        return entities
    }

    /**
     * 备用方法：提取数字
     */
    private fun extractNumbers(text: String): List<String> {
        val numberPattern = Regex("\\d+(?:\\.\\d+)?")
        return numberPattern.findAll(text).map { it.value }.toList()
    }

    /**
     * 检查是否为常见词（低信息量词汇）- 使用预定义集合
     */
    private fun isCommonWord(word: String): Boolean {
        return STOP_WORDS.contains(word.lowercase())
    }

    /**
     * 计算时间衰减因子 - 智能时间权重
     */
    private fun calculateTimeDecayFactor(timestamp: Date): Float {
        val now = Date()
        val ageInDays = (now.time - timestamp.time) / (1000 * 60 * 60 * 24).toFloat()

        // 多级指数衰减：最近7天记忆几乎不衰减，30天内轻微衰减，365天前降至一半
        return when {
            ageInDays <= 7 -> 1.0f
            ageInDays <= 30 -> 0.9f.pow(ageInDays / 30)
            else -> 0.5f + 0.3f * (365f - ageInDays.coerceAtMost(365f)) / 335f
        }
    }

    /**
     * 文本信息数据类 - 包含丰富的语义分析结果
     */
    data class TextInfo(
        val normalizedText: String,                  // 标准化处理后的文本
        val words: List<String>,                     // 分词结果
        val segmentedTerms: List<Term> = emptyList(),// HanLP分词结果带词性
        val entities: List<String>,                  // 命名实体 (人名、地名、机构名等)
        val numbers: List<String>,                   // 数字和数值表达
        val keywords: List<String>,                  // 关键词提取结果
        val emotionalWords: List<String> = listOf(), // 情感词汇
        val netWords: List<String> = listOf()        // 网络流行词
    )
}

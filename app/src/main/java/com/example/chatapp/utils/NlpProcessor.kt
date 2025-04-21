package com.example.chatapp.utils

import android.util.Log
import com.hankcs.hanlp.HanLP
import com.hankcs.hanlp.seg.common.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.math.pow
import java.util.concurrent.ConcurrentHashMap

/**
 * NLP处理工具类
 * 提供完整的语义分析、情感分析、实体识别和文本相似度计算能力
 */
class NlpProcessor {

    companion object {
        private const val TAG = "NlpProcessor"

        // 相关性阈值
        const val RELEVANCE_THRESHOLD = 0.22f

        // 文本分析缓存 - 提高频繁分析同一文本的性能
        private val textInfoCache = ConcurrentHashMap<String, TextInfo>(100)

        // 停用词表 - 高频但低信息量的词汇
        private val STOP_WORDS = setOf(
            "的", "了", "和", "是", "在", "我", "有", "你", "他", "她", "它", "这", "那", "都", "就",
            "也", "要", "会", "到", "可以", "可能", "应该", "没有", "这个", "那个", "什么", "怎么",
            "如何", "为什么", "哪里", "谁", "何时", "怎样", "多少", "几", "啊", "呢", "吧", "呀", "哦",
            "哈", "呵", "嗯", "哼", "嘿", "喂", "嗨", "哟", "唉", "咦", "啧",
            "感觉", "觉得", "认为", "想", "不想", "有点", "非常", "太", "一点", "稍微", "喜欢",
            "害怕", "时间", "世界", "生活", "比如", "例如", "其实", "确实", "只是", "一般", "正常",
            "必须", "建议", "不要", "一直", "完全", "事情", "东西", "情况", "经验", "方面", "方法",
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "be", "been",
            "have", "has", "had", "do", "does", "did", "will", "would", "can", "could", "should",
            "i", "you", "he", "she", "it", "we", "they", "my", "your", "his", "her", "its", "our", "their"
        )

        // 情感词汇表 - 分类为积极和消极
        private val POSITIVE_EMOTIONS = setOf(
            "喜欢", "爱", "开心", "快乐", "幸福", "享受", "满意", "感谢", "感动", "感激", "欣赏",
            "赞美", "高兴", "愉快", "欢喜", "惊喜", "兴奋", "激动", "成就", "满足", "尊重", "信任",
            "佩服", "敬佩", "欣慰", "温馨", "温暖", "甜蜜", "绝绝子", "yyds", "磕死我了", "冲鸭",
            "爱了爱了", "太棒了", "赞", "有被感动到", "破防了", "真好", "好起来了", "太喜欢了",
            "永远爱", "真香", "神仙", "心动", "奈斯", "舒服了", "就是我想要的", "嗨翻天", "爽到",
            "感觉人生圆满了", "赞爆了", "水灵灵", "硬控", "松弛感"
        )

        private val NEGATIVE_EMOTIONS = setOf(
            "讨厌", "恨", "伤心", "难过", "痛苦", "焦虑", "担心", "害怕", "恐惧", "生气", "愤怒",
            "失望", "沮丧", "悲伤", "怨恨", "委屈", "嫉妒", "孤独", "寂寞", "无奈", "疲惫", "厌倦",
            "烦躁", "压力", "紧张", "不安", "忧郁", "后悔", "emo了", "破防了", "太难了", "崩溃",
            "心态崩了", "心碎", "太难受了", "麻了", "好烦啊", "扎心了", "泪目", "呜呜", "想哭",
            "太痛了", "心态爆炸", "ptsd了", "焯", "绝望", "离谱", "晕倒", "不行了", "无语",
            "烦死了", "寄了", "躺平了", "偷感", "偷感很重", "班味", "梦回童年"
        )

        // 年轻人情感词汇
        private val YOUTH_EMOTIONS = setOf(
            "emo", "破防", "社死", "锁死", "上头", "绝绝子", "爆哭", "爆笑", "自闭", "上头",
            "爱慕强者", "绷不住", "裂开", "笑嘻了", "麻了", "害怕", "酸了", "润", "寄了", "真香",
            "内卷", "内耗", "摆烂", "躺平", "绝", "自嗨", "破涕为笑", "磕到了", "心动", "搞抽象",
            "抽象", "水灵灵地", "硬控", "红温", "那咋了", "班味", "松弛感", "偷感", "偷感很重",
            "草台班子", "city", "city不city", "包的", "古希腌掌管"
        )

        // 所有情感词汇的集合
        private val ALL_EMOTIONS = POSITIVE_EMOTIONS + NEGATIVE_EMOTIONS + YOUTH_EMOTIONS

        // 网络流行词集合
        private val NETWORK_WORDS = setOf(
            // 经典流行词
            "yyds", "awsl", "xswl", "xdm", "plmm", "tcl", "nsdd", "gkd", "emo", "绝绝子",
            "真下头", "笑死", "破防", "社死", "锁死", "蚌埠住了", "爆金币", "绷不住", "超绝", "冲",
            "冲鸭", "打call", "达咩", "抽象", "特种兵", "夺冠", "二创", "乏了", "芙蓉王", "干饭人",
            "高质量男性", "呱呱", "逛", "海鲜市场", "好家伙", "好似", "好嘞", "红温了", "滑了",
            "几把", "集美", "那咋了", "不嘻嘻", "脚趾抓地", "解包", "班味", "尽快", "就硬", "开香槟",
            "烤山药", "PUA", "夸", "拉了", "拉清单", "老色批", "老铁", "麻了", "麻中麻", "草台班子",
            "古希腊", "牛马", "没绷住", "红温", "city", "魔怔", "内个", "能处", "牛魔", "牛魔王",
            "女拳", "欧拉欧拉", "水灵灵", "我不李姐", "皮套", "啤酒加鸡排", "评价", "急了", "米线",
            "纯良", "寄了", "摆烂", "躺平", "内卷", "后浪", "35岁", "破防", "内娱", "流量", "蹲一个",
            "恰饭", "凡尔赛", "冲浪", "奥利给", "懂王", "反转", "爹味", "家人们", "整蛊", "冲冲冲",
            "沉默的大多数", "狠狠", "瑞斯拜", "救命啊", "ikun", "上岸", "大馋丫头", "烤猪蹄",
            "你说的对", "社牛", "是我不够努力", "我不服", "全给你们懂完了", "拉垮", "绝了", "下饭",
            "磕到了", "小丑竟是我", "上头", "安排", "偷感", "偷感很重", "草台班子", "世界是一个巨大的草台班子",
            "班味", "那咋了", "水灵灵地", "古希腊掌管", "city不city", "包的", "红温", "搞抽象", "硬控",
            "数智化", "智能向善", "未来产业", "水灵灵地", "松弛感", "银发力量", "小孩哥", "小孩姐",
            "北京到底有谁在啊", "小心翼翼", "偷偷摸摸", "玩抽象", "抽象文化", "抽象元年", "显眼包", "草台班子理论"
        )

        // 情感查询关键词
        private val EMOTIONAL_KEYWORDS = setOf(
            "爱情", "恋爱", "喜欢", "暗恋", "失恋", "约会", "表白", "恋人", "感情", "脱单", "单身",
            "爱上", "感动", "分手", "复合", "挽回", "吸引力", "爱", "情感", "心动", "结婚", "离婚",
            "夫妻", "老公", "老婆", "男友", "女友", "出轨", "吵架", "冷战", "婚姻", "两性", "伴侣",
            "丈夫", "妻子", "相亲", "婚恋", "孤独", "心碎", "寂寞", "吃醋", "嫉妒", "红娘", "幸福",
            "家庭", "家人", "父母", "妈妈", "爸爸", "父亲", "母亲", "姐妹", "兄弟", "孩子", "子女",
            "婆媳", "岳母", "公婆", "妯娌", "朋友", "闺蜜", "哥们", "暗恋", "心动", "心动的感觉",
            "绝绝子", "狠狠心动", "恋爱脑", "双向奔赴", "备胎", "渣男", "渣女", "舔狗", "绿茶",
            "小三", "塌房", "塌烂", "海王", "海后", "捞女", "奔现", "网恋", "异地恋", "姐弟恋",
            "年下", "年上", "追星", "脱粉", "官宣", "锁死", "前任", "爱而不得", "社恐", "社牛", "追",
            "撩", "掰弯", "be", "he", "发糖", "双向暗恋", "暧昧", "搭讪", "搭子", "铁", "姐妹",
            "兄弟", "cp", "嗑cp", "磕到了", "真情实感", "ghs", "动心", "心尖尖", "上头", "心动瞬间",
            "错过", "缘分", "注孤生", "脱单", "单身狗", "女同", "男同", "百合", "水仙", "bg", "bl",
            "人机恋", "性缘脑", "智性恋"
        )

        // 情感表达模式 - 用于检测情感查询
        private val EMOTIONAL_PATTERNS = listOf(
            "我.*?他", "他.*?我", "我.*?她", "她.*?我",
            "我.*?爱", "爱.*?我", "我.*?喜欢", "喜欢.*?我",
            "我们.*?关系", "他不.*?我", "她不.*?我", "不爱我",
            "对我.*?冷", "联系", "生气", "道歉", "分手",
            "怎么.*?挽回", "怎么.*?追", "怎么.*?表白",
            "我该.*?怎么办", "我应该.*?怎么办",
            "我想.*?(结婚|离婚)", "我.*?(结婚|离婚).*?想",
            "暗恋.*?表白", "表白.*?成功", "挽回.*?可能", "前任.*?复合",
            "我.*?被.*?拒绝", "我.*?有.*?好感", "喜欢.*?不敢", "单相思",
            "怎么判断.*?喜欢我", "如何判断.*?爱我", "怎么知道.*?心动",
            "他说.*?慢慢来", "她想.*?做朋友", "暧昧期", "考验", "追.*?套路",
            "不联系", "聊天.*?话题"
        )

        // 时间引用模式
        private val TIME_PATTERNS = listOf(
            "(昨天|今天|明天|后天|周末|下周|上周|上个月|下个月|年初|年底|春节|新年)",
            "(几点|时间|日期|几月|星期|哪天|早上|中午|下午|晚上|凌晨)",
            "(时间表|行程|计划|安排|日程|提醒|闹钟|定时|提醒)",
            "(多久|多长时间|几个月|几年|多少天)"
        )

        // 最近引用模式
        private val RECENT_PATTERNS = listOf(
            "(最近|近期|这几天|这段时间|上次|刚刚|之前|前几天|不久前)",
            "(上周|上个月|上次聊过|以前说过|前面提到)"
        )

        // 类别映射表 - 优化类别识别
        private val CATEGORY_MAP = mapOf(
            "技术问题" to setOf("代码", "编程", "算法", "程序", "bug", "开发", "框架", "技术", "软件", "系统", "数据库", "服务器", "云服务"),
            "AI与科技" to setOf("ai", "人工智能", "大模型", "chatgpt", "机器学习", "gpt", "深度学习", "数智化", "智能向善", "llm", "智能", "算法"),
            "职场发展" to setOf("工作", "职场", "简历", "上班", "同事", "老板", "工资", "跳槽", "面试", "offer", "kpi", "晋升", "内卷", "裁员"),
            "副业创收" to setOf("副业", "赚钱", "创业", "收入", "项目", "电商", "被动收入", "创收", "生意", "兼职", "小本经营", "投资"),
            "日常生活" to setOf("日常", "生活", "家务", "通勤", "习惯", "平时", "每天", "上下班", "社区", "购物", "水灵灵", "松弛感"),
            "健康医疗" to setOf("医生", "医院", "健康", "疾病", "症状", "治疗", "药物", "疼痛", "身体", "亚健康", "营养", "保健", "养生"),
            "美食探店" to setOf("美食", "餐厅", "菜品", "食物", "好吃", "探店", "烹饪", "食谱", "菜谱", "外卖", "小吃", "网红店"),
            "旅行出行" to setOf("旅行", "旅游", "景点", "行程", "攻略", "酒店", "机票", "签证", "特种兵旅游", "度假", "观光", "打卡"),
            "居家生活" to setOf("家居", "装修", "家具", "收纳", "整理", "打扫", "清洁", "家电", "窗帘", "沙发", "硬控", "家居风格"),
            "情感" to setOf("恋爱", "喜欢", "爱情", "表白", "暗恋", "心动", "分手", "爱", "情感", "感情", "恋人", "心碎", "告白"),
            "脱单攻略" to setOf("脱单", "撩", "追", "约会", "相亲", "婚恋", "告白", "暧昧", "表白", "技巧", "搭讪", "脱粉", "追星"),
            "伴侣婚姻" to setOf("婚姻", "结婚", "离婚", "老公", "老婆", "伴侣", "夫妻", "彩礼", "婚房", "婚礼", "婚期", "感情"),
            "家庭亲子" to setOf("家庭", "父母", "孩子", "亲子", "教育", "养育", "母亲", "儿女", "小孩哥", "小孩姐", "家人", "亲情"),
            "社交" to setOf("社交", "朋友", "聚会", "交友", "相处", "沟通", "社恐", "社牛", "人际关系", "社交恐惧症", "朋友圈"),
            "虚拟社交" to setOf("社区", "论坛", "群聊", "贴吧", "微信群", "QQ群", "discord", "社群", "网络社区", "虚拟社区", "互联网"),
            "兴趣圈层" to setOf("兴趣", "爱好", "圈子", "小众", "团建", "聚会", "饭圈", "粉丝", "草台班子", "搞抽象", "抽象"),
            "键政社会" to setOf("政治", "时事", "新闻", "社会", "热点", "舆论", "争议", "敏感", "未来产业", "银发力量", "数智化"),
            "游戏电竞" to setOf("游戏", "电竞", "手游", "主机", "steam", "联盟", "王者", "原神", "游戏圈", "电子竞技", "赛事"),
            "二次元" to setOf("二次元", "动漫", "漫画", "宅", "cos", "acgn", "漫展", "声优", "偶像", "番剧", "动画", "同人"),
            "直播短视频" to setOf("直播", "短视频", "抖音", "快手", "b站", "主播", "up主", "网红", "vlog", "博主", "视频创作"),
            "影视剧集" to setOf("电影", "电视剧", "综艺", "追剧", "演员", "导演", "明星", "节目", "电视", "院线", "北京到底有谁在啊"),
            "宠物萌宠" to setOf("猫", "狗", "宠物", "萌宠", "铲屎官", "猫咪", "狗狗", "宠物医院", "养宠", "猫粮", "狗粮", "宠物用品")
        )

        // 是否启用缓存 - 可在初始化时配置
        private var cacheEnabled = true

        // LRU缓存大小限制
        private const val MAX_CACHE_SIZE = 100

        // 缓存命中统计
        private var cacheHits = 0
        private var cacheMisses = 0
    }

    /**
     * 文本信息数据类 - 包含完整的语义分析结果
     */
    data class TextInfo(
        val normalizedText: String,                  // 标准化处理后的文本
        val words: List<String>,                     // 分词结果
        val segmentedTerms: List<Term> = emptyList(),// HanLP分词结果带词性
        val entities: List<String>,                  // 命名实体 (人名、地名、机构名等)
        val numbers: List<String>,                   // 数字和数值表达
        val keywords: List<String>,                  // 关键词提取结果
        val emotionalWords: List<String> = listOf(), // 情感词汇
        val netWords: List<String> = listOf(),       // 网络流行词
        val category: String = "",                   // 预测的主要分类
        val categories: Set<String> = setOf(),       // 所有可能的分类
        val isEmotional: Boolean = false,            // 是否情感相关
        val isTimeReference: Boolean = false,        // 是否包含时间引用
        val isRecentReference: Boolean = false,      // 是否包含最近引用
        val sentiment: Float = 0f                    // 情感得分 (-1到1)
    ) {
        /**
         * 获取文本简明描述 - 用于日志和调试
         */
        fun getSummary(): String {
            return "文本(${normalizedText.take(20)}${if(normalizedText.length > 20) "..." else ""}) " +
                    "类别:$category " +
                    "情感:${if(isEmotional) "是" else "否"} " +
                    "关键词:${keywords.take(3).joinToString(",")}"
        }

        /**
         * 获取文本的唯一指纹(用于缓存)
         */
        fun getFingerprint(): String {
            return "${normalizedText.take(50)}_${words.size}_${keywords.size}"
        }
    }

    /**
     * 配置类 - 可自定义NLP处理器行为
     */
    data class Config(
        val enableCache: Boolean = true,           // 是否启用结果缓存
        val maxCacheSize: Int = MAX_CACHE_SIZE,    // 最大缓存条目数
        val extractKeywordCount: Int = 8,          // 提取关键词数量
        val minTextLengthForAnalysis: Int = 3,     // 最小分析文本长度
        val enablePerformanceLogging: Boolean = false // 是否启用性能日志
    )

    // 初始化配置
    private var config = Config()

    /**
     * 初始化NLP处理器
     * @param config 自定义配置
     */
    fun initialize(config: Config = Config()) {
        this.config = config
        cacheEnabled = config.enableCache
        if (!cacheEnabled) {
            textInfoCache.clear()
        }

        Log.d(TAG, "NLP处理器初始化完成, 缓存:${if (cacheEnabled) "启用" else "禁用"}, 缓存大小:${config.maxCacheSize}")
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        textInfoCache.clear()
        cacheHits = 0
        cacheMisses = 0
        Log.d(TAG, "NLP处理器缓存已清除")
    }

    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): Pair<Int, Int> {
        return Pair(cacheHits, cacheMisses)
    }

    /**
     * 提取并分析文本的语义信息
     * @param text 输入文本
     * @param forceFresh 是否强制重新分析
     * @return 完整的文本分析信息
     */
    suspend fun extractTextInfo(text: String, forceFresh: Boolean = false): TextInfo = withContext(Dispatchers.Default) {
        val startTime = if (config.enablePerformanceLogging) System.currentTimeMillis() else 0L

        // 文本标准化处理
        val normalizedText = text.lowercase().trim()

        // 如果文本过短，返回简单分析结果
        if (normalizedText.length < config.minTextLengthForAnalysis) {
            val result = createSimpleTextInfo(normalizedText)
            if (config.enablePerformanceLogging) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "简单文本分析完成: ${elapsed}ms")
            }
            return@withContext result
        }

        // 检查缓存，除非要求强制刷新
        if (cacheEnabled && !forceFresh && textInfoCache.containsKey(normalizedText)) {
            cacheHits++
            if (config.enablePerformanceLogging) {
                Log.d(TAG, "缓存命中: $normalizedText")
            }
            return@withContext textInfoCache[normalizedText]!!
        }

        cacheMisses++
        try {
            // 执行完整文本分析
            val result = performFullTextAnalysis(normalizedText)

            // 存入缓存，同时确保缓存不会无限增长
            if (cacheEnabled) {
                // 如果缓存超过限制，删除一个随机条目
                if (textInfoCache.size >= config.maxCacheSize) {
                    // 移除随机条目而不是最旧的，这样更简单且性能更好
                    val randomKey = textInfoCache.keys().nextElement()
                    textInfoCache.remove(randomKey)
                }

                textInfoCache[normalizedText] = result
            }

            if (config.enablePerformanceLogging) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "完整文本分析完成: ${elapsed}ms, 缓存大小: ${textInfoCache.size}")
            }

            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "文本分析失败: ${e.message}", e)
            // 出错时使用备用方法
            val result = extractTextInfoFallback(normalizedText)
            if (config.enablePerformanceLogging) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "备用文本分析完成: ${elapsed}ms")
            }
            return@withContext result
        }
    }

    /**
     * 执行完整文本分析
     */
    private fun performFullTextAnalysis(text: String): TextInfo {
        // 分词
        val terms = try {
            HanLP.segment(text)
        } catch (e: Exception) {
            // 如果HanLP分词失败，使用简单分词
            listOf<Term>()
        }
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
            HanLP.extractKeyword(text, config.extractKeywordCount)
        } catch (e: Exception) {
            // 备用关键词提取
            extractKeywordsFallback(text, config.extractKeywordCount)
        }

        // 提取情感词汇
        val emotionalWords = extractEmotionalWords(text)

        // 提取网络流行词
        val netWords = identifyNetworkWords(text)

        // 分析语义特征
        val isEmotional = isEmotionalQuery(text)
        val isTimeReference = containsTimeReference(text)
        val isRecentReference = containsRecentReference(text)

        // 情感分析
        val sentiment = analyzeSentiment(text, emotionalWords)

        // 预测分类
        val categories = predictCategories(text, keywords)
        val primaryCategory = if (categories.isNotEmpty()) categories.first() else "其他"

        // 创建结果对象
        return TextInfo(
            normalizedText = text,
            words = words,
            segmentedTerms = terms,
            entities = entities,
            numbers = numbers,
            keywords = keywords,
            emotionalWords = emotionalWords,
            netWords = netWords,
            category = primaryCategory,
            categories = categories,
            isEmotional = isEmotional,
            isTimeReference = isTimeReference,
            isRecentReference = isRecentReference,
            sentiment = sentiment
        )
    }

    /**
     * 创建简单文本信息
     */
    private fun createSimpleTextInfo(text: String): TextInfo {
        val words = text.split(" ", "，", "。", "？", "！", "、")
            .filter { it.isNotEmpty() }

        return TextInfo(
            normalizedText = text,
            words = words,
            entities = emptyList(),
            numbers = emptyList(),
            keywords = words.filter { it.length > 1 },
            emotionalWords = extractEmotionalWords(text),
            netWords = identifyNetworkWords(text),
            category = "其他"
        )
    }

    /**
     * 备用文本信息提取方法 - 不依赖HanLP
     */
    private fun extractTextInfoFallback(text: String): TextInfo {
        // 简单分词
        val words = text.split(" ", "，", "。", "？", "！", "、")
            .filter { it.isNotEmpty() }

        // 提取数字
        val numbers = extractNumbers(text)

        // 提取实体
        val entities = extractEntitiesFallback(text)

        // 提取关键词
        val keywords = extractKeywordsFallback(text, config.extractKeywordCount)

        // 提取情感词汇
        val emotionalWords = extractEmotionalWords(text)

        // 提取网络流行词
        val netWords = identifyNetworkWords(text)

        // 分析语义特征
        val isEmotional = isEmotionalQuery(text)
        val isTimeReference = containsTimeReference(text)
        val isRecentReference = containsRecentReference(text)

        // 情感分析
        val sentiment = analyzeSentiment(text, emotionalWords)

        // 预测分类
        val categories = predictCategoriesFallback(text)
        val primaryCategory = if (categories.isNotEmpty()) categories.first() else "其他"

        return TextInfo(
            normalizedText = text,
            words = words,
            segmentedTerms = emptyList(), // 无分词结果
            entities = entities,
            numbers = numbers,
            keywords = keywords,
            emotionalWords = emotionalWords,
            netWords = netWords,
            category = primaryCategory,
            categories = categories,
            isEmotional = isEmotional,
            isTimeReference = isTimeReference,
            isRecentReference = isRecentReference,
            sentiment = sentiment
        )
    }

    /**
     * 分词并提取关键词
     * @param text 输入文本
     * @param topK 需要的关键词数量
     * @return 关键词列表
     */
    suspend fun extractKeywords(text: String, topK: Int = 8): List<String> = withContext(Dispatchers.Default) {
        try {
            // 检查文本长度
            if (text.length < config.minTextLengthForAnalysis) {
                return@withContext text.split(" ", "，", "。")
                    .filter { it.length > 1 }
                    .take(topK)
            }

            // 使用HanLP提取关键词
            return@withContext HanLP.extractKeyword(text, topK)
        } catch (e: Exception) {
            // 发生异常时使用备用方法
            return@withContext extractKeywordsFallback(text, topK)
        }
    }

    /**
     * 中文分词
     * @param text 输入文本
     * @return 分词结果
     */
    suspend fun segment(text: String): List<String> = withContext(Dispatchers.Default) {
        try {
            if (text.length < 2) return@withContext listOf(text)

            val terms: List<Term> = HanLP.segment(text)
            return@withContext terms.map { it.word }
        } catch (e: Exception) {
            // 备用分词方法
            return@withContext text.split(" ", "，", "。", "？", "！", "、")
                .filter { it.isNotEmpty() }
        }
    }

    /**
     * 命名实体识别
     * @param text 输入文本
     * @return 实体列表，每个实体为(文本,类型)对
     */
    suspend fun recognizeEntities(text: String): List<Pair<String, String>> = withContext(Dispatchers.Default) {
        try {
            if (text.length < 3) return@withContext emptyList()

            val terms = HanLP.segment(text)
            val entities = mutableListOf<Pair<String, String>>()

            for (term in terms) {
                if (term.nature.startsWith("nr") || // 人名
                    term.nature.startsWith("ns") || // 地名
                    term.nature.startsWith("nt") || // 机构名
                    term.nature.startsWith("nz"))   // 其他专名
                {
                    entities.add(Pair(term.word, term.nature.toString()))
                }
            }
            return@withContext entities
        } catch (e: Exception) {
            // 备用方法
            val entities = extractEntitiesFallback(text)
            return@withContext entities.map { Pair(it, "unknown") }
        }
    }

    /**
     * 情感分析
     * @param text 文本
     * @param emotionalWords 已提取的情感词汇(如果有)
     * @return 情感得分(-1.0到1.0，负值表示消极，正值表示积极)
     */
    private fun analyzeSentiment(text: String, emotionalWords: List<String> = emptyList()): Float {
        // 如果没有提供情感词汇，先提取
        val words = if (emotionalWords.isEmpty()) extractEmotionalWords(text) else emotionalWords

        // 计算情感得分
        var score = 0.0f
        var positiveCount = 0
        var negativeCount = 0

        for (word in words) {
            when {
                POSITIVE_EMOTIONS.contains(word) -> {
                    score += 0.2f
                    positiveCount++
                }
                NEGATIVE_EMOTIONS.contains(word) -> {
                    score -= 0.2f
                    negativeCount++
                }
                YOUTH_EMOTIONS.contains(word) -> {
                    // 年轻人情感词汇需要根据上下文判断
                    if (isPositiveContext(text, word)) {
                        score += 0.1f
                    } else {
                        score -= 0.1f
                    }
                }
            }
        }

        // 基于情感词数量标准化得分
        if (words.isNotEmpty()) {
            // 考虑正负情感词的比例
            val totalEmotionWords = positiveCount + negativeCount
            if (totalEmotionWords > 0) {
                val positiveRatio = positiveCount.toFloat() / totalEmotionWords
                val negativeRatio = negativeCount.toFloat() / totalEmotionWords

                // 合并绝对得分和相对比例
                score = (score * 0.7f) + ((positiveRatio - negativeRatio) * 0.3f)
            }
        } else {
            // 无情感词时，检查一些常见表达方式
            if (text.contains("谢谢") || text.contains("感谢") || text.contains("好的") ||
                text.contains("棒") || text.contains("赞")) {
                score = 0.3f
            } else if (text.contains("不行") || text.contains("垃圾") || text.contains("差") ||
                text.contains("烂") || text.contains("恶心")) {
                score = -0.3f
            }
        }

        return score.coerceIn(-1.0f, 1.0f)
    }

    /**
     * 判断情感词汇是否在积极上下文中
     */
    private fun isPositiveContext(text: String, word: String): Boolean {
        // 提取词前后的一段文本
        val wordIndex = text.indexOf(word)
        if (wordIndex < 0) return false

        val startPos = maxOf(0, wordIndex - 5)
        val endPos = minOf(text.length, wordIndex + word.length + 5)
        val context = text.substring(startPos, endPos)

        // 检测积极上下文标志
        val positiveMarkers = listOf("哈哈", "开心", "笑", "喜欢", "好", "赞", "爱", "太棒", "真香", "不错")
        val negativeMarkers = listOf("难过", "伤心", "痛苦", "难受", "恶心", "讨厌", "烦", "呕", "恨", "差")

        for (marker in positiveMarkers) {
            if (context.contains(marker)) return true
        }

        for (marker in negativeMarkers) {
            if (context.contains(marker)) return false
        }

        // 默认情况
        return false
    }

    /**
     * 情感分析 - 协程版公开API
     * @param text 输入文本
     * @return 情感得分(-1.0到1.0，负值表示消极，正值表示积极)
     */
    suspend fun analyzeSentiment(text: String): Float = withContext(Dispatchers.Default) {
        val emotionalWords = extractEmotionalWords(text)
        return@withContext analyzeSentiment(text, emotionalWords)
    }

    /**
     * 提取文本中的情感词汇
     */
    fun extractEmotionalWords(text: String): List<String> {
        val lowerText = text.lowercase()
        val emotionalWords = mutableListOf<String>()

        // 在文本中搜索情感词汇
        for (emotion in ALL_EMOTIONS) {
            if (lowerText.contains(emotion)) {
                emotionalWords.add(emotion)
            }
        }

        return emotionalWords
    }

    /**
     * 识别文本中的网络流行词
     */
    fun identifyNetworkWords(text: String): List<String> {
        val lowerText = text.lowercase()
        return NETWORK_WORDS.filter { lowerText.contains(it.lowercase()) }
    }

    /**
     * 检查是否包含时间引用
     */
    fun containsTimeReference(text: String): Boolean {
        for (pattern in TIME_PATTERNS) {
            if (Regex(pattern).find(text) != null) {
                return true
            }
        }
        return false
    }

    /**
     * 检查是否包含最近引用
     */
    fun containsRecentReference(text: String): Boolean {
        for (pattern in RECENT_PATTERNS) {
            if (Regex(pattern).find(text) != null) {
                return true
            }
        }
        return false
    }

    /**
     * 检查是否为情感/伴侣关系相关查询
     */
    fun isEmotionalQuery(text: String): Boolean {
        val lowerText = text.lowercase()

        // 快速检查 - 使用预定义的情感关键词
        for (keyword in EMOTIONAL_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                return true
            }
        }

        // 使用正则表达式检查情感模式
        for (pattern in EMOTIONAL_PATTERNS) {
            if (Regex(pattern).find(lowerText) != null) {
                return true
            }
        }

        return false
    }

    /**
     * 预测文本可能属于的分类
     */
    fun predictCategories(text: String, extractedKeywords: List<String> = emptyList()): Set<String> {
        val result = mutableSetOf<String>()
        val lowerText = text.lowercase()

        // 使用已提取的关键词或重新提取
        val keywords = if (extractedKeywords.isEmpty()) {
            try {
                HanLP.extractKeyword(text, config.extractKeywordCount)
            } catch (e: Exception) {
                extractKeywordsFallback(text, config.extractKeywordCount)
            }
        } else {
            extractedKeywords
        }

        // 快速分类匹配 - 使用预定义的类别映射
        for ((category, categoryKeywords) in CATEGORY_MAP) {
            // 先检查关键词
            for (keyword in keywords) {
                if (categoryKeywords.contains(keyword.lowercase())) {
                    result.add(category)
                    break
                }
            }

            // 如果关键词匹配失败，检查原始文本
            if (category !in result) {
                for (categoryKeyword in categoryKeywords) {
                    if (lowerText.contains(categoryKeyword)) {
                        result.add(category)
                        break
                    }
                }
            }
        }

        // 如果没有找到匹配的类别，使用备用方法
        if (result.isEmpty()) {
            return predictCategoriesFallback(text)
        }

        return result
    }

    /**
     * 备用分类预测方法 - 基于规则和模式
     */
    fun predictCategoriesFallback(text: String): Set<String> {
        val result = mutableSetOf<String>()
        val lowerText = text.lowercase()

        // 技术与工作
        if (Regex("(代码|编程|函数|算法|程序|bug|开发|框架|编译|库|api|接口|调试|部署|报错|git|服务器|云服务|github)").find(lowerText) != null)
            result.add("技术问题")

        if (Regex("(ai|人工智能|大模型|chatgpt|gpt|claude|midjourney|prompt|生成式|训练|机器学习|神经网络|大语言模型|llm|数智化)").find(lowerText) != null)
            result.add("AI与科技")

        if (Regex("(工作|职位|简历|上班|同事|老板|项目|工资|薪资|加班|裁员|晋升|职场|跳槽|面试|offer|内卷|35岁|绩效|kpi)").find(lowerText) != null)
            result.add("职场发展")

        if (Regex("(副业|兼职|赚钱|创业|收入|项目|被动收入|躺赚|小生意|创收|商机|销售|客源|电商|直播带货)").find(lowerText) != null)
            result.add("副业创收")

        // 生活场景
        if (Regex("(日常|平时|每天|习惯|早起|晚睡|通勤|上班|下班|公交|地铁|吃饭|睡觉|逛街|购物|做饭|洗衣|收拾|打扫|松弛感)").find(lowerText) != null)
            result.add("日常生活")

        if (Regex("(生病|医生|医院|症状|药物|健康|疼痛|治疗|睡眠|身体不适|体检|保健|疾病|肠胃|失眠|焦虑|抑郁|精神|心理)").find(lowerText) != null)
            result.add("健康医疗")

        if (Regex("(美食|餐厅|菜品|食物|烹饪|食谱|厨艺|探店|外卖|火锅|烧烤|甜品|饮料|网红店|必吃|打卡|试吃|测评)").find(lowerText) != null)
            result.add("美食探店")

        if (Regex("(旅游|旅行|景点|出游|旅程|度假|目的地|行程|攻略|机票|酒店|民宿|签证|景区|打卡|拍照|vlog|city)").find(lowerText) != null)
            result.add("旅行出行")

        // 网络文化
        if (Regex("(搞抽象|抽象|玩抽象|草台班子|古希腊|硬控|红温|偷感|水灵灵|班味)").find(lowerText) != null)
            result.add("兴趣圈层")

        // 如果没有匹配到任何类别，返回"其他"作为默认分类
        if (result.isEmpty()) {
            result.add("其他")
        }

        return result
    }

    /**
     * 备用方法：提取实体
     */
    private fun extractEntitiesFallback(text: String): List<String> {
        val entities = mutableListOf<String>()

        // 提取中文专有名词
        val chineseEntityPattern = Regex("[\\u4e00-\\u9fa5]{2,}(?:公司|大学|学院|研究所|集团|平台|系统|框架|协议|标准|品牌|型号|软件|应用|游戏|电影|剧集)")
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
     * 备用关键词提取方法
     */
    private fun extractKeywordsFallback(text: String, topK: Int): List<String> {
        val wordCounts = mutableMapOf<String, Int>()

        val words = text.split(" ", "，", "。", "？", "！", "、")
            .filter { it.isNotEmpty() && it.length > 1 && it !in STOP_WORDS }

        words.forEach { word ->
            wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
        }

        return wordCounts.entries
            .sortedByDescending { it.value }
            .take(topK)
            .map { it.key }
    }

    /**
     * 计算两段文本的语义相似度
     * @param text1 第一段文本
     * @param text2 第二段文本
     * @return 相似度分数(0-1)
     */
    suspend fun calculateSemanticSimilarity(text1: String, text2: String): Float = withContext(Dispatchers.Default) {
        try {
            // 提取两段文本的语义信息
            val textInfo1 = extractTextInfo(text1)
            val textInfo2 = extractTextInfo(text2)

            // 使用内部方法计算相似度
            return@withContext calculateSemanticSimilarity(textInfo1, textInfo2)
        } catch (e: Exception) {
            // 如果复杂分析失败，使用简单的词袋模型计算相似度
            val words1 = segment(text1)
            val words2 = segment(text2)
            return@withContext calculateCosineSimilarity(words1, words2)
        }
    }

    /**
     * 计算两个TextInfo对象的语义相似度
     */
    fun calculateSemanticSimilarity(textInfo1: TextInfo, textInfo2: TextInfo): Float {
        // 1. 词袋模型余弦相似度 (30%)
        val bagOfWordsScore = calculateCosineSimilarity(textInfo1.words, textInfo2.words)

        // 2. 关键词匹配得分 (25%)
        val keywordScore = calculateKeywordSimilarity(textInfo1.keywords, textInfo2.keywords)

        // 3. 实体匹配得分 (15%)
        val entityScore = calculateEntityMatch(textInfo1.entities, textInfo2.entities)

        // 4. 数字匹配得分 (5%)
        val numberScore = calculateNumberMatch(textInfo1.numbers, textInfo2.numbers)

        // 5. 情感词汇匹配得分 (15%)
        val emotionalScore = calculateEmotionalWordMatch(textInfo1.emotionalWords, textInfo2.emotionalWords)

        // 6. 网络流行词匹配得分 (5%)
        val netWordsScore = calculateNetWordsMatch(textInfo1.netWords, textInfo2.netWords)

        // 7. 类别匹配加权 (额外加成)
        val categoryBoost = if (textInfo1.category == textInfo2.category && textInfo1.category.isNotEmpty()) 0.05f else 0f

        // 8. 时间/最近引用匹配 (额外加成)
        val timeBoost = if (textInfo1.isTimeReference && textInfo2.isTimeReference) 0.05f else 0f
        val recentBoost = if (textInfo1.isRecentReference && textInfo2.isRecentReference) 0.05f else 0f

        // 9. 情感类型匹配 (额外加成)
        val emotionalBoost = if (textInfo1.isEmotional && textInfo2.isEmotional) 0.1f else 0f

        // 综合评分
        val baseScore = bagOfWordsScore * 0.30f +
                keywordScore * 0.25f +
                entityScore * 0.15f +
                numberScore * 0.05f +
                emotionalScore * 0.15f +
                netWordsScore * 0.05f

        // 附加加成不改变基础评分，只对高于阈值的相似度进行提升
        val totalBoost = categoryBoost + timeBoost + recentBoost + emotionalBoost

        // 基础评分和加成结合
        return if (baseScore > RELEVANCE_THRESHOLD) {
            (baseScore * (1f + totalBoost)).coerceAtMost(1.0f)
        } else {
            baseScore // 低于阈值不给加成
        }
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
        val totalMatches = exactMatches * 1.5f + partialMatches * 0.8f
        val maxPossibleMatches = minOf(keywords1.size, keywords2.size) * 1.5f // 考虑到精确匹配加权

        val simpleRatio = totalMatches / maxPossibleMatches

        // 非线性映射，使高匹配度更突出
        return (simpleRatio * 0.6f) + (simpleRatio * simpleRatio * 0.4f)
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
        return matchRatio * (1.0f + (0.25f * matches)) // 匹配越多，加成越高
    }

    /**
     * 计算情感词汇匹配得分
     */
    private fun calculateEmotionalWordMatch(words1: List<String>, words2: List<String>): Float {
        if (words1.isEmpty() || words2.isEmpty()) return 0f

        // 计算匹配的情感词数量
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

        // 计算基础得分
        val baseScore = matches.toFloat() / maxOf(1, minOf(words1.size, words2.size))

        // 如果两个集合都包含多个情感词，给予额外加成
        val bonusFactor = if (words1.size >= 2 && words2.size >= 2) 1.2f else 1.0f

        return (baseScore * bonusFactor).coerceAtMost(1.0f)
    }

    /**
     * 计算实体匹配得分
     */
    private fun calculateEntityMatch(entities1: List<String>, entities2: List<String>): Float {
        if (entities1.isEmpty() || entities2.isEmpty()) return 0f

        // 计算实体匹配数量，区分完全匹配和部分匹配
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
        val totalScore = (exactMatches * 1.5f + partialMatches * 0.7f) /
                maxOf(1, maxOf(entities1.size, entities2.size))

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
        val totalScore = (exactMatches * 1.5f + similarMatches * 0.8f) /
                maxOf(1, maxOf(numbers1.size, numbers2.size))

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

        // 获取所有唯一词
        val allWords = freqMap1.keys.union(freqMap2.keys)

        // 计算点积、范数等
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (word in allWords) {
            // 应用TF-IDF思想：通用词权重降低，特殊词权重提高
            val weight = when {
                STOP_WORDS.contains(word) -> 0.3              // 停用词权重很低
                word.length <= 1 -> 0.5                       // 单字词权重较低
                EMOTIONAL_KEYWORDS.contains(word) -> 2.0      // 情感词权重很高
                NETWORK_WORDS.contains(word) -> 1.8           // 网络词权重较高
                word.length >= 4 -> 1.5                       // 长词可能是术语，权重较高
                else -> 1.0                                   // 默认权重
            }

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
        val similarity = (dotProduct / (sqrt(norm1) * sqrt(norm2))).toFloat()

        // 非线性增强处理，使中等相似度更加凸显
        return if (similarity > 0.5f) {
            val enhancement = (similarity - 0.5f) * 0.2f
            (similarity + enhancement).coerceAtMost(1.0f)
        } else {
            similarity
        }
    }

    /**
     * 比较两个文本的相似度并给出解释
     * @param text1 第一段文本
     * @param text2 第二段文本
     * @return 相似度详细分析结果
     */
    suspend fun explainSimilarity(text1: String, text2: String): SimilarityExplanation = withContext(Dispatchers.Default) {
        try {
            // 提取两段文本的语义信息
            val textInfo1 = extractTextInfo(text1)
            val textInfo2 = extractTextInfo(text2)

            // 计算各项相似度得分
            val bagOfWordsScore = calculateCosineSimilarity(textInfo1.words, textInfo2.words)
            val keywordScore = calculateKeywordSimilarity(textInfo1.keywords, textInfo2.keywords)
            val entityScore = calculateEntityMatch(textInfo1.entities, textInfo2.entities)
            val emotionalScore = calculateEmotionalWordMatch(textInfo1.emotionalWords, textInfo2.emotionalWords)
            val netWordsScore = calculateNetWordsMatch(textInfo1.netWords, textInfo2.netWords)

            // 类别和情感匹配
            val categoryMatch = textInfo1.category == textInfo2.category
            val emotionalMatch = textInfo1.isEmotional && textInfo2.isEmotional

            // 计算总体相似度
            val totalScore = calculateSemanticSimilarity(textInfo1, textInfo2)

            // 查找共有关键词
            val commonKeywords = textInfo1.keywords.filter { kw1 ->
                textInfo2.keywords.any { kw2 ->
                    kw1 == kw2 || kw1.contains(kw2) || kw2.contains(kw1)
                }
            }

            // 构建解释
            return@withContext SimilarityExplanation(
                overallSimilarity = totalScore,
                keywordSimilarity = keywordScore,
                wordsSimilarity = bagOfWordsScore,
                categoriesMatch = categoryMatch,
                emotionalMatch = emotionalMatch,
                commonKeywords = commonKeywords.take(5),
                text1Categories = textInfo1.categories,
                text2Categories = textInfo2.categories
            )
        } catch (e: Exception) {
            // 出错时返回基本解释
            val basicScore = try {
                val words1 = segment(text1)
                val words2 = segment(text2)
                calculateCosineSimilarity(words1, words2)
            } catch (e2: Exception) {
                0.0f
            }

            return@withContext SimilarityExplanation(
                overallSimilarity = basicScore,
                keywordSimilarity = 0.0f,
                wordsSimilarity = basicScore,
                categoriesMatch = false,
                emotionalMatch = false,
                commonKeywords = emptyList(),
                text1Categories = setOf("其他"),
                text2Categories = setOf("其他"),
                error = e.message ?: "计算相似度过程中发生错误"
            )
        }
    }

    /**
     * 相似度解释数据类
     */
    data class SimilarityExplanation(
        val overallSimilarity: Float,        // 总体相似度
        val keywordSimilarity: Float,        // 关键词相似度
        val wordsSimilarity: Float,          // 词汇相似度
        val categoriesMatch: Boolean,        // 类别是否匹配
        val emotionalMatch: Boolean,         // 情感类型是否匹配
        val commonKeywords: List<String>,    // 共有关键词
        val text1Categories: Set<String>,    // 文本1分类
        val text2Categories: Set<String>,    // 文本2分类
        val error: String? = null            // 错误信息(如果有)
    ) {
        /**
         * 获取人类可读的相似度解释
         */
        fun getHumanReadableExplanation(): String {
            if (error != null) {
                return "分析过程发生错误: $error"
            }

            val similarityLevel = when {
                overallSimilarity >= 0.8f -> "非常相似"
                overallSimilarity >= 0.6f -> "较为相似"
                overallSimilarity >= 0.4f -> "有一定相似度"
                overallSimilarity >= 0.2f -> "略有相似"
                else -> "相似度较低"
            }

            val explanation = StringBuilder("这两段文本$similarityLevel (${(overallSimilarity * 100).toInt()}%)。\n")

            // 添加类别信息
            if (categoriesMatch) {
                explanation.append("它们属于相同的类别: ${text1Categories.first()}\n")
            } else {
                explanation.append("它们可能属于不同的类别: ${text1Categories.first()} vs ${text2Categories.first()}\n")
            }

            // 添加关键词信息
            if (commonKeywords.isNotEmpty()) {
                explanation.append("共同关键词: ${commonKeywords.joinToString(", ")}\n")
            }

            // 添加情感匹配信息
            if (emotionalMatch) {
                explanation.append("两段文本都包含情感表达\n")
            }

            return explanation.toString()
        }
    }

    /**
     * 批量处理多个文本
     * @param texts 要处理的文本列表
     * @return 处理结果列表
     */
    suspend fun batchProcessTexts(texts: List<String>): List<TextInfo> = withContext(Dispatchers.Default) {
        val results = mutableListOf<TextInfo>()

        for (text in texts) {
            try {
                val info = extractTextInfo(text)
                results.add(info)
            } catch (e: Exception) {
                Log.e(TAG, "批量处理文本失败: ${e.message}", e)
                // 失败时添加简单信息
                results.add(createSimpleTextInfo(text))
            }
        }

        return@withContext results
    }
}
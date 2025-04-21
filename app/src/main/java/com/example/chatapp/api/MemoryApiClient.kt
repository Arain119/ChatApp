package com.example.chatapp.api

import android.content.Context
import android.util.Log
import com.example.chatapp.data.ApiConfig
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
 * 记忆API客户端 - 与记忆 API交互
 */
class MemoryApiClient(private val context: Context) {

    private val TAG = "MemoryApiClient"

    // 跟踪当前URL，用于检测变化
    private var currentBaseUrl: String? = null
    private var retrofitInstance: Retrofit? = null
    private var serviceInstance: MemoryApiService? = null

    companion object {
        // 预定义的记忆分类列表
        private val MEMORY_CATEGORIES = listOf(
            // 技术与工作
            "技术问题", "产品咨询", "职场发展", "副业创收", "AI与科技",

            // 生活场景
            "日常生活", "健康医疗", "美食探店", "旅行出行", "居家生活",

            // 个人与情感
            "个人信息", "情感", "脱单攻略", "伴侣婚姻", "家庭亲子","婚恋","家庭",

            // 社交与社区
            "社交", "虚拟社交", "兴趣圈层", "键政社会","社区",

            // 数字娱乐
            "游戏电竞", "二次元", "直播短视频", "影视剧集", "宠物萌宠",

            // 学习与发展
            "学习教育", "自我提升", "考证考公", "职业规划",

            // 创意与创作
            "内容创作", "种草安利", "创意设计", "整活沙雕",

            // 生活方式
            "时尚潮流", "颜值护理", "数码科技", "文玩收藏",

            // 理财与消费
            "金融理财", "理性消费", "信用生活",

            // 心理与成长
            "心理疗愈", "破防时刻", "emo治愈", "社恐指南",

            // 流行文化
            "梗与流行", "潮流解读", "网红现象",

            // 新生活方式
            "躺平佛系", "可持续生活", "断舍离", "数字排毒",

            // 其他
            "灵异玄学", "人间观察", "其他"
        )
    }

    // 创建OkHttpClient
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // 获取Retrofit实例，如果URL变化则重新创建
    private fun getRetrofit(): Retrofit {
        val baseUrl = ApiConfig.getMemoryApiUrl(context)

        if (retrofitInstance == null || currentBaseUrl != baseUrl) {
            Log.d(TAG, "初始化记忆API Retrofit - BaseURL: $baseUrl")
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
    private val apiService: MemoryApiService
        get() {
            if (serviceInstance == null) {
                serviceInstance = getRetrofit().create(MemoryApiService::class.java)
            }
            return serviceInstance!!
        }

    /**
     * 使用模型生成记忆
     * 包括内容摘要、分类、重要性评分和关键词
     */
    suspend fun generateEnhancedMemory(messages: List<ChatMessage>): MemoryGenerationResult {
        try {
            Log.d(TAG, "生成记忆，消息数量: ${messages.size}")

            // 如果消息数量太少，返回简单结果
            if (messages.size < 2) {
                Log.d(TAG, "消息数量太少，生成简单记忆")
                return createSimpleMemory(messages)
            }

            // 构造增强提示词
            val prompt = buildEnhancedMemoryPrompt(messages)

            // 构造请求
            val requestMessages = listOf(
                ChatMessage("system", prompt),
                ChatMessage("user", """
                    请根据上述对话，提供以下几项信息：
                    1. 总结：对话的核心内容概括（25字以内，简洁有力）
                    2. 类别：从以下选项中选择一个最匹配的类别：
                       技术问题、产品咨询、职场发展、副业创收、AI与科技、日常生活、健康医疗、美食探店、旅行出行、居家生活、个人信息、情感关系、脱单攻略、伴侣婚姻、家庭亲子、社交社区、虚拟社交、兴趣圈层、键政社会、游戏电竞、二次元、直播短视频、影视剧集、宠物萌宠、学习教育、自我提升、考证考公、职业规划、内容创作、种草安利、创意设计、整活沙雕、时尚潮流、颜值护理、数码科技、文玩收藏、金融理财、理性消费、信用生活、心理疗愈、破防时刻、emo治愈、社恐指南、梗与流行、潮流解读、网红现象、躺平佛系、可持续生活、断舍离、数字排毒、灵异玄学、人间观察、其他
                    3. 重要性：评估对话的重要程度（1-10分，10分最重要）
                    4. 关键词：从对话中提取3-5个关键词或术语
                    
                    请按以下格式回复，每项单独一行：
                    总结：对话总结
                    类别：对话类别
                    重要性：评分数字
                    关键词：关键词1,关键词2,关键词3
                """.trimIndent())
            )

            val request = ChatGptRequest(
                model = ApiConfig.getMemoryModelName(context),  // 使用配置中的模型名
                messages = requestMessages,
                temperature = 0.5
            )

            // 发送请求，使用配置中的API Key
            val apiKey = ApiConfig.getMemoryApiKey(context)
            val response = apiService.generateMemory(
                "Bearer $apiKey",
                request
            )

            if (response.isSuccessful && response.body() != null) {
                val rawResult = response.body()!!.choices[0].message.content.toString()
                Log.d(TAG, "增强记忆生成结果: $rawResult")

                // 解析结果
                return try {
                    parseResult(rawResult)
                } catch (e: Exception) {
                    // 如果解析失败，至少返回摘要内容
                    Log.e(TAG, "解析记忆结果失败: ${e.message}", e)
                    createSimpleMemory(messages)
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "未知错误"
                Log.e(TAG, "记忆生成失败: $errorBody")
                return createSimpleMemory(messages)
            }
        } catch (e: Exception) {
            Log.e(TAG, "记忆生成异常: ${e.message}", e)
            return createSimpleMemory(messages)
        }
    }

    /**
     * 使用更简单的格式解析结果
     */
    private fun parseResult(rawResult: String): MemoryGenerationResult {
        var summary = ""
        var category = "其他"
        var importance = 5
        val keywords = mutableListOf<String>()

        // 按行解析结果
        val lines = rawResult.split("\n")
        for (line in lines) {
            when {
                line.startsWith("总结：") -> {
                    summary = line.substringAfter("总结：").trim()
                }
                line.startsWith("类别：") -> {
                    val cat = line.substringAfter("类别：").trim()
                    category = if (MEMORY_CATEGORIES.contains(cat)) cat else "其他"
                }
                line.startsWith("重要性：") -> {
                    importance = try {
                        line.substringAfter("重要性：").trim().toInt().coerceIn(1, 10)
                    } catch (e: Exception) {
                        5 // 默认中等重要性
                    }
                }
                line.startsWith("关键词：") -> {
                    val keywordStr = line.substringAfter("关键词：").trim()
                    keywords.addAll(keywordStr.split(",", "，", " ").map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
        }

        return MemoryGenerationResult(
            summary = summary,
            category = category,
            importance = importance,
            keywords = keywords.take(5) // 最多取5个关键词
        )
    }

    /**
     * 创建简单记忆（不使用API时的后备方案）
     */
    private fun createSimpleMemory(messages: List<ChatMessage>): MemoryGenerationResult {
        // 尝试从用户的第一条消息中提取主题
        val firstUserMessage = messages.firstOrNull { it.role == "user" }?.content?.toString() ?: ""

        // 摘要：使用用户第一条消息的前20个字符
        val summary = if (firstUserMessage.length <= 20) {
            firstUserMessage
        } else {
            firstUserMessage.take(20)
        }

        // 尝试基于关键词确定类别
        val category = guessCategory(firstUserMessage)

        // 简单提取关键词
        val keywords = extractSimpleKeywords(firstUserMessage)

        return MemoryGenerationResult(
            summary = summary,
            category = category,
            importance = 5, // 默认中等重要性
            keywords = keywords
        )
    }

    /**
     * 基于文本内容猜测可能的类别
     */
    private fun guessCategory(text: String): String {
        val lowerText = text.lowercase()

        return when {
            // 技术和工作
            Regex("(代码|编程|函数|算法|程序|bug|开发|框架|编译|库|api|接口|调试|部署|报错|git|服务器|云服务|github|npm|docker)").find(lowerText) != null -> "技术问题"

            // AI与科技
            Regex("(ai|人工智能|大模型|chatgpt|gpt|claude|midjourney|stable diffusion|prompt|生成式|训练|机器学习|神经网络|大语言模型|llm|深度学习)").find(lowerText) != null -> "AI与科技"

            // 职场发展
            Regex("(工作|职位|简历|上班|同事|老板|项目|工资|薪资|加班|裁员|晋升|职场|跳槽|面试|offer|内卷|35岁|绩效|kpi|pua|icu)").find(lowerText) != null -> "职场发展"

            // 副业创收
            Regex("(副业|兼职|赚钱|创业|收入|项目|被动收入|躺赚|小生意|创收|商机|销售|客源|电商|直播带货|淘宝|拼多多|抖音|快手|知乎|小红书)").find(lowerText) != null -> "副业创收"

            // 学习教育
            Regex("(学习|课程|教育|考试|学校|大学|论文|学生|老师|教授|学位|考研|考证|自学|培训|线上课程|慕课|学习通|知识付费|公开课)").find(lowerText) != null -> "学习教育"

            // 考证考公
            Regex("(考试|考证|考公|公务员|事业编|国考|省考|教师资格|会计|cpa|法考|建造师|研究生|考研|考博|留学|gre|雅思|托福)").find(lowerText) != null -> "考证考公"

            // 健康医疗
            Regex("(病|医生|医院|症状|药|健康|疼痛|治疗|睡眠|身体不适|体检|保健|疾病|肠胃|失眠|焦虑|抑郁|精神|心理|咨询|医保)").find(lowerText) != null -> "健康医疗"

            // 自我提升
            Regex("(自我提升|成长|效率|时间管理|记忆力|学习方法|思维导图|阅读|笔记|目标|计划|todo|打卡|习惯养成|早起|晨间|拖延|行动力)").find(lowerText) != null -> "自我提升"

            // 游戏电竞
            Regex("(游戏|moba|fps|rpg|氪金|手游|主机|steam|xbox|ps5|ns|开黑|电竞|赛事|皮肤|英雄|段位|版本|赛季|对局|排位|天梯|匹配|大乱斗|开局|上分)").find(lowerText) != null -> "游戏电竞"

            // 躺平佛系
            Regex("(躺平|摆烂|佛系|不内卷|内卷|卷王|不努力|养老|躺赢|咸鱼|无所谓|看开|佛了|随缘|顺其自然|破防|emo)").find(lowerText) != null -> "躺平佛系"

            // 二次元
            Regex("(二次元|动漫|漫画|acg|宅|cos|coser|手办|b站|番剧|萌|老婆|角色|声优|vtuber|galgame|轻小说|日漫|国漫|同人|周边|痛车|痛包)").find(lowerText) != null -> "二次元"

            // 直播短视频
            Regex("(直播|短视频|抖音|快手|小红书|b站|vlog|主播|网红|博主|点赞|投币|关注|粉丝|评论区|流量|kol|dou+|推荐算法|私域|公域|评论区|弹幕)").find(lowerText) != null -> "直播短视频"

            // 影视剧集
            Regex("(电影|电视剧|剧集|综艺|追剧|刷剧|演员|导演|豆瓣|评分|烂片|神作|口碑|影评|解说|票房|热映|档期|爆款|流量明星|顶流|番茄|烂番茄)").find(lowerText) != null -> "影视剧集"

            // 美食探店
            Regex("(美食|餐厅|菜品|食物|烹饪|食谱|厨艺|探店|外卖|火锅|烧烤|甜品|饮料|网红店|必吃|打卡|试吃|测评|值得|安利|推荐|好吃|好喝|回购)").find(lowerText) != null -> "美食探店"

            // 旅行出行
            Regex("(旅游|旅行|景点|出游|旅程|度假|目的地|行程|攻略|机票|酒店|民宿|签证|景区|打卡|拍照|vlog|景色|风景|地铁|公交|高铁|动车|飞机)").find(lowerText) != null -> "旅行出行"

            // 时尚潮流
            Regex("(穿搭|搭配|衣服|潮牌|球鞋|服饰|时尚|品牌|风格|街拍|vintage|配色|单品|限量|联名|潮鞋|潮流|牛仔|风衣|卫衣|t恤|阿迪|耐克|supreme)").find(lowerText) != null -> "时尚潮流"

            // 数码科技
            Regex("(手机|电脑|耳机|相机|智能手表|数码|科技|测评|体验|配置|参数|升级|新款|旗舰|入手|苹果|华为|小米|三星|vivo|oppo|平板|笔记本|台式机)").find(lowerText) != null -> "数码科技"

            // 宠物萌宠
            Regex("(猫|狗|宠物|cue|铲屎官|猫咪|狗狗|喵|汪|萌宠|猫粮|狗粮|撸猫|遛狗|猫砂|猫抓板|猫窝|猫爬架|猫树|猫别墅|吸猫|铲屎|萌|可爱|小宝贝)").find(lowerText) != null -> "宠物萌宠"

            // 颜值护理
            Regex("(护肤|彩妆|化妆|保养|护理|发型|染发|减肥|健身|美容|医美|健身房|锻炼|瘦身|面膜|乳液|精华|水乳|防晒|卸妆|口红|眼影|眼线|粉底|气垫)").find(lowerText) != null -> "颜值护理"

            // 居家生活
            Regex("(家居|家装|装修|家具|收纳|整理|清洁|打扫|懒人|神器|生活|日用|家电|电器|锅碗瓢盆|床上用品|沙发|茶几|电视|洗衣机|冰箱|空调|扫地机器人)").find(lowerText) != null -> "居家生活"

            // 心理疗愈
            Regex("(焦虑|抑郁|压力|情绪|心理|疗愈|心态|情绪低落|崩溃|内耗|emo|安慰|倾诉|倦怠|心累|痛苦|失眠|冥想|正念|舒适圈|边界感|自我成长|ptsd)").find(lowerText) != null -> "心理疗愈"

            // 破防时刻
            Regex("(破防|破了|泪目|哭了|流泪|感动|想哭|心酸|心疼|扎心|暖心|感人|催泪|治愈|心碎|难过|悲伤|遗憾|难受|委屈|痛苦|绝望)").find(lowerText) != null -> "破防时刻"

            // emo治愈
            Regex("(emo|情绪低落|低落|难过|心情|不好|消沉|郁闷|烦躁|治愈|解压|释放|放松|舒缓|缓解|减压|安心|放空|摆脱|走出|走出来)").find(lowerText) != null -> "emo治愈"

            // 社恐指南
            Regex("(社恐|社交恐惧|不敢社交|害怕人群|不会聊天|尴尬|交流障碍|沉默|不说话|紧张|局促|生人|陌生人|不认识|不熟悉|退缩|回避|逃避|推脱|社交技巧)").find(lowerText) != null -> "社恐指南"

            // 内容创作
            Regex("(创作|自媒体|内容|粉丝|流量|博主|创作者|变现|平台|剪辑|剧本|策划|脚本|拍摄|运营|矩阵|公众号|视频号|小程序|微信|抖音|知乎|小红书)").find(lowerText) != null -> "内容创作"

            // 种草安利
            Regex("(种草|安利|推荐|好物|推荐|入手|购物|剁手|拔草|买了|回购|囤货|必买|值得|不值|收藏|购入|清单|好用|好吃|好喝|好看|划算|便宜|性价比)").find(lowerText) != null -> "种草安利"

            // 梗与流行
            Regex("(梗|热梗|xswl|yyds|绝绝子|emo|破防|社死|笑死|蚌埠住了|典|孝|xhs|汪汪火腿肠|ikun|小黑子|鸡你太美|南山必胜客|隔壁王矿长|烤山药|手动艾特|自己狠狠)").find(lowerText) != null -> "梗与流行"

            // 整活沙雕
            Regex("(整活|活|搞笑|沙雕|离谱|魔性|社死|猎奇|迷惑|抽象|典|孝|蚌埠住了|绷不住|蚌|笑不活了|哈人|麻了|孝完了|不讲武德|闪现|骚操作|神操作|迷惑行为)").find(lowerText) != null -> "整活沙雕"

            // 虚拟社交
            Regex("(虚拟社交|社交平台|交友软件|社区|论坛|贴吧|群聊|微信群|小组|qq群|discord|telegram|社团|俱乐部|同好会|粉丝群|兴趣小组|兴趣社区)").find(lowerText) != null -> "虚拟社交"

            // 兴趣圈层
            Regex("(兴趣|爱好|圈子|小众|小圈子|小群体|亚文化|粉丝文化|饭圈|团建|聚会|线下|趴|主题活动|展会|展览|音乐节|嘉年华|漫展|车展|潮玩|游戏展|电玩节)").find(lowerText) != null -> "兴趣圈层"

            // 可持续生活
            Regex("(可持续|环保|低碳|减塑|节能|循环经济|二手|闲置|旧物利用|素食|纯素|低欲望|极简|断舍离|轻断食|节制|环保袋|环保餐具|无纸化|环保主义|气候变化)").find(lowerText) != null -> "可持续生活"

            // 断舍离
            Regex("(断舍离|极简|极简主义|整理|收纳|断|舍|离|处理|整理控|收纳控|断舍离|不要|扔掉|处理掉|简化|简约|无印良品|muji|宜家|ikea|生活质量|提升)").find(lowerText) != null -> "断舍离"

            // 数字排毒
            Regex("(数字排毒|数字极简|远离社交媒体|戒手机|戒网|戒短视频|戒游戏|沉迷|上瘾|低头族|抬头|数字焦虑|信息焦虑|不在线|离线|禁网|无网|乡村|远离|数字原住民)").find(lowerText) != null -> "数字排毒"

            // 个人信息
            Regex("(个人信息|名字|年龄|性别|出生|住址|地址|联系方式|电话|邮箱|身份证|护照|学历|学位|经历|工作|职业|收入|存款|我是|我的|我|本人)").find(lowerText) != null -> "个人信息"

            // 产品咨询
            Regex("(产品|价格|功能|购买|使用|服务|订阅|客户|支持|退款|售后|开通|升级|版本|费用|收费|付费|免费|会员|vip|试用|体验|优惠|折扣|促销|打折)").find(lowerText) != null -> "产品咨询"

            // 日常生活
            Regex("(日常|平时|每天|习惯|早起|晚睡|通勤|上班|下班|公交|地铁|吃饭|睡觉|逛街|购物|做饭|洗衣|收拾|打扫|卫生|家务|周末|休息|娱乐|爱好)").find(lowerText) != null -> "日常生活"

            // 情感关系
            Regex("(爱情|恋爱|喜欢|暗恋|失恋|约会|表白|恋人|感情|脱单|单身|爱上|感动|分手|复合|挽回|吸引力|自由恋爱|姐弟恋|双向奔赴|情侣|男女)").find(lowerText) != null -> "情感关系"

            // 脱单攻略
            Regex("(脱单|摆脱单身|谈恋爱|约会|相亲|交友软件|婚恋网|相亲角|介绍|撩|撩妹|撩汉|追|追求|表白|示好|心动|心意|好感|搭讪|搭话|搭腔|暧昧|升温)").find(lowerText) != null -> "脱单攻略"

            // 伴侣婚姻
            Regex("(婚姻|伴侣|夫妻|妻子|丈夫|老公|老婆|男友|女友|结婚|离婚|婚礼|彩礼|嫁妆|两性|冷战|争吵|婚前|婚后|领证|结婚证|摆酒|婚宴|蜜月|度蜜月)").find(lowerText) != null -> "伴侣婚姻"

            // 家庭亲子
            Regex("(家庭|家人|亲人|父母|儿女|孩子|养育|子女|抚养|教育|母亲|父亲|亲子|隔代|公婆|岳父母|催生|婆媳|姻亲|亲家|三代同堂|四世同堂)").find(lowerText) != null -> "家庭亲子"

            // 社交社区
            Regex("(社交|交友|社区|邻居|同学|认识|人际关系|社交圈|聚会|朋友|友谊|相处|融入|交流|沟通|联系|联络|微信|通讯录|互加|好友|联系人)").find(lowerText) != null -> "社交社区"

            // 金融理财
            Regex("(理财|投资|股票|基金|保险|债券|房产|存款|贷款|信用卡|消费|支出|预算|支付宝|微信支付|股市|牛市|熊市|回调|收益|收入|盈亏|股市|大盘)").find(lowerText) != null -> "金融理财"

            // 理性消费
            Regex("(理性消费|消费|能省|折扣|优惠|入手|家庭|预算|开销|支出|收支|入不敷出|超支|透支|够用|钱包|消费观|消费主义|拒绝|不买|克制|自制力)").find(lowerText) != null -> "理性消费"

            // 信用生活
            Regex("(信用|信用卡|贷款|房贷|车贷|花呗|借呗|白条|信用额度|信用评分|征信|负债|偿还|还款|欠款|逾期|利率|分期|套现|养卡|提额|额度|透支)").find(lowerText) != null -> "信用生活"

            // 键政社会
            Regex("(键政|政治|社会|时事|新闻|政策|法律|制度|国际|国内|改革|事件|热点|舆论|争议|讨论|观点|意见|立场|影响|问题|社会化|民生|民众|百姓)").find(lowerText) != null -> "键政社会"

            // 创意设计
            Regex("(设计|绘画|画画|素描|创意|艺术|手绘|板绘|配色|排版|字体|美学|插画|平面|UI|用户界面|交互|界面|产品设计|服装设计|室内设计|建筑设计)").find(lowerText) != null -> "创意设计"

            // 文玩收藏
            Regex("(文玩|收藏|把玩|鉴赏|把件|盘玩|籽料|玉石|翡翠|和田玉|南红|蜜蜡|琥珀|沉香|手串|手链|挂件|串珠|金刚|菩提|核桃|手把件)").find(lowerText) != null -> "文玩收藏"

            // 潮流解读
            Regex("(潮流|解读|分析|趋势|潮人|时尚|流行|潮牌|潮品|单品|必备|爆款|流行款|大火|出圈|出街|时尚圈|潮流圈|时尚博主|穿搭|搭配|风格)").find(lowerText) != null -> "潮流解读"

            // 网红现象
            Regex("(网红|网络红人|出圈|出名|爆火|爆款|爆红|走红|走红网络|爆米花|出圈了|爆了|涨粉|爆粉|破圈|出圈造星|流量明星|流量|顶流|小火|刷屏|蹿红)").find(lowerText) != null -> "网红现象"

            // 灵异玄学
            Regex("(灵异|玄学|星座|塔罗|神秘|灵异事件|超自然|感应|灵感|预知|命运|命理|风水|算命|算卦|八字|占卜|预测|心灵|灵魂|轮回|前世|今生|解读)").find(lowerText) != null -> "灵异玄学"

            // 人间观察
            Regex("(人间|观察|社会学|人类|行为|习惯|现象|怪现状|奇葩|无语|震惊|唏嘘|感叹|感慨|世态|炎凉|百态|人情|冷暖|世事|沧桑|变迁|存在|真相|真理)").find(lowerText) != null -> "人间观察"

            // 默认分类
            else -> "其他"
        }
    }

    /**
     * 从文本中提取简单关键词
     */
    private fun extractSimpleKeywords(text: String): List<String> {
        // 移除常见标点和停用词
        val cleanedText = text.replace(Regex("[,，.。!！?？;；:：()（）\\[\\]【】\"'']"), " ")

        // 分词
        val words = cleanedText.split(Regex("\\s+"))
            .filter { it.length > 1 } // 过滤单字
            .filter { !isStopWord(it) } // 过滤停用词

        // 检测网络热词和流行词汇
        val netWords = identifyNetworkWords(cleanedText)

        // 合并词汇，并去重
        val combinedWords = (netWords + words).distinct()

        // 按优先级排序
        val sortedWords = combinedWords.sortedWith(
            compareByDescending<String> { it in netWords }
                .thenByDescending { it.length }
        )

        // 最多取5个关键词
        return sortedWords.take(5)
    }

    /**
     * 识别网络热词和流行词汇
     */
    private fun identifyNetworkWords(text: String): List<String> {
        val lowerText = text.lowercase()
        val networkWords = listOf(
            // 2024-2025年官方评选流行语
            "数智化", "智能向善", "未来产业", "city不city", "硬控", "水灵灵地",
            "班味", "松弛感", "银发力量", "小孩哥", "小孩姐", "偷感", "草台班子",
            "那咋了", "古希腊掌管", "包的", "红温", "搞抽象",

            // 网络文化流行语
            "你真是饿了", "五旬老太守国门", "亚比囧囧囧", "没苦硬吃", "牛马",
            "没想到是这样的第一名", "洽古", "麦门", "整根", "青春很好", "丁真",
            "逆天", "太潮辣", "超绝", "跪", "破防", "emo", "爱莫能助",
            "润", "打工人", "社恐", "蚌埠住了", "整活", "整蛊",
            "无语", "麻了", "有被笑到", "离谱", "整笑了", "笑嘻了", "绷不住了",
            "爆金币", "爆杀", "上头", "下头", "鬼才", "他急了", "他真的我哭死",

            // Z世代特有表达
            "普信男", "海鲜市场", "girlboss", "姐性", "凡尔赛文学", "内卷",
            "躺平", "摆烂", "卷王", "绝绝子", "yyds", "绝", "超绝", "梦幻",
            "顶流", "顶真", "神", "超神", "自助餐", "心动词", "叠词",
            "笑死", "不谈了", "不约", "不约而同", "奥利给", "skr", "c位",
            "plmm", "xswl", "u1s1", "yysy", "xdm", "懂得都懂", "老铁",
            "给我冲", "上大分", "高能", "锁死", "原地结婚", "磕到了", "嗑死我了",

            // 情感表达类
            "好嗨哟", "好家伙", "绝了", "绝绝子", "社死", "破防", "emo",
            "救命", "我不好了", "啊这", "高糊", "低糊", "真实", "太真实了",
            "真下头", "我哭死", "属于是", "生活中总会有这样的时刻", "寄了",
            "我超爱的", "我吹爆", "我真的栓Q", "大无语事件", "太酸了", "甜到齁",
            "我哭死", "心态崩了", "好嘞", "好似", "好日子还在后头呢", "红温了",

            // 网络趋势和梗
            "元宇宙", "AIGC", "大语言模型", "chatgpt", "midjourney", "大模型",
            "数字人", "出圈", "破圈", "出圈造星", "流量密码", "顶流", "塌房",
            "私域", "公域", "kol", "种草", "拔草", "收手", "李姐万岁", "笑死",
            "神仙打架", "带货", "直播带货", "割韭菜", "韭菜盒子", "韭菜本韭",
            "硬币模型", "二创", "二次元", "galgame", "漫圈", "饭圈", "对标",

            // 职场和生活
            "打工人", "打工魂", "打工都", "秃头", "秃如其来", "脱发", "掉发",
            "社畜", "摸鱼", "躺平", "996", "35岁", "中年危机", "职场pua",
            "内卷", "恶性竞争", "鸡娃", "超前教育", "偷着乐", "摆烂", "干饭人",
            "考公", "考编", "上岸", "裸辞", "被离职", "失业", "招聘", "offer",
            "大小周", "工作，是什么工作", "重新定义", "开会", "汇报", "复盘",

            // 网络缩写和谐音
            "awsl", "dbq", "nsdd", "gkd", "yyds", "xswl", "plmm", "tcl",
            "xdm", "yjgj", "yygq", "zqsg", "yysy", "giao", "jio", "u1s1",
            "v50", "v我50", "bdjw", "bdjd", "srds", "xdz", "tnl", "yxqs",
            "ghs", "nsfw", "ntr", "lsp", "tql", "yyqg", "1551", "1145",
            "1919810", "996", "007", "2333", "6324", "114514",

            // 俚语和网络语录
            "直球", "弯弯绕", "整根网络用语", "被安排", "被上了", "给我冲",
            "加大力度", "加油干", "人麻了", "离谱", "玩原神", "玩明日方舟",
            "我不李姐", "不懂就问", "不会真有人", "不愧是你", "不认识", "不如睡觉",
            "什么梗", "实名diss", "实名羡慕", "实名制举报", "是个狠人",
            "是兄弟就来砍我", "委屈", "文艺复兴", "我看不懂，但我大受震撼",
            "我哭死", "我真情实感", "无慈悲", "无语", "武德充沛",

            // 流行网络词汇
            "芙蓉王", "摸着石头过河", "魔怔", "内个", "能处", "牛魔", "牛魔王",
            "怒斥", "女拳", "欧拉欧拉", "殴打", "啪啪啪", "耪", "急了",
            "皮套", "批", "啤酒加鸡排", "姘头", "评价", "瞎骂", "急了",
            "米线", "纯良", "寄了", "摆烂", "躺平", "内卷", "后浪", "35岁",
            "破防", "内娱", "流量", "蹲一个", "恰饭", "凡尔赛", "冲浪", "奥利给"
        )

        return networkWords.filter { word ->
            lowerText.contains(word.lowercase())
        }
    }

    /**
     * 检查是否为停用词
     */
    private fun isStopWord(word: String): Boolean {
        val stopWords = setOf(
            // 中文常用停用词
            "的", "了", "和", "是", "在", "我", "有", "你", "他", "她", "它", "这", "那", "都", "就",
            "也", "要", "会", "到", "可以", "可能", "应该", "没有", "这个", "那个", "什么", "怎么",
            "如何", "为什么", "哪里", "谁", "何时", "怎样", "多少", "几", "啊", "呢", "吧", "呀", "哦",
            "哈", "呵", "嗯", "哼", "嘿", "喂", "嗨", "哟", "唉", "咦", "啧",

            // 网络用语中常见无实际意义的词
            "哈哈", "哈哈哈", "嘻嘻",  "hiahia", "hh", "hhh", "哒", "啦", "咯",
            "呐", "呗", "啥", "咋", "么", "呢", "嘛", "啊", "哇", "嗷", "嘞", "叭",

            // 英文停用词
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "be", "been",
            "have", "has", "had", "do", "does", "did", "will", "would", "can", "could", "should",
            "i", "you", "he", "she", "it", "we", "they", "my", "your", "his", "her", "its",
            "our", "their", "this", "that", "these", "those", "am", "is", "are", "was", "were"
        )
        return stopWords.contains(word.lowercase())
    }

    /**
     * 使用模型生成用户画像
     * 基于用户的历史消息生成综合画像，并融合现有画像信息
     */
    suspend fun generateUserProfile(messages: List<ChatMessage>, existingProfile: String? = null): String {
        try {
            Log.d(TAG, "生成用户画像，基于 ${messages.size} 条用户消息，现有画像: ${existingProfile?.take(50)}...")

            // 构造提示词
            val prompt = buildUserProfilePrompt(messages, existingProfile)

            // 构造请求
            val requestMessages = listOf(
                ChatMessage("system", prompt),
                ChatMessage("user", "请分析上述对话中用户的特征，并与现有画像融合，生成一份更新的用户画像。")
            )

            val request = ChatGptRequest(
                model = ApiConfig.getMemoryModelName(context),  // 使用配置中的模型名
                messages = requestMessages,
                temperature = 0.7
            )

            // 发送请求，使用配置中的API Key
            val apiKey = ApiConfig.getMemoryApiKey(context)
            val response = apiService.generateMemory(
                "Bearer $apiKey",
                request
            )

            if (response.isSuccessful && response.body() != null) {
                val result = "用户画像：" + response.body()!!.choices[0].message.content.toString()
                Log.d(TAG, "用户画像生成成功: ${result.take(100)}...")
                return result
            } else {
                val errorBody = response.errorBody()?.string() ?: "未知错误"
                Log.e(TAG, "用户画像生成失败: $errorBody")
                throw Exception("用户画像生成失败 (${response.code()}): ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "用户画像生成异常: ${e.message}", e)
            // 如果生成失败，返回现有画像或默认画像
            return existingProfile ?: "用户画像：无足够信息"
        }
    }

    /**
     * 构建增强版记忆提示词
     */
    private fun buildEnhancedMemoryPrompt(messages: List<ChatMessage>): String {
        val conversations = messages.joinToString("\n\n") { message ->
            val role = if (message.role == "user") "用户" else "AI"
            "$role：${message.content}"
        }

        return """
            你是一个专业的对话分析和总结助手，擅长从对话中提取关键信息、识别主题和评估重要性。
            
            请遵循以下指南：
            1. 关注对话中的关键事实、数据和重要概念
            2. 识别用户主要的问题或需求
            3. 保留具体的专有名词、数字和重要术语
            4. 评估对话的重要性和未来参考价值
            5. 识别对话的主要类别或主题领域
            6. 捕捉用户的语言习惯和表达方式，这些对于用户画像很有价值
            
            对话内容：
            
            $conversations
        """.trimIndent()
    }

    /**
     * 构建用户画像提示词
     */
    private fun buildUserProfilePrompt(messages: List<ChatMessage>, existingProfile: String? = null): String {
        // 限制消息数量，避免超过模型上下文长度
        val limitedMessages = if (messages.size > 50) {
            messages.takeLast(50)
        } else {
            messages
        }

        val userMessages = limitedMessages.joinToString("\n\n") { message ->
            "用户: ${message.content}"
        }

        val basePrompt = """
            你是一个专业的用户分析助手，擅长从对话中分析用户的特征、兴趣和需求。请分析用户的以下特征：
            
            1. 用户的知识背景和专业领域
            2. 用户关注的主要话题和兴趣点
            3. 用户的表达风格和习惯
            4. 用户可能的生活方式和价值观 
            5. 用户的情感状态和社交关系特点 
            6. 特别注意分析用户的性格态度和表达方式
        """.trimIndent()

        // 如果有现有画像，添加到提示中
        val existingProfilePrompt = if (!existingProfile.isNullOrEmpty()) {
            """
            
            现有的用户画像如下：
            $existingProfile
            
            请融合现有画像和新的观察，更新用户画像。
            - 保留现有画像中仍然相关的重要信息
            - 添加从新消息中发现的新特征
            - 更新可能发生变化的特征
            - 移除过时或不再相关的信息
            - 解决现有画像和新观察之间的冲突
            """.trimIndent()
        } else {
            """
            
            这是首次生成用户画像，请详细分析用户特征。
            """.trimIndent()
        }

        return """
            $basePrompt
            $existingProfilePrompt
            
            生成一份简明的用户画像，用于帮助AI更好地理解用户并提供更加个性化的回复。
            画像应该简洁、准确，捕获用户性格特点和语言习惯，不超过250字。
            分析用户的情感、爱好、诉求、知识水平、关注点，全面构建用户形象。            
            用户消息：
            
            $userMessages
        """.trimIndent()
    }

    /**
     * 记忆生成结果数据类
     */
    data class MemoryGenerationResult(
        val summary: String,
        val category: String,
        val importance: Int,
        val keywords: List<String>
    )

    /**
     * API服务接口
     */
    interface MemoryApiService {
        @POST("chat/completions")
        suspend fun generateMemory(
            @Header("Authorization") authorization: String,
            @Body request: ChatGptRequest
        ): Response<ChatGptResponse>
    }
}
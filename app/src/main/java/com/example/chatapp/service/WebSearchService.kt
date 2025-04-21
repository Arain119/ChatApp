package com.example.chatapp.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

class WebSearchService {
    private val TAG = "WebSearchService"

    // 搜索引擎URLs
    private val GOOGLE_SEARCH_URL = "https://www.google.com/search?q="
    private val BING_SEARCH_URL = "https://www.bing.com/search?q="
    private val DUCKDUCKGO_SEARCH_URL = "https://html.duckduckgo.com/html/?q="

    // 用户代理字符串，模拟移动设备
    private val USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.58 Mobile Safari/537.36"

    // 权威网站域名列表（按权威性排序）
    private val authorityDomains = listOf(
        // 政府网站
        ".gov.cn", ".edu.cn", ".ac.cn", ".org.cn", ".gov",
        // 教育机构
        ".edu",
        // 国际组织
        ".int", ".org",
        // 媒体和机构
        "people.com.cn", "xinhuanet.com", "cctv.com", "chinadaily.com.cn", "cas.cn",
        "sina.com.cn", "sohu.com", "163.com", "ifeng.com", "qq.com", "youth.cn",
        // 科技企业
        "baidu.com", "alibaba.com", "taobao.com", "jd.com", "tencent.com","apple.com",
        "aliyun.com", "huawei.com", "mi.com", "xiaomi.com", "bytedance.com","microsoft.com",
        // 国际知名媒体
        "bbc.co.uk", "nytimes.com", "theguardian.com", "reuters.com", "bloomberg.com",
        "cnn.com", "washingtonpost.com", "economist.com", "time.com",
        // 知名学术网站
        "nature.com", "science.org", "scientificamerican.com", "ieee.org", "mit.edu",
        "stanford.edu", "berkeley.edu", "harvard.edu", "oxford.ac.uk", "cambridge.org",
        // 参考资源
        "wikipedia.org", "stackoverflow.com", "github.com", "zhihu.com", "gitee.com"
    )

    /**
     * 执行网络搜索
     */
    suspend fun search(query: String, searchEngine: String, maxResults: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = when (searchEngine.lowercase()) {
                "google" -> GOOGLE_SEARCH_URL + encodedQuery
                "bing" -> BING_SEARCH_URL + encodedQuery
                "duckduckgo" -> DUCKDUCKGO_SEARCH_URL + encodedQuery
                else -> GOOGLE_SEARCH_URL + encodedQuery
            }

            Log.d(TAG, "执行搜索: $searchUrl，引擎: $searchEngine")

            // 获取HTML文档
            val doc: Document = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .followRedirects(true)
                .get()

            Log.d(TAG, "获取到HTML文档，标题: ${doc.title()}")

            // 根据不同的搜索引擎解析结果
            when (searchEngine.lowercase()) {
                "google" -> parseGoogleResults(doc, results, maxResults)
                "bing" -> parseBingResults(doc, results, maxResults)
                "duckduckgo" -> parseDuckDuckGoResults(doc, results, maxResults)
                else -> parseGoogleResults(doc, results, maxResults)
            }

            // 根据权威性和有用性对结果进行排序
            val sortedResults = sortResultsByAuthority(results)
            results.clear()
            results.addAll(sortedResults)

            Log.d(TAG, "搜索完成，找到 ${results.size} 条结果，已根据权威性和有用性排序")

        } catch (e: IOException) {
            Log.e(TAG, "搜索网络错误: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "搜索过程中出现异常: ${e.message}", e)
        }

        if (results.isEmpty()) {
            // 如果没有结果，添加一个默认结果，避免返回空列表
            results.add(
                SearchResult(
                    title = "搜索结果不可用",
                    url = "https://example.com",
                    description = "无法获取相关搜索结果，请尝试其他搜索引擎或直接查询。",
                    source = searchEngine
                )
            )
            Log.w(TAG, "未能获取搜索结果，返回默认结果")
        }

        results
    }

    /**
     * 根据网站权威性和有用性智能排序搜索结果
     */
    private fun sortResultsByAuthority(results: List<SearchResult>): List<SearchResult> {
        // 为每个结果计算得分并保存
        val scoredResults = results.map { result ->
            // 计算URL、域名和其他数据
            val url = result.url.lowercase(Locale.getDefault())
            val domain = extractDomain(url)
            val title = result.title.lowercase(Locale.getDefault())
            val description = result.description.lowercase(Locale.getDefault())

            // 检查是否为官方网站
            val isOfficial = isOfficialWebsite(url, domain, title, description)

            // 计算得分 (传入是否官方的判断结果)
            val score = calculateAuthorityScore(result, isOfficial)
            Log.d(TAG, "排序: ${result.title.take(30)}... - 得分:$score, URL:${result.url}")

            Pair(result, score)
        }

        // 按照得分从高到低排序
        val sortedResults = scoredResults.sortedByDescending { it.second }

        // 输出排序结果日志
        Log.d(TAG, "排序后的前3个结果:")
        sortedResults.take(3).forEachIndexed { index, (result, score) ->
            Log.d(TAG, "${index+1}. ${result.title.take(30)}... - 得分:$score")
        }

        // 返回排序后的结果列表
        return sortedResults.map { it.first }
    }

    /**
     * 计算网站权威性得分
     * 得分范围: 0-100，得分越高表示越权威
     * @param result 搜索结果
     * @param isOfficial 是否为官方网站
     */
    private fun calculateAuthorityScore(result: SearchResult, isOfficial: Boolean): Int {
        val url = result.url.lowercase(Locale.getDefault())
        val domain = extractDomain(url)
        val title = result.title.lowercase(Locale.getDefault())
        val description = result.description.lowercase(Locale.getDefault())

        // 基础得分
        var score = 50

        // 官方网站检测
        if (isOfficial) {
            score += 45  // 官方网站获得最高加分
            Log.d(TAG, "检测到官方网站 $url, 加分45")
        }

        // 权威性评分
        // 根据预定义的权威域名加分
        for ((index, authorityDomain) in authorityDomains.withIndex()) {
            if (domain.endsWith(authorityDomain)) {
                // 根据列表顺序给不同权重，越靠前的权威域名权重越高
                val authorityScore = 30 - (index / 2)
                score += authorityScore
                Log.d(TAG, "权威域名匹配 $authorityDomain, 加分$authorityScore")
                break
            }
        }

        // 根据顶级域名类型调整分数
        when {
            domain.endsWith(".gov.cn") -> score += 25
            domain.endsWith(".edu.cn") -> score += 20
            domain.endsWith(".ac.cn") -> score += 20
            domain.endsWith(".org.cn") -> score += 15
            domain.endsWith(".gov") -> score += 20
            domain.endsWith(".edu") -> score += 15
            domain.endsWith(".org") -> score += 10
            domain.endsWith(".com.cn") -> score += 10
            domain.endsWith(".com") -> score += 5  // 商业网站基础加分较低
        }

        // 有用性评分
        // 内容完整性评分
        if (description.length > 150) score += 10  // 较长的描述通常更有用
        else if (description.length > 80) score += 5

        // 专业性/内容深度评分 - 优先中文术语
        val professionalTerms = listOf(
            "研究", "报告", "分析", "数据", "白皮书", "论文", "专业", "详细", "全面", "深度",
            "官方", "认证", "权威", "指南", "教程", "手册", "说明", "文档", "攻略", "使用方法",
            "帮助", "常见问题", "问答", "study", "research", "paper", "analysis", "evidence",
            "official", "verified", "guide", "tutorial", "comprehensive", "complete"
        )

        var termMatchCount = 0
        for (term in professionalTerms) {
            if (description.contains(term) || title.contains(term)) {
                termMatchCount++
            }
        }

        // 根据匹配的专业术语数量加分
        score += minOf(termMatchCount * 2, 12) // 最多加12分

        // 实用性检测 - 优先中文指标
        val usefulnessIndicators = listOf(
            "指南", "教程", "攻略", "入门", "说明", "文档", "手册", "常见问题", "问答", "下载", "官网",
            "官方", "使用", "帮助", "支持", "工具", "安装", "配置", "如何", "怎么", "最新", "更新",
            "发布", "how to", "tutorial", "guide", "documentation", "manual", "faq",
            "download", "official", "help", "support"
        )

        for (indicator in usefulnessIndicators) {
            if (url.contains(indicator) || title.contains(indicator) || description.contains(indicator)) {
                score += 8
                Log.d(TAG, "检测到实用性指标 $indicator, 加分8")
                break  // 只加一次分
            }
        }

        // 避免无效结果
        // 检测广告或低质量页面
        val lowQualityIndicators = listOf(
            "广告", "推广", "软文", "优惠", "促销", "打折", "抢购", "特价", "限时", "秒杀", "代理",
            "加盟", "会销", "批发", "ads", "advertisement", "sponsored", "buy now", "discount"
        )

        for (indicator in lowQualityIndicators) {
            if (title.contains(indicator) || description.contains(indicator)) {
                score -= 25  // 广告内容大幅降权
                Log.d(TAG, "检测到低质量指标 $indicator, 减分25")
                break
            }
        }

        // 确保分数在0-100范围内
        return score.coerceIn(0, 100)
    }

    /**
     * 检测URL是否为官方网站
     */
    private fun isOfficialWebsite(url: String, domain: String, title: String, description: String): Boolean {
        // 1. 直接包含"官方"或"official"的情况
        val officialKeywords = listOf(
            "官方网站", "官网", "官方", "官方入口", "官方主页", "官方商城", "官方旗舰店",
            "官方认证", "授权", "认证", "主页", "官方下载", "官方发布", "官方公告",
            "official", "official site", "official website", "verified", "authorized", "homepage"
        )

        for (keyword in officialKeywords) {
            if (title.contains(keyword) || description.contains(keyword)) {
                return true
            }
        }

        // 知名平台的官方页面
        val officialDomains = mapOf(
            "baidu.com" to true,
            "qq.com" to true,
            "163.com" to true,
            "sina.com.cn" to true,
            "weibo.com" to true,
            "taobao.com" to true,
            "tmall.com" to true,
            "jd.com" to true,
            "mi.com" to true,
            "xiaomi.com" to true,
            "huawei.com" to true,
            "zhihu.com" to true,
            "alipay.com" to true,
            "tencent.com" to true,
            "bilibili.com" to true,
            // 国际平台
            "github.com/official" to true,
            "microsoft.com" to true,
            "apple.com" to true,
            "google.com" to true
        )

        for ((officialDomain, _) in officialDomains) {
            if (domain.contains(officialDomain)) {
                return true
            }
        }

        // 简洁域名更可能是官网
        // 排除常见的非官方域名特征
        val nonOfficialPatterns = listOf(
            "blog\\.", "forum\\.", "bbs\\.", "support\\.", "help\\.", "ask\\.", "zhidao\\.",
            "review", "comparison", "vs", "forum", "community", "fans", "tieba", "tiezi"
        )

        var isLikelyOfficial = true
        for (pattern in nonOfficialPatterns) {
            if (Regex(pattern).containsMatchIn(domain)) {
                isLikelyOfficial = false
                break
            }
        }

        // 简短的二级域名更可能是官网
        if (isLikelyOfficial && domain.split(".").first().length <= 10) {
            // 检查URL路径是否简短(首页或一级目录)
            val pathDepth = url.split("/").size - 3 // http://domain.com/ = 深度0
            if (pathDepth <= 1) {
                return true
            }
        }

        return false
    }

    /**
     * 从URL中提取域名
     */
    private fun extractDomain(url: String): String {
        val domainPattern = Regex("https?://([^/]+)")
        val matchResult = domainPattern.find(url)
        return matchResult?.groupValues?.getOrNull(1) ?: url
    }

    /**
     * 解析Google搜索结果
     */
    private fun parseGoogleResults(doc: Document, results: MutableList<SearchResult>, maxResults: Int) {
        try {
            // 尝试多种可能的选择器
            val selectors = listOf(
                "div.g",
                "div.xpd",
                "div[data-hveid]",
                "div.mnr-c"
            )

            var found = false
            for (selector in selectors) {
                val elements = doc.select(selector)
                Log.d(TAG, "Google选择器 '$selector' 找到 ${elements.size} 个元素")

                if (elements.isNotEmpty()) {
                    for (element in elements) {
                        if (results.size >= maxResults) break

                        // 修复空安全问题
                        val titleElement = element.selectFirst("h3")
                            ?: element.selectFirst(".DKV0Md")
                            ?: element.selectFirst(".vvjwJb")

                        val linkElement = element.selectFirst("a[href]")

                        val descElement = element.selectFirst(".VwiC3b")
                            ?: element.selectFirst(".s3v9rd")
                            ?: element.selectFirst(".yXK7lf")

                        if (titleElement != null && linkElement != null) {
                            val title = titleElement.text()
                            val url = if (linkElement.hasAttr("href")) linkElement.attr("href") else "#"
                            val description = descElement?.text() ?: "无描述"

                            if (url.startsWith("http") && title.isNotEmpty()) {
                                results.add(
                                    SearchResult(
                                        title = title,
                                        url = url,
                                        description = description,
                                        source = "Google"
                                    )
                                )
                                found = true
                            }
                        }
                    }

                    if (found) break
                }
            }

            if (!found) {
                // 如果尝试所有选择器后仍未找到结果，尝试直接从页面提取内容
                Log.d(TAG, "Google搜索结果解析失败，尝试提取页面主要内容")
                val mainContent = doc.select("body").text()
                if (mainContent.isNotEmpty()) {
                    results.add(
                        SearchResult(
                            title = "Google搜索: ${doc.title()}",
                            url = "https://www.google.com",
                            description = mainContent.take(200) + "...",
                            source = "Google"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析Google结果时出错: ${e.message}", e)
        }
    }

    /**
     * 解析Bing搜索结果
     */
    private fun parseBingResults(doc: Document, results: MutableList<SearchResult>, maxResults: Int) {
        try {
            // 尝试多种可能的选择器
            val selectors = listOf(
                "li.b_algo",
                ".b_algo",
                ".b_results > li",
                "li.b_ans"
            )

            var found = false
            for (selector in selectors) {
                val elements = doc.select(selector)
                Log.d(TAG, "Bing选择器 '$selector' 找到 ${elements.size} 个元素")

                if (elements.isNotEmpty()) {
                    for (element in elements) {
                        if (results.size >= maxResults) break

                        val titleElement = element.selectFirst("h2 a")
                            ?: element.selectFirst("a.title")
                            ?: element.selectFirst("a strong")

                        val descElement = element.selectFirst("p")
                            ?: element.selectFirst(".b_caption")
                            ?: element.selectFirst(".b_snippet")

                        if (titleElement != null) {
                            val title = titleElement.text()
                            val url = titleElement.attr("href")
                            val description = descElement?.text() ?: "无描述"

                            if (title.isNotEmpty()) {
                                results.add(
                                    SearchResult(
                                        title = title,
                                        url = if (url.startsWith("http")) url else "https://www.bing.com$url",
                                        description = description,
                                        source = "Bing"
                                    )
                                )
                                found = true
                            }
                        }
                    }

                    if (found) break
                }
            }

            if (!found) {
                // 直接寻找Bing的搜索结果框
                val bingAnswers = doc.select(".b_antiTopBleed, .b_entityTP, .b_prominentFactor")
                if (bingAnswers.isNotEmpty()) {
                    val answerElement = bingAnswers.first()
                    val answerText = answerElement?.text() ?: "" // 修复空安全问题
                    if (answerText.isNotEmpty()) {
                        results.add(
                            SearchResult(
                                title = "Bing搜索结果",
                                url = "https://www.bing.com",
                                description = answerText.take(200) + "...",
                                source = "Bing"
                            )
                        )
                        found = true
                    }
                }
            }

            if (!found) {
                // 如果尝试所有选择器后仍未找到结果，尝试直接从页面提取内容
                Log.d(TAG, "Bing搜索结果解析失败，尝试提取页面主要内容")
                val mainContent = doc.select("body").text()
                if (mainContent.isNotEmpty()) {
                    results.add(
                        SearchResult(
                            title = "Bing搜索: ${doc.title()}",
                            url = "https://www.bing.com",
                            description = mainContent.take(200) + "...",
                            source = "Bing"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析Bing结果时出错: ${e.message}", e)
        }
    }

    /**
     * 解析DuckDuckGo搜索结果
     */
    private fun parseDuckDuckGoResults(doc: Document, results: MutableList<SearchResult>, maxResults: Int) {
        try {
            val resultElements = doc.select(".result")
            Log.d(TAG, "DuckDuckGo找到 ${resultElements.size} 个结果元素")

            if (resultElements.isNotEmpty()) {
                for (element in resultElements) {
                    if (results.size >= maxResults) break

                    val titleElement = element.selectFirst(".result__a")
                    val descElement = element.selectFirst(".result__snippet")

                    if (titleElement != null) {
                        val title = titleElement.text()
                        val url = titleElement.attr("href")
                        val description = descElement?.text() ?: "无描述"

                        results.add(
                            SearchResult(
                                title = title,
                                url = url,
                                description = description,
                                source = "DuckDuckGo"
                            )
                        )
                    }
                }
            } else {
                // 尝试其他选择器
                val otherElements = doc.select(".web-result")
                Log.d(TAG, "DuckDuckGo替代选择器找到 ${otherElements.size} 个元素")

                for (element in otherElements) {
                    if (results.size >= maxResults) break

                    val titleElement = element.selectFirst("a.result__a")
                    val url = element.selectFirst("a.result__url")?.attr("href") ?: ""
                    val description = element.selectFirst(".result__snippet")?.text() ?: "无描述"

                    if (titleElement != null) {
                        results.add(
                            SearchResult(
                                title = titleElement.text(),
                                url = url,
                                description = description,
                                source = "DuckDuckGo"
                            )
                        )
                    }
                }
            }

            if (results.isEmpty()) {
                // 如果两种选择器都未找到结果，提取页面主要内容
                Log.d(TAG, "DuckDuckGo搜索结果解析失败，尝试提取页面主要内容")
                val mainContent = doc.select("body").text()
                if (mainContent.isNotEmpty()) {
                    results.add(
                        SearchResult(
                            title = "DuckDuckGo搜索: ${doc.title()}",
                            url = "https://duckduckgo.com",
                            description = mainContent.take(200) + "...",
                            source = "DuckDuckGo"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析DuckDuckGo结果时出错: ${e.message}", e)
        }
    }

}
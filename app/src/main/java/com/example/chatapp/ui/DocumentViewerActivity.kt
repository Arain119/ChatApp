package com.example.chatapp.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.net.Uri
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.os.Build
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import com.example.chatapp.R
import com.example.chatapp.utils.DocumentProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * 文档查看器Activity
 * 用于显示上传的文档内容
 */
class DocumentViewerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var errorTextView: TextView
    private lateinit var documentProcessor: DocumentProcessor

    companion object {
        private const val TAG = "DocumentViewerActivity"
        private const val EXTRA_DOCUMENT_URI = "EXTRA_DOCUMENT_URI"
        private const val EXTRA_DOCUMENT_TITLE = "EXTRA_DOCUMENT_TITLE"

        // 文档类型常量
        private const val DOC_TYPE_WORD = "word"
        private const val DOC_TYPE_EXCEL = "excel"
        private const val DOC_TYPE_POWERPOINT = "powerpoint"
        private const val DOC_TYPE_PDF = "pdf"
        private const val DOC_TYPE_CODE = "code"
        private const val DOC_TYPE_TEXT = "text"
        private const val DOC_TYPE_MARKDOWN = "markdown"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_viewer)

        // 初始化文档处理器
        documentProcessor = DocumentProcessor(this)

        // 获取传入参数
        val documentUriString = intent.getStringExtra(EXTRA_DOCUMENT_URI)
        val documentTitle = intent.getStringExtra(EXTRA_DOCUMENT_TITLE) ?: "文档查看器"

        // 初始化视图
        initializeViews(documentTitle)

        if (documentUriString != null) {
            val documentUri = Uri.parse(documentUriString)
            loadDocument(documentUri)
        } else {
            showError("无效的文档URI")
        }
    }

    /**
     * 初始化视图
     */
    private fun initializeViews(documentTitle: String) {
        // 设置Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)

            // 从文件名中提取实际标题
            title = cleanupDocumentTitle(documentTitle)
        }

        // 初始化WebView
        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = false // 出于安全考虑禁用JavaScript
            builtInZoomControls = true // 启用内置缩放控件
            displayZoomControls = false // 隐藏缩放按钮
            setSupportZoom(true) // 支持缩放
            useWideViewPort = true // 支持viewport
            loadWithOverviewMode = true // 自适应屏幕
            defaultTextEncodingName = "UTF-8" // 设置编码
            textZoom = 100 // 标准文本尺寸

            // 设置更好的缓存模式
            cacheMode = WebSettings.LOAD_DEFAULT
            domStorageEnabled = true

            // 改善渲染性能
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = false // 不需要安全浏览特性
            }

            // 设置混合内容模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }

        // 启用WebView调试（仅在适当的SDK版本上）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // 设置WebViewClient进行错误处理
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                Log.e(TAG, "WebView加载错误: 码: ${error.errorCode}, URL: ${request.url}")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.e(TAG, "错误描述: ${error.description}")
                    showError("加载错误(${error.errorCode}): ${error.description}")
                } else {
                    showError("加载错误，请检查文档格式")
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.d(TAG, "页面加载完成: $url")

                // 平滑显示页面内容
                animateContentDisplay()
            }
        }

        // 进度条和错误视图
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        errorTextView = findViewById(R.id.errorTextView)

        // 设置自定义加载动画
        setupLoadingAnimation()
    }

    /**
     * 设置加载进度条的动画
     */
    private fun setupLoadingAnimation() {
        val animator = ObjectAnimator.ofFloat(loadingProgressBar, "rotation", 0f, 360f)
        animator.duration = 1000
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.RESTART
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
    }

    /**
     * 平滑显示内容的动画
     */
    private fun animateContentDisplay() {
        // 平滑隐藏加载进度条
        loadingProgressBar.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                loadingProgressBar.visibility = View.GONE
            }
            .start()

        // 平滑显示WebView
        webView.alpha = 0f
        webView.visibility = View.VISIBLE
        webView.animate()
            .alpha(1f)
            .setDuration(400)
            .start()
    }

    /**
     * 清理文档标题，移除UUID和时间戳
     */
    private fun cleanupDocumentTitle(rawTitle: String): String {
        // 尝试匹配UUID和时间戳模式
        val uuidPattern = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        val timestampPattern = Pattern.compile("_\\d{13,}_")

        var cleanTitle = rawTitle

        // 处理UUID格式
        val uuidMatcher = uuidPattern.matcher(cleanTitle)
        if (uuidMatcher.find()) {
            // 找到UUID后的部分
            val startIdx = uuidMatcher.end()
            if (startIdx < cleanTitle.length) {
                cleanTitle = cleanTitle.substring(startIdx)
            }
        }

        // 处理时间戳
        val timestampMatcher = timestampPattern.matcher(cleanTitle)
        if (timestampMatcher.find()) {
            val endIdx = timestampMatcher.end()
            if (endIdx < cleanTitle.length) {
                cleanTitle = cleanTitle.substring(endIdx)
            }
        }

        // 如果仍有多余前缀，尝试找到最后一个下划线或斜杠后的内容
        val lastUnderscore = cleanTitle.lastIndexOf('_')
        if (lastUnderscore > 0 && lastUnderscore < cleanTitle.length - 1) {
            cleanTitle = cleanTitle.substring(lastUnderscore + 1)
        }

        return cleanTitle
    }

    /**
     * 加载文档
     */
    private fun loadDocument(documentUri: Uri) {
        loadingProgressBar.visibility = View.VISIBLE
        webView.visibility = View.GONE
        errorTextView.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 使用DocumentProcessor提取文本内容
                val documentText = withContext(Dispatchers.IO) {
                    documentProcessor.extractTextFromDocument(documentUri)
                }

                if (documentText.isNullOrEmpty()) {
                    showError("无法读取文档内容")
                    return@launch
                }

                // 获取清理后的文件名
                val rawFileName = documentProcessor.getFileName(documentUri)
                val cleanFileName = cleanupDocumentTitle(rawFileName)

                // 根据文档类型和内容生成适当的HTML
                val html = generateEnhancedHtmlContent(documentUri, documentText, cleanFileName)

                // 直接使用loadDataWithBaseURL加载HTML内容
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)

            } catch (e: Exception) {
                Log.e(TAG, "加载文档失败: ${e.message}", e)
                showError("加载文档失败: ${e.message}")
            }
        }
    }

    /**
     * 检测文档类型并返回Office类型标识符
     */
    private fun detectOfficeDocType(mimeType: String, fileName: String): String {
        return when {
            // Word类文档
            mimeType.contains("wordprocessing") ||
                    fileName.endsWith(".doc", true) ||
                    fileName.endsWith(".docx", true) ||
                    fileName.endsWith(".rtf", true) -> DOC_TYPE_WORD

            // Excel类文档
            mimeType.contains("spreadsheet") ||
                    mimeType.contains("excel") ||
                    fileName.endsWith(".xls", true) ||
                    fileName.endsWith(".xlsx", true) ||
                    fileName.endsWith(".csv", true) -> DOC_TYPE_EXCEL

            // PowerPoint类文档
            mimeType.contains("presentation") ||
                    fileName.endsWith(".ppt", true) ||
                    fileName.endsWith(".pptx", true) -> DOC_TYPE_POWERPOINT

            // Markdown文档
            fileName.endsWith(".md", true) ||
                    fileName.endsWith(".markdown", true) -> DOC_TYPE_MARKDOWN

            // 文本文档
            mimeType.contains("text/plain") ||
                    fileName.endsWith(".txt", true) -> DOC_TYPE_TEXT

            // PDF文档
            mimeType.contains("pdf") ||
                    fileName.endsWith(".pdf", true) -> DOC_TYPE_PDF

            // 代码类文档
            mimeType.contains("json") ||
                    fileName.endsWith(".json", true) ||
                    fileName.endsWith(".xml", true) ||
                    fileName.endsWith(".html", true) ||
                    fileName.endsWith(".css", true) ||
                    fileName.endsWith(".js", true) ||
                    fileName.endsWith(".java", true) ||
                    fileName.endsWith(".py", true) ||
                    fileName.endsWith(".c", true) ||
                    fileName.endsWith(".cpp", true) ||
                    fileName.endsWith(".cs", true) -> DOC_TYPE_CODE

            // 其他文档默认为普通文本
            else -> DOC_TYPE_TEXT
        }
    }

    /**
     * 获取文档主题颜色
     */
    private fun getDocumentThemeColor(docType: String): String {
        return when(docType) {
            DOC_TYPE_WORD -> "#2B579A" // Word蓝色
            DOC_TYPE_EXCEL -> "#217346" // Excel绿色
            DOC_TYPE_POWERPOINT -> "#D24726" // PowerPoint橙红色
            DOC_TYPE_PDF -> "#F40F02" // Adobe PDF红色
            DOC_TYPE_CODE -> "#0078D7" // Visual Studio蓝色
            DOC_TYPE_MARKDOWN -> "#764ABC" // Markdown紫色
            else -> "#0078D7" // Office蓝色
        }
    }

    /**
     * 获取文档图标
     */
    private fun getDocumentIconSvg(docType: String): String {
        // 使用inline SVG图标替代Unicode字符，提供更精美的图标
        return when(docType) {
            DOC_TYPE_WORD -> """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="100%" height="100%" fill="white">
                    <path d="M19.5,3H4.5C3.12,3,2,4.12,2,5.5v13C2,19.88,3.12,21,4.5,21h15c1.38,0,2.5-1.12,2.5-2.5v-13C22,4.12,20.88,3,19.5,3z M15.42,16.28H14.3l-1.05-4.12l-1.01,4.12h-1.11l-1.68-8.68h1.4l1.08,5.38l1.14-5.38h0.95l1.17,5.57l1.14-5.57h1.31L15.42,16.28z"/>
                </svg>
            """
            DOC_TYPE_EXCEL -> """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="100%" height="100%" fill="white">
                    <path d="M19.5,3H4.5C3.12,3,2,4.12,2,5.5v13C2,19.88,3.12,21,4.5,21h15c1.38,0,2.5-1.12,2.5-2.5v-13C22,4.12,20.88,3,19.5,3z M9.37,16.28H7.97L6.09,13.62L4.21,16.28H2.91L5.44,12.9L3.07,9.62H4.51L6.11,12.07L7.84,9.62H9.15L6.73,12.9L9.37,16.28z"/>
                </svg>
            """
            DOC_TYPE_POWERPOINT -> """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="100%" height="100%" fill="white">
                    <path d="M19.5,3H4.5C3.12,3,2,4.12,2,5.5v13C2,19.88,3.12,21,4.5,21h15c1.38,0,2.5-1.12,2.5-2.5v-13C22,4.12,20.88,3,19.5,3z M13.75,15.11C13.02,15.55,12.02,15.75,10.9,15.75H9.48v2.17H7.73V8.45h3.61c0.98,0,1.83,0.31,2.53,0.83c0.69,0.53,1.14,1.28,1.14,2.4C15.01,13.36,14.47,14.66,13.75,15.11z M10.9,10.01h-1.42v4.15h1.17c1.38,0,2.35-0.5,2.35-2.17C13,10.54,12.14,10.01,10.9,10.01z"/>
                </svg>
            """
            DOC_TYPE_PDF -> """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="100%" height="100%" fill="white">
                    <path d="M20,2H8C6.9,2,6,2.9,6,4v12c0,1.1,0.9,2,2,2h12c1.1,0,2-0.9,2-2V4C22,2.9,21.1,2,20,2z M11.5,13.5c0,0.83-0.67,1.5-1.5,1.5H9v1.25c0,0.41-0.34,0.75-0.75,0.75S7.5,16.66,7.5,16.25V8c0-0.55,0.45-1,1-1h1.5c0.83,0,1.5,0.67,1.5,1.5V13.5z M16.5,16.25c0,0.41-0.34,0.75-0.75,0.75H14c-0.55,0-1-0.45-1-1V8c0-0.55,0.45-1,1-1h1.75c0.41,0,0.75,0.34,0.75,0.75l0,0c0,0.41-0.34,0.75-0.75,0.75H14.5v2h1.25c0.41,0,0.75,0.34,0.75,0.75l0,0c0,0.41-0.34,0.75-0.75,0.75H14.5v2.5h1.25C16.16,15.5,16.5,15.84,16.5,16.25z M3,6c-0.55,0-1,0.45-1,1v13c0,1.1,0.9,2,2,2h13c0.55,0,1-0.45,1-1s-0.45-1-1-1H5c-0.55,0-1-0.45-1-1V7C4,6.45,3.55,6,3,6z"/>
                </svg>
            """
            DOC_TYPE_CODE -> """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="100%" height="100%" fill="white">
                    <path d="M9.4,16.6L4.8,12l4.6-4.6L8,6l-6,6l6,6L9.4,16.6z M14.6,16.6l4.6-4.6l-4.6-4.6L16,6l6,6l-6,6L14.6,16.6z"/>
                </svg>
            """
            DOC_TYPE_MARKDOWN -> """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="100%" height="100%" fill="white">
                    <path d="M20.56 5.51H3.45C2.65 5.51 2 6.16 2 6.96v10.09c0 .8.65 1.44 1.45 1.44h17.11c.8 0 1.44-.65 1.44-1.44V6.96c0-.8-.65-1.45-1.44-1.45zm-3.18 10.3h-2.48v-3.59l-1.9 2.45-1.9-2.45v3.59H8.62V8.19h2.48l1.9 2.35 1.9-2.35h2.48v7.62z"/>
                </svg>
            """
            else -> """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="100%" height="100%" fill="white">
                    <path d="M14,2H6C4.9,2,4,2.9,4,4v16c0,1.1,0.9,2,2,2h12c1.1,0,2-0.9,2-2V8L14,2z M16,18H8v-2h8V18z M16,14H8v-2h8V14z M13,9V3.5L18.5,9H13z"/>
                </svg>
            """
        }
    }

    /**
     * 生成HTML内容
     */
    private fun generateEnhancedHtmlContent(documentUri: Uri, documentText: String, cleanFileName: String): String {
        val mimeType = documentProcessor.getDocumentType(documentUri)
        val fileName = documentProcessor.getFileName(documentUri)
        val docType = detectOfficeDocType(mimeType, fileName)
        val themeColor = getDocumentThemeColor(docType)

        // 动态计算次要颜色（变暗20%）
        val themeColorInt = Color.parseColor(themeColor)
        val darkThemeColorInt = ColorUtils.blendARGB(themeColorInt, Color.BLACK, 0.2f)
        val darkThemeColor = String.format("#%06X", 0xFFFFFF and darkThemeColorInt)

        // 明亮的主题颜色（变亮15%）用于高亮
        val lightThemeColorInt = ColorUtils.blendARGB(themeColorInt, Color.WHITE, 0.15f)
        val lightThemeColor = String.format("#%06X", 0xFFFFFF and lightThemeColorInt)

        // 获取SVG图标
        val docIconSvg = getDocumentIconSvg(docType)

        // 获取文件创建时间
        val fileCreationTime = getFileCreationTime(documentUri)

        // 格式化文本内容
        val formattedContent = formatDocumentContent(documentText, docType)

        // 现代化HTML模板
        return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>${cleanFileName}</title>
        <style>
            body {
                font-family: 'Segoe UI', Arial, sans-serif;
                line-height: 1.6;
                color: #333;
                margin: 0;
                padding: 0;
                background-color: #f9f9f9;
            }
            .document-container {
                max-width: 900px;
                margin: 0 auto;
                background-color: white;
                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                min-height: 100vh;
                border-radius: 16px;
                overflow: hidden;
            }
            .document-header {
                background-color: ${themeColor};
                color: white;
                padding: 20px 30px;
                position: relative;
                border-bottom-left-radius: 16px; 
                border-bottom-right-radius: 16px;
            }
            .title-container {
                display: flex;
                align-items: center;
                padding-top: 22px; 
            }
            .document-icon {
                margin-right: 15px;
                width: 40px;
                height: 40px;
            }
            .document-title {
                font-size: 24px; 
                font-weight: 600;
                margin: 0;
            }
            .document-content {
                padding: 30px;
                overflow-x: auto;
            }
            .document-footer {
                text-align: center;
                font-size: 12px;
                color: #666;
                padding: 15px;
                border-top: 1px solid #eee;
                display: flex;
                justify-content: space-between;
                align-items: center;
            }
            table {
                width: 100%;
                border-collapse: collapse;
                margin: 20px 0;
                border-radius: 12px; 
                overflow: hidden;
            }
            th {
                background-color: ${lightThemeColor};
                color: #333;
                font-weight: 600;
                text-align: left;
            }
            th, td {
                padding: 10px;
                border: 1px solid #e0e0e0;
            }
            tr:nth-child(even) {
                background-color: #f8f8f8;
            }
            .metadata-container {
                background-color: #f5f5f5;
                border-radius: 12px; 
                padding: 16px;
                margin-bottom: 24px;
            }
            /* 自适应表格布局 */
            @media (max-width: 600px) {
                table {
                    display: block;
                    overflow-x: auto;
                    white-space: nowrap;
                }
                th, td {
                    min-width: 120px;
                }
            }
            /* 暗色模式支持 */
            @media (prefers-color-scheme: dark) {
                body {
                    background-color: #121212;
                    color: #e0e0e0;
                }
                .document-container {
                    background-color: #1e1e1e;
                }
                .document-header {
                    background-color: ${darkThemeColor};
                }
                .document-content {
                    color: #e0e0e0;
                }
                .document-footer {
                    color: #aaa;
                    border-top: 1px solid #333;
                }
                table {
                    border-color: #444;
                }
                th {
                    background-color: ${themeColor};
                    color: white;
                }
                td {
                    border-color: #444;
                }
                tr:nth-child(even) {
                    background-color: #2a2a2a;
                }
                .metadata-container {
                    background-color: #2a2a2a;
                }
            }
        </style>
    </head>
    <body>
        <div class="document-container">
            <header class="document-header">
                <div class="title-container">
                    <div class="document-icon">
                        ${docIconSvg}
                    </div>
                    <h1 class="document-title">${cleanFileName}</h1>
                </div>
                <!-- 已移除document-info部分 -->
            </header>
            
            <main class="document-content">
                ${formattedContent}
            </main>
            
            <footer class="document-footer">
                <span>Alice文档查看器</span>
                <span>${fileCreationTime}</span>
            </footer>
        </div>
    </body>
    </html>
    """
    }

    /**
     * 尝试获取文件的创建时间
     */
    private fun getFileCreationTime(uri: Uri): String {
        try {
            val cursor = contentResolver.query(uri,
                arrayOf(android.provider.MediaStore.MediaColumns.DATE_ADDED),
                null, null, null)

            cursor?.use {
                if (it.moveToFirst()) {
                    val dateAdded = it.getLong(0) * 1000 // 转换为毫秒
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    return dateFormat.format(Date(dateAdded))
                }
            }

            // 尝试通过文件路径获取
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    return dateFormat.format(Date(file.lastModified()))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取文件创建时间失败: ${e.message}", e)
        }

        // 默认返回当前时间
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return dateFormat.format(Date())
    }

    /**
     * 格式化文档内容，根据文档类型应用不同的处理
     */
    private fun formatDocumentContent(content: String, docType: String): String {
        // 根据文档类型执行不同的内容处理
        return when(docType) {
            DOC_TYPE_CODE -> formatCodeContent(content)
            DOC_TYPE_EXCEL -> formatExcelContent(content)
            DOC_TYPE_PDF -> formatPdfContent(content)
            DOC_TYPE_MARKDOWN -> formatMarkdownContent(content)
            DOC_TYPE_POWERPOINT -> formatPowerPointContent(content)
            DOC_TYPE_WORD -> formatWordContent(content)
            else -> content.replace("\n", "<br>")
                .replace("  ", "&nbsp;&nbsp;")
        }
    }

    /**
     * 格式化代码内容，添加语法高亮和行号
     */
    private fun formatCodeContent(content: String): String {
        val lines = content.split("\n")
        val htmlBuilder = StringBuilder()

        // 遍历所有行，应用行号和基本语法高亮
        lines.forEachIndexed { index, line ->
            // 转义HTML实体
            val escapedLine = line.replace("<", "&lt;")
                .replace(">", "&gt;")

            // 应用基本语法高亮
            val highlightedLine = escapedLine
                // 高亮关键字
                .replace(
                    Regex("\\b(function|return|if|else|for|while|class|var|let|const|import|export|from|extends|implements|interface|private|public|protected|static|async|await|try|catch|finally|throw|new|this|super|null|undefined|true|false)\\b"),
                    "<span style='color:#569CD6;'>$1</span>"
                )
                // 高亮字符串
                .replace(
                    Regex("(\"[^\"]*\"|'[^']*')"),
                    "<span style='color:#CE9178;'>$1</span>"
                )
                // 高亮注释
                .replace(
                    Regex("(//.*)"),
                    "<span style='color:#6A9955;'>$1</span>"
                )
                // 高亮数字
                .replace(
                    Regex("\\b(\\d+\\.?\\d*)\\b"),
                    "<span style='color:#B5CEA8;'>$1</span>"
                )

            htmlBuilder.append("<div style='display:flex;'>")
            htmlBuilder.append("<span style='color:#858585;min-width:36px;text-align:right;margin-right:12px;user-select:none;'>${index + 1}</span>")
            htmlBuilder.append(highlightedLine)
            htmlBuilder.append("</div>")
        }

        return "<pre style='background-color:#1E1E1E;color:#D4D4D4;padding:15px;border-radius:5px;overflow-x:auto;'>${htmlBuilder}</pre>"
    }

    /**
     * 格式化Excel内容，生成表格
     */
    private fun formatExcelContent(content: String): String {
        // 如果内容已包含HTML表格标签，则不处理
        if (content.contains("<table>")) {
            return content
        }

        val lines = content.split("\n")
        val htmlBuilder = StringBuilder()

        // 识别可能的元数据行（通常在开头）
        val metadataLines = mutableListOf<String>()
        var dataStartIndex = 0

        // 遍历开头几行，尝试识别元数据
        for (i in lines.indices) {
            val line = lines[i].trim()
            // 检查是否为元数据行：通常包含冒号或特定标记，或者是"工作表"相关信息
            if ((line.contains(":") && !line.contains("\t")) ||
                line.startsWith("文档信息") ||
                line.startsWith("作者") ||
                line.contains("工作表")) {
                metadataLines.add(line)
                dataStartIndex = i + 1
            } else if (i >= 1 && line.isNotEmpty() && !metadataLines.isEmpty()) {
                // 找到非元数据行且已有元数据，结束搜索
                break
            }
        }

        // 如果找到元数据，以横向方式展示
        if (metadataLines.isNotEmpty()) {
            htmlBuilder.append("<div class='metadata-container'>")
            htmlBuilder.append("<div style='display:flex;flex-wrap:wrap;gap:15px;'>")

            // 处理每一条元数据
            for (metadata in metadataLines) {
                // 分割键值对
                if (metadata.contains(":")) {
                    val parts = metadata.split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()

                        htmlBuilder.append("<div style='margin-right:20px;margin-bottom:8px;'>")
                        htmlBuilder.append("<strong>${key}:</strong> ${value}")
                        htmlBuilder.append("</div>")
                    } else {
                        // 如果无法分割，就直接显示整行
                        htmlBuilder.append("<div style='margin-right:20px;margin-bottom:8px;'>${metadata}</div>")
                    }
                } else {
                    // 没有冒号的元数据行
                    htmlBuilder.append("<div style='margin-right:20px;margin-bottom:8px;'><strong>${metadata}</strong></div>")
                }
            }

            htmlBuilder.append("</div></div>")
        }

        // 处理实际数据表格部分
        // 跳过已处理的元数据行
        val dataLines = lines.subList(dataStartIndex, lines.size).filter { it.trim().isNotEmpty() }

        if (dataLines.isNotEmpty()) {
            // 判断是否有标题行
            val hasHeader = dataLines.size > 1

            htmlBuilder.append("<table>")

            dataLines.forEachIndexed { index, line ->
                // 使用tab、逗号、分号或竖线作为单元格分隔符
                val cells = line.split("\t", ",", ";", "|")

                if (cells.isNotEmpty()) {
                    htmlBuilder.append("<tr>")

                    if (index == 0 && hasHeader) {
                        // 第一行作为表头
                        for (cell in cells) {
                            htmlBuilder.append("<th>${cell.trim()}</th>")
                        }
                    } else {
                        // 普通数据行
                        for (cell in cells) {
                            htmlBuilder.append("<td>${cell.trim()}</td>")
                        }
                    }

                    htmlBuilder.append("</tr>")
                }
            }

            htmlBuilder.append("</table>")
        } else if (metadataLines.isEmpty()) {
            // 如果没有元数据也没有数据行，显示原始内容
            htmlBuilder.append("<pre>${content}</pre>")
        }

        return htmlBuilder.toString()
    }

    /**
     * 格式化PDF内容，添加分页标记
     */
    private fun formatPdfContent(content: String): String {
        val htmlBuilder = StringBuilder()

        // 检测可能的页面分隔
        val pageBreakPattern = Pattern.compile("\\n{3,}")
        val pages = pageBreakPattern.split(content)

        pages.forEachIndexed { index, page ->
            htmlBuilder.append(formatWordContent(page))

            // 添加页面分隔符（除了最后一页）
            if (index < pages.size - 1) {
                htmlBuilder.append("<div style='border-top:1px dashed #ccc; margin:20px 0; page-break-after:always;'></div>")
            }
        }

        return htmlBuilder.toString()
    }

    /**
     * 格式化Markdown内容，应用简单的Markdown语法
     */
    private fun formatMarkdownContent(content: String): String {
        var html = content

        // 转义HTML实体
        html = html.replace("<", "&lt;").replace(">", "&gt;")

        // 处理标题（#, ##, ###）
        html = html.replace(Regex("^\\s*#\\s+(.+)$", RegexOption.MULTILINE), "<h1>$1</h1>")
            .replace(Regex("^\\s*##\\s+(.+)$", RegexOption.MULTILINE), "<h2>$1</h2>")
            .replace(Regex("^\\s*###\\s+(.+)$", RegexOption.MULTILINE), "<h3>$1</h3>")

        // 处理粗体和斜体
        html = html.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
            .replace(Regex("_(.+?)_"), "<em>$1</em>")

        // 处理行内代码
        html = html.replace(Regex("`([^`]+)`"), "<code>$1</code>")

        // 处理链接
        html = html.replace(Regex("\\[(.+?)\\]\\((.+?)\\)"), "<a href='$2'>$1</a>")

        // 处理图片
        html = html.replace(Regex("!\\[(.+?)\\]\\((.+?)\\)"), "<img src='$2' alt='$1' style='max-width:100%;'>")

        // 处理引用
        html = html.replace(Regex("^\\s*>\\s+(.+)$", RegexOption.MULTILINE), "<blockquote>$1</blockquote>")

        // 处理分割线
        html = html.replace(Regex("^\\s*-{3,}\\s*$", RegexOption.MULTILINE), "<hr>")

        // 处理段落 - 双换行作为段落分隔
        html = html.replace(Regex("\n\n"), "</p><p>")

        // 包装在段落标签中
        html = "<p>" + html + "</p>"

        // 修复可能的嵌套段落问题
        html = html.replace("<p><h1>", "<h1>")
            .replace("</h1></p>", "</h1>")
            .replace("<p><h2>", "<h2>")
            .replace("</h2></p>", "</h2>")
            .replace("<p><h3>", "<h3>")
            .replace("</h3></p>", "</h3>")
            .replace("<p><blockquote>", "<blockquote>")
            .replace("</blockquote></p>", "</blockquote>")

        return html
    }

    /**
     * 格式化PowerPoint内容，添加幻灯片样式
     */
    private fun formatPowerPointContent(content: String): String {
        val htmlBuilder = StringBuilder()

        // 检测可能的幻灯片分隔
        val slideBreakPattern = Pattern.compile("\\n{3,}")
        val slides = slideBreakPattern.split(content)

        slides.forEachIndexed { index, slide ->
            val slideContent = slide.trim()

            if (slideContent.isNotEmpty()) {
                // 尝试识别标题和内容
                val lines = slideContent.split("\n")

                if (lines.isNotEmpty()) {
                    // 第一行作为标题
                    htmlBuilder.append("<div style='margin-bottom:30px;'>")
                    htmlBuilder.append("<h2 style='color:#D24726;font-size:22px;margin-bottom:15px;'>${lines[0]}</h2>")

                    // 其余行作为内容
                    if (lines.size > 1) {
                        val slideBody = lines.subList(1, lines.size).joinToString("\n")

                        // 处理无序列表项
                        if (slideBody.contains(Regex("^\\s*[•\\-\\*]", RegexOption.MULTILINE))) {
                            val listItems = slideBody.split(Regex("\n(?=\\s*[•\\-\\*])"))

                            htmlBuilder.append("<ul style='margin-left:20px;'>")
                            for (item in listItems) {
                                val cleanItem = item.trim().replaceFirst(Regex("^[•\\-\\*]\\s*"), "")
                                htmlBuilder.append("<li style='margin-bottom:8px;'>${cleanItem}</li>")
                            }
                            htmlBuilder.append("</ul>")
                        }
                        // 处理有序列表项
                        else if (slideBody.contains(Regex("^\\s*\\d+\\.", RegexOption.MULTILINE))) {
                            val listItems = slideBody.split(Regex("\n(?=\\s*\\d+\\.)"))

                            htmlBuilder.append("<ol style='margin-left:20px;'>")
                            for (item in listItems) {
                                val cleanItem = item.trim().replaceFirst(Regex("^\\d+\\.\\s*"), "")
                                htmlBuilder.append("<li style='margin-bottom:8px;'>${cleanItem}</li>")
                            }
                            htmlBuilder.append("</ol>")
                        }
                        // 普通段落
                        else {
                            val paragraphs = slideBody.split("\n\n")
                            for (paragraph in paragraphs) {
                                if (paragraph.trim().isNotEmpty()) {
                                    htmlBuilder.append("<p style='margin-bottom:12px;'>${paragraph.replace("\n", "<br>")}</p>")
                                }
                            }
                        }
                    }
                    htmlBuilder.append("</div>")
                }
            }

            // 添加幻灯片分隔符
            if (index < slides.size - 1 && slideContent.isNotEmpty()) {
                htmlBuilder.append("<div style='border-bottom:1px dashed #ccc; margin:30px 0;'></div>")
            }
        }

        return htmlBuilder.toString()
    }

    /**
     * 格式化Word内容，改善段落和标题显示
     */
    private fun formatWordContent(content: String): String {
        // 如果已经包含HTML标签，则不处理
        if (content.contains("<h1>") || content.contains("<p>") || content.contains("<div>")) {
            return content
        }

        val paragraphs = content.split("\n\n")
        val htmlBuilder = StringBuilder()

        for (paragraph in paragraphs) {
            if (paragraph.trim().isEmpty()) {
                continue
            }

            // 检查是否是标题
            if (paragraph.trim().length < 100 && paragraph.trim().endsWith(":")) {
                htmlBuilder.append("<h3 style='color:#2B579A;margin-top:25px;margin-bottom:10px;'>${paragraph}</h3>")
            }
            // 检查是否是章节标题（短且全大写）
            else if (paragraph.trim().length < 50 && paragraph.trim() == paragraph.trim().uppercase() && paragraph.trim().isNotEmpty()) {
                htmlBuilder.append("<h2 style='color:#2B579A;margin-top:30px;margin-bottom:15px;'>${paragraph}</h2>")
            }
            // 普通段落
            else {
                htmlBuilder.append("<p style='margin-bottom:15px;'>${paragraph.replace("\n", "<br>")}</p>")
            }
        }

        return htmlBuilder.toString()
    }

    /**
     * 显示错误信息
     */
    private fun showError(errorMessage: String) {
        loadingProgressBar.visibility = View.GONE
        webView.visibility = View.GONE
        errorTextView.visibility = View.VISIBLE
        errorTextView.text = errorMessage
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        Log.e(TAG, "显示错误: $errorMessage")
    }

    /**
     * 处理返回按钮点击
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * 清理资源
     */
    override fun onDestroy() {
        super.onDestroy()

        // 清理WebView
        webView.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
    }
}

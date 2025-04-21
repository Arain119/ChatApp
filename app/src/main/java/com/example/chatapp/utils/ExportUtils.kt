package com.example.chatapp.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.chatapp.data.db.MessageEntity
import com.itextpdf.text.Document
import com.itextpdf.text.Font
import com.itextpdf.text.PageSize
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 聊天记录导出工具类
 */
class ExportUtils {
    companion object {
        private const val TAG = "ExportUtils"

        /**
         * 导出聊天记录为指定格式
         * @param context 上下文
         * @param messages 消息列表
         * @param chatTitle 聊天标题，用于文件命名
         * @param format 导出格式，"txt" 或 "pdf"
         * @param includeTimestamp 是否包含时间戳
         * @return 导出文件的Uri
         */
        fun exportChatHistory(
            context: Context,
            messages: List<MessageEntity>,
            chatTitle: String,
            format: String,
            includeTimestamp: Boolean
        ): Uri? {
            return try {
                // 安全的文件名（移除特殊字符）
                val safeTitle = chatTitle.replace("[^a-zA-Z0-9\u4e00-\u9fa5._\\s]".toRegex(), "_")
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
                val fileName = "${safeTitle}_$timeStamp.$format"

                // 创建外部缓存目录下的导出文件夹
                val exportDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val exportFile = File(exportDir, fileName)

                when (format.lowercase()) {
                    "txt" -> exportToTxt(exportFile, messages, includeTimestamp)
                    "pdf" -> exportToPdf(exportFile, messages, chatTitle, includeTimestamp)
                    else -> throw IllegalArgumentException("不支持的格式: $format")
                }

                // 使用FileProvider生成内容URI
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )
            } catch (e: Exception) {
                Log.e(TAG, "导出聊天记录失败: ${e.message}", e)
                null
            }
        }

        /**
         * 导出为TXT格式
         */
        private fun exportToTxt(file: File, messages: List<MessageEntity>, includeTimestamp: Boolean) {
            FileOutputStream(file).use { fos ->
                messages.forEach { message ->
                    val rolePrefix = if (message.type == 0) "用户: " else "AI: "
                    val timestamp = if (includeTimestamp) {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        "[${dateFormat.format(message.timestamp)}] "
                    } else {
                        ""
                    }
                    val line = "$timestamp$rolePrefix${message.content}\n\n"
                    fos.write(line.toByteArray())
                }
            }
        }

        /**
         * 导出为PDF格式
         */
        private fun exportToPdf(file: File, messages: List<MessageEntity>, title: String, includeTimestamp: Boolean) {
            val document = Document(PageSize.A4)
            PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()

            // 添加标题
            val titleFont = Font(Font.FontFamily.HELVETICA, 18f, Font.BOLD)
            document.add(Paragraph(title, titleFont))
            document.add(Paragraph(" ")) // 空行

            // 添加正文内容
            val normalFont = Font(Font.FontFamily.HELVETICA, 12f)
            val boldFont = Font(Font.FontFamily.HELVETICA, 12f, Font.BOLD)

            messages.forEach { message ->
                val rolePrefix = if (message.type == 0) "用户: " else "AI: "
                val timestampText = if (includeTimestamp) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    "[${dateFormat.format(message.timestamp)}] "
                } else {
                    ""
                }

                // 添加角色前缀
                val paragraph = Paragraph()
                paragraph.add(Paragraph("$timestampText$rolePrefix", boldFont))
                paragraph.add(Paragraph(message.content, normalFont))
                document.add(paragraph)
                document.add(Paragraph(" ")) // 空行
            }

            document.close()
        }

        /**
         * 分享导出的文件
         */
        fun shareExportedFile(context: Context, fileUri: Uri, format: String) {
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, fileUri)

                // 设置文件类型
                type = when (format) {
                    "txt" -> "text/plain"
                    "pdf" -> "application/pdf"
                    else -> "*/*"
                }

                // 添加ClipData以确保接收应用获得适当的读取权限
                val clipData = ClipData.newRawUri("", fileUri)
                this.clipData = clipData

                // 添加权限标志
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
            }

            // 创建选择器并启动
            val chooserIntent = Intent.createChooser(intent, "分享聊天记录")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        }
    }
}

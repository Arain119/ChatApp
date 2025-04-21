package com.example.chatapp.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 文档存储助手类
 * 用于保存和管理上传的文档
 */
class DocumentStorageHelper(private val context: Context) {

    companion object {
        private const val TAG = "DocumentStorageHelper"
        private const val DOCUMENTS_DIR = "documents"
        private const val PREFS_NAME = "document_prefs"
        private const val URI_PREFIX = "document_uri_"
    }

    /**
     * 保存上传的文档到应用私有存储
     * @param sourceUri 原始文档URI
     * @param fileName 文件名
     * @return 保存的文件URI，如果保存失败则返回null
     */
    suspend fun saveDocument(sourceUri: Uri, fileName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            // 创建文档目录（如果不存在）
            val documentsDir = File(context.getExternalFilesDir(null), DOCUMENTS_DIR)
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
            }

            // 清理文件名，移除不安全字符
            val sanitizedFileName = sanitizeFileName(fileName)

            // 创建目标文件
            val uniqueId = UUID.randomUUID().toString().substring(0, 8)
            val targetFile = File(documentsDir, "${uniqueId}_$sanitizedFileName")

            // 复制文件内容
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("无法打开源文件")

            // 将文件URI保存到SharedPreferences，以便后续查找
            val documentUri = Uri.fromFile(targetFile)
            saveDocumentUri(sanitizedFileName, documentUri)

            Log.d(TAG, "文档已保存: $sanitizedFileName -> $documentUri")

            return@withContext documentUri
        } catch (e: Exception) {
            Log.e(TAG, "保存文档失败: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * 查找特定文件名的文档URI
     * @param fileName 文件名
     * @return 文档URI，如果未找到则返回null
     */
    suspend fun findDocumentUri(fileName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            // 尝试从SharedPreferences获取
            val sanitizedFileName = sanitizeFileName(fileName)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val uriString = prefs.getString("$URI_PREFIX$sanitizedFileName", null)

            if (uriString != null) {
                return@withContext Uri.parse(uriString)
            }

            // 如果没有找到精确匹配，尝试在文档目录中查找包含该文件名的文件
            val documentsDir = File(context.getExternalFilesDir(null), DOCUMENTS_DIR)
            if (documentsDir.exists()) {
                val files = documentsDir.listFiles { file ->
                    file.name.contains(sanitizedFileName, ignoreCase = true)
                }

                if (files != null && files.isNotEmpty()) {
                    // 找到匹配的文件，返回其URI
                    return@withContext Uri.fromFile(files[0])
                }
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "查找文档URI失败: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * 删除文档
     * @param fileName 文件名
     * @return 是否成功删除
     */
    suspend fun deleteDocument(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sanitizedFileName = sanitizeFileName(fileName)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val uriString = prefs.getString("$URI_PREFIX$sanitizedFileName", null)

            if (uriString != null) {
                val file = File(Uri.parse(uriString).path ?: "")
                if (file.exists() && file.delete()) {
                    // 从SharedPreferences中移除记录
                    prefs.edit().remove("$URI_PREFIX$sanitizedFileName").apply()
                    return@withContext true
                }
            }

            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "删除文档失败: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * 清理文件名，移除不安全字符
     */
    private fun sanitizeFileName(fileName: String): String {
        return fileName.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_") // 移除不允许的文件名字符
            .take(100) // 限制长度
    }

    /**
     * 保存文档URI到SharedPreferences
     */
    private fun saveDocumentUri(fileName: String, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$URI_PREFIX$fileName", uri.toString()).apply()
    }
}
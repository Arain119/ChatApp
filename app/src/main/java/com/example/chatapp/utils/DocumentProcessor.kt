package com.example.chatapp.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.DecimalFormat
import java.util.Locale

/**
 * 文档处理工具类
 * 用于从不同类型的文档中提取文本
 */
class DocumentProcessor(private val context: Context) {

    companion object {
        private const val TAG = "DocumentProcessor"
        private const val MAX_TEXT_LENGTH = 15000 // 增加最大文本长度，以支持更完整的文档解析
    }

    /**
     * 从文档URI中提取文本
     */
    fun extractTextFromDocument(fileUri: Uri): String? {
        val mimeType = context.contentResolver.getType(fileUri)
        val fileName = getFileName(fileUri)
        val extension = fileName.substringAfterLast('.', "").toLowerCase()

        return try {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                when {
                    // 文本文件
                    mimeType?.contains("text", ignoreCase = true) == true ||
                            mimeType?.contains("txt", ignoreCase = true) == true ||
                            extension == "txt" -> {
                        extractTextFromTxt(inputStream)
                    }
                    // PDF文件
                    mimeType?.contains("pdf", ignoreCase = true) == true ||
                            extension == "pdf" -> {
                        extractTextFromPdf(inputStream)
                    }
                    // Word文档 - DOC格式
                    mimeType?.contains("msword", ignoreCase = true) == true ||
                            extension == "doc" -> {
                        extractTextFromDoc(inputStream)
                    }
                    // Word文档 - DOCX格式
                    mimeType?.contains("officedocument.wordprocessingml", ignoreCase = true) == true ||
                            extension == "docx" -> {
                        extractTextFromDocx(inputStream)
                    }
                    // Excel文档 - XLS格式
                    mimeType?.contains("excel", ignoreCase = true) == true ||
                            extension == "xls" -> {
                        extractTextFromXls(inputStream)
                    }
                    // Excel文档 - XLSX格式
                    mimeType?.contains("officedocument.spreadsheetml", ignoreCase = true) == true ||
                            extension == "xlsx" -> {
                        extractTextFromXlsx(inputStream)
                    }
                    // 不支持的类型
                    else -> {
                        "抱歉，目前支持TXT、PDF、Word文档(DOC/DOCX)和Excel表格(XLS/XLSX)的解析。不支持的文件类型: $mimeType，扩展名: $extension"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取文档文本失败: ${e.message}", e)
            "文档解析失败: ${e.message}"
        }
    }

    /**
     * 从文本文件中提取文本
     */
    private fun extractTextFromTxt(inputStream: InputStream): String {
        return try {
            val reader = InputStreamReader(inputStream, Charsets.UTF_8)
            val text = reader.readText()

            if (text.length > MAX_TEXT_LENGTH) {
                text.substring(0, MAX_TEXT_LENGTH) + "\n...(文档过大，仅显示部分内容)"
            } else {
                text
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取文本文件失败: ${e.message}", e)
            "文本文件解析失败: ${e.message}"
        }
    }

    /**
     * 从PDF文件中提取文本
     */
    private fun extractTextFromPdf(inputStream: InputStream): String {
        return try {
            // 创建临时文件
            val tempFile = File.createTempFile("temp", ".pdf", context.cacheDir)
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }

            // 使用iText读取PDF
            val reader = PdfReader(tempFile.absolutePath)
            val stringBuilder = StringBuilder()
            var count = 0

            // 遍历每一页
            for (i in 1..reader.numberOfPages) {
                if (count >= MAX_TEXT_LENGTH) break

                // 提取当前页的文本
                val pageText = PdfTextExtractor.getTextFromPage(reader, i)
                stringBuilder.append("=== 第${i}页 ===\n").append(pageText).append("\n\n")

                count += pageText.length
            }

            reader.close()
            tempFile.delete()  // 删除临时文件

            if (count >= MAX_TEXT_LENGTH) {
                stringBuilder.append("...(文档过大，仅显示部分内容)")
            }

            // 附加PDF元数据
            val info = reader.info
            if (info != null) {
                stringBuilder.append("\n\n=== 文档信息 ===\n")
                info["Title"]?.let { stringBuilder.append("标题: $it\n") }
                info["Author"]?.let { stringBuilder.append("作者: $it\n") }
                info["Subject"]?.let { stringBuilder.append("主题: $it\n") }
                info["Keywords"]?.let { stringBuilder.append("关键词: $it\n") }
                info["Producer"]?.let { stringBuilder.append("生成器: $it\n") }
                info["CreationDate"]?.let { stringBuilder.append("创建日期: $it\n") }
            }

            stringBuilder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "提取PDF文件失败: ${e.message}", e)
            "PDF文件解析失败: ${e.message}"
        }
    }

    /**
     * 从DOC文件(旧版Word)中提取文本
     * 使用Apache POI库
     */
    private fun extractTextFromDoc(inputStream: InputStream): String {
        return try {
            // 使用POI提取文本
            val doc = HWPFDocument(inputStream)
            val text = doc.text.toString()

            doc.close()

            // 限制文本长度
            if (text.length > MAX_TEXT_LENGTH) {
                text.substring(0, MAX_TEXT_LENGTH) + "\n...(文档过大，仅显示部分内容)"
            } else {
                text
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取DOC文件失败: ${e.message}", e)
            "DOC文件解析失败: ${e.message}"
        }
    }

    /**
     * 从DOCX文件(新版Word)中提取文本
     */
    private fun extractTextFromDocx(inputStream: InputStream): String {
        return try {
            // 读取文档
            val doc = XWPFDocument(inputStream)
            val extractor = XWPFWordExtractor(doc)
            val text = extractor.text

            val stringBuilder = StringBuilder()

            // 添加文档主体内容
            if (text.length > MAX_TEXT_LENGTH) {
                stringBuilder.append(text.substring(0, MAX_TEXT_LENGTH))
                stringBuilder.append("\n...(文档过大，仅显示部分内容)")
            } else {
                stringBuilder.append(text)
            }

            // 添加文档元数据
            stringBuilder.append("\n\n=== 文档信息 ===\n")
            doc.properties?.coreProperties?.let { props ->
                props.title?.let { stringBuilder.append("标题: $it\n") }
                props.creator?.let { stringBuilder.append("作者: $it\n") }
                props.description?.let { stringBuilder.append("描述: $it\n") }
                props.subject?.let { stringBuilder.append("主题: $it\n") }
                props.keywords?.let { stringBuilder.append("关键词: $it\n") }
                props.created?.let { stringBuilder.append("创建时间: $it\n") }
                props.modified?.let { stringBuilder.append("修改时间: $it\n") }
            }

            extractor.close()
            doc.close()

            stringBuilder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "提取DOCX文件失败: ${e.message}", e)
            "DOCX文件解析失败: ${e.message}"
        }
    }

    /**
     * 从XLS文件(旧版Excel)中提取文本
     */
    private fun extractTextFromXls(inputStream: InputStream): String {
        return try {
            // 读取工作簿
            val workbook = HSSFWorkbook(inputStream)
            extractTextFromExcel(workbook)
        } catch (e: Exception) {
            Log.e(TAG, "提取XLS文件失败: ${e.message}", e)
            "XLS文件解析失败: ${e.message}"
        }
    }

    /**
     * 从XLSX文件(新版Excel)中提取文本
     */
    private fun extractTextFromXlsx(inputStream: InputStream): String {
        return try {
            // 读取工作簿
            val workbook = XSSFWorkbook(inputStream)
            extractTextFromExcel(workbook)
        } catch (e: Exception) {
            Log.e(TAG, "提取XLSX文件失败: ${e.message}", e)
            "XLSX文件解析失败: ${e.message}"
        }
    }

    /**
     * 从Excel工作簿中提取文本
     * 通用处理方法，适用于XLS和XLSX
     */
    private fun extractTextFromExcel(workbook: Workbook): String {
        val stringBuilder = StringBuilder()
        val dataFormatter = DataFormatter()
        var totalChars = 0

        try {
            // 添加工作簿属性信息（如果有）
            if (workbook is XSSFWorkbook) {
                try {
                    val props = workbook.properties?.coreProperties
                    stringBuilder.append("=== 文档信息 ===\n")
                    props?.title?.let { stringBuilder.append("标题: $it\n") }
                    props?.creator?.let { stringBuilder.append("作者: $it\n") }
                    props?.description?.let { stringBuilder.append("描述: $it\n") }
                    stringBuilder.append("\n")
                } catch (e: Exception) {
                    // 忽略属性读取错误
                }
            }

            // 遍历所有工作表
            for (i in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(i)
                val sheetName = workbook.getSheetName(i)

                // 添加工作表名称
                stringBuilder.append("【工作表: ${sheetName}】\n")

                // 检查是否有单元格合并情况
                val mergedRegions = sheet.mergedRegions
                val mergedCellsMap = mutableMapOf<Pair<Int, Int>, String>()

                // 预处理合并单元格
                for (region in mergedRegions) {
                    val firstRow = region.firstRow
                    val firstCol = region.firstColumn
                    val cell = sheet.getRow(firstRow)?.getCell(firstCol)
                    if (cell != null) {
                        val value = dataFormatter.formatCellValue(cell)

                        // 存储合并单元格的值
                        for (r in region.firstRow..region.lastRow) {
                            for (c in region.firstColumn..region.lastColumn) {
                                mergedCellsMap[Pair(r, c)] = value
                            }
                        }
                    }
                }

                // 遍历行
                var hasData = false
                for (row in sheet) {
                    val rowNum = row.rowNum
                    val rowStr = StringBuilder()
                    var cellAdded = false

                    // 获取此行最后一个单元格的索引
                    val lastCellNum = row.lastCellNum.toInt()

                    // 遍历单元格
                    for (colIdx in 0 until lastCellNum) {
                        val cell = row.getCell(colIdx)

                        // 检查是否是合并单元格
                        val mergedValue = mergedCellsMap[Pair(rowNum, colIdx)]

                        val cellValue = when {
                            mergedValue != null -> mergedValue
                            cell != null -> {
                                when (cell.cellType) {
                                    CellType.NUMERIC -> dataFormatter.formatCellValue(cell)
                                    CellType.STRING -> cell.stringCellValue ?: ""
                                    CellType.FORMULA -> {
                                        try {
                                            // 尝试获取公式计算结果
                                            dataFormatter.formatCellValue(cell)
                                        } catch (e: Exception) {
                                            cell.cellFormula ?: ""
                                        }
                                    }
                                    CellType.BOOLEAN -> cell.booleanCellValue.toString()
                                    else -> ""
                                }
                            }
                            else -> ""
                        }

                        if (cellValue.isNotEmpty()) {
                            rowStr.append(cellValue).append("\t")
                            cellAdded = true
                        } else {
                            rowStr.append("\t") // 保持列对齐
                        }
                    }

                    if (cellAdded) {
                        stringBuilder.append(rowStr).append("\n")
                        hasData = true
                        totalChars += rowStr.length + 1
                    }

                    // 检查是否达到最大长度
                    if (totalChars >= MAX_TEXT_LENGTH) {
                        stringBuilder.append("...(表格过大，仅显示部分内容)")
                        break
                    }
                }

                if (!hasData) {
                    stringBuilder.append("(空工作表)\n")
                }

                stringBuilder.append("\n")

                // 如果已经达到最大长度，不再处理其他工作表
                if (totalChars >= MAX_TEXT_LENGTH) {
                    stringBuilder.append("...(还有更多工作表未显示)")
                    break
                }
            }

            workbook.close()
            return stringBuilder.toString()
        } catch (e: Exception) {
            workbook.close()
            Log.e(TAG, "提取Excel内容失败: ${e.message}", e)
            return "Excel表格解析失败: ${e.message}"
        }
    }

    /**
     * 获取文件名
     */
    fun getFileName(uri: Uri): String {
        var result = "unknown"
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == "unknown") {
            result = uri.path?.substringAfterLast('/') ?: "unknown"
        }
        return result
    }

    /**
     * 获取文件大小
     */
    fun getFileSize(uri: Uri): Long {
        var size = 0L
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !it.isNull(sizeIndex)) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }
        }

        // 如果无法通过内容提供者获取大小，尝试打开流并读取大小
        if (size == 0L) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    size = inputStream.available().toLong()
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取文件大小失败: ${e.message}", e)
            }
        }

        return size
    }

    /**
     * 格式化文件大小为易读格式
     */
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    /**
     * 获取文档类型
     */
    fun getDocumentType(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null) {
            return mimeType
        }

        // 根据文件名后缀推断类型
        val fileName = getFileName(uri)
        val extension = fileName.substringAfterLast('.', "").toLowerCase()

        return when (extension) {
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "application/octet-stream"
        }
    }

    /**
     * 获取友好的文件类型显示名称
     */
    fun getFileTypeDisplayName(uri: Uri): String {
        val mimeType = getDocumentType(uri)
        val fileName = getFileName(uri)
        val extension = fileName.substringAfterLast('.', "").toUpperCase(Locale.ROOT)

        return when {
            extension.isNotEmpty() -> extension
            mimeType.contains("pdf", ignoreCase = true) -> "PDF"
            mimeType.contains("text/plain", ignoreCase = true) -> "TXT"
            mimeType.contains("msword", ignoreCase = true) -> "DOC"
            mimeType.contains("wordprocessingml", ignoreCase = true) -> "DOCX"
            mimeType.contains("ms-excel", ignoreCase = true) -> "XLS"
            mimeType.contains("spreadsheetml", ignoreCase = true) -> "XLSX"
            else -> "文档"
        }
    }

    /**
     * 获取文档信息（大小和类型）
     * @return Pair<String, String>
     * 第一个是格式化大小，第二个是类型显示名
     */
    fun getDocumentInfo(uri: Uri): Pair<String, String> {
        val size = getFileSize(uri)
        val formattedSize = formatFileSize(size)
        val fileType = getFileTypeDisplayName(uri)

        return Pair(formattedSize, fileType)
    }
}
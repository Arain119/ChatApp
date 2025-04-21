package com.example.chatapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 图像处理工具类 - 用于准备图像数据发送给API
 */
class ImageProcessor(private val context: Context) {

    companion object {
        private const val TAG = "ImageProcessor"
        private const val MAX_IMAGE_DIMENSION = 1024 // 最大图像尺寸，用于压缩
        private const val JPEG_QUALITY = 85 // JPEG压缩质量
    }

    /**
     * 处理图像URI并返回Base64编码
     */
    fun processImage(imageUri: Uri): String? {
        try {
            // 从URI加载Bitmap
            val bitmap = loadAndResizeBitmap(imageUri)
            if (bitmap != null) {
                // 转换为Base64
                val base64Image = bitmapToBase64(bitmap)
                // 清理资源
                bitmap.recycle()
                return base64Image
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理图像失败: ${e.message}", e)
        }
        return null
    }

    /**
     * 加载并调整图像大小
     */
    private fun loadAndResizeBitmap(imageUri: Uri): Bitmap? {
        try {
            // 获取图像的原始尺寸
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            // 计算缩放比例
            val width = options.outWidth
            val height = options.outHeight
            val scaleFactor = calculateScaleFactor(width, height)

            // 加载并调整大小的图像
            val scaledOptions = BitmapFactory.Options().apply {
                inSampleSize = scaleFactor
                inJustDecodeBounds = false
            }

            return context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream, null, scaledOptions)

                // 如果图像仍然太大，进一步调整大小
                if (bitmap != null && (bitmap.width > MAX_IMAGE_DIMENSION || bitmap.height > MAX_IMAGE_DIMENSION)) {
                    val resizedBitmap = resizeBitmap(bitmap)
                    if (bitmap != resizedBitmap) {
                        bitmap.recycle()
                    }
                    resizedBitmap
                } else {
                    bitmap
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载图像失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 调整Bitmap大小
     */
    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }

        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = MAX_IMAGE_DIMENSION
            newHeight = (newWidth / aspectRatio).toInt()
        } else {
            newHeight = MAX_IMAGE_DIMENSION
            newWidth = (newHeight * aspectRatio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * 计算缩放因子
     */
    private fun calculateScaleFactor(width: Int, height: Int): Int {
        var scaleFactor = 1
        while ((width / scaleFactor) > MAX_IMAGE_DIMENSION * 2 ||
            (height / scaleFactor) > MAX_IMAGE_DIMENSION * 2) {
            scaleFactor *= 2
        }
        return scaleFactor
    }

    /**
     * 将Bitmap转换为Base64编码
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        try {
            // 使用JPEG格式压缩图像
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            // 返回Base64编码
            return Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } finally {
            try {
                byteArrayOutputStream.close()
            } catch (e: IOException) {
                Log.e(TAG, "关闭输出流失败", e)
            }
        }
    }

    /**
     * 获取图像的MIME类型
     */
    fun getImageMimeType(imageUri: Uri): String {
        return context.contentResolver.getType(imageUri) ?: "image/jpeg"
    }

    /**
     * 获取图像的文件名
     */
    fun getImageFileName(imageUri: Uri): String {
        val cursor = context.contentResolver.query(imageUri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex("_display_name")
                if (nameIndex >= 0) {
                    return it.getString(nameIndex)
                }
            }
        }
        return "image_${System.currentTimeMillis()}.jpg"
    }
}
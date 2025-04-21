package com.example.chatapp.ui.custom

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.example.chatapp.R
import kotlin.math.max
import kotlin.math.min

class AvatarCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private val savedMatrix = Matrix()

    private var startX = 0f
    private var startY = 0f
    private var midX = 0f
    private var midY = 0f
    private var scale = 1f
    private var rotation = 0f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    // 剪裁区域
    private val cropRect = RectF()
    private val cropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f
    }

    init {
        // 启用绘图缓存
        isDrawingCacheEnabled = true
    }

    fun setImageUri(uri: Uri) {
        try {
            // 解码图片
            val inputStream = context.contentResolver.openInputStream(uri)
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // 初始化变换矩阵
            resetMatrix()
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetMatrix() {
        bitmap?.let {
            // 计算图片缩放比例，使其适应view
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            val bitmapWidth = it.width.toFloat()
            val bitmapHeight = it.height.toFloat()

            // 计算裁剪区域为圆形
            val cropSize = min(viewWidth, viewHeight) * 0.8f
            cropRect.set(
                (viewWidth - cropSize) / 2,
                (viewHeight - cropSize) / 2,
                (viewWidth + cropSize) / 2,
                (viewHeight + cropSize) / 2
            )

            // 计算缩放比例
            val scaleX = cropSize / bitmapWidth
            val scaleY = cropSize / bitmapHeight
            scale = max(scaleX, scaleY)

            // 设置初始变换
            matrix.reset()
            matrix.postScale(scale, scale)

            // 居中
            val scaledWidth = bitmapWidth * scale
            val scaledHeight = bitmapHeight * scale
            matrix.postTranslate(
                (viewWidth - scaledWidth) / 2,
                (viewHeight - scaledHeight) / 2
            )

            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        bitmap?.let {
            // 绘制背景
            canvas.drawColor(ContextCompat.getColor(context, R.color.black))

            // 绘制图片
            canvas.drawBitmap(it, matrix, paint)

            // 绘制半透明黑色背景，突出圆形区域
            val path = Path()
            path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            path.addOval(cropRect, Path.Direction.CCW)

            val bgPaint = Paint().apply {
                color = Color.parseColor("#80000000")
                style = Paint.Style.FILL
            }
            canvas.drawPath(path, bgPaint)

            // 绘制圆形剪裁边框
            canvas.drawOval(cropRect, cropPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    // 获取裁剪后的图片
    fun getCroppedBitmap(): Bitmap? {
        bitmap?.let {
            // 创建输出位图
            val outputBitmap = Bitmap.createBitmap(
                cropRect.width().toInt(),
                cropRect.height().toInt(),
                Bitmap.Config.ARGB_8888
            )

            // 创建画布
            val canvas = Canvas(outputBitmap)

            // 计算裁剪矩阵
            val cropMatrix = Matrix(matrix)
            cropMatrix.postTranslate(-cropRect.left, -cropRect.top)

            // 绘制裁剪后的图片
            canvas.drawBitmap(it, cropMatrix, paint)

            // 创建圆形裁剪
            val circleBitmap = Bitmap.createBitmap(
                outputBitmap.width,
                outputBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val circleCanvas = Canvas(circleBitmap)
            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

            // 绘制圆形
            val radius = min(outputBitmap.width, outputBitmap.height) / 2f
            circleCanvas.drawCircle(
                outputBitmap.width / 2f,
                outputBitmap.height / 2f,
                radius,
                circlePaint
            )

            // 设置混合模式
            circlePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            circleCanvas.drawBitmap(outputBitmap, 0f, 0f, circlePaint)

            return circleBitmap
        }
        return null
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // 处理缩放
            val scaleFactor = detector.scaleFactor
            scale *= scaleFactor

            // 限制缩放范围
            scale = scale.coerceIn(0.5f, 5.0f)

            // 应用变换
            matrix.postScale(
                scaleFactor, scaleFactor,
                detector.focusX, detector.focusY
            )

            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            // 处理拖动
            matrix.postTranslate(-distanceX, -distanceY)
            invalidate()
            return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (bitmap != null && (oldw != w || oldh != h)) {
            resetMatrix()
        }
    }
}
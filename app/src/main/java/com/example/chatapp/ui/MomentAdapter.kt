package com.example.chatapp.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.chatapp.R
import com.example.chatapp.data.Moment
import com.example.chatapp.data.MomentType
import com.example.chatapp.data.SettingsManager
import com.example.chatapp.utils.TextFormatter
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Log

private const val TAG = "MomentAdapter"

/**
 * 动态列表适配器
 */
class MomentAdapter(
    private val context: Context,
    private val onCopyClick: (String) -> Unit,
    private val onShareClick: (String) -> Unit,
    private val onMoreClick: (Moment, View) -> Unit,
    private val onMomentClick: OnMomentClickListener
) : ListAdapter<Moment, MomentAdapter.MomentViewHolder>(MomentDiffCallback()) {

    private val settingsManager = SettingsManager(context)

    // 添加点击监听器接口
    interface OnMomentClickListener {
        fun onMomentClick(moment: Moment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MomentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_moment_card, parent, false)
        return MomentViewHolder(view)
    }

    override fun onBindViewHolder(holder: MomentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MomentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val aiAvatar: ShapeableImageView = itemView.findViewById(R.id.aiAvatar)
        private val aiName: TextView = itemView.findViewById(R.id.aiName)
        private val momentTitle: TextView = itemView.findViewById(R.id.momentTitle)
        private val momentTime: TextView = itemView.findViewById(R.id.momentTime)
        private val momentTypeTag: TextView = itemView.findViewById(R.id.momentTypeTag)
        private val momentContent: TextView = itemView.findViewById(R.id.momentContent)
        private val momentImage: ShapeableImageView = itemView.findViewById(R.id.momentImage)
        private val moreButton: ImageButton = itemView.findViewById(R.id.moreButton)
        private val momentCard: CardView = itemView.findViewById(R.id.momentCard)

        fun bind(moment: Moment) {
            // 设置AI头像和名称
            aiName.text = settingsManager.aiName ?: "ChatGPT"

            // 加载AI头像
            val aiAvatarUri = settingsManager.aiAvatarUri
            if (aiAvatarUri != null && aiAvatarUri.isNotEmpty()) {
                Glide.with(context)
                    .load(Uri.parse(aiAvatarUri))
                    .apply(RequestOptions.circleCropTransform())
                    .error(R.drawable.default_ai_avatar)
                    .into(aiAvatar)
            } else {
                aiAvatar.setImageResource(R.drawable.default_ai_avatar)
            }

            // 设置标题 - 使用TextFormatter格式化
            val titleText = if (moment.title.isNotEmpty()) {
                moment.title
            } else {
                // 尝试从内容中提取第一句话
                val firstSentence = extractFirstSentence(moment.content)
                if (firstSentence.isNotEmpty()) firstSentence else "今日随想"
            }

            // 使用直接应用方法设置标题
            TextFormatter.applyFormattingToTextView(momentTitle, titleText)

            // 设置时间
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            momentTime.text = dateFormat.format(moment.timestamp)

            // 设置类型标签
            momentTypeTag.text = if (moment.type == MomentType.AI_GENERATED) "AI生成" else "我的动态"
            momentTypeTag.setBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (moment.type == MomentType.AI_GENERATED) R.color.primary_light else R.color.accent_light
                )
            )
            momentTypeTag.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (moment.type == MomentType.AI_GENERATED) R.color.primary else R.color.accent
                )
            )

            // 设置内容 - 使用TextFormatter格式化
            val content = moment.content
            // 设置最多显示5行
            momentContent.maxLines = 5
            momentContent.ellipsize = android.text.TextUtils.TruncateAt.END

            // 使用直接应用方法设置内容
            TextFormatter.applyFormattingToTextView(momentContent, content)

            // 设置图片
            if (!moment.imageUri.isNullOrEmpty()) {
                momentImage.visibility = View.VISIBLE

                try {
                    Log.d(TAG, "加载图片: imageUri=${moment.imageUri?.take(100)}")

                    // 检查是否是Base64数据
                    if (isBase64Image(moment.imageUri)) {
                        // 直接加载Base64编码的图片数据
                        loadBase64Image(moment.imageUri, momentImage)
                    } else {
                        // 加载标准URI
                        Glide.with(context)
                            .load(Uri.parse(moment.imageUri))
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.ic_image)
                            .error(R.drawable.ic_broken_image)
                            .centerCrop()
                            .into(momentImage)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "加载图片失败: ${e.message}", e)
                    momentImage.visibility = View.GONE
                }
            } else {
                momentImage.visibility = View.GONE
            }

            // 设置更多按钮
            moreButton.setOnClickListener {
                onMoreClick(moment, moreButton)
            }

            // 设置卡片点击事件
            momentCard.setOnClickListener {
                onMomentClick.onMomentClick(moment)
            }

            // 设置整体长按
            itemView.setOnLongClickListener {
                onMoreClick(moment, moreButton)
                true
            }
        }

        /**
         * 判断是否为Base64编码的图片
         */
        private fun isBase64Image(data: String?): Boolean {
            if (data == null) return false

            // 快速判断：检查是否包含Base64数据URI前缀
            if (data.startsWith("data:image/")) {
                return true
            }

            // 检查是否直接是Base64字符串 (无前缀)
            try {
                // 检查是否符合Base64编码特征
                if (data.length % 4 != 0) return false

                // 检查是否只包含Base64字符
                val base64Pattern = Regex("^[A-Za-z0-9+/]+={0,2}$")
                // 取较短的子串进行快速检查
                val checkSample = if (data.length > 100) data.substring(0, 100) else data
                if (!base64Pattern.matches(checkSample)) return false

                // 尝试解码前100个字符
                val decoded = Base64.decode(checkSample, Base64.DEFAULT)
                return decoded.size > 0
            } catch (e: Exception) {
                return false
            }
        }

        /**
         * 加载Base64编码的图片
         */
        private fun loadBase64Image(base64Data: String, imageView: ShapeableImageView) {
            try {
                // 提取实际的Base64数据
                val actualBase64 = if (base64Data.contains(",")) {
                    base64Data.substring(base64Data.indexOf(",") + 1)
                } else {
                    base64Data
                }

                // 解码Base64为字节数组
                val imageBytes = Base64.decode(actualBase64, Base64.DEFAULT)

                // 使用Glide加载
                Glide.with(context)
                    .load(imageBytes)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_broken_image)
                    .centerCrop()
                    .into(imageView)

                Log.d(TAG, "Base64图片加载成功，数据长度: ${imageBytes.size}")
            } catch (e: Exception) {
                Log.e(TAG, "加载Base64图片失败: ${e.message}", e)
                imageView.visibility = View.GONE
            }
        }

        // 辅助方法：从文本中提取第一句话
        private fun extractFirstSentence(text: String): String {
            val sentenceEndMarkers = arrayOf("。", "！", "？", ".", "!", "?", "\n")
            var endPos = Int.MAX_VALUE

            // 查找第一个句子结束标记
            for (marker in sentenceEndMarkers) {
                val pos = text.indexOf(marker)
                if (pos != -1 && pos < endPos) {
                    endPos = pos
                }
            }

            // 提取并返回第一句话，限制长度不超过20字符
            return if (endPos < Int.MAX_VALUE) {
                val sentence = text.substring(0, endPos + 1)
                if (sentence.length > 20) sentence.substring(0, 20) + "..." else sentence
            } else {
                // 如果没有找到句子结束标记，返回前20个字符
                if (text.length > 20) text.substring(0, 20) + "..." else text
            }
        }
    }

    class MomentDiffCallback : DiffUtil.ItemCallback<Moment>() {
        override fun areItemsTheSame(oldItem: Moment, newItem: Moment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Moment, newItem: Moment): Boolean {
            return oldItem == newItem
        }
    }
}

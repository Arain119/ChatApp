package com.example.chatapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.R
import com.example.chatapp.data.db.MemoryEntity
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 记忆适配器
 * 用于显示聊天记忆列表
 * 单击：显示完整内容
 * 长按：删除
 */
class MemoryAdapter(
    // 用于处理单击事件的回调，以查看完整内容
    private val onItemClick: (MemoryEntity) -> Unit,
    // 用于处理长按事件的回调，以删除
    private val onLongClick: (MemoryEntity) -> Unit
) : RecyclerView.Adapter<MemoryAdapter.MemoryViewHolder>() {

    private var memories: List<MemoryEntity> = emptyList()

    // 监听器接口定义
    interface OnMemoryItemClickListener {
        fun onMemoryItemClicked(memory: MemoryEntity)
    }
    interface OnMemoryItemLongClickListener {
        fun onMemoryItemLongClicked(memory: MemoryEntity)
    }

    fun submitList(newList: List<MemoryEntity>) {
        memories = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memory, parent, false)
        // 将监听器回调传递给 ViewHolder
        return MemoryViewHolder(view, onItemClick, onLongClick)
    }

    override fun onBindViewHolder(holder: MemoryViewHolder, position: Int) {
        val memory = memories[position]
        holder.bind(memory)
    }

    override fun getItemCount(): Int = memories.size

    class MemoryViewHolder(
        itemView: View,
        // 接收监听器回调
        private val onItemClick: (MemoryEntity) -> Unit,
        private val onLongClick: (MemoryEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val contentTextView: TextView = itemView.findViewById(R.id.memory_content)
        private val dateTextView: TextView = itemView.findViewById(R.id.memory_date)

        companion object {
            // 为列表预览保留初始最大行数
            private const val PREVIEW_MAX_LINES = 2
        }

        fun bind(memory: MemoryEntity) {
            contentTextView.text = memory.content
            // 设置初始最大行数用于列表预览
            contentTextView.maxLines = PREVIEW_MAX_LINES
            // 确保超出部分显示省略号
            contentTextView.ellipsize = android.text.TextUtils.TruncateAt.END

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            dateTextView.text = dateFormat.format(memory.timestamp)

            // 设置长按监听器用于删除
            itemView.setOnLongClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    // 直接使用传递给 bind 的 memory 对象
                    onLongClick(memory)
                }
                true // 消费长按事件，防止触发单击
            }

            // 设置单击监听器以查看完整内容
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    // 直接使用传递给 bind 的 memory 对象
                    onItemClick(memory)
                }
            }
        }
    }
}
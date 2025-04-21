package com.example.chatapp.utils

import android.content.Context
import android.text.Spannable
import android.widget.TextView

/**
 * 消息格式化工具类 - 兼容层
 * 使用MarkdownFormatter实现全部功能
 */
object MessageFormatter {
    /**
     * 处理消息文本的格式化，需要传入Context
     */
    fun formatBoldText(context: Context, message: String): Spannable {
        return MarkdownFormatter.formatMarkdown(context, message)
    }

    /**
     * 直接应用到TextView的便捷方法
     */
    fun applyFormattingToTextView(textView: TextView, message: String) {
        MarkdownFormatter.applyMarkdownToTextView(textView, message)
    }
}
package com.example.chatapp.utils

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.widget.TextView
import android.util.Log
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Markdown格式化工具类、
 * 使用Markwon库
 */
object MarkdownFormatter {

    private const val TAG = "MarkdownFormatter"
    private var markwonInstances = mutableMapOf<Int, Markwon>()

    /**
     * 获取Markwon实例
     * 使用缓存提高性能
     */
    private fun getMarkwon(context: Context): Markwon {
        val contextHashCode = context.hashCode()

        return markwonInstances.getOrPut(contextHashCode) {
            Log.d(TAG, "为Context创建新的Markwon实例: $contextHashCode")
            Markwon.builder(context.applicationContext)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(ImagesPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .build()
        }
    }

    /**
     * 格式化Markdown文本
     * 需要传入Context参数，可以是任何Context（Activity, Fragment, View等）
     */
    fun formatMarkdown(context: Context, markdown: String): Spannable {
        val spanned = getMarkwon(context).toMarkdown(markdown)
        return SpannableString.valueOf(spanned)
    }

    /**
     * 应用Markdown格式到TextView
     */
    fun applyMarkdownToTextView(textView: TextView, markdown: String) {
        val markwon = getMarkwon(textView.context)
        markwon.setMarkdown(textView, markdown)
    }
}
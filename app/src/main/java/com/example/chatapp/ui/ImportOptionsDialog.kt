package com.example.chatapp.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.example.chatapp.R
import com.example.chatapp.utils.HapticUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * 导入选项底部抽屉
 */
class ImportOptionsDialog : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "ImportOptionsDialog"

        fun newInstance(): ImportOptionsDialog {
            return ImportOptionsDialog()
        }
    }

    private var listener: ImportOptionsListener? = null

    /**
     * 导入选项监听器接口
     */
    interface ImportOptionsListener {
        /**
         * 选择了导入选项
         * @param generateMemories 是否自动生成记忆
         */
        fun onImportOptionSelected(generateMemories: Boolean)
    }

    fun setImportOptionsListener(listener: ImportOptionsListener) {
        this.listener = listener
    }

    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        // 设置展开时的高度
        dialog.setOnShowListener {
            val bottomSheet = (dialog as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                // 设置背景为透明，让自定义背景显示圆角
                it.setBackgroundResource(android.R.color.transparent)

                val behavior = BottomSheetBehavior.from(it)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED

                // 设置峰值高度为屏幕高度的1/3
                val windowHeight = resources.displayMetrics.heightPixels
                val peekHeight = windowHeight / 3
                behavior.peekHeight = peekHeight
            }
        }

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_import_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置透明背景
        dialog?.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(requireContext(), android.R.color.transparent)
        )

        // 设置导入且生成记忆按钮点击事件
        view.findViewById<View>(R.id.btn_import_with_memories).setOnClickListener {
            // 添加震动反馈
            try {
                HapticUtils.performViewHapticFeedback(it)
            } catch (e: Exception) {
                // 忽略震动失败
            }

            listener?.onImportOptionSelected(true)
            dismiss()
        }

        // 设置仅导入按钮点击事件
        view.findViewById<View>(R.id.btn_import_only).setOnClickListener {
            // 添加震动反馈
            try {
                HapticUtils.performViewHapticFeedback(it)
            } catch (e: Exception) {
                // 忽略震动失败
            }

            listener?.onImportOptionSelected(false)
            dismiss()
        }

        // 设置取消按钮点击事件
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            // 添加震动反馈
            try {
                HapticUtils.performViewHapticFeedback(it, false)
            } catch (e: Exception) {
                // 忽略震动失败
            }

            dismiss()
        }
    }
}
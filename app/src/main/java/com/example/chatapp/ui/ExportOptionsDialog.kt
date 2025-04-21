package com.example.chatapp.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.chatapp.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * 导出选项对话框
 * 用于选择导出格式和其他选项
 */
class ExportOptionsDialog : BottomSheetDialogFragment() {

    interface ExportOptionsListener {
        fun onExportOptionSelected(format: String, includeTimestamp: Boolean)
    }

    private var listener: ExportOptionsListener? = null

    fun setExportOptionsListener(listener: ExportOptionsListener) {
        this.listener = listener
    }

    // 应用自定义样式
    override fun getTheme(): Int {
        return R.style.BottomSheetDialogTheme
    }

    // 创建Dialog
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                // 设置背景为圆角drawable
                it.setBackgroundResource(R.drawable.rounded_top_corners)
            }
        }

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_export_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 获取控件引用
        val radioGroup = view.findViewById<RadioGroup>(R.id.format_radio_group)
        val timestampSwitch = view.findViewById<Switch>(R.id.timestamp_switch)
        val exportButton = view.findViewById<Button>(R.id.export_button)
        val cancelButton = view.findViewById<Button>(R.id.cancel_button)

        // 设置导出按钮点击事件
        exportButton.setOnClickListener {
            // 获取选中的格式
            val selectedRadioButtonId = radioGroup.checkedRadioButtonId
            if (selectedRadioButtonId == -1) {
                Toast.makeText(context, "请选择导出格式", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val radioButton = view.findViewById<RadioButton>(selectedRadioButtonId)
            val format = when (radioButton.id) {
                R.id.radio_txt -> "txt"
                R.id.radio_pdf -> "pdf"
                else -> "txt" // 默认为txt
            }

            // 获取是否包含时间戳选项
            val includeTimestamp = timestampSwitch.isChecked

            // 调用回调
            listener?.onExportOptionSelected(format, includeTimestamp)
            dismiss()
        }

        // 设置取消按钮点击事件
        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "ExportOptionsDialog"

        fun newInstance(): ExportOptionsDialog {
            return ExportOptionsDialog()
        }
    }
}
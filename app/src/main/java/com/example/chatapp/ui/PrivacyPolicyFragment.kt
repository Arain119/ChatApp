package com.example.chatapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.chatapp.R

/**
 * 隐私政策页面Fragment
 */
class PrivacyPolicyFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_privacy_policy, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置隐私政策文本
        val policyTextView = view.findViewById<TextView>(R.id.privacy_policy_text)
        policyTextView.text = PRIVACY_POLICY_TEXT

        // 设置返回图标点击事件
        view.findViewById<ImageView>(R.id.back_icon).setOnClickListener {
            // 返回上一页
            activity?.supportFragmentManager?.popBackStack()
        }
    }

    companion object {
        // 隐私政策文本内容
        private const val PRIVACY_POLICY_TEXT = """
隐私政策
        """
    }
}
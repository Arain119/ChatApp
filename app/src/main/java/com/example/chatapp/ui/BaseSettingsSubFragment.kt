package com.example.chatapp.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment

/**
 * 设置子页面的基类
 * 提供通用功能
 */
abstract class BaseSettingsSubFragment : Fragment() {
    private val TAG = "BaseSettingsSubFragment"

    // 返回标题文本，由子类实现
    abstract fun getTitle(): String


    // 设置父级Fragment的回调接口
    interface NavigationCallback {
        fun navigateBack()
    }

    // 使用私有字段和公开方法避免冲突
    private var _navigationCallback: NavigationCallback? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "${this.javaClass.simpleName} 已加载，标题: ${getTitle()}")
    }

    // 提供getter方法
    protected fun getNavigationCallback(): NavigationCallback? = _navigationCallback

    // 提供setter方法
    fun setNavigationCallback(callback: NavigationCallback) {
        _navigationCallback = callback
        Log.d(TAG, "已设置导航回调: ${callback.javaClass.simpleName}")
    }

    // 子类调用此方法通知需要返回上一级
    protected fun notifyNavigationBack() {
        Log.d(TAG, "触发导航回调: ${this.javaClass.simpleName} -> ${_navigationCallback?.javaClass?.simpleName}")

        // 使用临时变量避免递归调用
        val callback = _navigationCallback
        if (callback != null) {
            callback.navigateBack()
        } else {
            Log.e(TAG, "导航回调为空，无法返回上一级")
        }
    }

    // 处理返回按钮点击事件的辅助方法
    fun handleBackPressed(): Boolean {
        Log.d(TAG, "子页面处理返回按钮点击")
        notifyNavigationBack()
        return true
    }

    override fun onDetach() {
        super.onDetach()
        // 清除引用避免内存泄漏
        _navigationCallback = null
        Log.d(TAG, "${this.javaClass.simpleName} 已分离，清除回调")
    }
}
package com.example.chatapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatapp.R
import com.example.chatapp.data.db.ChatEntity
import com.example.chatapp.utils.HapticUtils
import com.example.chatapp.viewmodel.ChatHistoryViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 聊天记录管理Fragment
 */
class ChatHistoryFragment : BaseSettingsSubFragment(), BaseSettingsSubFragment.NavigationCallback {

    private val TAG = "ChatHistoryFragment"
    private lateinit var viewModel: ChatHistoryViewModel
    private lateinit var allChatsAdapter: ChatHistoryAdapter
    private lateinit var searchResultsAdapter: SearchResultsAdapter

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var searchEditText: EditText
    private lateinit var clearSearchButton: ImageButton
    private lateinit var searchCard: MaterialCardView
    private lateinit var rootView: View
    private lateinit var contentCard: MaterialCardView

    // 回退栈名称常量
    private val CHAT_DETAIL_BACKSTACK = "chat_detail_backstack"

    // 搜索框默认和激活状态的阴影深度
    private val SEARCH_CARD_DEFAULT_ELEVATION = 8f // 默认阴影
    private val SEARCH_CARD_ACTIVE_ELEVATION = 16f // 激活时阴影

    // 用于跟踪已设置触摸监听器的视图
    private val viewsWithTouchListener = ConcurrentHashMap<View, Boolean>()

    override fun getTitle(): String = "聊天记录"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化ViewModel
        viewModel = ViewModelProvider(this).get(ChatHistoryViewModel::class.java)

        // 初始化适配器
        allChatsAdapter = ChatHistoryAdapter(
            onEnterClick = { chatId -> navigateToChat(chatId) },
            onDeleteClick = { chatId -> confirmDeleteChat(chatId) }
        )

        searchResultsAdapter = SearchResultsAdapter { chatId ->
            navigateToChat(chatId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 延迟入场动画
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        // 保存根视图引用
        rootView = view

        // 初始化视图
        recyclerView = view.findViewById(R.id.recycler_view)
        emptyView = view.findViewById(R.id.empty_view)
        searchEditText = view.findViewById(R.id.search_edit_text)
        clearSearchButton = view.findViewById(R.id.clear_search_button)
        searchCard = view.findViewById(R.id.search_card)
        contentCard = view.findViewById(R.id.content_card)

        // 设置内容卡片初始状态，准备动画
        setupContentCardAnimation()

        // 设置搜索框点击动效
        setupSearchCardAnimation()

        // 使用更可靠的方式设置点击外部区域处理
        setupOutsideTouchHandler()

        // 初始化新建对话按钮并添加弹跳动画效果
        val fabNewChat = view.findViewById<FloatingActionButton>(R.id.fab_new_chat)
        setupFabAnimation(fabNewChat)

        // 设置布局管理器
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // 设置默认适配器
        recyclerView.adapter = allChatsAdapter

        // 设置搜索功能
        setupSearch()

        // 观察数据变化
        observeViewModel()
    }

    /**
     * 设置内容卡片动画
     */
    private fun setupContentCardAnimation() {
        // 初始状态 - 透明度为0，稍微向下位移
        contentCard.alpha = 0f
        contentCard.translationY = 50f

        // 创建动画
        contentCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .setStartDelay(200L)
            .start()
    }

    /**
     * 设置浮动按钮动画
     */
    private fun setupFabAnimation(fab: FloatingActionButton) {
        // 设置初始状态
        fab.scaleX = 0f
        fab.scaleY = 0f
        fab.alpha = 0f

        // 设置点击事件
        fab.setOnClickListener {
            // 添加触觉反馈
            HapticUtils.performViewHapticFeedback(it)

            // 创建弹跳动画效果
            animateFabPress(fab) {
                // 动画结束后创建聊天
                createNewChat()
            }
        }

        // 入场动画
        fab.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.5f))
            .setStartDelay(600L) // 延迟显示，让其他元素先显示
            .start()
    }

    /**
     * 浮动按钮按压动画
     */
    private fun animateFabPress(fab: FloatingActionButton, onComplete: () -> Unit) {
        // 按下动画
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(fab, "scaleX", 0.85f),
                ObjectAnimator.ofFloat(fab, "scaleY", 0.85f)
            )
            duration = 100
            interpolator = DecelerateInterpolator()
        }

        // 弹起动画
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(fab, "scaleX", 1.1f),
                ObjectAnimator.ofFloat(fab, "scaleY", 1.1f)
            )
            duration = 200
            interpolator = DecelerateInterpolator()
        }

        // 恢复正常大小
        val scaleNormal = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(fab, "scaleX", 1f),
                ObjectAnimator.ofFloat(fab, "scaleY", 1f)
            )
            duration = 150
            interpolator = OvershootInterpolator()
        }

        // 按顺序播放动画
        val sequence = AnimatorSet()
        sequence.playSequentially(scaleDown, scaleUp, scaleNormal)

        // 动画结束后执行操作
        sequence.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                onComplete()
            }
        })

        sequence.start()
    }

    /**
     * 使用更可靠的方式处理点击外部区域隐藏键盘
     */
    private fun setupOutsideTouchHandler() {
        // 为整个RecyclerView添加触摸监听器
        recyclerView.setOnTouchListener { _, _ ->
            if (searchEditText.hasFocus()) {
                searchEditText.clearFocus()
                hideKeyboard()
                animateSearchBarFocusLost()
                return@setOnTouchListener true
            }
            false
        }

        // 为空视图添加触摸监听器
        emptyView.setOnTouchListener { _, _ ->
            if (searchEditText.hasFocus()) {
                searchEditText.clearFocus()
                hideKeyboard()
                animateSearchBarFocusLost()
                return@setOnTouchListener true
            }
            false
        }

        // 为根视图添加触摸监听器 - 用于处理其他区域的点击
        rootView.setOnTouchListener { _, event ->
            if (searchEditText.hasFocus() && event.action == MotionEvent.ACTION_DOWN) {
                // 检查点击位置是否在搜索卡片外部
                val searchCardLocation = IntArray(2)
                searchCard.getLocationOnScreen(searchCardLocation)

                val searchCardRect = android.graphics.Rect(
                    searchCardLocation[0],
                    searchCardLocation[1],
                    searchCardLocation[0] + searchCard.width,
                    searchCardLocation[1] + searchCard.height
                )

                if (!searchCardRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    searchEditText.clearFocus()
                    hideKeyboard()
                    animateSearchBarFocusLost()
                    return@setOnTouchListener true
                }
            }
            false
        }

        // 添加一个全局布局监听器
        view?.viewTreeObserver?.addOnGlobalLayoutListener {
            // 当布局变化时，设置子视图的触摸监听器
            ensureTouchListeners()
        }
    }

    /**
     * 确保所有需要的触摸监听器都已设置
     */
    private fun ensureTouchListeners() {
        // 确保根视图的所有子视图（除了搜索卡片）都能处理触摸事件
        val rootView = view as? ViewGroup ?: return
        for (i in 0 until rootView.childCount) {
            val child = rootView.getChildAt(i)
            // 跳过搜索卡片本身
            if (child == searchCard) continue

            // 为每个子视图设置触摸监听器（如果尚未设置）
            if (!viewsWithTouchListener.containsKey(child)) {
                child.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN && searchEditText.hasFocus()) {
                        searchEditText.clearFocus()
                        hideKeyboard()
                        // 执行搜索框失去焦点动画
                        animateSearchBarFocusLost()
                        return@setOnTouchListener true
                    }
                    false
                }
                // 标记已设置触摸监听器
                viewsWithTouchListener[child] = true
            }
        }
    }

    /**
     * 专门用于搜索框失去焦点的动画
     */
    private fun animateSearchBarFocusLost() {
        // 创建失去焦点动画
        val focusLossAnimation = AnimatorSet()

        // 缩小动画
        val scaleDownX = ObjectAnimator.ofFloat(searchCard, "scaleX", searchCard.scaleX, 1.0f)
        val scaleDownY = ObjectAnimator.ofFloat(searchCard, "scaleY", searchCard.scaleY, 1.0f)
        // 降低阴影
        val elevationDown = ObjectAnimator.ofFloat(searchCard, "cardElevation",
            searchCard.cardElevation, SEARCH_CARD_DEFAULT_ELEVATION)

        // 组合动画
        focusLossAnimation.playTogether(scaleDownX, scaleDownY, elevationDown)
        focusLossAnimation.duration = 150 // 加快动画以避免超时
        focusLossAnimation.interpolator = DecelerateInterpolator()

        // 启动动画
        focusLossAnimation.start()

        // 恢复原始位置
        searchCard.animate()
            .translationY(0f)
            .setDuration(150) // 加快动画以避免超时
            .start()

        // 恢复搜索图标颜色
        val searchIcon = searchEditText.compoundDrawables[0]
        searchIcon?.setTint(ContextCompat.getColor(requireContext(), R.color.text_secondary))
    }

    /**
     * 改进的键盘隐藏方法，确保在主线程执行并包含错误处理
     */
    private fun hideKeyboard() {
        try {
            val activity = activity ?: return
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentFocus = activity.currentFocus ?: View(activity)

            // 确保在主线程执行
            Handler(Looper.getMainLooper()).post {
                imm?.hideSoftInputFromWindow(currentFocus.windowToken, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "隐藏键盘出错: ${e.message}")
        }
    }

    /**
     * 设置搜索卡片的高级动画效果
     */
    private fun setupSearchCardAnimation() {
        // 存储原始的阴影深度和大小
        val originalElevation = searchCard.cardElevation
        val originalScaleX = searchCard.scaleX
        val originalScaleY = searchCard.scaleY

        // 入场动画
        searchCard.alpha = 0f
        searchCard.translationY = -50f
        searchCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 设置搜索框获得焦点时的动画
        searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 创建获得焦点时的动画序列
                val focusGainAnimSet = AnimatorSet()

                // 第一阶段：轻微缩小
                val initialScaleX = ObjectAnimator.ofFloat(searchCard, "scaleX", originalScaleX, 0.97f)
                val initialScaleY = ObjectAnimator.ofFloat(searchCard, "scaleY", originalScaleY, 0.97f)
                val initialAnimSet = AnimatorSet()
                initialAnimSet.playTogether(initialScaleX, initialScaleY)
                initialAnimSet.duration = 100

                // 第二阶段：弹性放大并增加阴影
                val finalScaleX = ObjectAnimator.ofFloat(searchCard, "scaleX", 0.97f, 1.03f)
                val finalScaleY = ObjectAnimator.ofFloat(searchCard, "scaleY", 0.97f, 1.03f)
                val elevationUp = ObjectAnimator.ofFloat(searchCard, "cardElevation", originalElevation, SEARCH_CARD_ACTIVE_ELEVATION)
                val finalAnimSet = AnimatorSet()
                finalAnimSet.playTogether(finalScaleX, finalScaleY, elevationUp)
                finalAnimSet.duration = 200 // 减少时间避免超时
                finalAnimSet.interpolator = OvershootInterpolator(1.5f)

                // 第三阶段：稳定到稍大状态
                val settleScaleX = ObjectAnimator.ofFloat(searchCard, "scaleX", 1.03f, 1.02f)
                val settleScaleY = ObjectAnimator.ofFloat(searchCard, "scaleY", 1.03f, 1.02f)
                val settleAnimSet = AnimatorSet()
                settleAnimSet.playTogether(settleScaleX, settleScaleY)
                settleAnimSet.duration = 100
                settleAnimSet.interpolator = DecelerateInterpolator()

                // 按顺序播放动画
                focusGainAnimSet.playSequentially(initialAnimSet, finalAnimSet, settleAnimSet)
                focusGainAnimSet.start()

                // 使搜索图标颜色更深
                val searchIcon = searchEditText.compoundDrawables[0]
                searchIcon?.setTint(ContextCompat.getColor(requireContext(), R.color.primary))

                // 向上移动一点点，增加浮动感 - 动画时间减少避免超时
                searchCard.animate().translationY(-6f).setDuration(200).start()

                // 添加触觉反馈
                HapticUtils.performHapticFeedback(requireContext())
            } else {
                // 使用专门的失去焦点动画方法
                animateSearchBarFocusLost()
            }
        }

        // 设置点击整个搜索卡片也能触发搜索框获得焦点
        searchCard.setOnClickListener {
            // 添加轻微的点击动画反馈
            val clickFeedback = AnimatorSet()
            val scaleDown = ObjectAnimator.ofFloat(searchCard, "scaleX", searchCard.scaleX, searchCard.scaleX * 0.98f)
            val scaleDown2 = ObjectAnimator.ofFloat(searchCard, "scaleY", searchCard.scaleY, searchCard.scaleY * 0.98f)
            clickFeedback.playTogether(scaleDown, scaleDown2)
            clickFeedback.duration = 50
            clickFeedback.interpolator = DecelerateInterpolator()

            val scaleUp = ObjectAnimator.ofFloat(searchCard, "scaleX", searchCard.scaleX * 0.98f, searchCard.scaleX)
            val scaleUp2 = ObjectAnimator.ofFloat(searchCard, "scaleY", searchCard.scaleY * 0.98f, searchCard.scaleY)
            val scaleUpSet = AnimatorSet()
            scaleUpSet.playTogether(scaleUp, scaleUp2)
            scaleUpSet.duration = 100
            scaleUpSet.interpolator = OvershootInterpolator(2f)

            clickFeedback.play(scaleUpSet).after(clickFeedback)
            clickFeedback.start()

            // 让搜索框获取焦点，会触发上面的焦点变化监听器
            searchEditText.requestFocus()

            // 显示键盘
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // 实现 NavigationCallback 接口的方法
    override fun navigateBack() {
        // 处理从子Fragment返回到当前Fragment的逻辑
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
        } else {
            notifyNavigationBack()
        }
    }

    // 添加新建聊天的方法
    private fun createNewChat() {
        // 通知Activity创建新对话
        requireActivity().run {
            if (this is ChatSelectionListener) {
                this.onNewChatRequested()

                // 返回上级界面
                notifyNavigationBack()
            }
        }
    }

    private fun setupSearch() {
        // 设置搜索文本变化监听
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()

                if (query.isEmpty()) {
                    // 清空搜索，显示所有聊天
                    clearSearchButton.visibility = View.GONE
                    loadAllChats()
                } else {
                    // 显示清除按钮 - 添加动画效果
                    if (clearSearchButton.visibility != View.VISIBLE) {
                        clearSearchButton.alpha = 0f
                        clearSearchButton.visibility = View.VISIBLE
                        clearSearchButton.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }

                    // 执行搜索
                    viewModel.searchMessages(query)
                }
            }
        })

        // 设置清除搜索按钮点击事件
        clearSearchButton.setOnClickListener { view ->
            // 添加按钮缩放动画
            view.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(100)
                .withEndAction {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()

                    // 清除搜索内容
                    searchEditText.setText("")

                    // 带动画效果隐藏清除按钮
                    clearSearchButton.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            clearSearchButton.visibility = View.GONE
                        }
                        .start()

                    loadAllChats()
                }
                .start()

            // 添加触觉反馈
            HapticUtils.performViewHapticFeedback(view)
        }
    }

    private fun loadAllChats() {
        recyclerView.adapter = allChatsAdapter
        viewModel.loadAllChats()
    }

    private fun observeViewModel() {
        // 观察所有聊天记录
        lifecycleScope.launch {
            viewModel.allChats.collect { chats ->
                allChatsAdapter.submitList(chats)
                updateEmptyViewVisibility(chats.isEmpty())
            }
        }

        // 观察搜索结果
        lifecycleScope.launch {
            viewModel.searchResults.collect { results ->
                if (searchEditText.text.toString().trim().isNotEmpty()) {
                    recyclerView.adapter = searchResultsAdapter
                    searchResultsAdapter.submitList(results)
                    updateEmptyViewVisibility(results.isEmpty())
                }
            }
        }
    }

    private fun updateEmptyViewVisibility(isEmpty: Boolean) {
        if (isEmpty) {
            // 添加淡入动画
            if (emptyView.visibility != View.VISIBLE) {
                emptyView.alpha = 0f
                emptyView.visibility = View.VISIBLE
                emptyView.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }

            // 根据当前状态设置不同的提示文本
            if (recyclerView.adapter == searchResultsAdapter) {
                emptyView.text = "未找到符合条件的聊天记录"
            } else {
                emptyView.text = "暂无聊天记录"
            }
        } else {
            // 如果已显示，添加淡出动画
            if (emptyView.visibility == View.VISIBLE) {
                emptyView.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        emptyView.visibility = View.GONE
                    }
                    .start()
            } else {
                emptyView.visibility = View.GONE
            }
        }
    }

    /**
     * 打开聊天详情页面
     */
    private fun navigateToChat(chatId: String) {
        try {
            // 检查Activity是否存在
            val activity = activity ?: return

            // 创建聊天详情Fragment实例
            val detailFragment = ChatDetailFragment.newInstance(chatId)

            // 设置导航回调
            detailFragment.setNavigationCallback(object : BaseSettingsSubFragment.NavigationCallback {
                override fun navigateBack() {
                    // 返回时弹出回退栈
                    activity.supportFragmentManager.popBackStack()

                    // 如果需要刷新数据
                    viewModel.loadAllChats()
                }
            })

            // 使用Activity的FragmentManager添加到Activity的内容区域
            activity.supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
                .replace(android.R.id.content, detailFragment)  // 使用Activity的内容视图ID
                .addToBackStack(CHAT_DETAIL_BACKSTACK)
                .commit()

            // 添加触觉反馈
            HapticUtils.performHapticFeedback(requireContext())
        } catch (e: Exception) {
            Log.e(TAG, "导航到聊天详情失败: ${e.message}", e)
        }
    }

    private fun confirmDeleteChat(chatId: String) {
        // 添加触觉反馈
        HapticUtils.performHapticFeedback(requireContext())

        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("删除对话")
            .setMessage("确定要删除这个对话吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    viewModel.deleteChat(chatId)
                }
                Toast.makeText(requireContext(), "对话已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.dialog_rounded_bg))
            .show()
    }

    /**
     * 聊天记录适配器
     */
    inner class ChatHistoryAdapter(
        private val onEnterClick: (String) -> Unit,
        private val onDeleteClick: (String) -> Unit
    ) : RecyclerView.Adapter<ChatHistoryAdapter.ChatViewHolder>() {

        private var chats: List<ChatEntity> = emptyList()
        private var lastAnimatedPosition = -1 // 跟踪最后动画的位置

        fun submitList(newList: List<ChatEntity>) {
            chats = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_history, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val chat = chats[position]
            holder.bind(chat)

            // 添加入场动画，只有新出现的项目才有动画
            if (position > lastAnimatedPosition) {
                animateItem(holder.itemView, position)
                lastAnimatedPosition = position
            }
        }

        /**
         * 为列表项添加入场动画
         */
        private fun animateItem(view: View, position: Int) {
            // 设置初始状态
            view.translationY = 50f
            view.alpha = 0f

            // 创建动画
            view.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator(1.2f))
                .setStartDelay(position * 50L) // 错开显示时间
                .start()
        }

        override fun getItemCount(): Int = chats.size

        inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleTextView: TextView = itemView.findViewById(R.id.chat_title)
            private val dateTextView: TextView = itemView.findViewById(R.id.chat_date)
            private val modelTextView: TextView = itemView.findViewById(R.id.chat_model)
            private val enterButton: ImageButton = itemView.findViewById(R.id.enter_button)
            private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)
            private val cardView: MaterialCardView = itemView as MaterialCardView

            // 触摸状态追踪器
            private var isPressed = false
            private var isTouchValid = false

            fun bind(chat: ChatEntity) {
                titleTextView.text = chat.title

                // 格式化日期
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                dateTextView.text = dateFormat.format(chat.updatedAt)

                // 显示模型信息
                modelTextView.text = chat.modelType

                // 设置按钮点击事件
                enterButton.setOnClickListener {
                    HapticUtils.performViewHapticFeedback(it)
                    animateButtonPress(it) {
                        onEnterClick(chat.id)
                    }
                }

                deleteButton.setOnClickListener {
                    HapticUtils.performViewHapticFeedback(it)
                    animateButtonPress(it) {
                        onDeleteClick(chat.id)
                    }
                }

                // 增强卡片的立体感
                setupCardTouchAnimation()

                // 设置整个卡片的点击事件
                cardView.setOnClickListener {
                    HapticUtils.performHapticFeedback(requireContext())
                    onEnterClick(chat.id)
                }
            }

            /**
             * 按钮按压动画
             */
            private fun animateButtonPress(button: View, action: () -> Unit) {
                val scaleDown = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(button, "scaleX", 0.8f),
                        ObjectAnimator.ofFloat(button, "scaleY", 0.8f)
                    )
                    duration = 100
                }

                val scaleUp = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(button, "scaleX", 1f),
                        ObjectAnimator.ofFloat(button, "scaleY", 1f)
                    )
                    duration = 100
                    interpolator = OvershootInterpolator(2f)
                }

                val sequence = AnimatorSet()
                sequence.playSequentially(scaleDown, scaleUp)
                sequence.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        action()
                    }
                })
                sequence.start()
            }

            /**
             * 设置卡片触摸动画
             */
            private fun setupCardTouchAnimation() {
                cardView.setOnTouchListener { view, event ->
                    val card = view as MaterialCardView

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // 记录按下状态
                            isPressed = true
                            isTouchValid = true

                            // 创建按下动画
                            val scaleDownX = ObjectAnimator.ofFloat(card, "scaleX", 0.97f)
                            val scaleDownY = ObjectAnimator.ofFloat(card, "scaleY", 0.97f)
                            val elevationDown = ObjectAnimator.ofFloat(card, "cardElevation", card.cardElevation, card.cardElevation * 0.7f)

                            val scaleDown = AnimatorSet()
                            scaleDown.playTogether(scaleDownX, scaleDownY, elevationDown)
                            scaleDown.duration = 100
                            scaleDown.start()

                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isPressed && isTouchValid) {
                                // 创建释放动画
                                val scaleUpX = ObjectAnimator.ofFloat(card, "scaleX", 1f)
                                val scaleUpY = ObjectAnimator.ofFloat(card, "scaleY", 1f)
                                val elevationUp = ObjectAnimator.ofFloat(card, "cardElevation", card.cardElevation, 6f) // 恢复默认高度

                                val scaleUp = AnimatorSet()
                                scaleUp.playTogether(scaleUpX, scaleUpY, elevationUp)
                                scaleUp.duration = 150
                                scaleUp.interpolator = OvershootInterpolator(1.2f)
                                scaleUp.start()

                                // 执行点击事件
                                if (event.x >= 0 && event.x <= card.width &&
                                    event.y >= 0 && event.y <= card.height) {
                                    view.performClick()
                                }
                            }

                            // 重置状态
                            isPressed = false
                            isTouchValid = false
                            false
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            if (isPressed) {
                                // 取消时恢复原始状态
                                val scaleUpX = ObjectAnimator.ofFloat(card, "scaleX", 1f)
                                val scaleUpY = ObjectAnimator.ofFloat(card, "scaleY", 1f)
                                val elevationUp = ObjectAnimator.ofFloat(card, "cardElevation", card.cardElevation, 6f)

                                val scaleUp = AnimatorSet()
                                scaleUp.playTogether(scaleUpX, scaleUpY, elevationUp)
                                scaleUp.duration = 100
                                scaleUp.start()
                            }

                            // 重置状态
                            isPressed = false
                            isTouchValid = false
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // 如果手指移出卡片区域，判定为取消触摸
                            if (isPressed && (event.x < 0 || event.x > card.width ||
                                        event.y < 0 || event.y > card.height)) {
                                isTouchValid = false

                                // 恢复原始状态
                                val scaleUpX = ObjectAnimator.ofFloat(card, "scaleX", 1f)
                                val scaleUpY = ObjectAnimator.ofFloat(card, "scaleY", 1f)
                                val elevationUp = ObjectAnimator.ofFloat(card, "cardElevation", card.cardElevation, 6f)

                                val scaleUp = AnimatorSet()
                                scaleUp.playTogether(scaleUpX, scaleUpY, elevationUp)
                                scaleUp.duration = 100
                                scaleUp.start()
                            }
                            false
                        }
                        else -> false
                    }
                }
            }
        }
    }

    /**
     * 搜索结果适配器
     */
    inner class SearchResultsAdapter(
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<SearchResultsAdapter.SearchResultViewHolder>() {

        private var results: List<com.example.chatapp.data.db.ChatDatabaseHelper.MessageWithChat> = emptyList()
        private var lastAnimatedPosition = -1 // 跟踪最后动画的位置

        fun submitList(newList: List<com.example.chatapp.data.db.ChatDatabaseHelper.MessageWithChat>) {
            results = newList
            lastAnimatedPosition = -1 // 重置动画位置，确保新结果也有动画
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_result, parent, false)
            return SearchResultViewHolder(view)
        }

        override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
            val result = results[position]
            holder.bind(result)

            // 添加入场动画
            if (position > lastAnimatedPosition) {
                animateItem(holder.itemView, position)
                lastAnimatedPosition = position
            }
        }

        /**
         * 为搜索结果项添加入场动画
         */
        private fun animateItem(view: View, position: Int) {
            // 设置初始状态
            view.translationY = 50f
            view.alpha = 0f

            // 创建动画
            view.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator(1.2f))
                .setStartDelay(position * 50L) // 错开显示时间
                .start()
        }

        override fun getItemCount(): Int = results.size

        inner class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val chatTitleTextView: TextView = itemView.findViewById(R.id.chat_title)
            private val messageContentTextView: TextView = itemView.findViewById(R.id.message_content)
            private val dateTextView: TextView = itemView.findViewById(R.id.result_date)
            private val cardView: MaterialCardView = itemView as MaterialCardView

            // 触摸状态追踪
            private var isPressed = false

            fun bind(result: com.example.chatapp.data.db.ChatDatabaseHelper.MessageWithChat) {
                chatTitleTextView.text = result.chat.title

                // 显示消息内容片段
                val content = result.message.content
                messageContentTextView.text = if (content.length > 50) {
                    content.substring(0, 50) + "..."
                } else {
                    content
                }

                // 格式化日期
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                dateTextView.text = dateFormat.format(result.message.timestamp)

                // 设置点击事件
                itemView.setOnClickListener {
                    HapticUtils.performHapticFeedback(requireContext())
                    onItemClick(result.chat.id)
                }

                // 增强卡片的立体感
                setupSearchCardAnimation(cardView, result.chat.id)
            }

            /**
             * 设置搜索结果卡片触摸动画
             */
            private fun setupSearchCardAnimation(card: MaterialCardView, chatId: String) {
                card.setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isPressed = true

                            // 缩小并降低高度
                            val scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 0.98f)
                            val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 0.98f)
                            val elevationDown = ObjectAnimator.ofFloat(card, "cardElevation", card.cardElevation, card.cardElevation * 0.8f)

                            val scaleDown = AnimatorSet()
                            scaleDown.playTogether(scaleDownX, scaleDownY, elevationDown)
                            scaleDown.duration = 100
                            scaleDown.start()

                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isPressed) {
                                // 恢复原始大小和高度，添加弹性效果
                                val scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 1.02f)
                                val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 1.02f)
                                val elevationUp = ObjectAnimator.ofFloat(card, "cardElevation", card.cardElevation, 6f)

                                val scaleUp = AnimatorSet()
                                scaleUp.playTogether(scaleUpX, scaleUpY, elevationUp)
                                scaleUp.duration = 150
                                scaleUp.interpolator = OvershootInterpolator(1.5f)

                                // 最终归位动画
                                val scaleFinal = AnimatorSet()
                                scaleFinal.playTogether(
                                    ObjectAnimator.ofFloat(view, "scaleX", 1f),
                                    ObjectAnimator.ofFloat(view, "scaleY", 1f)
                                )
                                scaleFinal.duration = 100

                                // 组合动画序列
                                val sequence = AnimatorSet()
                                sequence.playSequentially(scaleUp, scaleFinal)
                                sequence.start()

                                // 触发点击
                                if (event.x >= 0 && event.x <= card.width &&
                                    event.y >= 0 && event.y <= card.height) {
                                    onItemClick(chatId)
                                }
                            }

                            isPressed = false
                            false
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            if (isPressed) {
                                // 取消时恢复原状
                                val scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 1f)
                                val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 1f)
                                val elevationUp = ObjectAnimator.ofFloat(card, "cardElevation", card.cardElevation, 6f)

                                val scaleUp = AnimatorSet()
                                scaleUp.playTogether(scaleUpX, scaleUpY, elevationUp)
                                scaleUp.duration = 100
                                scaleUp.start()
                            }

                            isPressed = false
                            false
                        }
                        else -> false
                    }
                }
            }
        }
    }

    /**
     * 回调接口，用于通知Activity处理聊天选择事件
     */
    interface ChatSelectionListener {
        fun onChatSelected(chatId: String)
        fun onNewChatRequested()
    }
}
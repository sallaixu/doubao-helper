package com.doubao.helper.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.doubao.helper.App
import com.doubao.helper.R
import com.doubao.helper.model.CallButtonConfig
import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.DebugNodeInfo
import com.doubao.helper.model.MonitorRule
import com.doubao.helper.model.Sender
import com.doubao.helper.wakeup.WakeupWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 管理悬浮窗的三种状态：
 * - 小悬浮球（可拖动），始终显示
 * - 全屏对话面板，点击小悬浮球展开
 * - 选区模式，全屏透明拦截触摸，点击选择节点
 */
class OverlayManager(private val context: Context, private val windowManager: WindowManager) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 小悬浮球
    private var floatingBall: TextView? = null
    private var floatingBallParams: WindowManager.LayoutParams? = null

    // 全屏对话面板
    private var panelView: View? = null  // 实际添加到 WindowManager 的 view（可能是 RotatedContainer）
    private var panelInnerView: View? = null  // inflate 出来的面板内容
    private var panelParams: WindowManager.LayoutParams? = null
    private var messageContainer: LinearLayout? = null
    private var scrollView: ScrollView? = null
    private var tvStatus: TextView? = null
    private var btnPause: Button? = null
    private var rotatedContainer: RotatedContainer? = null

    // 选区模式透明覆盖层
    private var selectionOverlay: View? = null
    private var selectionParams: WindowManager.LayoutParams? = null

    // 选中节点确认 UI
    private var confirmView: LinearLayout? = null
    private var confirmParams: WindowManager.LayoutParams? = null

    // 待机模式
    private var standbyView: StandbyView? = null
    private var standbyParams: WindowManager.LayoutParams? = null
    private val standbyHandler = Handler(Looper.getMainLooper())
    private val burnInShiftRunnable = object : Runnable {
        override fun run() {
            standbyView?.applyBurnInShift()
            standbyView?.updateTime()
            standbyHandler.postDelayed(this, 60_000) // 每分钟更新
        }
    }

    // 唤醒词检测
    private var wakeupDetector: WakeupWordDetector? = null

    var onClose: (() -> Unit)? = null

    private var isPanelExpanded = false
    private var isPaused = false

    // 悬浮球拖动相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    fun show() {
        showFloatingBall()
    }

    // ======== 小悬浮球 ========

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBall() {
        if (floatingBall != null) return

        val app = context.applicationContext as App

        floatingBall = TextView(context).apply {
            text = if (app.isSelectionMode) "🎯" else "🤖"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(
                if (app.isSelectionMode) Color.parseColor("#CCFF5722") else Color.parseColor("#CC6750A4")
            )
            gravity = Gravity.CENTER
            val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics).toInt()
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        }

        floatingBallParams = WindowManager.LayoutParams(
            dp(48), dp(48),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        floatingBall?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatingBallParams?.x ?: 0
                    initialY = floatingBallParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isDragging = true
                    }
                    if (isDragging) {
                        floatingBallParams?.x = initialX + dx.toInt()
                        floatingBallParams?.y = initialY + dy.toInt()
                        floatingBall?.let { windowManager.updateViewLayout(it, floatingBallParams) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        if (app.isSelectionMode) {
                            // 选区模式下点击悬浮球退出选区
                            exitSelectionMode()
                        } else {
                            expandPanel()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingBall, floatingBallParams)
    }

    private fun removeFloatingBall() {
        floatingBall?.let { windowManager.removeView(it) }
        floatingBall = null
    }

    // ======== 全屏对话面板 ========

    @SuppressLint("ClickableViewAccessibility")
    private fun expandPanel() {
        if (isPanelExpanded) return

        val innerView = LayoutInflater.from(context).inflate(R.layout.overlay_view, null as ViewGroup?)
        panelInnerView = innerView

        messageContainer = innerView.findViewById(R.id.messageContainer)
        scrollView = innerView.findViewById(R.id.scrollView)
        tvStatus = innerView.findViewById(R.id.tvStatus)
        btnPause = innerView.findViewById(R.id.btnPause)

        val btnClose = innerView.findViewById<ImageView>(R.id.btnClose)
        val btnMinimize = innerView.findViewById<ImageView>(R.id.btnMinimize)
        val btnDebug = innerView.findViewById<Button>(R.id.btnDebug)
        val btnMicSelect = innerView.findViewById<Button>(R.id.btnMicSelect)

        btnClose.setOnClickListener { onClose?.invoke() }
        btnMinimize.setOnClickListener { collapsePanel() }
        btnDebug.setOnClickListener {
            // 收起面板，进入监听规则选区模式
            collapsePanel()
            enterSelectionMode("monitor")
        }
        btnMicSelect?.setOnClickListener {
            // 收起面板，进入麦克风按钮选区模式
            collapsePanel()
            enterSelectionMode("mic")
        }

        btnPause?.setOnClickListener {
            isPaused = !isPaused
            btnPause?.text = if (isPaused) context.getString(R.string.overlay_btn_resume) else context.getString(R.string.overlay_btn_pause)
            tvStatus?.text = if (isPaused) context.getString(R.string.overlay_status_paused) else context.getString(R.string.overlay_status_listening)
            tvStatus?.setTextColor(if (isPaused) Color.parseColor("#FFFF9800") else Color.parseColor("#FF4CAF50"))
        }

        val portrait = isPortrait()

        if (portrait) {
            // 竖屏：用 RotatedContainer 包裹面板，旋转 90°
            val container = RotatedContainer(context)
            container.rotationDegrees = 90
            container.addView(innerView)
            panelView = container
            rotatedContainer = container
        } else {
            // 横屏：直接使用面板
            panelView = innerView
            rotatedContainer = null
        }

        panelParams = fullscreenParams()

        windowManager.addView(panelView, panelParams)

        // 内容区域留白：padding 加在 innerView 上（不在 RotatedContainer 上）
        // 这样窗口和容器始终全屏，内容不顶边
        val sidePadding = dp(32) // 左右边距，避免和状态栏/导航栏重叠
        innerView.setPadding(sidePadding, 0, sidePadding, 0)

        removeFloatingBall()
        isPanelExpanded = true
    }

    private fun collapsePanel() {
        if (!isPanelExpanded) return

        panelView?.let { windowManager.removeView(it) }
        panelView = null
        panelInnerView = null
        rotatedContainer = null
        messageContainer = null
        scrollView = null
        tvStatus = null
        btnPause = null
        isPanelExpanded = false
        lastTextViewByRegion.clear()

        showFloatingBall()
    }

    // ======== 选区模式 ========

    @SuppressLint("ClickableViewAccessibility")
    fun enterSelectionMode(type: String = "monitor") {
        val app = context.applicationContext as App
        app.isSelectionMode = true
        app.selectionModeType = type

        // 收起面板
        if (isPanelExpanded) {
            collapsePanel()
        }

        // 先移除悬浮球（后面会重新添加到覆盖层之上）
        removeFloatingBall()

        // 添加全屏透明覆盖层拦截触摸
        selectionOverlay = View(context).apply {
            setBackgroundColor(Color.parseColor("#15000000")) // 轻微半透明提示
        }

        selectionParams = fullscreenParams()

        selectionOverlay?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                handleSelectionClick(x, y)
                true
            } else {
                false
            }
        }

        windowManager.addView(selectionOverlay, selectionParams)

        // 后添加悬浮球，确保在覆盖层之上（z-order更高），可以点击退出
        showFloatingBall()
    }

    private fun exitSelectionMode() {
        val app = context.applicationContext as App
        app.isSelectionMode = false

        // 移除选区覆盖层
        selectionOverlay?.let { windowManager.removeView(it) }
        selectionOverlay = null

        // 移除确认 UI
        confirmView?.let { windowManager.removeView(it) }
        confirmView = null

        // 更新悬浮球外观
        removeFloatingBall()
        showFloatingBall()
    }

    private fun handleSelectionClick(x: Int, y: Int) {
        val app = context.applicationContext as App

        // 通话按钮选区：分两步——先选挂断按钮，再选拨通按钮
        if (app.selectionModeType == "mic") {
            if (app.pendingHangUpX == 0 && app.pendingHangUpY == 0) {
                // 第一步：选择挂断按钮
                app.pendingHangUpX = x
                app.pendingHangUpY = y

                // 退出选区让点击透传，然后重新进入选区选第二个按钮
                exitSelectionMode()
                showMicStepToast("① 挂断按钮已记录，请再选拨通按钮位置")
                // 短暂延迟后自动重新进入选区模式
                standbyHandler.postDelayed({
                    enterSelectionMode("mic")
                }, 2500)
            } else {
                // 第二步：选拨通按钮
                app.setCallButtonConfig(CallButtonConfig(
                    hangUpX = app.pendingHangUpX,
                    hangUpY = app.pendingHangUpY,
                    callX = x,
                    callY = y
                ))
                app.pendingHangUpX = 0
                app.pendingHangUpY = 0

                // 退出选区让点击透传
                exitSelectionMode()
                showMicStepToast("✅ 通话按钮配置完成！")
            }
            return
        }

        // 监听规则选区：找节点并确认
        // 临时移除覆盖层以便无障碍服务能访问节点树
        selectionOverlay?.let { windowManager.removeView(it) }
        selectionOverlay = null

        // 通过 AccessibilityService 查找节点
        // 由于 AccessibilityService 是系统管理的，我们通过 App 中转
        scope.launch {
            // 使用节点查找
            val nodeInfo = findNodeFromAccessibility(x, y)
            if (nodeInfo != null) {
                app.emitSelectedNode(nodeInfo)
                showNodeConfirm(nodeInfo)
            } else {
                // 没找到节点，重新显示覆盖层
                showSelectionOverlay()
            }
        }
    }

    private fun findNodeFromAccessibility(x: Int, y: Int): DebugNodeInfo? {
        val app = context.applicationContext as App
        val result = app.chatRepository.requestNodeFind(x, y)
        return result
    }

    /**
     * 显示通话按钮配置提示 toast
     */
    private fun showMicStepToast(msg: String) {
        val toastView = TextView(context).apply {
            text = msg
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            gravity = Gravity.CENTER
        }

        val toastParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(toastView, toastParams)

        // 2秒后自动移除
        standbyHandler.postDelayed({
            try { windowManager.removeView(toastView) } catch (_: Exception) {}
        }, 2000)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showNodeConfirm(nodeInfo: DebugNodeInfo) {
        // 创建确认 UI
        confirmView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E0303030"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // 标题
        confirmView?.addView(TextView(context).apply {
            text = "选中节点"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        // 节点信息
        val info = buildString {
            append("viewId: ${if (nodeInfo.viewId.isNotEmpty()) nodeInfo.viewId else "(无)"}\n")
            append("class: ${nodeInfo.className.substringAfterLast(".")}\n")
            append("文本: ${if (nodeInfo.text.length > 80) nodeInfo.text.substring(0, 80) + "…" else nodeInfo.text}\n")
            append("区域: (${nodeInfo.bounds.left},${nodeInfo.bounds.top})-(${nodeInfo.bounds.right},${nodeInfo.bounds.bottom})\n")
            append("匹配方式: ${if (nodeInfo.viewId.isNotEmpty()) "viewId+区域" else "className+区域"}")
        }
        confirmView?.addView(TextView(context).apply {
            text = info
            setTextColor(Color.parseColor("#FFB0C4FF"))
            textSize = 14f
            setPadding(0, dp(8), 0, dp(12))
        })

        // 按钮行
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        buttonRow.addView(Button(context).apply {
            text = "取消"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#666666"))
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener {
                removeConfirmAndReshowOverlay()
            }
        })

        buttonRow.addView(Button(context).apply {
            val app = context.applicationContext as App
            text = if (app.selectionModeType == "mic") "保存麦克风" else "保存规则"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener {
                saveRuleFromNode(nodeInfo)
                removeConfirmAndReshowOverlay()
            }
        })

        confirmView?.addView(buttonRow)

        confirmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(confirmView, confirmParams)
    }

    private fun saveRuleFromNode(nodeInfo: DebugNodeInfo) {
        val app = context.applicationContext as App

        if (app.selectionModeType == "mic") {
            // 通话按钮配置现在在 handleSelectionClick 中两步完成，这里不再需要
        } else {
            // 保存监听规则
            val rule = MonitorRule(
                id = "rule_${System.currentTimeMillis()}",
                viewIdPattern = nodeInfo.viewId,
                className = nodeInfo.className,
                boundsRegion = android.graphics.Rect(nodeInfo.bounds),
                textMinLength = 2,
                description = "从选区创建: ${nodeInfo.text.take(20)}"
            )
            app.addRule(rule)
        }
    }

    private fun removeConfirmAndReshowOverlay() {
        confirmView?.let { windowManager.removeView(it) }
        confirmView = null
        showSelectionOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSelectionOverlay() {
        if (selectionOverlay != null) return

        // 先移除悬浮球
        removeFloatingBall()

        selectionOverlay = View(context).apply {
            setBackgroundColor(Color.parseColor("#15000000"))
        }

        selectionOverlay?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                handleSelectionClick(event.rawX.toInt(), event.rawY.toInt())
                true
            } else {
                false
            }
        }

        selectionParams = fullscreenParams()

        windowManager.addView(selectionOverlay, selectionParams)

        // 重新添加悬浮球，确保在覆盖层之上
        showFloatingBall()
    }

    // ======== 消息显示 ========

    // 记录每个区域最后一条消息的 TextView，用于续写更新
    private val lastTextViewByRegion = mutableMapOf<String, android.widget.TextView>()
    private var lastRegionKey: String? = null

    fun appendMessage(message: ChatMessage) {
        if (!isPanelExpanded || messageContainer == null) return

        val app = context.applicationContext as App
        val fontSize = app.chatRepository.getFontSize(context)
        val regionKey = message.regionKey

        // 检查是否是续写（同一区域的文本变长了）
        val lastTextView = lastTextViewByRegion[regionKey]
        if (lastTextView != null) {
            // 续写：更新现有 TextView 的文本
            lastTextView.text = message.content
            scrollView?.post { scrollView?.fullScroll(ScrollView.FOCUS_DOWN) }
            return
        }

        // 新消息：创建新的 TextView
        val textView = android.widget.TextView(context).apply {
            text = message.content
            setTextColor(Color.parseColor("#FFB0C4FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
            setPadding(0, dp(4), 0, dp(4))
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        messageContainer?.addView(textView)
        lastTextViewByRegion[regionKey] = textView

        // 当消息太多时，清理旧的区域映射防止内存泄漏
        if (lastTextViewByRegion.size > 50) {
            val keysToRemove = lastTextViewByRegion.keys.take(lastTextViewByRegion.size - 20)
            keysToRemove.forEach { lastTextViewByRegion.remove(it) }
        }

        scrollView?.post { scrollView?.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    fun clearMessages() {
        messageContainer?.removeAllViews()
        lastTextViewByRegion.clear()
    }

    /**
     * 屏幕方向变化时调用。如果面板展开中，收起再重新展开以适配新方向。
     */
    fun onDisplayRotationChanged() {
        if (isPanelExpanded) {
            collapsePanel()
            expandPanel()
        }
    }

    // ======== 待机模式 ========

    /**
     * 进入待机模式：关闭对话面板，显示万年历全屏。
     * 先尝试点击豆包挂断按钮关闭通话。
     */
    fun enterStandby() {
        val app = context.applicationContext as App
        if (app.isStandbyMode) return

        // 收起对话面板
        if (isPanelExpanded) {
            collapsePanel()
        }

        // 点击挂断按钮关闭通话
        val config = app.callButtonConfig
        if (config != null) {
            app.chatRepository.requestNodeClick(config.hangUpX, config.hangUpY)
        }

        app.isStandbyMode = true

        // 移除悬浮球
        removeFloatingBall()

        // 显示万年历待机视图
        val portrait = isPortrait()
        standbyView = StandbyView(context).apply {
            onTapToExit = {
                exitStandby()
            }
        }

        if (portrait) {
            val container = RotatedContainer(context)
            container.rotationDegrees = 90
            container.addView(standbyView)
            standbyParams = fullscreenParams()
            windowManager.addView(container, standbyParams)
        } else {
            standbyParams = fullscreenParams()
            windowManager.addView(standbyView, standbyParams)
        }

        // 启动防烧屏和时钟更新
        standbyHandler.postDelayed(burnInShiftRunnable, 60_000)

        // 延迟启动唤醒词检测（等待豆包 App 释放麦克风）
        standbyView?.showWakeupHint("⏳ 等待麦克风释放...")
        standbyHandler.postDelayed({
            startWakeupDetection()
        }, 3000)
    }

    /**
     * 退出待机模式：点击拨通按钮恢复通话，展开对话面板。
     */
    private fun exitStandby() {
        val app = context.applicationContext as App
        if (!app.isStandbyMode) return

        // 停止防烧屏更新
        standbyHandler.removeCallbacks(burnInShiftRunnable)

        // 停止唤醒词检测
        stopWakeupDetection()

        // 移除待机视图
        standbyView?.let {
            (it.parent as? View)?.let { parent ->
                windowManager.removeView(parent)
            } ?: windowManager.removeView(it)
        }
        standbyView = null

        app.isStandbyMode = false

        // 点击拨通按钮恢复通话
        val config = app.callButtonConfig
        if (config != null) {
            app.chatRepository.requestNodeClick(config.callX, config.callY)
        }

        // 重置超时计时
        app.chatRepository.resetLastMessageTime()

        // 显示悬浮球，然后自动展开对话面板
        showFloatingBall()
        scope.launch {
            kotlinx.coroutines.delay(500)
            expandPanel()
        }
    }

    // ======== 唤醒词检测 ========

    private fun startWakeupDetection() {
        // 检查录音权限
        if (!com.doubao.helper.util.PermissionChecker.hasRecordAudioPermission(context)) {
            Log.w(TAG, "无录音权限，唤醒词检测不可用")
            standbyView?.showWakeupHint("⚠️ 无录音权限，唤醒词不可用，请点击屏幕退出")
            return
        }

        stopWakeupDetection()

        val app = context.applicationContext as App
        val wakeupWord = app.chatRepository.getWakeupWord(context)
        wakeupDetector = WakeupWordDetector(context).apply {
            this.wakeupWord = wakeupWord
            onWakeup = {
                Log.i(TAG, "唤醒词触发，退出待机")
                exitStandby()
            }
            onError = { errorMsg ->
                Log.e(TAG, "唤醒词检测错误: $errorMsg")
                standbyView?.showWakeupHint("⚠️ $errorMsg  |  点击屏幕退出")
            }
        }
        // init + startListening（与 hiai 一致的两步流程）
        if (wakeupDetector?.init() == true) {
            wakeupDetector?.startListening()
            Log.i(TAG, "唤醒词检测已启动: $wakeupWord")
            standbyView?.showWakeupHint("🎤 说\"$wakeupWord\"唤醒  |  点击屏幕退出")
        } else {
            Log.e(TAG, "唤醒词检测器初始化失败")
            standbyView?.showWakeupHint("⚠️ 唤醒词初始化失败  |  点击屏幕退出")
        }
    }

    private fun stopWakeupDetection() {
        wakeupDetector?.release()
        wakeupDetector = null
    }

    fun destroy() {
        standbyHandler.removeCallbacks(burnInShiftRunnable)
        stopWakeupDetection()
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        panelInnerView = null
        rotatedContainer = null
        floatingBall?.let { windowManager.removeView(it) }
        floatingBall = null
        selectionOverlay?.let { windowManager.removeView(it) }
        selectionOverlay = null
        confirmView?.let { windowManager.removeView(it) }
        confirmView = null
        standbyView?.let {
            (it.parent as? View)?.let { parent ->
                windowManager.removeView(parent)
            } ?: windowManager.removeView(it)
        }
        standbyView = null
        scope.cancel()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
    }

    companion object {
        private const val TAG = "OverlayManager"
    }

    /**
     * 获取屏幕全屏尺寸（包含状态栏和导航栏区域）
     */
    private fun getFullscreenSize(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * 判断当前是否竖屏
     */
    private fun isPortrait(): Boolean {
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels < metrics.heightPixels
    }

    /**
     * 创建全屏 WindowManager.LayoutParams，覆盖状态栏和导航栏
     */
    private fun fullscreenParams(): WindowManager.LayoutParams {
        val (w, h) = getFullscreenSize()
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_FULLSCREEN
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }
}

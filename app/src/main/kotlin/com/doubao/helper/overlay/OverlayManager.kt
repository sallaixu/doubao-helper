package com.doubao.helper.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
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
import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.DebugNodeInfo
import com.doubao.helper.model.MonitorRule
import com.doubao.helper.model.Sender
import com.doubao.helper.service.DoubaoAccessibilityService
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
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var messageContainer: LinearLayout? = null
    private var scrollView: ScrollView? = null
    private var tvStatus: TextView? = null
    private var btnPause: Button? = null

    // 选区模式透明覆盖层
    private var selectionOverlay: View? = null
    private var selectionParams: WindowManager.LayoutParams? = null

    // 选中节点确认 UI
    private var confirmView: LinearLayout? = null
    private var confirmParams: WindowManager.LayoutParams? = null

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_view, null as ViewGroup?)
        panelView = view

        messageContainer = view.findViewById(R.id.messageContainer)
        scrollView = view.findViewById(R.id.scrollView)
        tvStatus = view.findViewById(R.id.tvStatus)
        btnPause = view.findViewById(R.id.btnPause)

        val btnClose = view.findViewById<ImageView>(R.id.btnClose)
        val btnMinimize = view.findViewById<ImageView>(R.id.btnMinimize)
        val btnDebug = view.findViewById<Button>(R.id.btnDebug)

        btnClose.setOnClickListener { onClose?.invoke() }
        btnMinimize.setOnClickListener { collapsePanel() }
        btnDebug.setOnClickListener {
            // 收起面板，进入选区模式
            collapsePanel()
            enterSelectionMode()
        }

        btnPause?.setOnClickListener {
            isPaused = !isPaused
            btnPause?.text = if (isPaused) context.getString(R.string.overlay_btn_resume) else context.getString(R.string.overlay_btn_pause)
            tvStatus?.text = if (isPaused) context.getString(R.string.overlay_status_paused) else context.getString(R.string.overlay_status_listening)
            tvStatus?.setTextColor(if (isPaused) Color.parseColor("#FFFF9800") else Color.parseColor("#FF4CAF50"))
        }

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(panelView, panelParams)
        removeFloatingBall()
        isPanelExpanded = true
    }

    private fun collapsePanel() {
        if (!isPanelExpanded) return

        panelView?.let { windowManager.removeView(it) }
        panelView = null
        messageContainer = null
        scrollView = null
        tvStatus = null
        btnPause = null
        isPanelExpanded = false

        showFloatingBall()
    }

    // ======== 选区模式 ========

    @SuppressLint("ClickableViewAccessibility")
    fun enterSelectionMode() {
        val app = context.applicationContext as App
        app.isSelectionMode = true

        // 收起面板
        if (isPanelExpanded) {
            collapsePanel()
        }

        // 更新悬浮球外观
        removeFloatingBall()
        showFloatingBall()

        // 添加全屏透明覆盖层拦截触摸
        selectionOverlay = View(context).apply {
            setBackgroundColor(Color.parseColor("#15000000")) // 轻微半透明提示
        }

        selectionParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

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
        // 直接用 App 的 ChatRepository 触发一次查找
        // 由于 AccessibilityService 实例由系统管理，这里用一种间接方式
        // 通过 App 发一个请求，让 AccessibilityService 处理
        // 更简单的方式：让 FloatingWindowService 直接访问 AccessibilityService
        val app = context.applicationContext as App
        // 通过 chatRepository 中转请求
        val result = app.chatRepository.requestNodeFind(x, y)
        return result
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
            append("位置: (${nodeInfo.bounds.left}, ${nodeInfo.bounds.top}) - (${nodeInfo.bounds.right}, ${nodeInfo.bounds.bottom})")
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
            text = "保存规则"
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
        val rule = MonitorRule(
            id = "rule_${System.currentTimeMillis()}",
            viewIdPattern = nodeInfo.viewId,
            className = nodeInfo.className,
            textMinLength = 2,
            description = "从选区创建: ${nodeInfo.text.take(20)}"
        )
        app.addRule(rule)
    }

    private fun removeConfirmAndReshowOverlay() {
        confirmView?.let { windowManager.removeView(it) }
        confirmView = null
        showSelectionOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSelectionOverlay() {
        if (selectionOverlay != null) return

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

        selectionParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(selectionOverlay, selectionParams)
    }

    // ======== 消息显示 ========

    fun appendMessage(message: ChatMessage) {
        if (!isPanelExpanded || messageContainer == null) return

        val textView = TextView(context).apply {
            text = if (message.sender == Sender.DOUBAO) {
                "🤖 豆包：${message.content}"
            } else {
                "👤 你：${message.content}"
            }
            setTextColor(
                if (message.sender == Sender.DOUBAO) Color.parseColor("#FFB0C4FF")
                else Color.parseColor("#FFE0E0E0")
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, 24, 0, 0)
        }
        messageContainer?.addView(textView)
        scrollView?.post { scrollView?.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    fun clearMessages() {
        messageContainer?.removeAllViews()
    }

    fun destroy() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        floatingBall?.let { windowManager.removeView(it) }
        floatingBall = null
        selectionOverlay?.let { windowManager.removeView(it) }
        selectionOverlay = null
        confirmView?.let { windowManager.removeView(it) }
        confirmView = null
        scope.cancel()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
    }
}

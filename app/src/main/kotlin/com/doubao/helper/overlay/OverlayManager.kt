package com.doubao.helper.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import com.doubao.helper.R
import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.Sender

/**
 * 管理悬浮窗的两种状态：
 * - 小悬浮球（可拖动），始终显示
 * - 全屏对话面板，点击小悬浮球展开/关闭
 */
class OverlayManager(private val context: Context, private val windowManager: WindowManager) {

    // 小悬浮球
    private var floatingBall: TextView? = null
    private var floatingBallParams: WindowManager.LayoutParams? = null

    // 全屏对话面板
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null

    // 面板内部控件
    private var messageContainer: LinearLayout? = null
    private var scrollView: ScrollView? = null
    private var tvStatus: TextView? = null
    private var btnPause: Button? = null

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

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBall() {
        if (floatingBall != null) return

        floatingBall = TextView(context).apply {
            text = "🤖"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC6750A4"))
            gravity = Gravity.CENTER
            val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics).toInt()
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            setPadding(0, 0, 0, 0)
        }

        floatingBallParams = WindowManager.LayoutParams(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, context.resources.displayMetrics).toInt(),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, context.resources.displayMetrics).toInt(),
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
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
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
                        togglePanel()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(floatingBall, floatingBallParams)
    }

    private fun togglePanel() {
        if (isPanelExpanded) {
            collapsePanel()
        } else {
            expandPanel()
        }
    }

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

        btnClose.setOnClickListener {
            // 关闭整个服务
            onClose?.invoke()
        }

        btnMinimize.setOnClickListener {
            collapsePanel()
        }

        btnPause?.setOnClickListener {
            isPaused = !isPaused
            btnPause?.text = if (isPaused) {
                context.getString(R.string.overlay_btn_resume)
            } else {
                context.getString(R.string.overlay_btn_pause)
            }
            tvStatus?.text = if (isPaused) {
                context.getString(R.string.overlay_status_paused)
            } else {
                context.getString(R.string.overlay_status_listening)
            }
            tvStatus?.setTextColor(
                if (isPaused) Color.parseColor("#FFFF9800")
                else Color.parseColor("#FF4CAF50")
            )
        }

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(panelView, panelParams)

        // 隐藏小悬浮球
        floatingBall?.let { windowManager.removeView(it) }
        floatingBall = null

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

        // 重新显示小悬浮球
        showFloatingBall()
    }

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

        scrollView?.post {
            scrollView?.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    fun clearMessages() {
        messageContainer?.removeAllViews()
    }

    fun destroy() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        floatingBall?.let { windowManager.removeView(it) }
        floatingBall = null
    }
}

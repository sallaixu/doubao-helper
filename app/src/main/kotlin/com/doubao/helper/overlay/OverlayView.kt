package com.doubao.helper.overlay

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.doubao.helper.R
import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.Sender
import android.widget.Button

class OverlayView(context: Context) : LinearLayout(context) {

    private val messageContainer: LinearLayout
    private val scrollView: ScrollView
    private val tvStatus: TextView
    private val btnPause: Button
    private val btnClose: ImageView
    private val btnMinimize: ImageView

    var onPauseToggle: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onMinimize: (() -> Unit)? = null

    private var isPaused = false

    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_view, this, true)
        messageContainer = findViewById(R.id.messageContainer)
        scrollView = findViewById(R.id.scrollView)
        tvStatus = findViewById(R.id.tvStatus)
        btnPause = findViewById(R.id.btnPause)
        btnClose = findViewById(R.id.btnClose)
        btnMinimize = findViewById(R.id.btnMinimize)

        btnPause.setOnClickListener {
            isPaused = !isPaused
            btnPause.text = if (isPaused) {
                context.getString(R.string.overlay_btn_resume)
            } else {
                context.getString(R.string.overlay_btn_pause)
            }
            tvStatus.text = if (isPaused) {
                context.getString(R.string.overlay_status_paused)
            } else {
                context.getString(R.string.overlay_status_listening)
            }
            tvStatus.setTextColor(
                if (isPaused) Color.parseColor("#FFFF9800")
                else Color.parseColor("#FF4CAF50")
            )
            onPauseToggle?.invoke()
        }

        btnClose.setOnClickListener { onClose?.invoke() }
        btnMinimize.setOnClickListener { onMinimize?.invoke() }
    }

    fun appendMessage(message: ChatMessage) {
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
        messageContainer.addView(textView)

        // Auto-scroll to bottom
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    fun clearMessages() {
        messageContainer.removeAllViews()
    }
}

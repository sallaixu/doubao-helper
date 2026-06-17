package com.doubao.helper.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * 万年历待机视图：全屏黑底白字显示日期、星期、时间。
 * OLED 防烧屏优化：每分钟随机微移文字位置。
 * 点击屏幕任意位置触发退出回调。
 */
@SuppressLint("ClickableViewAccessibility")
class StandbyView(context: Context) : FrameLayout(context) {

    private val tvDate: TextView
    private val tvWeekday: TextView
    private val tvTime: TextView
    private val tvWakeupHint: TextView
    private val innerLayout: android.widget.LinearLayout

    var onTapToExit: (() -> Unit)? = null

    private val dateFormat = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val weekdays = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")

    init {
        setBackgroundColor(Color.BLACK)

        innerLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        tvDate = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            gravity = Gravity.CENTER
        }

        tvWeekday = TextView(context).apply {
            setTextColor(Color.parseColor("#CCFFFFFF"))  // 略暗一点
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        }

        tvTime = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        tvWakeupHint = TextView(context).apply {
            setTextColor(Color.parseColor("#88FFFFFF"))  // 半透明白色
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }

        innerLayout.addView(tvDate)
        innerLayout.addView(tvWeekday)
        innerLayout.addView(tvTime)
        innerLayout.addView(tvWakeupHint)

        addView(innerLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onTapToExit?.invoke()
                true
            } else {
                false
            }
        }

        updateTime()
    }

    /** 更新时间显示 */
    fun updateTime() {
        val now = Date()
        tvDate.text = dateFormat.format(now)
        tvWeekday.text = weekdays[now.day]  // Date.day: 0=Sunday
        tvTime.text = timeFormat.format(now)
    }

    /** 显示唤醒词提示 */
    fun showWakeupHint(hint: String) {
        tvWakeupHint.text = hint
    }

    /** OLED 防烧屏：随机微移文字位置 */
    fun applyBurnInShift() {
        val offsetX = Random.nextInt(-4, 5)  // -4 到 4 像素
        val offsetY = Random.nextInt(-4, 5)
        innerLayout.translationX = offsetX.toFloat()
        innerLayout.translationY = offsetY.toFloat()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    }
}

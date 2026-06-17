package com.doubao.helper.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/**
 * 旋转容器：在竖屏下将面板内容旋转 90° 使其以横屏布局显示。
 *
 * 实现方式：
 * - 容器本身保持窗口尺寸（竖屏 W x H）
 * - onMeasure 时给子 View 传入交换后的宽高（H x W），让子 View 按横屏布局
 * - onLayout 将子 View 摆放在 (0, 0) 到 (H, W) 的区域
 * - dispatchDraw 旋转 Canvas 90° 后绘制子 View
 * - dispatchTouchEvent 将竖屏触摸坐标映射为横屏坐标
 *
 * 不使用 View.rotation，避免旋转后位置偏移问题。
 */
class RotatedContainer(context: Context) : FrameLayout(context) {

    /** 旋转角度：0, 90, -90 */
    var rotationDegrees: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (rotationDegrees != 0) {
            // 容器自身保持窗口尺寸
            val windowW = MeasureSpec.getSize(widthMeasureSpec)
            val windowH = MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(windowW, windowH)

            // 给子 View 传入交换后的宽高（横屏尺寸），让子 View 按横屏布局
            val childW = MeasureSpec.makeMeasureSpec(windowH, MeasureSpec.EXACTLY)
            val childH = MeasureSpec.makeMeasureSpec(windowW, MeasureSpec.EXACTLY)
            measureChildren(childW, childH)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (rotationDegrees != 0) {
            // 子 View 按横屏尺寸布局：left=0, top=0, right=screenHeight, bottom=screenWidth
            val windowW = r - l
            val windowH = b - t
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.layout(0, 0, windowH, windowW)
            }
        } else {
            super.onLayout(changed, l, t, r, b)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (rotationDegrees == 0) {
            super.dispatchDraw(canvas)
            return
        }

        canvas.save()
        when (rotationDegrees) {
            90 -> {
                // 顺时针 90°: translate(width, 0) + rotate(90)
                // 子 View 坐标系: x ∈ [0, H], y ∈ [0, W] (横屏)
                // 旋转后映射到窗口: windowX = width - viewY, windowY = viewX
                canvas.translate(width.toFloat(), 0f)
                canvas.rotate(90f)
            }
            -90 -> {
                // 逆时针 90°: translate(0, height) + rotate(-90)
                canvas.translate(0f, height.toFloat())
                canvas.rotate(-90f)
            }
        }
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (rotationDegrees == 0) return super.dispatchTouchEvent(ev)

        val transformed = transformMotionEvent(ev)
        val result = super.dispatchTouchEvent(transformed)
        transformed.recycle()
        return result
    }

    @SuppressLint("Recycle")
    private fun transformMotionEvent(ev: MotionEvent): MotionEvent {
        val pointerCount = ev.pointerCount
        val pointers = mutableListOf<MotionEvent.PointerCoords>()
        val pointerProperties = mutableListOf<MotionEvent.PointerProperties>()

        for (i in 0 until pointerCount) {
            val coords = MotionEvent.PointerCoords()
            ev.getPointerCoords(i, coords)
            val (nx, ny) = transformCoord(coords.x, coords.y)
            coords.x = nx
            coords.y = ny
            pointers.add(coords)

            val props = MotionEvent.PointerProperties()
            ev.getPointerProperties(i, props)
            pointerProperties.add(props)
        }

        return MotionEvent.obtain(
            ev.downTime, ev.eventTime, ev.action,
            pointerCount, pointerProperties.toTypedArray(), pointers.toTypedArray(),
            ev.metaState, ev.buttonState,
            ev.xPrecision, ev.yPrecision,
            ev.deviceId, ev.edgeFlags, ev.source, ev.flags
        )
    }

    /**
     * 坐标变换。
     * 窗口坐标系 (W x H) → View 坐标系 (H x W)
     *
     * 顺时针 90°: viewX = windowY, viewY = W - windowX
     * 逆时针 90°: viewX = H - windowY, viewY = windowX
     */
    private fun transformCoord(x: Float, y: Float): Pair<Float, Float> {
        return when (rotationDegrees) {
            90 -> Pair(y, width.toFloat() - x)
            -90 -> Pair(height.toFloat() - y, x)
            else -> Pair(x, y)
        }
    }
}

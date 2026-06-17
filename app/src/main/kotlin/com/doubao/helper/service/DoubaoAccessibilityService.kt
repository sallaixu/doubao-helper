package com.doubao.helper.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.doubao.helper.App
import com.doubao.helper.util.NodeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DoubaoAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != App.DOUBAO_PACKAGE) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        try {
            val app = application as App

            if (app.isSelectionMode) {
                return
            }

            val rules = app.monitorRules
            if (rules.isEmpty()) return

            val messages = NodeParser.parseByRules(rootNode, rules, App.DOUBAO_PACKAGE)
            if (messages.isEmpty()) return

            serviceScope.launch {
                val repository = app.chatRepository
                for (message in messages) {
                    repository.emitMessage(message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing accessibility event", e)
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 根据屏幕坐标查找节点（供选区模式调用）
     */
    fun findNodeAtPoint(x: Int, y: Int): com.doubao.helper.model.DebugNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return try {
            NodeParser.findNodeAtPoint(rootNode, x, y)
        } catch (e: Exception) {
            Log.e(TAG, "Error finding node at point", e)
            null
        }
    }

    /**
     * 根据屏幕坐标点击节点（供麦克风按钮点击调用）。
     * 优先找可点击节点执行 performAction(ACTION_CLICK)，
     * 找不到则用 dispatchGesture 模拟点击。
     */
    fun clickNodeAtPoint(x: Int, y: Int): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        return try {
            // 查找该坐标处最深的可点击节点
            val clickableNode = findClickableNodeAt(rootNode, x, y)
            if (clickableNode != null) {
                val result = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "clickNodeAtPoint via performAction: $result")
                return result
            }

            // 备选：用 dispatchGesture 模拟点击（Android 7+）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val gestureResult = dispatchGestureClick(x, y)
                Log.i(TAG, "clickNodeAtPoint via dispatchGesture: $gestureResult")
                return gestureResult
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking node at point", e)
            false
        }
    }

    /**
     * 查找坐标处最深的可点击节点
     */
    private fun findClickableNodeAt(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        var bestNode: AccessibilityNodeInfo? = null
        var bestDepth = -1

        fun traverse(n: AccessibilityNodeInfo, depth: Int) {
            val bounds = android.graphics.Rect()
            n.getBoundsInScreen(bounds)
            if (!bounds.contains(x, y)) return

            if (n.isClickable && depth > bestDepth) {
                bestDepth = depth
                bestNode = n
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { traverse(it, depth + 1) }
            }
        }

        traverse(node, 0)
        return bestNode
    }

    /**
     * 用 dispatchGesture 模拟点击（Android 7+ 备选方案）
     */
    private fun dispatchGestureClick(x: Int, y: Int): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return false

        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                android.graphics.Path().apply {
                    moveTo(x.toFloat(), y.toFloat())
                },
                0L, 100L
            ))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")

        serviceInfo = serviceInfo.apply {
            packageNames = arrayOf(App.DOUBAO_PACKAGE)
        }

        val app = application as App
        app.chatRepository.nodeFindCallback = { x, y ->
            findNodeAtPoint(x, y)
        }
        app.chatRepository.nodeClickCallback = { x, y ->
            clickNodeAtPoint(x, y)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val app = application as App
        app.chatRepository.nodeFindCallback = null
        app.chatRepository.nodeClickCallback = null
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "DoubaoAccessibility"
    }
}

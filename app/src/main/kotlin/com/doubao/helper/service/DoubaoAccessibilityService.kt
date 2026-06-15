package com.doubao.helper.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
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
                // 选区模式下不解析消息，等待用户点击
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

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")

        serviceInfo = serviceInfo.apply {
            packageNames = arrayOf(App.DOUBAO_PACKAGE)
        }

        // 注册节点查找回调，供 OverlayManager 通过 ChatRepository 调用
        val app = application as App
        app.chatRepository.nodeFindCallback = { x, y ->
            findNodeAtPoint(x, y)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val app = application as App
        app.chatRepository.nodeFindCallback = null
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "DoubaoAccessibility"
    }
}

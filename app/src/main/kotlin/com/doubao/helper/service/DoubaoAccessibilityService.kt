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
            val messages = NodeParser.parseMessages(rootNode, App.DOUBAO_PACKAGE)
            if (messages.isEmpty()) return

            serviceScope.launch {
                val repository = (application as App).chatRepository
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

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")

        serviceInfo = serviceInfo.apply {
            // 动态设置监听的包名，便于后续配置
            packageNames = arrayOf(App.DOUBAO_PACKAGE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "DoubaoAccessibility"
    }
}

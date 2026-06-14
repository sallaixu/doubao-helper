package com.doubao.helper.util

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.Sender

object NodeParser {

    /**
     * 从豆包 App 的节点树中解析对话消息。
     *
     * 当前实现为通用策略：遍历所有可见文本节点，按位置和内容特征区分发送者。
     * 后续可根据豆包 App 的实际 UI 结构优化定位策略。
     */
    fun parseMessages(rootNode: AccessibilityNodeInfo, doubaoPackage: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        traverseNodes(rootNode, messages, doubaoPackage)
        return messages
    }

    private fun traverseNodes(
        node: AccessibilityNodeInfo,
        messages: MutableList<ChatMessage>,
        doubaoPackage: String
    ) {
        if (node.packageName != null && node.packageName.toString() != doubaoPackage) {
            return
        }

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && !node.isClickable) {
            val sender = inferSender(node)
            val id = generateMessageId(node, text)
            messages.add(
                ChatMessage(
                    id = id,
                    content = text,
                    sender = sender,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNodes(child, messages, doubaoPackage)
                child.recycle()
            }
        }
    }

    /**
     * 根据节点的 viewId 和位置特征推断发送者。
     * 豆包的 AI 回复通常位于屏幕左侧或具有特定的 viewId 前缀。
     * 这是一个启发式方法，需要根据实际 UI 调整。
     */
    private fun inferSender(node: AccessibilityNodeInfo): Sender {
        val viewId = node.viewIdResourceName ?: ""
        return if (viewId.contains("ai", ignoreCase = true) ||
            viewId.contains("bot", ignoreCase = true) ||
            viewId.contains("assistant", ignoreCase = true)
        ) {
            Sender.DOUBAO
        } else {
            Sender.USER
        }
    }

    private fun generateMessageId(node: AccessibilityNodeInfo, text: String): String {
        val viewId = node.viewIdResourceName ?: ""
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return "${viewId}_${bounds.left}_${bounds.top}_${text.hashCode()}"
    }
}

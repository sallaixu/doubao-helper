package com.doubao.helper.util

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.DebugNodeInfo
import com.doubao.helper.model.MonitorRule
import com.doubao.helper.model.Sender

object NodeParser {

    /**
     * 根据点击坐标找到最匹配的无障碍节点。
     * 优先选择最深层（最具体）的、有文本内容的、面积最小的节点。
     */
    fun findNodeAtPoint(rootNode: AccessibilityNodeInfo, x: Int, y: Int): DebugNodeInfo? {
        var bestNode: DebugNodeInfo? = null
        var bestArea = Long.MAX_VALUE
        var bestDepth = -1

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            // 检查坐标是否在节点范围内
            if (!bounds.contains(x, y)) {
                node.recycle()
                return
            }

            val text = node.text?.toString()?.trim() ?: ""

            // 如果节点有文本，且面积更小或更深，更新最佳匹配
            if (text.isNotEmpty()) {
                val area = bounds.width().toLong() * bounds.height().toLong()
                if (depth > bestDepth || (depth == bestDepth && area < bestArea)) {
                    bestDepth = depth
                    bestArea = area
                    bestNode = DebugNodeInfo(
                        viewId = node.viewIdResourceName ?: "",
                        className = node.className?.toString() ?: "",
                        text = text,
                        bounds = Rect(bounds),
                        depth = depth,
                        isClickable = node.isClickable
                    )
                }
            }

            // 继续遍历子节点
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    traverse(child, depth + 1)
                }
            }
        }

        traverse(rootNode, 0)
        return bestNode
    }

    /**
     * 根据监听规则从节点树中提取对话消息。
     * 匹配条件：viewId 以 rule.viewIdPattern 开头，className 一致，文本长度 >= textMinLength
     */
    fun parseByRules(
        rootNode: AccessibilityNodeInfo,
        rules: List<MonitorRule>,
        doubaoPackage: String
    ): List<ChatMessage> {
        if (rules.isEmpty()) return emptyList()
        val messages = mutableListOf<ChatMessage>()
        traverseAndMatch(rootNode, rules, doubaoPackage, messages, 0)
        return messages
    }

    private fun traverseAndMatch(
        node: AccessibilityNodeInfo,
        rules: List<MonitorRule>,
        doubaoPackage: String,
        messages: MutableList<ChatMessage>,
        depth: Int
    ) {
        if (node.packageName != null && node.packageName.toString() != doubaoPackage) {
            return
        }

        val text = node.text?.toString()?.trim() ?: ""
        if (text.isNotEmpty()) {
            val viewId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""

            for (rule in rules) {
                if (matchesRule(viewId, className, text, rule)) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    val id = "${viewId}_${bounds.left}_${bounds.top}_${text.hashCode()}"
                    messages.add(
                        ChatMessage(
                            id = id,
                            content = text,
                            sender = Sender.DOUBAO, // 规则匹配的默认为豆包消息
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    break // 一个节点只匹配一条规则
                }
            }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseAndMatch(child, rules, doubaoPackage, messages, depth + 1)
            }
        }
    }

    private fun matchesRule(viewId: String, className: String, text: String, rule: MonitorRule): Boolean {
        // viewId 匹配：精确匹配或前缀匹配
        val viewIdMatch = if (rule.viewIdPattern.isEmpty()) {
            true
        } else {
            viewId == rule.viewIdPattern || viewId.startsWith(rule.viewIdPattern)
        }

        // className 匹配：如果规则指定了 className，则必须包含
        val classMatch = if (rule.className.isEmpty()) {
            true
        } else {
            className == rule.className || className.contains(rule.className)
        }

        // 文本长度过滤
        val lengthMatch = text.length >= rule.textMinLength

        return viewIdMatch && classMatch && lengthMatch
    }

    /**
     * Dump 节点树信息（用于调试面板展示）
     */
    fun dumpNodeTree(rootNode: AccessibilityNodeInfo, maxNodes: Int = 100): List<DebugNodeInfo> {
        val nodes = mutableListOf<DebugNodeInfo>()
        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (nodes.size >= maxNodes) return
            val text = node.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                nodes.add(
                    DebugNodeInfo(
                        viewId = node.viewIdResourceName ?: "",
                        className = node.className?.toString() ?: "",
                        text = if (text.length > 50) text.substring(0, 50) + "…" else text,
                        bounds = bounds,
                        depth = depth,
                        isClickable = node.isClickable
                    )
                )
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    traverse(child, depth + 1)
                }
            }
        }
        traverse(rootNode, 0)
        return nodes
    }
}

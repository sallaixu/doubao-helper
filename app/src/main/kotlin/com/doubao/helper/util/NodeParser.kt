package com.doubao.helper.util

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.DebugNodeInfo
import com.doubao.helper.model.MonitorRule
import com.doubao.helper.model.Sender

object NodeParser {

    private const val TAG = "NodeParser"

    /**
     * 根据点击坐标找到最匹配的无障碍节点。
     */
    fun findNodeAtPoint(rootNode: AccessibilityNodeInfo, x: Int, y: Int): DebugNodeInfo? {
        var bestNode: DebugNodeInfo? = null
        var bestArea = Long.MAX_VALUE
        var bestDepth = -1

        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            if (!bounds.contains(x, y)) {
                node.recycle()
                return
            }

            val text = node.text?.toString()?.trim() ?: ""

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
     */
    fun parseByRules(
        rootNode: AccessibilityNodeInfo,
        rules: List<MonitorRule>,
        doubaoPackage: String
    ): List<ChatMessage> {
        if (rules.isEmpty()) return emptyList()
        val messages = mutableListOf<ChatMessage>()
        traverseAndMatch(rootNode, rules, doubaoPackage, messages, 0)

        // 日志：输出本次解析到的所有消息
        if (messages.isNotEmpty()) {
            Log.d(TAG, "parseByRules: found ${messages.size} messages")
            for ((index, msg) in messages.withIndex()) {
                Log.d(TAG, "  [$index] regionKey=${msg.regionKey}, id=${msg.id}, " +
                    "content=[${msg.content.take(50)}${if (msg.content.length > 50) "…" else ""}]")
            }
        }

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
            val nodeBounds = Rect()
            node.getBoundsInScreen(nodeBounds)

            for (rule in rules) {
                if (matchesRule(viewId, className, text, nodeBounds, rule)) {
                    // 使用 viewId + 内容前缀hash 作为 regionKey（不依赖 bounds 坐标，避免滚动导致去重失效）
                    val contentPrefixHash = text.take(30).hashCode()
                    val regionKey = "${viewId}_${contentPrefixHash}"
                    val id = "${regionKey}_${text.hashCode()}"

                    Log.d(TAG, "match: viewId=$viewId, bounds=(${nodeBounds.left},${nodeBounds.top})-(${nodeBounds.right},${nodeBounds.bottom}), " +
                        "regionKey=$regionKey, textLen=${text.length}, textHash=${text.hashCode()}, " +
                        "text=[${text.take(40)}${if (text.length > 40) "…" else ""}]")

                    messages.add(
                        ChatMessage(
                            id = id,
                            content = text,
                            sender = Sender.DOUBAO,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    break
                }
            }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseAndMatch(child, rules, doubaoPackage, messages, depth + 1)
            }
        }
    }

    private fun matchesRule(viewId: String, className: String, text: String, nodeBounds: Rect, rule: MonitorRule): Boolean {
        val viewIdMatch = if (rule.viewIdPattern.isEmpty()) {
            true
        } else {
            viewId == rule.viewIdPattern || viewId.startsWith(rule.viewIdPattern)
        }

        val classMatch = if (rule.className.isEmpty()) {
            true
        } else {
            className == rule.className || className.contains(rule.className)
        }

        val boundsMatch = if (rule.boundsRegion != null) {
            Rect.intersects(nodeBounds, rule.boundsRegion)
        } else {
            true
        }

        val lengthMatch = text.length >= rule.textMinLength

        return viewIdMatch && classMatch && boundsMatch && lengthMatch
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

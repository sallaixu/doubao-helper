package com.doubao.helper.model

import android.graphics.Rect

/**
 * 调试节点信息：从无障碍节点树中提取的节点摘要，
 * 用于选区调试时向用户展示候选节点。
 */
data class DebugNodeInfo(
    val viewId: String,
    val className: String,
    val text: String,
    val bounds: Rect,
    val depth: Int,
    val isClickable: Boolean
)

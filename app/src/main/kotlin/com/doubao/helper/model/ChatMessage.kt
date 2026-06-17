package com.doubao.helper.model

data class ChatMessage(
    val id: String,
    val content: String,
    val sender: Sender,
    val timestamp: Long
) {
    /**
     * 区域标识：用于续写检测。
     * 基于 viewId + 内容前缀 hash，不依赖屏幕坐标（滚动时坐标会变）。
     */
    val regionKey: String
        get() = id.substringBeforeLast("_")  // viewId + contentPrefixHash
}

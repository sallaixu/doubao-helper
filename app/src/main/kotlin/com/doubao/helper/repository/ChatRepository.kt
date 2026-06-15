package com.doubao.helper.repository

import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.DebugNodeInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ChatRepository {

    private val _messages = MutableSharedFlow<ChatMessage>(replay = 0, extraBufferCapacity = 64)
    val messages: SharedFlow<ChatMessage> = _messages.asSharedFlow()

    private val emittedIds = mutableSetOf<String>()

    // 节点查找回调：由 FloatingWindowService 设置，指向 AccessibilityService
    var nodeFindCallback: ((x: Int, y: Int) -> DebugNodeInfo?)? = null

    suspend fun emitMessage(message: ChatMessage) {
        if (emittedIds.add(message.id)) {
            _messages.emit(message)
        }
    }

    fun clearHistory() {
        emittedIds.clear()
    }

    /**
     * 请求在指定坐标查找无障碍节点。
     * 通过 nodeFindCallback 转发给 DoubaoAccessibilityService。
     */
    fun requestNodeFind(x: Int, y: Int): DebugNodeInfo? {
        return nodeFindCallback?.invoke(x, y)
    }
}

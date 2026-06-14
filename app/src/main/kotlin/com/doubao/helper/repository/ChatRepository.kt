package com.doubao.helper.repository

import com.doubao.helper.model.ChatMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ChatRepository {

    private val _messages = MutableSharedFlow<ChatMessage>(replay = 0, extraBufferCapacity = 64)

    val messages: SharedFlow<ChatMessage> = _messages.asSharedFlow()

    private val emittedIds = mutableSetOf<String>()

    suspend fun emitMessage(message: ChatMessage) {
        if (emittedIds.add(message.id)) {
            _messages.emit(message)
        }
    }

    fun clearHistory() {
        emittedIds.clear()
    }
}

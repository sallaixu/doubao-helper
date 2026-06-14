package com.doubao.helper.model

data class ChatMessage(
    val id: String,
    val content: String,
    val sender: Sender,
    val timestamp: Long
)

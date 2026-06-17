package com.doubao.helper.repository

import android.content.Context
import android.util.Log
import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.DebugNodeInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ChatRepository {

    private val _messages = MutableSharedFlow<ChatMessage>(replay = 0, extraBufferCapacity = 64)
    val messages: SharedFlow<ChatMessage> = _messages.asSharedFlow()

    // 去重：跟踪每个规则区域的最后一条消息
    private val lastEmittedByRegion = mutableMapOf<String, String>()
    private val emittedIds = mutableSetOf<String>()

    // 节点查找回调：由 AccessibilityService 设置
    var nodeFindCallback: ((x: Int, y: Int) -> DebugNodeInfo?)? = null

    // 节点点击回调：由 AccessibilityService 设置
    var nodeClickCallback: ((x: Int, y: Int) -> Boolean)? = null

    // 最后消息时间（用于待机超时检测）
    var lastMessageTime: Long = System.currentTimeMillis()
        private set

    /**
     * 发送消息，带去重和续写检测。
     */
    suspend fun emitMessage(message: ChatMessage) {
        val regionKey = message.regionKey
        val contentPreview = message.content.take(60) + if (message.content.length > 60) "…" else ""

        // 检查 id 去重
        if (!emittedIds.add(message.id)) {
            Log.d(TAG, "emitMessage: DUPLICATE id=${message.id}, content=[$contentPreview]")
            return
        }

        val lastContent = lastEmittedByRegion[regionKey]

        if (lastContent != null) {
            val lastPreview = lastContent.take(60) + if (lastContent.length > 60) "…" else ""

            if (message.content.startsWith(lastContent) && message.content.length > lastContent.length) {
                // 续写：同一区域文本变长了
                Log.d(TAG, "emitMessage: APPEND regionKey=$regionKey, " +
                    "lastLen=${lastContent.length} → newLen=${message.content}, " +
                    "last=[$lastPreview] → new=[$contentPreview]")
                lastEmittedByRegion[regionKey] = message.content
                lastMessageTime = System.currentTimeMillis()
                _messages.emit(message)
                return
            }

            if (message.content == lastContent) {
                // 完全相同的内容（理论上 id 去重已经过滤了，但以防万一）
                Log.d(TAG, "emitMessage: SAME content regionKey=$regionKey, content=[$contentPreview]")
                return
            }

            // 同一区域内容不同（不是续写）
            Log.d(TAG, "emitMessage: NEW-REGION-REPLACE regionKey=$regionKey, " +
                "old=[$lastPreview] → new=[$contentPreview]")
        } else {
            Log.d(TAG, "emitMessage: NEW regionKey=$regionKey, content=[$contentPreview]")
        }

        // 非续写（新消息或内容完全不同），正常发送
        lastEmittedByRegion[regionKey] = message.content
        lastMessageTime = System.currentTimeMillis()
        _messages.emit(message)
    }

    /** 重置最后消息时间（恢复对话时调用） */
    fun resetLastMessageTime() {
        lastMessageTime = System.currentTimeMillis()
    }

    fun clearHistory() {
        Log.d(TAG, "clearHistory: clearing ${emittedIds.size} emittedIds, ${lastEmittedByRegion.size} regions")
        emittedIds.clear()
        lastEmittedByRegion.clear()
    }

    fun requestNodeFind(x: Int, y: Int): DebugNodeInfo? {
        return nodeFindCallback?.invoke(x, y)
    }

    fun requestNodeClick(x: Int, y: Int): Boolean {
        return nodeClickCallback?.invoke(x, y) ?: false
    }

    // ====== 设置项 ======

    /** 默认字体大小 (sp) */
    val defaultFontSize = 16f

    /** 默认待机超时（分钟） */
    val defaultStandbyTimeout = 3

    fun getFontSize(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_FONT_SIZE, defaultFontSize)
    }

    fun setFontSize(context: Context, sizeSp: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_FONT_SIZE, sizeSp).apply()
    }

    fun getStandbyTimeout(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_STANDBY_TIMEOUT, defaultStandbyTimeout)
    }

    fun setStandbyTimeout(context: Context, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_STANDBY_TIMEOUT, minutes).apply()
    }

    /** 默认唤醒词 */
    val defaultWakeupWord = "小豆小豆"

    fun getWakeupWord(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WAKEUP_WORD, defaultWakeupWord) ?: defaultWakeupWord
    }

    fun setWakeupWord(context: Context, word: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WAKEUP_WORD, word).apply()
    }

    companion object {
        private const val TAG = "ChatRepo"
        private const val PREFS_NAME = "doubao_helper_settings"
        private const val KEY_FONT_SIZE = "overlay_font_size_sp"
        private const val KEY_STANDBY_TIMEOUT = "standby_timeout_minutes"
        private const val KEY_WAKEUP_WORD = "wakeup_word"
    }
}

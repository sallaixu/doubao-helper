package com.doubao.helper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.doubao.helper.model.DebugNodeInfo
import com.doubao.helper.model.MonitorRule
import com.doubao.helper.repository.ChatRepository
import com.doubao.helper.util.RuleStorage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class App : Application() {

    val chatRepository = ChatRepository()

    // 选区调试状态
    var isSelectionMode = false

    // 选中节点的结果流
    private val _selectedNode = MutableSharedFlow<DebugNodeInfo>(replay = 0, extraBufferCapacity = 1)
    val selectedNode: SharedFlow<DebugNodeInfo> = _selectedNode.asSharedFlow()

    suspend fun emitSelectedNode(node: DebugNodeInfo) {
        _selectedNode.emit(node)
    }

    // 监听规则
    var monitorRules: List<MonitorRule> = emptyList()
        private set

    fun reloadRules() {
        monitorRules = RuleStorage.loadRules(this)
    }

    fun addRule(rule: MonitorRule) {
        RuleStorage.addRule(this, rule)
        monitorRules = RuleStorage.loadRules(this)
    }

    fun removeRule(ruleId: String) {
        RuleStorage.removeRule(this, ruleId)
        monitorRules = RuleStorage.loadRules(this)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        reloadRules()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_name)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "doubao_helper_channel"
        const val NOTIFICATION_ID = 1
        const val DOUBAO_PACKAGE = "com.larus.nova"
    }
}

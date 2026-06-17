package com.doubao.helper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.doubao.helper.model.CallButtonConfig
import com.doubao.helper.model.DebugNodeInfo
import com.doubao.helper.model.MonitorRule
import com.doubao.helper.repository.ChatRepository
import com.doubao.helper.util.MicButtonStorage
import com.doubao.helper.util.RuleStorage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class App : Application() {

    val chatRepository = ChatRepository()

    // 选区调试状态
    var isSelectionMode = false
    // 选区模式类型：monitor = 监听规则配置, mic = 麦克风按钮配置
    var selectionModeType: String = "monitor"

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

    // 通话按钮配置
    var callButtonConfig: CallButtonConfig? = null
        private set

    fun reloadCallButton() {
        callButtonConfig = MicButtonStorage.loadConfig(this)
    }

    fun setCallButtonConfig(config: CallButtonConfig) {
        MicButtonStorage.saveConfig(this, config)
        callButtonConfig = MicButtonStorage.loadConfig(this)
    }

    // 选区模式临时存储：已选的第一个坐标（挂断按钮），等选第二个（拨通按钮）
    var pendingHangUpX: Int = 0
    var pendingHangUpY: Int = 0

    // 待机状态
    var isStandbyMode = false

    // 最后消息时间（用于超时检测）
    var lastMessageTime: Long = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        reloadRules()
        reloadCallButton()
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

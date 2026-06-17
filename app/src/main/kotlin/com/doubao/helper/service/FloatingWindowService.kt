package com.doubao.helper.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.view.WindowManager
import com.doubao.helper.App
import com.doubao.helper.R
import com.doubao.helper.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingWindowService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlayManager: OverlayManager? = null
    private var displayManager: DisplayManager? = null
    private var lastRotation: Int = -1

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val rotation = windowManager.defaultDisplay.rotation
            if (lastRotation != -1 && rotation != lastRotation) {
                overlayManager?.onDisplayRotationChanged()
            }
            lastRotation = rotation
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(App.NOTIFICATION_ID, createNotification())
        setupOverlay()
        collectMessages()
        registerDisplayListener()
        startStandbyTimeoutChecker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun setupOverlay() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayManager = OverlayManager(this, windowManager).apply {
            onClose = { stopSelf() }
            show()
        }
    }

    private fun collectMessages() {
        val repository = (application as App).chatRepository
        serviceScope.launch {
            repository.messages.collect { message ->
                overlayManager?.appendMessage(message)
            }
        }
    }

    private fun registerDisplayListener() {
        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lastRotation = windowManager.defaultDisplay.rotation
        displayManager?.registerDisplayListener(displayListener, null)
    }

    /**
     * 定时检查待机超时：如果超过配置的时间没有新消息，自动进入待机模式。
     */
    private fun startStandbyTimeoutChecker() {
        val app = application as App
        serviceScope.launch {
            while (true) {
                delay(30_000) // 每30秒检查一次
                val timeoutMinutes = app.chatRepository.getStandbyTimeout(this@FloatingWindowService)
                val timeoutMs = timeoutMinutes * 60_000L
                val elapsed = System.currentTimeMillis() - app.chatRepository.lastMessageTime
                if (elapsed > timeoutMs && !app.isStandbyMode && app.callButtonConfig != null) {
                    overlayManager?.enterStandby()
                }
            }
        }
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, App.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager?.unregisterDisplayListener(displayListener)
        overlayManager?.destroy()
        overlayManager = null
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "FloatingWindowService"
    }
}

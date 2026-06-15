package com.doubao.helper.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.WindowManager
import com.doubao.helper.App
import com.doubao.helper.R
import com.doubao.helper.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingWindowService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlayManager: OverlayManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(App.NOTIFICATION_ID, createNotification())
        setupOverlay()
        collectMessages()
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
        overlayManager?.destroy()
        overlayManager = null
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "FloatingWindowService"
    }
}

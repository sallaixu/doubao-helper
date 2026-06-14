package com.doubao.helper.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.doubao.helper.App
import com.doubao.helper.R
import com.doubao.helper.overlay.OverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingWindowService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var overlayView: OverlayView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(App.NOTIFICATION_ID, createNotification())
        showOverlay()
        collectMessages()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = OverlayView(this).apply {
            onClose = { stopSelf() }
            onMinimize = {
                // 移除悬浮窗但保持服务运行
                overlayView?.let { view ->
                    windowManager?.removeView(view)
                }
            }
            onPauseToggle = {
                Log.d(TAG, "Pause toggled")
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager?.addView(overlayView, params)
    }

    private fun collectMessages() {
        val repository = (application as App).chatRepository
        serviceScope.launch {
            repository.messages.collect { message ->
                overlayView?.appendMessage(message)
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
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        windowManager = null
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "FloatingWindowService"
    }
}

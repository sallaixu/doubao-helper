package com.doubao.helper

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.doubao.helper.repository.ChatRepository

class App : Application() {

    val chatRepository = ChatRepository()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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

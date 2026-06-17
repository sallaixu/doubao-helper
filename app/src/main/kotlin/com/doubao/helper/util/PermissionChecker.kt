package com.doubao.helper.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object PermissionChecker {

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasAccessibilityPermission(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        if (enabledServices.isEmpty()) return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.startsWith(context.packageName)) {
                return true
            }
        }
        return false
    }

    fun allPermissionsGranted(context: Context): Boolean {
        return hasOverlayPermission(context) && hasAccessibilityPermission(context)
    }

    fun hasRecordAudioPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

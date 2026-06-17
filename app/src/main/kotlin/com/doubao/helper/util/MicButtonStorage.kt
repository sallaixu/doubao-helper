package com.doubao.helper.util

import android.content.Context
import com.doubao.helper.model.CallButtonConfig
import org.json.JSONObject

/**
 * 用 SharedPreferences 存取通话按钮配置。
 */
object MicButtonStorage {

    private const val PREFS_NAME = "doubao_helper_mic"
    private const val KEY_CONFIG = "call_button_config"

    fun loadConfig(context: Context): CallButtonConfig? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIG, null) ?: return null
        val obj = JSONObject(json)
        return CallButtonConfig(
            hangUpX = obj.getInt("hangUpX"),
            hangUpY = obj.getInt("hangUpY"),
            callX = obj.getInt("callX"),
            callY = obj.getInt("callY")
        )
    }

    fun saveConfig(context: Context, config: CallButtonConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject()
        obj.put("hangUpX", config.hangUpX)
        obj.put("hangUpY", config.hangUpY)
        obj.put("callX", config.callX)
        obj.put("callY", config.callY)
        prefs.edit().putString(KEY_CONFIG, obj.toString()).apply()
    }

    fun clearConfig(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CONFIG).apply()
    }
}

package com.doubao.helper.util

import android.content.Context
import android.graphics.Rect
import com.doubao.helper.model.MonitorRule
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用 SharedPreferences 存取 MonitorRule 列表。
 */
object RuleStorage {

    private const val PREFS_NAME = "doubao_helper_rules"
    private const val KEY_RULES = "monitor_rules"

    fun loadRules(context: Context): List<MonitorRule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RULES, null) ?: return emptyList()

        val rules = mutableListOf<MonitorRule>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val boundsRegion = if (obj.has("boundsRegion") && !obj.isNull("boundsRegion")) {
                val br = obj.getJSONObject("boundsRegion")
                Rect(
                    br.getInt("left"),
                    br.getInt("top"),
                    br.getInt("right"),
                    br.getInt("bottom")
                )
            } else null

            rules.add(
                MonitorRule(
                    id = obj.getString("id"),
                    viewIdPattern = obj.getString("viewIdPattern"),
                    className = obj.getString("className"),
                    boundsRegion = boundsRegion,
                    textMinLength = obj.getInt("textMinLength"),
                    description = obj.optString("description", "")
                )
            )
        }
        return rules
    }

    fun saveRules(context: Context, rules: List<MonitorRule>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (rule in rules) {
            val obj = JSONObject()
            obj.put("id", rule.id)
            obj.put("viewIdPattern", rule.viewIdPattern)
            obj.put("className", rule.className)
            obj.put("boundsRegion", rule.boundsRegion?.let {
                JSONObject().apply {
                    put("left", it.left)
                    put("top", it.top)
                    put("right", it.right)
                    put("bottom", it.bottom)
                }
            })
            obj.put("textMinLength", rule.textMinLength)
            obj.put("description", rule.description)
            array.put(obj)
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    fun addRule(context: Context, rule: MonitorRule) {
        val rules = loadRules(context).toMutableList()
        // 避免重复添加相同 className+boundsRegion 的规则
        rules.removeAll {
            it.className == rule.className && it.boundsRegion == rule.boundsRegion
        }
        rules.add(rule)
        saveRules(context, rules)
    }

    fun removeRule(context: Context, ruleId: String) {
        val rules = loadRules(context).toMutableList()
        rules.removeAll { it.id == ruleId }
        saveRules(context, rules)
    }

    fun clearRules(context: Context) {
        saveRules(context, emptyList())
    }
}

package com.doubao.helper.util

import android.content.Context
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
            rules.add(
                MonitorRule(
                    id = obj.getString("id"),
                    viewIdPattern = obj.getString("viewIdPattern"),
                    className = obj.getString("className"),
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
            obj.put("textMinLength", rule.textMinLength)
            obj.put("description", rule.description)
            array.put(obj)
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    fun addRule(context: Context, rule: MonitorRule) {
        val rules = loadRules(context).toMutableList()
        // 避免重复添加相同 viewIdPattern
        rules.removeAll { it.viewIdPattern == rule.viewIdPattern && it.className == rule.className }
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

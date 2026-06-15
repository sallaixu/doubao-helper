package com.doubao.helper.model

/**
 * 监听规则：定义无障碍服务要抓取哪些节点的内容。
 * 用户通过"选区调试"在豆包界面上点击选择区域后生成。
 */
data class MonitorRule(
    val id: String,
    val viewIdPattern: String,  // viewId 精确匹配或前缀匹配
    val className: String,      // 节点 className 过滤
    val textMinLength: Int,     // 排除按钮等短文本
    val description: String    // 用户备注
)

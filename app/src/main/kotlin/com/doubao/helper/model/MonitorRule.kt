package com.doubao.helper.model

import android.graphics.Rect

/**
 * 监听规则：定义无障碍服务要抓取哪些节点的内容。
 * 用户通过"选区调试"在豆包界面上点击选择区域后生成。
 *
 * 匹配逻辑（所有条件取交集）：
 * - viewIdPattern: 非空时要求节点 viewId 精确/前缀匹配；为空时不过滤 viewId
 * - className: 非空时要求节点 className 精确/包含匹配；为空时不过滤 className
 * - boundsRegion: 非空时要求节点 bounds 与此区域有交集；为空时不过滤位置
 * - textMinLength: 节点文本长度 >= 此值
 */
data class MonitorRule(
    val id: String,
    val viewIdPattern: String,  // viewId 精确匹配或前缀匹配，空=不过滤
    val className: String,      // 节点 className 过滤，空=不过滤
    val boundsRegion: Rect?,    // 屏幕区域（选区时记录的节点bounds），null=不过滤位置
    val textMinLength: Int,     // 排除按钮等短文本
    val description: String    // 用户备注
)

package com.doubao.helper.model

/**
 * 通话按钮配置：记录豆包 App 上关闭通话和打开通话按钮的屏幕坐标。
 * 两个按钮位置可能不同。
 */
data class CallButtonConfig(
    val hangUpX: Int,   // 关闭通话按钮坐标 X
    val hangUpY: Int,   // 关闭通话按钮坐标 Y
    val callX: Int,     // 打开通话按钮坐标 X
    val callY: Int      // 打开通话按钮坐标 Y
)

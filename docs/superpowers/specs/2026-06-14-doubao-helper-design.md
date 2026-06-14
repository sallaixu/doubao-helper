# 豆包助手 (doubao-helper) 设计文档

## 概述

豆包助手是一个 Android 辅助工具 App，核心场景是**语音通话辅助**：在与豆包 App 语音通话时，实时将豆包的对话文字显示在自定义的横屏悬浮窗界面上，类似字幕/提词器效果。

## 核心方案

采用**悬浮窗 + 无障碍服务**方案：

- 自定义 App → 以悬浮窗形式全屏覆盖在前台，横屏显示对话文字
- 豆包 App → 在后台运行，维持语音通话
- 无障碍服务 → 桥梁，实时从豆包界面抓取对话文字，传递给悬浮窗

## 架构

```
┌─────────────────────────────────────────────────┐
│                  doubao-helper App               │
├─────────────┬──────────────┬────────────────────┤
│   UI 层     │   服务层     │      数据层        │
├─────────────┼──────────────┼────────────────────┤
│ MainActivity│ FloatingWin- │ ChatRepository     │
│ (权限引导)  │ dowService   │ (SharedFlow 数据)  │
│             │ (悬浮窗管理) │                    │
│ OverlayView │ DoubaoAcce-  │                    │
│ (横屏对话   │ ssibility    │                    │
│  显示界面)  │ Service      │                    │
│             │ (文字抓取)   │                    │
├─────────────┴──────────────┴────────────────────┤
│              通信层 (SharedFlow)                  │
└─────────────────────────────────────────────────┘
```

### 数据流

```
豆包App界面变化 → DoubaoAccessibilityService.onAccessibilityEvent()
    → NodeParser 解析节点树，提取对话文字
    → 构造 ChatMessage
    → ChatRepository.sharedFlow 发射数据
    → FloatingWindowService 收集数据
    → 更新 OverlayView 显示
```

## 模块设计

### 1. DoubaoAccessibilityService（无障碍服务）

**职责**：监听豆包 App 界面变化，提取对话文字。

**关键行为**：
- 通过 `packageNames` 过滤，只监听豆包 App（包名待确认，可能为 `com.larus.nova`）的界面变化
- 监听 `TYPE_WINDOW_CONTENT_CHANGED` 和 `TYPE_WINDOW_STATE_CHANGED` 事件
- 收到事件后遍历 AccessibilityNodeInfo 树，通过 NodeParser 解析对话内容
- 识别"豆包说的话"和"用户说的话"（通过节点位置、viewId 前缀、文本特征区分）
- 提取最新对话内容，通过 ChatRepository 发射

**ChatMessage 数据模型**：
```kotlin
data class ChatMessage(
    val id: String,           // 唯一标识（用于去重）
    val content: String,      // 对话文字内容
    val sender: Sender,       // 发送者：DOUBAO / USER
    val timestamp: Long       // 时间戳
)

enum class Sender { DOUBAO, USER }
```

**设计决策**：
- 使用 SharedFlow 而非 BroadcastReceiver，数据只在 App 内部流转，更轻量
- 豆包 UI 节点定位策略需可配置（viewId 可能随版本变化），后续通过配置文件或运行时调试确定
- 服务启动时自动注册监听，服务销毁时清理资源

### 2. FloatingWindowService（悬浮窗前台服务）

**职责**：管理悬浮窗生命周期，保持 App 始终在前台。

**关键行为**：
- 以 `FOREGROUND_SERVICE` 形式运行，显示常驻通知
- 启动时通过 `WindowManager` 创建全屏悬浮窗
- 收集 ChatRepository 的 SharedFlow 数据，更新 OverlayView
- 停止时移除悬浮窗

**悬浮窗参数**：
```kotlin
WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    PixelFormat.TRANSLUCENT
)
```

### 3. OverlayView（横屏对话显示界面）

**职责**：悬浮窗内的自定义布局，横屏显示实时对话文字。

**布局结构**：
```
┌──────────────────────────────────────────────────────────┐
│  豆包助手                                      ─ □ ×    │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  🤖 豆包：你好！有什么我可以帮你的吗？                       │
│                                                          │
│  🤖 豆包：当然可以，让我来帮你分析一下...                    │
│     这段代码的主要问题是内存泄漏...                          │
│                                                          │
│  🤖 豆包：你还可以尝试以下方法...                           │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  ● 实时监听中                              [暂停] [设置]  │
└──────────────────────────────────────────────────────────┘
```

**布局特点**：
- 全屏横屏显示，背景半透明（透明度可配置）
- 对话文字区域自动滚动到最新内容
- 顶部状态栏：显示连接状态、最小化/关闭按钮
- 底部工具栏：暂停监听、设置按钮
- 文字大小/颜色可配置，方便远距离阅读

### 4. MainActivity（权限引导入口）

**职责**：App 入口，引导用户授权必要权限。

**权限流程**：
```
启动 App
  → 检查悬浮窗权限 (SYSTEM_ALERT_WINDOW)
    → 未授权 → 跳转系统设置页授权
  → 检查无障碍服务权限
    → 未授权 → 跳转无障碍设置页，引导开启
  → 所有权限就绪
    → 启动 FloatingWindowService
    → 最小化 MainActivity（悬浮窗接管显示）
```

**界面**：
- 简洁的权限引导页，两个卡片分别对应两个权限
- 每个卡片显示权限名称、说明、授权状态、跳转按钮
- 全部授权后显示"启动助手"按钮
- 底部显示使用说明

**权限检查**：
- `SYSTEM_ALERT_WINDOW`：通过 `Settings.canDrawOverlays()` 检查
- 无障碍服务：通过 `AccessibilityManager.getEnabledAccessibilityServiceList()` 检查
- 使用 `ActivityResultLauncher` 处理授权返回结果

### 5. ChatRepository（数据层）

**职责**：管理对话数据的流转。

**关键行为**：
- 持有 `MutableSharedFlow<ChatMessage>`，作为数据管道
- 提供 `emitMessage()` 方法供无障碍服务调用
- 提供 `messages` SharedFlow 供悬浮窗服务收集
- 内置去重逻辑（基于 ChatMessage.id）

### 6. NodeParser（节点解析工具）

**职责**：解析豆包 App 的 AccessibilityNodeInfo 树，提取对话内容。

**关键行为**：
- 遍历节点树，根据配置的策略定位对话区域
- 提取对话文字，区分发送者
- 返回解析结果（ChatMessage 列表）
- 定位策略可配置（viewId、className、文本特征等）

## 项目结构

```
com.doubao.helper/
├── App.kt                          // Application 类
├── MainActivity.kt                  // 权限引导入口
│
├── service/
│   ├── FloatingWindowService.kt     // 悬浮窗前台服务
│   └── DoubaoAccessibilityService.kt // 无障碍服务
│
├── overlay/
│   └── OverlayView.kt              // 悬浮窗自定义布局
│
├── model/
│   ├── ChatMessage.kt              // 对话消息数据类
│   └── Sender.kt                   // 发送者枚举
│
├── repository/
│   └── ChatRepository.kt           // 对话数据管理（SharedFlow）
│
├── util/
│   ├── PermissionChecker.kt        // 权限检查工具
│   └── NodeParser.kt               // 无障碍节点解析工具
│
└── receiver/
    └── ServiceStateReceiver.kt     // 服务状态广播接收
```

## 技术栈

- **语言**：Kotlin
- **构建**：Gradle + Kotlin DSL
- **最低 SDK**：API 26 (Android 8.0)
- **目标 SDK**：API 34 (Android 14)
- **核心依赖**：
  - Kotlin + Coroutines + Flow
  - AndroidX Core / AppCompat / Material
  - Lifecycle

## AndroidManifest 关键声明

- `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`
- `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`
- 无障碍服务声明（带 `android.permission.BIND_ACCESSIBILITY_SERVICE` intent-filter）
- Application 类声明

## 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 豆包 App UI 变更导致抓取失效 | 无法提取对话文字 | NodeParser 定位策略可配置，支持运行时调试 |
| 无障碍服务被系统杀死 | 数据中断 | 前台服务保活 + 通知栏提示 + 引导用户加白名单 |
| 部分厂商 ROM 限制悬浮窗 | 无法显示 | 在权限引导页提示用户关闭系统限制 |
| 豆包 App 包名不确定 | 无法监听正确 App | 初版包名配置化，支持在设置中修改 |

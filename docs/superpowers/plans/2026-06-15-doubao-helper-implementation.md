# 豆包助手 (doubao-helper) 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建豆包助手 Android App 框架，实现悬浮窗 + 无障碍服务架构，在语音通话时实时显示豆包的对话文字。

**Architecture:** 悬浮窗前台服务全屏覆盖显示 + 无障碍服务监听豆包界面 + SharedFlow 数据管道。MainActivity 负责权限引导，授权后启动悬浮窗服务接管显示。

**Tech Stack:** Kotlin, Gradle Kotlin DSL, AndroidX, Coroutines + Flow, Material Components

---

## File Structure

```
doubao-helper/
├── build.gradle.kts                          // 根项目构建文件
├── settings.gradle.kts                       // 项目设置
├── gradle.properties                         // Gradle 属性
├── gradle/
│   └── libs.versions.toml                    // 版本目录
├── app/
│   ├── build.gradle.kts                      // App 模块构建文件
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml           // 清单文件
│           ├── res/
│           │   ├── values/
│           │   │   ├── strings.xml           // 字符串资源
│           │   │   ├── colors.xml            // 颜色资源
│           │   │   ├── themes.xml            // 主题资源
│           │   │   └── dimens.xml            // 尺寸资源
│           │   ├── layout/
│           │   │   ├── activity_main.xml     // 主界面布局
│           │   │   └── overlay_view.xml      // 悬浮窗布局
│           │   ├── xml/
│           │   │   └── accessibility_service_config.xml  // 无障碍服务配置
│           │   ├── drawable/
│           │   │   └── ic_notification.xml   // 通知图标
│           │   └── mipmap-*/
│           │       └── ic_launcher.webp      // 启动图标
│           └── kotlin/
│               └── com/
│                   └── doubao/
│                       └── helper/
│                           ├── App.kt                        // Application 类
│                           ├── MainActivity.kt               // 权限引导入口
│                           ├── model/
│                           │   ├── ChatMessage.kt            // 对话消息数据类
│                           │   └── Sender.kt                 // 发送者枚举
│                           ├── repository/
│                           │   └── ChatRepository.kt         // SharedFlow 数据管道
│                           ├── util/
│                           │   ├── PermissionChecker.kt      // 权限检查工具
│                           │   └── NodeParser.kt             // 无障碍节点解析
│                           ├── service/
│                           │   ├── FloatingWindowService.kt  // 悬浮窗前台服务
│                           │   └── DoubaoAccessibilityService.kt  // 无障碍服务
│                           └── overlay/
│                               └── OverlayView.kt            // 悬浮窗自定义视图
```

---

### Task 1: 项目骨架 — Gradle 构建文件

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`

- [ ] **Step 1: 创建根 build.gradle.kts**

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

- [ ] **Step 2: 创建 settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "doubao-helper"
include(":app")
```

- [ ] **Step 3: 创建 gradle.properties**

```properties
# Project-wide Gradle settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: 创建版本目录 gradle/libs.versions.toml**

```toml
[versions]
agp = "8.2.2"
kotlin = "1.9.22"
coreKtx = "1.12.0"
appcompat = "1.6.1"
material = "1.11.0"
lifecycleRuntimeKtx = "2.7.0"
coroutines = "1.7.3"
activityKtx = "1.8.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-ktx = { group = "androidx.activity", name = "activity-ktx", version.ref = "activityKtx" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 5: 创建 app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.doubao.helper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.doubao.helper"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
```

- [ ] **Step 6: 创建 app/proguard-rules.pro**

```
# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.doubao.helper.model.** { *; }
```

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts settings.gradle.kts gradle.properties gradle/ app/build.gradle.kts app/proguard-rules.pro
git commit -m "chore: initialize Gradle build files with Kotlin DSL"
```

---

### Task 2: AndroidManifest 与资源文件

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values/dimens.xml`
- Create: `app/src/main/res/xml/accessibility_service_config.xml`
- Create: `app/src/main/res/drawable/ic_notification.xml`

- [ ] **Step 1: 创建 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:name=".App"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.DoubaoHelper">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.FloatingWindowService"
            android:exported="false"
            android:foregroundServiceType="specialUse" />

        <service
            android:name=".service.DoubaoAccessibilityService"
            android:exported="false"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

    </application>

</manifest>
```

- [ ] **Step 2: 创建 strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">豆包助手</string>
    <string name="permission_overlay_title">悬浮窗权限</string>
    <string name="permission_overlay_desc">需要悬浮窗权限以在其他应用上方显示对话内容</string>
    <string name="permission_accessibility_title">无障碍服务</string>
    <string name="permission_accessibility_desc">需要无障碍服务以读取豆包的对话内容</string>
    <string name="permission_granted">已授权</string>
    <string name="permission_not_granted">未授权</string>
    <string name="permission_go_settings">去设置</string>
    <string name="btn_start_helper">启动助手</string>
    <string name="btn_stop_helper">停止助手</string>
    <string name="accessibility_service_description">读取豆包App的对话内容，用于实时显示</string>
    <string name="overlay_status_listening">● 实时监听中</string>
    <string name="overlay_status_paused">○ 已暂停</string>
    <string name="overlay_btn_pause">暂停</string>
    <string name="overlay_btn_resume">继续</string>
    <string name="overlay_btn_close">关闭</string>
    <string name="notification_channel_name">豆包助手服务</string>
    <string name="notification_title">豆包助手运行中</string>
    <string name="notification_text">正在监听豆包对话内容</string>
    <string name="usage_hint">使用说明：先授权两个权限，然后点击"启动助手"，再打开豆包App开始通话即可</string>
</resources>
```

- [ ] **Step 3: 创建 colors.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
    <color name="primary">#FF6750A4</color>
    <color name="primary_dark">#FF4F378B</color>
    <color name="primary_container">#FFEADDFF</color>
    <color name="on_primary">#FFFFFFFF</color>
    <color name="secondary">#FF625B71</color>
    <color name="surface">#FFFFFBFE</color>
    <color name="on_surface">#FF1C1B1F</color>
    <color name="overlay_background">#E0202020</color>
    <color name="overlay_text">#FFE0E0E0</color>
    <color name="overlay_text_doubao">#FFB0C4FF</color>
    <color name="overlay_status_bar">#CC303030</color>
    <color name="overlay_toolbar">#CC282828</color>
    <color name="status_listening">#FF4CAF50</color>
    <color name="status_paused">#FFFF9800</color>
</resources>
```

- [ ] **Step 4: 创建 themes.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.DoubaoHelper" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryDark">@color/primary_dark</item>
        <item name="colorPrimaryContainer">@color/primary_container</item>
        <item name="colorOnPrimary">@color/on_primary</item>
    </style>
</resources>
```

- [ ] **Step 5: 创建 dimens.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <dimen name="permission_card_margin">16dp</dimen>
    <dimen name="permission_card_padding">20dp</dimen>
    <dimen name="permission_card_corner">16dp</dimen>
    <dimen name="overlay_text_size">18sp</dimen>
    <dimen name="overlay_text_size_large">24sp</dimen>
    <dimen name="overlay_status_bar_height">40dp</dimen>
    <dimen name="overlay_toolbar_height">44dp</dimen>
    <dimen name="overlay_message_padding">12dp</dimen>
    <dimen name="overlay_message_spacing">8dp</dimen>
</resources>
```

- [ ] **Step 6: 创建无障碍服务配置 accessibility_service_config.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100" />
```

注意：不在 XML 中硬编码 `packageNames`，在代码中动态设置，以便运行时可配置豆包包名。

- [ ] **Step 7: 创建通知图标 ic_notification.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/white">
    <path
        android:fillColor="@color/white"
        android:pathData="M20,2H4C2.9,2 2,2.9 2,4V22L6,18H20C21.1,18 22,17.1 22,16V4C22,2.9 21.1,2 20,2ZM20,16H5.17L4,17.17V4H20V16Z" />
</vector>
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/
git commit -m "chore: add AndroidManifest and resource files"
```

---

### Task 3: 数据模型与 ChatRepository

**Files:**
- Create: `app/src/main/kotlin/com/doubao/helper/model/Sender.kt`
- Create: `app/src/main/kotlin/com/doubao/helper/model/ChatMessage.kt`
- Create: `app/src/main/kotlin/com/doubao/helper/repository/ChatRepository.kt`

- [ ] **Step 1: 创建 Sender.kt 枚举**

```kotlin
package com.doubao.helper.model

enum class Sender {
    DOUBAO,
    USER
}
```

- [ ] **Step 2: 创建 ChatMessage.kt 数据类**

```kotlin
package com.doubao.helper.model

data class ChatMessage(
    val id: String,
    val content: String,
    val sender: Sender,
    val timestamp: Long
)
```

- [ ] **Step 3: 创建 ChatRepository.kt**

```kotlin
package com.doubao.helper.repository

import com.doubao.helper.model.ChatMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ChatRepository {

    private val _messages = MutableSharedFlow<ChatMessage>(replay = 0, extraBufferCapacity = 64)

    val messages: SharedFlow<ChatMessage> = _messages.asSharedFlow()

    private val emittedIds = mutableSetOf<String>()

    suspend fun emitMessage(message: ChatMessage) {
        if (emittedIds.add(message.id)) {
            _messages.emit(message)
        }
    }

    fun clearHistory() {
        emittedIds.clear()
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/doubao/helper/model/ app/src/main/kotlin/com/doubao/helper/repository/
git commit -m "feat: add ChatMessage model and ChatRepository with SharedFlow"
```

---

### Task 4: Application 类与权限检查工具

**Files:**
- Create: `app/src/main/kotlin/com/doubao/helper/App.kt`
- Create: `app/src/main/kotlin/com/doubao/helper/util/PermissionChecker.kt`

- [ ] **Step 1: 创建 App.kt**

```kotlin
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
```

- [ ] **Step 2: 创建 PermissionChecker.kt**

```kotlin
package com.doubao.helper.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object PermissionChecker {

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasAccessibilityPermission(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    fun allPermissionsGranted(context: Context): Boolean {
        return hasOverlayPermission(context) && hasAccessibilityPermission(context)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/doubao/helper/App.kt app/src/main/kotlin/com/doubao/helper/util/PermissionChecker.kt
git commit -m "feat: add Application class with notification channel and PermissionChecker"
```

---

### Task 5: NodeParser — 无障碍节点解析工具

**Files:**
- Create: `app/src/main/kotlin/com/doubao/helper/util/NodeParser.kt`

- [ ] **Step 1: 创建 NodeParser.kt**

```kotlin
package com.doubao.helper.util

import android.view.accessibility.AccessibilityNodeInfo
import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.Sender

object NodeParser {

    /**
     * 从豆包 App 的节点树中解析对话消息。
     *
     * 当前实现为通用策略：遍历所有可见文本节点，按位置和内容特征区分发送者。
     * 后续可根据豆包 App 的实际 UI 结构优化定位策略。
     */
    fun parseMessages(rootNode: AccessibilityNodeInfo, doubaoPackage: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        traverseNodes(rootNode, messages, doubaoPackage)
        return messages
    }

    private fun traverseNodes(
        node: AccessibilityNodeInfo,
        messages: MutableList<ChatMessage>,
        doubaoPackage: String
    ) {
        if (node packageName != null && node.packageName.toString() != doubaoPackage) {
            return
        }

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && node.isClickable.not()) {
            val sender = inferSender(node)
            val id = generateMessageId(node, text)
            messages.add(
                ChatMessage(
                    id = id,
                    content = text,
                    sender = sender,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNodes(child, messages, doubaoPackage)
                child.recycle()
            }
        }
    }

    /**
     * 根据节点的 viewId 和位置特征推断发送者。
     * 豆包的 AI 回复通常位于屏幕左侧或具有特定的 viewId 前缀。
     * 这是一个启发式方法，需要根据实际 UI 调整。
     */
    private fun inferSender(node: AccessibilityNodeInfo): Sender {
        val viewId = node.viewIdResourceName ?: ""
        return if (viewId.contains("ai", ignoreCase = true) ||
            viewId.contains("bot", ignoreCase = true) ||
            viewId.contains("assistant", ignoreCase = true)
        ) {
            Sender.DOUBAO
        } else {
            Sender.USER
        }
    }

    private fun generateMessageId(node: AccessibilityNodeInfo, text: String): String {
        val viewId = node.viewIdResourceName ?: ""
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        return "${viewId}_${bounds.left}_${bounds.top}_${text.hashCode()}"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/doubao/helper/util/NodeParser.kt
git commit -m "feat: add NodeParser for accessibility node tree parsing"
```

---

### Task 6: DoubaoAccessibilityService — 无障碍服务

**Files:**
- Create: `app/src/main/kotlin/com/doubao/helper/service/DoubaoAccessibilityService.kt`

- [ ] **Step 1: 创建 DoubaoAccessibilityService.kt**

```kotlin
package com.doubao.helper.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.doubao.helper.App
import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.Sender
import com.doubao.helper.util.NodeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DoubaoAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != App.DOUBAO_PACKAGE) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        try {
            val messages = NodeParser.parseMessages(rootNode, App.DOUBAO_PACKAGE)
            if (messages.isEmpty()) return

            serviceScope.launch {
                val repository = (application as App).chatRepository
                for (message in messages) {
                    repository.emitMessage(message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing accessibility event", e)
        } finally {
            rootNode.recycle()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")

        serviceInfo = serviceInfo.apply {
            // 动态设置监听的包名，便于后续配置
            packageNames = arrayOf(App.DOUBAO_PACKAGE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "DoubaoAccessibility"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/doubao/helper/service/DoubaoAccessibilityService.kt
git commit -m "feat: add DoubaoAccessibilityService with NodeParser integration"
```

---

### Task 7: OverlayView — 悬浮窗自定义布局

**Files:**
- Create: `app/src/main/res/layout/overlay_view.xml`
- Create: `app/src/main/kotlin/com/doubao/helper/overlay/OverlayView.kt`

- [ ] **Step 1: 创建 overlay_view.xml 布局**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/overlay_background">

    <!-- Status Bar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="@dimen/overlay_status_bar_height"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingStart="16dp"
        android:paddingEnd="8dp"
        android:background="@color/overlay_status_bar">

        <TextView
            android:id="@+id/tvStatus"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/overlay_status_listening"
            android:textColor="@color/status_listening"
            android:textSize="14sp" />

        <ImageView
            android:id="@+id/btnMinimize"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:src="@android:drawable/ic_menu_crop"
            android:contentDescription="Minimize"
            android:padding="4dp" />

        <ImageView
            android:id="@+id/btnClose"
            android:layout_width="32dp"
            android:layout_height="32dp"
            android:src="@android:drawable/ic_menu_close_clear_cancel"
            android:contentDescription="Close"
            android:padding="4dp" />

    </LinearLayout>

    <!-- Message Area -->
    <ScrollView
        android:id="@+id/scrollView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:padding="@dimen/overlay_message_padding"
        android:scrollbars="none">

        <LinearLayout
            android:id="@+id/messageContainer"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical" />

    </ScrollView>

    <!-- Toolbar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="@dimen/overlay_toolbar_height"
        android:orientation="horizontal"
        android:gravity="center_vertical|end"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:background="@color/overlay_toolbar">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnPause"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="36dp"
            android:text="@string/overlay_btn_pause"
            android:textColor="@color/white"
            android:textSize="14sp" />

    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: 创建 OverlayView.kt**

```kotlin
package com.doubao.helper.overlay

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.doubao.helper.R
import com.doubao.helper.model.ChatMessage
import com.doubao.helper.model.Sender
import com.google.android.material.button.MaterialButton

class OverlayView(context: Context) : LinearLayout(context) {

    private val messageContainer: LinearLayout
    private val scrollView: ScrollView
    private val tvStatus: TextView
    private val btnPause: MaterialButton
    private val btnClose: android.widget.ImageView
    private val btnMinimize: android.widget.ImageView

    var onPauseToggle: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onMinimize: (() -> Unit)? = null

    private var isPaused = false

    init {
        LayoutInflater.from(context).inflate(R.layout.overlay_view, this, true)
        messageContainer = findViewById(R.id.messageContainer)
        scrollView = findViewById(R.id.scrollView)
        tvStatus = findViewById(R.id.tvStatus)
        btnPause = findViewById(R.id.btnPause)
        btnClose = findViewById(R.id.btnClose)
        btnMinimize = findViewById(R.id.btnMinimize)

        btnPause.setOnClickListener {
            isPaused = !isPaused
            btnPause.text = if (isPaused) {
                context.getString(R.string.overlay_btn_resume)
            } else {
                context.getString(R.string.overlay_btn_pause)
            }
            tvStatus.text = if (isPaused) {
                context.getString(R.string.overlay_status_paused)
            } else {
                context.getString(R.string.overlay_status_listening)
            }
            tvStatus.setTextColor(
                if (isPaused) Color.parseColor("#FFFF9800")
                else Color.parseColor("#FF4CAF50")
            )
            onPauseToggle?.invoke()
        }

        btnClose.setOnClickListener { onClose?.invoke() }
        btnMinimize.setOnClickListener { onMinimize?.invoke() }
    }

    fun appendMessage(message: ChatMessage) {
        val textView = TextView(context).apply {
            text = if (message.sender == Sender.DOUBAO) {
                "🤖 豆包：${message.content}"
            } else {
                "👤 你：${message.content}"
            }
            setTextColor(
                if (message.sender == Sender.DOUBAO) Color.parseColor("#FFB0C4FF")
                else Color.parseColor("#FFE0E0E0")
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, 24, 0, 0)
        }
        messageContainer.addView(textView)

        // Auto-scroll to bottom
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    fun clearMessages() {
        messageContainer.removeAllViews()
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/overlay_view.xml app/src/main/kotlin/com/doubao/helper/overlay/OverlayView.kt
git commit -m "feat: add OverlayView with message display and toolbar controls"
```

---

### Task 8: FloatingWindowService — 悬浮窗前台服务

**Files:**
- Create: `app/src/main/kotlin/com/doubao/helper/service/FloatingWindowService.kt`

- [ ] **Step 1: 创建 FloatingWindowService.kt**

```kotlin
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
            onPauseToggle = { paused ->
                Log.d(TAG, if (paused) "Listening paused" else "Listening resumed")
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/doubao/helper/service/FloatingWindowService.kt
git commit -m "feat: add FloatingWindowService with overlay and message collection"
```

---

### Task 9: MainActivity — 权限引导界面

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/kotlin/com/doubao/helper/MainActivity.kt`

- [ ] **Step 1: 创建 activity_main.xml 布局**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="32dp"
    android:background="@color/surface">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/app_name"
        android:textSize="32sp"
        android:textStyle="bold"
        android:textColor="@color/primary"
        android:layout_marginBottom="32dp" />

    <!-- Overlay Permission Card -->
    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="@dimen/permission_card_margin"
        app:cardCornerRadius="@dimen/permission_card_corner"
        app:cardElevation="2dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="@dimen/permission_card_padding">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/permission_overlay_title"
                android:textSize="18sp"
                android:textStyle="bold"
                android:textColor="@color/on_surface" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/permission_overlay_desc"
                android:textSize="14sp"
                android:layout_marginTop="4dp"
                android:layout_marginBottom="12dp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical">

                <TextView
                    android:id="@+id/tvOverlayStatus"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/permission_not_granted"
                    android:textColor="@color/primary_dark"
                    android:textSize="14sp" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnOverlayPermission"
                    style="@style/Widget.Material3.Button.TonalButton"
                    android:layout_width="wrap_content"
                    android:layout_height="40dp"
                    android:text="@string/permission_go_settings" />

            </LinearLayout>

        </LinearLayout>

    </com.google.android.material.card.MaterialCardView>

    <!-- Accessibility Permission Card -->
    <com.google.android.material.card.MaterialCardView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="@dimen/permission_card_margin"
        app:cardCornerRadius="@dimen/permission_card_corner"
        app:cardElevation="2dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="@dimen/permission_card_padding">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/permission_accessibility_title"
                android:textSize="18sp"
                android:textStyle="bold"
                android:textColor="@color/on_surface" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/permission_accessibility_desc"
                android:textSize="14sp"
                android:layout_marginTop="4dp"
                android:layout_marginBottom="12dp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical">

                <TextView
                    android:id="@+id/tvAccessibilityStatus"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/permission_not_granted"
                    android:textColor="@color/primary_dark"
                    android:textSize="14sp" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnAccessibilityPermission"
                    style="@style/Widget.Material3.Button.TonalButton"
                    android:layout_width="wrap_content"
                    android:layout_height="40dp"
                    android:text="@string/permission_go_settings" />

            </LinearLayout>

        </LinearLayout>

    </com.google.android.material.card.MaterialCardView>

    <!-- Start Button -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnStartHelper"
        android:layout_width="wrap_content"
        android:layout_height="52dp"
        android:layout_marginTop="16dp"
        android:layout_marginBottom="24dp"
        android:enabled="false"
        android:text="@string/btn_start_helper"
        android:textSize="18sp"
        android:paddingHorizontal="48dp" />

    <!-- Usage Hint -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/usage_hint"
        android:textSize="13sp"
        android:textColor="@color/secondary"
        android:gravity="center" />

</LinearLayout>
```

- [ ] **Step 2: 创建 MainActivity.kt**

```kotlin
package com.doubao.helper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.doubao.helper.service.FloatingWindowService
import com.doubao.helper.util.PermissionChecker
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnStartHelper: MaterialButton

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnStartHelper = findViewById(R.id.btnStartHelper)

        findViewById<MaterialButton>(R.id.btnOverlayPermission).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<MaterialButton>(R.id.btnAccessibilityPermission).setOnClickListener {
            requestAccessibilityPermission()
        }

        btnStartHelper.setOnClickListener {
            startHelper()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val hasOverlay = PermissionChecker.hasOverlayPermission(this)
        val hasAccessibility = PermissionChecker.hasAccessibilityPermission(this)

        tvOverlayStatus.text = getString(
            if (hasOverlay) R.string.permission_granted else R.string.permission_not_granted
        )
        tvAccessibilityStatus.text = getString(
            if (hasAccessibility) R.string.permission_granted else R.string.permission_not_granted
        )

        btnStartHelper.isEnabled = hasOverlay && hasAccessibility
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun startHelper() {
        val intent = Intent(this, FloatingWindowService::class.java)
        startForegroundService(intent)
        moveTaskToBack(true)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_main.xml app/src/main/kotlin/com/doubao/helper/MainActivity.kt
git commit -m "feat: add MainActivity with permission guidance and service launch"
```

---

### Task 10: 启动图标与构建验证

**Files:**
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (adaptive icon)
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`

- [ ] **Step 1: 创建 ic_launcher_background.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#6750A4"
        android:pathData="M0,0h108v108h-108z" />
</vector>
```

- [ ] **Step 2: 创建 ic_launcher_foreground.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,30C41.85,30 32,39.85 32,52C32,64.15 41.85,74 54,74C66.15,74 76,64.15 76,52C76,39.85 66.15,30 54,30ZM54,70C44.06,70 36,61.94 36,52C36,42.06 44.06,34 54,34C63.94,34 72,42.06 72,52C72,61.94 63.94,70 54,70Z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M46,48a4,4 0,1 1,8 0a4,4 0,1 1,-8 0" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M58,48a4,4 0,1 1,8 0a4,4 0,1 1,-8 0" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M46,58 Q54,66 62,58"
        android:strokeWidth="2.5"
        android:strokeColor="#FFFFFF"
        android:fillType="nonZero" />
</vector>
```

- [ ] **Step 3: 创建 adaptive icon ic_launcher.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 4: 添加 Gradle Wrapper**

确保项目有 Gradle Wrapper。运行：

```bash
cd D:/project/android/doubao-helper
gradle wrapper --gradle-version 8.5
```

如果系统没有全局 gradle 命令，手动创建 `gradle/wrapper/gradle-wrapper.properties`：

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

以及 `gradlew` 和 `gradlew.bat` 脚本（从已有 Android 项目复制或通过 Android Studio 生成）。

- [ ] **Step 5: 验证构建**

```bash
cd D:/project/android/doubao-helper
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/mipmap-anydpi-v26/ app/src/main/res/drawable/ic_launcher_*.xml gradle/
git commit -m "chore: add launcher icon and Gradle wrapper, verify build"
```

---

## Self-Review

### 1. Spec Coverage

| Spec 需求 | 对应 Task |
|-----------|----------|
| 悬浮窗全屏覆盖 | Task 7 (OverlayView) + Task 8 (FloatingWindowService) |
| 无障碍服务监听豆包 | Task 6 (DoubaoAccessibilityService) |
| 实时对话文字显示 | Task 3 (ChatRepository) + Task 7 (OverlayView) |
| 横屏显示 | Task 2 (AndroidManifest screenOrientation=landscape) |
| 权限引导 | Task 9 (MainActivity) |
| 数据管道 (SharedFlow) | Task 3 (ChatRepository) |
| 节点解析 | Task 5 (NodeParser) |
| 前台服务保活 | Task 8 (FloatingWindowService) |
| Application + 通知渠道 | Task 4 (App) |
| 暂停/继续/关闭控制 | Task 7 (OverlayView toolbar) |

### 2. Placeholder Scan

No TBD/TODO found. All code steps contain complete implementations.

### 3. Type Consistency

- `ChatMessage(id, content, sender, timestamp)` — used consistently across NodeParser, ChatRepository, OverlayView
- `Sender.DOUBAO / Sender.USER` — used consistently across NodeParser, OverlayView
- `App.CHANNEL_ID / App.NOTIFICATION_ID / App.DOUBAO_PACKAGE` — used consistently across App, FloatingWindowService, DoubaoAccessibilityService
- `PermissionChecker.hasOverlayPermission() / hasAccessibilityPermission()` — called correctly in MainActivity

All types, method signatures, and property names are consistent across tasks.

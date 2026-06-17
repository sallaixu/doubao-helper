# 🤖 豆包助手 (Doubao Helper)

> 一个 Android 辅助工具，通过无障碍服务实时提取豆包 App 的对话内容，以悬浮窗形式展示，支持语音唤醒、自动待机和 OLED 万年历待机屏保。

---

## ⚠️ 免责声明（必读）

**本项目仅供 Android 无障碍服务技术学习与交流使用，严禁用于任何商业用途。**

- 🚫 本项目**未获豆包（字节跳动/抖音旗下）官方授权**，与字节跳动公司无任何关联。
- 🚫 本项目**不提供豆包 App 的任何破解、修改或逆向**，仅使用 Android 系统原生的 AccessibilityService API 读取屏幕上已展示的文本节点。
- 🚫 本项目**不收集、不存储、不上传**任何用户数据，所有处理均在本地设备完成。
- 🚫 请勿将本项目用于侵犯他人隐私、违反豆包用户协议或其他违法行为。
- ✅ 使用本项目前，请确保您已充分了解并遵守豆包 App 的用户协议及相关法律法规。
- ✅ 如字节跳动公司认为本项目存在侵权，请联系作者立即删除。

**使用本项目即表示您已阅读并同意以上声明，因不当使用造成的任何后果由使用者自行承担。**

---

## ✨ 功能特性

### 📋 实时对话提取
通过 Android AccessibilityService 监听豆包 App 的界面变化，实时提取 AI 回复内容，以悬浮窗形式叠加显示在豆包界面之上。

- **智能去重**：同一消息不会重复显示
- **续写检测**：AI 逐字输出时自动拼接，不会丢失尾部文字
- **滚动兼容**：豆包对话列表滚动时不会出现重复消息

### 🎯 可视化选区调试
提供全屏半透明覆盖层，点击即可选中豆包界面上的文本节点，自动生成监听规则（MonitorRule），无需手动配置 viewId / className。

- 支持 viewId、className、屏幕区域、文本长度等多维度匹配
- 规则持久化存储，重启后自动恢复

### 🎤 语音唤醒
待机模式下使用 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) KeywordSpotter 进行离线唤醒词检测：

- **完全离线**：不依赖任何云端服务，唤醒词识别在本地完成
- **自定义唤醒词**：支持设置任意中文唤醒词，自动转换为拼音 token
- **基于 Zipformer2** 模型的关键词检测，低功耗运行

### 💤 智能待机
- 对话超过设定时间无新消息，自动进入待机模式
- 待机时自动点击挂断按钮结束通话
- 显示 **OLED 万年历** 待机屏保（黑底白字时钟），带防烧屏微移

### 📞 通话按钮管理
通过选区模式配置豆包的拨通/挂断按钮位置，支持待机自动挂断、唤醒自动拨通。

### 📱 横屏适配
- 强制横屏运行
- 自动检测屏幕旋转，悬浮窗和面板自适应方向

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                      豆包助手 App                        │
├─────────────┬──────────────┬──────────────┬─────────────┤
│ MainActivity│  FloatingWin │  DoubaoAccess│  WakeupWord │
│  (权限管理)  │  dowService  │  ibilityServ │  Detector   │
│             │  (悬浮窗服务)  │  ice         │ (sherpa-onnx)│
│             │              │  (无障碍服务)  │             │
├─────────────┴──────┬───────┴──────────────┴─────────────┤
│                     │                                    │
│        OverlayManager (悬浮窗 UI 管理)                     │
│        ├── 小悬浮球 (可拖动)                               │
│        ├── 全屏对话面板 (ScrollView + TextView)            │
│        ├── 选区覆盖层 (全屏透明)                           │
│        └── StandbyView (OLED 万年历)                      │
│                     │                                    │
├─────────────────────┴────────────────────────────────────┤
│              ChatRepository (消息去重/续写/分发)            │
├──────────────────────────────────────────────────────────┤
│              NodeParser (节点匹配/规则引擎)                 │
├──────────────────────────────────────────────────────────┤
│              PinyinTokenizer (中文→拼音Token)              │
└──────────────────────────────────────────────────────────┘
```

### 核心技术栈

| 技术 | 用途 |
|------|------|
| **AccessibilityService** | 读取豆包 App 的无障碍节点树，提取文本内容 |
| **sherpa-onnx** (Zipformer2 KWS) | 离线唤醒词检测，本地语音识别 |
| **ForegroundService** (microphone) | 后台持续录音 + 前台保活 |
| **WindowManager** (TYPE_APPLICATION_OVERLAY) | 悬浮窗显示 |
| **Kotlin Coroutines + SharedFlow** | 异步消息流，实时 UI 更新 |

---

## 📸 功能截图

> 由于涉及第三方 App 界面，此处不展示豆包 App 的实际截图。

| 功能 | 说明 |
|------|------|
| 🔴 小悬浮球 | 可拖动的入口，点击展开对话面板 |
| 📝 对话面板 | 全屏半透明面板，实时滚动显示提取的文本 |
| 🎯 选区调试 | 点击豆包界面选择要监听的文本区域 |
| 🕐 待机屏保 | OLED 友好的万年历时钟，支持语音唤醒 |

---

## 🔧 构建与安装

### 环境要求

- Android Studio (推荐最新版)
- JDK 17+
- Android SDK，compileSdk 36, minSdk 26 (Android 8.0+)
- 一台已开启开发者模式的 Android 设备

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/sallaixu/doubao-helper.git
cd doubao-helper

# 构建 Debug APK
./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 使用步骤

1. **授予权限**：打开 App，依次授权悬浮窗权限、无障碍服务权限、录音权限
2. **配置唤醒词**（可选）：在主界面设置唤醒词，默认"小豆小豆"
3. **配置通话按钮**（可选）：点击"选通话按钮"，在豆包界面上选择挂断和拨通按钮的位置
4. **启动助手**：点击"启动助手"，切换到豆包 App 开始通话
5. **选区调试**：首次使用时，点击悬浮球展开面板 → 点击"选区调试"，在豆包界面上点选要监听的文本区域
6. **待机唤醒**：对话静默超过设定时间后自动待机，说唤醒词即可恢复

---

## 📁 项目结构

```
app/src/main/kotlin/com/doubao/helper/
├── App.kt                          # Application，全局状态管理
├── MainActivity.kt                 # 主界面：权限管理 + 设置项
├── model/
│   ├── ChatMessage.kt             # 消息数据模型
│   ├── MonitorRule.kt             # 监听规则模型
│   ├── DebugNodeInfo.kt           # 调试节点信息
│   ├── MicButtonConfig.kt         # 通话按钮配置
│   └── Sender.kt                  # 消息发送者枚举
├── service/
│   ├── DoubaoAccessibilityService.kt  # 无障碍服务：监听豆包界面变化
│   └── FloatingWindowService.kt       # 前台服务：管理悬浮窗生命周期
├── overlay/
│   ├── OverlayManager.kt          # 悬浮窗管理：面板/选区/待机
│   ├── OverlayView.kt             # 面板布局
│   ├── RotatedContainer.kt        # 竖屏旋转容器
│   └── StandbyView.kt             # OLED 万年历待机视图
├── repository/
│   └── ChatRepository.kt          # 消息仓库：去重/续写/分发
├── util/
│   ├── NodeParser.kt              # 节点解析：规则匹配/文本提取
│   ├── RuleStorage.kt             # 监听规则持久化
│   ├── MicButtonStorage.kt        # 通话按钮配置持久化
│   └── PermissionChecker.kt       # 权限检查工具
└── wakeup/
    ├── WakeupWordDetector.kt       # 唤醒词检测器 (sherpa-onnx)
    └── PinyinTokenizer.kt          # 中文→拼音 token 转换
```

---

## 🔑 关键设计

### 消息去重与续写

```
NodeParser 提取节点文本
    ↓ regionKey = viewId + contentPrefixHash (滚动安全)
    ↓ id = regionKey + contentHashCode (完全去重)
ChatRepository.emitMessage()
    ├── id 已存在 → 丢弃（完全重复）
    ├── 同 regionKey + 新内容以旧内容开头 → 续写更新
    ├── 同 regionKey + 内容不同 → 新消息替换
    └── 新 regionKey → 新消息
OverlayManager.appendMessage()
    ├── 找到 regionKey 对应的 TextView → 原地更新文字
    └── 未找到 → 新建 TextView
```

### 唤醒词检测流程

```
中文唤醒词 "小豆小豆"
    ↓ PinyinTokenizer
拼音 token "x iǎo d òu x iǎo d òu @小豆小豆"
    ↓ validateKeywords (确保所有 token 在 tokens.txt 中)
KeywordSpotter (Zipformer2 模型)
    ↓ AudioRecord 16kHz 录音
    ↓ acceptWaveform → decode 循环
    ↓ getResult → keyword 非空
唤醒回调 → 退出待机 → 自动拨通
```

---

## 🤝 致谢

- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — 离线语音识别框架，提供 KeywordSpotter 能力
- [豆包](https://www.doubao.com/) — 优秀的 AI 助手产品（本项目仅作为辅助工具，与官方无关）

---

## 📜 开源协议

本项目基于 [MIT License](LICENSE) 开源。

**再次强调：本项目仅供学习交流，请勿用于任何违反第三方 App 用户协议或法律法规的场景。使用者需自行承担相关责任。**

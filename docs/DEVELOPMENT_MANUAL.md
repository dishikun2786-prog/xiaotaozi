# XiaoTaozi (小桃子) — 项目技术架构与开发手册

## 一、项目概述

**项目名称**: XiaoTaozi / 小桃子 / AutoPilot  
**仓库地址**: https://github.com/Turbo1123/XiaoTaozi.git  
**许可证**: MIT (Copyright 2025 XiaoTaozi Team)  
**当前版本**: 1.4.2 (versionCode=7)  

XiaoTaozi 是一个**基于视觉语言模型(VLM)的 Android 手机自动化助手**。它通过截图让 AI "看见"手机屏幕，通过 Shizuku 执行 shell 命令来"操作"手机，从而理解用户的自然语言指令并自动完成复杂任务（如点外卖、导航、发消息、播放音乐等）。全程无需电脑连接、无需 Root。

---

## 二、技术架构

### 2.1 技术栈总览

| 层面 | 技术选型 | 说明 |
|------|---------|------|
| **语言** | Kotlin 1.9.20 | JVM Target 17 |
| **UI框架** | Jetpack Compose + Material 3 | 纯 Compose，零 XML 布局 |
| **构建系统** | Gradle 8.2 + AGP 8.2.0 | Kotlin DSL (.kts) |
| **最低SDK** | 26 (Android 8.0) | 覆盖 95%+ 设备 |
| **目标SDK** | 34 (Android 14) | |
| **系统权限** | Shizuku 13.1.5 | 替代 AccessibilityService |
| **网络请求** | OkHttp 4.12.0 | 用于 VLM API 调用 |
| **加密存储** | EncryptedSharedPreferences | AES-256-GCM |
| **崩溃上报** | Firebase Crashlytics + Analytics | |
| **协程** | kotlinx-coroutines-android 1.7.3 | 异步操作 |

### 2.2 分层架构

```
┌─────────────────────────────────────────────┐
│              Compose UI Layer               │
│  HomeScreen / Capabilities / History /      │
│  Settings / Onboarding / OverlayService     │
├─────────────────────────────────────────────┤
│             Agent Layer (核心)              │
│  MobileAgent (主循环)                       │
│  Manager → Executor → Reflector → Notetaker │
│  ConversationMemory / InfoPool              │
├─────────────────────────────────────────────┤
│           Skills Layer (意图路由)            │
│  SkillManager → SkillRegistry              │
│  两种执行路径: DELEGATION / GUI_AUTOMATION   │
├─────────────────────────────────────────────┤
│           Tools Layer (原子能力)             │
│  search_apps / open_app / clipboard /       │
│  deep_link / shell / http                   │
├─────────────────────────────────────────────┤
│         Controller Layer (硬件抽象)          │
│  DeviceController / AppScanner              │
├─────────────────────────────────────────────┤
│          Service Layer (系统桥接)            │
│  ShellService (Shizuku AIDL)               │
├─────────────────────────────────────────────┤
│           Android Shell                     │
│  screencap / input tap|swipe|text /         │
│  monkey / am / cmd                          │
└─────────────────────────────────────────────┘
```

### 2.3 核心 Agent 循环

```
             ┌──────────┐
             │ 截取屏幕  │ ← screencap via Shizuku
             └────┬─────┘
                  ↓
             ┌──────────┐
             │ Manager  │ ← 制定/更新计划，检测敏感页面
             └────┬─────┘
                  ↓
             ┌──────────┐
             │ Executor │ ← 决定具体动作 (坐标/滑动/输入)
             └────┬─────┘
                  ↓
             ┌──────────┐
             │ 执行动作  │ ← 通过 DeviceController 操作
             └────┬─────┘
                  ↓
             ┌──────────┐
             │Reflector │ ← 对比前后截图，评估结果 (A/B/C)
             └────┬─────┘
                  ↓
             ┌──────────┐
             │Notetaker │ ← 记录重要信息 (可选)
             └──────────┘
                  ↓
              循环直到完成或达到最大步数
```

### 2.4 三种 VLM 执行模式

| 模式 | 客户端类 | 适用场景 |
|------|---------|---------|
| **OpenAI兼容** | `VLMClient.kt` | Qwen-VL, GPT-4V, Claude (云端) |
| **GUI-Owl** | `GUIOwlClient.kt` | 阿里云 GUI Agent 专用 API |
| **MAI-UI** | `MAIUIClient.kt` | 本地部署模型 (MAI-UI-2B/8B) |

### 2.5 包结构

```
com.xiaotaozi.autopilot              # 根包 (App.kt, MainActivity.kt)
├── agent/                         # Agent 框架
│   ├── MobileAgent.kt             #   主控循环 (~1150行)
│   ├── Manager.kt                 #   规划Agent
│   ├── Executor.kt                #   执行Agent
│   ├── ActionReflector.kt         #   反思Agent
│   ├── Notetaker.kt               #   笔记Agent
│   ├── InfoPool.kt                #   状态容器
│   ├── ConversationMemory.kt      #   对话历史管理
│   └── Action.kt                  #   动作数据模型
├── controller/                    # 设备控制
│   ├── DeviceController.kt        #   屏幕操作 (tap/swipe/type/screenshot)
│   └── AppScanner.kt              #   应用扫描 (拼音/分类/语义)
├── data/                          # 数据持久化
│   ├── SettingsManager.kt         #   设置+加密API Key
│   └── ExecutionHistory.kt        #   执行记录 JSON 存储
├── service/                       # Shizuku 服务
│   └── ShellService.kt            #   AIDL UserService
├── skills/                        # 技能层
│   ├── SkillManager.kt            #   意图匹配+调度
│   ├── SkillRegistry.kt           #   从JSON加载技能
│   └── Skill.kt                   #   技能数据模型
├── tools/                         # 工具层 (6个原子工具)
│   ├── Tool.kt                    #   工具接口+注册表
│   ├── ToolManager.kt             #   工具初始化
│   ├── SearchAppsTool.kt          #   搜索应用
│   ├── OpenAppTool.kt             #   打开应用
│   ├── ClipboardTool.kt           #   读写剪贴板
│   ├── DeepLinkTool.kt            #   DeepLink跳转
│   ├── ShellTool.kt               #   Shell命令
│   └── HttpTool.kt                #   HTTP请求
├── ui/                            # UI层
│   ├── OverlayService.kt          #   悬浮窗前台服务
│   ├── screens/
│   │   ├── HomeScreen.kt          #   主页 (预设命令+输入+日志)
│   │   ├── CapabilitiesScreen.kt  #   能力展示
│   │   ├── HistoryScreen.kt       #   执行历史列表+详情
│   │   ├── SettingsScreen.kt      #   设置页 (API/主题/帮助)
│   │   └── OnboardingScreen.kt    #   4页引导
│   └── theme/
│       └── Theme.kt               #   Material 3 主题
├── utils/                         # 工具类
│   └── CrashHandler.kt            #   崩溃日志捕获导出
└── vlm/                           # VLM客户端
    ├── VLMClient.kt               #   OpenAI兼容API
    ├── GUIOwlClient.kt            #   阿里GUI-Owl
    └── MAIUIClient.kt             #   本地MAI-UI模型
```

---

## 三、功能清单

### 3.1 核心功能

1. **自然语言驱动自动化** — 用户用自然语言描述任务（如"帮我点一份麦当劳外卖"），Agent 自动规划并执行
2. **多 Agent 协作** — Manager(规划) → Executor(决策) → Reflector(评估) → Notetaker(记录)
3. **三种 AI 后端** — 云端 VLM (OpenAI兼容)、阿里 GUI-Owl、本地模型 (MAI-UI)
4. **21 个预定义技能** — 外卖、导航、打车、音乐、视频、社交、支付、扫码、闹钟、购物、阅读等

### 3.2 系统能力

- **Shizuku 集成** — 无需 Root，通过 ADB 权限执行 shell 命令实现屏幕操作
- **悬浮窗进度显示** — 彩虹渐变动画的前台服务，显示执行步骤
- **敏感页面检测** — 自动检测支付/密码页面，请求用户确认
- **人工接管模式** — Agent 遇到验证码等可暂停，用户手动操作后继续
- **Root 模式** (可选) — 支持切换至 Root 权限执行命令

### 3.3 安全机制

- API Key 使用 AES-256-GCM 加密存储 (EncryptedSharedPreferences)
- Shell 命令黑名单 (`rm -rf`, `format`, `reboot`, `dd` 等)
- `su -c` 需双重安全确认
- 支付相关操作需用户确认

### 3.4 其他特性

- 中英文双语支持 (values + values-en)
- Material 3 亮色/暗色/跟随系统主题
- 执行历史记录 (最多100条，JSON持久化)
- 中文输入法兼容 (通过剪贴板粘贴方式)
- 拼音+语义应用搜索 (60+ 中文App)
- 崩溃日志本地捕获与导出

---

## 四、详细开发手册

### 4.1 开发环境准备

**必需工具**:
- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17
- Android SDK 34 + Build Tools 34.0.0
- Kotlin 1.9.20 插件

**克隆项目**:
```bash
git clone https://github.com/Turbo1123/XiaoTaozi.git
cd XiaoTaozi
```

**同步依赖** — 在 Android Studio 中打开项目，等待 Gradle 同步完成。

### 4.2 项目构建配置

#### settings.gradle.kts
```kotlin
rootProject.name = "AutoPilot"
include(":app")
```
仓库配置: google(), mavenCentral(), jitpack.io

#### app/build.gradle.kts 关键配置
```kotlin
applicationId = "com.xiaotaozi.autopilot"
compileSdk = 34
minSdk = 26
targetSdk = 34
versionCode = 7
versionName = "1.4.2"

// 启用特性
buildFeatures { compose = true; aidl = true; buildConfig = true }
composeCompiler { kotlinCompilerExtensionVersion = "1.5.5" }
```

### 4.3 依赖详表

| 依赖 | 版本 | 用途 |
|------|------|------|
| `androidx.core:core-ktx` | 1.12.0 | AndroidX 核心扩展 |
| `androidx.core:core-splashscreen` | 1.0.1 | Android 12+ 启动画面 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.6.2 | 生命周期感知协程 |
| `androidx.activity:activity-compose` | 1.8.1 | Compose Activity 集成 |
| `androidx.security:security-crypto` | 1.1.0-alpha06 | 加密 SharedPreferences |
| `androidx.compose:compose-bom` | 2023.10.01 | Compose 版本管理 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.7.3 | 协程 |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP 客户端 |
| `dev.rikka.shizuku:api` + `:provider` | 13.1.5 | Shizuku 系统级 API |
| `org.json:json` | 20231013 | JSON 解析 |
| `com.google.firebase:firebase-bom` | 33.7.0 | Firebase (Crashlytics + Analytics) |

### 4.4 关键文件速查表

#### 入口文件

| 文件 | 路径 | 说明 |
|------|------|------|
| Application | `app/src/main/java/com/XiaoTaozi/autopilot/App.kt` | 初始化所有组件 |
| Activity | `app/src/main/java/com/XiaoTaozi/autopilot/MainActivity.kt` | 唯一 Activity，含导航+Agent调度 |
| Manifest | `app/src/main/AndroidManifest.xml` | 权限、组件声明 |

#### Agent 核心 (最重要)

| 文件 | 职责 |
|------|------|
| `agent/MobileAgent.kt` | 主控循环 (~1150行)，编排整个Agent流程 |
| `agent/Manager.kt` | 规划Agent：制定计划、检测敏感页面 |
| `agent/Executor.kt` | 执行Agent：决定具体动作和坐标 |
| `agent/ActionReflector.kt` | 反思Agent：评估动作结果 |
| `agent/Notetaker.kt` | 笔记Agent：记录关键信息 |
| `agent/InfoPool.kt` | Agent 状态容器 |
| `agent/ConversationMemory.kt` | 对话历史管理 |
| `agent/Action.kt` | 动作数据模型 + JSON 解析 |

#### 设备控制

| 文件 | 职责 |
|------|------|
| `controller/DeviceController.kt` | 通过 Shizuku 执行 tap/swipe/type/screenshot |
| `controller/AppScanner.kt` | 已安装应用扫描 (拼音搜索、分类、语义匹配) |

#### VLM 客户端

| 文件 | 职责 |
|------|------|
| `vlm/VLMClient.kt` | OpenAI 兼容 API (Qwen-VL/GPT-4V/Claude) |
| `vlm/GUIOwlClient.kt` | 阿里云 GUI-Owl 专用 |
| `vlm/MAIUIClient.kt` | MAI-UI 本地模型 |

#### 技能与工具

| 文件 | 职责 |
|------|------|
| `skills/SkillManager.kt` | 技能管理器 (意图匹配+调度) |
| `skills/SkillRegistry.kt` | 技能注册表 (从 JSON 加载) |
| `skills/Skill.kt` | 技能数据模型 |
| `tools/ToolManager.kt` | 工具管理器 (注册6个工具) |
| `tools/Tool.kt` | 工具接口定义 |
| `assets/skills.json` | 21个预定义技能配置 |

#### UI 层

| 文件 | 职责 |
|------|------|
| `ui/screens/HomeScreen.kt` | 主页 (预设命令+输入+日志) |
| `ui/screens/CapabilitiesScreen.kt` | 能力展示页 |
| `ui/screens/HistoryScreen.kt` | 执行历史列表+详情 |
| `ui/screens/SettingsScreen.kt` | 设置页 (API/主题/帮助) |
| `ui/screens/OnboardingScreen.kt` | 4页引导页 |
| `ui/OverlayService.kt` | 悬浮窗前台服务 |
| `ui/theme/Theme.kt` | Material 3 主题定义 |

#### 数据与工具

| 文件 | 职责 |
|------|------|
| `data/SettingsManager.kt` | 设置+加密API Key管理 |
| `data/ExecutionHistory.kt` | 执行记录持久化 |
| `service/ShellService.kt` | Shizuku UserService (AIDL) |
| `utils/CrashHandler.kt` | 本地崩溃日志 |

### 4.5 核心流程追踪

#### 用户发起任务的完整流程

```
1. 用户在 HomeScreen 输入指令 ("帮我点一份麦当劳外卖")
         ↓
2. HomeScreen 调用 MainActivity.runAgent(instruction)
         ↓
3. MainActivity.runAgent():
   - 检查 Shizuku 连接状态
   - 创建 ExecutionRecord (RUNNING)
   - 调用 SkillManager.matchIntent() 匹配技能
   - 启动 OverlayService (悬浮窗)
   - 根据 VLM 提供商选择执行路径:
     ├── GUI-Owl → GUIOwlClient.predict()
     ├── MAI-UI  → MAIUIClient.predict()
     └── 标准    → MobileAgent.runAgent()
         ↓
4. MobileAgent.runAgent() (标准路径):
   for step in 1..maxSteps:
     a. 截图 (DeviceController.screenshot())
     b. 检查敏感页面
     c. Manager 规划/更新计划
     d. Executor 决定动作 (含坐标)
     e. DeviceController 执行动作
     f. 截图 (执行后)
     g. Reflector 评估结果 (A/B/C)
     h. Notetaker 记录 (可选)
     i. 更新 OverlayService 状态
     j. 检查是否完成/需要接管/用户停止
         ↓
5. 完成后:
   - 关闭 OverlayService
   - 更新 ExecutionRecord (COMPLETED/FAILED/STOPPED)
   - 保存到 ExecutionHistory
   - 导航到 HistoryDetailScreen
```

#### 技能匹配流程

```
SkillManager.matchIntent(query):
  ├── 1. SkillRegistry.match(query) → 关键词匹配
  │      ├── 高置信度 (>阈值) → 直接使用
  │      └── 低置信度 → 进入步骤2
  ├── 2. LLM 意图匹配 (调用 VLMClient)
  │      └── 返回最佳匹配的 Skill
  └── 3. 执行路径判断:
         ├── DELEGATION: 有 DeepLink 且目标App已安装
         │   → 直接打开 DeepLink (快速路径)
         └── GUI_AUTOMATION: 无DeepLink或App未安装
             → 进入 Agent 截图循环
```

### 4.6 如何添加新功能

#### 添加新的 VLM 提供方

1. 在 `data/SettingsManager.kt` 的 `builtInProviders` 中添加新的 `ApiProvider`
2. 在 `vlm/` 下创建新的客户端类，参考 `vlm/VLMClient.kt` 的接口设计
3. 在 `MainActivity.kt` 的 `runAgent()` 中添加新的执行分支

#### 添加新技能

1. 编辑 `app/src/main/assets/skills.json`，添加新技能定义:
   ```json
   {
     "id": "your_skill",
     "name": "技能名称",
     "description": "技能描述",
     "category": "分类",
     "keywords": ["触发词1", "触发词2"],
     "params": [
       { "name": "参数名", "type": "string", "description": "参数描述",
         "required": true, "examples": ["示例值"] }
     ],
     "related_apps": [
       {
         "package_name": "com.example.app",
         "type": "delegation",
         "deep_link": "example://action",
         "priority": 1
       }
     ]
   }
   ```
2. 如果涉及新的 DeepLink，在 `tools/DeepLinkTool.kt` 的模板库中添加

#### 添加新工具

1. 创建新的 Tool 类实现 `Tool` 接口，参考 `tools/SearchAppsTool.kt`
2. 在 `tools/ToolManager.kt` 的 `init()` 方法中注册新工具

#### 添加新 UI 页面

1. 在 `ui/screens/` 下创建新的 Composable 文件
2. 在 `MainActivity.kt` 的 `Screen` 枚举中添加新项
3. 在 `MainApp()` composable 的 `when` 分支和 `NavigationBar` 中添加对应项

### 4.7 调试与测试

#### 在模拟器上调试

```bash
# 1. 启动模拟器 (API 26+)
# 2. 安装 Shizuku APK (https://shizuku.rikka.app/download/)
# 3. 在 Shizuku 中启动服务并授权本应用
# 4. 在应用设置中配置 VLM API Key (阿里云/OpenAI/OpenRouter)
# 5. 在主页输入测试指令，观察底部日志输出
```

#### 日志

- Agent 执行日志实时显示在 HomeScreen 底部
- adb logcat 过滤: `adb logcat | findstr "XiaoTaoziAgent"`
- 崩溃日志: 保存在 `{filesDir}/crash_logs/`，可在设置中导出分享

#### 常见问题排查

| 问题 | 排查方向 |
|------|---------|
| Shizuku 连接失败 | 检查 Shizuku App 是否运行且服务已启动；确认已授权本应用 |
| API 调用失败 | 检查 API Key 是否正确、网络是否可达、模型名称是否有效 |
| 截图返回空 | 可能是敏感页面 (支付/密码) — 这是预期行为，App会自动检测 |
| 中文输入失败 | 设备可能不支持剪贴板粘贴；检查 ADBKeyboard 是否安装 |
| 动作坐标不准 | 检查 DeviceController 获取的屏幕分辨率是否正确 |
| 悬浮窗不显示 | 检查是否授予了"显示在其他应用上层"权限 |

### 4.8 代码规范

- **语言**: Kotlin，遵循官方编码规范
- **命名**: camelCase 变量/方法，PascalCase 类/接口
- **架构**: 手动依赖注入 (Application 类集中创建)，无 Hilt/Koin
- **状态管理**: StateFlow + Compose `collectAsState()`
- **异步**: Kotlin Coroutines (`Dispatchers.IO` / `Dispatchers.Main`)
- **加密**: API Key 使用 `EncryptedSharedPreferences` (AES-256-GCM)
- **JSON**: 使用 `org.json` 库 (非 kotlinx.serialization)
- **HTTP**: OkHttp (非 Retrofit)
- **UI**: 纯 Jetpack Compose (零 XML 布局文件)

### 4.9 项目关键常量

```kotlin
// SettingsManager.kt
const val MAX_HISTORY_RECORDS = 100       // 最大历史记录数
const val MAX_CRASH_LOG_FILES = 10        // 最大崩溃日志文件数

// MobileAgent.kt
maxSteps: Int  // 默认在设置中配置 (建议 10-30)

// DeviceController.kt
SCREENSHOT_PATH = "/data/local/tmp/autopilot_screen.png"  // 截图临时路径
```

### 4.10 权限清单

| 权限 | 用途 |
|------|------|
| `INTERNET` | VLM API 调用 |
| `ACCESS_NETWORK_STATE` | 网络状态检测 |
| `READ/WRITE_EXTERNAL_STORAGE` (maxSdk=28) | 兼容旧版存储 |
| `moe.shizuku.manager.permission.API_V23` | Shizuku 系统权限 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗覆盖层 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 执行期间前台服务 |
| `POST_NOTIFICATIONS` | 前台服务通知 |
| `WAKE_LOCK` | 自动化期间保持屏幕常亮 |
| `QUERY_ALL_PACKAGES` | 查询已安装应用列表 |

---

## 五、预定义技能列表

| ID | 名称 | 类别 | 执行方式 |
|----|------|------|---------|
| `order_food` | 点外卖 | 外卖 | 美团AI代理 / GUI自动化 |
| `find_food` | 找美食 | 美食 | 美团AI / 大众点评 |
| `find_fun` | 找好玩的 | 娱乐 | 美团AI / 大众点评 |
| `book_hotel` | 订酒店 | 出行 | 美团/去哪儿/携程 |
| `book_movie` | 买电影票 | 娱乐 | 美团/淘票票 |
| `meituan_general` | 问小美 | 通用 | 美团AI代理 |
| `navigate` | 导航 | 出行 | 高德/百度/腾讯地图 |
| `call_taxi` | 打车 | 出行 | 滴滴/高德/曹操/T3 |
| `generate_image` | AI画图 | AI | 即梦/豆包 |
| `ai_chat` | AI对话 | AI | 豆包/Kimi/通义千问 |
| `play_music` | 听音乐 | 娱乐 | 网易云/QQ音乐/酷狗/Spotify |
| `watch_video` | 看视频 | 娱乐 | B站/抖音/快手/优酷/爱奇艺 |
| `send_message` | 发消息 | 社交 | 微信/QQ |
| `post_social` | 发动态 | 社交 | 微信朋友圈/微博/抖音 |
| `post_xiaohongshu` | 发小红书 | 社交 | 小红书 |
| `pay` | 付款 | 支付 | 支付宝/微信支付 |
| `scan` | 扫码 | 工具 | 支付宝/微信扫一扫 |
| `take_photo` | 拍照 | 工具 | 系统相机 |
| `set_alarm` | 设闹钟 | 工具 | 系统时钟 |
| `shopping` | 购物 | 购物 | 淘宝/京东/拼多多 |
| `read_book` | 看书 | 阅读 | 微信读书/番茄小说 |

---

## 六、项目亮点

1. **无需 Root/PC** — 基于 Shizuku 的创新方案，手机端独立运行
2. **多 Agent 架构** — Plan-Execute-Reflect 协作模式，非简单的截图→动作映射
3. **三模 AI 后端** — 云端 VLM / 专用 GUI Agent / 本地模型，灵活切换
4. **Skill + Tool 双层抽象** — 高层意图匹配 + 底层原子能力，各自独立可扩展
5. **安全优先** — 加密存储、命令黑名单、敏感页面检测、用户确认机制
6. **中国市场深度优化** — 60+ 中文 App、拼音搜索、中文输入法兼容、DeepLink 快速路径
7. **纯 Compose UI** — 零 XML 布局，Material 3 设计，亮暗主题自动切换

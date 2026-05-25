# anatomy.md

> Auto-maintained by OpenWolf. Last updated: 2026-05-25
> Files: ~85 tracked | Cloud-service refactoring completed

## ./

- `.gitignore` — Git ignore rules
- `build.gradle.kts` — Root Gradle build config
- `CLAUDE.md` — OpenWolf instructions
- `docker-compose.yml` — PostgreSQL + Server + Nginx deployment
- `.env.example` — Environment variables template (server)
- `nginx.conf` — Reverse proxy config
- `gradle.properties` — Gradle properties
- `settings.gradle.kts` — Gradle settings
- `README.md` — Project documentation (Chinese)
- `README_EN.md` — Project documentation (English)
- `LICENSE` — Project license

## .claude/

- `settings.json` — Claude Code settings

## .claude/rules/

- `openwolf.md` — OpenWolf operating protocol

## .wolf/

- `anatomy.md` — This file
- `cerebrum.md` — Learning memory
- `buglog.json` — Bug tracking log
- `memory.md` — Session memory

## app/

- `build.gradle.kts` — App build config (namespace: com.xiaotaozi.autopilot)

## app/src/main/

- `AndroidManifest.xml` — App manifest (Theme.Taozi)

## app/src/main/aidl/com/xiaotaozi/autopilot/

- `IShellService.aidl` — Shizuku UserService AIDL interface

## app/src/main/assets/

- `skills.json` — Skill definitions

## app/src/main/java/com/xiaotaozi/autopilot/

- `App.kt` — Application class (init AuthManager, DeviceController, AppScanner, tools, skills)
- `MainActivity.kt` — Single Activity: AuthGate, 4 tabs (Home/Profile/History/Settings), CloudVlmClient

## app/src/main/java/com/xiaotaozi/autopilot/agent/

- `ActionReflector.kt` — Reflects on action success
- `ConversationMemory.kt` — Conversation history management
- `Executor.kt` — Decides specific actions
- `InfoPool.kt` — Agent state during execution
- `Manager.kt` — Planning and progress management
- `MobileAgent.kt` — Main agent loop (OpenAI / GUI-Owl / MAI-UI modes)
- `Notetaker.kt` — Records important info

## app/src/main/java/com/xiaotaozi/autopilot/controller/

- `AppScanner.kt` — Scans installed apps
- `DeviceController.kt` — Shizuku UserService device control

## app/src/main/java/com/xiaotaozi/autopilot/data/

- `ApiClient.kt` — Retrofit-like HTTP client for cloud API (NEW)
- `AuthManager.kt` — JWT auth with EncryptedSharedPreferences (NEW)
- `ExecutionHistory.kt` — Execution step records
- `SettingsManager.kt` — App settings (theme, maxSteps, etc. — API Key removed)

## app/src/main/java/com/xiaotaozi/autopilot/service/

- `ShellService.kt` — Shizuku UserService implementation

## app/src/main/java/com/xiaotaozi/autopilot/skills/

- `Skill.kt` — Skill data class
- `SkillManager.kt` — Skill manager
- `SkillRegistry.kt` — Skill registry

## app/src/main/java/com/xiaotaozi/autopilot/tools/

- `ClipboardTool.kt` — Clipboard operations
- `DeepLinkTool.kt` — DeepLink navigation
- `HttpTool.kt` — HTTP requests
- `OpenAppTool.kt` — Open apps
- `SearchAppsTool.kt` — Search installed apps
- `ShellTool.kt` — Shell commands
- `Tool.kt` — Tool result data class
- `ToolManager.kt` — Tool manager

## app/src/main/java/com/xiaotaozi/autopilot/ui/

- `OverlayService.kt` — Foreground overlay service

## app/src/main/java/com/xiaotaozi/autopilot/ui/components/

- `AuthGate.kt` — Auth routing composable (NEW: login/activate/main)

## app/src/main/java/com/xiaotaozi/autopilot/ui/screens/

- `ActivationScreen.kt` — Card key activation page (NEW)
- `CapabilitiesScreen.kt` — Tools/capabilities display
- `HistoryScreen.kt` — Execution history + detail
- `HomeScreen.kt` — Home: preset commands + instruction input
- `LoginScreen.kt` — Login page (NEW)
- `OnboardingScreen.kt` — First-launch onboarding
- `ProfileScreen.kt` — User profile (NEW: replaces Capabilities tab)
- `RegisterScreen.kt` — Registration page (NEW)
- `SettingsScreen.kt` — Settings (theme, maxSteps, Shizuku, etc.)

## app/src/main/java/com/xiaotaozi/autopilot/ui/theme/

- `Theme.kt` — TaoziTheme, TaoziColors (DarkTaoziColors, LightTaoziColors)

## app/src/main/java/com/xiaotaozi/autopilot/utils/

- `CrashHandler.kt` — Global crash capture

## app/src/main/java/com/xiaotaozi/autopilot/vlm/

- `CloudVlmClient.kt` — Cloud VLM proxy client (NEW: JWT auth, server proxy)
- `GUIOwlClient.kt` — GUI-Owl client (supports cloud proxy mode)
- `MAIUIClient.kt` — Local MAI-UI client (no cloud needed)
- `VLMClient.kt` — VLM client (supports cloud proxy mode)

## app/src/main/res/

- `values/strings.xml` — Chinese strings (小桃子 brand)
- `values-en/strings.xml` — English strings (XiaoTaozi brand)
- `values/colors.xml` — Color definitions
- `values/themes.xml` — Theme.Taozi styling

## app/src/main/res/xml/

- `file_paths.xml` — FileProvider paths
- `network_security_config.xml` — Network security

## server/

- `package.json` — Fastify 5 + Prisma 6 + TypeScript
- `tsconfig.json` — TypeScript config
- `Dockerfile` — Node 22 Alpine
- `prisma/schema.prisma` — 9 PostgreSQL tables (AdminUser, AppUser, CardKey, etc.)
- `prisma/seed.ts` — Super admin seed
- `src/index.ts` — Fastify entry (10 route modules, CORS, rate-limit)
- `src/config.ts` — Environment config
- `src/routes/admin/` — Admin API routes (auth, providers, cardKeys, users, stats)
- `src/routes/auth.ts` — App user auth routes
- `src/routes/activation.ts` — Card key activation route
- `src/routes/vlm.ts` — VLM proxy route (chat + gui-owl)
- `src/routes/providers.ts` — Provider listing
- `src/middleware/auth.ts` — JWT middleware
- `src/services/` — Business logic
- `src/utils/crypto.ts` — AES-256-GCM encryption
- `src/utils/cardkey.ts` — Card key generation (XTZ- prefix)

## admin-ui/

- `package.json` — React 18 + Vite + Tailwind
- `vite.config.ts` — Vite config
- `tailwind.config.ts` — Taozi color palette
- `src/App.tsx` — React Router with AuthGuard
- `src/lib/api.ts` — API client with JWT refresh
- `src/components/` — Layout (Sidebar, AdminLayout)
- `src/pages/` — Login, Dashboard, Providers, CardKeys, Users, Admins

## docs/

- `DEVELOPMENT_MANUAL.md` — Development guide (updated brand)
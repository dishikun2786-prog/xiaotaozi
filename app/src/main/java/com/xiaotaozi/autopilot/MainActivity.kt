package com.xiaotaozi.autopilot

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import android.net.Uri
import android.provider.Settings
import com.xiaotaozi.autopilot.agent.MobileAgent
import com.xiaotaozi.autopilot.controller.AppScanner
import com.xiaotaozi.autopilot.controller.DeviceController
import com.xiaotaozi.autopilot.data.*
import com.xiaotaozi.autopilot.ui.components.AuthGate
import com.xiaotaozi.autopilot.ui.screens.*
import com.xiaotaozi.autopilot.ui.theme.*
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.xiaotaozi.autopilot.vlm.GUIOwlClient
import com.xiaotaozi.autopilot.vlm.MAIUIClient
import com.xiaotaozi.autopilot.vlm.VLMClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import android.util.Log

private const val TAG = "MainActivity"

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    object Home : Screen("home", "小桃子", Icons.Outlined.Home, Icons.Filled.Home)
    object Profile : Screen("profile", "个人", Icons.Outlined.Person, Icons.Filled.Person)
    object History : Screen("history", "记录", Icons.Outlined.List, Icons.Filled.List)
    object Settings : Screen("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var deviceController: DeviceController
    private lateinit var settingsManager: SettingsManager
    private lateinit var executionRepository: ExecutionRepository
    private lateinit var authManager: AuthManager
    private lateinit var apiClient: ApiClient

    private val mobileAgent = mutableStateOf<MobileAgent?>(null)
    private var shizukuAvailable = mutableStateOf(false)
    private var currentExecutionJob: kotlinx.coroutines.Job? = null
    private val executionRecords = mutableStateOf<List<ExecutionRecord>>(emptyList())
    private val isExecuting = mutableStateOf(false)
    private val currentRecordId = mutableStateOf<String?>(null)
    private val shouldNavigateToRecord = mutableStateOf(false)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        shizukuAvailable.value = true
        if (checkShizukuPermission()) {
            Log.d(TAG, "Shizuku permission granted, binding service")
            deviceController.bindService()
        } else {
            Log.d(TAG, "Shizuku permission not granted")
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        shizukuAvailable.value = false
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        Log.d(TAG, "Shizuku permission result: $grantResult")
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            deviceController.bindService()
            Toast.makeText(this, "Shizuku 权限已获取", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        deviceController = DeviceController(this)
        deviceController.setCacheDir(cacheDir)
        settingsManager = SettingsManager(this)
        executionRepository = ExecutionRepository(this)
        authManager = App.getInstance().authManager
        apiClient = ApiClient(authManager)

        lifecycleScope.launch {
            executionRecords.value = executionRepository.getAllRecords()
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        checkAndUpdateShizukuStatus()

        lifecycleScope.launch(Dispatchers.IO) {
            AppScanner(this@MainActivity).getApps()
        }

        setContent {
            val settings by settingsManager.settings.collectAsState()
            TaoziTheme(themeMode = settings.themeMode) {
                val colors = TaoziTheme.colors
                SideEffect {
                    val window = this@MainActivity.window
                    window.statusBarColor = colors.background.toArgb()
                    window.navigationBarColor = colors.backgroundCard.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !colors.isDark
                        isAppearanceLightNavigationBars = !colors.isDark
                    }
                }

                if (!settings.hasSeenOnboarding) {
                    OnboardingScreen(
                        onComplete = {
                            settingsManager.setOnboardingSeen()
                        }
                    )
                } else {
                    AuthGate(
                        authManager = authManager,
                        apiClient = apiClient,
                        onAuthenticated = { MainApp() }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainApp() {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        var selectedRecord by remember { mutableStateOf<ExecutionRecord?>(null) }
        var showShizukuHelpDialog by remember { mutableStateOf(false) }
        var hasShownShizukuHelp by remember { mutableStateOf(false) }

        val settings by settingsManager.settings.collectAsState()
        val colors = TaoziTheme.colors
        val agent = mobileAgent.value
        val agentState by agent?.state?.collectAsState() ?: remember { mutableStateOf(null) }
        val logs by agent?.logs?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
        val records by remember { executionRecords }
        val isShizukuAvailable = shizukuAvailable.value && checkShizukuPermission()
        val executing by remember { isExecuting }
        val navigateToRecord by remember { shouldNavigateToRecord }
        val recordId by remember { currentRecordId }

        LaunchedEffect(navigateToRecord, recordId) {
            if (navigateToRecord && recordId != null) {
                val record = records.find { it.id == recordId }
                if (record != null) {
                    selectedRecord = record
                    currentScreen = Screen.History
                }
                shouldNavigateToRecord.value = false
            }
        }

        LaunchedEffect(Unit) {
            if (!isShizukuAvailable && settings.hasSeenOnboarding && !hasShownShizukuHelp) {
                hasShownShizukuHelp = true
                showShizukuHelpDialog = true
            }
        }

        Scaffold(
            modifier = Modifier.background(colors.background),
            containerColor = colors.background,
            bottomBar = {
                if (selectedRecord == null) {
                    NavigationBar(
                        containerColor = colors.background,
                        contentColor = colors.textPrimary,
                        tonalElevation = 0.dp
                    ) {
                        listOf(Screen.Home, Screen.Profile, Screen.History, Screen.Settings).forEach { screen ->
                            val selected = currentScreen == screen
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.icon,
                                        contentDescription = screen.title
                                    )
                                },
                                label = { Text(screen.title) },
                                selected = selected,
                                onClick = { currentScreen = screen },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (colors.isDark) colors.textPrimary else Color.White,
                                    selectedTextColor = colors.primary,
                                    unselectedIconColor = colors.textSecondary,
                                    unselectedTextColor = colors.textSecondary,
                                    indicatorColor = colors.primary
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                BackHandler(enabled = selectedRecord != null) {
                    selectedRecord = null
                }

                if (selectedRecord != null) {
                    HistoryDetailScreen(
                        record = selectedRecord!!,
                        onBack = { selectedRecord = null }
                    )
                } else {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen"
                    ) { screen ->
                        when (screen) {
                            Screen.Home -> {
                                LaunchedEffect(Unit) { checkAndUpdateShizukuStatus() }
                                HomeScreen(
                                    agentState = agentState,
                                    logs = logs,
                                    onExecute = { instruction -> runAgent(instruction, settings.maxSteps) },
                                    onStop = { mobileAgent.value?.stop() },
                                    shizukuAvailable = isShizukuAvailable,
                                    currentModel = "云端代理",
                                    onRefreshShizuku = { refreshShizukuStatus() },
                                    onShizukuRequired = { showShizukuHelpDialog = true },
                                    isExecuting = executing
                                )
                            }
                            Screen.Profile -> ProfileScreen(authManager = authManager)
                            Screen.History -> HistoryScreen(
                                records = records,
                                onRecordClick = { record -> selectedRecord = record },
                                onDeleteRecord = { id -> deleteRecord(id) }
                            )
                            Screen.Settings -> SettingsScreen(
                                settings = settings,
                                onUpdateThemeMode = { settingsManager.updateThemeMode(it) },
                                onUpdateMaxSteps = { settingsManager.updateMaxSteps(it) },
                                onUpdateCloudCrashReport = { enabled ->
                                    settingsManager.updateCloudCrashReportEnabled(enabled)
                                    App.getInstance().updateCloudCrashReportEnabled(enabled)
                                },
                                onUpdateRootModeEnabled = { settingsManager.updateRootModeEnabled(it) },
                                onUpdateSuCommandEnabled = { settingsManager.updateSuCommandEnabled(it) },
                                shizukuAvailable = isShizukuAvailable,
                                shizukuPrivilegeLevel = if (isShizukuAvailable) {
                                    when (deviceController.getShizukuPrivilegeLevel()) {
                                        DeviceController.ShizukuPrivilegeLevel.ROOT -> "ROOT"
                                        DeviceController.ShizukuPrivilegeLevel.ADB -> "ADB"
                                        else -> "NONE"
                                    }
                                } else "NONE",
                                authManager = authManager
                            )
                        }
                    }
                }
            }
        }

        if (showShizukuHelpDialog) {
            ShizukuHelpDialog(onDismiss = { showShizukuHelpDialog = false })
        }
    }

    private fun deleteRecord(id: String) {
        lifecycleScope.launch {
            executionRepository.deleteRecord(id)
            executionRecords.value = executionRepository.getAllRecords()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        deviceController.unbindService()
    }

    private fun checkShizukuPermission(): Boolean {
        return try {
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "checkShizukuPermission: $granted")
            granted
        } catch (e: Exception) {
            Log.e(TAG, "checkShizukuPermission error", e)
            false
        }
    }

    private fun checkAndUpdateShizukuStatus() {
        Log.d(TAG, "checkAndUpdateShizukuStatus called")
        try {
            val binderAlive = Shizuku.pingBinder()
            Log.d(TAG, "Shizuku pingBinder: $binderAlive")
            if (binderAlive) {
                shizukuAvailable.value = true
                val hasPermission = checkShizukuPermission()
                Log.d(TAG, "Shizuku hasPermission: $hasPermission")
                if (hasPermission) {
                    Log.d(TAG, "Binding Shizuku service")
                    deviceController.bindService()
                } else {
                    Log.d(TAG, "Requesting Shizuku permission")
                    requestShizukuPermission()
                }
            } else {
                Log.d(TAG, "Shizuku binder not alive")
                shizukuAvailable.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkAndUpdateShizukuStatus error", e)
            shizukuAvailable.value = false
        }
    }

    private fun refreshShizukuStatus() {
        Log.d(TAG, "refreshShizukuStatus called by user")
        Toast.makeText(this, "正在检查 Shizuku 状态...", Toast.LENGTH_SHORT).show()
        checkAndUpdateShizukuStatus()
        if (shizukuAvailable.value && checkShizukuPermission()) {
            Toast.makeText(this, "Shizuku 已连接", Toast.LENGTH_SHORT).show()
        } else if (shizukuAvailable.value) {
            Toast.makeText(this, "请在弹窗中授权 Shizuku", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "请先启动 Shizuku App", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "请先启动 Shizuku App", Toast.LENGTH_SHORT).show()
                return
            }
            if (Shizuku.isPreV11()) {
                Toast.makeText(this, "Shizuku 版本过低", Toast.LENGTH_SHORT).show()
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku 权限已获取", Toast.LENGTH_SHORT).show()
                shizukuAvailable.value = true
                deviceController.bindService()
                return
            }
            Shizuku.requestPermission(0)
        } catch (e: Exception) {
            Toast.makeText(this, "请先启动 Shizuku App", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runAgent(
        instruction: String,
        maxSteps: Int
    ) {
        if (instruction.isBlank()) {
            Toast.makeText(this, "请输入指令", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
            val intent = android.content.Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        isExecuting.value = true

        // 使用云端代理模式创建客户端（MAI-UI 除外）
        // 从云端服务端获取可用提供商列表，选择优先级最高的
        val serverUrl = authManager.getBaseUrl()

        // 默认使用云端代理模式
        val vlmClient = VLMClient.fromCloud(
            authManager = authManager,
            providerId = "aliyun",
            model = "qwen3-vl-plus"
        )
        mobileAgent.value = MobileAgent(vlmClient, deviceController, this)

        // 设置停止回调
        mobileAgent.value?.onStopRequested = {
            currentExecutionJob?.cancel()
            currentExecutionJob = null
        }

        val record = ExecutionRecord(
            title = generateTitle(instruction),
            instruction = instruction,
            startTime = System.currentTimeMillis(),
            status = ExecutionStatus.RUNNING
        )

        currentRecordId.value = record.id
        currentExecutionJob?.cancel()

        currentExecutionJob = lifecycleScope.launch {
            executionRepository.saveRecord(record)
            executionRecords.value = executionRepository.getAllRecords()

            try {
                val result = mobileAgent.value!!.runInstruction(instruction, maxSteps)

                val agentState = mobileAgent.value?.state?.value
                val steps = agentState?.executionSteps ?: emptyList()
                val currentLogs = mobileAgent.value?.logs?.value ?: emptyList()

                val updatedRecord = record.copy(
                    endTime = System.currentTimeMillis(),
                    status = if (result.success) ExecutionStatus.COMPLETED else ExecutionStatus.FAILED,
                    steps = steps,
                    logs = currentLogs,
                    resultMessage = result.message
                )
                executionRepository.saveRecord(updatedRecord)
                executionRecords.value = executionRepository.getAllRecords()

                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()

                isExecuting.value = false
                kotlinx.coroutines.delay(3000)
                mobileAgent.value?.clearLogs()
            } catch (e: kotlinx.coroutines.CancellationException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    val agentState = mobileAgent.value?.state?.value
                    val steps = agentState?.executionSteps ?: emptyList()
                    val currentLogs = mobileAgent.value?.logs?.value ?: emptyList()

                    println("[MainActivity] 取消任务 - steps: ${steps.size}, logs: ${currentLogs.size}")

                    val updatedRecord = record.copy(
                        endTime = System.currentTimeMillis(),
                        status = ExecutionStatus.STOPPED,
                        steps = steps,
                        logs = currentLogs,
                        resultMessage = "已取消"
                    )
                    executionRepository.saveRecord(updatedRecord)
                    executionRecords.value = executionRepository.getAllRecords()

                    isExecuting.value = false
                    Toast.makeText(this@MainActivity, "任务已停止", Toast.LENGTH_SHORT).show()
                    mobileAgent.value?.clearLogs()
                    shouldNavigateToRecord.value = true
                }
            } catch (e: Exception) {
                val currentLogs = mobileAgent.value?.logs?.value ?: emptyList()
                val updatedRecord = record.copy(
                    endTime = System.currentTimeMillis(),
                    status = ExecutionStatus.FAILED,
                    logs = currentLogs,
                    resultMessage = "错误: ${e.message}"
                )
                executionRepository.saveRecord(updatedRecord)
                executionRecords.value = executionRepository.getAllRecords()

                isExecuting.value = false
                Toast.makeText(this@MainActivity, "错误: ${e.message}", Toast.LENGTH_LONG).show()

                kotlinx.coroutines.delay(3000)
                mobileAgent.value?.clearLogs()
            }
        }
    }

    private fun generateTitle(instruction: String): String {
        val keywords = listOf(
            "打开" to "打开应用",
            "点" to "点餐",
            "发" to "发送消息",
            "看" to "浏览内容",
            "搜" to "搜索",
            "设置" to "调整设置",
            "播放" to "播放媒体"
        )
        for ((key, title) in keywords) {
            if (instruction.contains(key)) return title
        }
        return if (instruction.length > 10) instruction.take(10) + "..." else instruction
    }
}
package com.xiaotaozi.autopilot

import android.app.Application
import android.content.pm.PackageManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.xiaotaozi.autopilot.controller.AppScanner
import com.xiaotaozi.autopilot.controller.DeviceController
import com.xiaotaozi.autopilot.data.AuthManager
import com.xiaotaozi.autopilot.data.SettingsManager
import com.xiaotaozi.autopilot.skills.SkillManager
import com.xiaotaozi.autopilot.tools.ToolManager
import com.xiaotaozi.autopilot.utils.CrashHandler
import rikka.shizuku.Shizuku

class App : Application() {

    lateinit var deviceController: DeviceController
        private set
    lateinit var appScanner: AppScanner
        private set
    lateinit var authManager: AuthManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化崩溃捕获（本地日志）
        CrashHandler.getInstance().init(this)

        // 初始化认证管理器
        authManager = AuthManager(this)

        // 初始化设置管理器
        val settingsManager = SettingsManager(this)

        // 初始化 Firebase Crashlytics（根据用户设置决定是否启用）
        val cloudCrashReportEnabled = settingsManager.settings.value.cloudCrashReportEnabled
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(cloudCrashReportEnabled)
            setCustomKey("app_version", BuildConfig.VERSION_NAME)
            setCustomKey("device_model", android.os.Build.MODEL)
            setCustomKey("android_version", android.os.Build.VERSION.SDK_INT.toString())
            sendUnsentReports()
        }
        println("[小桃子] 云端崩溃上报: ${if (cloudCrashReportEnabled) "已开启" else "已关闭"}")

        // 初始化 Shizuku
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)

        // 初始化核心组件
        initializeComponents()
    }

    private fun initializeComponents() {
        // 初始化设备控制器
        deviceController = DeviceController(this)
        deviceController.setCacheDir(cacheDir)

        // 初始化应用扫描器
        appScanner = AppScanner(this)

        // 初始化 Tools 层
        val toolManager = ToolManager.init(this, deviceController, appScanner)

        // 异步预扫描应用列表（避免 ANR）
        println("[小桃子] 开始异步扫描已安装应用...")
        Thread {
            appScanner.refreshApps()
            println("[小桃子] 已扫描 ${appScanner.getApps().size} 个应用")
        }.start()

        // 初始化 Skills 层（传入 appScanner 用于检测已安装应用）
        val skillManager = SkillManager.init(this, toolManager, appScanner)
        println("[小桃子] SkillManager 已加载 ${skillManager.getAllSkills().size} 个 Skills")

        println("[小桃子] 组件初始化完成")
    }

    override fun onTerminate() {
        super.onTerminate()
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
    }

    fun updateCloudCrashReportEnabled(enabled: Boolean) {
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        println("[小桃子] 云端崩溃上报已${if (enabled) "开启" else "关闭"}")
    }

    companion object {
        @Volatile
        private var instance: App? = null

        fun getInstance(): App {
            return instance ?: throw IllegalStateException("App 未初始化")
        }

        private val REQUEST_PERMISSION_RESULT_LISTENER =
            Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                println("[Shizuku] Permission result: $granted")
            }
    }
}
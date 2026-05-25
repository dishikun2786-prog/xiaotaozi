package com.xiaotaozi.autopilot.data

import android.content.Context
import android.content.SharedPreferences
import com.xiaotaozi.autopilot.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用设置（云端版 - 不再存储 API Key）
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val hasSeenOnboarding: Boolean = false,
    val maxSteps: Int = 25,
    val cloudCrashReportEnabled: Boolean = true,
    val rootModeEnabled: Boolean = false,
    val suCommandEnabled: Boolean = false
) {
    // === 以下属性已废弃（云端版不再使用），保留以兼容旧 UI 代码 ===
    @Deprecated("云端版不再使用 API Key")
    val currentProviderId: String get() = ""
    @Deprecated("云端版不再使用 API Key")
    val apiKey: String get() = ""
    @Deprecated("云端版不再使用 API Key")
    val model: String get() = ""
    @Deprecated("云端版不再使用 API Key")
    val baseUrl: String get() = ""
    @Deprecated("云端版不再使用 API Key")
    val cachedModels: List<String> get() = emptyList()

    @Deprecated("云端版不再使用 Provider 配置")
    data class ProviderConfig(
        val apiKey: String = "",
        val model: String = "",
        val cachedModels: List<String> = emptyList(),
        val customBaseUrl: String = ""
    )

    @Deprecated("云端版不再使用 Provider 配置")
    val providerConfigs: Map<String, ProviderConfig> get() = emptyMap()
    @Deprecated("云端版不再使用 Provider 配置")
    val currentConfig: ProviderConfig get() = ProviderConfig()

    @Deprecated("云端版不再使用 ApiProvider")
    data class ApiProvider(
        val id: String,
        val name: String,
        val baseUrl: String,
        val defaultModel: String,
        val isGUIAgent: Boolean = false
    ) {
        companion object {
            val ALL: List<ApiProvider> get() = emptyList()
            val ALIYUN: ApiProvider get() = ApiProvider("aliyun", "", "", "")
        }
    }

    @Deprecated("云端版不再使用")
    val currentProvider: ApiProvider get() = ApiProvider.ALIYUN
}

/**
 * 设置管理器（云端版）
 * API Key 由云端管理，App 不再存储
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("taozi_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings

    private fun loadSettings(): AppSettings {
        val themeModeStr = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }

        return AppSettings(
            themeMode = themeMode,
            hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false),
            maxSteps = prefs.getInt("max_steps", 25),
            cloudCrashReportEnabled = prefs.getBoolean("cloud_crash_report_enabled", true),
            rootModeEnabled = prefs.getBoolean("root_mode_enabled", false),
            suCommandEnabled = prefs.getBoolean("su_command_enabled", false)
        )
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        prefs.edit().putString("theme_mode", themeMode.name).apply()
        _settings.value = _settings.value.copy(themeMode = themeMode)
    }

    fun setOnboardingSeen() {
        prefs.edit().putBoolean("has_seen_onboarding", true).apply()
        _settings.value = _settings.value.copy(hasSeenOnboarding = true)
    }

    fun updateMaxSteps(maxSteps: Int) {
        val validSteps = maxSteps.coerceIn(5, 100)
        prefs.edit().putInt("max_steps", validSteps).apply()
        _settings.value = _settings.value.copy(maxSteps = validSteps)
    }

    fun updateCloudCrashReportEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("cloud_crash_report_enabled", enabled).apply()
        _settings.value = _settings.value.copy(cloudCrashReportEnabled = enabled)
    }

    fun updateRootModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("root_mode_enabled", enabled).apply()
        _settings.value = _settings.value.copy(rootModeEnabled = enabled)
        if (!enabled) {
            updateSuCommandEnabled(false)
        }
    }

    fun updateSuCommandEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("su_command_enabled", enabled).apply()
        _settings.value = _settings.value.copy(suCommandEnabled = enabled)
    }
}
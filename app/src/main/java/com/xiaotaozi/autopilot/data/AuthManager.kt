package com.xiaotaozi.autopilot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UserInfo(
    val id: String,
    val username: String,
    val email: String? = null,
    val isActivated: Boolean = false,
    val createdAt: String? = null
)

data class AuthState(
    val isLoggedIn: Boolean = false,
    val isActivated: Boolean = false,
    val user: UserInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthManager(context: Context) {

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "taozi_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state

    val accessToken: String?
        get() = securePrefs.getString("access_token", null)

    val refreshToken: String?
        get() = securePrefs.getString("refresh_token", null)

    private fun saveTokens(accessToken: String, refreshToken: String) {
        securePrefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    fun setUser(user: UserInfo) {
        securePrefs.edit()
            .putString("user_id", user.id)
            .putString("username", user.username)
            .putBoolean("is_activated", user.isActivated)
            .apply()
        _state.value = _state.value.copy(
            isLoggedIn = true,
            isActivated = user.isActivated,
            user = user,
            error = null
        )
    }

    fun restoreSession(user: UserInfo) {
        _state.value = AuthState(
            isLoggedIn = true,
            isActivated = user.isActivated,
            user = user
        )
    }

    fun setActivated() {
        securePrefs.edit().putBoolean("is_activated", true).apply()
        _state.value = _state.value.copy(isActivated = true, user = _state.value.user?.copy(isActivated = true))
    }

    fun setLoading(loading: Boolean) {
        _state.value = _state.value.copy(isLoading = loading)
    }

    fun setError(error: String?) {
        _state.value = _state.value.copy(error = error, isLoading = false)
    }

    fun logout() {
        securePrefs.edit().clear().apply()
        _state.value = AuthState()
    }

    fun getBaseUrl(): String {
        return securePrefs.getString("server_base_url", "http://10.0.2.2:3000") ?: "http://10.0.2.2:3000"
    }

    fun setBaseUrl(url: String) {
        securePrefs.edit().putString("server_base_url", url).apply()
    }
}

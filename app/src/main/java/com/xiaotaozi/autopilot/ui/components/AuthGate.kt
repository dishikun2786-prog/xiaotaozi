package com.xiaotaozi.autopilot.ui.components

import androidx.compose.runtime.*
import com.xiaotaozi.autopilot.data.AuthManager
import com.xiaotaozi.autopilot.data.ApiClient
import com.xiaotaozi.autopilot.ui.screens.ActivationScreen
import com.xiaotaozi.autopilot.ui.screens.LoginScreen

@Composable
fun AuthGate(
    authManager: AuthManager,
    apiClient: ApiClient,
    onAuthenticated: @Composable () -> Unit
) {
    val authState by authManager.state.collectAsState()

    if (authState.isLoggedIn && authState.isActivated) {
        onAuthenticated()
    } else if (authState.isLoggedIn && !authState.isActivated) {
        ActivationScreen(
            authManager = authManager,
            apiClient = apiClient
        )
    } else {
        LoginScreen(
            authManager = authManager,
            apiClient = apiClient
        )
    }
}
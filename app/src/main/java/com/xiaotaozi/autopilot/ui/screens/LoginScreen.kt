package com.xiaotaozi.autopilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaotaozi.autopilot.data.AuthManager
import com.xiaotaozi.autopilot.data.ApiClient
import com.xiaotaozi.autopilot.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authManager: AuthManager,
    apiClient: ApiClient
) {
    val colors = TaoziTheme.colors
    val scope = rememberCoroutineScope()
    val authState by authManager.state.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showRegister by remember { mutableStateOf(false) }

    if (showRegister) {
        RegisterScreen(
            authManager = authManager,
            apiClient = apiClient,
            onBack = { showRegister = false }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🍑", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "小桃子",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                "智能自动化助手",
                fontSize = 14.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(40.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.surfaceVariant,
                    cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.surfaceVariant,
                    cursorColor = colors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            if (authState.error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(authState.error!!, color = colors.error, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        authManager.setLoading(true)
                        val result = apiClient.login(username.trim(), password)
                        if (!result.success) {
                            authManager.setError(result.error)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !authState.isLoading && username.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                if (authState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.textPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("登录", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { showRegister = true }) {
                Text(
                    "没有账号？注册新用户",
                    color = colors.textSecondary
                )
            }
        }
    }
}

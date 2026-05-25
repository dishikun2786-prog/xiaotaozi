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
fun RegisterScreen(
    authManager: AuthManager,
    apiClient: ApiClient,
    onBack: () -> Unit
) {
    val colors = TaoziTheme.colors
    val scope = rememberCoroutineScope()
    val authState by authManager.state.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

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
            Text("注册新用户", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Text("创建小桃子账号", fontSize = 14.sp, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; localError = null },
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
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; localError = null },
                label = { Text("邮箱 (选填)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; localError = null },
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
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; localError = null },
                label = { Text("确认密码") },
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

            val errorMsg = localError ?: authState.error
            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorMsg, color = colors.error, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (password != confirmPassword) {
                        localError = "两次密码不一致"
                        return@Button
                    }
                    if (password.length < 6) {
                        localError = "密码长度至少6位"
                        return@Button
                    }
                    scope.launch {
                        authManager.setLoading(true)
                        val result = apiClient.register(username.trim(), password, email.trim().ifBlank { null })
                        if (!result.success) authManager.setError(result.error)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !authState.isLoading && username.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                if (authState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.textPrimary, strokeWidth = 2.dp)
                } else {
                    Text("注册", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBack) {
                Text("已有账号？返回登录", color = colors.textSecondary)
            }
        }
    }
}

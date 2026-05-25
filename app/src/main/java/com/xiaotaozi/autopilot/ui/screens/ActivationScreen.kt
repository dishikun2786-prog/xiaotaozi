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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaotaozi.autopilot.data.AuthManager
import com.xiaotaozi.autopilot.data.ApiClient
import com.xiaotaozi.autopilot.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ActivationScreen(
    authManager: AuthManager,
    apiClient: ApiClient
) {
    val colors = TaoziTheme.colors
    val scope = rememberCoroutineScope()
    val authState by authManager.state.collectAsState()

    var cardCode by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

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
            Text("🔑", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "激活账号",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                "请输入您的卡密以激活账号",
                fontSize = 14.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = cardCode,
                onValueChange = { cardCode = it.uppercase(); localError = null },
                label = { Text("卡密 (XTZ-XXXX-XXXX-XXXX)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
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

            if (authState.error != null || localError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(authState.error ?: localError!!, color = colors.error, fontSize = 13.sp)
            }
            if (successMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(successMessage!!, color = colors.success, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val code = cardCode.trim()
                    if (code.isBlank()) { localError = "请输入卡密"; return@Button }
                    if (!code.startsWith("XTZ-")) { localError = "卡密格式无效，应以 XTZ- 开头"; return@Button }
                    isLoading = true
                    scope.launch {
                        val result = apiClient.activate(code)
                        isLoading = false
                        if (!result.success) {
                            localError = result.error
                        } else {
                            successMessage = "激活成功！"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading && cardCode.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.textPrimary, strokeWidth = 2.dp)
                } else {
                    Text("激活", fontSize = 16.sp)
                }
            }
        }
    }
}

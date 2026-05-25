package com.xiaotaozi.autopilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaotaozi.autopilot.data.AuthManager
import com.xiaotaozi.autopilot.ui.theme.*

@Composable
fun ProfileScreen(
    authManager: AuthManager
) {
    val colors = TaoziTheme.colors
    val authState by authManager.state.collectAsState()
    val user = authState.user

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp)
    ) {
        Text("个人中心", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(modifier = Modifier.height(24.dp))

        // User info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundCard)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        user?.username?.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(user?.username ?: "未知", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(
                    if (authState.isActivated) "已激活" else "未激活",
                    fontSize = 13.sp,
                    color = if (authState.isActivated) colors.success else colors.warning
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info rows
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileInfoRow("用户名", user?.username ?: "-", colors)
                HorizontalDivider(color = colors.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                ProfileInfoRow("邮箱", user?.email ?: "未设置", colors)
                HorizontalDivider(color = colors.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                ProfileInfoRow("账号状态", if (authState.isActivated) "已激活" else "未激活", colors)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Logout button
        Button(
            onClick = { authManager.logout() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.error)
        ) {
            Text("退出登录", fontSize = 16.sp)
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String, colors: TaoziColors) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = colors.textSecondary)
        Text(value, fontSize = 14.sp, color = colors.textPrimary)
    }
}

package com.easylistening.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.easylistening.app.data.Repo

@Composable
fun SettingsScreen(initResult: String) {
    val context = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    var permExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        SectionHeader("权限", permExpanded) { permExpanded = !permExpanded }
        if (permExpanded) {
            SettingRowClickable(
                title = "通知栏权限",
                subtitle = if (notifGranted) "已授权（播放通知/锁屏控制）" else "未授权，点此开启",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    }
                }
            )
            SettingRowClickable(
                title = "后台自启动",
                subtitle = "点此打开厂商的「自启动 / 后台启动」设置（各机型不同）",
                onClick = { openAutoStartSettings(context) }
            )
            SettingRowClickable(
                title = "定时（精确闹钟）权限",
                subtitle = exactAlarmStatus(context),
                onClick = { requestExactAlarmIfNeeded(context) }
            )
            SettingRow(
                "剪贴板取词",
                "无需权限；在主页搜索框点「粘贴」即可"
            )
        }

        SectionHeader("关于软件", aboutExpanded) { aboutExpanded = !aboutExpanded }
        if (aboutExpanded) {
            SettingRow("内核状态", initResult)
            SettingRow("检查更新", "当前版本 0.3")
            SettingRow("问题反馈", "待实现")
        }

        SettingRow("软件配色", "跟随系统（待实现）")
        SettingRow("离线词典", "开源词库 ECDICT · ${Repo.dictCount()} 词")
    }
}

private fun exactAlarmStatus(context: Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return "无需授权（Android 11 及以下自动允许）"
    }
    val am = context.getSystemService(AlarmManager::class.java)
    return if (am != null && am.canScheduleExactAlarms()) "已授权" else "未授权，点此开启"
}

private fun requestExactAlarmIfNeeded(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (!am.canScheduleExactAlarms()) {
            runCatching {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (expanded) "▾" else "▸",
            style = MaterialTheme.typography.titleMedium
        )
    }
    HorizontalDivider()
}

@Composable
private fun SettingRow(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

@Composable
private fun SettingRowClickable(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

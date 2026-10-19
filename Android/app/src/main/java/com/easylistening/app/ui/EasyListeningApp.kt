package com.easylistening.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easylistening.app.PlaybackService
import com.easylistening.app.data.Book
import com.easylistening.app.data.Settings

enum class Tab(val label: String) {
    Home("主页"),
    Listen("听力"),
    Settings("设置")
}

@Composable
fun EasyListeningApp(initResult: String) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.Home) }
    var openBook by remember { mutableStateOf<Book?>(null) }

    val homeState = remember {
        HomeUiState(targetLang = Settings.loadTargetLang(context))
    }
    homeState.onTargetLangChange = { Settings.saveTargetLang(context, it) }

    val playback = PlaybackHolder.controller

    // Android 13+ 通知权限
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // 播放开始 -> 启动前台服务；结束 -> 停止服务
    LaunchedEffect(playback.active) {
        if (playback.active) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }

    // 返回键：优先关闭详情 / 退回主页；播放中返回主页不打断播放
    BackHandler(enabled = openBook != null || tab != Tab.Home) {
        when {
            openBook != null -> openBook = null
            tab != Tab.Home -> tab = Tab.Home
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                if (playback.active && tab != Tab.Listen && openBook == null) {
                    MiniPlayerBar(playback = playback, onOpen = { tab = Tab.Listen })
                }
                if (openBook == null) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == Tab.Home,
                            onClick = { tab = Tab.Home },
                            icon = { Icon(Icons.Filled.Home, contentDescription = "主页") },
                            label = { Text("主页") }
                        )
                        NavigationBarItem(
                            selected = tab == Tab.Listen,
                            onClick = { tab = Tab.Listen },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "听力",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            },
                            label = { Text("听力") }
                        )
                        NavigationBarItem(
                            selected = tab == Tab.Settings,
                            onClick = { tab = Tab.Settings },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                            label = { Text("设置") }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val book = openBook
            if (book != null) {
                BookDetailScreen(book = book, onBack = { openBook = null })
            } else {
                when (tab) {
                    Tab.Home -> HomeScreen(state = homeState, onOpenBook = { openBook = it })
                    Tab.Listen -> ListenScreen(
                        playback = playback,
                        needsInstall = playback.needsInstall
                    )
                    Tab.Settings -> SettingsScreen(initResult = initResult)
                }
            }
        }
    }
}

/** 应用内迷你播放条（类似音乐 App 底部的小条）。 */
@Composable
private fun MiniPlayerBar(playback: PlaybackController, onOpen: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen() }
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playback.current?.word ?: "播放已结束",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                val pos = if (playback.current != null) {
                    (playback.total - playback.remaining + 1).coerceAtMost(playback.total)
                } else {
                    playback.total
                }
                Text(
                    text = "$pos/${playback.total}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = { playback.prev() }) { Text("上一") }
            TextButton(
                onClick = { playback.togglePause() },
                enabled = playback.current != null
            ) { Text(if (playback.paused) "继续" else "暂停") }
            TextButton(
                onClick = { playback.next() },
                enabled = playback.current != null
            ) { Text("下一") }
        }
    }
}

package com.easylistening.app.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.easylistening.app.AlarmScheduler
import com.easylistening.app.data.Repo
import com.easylistening.app.data.ScheduleConfig
import com.easylistening.app.data.Settings
import kotlin.math.roundToInt

@Composable
fun ListenScreen(playback: PlaybackController, needsInstall: Boolean) {
    val books = remember { Repo.listBooks() }
    val context = LocalContext.current

    var selectedIndex by remember { mutableStateOf(0) }
    var dailyText by remember { mutableStateOf(Settings.loadDailyCount(context).toString()) }
    var focusMode by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var scheduleCfg by remember { mutableStateOf(Settings.loadSchedule(context)) }
    var showSchedule by remember { mutableStateOf(false) }
    var schedMsg by remember { mutableStateOf("") }

    // 首次点击：先处理精确闹钟权限；已授权则直接打开配置
    fun onScheduleClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(AlarmManager::class.java)
            if (am != null && !am.canScheduleExactAlarms()) {
                schedMsg = "请先允许「闹钟和提醒」权限，返回后再点一次「定时」"
                runCatching {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                return
            }
        }
        scheduleCfg = Settings.loadSchedule(context)
        showSchedule = true
    }

    fun startListening() {
        if (books.isEmpty()) {
            message = "请先在主页创建或导入单词本"
            return
        }
        val book = books[selectedIndex.coerceIn(0, books.size - 1)]
        val daily = dailyText.toIntOrNull() ?: 0
        if (playback.start(book.id, daily)) {
            focusMode = false
            message = ""
        } else {
            message = "无法开始：单词本为空？"
        }
    }

    if (!playback.active) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Text("听力设置", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                // 定时播放（移到听力页顶部；24 小时制）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("定时播放（24 小时制）", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (scheduleCfg.enabled) {
                                "每天 %02d:%02d".format(scheduleCfg.hour, scheduleCfg.minute)
                            } else {
                                "未开启"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(onClick = { onScheduleClick() }) { Text("设置") }
                }
                if (schedMsg.isNotEmpty()) {
                    Text(
                        schedMsg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(12.dp))

                Text("选择单词本")
                Spacer(Modifier.height(4.dp))
                BookSelector(
                    books = books.map { it.name },
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it }
                )

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = dailyText,
                    onValueChange = {
                        dailyText = it.filter { ch -> ch.isDigit() }
                        Settings.saveDailyCount(context, dailyText.toIntOrNull() ?: 0)
                    },
                    label = { Text("每日词数（0 = 全部）") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自动播放", modifier = Modifier.weight(1f))
                    Switch(
                        checked = playback.autoPlay,
                        onCheckedChange = { playback.autoPlay = it }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("复读设置", style = MaterialTheme.typography.titleMedium)

                CountSlider(
                    label = "单词重复次数",
                    value = playback.cfg.wordRepeat,
                    onChange = {
                        playback.cfg = playback.cfg.copy(wordRepeat = it)
                        Settings.save(context, playback.cfg)
                    }
                )
                IntervalSlider(
                    label = "单词间隔",
                    ms = playback.cfg.wordIntervalMs,
                    onChange = {
                        playback.cfg = playback.cfg.copy(wordIntervalMs = it)
                        Settings.save(context, playback.cfg)
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("朗读释义", modifier = Modifier.weight(1f))
                    Switch(
                        checked = playback.cfg.speakTranslation,
                        onCheckedChange = {
                            playback.cfg = playback.cfg.copy(speakTranslation = it)
                            Settings.save(context, playback.cfg)
                        }
                    )
                }
                if (playback.cfg.speakTranslation) {
                    Text(
                        "提示：中文朗读需要设备装有中文语音包；若听不到释义，可点下方“安装语音包”或关闭本开关。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                CountSlider(
                    label = "释义重复次数",
                    value = playback.cfg.transRepeat,
                    onChange = {
                        playback.cfg = playback.cfg.copy(transRepeat = it)
                        Settings.save(context, playback.cfg)
                    }
                )
                IntervalSlider(
                    label = "释义间隔",
                    ms = playback.cfg.transIntervalMs,
                    onChange = {
                        playback.cfg = playback.cfg.copy(transIntervalMs = it)
                        Settings.save(context, playback.cfg)
                    }
                )

                if (needsInstall) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "未检测到英语/中文语音。建议先安装离线语音包（否则可能无声或需联网）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                        }
                    }) { Text("安装语音包") }
                }
            }

            if (message.isNotEmpty()) {
                Text(message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(4.dp))
            }
            Button(onClick = { startListening() }, modifier = Modifier.fillMaxWidth()) {
                Text("开始听力")
            }
        }

        if (showSchedule) {
            ScheduleDialog(
                initial = scheduleCfg,
                books = books,
                onDismiss = { showSchedule = false },
                onSave = { cfg ->
                    scheduleCfg = cfg
                    Settings.saveSchedule(context, cfg)
                    if (cfg.enabled) {
                        AlarmScheduler.schedule(context, cfg.hour, cfg.minute)
                    } else {
                        AlarmScheduler.cancel(context)
                    }
                    showSchedule = false
                }
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val currentPos = if (playback.current != null) {
                (playback.total - playback.remaining + 1).coerceAtMost(playback.total)
            } else {
                playback.total
            }
            Text(
                text = "进度 $currentPos/${playback.total}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().clickable { focusMode = !focusMode },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val word = playback.current
                Text(
                    text = word?.word ?: "今日完成",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (word != null && !focusMode) {
                    Spacer(Modifier.height(12.dp))
                    if (word.phonetic.isNotEmpty()) {
                        Text(word.phonetic, style = MaterialTheme.typography.titleMedium)
                    }
                    if (word.pos.isNotEmpty()) {
                        Text(word.pos, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(word.translation, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("自动播放", modifier = Modifier.weight(1f))
                Switch(
                    checked = playback.autoPlay,
                    onCheckedChange = { playback.autoPlay = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = { playback.prev() }) { Text("上一个") }
                OutlinedButton(
                    onClick = { playback.togglePause() },
                    enabled = playback.current != null || playback.paused
                ) { Text(if (playback.paused) "继续" else "暂停") }
                OutlinedButton(
                    onClick = { playback.next() },
                    enabled = playback.current != null
                ) { Text("下一个") }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { playback.replay() }) {
                    Text(if (playback.current == null) "重播一轮" else "重播本词")
                }
                TextButton(onClick = { playback.stop() }) { Text("结束") }
            }
        }
    }
}

@Composable
private fun CountSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f))
            Text("$value 次")
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(1, 10)) },
            valueRange = 1f..10f,
            steps = 8
        )
    }
}

@Composable
private fun IntervalSlider(label: String, ms: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f))
            Text("%.1f 秒".format(ms / 1000f))
        }
        Slider(
            value = (ms / 1000f).coerceIn(0f, 2f),
            onValueChange = { onChange((it * 1000).roundToInt().coerceIn(0, 2000)) },
            valueRange = 0f..2f,
            steps = 19
        )
    }
}

@Composable
private fun BookSelector(
    books: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = books.getOrNull(selectedIndex) ?: "（无单词本）"
    Box {
        OutlinedButton(
            onClick = { if (books.isNotEmpty()) expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            books.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(index); expanded = false }
                )
            }
        }
    }
}

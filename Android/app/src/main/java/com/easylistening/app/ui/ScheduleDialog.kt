package com.easylistening.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.easylistening.app.data.Book
import com.easylistening.app.data.ScheduleConfig

/** 定时播放配置弹窗（24 小时制）。 */
@Composable
fun ScheduleDialog(
    initial: ScheduleConfig,
    books: List<Book>,
    onDismiss: () -> Unit,
    onSave: (ScheduleConfig) -> Unit
) {
    var enabled by remember { mutableStateOf(initial.enabled) }
    var hour by remember { mutableStateOf(initial.hour.toString()) }
    var minute by remember { mutableStateOf(initial.minute.toString()) }
    var bookId by remember { mutableStateOf(initial.bookId) }
    var daily by remember { mutableStateOf(initial.daily.toString()) }
    var expanded by remember { mutableStateOf(false) }
    val bookName = books.find { it.id == bookId }?.name ?: "请选择单词本"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时播放（24 小时制）") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("时（0-23）") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("分（0-59）") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { if (books.isNotEmpty()) expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(bookName) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        books.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b.name) },
                                onClick = { bookId = b.id; expanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = daily,
                    onValueChange = { daily = it.filter { c -> c.isDigit() } },
                    label = { Text("每日词数（0 = 全部）") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    ScheduleConfig(
                        enabled = enabled,
                        hour = (hour.toIntOrNull() ?: 0).coerceIn(0, 23),
                        minute = (minute.toIntOrNull() ?: 0).coerceIn(0, 59),
                        bookId = bookId,
                        daily = (daily.toIntOrNull() ?: 0).coerceAtLeast(0)
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

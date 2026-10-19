package com.easylistening.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easylistening.app.data.Book
import com.easylistening.app.data.Repo

@Composable
fun BookDetailScreen(book: Book, onBack: () -> Unit) {
    var words by remember { mutableStateOf(Repo.bookWords(book.id)) }
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showAdd by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    val books = remember { Repo.listBooks() }

    fun refresh() {
        words = Repo.bookWords(book.id)
    }

    fun deleteSelected() {
        selected.forEach { Repo.removeWord(book.id, it) }
        selected = emptySet()
        refresh()
    }

    fun moveSelected(targetId: Long) {
        val map = words.associateBy { it.word }
        selected.forEach { w ->
            val entry = map[w] ?: return@forEach
            Repo.removeWord(book.id, w)
            Repo.addWord(targetId, entry.word, entry.phonetic, entry.pos, entry.translation)
        }
        selected = emptySet()
        refresh()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = book.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (words.isNotEmpty()) {
                TextButton(onClick = { editing = !editing; selected = emptySet() }) {
                    Text(if (editing) "完成" else "编辑")
                }
            }
            if (!editing) {
                TextButton(onClick = { showAdd = true }) { Text("新增") }
            }
        }
        HorizontalDivider()

        Box(modifier = Modifier.weight(1f)) {
            if (words.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("本单词本还没有词，点右上角“新增”或“编辑”")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(words, key = { it.word }) { w ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (editing) {
                                Checkbox(
                                    checked = w.word in selected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + w.word
                                        else selected - w.word
                                    }
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = w.word,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val sub = buildString {
                                    if (w.phonetic.isNotEmpty()) append(w.phonetic).append("  ")
                                    if (w.pos.isNotEmpty()) append(w.pos).append(" ")
                                    append(w.translation)
                                }
                                if (sub.isNotBlank()) {
                                    Text(text = sub, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (!editing) {
                                IconButton(onClick = { Repo.removeWord(book.id, w.word); refresh() }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        if (editing && selected.isNotEmpty()) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { deleteSelected() }) {
                    Text("删除 (${selected.size})")
                }
                TextButton(onClick = { showMove = true }) {
                    Text("移动到… (${selected.size})")
                }
            }
        }
    }

    if (showAdd) {
        AddWordDialog(
            onDismiss = { showAdd = false },
            onAdd = { word, phonetic, pos, translation ->
                Repo.addWord(book.id, word, phonetic, pos, translation)
                showAdd = false
                refresh()
            }
        )
    }

    if (showMove) {
        MoveTargetDialog(
            books = books.filter { it.id != book.id },
            onDismiss = { showMove = false },
            onPick = { targetId -> showMove = false; moveSelected(targetId) }
        )
    }
}

@Composable
private fun MoveTargetDialog(
    books: List<Book>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到") },
        text = {
            if (books.isEmpty()) {
                Text("没有其他单词本")
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    books.forEach { b ->
                        Text(
                            text = b.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(b.id) }
                                .padding(vertical = 12.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun AddWordDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit
) {
    var word by remember { mutableStateOf("") }
    var phonetic by remember { mutableStateOf("") }
    var pos by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增单词") },
        text = {
            Column {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text("单词") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phonetic,
                    onValueChange = { phonetic = it },
                    label = { Text("音标") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pos,
                    onValueChange = { pos = it },
                    label = { Text("词性") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = translation,
                    onValueChange = { translation = it },
                    label = { Text("释义") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (word.isNotBlank()) onAdd(word.trim(), phonetic, pos, translation)
            }) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

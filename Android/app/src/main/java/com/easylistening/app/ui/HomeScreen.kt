package com.easylistening.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easylistening.app.data.Book
import com.easylistening.app.data.Repo
import com.easylistening.app.data.Word
import com.easylistening.app.data.copyImageToPrivate
import com.easylistening.app.data.copyUriToCache
import com.easylistening.app.data.queryDisplayName
import com.easylistening.app.data.writeWordsToUri
import java.io.File

/** 主页查词状态（提升到 App 层，切换标签不丢失）。 */
class HomeUiState(targetLang: String) {
    var input by mutableStateOf("")
    var targetLang by mutableStateOf(targetLang)
    var result by mutableStateOf<Word?>(null)
    var searched by mutableStateOf(false)
    var existsInAny by mutableStateOf(false)
    var onTargetLangChange: (String) -> Unit = {}
}

@Composable
fun HomeScreen(state: HomeUiState, onOpenBook: (Book) -> Unit) {
    val context = LocalContext.current
    var books by remember { mutableStateOf(Repo.listBooks()) }
    var showCreate by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Book?>(null) }
    var exportTarget by remember { mutableStateOf<Book?>(null) }
    var coverTarget by remember { mutableStateOf<Book?>(null) }

    fun refresh() {
        books = Repo.listBooks()
    }

    fun uniqueBookName(base: String): String {
        val names = books.map { it.name }.toSet()
        if (base !in names) return base
        var i = 2
        while ("$base$i" in names) i++
        return "$base$i"
    }

    fun moveBook(book: Book, delta: Int) {
        val idx = books.indexOfFirst { it.id == book.id }
        if (idx < 0) return
        val target = (idx + delta).coerceIn(0, books.size - 1)
        if (target != idx) {
            Repo.moveBook(book.id, target)
            refresh()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = queryDisplayName(context, uri) ?: "导入词表"
            val bookName = fileName.substringBeforeLast('.')
            val bookId = Repo.createBook(bookName)
            val path = copyUriToCache(context, uri, "import_$bookId.txt")
            if (path != null) {
                val stat = Repo.importTxt(bookId, path)
                statusText = "导入「$bookName」：新增 ${stat.added}，重复 ${stat.dup}"
            } else {
                statusText = "导入失败：无法读取文件"
            }
            refresh()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val target = exportTarget
        if (uri != null && target != null) {
            val words = Repo.bookWords(target.id).map { it.word }
            statusText = if (writeWordsToUri(context, uri, words)) {
                "已导出「${target.name}」（${words.size} 词）"
            } else {
                "导出失败"
            }
        }
        exportTarget = null
    }

    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val target = coverTarget
        if (uri != null && target != null) {
            val dest = File(File(context.filesDir, "covers"), "${target.id}.jpg")
            val path = copyImageToPrivate(context, uri, dest)
            if (path != null) {
                Repo.setCover(target.id, path)
                statusText = "已更新「${target.name}」的封面"
                refresh()
            } else {
                statusText = "封面设置失败"
            }
        }
        coverTarget = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 上半：查词/翻译，整屏水平居中，半屏内垂直居中
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            SearchCard(
                state = state,
                books = books,
                statusText = statusText,
                onStatus = { statusText = it },
                onNewBook = { uniqueBookName("生词本") },
                onChanged = {
                    state.result?.let { state.existsInAny = Repo.existsAnyWhere(it.word) }
                    refresh()
                }
            )
        }

        // 下半：我的单词本
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "我的单词本",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showCreate = true }) { Text("新建") }
                TextButton(onClick = { importLauncher.launch(arrayOf("text/plain", "*/*")) }) {
                    Text("导入 .txt")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (books.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有单词本，点“新建”或“导入 .txt”")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(books, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            onOpen = onOpenBook,
                            onRename = { renameTarget = book },
                            onCover = { coverTarget = book; coverLauncher.launch("image/*") },
                            onExport = { exportTarget = book; exportLauncher.launch("${book.name}.txt") },
                            onMoveUp = { moveBook(book, -1) },
                            onMoveDown = { moveBook(book, 1) },
                            onDelete = { Repo.deleteBook(book.id); refresh() }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateBookDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                Repo.createBook(name)
                showCreate = false
                refresh()
            }
        )
    }

    renameTarget?.let { book ->
        RenameBookDialog(
            initial = book.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                Repo.renameBook(book.id, name)
                renameTarget = null
                refresh()
            }
        )
    }
}

@Composable
private fun SearchCard(
    state: HomeUiState,
    books: List<Book>,
    statusText: String,
    onStatus: (String) -> Unit,
    onNewBook: () -> String,
    onChanged: () -> Unit
) {
    val languages = listOf("中文", "English", "日本語", "Русский")
    var expanded by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun doSearch() {
        val w = state.input.trim()
        if (w.isEmpty()) return
        onStatus("")
        val found = Repo.lookup(w)
        state.result = found
        state.searched = true
        state.existsInAny = found?.let { Repo.existsAnyWhere(it.word) } ?: false
    }

    fun pasteFromClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                state.input = text.lineSequence().first().trim()
                doSearch()
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = { state.input = it },
                    label = { Text("输入单词") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { doSearch() }) {
                            Icon(Icons.Filled.Search, contentDescription = "查询")
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(state.targetLang) }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang) },
                                onClick = {
                                    state.targetLang = lang
                                    state.onTargetLangChange(lang)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            TextButton(onClick = { pasteFromClipboard() }) { Text("粘贴") }

            if (state.searched) {
                Spacer(Modifier.height(8.dp))
                val r = state.result
                if (r == null) {
                    Text("未收录：${state.input}", color = MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        text = r.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.existsInAny) {
                        Text(
                            text = "已收录",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    val sub = buildString {
                        if (r.phonetic.isNotEmpty()) append(r.phonetic).append("  ")
                        if (r.pos.isNotEmpty()) append(r.pos)
                    }
                    if (sub.isNotBlank()) {
                        Text(sub, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(r.translation, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { showPicker = true }) { Text("加入单词本") }
                }
            }

            if (statusText.isNotEmpty()) {
                Text(
                    text = statusText,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showPicker && state.result != null) {
        val r = state.result!!
        BookPickerDialog(
            books = books,
            onDismiss = { showPicker = false },
            onPick = { bookId ->
                Repo.addWord(bookId, r.word, r.phonetic, r.pos, r.translation)
                showPicker = false
                onStatus("已把「${r.word}」加入单词本")
                onChanged()
            },
            onPickNew = {
                val name = onNewBook()
                val id = Repo.createBook(name)
                Repo.addWord(id, r.word, r.phonetic, r.pos, r.translation)
                showPicker = false
                onStatus("已新建「$name」并加入「${r.word}」")
                onChanged()
            }
        )
    }
}

@Composable
private fun BookPickerDialog(
    books: List<Book>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    onPickNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入单词本") },
        text = {
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
                Text(
                    text = "＋ 新建单词本并加入",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickNew() }
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun BookCard(
    book: Book,
    onOpen: (Book) -> Unit,
    onRename: () -> Unit,
    onCover: () -> Unit,
    onExport: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.clickable { onOpen(book) }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BookCover(path = book.cover)
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${book.count} 词",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("更换封面") }, onClick = { menu = false; onCover() })
                        DropdownMenuItem(text = { Text("导出 .txt") }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text("上移") }, onClick = { menu = false; onMoveUp() })
                        DropdownMenuItem(text = { Text("下移") }, onClick = { menu = false; onMoveDown() })
                        DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CreateBookDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建单词本") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun RenameBookDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

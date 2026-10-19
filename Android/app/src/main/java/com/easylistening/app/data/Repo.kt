package com.easylistening.app.data

import com.easylistening.app.NativeLib

data class Book(val id: Long, val name: String, val cover: String, val count: Int)

data class Word(
    val word: String,
    val phonetic: String,
    val pos: String,
    val translation: String
)

data class ImportStat(val added: Int, val dup: Int)

/** 对 C 内核的薄封装：把 Tab 文本解析成 Kotlin 数据类。 */
object Repo {

    fun listBooks(): List<Book> =
        NativeLib.nativeListBooks().lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val p = line.split("\t", limit = 4)
                val id = p.getOrNull(0)?.toLongOrNull()
                if (p.size < 4 || id == null) null
                else Book(id, p[1], p[2], p[3].toIntOrNull() ?: 0)
            }

    fun dictCount(): Int = NativeLib.nativeDictCount()

    fun lookup(word: String): Word? {
        if (word.isBlank()) return null
        val s = NativeLib.nativeDictLookup(word.trim())
        if (s.isEmpty()) return null
        val p = s.split("\t", limit = 4)
        return Word(
            p.getOrElse(0) { "" },
            p.getOrElse(1) { "" },
            p.getOrElse(2) { "" },
            p.getOrElse(3) { "" }
        )
    }

    fun createBook(name: String): Long = NativeLib.nativeCreateBook(name)
    fun deleteBook(id: Long) = NativeLib.nativeDeleteBook(id)
    fun renameBook(id: Long, name: String) = NativeLib.nativeRenameBook(id, name)
    fun setCover(id: Long, path: String) = NativeLib.nativeSetCover(id, path)
    fun moveBook(id: Long, newIndex: Int) = NativeLib.nativeMoveBook(id, newIndex)

    fun bookWords(id: Long): List<Word> =
        NativeLib.nativeBookWords(id).lines()
            .filter { it.isNotBlank() }
            .map { line ->
                val p = line.split("\t", limit = 4)
                Word(
                    p.getOrElse(0) { "" },
                    p.getOrElse(1) { "" },
                    p.getOrElse(2) { "" },
                    p.getOrElse(3) { "" }
                )
            }

    fun addWord(
        id: Long, word: String, phonetic: String, pos: String, translation: String
    ): Int = NativeLib.nativeAddWord(id, word, phonetic, pos, translation)

    fun removeWord(id: Long, word: String) = NativeLib.nativeRemoveWord(id, word)

    fun existsAnyWhere(word: String): Boolean = NativeLib.nativeWordExistsAny(word)

    fun importTxt(id: Long, path: String): ImportStat {
        val p = NativeLib.nativeImportTxt(id, path).split("\t")
        return ImportStat(
            p.getOrElse(0) { "0" }.toIntOrNull() ?: 0,
            p.getOrElse(1) { "0" }.toIntOrNull() ?: 0
        )
    }

    fun scheduleConfig(bookId: Long, daily: Int) =
        NativeLib.nativeScheduleConfig(bookId, daily)

    fun scheduleStart(): Int = NativeLib.nativeScheduleStart()
    fun scheduleNext(): Long = NativeLib.nativeScheduleNext()
    fun scheduleCommit() = NativeLib.nativeScheduleCommit()
    fun schedulePrev(): Long = NativeLib.nativeSchedulePrev()
    fun scheduleRestart(): Int = NativeLib.nativeScheduleRestart()
    fun scheduleTotal(): Int = NativeLib.nativeScheduleTotal()
    fun scheduleRemaining(): Int = NativeLib.nativeScheduleRemaining()

    fun scheduleCurrentWord(): Word? {
        val s = NativeLib.nativeScheduleCurrentWord()
        if (s.isEmpty()) return null
        val p = s.split("\t", limit = 4)
        return Word(
            p.getOrElse(0) { "" },
            p.getOrElse(1) { "" },
            p.getOrElse(2) { "" },
            p.getOrElse(3) { "" }
        )
    }
}

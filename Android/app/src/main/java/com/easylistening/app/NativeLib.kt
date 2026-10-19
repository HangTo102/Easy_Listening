package com.easylistening.app

/**
 * C 内核的 JNI 入口（对应 core/src 编出的 libeasylistening.so）。
 *
 * 列表类返回值统一用 Tab 分隔的行文本，由 Repo 解析，避免在 C 里拼 JSON。
 */
object NativeLib {
    init {
        System.loadLibrary("easylistening")
    }

    external fun nativeVersion(): String
    external fun nativeInit(workdir: String): String

    external fun nativeDictCount(): Int
    external fun nativeDictLookup(word: String): String

    external fun nativeListBooks(): String
    external fun nativeCreateBook(name: String): Long
    external fun nativeDeleteBook(id: Long)
    external fun nativeRenameBook(id: Long, name: String)
    external fun nativeSetCover(id: Long, path: String)
    external fun nativeMoveBook(id: Long, newIndex: Int)
    external fun nativeBookWords(id: Long): String
    external fun nativeAddWord(
        id: Long, word: String, phonetic: String, pos: String, translation: String
    ): Int
    external fun nativeRemoveWord(id: Long, word: String)
    external fun nativeWordExistsAny(word: String): Boolean
    external fun nativeImportTxt(id: Long, path: String): String
    external fun nativeSeedBook(name: String, listPath: String): Long

    external fun nativeScheduleConfig(bookId: Long, daily: Int)
    external fun nativeScheduleStart(): Int
    external fun nativeScheduleNext(): Long
    external fun nativeScheduleCommit()
    external fun nativeSchedulePrev(): Long
    external fun nativeScheduleRestart(): Int
    external fun nativeScheduleTotal(): Int
    external fun nativeScheduleRemaining(): Int
    external fun nativeScheduleCurrentWord(): String
}

package com.easylistening.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

fun queryDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
            return cursor.getString(idx)
        }
    }
    return null
}

/** 把 SAF 选中的文件复制到 App 私有缓存，返回可被 C 内核 fopen 的绝对路径。 */
fun copyUriToCache(context: Context, uri: Uri, name: String): String? =
    try {
        val file = File(context.cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file.absolutePath
    } catch (e: Exception) {
        null
    }

/** 首次启动时把 assets 里的文件复制到私有目录（已存在则跳过）。 */
fun ensureAsset(context: Context, assetPath: String, dest: File) {
    if (dest.exists()) return
    dest.parentFile?.mkdirs()
    context.assets.open(assetPath).use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    }
}

/** 复制 assets 文件到私有目录（总是覆盖）。asset 不存在返回 false。 */
fun copyAssetIfExists(context: Context, assetPath: String, dest: File): Boolean =
    try {
        context.assets.open(assetPath).use { input ->
            dest.parentFile?.mkdirs()
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        true
    } catch (e: Exception) {
        false
    }

/** 把词表按行写入用户选择的目标（SAF 导出的 Uri）。 */
fun writeWordsToUri(context: Context, uri: Uri, words: List<String>): Boolean =
    try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            words.forEach { out.write((it + "\n").toByteArray(Charsets.UTF_8)) }
        }
        true
    } catch (e: Exception) {
        false
    }

/** 把选中的图片复制到私有目录，返回绝对路径。 */
fun copyImageToPrivate(context: Context, uri: Uri, dest: File): String? =
    try {
        dest.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    } catch (e: Exception) {
        null
    }

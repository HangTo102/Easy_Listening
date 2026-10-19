package com.easylistening.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** 单词本封面：从本地图片路径解码显示（无封面则不渲染）。 */
@Composable
fun BookCover(path: String, modifier: Modifier = Modifier) {
    if (path.isEmpty()) return
    val bitmap = remember(path) {
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.fillMaxWidth().height(96.dp),
            contentScale = ContentScale.Crop
        )
    }
}

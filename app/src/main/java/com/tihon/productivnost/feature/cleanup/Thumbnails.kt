package com.tihon.productivnost.feature.cleanup

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Маленький кеш миниатюр — Coil тянуть ради одной сетки незачем. */
private val cache = LruCache<String, ImageBitmap>(64)

@Composable
fun rememberThumbnail(context: Context, uri: Uri, size: Int = 192): ImageBitmap? {
    val key = "$uri@$size"
    var image by remember(key) { mutableStateOf(cache.get(key)) }
    LaunchedEffect(key) {
        if (image != null) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) { PhotoScanner.loadTiny(context, uri, size) }
        if (bmp != null) {
            val img = bmp.asImageBitmap()
            cache.put(key, img)
            image = img
        }
    }
    return image
}

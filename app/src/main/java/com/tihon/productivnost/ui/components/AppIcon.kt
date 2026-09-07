package com.tihon.productivnost.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.tihon.productivnost.util.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = LruCache<String, ImageBitmap>(160)

@Composable
fun AppIcon(pkg: String, modifier: Modifier = Modifier, sizePx: Int = 108) {
    val context = LocalContext.current
    var icon by remember(pkg) { mutableStateOf(iconCache.get(pkg)) }
    LaunchedEffect(pkg) {
        if (icon != null) return@LaunchedEffect
        val img = withContext(Dispatchers.IO) { load(context, pkg, sizePx) }
        if (img != null) {
            iconCache.put(pkg, img)
            icon = img
        }
    }
    val bmp = icon
    if (bmp != null) {
        Image(bmp, null, modifier, contentScale = ContentScale.Fit)
    }
}

private fun load(context: Context, pkg: String, sizePx: Int): ImageBitmap? = runCatching {
    Apps.icon(context, pkg)?.toBitmap(sizePx, sizePx)?.asImageBitmap()
}.getOrNull()

package com.tihon.productivnost.feature.cleanup

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateMs: Long,
    val bucket: String,
    val width: Int,
    val height: Int,
    val hash: Long,
) {
    val pixels: Long get() = width.toLong() * height.toLong()
}

data class PhotoGroup(
    val key: String,
    val items: List<PhotoItem>,
) {
    /** Лучший кадр в группе — самый крупный по разрешению, затем по весу файла. */
    val keep: PhotoItem = items.maxByOrNull { it.pixels * 1000L + it.size / 1024L } ?: items.first()
    val extras: List<PhotoItem> get() = items.filter { it.id != keep.id }
    val reclaimable: Long get() = extras.sumOf { it.size }
}

data class ScanResult(
    val total: Int = 0,
    val duplicates: List<PhotoGroup> = emptyList(),
    val similar: List<PhotoGroup> = emptyList(),
    val screenshots: List<PhotoItem> = emptyList(),
    val heavy: List<PhotoItem> = emptyList(),
) {
    val duplicateBytes get() = duplicates.sumOf { it.reclaimable }
    val similarBytes get() = similar.sumOf { it.reclaimable }
    val screenshotBytes get() = screenshots.sumOf { it.size }
    val heavyBytes get() = heavy.sumOf { it.size }
    val isEmpty get() = duplicates.isEmpty() && similar.isEmpty() && screenshots.isEmpty() && heavy.isEmpty()
}

/**
 * Сканер галереи: ищет точные дубликаты, серии почти одинаковых кадров,
 * скриншоты и самые тяжёлые файлы.
 *
 * Схожесть считается по перцептивному хешу 8×8 — он устойчив к пережатию
 * и разнице в разрешении, поэтому находит именно повторяющиеся кадры.
 */
object PhotoScanner {

    private const val MAX_SCAN = 3000
    private const val HEAVY_BYTES = 8L * 1024 * 1024
    private const val SIMILAR_DISTANCE = 6
    private const val SIMILAR_WINDOW_MS = 5 * 60_000L

    suspend fun scan(
        context: Context,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ScanResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val items = ArrayList<PhotoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val rows = ArrayList<PhotoItem>()
        resolver.query(collection, projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeC = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketC = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val wC = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hC = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (c.moveToNext() && rows.size < MAX_SCAN) {
                val id = c.getLong(idC)
                rows += PhotoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(nameC) ?: "",
                    size = c.getLong(sizeC),
                    dateMs = c.getLong(dateC) * 1000L,
                    bucket = c.getString(bucketC) ?: "",
                    width = c.getInt(wC),
                    height = c.getInt(hC),
                    hash = 0L,
                )
            }
        }

        val total = rows.size
        rows.forEachIndexed { i, row ->
            coroutineContext.ensureActive()
            val hash = perceptualHash(context, row.uri)
            items += row.copy(hash = hash)
            if (i % 25 == 0) onProgress(i, total)
        }
        onProgress(total, total)

        val hashed = items.filter { it.hash != 0L }

        // Точные дубликаты: одинаковый вес и одинаковый хеш.
        val duplicates = hashed
            .groupBy { "${it.size}:${it.hash}" }
            .values.filter { it.size > 1 }
            .map { PhotoGroup("dup-${it.first().id}", it.sortedByDescending { p -> p.pixels }) }
            .sortedByDescending { it.reclaimable }

        val dupIds = duplicates.flatMap { g -> g.items.map { it.id } }.toHashSet()

        // Похожие кадры: близкий хеш и снятые примерно в одно время.
        val similar = ArrayList<PhotoGroup>()
        val used = HashSet<Long>()
        val byTime = hashed.filter { it.id !in dupIds }.sortedByDescending { it.dateMs }
        for (i in byTime.indices) {
            coroutineContext.ensureActive()
            val a = byTime[i]
            if (a.id in used) continue
            val bucket = ArrayList<PhotoItem>()
            var j = i + 1
            while (j < byTime.size && abs(byTime[j].dateMs - a.dateMs) <= SIMILAR_WINDOW_MS) {
                val b = byTime[j]
                if (b.id !in used && hamming(a.hash, b.hash) <= SIMILAR_DISTANCE) bucket += b
                j++
            }
            if (bucket.isNotEmpty()) {
                bucket += a
                bucket.forEach { used += it.id }
                similar += PhotoGroup("sim-${a.id}", bucket.sortedByDescending { it.pixels })
            }
        }

        val screenshots = items.filter {
            it.bucket.equals("Screenshots", true) || it.name.startsWith("Screenshot", true) ||
                it.name.startsWith("Скриншот", true)
        }.sortedByDescending { it.size }

        val heavy = items.filter { it.size >= HEAVY_BYTES }.sortedByDescending { it.size }.take(120)

        ScanResult(
            total = total,
            duplicates = duplicates,
            similar = similar.sortedByDescending { it.reclaimable },
            screenshots = screenshots,
            heavy = heavy,
        )
    }

    private fun hamming(a: Long, b: Long) = java.lang.Long.bitCount(a xor b)

    /** Средний хеш 8×8: изображение сжимается и сравнивается со своей средней яркостью. */
    private fun perceptualHash(context: Context, uri: Uri): Long {
        val bmp = loadTiny(context, uri) ?: return 0L
        val scaled = runCatching { Bitmap.createScaledBitmap(bmp, 8, 8, true) }.getOrNull() ?: return 0L
        val gray = IntArray(64)
        var sum = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            val p = scaled.getPixel(x, y)
            val g = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            gray[y * 8 + x] = g
            sum += g
        }
        val avg = sum / 64
        var hash = 0L
        for (i in 0 until 64) if (gray[i] >= avg) hash = hash or (1L shl i)
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        // Полностью однотонные кадры дают вырожденный хеш — их не сравниваем.
        return if (hash == 0L || hash == -1L) 0L else hash
    }

    fun loadTiny(context: Context, uri: Uri, target: Int = 96): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(target, target), null)
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val opts = BitmapFactory.Options().apply { inSampleSize = 8; inPreferredConfig = Bitmap.Config.RGB_565 }
                BitmapFactory.decodeStream(input, null, opts)
            }
        }
    }.getOrNull()
}

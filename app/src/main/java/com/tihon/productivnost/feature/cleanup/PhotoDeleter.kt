package com.tihon.productivnost.feature.cleanup

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object PhotoDeleter {

    /** На Android 11+ удаление подтверждает сама система — возвращаем её запрос. */
    fun deleteRequest(context: Context, uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        }.getOrNull()
    }

    /** До Android 11 удаляем напрямую — разрешение на запись уже получено. */
    fun deleteDirect(context: Context, uris: List<Uri>): Int {
        var count = 0
        uris.forEach { uri ->
            runCatching { count += context.contentResolver.delete(uri, null, null) }
        }
        return count
    }
}

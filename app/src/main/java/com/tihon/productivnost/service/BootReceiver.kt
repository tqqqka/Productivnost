package com.tihon.productivnost.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.util.Permissions

/** После перезагрузки наблюдение должно подняться само. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Store.init(context)
        if (Permissions.hasUsageAccess(context)) {
            ProductivityService.start(context)
        }
    }
}

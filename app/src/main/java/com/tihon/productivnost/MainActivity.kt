package com.tihon.productivnost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.service.ProductivityService
import com.tihon.productivnost.ui.AppRoot
import com.tihon.productivnost.ui.theme.ProductivnostTheme
import com.tihon.productivnost.util.Permissions

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent {
            ProductivnostTheme {
                LaunchedEffect(Unit) {
                    if (Permissions.hasUsageAccess(this@MainActivity)) {
                        ProductivityService.start(this@MainActivity)
                    }
                }
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Store.rolloverIfNeeded()
    }
}

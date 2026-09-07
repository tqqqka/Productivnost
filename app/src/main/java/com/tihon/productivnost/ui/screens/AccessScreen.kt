package com.tihon.productivnost.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.tihon.productivnost.service.ProductivityService
import com.tihon.productivnost.ui.ScreenHeader
import com.tihon.productivnost.ui.components.GhostButton
import com.tihon.productivnost.ui.components.SoftCard
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.util.Permissions

@Composable
fun AccessScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val e = LocalExtra.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val usage = remember(refresh) { Permissions.hasUsageAccess(context) }
    val overlay = remember(refresh) { Permissions.hasOverlay(context) }
    val notif = remember(refresh) { Permissions.hasNotifications(context) }
    val camera = remember(refresh) { Permissions.hasCamera(context) }
    val media = remember(refresh) { Permissions.hasMedia(context) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                "Разрешения",
                "Без первых двух приложение не сможет ни напомнить, ни защитить дерево",
                onBack,
            )
        }
        item {
            AccessCard(
                "Доступ к статистике",
                "Нужен, чтобы видеть, каким приложением ты пользуешься прямо сейчас",
                usage, required = true,
            ) {
                context.startActivity(Permissions.usageAccessIntent())
            }
        }
        item {
            AccessCard(
                "Поверх других приложений",
                "Полноэкранные напоминания и заслонка на запрещённых приложениях",
                overlay, required = true,
            ) {
                context.startActivity(Permissions.overlayIntent(context))
            }
        }
        item {
            AccessCard(
                "Уведомления",
                "Тихая строка о том, что сессия идёт",
                notif, required = false,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        item {
            AccessCard(
                "Камера",
                "Считает отжимания для продления приложений",
                camera, required = false,
            ) { cameraLauncher.launch(Manifest.permission.CAMERA) }
        }
        item {
            AccessCard(
                "Доступ к фото",
                "Поиск дубликатов и лишних кадров в галерее",
                media, required = false,
            ) { mediaLauncher.launch(Permissions.mediaPermission()) }
        }
        item {
            SoftCard {
                Text("Работа в фоне", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Если система усыпляет приложение, напоминания начнут опаздывать. " +
                        "Отключи для него экономию батареи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = e.faint,
                )
                Spacer(Modifier.height(12.dp))
                GhostButton("Открыть настройки батареи", Modifier.fillMaxWidth()) {
                    runCatching { context.startActivity(Permissions.batteryIntent(context)) }
                }
            }
        }
        item {
            GhostButton("Перезапустить наблюдение", Modifier.fillMaxWidth(), tint = P.leaf) {
                ProductivityService.start(context)
                refresh++
            }
        }
    }
}

@Composable
private fun AccessCard(
    title: String,
    body: String,
    granted: Boolean,
    required: Boolean,
    onGrant: () -> Unit,
) {
    val e = LocalExtra.current
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(body, style = MaterialTheme.typography.bodySmall, color = e.faint)
            }
            Spacer(Modifier.height(0.dp))
            Text(
                if (granted) "✓" else if (required) "!" else "—",
                style = MaterialTheme.typography.headlineSmall,
                color = if (granted) P.leaf else if (required) P.rose else e.faint,
            )
        }
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            GhostButton("Выдать", Modifier.fillMaxWidth(), tint = if (required) P.amber else e.dim, onClick = onGrant)
        }
    }
}

package com.tihon.productivnost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.ui.ScreenHeader
import com.tihon.productivnost.ui.components.AppIcon
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.util.AppInfo
import com.tihon.productivnost.util.Apps

@Composable
fun AllowedAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val e = LocalExtra.current
    val settings by Store.settings.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = Apps.launchable(context) }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
    }
    val selected = settings.allowedApps

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                "Разрешённые приложения",
                "Их можно открывать во время сессии — всё остальное будет закрыто",
                onBack,
            )
        }
        item {
            SearchField(query, e.card, e.outline, e.faint) { query = it }
            Spacer(Modifier.height(4.dp))
            Text(
                "Выбрано: ${selected.size}",
                style = MaterialTheme.typography.bodySmall,
                color = e.faint,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            )
        }
        items(filtered, key = { it.pkg }) { app ->
            val checked = app.pkg in selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(e.card)
                    .border(1.dp, if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else e.outline, MaterialTheme.shapes.medium)
                    .clickable {
                        Store.updateSettings { s ->
                            s.copy(
                                allowedApps = if (checked) s.allowedApps - app.pkg else s.allowedApps + app.pkg,
                            )
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.pkg, Modifier.size(36.dp))
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = e.faint, maxLines = 1)
                }
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (checked) MaterialTheme.colorScheme.primary else e.cardHigh,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) Text("✓", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFF061109))
                }
            }
        }
    }
}

@Composable
fun SearchField(
    value: String,
    bg: androidx.compose.ui.graphics.Color,
    outline: androidx.compose.ui.graphics.Color,
    hintColor: androidx.compose.ui.graphics.Color,
    onChange: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(1.dp, outline, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text("Поиск", style = MaterialTheme.typography.bodyLarge, color = hintColor)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

package com.tihon.productivnost.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tihon.productivnost.ui.components.ScreenBackground
import com.tihon.productivnost.ui.screens.AccessScreen
import com.tihon.productivnost.ui.screens.AllowedAppsScreen
import com.tihon.productivnost.ui.screens.CleanupScreen
import com.tihon.productivnost.ui.screens.GardenScreen
import com.tihon.productivnost.ui.screens.HomeScreen
import com.tihon.productivnost.ui.screens.LimitsScreen
import com.tihon.productivnost.ui.screens.MoreScreen
import com.tihon.productivnost.ui.screens.PlantDetailScreen
import com.tihon.productivnost.ui.screens.SessionsScreen
import com.tihon.productivnost.ui.screens.SettingsScreen
import com.tihon.productivnost.ui.screens.TasksScreen
import com.tihon.productivnost.ui.theme.LocalExtra

@Composable
fun AppRoot() {
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val current = stack.last()
    val navigate: (Route) -> Unit = { route ->
        if (route.isTab) {
            stack.clear()
            stack.add(route)
        } else if (stack.last() != route) {
            stack.add(route)
        }
    }
    val back: () -> Unit = {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        else if (stack.last() != Route.Home) { stack.clear(); stack.add(Route.Home) }
    }
    BackHandler(enabled = stack.size > 1) { back() }

    ScreenBackground {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
                targetState = current,
                transitionSpec = {
                    val forward = targetState.isTab.not()
                    if (forward) {
                        (slideInHorizontally(tween(320)) { it / 6 } + fadeIn(tween(220))) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(320)) { -it / 14 })
                    } else {
                        (fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 30 }) togetherWith
                            fadeOut(tween(150))
                    }
                },
                label = "route",
            ) { route ->
                when (route) {
                    Route.Home -> HomeScreen(navigate)
                    Route.Sessions -> SessionsScreen(navigate)
                    Route.Garden -> GardenScreen(navigate)
                    Route.More -> MoreScreen(navigate)
                    Route.AllowedApps -> AllowedAppsScreen(back)
                    Route.Limits -> LimitsScreen(back)
                    Route.Tasks -> TasksScreen(back)
                    Route.Settings -> SettingsScreen(back, navigate)
                    Route.Cleanup -> CleanupScreen(back)
                    Route.Access -> AccessScreen(back)
                    is Route.PlantDetail -> PlantDetailScreen(route.id, back)
                }
            }

            if (current.isTab) {
                BottomBar(
                    current = current,
                    onSelect = navigate,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 14.dp),
                )
            }
        }
    }
}

private data class TabSpec(val route: Route, val icon: ImageVector, val label: String)

@Composable
private fun BottomBar(current: Route, onSelect: (Route) -> Unit, modifier: Modifier = Modifier) {
    val e = LocalExtra.current
    val items = listOf(
        TabSpec(Route.Home, Icons.Rounded.Home, "Главная"),
        TabSpec(Route.Sessions, Icons.Rounded.Timer, "Сессии"),
        TabSpec(Route.Garden, Icons.Rounded.Forest, "Сад"),
        TabSpec(Route.More, Icons.Rounded.Tune, "Ещё"),
    )
    Row(
        modifier
            .padding(horizontal = 22.dp)
            .clip(CircleShape)
            .background(Color(0xF21A231F))
            .border(1.dp, e.outline, CircleShape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val active = current == item.route
            val tint by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else e.faint,
                tween(240), label = "tabTint",
            )
            val bg by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                tween(240), label = "tabBg",
            )
            val scale by animateFloatAsState(if (active) 1f else 0.94f, spring(Spring.DampingRatioMediumBouncy), label = "tabScale")
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .scale(scale)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(interaction, null) { onSelect(item.route) }
                    .padding(horizontal = 17.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(21.dp))
                Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

/** Общая шапка внутренних экранов. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    val e = LocalExtra.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Text("‹  Назад", style = MaterialTheme.typography.labelMedium, color = e.dim)
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = e.faint, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

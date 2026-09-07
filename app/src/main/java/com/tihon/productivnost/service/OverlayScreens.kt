package com.tihon.productivnost.service

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tihon.productivnost.ui.components.GhostButton
import com.tihon.productivnost.ui.components.PrimaryButton
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.ui.theme.ProductivnostTheme

/** Общая «сцена» полноэкранного оверлея: тёмный градиент и мягкое сияние акцента. */
@Composable
private fun OverlayScene(accent: Color, content: @Composable () -> Unit) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF20A100E), Color(0xFA050908))))
            .alpha(enter.value.coerceIn(0f, 1f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(0.5f, 0.34f),
                        radius = 900f,
                    ),
                ),
        )
        Box(Modifier.scale(0.92f + 0.08f * enter.value)) { content() }
    }
}

@Composable
private fun PulsingBadge(icon: String, accent: Color) {
    val t = rememberInfiniteTransition(label = "badge")
    val pulse by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size((116 + 54 * pulse).dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f * (1f - pulse))),
        )
        Box(
            Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, style = MaterialTheme.typography.displayMedium)
        }
    }
}

/** Полноэкранное напоминание. */
@Composable
fun ReminderOverlay(
    icon: String,
    title: String,
    body: String,
    accent: Color = P.leaf,
    primaryLabel: String = "Хорошо",
    secondaryLabel: String? = "Позже",
    footnote: String? = null,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit = {},
) {
    ProductivnostTheme {
        OverlayScene(accent) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 34.dp, vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PulsingBadge(icon, accent)
                Spacer(Modifier.height(34.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmall,
                    color = P.text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = P.textDim,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(42.dp))
                PrimaryButton(primaryLabel, Modifier.fillMaxWidth(0.72f), color = accent, onClick = onPrimary)
                if (secondaryLabel != null) {
                    Spacer(Modifier.height(12.dp))
                    GhostButton(secondaryLabel, Modifier.fillMaxWidth(0.72f), onClick = onSecondary)
                }
                if (footnote != null) {
                    Spacer(Modifier.height(22.dp))
                    Text(footnote, style = MaterialTheme.typography.bodySmall, color = P.textFaint, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Экран-заслонка поверх запрещённого приложения. */
@Composable
fun BlockOverlay(
    title: String,
    body: String,
    appLabel: String,
    accent: Color = P.amber,
    timerText: String? = null,
    canExtend: Boolean = false,
    onHome: () -> Unit,
    onExtend: () -> Unit = {},
    onEndSession: (() -> Unit)? = null,
) {
    ProductivnostTheme {
        OverlayScene(accent) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 34.dp, vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PulsingBadge("🛡️", accent)
                Spacer(Modifier.height(30.dp))
                Text(title, style = MaterialTheme.typography.displaySmall, color = P.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(appLabel, style = MaterialTheme.typography.titleMedium, color = accent, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = P.textDim, textAlign = TextAlign.Center)
                if (timerText != null) {
                    Spacer(Modifier.height(20.dp))
                    Text(timerText, style = MaterialTheme.typography.displayMedium, color = P.text)
                }
                Spacer(Modifier.height(40.dp))
                PrimaryButton("Вернуться", Modifier.fillMaxWidth(0.72f), color = accent, onClick = onHome)
                if (canExtend) {
                    Spacer(Modifier.height(12.dp))
                    GhostButton("Продлить отжиманиями", Modifier.fillMaxWidth(0.72f), tint = P.leaf, onClick = onExtend)
                }
                if (onEndSession != null) {
                    Spacer(Modifier.height(12.dp))
                    GhostButton("Завершить сессию", Modifier.fillMaxWidth(0.72f), tint = P.textFaint, onClick = onEndSession)
                }
            }
        }
    }
}

/** Итог сессии: дерево выросло или засохло. */
@Composable
fun OutcomeOverlay(
    success: Boolean,
    title: String,
    body: String,
    plants: Int,
    onOk: () -> Unit,
) {
    val accent = if (success) P.leaf else P.rose
    ProductivnostTheme {
        OverlayScene(accent) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 34.dp, vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PulsingBadge(if (success) "🌳" else "🥀", accent)
                Spacer(Modifier.height(30.dp))
                Text(title, style = MaterialTheme.typography.displaySmall, color = P.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = P.textDim, textAlign = TextAlign.Center)
                if (success && plants > 0) {
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(plants.coerceAtMost(8)) {
                            Text("🌳", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
                PrimaryButton("В сад", Modifier.fillMaxWidth(0.72f), color = accent, onClick = onOk)
            }
        }
    }
}

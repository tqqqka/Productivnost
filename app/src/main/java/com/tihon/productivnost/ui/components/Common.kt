package com.tihon.productivnost.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P

@Composable
fun ScreenBackground(content: @Composable () -> Unit) {
    val e = LocalExtra.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(e.bgTop, e.bgBottom))),
    ) { content() }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val e = LocalExtra.current
    val interaction = remember0()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "cardScale")
    Column(
        modifier
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .background(if (highlight) e.cardHigh else e.card)
            .border(BorderStroke(1.dp, e.outline), MaterialTheme.shapes.large)
            .then(
                if (onClick != null) Modifier.clickable(interaction, null) { onClick() } else Modifier,
            )
            .padding(padding),
        content = content,
    )
}

@Composable
private fun remember0() = androidx.compose.runtime.remember { MutableInteractionSource() }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalExtra.current.faint,
        modifier = modifier.padding(start = 6.dp, bottom = 10.dp, top = 4.dp),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember0()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "btn")
    val bg by animateColorAsState(if (enabled) color else LocalExtra.current.cardHigh, tween(180), label = "btnBg")
    Box(
        modifier
            .scale(scale)
            .clip(CircleShape)
            .background(bg)
            .clickable(interaction, null, enabled = enabled) { onClick() }
            .padding(horizontal = 26.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color(0xFF061109) else LocalExtra.current.faint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalExtra.current.dim,
    onClick: () -> Unit,
) {
    val interaction = remember0()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "ghost")
    Box(
        modifier
            .scale(scale)
            .clip(CircleShape)
            .border(BorderStroke(1.dp, LocalExtra.current.outline), CircleShape)
            .clickable(interaction, null) { onClick() }
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** Кольцевой индикатор прогресса с плавной анимацией. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = LocalExtra.current.outline,
    stroke: Dp = 8.dp,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2f
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(
                color = track,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = w, cap = StrokeCap.Round),
            )
            if (animated > 0.001f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color, color)),
                    startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = w, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
fun StatTile(value: String, label: String, tint: Color = P.leaf, modifier: Modifier = Modifier) {
    val e = LocalExtra.current
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(e.card)
            .border(BorderStroke(1.dp, e.outline), MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tint)
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = e.faint)
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val e = LocalExtra.current
    val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.16f) else e.card, tween(200), label = "chipBg")
    val border by animateColorAsState(if (selected) accent.copy(alpha = 0.65f) else e.outline, tween(200), label = "chipBd")
    val fg by animateColorAsState(if (selected) accent else e.dim, tween(200), label = "chipFg")
    Box(
        modifier
            .clip(CircleShape)
            .background(bg)
            .border(BorderStroke(1.dp, border), CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onSelect: (Int) -> Unit,
) {
    val e = LocalExtra.current
    Row(
        modifier
            .clip(CircleShape)
            .background(e.card)
            .border(BorderStroke(1.dp, e.outline), CircleShape)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selectedIndex
            val bg by animateColorAsState(if (active) accent.copy(alpha = 0.18f) else Color.Transparent, tween(220), label = "segBg")
            val fg by animateColorAsState(if (active) accent else e.dim, tween(220), label = "segFg")
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
            }
        }
    }
}

@Composable
fun Bullet(icon: String, title: String, subtitle: String? = null, tint: Color = P.leaf) {
    val e = LocalExtra.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Text(icon, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = e.faint)
            }
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalExtra.current.outline)
            .alpha(0.7f),
    )
}

@Composable
fun ShapeBox(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    color: Color = LocalExtra.current.card,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(color),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun ContentColorText(text: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(text, style = style, color = LocalContentColor.current)
}

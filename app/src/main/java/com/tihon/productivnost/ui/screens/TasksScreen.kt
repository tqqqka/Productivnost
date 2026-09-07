package com.tihon.productivnost.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tihon.productivnost.core.HabitTask
import com.tihon.productivnost.core.Store
import com.tihon.productivnost.core.TaskRepeat
import com.tihon.productivnost.ui.ScreenHeader
import com.tihon.productivnost.ui.components.Chip
import com.tihon.productivnost.ui.components.Divider
import com.tihon.productivnost.ui.components.GhostButton
import com.tihon.productivnost.ui.components.PrimaryButton
import com.tihon.productivnost.ui.components.SectionTitle
import com.tihon.productivnost.ui.components.Segmented
import com.tihon.productivnost.ui.components.SoftCard
import com.tihon.productivnost.ui.theme.LocalExtra
import com.tihon.productivnost.ui.theme.P
import com.tihon.productivnost.util.Fmt

private val icons = listOf("🪥", "🚿", "💊", "📖", "🧹", "🥗", "🏃", "📞", "🧺", "💧")

@Composable
fun TasksScreen(onBack: () -> Unit) {
    val e = LocalExtra.current
    val tasks by Store.tasks.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newIcon by remember { mutableIntStateOf(0) }
    var newMinute by remember { mutableIntStateOf(20 * 60) }
    var newRepeat by remember { mutableIntStateOf(1) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                "Привычки",
                "Приходят на весь экран, когда ты в телефоне",
                onBack,
            )
        }

        items(tasks, key = { it.id }) { task ->
            TaskCard(
                task = task,
                expanded = expanded == task.id,
                onToggleExpand = { expanded = if (expanded == task.id) null else task.id },
            )
        }

        item { SectionTitle("Добавить своё") }
        item {
            SoftCard {
                if (!adding) {
                    GhostButton("Новое напоминание", Modifier.fillMaxWidth(), tint = P.leaf) { adding = true }
                } else {
                    SearchFieldLabelled("Название", newTitle) { newTitle = it }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        icons.forEachIndexed { i, ic ->
                            Chip(ic, newIcon == i) { newIcon = i }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Во сколько", style = MaterialTheme.typography.bodySmall, color = e.faint)
                    Spacer(Modifier.height(8.dp))
                    TimeChips(newMinute) { newMinute = it }
                    Spacer(Modifier.height(12.dp))
                    Segmented(listOf("Разово", "Каждый день", "Через день"), newRepeat) { newRepeat = it }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Добавить", Modifier.fillMaxWidth(), enabled = newTitle.isNotBlank()) {
                        Store.updateTasks { list ->
                            list + HabitTask(
                                id = "u${System.currentTimeMillis()}",
                                title = newTitle.trim(),
                                subtitle = "Своё напоминание",
                                icon = icons[newIcon],
                                repeat = TaskRepeat.entries[newRepeat],
                                minuteOfDay = newMinute,
                                anchorDay = Store.today(),
                            )
                        }
                        newTitle = ""; adding = false
                    }
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Отмена", Modifier.fillMaxWidth()) { adding = false }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: HabitTask, expanded: Boolean, onToggleExpand: () -> Unit) {
    val e = LocalExtra.current
    SoftCard(onClick = onToggleExpand) {
        ToggleRow(
            "${task.icon}  ${task.title}",
            "${repeatLabel(task.repeat)} · ${Fmt.timeOfDay(task.minuteOfDay)}",
            task.enabled,
        ) { v ->
            Store.updateTasks { list -> list.map { if (it.id == task.id) it.copy(enabled = v) else it } }
        }
        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(14.dp)); Divider(); Spacer(Modifier.height(14.dp))
                Text("Во сколько", style = MaterialTheme.typography.bodySmall, color = e.faint)
                Spacer(Modifier.height(8.dp))
                TimeChips(task.minuteOfDay) { m ->
                    Store.updateTasks { list -> list.map { if (it.id == task.id) it.copy(minuteOfDay = m) else it } }
                }
                Spacer(Modifier.height(14.dp))
                Text("Повтор", style = MaterialTheme.typography.bodySmall, color = e.faint)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    listOf("Разово", "Каждый день", "Через день"),
                    TaskRepeat.entries.indexOf(task.repeat),
                    accent = P.violet,
                ) { i ->
                    Store.updateTasks { list ->
                        list.map {
                            if (it.id == task.id) it.copy(repeat = TaskRepeat.entries[i], anchorDay = Store.today(), done = false)
                            else it
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                GhostButton("Удалить", Modifier.fillMaxWidth(), tint = P.rose) {
                    Store.updateTasks { list -> list.filterNot { it.id == task.id } }
                }
            }
        }
    }
}

@Composable
private fun TimeChips(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        7 * 60, 8 * 60, 12 * 60, 18 * 60, 19 * 60, 20 * 60,
        20 * 60 + 30, 21 * 60, 21 * 60 + 30, 22 * 60,
    )
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { m -> Chip(Fmt.timeOfDay(m), selected == m, accent = P.sky) { onSelect(m) } }
    }
}

@Composable
private fun SearchFieldLabelled(label: String, value: String, onChange: (String) -> Unit) {
    val e = LocalExtra.current
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = e.faint, modifier = Modifier.padding(bottom = 8.dp))
        SearchField(value, e.cardHigh, e.outline, e.faint, onChange)
    }
}

private fun repeatLabel(r: TaskRepeat) = when (r) {
    TaskRepeat.ONCE -> "Разово"
    TaskRepeat.DAILY -> "Каждый день"
    TaskRepeat.EVERY_OTHER_DAY -> "Через день"
}

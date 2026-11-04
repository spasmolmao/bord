package com.example.bord.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bord.domain.engine.BordAppState
import com.example.bord.domain.engine.TaskHubEngine
import com.example.bord.domain.model.DayLoadType
import com.example.bord.domain.model.MicroTask
import com.example.bord.domain.model.NodeType
import com.example.bord.domain.model.TaskNode
import com.example.bord.domain.model.UrgencyTier
import com.example.bord.platform.BordAlarmScheduler
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * BLOCK 3 SCREEN: Monthly "Occupied For What" Load Grid + 3-Zone Clump-Free Daily View.
 */
@Composable
fun CalendarScreen(
    appState: BordAppState,
    engine: TaskHubEngine
) {
    val context = LocalContext.current
    val selectedDate = appState.selectedDate
    var currentMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    val dayNodes = remember(appState.taskNodes, selectedDate) {
        engine.getNodesForDate(selectedDate)
    }

    // Split into the 3 Non-Cluttered Daily Zones
    val zone1AllDayErrands = dayNodes.filter { it.nodeType == NodeType.ERRAND_TASK && it.isAllDay }
    val zone2ProjectQuotas = dayNodes.filter { it.nodeType == NodeType.PROJECT_STUDY }
    val zone3TimedSlots = dayNodes.filter { !it.isAllDay }.sortedBy { it.startTimeMinutes ?: 0 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F131A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. MONTH HEADER & LOAD LEGEND ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171C26)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color.White)
                        }
                        Text(
                            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.US)} ${currentMonth.year}",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
                        }
                    }

                    // Workload Color Legend
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LegendDot("Errand", Color(0xFFF97316))
                        LegendDot("Prep/Study", Color(0xFF3B82F6))
                        LegendDot("Hybrid", Color(0xFFA855F7))
                        LegendDot("Cutoff", Color(0xFFEF4444))
                    }

                    Spacer(Modifier.height(8.dp))

                    // 14-Day / Month Compact Strip Grid
                    MonthGridStrip(
                        yearMonth = currentMonth,
                        selectedDate = selectedDate,
                        getLoadType = { engine.getDayLoadType(it) },
                        onSelectDate = { engine.selectDate(it) }
                    )
                }
            }
        }

        // --- 2. SELECTED DAY HEADER + STANDALONE CALENDAR TASK BUTTON ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AGENDA FOR ${selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase()}, $selectedDate",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "3-Zone Non-Clumped Daily View",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Button(onClick = { showAddTaskDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Task")
                }
            }
        }

        // --- ZONE 1: ALL-DAY / ANYTIME TODAY BANNER (ZERO FAKE CLOCK TIMES) ---
        item {
            ZoneHeader(
                title = "ZONE 1 • ALL-DAY / ANYTIME TODAY (${zone1AllDayErrands.size})",
                accent = Color(0xFFF97316)
            )
        }
        if (zone1AllDayErrands.isEmpty()) {
            item { EmptyZoneNote("No all-day errands on this date.") }
        } else {
            items(zone1AllDayErrands, key = { "z1_${it.id}" }) { node ->
                CalendarTaskCard(
                    node = node,
                    selectedDate = selectedDate,
                    engine = engine,
                    onSetAlarm = { BordAlarmScheduler.scheduleAlarmsForNode(context, node) }
                )
            }
        }

        // --- ZONE 2: TODAY'S PROJECT & HOUR BANK QUOTA ---
        item {
            ZoneHeader(
                title = "ZONE 2 • PROJECT & STUDY QUOTAS (${zone2ProjectQuotas.size})",
                accent = Color(0xFF3B82F6)
            )
        }
        if (zone2ProjectQuotas.isEmpty()) {
            item { EmptyZoneNote("No multi-day project or study chunks active on this date.") }
        } else {
            items(zone2ProjectQuotas, key = { "z2_${it.id}" }) { node ->
                CalendarTaskCard(
                    node = node,
                    selectedDate = selectedDate,
                    engine = engine,
                    onSetAlarm = { BordAlarmScheduler.scheduleAlarmsForNode(context, node) }
                )
            }
        }

        // --- ZONE 3: FIXED-CLOCK TIME SLOTS ---
        item {
            ZoneHeader(
                title = "ZONE 3 • FIXED-TIME SLOTS & BUFFER ALARMS (${zone3TimedSlots.size})",
                accent = Color(0xFFA855F7)
            )
        }
        if (zone3TimedSlots.isEmpty()) {
            item { EmptyZoneNote("No fixed-clock errands today.") }
        } else {
            items(zone3TimedSlots, key = { "z3_${it.id}" }) { node ->
                CalendarTaskCard(
                    node = node,
                    selectedDate = selectedDate,
                    engine = engine,
                    onSetAlarm = { BordAlarmScheduler.scheduleAlarmsForNode(context, node) }
                )
            }
        }
    }

    if (showAddTaskDialog) {
        AddStandaloneCalendarTaskDialog(
            selectedDate = selectedDate,
            onDismiss = { showAddTaskDialog = false },
            onCreate = { title, isAllDay, startMins, bufferMins, checklist ->
                // Calls shared initializeTask() with rootTrackId = NULL so it stays off the Miro Board!
                engine.initializeTask(
                    title = title,
                    nodeType = NodeType.ERRAND_TASK,
                    urgency = UrgencyTier.TODAY_MUST,
                    startDate = selectedDate,
                    endDate = selectedDate,
                    isAllDay = isAllDay,
                    startTimeMinutes = startMins,
                    bufferMinutes = bufferMins,
                    rootTrackId = null,
                    checklistItems = checklist
                )
                showAddTaskDialog = false
            }
        )
    }
}

@Composable
private fun MonthGridStrip(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    getLoadType: (LocalDate) -> DayLoadType,
    onSelectDate: (LocalDate) -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val rows = (1..daysInMonth).chunked(7)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { weekDays ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekDays.forEach { dayNum ->
                    val date = yearMonth.atDay(dayNum)
                    val isSelected = date == selectedDate
                    val loadColor = when (getLoadType(date)) {
                        DayLoadType.FREE -> Color(0xFF1E293B)
                        DayLoadType.ERRAND_HEAVY -> Color(0xFFF97316)
                        DayLoadType.PREP_HEAVY -> Color(0xFF3B82F6)
                        DayLoadType.HYBRID -> Color(0xFFA855F7)
                        DayLoadType.DEADLINE_CUT -> Color(0xFFEF4444)
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(loadColor.copy(alpha = if (isSelected) 0.9f else 0.35f))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectDate(date) }
                    ) {
                        Text(
                            text = dayNum.toString(),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
                repeat(7 - weekDays.size) {
                    Spacer(modifier = Modifier.size(38.dp))
                }
            }
        }
    }
}

@Composable
private fun CalendarTaskCard(
    node: TaskNode,
    selectedDate: LocalDate,
    engine: TaskHubEngine,
    onSetAlarm: () -> Unit
) {
    var newMicroText by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2232)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = { engine.toggleTaskNodeCompleted(node) }) {
                        Icon(
                            imageVector = if (node.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (node.isCompleted) Color(0xFF10B981) else Color(0xFF94A3B8)
                        )
                    }
                    Column {
                        Text(
                            text = node.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (node.isCompleted) TextDecoration.LineThrough else null
                        )
                        val subtitle = buildString {
                            if (node.isBoardLinked) append("📋 Linked to Miro Board  •  ")
                            if (node.isAllDay) {
                                append("All-Day (${node.startDate} .. ${node.endDate})")
                            } else {
                                val h = (node.startTimeMinutes ?: 540) / 60
                                val m = (node.startTimeMinutes ?: 540) % 60
                                append(String.format(Locale.US, "%02d:%02d (%dm • %dm buffer)", h, m, node.durationMinutes, node.bufferMinutes))
                            }
                        }
                        Text(subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }

                Row {
                    // Block 1 Micro-Feature: "Ran Late (+30m)" Button
                    IconButton(onClick = { engine.extendTaskDurationBy30Min(node) }) {
                        Icon(Icons.Default.MoreTime, contentDescription = "Extend +30m", tint = Color(0xFFF59E0B))
                    }
                    // Integrated Native Alarm Button
                    IconButton(onClick = onSetAlarm) {
                        Icon(Icons.Default.Alarm, contentDescription = "Set Alarm", tint = Color(0xFF38BDF8))
                    }
                }
            }

            // Pocket Checklist / Persistent Project Micro-Tasks
            if (node.microTasks.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                node.microTasks.forEach { item ->
                    val isPinnedToday = item.pinnedDate == selectedDate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { engine.toggleMicroTask(item, node) }
                        ) {
                            Checkbox(
                                checked = item.isCompleted,
                                onCheckedChange = { engine.toggleMicroTask(item, node) }
                            )
                            Text(
                                text = item.title,
                                color = if (item.isCompleted) Color(0xFF64748B) else Color(0xFFE2E8F0),
                                fontSize = 13.sp,
                                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null
                            )
                        }

                        // Pin / Unpin Micro-Task to Selected Day ("Big Chunk Until Sliced" Rule)
                        TextButton(
                            onClick = {
                                val nextPin = if (isPinnedToday) null else selectedDate
                                engine.pinMicroTaskToDate(item, node, nextPin)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = if (isPinnedToday) Color(0xFF38BDF8) else Color(0xFF64748B),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = item.pinnedDate?.toString() ?: "Chunk",
                                color = if (isPinnedToday) Color(0xFF38BDF8) else Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Quick add micro-checklist item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newMicroText,
                    onValueChange = { newMicroText = it },
                    placeholder = { Text("Add pocket/project step...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = {
                        if (newMicroText.isNotBlank()) {
                            engine.addMicroTaskToNode(node.id, newMicroText)
                            newMicroText = ""
                        }
                    }
                ) {
                    Text("+ Step")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color(0xFFCBD5E1), fontSize = 11.sp)
    }
}

@Composable
private fun ZoneHeader(title: String, accent: Color) {
    Text(
        text = title,
        color = accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun EmptyZoneNote(text: String) {
    Text(
        text = text,
        color = Color(0xFF64748B),
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun AddStandaloneCalendarTaskDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onCreate: (String, Boolean, Int?, Int, List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var isAllDay by remember { mutableStateOf(true) }
    var hourText by remember { mutableStateOf("14") }
    var minText by remember { mutableStateOf("00") }
    var bufferText by remember { mutableStateOf("30") }
    var checklistRaw by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Task on $selectedDate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task / Errand Name (e.g. Place A: Bank)") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAllDay, onCheckedChange = { isAllDay = it })
                    Text("All-Day / Anytime Today (Zero Clock Time)")
                }
                if (!isAllDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { hourText = it },
                            label = { Text("Hour (0-23)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minText,
                            onValueChange = { minText = it },
                            label = { Text("Min") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = bufferText,
                        onValueChange = { bufferText = it },
                        label = { Text("Stage-1 Buffer Alarm (mins before)") }
                    )
                }
                OutlinedTextField(
                    value = checklistRaw,
                    onValueChange = { checklistRaw = it },
                    label = { Text("Pocket Checklist (comma separated)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val h = (hourText.toIntOrNull() ?: 14).coerceIn(0, 23)
                        val m = (minText.toIntOrNull() ?: 0).coerceIn(0, 59)
                        val buf = bufferText.toIntOrNull() ?: 30
                        val items = checklistRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onCreate(title, isAllDay, if (isAllDay) null else (h * 60 + m), buf, items)
                    }
                }
            ) {
                Text("Save to Calendar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
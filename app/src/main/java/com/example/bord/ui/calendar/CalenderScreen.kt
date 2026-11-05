package com.example.bord.ui.calendar

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bord.domain.engine.BordAppState
import com.example.bord.domain.engine.TaskHubEngine
import com.example.bord.domain.model.DayLoadType
import com.example.bord.domain.model.NodeType
import com.example.bord.domain.model.TaskNode
import com.example.bord.domain.model.UrgencyTier
import com.example.bord.platform.BordAlarmScheduler
import com.example.bord.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * BLOCK 3 SCREEN (Minimalist Black & White Edition):
 * High-contrast Month Occupancy Grid + 3-Zone Clump-Free Daily Agenda + Task/Step Deletion.
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

    val zone1AllDayErrands = dayNodes.filter { it.nodeType == NodeType.ERRAND_TASK && it.isAllDay }
    val zone2ProjectQuotas = dayNodes.filter { it.nodeType == NodeType.PROJECT_STUDY }
    val zone3TimedSlots = dayNodes.filter { !it.isAllDay }.sortedBy { it.startTimeMinutes ?: 0 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BordPalette.BgPureBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. MINIMALIST MONOCHROME MONTH MATRIX ---
        item {
            MonoSectionTitle(
                title = "01 / OCCUPANCY MATRIX",
                rightText = "${currentMonth.month.getDisplayName(TextStyle.SHORT, Locale.US).uppercase()} ${currentMonth.year}"
            )
            MonoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = BordPalette.TextPrimary)
                    }
                    Text(
                        text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.US).uppercase()} ${currentMonth.year}",
                        color = BordPalette.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BordPalette.TextPrimary)
                    }
                }

                Spacer(Modifier.height(10.dp))

                MonthGridStrip(
                    yearMonth = currentMonth,
                    selectedDate = selectedDate,
                    getLoadType = { engine.getDayLoadType(it) },
                    onSelectDate = { engine.selectDate(it) }
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("□ FREE", color = BordPalette.TextDim, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    Text("▤ PREP/ERRAND", color = BordPalette.TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    Text("■ HYBRID", color = BordPalette.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    Text("● CUTOFF", color = BordPalette.SignalAlert, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
        }

        // --- 2. SELECTED DAY AGENDA HEADER ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase()} // $selectedDate",
                        color = BordPalette.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "3-ZONE DAILY EXECUTION VIEW",
                        color = BordPalette.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
                MonoButton(
                    text = "+ Task",
                    onClick = { showAddTaskDialog = true },
                    inverted = true
                )
            }
        }

        // --- ZONE 1: ALL-DAY / ANYTIME TODAY ---
        item {
            MonoSectionTitle(
                title = "ZONE 01 // ALL-DAY ERRANDS",
                rightText = "${zone1AllDayErrands.size} ACTIVE"
            )
        }
        if (zone1AllDayErrands.isEmpty()) {
            item { EmptyZoneNote("NO ALL-DAY ERRANDS SCHEDULED.") }
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

        // --- ZONE 2: PROJECT & STUDY QUOTAS ---
        item {
            MonoSectionTitle(
                title = "ZONE 02 // PROJECT & STUDY CHUNKS",
                rightText = "${zone2ProjectQuotas.size} ACTIVE"
            )
        }
        if (zone2ProjectQuotas.isEmpty()) {
            item { EmptyZoneNote("NO STUDY OR PROJECT CHUNKS ON THIS DATE.") }
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

        // --- ZONE 3: FIXED-CLOCK SLOTS ---
        item {
            MonoSectionTitle(
                title = "ZONE 03 // FIXED-CLOCK & BUFFER ALARMS",
                rightText = "${zone3TimedSlots.size} ACTIVE"
            )
        }
        if (zone3TimedSlots.isEmpty()) {
            item { EmptyZoneNote("NO FIXED-TIME SLOTS TODAY.") }
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
                    val load = getLoadType(date)

                    val cellBg = when {
                        isSelected -> BordPalette.SignalWhite
                        load == DayLoadType.DEADLINE_CUT -> BordPalette.SignalAlert.copy(alpha = 0.22f)
                        load == DayLoadType.HYBRID -> Color(0xFF27272A)
                        load == DayLoadType.PREP_HEAVY || load == DayLoadType.ERRAND_HEAVY -> Color(0xFF18181B)
                        else -> BordPalette.BgPureBlack
                    }
                    val borderCol = when {
                        isSelected -> BordPalette.SignalWhite
                        load == DayLoadType.DEADLINE_CUT -> BordPalette.SignalAlert
                        load != DayLoadType.FREE -> BordPalette.TextSecondary
                        else -> BordPalette.BorderHairline
                    }
                    val textCol = if (isSelected) BordPalette.BgPureBlack else BordPalette.TextPrimary

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(cellBg)
                            .border(1.dp, borderCol, RoundedCornerShape(4.dp))
                            .clickable { onSelectDate(date) }
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d", dayNum),
                            color = textCol,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
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

    MonoCard(
        isHighlighted = !node.isCompleted && node.urgency == UrgencyTier.CRITICAL,
        modifier = Modifier.animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = { engine.toggleTaskNodeCompleted(node) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (node.isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (node.isCompleted) BordPalette.SignalWhite else BordPalette.TextSecondary
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = node.title.uppercase(),
                        color = if (node.isCompleted) BordPalette.TextDim else BordPalette.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textDecoration = if (node.isCompleted) TextDecoration.LineThrough else null
                    )
                    val meta = buildString {
                        if (node.isBoardLinked) append("[BOARD CHUNK] ")
                        if (node.isAllDay) {
                            append("${node.startDate} -> ${node.endDate}")
                        } else {
                            val h = (node.startTimeMinutes ?: 540) / 60
                            val m = (node.startTimeMinutes ?: 540) % 60
                            append(String.format(Locale.US, "%02d:%02d (%dM • %dM BUF)", h, m, node.durationMinutes, node.bufferMinutes))
                        }
                    }
                    Text(
                        text = meta,
                        color = BordPalette.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { engine.extendTaskDurationBy30Min(node) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreTime, contentDescription = "+30m", tint = BordPalette.TextSecondary)
                }
                IconButton(onClick = onSetAlarm, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Alarm, contentDescription = "Alarm", tint = BordPalette.TextPrimary)
                }
                IconButton(onClick = { engine.deleteTaskNode(node.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = BordPalette.TextDim)
                }
            }
        }

        // Micro-Checklist items with Pin & Delete controls
        if (node.microTasks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BordPalette.BorderHairline, thickness = 1.dp)
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
                        Icon(
                            imageVector = if (item.isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (item.isCompleted) BordPalette.TextDim else BordPalette.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item.title,
                            color = if (item.isCompleted) BordPalette.TextDim else BordPalette.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isPinnedToday) "[PINNED TODAY]" else item.pinnedDate?.let { "[$it]" } ?: "[CHUNK]",
                            color = if (isPinnedToday) BordPalette.SignalWhite else BordPalette.TextDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    val nextPin = if (isPinnedToday) null else selectedDate
                                    engine.pinMicroTaskToDate(item, node, nextPin)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        IconButton(
                            onClick = { engine.deleteMicroTask(item.id) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Step",
                                tint = BordPalette.TextDim,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Add Micro-Step Row
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newMicroText,
                onValueChange = { newMicroText = it },
                placeholder = { Text("+ Add checklist step...", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            MonoButton(
                text = "Add",
                onClick = {
                    if (newMicroText.isNotBlank()) {
                        engine.addMicroTaskToNode(node.id, newMicroText)
                        newMicroText = ""
                    }
                },
                inverted = false
            )
        }
    }
}

@Composable
private fun EmptyZoneNote(text: String) {
    Text(
        text = text,
        color = BordPalette.TextDim,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
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
        title = { Text("NEW TASK // $selectedDate", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task / Errand Name") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAllDay, onCheckedChange = { isAllDay = it })
                    Text("All-Day / Anytime Today", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
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
                        label = { Text("Buffer Alarm (mins before)") }
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
            MonoButton(
                text = "Save",
                onClick = {
                    if (title.isNotBlank()) {
                        val h = (hourText.toIntOrNull() ?: 14).coerceIn(0, 23)
                        val m = (minText.toIntOrNull() ?: 0).coerceIn(0, 59)
                        val buf = bufferText.toIntOrNull() ?: 30
                        val items = checklistRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onCreate(title, isAllDay, if (isAllDay) null else (h * 60 + m), buf, items)
                    }
                }
            )
        },
        dismissButton = {
            MonoButton(text = "Cancel", onClick = onDismiss, inverted = false)
        }
    )
}
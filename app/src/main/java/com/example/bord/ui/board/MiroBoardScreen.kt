package com.example.bord.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bord.domain.engine.BordAppState
import com.example.bord.domain.engine.TaskHubEngine
import com.example.bord.domain.model.BoardBorderStatus
import com.example.bord.domain.model.NodeType
import com.example.bord.domain.model.RootTrack
import com.example.bord.domain.model.TaskNode
import com.example.bord.domain.model.UrgencyTier
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * BLOCK 4 SCREEN: 2D Pan-and-Zoom Miro-Style Visual Board
 * - Shows Tier 1 RootTracks connected by curved lines to Tier 2 TaskNodes
 * - Highlights IDLE (unscheduled) nodes with a warning border
 * - Lets you tap any node to map its date chunk & hours straight onto the Calendar
 */
@Composable
fun MiroBoardScreen(
    appState: BordAppState,
    engine: TaskHubEngine
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedNodeForMapping by remember { mutableStateOf<TaskNode?>(null) }
    var parentTrackForNewChild by remember { mutableStateOf<RootTrack?>(null) }

    val boardNodes = remember(appState.taskNodes) {
        // Strictly display Board-linked nodes (standalone calendar errands never clutter the board!)
        appState.taskNodes.filter { it.isBoardLinked }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.55f, 2.0f)
                    panOffset += pan
                }
            }
    ) {
        // --- 1. INFINITE 2D TRANSFORMABLE BOARD LAYER ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = panOffset.x,
                    translationY = panOffset.y
                )
        ) {
            val density = LocalDensity.current.density

            // Draw Background Dot Grid + Curved Connector Lines between RootTrack and Child TaskNodes
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 48.dp.toPx()
                for (x in 0..size.width.toInt() step step.toInt().coerceAtLeast(24)) {
                    for (y in 0..size.height.toInt() step step.toInt().coerceAtLeast(24)) {
                        drawCircle(
                            color = Color(0xFF1E293B),
                            radius = 2f,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                    }
                }

                // Draw Bezier Connectors from each RootTrack to its Child TaskNodes
                appState.rootTracks.forEach { track ->
                    val startX = (track.boardX + 210f) * density
                    val startY = (track.boardY + 55f) * density

                    boardNodes.filter { it.rootTrackId == track.id }.forEach { child ->
                        val endX = child.boardX * density
                        val endY = (child.boardY + 55f) * density

                        val path = Path().apply {
                            moveTo(startX, startY)
                            cubicTo(
                                (startX + endX) / 2f, startY,
                                (startX + endX) / 2f, endY,
                                endX, endY
                            )
                        }
                        val status = child.computeBoardStatus()
                        val lineColor = when (status) {
                            BoardBorderStatus.IDLE_UNSCHEDULED -> Color(0xFFF59E0B)
                            BoardBorderStatus.SCHEDULED_ACTIVE -> Color(0xFF3B82F6)
                            BoardBorderStatus.BEHIND_PACE -> Color(0xFFEF4444)
                            BoardBorderStatus.COMPLETED -> Color(0xFF10B981)
                        }
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(
                                width = 5f,
                                pathEffect = if (status == BoardBorderStatus.IDLE_UNSCHEDULED) {
                                    PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                                } else null
                            )
                        )
                    }
                }
            }

            // Render Tier 1 RootTrack Cards
            appState.rootTracks.forEach { track ->
                RootTrackBoardCard(
                    track = track,
                    onDrag = { dx, dy ->
                        engine.moveBoardItem(true, track.id, track.boardX + dx, track.boardY + dy)
                    },
                    onAddChildNode = { parentTrackForNewChild = track }
                )
            }

            // Render Tier 2 Child PlanNode Cards
            boardNodes.forEach { node ->
                ChildPlanNodeBoardCard(
                    node = node,
                    onDrag = { dx, dy ->
                        engine.moveBoardItem(false, node.id, node.boardX + dx, node.boardY + dy)
                    },
                    onClickMap = { selectedNodeForMapping = node }
                )
            }
        }

        // --- 2. HUD OVERLAY (Legend & Reset View) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xCC161B26),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Drag boxes • Pinch to zoom • Tap a Child Node to Map to Calendar",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            FilledTonalIconButton(
                onClick = {
                    scale = 1f
                    panOffset = Offset.Zero
                }
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset Camera")
            }
        }
    }

    // Drawer/Dialog to Map an Idle or Existing Board Node onto the Calendar
    selectedNodeForMapping?.let { node ->
        MapNodeToCalendarDialog(
            node = node,
            onDismiss = { selectedNodeForMapping = null },
            onSaveMapping = { start, end, hrs ->
                engine.mapBoardNodeToCalendar(node.id, start, end, hrs)
                selectedNodeForMapping = null
            }
        )
    }

    // Dialog to branch a new Child Node off a RootTrack
    parentTrackForNewChild?.let { parent ->
        CreateBoardChildNodeDialog(
            parentTrack = parent,
            onDismiss = { parentTrackForNewChild = null },
            onCreate = { title, spanDays, hours, checklist ->
                val start = if (spanDays > 0) LocalDate.now() else null
                val end = if (spanDays > 0) LocalDate.now().plusDays((spanDays - 1).toLong()) else null
                engine.initializeTask(
                    title = title,
                    nodeType = NodeType.PROJECT_STUDY,
                    urgency = UrgencyTier.TODAY_MUST,
                    startDate = start,
                    endDate = end,
                    isAllDay = true,
                    allocatedHours = hours,
                    rootTrackId = parent.id,
                    checklistItems = checklist,
                    boardX = parent.boardX + 340f,
                    boardY = parent.boardY + 40f
                )
                parentTrackForNewChild = null
            }
        )
    }
}

@Composable
private fun RootTrackBoardCard(
    track: RootTrack,
    onDrag: (Float, Float) -> Unit,
    onAddChildNode: () -> Unit
) {
    val density = LocalDensity.current.density
    var dragX by remember(track.boardX) { mutableFloatStateOf(track.boardX) }
    var dragY by remember(track.boardY) { mutableFloatStateOf(track.boardY) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .offset {
                IntOffset(
                    (dragX * density).roundToInt(),
                    (dragY * density).roundToInt()
                )
            }
            .width(215.dp)
            .border(2.dp, Color(0xFF3B82F6), RoundedCornerShape(14.dp))
            .pointerInput(track.id) {
                detectDragGestures(
                    onDragEnd = { onDrag(dragX - track.boardX, dragY - track.boardY) }
                ) { change, dragAmount ->
                    change.consume()
                    dragX = (dragX + dragAmount.x / density).coerceAtLeast(0f)
                    dragY = (dragY + dragAmount.y / density).coerceAtLeast(0f)
                }
            }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("TIER 1 • ROOT TRACK", color = Color(0xFF60A5FA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(track.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${String.format(Locale.US, "%.1f", track.investedHours)} / ${String.format(Locale.US, "%.1f", track.targetHours)} hrs invested",
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp
            )
            Text(
                text = "Pace: ${String.format(Locale.US, "%.1f", track.requiredDailyPace())}h/day",
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onAddChildNode,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Branch Node", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChildPlanNodeBoardCard(
    node: TaskNode,
    onDrag: (Float, Float) -> Unit,
    onClickMap: () -> Unit
) {
    val density = LocalDensity.current.density
    var dragX by remember(node.boardX) { mutableFloatStateOf(node.boardX) }
    var dragY by remember(node.boardY) { mutableFloatStateOf(node.boardY) }

    val status = node.computeBoardStatus()
    val borderColor = when (status) {
        BoardBorderStatus.IDLE_UNSCHEDULED -> Color(0xFFF59E0B) // Anti-Idle Warning!
        BoardBorderStatus.SCHEDULED_ACTIVE -> Color(0xFF3B82F6)
        BoardBorderStatus.BEHIND_PACE -> Color(0xFFEF4444)
        BoardBorderStatus.COMPLETED -> Color(0xFF10B981)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C28)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .offset {
                IntOffset(
                    (dragX * density).roundToInt(),
                    (dragY * density).roundToInt()
                )
            }
            .width(235.dp)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClickMap() }
            .pointerInput(node.id) {
                detectDragGestures(
                    onDragEnd = { onDrag(dragX - node.boardX, dragY - node.boardY) }
                ) { change, dragAmount ->
                    change.consume()
                    dragX = (dragX + dragAmount.x / density).coerceAtLeast(0f)
                    dragY = (dragY + dragAmount.y / density).coerceAtLeast(0f)
                }
            }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (status) {
                        BoardBorderStatus.IDLE_UNSCHEDULED -> "⚠️ IDLE (NO DATE)"
                        BoardBorderStatus.SCHEDULED_ACTIVE -> "📅 SCHEDULED CHUNK"
                        BoardBorderStatus.BEHIND_PACE -> "🚨 OVERDUE CHUNK"
                        BoardBorderStatus.COMPLETED -> "✅ COMPLETED"
                    },
                    color = borderColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${(node.checklistProgressFraction * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(node.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

            val dateLabel = if (node.startDate != null && node.endDate != null) {
                "${node.startDate} → ${node.endDate}"
            } else {
                "Tap card to assign Calendar dates"
            }
            Text(dateLabel, color = Color(0xFF94A3B8), fontSize = 11.sp)

            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { node.checklistProgressFraction },
                modifier = Modifier.fillMaxWidth(),
                color = borderColor,
                trackColor = Color(0xFF262F40)
            )

            Spacer(Modifier.height(6.dp))
            val doneCount = node.microTasks.count { it.isCompleted }
            Text(
                text = "Checklist: $doneCount/${node.microTasks.size}  •  Hours: ${String.format(Locale.US, "%.1f", node.investedHours)}/${String.format(Locale.US, "%.1f", node.allocatedHours)}h",
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MapNodeToCalendarDialog(
    node: TaskNode,
    onDismiss: () -> Unit,
    onSaveMapping: (LocalDate, LocalDate, Double) -> Unit
) {
    var startOffsetDays by remember { mutableStateOf("0") }
    var durationDays by remember { mutableStateOf("5") }
    var hoursText by remember { mutableStateOf(node.allocatedHours.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
        title = { Text("Map '${node.title}' to Calendar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Sets the 'One Big Chunk' span on your Calendar. Micro-tasks will ride this whole chunk unless pinned.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                OutlinedTextField(
                    value = startOffsetDays,
                    onValueChange = { startOffsetDays = it },
                    label = { Text("Start in how many days from today? (0 = Today)") }
                )
                OutlinedTextField(
                    value = durationDays,
                    onValueChange = { durationDays = it },
                    label = { Text("Chunk Duration in Days (e.g. 5)") }
                )
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text("Allocated Hours from Hour Bank") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val offset = startOffsetDays.toLongOrNull() ?: 0L
                    val span = (durationDays.toLongOrNull() ?: 1L).coerceAtLeast(1L)
                    val hrs = hoursText.toDoubleOrNull() ?: node.allocatedHours
                    val start = LocalDate.now().plusDays(offset)
                    val end = start.plusDays(span - 1L)
                    onSaveMapping(start, end, hrs)
                }
            ) {
                Text("Lock Into Calendar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateBoardChildNodeDialog(
    parentTrack: RootTrack,
    onDismiss: () -> Unit,
    onCreate: (String, Int, Double, List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var spanDaysText by remember { mutableStateOf("4") }
    var hoursText by remember { mutableStateOf("8") }
    var checklistRaw by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Branch Node under '${parentTrack.title}'") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Milestone / Project Node Title") }
                )
                OutlinedTextField(
                    value = spanDaysText,
                    onValueChange = { spanDaysText = it },
                    label = { Text("Days to Occupy on Calendar (0 = Keep Idle)") }
                )
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text("Allocated Hours (e.g. 8.0)") }
                )
                OutlinedTextField(
                    value = checklistRaw,
                    onValueChange = { checklistRaw = it },
                    label = { Text("Micro-Checklist (comma separated)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val days = spanDaysText.toIntOrNull() ?: 0
                        val hrs = hoursText.toDoubleOrNull() ?: 4.0
                        val items = checklistRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onCreate(title, days, hrs, items)
                    }
                }
            ) {
                Text("Add to Board")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
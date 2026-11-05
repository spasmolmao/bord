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
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.text.font.FontFamily
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
import com.example.bord.ui.theme.*
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * BLOCK 4 SCREEN (Minimalist Black & White Edition):
 * 2D Pan-and-Zoom Node Canvas with Monochrome Bezier Connectors & Node Inspector Drawer.
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
        appState.taskNodes.filter { it.isBoardLinked }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BordPalette.BgPureBlack)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.55f, 2.0f)
                    panOffset += pan
                }
            }
    ) {
        // --- 1. 2D TRANSFORMABLE CANVAS LAYER ---
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

            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 44.dp.toPx()
                for (x in 0..size.width.toInt() step step.toInt().coerceAtLeast(24)) {
                    for (y in 0..size.height.toInt() step step.toInt().coerceAtLeast(24)) {
                        drawCircle(
                            color = Color(0xFF1F1F23),
                            radius = 1.5f,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                    }
                }

                // Draw Monochrome Bezier Curves from RootTrack -> Child TaskNodes
                appState.rootTracks.forEach { track ->
                    val startX = (track.boardX + 215f) * density
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
                            BoardBorderStatus.IDLE_UNSCHEDULED -> BordPalette.TextDim
                            BoardBorderStatus.SCHEDULED_ACTIVE -> BordPalette.SignalWhite
                            BoardBorderStatus.BEHIND_PACE -> BordPalette.SignalAlert
                            BoardBorderStatus.COMPLETED -> BordPalette.TextSecondary
                        }
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(
                                width = 3.5f,
                                pathEffect = if (status == BoardBorderStatus.IDLE_UNSCHEDULED) {
                                    PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                } else null
                            )
                        )
                    }
                }
            }

            // Tier 1 Root Track Nodes
            appState.rootTracks.forEach { track ->
                RootTrackBoardCard(
                    track = track,
                    onDrag = { dx, dy ->
                        engine.moveBoardItem(true, track.id, track.boardX + dx, track.boardY + dy)
                    },
                    onAddChildNode = { parentTrackForNewChild = track }
                )
            }

            // Tier 2 Child Plan Nodes
            boardNodes.forEach { node ->
                ChildPlanNodeBoardCard(
                    node = node,
                    onDrag = { dx, dy ->
                        engine.moveBoardItem(false, node.id, node.boardX + dx, node.boardY + dy)
                    },
                    onClickInspect = { selectedNodeForMapping = node }
                )
            }
        }

        // --- 2. MINIMALIST TOP HUD BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(BordPalette.SurfaceCard)
                    .border(1.dp, BordPalette.BorderHairline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "DRAG NODES // TAP CHILD NODE TO MAP OR START TIMER",
                    color = BordPalette.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = {
                    scale = 1f
                    panOffset = Offset.Zero
                },
                modifier = Modifier
                    .size(34.dp)
                    .background(BordPalette.SurfaceCard, RoundedCornerShape(4.dp))
                    .border(1.dp, BordPalette.BorderHairline, RoundedCornerShape(4.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Center Canvas",
                    tint = BordPalette.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // Node Inspector & Calendar Mapper Dialog
    selectedNodeForMapping?.let { node ->
        MapNodeToCalendarDialog(
            node = node,
            onDismiss = { selectedNodeForMapping = null },
            onSaveMapping = { start, end, hrs ->
                engine.mapBoardNodeToCalendar(node.id, start, end, hrs)
                selectedNodeForMapping = null
            },
            onStartTimerForNode = {
                engine.selectTimerNode(node.id)
                if (!appState.isTimerRunning) engine.toggleGlobalTimer()
                selectedNodeForMapping = null
            },
            onDeleteNode = {
                engine.deleteTaskNode(node.id)
                selectedNodeForMapping = null
            }
        )
    }

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
                    boardX = parent.boardX + 320f,
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

    Surface(
        color = BordPalette.SurfaceCard,
        shape = RoundedCornerShape(BordPalette.CornerRadius),
        modifier = Modifier
            .offset {
                IntOffset(
                    (dragX * density).roundToInt(),
                    (dragY * density).roundToInt()
                )
            }
            .width(215.dp)
            .border(1.5.dp, BordPalette.SignalWhite, RoundedCornerShape(BordPalette.CornerRadius))
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
            Text(
                text = "TIER 01 // ROOT TRACK",
                color = BordPalette.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.title.uppercase(),
                color = BordPalette.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "HOURS: ${String.format(Locale.US, "%.1f", track.investedHours)} / ${String.format(Locale.US, "%.1f", track.targetHours)}H",
                color = BordPalette.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                text = "PACE: ${String.format(Locale.US, "%.1f", track.requiredDailyPace())}H / DAY",
                color = BordPalette.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(10.dp))
            MonoButton(
                text = "+ Branch Node",
                onClick = onAddChildNode,
                modifier = Modifier.fillMaxWidth(),
                inverted = true
            )
        }
    }
}

@Composable
private fun ChildPlanNodeBoardCard(
    node: TaskNode,
    onDrag: (Float, Float) -> Unit,
    onClickInspect: () -> Unit
) {
    val density = LocalDensity.current.density
    var dragX by remember(node.boardX) { mutableFloatStateOf(node.boardX) }
    var dragY by remember(node.boardY) { mutableFloatStateOf(node.boardY) }

    val status = node.computeBoardStatus()
    val borderColor = when (status) {
        BoardBorderStatus.IDLE_UNSCHEDULED -> BordPalette.TextDim
        BoardBorderStatus.SCHEDULED_ACTIVE -> BordPalette.SignalWhite
        BoardBorderStatus.BEHIND_PACE -> BordPalette.SignalAlert
        BoardBorderStatus.COMPLETED -> BordPalette.TextSecondary
    }

    Surface(
        color = BordPalette.SurfaceCard,
        shape = RoundedCornerShape(BordPalette.CornerRadius),
        modifier = Modifier
            .offset {
                IntOffset(
                    (dragX * density).roundToInt(),
                    (dragY * density).roundToInt()
                )
            }
            .width(235.dp)
            .border(
                width = if (status == BoardBorderStatus.SCHEDULED_ACTIVE || status == BoardBorderStatus.BEHIND_PACE) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(BordPalette.CornerRadius)
            )
            .clickable { onClickInspect() }
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonoBadge(
                    text = when (status) {
                        BoardBorderStatus.IDLE_UNSCHEDULED -> "IDLE // UNMAPPED"
                        BoardBorderStatus.SCHEDULED_ACTIVE -> "ACTIVE CHUNK"
                        BoardBorderStatus.BEHIND_PACE -> "OVERDUE"
                        BoardBorderStatus.COMPLETED -> "DONE"
                    },
                    isAlert = status == BoardBorderStatus.BEHIND_PACE,
                    inverted = status == BoardBorderStatus.SCHEDULED_ACTIVE
                )
                Text(
                    text = "${(node.checklistProgressFraction * 100).toInt()}%",
                    color = BordPalette.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = node.title.uppercase(),
                color = BordPalette.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )

            val dateLabel = if (node.startDate != null && node.endDate != null) {
                "${node.startDate} -> ${node.endDate}"
            } else {
                "[TAP TO ASSIGN DATES]"
            }
            Text(
                text = dateLabel,
                color = BordPalette.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(BordPalette.SurfaceElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(node.checklistProgressFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(BordPalette.SignalWhite)
                )
            }

            Spacer(Modifier.height(6.dp))
            val doneCount = node.microTasks.count { it.isCompleted }
            Text(
                text = "STEPS: $doneCount/${node.microTasks.size}  //  HRS: ${String.format(Locale.US, "%.1f", node.investedHours)}/${String.format(Locale.US, "%.1f", node.allocatedHours)}H",
                color = BordPalette.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun MapNodeToCalendarDialog(
    node: TaskNode,
    onDismiss: () -> Unit,
    onSaveMapping: (LocalDate, LocalDate, Double) -> Unit,
    onStartTimerForNode: () -> Unit,
    onDeleteNode: () -> Unit
) {
    var startOffsetDays by remember { mutableStateOf("0") }
    var durationDays by remember { mutableStateOf("5") }
    var hoursText by remember { mutableStateOf(node.allocatedHours.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.title.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDeleteNode) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Node", tint = BordPalette.SignalAlert)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoButton(
                    text = "▶ Start Focus Timer on This Node",
                    onClick = onStartTimerForNode,
                    modifier = Modifier.fillMaxWidth(),
                    inverted = false
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "MAP DATE CHUNK TO CALENDAR:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BordPalette.TextSecondary
                )
                OutlinedTextField(
                    value = startOffsetDays,
                    onValueChange = { startOffsetDays = it },
                    label = { Text("Start Offset Days (0 = Today)") }
                )
                OutlinedTextField(
                    value = durationDays,
                    onValueChange = { durationDays = it },
                    label = { Text("Duration in Days (e.g. 5)") }
                )
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text("Allocated Hours") }
                )
            }
        },
        confirmButton = {
            MonoButton(
                text = "Lock Dates",
                onClick = {
                    val offset = startOffsetDays.toLongOrNull() ?: 0L
                    val span = (durationDays.toLongOrNull() ?: 1L).coerceAtLeast(1L)
                    val hrs = hoursText.toDoubleOrNull() ?: node.allocatedHours
                    val start = LocalDate.now().plusDays(offset)
                    val end = start.plusDays(span - 1L)
                    onSaveMapping(start, end, hrs)
                }
            )
        },
        dismissButton = {
            MonoButton(text = "Close", onClick = onDismiss, inverted = false)
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
        title = { Text("BRANCH NODE // ${parentTrack.title.uppercase()}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Milestone / Node Title") }
                )
                OutlinedTextField(
                    value = spanDaysText,
                    onValueChange = { spanDaysText = it },
                    label = { Text("Calendar Span Days (0 = Keep Idle)") }
                )
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text("Allocated Hours (e.g. 8.0)") }
                )
                OutlinedTextField(
                    value = checklistRaw,
                    onValueChange = { checklistRaw = it },
                    label = { Text("Checklist Steps (comma separated)") }
                )
            }
        },
        confirmButton = {
            MonoButton(
                text = "Create Node",
                onClick = {
                    if (title.isNotBlank()) {
                        val days = spanDaysText.toIntOrNull() ?: 0
                        val hrs = hoursText.toDoubleOrNull() ?: 4.0
                        val items = checklistRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onCreate(title, days, hrs, items)
                    }
                }
            )
        },
        dismissButton = {
            MonoButton(text = "Cancel", onClick = onDismiss, inverted = false)
        }
    )
}
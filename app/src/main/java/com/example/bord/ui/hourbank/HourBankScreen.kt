package com.example.bord.ui.hourbank

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bord.domain.engine.BordAppState
import com.example.bord.domain.engine.TaskHubEngine
import com.example.bord.domain.model.NodeType
import com.example.bord.domain.model.RootTrack
import com.example.bord.domain.model.TaskNode
import com.example.bord.domain.model.UserMomentum
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

/**
 * Reusable Top Bar showing Points (including Negative Debt), Streak, and Momentum Multiplier.
 */
@Composable
fun MomentumHeaderBar(momentum: UserMomentum) {
    val isNegative = momentum.points < 0
    val bgColor = if (isNegative) Color(0xFF3B1219) else Color(0xFF161B26)
    val accentColor = if (isNegative) Color(0xFFEF4444) else Color(0xFF10B981)

    Surface(
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isNegative) "MOMENTUM DEBT (PENALTY)" else "MOMENTUM & STREAK",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${momentum.points} PTS  •  ${String.format(Locale.US, "%.2fx", momentum.multiplier)} Multiplier",
                    color = accentColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Surface(
                color = accentColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "🔥 ${momentum.currentStreakDays}d Streak",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * BLOCK 2 SCREEN: Placement Prep "Hours Invested" Calculator, Ghost Progress Bar, and Live Stopwatch.
 */
@Composable
fun HourBankScreen(
    appState: BordAppState,
    engine: TaskHubEngine
) {
    val studyNodes = remember(appState.taskNodes) {
        appState.taskNodes.filter { it.nodeType == NodeType.PROJECT_STUDY }
    }

    var activeNodeId by remember { mutableStateOf(studyNodes.firstOrNull()?.id) }
    var isRunning by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showNewTrackDialog by remember { mutableStateOf(false) }
    var manualHoursText by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }

    // Live 1-second Stopwatch coroutine loop
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000L)
            elapsedSeconds += 1
        }
    }

    val selectedNode: TaskNode? = studyNodes.find { it.id == activeNodeId } ?: studyNodes.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F131A))
            .padding(16.dp)
    ) {
        // --- 1. DEFAULT LIVE FOCUS STOPWATCH CARD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2232)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFF60A5FA))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "LIVE FOCUS STOPWATCH (DEFAULT)",
                            color = Color(0xFF93C5FD),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = { showManualInput = !showManualInput }) {
                        Text(
                            text = if (showManualInput) "Hide Manual Log" else "+ Manual Hours",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    text = selectedNode?.title ?: "Create a Project/Study Node to track hours",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                val hrs = elapsedSeconds / 3600
                val mins = (elapsedSeconds % 3600) / 60
                val secs = elapsedSeconds % 60

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { isRunning = !isRunning },
                            enabled = selectedNode != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) Color(0xFFF59E0B) else Color(0xFF2563EB)
                            )
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (isRunning) "Pause" else "Start")
                        }

                        if (elapsedSeconds > 0 && selectedNode != null) {
                            Button(
                                onClick = {
                                    isRunning = false
                                    // Convert seconds to hours (minimum 0.1h credit when testing)
                                    val loggedHrs = maxOf(0.1, elapsedSeconds / 3600.0)
                                    engine.logInvestedHours(selectedNode, loggedHrs)
                                    elapsedSeconds = 0
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Bank")
                            }
                        }
                    }
                }

                // Secondary Manual Fallback Input
                if (showManualInput && selectedNode != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualHoursText,
                            onValueChange = { manualHoursText = it },
                            label = { Text("Hours (e.g. 1.5)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val h = manualHoursText.toDoubleOrNull()
                                if (h != null && h > 0.0) {
                                    engine.logInvestedHours(selectedNode, h)
                                    manualHoursText = ""
                                    showManualInput = false
                                }
                            }
                        ) {
                            Text("Log")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- 2. HEADER FOR MACRO HOUR BANK BUCKETS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PLACEMENT & PROJECT HOUR BANKS",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            FilledTonalButton(onClick = { showNewTrackDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("New Hour Bucket")
            }
        }

        Spacer(Modifier.height(8.dp))

        // --- 3. LIST OF HOUR BUCKETS WITH GHOST PROGRESS BARS ---
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(appState.rootTracks) { track ->
                val childNodes = appState.taskNodes.filter { it.rootTrackId == track.id }
                HourBucketCard(
                    track = track,
                    childNodes = childNodes,
                    selectedNodeId = selectedNode?.id,
                    onSelectNodeForTimer = { activeNodeId = it.id }
                )
            }
        }
    }

    if (showNewTrackDialog) {
        NewHourBucketDialog(
            onDismiss = { showNewTrackDialog = false },
            onCreate = { title, hours, days ->
                engine.createRootTrack(
                    title = title,
                    targetHours = hours,
                    deadlineDate = LocalDate.now().plusDays(days.toLong())
                )
                showNewTrackDialog = false
            }
        )
    }
}

@Composable
private fun HourBucketCard(
    track: RootTrack,
    childNodes: List<TaskNode>,
    selectedNodeId: Long?,
    onSelectNodeForTimer: (TaskNode) -> Unit
) {
    val today = LocalDate.now()
    val requiredToday = track.requiredDailyPace(today)
    val idealByToday = track.idealGhostHoursBy(today)
    val ghostDeficit = track.ghostBarDeficit(today)
    val scheduledTotal = childNodes.filter { it.startDate != null }.sumOf { it.allocatedHours }
    val unplannedDeficit = maxOf(0.0, track.targetHours - scheduledTotal)

    val actualFraction = (track.investedHours / track.targetHours.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val ghostFraction = (idealByToday / track.targetHours.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171C26)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Deadline: ${track.deadlineDate}  •  Remaining: ${String.format(Locale.US, "%.1f", track.remainingHours)}h",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))

            // REQUIRED DAILY PACE BANNER
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚡ Finish at least ${String.format(Locale.US, "%.1f", requiredToday)} hrs today to stay on pace",
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // DUAL GHOST PROGRESS BAR (Ideal Run vs. Actual Run)
            Text(
                text = "Ghost Pace (Ideal by Today: ${String.format(Locale.US, "%.1f", idealByToday)}h)",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF262F40))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ghostFraction)
                        .fillMaxHeight()
                        .background(Color(0xFF64748B))
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Your Invested Hours: ${String.format(Locale.US, "%.1f", track.investedHours)} / ${String.format(Locale.US, "%.1f", track.targetHours)}h",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (ghostDeficit > 0.05) {
                    Text(
                        text = "+${String.format(Locale.US, "%.1f", ghostDeficit)}h needed to catch Ghost Bar",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Ahead of Ghost Pace!",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF262F40))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(actualFraction)
                        .fillMaxHeight()
                        .background(if (ghostDeficit > 0.05) Color(0xFFF59E0B) else Color(0xFF10B981))
                )
            }

            Spacer(Modifier.height(10.dp))

            // 4-STAT BREAKDOWN ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Scheduled: ${String.format(Locale.US, "%.1f", scheduledTotal)}h", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Text(
                    text = "Unplanned Deficit: ${String.format(Locale.US, "%.1f", unplannedDeficit)}h",
                    color = if (unplannedDeficit > 0.0) Color(0xFFF87171) else Color(0xFF34D399),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Linked Child Study Nodes (Tap to load into Stopwatch)
            if (childNodes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Tap a sprint node to load into Stopwatch:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                childNodes.forEach { node ->
                    val isSelected = node.id == selectedNodeId
                    Surface(
                        color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF222938),
                        shape = RoundedCornerShape(8.dp),
                        onClick = { onSelectNodeForTimer(node) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(node.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${String.format(Locale.US, "%.1f", node.investedHours)} / ${String.format(Locale.US, "%.1f", node.allocatedHours)}h",
                                color = Color(0xFF93C5FD),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewHourBucketDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Double, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var hoursText by remember { mutableStateOf("40") }
    var daysText by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Placement / Project Hour Bucket") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Track Name (e.g. Java & System Design)") }
                )
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text("Target Total Hours (e.g. 40)") }
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Days Until Deadline (e.g. 30)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hrs = hoursText.toDoubleOrNull() ?: 40.0
                    val days = daysText.toIntOrNull() ?: 30
                    if (title.isNotBlank()) onCreate(title, hrs, days)
                }
            ) {
                Text("Create Bucket")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
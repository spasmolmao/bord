package com.example.bord.ui.hourbank

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bord.domain.engine.BordAppState
import com.example.bord.domain.engine.TaskHubEngine
import com.example.bord.domain.model.NodeType
import com.example.bord.domain.model.RootTrack
import com.example.bord.domain.model.TaskNode
import com.example.bord.domain.model.UserMomentum
import com.example.bord.ui.theme.*
import java.time.LocalDate
import java.util.Locale

/**
 * Minimalist Black & White Top Momentum & Streak Header.
 */
@Composable
fun MomentumHeaderBar(momentum: UserMomentum) {
    val isNegative = momentum.points < 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BordPalette.BgPureBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = if (isNegative) "STATUS // PENALTY DEBT" else "STATUS // MOMENTUM",
                color = if (isNegative) BordPalette.SignalAlert else BordPalette.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "${momentum.points} PTS  [${String.format(Locale.US, "%.2fx", momentum.multiplier)}]",
                color = if (isNegative) BordPalette.SignalAlert else BordPalette.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        MonoBadge(
            text = "${momentum.currentStreakDays}D STREAK",
            isAlert = isNegative,
            inverted = !isNegative
        )
    }
}

/**
 * BLOCK 2 SCREEN (Minimalist B&W Edition):
 * Global Stopwatch + Ghost Progress Bar + 1-Tap Rebalancer + Track Deletion.
 */
@Composable
fun HourBankScreen(
    appState: BordAppState,
    engine: TaskHubEngine
) {
    val studyNodes = remember(appState.taskNodes) {
        appState.taskNodes.filter { it.nodeType == NodeType.PROJECT_STUDY }
    }

    var showNewTrackDialog by remember { mutableStateOf(false) }
    var manualHoursText by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }

    val selectedNode: TaskNode? = studyNodes.find { it.id == appState.activeTimerNodeId }
        ?: studyNodes.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BordPalette.BgPureBlack)
            .padding(16.dp)
    ) {
        // --- 1. MINIMALIST MONOCHROME STOPWATCH DECK ---
        MonoSectionTitle(title = "01 / FOCUS STOPWATCH", rightText = "GLOBAL SYNC")
        MonoCard(isHighlighted = appState.isTimerRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedNode?.title?.uppercase() ?: "SELECT A SPRINT NODE BELOW",
                    color = BordPalette.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (showManualInput) "[CLOSE MANUAL]" else "[+ MANUAL HRS]",
                    color = BordPalette.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { showManualInput = !showManualInput }
                )
            }

            Spacer(Modifier.height(10.dp))

            val hrs = appState.timerElapsedSeconds / 3600
            val mins = (appState.timerElapsedSeconds % 3600) / 60
            val secs = appState.timerElapsedSeconds % 60

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs),
                    color = BordPalette.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonoButton(
                        text = if (appState.isTimerRunning) "Pause" else "Start",
                        onClick = {
                            if (selectedNode != null && appState.activeTimerNodeId == null) {
                                engine.selectTimerNode(selectedNode.id)
                            }
                            engine.toggleGlobalTimer()
                        },
                        enabled = selectedNode != null,
                        inverted = true
                    )

                    if (appState.timerElapsedSeconds > 0 && selectedNode != null) {
                        MonoButton(
                            text = "Bank",
                            onClick = { engine.bankGlobalTimerSession() },
                            inverted = false
                        )
                    }
                }
            }

            if (showManualInput && selectedNode != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualHoursText,
                        onValueChange = { manualHoursText = it },
                        label = { Text("Manual Hours (e.g. 1.5)", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    MonoButton(
                        text = "Log",
                        onClick = {
                            val h = manualHoursText.toDoubleOrNull()
                            if (h != null && h > 0.0) {
                                engine.logInvestedHours(selectedNode, h)
                                manualHoursText = ""
                                showManualInput = false
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // --- 2. MACRO HOUR BANKS HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoSectionTitle(title = "02 / HOUR BANK BUCKETS (${appState.rootTracks.size})")
            MonoButton(
                text = "+ Bucket",
                onClick = { showNewTrackDialog = true },
                inverted = false
            )
        }

        Spacer(Modifier.height(8.dp))

        // --- 3. BUCKET CARDS WITH GHOST PROGRESS & 1-TAP REBALANCER ---
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(appState.rootTracks, key = { it.id }) { track ->
                val childNodes = appState.taskNodes.filter { it.rootTrackId == track.id }
                HourBucketCard(
                    track = track,
                    childNodes = childNodes,
                    selectedNodeId = selectedNode?.id,
                    onSelectNodeForTimer = { engine.selectTimerNode(it.id) },
                    onRebalance = { engine.rebalanceTrackDeficit(track) },
                    onDeleteTrack = { engine.deleteRootTrack(track.id) }
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
    onSelectNodeForTimer: (TaskNode) -> Unit,
    onRebalance: () -> Unit,
    onDeleteTrack: () -> Unit
) {
    val today = LocalDate.now()
    val requiredToday = track.requiredDailyPace(today)
    val idealByToday = track.idealGhostHoursBy(today)
    val ghostDeficit = track.ghostBarDeficit(today)
    val scheduledTotal = childNodes.filter { it.startDate != null }.sumOf { it.allocatedHours }
    val unplannedDeficit = maxOf(0.0, track.targetHours - scheduledTotal)

    val actualFraction = (track.investedHours / track.targetHours.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val ghostFraction = (idealByToday / track.targetHours.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val isBehind = ghostDeficit > 0.05

    MonoCard(isAlert = isBehind) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.uppercase(),
                    color = BordPalette.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "DEADLINE: ${track.deadlineDate}  //  REM: ${String.format(Locale.US, "%.1f", track.remainingHours)}H",
                    color = BordPalette.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onDeleteTrack, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Bucket",
                    tint = BordPalette.TextDim
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // REQUIRED DAILY PACE CALLOUT
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BordPalette.SurfaceElevated)
                .border(1.dp, BordPalette.BorderHairline, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REQUIRED TODAY",
                color = BordPalette.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "AT LEAST ${String.format(Locale.US, "%.1f", requiredToday)} HRS",
                color = BordPalette.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(Modifier.height(12.dp))

        // GHOST BAR VS ACTUAL BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "INVESTED: ${String.format(Locale.US, "%.1f", track.investedHours)} / ${String.format(Locale.US, "%.1f", track.targetHours)}H",
                color = BordPalette.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isBehind) {
                    "DEFICIT: +${String.format(Locale.US, "%.1f", ghostDeficit)}H TO GHOST"
                } else {
                    "GHOST PACE: ON TRACK (${String.format(Locale.US, "%.1f", idealByToday)}H)"
                },
                color = if (isBehind) BordPalette.SignalAlert else BordPalette.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))
        MonoDualGhostBar(
            actualFraction = actualFraction,
            ghostFraction = ghostFraction,
            isBehindGhost = isBehind
        )

        Spacer(Modifier.height(10.dp))

        // SCHEDULED VS UNPLANNED + 1-TAP REBALANCER BUTTON
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SCHEDULED: ${String.format(Locale.US, "%.1f", scheduledTotal)}H",
                    color = BordPalette.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Text(
                    text = "UNPLANNED: ${String.format(Locale.US, "%.1f", unplannedDeficit)}H",
                    color = if (unplannedDeficit > 0.0) BordPalette.SignalAlert else BordPalette.TextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if ((unplannedDeficit > 0.1 || isBehind) && childNodes.isNotEmpty()) {
                MonoButton(
                    text = "Rebalance Hours",
                    onClick = onRebalance,
                    inverted = false
                )
            }
        }

        // SPRINT NODES LIST (TAP TO SELECT FOR STOPWATCH)
        if (childNodes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BordPalette.BorderHairline, thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
            childNodes.forEach { node ->
                val isSelected = node.id == selectedNodeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(if (isSelected) BordPalette.SignalWhite else BordPalette.SurfaceElevated)
                        .border(1.dp, if (isSelected) BordPalette.SignalWhite else BordPalette.BorderHairline)
                        .clickable { onSelectNodeForTimer(node) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = node.title.uppercase(),
                        color = if (isSelected) BordPalette.BgPureBlack else BordPalette.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", node.investedHours)} / ${String.format(Locale.US, "%.1f", node.allocatedHours)}H",
                        color = if (isSelected) BordPalette.BgPureBlack else BordPalette.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
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
        title = { Text("NEW HOUR BANK BUCKET", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Track Name (e.g. DSA / Core Java)") }
                )
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text("Total Target Hours (e.g. 40)") }
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Days Until Deadline (e.g. 30)") }
                )
            }
        },
        confirmButton = {
            MonoButton(
                text = "Create",
                onClick = {
                    val hrs = hoursText.toDoubleOrNull() ?: 40.0
                    val days = daysText.toIntOrNull() ?: 30
                    if (title.isNotBlank()) onCreate(title, hrs, days)
                }
            )
        },
        dismissButton = {
            MonoButton(text = "Cancel", onClick = onDismiss, inverted = false)
        }
    )
}
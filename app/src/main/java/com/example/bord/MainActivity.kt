package com.example.bord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bord.data.local.BordDatabase
import com.example.bord.domain.engine.BordAppState
import com.example.bord.domain.engine.TaskHubEngine
import com.example.bord.ui.board.MiroBoardScreen
import com.example.bord.ui.calendar.CalendarScreen
import com.example.bord.ui.hourbank.HourBankScreen
import com.example.bord.ui.hourbank.MomentumHeaderBar
import com.example.bord.ui.theme.BordPalette
import kotlinx.coroutines.delay
import java.util.Locale

private enum class BordTab(val label: String) {
    CALENDAR("CALENDAR"),
    FLOW_BOARD("FLOW BOARD"),
    HOUR_BANK("HOUR BANK")
}

class MainActivity : ComponentActivity() {

    private lateinit var database: BordDatabase
    private lateinit var engine: TaskHubEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = BordDatabase(applicationContext)
        engine = TaskHubEngine(database)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val appState by engine.state.collectAsState()
                var currentTab by remember { mutableStateOf(BordTab.CALENDAR) }

                // GLOBAL STOPWATCH COROUTINE: Ticks regardless of which tab you are viewing!
                LaunchedEffect(appState.isTimerRunning) {
                    while (appState.isTimerRunning) {
                        delay(1000L)
                        engine.tickGlobalTimerOneSecond()
                    }
                }

                Scaffold(
                    containerColor = BordPalette.BgPureBlack,
                    bottomBar = {
                        Column {
                            // Global Persistent Stopwatch Mini-Bar (visible on Calendar & Board when active!)
                            if (appState.isTimerRunning || appState.timerElapsedSeconds > 0) {
                                GlobalMiniStopwatchBar(
                                    appState = appState,
                                    onToggle = { engine.toggleGlobalTimer() },
                                    onBank = { engine.bankGlobalTimerSession() }
                                )
                            }

                            HorizontalDivider(color = BordPalette.BorderHairline, thickness = 1.dp)

                            NavigationBar(
                                containerColor = BordPalette.BgPureBlack,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == BordTab.CALENDAR,
                                    onClick = { currentTab = BordTab.CALENDAR },
                                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                                    label = {
                                        Text(
                                            text = BordTab.CALENDAR.label,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BordPalette.BgPureBlack,
                                        selectedTextColor = BordPalette.TextPrimary,
                                        indicatorColor = BordPalette.SignalWhite,
                                        unselectedIconColor = BordPalette.TextDim,
                                        unselectedTextColor = BordPalette.TextDim
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == BordTab.FLOW_BOARD,
                                    onClick = { currentTab = BordTab.FLOW_BOARD },
                                    icon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                                    label = {
                                        Text(
                                            text = BordTab.FLOW_BOARD.label,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BordPalette.BgPureBlack,
                                        selectedTextColor = BordPalette.TextPrimary,
                                        indicatorColor = BordPalette.SignalWhite,
                                        unselectedIconColor = BordPalette.TextDim,
                                        unselectedTextColor = BordPalette.TextDim
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentTab == BordTab.HOUR_BANK,
                                    onClick = { currentTab = BordTab.HOUR_BANK },
                                    icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                    label = {
                                        Text(
                                            text = BordTab.HOUR_BANK.label,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BordPalette.BgPureBlack,
                                        selectedTextColor = BordPalette.TextPrimary,
                                        indicatorColor = BordPalette.SignalWhite,
                                        unselectedIconColor = BordPalette.TextDim,
                                        unselectedTextColor = BordPalette.TextDim
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        MomentumHeaderBar(momentum = appState.momentum)
                        HorizontalDivider(color = BordPalette.BorderHairline, thickness = 1.dp)

                        when (currentTab) {
                            BordTab.CALENDAR -> CalendarScreen(appState = appState, engine = engine)
                            BordTab.FLOW_BOARD -> MiroBoardScreen(appState = appState, engine = engine)
                            BordTab.HOUR_BANK -> HourBankScreen(appState = appState, engine = engine)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalMiniStopwatchBar(
    appState: BordAppState,
    onToggle: () -> Unit,
    onBank: () -> Unit
) {
    val activeNode = appState.taskNodes.find { it.id == appState.activeTimerNodeId }
    val hrs = appState.timerElapsedSeconds / 3600
    val mins = (appState.timerElapsedSeconds % 3600) / 60
    val secs = appState.timerElapsedSeconds % 60

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BordPalette.SurfaceElevated)
            .border(1.dp, BordPalette.BorderActive)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ACTIVE FOCUS SESSION",
                color = BordPalette.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = activeNode?.title ?: "Study Session",
                color = BordPalette.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs),
            color = BordPalette.SignalWhite,
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (appState.isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = BordPalette.SignalWhite
                )
            }
            IconButton(onClick = onBank, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Bank Hours",
                    tint = BordPalette.SignalAlert
                )
            }
        }
    }
}
package com.example.bord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.bord.data.local.BordDatabase
import com.example.bord.domain.engine.TaskHubEngine
import com.example.bord.ui.board.MiroBoardScreen
import com.example.bord.ui.calendar.CalendarScreen
import com.example.bord.ui.hourbank.HourBankScreen
import com.example.bord.ui.hourbank.MomentumHeaderBar

private enum class BordTab(val label: String) {
    CALENDAR("Calendar"),
    MIRO_BOARD("Miro Board"),
    HOUR_BANK("Hour Bank")
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

                Scaffold(
                    containerColor = Color(0xFF0F131A),
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF161B26)) {
                            NavigationBarItem(
                                selected = currentTab == BordTab.CALENDAR,
                                onClick = { currentTab = BordTab.CALENDAR },
                                icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                                label = { Text(BordTab.CALENDAR.label) }
                            )
                            NavigationBarItem(
                                selected = currentTab == BordTab.MIRO_BOARD,
                                onClick = { currentTab = BordTab.MIRO_BOARD },
                                icon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                                label = { Text(BordTab.MIRO_BOARD.label) }
                            )
                            NavigationBarItem(
                                selected = currentTab == BordTab.HOUR_BANK,
                                onClick = { currentTab = BordTab.HOUR_BANK },
                                icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                label = { Text(BordTab.HOUR_BANK.label) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Top Momentum, Streak & Negative Point Penalty Bar across all tabs
                        MomentumHeaderBar(momentum = appState.momentum)

                        when (currentTab) {
                            BordTab.CALENDAR -> CalendarScreen(appState = appState, engine = engine)
                            BordTab.MIRO_BOARD -> MiroBoardScreen(appState = appState, engine = engine)
                            BordTab.HOUR_BANK -> HourBankScreen(appState = appState, engine = engine)
                        }
                    }
                }
            }
        }
    }
}
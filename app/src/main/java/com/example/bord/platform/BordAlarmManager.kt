package com.example.bord.platform

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.example.bord.domain.model.TaskNode
import com.example.bord.ui.theme.BordPalette
import com.example.bord.ui.theme.MonoBadge
import com.example.bord.ui.theme.MonoButton
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.text.toInt
import kotlin.times

/**
 * Singleton Audio & Vibration controller that loops the native OS alarm sound continuously
 * until the user taps DISMISS or SNOOZE on BordAlarmRingActivity.
 */
object BordAlarmSoundPlayer {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    @android.annotation.SuppressLint("MissingPermission")
    fun startRinging(context: Context) {
        stopRinging()
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(context.applicationContext, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                r.isLooping = true
            }
            r.play()
            ringtone = r
        }

        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 600, 400, 600, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
            vibrator = vib
        }
    }

    @SuppressLint("MissingPermission")
    fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }
}

/**
 * FULL-SCREEN WAKE-UP ALARM ACTIVITY
 * Pops up over the lockscreen in stark Black/White/Red contrast with the task's Pocket Checklist,
 * SNOOZE 10M, and DISMISS buttons.
 */
class BordAlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and show over lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val title = intent.getStringExtra("ALARM_TITLE") ?: "BORD EXECUTION ALARM"
        val body = intent.getStringExtra("ALARM_BODY") ?: "Scheduled deadline window active."
        val stageLabel = intent.getStringExtra("ALARM_STAGE") ?: "HARD DEADLINE ALARM"
        val alarmId = intent.getIntExtra("ALARM_ID", 1001)

        // Start continuous alarm sound & vibration
        BordAlarmSoundPlayer.startRinging(this)

        setContent {
            Surface(
                color = BordPalette.BgPureBlack,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))

                    // Top Alert Header
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MonoBadge(text = "⏰ $stageLabel", isAlert = true)

                        Text(
                            text = title.uppercase(),
                            color = BordPalette.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Middle Pocket Checklist / Briefing Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BordPalette.SurfaceCard, RoundedCornerShape(6.dp))
                            .border(1.5.dp, BordPalette.SignalAlert, RoundedCornerShape(6.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CHECKLIST // BRIEFING BEFORE YOU MOVE:",
                            color = BordPalette.SignalAlert,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = body,
                            color = BordPalette.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Bottom Action Buttons: SNOOZE 10M vs. DISMISS
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MonoButton(
                            text = "Dismiss & Execute Now",
                            onClick = {
                                BordAlarmSoundPlayer.stopRinging()
                                cancelNotification(alarmId)
                                finish()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            inverted = true
                        )

                        MonoButton(
                            text = "Snooze +10 Minutes",
                            onClick = {
                                BordAlarmSoundPlayer.stopRinging()
                                cancelNotification(alarmId)
                                BordAlarmScheduler.scheduleSnooze10Min(
                                    context = this@BordAlarmRingActivity,
                                    title = title,
                                    body = body
                                )
                                finish()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            inverted = false
                        )
                    }
                }
            }
        }
    }

    private fun cancelNotification(id: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }

    override fun onDestroy() {
        BordAlarmSoundPlayer.stopRinging()
        super.onDestroy()
    }
}

/**
 * BroadcastReceiver that catches the OS AlarmManager trigger, starts ringing,
 * and launches BordAlarmRingActivity.
 */
class BordAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("ALARM_TITLE") ?: "Bord Task Alarm"
        val body = intent.getStringExtra("ALARM_BODY") ?: "Time to execute your scheduled task."
        val stage = intent.getStringExtra("ALARM_STAGE") ?: "EXECUTION ALARM"
        val alarmId = intent.getIntExtra("ALARM_ID", 1001)

        // 1. Launch Full-Screen Ringing Activity
        val ringIntent = Intent(context, BordAlarmRingActivity::class.java).apply {
            putExtra("ALARM_TITLE", title)
            putExtra("ALARM_BODY", body)
            putExtra("ALARM_STAGE", stage)
            putExtra("ALARM_ID", alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(ringIntent) }

        // 2. Also post high-priority fullScreenIntent notification for locked devices
        val channelId = "bord_hard_alarm_v2"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Bord Continuous Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full-screen ringing alarms for errands and study quotas"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val fullScreenPending = PendingIntent.getActivity(
            context,
            alarmId,
            ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ $title")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .setOngoing(true)
            .setAutoCancel(true)
            .build()

        manager.notify(alarmId, notification)
    }
}

/**
 * LAYER 3 PLATFORM SERVICE: Schedules Two-Stage Buffer Alarms, Morning Briefings, and QA Test Triggers.
 */
object BordAlarmScheduler {

    fun scheduleAlarmsForNode(context: Context, node: TaskNode) {
        val date = node.startDate ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val checklistSummary = if (node.microTasks.isNotEmpty()) {
            node.microTasks.joinToString(separator = "\n") {
                "${if (it.isCompleted) "[✓]" else "[ ]"} ${it.title}"
            }
        } else {
            "No pocket checklist items attached."
        }

        // INSTANT 0-SECOND FOREGROUND LAUNCH FOR QA TESTING:
        val instantRingIntent = Intent(context, BordAlarmRingActivity::class.java).apply {
            putExtra("ALARM_TITLE", node.title)
            putExtra("ALARM_BODY", checklistSummary)
            putExtra(
                "ALARM_STAGE",
                if (node.isAllDay) "ALL-DAY BRIEFING ALARM" else "STAGE 1 // ${node.bufferMinutes}M BUFFER ALARM"
            )
            putExtra("ALARM_ID", (node.id * 10 + 1).toInt())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(instantRingIntent)

        // Also register the scheduled OS AlarmManager triggers
        if (node.isAllDay || node.startTimeMinutes == null) {
            val morningTrigger = LocalDateTime.of(date, LocalTime.of(9, 0))
            val morningMillis = morningTrigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (morningMillis > System.currentTimeMillis()) {
                scheduleExactOrFallback(
                    context = context,
                    alarmManager = alarmManager,
                    requestCode = (node.id * 10 + 1).toInt(),
                    triggerMillis = morningMillis,
                    title = node.title,
                    body = checklistSummary,
                    stage = "09:00 AM ALL-DAY BRIEFING"
                )
            }
        } else {
            val hours = node.startTimeMinutes / 60
            val mins = node.startTimeMinutes % 60
            val mainTrigger = LocalDateTime.of(date, LocalTime.of(hours, mins))
            val bufferTrigger = mainTrigger.minusMinutes(node.bufferMinutes.toLong())

            val bufferMillis = bufferTrigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val mainMillis = mainTrigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (node.bufferMinutes > 0 && bufferMillis > System.currentTimeMillis()) {
                scheduleExactOrFallback(
                    context = context,
                    alarmManager = alarmManager,
                    requestCode = (node.id * 10 + 2).toInt(),
                    triggerMillis = bufferMillis,
                    title = node.title,
                    body = checklistSummary,
                    stage = "STAGE 1 // ${node.bufferMinutes}M BUFFER WARNING"
                )
            }

            if (mainMillis > System.currentTimeMillis()) {
                scheduleExactOrFallback(
                    context = context,
                    alarmManager = alarmManager,
                    requestCode = (node.id * 10 + 3).toInt(),
                    triggerMillis = mainMillis,
                    title = node.title,
                    body = checklistSummary,
                    stage = "STAGE 2 // HARD START DEADLINE"
                )
            }
        }
    }

    fun scheduleSnooze10Min(context: Context, title: String, body: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerMillis = System.currentTimeMillis() + 10 * 60 * 1000L
        scheduleExactOrFallback(
            context = context,
            alarmManager = alarmManager,
            requestCode = 9999,
            triggerMillis = triggerMillis,
            title = title,
            body = body,
            stage = "SNOOZED ALARM (+10M)"
        )
        Toast.makeText(context, "Snoozed for 10 minutes", Toast.LENGTH_SHORT).show()
    }

    private fun scheduleExactOrFallback(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        triggerMillis: Long,
        title: String,
        body: String,
        stage: String
    ) {
        val intent = Intent(context, BordAlarmReceiver::class.java).apply {
            putExtra("ALARM_TITLE", title)
            putExtra("ALARM_BODY", body)
            putExtra("ALARM_STAGE", stage)
            putExtra("ALARM_ID", requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runCatching {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent
            )
        }.onFailure {
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        }
    }
}
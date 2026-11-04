package com.example.bord.platform

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.provider.AlarmClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.bord.domain.model.TaskNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * BroadcastReceiver that fires when a scheduled Bord alarm triggers.
 */
class BordAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("ALARM_TITLE") ?: "Bord Task Alarm"
        val body = intent.getStringExtra("ALARM_BODY") ?: "Time to execute your scheduled task."
        val alarmId = intent.getIntExtra("ALARM_ID", 1001)

        val channelId = "bord_hard_alarm_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val channel = NotificationChannel(
                channelId,
                "Bord Task & Prep Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Standard loud alarms for errands and placement study blocks"
                setSound(alarmUri, null)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        manager.notify(alarmId, notification)
    }
}

/**
 * LAYER 3 PLATFORM SERVICE: Standard Android Alarm Scheduler
 * Supports:
 * 1. Two-Stage Buffer Alarms (Prep/Leave Buffer + Action Start)
 * 2. All-Day Morning Briefing Alarms (9:00 AM on the scheduled date)
 * 3. Direct System Clock Alarm handoff for loud native wake-ups
 */
object BordAlarmScheduler {

    fun scheduleAlarmsForNode(context: Context, node: TaskNode) {
        val date = node.startDate ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (node.isAllDay || node.startTimeMinutes == null) {
            // All-Day Task: Schedule 9:00 AM Morning Briefing Alarm
            val morningTrigger = LocalDateTime.of(date, LocalTime.of(9, 0))
            scheduleSingleTrigger(
                context = context,
                alarmManager = alarmManager,
                requestCode = (node.id * 10 + 1).toInt(),
                triggerDateTime = morningTrigger,
                title = "All-Day Task Today: ${node.title}",
                body = if (node.microTasks.isNotEmpty()) {
                    "Pocket Checklist: ${node.microTasks.joinToString { it.title }}"
                } else {
                    "Scheduled for anytime today."
                }
            )
            Toast.makeText(context, "9:00 AM All-Day Briefing set for ${node.title}", Toast.LENGTH_SHORT).show()
        } else {
            // Fixed-Clock Task: Schedule Stage 1 (Buffer) + Stage 2 (Start Time)
            val hours = node.startTimeMinutes / 60
            val mins = node.startTimeMinutes % 60
            val mainTrigger = LocalDateTime.of(date, LocalTime.of(hours, mins))
            val bufferTrigger = mainTrigger.minusMinutes(node.bufferMinutes.toLong())

            // Stage 1: Wrap-Up / Prep Buffer Alarm
            if (node.bufferMinutes > 0) {
                scheduleSingleTrigger(
                    context = context,
                    alarmManager = alarmManager,
                    requestCode = (node.id * 10 + 2).toInt(),
                    triggerDateTime = bufferTrigger,
                    title = "Wrap Up (${node.bufferMinutes}m Buffer): ${node.title}",
                    body = if (node.microTasks.isNotEmpty()) {
                        "Check items before leaving: ${node.microTasks.joinToString { it.title }}"
                    } else {
                        "Starts in ${node.bufferMinutes} minutes."
                    }
                )
            }

            // Stage 2: Hard Action Alarm
            scheduleSingleTrigger(
                context = context,
                alarmManager = alarmManager,
                requestCode = (node.id * 10 + 3).toInt(),
                triggerDateTime = mainTrigger,
                title = "Start Now: ${node.title}",
                body = "Duration: ${node.durationMinutes} mins"
            )
            Toast.makeText(
                context,
                "2-Stage Alarm set (${node.bufferMinutes}m buffer + start time)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Also allows setting a direct Android System Clock Alarm with 1 tap.
     */
    fun setSystemClockAlarm(context: Context, title: String, hour: Int, minute: Int) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, title)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, "Clock app unavailable on this device", Toast.LENGTH_SHORT).show()
            }
    }

    private fun scheduleSingleTrigger(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        triggerDateTime: LocalDateTime,
        title: String,
        body: String
    ) {
        val triggerMillis = triggerDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (triggerMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, BordAlarmReceiver::class.java).apply {
            putExtra("ALARM_TITLE", title)
            putExtra("ALARM_BODY", body)
            putExtra("ALARM_ID", requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent
            )
        }
    }
}


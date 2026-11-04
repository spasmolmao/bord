package com.example.bord.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Distinguishes between a multi-day Placement/Project node and a fast Errand/Deadline task.
 */
enum class NodeType {
    PROJECT_STUDY, // Feeds the Hour Bank, supports multi-day chunks & persistent checklists
    ERRAND_TASK    // Place-A style errands or hard deadlines (can be All-Day or Fixed-Clock)
}

/**
 * Urgency tiers from Block 1.
 */
enum class UrgencyTier {
    CRITICAL,   // Hard external cutoff—cannot miss
    TODAY_MUST, // Flexible clock time, must finish on its scheduled day
    QUICK_HIT   // < 30 mins fast task
}

/**
 * Visual border status for the Miro Board (Block 4 Anti-Idle rule).
 */
enum class BoardBorderStatus {
    IDLE_UNSCHEDULED, // Grey dashed: sitting on the board with no dates/hours attached
    SCHEDULED_ACTIVE, // Blue solid: mapped to the calendar and on track
    BEHIND_PACE,      // Red glow: overdue or behind Ghost Bar pace
    COMPLETED         // Green solid: 100% done
}

/**
 * Daily workload color-coding for the Monthly Calendar grid (Block 3).
 */
enum class DayLoadType {
    FREE,         // Green / Light
    ERRAND_HEAVY, // Orange
    PREP_HEAVY,   // Blue
    HYBRID,       // Purple (Both Errands + Study Quotas)
    DEADLINE_CUT  // Red (Critical cutoff day)
}

/**
 * TIER 1: RootTrack (Macro Goal / Hour Bank Bucket on the Miro Board)
 * Example: "Placement Coding (40 hrs by Nov 30)" or "Final Year Project"
 */
data class RootTrack(
    val id: Long = 0L,
    val title: String,
    val targetHours: Double,          // e.g., 40.0 hours for the month
    val investedHours: Double = 0.0,  // Live hours logged via Stopwatch or Manual entry
    val startDate: LocalDate = LocalDate.now(),
    val deadlineDate: LocalDate,
    val boardX: Float = 100f,         // X coordinate on the 2D Miro Canvas
    val boardY: Float = 100f,         // Y coordinate on the 2D Miro Canvas
    val colorHex: Long = 0xFF3B82F6   // Default electric blue accent
) {
    val remainingHours: Double
        get() = max(0.0, targetHours - investedHours)

    /**
     * Required Daily Pace ("Finish at least X hrs today to finish on time").
     */
    fun requiredDailyPace(today: LocalDate = LocalDate.now()): Double {
        if (remainingHours <= 0.0) return 0.0
        val daysLeft = max(1L, ChronoUnit.DAYS.between(today, deadlineDate) + 1L)
        return remainingHours / daysLeft.toDouble()
    }

    /**
     * Ideal hours that *should* be completed by today (Ghost Progress Bar target).
     */
    fun idealGhostHoursBy(today: LocalDate = LocalDate.now()): Double {
        val totalSpanDays = max(1L, ChronoUnit.DAYS.between(startDate, deadlineDate) + 1L)
        val elapsedDays = ChronoUnit.DAYS.between(startDate, today) + 1L
        val clampedElapsed = elapsedDays.coerceIn(0L, totalSpanDays)
        return (targetHours * clampedElapsed.toDouble()) / totalSpanDays.toDouble()
    }

    /**
     * Exact hours needed right now to catch up to the Ghost Bar ("Ideal Run" deficit).
     */
    fun ghostBarDeficit(today: LocalDate = LocalDate.now()): Double {
        return max(0.0, idealGhostHoursBy(today) - investedHours)
    }
}

/**
 * TIER 2: TaskNode (The unified unit of work—lives on the Calendar, and optionally on the Board)
 *
 * STRICT CONTRACT:
 * - If [rootTrackId] != null -> Created from the Miro Board (syncs feedback to the Board).
 * - If [rootTrackId] == null -> Standalone Calendar task (never clutters the Miro Board).
 */
data class TaskNode(
    val id: Long = 0L,
    val rootTrackId: Long? = null,    // NULL = Standalone Calendar Task; ID = Miro Board Child Node
    val title: String,
    val nodeType: NodeType = NodeType.ERRAND_TASK,
    val urgency: UrgencyTier = UrgencyTier.TODAY_MUST,

    // Date Chunk Span ("Big Chunk Until Sliced" rule)
    val startDate: LocalDate?,        // NULL only if sitting unscheduled ("Idle") on the Miro Board
    val endDate: LocalDate?,          // Same as startDate for 1-day errands; multi-day for projects

    // Clock & Alarm settings (Block 1: Zero fake clock times required)
    val isAllDay: Boolean = true,     // TRUE = "Anytime Today" banner; FALSE = Fixed-Clock slot
    val startTimeMinutes: Int? = null,// Minutes from midnight (e.g., 14:00 = 840) if isAllDay == false
    val durationMinutes: Int = 60,    // Can be extended by +30m via "Ran Late" button
    val bufferMinutes: Int = 30,      // 2-Stage Alarm: rings [bufferMinutes] before [startTimeMinutes]

    // Hour Quota & Progress (Block 2)
    val allocatedHours: Double = 0.0, // Slice of the parent RootTrack hours assigned to this node
    val investedHours: Double = 0.0,  // Hours logged specifically against this node
    val isCompleted: Boolean = false,

    // 2D Miro Board Canvas Coordinates (used when rootTrackId != null)
    val boardX: Float = 350f,
    val boardY: Float = 220f,

    // Tier 3 Pocket / Project Micro-Checklist
    val microTasks: List<MicroTask> = emptyList()
) {
    val isBoardLinked: Boolean
        get() = rootTrackId != null

    val checklistProgressFraction: Float
        get() = if (microTasks.isEmpty()) {
            if (isCompleted) 1f else 0f
        } else {
            microTasks.count { it.isCompleted }.toFloat() / microTasks.size.toFloat()
        }

    /**
     * Determines the visual border glow on the Miro Board (Block 4).
     */
    fun computeBoardStatus(today: LocalDate = LocalDate.now()): BoardBorderStatus {
        if (isCompleted || (microTasks.isNotEmpty() && microTasks.all { it.isCompleted })) {
            return BoardBorderStatus.COMPLETED
        }
        if (startDate == null || endDate == null) {
            return BoardBorderStatus.IDLE_UNSCHEDULED
        }
        if (today.isAfter(endDate)) {
            return BoardBorderStatus.BEHIND_PACE
        }
        return BoardBorderStatus.SCHEDULED_ACTIVE
    }
}

/**
 * TIER 3: MicroTask (Pocket Checklist item for "Place A" errands OR Persistent Project step)
 *
 * STRICT CONTRACT:
 * - If [pinnedDate] == null -> Rides the parent TaskNode's entire [startDate.endDate] chunk.
 * - If [pinnedDate] != null -> Must fall inside [TaskNode.startDate.TaskNode.endDate] and
 *   spotlights on that specific day on the Calendar.
 */
data class MicroTask(
    val id: Long = 0L,
    val taskNodeId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val pinnedDate: LocalDate? = null, // Optional day-pinning inside the parent chunk
    val estimatedHours: Double? = null // Optional mini-hour estimate
)

/**
 * User Momentum, Streak & Anti-Procrastination Score (Block 1 & 3).
 * Drops into negative penalty territory at Midnight Audit when daily tasks/quotas are skipped.
 */
data class UserMomentum(
    val points: Int = 100,             // Can drop negative if you skip scheduled days
    val currentStreakDays: Int = 0,
    val multiplier: Double = 1.0,      // Climbs from 1.0x -> 2.5x as you complete daily quotas
    val lastAuditDate: LocalDate = LocalDate.now()
)
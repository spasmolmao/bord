package com.example.bord.domain.engine

import com.example.bord.data.local.BordDatabase
import com.example.bord.domain.model.DayLoadType
import com.example.bord.domain.model.MicroTask
import com.example.bord.domain.model.NodeType
import com.example.bord.domain.model.RootTrack
import com.example.bord.domain.model.TaskNode
import com.example.bord.domain.model.UrgencyTier
import com.example.bord.domain.model.UserMomentum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * Snapshot of all app data emitted to the Calendar, Board, and Hour Bank screens.
 */
data class BordAppState(
    val rootTracks: List<RootTrack> = emptyList(),
    val taskNodes: List<TaskNode> = emptyList(),
    val momentum: UserMomentum = UserMomentum(),
    val selectedDate: LocalDate = LocalDate.now(),
    val activeTimerNodeId: Long? = null,
    val isTimerRunning: Boolean = false,
    val timerElapsedSeconds: Int = 0
)

/**
 * LAYER 2: CENTRAL TASK HUB & EXECUTION ENGINE
 * Implements Unidirectional Data Flow:
 * - Board creates RootTracks & linked TaskNodes (rootTrackId != null)
 * - Calendar creates standalone TaskNodes (rootTrackId == null)
 * - Both go through initializeTask() and emit live state back to all screens.
 */
class TaskHubEngine(private val db: BordDatabase) {

    private val _state = MutableStateFlow(BordAppState())
    val state: StateFlow<BordAppState> = _state.asStateFlow()

    init {
        refreshState()
        runMidnightAuditIfNeeded()
        seedStarterDataIfEmpty()
    }

    fun refreshState(selectedDate: LocalDate = _state.value.selectedDate) {
        val current = _state.value
        val nodes = db.getAllTaskNodes()
        val defaultTimerId = current.activeTimerNodeId
            ?: nodes.firstOrNull { it.nodeType == NodeType.PROJECT_STUDY }?.id

        _state.value = current.copy(
            rootTracks = db.getAllRootTracks(),
            taskNodes = nodes,
            momentum = db.getUserMomentum(),
            selectedDate = selectedDate,
            activeTimerNodeId = defaultTimerId
        )
    }
    fun selectDate(date: LocalDate) {
        refreshState(selectedDate = date)
    }

    /**
     * SHARED ENTRY POINT (F1.1): Called by BOTH the Miro Board and the Calendar.
     * - If [rootTrackId] == null -> Standalone Calendar task (never touches the Board).
     * - If [rootTrackId] != null -> Linked Miro Board child node (syncs feedback to Board).
     */
    fun initializeTask(
        title: String,
        nodeType: NodeType,
        urgency: UrgencyTier = UrgencyTier.TODAY_MUST,
        startDate: LocalDate?,
        endDate: LocalDate? = startDate,
        isAllDay: Boolean = true,
        startTimeMinutes: Int? = null,
        durationMinutes: Int = 60,
        bufferMinutes: Int = 30,
        allocatedHours: Double = 0.0,
        rootTrackId: Long? = null,
        checklistItems: List<String> = emptyList(),
        boardX: Float = 360f,
        boardY: Float = 220f
    ): Long {
        val normalizedEnd = if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            startDate
        } else {
            endDate ?: startDate
        }

        val node = TaskNode(
            rootTrackId = rootTrackId,
            title = title.trim(),
            nodeType = nodeType,
            urgency = urgency,
            startDate = startDate,
            endDate = normalizedEnd,
            isAllDay = isAllDay,
            startTimeMinutes = if (isAllDay) null else startTimeMinutes,
            durationMinutes = durationMinutes,
            bufferMinutes = bufferMinutes,
            allocatedHours = allocatedHours,
            boardX = boardX,
            boardY = boardY
        )
        val id = db.insertTaskNodeWithChecklist(node, checklistItems)
        refreshState()
        return id
    }

    /**
     * Creates a Tier 1 Root Track (Macro Hour Bucket on the Miro Board & Hour Bank).
     */
    fun createRootTrack(
        title: String,
        targetHours: Double,
        deadlineDate: LocalDate,
        boardX: Float = 80f,
        boardY: Float = 160f,
        colorHex: Long = 0xFF3B82F6
    ): Long {
        val id = db.insertRootTrack(
            RootTrack(
                title = title.trim(),
                targetHours = targetHours,
                startDate = LocalDate.now(),
                deadlineDate = deadlineDate,
                boardX = boardX,
                boardY = boardY,
                colorHex = colorHex
            )
        )
        refreshState()
        return id
    }

    /**
     * Maps an idle Miro Board node onto a Calendar date chunk and Hour Bank slice (F4.2).
     */
    fun mapBoardNodeToCalendar(
        nodeId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
        allocatedHours: Double
    ) {
        val safeEnd = if (endDate.isBefore(startDate)) startDate else endDate
        db.updateNodeScheduleAndHours(nodeId, startDate, safeEnd, allocatedHours)
        refreshState()
    }

    /**
     * STRICT RULE 3 ("Big Chunk Until Sliced"):
     * Pins a micro-task to a specific date strictly within its parent node's [startDate.endDate].
     */
    fun pinMicroTaskToDate(microTask: MicroTask, parentNode: TaskNode, targetDate: LocalDate?) {
        if (targetDate != null && parentNode.startDate != null && parentNode.endDate != null) {
            val clampedDate = when {
                targetDate.isBefore(parentNode.startDate) -> parentNode.startDate
                targetDate.isAfter(parentNode.endDate) -> parentNode.endDate
                else -> targetDate
            }
            db.updateMicroTaskPinnedDate(microTask.id, clampedDate)
        } else {
            db.updateMicroTaskPinnedDate(microTask.id, null)
        }
        refreshState()
    }

    fun addMicroTaskToNode(nodeId: Long, title: String, pinnedDate: LocalDate? = null) {
        if (title.isBlank()) return
        db.addMicroTask(nodeId, title, pinnedDate)
        refreshState()
    }

    /**
     * Toggles a micro-checklist item and updates Momentum XP + parent completion state.
     */
    fun toggleMicroTask(microTask: MicroTask, parentNode: TaskNode) {
        val newDone = !microTask.isCompleted
        db.toggleMicroTask(microTask.id, newDone)

        // Award or deduct Momentum points
        val currentMom = db.getUserMomentum()
        val deltaPoints = if (newDone) (10 * currentMom.multiplier).toInt() else -10
        val newMultiplier = if (newDone) min(2.5, currentMom.multiplier + 0.05) else currentMom.multiplier
        db.saveUserMomentum(
            currentMom.copy(
                points = currentMom.points + deltaPoints,
                multiplier = newMultiplier
            )
        )

        // Check if all micro-tasks in parent node are now completed
        val updatedNode = db.getAllTaskNodes().find { it.id == parentNode.id }
        if (updatedNode != null && updatedNode.microTasks.isNotEmpty()) {
            val allDone = updatedNode.microTasks.all { it.isCompleted }
            db.setNodeCompleted(parentNode.id, allDone)
        }
        refreshState()
    }

    fun toggleTaskNodeCompleted(node: TaskNode) {
        val newDone = !node.isCompleted
        db.setNodeCompleted(node.id, newDone)

        val currentMom = db.getUserMomentum()
        val delta = if (newDone) (20 * currentMom.multiplier).toInt() else -20
        val newMultiplier = if (newDone) min(2.5, currentMom.multiplier + 0.1) else currentMom.multiplier
        db.saveUserMomentum(currentMom.copy(points = currentMom.points + delta, multiplier = newMultiplier))
        refreshState()
    }

    /**
     * Block 1 Micro-Feature (F1.4): "Ran Late (+30m)" button.
     */
    fun extendTaskDurationBy30Min(node: TaskNode) {
        db.updateNodeDuration(node.id, node.durationMinutes + 30)
        refreshState()
    }

    /**
     * Block 2 (F2.5): Logs invested study hours from the Stopwatch or Manual entry.
     * Atomically updates both the TaskNode AND the parent RootTrack Hour Bank.
     */
    fun logInvestedHours(node: TaskNode, hoursAdded: Double) {
        if (hoursAdded <= 0.0) return
        db.addInvestedHoursAtomic(node.id, node.rootTrackId, hoursAdded)

        val currentMom = db.getUserMomentum()
        val earnedXp = (hoursAdded * 25.0 * currentMom.multiplier).toInt()
        db.saveUserMomentum(
            currentMom.copy(
                points = currentMom.points + earnedXp,
                multiplier = min(2.5, currentMom.multiplier + 0.1)
            )
        )
        refreshState()
    }

    /**
     * Updates 2D coordinates when dragging cards on the Miro Board.
     */
    fun moveBoardItem(isRootTrack: Boolean, id: Long, newX: Float, newY: Float) {
        if (isRootTrack) {
            db.updateTrackBoardPosition(id, max(0f, newX), max(0f, newY))
        } else {
            db.updateNodeBoardPosition(id, max(0f, newX), max(0f, newY))
        }
        refreshState()
    }

    /**
     * Block 3 (F3.1): Computes the workload color for any calendar date cell.
     */
    fun getDayLoadType(date: LocalDate): DayLoadType {
        val activeNodes = getNodesForDate(date)
        if (activeNodes.isEmpty()) return DayLoadType.FREE
        if (activeNodes.any { it.urgency == UrgencyTier.CRITICAL && it.endDate == date && !it.isCompleted }) {
            return DayLoadType.DEADLINE_CUT
        }
        val hasErrands = activeNodes.any { it.nodeType == NodeType.ERRAND_TASK }
        val hasPrep = activeNodes.any { it.nodeType == NodeType.PROJECT_STUDY }
        return when {
            hasErrands && hasPrep -> DayLoadType.HYBRID
            hasErrands -> DayLoadType.ERRAND_HEAVY
            else -> DayLoadType.PREP_HEAVY
        }
    }

    /**
     * Returns all TaskNodes active on [date] (either 1-day tasks or multi-day chunks spanning [date]).
     */
    fun getNodesForDate(date: LocalDate): List<TaskNode> {
        return _state.value.taskNodes.filter { node ->
            val start = node.startDate
            val end = node.endDate
            start != null && end != null && !date.isBefore(start) && !date.isAfter(end)
        }
    }

    /**
     * Block 3 (F3.3): MIDNIGHT AUDIT & REAL-LIFE ROLLOVER
     * - Docks negative points for unfinished past-due tasks
     * - Rolls unfinished tasks forward so tomorrow reflects real life
     */
    fun runMidnightAuditIfNeeded(today: LocalDate = LocalDate.now()) {
        val currentMom = db.getUserMomentum()
        if (!today.isAfter(currentMom.lastAuditDate)) return

        val allNodes = db.getAllTaskNodes()
        var penaltyPoints = 0

        allNodes.forEach { node ->
            val end = node.endDate
            if (end != null && end.isBefore(today) && !node.isCompleted) {
                // Unfinished past-due node: dock points & roll end_date forward to today
                penaltyPoints += 25
                val newStart = node.startDate ?: today
                db.updateNodeScheduleAndHours(node.id, newStart, today, node.allocatedHours)
            }
        }

        val updatedMom = if (penaltyPoints > 0) {
            currentMom.copy(
                points = currentMom.points - penaltyPoints, // Can go negative!
                currentStreakDays = 0,
                multiplier = 1.0,
                lastAuditDate = today
            )
        } else {
            currentMom.copy(
                currentStreakDays = currentMom.currentStreakDays + 1,
                multiplier = min(2.5, currentMom.multiplier + 0.15),
                lastAuditDate = today
            )
        }
        db.saveUserMomentum(updatedMom)
        refreshState()
    }

    /**
     * Seeds realistic starter data on first launch so the Board, Calendar, and Hour Bank
     * immediately show your Placement Prep + Place A Errand workflow in action.
     */
    // --- GLOBAL PERSISTENT STOPWATCH CONTROLS ---

    fun selectTimerNode(nodeId: Long) {
        _state.value = _state.value.copy(activeTimerNodeId = nodeId)
    }

    fun toggleGlobalTimer() {
        val current = _state.value
        if (current.activeTimerNodeId == null) return
        _state.value = current.copy(isTimerRunning = !current.isTimerRunning)
    }

    fun tickGlobalTimerOneSecond() {
        val current = _state.value
        if (current.isTimerRunning) {
            _state.value = current.copy(timerElapsedSeconds = current.timerElapsedSeconds + 1)
        }
    }

    fun bankGlobalTimerSession() {
        val current = _state.value
        val activeNode = current.taskNodes.find { it.id == current.activeTimerNodeId } ?: return
        if (current.timerElapsedSeconds <= 0) return

        val hoursLogged = max(0.1, current.timerElapsedSeconds / 3600.0)
        _state.value = current.copy(isTimerRunning = false, timerElapsedSeconds = 0)
        logInvestedHours(activeNode, hoursLogged)
    }

    // --- DELETE & 1-TAP DEFICIT REBALANCER (F2.6) ---

    fun deleteRootTrack(trackId: Long) {
        db.deleteRootTrack(trackId)
        refreshState()
    }

    fun deleteTaskNode(nodeId: Long) {
        db.deleteTaskNode(nodeId)
        refreshState()
    }

    fun deleteMicroTask(microTaskId: Long) {
        db.deleteMicroTask(microTaskId)
        refreshState()
    }

    /**
     * Block 2 (F2.6): 1-Tap Deficit Rebalancer
     * Takes any unplanned hour deficit (or Ghost Bar deficit) on a RootTrack and spreads it
     * evenly across all unfinished child nodes of that track.
     */
    fun rebalanceTrackDeficit(track: RootTrack) {
        val activeChildren = _state.value.taskNodes.filter {
            it.rootTrackId == track.id && !it.isCompleted
        }
        if (activeChildren.isEmpty()) return

        val scheduledTotal = activeChildren.sumOf { it.allocatedHours }
        val unplannedGap = max(0.0, track.remainingHours - scheduledTotal)
        val ghostGap = track.ghostBarDeficit()
        val hoursToSpread = if (unplannedGap > 0.1) unplannedGap else max(1.0, ghostGap)

        val addPerNode = hoursToSpread / activeChildren.size.toDouble()
        db.redistributeTrackHoursEvenly(track.id, addPerNode)
        refreshState()
    }
    private fun seedStarterDataIfEmpty() {
        if (_state.value.rootTracks.isNotEmpty() || _state.value.taskNodes.isNotEmpty()) return

        val today = LocalDate.now()

        // 1. Create a Macro Placement Track (40 hrs this month)
        val dsaTrackId = createRootTrack(
            title = "Placement Prep: DSA & Coding",
            targetHours = 40.0,
            deadlineDate = today.plusDays(25),
            boardX = 60f,
            boardY = 120f,
            colorHex = 0xFF3B82F6
        )

        // 2. Create a Multi-Day Project/Study Chunk under that Track
        initializeTask(
            title = "Graphs, Trees & DP Sprint",
            nodeType = NodeType.PROJECT_STUDY,
            urgency = UrgencyTier.CRITICAL,
            startDate = today,
            endDate = today.plusDays(4),
            isAllDay = true,
            allocatedHours = 12.0,
            rootTrackId = dsaTrackId,
            checklistItems = listOf(
                "BFS & DFS 5 Medium Problems",
                "Shortest Path / Dijkstra Template",
                "Tree DP & Memoization Set"
            ),
            boardX = 420f,
            boardY = 90f
        )

        // 3. Create an Idle (Unscheduled) Board Node to test the Anti-Idle Border Warning
        initializeTask(
            title = "Mock Interview & Resume Polish",
            nodeType = NodeType.PROJECT_STUDY,
            urgency = UrgencyTier.TODAY_MUST,
            startDate = null, // IDLE on the Board until mapped!
            endDate = null,
            allocatedHours = 6.0,
            rootTrackId = dsaTrackId,
            checklistItems = listOf("Update Kotlin project on CV", "2 Timed OA Mocks"),
            boardX = 420f,
            boardY = 300f
        )

        // 4. Create a Standalone Calendar Errand ("Place A" with Pocket Checklist)
        initializeTask(
            title = "Place A: Document & Bank Errand",
            nodeType = NodeType.ERRAND_TASK,
            urgency = UrgencyTier.CRITICAL,
            startDate = today,
            endDate = today,
            isAllDay = true, // Zero fake clock times!
            rootTrackId = null, // Standalone Calendar task (doesn't clutter the Miro Board)
            checklistItems = listOf("Carry ID / PAN Copy", "2 Passport Photos", "Get form stamped")
        )
    }
}
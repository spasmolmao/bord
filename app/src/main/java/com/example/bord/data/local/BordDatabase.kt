package com.example.bord.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.bord.domain.model.MicroTask
import com.example.bord.domain.model.NodeType
import com.example.bord.domain.model.RootTrack
import com.example.bord.domain.model.TaskNode
import com.example.bord.domain.model.UrgencyTier
import com.example.bord.domain.model.UserMomentum
import java.time.LocalDate

/**
 * LAYER 1: SINGLE SQLITE DATABASE
 * Both the Miro Board and the Calendar read/write to this exact same database.
 * Zero duplicate tables, zero sync bugs.
 */
class BordDatabase(context: Context) : SQLiteOpenHelper(context, "bord_planner.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true) // Enforces strict parent -> child links
    }

    override fun onCreate(db: SQLiteDatabase) {
        // TABLE 1: Tier 1 RootTracks (Macro Hour Bank & Miro Root Nodes)
        db.execSQL(
            """
            CREATE TABLE root_tracks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                target_hours REAL NOT NULL,
                invested_hours REAL NOT NULL DEFAULT 0.0,
                start_date TEXT NOT NULL,
                deadline_date TEXT NOT NULL,
                board_x REAL NOT NULL DEFAULT 100.0,
                board_y REAL NOT NULL DEFAULT 100.0,
                color_hex INTEGER NOT NULL DEFAULT 4282090486
            )
            """.trimIndent()
        )

        // TABLE 2: Tier 2 TaskNodes (Shared by Board & Calendar)
        db.execSQL(
            """
            CREATE TABLE task_nodes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                root_track_id INTEGER,
                title TEXT NOT NULL,
                node_type TEXT NOT NULL,
                urgency TEXT NOT NULL,
                start_date TEXT,
                end_date TEXT,
                is_all_day INTEGER NOT NULL DEFAULT 1,
                start_time_minutes INTEGER,
                duration_minutes INTEGER NOT NULL DEFAULT 60,
                buffer_minutes INTEGER NOT NULL DEFAULT 30,
                allocated_hours REAL NOT NULL DEFAULT 0.0,
                invested_hours REAL NOT NULL DEFAULT 0.0,
                is_completed INTEGER NOT NULL DEFAULT 0,
                board_x REAL NOT NULL DEFAULT 350.0,
                board_y REAL NOT NULL DEFAULT 220.0,
                FOREIGN KEY(root_track_id) REFERENCES root_tracks(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // TABLE 3: Tier 3 MicroTasks (Pocket Checklists & Persistent Project Checklists)
        db.execSQL(
            """
            CREATE TABLE micro_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_node_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                is_completed INTEGER NOT NULL DEFAULT 0,
                pinned_date TEXT,
                estimated_hours REAL,
                FOREIGN KEY(task_node_id) REFERENCES task_nodes(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // TABLE 4: User Momentum & Streak Bar
        db.execSQL(
            """
            CREATE TABLE user_momentum (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                points INTEGER NOT NULL DEFAULT 100,
                streak_days INTEGER NOT NULL DEFAULT 0,
                multiplier REAL NOT NULL DEFAULT 1.0,
                last_audit_date TEXT NOT NULL
            )
            """.trimIndent()
        )

        // Initialize default Momentum row
        val initMomentum = ContentValues().apply {
            put("id", 1)
            put("points", 100)
            put("streak_days", 0)
            put("multiplier", 1.0)
            put("last_audit_date", LocalDate.now().toString())
        }
        db.insert("user_momentum", null, initMomentum)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS micro_tasks")
        db.execSQL("DROP TABLE IF EXISTS task_nodes")
        db.execSQL("DROP TABLE IF EXISTS root_tracks")
        db.execSQL("DROP TABLE IF EXISTS user_momentum")
        onCreate(db)
    }

    // --- ROOT TRACK CRUD ---

    fun insertRootTrack(track: RootTrack): Long {
        val values = ContentValues().apply {
            put("title", track.title)
            put("target_hours", track.targetHours)
            put("invested_hours", track.investedHours)
            put("start_date", track.startDate.toString())
            put("deadline_date", track.deadlineDate.toString())
            put("board_x", track.boardX)
            put("board_y", track.boardY)
            put("color_hex", track.colorHex)
        }
        return writableDatabase.insert("root_tracks", null, values)
    }

    fun getAllRootTracks(): List<RootTrack> {
        val tracks = mutableListOf<RootTrack>()
        readableDatabase.rawQuery("SELECT * FROM root_tracks ORDER BY deadline_date ASC", null).use { c ->
            while (c.moveToNext()) {
                tracks.add(
                    RootTrack(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        targetHours = c.getDouble(c.getColumnIndexOrThrow("target_hours")),
                        investedHours = c.getDouble(c.getColumnIndexOrThrow("invested_hours")),
                        startDate = LocalDate.parse(c.getString(c.getColumnIndexOrThrow("start_date"))),
                        deadlineDate = LocalDate.parse(c.getString(c.getColumnIndexOrThrow("deadline_date"))),
                        boardX = c.getFloat(c.getColumnIndexOrThrow("board_x")),
                        boardY = c.getFloat(c.getColumnIndexOrThrow("board_y")),
                        colorHex = c.getLong(c.getColumnIndexOrThrow("color_hex"))
                    )
                )
            }
        }
        return tracks
    }

    fun updateTrackBoardPosition(trackId: Long, x: Float, y: Float) {
        val values = ContentValues().apply {
            put("board_x", x)
            put("board_y", y)
        }
        writableDatabase.update("root_tracks", values, "id = ?", arrayOf(trackId.toString()))
    }

    // --- TASK NODE & MICRO-TASK CRUD ---

    fun insertTaskNodeWithChecklist(node: TaskNode, checklistTitles: List<String>): Long {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val values = ContentValues().apply {
                if (node.rootTrackId != null) put("root_track_id", node.rootTrackId) else putNull("root_track_id")
                put("title", node.title)
                put("node_type", node.nodeType.name)
                put("urgency", node.urgency.name)
                if (node.startDate != null) put("start_date", node.startDate.toString()) else putNull("start_date")
                if (node.endDate != null) put("end_date", node.endDate.toString()) else putNull("end_date")
                put("is_all_day", if (node.isAllDay) 1 else 0)
                if (node.startTimeMinutes != null) put("start_time_minutes", node.startTimeMinutes) else putNull("start_time_minutes")
                put("duration_minutes", node.durationMinutes)
                put("buffer_minutes", node.bufferMinutes)
                put("allocated_hours", node.allocatedHours)
                put("invested_hours", node.investedHours)
                put("is_completed", if (node.isCompleted) 1 else 0)
                put("board_x", node.boardX)
                put("board_y", node.boardY)
            }
            val newNodeId = db.insert("task_nodes", null, values)

            checklistTitles.filter { it.isNotBlank() }.forEach { itemTitle ->
                val microValues = ContentValues().apply {
                    put("task_node_id", newNodeId)
                    put("title", itemTitle.trim())
                    put("is_completed", 0)
                }
                db.insert("micro_tasks", null, microValues)
            }
            db.setTransactionSuccessful()
            newNodeId
        } finally {
            db.endTransaction()
        }
    }

    fun getAllTaskNodes(): List<TaskNode> {
        val microMap = mutableMapOf<Long, MutableList<MicroTask>>()
        readableDatabase.rawQuery("SELECT * FROM micro_tasks ORDER BY id ASC", null).use { mc ->
            while (mc.moveToNext()) {
                val nodeId = mc.getLong(mc.getColumnIndexOrThrow("task_node_id"))
                val pinnedIdx = mc.getColumnIndexOrThrow("pinned_date")
                val estIdx = mc.getColumnIndexOrThrow("estimated_hours")
                val item = MicroTask(
                    id = mc.getLong(mc.getColumnIndexOrThrow("id")),
                    taskNodeId = nodeId,
                    title = mc.getString(mc.getColumnIndexOrThrow("title")),
                    isCompleted = mc.getInt(mc.getColumnIndexOrThrow("is_completed")) == 1,
                    pinnedDate = if (mc.isNull(pinnedIdx)) null else LocalDate.parse(mc.getString(pinnedIdx)),
                    estimatedHours = if (mc.isNull(estIdx)) null else mc.getDouble(estIdx)
                )
                microMap.getOrPut(nodeId) { mutableListOf() }.add(item)
            }
        }

        val nodes = mutableListOf<TaskNode>()
        readableDatabase.rawQuery("SELECT * FROM task_nodes ORDER BY id ASC", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow("id"))
                val trackIdx = c.getColumnIndexOrThrow("root_track_id")
                val startIdx = c.getColumnIndexOrThrow("start_date")
                val endIdx = c.getColumnIndexOrThrow("end_date")
                val timeIdx = c.getColumnIndexOrThrow("start_time_minutes")

                nodes.add(
                    TaskNode(
                        id = id,
                        rootTrackId = if (c.isNull(trackIdx)) null else c.getLong(trackIdx),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        nodeType = NodeType.valueOf(c.getString(c.getColumnIndexOrThrow("node_type"))),
                        urgency = UrgencyTier.valueOf(c.getString(c.getColumnIndexOrThrow("urgency"))),
                        startDate = if (c.isNull(startIdx)) null else LocalDate.parse(c.getString(startIdx)),
                        endDate = if (c.isNull(endIdx)) null else LocalDate.parse(c.getString(endIdx)),
                        isAllDay = c.getInt(c.getColumnIndexOrThrow("is_all_day")) == 1,
                        startTimeMinutes = if (c.isNull(timeIdx)) null else c.getInt(timeIdx),
                        durationMinutes = c.getInt(c.getColumnIndexOrThrow("duration_minutes")),
                        bufferMinutes = c.getInt(c.getColumnIndexOrThrow("buffer_minutes")),
                        allocatedHours = c.getDouble(c.getColumnIndexOrThrow("allocated_hours")),
                        investedHours = c.getDouble(c.getColumnIndexOrThrow("invested_hours")),
                        isCompleted = c.getInt(c.getColumnIndexOrThrow("is_completed")) == 1,
                        boardX = c.getFloat(c.getColumnIndexOrThrow("board_x")),
                        boardY = c.getFloat(c.getColumnIndexOrThrow("board_y")),
                        microTasks = microMap[id] ?: emptyList()
                    )
                )
            }
        }
        return nodes
    }

    fun updateNodeScheduleAndHours(nodeId: Long, startDate: LocalDate, endDate: LocalDate, allocatedHours: Double) {
        val values = ContentValues().apply {
            put("start_date", startDate.toString())
            put("end_date", endDate.toString())
            put("allocated_hours", allocatedHours)
        }
        writableDatabase.update("task_nodes", values, "id = ?", arrayOf(nodeId.toString()))
    }

    fun updateNodeBoardPosition(nodeId: Long, x: Float, y: Float) {
        val values = ContentValues().apply {
            put("board_x", x)
            put("board_y", y)
        }
        writableDatabase.update("task_nodes", values, "id = ?", arrayOf(nodeId.toString()))
    }

    fun updateNodeDuration(nodeId: Long, newDurationMinutes: Int) {
        val values = ContentValues().apply {
            put("duration_minutes", newDurationMinutes)
        }
        writableDatabase.update("task_nodes", values, "id = ?", arrayOf(nodeId.toString()))
    }

    fun setNodeCompleted(nodeId: Long, completed: Boolean) {
        val values = ContentValues().apply {
            put("is_completed", if (completed) 1 else 0)
        }
        writableDatabase.update("task_nodes", values, "id = ?", arrayOf(nodeId.toString()))
    }

    fun addMicroTask(taskNodeId: Long, title: String, pinnedDate: LocalDate? = null) {
        val values = ContentValues().apply {
            put("task_node_id", taskNodeId)
            put("title", title.trim())
            put("is_completed", 0)
            if (pinnedDate != null) put("pinned_date", pinnedDate.toString()) else putNull("pinned_date")
        }
        writableDatabase.insert("micro_tasks", null, values)
    }

    fun toggleMicroTask(microTaskId: Long, completed: Boolean) {
        val values = ContentValues().apply {
            put("is_completed", if (completed) 1 else 0)
        }
        writableDatabase.update("micro_tasks", values, "id = ?", arrayOf(microTaskId.toString()))
    }

    fun updateMicroTaskPinnedDate(microTaskId: Long, pinnedDate: LocalDate?) {
        val values = ContentValues().apply {
            if (pinnedDate != null) put("pinned_date", pinnedDate.toString()) else putNull("pinned_date")
        }
        writableDatabase.update("micro_tasks", values, "id = ?", arrayOf(microTaskId.toString()))
    }

    fun addInvestedHoursAtomic(nodeId: Long, rootTrackId: Long?, hoursDelta: Double) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE task_nodes SET invested_hours = invested_hours + ? WHERE id = ?",
                arrayOf<Any>(hoursDelta, nodeId)
            )
            if (rootTrackId != null) {
                db.execSQL(
                    "UPDATE root_tracks SET invested_hours = invested_hours + ? WHERE id = ?",
                    arrayOf<Any>(hoursDelta, rootTrackId)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // --- MOMENTUM & STREAK CRUD ---

    fun getUserMomentum(): UserMomentum {
        readableDatabase.rawQuery("SELECT * FROM user_momentum WHERE id = 1", null).use { c ->
            if (c.moveToFirst()) {
                return UserMomentum(
                    points = c.getInt(c.getColumnIndexOrThrow("points")),
                    currentStreakDays = c.getInt(c.getColumnIndexOrThrow("streak_days")),
                    multiplier = c.getDouble(c.getColumnIndexOrThrow("multiplier")),
                    lastAuditDate = LocalDate.parse(c.getString(c.getColumnIndexOrThrow("last_audit_date")))
                )
            }
        }
        return UserMomentum()
    }

    fun saveUserMomentum(momentum: UserMomentum) {
        val values = ContentValues().apply {
            put("points", momentum.points)
            put("streak_days", momentum.currentStreakDays)
            put("multiplier", momentum.multiplier)
            put("last_audit_date", momentum.lastAuditDate.toString())
        }
        writableDatabase.update("user_momentum", values, "id = 1", null)
    }
}
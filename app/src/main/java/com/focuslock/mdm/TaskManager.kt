package com.focuslock.mdm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

enum class TaskRepeat {
    DAILY,
    WEEKLY,
    MONTHLY
}

enum class TaskValidationMode {
    MANUAL,
    APP_TIMER
}

data class TaskTimeBlock(
    val startMinutes: Int,
    val endMinutes: Int
)

data class TaskChecklistItem(
    val text: String,
    val done: Boolean
)

data class TaskItem(
    val id: String,
    val title: String,
    val durationMinutes: Int,
    val allowedApps: List<String>,
    val repeat: TaskRepeat,
    val daysOfWeek: List<Int>,
    val dayOfMonth: Int,
    val timeBlocks: List<TaskTimeBlock>,
    val priority: Int,
    val tags: List<String>,
    val notes: String,
    val location: String,
    val checklist: List<TaskChecklistItem>,
    val customFields: Map<String, String>,
    val validationMode: TaskValidationMode
)

data class TaskProgress(
    val taskId: String,
    val dateKey: String,
    val minutesDone: Int,
    val manualDone: Boolean
)

data class TaskActiveWindow(
    val task: TaskItem,
    val block: TaskTimeBlock
)

object TaskManager {

    private fun prefs(context: Context) =
        context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)

    fun getTasks(context: Context): List<TaskItem> {
        val raw = prefs(context).getString(Constants.KEY_TASKS_JSON, null) ?: return emptyList()
        return parseTasks(raw)
    }

    fun saveTasks(context: Context, tasks: List<TaskItem>) {
        prefs(context).edit().putString(Constants.KEY_TASKS_JSON, serializeTasks(tasks)).apply()
    }

    fun addTask(context: Context, task: TaskItem) {
        val current = getTasks(context).toMutableList()
        current.add(task)
        saveTasks(context, current)
    }

    fun updateTask(context: Context, updated: TaskItem) {
        val current = getTasks(context).map { if (it.id == updated.id) updated else it }
        saveTasks(context, current)
    }

    fun removeTask(context: Context, taskId: String) {
        val current = getTasks(context).filterNot { it.id == taskId }
        saveTasks(context, current)
        removeProgress(context, taskId)
    }

    fun newTask(
        title: String,
        durationMinutes: Int,
        allowedApps: List<String>,
        repeat: TaskRepeat,
        daysOfWeek: List<Int>,
        dayOfMonth: Int,
        timeBlocks: List<TaskTimeBlock>,
        priority: Int,
        tags: List<String>,
        notes: String,
        location: String,
        checklist: List<TaskChecklistItem>,
        customFields: Map<String, String>,
        validationMode: TaskValidationMode
    ): TaskItem {
        return TaskItem(
            id = UUID.randomUUID().toString(),
            title = title,
            durationMinutes = durationMinutes,
            allowedApps = allowedApps,
            repeat = repeat,
            daysOfWeek = daysOfWeek,
            dayOfMonth = dayOfMonth,
            timeBlocks = timeBlocks,
            priority = priority,
            tags = tags,
            notes = notes,
            location = location,
            checklist = checklist,
            customFields = customFields,
            validationMode = validationMode
        )
    }

    fun getActiveTaskWindow(context: Context, now: Calendar = Calendar.getInstance()): TaskActiveWindow? {
        val tasks = getTasks(context)
        if (tasks.isEmpty()) return null

        val active = tasks
            .filter { matchesRepeat(it, now) }
            .flatMap { task ->
                task.timeBlocks.mapNotNull { block ->
                    if (isWithinBlock(block, now)) TaskActiveWindow(task, block) else null
                }
            }
            .sortedWith(
                compareByDescending<TaskActiveWindow> { it.task.priority }
                    .thenBy { it.block.startMinutes }
            )
            .firstOrNull()

        if (active == null) return null
        return if (isTaskCompleted(context, active.task)) null else active
    }

    fun recordFocusTime(context: Context, taskId: String, deltaMs: Long) {
        if (deltaMs <= 0L) return
        val dateKey = todayKey()
        val progress = getProgress(context).toMutableList()
        val existing = progress.firstOrNull { it.taskId == taskId && it.dateKey == dateKey }
        val deltaMinutes = (deltaMs / 60_000).toInt().coerceAtLeast(0)
        if (deltaMinutes == 0) return

        if (existing == null) {
            progress.add(TaskProgress(taskId, dateKey, deltaMinutes, manualDone = false))
        } else {
            val updated = existing.copy(minutesDone = existing.minutesDone + deltaMinutes)
            progress[progress.indexOf(existing)] = updated
        }
        saveProgress(context, progress)
    }

    fun markManualDone(context: Context, taskId: String) {
        val dateKey = todayKey()
        val progress = getProgress(context).toMutableList()
        val existing = progress.firstOrNull { it.taskId == taskId && it.dateKey == dateKey }
        if (existing == null) {
            progress.add(TaskProgress(taskId, dateKey, minutesDone = 0, manualDone = true))
        } else {
            val updated = existing.copy(manualDone = true)
            progress[progress.indexOf(existing)] = updated
        }
        saveProgress(context, progress)
    }

    fun getProgressForTask(context: Context, taskId: String): TaskProgress? {
        val dateKey = todayKey()
        return getProgress(context).firstOrNull { it.taskId == taskId && it.dateKey == dateKey }
    }

    fun isTaskCompleted(context: Context, task: TaskItem): Boolean {
        val progress = getProgressForTask(context, task.id)
        return when (task.validationMode) {
            TaskValidationMode.MANUAL -> progress?.manualDone == true
            TaskValidationMode.APP_TIMER -> (progress?.minutesDone ?: 0) >= task.durationMinutes
        }
    }

    fun getCompletionRatio(context: Context): Pair<Int, Int> {
        val tasks = getTasks(context)
        val total = tasks.size
        val completed = tasks.count { isTaskCompleted(context, it) }
        return completed to total
    }

    fun getPendingTasks(context: Context, now: Calendar = Calendar.getInstance()): List<TaskItem> {
        return getTasks(context)
            .filter { matchesRepeat(it, now) }
            .filterNot { isTaskCompleted(context, it) }
    }

    fun areAllTasksCompleted(context: Context, now: Calendar = Calendar.getInstance()): Boolean {
        return getPendingTasks(context, now).isEmpty()
    }

    fun getAllowedAppsForTasks(tasks: List<TaskItem>): Set<String> {
        return tasks.flatMap { it.allowedApps }.toSet()
    }

    fun pickTimerTask(tasks: List<TaskItem>, packageName: String): TaskItem? {
        return tasks
            .filter { packageName in it.allowedApps }
            .sortedByDescending { it.priority }
            .firstOrNull()
    }

    private fun matchesRepeat(task: TaskItem, now: Calendar): Boolean {
        return when (task.repeat) {
            TaskRepeat.DAILY -> true
            TaskRepeat.WEEKLY -> {
                if (task.daysOfWeek.isEmpty()) true
                else task.daysOfWeek.contains(now.get(Calendar.DAY_OF_WEEK))
            }
            TaskRepeat.MONTHLY -> {
                val day = task.dayOfMonth
                day in 1..31 && now.get(Calendar.DAY_OF_MONTH) == day
            }
        }
    }

    private fun isWithinBlock(block: TaskTimeBlock, now: Calendar): Boolean {
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = block.startMinutes
        val end = block.endMinutes
        if (start == end) return false
        return if (end > start) {
            nowMinutes in start until end
        } else {
            nowMinutes >= start || nowMinutes < end
        }
    }

    private fun todayKey(): String {
        val format = SimpleDateFormat("yyyyMMdd", Locale.US)
        return format.format(System.currentTimeMillis())
    }

    private fun getProgress(context: Context): List<TaskProgress> {
        val raw = prefs(context).getString(Constants.KEY_TASK_PROGRESS_JSON, null) ?: return emptyList()
        return parseProgress(raw)
    }

    private fun saveProgress(context: Context, progress: List<TaskProgress>) {
        prefs(context).edit().putString(Constants.KEY_TASK_PROGRESS_JSON, serializeProgress(progress)).apply()
    }

    private fun removeProgress(context: Context, taskId: String) {
        val progress = getProgress(context).filterNot { it.taskId == taskId }
        saveProgress(context, progress)
    }

    private fun serializeTasks(tasks: List<TaskItem>): String {
        val array = JSONArray()
        tasks.forEach { task ->
            val obj = JSONObject()
            obj.put("id", task.id)
            obj.put("title", task.title)
            obj.put("durationMinutes", task.durationMinutes)
            obj.put("repeat", task.repeat.name)
            obj.put("dayOfMonth", task.dayOfMonth)
            obj.put("priority", task.priority)
            obj.put("notes", task.notes)
            obj.put("location", task.location)
            obj.put("validationMode", task.validationMode.name)

            val apps = JSONArray()
            task.allowedApps.forEach { apps.put(it) }
            obj.put("allowedApps", apps)

            val days = JSONArray()
            task.daysOfWeek.forEach { days.put(it) }
            obj.put("daysOfWeek", days)

            val tags = JSONArray()
            task.tags.forEach { tags.put(it) }
            obj.put("tags", tags)

            val blocks = JSONArray()
            task.timeBlocks.forEach { block ->
                val blockObj = JSONObject()
                blockObj.put("startMinutes", block.startMinutes)
                blockObj.put("endMinutes", block.endMinutes)
                blocks.put(blockObj)
            }
            obj.put("timeBlocks", blocks)

            val checklist = JSONArray()
            task.checklist.forEach { item ->
                val itemObj = JSONObject()
                itemObj.put("text", item.text)
                itemObj.put("done", item.done)
                checklist.put(itemObj)
            }
            obj.put("checklist", checklist)

            val custom = JSONObject()
            task.customFields.forEach { (key, value) -> custom.put(key, value) }
            obj.put("customFields", custom)

            array.put(obj)
        }
        return array.toString()
    }

    private fun parseTasks(raw: String): List<TaskItem> {
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val repeat = runCatching { TaskRepeat.valueOf(obj.optString("repeat")) }
                    .getOrElse { TaskRepeat.DAILY }
                val validation = runCatching { TaskValidationMode.valueOf(obj.optString("validationMode")) }
                    .getOrElse { TaskValidationMode.MANUAL }

                val days = obj.optJSONArray("daysOfWeek")?.let { daysArray ->
                    (0 until daysArray.length()).mapNotNull { i ->
                        val day = daysArray.optInt(i, -1)
                        if (day in 1..7) day else null
                    }
                } ?: emptyList()

                val apps = obj.optJSONArray("allowedApps")?.let { appsArray ->
                    (0 until appsArray.length()).mapNotNull { i ->
                        appsArray.optString(i).ifBlank { null }
                    }
                } ?: emptyList()

                val tags = obj.optJSONArray("tags")?.let { tagsArray ->
                    (0 until tagsArray.length()).mapNotNull { i ->
                        tagsArray.optString(i).ifBlank { null }
                    }
                } ?: emptyList()

                val blocks = obj.optJSONArray("timeBlocks")?.let { blockArray ->
                    (0 until blockArray.length()).mapNotNull { i ->
                        val blockObj = blockArray.optJSONObject(i) ?: return@mapNotNull null
                        TaskTimeBlock(
                            startMinutes = blockObj.optInt("startMinutes"),
                            endMinutes = blockObj.optInt("endMinutes")
                        )
                    }
                } ?: emptyList()

                val checklist = obj.optJSONArray("checklist")?.let { listArray ->
                    (0 until listArray.length()).mapNotNull { i ->
                        val itemObj = listArray.optJSONObject(i) ?: return@mapNotNull null
                        TaskChecklistItem(
                            text = itemObj.optString("text"),
                            done = itemObj.optBoolean("done")
                        )
                    }
                } ?: emptyList()

                val customFields = obj.optJSONObject("customFields")?.let { customObj ->
                    customObj.keys().asSequence().associateWith { key ->
                        customObj.optString(key)
                    }
                } ?: emptyMap()

                TaskItem(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    durationMinutes = obj.optInt("durationMinutes"),
                    allowedApps = apps,
                    repeat = repeat,
                    daysOfWeek = days,
                    dayOfMonth = obj.optInt("dayOfMonth"),
                    timeBlocks = blocks,
                    priority = obj.optInt("priority"),
                    tags = tags,
                    notes = obj.optString("notes"),
                    location = obj.optString("location"),
                    checklist = checklist,
                    customFields = customFields,
                    validationMode = validation
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeProgress(progress: List<TaskProgress>): String {
        val array = JSONArray()
        progress.forEach { entry ->
            val obj = JSONObject()
            obj.put("taskId", entry.taskId)
            obj.put("dateKey", entry.dateKey)
            obj.put("minutesDone", entry.minutesDone)
            obj.put("manualDone", entry.manualDone)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseProgress(raw: String): List<TaskProgress> {
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                TaskProgress(
                    taskId = obj.optString("taskId"),
                    dateKey = obj.optString("dateKey"),
                    minutesDone = obj.optInt("minutesDone"),
                    manualDone = obj.optBoolean("manualDone")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

package com.focuslock.mdm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

enum class RepeatType {
    DAILY,
    WEEKLY,
    MONTHLY
}

enum class ScheduleSource {
    MANUAL,
    TASK
}

data class ScheduleWindow(
    val id: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val repeat: RepeatType,
    val daysOfWeek: List<Int>,
    val dayOfMonth: Int,
    val message: String,
    val source: ScheduleSource = ScheduleSource.MANUAL,
    val taskId: String? = null
)

object ScheduleManager {

    private fun prefs(context: Context) =
        context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)

    fun getSchedules(context: Context): List<ScheduleWindow> {
        val raw = prefs(context).getString(Constants.KEY_SCHEDULE_JSON, null) ?: return emptyList()
        return parseSchedules(raw)
    }

    fun saveSchedules(context: Context, schedules: List<ScheduleWindow>) {
        val json = serializeSchedules(schedules)
        prefs(context).edit().putString(Constants.KEY_SCHEDULE_JSON, json).apply()
    }

    fun addSchedule(context: Context, schedule: ScheduleWindow) {
        val current = getSchedules(context).toMutableList()
        current.add(schedule)
        saveSchedules(context, current)
    }

    fun removeSchedule(context: Context, id: String) {
        val current = getSchedules(context).filterNot { it.id == id }
        saveSchedules(context, current)
    }

    fun updateSchedule(context: Context, updated: ScheduleWindow) {
        val current = getSchedules(context).map { if (it.id == updated.id) updated else it }
        saveSchedules(context, current)
    }

    fun newSchedule(
        startMinutes: Int,
        endMinutes: Int,
        repeat: RepeatType,
        daysOfWeek: List<Int>,
        dayOfMonth: Int,
        message: String
    ): ScheduleWindow {
        return ScheduleWindow(
            id = UUID.randomUUID().toString(),
            startMinutes = startMinutes,
            endMinutes = endMinutes,
            repeat = repeat,
            daysOfWeek = daysOfWeek,
            dayOfMonth = dayOfMonth,
            message = message
        )
    }

    fun getActiveWindow(context: Context, now: Calendar = Calendar.getInstance()): ScheduleWindow? {
        val schedules = getSchedules(context)
        return schedules.firstOrNull { schedule ->
            matchesRepeat(schedule, now) && isWithinWindow(schedule, now)
        }
    }

    private fun matchesRepeat(schedule: ScheduleWindow, now: Calendar): Boolean {
        return when (schedule.repeat) {
            RepeatType.DAILY -> true
            RepeatType.WEEKLY -> {
                if (schedule.daysOfWeek.isEmpty()) true
                else schedule.daysOfWeek.contains(now.get(Calendar.DAY_OF_WEEK))
            }
            RepeatType.MONTHLY -> {
                val day = schedule.dayOfMonth
                day in 1..31 && now.get(Calendar.DAY_OF_MONTH) == day
            }
        }
    }

    private fun isWithinWindow(schedule: ScheduleWindow, now: Calendar): Boolean {
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = schedule.startMinutes
        val end = schedule.endMinutes
        if (start == end) return false
        return if (end > start) {
            nowMinutes in start until end
        } else {
            nowMinutes >= start || nowMinutes < end
        }
    }

    private fun serializeSchedules(schedules: List<ScheduleWindow>): String {
        val array = JSONArray()
        schedules.forEach { schedule ->
            val obj = JSONObject()
            obj.put("id", schedule.id)
            obj.put("startMinutes", schedule.startMinutes)
            obj.put("endMinutes", schedule.endMinutes)
            obj.put("repeat", schedule.repeat.name)
            obj.put("dayOfMonth", schedule.dayOfMonth)
            obj.put("message", schedule.message)
            obj.put("source", schedule.source.name)
            obj.put("taskId", schedule.taskId ?: "")
            val days = JSONArray()
            schedule.daysOfWeek.forEach { days.put(it) }
            obj.put("daysOfWeek", days)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseSchedules(raw: String): List<ScheduleWindow> {
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val repeat = runCatching { RepeatType.valueOf(obj.optString("repeat")) }
                    .getOrElse { RepeatType.DAILY }
                val source = runCatching { ScheduleSource.valueOf(obj.optString("source")) }
                    .getOrElse { ScheduleSource.MANUAL }
                val days = obj.optJSONArray("daysOfWeek")?.let { daysArray ->
                    (0 until daysArray.length()).mapNotNull { i ->
                        val day = daysArray.optInt(i, -1)
                        if (day in 1..7) day else null
                    }
                } ?: emptyList()
                ScheduleWindow(
                    id = obj.optString("id"),
                    startMinutes = obj.optInt("startMinutes"),
                    endMinutes = obj.optInt("endMinutes"),
                    repeat = repeat,
                    daysOfWeek = days,
                    dayOfMonth = obj.optInt("dayOfMonth"),
                    message = obj.optString("message"),
                    source = source,
                    taskId = obj.optString("taskId").ifBlank { null }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

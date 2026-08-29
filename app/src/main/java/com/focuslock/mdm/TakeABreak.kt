package com.focuslock.mdm

import android.content.Context
import org.json.JSONObject

/**
 * A short, deliberate, counted exception.
 *
 * All-or-nothing blocking is what turns one slip into an abandoned session, so
 * FocusLock lets you open a blocked app on purpose for a few minutes and then
 * closes it again. Taking a break is not a failure state and is never described
 * as one.
 */
object TakeABreak {

    private const val KEY_PASSES = "break_passes_json"
    private const val KEY_USED_TODAY = "break_used_today_json"

    private const val PARAM_MINUTES = "minutes"
    private const val PARAM_DAILY_MAX = "dailyMax"

    const val DEFAULT_MINUTES = 5
    const val DEFAULT_DAILY_MAX = 3

    fun isAvailable(context: Context): Boolean =
        CapabilityRegistry.isEnabled(context, Capabilities.TAKE_A_BREAK)

    fun breakMinutes(context: Context): Int =
        CapabilityRegistry.getIntParam(context, Capabilities.TAKE_A_BREAK, PARAM_MINUTES, DEFAULT_MINUTES)
            .coerceIn(1, 60)

    fun setBreakMinutes(context: Context, minutes: Int) {
        CapabilityRegistry.setIntParam(
            context,
            Capabilities.TAKE_A_BREAK,
            PARAM_MINUTES,
            minutes.coerceIn(1, 60)
        )
    }

    fun dailyMax(context: Context): Int =
        CapabilityRegistry.getIntParam(context, Capabilities.TAKE_A_BREAK, PARAM_DAILY_MAX, DEFAULT_DAILY_MAX)
            .coerceIn(0, 20)

    fun setDailyMax(context: Context, value: Int) {
        CapabilityRegistry.setIntParam(
            context,
            Capabilities.TAKE_A_BREAK,
            PARAM_DAILY_MAX,
            value.coerceIn(0, 20)
        )
    }

    // ── Passes ────────────────────────────────────────────────────

    /** Milliseconds left on an active pass, or 0 when there is none. */
    fun remainingMs(context: Context, packageName: String): Long {
        val passes = FocusStore.getJsonObject(context, KEY_PASSES)
        val expiry = passes.optLong(packageName, 0L)
        if (expiry <= 0L) return 0L
        val remaining = expiry - System.currentTimeMillis()
        if (remaining <= 0L) {
            passes.remove(packageName)
            FocusStore.setJsonObject(context, KEY_PASSES, passes)
            return 0L
        }
        return remaining
    }

    fun hasActivePass(context: Context, packageName: String): Boolean =
        remainingMs(context, packageName) > 0L

    fun activePasses(context: Context): Map<String, Long> {
        val passes = FocusStore.getJsonObject(context, KEY_PASSES)
        val now = System.currentTimeMillis()
        val out = LinkedHashMap<String, Long>()
        val keys = passes.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val expiry = passes.optLong(key, 0L)
            if (expiry > now) out[key] = expiry - now
        }
        return out
    }

    fun usedToday(context: Context): Int {
        val record = FocusStore.getJsonObject(context, KEY_USED_TODAY)
        if (record.optString("day", "") != todayKey()) return 0
        return record.optInt("count", 0)
    }

    fun remainingToday(context: Context): Int {
        val max = dailyMax(context)
        if (max <= 0) return 0
        return (max - usedToday(context)).coerceAtLeast(0)
    }

    fun canStart(context: Context): Boolean = isAvailable(context) && remainingToday(context) > 0

    /** Grants a pass. Returns false when the daily allowance is already spent. */
    fun start(context: Context, packageName: String): Boolean {
        if (!canStart(context)) return false

        val passes = FocusStore.getJsonObject(context, KEY_PASSES)
        passes.put(packageName, System.currentTimeMillis() + breakMinutes(context) * 60_000L)
        FocusStore.setJsonObject(context, KEY_PASSES, passes)

        val record = JSONObject()
        record.put("day", todayKey())
        record.put("count", usedToday(context) + 1)
        FocusStore.setJsonObject(context, KEY_USED_TODAY, record)

        PolicySync.request(context, "break:" + packageName)
        return true
    }

    fun endEarly(context: Context, packageName: String) {
        val passes = FocusStore.getJsonObject(context, KEY_PASSES)
        passes.remove(packageName)
        FocusStore.setJsonObject(context, KEY_PASSES, passes)
        PolicySync.request(context, "breakEnd:" + packageName)
    }

    fun clearAll(context: Context) {
        FocusStore.setJsonObject(context, KEY_PASSES, JSONObject())
        PolicySync.request(context, "breakClear")
    }

    private fun todayKey(): String {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.YEAR).toString() + "-" +
            calendar.get(java.util.Calendar.DAY_OF_YEAR)
    }
}

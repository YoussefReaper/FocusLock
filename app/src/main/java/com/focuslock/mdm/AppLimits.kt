package com.focuslock.mdm

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/**
 * Daily budgets: minutes of use, and number of opens.
 *
 * Both are read from Android's own usage stats rather than a private counter,
 * so the numbers match what Digital Wellbeing shows and survive FocusLock being
 * killed. Results are cached briefly because the enforcement loop asks often.
 */
object AppLimits {

    private const val KEY_MINUTE_LIMITS = "app_minute_limits_json"
    private const val KEY_OPEN_LIMITS = "app_open_limits_json"

    private const val CACHE_TTL_MS = 20_000L

    private var minutesCache: Map<String, Long> = emptyMap()
    private var opensCache: Map<String, Int> = emptyMap()
    private var cacheStamp: Long = 0L

    // ── Configuration ─────────────────────────────────────────────

    fun minuteLimit(context: Context, packageName: String): Int? {
        if (!CapabilityRegistry.isEnabled(context, Capabilities.PER_APP_LIMITS)) return null
        val value = FocusStore.getIntMap(context, KEY_MINUTE_LIMITS)[packageName] ?: return null
        return if (value > 0) value else null
    }

    fun setMinuteLimit(context: Context, packageName: String, minutes: Int?) {
        val map = FocusStore.getIntMap(context, KEY_MINUTE_LIMITS).toMutableMap()
        if (minutes == null || minutes <= 0) map.remove(packageName) else map[packageName] = minutes
        FocusStore.setIntMap(context, KEY_MINUTE_LIMITS, map)
        PolicySync.request(context, "minuteLimit:" + packageName)
    }

    fun allMinuteLimits(context: Context): Map<String, Int> =
        FocusStore.getIntMap(context, KEY_MINUTE_LIMITS).filterValues { it > 0 }

    fun openLimit(context: Context, packageName: String): Int? {
        if (!CapabilityRegistry.isEnabled(context, Capabilities.OPEN_COUNT_LIMITS)) return null
        val value = FocusStore.getIntMap(context, KEY_OPEN_LIMITS)[packageName] ?: return null
        return if (value > 0) value else null
    }

    fun setOpenLimit(context: Context, packageName: String, opens: Int?) {
        val map = FocusStore.getIntMap(context, KEY_OPEN_LIMITS).toMutableMap()
        if (opens == null || opens <= 0) map.remove(packageName) else map[packageName] = opens
        FocusStore.setIntMap(context, KEY_OPEN_LIMITS, map)
        PolicySync.request(context, "openLimit:" + packageName)
    }

    fun allOpenLimits(context: Context): Map<String, Int> =
        FocusStore.getIntMap(context, KEY_OPEN_LIMITS).filterValues { it > 0 }

    fun hasAnyLimit(context: Context, packageName: String): Boolean =
        minuteLimit(context, packageName) != null || openLimit(context, packageName) != null

    /**
     * Whether any budget is both set and switched on.
     *
     * The editors deliberately still show budgets whose capability is off, so a
     * person can set one up before enabling it; enforcement uses this instead,
     * so a disabled capability costs nothing at runtime.
     */
    fun hasEnforceableBudgets(context: Context): Boolean {
        if (CapabilityRegistry.isEnabled(context, Capabilities.PER_APP_LIMITS) &&
            allMinuteLimits(context).isNotEmpty()
        ) {
            return true
        }
        return CapabilityRegistry.isEnabled(context, Capabilities.OPEN_COUNT_LIMITS) &&
            allOpenLimits(context).isNotEmpty()
    }

    // ── Today's usage ─────────────────────────────────────────────

    fun usedMinutesToday(context: Context, packageName: String): Int {
        refreshCache(context)
        val ms = minutesCache[packageName] ?: 0L
        return (ms / 60_000L).toInt()
    }

    fun opensToday(context: Context, packageName: String): Int {
        refreshCache(context)
        return opensCache[packageName] ?: 0
    }

    fun minutesRemaining(context: Context, packageName: String): Int? {
        val limit = minuteLimit(context, packageName) ?: return null
        return (limit - usedMinutesToday(context, packageName)).coerceAtLeast(0)
    }

    fun opensRemaining(context: Context, packageName: String): Int? {
        val limit = openLimit(context, packageName) ?: return null
        return (limit - opensToday(context, packageName)).coerceAtLeast(0)
    }

    /** Null when there is no budget; otherwise the reason the budget is spent. */
    fun exhaustedReason(context: Context, packageName: String): String? {
        minuteLimit(context, packageName)?.let { limit ->
            val used = usedMinutesToday(context, packageName)
            if (used >= limit) {
                return "Today's " + limit + " minutes are used up. It comes back at midnight."
            }
        }
        openLimit(context, packageName)?.let { limit ->
            val opens = opensToday(context, packageName)
            if (opens >= limit) {
                return "That is open number " + opens + " today, and you set the cap at " + limit + "."
            }
        }
        return null
    }

    fun invalidate() {
        cacheStamp = 0L
    }

    private fun startOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    @Synchronized
    private fun refreshCache(context: Context) {
        val now = System.currentTimeMillis()
        if (now - cacheStamp < CACHE_TTL_MS) return
        cacheStamp = now

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val start = startOfToday()

        val minutes = HashMap<String, Long>()
        try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)?.forEach { stat ->
                val pkg = stat.packageName ?: return@forEach
                if (stat.totalTimeInForeground <= 0L) return@forEach
                minutes[pkg] = (minutes[pkg] ?: 0L) + stat.totalTimeInForeground
            }
        } catch (_: Exception) {
            // Usage access can be revoked at any time; limits simply stop biting.
        }
        minutesCache = minutes

        val opens = HashMap<String, Int>()
        try {
            val events = usm.queryEvents(start, now)
            val event = UsageEvents.Event()
            var lastPackage: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isResume = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                if (!isResume) continue
                val pkg = event.packageName ?: continue
                // Only count a genuine switch into the app, not every activity change.
                if (pkg == lastPackage) continue
                lastPackage = pkg
                opens[pkg] = (opens[pkg] ?: 0) + 1
            }
        } catch (_: Exception) {
            // Same story: no events, no open counting.
        }
        opensCache = opens
    }

    fun exportJson(context: Context): org.json.JSONObject {
        val out = org.json.JSONObject()
        out.put("minutes", FocusStore.getJsonObject(context, KEY_MINUTE_LIMITS))
        out.put("opens", FocusStore.getJsonObject(context, KEY_OPEN_LIMITS))
        return out
    }

    fun importJson(context: Context, json: org.json.JSONObject) {
        json.optJSONObject("minutes")?.let { FocusStore.setJsonObject(context, KEY_MINUTE_LIMITS, it) }
        json.optJSONObject("opens")?.let { FocusStore.setJsonObject(context, KEY_OPEN_LIMITS, it) }
        PolicySync.request(context, "limits:import")
    }
}

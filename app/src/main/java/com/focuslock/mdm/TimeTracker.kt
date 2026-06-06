package com.focuslock.mdm

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object TimeTracker {

    private fun prefs(context: Context) =
        context.getSharedPreferences(Constants.PREFS_TIME, Context.MODE_PRIVATE)

    /** URL-safe key: strip protocol, replace non-alphanumeric with underscore */
    private fun keyFor(url: String) = url.replace(Regex("[^a-zA-Z0-9]"), "_")

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    // ── Public API ────────────────────────────────────────────────

    /** Milliseconds already used today for this URL. */
    fun getUsedMs(context: Context, url: String): Long {
        val p   = prefs(context)
        val key = keyFor(url)
        val savedDate = p.getString(Constants.KEY_URL_DATE + key, "")
        return if (savedDate == today()) p.getLong(Constants.KEY_URL_TIME + key, 0L) else 0L
    }

    /** Milliseconds still available today for this URL. */
    fun getRemainingMs(context: Context, url: String): Long =
        maxOf(0L, Constants.DAILY_LIMIT_MS - getUsedMs(context, url))

    /** Returns true if the 2-hour daily cap has been exhausted. */
    fun isLimitReached(context: Context, url: String): Boolean =
        getUsedMs(context, url) >= Constants.DAILY_LIMIT_MS

    /**
     * Add [ms] milliseconds to the running total for today.
     * Resets the counter automatically if the stored date is not today.
     */
    fun addUsedMs(context: Context, url: String, ms: Long) {
        if (ms <= 0L) return
        val p       = prefs(context)
        val key     = keyFor(url)
        val today   = today()
        val saved   = p.getString(Constants.KEY_URL_DATE + key, "")
        val current = if (saved == today) p.getLong(Constants.KEY_URL_TIME + key, 0L) else 0L
        p.edit()
            .putString(Constants.KEY_URL_DATE + key, today)
            .putLong(Constants.KEY_URL_TIME  + key, current + ms)
            .apply()
    }

    /** Human-readable remaining time, e.g. "1h 47m" */
    fun remainingFormatted(context: Context, url: String): String {
        val ms   = getRemainingMs(context, url)
        val mins = ms / 60_000
        val h    = mins / 60
        val m    = mins % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}

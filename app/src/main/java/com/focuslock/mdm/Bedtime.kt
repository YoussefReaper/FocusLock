package com.focuslock.mdm

import android.content.Context
import java.util.Calendar

/**
 * The night shift.
 *
 * Bedtime is not a session: it runs on the clock whether or not a session is
 * active, dims the screen, forces the quiet theme, and holds back the categories
 * the user named. Everything about it is a setting, including which categories.
 */
object Bedtime {

    private const val KEY_START = "bedtime_start_minutes"
    private const val KEY_END = "bedtime_end_minutes"
    private const val KEY_CATEGORIES = "bedtime_categories"
    private const val KEY_DIM = "bedtime_dim_percent"
    private const val KEY_DARK_THEME = "bedtime_dark_theme"
    private const val KEY_GRAYSCALE_HINT = "bedtime_grayscale_hint"

    const val DEFAULT_START = 22 * 60
    const val DEFAULT_END = 6 * 60

    fun isEnabled(context: Context): Boolean =
        CapabilityRegistry.isEnabled(context, Capabilities.BEDTIME_MODE)

    fun startMinutes(context: Context): Int = FocusStore.getInt(context, KEY_START, DEFAULT_START)

    fun endMinutes(context: Context): Int = FocusStore.getInt(context, KEY_END, DEFAULT_END)

    fun setWindow(context: Context, startMinutes: Int, endMinutes: Int) {
        FocusStore.setInt(context, KEY_START, startMinutes.coerceIn(0, 1439))
        FocusStore.setInt(context, KEY_END, endMinutes.coerceIn(0, 1439))
        PolicySync.request(context, "bedtimeWindow")
    }

    fun blockedCategories(context: Context): Set<AppCategory> {
        val stored = FocusStore.getSetOrNull(context, KEY_CATEGORIES)
            ?: return setOf(AppCategory.SOCIAL, AppCategory.VIDEO, AppCategory.GAMES)
        return stored.map { AppCategory.fromId(it) }.toSet()
    }

    fun setBlockedCategories(context: Context, categories: Collection<AppCategory>) {
        FocusStore.setSet(context, KEY_CATEGORIES, categories.map { it.id })
        PolicySync.request(context, "bedtimeCategories")
    }

    /** How far to dim, as a percentage of the screen's own brightness. */
    fun dimPercent(context: Context): Int = FocusStore.getInt(context, KEY_DIM, 45).coerceIn(0, 80)

    fun setDimPercent(context: Context, value: Int) {
        FocusStore.setInt(context, KEY_DIM, value.coerceIn(0, 80))
    }

    fun forcesDarkTheme(context: Context): Boolean = FocusStore.getBool(context, KEY_DARK_THEME, true)

    fun setForcesDarkTheme(context: Context, value: Boolean) {
        FocusStore.setBool(context, KEY_DARK_THEME, value)
    }

    fun showsGrayscaleHint(context: Context): Boolean =
        FocusStore.getBool(context, KEY_GRAYSCALE_HINT, false)

    fun setShowsGrayscaleHint(context: Context, value: Boolean) {
        FocusStore.setBool(context, KEY_GRAYSCALE_HINT, value)
    }

    // ── State ─────────────────────────────────────────────────────

    fun isActive(context: Context, now: Calendar = Calendar.getInstance()): Boolean {
        if (!isEnabled(context)) return false
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return isWithin(minutes, startMinutes(context), endMinutes(context))
    }

    private fun isWithin(nowMinutes: Int, start: Int, end: Int): Boolean {
        if (start == end) return false
        return if (end > start) nowMinutes in start until end else nowMinutes >= start || nowMinutes < end
    }

    fun blocks(context: Context, packageName: String): Boolean {
        if (!isActive(context)) return false
        if (AppRules.isAlwaysAllowed(context, packageName)) return false
        return AppCatalog.categoryOf(context, packageName) in blockedCategories(context)
    }

    /** Minutes until bedtime lifts, for the "back at 6:00" line on the block screen. */
    fun minutesUntilEnd(context: Context, now: Calendar = Calendar.getInstance()): Int {
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val end = endMinutes(context)
        return if (end > nowMinutes) end - nowMinutes else 1_440 - nowMinutes + end
    }

    fun formatWindow(context: Context): String =
        formatTime(startMinutes(context)) + " to " + formatTime(endMinutes(context))

    fun formatTime(minutes: Int): String {
        val hour = minutes / 60
        val minute = minutes % 60
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
    }
}

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
    const val PARAM_BLOCK_ALL = "blockEverything"
    private const val KEY_DIM = "bedtime_dim_percent"
    private const val KEY_DARK_THEME = "bedtime_dark_theme"
    private const val KEY_GRAYSCALE_HINT = "bedtime_grayscale_hint"

    const val DEFAULT_START = 22 * 60
    const val DEFAULT_END = 6 * 60

    fun isEnabled(context: Context): Boolean =
        CapabilityRegistry.isEnabled(context, Capabilities.BEDTIME_MODE)

    fun startMinutes(context: Context): Int = FocusStore.getInt(context, KEY_START, DEFAULT_START)

    fun endMinutes(context: Context): Int = FocusStore.getInt(context, KEY_END, DEFAULT_END)

    /** Frozen-gated: narrowing tonight's window mid-session is the "Advanced" pattern applied here too. */
    fun setWindow(context: Context, startMinutes: Int, endMinutes: Int): Boolean {
        if (SessionLock.isFrozen(context)) return false
        FocusStore.setInt(context, KEY_START, startMinutes.coerceIn(0, 1439))
        FocusStore.setInt(context, KEY_END, endMinutes.coerceIn(0, 1439))
        PolicySync.request(context, "bedtimeWindow")
        return true
    }

    fun blockedCategories(context: Context): Set<AppCategory> {
        val stored = FocusStore.getSetOrNull(context, KEY_CATEGORIES)
            ?: return setOf(AppCategory.SOCIAL, AppCategory.VIDEO, AppCategory.GAMES)
        return stored.map { AppCategory.fromId(it) }.toSet()
    }

    fun setBlockedCategories(context: Context, categories: Collection<AppCategory>): Boolean {
        if (SessionLock.isFrozen(context)) return false
        FocusStore.setSet(context, KEY_CATEGORIES, categories.map { it.id })
        PolicySync.request(context, "bedtimeCategories")
        return true
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

    /**
     * Whether bedtime should stop this app right now.
     *
     * Category matching is the default and it has a real hole: an app whose
     * category the catalogue could not work out lands in OTHER and sails
     * straight through, which is why "bedtime isn't locking" gets reported by
     * people who did switch it on. [blocksEverything] closes that by inverting
     * the question — everything stops except what you named as essential.
     */
    fun blocks(context: Context, packageName: String): Boolean {
        if (!isActive(context)) return false
        if (AppRules.isAlwaysAllowed(context, packageName)) return false
        if (SystemSurfaces.isCritical(packageName)) return false
        if (blocksEverything(context)) return true
        return AppCatalog.categoryOf(context, packageName) in blockedCategories(context)
    }

    /**
     * Strict bedtime: block everything that is not always-allowed.
     *
     * Off by default, because turning it on for an existing user overnight
     * would lock them out of apps they never asked bedtime to touch.
     */
    fun blocksEverything(context: Context): Boolean =
        CapabilityRegistry.getBoolParam(context, Capabilities.BEDTIME_MODE, PARAM_BLOCK_ALL, false)

    fun setBlocksEverything(context: Context, value: Boolean): Boolean =
        CapabilityRegistry.setBoolParam(context, Capabilities.BEDTIME_MODE, PARAM_BLOCK_ALL, value)

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

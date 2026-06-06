package com.focuslock.mdm

import android.content.Context

object LockManager {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Initialize baseline prefs safely. */
    fun initLock(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        if (!prefs.contains(Constants.KEY_INSTALL_TIME)) {
            prefs.edit()
                .putLong(Constants.KEY_INSTALL_TIME, System.currentTimeMillis())
                .apply()
        }
    }

    fun startKiosk(context: Context, durationMs: Long) {
        val prefs = context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(Constants.KEY_KIOSK_ACTIVE, true)
            .putLong(Constants.KEY_KIOSK_START_MS, now)
            .putLong(Constants.KEY_KIOSK_DURATION_MS, durationMs)
            .apply()
    }

    fun stopKiosk(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(Constants.KEY_KIOSK_ACTIVE, false)
            .putLong(Constants.KEY_KIOSK_START_MS, 0L)
            .putLong(Constants.KEY_KIOSK_DURATION_MS, 0L)
            .apply()
    }

    fun isKioskActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Constants.KEY_KIOSK_ACTIVE, false)) return false
        val start = prefs.getLong(Constants.KEY_KIOSK_START_MS, 0L)
        val duration = prefs.getLong(Constants.KEY_KIOSK_DURATION_MS, 0L)
        if (start <= 0L || duration <= 0L) return false
        return (System.currentTimeMillis() - start) < duration
    }

    fun getKioskDurationMs(context: Context): Long {
        val prefs = context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        return prefs.getLong(Constants.KEY_KIOSK_DURATION_MS, 0L)
    }

    fun getKioskRemainingMs(context: Context): Long {
        val prefs = context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        val start = prefs.getLong(Constants.KEY_KIOSK_START_MS, 0L)
        val duration = prefs.getLong(Constants.KEY_KIOSK_DURATION_MS, 0L)
        if (start <= 0L || duration <= 0L) return 0L
        val remaining = duration - (System.currentTimeMillis() - start)
        return remaining.coerceAtLeast(0L)
    }

    fun hasKioskExpired(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Constants.KEY_KIOSK_ACTIVE, false)) return false
        val start = prefs.getLong(Constants.KEY_KIOSK_START_MS, 0L)
        val duration = prefs.getLong(Constants.KEY_KIOSK_DURATION_MS, 0L)
        if (start <= 0L || duration <= 0L) return true
        return (System.currentTimeMillis() - start) >= duration
    }

    /** True after initial permission/bootstrap setup has been completed once. */
    fun isSecurityBaselineReady(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.KEY_SECURITY_BASELINE_READY, false)
    }

    /** Kiosk lock should be hard-enforced for the full active lock window. */
    fun shouldEnforceKiosk(context: Context): Boolean {
        return isKioskActive(context)
    }

    fun allowSettingsUntil(context: Context, untilMs: Long) {
        val prefs = context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        prefs.edit().putLong(Constants.KEY_SETTINGS_ALLOW_UNTIL_MS, untilMs).apply()
    }

    fun isSettingsAccessAllowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        val until = prefs.getLong(Constants.KEY_SETTINGS_ALLOW_UNTIL_MS, 0L)
        return System.currentTimeMillis() <= until
    }

    /** Returns remaining full days (rounded up) for messaging/UI. */
    fun getDaysRemaining(context: Context): Long {
        val remaining = getKioskRemainingMs(context)
        if (remaining <= 0L) return 0L
        return (remaining + DAY_MS - 1L) / DAY_MS
    }
}
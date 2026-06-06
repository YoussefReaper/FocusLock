package com.focuslock.mdm

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for all video-related state.
 *
 * Rules enforced here:
 *  - Folder URI is written once and never changed again.
 *  - One new video can be unlocked every 24 hours (rolling window).
 *  - Once unlocked, a video stays unlocked for the entire 90-day period.
 */
object VideoManager {

    private const val PREFS             = "focuslock_video"
    private const val KEY_FOLDER_URI    = "folder_uri"
    private const val KEY_UNLOCKED      = "unlocked_uris"   // comma-separated URI strings
    private const val KEY_LAST_UNLOCK   = "last_unlock_ms"

    val UNLOCK_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)

    // ── Folder ────────────────────────────────────────────────────

    fun isFolderSelected(context: Context): Boolean =
        prefs(context).contains(KEY_FOLDER_URI)

    /**
     * Saves the folder URI permanently and takes a persistent read permission
     * so the app can access it across reboots without asking again.
     */
    fun setFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        prefs(context).edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
    }

    fun getFolderUri(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_FOLDER_URI, null) ?: return null
        return Uri.parse(raw)
    }

    // ── Video scanning ────────────────────────────────────────────

    /**
     * Returns all video files in the selected folder, sorted by name.
     * Each item carries its current unlock state.
     * Returns empty list if folder is not selected or inaccessible.
     */
    fun getAllVideos(context: Context): List<VideoItem> {
        val folderUri = getFolderUri(context) ?: return emptyList()
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()

        return root.listFiles()
            .filter { file ->
                file.isFile &&
                        (file.type?.startsWith("video/") == true ||
                                file.name?.let { n ->
                                    n.endsWith(".mp4", true) || n.endsWith(".mkv", true) ||
                                            n.endsWith(".avi", true) || n.endsWith(".mov", true) ||
                                            n.endsWith(".webm", true)
                                } == true)
            }
            .sortedBy { it.name?.lowercase() }
            .map { file ->
                VideoItem(
                    uri      = file.uri,
                    name     = file.name?.substringBeforeLast('.') ?: "Unknown",
                    fileName = file.name ?: "",
                    isUnlocked = isUnlocked(context, file.uri)
                )
            }
    }

    // ── 24-hour gate ──────────────────────────────────────────────

    /** True if 24 hours have passed since the last unlock (or no unlock ever). */
    fun canUnlockToday(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_UNLOCK, 0L)
        return System.currentTimeMillis() - last >= UNLOCK_INTERVAL_MS
    }

    /** Milliseconds remaining until the next unlock is available. */
    fun msUntilNextUnlock(context: Context): Long {
        val last = prefs(context).getLong(KEY_LAST_UNLOCK, 0L)
        return maxOf(0L, UNLOCK_INTERVAL_MS - (System.currentTimeMillis() - last))
    }

    /** Human-readable countdown, e.g. "18h 34m". */
    fun nextUnlockFormatted(context: Context): String {
        val ms   = msUntilNextUnlock(context)
        val h    = TimeUnit.MILLISECONDS.toHours(ms)
        val m    = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return "${h}h ${m}m"
    }

    // ── Unlocking ─────────────────────────────────────────────────

    /**
     * Marks a video as permanently unlocked and stamps the current time
     * as the last-unlock timestamp, starting the next 24-hour window.
     *
     * Guard with [canUnlockToday] before calling.
     */
    fun unlockVideo(context: Context, uri: Uri) {
        val p       = prefs(context)
        val current = getUnlockedSet(p)
        current.add(uri.toString())
        p.edit()
            .putString(KEY_UNLOCKED, current.joinToString(","))
            .putLong(KEY_LAST_UNLOCK, System.currentTimeMillis())
            .apply()
    }

    fun isUnlocked(context: Context, uri: Uri): Boolean =
        uri.toString() in getUnlockedSet(prefs(context))

    fun unlockedCount(context: Context): Int =
        getUnlockedSet(prefs(context)).size

    // ── Internals ─────────────────────────────────────────────────

    private fun getUnlockedSet(
        p: android.content.SharedPreferences
    ): MutableSet<String> {
        val raw = p.getString(KEY_UNLOCKED, "") ?: ""
        return if (raw.isBlank()) mutableSetOf()
        else raw.split(",").filter { it.isNotBlank() }.toMutableSet()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
package com.focuslock.mdm

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale

/**
 * Keeps Telegram usable for the two people you actually need, and closed for
 * the forty channels you do not.
 *
 * Telegram is the hardest case the content guard has: blocking the app costs
 * you your group chats, and blocking by keyword cannot tell a study group from
 * a meme channel because both are just text. So this one works the other way
 * round — you name the chats that are allowed, and anything else gets backed
 * out of.
 *
 * ### Why this fails open, deliberately
 *
 * There is no public, stable way to read "which chat am I in" from outside
 * Telegram. This reads the title out of the toolbar by view id, and Telegram
 * are free to rename that id in any update. A guard that guessed wrong while
 * failing *closed* would back you out of every chat including the allowed ones,
 * and make the app useless with no obvious cause.
 *
 * So: it only ever acts on a title it positively read. No title, no action.
 * The cost of a missed update is that the guard quietly stops guarding, which
 * the user can see on the Keyword guard screen — not that Telegram becomes
 * unusable.
 */
object TelegramGuard {

    const val PACKAGE = "org.telegram.messenger"

    /** The other builds people actually run. */
    val packages: Set<String> = setOf(
        PACKAGE,
        "org.telegram.messenger.web",
        "org.telegram.plus",
        "org.thunderdog.challegram"
    )

    private const val KEY_ALLOWED = "telegram_allowed_chats"
    private const val KEY_LAST_TITLE = "telegram_last_title"

    /**
     * Toolbar title ids seen across Telegram builds and forks.
     *
     * Matched as substrings against `viewIdResourceName`, so a fork that
     * prefixes its own package still lines up.
     */
    private val TITLE_ID_HINTS = listOf(
        "action_bar_title",
        "actionbartitle",
        "chat_title",
        "dialog_title",
        "toolbar_title"
    )

    fun isTelegram(packageName: String): Boolean = packageName in packages

    fun isEnabled(context: Context): Boolean =
        CapabilityRegistry.isEnabled(context, Capabilities.TELEGRAM_GUARD)

    /** Chat titles the user named as allowed, lowercased for comparison. */
    fun allowed(context: Context): List<String> =
        FocusStore.jsonArrayToStringList(FocusStore.getJsonArray(context, KEY_ALLOWED))
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotEmpty() }

    fun allowedRaw(context: Context): List<String> =
        FocusStore.jsonArrayToStringList(FocusStore.getJsonArray(context, KEY_ALLOWED))

    fun setAllowed(context: Context, titles: List<String>): Boolean {
        if (SessionLock.isFrozen(context)) return false
        val cleaned = titles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        FocusStore.setJsonArray(context, KEY_ALLOWED, FocusStore.stringListToJsonArray(cleaned))
        PolicySync.request(context, "telegramGuard")
        return true
    }

    fun addAllowed(context: Context, title: String): Boolean =
        setAllowed(context, allowedRaw(context) + title)

    fun removeAllowed(context: Context, title: String): Boolean =
        setAllowed(context, allowedRaw(context).filterNot { it.equals(title, ignoreCase = true) })

    /**
     * The last chat title the guard managed to read.
     *
     * Shown on the settings screen so "add the chat you are in" is a tap
     * instead of a typing exercise, and so a user can tell at a glance whether
     * the guard can still see anything at all after a Telegram update.
     */
    fun lastSeenTitle(context: Context): String? =
        FocusStore.getString(context, KEY_LAST_TITLE, "").takeIf { it.isNotBlank() }

    fun rememberTitle(context: Context, title: String) {
        FocusStore.setString(context, KEY_LAST_TITLE, title)
    }

    /**
     * Reads the chat title out of the visible tree, or null if it cannot.
     *
     * Null is a normal answer, not an error: the chat list has no chat title,
     * and neither does a settings screen.
     */
    fun readChatTitle(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited += 1

            val viewId = node.viewIdResourceName?.lowercase(Locale.US)
            if (viewId != null && TITLE_ID_HINTS.any { viewId.contains(it) }) {
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) return text
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * Whether this chat should be backed out of.
     *
     * An empty allowlist means "not configured yet" and blocks nothing — the
     * alternative is a guard that locks the user out of all of Telegram the
     * instant they switch it on, before they have had any chance to say what
     * matters to them.
     */
    fun blocks(context: Context, title: String): Boolean {
        if (!isEnabled(context)) return false
        val allowed = allowed(context)
        if (allowed.isEmpty()) return false
        val needle = title.trim().lowercase(Locale.US)
        if (needle.isEmpty()) return false
        return allowed.none { needle == it || needle.contains(it) }
    }

    private const val MAX_NODES = 400
}

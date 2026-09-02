package com.focuslock.mdm

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale

/**
 * The content guard.
 *
 * This is the generalisation of the old WhatsApp-only service. It reads the
 * screen of whichever app is in front, matches it against the user's keyword
 * rules, and steps back out of the surfaces they said they did not want, while
 * leaving the rest of the app working. Blocking a whole messenger to avoid its
 * Channels tab is the kind of blunt trade that gets a blocker abandoned.
 *
 * It only ever looks at text. It never types, never taps a control, and never
 * sends anything anywhere.
 */
class ContentGuardService : AccessibilityService() {

    private var lastActionAt = 0L
    private var lastEventAt = 0L
    private var lastPackage: String? = null
    private var cachedRules: List<KeywordRule> = emptyList()
    private var cachedRevision = -1L
    private var cachedExceptions: List<String> = emptyList()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (!CapabilityRegistry.isEnabled(this, Capabilities.CONTENT_GUARD)) {
            GuardState.clear()
            return
        }

        val packageName = event.packageName?.toString() ?: return
        if (packageName == packageName()) return

        // The system's own surfaces are not content to guard. The status bar,
        // the recents switcher and the permission dialogs all fire events with
        // whatever text is behind them, and reacting to those was what produced
        // the "System UI blocked" reports: a back-press aimed at a shade that
        // was never the thing the rule meant.
        if (SystemSurfaces.isCritical(packageName)) {
            GuardState.clear()
            return
        }

        // Accessibility fires a burst of events for one screen change. Walking
        // the tree on every single one is wasted work and, worse, walks it
        // mid-transition when the window is half the old screen and half the
        // new one — which is how a rule matches something the person never
        // actually looked at.
        val now = SystemClock.elapsedRealtime()
        if (now - lastEventAt < EVENT_DEBOUNCE_MS) return
        lastEventAt = now

        // Telegram is handled by allowlist rather than by phrase: naming every
        // channel you do not want is endless, naming the few you do is not.
        if (TelegramGuard.isTelegram(packageName) && TelegramGuard.isEnabled(this)) {
            val root = rootInActiveWindow
            if (root != null) {
                val title = TelegramGuard.readChatTitle(root)
                if (title != null) {
                    TelegramGuard.rememberTitle(this, title)
                    if (TelegramGuard.blocks(this, title)) {
                        lastPackage = packageName
                        act(GuardAction.STEP_BACK, packageName, title)
                        return
                    }
                }
            }
        }

        val rules = rulesFor(packageName)
        if (rules.isEmpty()) {
            if (lastPackage == packageName) GuardState.clear()
            lastPackage = packageName
            return
        }
        lastPackage = packageName

        val root = rootInActiveWindow ?: return
        val hit = try {
            findHit(root, rules)
        } catch (_: Exception) {
            null
        }

        if (hit == null) {
            GuardState.clear()
            return
        }

        GuardState.record(packageName, hit.phrase, hit.action)
        act(hit.action, packageName, hit.phrase)
    }

    override fun onInterrupt() {
        // Nothing buffered, nothing to abandon.
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Migration.run(this)
    }

    private fun packageName(): String = applicationContext.packageName

    // ── Rules ─────────────────────────────────────────────────────

    private fun rulesFor(packageName: String): List<KeywordRule> {
        val revision = PolicySync.revision()
        if (revision != cachedRevision) {
            cachedRules = KeywordRules.activeRules(this)
            cachedExceptions = KeywordRules.exceptions(this)
            cachedRevision = revision
        }
        return cachedRules.filter { it.appliesEverywhere || packageName in it.packages }
    }

    private fun findHit(root: AccessibilityNodeInfo, rules: List<KeywordRule>): KeywordRule? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited += 1

            val text = normalize(node.text)
            val description = normalize(node.contentDescription)
            val viewId = node.viewIdResourceName?.lowercase(Locale.US)

            if (!isException(text) && !isException(description)) {
                rules.forEach { rule ->
                    val phrase = rule.phrase.lowercase(Locale.US)
                    if (matches(text, phrase) || matches(description, phrase) || matchesId(viewId, phrase)) {
                        return rule
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun matches(value: String?, phrase: String): Boolean {
        if (value.isNullOrBlank() || phrase.isBlank()) return false
        return value.contains(phrase)
    }

    /**
     * View ids are the reliable signal for surfaces whose label is an icon.
     * A YouTube Shorts player has no word "shorts" on it, but its container id
     * says so, which is why this looks at ids as well as visible text.
     */
    private fun matchesId(viewId: String?, phrase: String): Boolean {
        if (viewId.isNullOrBlank() || phrase.length < 4) return false
        val collapsed = phrase.replace(" ", "_")
        return viewId.contains(collapsed)
    }

    private fun isException(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return cachedExceptions.any { value.contains(it) }
    }

    private fun normalize(text: CharSequence?): String? {
        val raw = text?.toString()?.trim()?.lowercase(Locale.US) ?: return null
        if (raw.isEmpty()) return null
        return raw.replace(Regex("\\s+"), " ")
    }

    // ── Acting ────────────────────────────────────────────────────

    private fun act(action: GuardAction, packageName: String, phrase: String) {
        // Same demo gate as AppBlockerService and FocusNotificationService:
        // the guard may still detect a hit (GuardState reflects it on screen
        // for the curious), but it never presses GLOBAL_ACTION_BACK for real.
        if (!BuildConfig.ENFORCEMENT) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAt < ACTION_COOLDOWN_MS) return
        lastActionAt = now

        when (action) {
            GuardAction.STEP_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            GuardAction.CLOSE_APP -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                showIntercept(packageName, phrase)
            }
            GuardAction.NUDGE -> {
                // The state record is the whole action: the blocker surfaces a
                // quiet line without taking the screen away.
            }
        }
    }

    private fun showIntercept(packageName: String, phrase: String) {
        if (!CapabilityRegistry.isEnabled(this, Capabilities.CONTENT_GUARD)) return
        try {
            startActivity(
                Intent(this, InterceptActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    putExtra(InterceptActivity.EXTRA_PACKAGE, packageName)
                    putExtra(InterceptActivity.EXTRA_SOURCE, "contentGuard")
                    putExtra(InterceptActivity.EXTRA_PHRASE, phrase)
                }
            )
        } catch (_: Exception) {
            // If we cannot show the screen, the back press already happened.
        }
    }

    companion object {
        private const val ACTION_COOLDOWN_MS = 450L

        /**
         * Ignore repeat events inside this window.
         *
         * One screen change produces many events. This collapses the burst to
         * the settled state, which is both cheaper and more accurate.
         */
        private const val EVENT_DEBOUNCE_MS = 500L
        private const val MAX_NODES = 600
    }
}

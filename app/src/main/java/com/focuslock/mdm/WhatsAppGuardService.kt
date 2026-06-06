package com.focuslock.mdm

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.Locale

class WhatsAppGuardService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!LockManager.isKioskActive(this)) {
            WhatsAppGuardState.clear()
            return
        }

        val pkg = event.packageName?.toString() ?: return
        if (pkg !in Constants.WHATSAPP_PACKAGES) return

        val root = rootInActiveWindow ?: return
        val restriction = detectRestriction(root)

        if (restriction != null) {
            WhatsAppGuardState.markRestricted(restriction)
        } else {
            WhatsAppGuardState.clear()
        }
    }

    override fun onInterrupt() {
        // No-op.
    }

    private fun detectRestriction(root: AccessibilityNodeInfo): String? {
        return findNode(root) { node ->
            nodeMatchesBlockedPhrase(node)
        }?.let { "blocked_phrase" }
    }

    private fun nodeMatchesBlockedPhrase(node: AccessibilityNodeInfo): Boolean {
        val text = normalizeText(node.text)
        val desc = normalizeText(node.contentDescription)
        if (text == null && desc == null) return false

        if (matchesAllowedPhrase(text) || matchesAllowedPhrase(desc)) return false

        return matchesBlockedPhrase(text) || matchesBlockedPhrase(desc)
    }

    private fun matchesBlockedPhrase(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return Constants.WHATSAPP_BLOCKED_PHRASES.any { value.contains(it) }
    }

    private fun matchesAllowedPhrase(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return Constants.WHATSAPP_ALLOWED_PHRASES.any { value.contains(it) }
    }

    private fun normalizeText(text: CharSequence?): String? {
        val raw = text?.toString()?.trim()?.lowercase(Locale.US) ?: return null
        if (raw.isEmpty()) return null
        return raw.replace(Regex("\\s+"), " ")
    }

    private inline fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }
}

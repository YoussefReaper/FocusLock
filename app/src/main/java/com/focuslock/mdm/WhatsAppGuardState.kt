package com.focuslock.mdm

data class WhatsAppRestriction(
    val reason: String,
    val timestampMs: Long
)

object WhatsAppGuardState {
    private const val RESTRICTION_TTL_MS = 1_500L

    @Volatile private var lastRestrictedAt = 0L
    @Volatile private var lastReason: String? = null

    @Synchronized
    fun markRestricted(reason: String) {
        lastReason = reason
        lastRestrictedAt = System.currentTimeMillis()
    }

    @Synchronized
    fun clear() {
        lastReason = null
        lastRestrictedAt = 0L
    }

    @Synchronized
    fun getActiveRestriction(): WhatsAppRestriction? {
        if (lastRestrictedAt <= 0L) return null
        val age = System.currentTimeMillis() - lastRestrictedAt
        if (age > RESTRICTION_TTL_MS) {
            clear()
            return null
        }
        val reason = lastReason ?: "restricted"
        return WhatsAppRestriction(reason, lastRestrictedAt)
    }
}

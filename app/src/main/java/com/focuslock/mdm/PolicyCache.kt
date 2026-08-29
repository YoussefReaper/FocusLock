package com.focuslock.mdm

/**
 * A memo for the enforcement loop.
 *
 * [RuleEngine] runs several times a second, and a naive implementation would
 * re-parse every JSON blob on every tick just to answer "is this app allowed".
 * Each entry here is tagged with the [PolicySync] revision it was computed
 * under, so a cached answer is thrown away the instant any store is written and
 * a toggle still takes effect immediately.
 */
object PolicyCache {

    private val entries = HashMap<String, Pair<Long, Any?>>()

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T> get(key: String, compute: () -> T): T {
        val revision = PolicySync.revision()
        val cached = entries[key]
        if (cached != null && cached.first == revision) {
            return cached.second as T
        }
        val value = compute()
        entries[key] = revision to value
        return value
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

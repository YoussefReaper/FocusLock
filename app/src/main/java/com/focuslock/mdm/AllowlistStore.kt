package com.focuslock.mdm

import android.content.Context
import android.net.Uri

/**
 * The web allowlist, plus a thin compatibility layer over [AppRules].
 *
 * App policy moved to [AppRules] when the registry landed. The app-facing
 * methods here still work and simply forward, so older call sites (the ADB
 * receiver, the safe browser's deep-link check) keep behaving the same while
 * reading the user's single source of truth.
 */
object AllowlistStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)

    // ── Apps (forwarding) ─────────────────────────────────────────

    fun getAppAllowlist(context: Context): Set<String> = AppRules.kioskAllowlist(context)

    fun setAppAllowlist(context: Context, packages: Collection<String>) {
        AppRules.setKioskAllowlist(context, packages)
    }

    fun isAppAllowlistLocked(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_APP_ALLOWLIST_LOCKED, false)

    fun setAppAllowlistLocked(context: Context, locked: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_APP_ALLOWLIST_LOCKED, locked).apply()
    }

    // ── Web ───────────────────────────────────────────────────────

    fun getWebAllowlistUrls(context: Context): Set<String> {
        val p = prefs(context)
        val stored = p.getStringSet(Constants.KEY_WEB_ALLOWLIST, null)
        if (stored != null) return stored.toSet()

        val seeded = Constants.WEB_LINKS.map { it.url }.toMutableSet()
        p.edit().putStringSet(Constants.KEY_WEB_ALLOWLIST, seeded).apply()
        return seeded
    }

    // Deliberately NOT gated on SessionLock.isFrozen() here, unlike every other
    // store in this audit: this list has its own older, more specific rule -
    // WebAllowlistEditorActivity blocks *additions* unconditionally during any
    // active session (stricter than the freeze, and independent of the
    // LOCK_RULES_IN_SESSION capability), while deliberately always allowing
    // *removals* even mid-session ("removing is fine, adding waits"). A blanket
    // freeze check here would have silently broken that removal path the first
    // time this function was reached from a session with rules frozen.
    fun setWebAllowlistUrls(context: Context, urls: Collection<String>) {
        val cleaned = urls.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        prefs(context).edit().putStringSet(Constants.KEY_WEB_ALLOWLIST, cleaned).apply()
        PolicySync.request(context, "webAllowlist")
    }

    fun addWebUrl(context: Context, url: String) {
        val cleaned = normalizeUrl(url)
        if (cleaned.isBlank()) return
        setWebAllowlistUrls(context, getWebAllowlistUrls(context) + cleaned)
    }

    fun removeWebUrl(context: Context, url: String) {
        setWebAllowlistUrls(context, getWebAllowlistUrls(context) - normalizeUrl(url))
    }

    fun normalizeUrl(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://" + trimmed
        }
    }

    fun isValidUrl(value: String): Boolean = try {
        val uri = Uri.parse(value)
        !uri.host.isNullOrBlank() && (uri.scheme == "http" || uri.scheme == "https")
    } catch (_: Exception) {
        false
    }

    fun isWebAllowlistLocked(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_WEB_ALLOWLIST_LOCKED, false)

    fun setWebAllowlistLocked(context: Context, locked: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_WEB_ALLOWLIST_LOCKED, locked).apply()
    }

    fun isWebTextOnlyEnabled(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_WEB_TEXT_ONLY, false)

    fun setWebTextOnlyEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_WEB_TEXT_ONLY, enabled).apply()
    }

    fun getWebLinks(context: Context): List<Constants.WebLink> {
        val allowed = getWebAllowlistUrls(context)
        val byUrl = Constants.WEB_LINKS.associateBy { it.url }
        return allowed
            .map { url -> byUrl[url] ?: Constants.WebLink(titleFromUrl(url), url, "Custom") }
            .sortedWith(compareBy({ it.category }, { it.title }))
    }

    fun getWebCategories(context: Context): List<String> =
        getWebLinks(context).map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

    fun getWebAllowlistDomains(context: Context): Array<String> {
        val hosts = getWebAllowlistUrls(context)
            .mapNotNull { url ->
                try {
                    Uri.parse(url).host?.lowercase()?.removePrefix("www.")
                } catch (_: Exception) {
                    null
                }
            }
            .filter { it.isNotBlank() }
            .distinct()

        val patterns = LinkedHashSet<String>()
        (hosts + SystemSurfaces.authDomains).forEach { host ->
            patterns.add(host)
            patterns.add("*." + host)
        }
        return patterns.toTypedArray()
    }

    /** Hosts the adult filter refuses even if they somehow reach the allowlist. */
    fun isBlockedHost(context: Context, host: String): Boolean {
        if (!CapabilityRegistry.isEnabled(context, Capabilities.ADULT_BLOCK)) return false
        val normalized = host.lowercase().removePrefix("www.")
        return Seed.adultDomains.any { normalized == it || normalized.endsWith("." + it) }
    }

    private fun titleFromUrl(url: String): String = try {
        Uri.parse(url).host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
    }
}

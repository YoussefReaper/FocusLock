package com.focuslock.mdm

import android.content.Context
import android.net.Uri

object AllowlistStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)

    fun getAppAllowlist(context: Context): Set<String> {
        val p = prefs(context)
        val stored = p.getStringSet(Constants.KEY_APP_ALLOWLIST, null)
        if (stored != null) return stored.toSet()

        val seeded = Constants.USER_LAUNCHABLE_WHITELIST.toMutableSet()
        seeded.add(Constants.OWN_PACKAGE)
        p.edit().putStringSet(Constants.KEY_APP_ALLOWLIST, seeded).apply()
        return seeded
    }

    fun setAppAllowlist(context: Context, packages: Collection<String>) {
        val normalized = packages.map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
        normalized.add(Constants.OWN_PACKAGE)
        prefs(context).edit().putStringSet(Constants.KEY_APP_ALLOWLIST, normalized).apply()
    }

    fun isAppAllowlistLocked(context: Context): Boolean =
        prefs(context).getBoolean(Constants.KEY_APP_ALLOWLIST_LOCKED, false)

    fun setAppAllowlistLocked(context: Context, locked: Boolean) {
        prefs(context).edit().putBoolean(Constants.KEY_APP_ALLOWLIST_LOCKED, locked).apply()
    }

    fun getWebAllowlistUrls(context: Context): Set<String> {
        val p = prefs(context)
        val stored = p.getStringSet(Constants.KEY_WEB_ALLOWLIST, null)
        if (stored != null) return stored.toSet()

        val seeded = Constants.WEB_LINKS.map { it.url }.toMutableSet()
        p.edit().putStringSet(Constants.KEY_WEB_ALLOWLIST, seeded).apply()
        return seeded
    }

    fun setWebAllowlistUrls(context: Context, urls: Collection<String>) {
        val cleaned = urls.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        prefs(context).edit().putStringSet(Constants.KEY_WEB_ALLOWLIST, cleaned).apply()
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
        val links = allowed.map { url ->
            byUrl[url] ?: Constants.WebLink(titleFromUrl(url), url, "Custom")
        }
        return links.sortedWith(compareBy({ it.category }, { it.title }))
    }

    fun getWebAllowlistDomains(context: Context): Array<String> {
        val hosts = getWebAllowlistUrls(context)
            .mapNotNull { url ->
                try {
                    val uri = Uri.parse(url)
                    uri.host?.lowercase()?.removePrefix("www.")
                } catch (_: Exception) {
                    null
                }
            }
            .filter { it.isNotBlank() }
            .distinct()

        val authHosts = listOf(
            "accounts.google.com",
            "accounts.youtube.com",
            "gstatic.com",
            "openai.com",
            "auth0.openai.com",
            "chatgpt.com"
        )

        val patterns = mutableSetOf<String>()
        (hosts + authHosts).forEach { host ->
            patterns.add(host)
            patterns.add("*.$host")
        }
        return patterns.toTypedArray()
    }

    private fun titleFromUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.removePrefix("www.") ?: url
            host
        } catch (_: Exception) {
            url
        }
    }
}

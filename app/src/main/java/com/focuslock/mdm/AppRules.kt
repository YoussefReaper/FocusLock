package com.focuslock.mdm

import android.content.Context
import org.json.JSONObject

/**
 * What FocusLock is allowed to do about one app.
 *
 * The ladder is deliberately gentle at the top: most people do better with a
 * pause than a wall, and a wall with no pause is what makes people uninstall
 * the blocker at 1am.
 */
enum class AppPolicy(val id: String, val label: String, val blurb: String) {
    ALLOW("allow", "Open freely", "No pause, no limit. This app is fine."),
    FRICTION("friction", "Pause first", "A few slow seconds and a way out before it opens."),
    LIMIT("limit", "Give it a budget", "Opens freely until the daily budget runs out."),
    BLOCK("block", "Blocked", "Does not open while a session is running."),
    HIDE("hide", "Blocked and hidden", "Does not open, and disappears from the launcher.");

    val stopsLaunch: Boolean get() = this == BLOCK || this == HIDE

    companion object {
        fun fromId(id: String?): AppPolicy =
            values().firstOrNull { it.id == id } ?: ALLOW

        /** The order the user sees when picking a policy for an app. */
        val ladder: List<AppPolicy> = listOf(ALLOW, FRICTION, LIMIT, BLOCK, HIDE)
    }
}

/**
 * The user-owned replacement for `Constants.WHITELIST` and `Constants.KILL_LIST`.
 *
 * There is exactly one source of truth for per-app policy now, and it is a file
 * the user writes. The old constants survive only as [Seed] data that is copied
 * in once, on first run, and never consulted again.
 */
object AppRules {

    private const val KEY_POLICIES = "app_policies_json"
    private const val KEY_ALWAYS_ALLOWED = "app_always_allowed"
    private const val KEY_CATEGORY_POLICIES = "category_policies_json"
    private const val KEY_KIOSK_ALLOWLIST_MODE = "kiosk_allowlist_mode"

    // ── Per-app policy ────────────────────────────────────────────

    /** The explicit policy the user set for this app, or null if they never said. */
    fun explicitPolicy(context: Context, packageName: String): AppPolicy? {
        val policies = FocusStore.getJsonObject(context, KEY_POLICIES)
        if (!policies.has(packageName)) return null
        return AppPolicy.fromId(policies.optString(packageName, ""))
    }

    /**
     * The policy actually in force: the app's own setting, else its category's
     * setting, else "open freely".
     */
    fun effectivePolicy(context: Context, packageName: String): AppPolicy {
        if (isAlwaysAllowed(context, packageName)) return AppPolicy.ALLOW
        explicitPolicy(context, packageName)?.let { return it }
        val category = AppCatalog.categoryOf(context, packageName)
        return categoryPolicy(context, category) ?: AppPolicy.ALLOW
    }

    fun setPolicy(context: Context, packageName: String, policy: AppPolicy): Boolean {
        if (SessionLock.isFrozen(context)) return false
        val policies = FocusStore.getJsonObject(context, KEY_POLICIES)
        policies.put(packageName, policy.id)
        FocusStore.setJsonObject(context, KEY_POLICIES, policies)
        PolicySync.request(context, "appPolicy:" + packageName)
        return true
    }

    fun clearPolicy(context: Context, packageName: String): Boolean {
        if (SessionLock.isFrozen(context)) return false
        val policies = FocusStore.getJsonObject(context, KEY_POLICIES)
        policies.remove(packageName)
        FocusStore.setJsonObject(context, KEY_POLICIES, policies)
        PolicySync.request(context, "appPolicy:" + packageName)
        return true
    }

    fun setPolicies(context: Context, packages: Collection<String>, policy: AppPolicy): Boolean {
        if (SessionLock.isFrozen(context)) return false
        val policies = FocusStore.getJsonObject(context, KEY_POLICIES)
        packages.forEach { policies.put(it, policy.id) }
        FocusStore.setJsonObject(context, KEY_POLICIES, policies)
        PolicySync.request(context, "appPolicy:bulk")
        return true
    }

    /** Cached per policy revision: the enforcement loop asks for this constantly. */
    fun allPolicies(context: Context): Map<String, AppPolicy> = PolicyCache.get("appPolicies") {
        val policies = FocusStore.getJsonObject(context, KEY_POLICIES)
        val out = LinkedHashMap<String, AppPolicy>()
        val keys = policies.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = AppPolicy.fromId(policies.optString(key, ""))
        }
        out
    }

    fun packagesWithPolicy(context: Context, policy: AppPolicy): Set<String> =
        allPolicies(context).filterValues { it == policy }.keys

    /** Everything the user has told FocusLock to stop, from any route. */
    fun blockedPackages(context: Context): Set<String> = PolicyCache.get("blockedPackages") {
        val out = LinkedHashSet<String>()
        allPolicies(context).forEach { entry ->
            if (entry.value.stopsLaunch) out.add(entry.key)
        }
        categoryPolicies(context).forEach { entry ->
            if (entry.value.stopsLaunch) {
                out.addAll(AppCatalog.packagesInCategory(context, entry.key))
            }
        }
        out.removeAll(alwaysAllowed(context))
        out.remove(context.packageName)
        out
    }

    fun hiddenPackages(context: Context): Set<String> {
        if (!CapabilityRegistry.isEnabled(context, Capabilities.HIDE_BLOCKED_APPS)) return emptySet()
        val out = LinkedHashSet<String>()
        allPolicies(context).forEach { entry ->
            if (entry.value == AppPolicy.HIDE) out.add(entry.key)
        }
        categoryPolicies(context).forEach { entry ->
            if (entry.value == AppPolicy.HIDE) {
                out.addAll(AppCatalog.packagesInCategory(context, entry.key))
            }
        }
        out.removeAll(alwaysAllowed(context))
        out.remove(context.packageName)
        return out
    }

    // ── Category policy ───────────────────────────────────────────

    fun categoryPolicy(context: Context, category: AppCategory): AppPolicy? {
        val policies = FocusStore.getJsonObject(context, KEY_CATEGORY_POLICIES)
        if (!policies.has(category.id)) return null
        return AppPolicy.fromId(policies.optString(category.id, ""))
    }

    fun categoryPolicies(context: Context): Map<AppCategory, AppPolicy> {
        val policies = FocusStore.getJsonObject(context, KEY_CATEGORY_POLICIES)
        val out = LinkedHashMap<AppCategory, AppPolicy>()
        val keys = policies.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[AppCategory.fromId(key)] = AppPolicy.fromId(policies.optString(key, ""))
        }
        return out
    }

    fun setCategoryPolicy(context: Context, category: AppCategory, policy: AppPolicy?): Boolean {
        if (SessionLock.isFrozen(context)) return false
        val policies = FocusStore.getJsonObject(context, KEY_CATEGORY_POLICIES)
        if (policy == null) policies.remove(category.id) else policies.put(category.id, policy.id)
        FocusStore.setJsonObject(context, KEY_CATEGORY_POLICIES, policies)
        PolicySync.request(context, "categoryPolicy:" + category.id)
        return true
    }

    // ── Always allowed ────────────────────────────────────────────
    //
    // This list outranks every other rule, including an active schedule window
    // and a running kiosk session. It exists because a blocker that cuts off a
    // phone call is a blocker that gets uninstalled.

    fun alwaysAllowed(context: Context): Set<String> = PolicyCache.get("alwaysAllowed") {
        if (!CapabilityRegistry.isEnabled(context, Capabilities.ALWAYS_ALLOWED)) {
            setOf(context.packageName)
        } else {
            FocusStore.getSet(context, KEY_ALWAYS_ALLOWED) + context.packageName
        }
    }

    fun setAlwaysAllowed(context: Context, packages: Collection<String>): Boolean {
        if (SessionLock.isFrozen(context)) return false
        val cleaned = packages
            .map { it.trim() }
            .filter { it.isNotBlank() && it != context.packageName }
            .toSet()
        FocusStore.setSet(context, KEY_ALWAYS_ALLOWED, cleaned)
        PolicySync.request(context, "alwaysAllowed")
        return true
    }

    /**
     * The stored list exactly as written, without the capability gate or the
     * implicit self-entry. Editors need this so a switch-off does not look like
     * the user's list was emptied.
     */
    fun alwaysAllowedRaw(context: Context): Set<String> = FocusStore.getSet(context, KEY_ALWAYS_ALLOWED)

    fun isAlwaysAllowed(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        if (!CapabilityRegistry.isEnabled(context, Capabilities.ALWAYS_ALLOWED)) return false
        return packageName in FocusStore.getSet(context, KEY_ALWAYS_ALLOWED)
    }

    fun addAlwaysAllowed(context: Context, packageName: String): Boolean {
        val current = FocusStore.getSet(context, KEY_ALWAYS_ALLOWED).toMutableSet()
        current.add(packageName)
        return setAlwaysAllowed(context, current)
    }

    // ── Kiosk allowlist ───────────────────────────────────────────
    //
    // Kiosk is the one mode that inverts the model: instead of "block what you
    // named", it is "allow only what you named". That inversion is itself a
    // user setting, because some people want kiosk-strength enforcement with a
    // blocklist rather than a whitelist.

    fun isKioskAllowlistMode(context: Context): Boolean =
        FocusStore.getBool(context, KEY_KIOSK_ALLOWLIST_MODE, true)

    fun setKioskAllowlistMode(context: Context, value: Boolean): Boolean {
        if (SessionLock.isFrozen(context)) return false
        FocusStore.setBool(context, KEY_KIOSK_ALLOWLIST_MODE, value)
        PolicySync.request(context, "kioskAllowlistMode")
        return true
    }

    /** Apps that stay open during a kiosk session. */
    fun kioskAllowlist(context: Context): Set<String> {
        val out = LinkedHashSet<String>()
        out.add(context.packageName)
        out.addAll(alwaysAllowed(context))
        allPolicies(context).forEach { entry ->
            if (!entry.value.stopsLaunch) out.add(entry.key)
        }
        return out
    }

    fun setKioskAllowlist(context: Context, packages: Collection<String>): Boolean {
        if (SessionLock.isFrozen(context)) return false
        val allowed = packages.toSet()
        val policies = FocusStore.getJsonObject(context, KEY_POLICIES)
        AppCatalog.launchable(context).forEach { app ->
            val current = AppPolicy.fromId(policies.optString(app.packageName, ""))
            if (app.packageName in allowed) {
                if (current.stopsLaunch || !policies.has(app.packageName)) {
                    policies.put(app.packageName, AppPolicy.ALLOW.id)
                }
            } else {
                if (!current.stopsLaunch) {
                    policies.put(app.packageName, AppPolicy.BLOCK.id)
                }
            }
        }
        FocusStore.setJsonObject(context, KEY_POLICIES, policies)
        PolicySync.request(context, "kioskAllowlist")
        return true
    }

    // ── Export / import ───────────────────────────────────────────

    fun exportJson(context: Context): JSONObject {
        val out = JSONObject()
        out.put("apps", FocusStore.getJsonObject(context, KEY_POLICIES))
        out.put("categories", FocusStore.getJsonObject(context, KEY_CATEGORY_POLICIES))
        out.put("alwaysAllowed", FocusStore.stringListToJsonArray(FocusStore.getSet(context, KEY_ALWAYS_ALLOWED)))
        out.put("kioskAllowlistMode", isKioskAllowlistMode(context))
        return out
    }

    fun importJson(context: Context, json: JSONObject) {
        json.optJSONObject("apps")?.let { FocusStore.setJsonObject(context, KEY_POLICIES, it) }
        json.optJSONObject("categories")?.let { FocusStore.setJsonObject(context, KEY_CATEGORY_POLICIES, it) }
        json.optJSONArray("alwaysAllowed")?.let {
            FocusStore.setSet(context, KEY_ALWAYS_ALLOWED, FocusStore.jsonArrayToStringList(it))
        }
        if (json.has("kioskAllowlistMode")) {
            FocusStore.setBool(context, KEY_KIOSK_ALLOWLIST_MODE, json.optBoolean("kioskAllowlistMode", true))
        }
        PolicySync.request(context, "appRules:import")
    }
}

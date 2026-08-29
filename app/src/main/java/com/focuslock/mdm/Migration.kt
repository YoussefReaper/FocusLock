package com.focuslock.mdm

import android.content.Context

/**
 * The one-way door out of hardcoded behaviour.
 *
 * Old builds kept the allow list, the kill list and the WhatsApp phrases in
 * `Constants`. This runs once and copies all of it into the user-owned stores,
 * preserving anything the person had already customised. Afterwards the app
 * never reads those constants for policy again, and every one of those entries
 * is an ordinary row the user can edit or delete.
 */
object Migration {

    private const val KEY_VERSION = "store_migration_version"
    private const val CURRENT_VERSION = 2

    fun run(context: Context) {
        val version = FocusStore.getInt(context, KEY_VERSION, 0)
        if (version >= CURRENT_VERSION) return

        if (version < 1) {
            migrateAppLists(context)
            migrateAlwaysAllowed(context)
            KeywordRules.seedIfEmpty(context)
        }
        if (version < 2) {
            seedCategoryDefaults(context)
        }

        FocusStore.setInt(context, KEY_VERSION, CURRENT_VERSION)
    }

    /**
     * The old model was a single allowlist plus a kill list. The new model is a
     * policy per app, so: everything previously allowed becomes "open freely",
     * everything on the kill list becomes "blocked", and anything the user had
     * explicitly allowed wins over the seed.
     */
    private fun migrateAppLists(context: Context) {
        val legacyAllowlist = FocusStore.getSetOrNull(context, Constants.KEY_APP_ALLOWLIST)
        val alreadyHasPolicies = AppRules.allPolicies(context).isNotEmpty()
        if (alreadyHasPolicies) return

        val allowed = legacyAllowlist ?: Constants.LEGACY_WHITELIST
        AppRules.setPolicies(context, allowed, AppPolicy.ALLOW)

        val blocked = (Constants.LEGACY_KILL_LIST + Seed.distractions)
            .distinct()
            .filterNot { it in allowed }
        AppRules.setPolicies(context, blocked, AppPolicy.BLOCK)
    }

    private fun migrateAlwaysAllowed(context: Context) {
        if (AppRules.alwaysAllowedRaw(context).isNotEmpty()) return

        val legacySchedule = FocusStore.getSet(context, Constants.KEY_SCHEDULE_ALWAYS_ALLOWED_APPS)
        val essentials = AppCatalog.detectEssentials(context)
        AppRules.setAlwaysAllowed(context, (legacySchedule + essentials).distinct())
    }

    /**
     * Categories start with no bulk policy at all. A first run that silently
     * blocked a whole category would be exactly the "app decided for you"
     * behaviour the registry exists to prevent, so this only records that the
     * step ran.
     */
    private fun seedCategoryDefaults(context: Context) {
        AppCatalog.invalidate()
    }
}

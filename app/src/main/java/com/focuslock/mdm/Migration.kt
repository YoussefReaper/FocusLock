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
    private const val CURRENT_VERSION = 3

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
        if (version < 3) {
            seedSessionBehaviourFlags(context)
        }

        FocusStore.setInt(context, KEY_VERSION, CURRENT_VERSION)
    }

    /**
     * Freezes existing behaviour before the flags take over.
     *
     * `canEndEarly` and `hardBlock` used to be decided by the mode enum. From
     * this version they are switches, and a switch has a default — which would
     * silently change what an already-installed phone does, possibly in the
     * middle of a running session. So on upgrade we write the values that
     * reproduce exactly what the person's current mode was already doing. From
     * then on the flags are theirs.
     *
     * Only writes flags the user has never set, so a deliberate choice is
     * never overwritten.
     */
    private fun seedSessionBehaviourFlags(context: Context) {
        val mode = SessionManager.mode(context)

        if (!CapabilityRegistry.isUserSet(context, Capabilities.CAN_END_EARLY)) {
            // Old rule: every mode except Kiosk could be ended early.
            CapabilityRegistry.setEnabled(
                context,
                Capabilities.CAN_END_EARLY,
                mode != FocusMode.KIOSK
            )
        }

        if (!CapabilityRegistry.isUserSet(context, Capabilities.HARD_BLOCK)) {
            // Old rule: Soft nudged, everything else blocked outright.
            CapabilityRegistry.setEnabled(
                context,
                Capabilities.HARD_BLOCK,
                mode != FocusMode.SOFT
            )
        }

        // Old rule: only Sanctuary and Kiosk hid apps from the launcher, even
        // when the hardening flag was on. The flag alone drives it now, so an
        // upgrading Soft/Block user would suddenly get hiding they never had.
        if (mode != FocusMode.SANCTUARY && mode != FocusMode.KIOSK &&
            CapabilityRegistry.isEnabled(context, Capabilities.HIDE_BLOCKED_APPS)
        ) {
            CapabilityRegistry.setEnabled(context, Capabilities.HIDE_BLOCKED_APPS, false)
        }

        // Same for suspend, which Soft used to be exempt from.
        if (mode == FocusMode.SOFT &&
            CapabilityRegistry.isEnabled(context, Capabilities.SUSPEND_BLOCKED_APPS)
        ) {
            CapabilityRegistry.setEnabled(context, Capabilities.SUSPEND_BLOCKED_APPS, false)
        }

        // Record the mode whose template these values represent, so the first
        // Start after upgrading does not immediately re-apply and undo them.
        SessionManager.markPresetApplied(context, mode)
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

package com.focuslock.mdm

import android.content.Context
import org.json.JSONObject

/**
 * Stable ids for the detail screens a capability can open.
 * Kept as plain strings so the registry never has to know about Activity classes.
 */
object Screens {
    const val APP_RULES = "appRules"
    const val WEB_RULES = "webRules"
    const val KEYWORDS = "keywords"
    const val LIMITS = "limits"
    const val SCHEDULES = "schedules"
    const val BEDTIME = "bedtime"
    const val PLACES = "places"
    const val RULES_LIST = "rulesList"
    const val ALWAYS_ALLOWED = "alwaysAllowed"
    const val PROFILES = "profiles"
    const val ANALYTICS = "analytics"
    const val TASKS = "tasks"
    const val EARN = "earn"
}

/**
 * Where a capability lives in the Rules -> Capabilities screen.
 * Order here is the order the user sees.
 */
enum class CapabilityGroup(val label: String, val blurb: String) {
    MODES("Modes", "How strong the lock is allowed to get."),
    BLOCKING("Blocking", "What FocusLock is allowed to stop."),
    CONTENT("Content", "Guards that read the screen, not just the app name."),
    ENVIRONMENT("Environment", "Rules that follow time, place and network."),
    TOOLS("Replacements", "The calm things FocusLock offers instead of the feed."),
    INSIGHT("Insight", "What FocusLock remembers and shows you about yourself."),
    HARDENING("Hardening", "Escape routes you can choose to close.")
}

/**
 * One user-owned switch.
 *
 * [weakenNote] is shown exactly once, as a single honest line, when the user
 * turns a safety capability OFF. It never nags and never blocks the change:
 * the point is informed autonomy, not guilt.
 */
data class CapabilitySpec(
    val id: String,
    val label: String,
    val blurb: String,
    val group: CapabilityGroup,
    val default: Boolean,
    val weakenNote: String? = null,
    val needsAccessibility: Boolean = false,
    val needsDeviceOwner: Boolean = false,
    val needsUsageAccess: Boolean = false,
    val needsNotificationAccess: Boolean = false,
    val needsLocation: Boolean = false,
    val detailScreen: String? = null
)

/**
 * The Capability Registry.
 *
 * Every behaviour in FocusLock is gated on one of these ids. Nothing is
 * hardcoded on: the app reads the registry, the user writes it, and onboarding
 * only ever proposes a starting set.
 */
object Capabilities {

    // Modes
    const val ADVISORY_MODE = "advisoryMode"
    const val SANCTUARY_MODE = "sanctuaryMode"
    const val KIOSK_MODE = "kioskMode"

    // How a running session behaves. These used to be decided by the FocusMode
    // enum, which meant a mode was a cage: pick Kiosk and you could not soften
    // any part of it. They are ordinary switches now, so a mode is a starting
    // point you can edit afterwards in You -> Advanced.
    const val CAN_END_EARLY = "canEndEarly"
    const val HARD_BLOCK = "hardBlock"

    // Blocking
    const val APP_BLOCK = "appBlock"
    const val WEB_BLOCK = "webBlock"
    const val RULE_ENGINE = "ruleEngine"
    const val ALWAYS_ALLOWED = "alwaysAllowed"
    const val LAUNCH_FRICTION = "launchFriction"
    const val TAKE_A_BREAK = "takeABreak"
    const val PER_APP_LIMITS = "perAppLimits"
    const val OPEN_COUNT_LIMITS = "openCountLimits"

    // Content
    const val CONTENT_GUARD = "contentGuard"
    const val KEYWORD_BLOCK = "keywordBlock"
    const val WHATSAPP_GUARD = "whatsappGuard"
    const val SHORTS_BLOCK = "shortsBlock"
    const val REELS_BLOCK = "reelsBlock"
    const val ADULT_BLOCK = "adultBlock"

    // Environment
    const val SCHEDULES = "schedules"
    const val BEDTIME_MODE = "bedtimeMode"
    const val LOCATION_BLOCK = "locationBlock"
    const val WIFI_CONDITIONS = "wifiConditions"
    const val NOTIFICATION_BLOCK = "notificationBlock"

    // Replacements
    const val SAFE_BROWSER = "safeBrowser"
    const val TEXT_SEARCH = "textSearch"
    const val VIDEO_LIBRARY = "videoLibrary"
    const val REPLACEMENT_SUGGESTIONS = "replacementSuggestions"

    // Earn
    const val EARN_MODE = "earnMode"

    // Insight
    const val ANALYTICS = "analytics"
    const val STREAKS = "streaks"
    const val SELF_COMPASSION_COPY = "selfCompassionCopy"
    const val PROFILES = "profiles"
    const val SOCIAL = "social"

    // Hardening
    const val SUSPEND_BLOCKED_APPS = "suspendBlockedApps"
    const val HIDE_BLOCKED_APPS = "hideBlockedApps"
    const val STATUS_BAR_LOCK = "statusBarLock"
    const val KEYGUARD_LOCK = "keyguardLock"
    const val UNINSTALL_PROTECTION = "uninstallProtection"
    const val SAFE_BOOT_BLOCK = "safeBootBlock"
    const val PERSISTENT_HOME = "persistentHome"

    val all: List<CapabilitySpec> = listOf(

        CapabilitySpec(
            id = ADVISORY_MODE,
            label = "Soft mode",
            blurb = "A pause and a gentle nudge when you open something distracting. Nothing is forced.",
            group = CapabilityGroup.MODES,
            default = true,
            needsUsageAccess = true
        ),
        CapabilitySpec(
            id = SANCTUARY_MODE,
            label = "Sanctuary mode",
            blurb = "Your normal home screen stays. Distracting apps disappear from it and go quiet.",
            group = CapabilityGroup.MODES,
            default = true,
            weakenNote = "Off: Sanctuary disappears from the mode picker. Block still stops apps " +
                "opening, it just leaves them sitting on your home screen.",
            needsDeviceOwner = true
        ),
        CapabilitySpec(
            id = KIOSK_MODE,
            label = "Kiosk mode",
            blurb = "FocusLock becomes the whole phone for a set stretch of time. The strongest setting there is.",
            group = CapabilityGroup.MODES,
            default = true,
            weakenNote = "Kiosk is the only mode that closes Safe Mode and the launcher. Without it there is a way back out.",
            needsDeviceOwner = true
        ),

        CapabilitySpec(
            id = CAN_END_EARLY,
            label = "Let me end a session early",
            blurb = "Shows an \"End this session\" button while a session is running.",
            group = CapabilityGroup.MODES,
            default = true,
            weakenNote = "Off: there is no early exit. The session runs to the end of its timer, " +
                "and in Kiosk the only way out before then is a factory reset, which wipes the phone."
        ),
        CapabilitySpec(
            id = HARD_BLOCK,
            label = "Actually stop blocked apps",
            blurb = "A blocked app does not open at all. Turn this off and you get a pause screen " +
                "you can walk through instead.",
            group = CapabilityGroup.MODES,
            default = true,
            weakenNote = "Off: blocked apps still open. You get a reminder first, and then you " +
                "decide. That is Soft mode's behaviour, applied to whatever mode you are in."
        ),

        CapabilitySpec(
            id = APP_BLOCK,
            label = "App blocking",
            blurb = "Stop chosen apps from opening while a session is running.",
            group = CapabilityGroup.BLOCKING,
            default = true,
            weakenNote = "With this off, every app on the phone opens normally. That is your call.",
            needsUsageAccess = true,
            detailScreen = Screens.APP_RULES
        ),
        CapabilitySpec(
            id = WEB_BLOCK,
            label = "Web blocking",
            blurb = "Hold the browser to your allowlist and keep the open internet out of Chrome.",
            group = CapabilityGroup.BLOCKING,
            default = true,
            weakenNote = "With this off, any site loads in any browser. That is your call.",
            needsDeviceOwner = true,
            detailScreen = Screens.WEB_RULES
        ),
        CapabilitySpec(
            id = RULE_ENGINE,
            label = "Custom rules",
            blurb = "Your own if-this-then-that rules: a target, a condition, an outcome.",
            group = CapabilityGroup.BLOCKING,
            default = true,
            detailScreen = Screens.RULES_LIST
        ),
        CapabilitySpec(
            id = ALWAYS_ALLOWED,
            label = "Always-allowed apps",
            blurb = "Essentials that stay open through everything: calls, maps, notes. Nothing overrides this list.",
            group = CapabilityGroup.BLOCKING,
            default = true,
            weakenNote = "With this off, a session can cut off calls and maps too. Most people keep this on.",
            detailScreen = Screens.ALWAYS_ALLOWED
        ),
        CapabilitySpec(
            id = LAUNCH_FRICTION,
            label = "Breathing pause",
            blurb = "A few slow seconds before a distracting app opens, with a way out. Often that is enough.",
            group = CapabilityGroup.BLOCKING,
            default = true,
            weakenNote = "Off: apps you set to pause first open immediately, with no breath in between.",
            needsUsageAccess = true
        ),
        CapabilitySpec(
            id = TAKE_A_BREAK,
            label = "Take a break",
            blurb = "Unlock a blocked app for a few minutes instead of abandoning the whole session.",
            group = CapabilityGroup.BLOCKING,
            default = true,
            weakenNote = "Off: a blocked app stays blocked for the whole session, with no exceptions. " +
                "Some people need exactly that. Others end the whole session instead of taking " +
                "five minutes."
        ),
        CapabilitySpec(
            id = PER_APP_LIMITS,
            label = "Daily time limits",
            blurb = "Give an app a budget of minutes a day. When it runs out, it closes until tomorrow.",
            group = CapabilityGroup.BLOCKING,
            default = false,
            weakenNote = "Off: daily minute budgets stop counting. Apps you limited open for as long as " +
                "you like.",
            needsUsageAccess = true,
            detailScreen = Screens.LIMITS
        ),
        CapabilitySpec(
            id = OPEN_COUNT_LIMITS,
            label = "Daily open limits",
            blurb = "Cap how many times an app can be opened in a day. Catches the reflex, not the minutes.",
            group = CapabilityGroup.BLOCKING,
            default = false,
            needsUsageAccess = true,
            detailScreen = Screens.LIMITS
        ),

        // Earn Mode sits in Modes because that is what it is: a fifth way to run
        // the phone, layered on any of the other four. Off by default, always.
        CapabilitySpec(
            id = EARN_MODE,
            label = "Earn mode",
            blurb = "Finish real tasks to unlock leisure time. You set the rate, the reward and what counts as done.",
            group = CapabilityGroup.MODES,
            default = false,
            weakenNote = "Off: tasks stay a plain to-do list and finishing one earns nothing. The list " +
                "still works, it just does not pay.",
            needsUsageAccess = true,
            detailScreen = Screens.TASKS
        ),

        CapabilitySpec(
            id = CONTENT_GUARD,
            label = "Content guard",
            blurb = "Reads what is on screen and backs out of the surfaces you named, inside apps you still want.",
            group = CapabilityGroup.CONTENT,
            default = true,
            weakenNote = "Off: FocusLock stops reading the screen. An app you allowed is allowed " +
                "entirely, including the parts of it you were trying to avoid.",
            needsAccessibility = true
        ),
        CapabilitySpec(
            id = KEYWORD_BLOCK,
            label = "Keyword guard",
            blurb = "Your own words. If one shows up on screen, FocusLock steps back out of that screen.",
            group = CapabilityGroup.CONTENT,
            default = true,
            needsAccessibility = true,
            detailScreen = Screens.KEYWORDS
        ),
        CapabilitySpec(
            id = WHATSAPP_GUARD,
            label = "WhatsApp guard",
            blurb = "Keeps chats working while quietly closing Channels, Updates and Meta AI.",
            group = CapabilityGroup.CONTENT,
            default = true,
            needsAccessibility = true
        ),
        CapabilitySpec(
            id = SHORTS_BLOCK,
            label = "Shorts blocker",
            blurb = "Neutralises the YouTube Shorts shelf and player. The rest of YouTube still works.",
            group = CapabilityGroup.CONTENT,
            default = true,
            needsAccessibility = true
        ),
        CapabilitySpec(
            id = REELS_BLOCK,
            label = "Reels blocker",
            blurb = "Closes Instagram, Facebook and Snapchat reel surfaces the moment they open.",
            group = CapabilityGroup.CONTENT,
            default = true,
            needsAccessibility = true
        ),
        CapabilitySpec(
            id = ADULT_BLOCK,
            label = "Adult content filter",
            blurb = "A maintained keyword and domain filter, on top of forced SafeSearch.",
            group = CapabilityGroup.CONTENT,
            default = true,
            needsAccessibility = true
        ),

        CapabilitySpec(
            id = SCHEDULES,
            label = "Schedules",
            blurb = "Windows of the week where the phone goes quiet on its own.",
            group = CapabilityGroup.ENVIRONMENT,
            default = true,
            weakenNote = "Off: quiet windows stop firing. Every session becomes something you have to " +
                "remember to start.",
            detailScreen = Screens.SCHEDULES
        ),
        CapabilitySpec(
            id = BEDTIME_MODE,
            label = "Bedtime",
            blurb = "Dims the screen, switches to a dark quiet theme and locks social after your cut-off.",
            group = CapabilityGroup.ENVIRONMENT,
            default = false,
            weakenNote = "Off: nothing changes at night. The phone is as available at 2am as it is at " +
                "2pm.",
            detailScreen = Screens.BEDTIME
        ),
        CapabilitySpec(
            id = LOCATION_BLOCK,
            label = "Place rules",
            blurb = "Block or allow apps depending on where you are. Social off at school, maps always on.",
            group = CapabilityGroup.ENVIRONMENT,
            default = false,
            needsLocation = true,
            detailScreen = Screens.PLACES
        ),
        CapabilitySpec(
            id = WIFI_CONDITIONS,
            label = "Network rules",
            blurb = "Use the Wi-Fi you are on as a condition: home network, school network, mobile data.",
            group = CapabilityGroup.ENVIRONMENT,
            default = false,
            detailScreen = Screens.PLACES
        ),
        CapabilitySpec(
            id = NOTIFICATION_BLOCK,
            label = "Notification shield",
            blurb = "Holds back alerts from blocked apps during a session. Always-allowed apps still get through.",
            group = CapabilityGroup.ENVIRONMENT,
            default = true,
            weakenNote = "Off: blocked apps can still buzz. The icon is out of reach but the pull is " +
                "not.",
            needsNotificationAccess = true
        ),

        CapabilitySpec(
            id = SAFE_BROWSER,
            label = "Safe browser",
            blurb = "A curated internet that opens instantly and has nowhere to fall into.",
            group = CapabilityGroup.TOOLS,
            default = true
        ),
        CapabilitySpec(
            id = TEXT_SEARCH,
            label = "Text search",
            blurb = "Google with the images, videos and thumbnails stripped out. Reading, not scrolling.",
            group = CapabilityGroup.TOOLS,
            default = true
        ),
        CapabilitySpec(
            id = VIDEO_LIBRARY,
            label = "Video library",
            blurb = "Your own folder, one new unlock every 24 hours. Yours to keep once opened.",
            group = CapabilityGroup.TOOLS,
            default = true
        ),
        CapabilitySpec(
            id = REPLACEMENT_SUGGESTIONS,
            label = "Offer an alternative",
            blurb = "When something is blocked, suggest a calm thing to do instead of a dead end.",
            group = CapabilityGroup.TOOLS,
            default = true
        ),

        CapabilitySpec(
            id = ANALYTICS,
            label = "Local analytics",
            blurb = "Time by app and category, kept on this phone only. Nothing leaves the device.",
            group = CapabilityGroup.INSIGHT,
            default = true,
            needsUsageAccess = true,
            detailScreen = Screens.ANALYTICS
        ),
        CapabilitySpec(
            id = STREAKS,
            label = "Gentle streaks",
            blurb = "Counts the days you showed up. A missed day pauses the count, it never shatters it.",
            group = CapabilityGroup.INSIGHT,
            default = true
        ),
        CapabilitySpec(
            id = SELF_COMPASSION_COPY,
            label = "Kind wording",
            blurb = "Block screens speak plainly and without blame. Turn off for blunt, minimal wording.",
            group = CapabilityGroup.INSIGHT,
            default = true
        ),
        CapabilitySpec(
            id = PROFILES,
            label = "Profiles",
            blurb = "Save a whole setup and switch between them: exam week, weekend, travel.",
            group = CapabilityGroup.INSIGHT,
            default = true,
            detailScreen = Screens.PROFILES
        ),
        CapabilitySpec(
            id = SOCIAL,
            label = "Study friend",
            blurb = "Share a read-only session status with one person you choose. Off unless you turn it on.",
            group = CapabilityGroup.INSIGHT,
            default = false
        ),

        CapabilitySpec(
            id = SUSPEND_BLOCKED_APPS,
            label = "Suspend blocked apps",
            blurb = "Blocked apps become unopenable at the system level, not just intercepted.",
            group = CapabilityGroup.HARDENING,
            default = true,
            weakenNote = "With this off, blocked apps can still start for a moment before being caught.",
            needsDeviceOwner = true
        ),
        CapabilitySpec(
            id = HIDE_BLOCKED_APPS,
            label = "Hide blocked apps",
            blurb = "Blocked apps vanish from the launcher during a session. Out of sight, out of reach.",
            group = CapabilityGroup.HARDENING,
            default = false,
            weakenNote = "With this off, blocked apps stay on your home screen. They still will not " +
                "open, but you see them every time you look.",
            needsDeviceOwner = true
        ),
        CapabilitySpec(
            id = STATUS_BAR_LOCK,
            label = "Lock the status bar",
            blurb = "Disables the notification shade and quick settings during a kiosk session.",
            group = CapabilityGroup.HARDENING,
            default = false,
            weakenNote = "Leaving this off keeps the shade reachable, a common way back out mid-session.",
            needsDeviceOwner = true
        ),
        CapabilitySpec(
            id = KEYGUARD_LOCK,
            label = "Simplify the lock screen",
            blurb = "Strips widgets and shortcuts off the lock screen while a kiosk session runs.",
            group = CapabilityGroup.HARDENING,
            default = false,
            needsDeviceOwner = true
        ),
        CapabilitySpec(
            id = UNINSTALL_PROTECTION,
            label = "Block uninstalling FocusLock",
            blurb = "Stops FocusLock itself being removed while a session is running.",
            group = CapabilityGroup.HARDENING,
            default = true,
            weakenNote = "With this off, a session can be ended by uninstalling the app.",
            needsDeviceOwner = true
        ),
        CapabilitySpec(
            id = SAFE_BOOT_BLOCK,
            label = "Disable Safe Mode",
            blurb = "Closes the Safe Mode reboot, which would otherwise start the phone with FocusLock inert.",
            group = CapabilityGroup.HARDENING,
            default = true,
            weakenNote = "Safe Mode is the classic way out. Leaving it open is a real escape route.",
            needsDeviceOwner = true
        ),
        CapabilitySpec(
            id = PERSISTENT_HOME,
            label = "FocusLock as home screen",
            blurb = "During a kiosk session the home button lands here instead of the launcher.",
            group = CapabilityGroup.HARDENING,
            default = true,
            weakenNote = "With this off, the home button returns to your normal launcher mid-session.",
            needsDeviceOwner = true
        )
    )

    private val byId: Map<String, CapabilitySpec> = all.associateBy { it.id }

    fun spec(id: String): CapabilitySpec? = byId[id]

    fun grouped(): List<Pair<CapabilityGroup, List<CapabilitySpec>>> =
        CapabilityGroup.values()
            .map { group -> group to all.filter { it.group == group } }
            .filter { it.second.isNotEmpty() }
}

/**
 * Reads and writes the user's capability choices.
 *
 * A capability is on when the user says it is. Until they touch it, the spec
 * default applies. Nothing in the app flips a switch behind their back: the
 * only writer other than the user is onboarding, and only on an explicit tap.
 */
object CapabilityRegistry {

    private const val KEY_ENABLED = "cap_enabled_json"
    private const val KEY_PARAMS = "cap_params_json"
    private const val KEY_SEEDED = "cap_seeded_v1"

    fun isEnabled(context: Context, id: String): Boolean {
        val overrides = FocusStore.getJsonObject(context, KEY_ENABLED)
        if (overrides.has(id)) return overrides.optBoolean(id, false)
        return Capabilities.spec(id)?.default ?: false
    }

    fun isUserSet(context: Context, id: String): Boolean =
        FocusStore.getJsonObject(context, KEY_ENABLED).has(id)

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val overrides = FocusStore.getJsonObject(context, KEY_ENABLED)
        overrides.put(id, enabled)
        FocusStore.setJsonObject(context, KEY_ENABLED, overrides)
        PolicySync.request(context, "capability:" + id)
    }

    /**
     * Loads a mode's template.
     *
     * Same merge as [applySet] but deliberately does NOT mark the registry
     * seeded: seeding means "this person has been through setup", and picking
     * a mode on the dashboard is not that. Conflating the two would skip
     * onboarding for someone who had never seen it.
     */
    fun applyPreset(context: Context, values: Map<String, Boolean>) {
        if (values.isEmpty()) return
        val overrides = FocusStore.getJsonObject(context, KEY_ENABLED)
        values.forEach { entry -> overrides.put(entry.key, entry.value) }
        FocusStore.setJsonObject(context, KEY_ENABLED, overrides)
        PolicySync.request(context, "capability:preset")
    }

    /** Applies a whole proposed set at once. Used only by onboarding, on confirm. */
    fun applySet(context: Context, values: Map<String, Boolean>) {
        val overrides = FocusStore.getJsonObject(context, KEY_ENABLED)
        values.forEach { entry -> overrides.put(entry.key, entry.value) }
        FocusStore.setJsonObject(context, KEY_ENABLED, overrides)
        FocusStore.setBool(context, KEY_SEEDED, true)
        PolicySync.request(context, "capability:set")
    }

    fun hasBeenSeeded(context: Context): Boolean = FocusStore.getBool(context, KEY_SEEDED, false)

    fun markSeeded(context: Context) {
        FocusStore.setBool(context, KEY_SEEDED, true)
    }

    fun resetToDefaults(context: Context) {
        FocusStore.remove(context, KEY_ENABLED)
        PolicySync.request(context, "capability:reset")
    }

    fun enabledSnapshot(context: Context): Map<String, Boolean> =
        Capabilities.all.associate { it.id to isEnabled(context, it.id) }

    fun getIntParam(context: Context, id: String, key: String, fallback: Int): Int {
        val forId = FocusStore.getJsonObject(context, KEY_PARAMS).optJSONObject(id) ?: return fallback
        return forId.optInt(key, fallback)
    }

    fun setIntParam(context: Context, id: String, key: String, value: Int) {
        val params = FocusStore.getJsonObject(context, KEY_PARAMS)
        val forId = params.optJSONObject(id) ?: JSONObject()
        forId.put(key, value)
        params.put(id, forId)
        FocusStore.setJsonObject(context, KEY_PARAMS, params)
        PolicySync.request(context, "capabilityParam:" + id)
    }

    fun getBoolParam(context: Context, id: String, key: String, fallback: Boolean): Boolean {
        val forId = FocusStore.getJsonObject(context, KEY_PARAMS).optJSONObject(id) ?: return fallback
        return forId.optBoolean(key, fallback)
    }

    fun setBoolParam(context: Context, id: String, key: String, value: Boolean) {
        val params = FocusStore.getJsonObject(context, KEY_PARAMS)
        val forId = params.optJSONObject(id) ?: JSONObject()
        forId.put(key, value)
        params.put(id, forId)
        FocusStore.setJsonObject(context, KEY_PARAMS, params)
        PolicySync.request(context, "capabilityParam:" + id)
    }

    fun exportJson(context: Context): JSONObject {
        val out = JSONObject()
        out.put("enabled", FocusStore.getJsonObject(context, KEY_ENABLED))
        out.put("params", FocusStore.getJsonObject(context, KEY_PARAMS))
        return out
    }

    fun importJson(context: Context, json: JSONObject) {
        json.optJSONObject("enabled")?.let { FocusStore.setJsonObject(context, KEY_ENABLED, it) }
        json.optJSONObject("params")?.let { FocusStore.setJsonObject(context, KEY_PARAMS, it) }
        PolicySync.request(context, "capability:import")
    }
}

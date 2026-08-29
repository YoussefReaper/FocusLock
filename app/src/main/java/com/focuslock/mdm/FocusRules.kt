package com.focuslock.mdm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

enum class RuleTargetType(val id: String, val label: String) {
    APP("app", "An app"),
    CATEGORY("category", "A category"),
    ALL("all", "Everything");

    companion object {
        fun fromId(id: String?): RuleTargetType = values().firstOrNull { it.id == id } ?: APP
    }
}

enum class RuleConditionType(val id: String, val label: String, val blurb: String) {
    ALWAYS("always", "Always", "Whenever a session is running."),
    TIME("time", "Between two times", "A window of the day, every day it applies."),
    DAYS("days", "On certain days", "Weekdays, weekends, or the days you pick."),
    PLACE("place", "At a place", "Uses one of your saved places."),
    WIFI("wifi", "On a network", "When connected to a named Wi-Fi."),
    USAGE_OVER("usageOver", "After N minutes today", "Kicks in once you have spent that long in it."),
    OPENS_OVER("opensOver", "After N opens today", "Kicks in once you have reached for it that often."),
    SESSION_ONLY("sessionOnly", "Only during a session", "Silent when no session is running.");

    companion object {
        fun fromId(id: String?): RuleConditionType = values().firstOrNull { it.id == id } ?: ALWAYS
    }
}

enum class RuleAction(val id: String, val label: String, val blurb: String) {
    ALLOW("allow", "Let it through", "An exception that beats the rules below it."),
    FRICTION("friction", "Pause first", "A breathing moment, then your choice."),
    BLOCK("block", "Block it", "Does not open while the condition holds."),
    ALLOW_TEMP("allowTemp", "Allow for a few minutes", "Opens a timed pass instead of a hard stop.");

    companion object {
        fun fromId(id: String?): RuleAction = values().firstOrNull { it.id == id } ?: BLOCK
    }
}

/**
 * One user-written rule: a target, a condition, an outcome.
 *
 * Rules are evaluated top to bottom and the first match wins, which is the only
 * ordering model people reliably predict. The list is drag-free but reorderable
 * with explicit move up / move down, so the priority is always visible.
 */
data class Rule(
    val id: String,
    val label: String,
    val targetType: RuleTargetType,
    val targetValue: String,
    val conditionType: RuleConditionType,
    val conditionValue: String,
    val conditionStart: Int,
    val conditionEnd: Int,
    val conditionDays: Set<Int>,
    val conditionNumber: Int,
    val action: RuleAction,
    val actionMinutes: Int,
    val enabled: Boolean
)

object RuleStore {

    private const val KEY_RULES = "focus_rules_json"

    /** Cached per policy revision: the enforcement loop walks this every tick. */
    fun all(context: Context): List<Rule> = PolicyCache.get("focusRules") {
        val array = FocusStore.getJsonArray(context, KEY_RULES)
        val out = ArrayList<Rule>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val days = FocusStore.jsonArrayToStringList(obj.optJSONArray("days") ?: JSONArray())
                .mapNotNull { it.toIntOrNull() }
                .toSet()
            out.add(
                Rule(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    label = obj.optString("label", ""),
                    targetType = RuleTargetType.fromId(obj.optString("targetType", "")),
                    targetValue = obj.optString("targetValue", ""),
                    conditionType = RuleConditionType.fromId(obj.optString("conditionType", "")),
                    conditionValue = obj.optString("conditionValue", ""),
                    conditionStart = obj.optInt("start", 0),
                    conditionEnd = obj.optInt("end", 0),
                    conditionDays = days,
                    conditionNumber = obj.optInt("number", 0),
                    action = RuleAction.fromId(obj.optString("action", "")),
                    actionMinutes = obj.optInt("actionMinutes", 5),
                    enabled = obj.optBoolean("enabled", true)
                )
            )
        }
        out
    }

    fun save(context: Context, rules: List<Rule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject()
            obj.put("id", rule.id)
            obj.put("label", rule.label)
            obj.put("targetType", rule.targetType.id)
            obj.put("targetValue", rule.targetValue)
            obj.put("conditionType", rule.conditionType.id)
            obj.put("conditionValue", rule.conditionValue)
            obj.put("start", rule.conditionStart)
            obj.put("end", rule.conditionEnd)
            obj.put("days", FocusStore.stringListToJsonArray(rule.conditionDays.map { it.toString() }))
            obj.put("number", rule.conditionNumber)
            obj.put("action", rule.action.id)
            obj.put("actionMinutes", rule.actionMinutes)
            obj.put("enabled", rule.enabled)
            array.put(obj)
        }
        FocusStore.setJsonArray(context, KEY_RULES, array)
        PolicySync.request(context, "rules")
    }

    fun add(context: Context, rule: Rule) = save(context, all(context) + rule)

    fun update(context: Context, rule: Rule) =
        save(context, all(context).map { if (it.id == rule.id) rule else it })

    fun remove(context: Context, id: String) =
        save(context, all(context).filterNot { it.id == id })

    fun move(context: Context, id: String, delta: Int) {
        val rules = all(context).toMutableList()
        val index = rules.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, rules.size - 1)
        if (target == index) return
        val rule = rules.removeAt(index)
        rules.add(target, rule)
        save(context, rules)
    }

    fun newRule(
        label: String,
        targetType: RuleTargetType,
        targetValue: String,
        conditionType: RuleConditionType,
        action: RuleAction
    ): Rule = Rule(
        id = UUID.randomUUID().toString(),
        label = label,
        targetType = targetType,
        targetValue = targetValue,
        conditionType = conditionType,
        conditionValue = "",
        conditionStart = 9 * 60,
        conditionEnd = 17 * 60,
        conditionDays = emptySet(),
        conditionNumber = 30,
        action = action,
        actionMinutes = 5,
        enabled = true
    )

    fun exportJson(context: Context): JSONArray = FocusStore.getJsonArray(context, KEY_RULES)

    fun importJson(context: Context, array: JSONArray) {
        FocusStore.setJsonArray(context, KEY_RULES, array)
        PolicySync.request(context, "rules:import")
    }
}

/** Why an app was stopped, and what the person can do about it. */
enum class GuardOutcome {
    ALLOW,
    PAUSE,
    BLOCK
}

data class GuardDecision(
    val packageName: String,
    val outcome: GuardOutcome,
    val headline: String,
    val detail: String,
    val source: String,
    val offersBreak: Boolean,
    val breakSuggestion: Boolean = false
) {
    val isBlocked: Boolean get() = outcome == GuardOutcome.BLOCK
    val isPause: Boolean get() = outcome == GuardOutcome.PAUSE
    val isAllowed: Boolean get() = outcome == GuardOutcome.ALLOW
}

/**
 * The one place that answers "can this app be in front right now".
 *
 * Everything that used to be scattered through `AppBlockerService.isAllowed`
 * lives here, reads from user-owned stores, and returns a decision carrying the
 * words the intercept screen will show. Enforcement never writes its own copy.
 */
object RuleEngine {

    fun decide(context: Context, packageName: String, className: String? = null): GuardDecision {
        val allow = { source: String ->
            GuardDecision(packageName, GuardOutcome.ALLOW, "", "", source, false)
        }

        if (packageName == context.packageName) return allow("self")
        if (SystemSurfaces.isCritical(packageName)) return allow("system")

        // Settings pages the user opened from inside FocusLock, plus the USB
        // page, stay reachable: locking someone out of their own USB settings
        // during a 90-day session is how a phone becomes a brick.
        if (SystemSurfaces.isUsbSettingsScreen(packageName, className)) return allow("usb")
        if (LockManager.isSettingsAccessAllowed(context) && SystemSurfaces.isSettings(packageName)) {
            return allow("settingsWindow")
        }
        if (SystemSurfaces.isOverlayPermissionScreen(packageName, className)) {
            return GuardDecision(
                packageName,
                GuardOutcome.BLOCK,
                Copy.overlayGuardHeadline(context),
                Copy.overlayGuardDetail(context),
                "overlayGuard",
                offersBreak = false
            )
        }

        if (AppRules.isAlwaysAllowed(context, packageName)) return allow("alwaysAllowed")

        if (TakeABreak.hasActivePass(context, packageName)) return allow("break")

        // An Earn task narrows before anything else gets a say. Checking it here
        // rather than last gives intersection semantics: the task can only ever
        // subtract from what the running mode already permits, never add to it,
        // and the message the person sees names the task they chose.
        EarnSession.activeTask(context)?.let { task ->
            val narrows = EarnSession.isStandalone(context) || task.allowedApps.isNotEmpty()
            if (narrows && packageName !in EarnSession.allowedPackages(context, task)) {
                return GuardDecision(
                    packageName,
                    GuardOutcome.BLOCK,
                    Copy.earnHeadline(context, packageName),
                    Copy.earnDetail(context, task),
                    "earn",
                    offersBreak = false
                )
            }
        }

        // Schedules and bedtime run on the clock and do not need a session.
        ScheduleManager.activeWindowIfEnabled(context)?.let { window ->
            val allowed = AppRules.alwaysAllowed(context) + window.allowedApps
            if (packageName !in allowed) {
                return GuardDecision(
                    packageName,
                    GuardOutcome.BLOCK,
                    Copy.scheduleHeadline(context, window),
                    Copy.scheduleDetail(context, window),
                    "schedule",
                    offersBreak = TakeABreak.canStart(context)
                )
            }
            return allow("scheduleAllowed")
        }

        if (Bedtime.blocks(context, packageName)) {
            return GuardDecision(
                packageName,
                GuardOutcome.BLOCK,
                Copy.bedtimeHeadline(context),
                Copy.bedtimeDetail(context),
                "bedtime",
                offersBreak = TakeABreak.canStart(context)
            )
        }

        PlaceRules.blocks(context, packageName)?.let { place ->
            return GuardDecision(
                packageName,
                GuardOutcome.BLOCK,
                Copy.placeHeadline(context, place),
                Copy.placeDetail(context, place),
                "place",
                offersBreak = TakeABreak.canStart(context)
            )
        }

        // Limits bite whether or not a session is running: a daily budget that
        // only applies during sessions is not a daily budget.
        AppLimits.exhaustedReason(context, packageName)?.let { reason ->
            return GuardDecision(
                packageName,
                GuardOutcome.BLOCK,
                Copy.limitHeadline(context, packageName),
                reason,
                "limit",
                offersBreak = TakeABreak.canStart(context)
            )
        }

        if (CapabilityRegistry.isEnabled(context, Capabilities.RULE_ENGINE)) {
            matchRule(context, packageName)?.let { match ->
                when (match.action) {
                    RuleAction.ALLOW -> return allow("rule:" + match.id)
                    RuleAction.ALLOW_TEMP -> {
                        if (TakeABreak.hasActivePass(context, packageName)) return allow("rule:temp")
                        return GuardDecision(
                            packageName,
                            GuardOutcome.BLOCK,
                            Copy.ruleHeadline(context, match),
                            Copy.ruleDetail(context, match),
                            "rule",
                            offersBreak = true,
                            breakSuggestion = true
                        )
                    }
                    RuleAction.FRICTION -> return GuardDecision(
                        packageName,
                        GuardOutcome.PAUSE,
                        Copy.pauseHeadline(context, packageName),
                        Copy.ruleDetail(context, match),
                        "rule",
                        offersBreak = false
                    )
                    RuleAction.BLOCK -> return GuardDecision(
                        packageName,
                        GuardOutcome.BLOCK,
                        Copy.ruleHeadline(context, match),
                        Copy.ruleDetail(context, match),
                        "rule",
                        offersBreak = TakeABreak.canStart(context)
                    )
                }
            }
        }

        // Earned leisure, being spent right now. It lifts app rules and session
        // blocks, which is what was earned; it deliberately does not lift a
        // schedule, bedtime or a daily budget, because those are commitments
        // about *when*, and the reward was for work, not for a later bedtime.
        if (EarnBudget.isSpending(context)) return allow("earnBudget")

        val sessionActive = SessionManager.isActive(context)
        if (!sessionActive) return allow("noSession")

        // Kiosk inverts the model: only what you named stays open. Driven by
        // the lock-task primitive rather than the enum directly, so softening a
        // mode in Advanced cannot accidentally strand someone inside an
        // allowlist they can no longer edit.
        if (SessionManager.usesAllowlistModel(context) && AppRules.isKioskAllowlistMode(context)) {
            if (packageName !in AppRules.kioskAllowlist(context)) {
                if (SystemSurfaces.isLauncher(packageName)) {
                    return GuardDecision(
                        packageName,
                        GuardOutcome.BLOCK,
                        Copy.kioskHomeHeadline(context),
                        Copy.kioskHomeDetail(context),
                        "kioskHome",
                        offersBreak = false
                    )
                }
                return GuardDecision(
                    packageName,
                    GuardOutcome.BLOCK,
                    Copy.blockHeadline(context, packageName),
                    Copy.kioskDetail(context),
                    "kiosk",
                    offersBreak = false
                )
            }
            return allow("kioskAllowlist")
        }

        if (SystemSurfaces.isLauncher(packageName)) return allow("launcher")

        if (!CapabilityRegistry.isEnabled(context, Capabilities.APP_BLOCK)) return allow("appBlockOff")

        return when (AppRules.effectivePolicy(context, packageName)) {
            AppPolicy.ALLOW -> allow("policyAllow")
            AppPolicy.LIMIT -> allow("policyLimit")
            AppPolicy.FRICTION -> {
                if (!CapabilityRegistry.isEnabled(context, Capabilities.LAUNCH_FRICTION)) {
                    allow("frictionOff")
                } else {
                    GuardDecision(
                        packageName,
                        GuardOutcome.PAUSE,
                        Copy.pauseHeadline(context, packageName),
                        Copy.pauseDetail(context),
                        "friction",
                        offersBreak = false
                    )
                }
            }
            AppPolicy.BLOCK, AppPolicy.HIDE -> {
                if (!SessionManager.blocksOutright(context)) {
                    GuardDecision(
                        packageName,
                        GuardOutcome.PAUSE,
                        Copy.pauseHeadline(context, packageName),
                        Copy.softDetail(context),
                        "soft",
                        offersBreak = false
                    )
                } else {
                    GuardDecision(
                        packageName,
                        GuardOutcome.BLOCK,
                        Copy.blockHeadline(context, packageName),
                        Copy.blockDetail(context, packageName),
                        "policy",
                        offersBreak = TakeABreak.canStart(context)
                    )
                }
            }
        }
    }

    /** Convenience for callers that only need a yes or no. */
    fun isAllowed(context: Context, packageName: String, className: String? = null): Boolean =
        decide(context, packageName, className).outcome == GuardOutcome.ALLOW

    // ── Matching ──────────────────────────────────────────────────

    private fun matchRule(context: Context, packageName: String): Rule? {
        val now = Calendar.getInstance()
        return RuleStore.all(context).firstOrNull { rule ->
            rule.enabled && matchesTarget(context, rule, packageName) && matchesCondition(context, rule, packageName, now)
        }
    }

    private fun matchesTarget(context: Context, rule: Rule, packageName: String): Boolean =
        when (rule.targetType) {
            RuleTargetType.ALL -> true
            RuleTargetType.APP -> rule.targetValue == packageName
            RuleTargetType.CATEGORY ->
                AppCatalog.categoryOf(context, packageName) == AppCategory.fromId(rule.targetValue)
        }

    private fun matchesCondition(
        context: Context,
        rule: Rule,
        packageName: String,
        now: Calendar
    ): Boolean = when (rule.conditionType) {
        RuleConditionType.ALWAYS -> true
        RuleConditionType.SESSION_ONLY -> SessionManager.isActive(context)
        RuleConditionType.TIME -> {
            val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            withinWindow(minutes, rule.conditionStart, rule.conditionEnd)
        }
        RuleConditionType.DAYS ->
            rule.conditionDays.isEmpty() || now.get(Calendar.DAY_OF_WEEK) in rule.conditionDays
        RuleConditionType.PLACE ->
            PlaceRules.activePlaces(context).any { it.id == rule.conditionValue }
        RuleConditionType.WIFI -> {
            val ssid = PlaceRules.currentWifiSsid(context)
            ssid != null && ssid.equals(rule.conditionValue, ignoreCase = true)
        }
        RuleConditionType.USAGE_OVER ->
            AppLimits.usedMinutesToday(context, packageName) >= rule.conditionNumber
        RuleConditionType.OPENS_OVER ->
            AppLimits.opensToday(context, packageName) >= rule.conditionNumber
    }

    private fun withinWindow(nowMinutes: Int, start: Int, end: Int): Boolean {
        if (start == end) return false
        return if (end > start) nowMinutes in start until end else nowMinutes >= start || nowMinutes < end
    }
}

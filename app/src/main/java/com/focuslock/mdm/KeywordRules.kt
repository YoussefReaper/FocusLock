package com.focuslock.mdm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** What the content guard does when it recognises a screen. */
enum class GuardAction(val id: String, val label: String, val blurb: String) {
    STEP_BACK("stepBack", "Step back out", "Presses back, so you land on the previous screen and keep using the app."),
    CLOSE_APP("closeApp", "Leave the app", "Returns you to FocusLock. Use this when the whole app is the problem."),
    NUDGE("nudge", "Just tell me", "Shows a quiet line and leaves the screen alone.");

    companion object {
        fun fromId(id: String?): GuardAction = values().firstOrNull { it.id == id } ?: STEP_BACK
    }
}

/**
 * One phrase the guard watches for.
 *
 * [group] links a rule to the capability that owns it. Rules with a group are
 * the built-in guards (WhatsApp, Shorts, Reels, adult filter) and switch on and
 * off with their capability. Rules without one belong to the user and are
 * governed by the keyword capability.
 */
data class KeywordRule(
    val id: String,
    val phrase: String,
    val packages: Set<String>,
    val action: GuardAction,
    val enabled: Boolean,
    val group: String?
) {
    val appliesEverywhere: Boolean get() = packages.isEmpty()
}

object KeywordRules {

    const val GROUP_WHATSAPP = "whatsapp"
    const val GROUP_SHORTS = "shorts"
    const val GROUP_REELS = "reels"
    const val GROUP_ADULT = "adult"

    private const val KEY_RULES = "keyword_rules_json"
    private const val KEY_EXCEPTIONS = "keyword_exceptions_json"

    // ── Read ──────────────────────────────────────────────────────

    fun all(context: Context): List<KeywordRule> {
        val array = FocusStore.getJsonArray(context, KEY_RULES)
        val out = ArrayList<KeywordRule>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val phrase = obj.optString("phrase", "").trim()
            if (phrase.isEmpty()) continue
            val packagesArray = obj.optJSONArray("packages") ?: JSONArray()
            out.add(
                KeywordRule(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    phrase = phrase,
                    packages = FocusStore.jsonArrayToStringList(packagesArray).toSet(),
                    action = GuardAction.fromId(obj.optString("action", "")),
                    enabled = obj.optBoolean("enabled", true),
                    group = obj.optString("group", "").takeIf { it.isNotBlank() }
                )
            )
        }
        return out
    }

    /** The rules the user wrote themselves. */
    fun userRules(context: Context): List<KeywordRule> = all(context).filter { it.group == null }

    fun rulesInGroup(context: Context, group: String): List<KeywordRule> =
        all(context).filter { it.group == group }

    /**
     * The rules in force right now, with every capability gate already applied.
     * The guard service calls only this.
     */
    fun activeRules(context: Context): List<KeywordRule> {
        if (!CapabilityRegistry.isEnabled(context, Capabilities.CONTENT_GUARD)) return emptyList()

        val keywordsOn = CapabilityRegistry.isEnabled(context, Capabilities.KEYWORD_BLOCK)
        val whatsappOn = CapabilityRegistry.isEnabled(context, Capabilities.WHATSAPP_GUARD)
        val shortsOn = CapabilityRegistry.isEnabled(context, Capabilities.SHORTS_BLOCK)
        val reelsOn = CapabilityRegistry.isEnabled(context, Capabilities.REELS_BLOCK)
        val adultOn = CapabilityRegistry.isEnabled(context, Capabilities.ADULT_BLOCK)

        return all(context).filter { rule ->
            if (!rule.enabled) return@filter false
            when (rule.group) {
                null -> keywordsOn
                GROUP_WHATSAPP -> whatsappOn
                GROUP_SHORTS -> shortsOn
                GROUP_REELS -> reelsOn
                GROUP_ADULT -> adultOn
                else -> keywordsOn
            }
        }
    }

    /** Phrases that mean "this looks like a hit but is not" — kept out of the way. */
    fun exceptions(context: Context): List<String> =
        FocusStore.jsonArrayToStringList(FocusStore.getJsonArray(context, KEY_EXCEPTIONS))
            .map { it.lowercase(Locale.US) }

    fun setExceptions(context: Context, phrases: Collection<String>) {
        val cleaned = phrases.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        FocusStore.setJsonArray(context, KEY_EXCEPTIONS, FocusStore.stringListToJsonArray(cleaned))
        PolicySync.request(context, "keywordExceptions")
    }

    /** Every package any active rule cares about, or null when a rule is global. */
    fun watchedPackages(context: Context): Set<String>? {
        val rules = activeRules(context)
        if (rules.any { it.appliesEverywhere }) return null
        return rules.flatMap { it.packages }.toSet()
    }

    // ── Write ─────────────────────────────────────────────────────

    fun save(context: Context, rules: List<KeywordRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject()
            obj.put("id", rule.id)
            obj.put("phrase", rule.phrase)
            obj.put("packages", FocusStore.stringListToJsonArray(rule.packages))
            obj.put("action", rule.action.id)
            obj.put("enabled", rule.enabled)
            if (rule.group != null) obj.put("group", rule.group)
            array.put(obj)
        }
        FocusStore.setJsonArray(context, KEY_RULES, array)
        PolicySync.request(context, "keywordRules")
    }

    fun add(context: Context, rule: KeywordRule) {
        save(context, all(context) + rule)
    }

    fun update(context: Context, rule: KeywordRule) {
        save(context, all(context).map { if (it.id == rule.id) rule else it })
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
    }

    fun setGroupEnabled(context: Context, group: String, enabled: Boolean) {
        save(context, all(context).map { if (it.group == group) it.copy(enabled = enabled) else it })
    }

    fun newRule(
        phrase: String,
        packages: Collection<String> = emptySet(),
        action: GuardAction = GuardAction.STEP_BACK,
        group: String? = null
    ): KeywordRule = KeywordRule(
        id = UUID.randomUUID().toString(),
        phrase = phrase.trim(),
        packages = packages.map { it.trim() }.filter { it.isNotBlank() }.toSet(),
        action = action,
        enabled = true,
        group = group
    )

    // ── Seeding ───────────────────────────────────────────────────

    /**
     * Turns the old hardcoded guard lists into ordinary, visible, editable rules.
     * Runs once; after that the user's copy is the only copy.
     */
    fun seedIfEmpty(context: Context) {
        if (FocusStore.has(context, KEY_RULES)) return

        val seeded = ArrayList<KeywordRule>()

        Seed.whatsappBlockedPhrases.forEach { phrase ->
            seeded.add(
                newRule(
                    phrase = phrase,
                    packages = Seed.whatsappPackages,
                    action = GuardAction.STEP_BACK,
                    group = GROUP_WHATSAPP
                )
            )
        }

        Seed.shortsPhrases.forEach { phrase ->
            seeded.add(
                newRule(
                    phrase = phrase,
                    packages = Seed.shortsPackages,
                    action = GuardAction.STEP_BACK,
                    group = GROUP_SHORTS
                )
            )
        }

        Seed.reelsPhrases.forEach { phrase ->
            seeded.add(
                newRule(
                    phrase = phrase,
                    packages = Seed.reelsPackages,
                    action = GuardAction.STEP_BACK,
                    group = GROUP_REELS
                )
            )
        }

        Seed.adultKeywords.forEach { phrase ->
            seeded.add(
                newRule(
                    phrase = phrase,
                    packages = emptySet(),
                    action = GuardAction.CLOSE_APP,
                    group = GROUP_ADULT
                )
            )
        }

        save(context, seeded)
        setExceptions(context, Seed.whatsappAllowedPhrases)
    }

    // ── Export / import ───────────────────────────────────────────

    fun exportJson(context: Context): JSONObject {
        val out = JSONObject()
        out.put("rules", FocusStore.getJsonArray(context, KEY_RULES))
        out.put("exceptions", FocusStore.getJsonArray(context, KEY_EXCEPTIONS))
        return out
    }

    fun importJson(context: Context, json: JSONObject) {
        json.optJSONArray("rules")?.let { FocusStore.setJsonArray(context, KEY_RULES, it) }
        json.optJSONArray("exceptions")?.let { FocusStore.setJsonArray(context, KEY_EXCEPTIONS, it) }
        PolicySync.request(context, "keywordRules:import")
    }
}

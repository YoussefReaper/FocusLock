package com.focuslock.mdm

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.Locale

data class InstalledApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    val isSystem: Boolean,
    val isLaunchable: Boolean
)

/**
 * Knows what is on the phone and roughly what kind of thing each app is.
 *
 * Categorisation is a three-step guess: the user's own override wins, then
 * Android's declared category, then a name heuristic. The user can always
 * correct it, and their correction is what every category rule reads afterwards.
 */
object AppCatalog {

    private const val KEY_CATEGORY_OVERRIDES = "app_category_overrides_json"

    @Volatile
    private var cache: List<InstalledApp>? = null

    @Volatile
    private var cacheStamp: Long = 0L

    private const val CACHE_TTL_MS = 30_000L

    private val labelCache = HashMap<String, String>()

    fun invalidate() {
        cache = null
        labelCache.clear()
    }

    fun all(context: Context): List<InstalledApp> {
        val now = System.currentTimeMillis()
        val cached = cache
        if (cached != null && now - cacheStamp < CACHE_TTL_MS) return cached

        val pm = context.packageManager
        val overrides = FocusStore.getStringMap(context, KEY_CATEGORY_OVERRIDES)

        val apps = try {
            pm.getInstalledApplications(0)
        } catch (_: Exception) {
            emptyList<ApplicationInfo>()
        }.mapNotNull { info ->
            try {
                val pkg = info.packageName ?: return@mapNotNull null
                val label = try {
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    pkg
                }
                val launchable = pm.getLaunchIntentForPackage(pkg) != null
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                InstalledApp(
                    packageName = pkg,
                    label = label,
                    category = resolveCategory(pkg, info, overrides),
                    isSystem = isSystem,
                    isLaunchable = launchable
                )
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.label.lowercase(Locale.getDefault()) }

        cache = apps
        cacheStamp = now
        return apps
    }

    /** Apps a person can actually tap: what every picker should show. */
    fun launchable(context: Context): List<InstalledApp> =
        all(context).filter { it.isLaunchable && it.packageName != context.packageName }

    fun find(context: Context, packageName: String): InstalledApp? =
        all(context).firstOrNull { it.packageName == packageName }

    fun label(context: Context, packageName: String): String {
        labelCache[packageName]?.let { return it }
        val resolved = try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
        labelCache[packageName] = resolved
        return resolved
    }

    fun icon(context: Context, packageName: String): Drawable? = try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (_: Exception) {
        null
    }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }

    // ── Categories ────────────────────────────────────────────────

    fun categoryOf(context: Context, packageName: String): AppCategory {
        find(context, packageName)?.let { return it.category }
        val overrides = FocusStore.getStringMap(context, KEY_CATEGORY_OVERRIDES)
        overrides[packageName]?.let { return AppCategory.fromId(it) }
        return guessFromName(packageName) ?: AppCategory.OTHER
    }

    fun setCategoryOverride(context: Context, packageName: String, category: AppCategory) {
        val overrides = FocusStore.getStringMap(context, KEY_CATEGORY_OVERRIDES).toMutableMap()
        overrides[packageName] = category.id
        FocusStore.setStringMap(context, KEY_CATEGORY_OVERRIDES, overrides)
        invalidate()
        PolicySync.request(context, "category:" + packageName)
    }

    fun clearCategoryOverride(context: Context, packageName: String) {
        val overrides = FocusStore.getStringMap(context, KEY_CATEGORY_OVERRIDES).toMutableMap()
        overrides.remove(packageName)
        FocusStore.setStringMap(context, KEY_CATEGORY_OVERRIDES, overrides)
        invalidate()
        PolicySync.request(context, "category:" + packageName)
    }

    fun packagesInCategory(context: Context, category: AppCategory): List<String> =
        all(context).filter { it.category == category }.map { it.packageName }

    private fun resolveCategory(
        packageName: String,
        info: ApplicationInfo,
        overrides: Map<String, String>
    ): AppCategory {
        overrides[packageName]?.let { return AppCategory.fromId(it) }

        guessFromName(packageName)?.let { return it }

        val declared = try {
            info.category
        } catch (_: Exception) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        when (declared) {
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.VIDEO
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_NEWS -> return AppCategory.BROWSING
            ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.VIDEO
        }

        if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return AppCategory.SYSTEM
        return AppCategory.OTHER
    }

    private fun guessFromName(packageName: String): AppCategory? {
        val lower = packageName.lowercase(Locale.US)
        Seed.categoryHints.forEach { hint ->
            if (lower.contains(hint.first)) return hint.second
        }
        return null
    }

    /**
     * Apps the phone would be unusable without. Used to seed the always-allowed
     * list and to warn when someone is about to lock away their own dialler.
     */
    fun detectEssentials(context: Context): List<String> {
        val installed = all(context).map { it.packageName }.toSet()
        val found = Seed.essentials.filter { it in installed }.toMutableList()

        val pm = context.packageManager
        listOfNotNull(
            resolveDefault(pm, android.content.Intent(android.content.Intent.ACTION_DIAL)),
            resolveDefault(pm, android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0")))
        ).forEach { pkg -> if (pkg !in found) found.add(pkg) }

        return found.distinct()
    }

    private fun resolveDefault(pm: PackageManager, intent: android.content.Intent): String? = try {
        pm.resolveActivity(intent, 0)?.activityInfo?.packageName
    } catch (_: Exception) {
        null
    }
}

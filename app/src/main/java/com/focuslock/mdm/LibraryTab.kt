package com.focuslock.mdm

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * The Library tab: the replacement ecosystem.
 *
 * The thesis of the whole product lives here. Blocking alone leaves a hole that
 * gets filled by finding a way around the block, so the locked phone has to
 * still offer something: a curated internet, a search that cannot become a
 * scroll, and a reward that arrives on a timer instead of on demand.
 *
 * Each surface is a capability, so a person who wants a bare phone can empty
 * this tab completely.
 */
class LibraryTab(activity: MainActivity, tokens: UiPrefs.Tokens) : FocusTab(activity, tokens) {

    private lateinit var container: LinearLayout

    private var cachedApps: List<InstalledApp>? = null
    private var cachedAppsRevision = -1L

    override fun build(): View {
        container = FocusUi.column(activity, tokens.density.contentPaddingDp)
        return FocusUi.scroll(activity, container)
    }

    override fun onShow() {
        render()
    }

    private fun render() {
        container.removeAllViews()
        val added = ArrayList<View>()

        fun add(view: View) {
            container.addView(view)
            added.add(view)
        }

        add(
            FocusUi.pageHeader(
                activity,
                tokens,
                "Library",
                "Somewhere to put the urge, instead of nowhere."
            )
        )

        var anything = false

        if (CapabilityRegistry.isEnabled(activity, Capabilities.SAFE_BROWSER) &&
            UiPrefs.showWebButton(activity)
        ) {
            anything = true
            add(buildBrowserCard())
        }

        if (CapabilityRegistry.isEnabled(activity, Capabilities.TEXT_SEARCH)) {
            anything = true
            add(buildTextSearchCard())
        }

        if (CapabilityRegistry.isEnabled(activity, Capabilities.VIDEO_LIBRARY) &&
            UiPrefs.showVideoButton(activity)
        ) {
            anything = true
            add(buildVideoCard())
        }

        if (UiPrefs.showAllowedApps(activity)) {
            val open = openableApps()
            if (open.isNotEmpty()) {
                add(FocusUi.sectionLabel(activity, tokens, "Apps you can open now"))
                add(buildAppGrid(open))
            }
        }

        if (!anything) {
            add(
                FocusUi.emptyState(
                    activity,
                    tokens,
                    "You have turned every library surface off. That is a valid choice; " +
                        "turn one back on in Rules if the empty phone stops working for you."
                )
            )
        }

        Motion.stagger(added, tokens)
    }

    // ── Cards ─────────────────────────────────────────────────────

    private fun buildBrowserCard(): View {
        val card = FocusUi.card(activity, tokens) {
            activity.startActivity(Intent(activity, WebViewActivity::class.java))
        }

        card.addView(FocusUi.heading(activity, tokens, "Safe browser"))
        card.addView(FocusUi.spacer(activity, 6))
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                "Only the sites on your list load. No address bar to wander out of."
            )
        )

        val categories = AllowlistStore.getWebCategories(activity).take(6)
        if (categories.isNotEmpty()) {
            card.addView(FocusUi.spacer(activity, 12))
            val strip = FocusUi.row(activity)
            strip.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            categories.forEach { category ->
                strip.addView(
                    FocusUi.pill(activity, tokens, category, tokens.textSecondary).apply {
                        (layoutParams as LinearLayout.LayoutParams).marginEnd = FocusUi.dp(activity, 6)
                    }
                )
            }
            card.addView(FocusUi.horizontalScroll(activity, strip))
        }

        card.addView(FocusUi.spacer(activity, 14))
        card.addView(
            FocusUi.primaryButton(activity, tokens, "Open the browser") {
                activity.startActivity(Intent(activity, WebViewActivity::class.java))
            }
        )
        card.addView(FocusUi.spacer(activity, 8))
        card.addView(
            FocusUi.ghostButton(
                activity,
                tokens,
                AllowlistStore.getWebAllowlistUrls(activity).size.toString() + " sites allowed - edit"
            ) {
                activity.startActivity(Intent(activity, WebAllowlistEditorActivity::class.java))
            }
        )
        return card
    }

    private fun buildTextSearchCard(): View {
        val card = FocusUi.card(activity, tokens) {
            activity.startActivity(Intent(activity, TextSearchActivity::class.java))
        }
        card.addView(FocusUi.heading(activity, tokens, "Text search"))
        card.addView(FocusUi.spacer(activity, 6))
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                "Google with images, video and thumbnails stripped out, and SafeSearch forced on. " +
                    "It answers questions without becoming a feed."
            )
        )
        card.addView(FocusUi.spacer(activity, 14))
        card.addView(
            FocusUi.secondaryButton(activity, tokens, "Search something") {
                activity.startActivity(Intent(activity, TextSearchActivity::class.java))
            }
        )
        return card
    }

    /**
     * The reward, and the only place in the app where waiting is the feature:
     * one unlock a day, permanent once opened.
     */
    private fun buildVideoCard(): View {
        val card = FocusUi.card(activity, tokens) {
            activity.startActivity(Intent(activity, VideoLibraryActivity::class.java))
        }

        val header = FocusUi.row(activity)
        val title = FocusUi.heading(activity, tokens, "Video library")
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(title)

        val ready = VideoManager.canUnlockToday(activity)
        header.addView(
            FocusUi.pill(
                activity,
                tokens,
                if (ready) "One ready" else "Locked",
                if (ready) tokens.success else tokens.textMuted
            )
        )
        card.addView(header)

        card.addView(FocusUi.spacer(activity, 6))
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                if (!VideoManager.isFolderSelected(activity)) {
                    "Pick a folder of your own videos. One new one unlocks every 24 hours and stays unlocked."
                } else if (ready) {
                    "Today's unlock is waiting. Everything you have already opened stays open."
                } else {
                    "Next unlock in " + VideoManager.nextUnlockFormatted(activity) + ". " +
                        VideoManager.unlockedCount(activity) + " already yours."
                }
            )
        )

        card.addView(FocusUi.spacer(activity, 14))
        card.addView(
            FocusUi.secondaryButton(activity, tokens, "Open the library") {
                activity.startActivity(Intent(activity, VideoLibraryActivity::class.java))
            }
        )
        return card
    }

    // ── App grid ──────────────────────────────────────────────────

    /**
     * The grid's contents, memoised against the policy revision.
     *
     * Filtering every installed app and asking for each one's effective policy
     * is not free, and this runs on every tab open. Nothing here can change
     * without PolicySync ticking, so the revision is a sound cache key.
     */
    private fun openableApps(): List<InstalledApp> {
        val revision = PolicySync.revision()
        val cached = cachedApps
        if (cached != null && cachedAppsRevision == revision) return cached

        val blocked = AppRules.blockedPackages(activity)
        val lockTask = SessionManager.shouldLockTask(activity)
        val allowlist = if (lockTask) AppRules.kioskAllowlist(activity) else emptySet()

        val computed = AppCatalog.launchable(activity)
            .filter { app ->
                if (app.packageName in blocked) return@filter false
                if (lockTask) {
                    app.packageName in allowlist
                } else {
                    AppRules.effectivePolicy(activity, app.packageName) != AppPolicy.HIDE
                }
            }
            .take(60)

        cachedApps = computed
        cachedAppsRevision = revision
        return computed
    }

    /**
     * A wrapping grid built by hand: four columns of icon plus label, so the
     * launcher-inside-the-app looks like a launcher rather than a settings list.
     */
    private fun buildAppGrid(apps: List<InstalledApp>): View {
        val card = FocusUi.card(activity, tokens)
        val columns = 4
        var currentRow: LinearLayout? = null

        apps.forEachIndexed { index, app ->
            if (index % columns == 0) {
                currentRow = FocusUi.row(activity)
                currentRow?.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = FocusUi.dp(activity, 10) }
                card.addView(currentRow)
            }
            currentRow?.addView(buildAppCell(app))
        }

        val remainder = apps.size % columns
        if (remainder != 0) {
            repeat(columns - remainder) {
                val filler = View(activity)
                filler.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                currentRow?.addView(filler)
            }
        }
        return card
    }

    private fun buildAppCell(app: InstalledApp): View {
        val cell = FocusUi.column(activity)
        cell.gravity = android.view.Gravity.CENTER_HORIZONTAL
        cell.setPadding(0, FocusUi.dp(activity, 8), 0, FocusUi.dp(activity, 8))
        cell.isClickable = true
        cell.isFocusable = true
        cell.background = FocusUi.withRipple(
            activity,
            FocusUi.roundedShape(activity, UiPrefs.withAlpha(tokens.surface, 0), 14),
            tokens
        )
        cell.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        cell.setOnClickListener { launch(app.packageName) }

        val icon = FocusUi.appIcon(activity, tokens, app.packageName, 44)
        (icon.layoutParams as? LinearLayout.LayoutParams)?.gravity = android.view.Gravity.CENTER_HORIZONTAL
        cell.addView(icon)

        val label = FocusUi.caption(activity, tokens, app.label)
        label.gravity = android.view.Gravity.CENTER
        label.maxLines = 2
        label.setTextColor(tokens.textSecondary)
        label.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(activity, 6) }
        cell.addView(label)
        return cell
    }

    private fun launch(packageName: String) {
        val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            FocusDialog.toast(activity, "That app has no screen to open.")
            return
        }
        try {
            activity.startActivity(intent)
        } catch (_: SecurityException) {
            FocusDialog.toast(activity, "The kiosk session is holding that one shut.")
        }
    }
}

package com.focuslock.mdm

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import java.util.Locale

/**
 * The site allowlist.
 *
 * The old version was one textarea holding two hundred URLs, which is fine for
 * writing and useless for finding. This is a searchable, categorised list with
 * one-tap removal, and the textarea is still there for anyone who wants to
 * paste a whole set in at once.
 *
 * During a session the list can only shrink. Being able to add sites mid-lock
 * would make the lock decorative.
 */
class WebAllowlistEditorActivity : FocusScreenActivity() {

    private var query = ""
    private var category = 0

    override fun screenTitle(): String = "Websites"

    override fun screenSubtitle(): String =
        "The only addresses the safe browser will load, and the only ones Chrome is allowed."

    override fun buildContent(column: LinearLayout) {
        column.addView(buildStateCard())
        column.addView(buildSearch())
        column.addView(buildCategoryStrip())
        column.addView(buildList())
        column.addView(sectionLabel("Bulk edit"))
        column.addView(buildBulkCard())
    }

    private fun locked(): Boolean = SessionManager.isActive(this)

    private fun buildStateCard(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Web blocking",
                "Holds every managed browser to this list.",
                CapabilityRegistry.isEnabled(this, Capabilities.WEB_BLOCK)
            ) { value ->
                CapabilityRegistry.setEnabled(this, Capabilities.WEB_BLOCK, value)
                if (!value) {
                    Capabilities.spec(Capabilities.WEB_BLOCK)?.let { FocusDialog.weakenNotice(this, it) }
                }
                refresh()
            }
        )

        if (locked()) {
            card.addView(FocusUi.spacer(this, 8))
            card.addView(
                FocusUi.caption(
                    this,
                    tokens,
                    "A session is running, so sites can be removed but not added. " +
                        "That is what stops the list becoming a back door."
                )
            )
        }

        card.addView(FocusUi.spacer(this, 10))
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Strip images and video",
                "Text-only browsing. Pages load faster and stop being a feed.",
                AllowlistStore.isWebTextOnlyEnabled(this)
            ) { value -> AllowlistStore.setWebTextOnlyEnabled(this, value) }
        )
    }

    private fun buildSearch(): View {
        val field = FocusUi.input(this, tokens, "Search sites", query)
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                renderList()
            }
        })
        return field
    }

    private fun categories(): List<String> = listOf("All") + AllowlistStore.getWebCategories(this)

    private fun buildCategoryStrip(): View =
        FocusUi.chipStrip(this, tokens, categories(), category) { index ->
            category = index
            renderList()
        }

    private lateinit var listHost: LinearLayout

    private fun buildList(): View {
        listHost = FocusUi.column(this)
        renderList()
        return listHost
    }

    private fun renderList() {
        if (!this::listHost.isInitialized) return
        listHost.removeAllViews()

        val cats = categories()
        val selectedCategory = cats.getOrElse(category) { "All" }
        val needle = query.trim().lowercase(Locale.getDefault())

        val links = AllowlistStore.getWebLinks(this)
            .filter { selectedCategory == "All" || it.category == selectedCategory }
            .filter {
                needle.isEmpty() ||
                    it.title.lowercase(Locale.getDefault()).contains(needle) ||
                    it.url.lowercase(Locale.getDefault()).contains(needle)
            }

        val card = FocusUi.card(this, tokens)

        if (links.isEmpty()) {
            card.addView(FocusUi.emptyState(this, tokens, "No sites match that."))
        } else {
            links.forEachIndexed { index, link ->
                card.addView(
                    FocusUi.listRow(
                        this,
                        tokens,
                        link.title,
                        link.url.removePrefix("https://").removePrefix("http://"),
                        trailing = FocusUi.smallButton(this, tokens, "Remove") { remove(link.url) }
                    )
                )
                if (index < links.size - 1) card.addView(FocusUi.divider(this, tokens))
            }
        }

        card.addView(FocusUi.spacer(this, 12))
        card.addView(
            FocusUi.primaryButton(this, tokens, "Add a site") { addSite() }
        )
        listHost.addView(card)
    }

    private fun remove(url: String) {
        AllowlistStore.removeWebUrl(this, url)
        renderList()
    }

    private fun addSite() {
        if (locked()) {
            FocusDialog.info(
                this,
                "Not while a session runs",
                "Sites can be removed at any time, but new ones wait until the session ends."
            )
            return
        }
        FocusDialog.textInput(
            this,
            title = "Add a site",
            subtitle = "Just the address. Everything on that domain will load.",
            hint = "example.com",
            confirmLabel = "Add"
        ) { value ->
            val normalized = AllowlistStore.normalizeUrl(value)
            if (!AllowlistStore.isValidUrl(normalized)) {
                FocusDialog.toast(this, "That does not look like an address.")
                return@textInput
            }
            AllowlistStore.addWebUrl(this, normalized)
            refresh()
        }
    }

    /** Kept for the paste-a-list case, which the row editor genuinely cannot beat. */
    private fun buildBulkCard(): View = card { card ->
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "One address per line. Saving replaces the whole list."
            )
        )
        card.addView(FocusUi.spacer(this, 10))

        val field = FocusUi.input(
            this,
            tokens,
            "https://example.com",
            AllowlistStore.getWebAllowlistUrls(this).sorted().joinToString("\n"),
            multiline = true
        )
        field.minLines = 8
        card.addView(field)

        card.addView(
            FocusUi.secondaryButton(this, tokens, "Save the whole list") {
                val original = AllowlistStore.getWebAllowlistUrls(this)
                val urls = field.text.toString()
                    .split("\n")
                    .map { AllowlistStore.normalizeUrl(it) }
                    .filter { it.isNotBlank() && AllowlistStore.isValidUrl(it) }
                    .toSet()

                if (locked() && urls.any { it !in original }) {
                    FocusDialog.info(
                        this,
                        "Not while a session runs",
                        "Removing sites is fine. Adding waits until the session ends."
                    )
                    return@secondaryButton
                }
                if (urls.isEmpty()) {
                    FocusDialog.toast(this, "That would leave the browser with nowhere to go.")
                    return@secondaryButton
                }

                AllowlistStore.setWebAllowlistUrls(this, urls)
                FocusDialog.toast(this, urls.size.toString() + " sites saved.")
                refresh()
            }
        )
    }
}

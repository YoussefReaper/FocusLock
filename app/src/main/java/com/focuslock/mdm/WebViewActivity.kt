package com.focuslock.mdm

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayInputStream

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView        : WebView
    private lateinit var webRoot        : LinearLayout
    private lateinit var pickerLayout   : LinearLayout
    private lateinit var categoryTabs   : LinearLayout
    private lateinit var rvLinks        : RecyclerView
    private lateinit var bottomBar      : LinearLayout
    private lateinit var btnBack        : Button
    private lateinit var switchTextOnly : Switch
    private lateinit var tvTextOnlyTitle: TextView
    private lateinit var tvTextOnlySubtitle: TextView
    private lateinit var webDivider: View

    private var currentUrl     : String? = null

    private var selectedCategory = "All"
    private var textOnlyMode = false

    private val imageExtensions = setOf(
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg", ".ico", ".avif"
    )
    private val videoExtensions = setOf(
        ".mp4", ".webm", ".mkv", ".mov", ".m4v", ".3gp", ".m3u8", ".ts", ".avi"
    )

    // ── Lifecycle ─────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If the website can go back a page (e.g., leaving Google Login), go back
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // Otherwise, close the browser and return to the main FocusLock dashboard
                    finish()
                }
            }
        })
        webRoot = findViewById(R.id.webRoot)
        pickerLayout  = findViewById(R.id.pickerLayout)
        categoryTabs  = findViewById(R.id.categoryTabs)
        rvLinks       = findViewById(R.id.rvLinks)
        bottomBar     = findViewById(R.id.bottomBar)
        btnBack       = findViewById(R.id.btnBack)
        webView       = findViewById(R.id.webView)
        switchTextOnly = findViewById(R.id.switchTextOnly)
        tvTextOnlyTitle = findViewById(R.id.tvTextOnlyTitle)
        tvTextOnlySubtitle = findViewById(R.id.tvTextOnlySubtitle)
        webDivider = findViewById(R.id.webDivider)

        textOnlyMode = AllowlistStore.isWebTextOnlyEnabled(this)
        switchTextOnly.isChecked = textOnlyMode
        switchTextOnly.setOnCheckedChangeListener { _, isChecked ->
            textOnlyMode = isChecked
            AllowlistStore.setWebTextOnlyEnabled(this, isChecked)
            applyWebViewSafetyMode()
            if (webView.visibility == View.VISIBLE) {
                webView.reload()
            }
        }

        setupWebView()
        buildCategoryTabs()
        buildLinkList()
        applyPersonalization()

        bottomBar.visibility  = View.GONE
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
        syncTextOnlyMode()
        applyPersonalization()
        buildCategoryTabs()
        buildLinkList()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    // ── WebView ───────────────────────────────────────────────────

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled   = true
            domStorageEnabled   = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
        }
        applyWebViewSafetyMode()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                if (request?.isForMainFrame == false) return false

                // 1. DEEP LINK BYPASS: If the URL is an app intent (like handing auth back to ChatGPT), let it through.
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        if (isDeepLinkIntentAllowed(intent)) {
                            view?.context?.startActivity(intent)
                        } else {
                            Toast.makeText(this@WebViewActivity, "This link target is blocked", Toast.LENGTH_SHORT).show()
                        }
                        true
                    } catch (_: Exception) {
                        true
                    }
                }

                if (textOnlyMode) {
                    if (isMediaNavigationBlocked(url)) {
                        Toast.makeText(this@WebViewActivity, "Images and videos are disabled", Toast.LENGTH_SHORT).show()
                        return true
                    }
                    val safeUrl = enforceGoogleSafeSearch(url)
                    if (safeUrl != null && safeUrl != url) {
                        view?.loadUrl(safeUrl)
                        return true
                    }
                }

                // 2. STANDARD WEB CHECK: Use our VIP domain logic
                if (isUrlAllowed(url)) {
                    return false // Let the WebView load the allowed page normally
                } else {
                    // THE TOAST: Only fire this if they actually try to escape to Reddit or Wikipedia
                    Toast.makeText(this@WebViewActivity, "External links aren't allowed", Toast.LENGTH_SHORT).show()
                    return true // Block the load
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (!textOnlyMode) return null
                val url = request?.url?.toString() ?: return null
                return if (isMediaResourceUrl(url)) emptyResponse() else null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view != null && !url.isNullOrBlank()) {
                    hideGoogleMediaTabs(view, url)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                wv: WebView?, cb: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean = false
        }

        webView.visibility = View.GONE
    }

    /**
     * A URL is allowed if it starts with any of the configured link URLs,
     * or if it is a same-domain navigation (contains the host of a whitelisted URL).
     */
    private fun isUrlAllowed(target: String): Boolean {
        try {
            val targetUri = Uri.parse(target)
            val scheme = targetUri.scheme?.lowercase() ?: return false
            if (scheme != "https" && scheme != "http") return false

            val targetHost = targetUri.host?.lowercase()?.removePrefix("www.") ?: return false

            // 1. VIP PASS: Allow Google Login & Authentication domains
            val authDomains = listOf(
                "accounts.google.com",
                "accounts.youtube.com",
                "gstatic.com",
                "openai.com",       // ChatGPT auth router
                "auth0.openai.com", // ChatGPT backend auth
                "chatgpt.com"
            )

            // If the URL matches an auth domain, let it through immediately
            if (authDomains.any { domain -> hostMatches(targetHost, domain) }) {
                return true
            }

            // 2. STANDARD CHECK: Verify against your custom allowlist
            return AllowlistStore.getWebAllowlistUrls(this).any { url ->
                val allowedUri = Uri.parse(url)
                val allowedHost = allowedUri.host?.lowercase()?.removePrefix("www.") ?: ""

                allowedHost.isNotBlank() && hostMatches(targetHost, allowedHost)
            }
        } catch (e: Exception) {
            return false
        }
    }

    private fun hostMatches(targetHost: String, allowedHost: String): Boolean {
        return targetHost == allowedHost || targetHost.endsWith(".$allowedHost")
    }

    private fun isDeepLinkIntentAllowed(intent: Intent): Boolean {
        val explicitPackage = intent.`package`
        val componentPackage = intent.component?.packageName
        val resolvedPackage = packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName

        val targetPackage = explicitPackage ?: componentPackage ?: resolvedPackage
        if (targetPackage.isNullOrBlank()) return false
        if (targetPackage == packageName) return true
        if (targetPackage in Constants.SETTINGS_ESCAPE_PACKAGES) return false

        return targetPackage in AllowlistStore.getAppAllowlist(this) ||
            targetPackage in Constants.SYSTEM_USAGE_SURFACES
    }

    // ── Category tabs ─────────────────────────────────────────────

    private fun buildCategoryTabs() {
        val theme = UiPrefs.getTheme(this)
        categoryTabs.removeAllViews()
        val categories = listOf("All") +
            AllowlistStore.getWebLinks(this)
                .map { it.category }
                .distinct()
                .filter { it.isNotBlank() }

        if (selectedCategory !in categories) {
            selectedCategory = "All"
        }

        categories.forEach { cat ->
            val btn = Button(this).apply {
                text      = cat
                textSize  = 11f
                isAllCaps = false
                setTextColor(theme.textPrimary)
                backgroundTintList = ColorStateList.valueOf(theme.card)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 8, 0) }
                layoutParams = lp
                setOnClickListener {
                    selectedCategory = cat
                    buildCategoryTabs()   // re-render to show active state
                    buildLinkList()
                }
                alpha = if (cat == selectedCategory) 1f else 0.4f
            }
            categoryTabs.addView(btn)
        }
    }

    private fun applyPersonalization() {
        val theme = UiPrefs.getTheme(this)
        val font = UiPrefs.getFont(this)
        val wallpaper = UiPrefs.getWallpaper(this)

        UiStyler.applyWallpaperOrColor(webRoot, theme, wallpaper)
        UiStyler.applyTypefaceRecursive(webRoot, font.typeface)

        window.statusBarColor = theme.background
        window.navigationBarColor = theme.background

        tvTextOnlyTitle.setTextColor(theme.textPrimary)
        tvTextOnlySubtitle.setTextColor(theme.textSecondary)
        webDivider.setBackgroundColor(theme.divider)

        bottomBar.setBackgroundColor(theme.card)
        btnBack.backgroundTintList = ColorStateList.valueOf(theme.card)
        btnBack.setTextColor(theme.textPrimary)

        webView.setBackgroundColor(theme.background)
    }

    // ── Link list ─────────────────────────────────────────────────

    private fun buildLinkList() {
        val links = AllowlistStore.getWebLinks(this)
        val filtered = if (selectedCategory == "All") links
        else links.filter { it.category == selectedCategory }

        rvLinks.layoutManager = LinearLayoutManager(this)
        rvLinks.adapter = LinkAdapter(filtered, this) { link ->
            openLink(link)
        }
    }

    // ── Text-only Safe Search ──────────────────────────────────

    private fun syncTextOnlyMode() {
        val enabled = AllowlistStore.isWebTextOnlyEnabled(this)
        if (enabled == textOnlyMode) return

        textOnlyMode = enabled
        switchTextOnly.isChecked = enabled
        applyWebViewSafetyMode()
    }

    private fun applyWebViewSafetyMode() {
        webView.settings.loadsImagesAutomatically = !textOnlyMode
        webView.settings.blockNetworkImage = textOnlyMode
    }

    private fun enforceGoogleSafeSearch(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
            if (host != "google.com") return null
            if (uri.path != "/search") return null

            val currentSafe = uri.getQueryParameter("safe")?.lowercase()
            if (currentSafe == "active") return null

            val builder = uri.buildUpon().clearQuery()
            uri.queryParameterNames.forEach { name ->
                if (name == "safe") return@forEach
                val value = uri.getQueryParameter(name)
                if (value != null) builder.appendQueryParameter(name, value)
            }
            builder.appendQueryParameter("safe", "active")
            builder.build().toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun isMediaNavigationBlocked(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            val path = uri.path?.lowercase() ?: ""
            val tbm = uri.getQueryParameter("tbm")?.lowercase()

            if (isGoogleHost(host)) {
                if (tbm == "isch" || tbm == "vid") return true
                if (path.contains("/imghp") || path.contains("/imgres")) return true
            }

            host.endsWith("youtube.com") ||
                host == "youtu.be" ||
                host.endsWith("googlevideo.com")
        } catch (_: Exception) {
            false
        }
    }

    private fun isMediaResourceUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("data:image") || lower.startsWith("data:video")) return true

        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: ""
            val path = uri.path?.lowercase() ?: ""
            val query = uri.query?.lowercase() ?: ""

            if (hasMediaExtension(path)) return true
            if (host.endsWith("gstatic.com") && (path.contains("/images") || query.contains("tbn"))) {
                return true
            }
            if (host.endsWith("googleusercontent.com") && (query.contains("tbn") || path.contains("/imgres"))) {
                return true
            }
            if (host.endsWith("ytimg.com") || host.endsWith("googlevideo.com")) return true

            false
        } catch (_: Exception) {
            false
        }
    }

    private fun hasMediaExtension(path: String): Boolean {
        return imageExtensions.any { path.endsWith(it) } || videoExtensions.any { path.endsWith(it) }
    }

    private fun isGoogleHost(host: String): Boolean {
        val normalized = host.removePrefix("www.")
        return normalized == "google.com" || normalized.endsWith(".google.com")
    }

    private fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    private fun hideGoogleMediaTabs(view: WebView, url: String) {
        if (!textOnlyMode) return
        val host = Uri.parse(url).host?.lowercase() ?: return
        if (!isGoogleHost(host)) return

        val script = """
            (function(){
                var selectors = [
                    'a[href*="tbm=isch"]',
                    'a[href*="tbm=vid"]',
                    'a[aria-label="Images"]',
                    'a[aria-label="Videos"]'
                ];
                selectors.forEach(function(sel){
                    var nodes = document.querySelectorAll(sel);
                    nodes.forEach(function(n){ n.style.display = 'none'; });
                });
            })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    // ── Open / close ──────────────────────────────────────────────

    private fun openLink(link: Constants.WebLink) {
        currentUrl     = link.url

        pickerLayout.visibility = View.GONE
        webView.visibility      = View.VISIBLE
        bottomBar.visibility    = View.VISIBLE
        webView.loadUrl(link.url)
    }

    private fun closeWebView() {
        currentUrl = null

        webView.loadUrl("about:blank")
        webView.visibility      = View.GONE
        bottomBar.visibility    = View.GONE
        pickerLayout.visibility = View.VISIBLE

        buildLinkList()
    }
}
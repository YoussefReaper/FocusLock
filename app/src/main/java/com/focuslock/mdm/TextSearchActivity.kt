package com.focuslock.mdm

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream
import java.net.URLEncoder

class TextSearchActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var header: View
    private lateinit var divider: View
    private lateinit var webView: WebView
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView

    private val imageExtensions = setOf(
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg", ".ico", ".avif"
    )
    private val videoExtensions = setOf(
        ".mp4", ".webm", ".mkv", ".mov", ".m4v", ".3gp", ".m3u8", ".ts", ".avi"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_search)

        root = findViewById(R.id.textSearchRoot)
        header = findViewById(R.id.textSearchHeader)
        divider = findViewById(R.id.textSearchDivider)
        webView = findViewById(R.id.textSearchWebView)
        etSearch = findViewById(R.id.etSearchQuery)
        btnSearch = findViewById(R.id.btnSearch)
        tvTitle = findViewById(R.id.tvTextSearchTitle)
        tvSubtitle = findViewById(R.id.tvTextSearchSubtitle)

        setupWebView()
        applyPersonalization()

        btnSearch.setOnClickListener { runSearch() }
        etSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                runSearch()
                true
            } else {
                false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
        applyPersonalization()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                if (request?.isForMainFrame == false) return false

                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(this@TextSearchActivity, "This link type is blocked", Toast.LENGTH_SHORT).show()
                    return true
                }

                if (isMediaNavigationBlocked(url)) {
                    Toast.makeText(this@TextSearchActivity, "Images and videos are disabled", Toast.LENGTH_SHORT).show()
                    return true
                }

                val safeUrl = enforceGoogleSafeSearch(url)
                if (safeUrl != null && safeUrl != url) {
                    view?.loadUrl(safeUrl)
                    return true
                }

                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                return if (isMediaResourceUrl(url)) emptyResponse() else null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view != null && !url.isNullOrBlank()) {
                    hideGoogleMediaTabs(view, url)
                    hideMediaElements(view)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                wv: WebView?, cb: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean = false
        }
    }

    private fun runSearch() {
        val query = etSearch.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            Toast.makeText(this, "Enter a search query", Toast.LENGTH_SHORT).show()
            return
        }
        webView.loadUrl(buildSearchUrl(query))
    }

    private fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://www.google.com/search?q=$encoded&safe=active"
    }

    private fun enforceGoogleSafeSearch(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
            if (!isGoogleHost(host)) return null
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

    private fun hideMediaElements(view: WebView) {
        val script = """
            (function(){
                var imgs = document.querySelectorAll('img');
                imgs.forEach(function(n){ n.style.display = 'none'; });
                var vids = document.querySelectorAll('video');
                vids.forEach(function(n){ n.style.display = 'none'; });
                var iframes = document.querySelectorAll('iframe');
                iframes.forEach(function(n){ n.style.display = 'none'; });
            })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    private fun applyPersonalization() {
        val theme = UiPrefs.getTheme(this)
        val font = UiPrefs.getFont(this)
        val density = UiPrefs.getDensity(this)
        val wallpaper = UiPrefs.getWallpaper(this)

        UiStyler.applyWallpaperOrColor(root, theme, wallpaper)
        UiStyler.applyTypefaceRecursive(root, font.typeface)

        window.statusBarColor = theme.background
        window.navigationBarColor = theme.background

        header.setBackgroundColor(theme.card)
        divider.setBackgroundColor(theme.divider)

        tvTitle.setTextColor(theme.textPrimary)
        tvSubtitle.setTextColor(theme.textSecondary)

        etSearch.setBackgroundColor(theme.input)
        etSearch.setTextColor(theme.textPrimary)
        etSearch.setHintTextColor(theme.textSecondary)

        btnSearch.backgroundTintList = ColorStateList.valueOf(theme.accent)
        btnSearch.setTextColor(theme.textPrimary)
        setHeightDp(btnSearch, density.buttonHeightDp)
        setHeightDp(etSearch, density.buttonHeightDp)

        webView.setBackgroundColor(theme.background)
    }

    private fun setHeightDp(view: View, heightDp: Int) {
        val params = view.layoutParams
        params.height = UiStyler.dpToPx(this, heightDp)
        view.layoutParams = params
    }
}

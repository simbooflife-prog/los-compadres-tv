package com.loscompadres.tv

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Los Compadres TV — Android TV / Fire Stick shell.
 *
 * Page 1 (default): customer slideshow WebView (no persistent staff chrome).
 * Page 2 (staff): /local cameras kiosk after long-press Menu + correct PIN
 * (no Lovelace / no HA login), with slim staff bar
 * (Refresh, View Standard|All 5, Back to slideshow).
 *
 * URLs default to Nabu Casa (restaurant Wi‑Fi cannot reach home Pi LAN).
 * If a LAN URL is loaded and fails, retry the matching Nabu URL once.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var staffBar: LinearLayout
    private lateinit var btnRefresh: Button
    private lateinit var btnViewToggle: Button
    private lateinit var btnBackSlideshow: Button

    private var showingCameras = false
    /** false = Standard ≤4 cams; true = All 5 */
    private var camerasAllFive = false
    private var pinDialog: AlertDialog? = null

    /** Long-press Menu detection (KEYCODE_MENU). */
    private var menuDownAt: Long = 0L
    private var menuLongHandled = false

    /** One-shot LAN→Nabu retry after onReceivedError. */
    private var nabuFallbackUsed = false

    companion object {
        private const val MENU_LONG_PRESS_MS = 700L
        private const val PREFS_NAME = "staff_prefs"
        private const val PREF_CAMERAS_ALL_FIVE = "cameras_all_five"
        private const val LAN_HOST_MARKER = "192.168.1.27"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        staffBar = findViewById(R.id.staff_bar)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnViewToggle = findViewById(R.id.btn_view_toggle)
        btnBackSlideshow = findViewById(R.id.btn_back_slideshow)

        camerasAllFive = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_CAMERAS_ALL_FIVE, false)

        btnRefresh.setOnClickListener { refreshCurrentPage() }
        btnViewToggle.setOnClickListener { toggleCamerasView() }
        btnBackSlideshow.setOnClickListener { loadHome() }

        updateViewToggleLabel()
        hideStaffBar()

        configureWebView(webView)
        loadHome()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(wv: WebView) {
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(wv, true)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            userAgentString = userAgentString + " LosCompadresTV/1.2.1"
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request == null || !request.isForMainFrame) return
                handleMainFrameLoadError(view, request.url?.toString())
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                // API < 23 path; also called on some devices alongside the new API
                handleMainFrameLoadError(view, failingUrl)
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            private var customView: View? = null
            private var customViewCallback: CustomViewCallback? = null

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                val root = findViewById<FrameLayout>(R.id.root)
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                webView.visibility = View.GONE
                hideStaffBar()
            }

            override fun onHideCustomView() {
                val root = findViewById<FrameLayout>(R.id.root)
                customView?.let { root.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
                if (showingCameras) showStaffBar()
            }

            override fun getDefaultVideoPoster(): Bitmap? {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
    }

    private fun isLanUrl(url: String): Boolean =
        url.contains(LAN_HOST_MARKER)

    /**
     * If a LAN URL fails to load (e.g. Stick not on home Wi‑Fi), retry the
     * matching Nabu primary URL once.
     */
    private fun handleMainFrameLoadError(view: WebView?, failingUrl: String?) {
        if (failingUrl.isNullOrBlank()) return
        if (nabuFallbackUsed) return
        if (!isLanUrl(failingUrl)) return
        nabuFallbackUsed = true
        val nabu = nabuUrlForCurrentPage()
        view?.loadUrl(nabu)
    }

    private fun nabuUrlForCurrentPage(): String {
        return when {
            showingCameras && camerasAllFive -> getString(R.string.url_cameras_all)
            showingCameras -> getString(R.string.url_cameras)
            else -> getString(R.string.url_home)
        }
    }

    /** prefer_*_lan false → Nabu (url_*); true → LAN (url_*_lan). */
    private fun resolveHomeUrl(): String {
        val preferLan = resources.getBoolean(R.bool.prefer_home_lan)
        return if (preferLan) {
            getString(R.string.url_home_lan)
        } else {
            getString(R.string.url_home)
        }
    }

    private fun loadHome() {
        showingCameras = false
        nabuFallbackUsed = false
        hideStaffBar()
        webView.loadUrl(resolveHomeUrl())
    }

    private fun loadCameras() {
        showingCameras = true
        nabuFallbackUsed = false
        showStaffBar()
        updateViewToggleLabel()
        webView.loadUrl(resolveCamerasUrl())
        // Give D-pad focus to Refresh for Fire Stick remotes
        btnRefresh.post { btnRefresh.requestFocus() }
    }

    private fun resolveCamerasUrl(): String {
        val preferLan = resources.getBoolean(R.bool.prefer_cameras_lan)
        return when {
            camerasAllFive && preferLan -> getString(R.string.url_cameras_all_lan)
            camerasAllFive -> getString(R.string.url_cameras_all)
            preferLan -> getString(R.string.url_cameras_lan)
            else -> getString(R.string.url_cameras)
        }
    }

    private fun refreshCurrentPage() {
        val current = webView.url
        if (!current.isNullOrBlank() && current != "about:blank") {
            webView.reload()
        } else if (showingCameras) {
            nabuFallbackUsed = false
            webView.loadUrl(resolveCamerasUrl())
        } else {
            nabuFallbackUsed = false
            webView.loadUrl(resolveHomeUrl())
        }
    }

    private fun toggleCamerasView() {
        camerasAllFive = !camerasAllFive
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_CAMERAS_ALL_FIVE, camerasAllFive)
            .apply()
        updateViewToggleLabel()
        if (showingCameras) {
            nabuFallbackUsed = false
            webView.loadUrl(resolveCamerasUrl())
        }
    }

    private fun updateViewToggleLabel() {
        btnViewToggle.text = if (camerasAllFive) {
            getString(R.string.staff_view_all_five_btn)
        } else {
            getString(R.string.staff_view_standard_btn)
        }
    }

    private fun showStaffBar() {
        staffBar.visibility = View.VISIBLE
    }

    private fun hideStaffBar() {
        staffBar.visibility = View.GONE
    }

    private fun showPinDialog() {
        if (pinDialog?.isShowing == true) return
        if (showingCameras) return

        val input = EditText(this).apply {
            hint = getString(R.string.pin_hint)
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            isFocusable = true
            isFocusableInTouchMode = true
        }

        pinDialog = AlertDialog.Builder(this)
            .setTitle(R.string.pin_title)
            .setMessage(R.string.pin_message)
            .setView(input)
            .setPositiveButton(R.string.pin_ok) { _, _ ->
                val entered = input.text?.toString()?.trim().orEmpty()
                val expected = getString(R.string.staff_pin)
                if (entered == expected) {
                    loadCameras()
                } else {
                    Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
                    // Stay on clean slideshow after wrong PIN
                }
            }
            .setNegativeButton(R.string.pin_cancel, null)
            .setCancelable(true)
            .create()

        pinDialog?.show()
        input.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        menuDownAt = SystemClock.elapsedRealtime()
                        menuLongHandled = false
                    } else if (!menuLongHandled) {
                        val held = SystemClock.elapsedRealtime() - menuDownAt
                        if (held >= MENU_LONG_PRESS_MS) {
                            menuLongHandled = true
                            if (showingCameras) {
                                // On cameras: long Menu focuses staff bar Refresh
                                showStaffBar()
                                btnRefresh.requestFocus()
                            } else {
                                showPinDialog()
                            }
                            return true
                        }
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    val held = SystemClock.elapsedRealtime() - menuDownAt
                    // Short press on slideshow: do nothing (keep customer UI clean)
                    // Short press on cameras: ensure staff bar visible / focus Refresh
                    if (!menuLongHandled && held < MENU_LONG_PRESS_MS) {
                        if (showingCameras) {
                            showStaffBar()
                            btnRefresh.requestFocus()
                        }
                    }
                    menuDownAt = 0L
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (showingCameras) {
                loadHome()
                return true
            }
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
            // Stay on home; do not exit app on Back from slideshow
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        pinDialog?.dismiss()
        pinDialog = null
        webView.apply {
            loadUrl("about:blank")
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}

package com.loscompadres.tv

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Los Compadres TV — Android TV / Fire Stick shell.
 *
 * Page 1 (default): customer slideshow WebView.
 * Page 2 (staff): cameras WebView after long-press Menu + correct PIN.
 * Back from cameras returns to slideshow. Customers never see a cameras tab.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var showingCameras = false
    private var pinDialog: AlertDialog? = null

    /** Long-press Menu detection (KEYCODE_MENU). */
    private var menuDownAt: Long = 0L
    private var menuLongHandled = false

    companion object {
        private const val MENU_LONG_PRESS_MS = 700L
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
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
            userAgentString = userAgentString + " LosCompadresTV/1.0"
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
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
            }

            override fun onHideCustomView() {
                val root = findViewById<FrameLayout>(R.id.root)
                customView?.let { root.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
            }

            override fun getDefaultVideoPoster(): Bitmap? {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
    }

    private fun loadHome() {
        showingCameras = false
        webView.loadUrl(getString(R.string.url_home))
    }

    private fun loadCameras() {
        showingCameras = true
        val preferLan = resources.getBoolean(R.bool.prefer_cameras_lan)
        val url = if (preferLan) {
            getString(R.string.url_cameras_lan)
        } else {
            getString(R.string.url_cameras)
        }
        webView.loadUrl(url)
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
                            showPinDialog()
                            return true
                        }
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    val held = SystemClock.elapsedRealtime() - menuDownAt
                    // Short press: intentionally do nothing useful for customers
                    if (!menuLongHandled && held < MENU_LONG_PRESS_MS) {
                        // ignore short Menu press
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

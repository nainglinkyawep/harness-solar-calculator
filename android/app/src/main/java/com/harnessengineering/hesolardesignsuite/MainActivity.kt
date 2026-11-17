package com.harnessengineering.hesolardesignsuite

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.webkit.WebViewAssetLoader
import com.harnessengineering.hesolardesignsuite.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var assetLoader: WebViewAssetLoader
    private var lastFailedUrl: String? = null
    private var currentUrl: String = HOME_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        applySafeAreaInsets()
        setupWebView()
        setupChromeClients()
        setupUiHandlers()
        setupBackNavigation()

        val startUrl = intent?.data?.toString()?.takeIf { isInternalUrl(it) } ?: HOME_URL
        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else {
            loadUrl(startUrl)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.takeIf { isInternalUrl(it) }?.let { loadUrl(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    private fun applySafeAreaInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom
            )
            insets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = true
            defaultTextEncodingName = "UTF-8"
            // Prefer mobile layout from responsive sites.
            userAgentString = userAgentString.replace("; wv", "")
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)
        WebView.setWebContentsDebuggingEnabled(false)
    }

    private fun setupChromeClients() {
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
                    ?: super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url?.toString() ?: return false
                return handleNavigation(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleNavigation(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                currentUrl = url ?: currentUrl
                showLoading(true)
                hideError()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                currentUrl = url ?: currentUrl
                showLoading(false)
                binding.swipeRefresh.isRefreshing = false
                // Viewport + full mobile layout / Myanmar font inject (APK-side CSS).
                view?.evaluateJavascript(MOBILE_VIEWPORT_JS, null)
                applyMobileLayoutFix(view)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    lastFailedUrl = request.url?.toString() ?: HOME_URL
                    showError(error.description?.toString())
                }
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility =
                    if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        binding.webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun setupUiHandlers() {
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.he_forest),
            ContextCompat.getColor(this, R.color.he_lime)
        )
        binding.swipeRefresh.setOnRefreshListener {
            if (isOnline()) {
                binding.webView.reload()
            } else {
                binding.swipeRefresh.isRefreshing = false
                showError(getString(R.string.error_message))
            }
        }

        binding.retryButton.setOnClickListener {
            val target = lastFailedUrl ?: currentUrl.ifBlank { HOME_URL }
            loadUrl(target)
        }
        binding.openBrowserButton.setOnClickListener {
            openExternal(lastFailedUrl ?: HOME_URL)
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun handleNavigation(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase().orEmpty()

        when {
            scheme == "tel" || scheme == "mailto" || scheme == "sms" || scheme == "geo" -> {
                openExternal(url)
                return true
            }
            scheme == "intent" || scheme == "market" -> {
                openExternal(url)
                return true
            }
            scheme == "http" || scheme == "https" -> {
                if (isInternalUrl(url)) {
                    // Stay inside the WebView for the full training site / HE domains.
                    return false
                }
                openExternal(url)
                return true
            }
            else -> {
                openExternal(url)
                return true
            }
        }
    }

    private fun isInternalUrl(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return INTERNAL_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
    }

    private fun loadUrl(url: String) {
        if (!isOnline()) {
            lastFailedUrl = url
            showError(getString(R.string.error_message))
            return
        }
        hideError()
        showLoading(true)
        currentUrl = url
        binding.webView.loadUrl(url)
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showError(detail: String?) {
        showLoading(false)
        binding.swipeRefresh.isRefreshing = false
        binding.errorPanel.visibility = View.VISIBLE
        binding.webView.visibility = View.INVISIBLE
        binding.errorDetail.text = detail?.takeIf { it.isNotBlank() }
            ?: getString(R.string.error_message)
    }

    private fun hideError() {
        binding.errorPanel.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDownload(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                setDescription(getString(R.string.app_name))
                setTitle(fileName)
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            // Fall back to opening the link outside the WebView.
            openExternal(url)
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        const val HOME_URL = "https://training.harnessengineering.com/"

        // Keep the full Harness Engineering web properties inside the APK WebView.
        private val INTERNAL_HOST_SUFFIXES = listOf(
            "training.harnessengineering.com",
            "harnessengineering.com"
        )

        private const val MOBILE_VIEWPORT_JS = """
            (function() {
              try {
                var meta = document.querySelector('meta[name=viewport]');
                if (!meta) {
                  meta = document.createElement('meta');
                  meta.name = 'viewport';
                  document.head.appendChild(meta);
                }
                var content = meta.getAttribute('content') || '';
                if (content.indexOf('width=device-width') === -1) {
                  meta.setAttribute('content',
                    'width=device-width, initial-scale=1, maximum-scale=5, viewport-fit=cover');
                }
                document.documentElement.style.webkitTextSizeAdjust = '100%';
                document.documentElement.style.touchAction = 'manipulation';
              } catch (e) {}
            })();
        """

        // CSS is injected inline from assets at runtime (see applyMobileLayoutFix).
    }

    private fun applyMobileLayoutFix(view: WebView?) {
        if (view == null) return
        val css = runCatching {
            assets.open("he-mobile-fix.css").bufferedReader().use { it.readText() }
        }.getOrElse { return }

        val escaped = JSONObject.quote(css)
        val js = """
            (function() {
              try {
                if (window.__heMobileLayoutFixApplied) return;
                window.__heMobileLayoutFixApplied = true;
                var head = document.head || document.getElementsByTagName('head')[0];
                if (!head) return;
                var charset = document.querySelector('meta[charset]');
                if (!charset) {
                  charset = document.createElement('meta');
                  charset.setAttribute('charset', 'UTF-8');
                  head.insertBefore(charset, head.firstChild);
                }
                var style = document.getElementById('he-mobile-layout-fix');
                if (!style) {
                  style = document.createElement('style');
                  style.id = 'he-mobile-layout-fix';
                  head.appendChild(style);
                }
                style.textContent = $escaped;
                var hub = document.querySelector('.he-hub');
                if (hub) {
                  var node = hub.parentElement;
                  while (node && node !== document.body) {
                    try {
                      node.style.setProperty('max-width', 'none', 'important');
                      node.style.setProperty('width', '100%', 'important');
                      node.style.setProperty('padding-left', '0', 'important');
                      node.style.setProperty('padding-right', '0', 'important');
                    } catch (e1) {}
                    node = node.parentElement;
                  }
                  document.documentElement.style.setProperty('overflow-x', 'hidden', 'important');
                  document.body.style.setProperty('overflow-x', 'hidden', 'important');
                }
                // Direct inline overrides beat site <style> for the worst offenders.
                var setImp = function(el, prop, val) {
                  if (el) el.style.setProperty(prop, val, 'important');
                };
                document.querySelectorAll('.he-hub .motto, p.motto').forEach(function(el) {
                  setImp(el, 'font-family', '"HE Myanmar","Noto Sans Myanmar","Pyidaungsu","Myanmar Text",sans-serif');
                  setImp(el, 'font-weight', '400');
                  setImp(el, 'font-size', '15px');
                  setImp(el, 'line-height', '1.85');
                  setImp(el, 'letter-spacing', '0');
                  setImp(el, 'white-space', 'normal');
                  setImp(el, 'text-shadow', 'none');
                  setImp(el, 'transform', 'none');
                  setImp(el, 'animation', 'none');
                  setImp(el, 'filter', 'none');
                  setImp(el, 'max-width', '100%');
                  setImp(el, 'display', 'block');
                  setImp(el, 'text-align', 'center');
                  setImp(el, 'margin', '14px 0 16px');
                  setImp(el, 'padding', '0 8px');
                  setImp(el, 'overflow-wrap', 'break-word');
                });
                document.querySelectorAll('.he-hub .logo').forEach(function(el) {
                  setImp(el, 'transform', 'none');
                  setImp(el, 'animation', 'none');
                  setImp(el, 'width', 'min(220px, 68%)');
                  setImp(el, 'margin', '0 auto 18px');
                });
                document.querySelectorAll('.he-hub .card, .he-hub .mark, .he-hub .grid').forEach(function(el) {
                  setImp(el, 'transform', 'none');
                  setImp(el, 'animation', 'none');
                  setImp(el, 'perspective', 'none');
                });
                document.querySelectorAll('.he-hub .tools').forEach(function(el) {
                  setImp(el, 'display', 'flex');
                  setImp(el, 'flex-direction', 'column');
                  setImp(el, 'align-items', 'stretch');
                  setImp(el, 'gap', '10px');
                  setImp(el, 'width', '100%');
                });
                document.querySelectorAll('.he-hub main').forEach(function(el) {
                  setImp(el, 'width', '100%');
                  setImp(el, 'max-width', '100%');
                  setImp(el, 'padding', '6px 14px 110px');
                  setImp(el, 'box-sizing', 'border-box');
                });
                document.querySelectorAll('.he-hub .grid').forEach(function(el) {
                  setImp(el, 'grid-template-columns', 'repeat(2, minmax(0, 1fr))');
                  setImp(el, 'gap', '12px');
                  setImp(el, 'margin-top', '18px');
                });
              } catch (e) {}
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }
}

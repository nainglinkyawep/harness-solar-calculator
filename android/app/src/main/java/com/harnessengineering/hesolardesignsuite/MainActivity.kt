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
import com.harnessengineering.hesolardesignsuite.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastFailedUrl: String? = null
    private var currentUrl: String = HOME_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            // Prefer mobile layout from responsive sites.
            userAgentString = userAgentString.replace("; wv", "")
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)
        WebView.setWebContentsDebuggingEnabled(false)
    }

    private fun setupChromeClients() {
        binding.webView.webViewClient = object : WebViewClient() {
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
                // Nudge responsive CSS / viewport if a page forgot mobile meta.
                view?.evaluateJavascript(MOBILE_VIEWPORT_JS, null)
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
    }
}

package com.islamweaving.dashboard

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {

    private val home = "https://nuhan-dev.github.io/Islam_Weaving/"
    private val homeHost = "nuhan-dev.github.io"
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#FBF6EA")))

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#FBF6EA"))
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.isHapticFeedbackEnabled = false
        web.setOnLongClickListener { true }
        setContentView(web)

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.textZoom = 100
        s.mediaPlaybackRequiresUserGesture = true

        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.host == homeHost) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: Exception) {
                }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showOffline()
            }
        }

        if (savedInstanceState == null) web.loadUrl(home) else web.restoreState(savedInstanceState)
    }

    private fun showOffline() {
        val html = assets.open("offline.html").bufferedReader().use { it.readText() }
        web.loadDataWithBaseURL(home, html, "text/html", "UTF-8", null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    // Back: from another tab go to the Summary tab first, then leave the app
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val js = "(function(){var a=document.querySelector('.nav-tab.active');" +
            "if(a&&a.dataset.tab!=='dashboard'){document.querySelector('[data-tab=dashboard]').click();return 'tab';}return 'exit';})()"
        web.evaluateJavascript(js) { result ->
            if (result == null || !result.contains("tab")) finish()
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    inner class Bridge {

        @JavascriptInterface
        fun retry() {
            runOnUiThread { web.loadUrl(home) }
        }

        // The page sends the finished PDF here (base64). It is saved into Downloads and then opened or shared.
        @JavascriptInterface
        fun savePdf(name: String, base64: String, share: Boolean) {
            Thread {
                try {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download")
                    }
                    val uri: Uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw Exception("insert failed")
                    contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                    runOnUiThread {
                        try {
                            if (share) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(send, name))
                            } else {
                                Toast.makeText(this@MainActivity, "Downloads ফোল্ডারে সেভ হয়েছে", Toast.LENGTH_LONG).show()
                                val view = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(view)
                            }
                        } catch (e: Exception) {
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "PDF সেভ করা যায়নি", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }
}

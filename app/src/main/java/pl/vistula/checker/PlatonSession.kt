package pl.vistula.checker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import pl.vistula.checker.core.PlatonLinks

/**
 * Silently renews an expired Platon session. A hidden WebView opens Platon and, when Platon asks for a
 * sign-in, follows the university (CAS / Microsoft) redirect. If the Microsoft session in the WebView is
 * still valid, Platon signs the student in without any input; otherwise this gives up and the caller asks
 * the student to sign in. No password is read, typed or stored, and the page never leaves the allowed hosts.
 */
object PlatonSession {
    private const val TIMEOUT_MS = 30_000L

    /** Blocks the calling (background) thread until the session is renewed or [TIMEOUT_MS] passes. */
    fun refresh(context: Context): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        val done = CountDownLatch(1)
        val success = AtomicBoolean(false)
        val main = Handler(Looper.getMainLooper())
        val holder = arrayOfNulls<WebView>(1)
        val finished = AtomicBoolean(false)

        fun finish(ok: Boolean) {
            if (!finished.compareAndSet(false, true)) return
            success.set(ok)
            main.post {
                holder[0]?.let { web ->
                    try {
                        CookieManager.getInstance().flush()
                        web.stopLoading()
                        web.destroy()
                    } catch (ignored: Exception) {
                    }
                }
                holder[0] = null
                done.countDown()
            }
        }

        main.post {
            try {
                startWebView(context.applicationContext, holder, ::finish)
            } catch (e: Exception) {
                finish(false)
            }
        }
        main.postDelayed({ finish(false) }, TIMEOUT_MS)
        done.await(TIMEOUT_MS + 5_000L, TimeUnit.MILLISECONDS)
        return success.get()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startWebView(context: Context, holder: Array<WebView?>, finish: (Boolean) -> Unit) {
        var casTried = false
        val web = WebView(context)
        holder[0] = web
        web.settings.apply {
            javaScriptEnabled = true // Microsoft/CAS redirects need it
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !PlatonLoginActivity.allowed(request.url.scheme, request.url.host)

            override fun onPageFinished(view: WebView, url: String?) {
                if (PlatonLoginActivity.signedIn(url)) {
                    finish(true)
                } else if (!casTried && PlatonLoginActivity.isPlatonLoginForm(url)) {
                    casTried = true
                    view.loadUrl(PlatonLoginActivity.CAS_LOGIN)
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {}

            /** A crashed renderer ends the silent renewal instead of the whole app; the caller asks for a sign-in. */
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                finish(false)
                return true
            }
        }
        web.loadUrl(PlatonLinks.HOME)
    }
}

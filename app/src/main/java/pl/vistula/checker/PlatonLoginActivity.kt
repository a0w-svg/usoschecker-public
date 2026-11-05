package pl.vistula.checker

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import pl.vistula.checker.core.PlatonLinks
import pl.vistula.checker.core.PlatonSync

/**
 * One-step Platon sign-in. It opens Platon's university (CAS / Microsoft) sign-in directly, so an existing
 * Microsoft session in this WebView signs the student in without typing anything, and it closes by itself as
 * soon as Platon shows a signed-in page. Students with a local Platon account use the button below.
 * The app never sees the password; the session cookie stays in the WebView cookie store.
 */
class PlatonLoginActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var loadBar: ProgressBar
    private lateinit var local: MaterialButton
    private var finished = false
    private var casTried = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Credentials are typed here: keep the window out of screenshots and the recents preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        title = getString(R.string.tasks_platon_login_title)

        loadBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        web = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            settings.apply {
                javaScriptEnabled = true // Microsoft/CAS sign-in does not work without it
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                setSupportMultipleWindows(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                @Suppress("DEPRECATION")
                savePassword = false
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !allowed(request.url.scheme, request.url.host)

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    loadBar.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    loadBar.visibility = View.GONE
                    CookieManager.getInstance().flush()
                    if (signedIn(url)) done()
                    else if (!casTried && isPlatonLoginForm(url)) {
                        // Not signed in: go straight to the university (CAS / Microsoft) sign-in, once.
                        casTried = true
                        view.loadUrl(CAS_LOGIN)
                    }
                    local.visibility = if (onLoginPage(url)) View.VISIBLE else View.GONE
                }
            }
        }
        local = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.tasks_platon_local_login)
            visibility = View.GONE
            setOnClickListener {
                casTried = true
                web.loadUrl(LOCAL_LOGIN)
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(loadBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(web)
            addView(local, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
        web.loadUrl(PlatonLinks.HOME)
    }

    /** Signed in: the session cookie is saved, so hand control back and let the caller sync. */
    private fun done() {
        if (finished) return
        finished = true
        CookieManager.getInstance().flush()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_URL, PlatonLinks.HOME))
        finish()
    }

    override fun onDestroy() {
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "platonUrl"
        internal const val CAS_LOGIN = "https://platon.vistula.edu.pl/LogowanieCas"
        private const val LOCAL_LOGIN = "https://platon.vistula.edu.pl/Logowanie"
        private val HOSTS = listOf("vistula.edu.pl", "microsoftonline.com", "microsoft.com", "live.com", "msauth.net", "msftauth.net")

        internal fun allowed(scheme: String?, host: String?): Boolean {
            if (!"https".equals(scheme, ignoreCase = true) || host == null) return false
            val h = host.lowercase()
            return HOSTS.any { h == it || h.endsWith(".$it") }
        }

        /** On Platon itself, past every sign-in, redirect and CAS callback page. */
        internal fun signedIn(url: String?): Boolean = try {
            PlatonLinks.normalize(url).isNotEmpty() && !PlatonSync.isLoginPage(url, null)
        } catch (e: IllegalArgumentException) {
            false
        }

        internal fun isPlatonLoginForm(url: String?): Boolean =
            url != null && url.lowercase().trimEnd('/').let { it.endsWith("platon.vistula.edu.pl/logowanie") || it.endsWith("/zaloguj") }

        /** Microsoft's form or Platon's own sign-in page, where the local-account button is useful. */
        private fun onLoginPage(url: String?): Boolean =
            url != null && !signedIn(url) && PlatonSync.isLoginPage(url, null)
    }
}

package dev.aezochka.budscontrol

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import dev.aezochka.budscontrol.data.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The production screen hosts the exact reviewed Expressive UI bundled inside
 * the APK (assets/ui/index.html), rather than a look-alike. It is local, has no
 * network dependency, and exposes a narrow native bridge for genuine device
 * state. The rendered design is therefore exactly what was reviewed.
 */
class MainActivity : ComponentActivity() {
    private lateinit var page: WebView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { sendNativeState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        page = WebView(this)
        configure(page)
        setContentView(page)
        page.loadUrl("file:///android_asset/ui/index.html")
        requestPermissions.launch(bluetoothPermissions())
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(webView: WebView) = with(webView) {
        setBackgroundColor(Color.rgb(12, 12, 11))
        overScrollMode = WebView.OVER_SCROLL_NEVER
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            textZoom = 100
        }
        addJavascriptInterface(NativeBridge(), "BudsNative")
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean = true
        }
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) { sendNativeState() }
        }
    }

    private fun bluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)

    /** Sends persisted real profile data into the reviewed local UI. */
    private fun sendNativeState() = scope.launch {
        val profile = LocalStore(this@MainActivity).profiles.first().firstOrNull { it.isSelected }
        val payload = JSONObject().apply {
            put("profileName", profile?.displayName ?: "realme Buds T110")
            put("address", profile?.address ?: "")
        }.toString().replace("\\", "\\\\").replace("'", "\\'")
        page.evaluateJavascript("window.applyNativeState && window.applyNativeState('$payload')", null)
    }

    inner class NativeBridge {
        /** UI asks for a native action; keep methods narrow and user-driven. */
        @JavascriptInterface fun requestPermissions(kind: String) {
            when (kind) {
                "history" -> requestPermissions.launch(arrayOf(
                    Manifest.permission.ACTIVITY_RECOGNITION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS,
                ))
                "bluetooth" -> requestPermissions.launch(bluetoothPermissions())
            }
        }
    }
}

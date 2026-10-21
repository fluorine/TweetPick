package dev.tweetpick

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Toast
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var captureBtn: Button
    private lateinit var findNextBtn: Button
    private lateinit var script: String
    private var nightMode = 0
    private val prefs by lazy { getSharedPreferences("tweetpick", MODE_PRIVATE) }

    private val pickMode
        get() = intent?.action == Intent.ACTION_GET_CONTENT || intent?.action == Intent.ACTION_PICK

    override fun onCreate(savedInstanceState: Bundle?) {
        // x.com's "use device settings" mode reads prefers-color-scheme, which
        // WebView takes from this theme's light/dark flag.
        when (prefs.getString("theme", "system")) {
            "light" -> setTheme(R.style.Theme_TweetPick_Light)
            "dark" -> setTheme(R.style.Theme_TweetPick_Dark)
        }
        super.onCreate(savedInstanceState)
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        script = assets.open("tp.js").bufferedReader().use { it.readText() }

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            // X's login risk check rejects clients it sees as embedded WebViews.
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace(Regex("""Version/[\d.]+ """), "")
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
            addJavascriptInterface(Bridge(), "Android")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    val scheme = req.url.scheme
                    if (scheme == "http" || scheme == "https") return false
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, req.url))
                    } catch (_: ActivityNotFoundException) {
                    }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) = inject()
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        val dp = resources.displayMetrics.density
        captureBtn = Button(this).apply {
            isAllCaps = false
            visibility = View.GONE
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(0xFF1D9BF0.toInt())
                cornerRadius = 24 * dp
            }
            elevation = 6 * dp
            setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), 0)
            setOnClickListener { web.evaluateJavascript("window.__tp&&__tp.prepare()", null) }
        }
        val content = FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            // Sits above x.com's bottom tab bar and left of its compose button.
            addView(captureBtn, FrameLayout.LayoutParams(WRAP_CONTENT, (48 * dp).toInt()).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (72 * dp).toInt()
            })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }
        setContentView(root)

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://x.com/i/bookmarks")
    }

    private fun toolbar(): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun btn(label: String, onClick: (View) -> Unit) =
            Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = label
                isAllCaps = false
                setOnClickListener(onClick)
                row.addView(this)
            }
        btn("←") { if (web.canGoBack()) web.goBack() }
        btn("Bookmarks") { web.loadUrl("https://x.com/i/bookmarks") }
        btn("Likes") { withHandle { web.loadUrl("https://x.com/$it/likes") } }
        btn("Mine") { withHandle { web.loadUrl("https://x.com/$it/with_replies") } }
        btn("Search") { searchDialog() }
        findNextBtn = btn("Next ↓") { web.findNext(true) }.apply {
            visibility = View.GONE
            setOnLongClickListener { web.clearMatches(); visibility = View.GONE; true }
        }
        btn("⋯") { menu(it) }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun inject() = web.evaluateJavascript(script, null)

    private fun withHandle(action: (String) -> Unit) {
        val saved = prefs.getString("handle", "").orEmpty()
        if (saved.isNotEmpty()) return action(saved)
        web.evaluateJavascript("window.__tp?__tp.handle():''") { raw ->
            val found = raw.trim('"')
            if (found.isNotEmpty()) {
                prefs.edit().putString("handle", found).apply()
                action(found)
            } else {
                handleDialog(action)
            }
        }
    }

    private fun handleDialog(then: ((String) -> Unit)? = null) {
        val input = EditText(this).apply {
            hint = "your_handle (without @)"
            setText(prefs.getString("handle", ""))
            isSingleLine = true
        }
        AlertDialog.Builder(this)
            .setTitle("Your X handle")
            .setView(padded(input))
            .setPositiveButton("Save") { _, _ ->
                val h = input.text.toString().trim().removePrefix("@")
                if (h.isNotEmpty()) {
                    prefs.edit().putString("handle", h).apply()
                    then?.invoke(h)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun searchDialog() {
        val input = EditText(this).apply {
            hint = "Words to search"
            isSingleLine = true
        }
        AlertDialog.Builder(this)
            .setTitle("Search")
            .setView(padded(input))
            .setPositiveButton("My tweets") { _, _ ->
                val q = input.text.toString().trim()
                withHandle { xSearch("from:$it $q") }
            }
            .setNeutralButton("All of X") { _, _ -> xSearch(input.text.toString().trim()) }
            .setNegativeButton("On this page") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    web.findAllAsync(q)
                    findNextBtn.visibility = View.VISIBLE
                }
            }
            .show()
    }

    private fun xSearch(q: String) {
        if (q.isBlank()) return
        web.loadUrl("https://x.com/search?f=live&q=" + Uri.encode(q))
    }

    private fun menu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Set my handle").setOnMenuItemClickListener { handleDialog(); true }
            menu.add("Clear selection").setOnMenuItemClickListener {
                web.evaluateJavascript("window.__tp&&__tp.clearSel()", null); true
            }
            val current = prefs.getString("theme", "system")
            menu.addSubMenu("Theme").apply {
                for ((key, label) in listOf("system" to "System", "light" to "Light", "dark" to "Dark")) {
                    add(1, Menu.NONE, Menu.NONE, label).setChecked(key == current)
                        .setOnMenuItemClickListener {
                            prefs.edit().putString("theme", key).apply()
                            recreate()
                            true
                        }
                }
                setGroupCheckable(1, true, true)
            }
            menu.add("Home").setOnMenuItemClickListener { web.loadUrl("https://x.com/home"); true }
            menu.add("Reload").setOnMenuItemClickListener { web.reload(); true }
            show()
        }
    }

    private fun padded(v: View): View {
        val p = (20 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            setPadding(p, p / 2, p, 0)
            addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    /** One capture in progress. Rect values are page coordinates in CSS px. */
    private inner class Stitch(o: JSONObject) {
        val dpr = o.getDouble("dpr").toFloat()
        val left = o.getDouble("x")
        val top = o.getDouble("y")
        val fullH = (o.getDouble("h") * dpr).roundToInt()
        val outH = min(fullH, MAX_HEIGHT_PX)
        val bottom = top + outH / dpr
        val out: Bitmap = Bitmap.createBitmap(
            (o.getDouble("w") * dpr).roundToInt().coerceAtLeast(1), outH.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val urls = o.getJSONArray("urls").let { a -> List(a.length()) { a.getString(it) } }
        val bg = parseCssColor(o.optString("bg")) ?: Color.WHITE
        var next = top
    }

    private var stitch: Stitch? = null

    private fun startCapture(o: JSONObject) {
        val s = Stitch(o)
        if (s.out.width <= 1 || s.fullH <= 0) {
            web.evaluateJavascript("__tp.done(false)", null)
            return toast("Nothing to capture.")
        }
        stitch = s
        captureBtn.visibility = View.GONE
        web.isVerticalScrollBarEnabled = false
        web.evaluateJavascript("__tp.scrollToY(${s.next})", null)
    }

    // The page has scrolled. Copies the visible part of the selection from the screen.
    private fun onScrolled(scrollY: Double, viewportH: Double) {
        val s = stitch ?: return
        val from = maxOf(s.next, scrollY)
        val to = minOf(s.bottom, scrollY + viewportH)
        if (to - from < 1) return finishCapture(s)

        val dstTop = ((from - s.top) * s.dpr).roundToInt()
        val dstBottom = min(((to - s.top) * s.dpr).roundToInt(), s.out.height)
        val loc = IntArray(2).also { web.getLocationInWindow(it) }
        val srcLeft = loc[0] + (s.left * s.dpr).roundToInt()
        val srcTop = loc[1] + ((from - scrollY) * s.dpr).roundToInt()
        val src = Rect(srcLeft, srcTop, srcLeft + s.out.width, srcTop + dstBottom - dstTop)
        if (src.height() <= 0) return finishCapture(s)
        val part = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888)

        PixelCopy.request(window, src, part, { result ->
            if (result != PixelCopy.SUCCESS) {
                toast("Screen copy failed ($result).")
                return@request finishCapture(s)
            }
            Canvas(s.out).drawBitmap(part, 0f, dstTop.toFloat(), null)
            part.recycle()
            s.next = to
            if (s.next >= s.bottom - 0.5) finishCapture(s)
            else web.evaluateJavascript("__tp.scrollToY(${s.next})", null)
        }, Handler(Looper.getMainLooper()))
    }

    private fun finishCapture(s: Stitch) {
        stitch = null
        web.isVerticalScrollBarEnabled = true
        web.evaluateJavascript("__tp.done(true)", null)
        if (s.fullH > s.out.height) toast("Selection is very tall. The bottom is cut off.")
        val file = Captures.save(this, Captures.withFooter(s.out, s.urls, s.bg, s.dpr))
        preview(file)
    }

    private fun preview(file: File) {
        val img = ImageView(this).apply {
            adjustViewBounds = true
            setImageBitmap(BitmapFactory.decodeFile(file.path))
        }
        val dialog = AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(img) })
            .setNegativeButton("Discard") { _, _ -> file.delete() }
        if (pickMode) {
            dialog.setPositiveButton("Use") { _, _ -> returnToCaller(file) }
                .setNeutralButton("Keep & pick more", null)
        } else {
            dialog.setPositiveButton("Share") { _, _ -> share(file) }
                .setNeutralButton("Keep", null)
        }
        dialog.show()
    }

    private fun returnToCaller(file: File) {
        val uri = Captures.uriOf(file)
        val data = Intent().apply {
            setDataAndType(uri, if (file.extension == "jpg") "image/jpeg" else "image/png")
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun share(file: File) {
        val uri = Captures.uriOf(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "jpg") "image/jpeg" else "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share tweet"))
    }

    private fun parseCssColor(css: String): Int? {
        val n = Regex("""[\d.]+""").findAll(css).map { it.value.toFloat() }.toList()
        if (n.size < 3) return null
        return Color.rgb(n[0].toInt(), n[1].toInt(), n[2].toInt())
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night != nightMode && prefs.getString("theme", "system") == "system") recreate()
        nightMode = night
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    inner class Bridge {
        @JavascriptInterface
        fun onSelection(count: Int) = runOnUiThread {
            captureBtn.text = if (count == 1) "Capture 1 tweet" else "Capture $count tweets"
            captureBtn.visibility = if (count > 0) View.VISIBLE else View.GONE
        }

        @JavascriptInterface
        fun onCaptureError(msg: String) = runOnUiThread { toast(msg) }

        @JavascriptInterface
        fun onRect(json: String) = runOnUiThread { startCapture(JSONObject(json)) }

        // The delay lets lazy images that scrolled in finish painting.
        @JavascriptInterface
        fun onScrolled(scrollY: Double, viewportH: Double) = runOnUiThread {
            web.postDelayed({ this@MainActivity.onScrolled(scrollY, viewportH) }, 250)
        }
    }

    private companion object {
        // Bounds bitmap memory: 1080 x 8000 ARGB is about 35 MB.
        const val MAX_HEIGHT_PX = 8000
    }
}

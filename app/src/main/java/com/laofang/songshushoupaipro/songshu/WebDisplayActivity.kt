@file:Suppress("SetJavaScriptEnabled", "DEPRECATION")

package com.laofang.songshushoupaipro.songshu

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.laofang.songshushoupaipro.songshu.core.SettingsManager
import com.laofang.songshushoupaipro.songshu.core.WebBadgeManager
import com.laofang.songshushoupaipro.songshu.core.WebViewInfo
import java.io.File
import kotlin.random.Random

class WebDisplayActivity : ComponentActivity() {
    private var webView: WebView? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var hideHintRunnable: Runnable? = null
    private var burnInRunnable: Runnable? = null

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        val settings = SettingsManager.loadSettings(this)
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).run {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val badgeId = intent.getStringExtra(EXTRA_BADGE_ID) ?: ""
        val badge = WebBadgeManager.findBadge(this, badgeId) ?: WebBadgeManager.getSelectedBadge(this)
        if (badge == null || !badge.hasIndex) {
            finish()
            return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val web = BadgeWebView(this)
        web.setBackgroundColor(Color.BLACK)
        // 单屏页面不要 iOS 式回弹/拉伸；长页面仍可正常纵向滚动
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.isVerticalScrollBarEnabled = false
        // 翻转不再用 View.rotation=180：旋转后的 WebView 硬件层滚动时不重绘新区域，
        // 会"滚到底缺一块、回滚才补"。改为把翻转做进网页（CSS 镜像 + JS 接管触摸），
        // WebView 本体保持不转，见 onPageFinished 里的 installFlipMode。
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            // 长页面：按设备宽度、100% 比例渲染，交给 WebView 正常纵向滚动；
            // 不做 overview 缩放，避免超长页面被整体缩小或只渲染首屏。
            useWideViewPort = false
            loadWithOverviewMode = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.webChromeClient = WebChromeClient()

        fun isAllowedLocalUrl(url: String): Boolean {
            val uri = android.net.Uri.parse(url)
            if (uri.scheme != "file") return true
            return try {
                val path = uri.path ?: return false
                File(path).canonicalPath.startsWith(badge.folder.canonicalPath + File.separator)
            } catch (_: Exception) {
                false
            }
        }

        val progress = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
            visibility = View.VISIBLE
        }

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                // 关闭 Chromium 自身的下拉回弹/拉伸，保留长页面正常滚动
                view?.evaluateJavascript(
                    "(function(){var st=document.createElement('style');" +
                        "st.textContent='html,body{overscroll-behavior:none!important}';" +
                        "document.documentElement.appendChild(st);})()",
                    null
                )
                // 长按复制未开启时禁止选中网页文字
                if (!settings.longPressCopy) {
                    view?.evaluateJavascript(
                        "(function(){var st=document.createElement('style');" +
                            "st.textContent='*{-webkit-user-select:none!important;user-select:none!important}';" +
                            "document.documentElement.appendChild(st);})()",
                        null
                    )
                }
                // 翻转显示：做进网页里（WebView 不旋转，避免硬件层滚动不重绘）
                if (settings.flipDisplay) {
                    view?.evaluateJavascript(flipScript(), null)
                }
                // 内容布局要等一会儿才稳定，稍后再判断是否需要锁死滚动
                if (view is BadgeWebView) {
                    handler.postDelayed({ updateScrollLock(view) }, 400L)
                    handler.postDelayed({ updateScrollLock(view) }, 1500L)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                if (url.startsWith("file:")) return !isAllowedLocalUrl(url)
                return openExternally(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("file:")) return !isAllowedLocalUrl(url)
                return openExternally(url)
            }

            override fun shouldInterceptRequest(view: WebView?, url: String): android.webkit.WebResourceResponse? {
                if (!isAllowedLocalUrl(url)) {
                    return android.webkit.WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), null)
                }
                return null
            }
        }

        root.addView(
            web,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            progress,
            FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER)
        )

        val batteryText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = roundedBackground(Color.argb(140, 0, 0, 0), 12f)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            visibility = View.GONE
        }
        root.addView(
            batteryText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = dp(14) }
        )

        val hint = TextView(this).apply {
            text = getString(R.string.exit_hint)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = roundedBackground(Color.argb(140, 0, 0, 0), 10f)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        root.addView(
            hint,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(40) }
        )
        hideHintRunnable = Runnable { hint.visibility = View.GONE }
        handler.postDelayed(hideHintRunnable!!, 3000L)

        // 内核过旧提醒：页面可能渲染异常，短暂提示用户更新 WebView
        val browserInfo = WebViewInfo.load(this)
        if (WebViewInfo.isOutdated(browserInfo)) {
            val kernelHint = TextView(this).apply {
                text = getString(R.string.webview_old_hint, browserInfo.chromiumMajor)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = roundedBackground(Color.argb(160, 0, 0, 0), 10f)
                setPadding(dp(12), dp(6), dp(12), dp(6))
            }
            root.addView(
                kernelHint,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                ).apply { topMargin = dp(52) }
            )
            handler.postDelayed({ kernelHint.visibility = View.GONE }, 6000L)
        }

        setContentView(root)
        webView = web

        if (settings.showBattery) {
            startBatteryUpdates(batteryText)
        }

        // 原生 View 层级下要等待一次布局完成，viewport 高度才正确
        web.post {
            web.loadUrl(android.net.Uri.fromFile(badge.indexHtml).toString())
            if (settings.antiBurnIn) startBurnIn(web)
        }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        hideHintRunnable?.let { handler.removeCallbacks(it) }
        burnInRunnable?.let { handler.removeCallbacks(it) }
        batteryReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        webView?.let {
            it.loadUrl("about:blank")
            it.stopLoading()
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun startBatteryUpdates(label: TextView) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    label.text = getString(R.string.battery_level, level * 100 / scale)
                    label.visibility = View.VISIBLE
                }
            }
        }
        batteryReceiver = receiver
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun startBurnIn(web: WebView) {
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        val step3 = screenH * 0.03f
        var phase = 0
        val runnable = object : Runnable {
            override fun run() {
                phase = (phase + 1) % 3
                val target = when (phase) {
                    1 -> -step3
                    2 -> step3
                    else -> 0f
                }
                web.animate().translationY(target).setDuration(3000L).start()
                handler.postDelayed(this, 300_000L)
            }
        }
        // 轻微随机一下，避免每次都在同一时刻移动
        handler.postDelayed(runnable, 300_000L + Random.nextLong(0, 30_000L))
        burnInRunnable = runnable
    }

    // 翻转显示：把翻转做进网页，WebView 本体不旋转。
    // - html/body 锁定为一屏高，内容装进内部滚动容器；
    // - body rotate(180deg)：整页倒转（不能用 scaleY(-1)，那只是垂直镜像，文字会左右反）；
    // - 滚动量、视口高重新映射，滚动锁定逻辑照常工作。
    private fun flipScript(): String = """
        (function(){
          if (window.__sf_flip) return;
          window.__sf_flip = true;
          var html = document.documentElement;
          var body = document.body;
          var cs = getComputedStyle(body);
          html.style.height = '100vh';
          html.style.overflow = 'hidden';
          body.style.height = '100vh';
          body.style.overflow = 'hidden';
          body.style.transform = 'rotate(180deg)';
          body.style.transformOrigin = 'center';
          body.style.webkitOverflowScrolling = 'auto';
          var wrap = document.createElement('div');
          wrap.id = '__sf_flip_wrap';
          // 滚动容器要接住 body 原本的布局角色（flex/grid 对齐、内边距、文字对齐等），
          // 否则像 flex 居中的页面，子元素搬进来后会退回普通块级排版（贴左、错位）。
          wrap.style.cssText =
            'width:100%;height:100vh;overflow-y:auto;overflow-x:hidden;' +
            'overscroll-behavior:none;-webkit-overflow-scrolling:auto;' +
            'box-sizing:' + cs.boxSizing + ';' +
            'display:' + cs.display + ';' +
            'flex-direction:' + cs.flexDirection + ';' +
            'flex-wrap:' + cs.flexWrap + ';' +
            'align-items:' + cs.alignItems + ';' +
            'justify-content:' + cs.justifyContent + ';' +
            'gap:' + cs.gap + ';' +
            'padding:' + cs.padding + ';' +
            'text-align:' + cs.textAlign + ';';
          body.style.display = 'block';
          while (body.firstChild) wrap.appendChild(body.firstChild);
          body.appendChild(wrap);
          Object.defineProperty(window, 'scrollY', { get: function(){ return Math.round(wrap.scrollTop); } });
          Object.defineProperty(window, 'innerHeight', { get: function(){ return wrap.clientHeight; } });
          document.documentElement.scrollHeight; // no-op
          Object.defineProperty(document.documentElement, 'scrollHeight', { get: function(){ return wrap.scrollHeight; } });
          wrap.addEventListener('scroll', function(){ window.dispatchEvent(new Event('scroll')); });
          // 翻转后页面的命中测试由浏览器按 CSS 旋转自动处理，保持原样；
          // 但 JS 读到的 clientX/clientY 是未旋转视口坐标，手势方向会反。
          // Chromium 把 clientX/clientY 作为事件实例的自有属性，原型 getter 补丁无效，
          // 所以在捕获阶段直接覆盖事件实例上的这两个属性（own property 可覆盖），
          // 页面手势代码零适配就跟手；preventDefault/stopPropagation 不受影响。
          var W = function(){ return window.innerWidth; };
          var H = function(){ return wrap.clientHeight; };
          var patchEv = function(ev){
            if (!ev || ev.__sfPatched) return;
            ev.__sfPatched = true;
            try {
              Object.defineProperty(ev, 'clientX', { value: W() - ev.clientX, configurable: true });
              Object.defineProperty(ev, 'clientY', { value: H() - ev.clientY, configurable: true });
            } catch (e) {}
          };
          ['pointerdown','pointermove','pointerup','pointercancel',
           'touchstart','touchmove','touchend','touchcancel',
           'mousedown','mousemove','mouseup'].forEach(function(type){
            document.addEventListener(type, patchEv, { capture: true, passive: true });
          });
        })()
    """.trimIndent()

    private fun updateScrollLock(web: BadgeWebView) {
        // 用页面里的真实布局高度判断，废弃的 WebView.getScale() 在部分设备上返回 0，
        // 会导致长页面被误判成单屏而锁死滚动（拖动被取消、页面回弹）。
        web.evaluateJavascript(
            "(function(){return document.documentElement.scrollHeight+'x'+window.innerHeight;})()"
        ) { value ->
            val parts = value?.trim('"', ' ')?.split('x')?.mapNotNull { it.toIntOrNull() } ?: return@evaluateJavascript
            if (parts.size == 2) {
                val (docH, viewH) = parts
                if (docH > 0 && viewH > 0) {
                    web.blockScroll = docH <= viewH + 8
                }
            }
        }
    }

    private fun openExternally(url: String): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_BADGE_ID = "badge_id"

        fun createIntent(context: Context, badgeId: String): Intent =
            Intent(context, WebDisplayActivity::class.java).putExtra(EXTRA_BADGE_ID, badgeId)
    }
}

/**
 * 内容不足一屏时吃掉拖动事件，避免 WebView 自带的拉伸/回弹让整页跟着手指移动；
 * 内容超过一屏时保持正常滚动。点击仍然会传给网页。
 */
private class BadgeWebView(context: Context) : WebView(context) {
    @Volatile
    var blockScroll: Boolean = false

    private var downX = 0f
    private var downY = 0f
    private var slop = 0f
    private var dragCancelled = false

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (!blockScroll) return super.onTouchEvent(event)
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                slop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                dragCancelled = false
                super.onTouchEvent(event)
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!dragCancelled &&
                    (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop)
                ) {
                    // 手指确实拖动了才取消，避免点按抖动把点击吞掉
                    val cancel = android.view.MotionEvent.obtain(event)
                    cancel.action = android.view.MotionEvent.ACTION_CANCEL
                    super.onTouchEvent(cancel)
                    cancel.recycle()
                    dragCancelled = true
                }
                if (!dragCancelled) super.onTouchEvent(event)
                return true
            }
            android.view.MotionEvent.ACTION_UP -> {
                val wasCancelled = dragCancelled
                dragCancelled = false
                // 拖动已取消就不再补 UP；正常点击照常交给网页
                if (!wasCancelled) super.onTouchEvent(event)
                return true
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                dragCancelled = false
                super.onTouchEvent(event)
                return true
            }
        }
        return true
    }
}

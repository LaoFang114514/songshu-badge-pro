package com.laofang.songshushoupaipro.songshu.core

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import org.json.JSONObject

/** 读取系统内置浏览器（Android WebView）的详细信息，供关于页展示和 MCP 查询。 */
object WebViewInfo {

    /** 推荐的最低 Chromium 主版本，低于此值提示用户更新 WebView */
    const val MIN_CHROMIUM_MAJOR = 100

    data class BrowserInfo(
        val providerPackage: String,
        val providerLabel: String,
        val versionName: String,
        val versionCode: Long,
        val chromiumMajor: String,
        val userAgent: String,
        val sdkInt: Int
    )

    fun isOutdated(info: BrowserInfo): Boolean =
        info.chromiumMajor.toIntOrNull()?.let { it < MIN_CHROMIUM_MAJOR } == true

    fun load(context: Context): BrowserInfo {
        val ctx = context.applicationContext
        val pm = ctx.packageManager
        var provider = ""
        var label = ""
        var versionName = ""
        var versionCode = 0L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val pkg = WebView.getCurrentWebViewPackage()
                if (pkg != null) {
                    provider = pkg.packageName
                    versionName = pkg.versionName ?: ""
                    versionCode = versionCodeOf(pkg)
                    pkg.applicationInfo?.let { label = pm.getApplicationLabel(it).toString() }
                }
            } catch (_: Throwable) {
            }
        }
        if (provider.isBlank()) {
            // 低版本兜底：尝试常见 WebView 包名
            for (name in listOf("com.google.android.webview", "com.android.webview")) {
                try {
                    val info = pm.getPackageInfo(name, 0)
                    provider = name
                    versionName = info.versionName ?: ""
                    versionCode = versionCodeOf(info)
                    info.applicationInfo?.let { label = pm.getApplicationLabel(it).toString() }
                    break
                } catch (_: Exception) {
                    // 继续尝试下一个包名
                }
            }
        }

        val ua = try {
            WebSettings.getDefaultUserAgent(ctx)
        } catch (_: Throwable) {
            ""
        }

        return BrowserInfo(
            providerPackage = provider,
            providerLabel = label,
            versionName = versionName,
            versionCode = versionCode,
            chromiumMajor = versionName.substringBefore('.').takeIf { it.all(Char::isDigit) && it.isNotEmpty() } ?: "",
            userAgent = ua,
            sdkInt = Build.VERSION.SDK_INT
        )
    }

    fun toJson(info: BrowserInfo): JSONObject = JSONObject().apply {
        put("provider_package", info.providerPackage)
        put("provider_label", info.providerLabel)
        put("version_name", info.versionName)
        put("version_code", info.versionCode)
        put("chromium_major", info.chromiumMajor)
        put("user_agent", info.userAgent)
        put("device_sdk", info.sdkInt)
        put("recommended_min_chromium", MIN_CHROMIUM_MAJOR)
        put("outdated", isOutdated(info))
    }

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
}
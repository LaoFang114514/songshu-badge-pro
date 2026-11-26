// 松鼠兽牌 PRO 网页兽牌版本
// Copyright (C) 2026  laofang
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package com.laofang.songshushoupaipro.songshu

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.os.Bundle
import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.laofang.songshushoupaipro.songshu.core.WebBadgeManager
import com.laofang.songshushoupaipro.songshu.ui.theme.SongshushoupaiAutoTheme
import java.io.File

/**
 * 媒体预览独立页：顶栏同编辑器（返回 + 文件名），黑底内容区。
 * 图片 / SVG 四周留边距；视频用系统 MediaController（进度条 + 播放暂停键），自动循环。
 */
class PreviewActivity : ComponentActivity() {

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

        val badgeId = intent.getStringExtra(EXTRA_BADGE_ID) ?: ""
        val relativePath = intent.getStringExtra(EXTRA_FILE) ?: ""
        val badge = WebBadgeManager.findBadge(this, badgeId)
        val file = if (badge != null && relativePath.isNotBlank()) File(badge.folder, relativePath) else null

        if (file == null || !file.isFile || badge == null ||
            !file.canonicalPath.startsWith(badge.folder.canonicalPath + File.separator)
        ) {
            finish()
            return
        }

        setContent {
            SongshushoupaiAutoTheme {
                PreviewScreen(
                    fileName = file.name,
                    file = file,
                    root = badge.folder,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.no_anim, R.anim.slide_out_bottom)
    }

    companion object {
        const val EXTRA_BADGE_ID = "badge_id"
        const val EXTRA_FILE = "file"

        fun createIntent(context: Context, badgeId: String, relativePath: String): Intent =
            Intent(context, PreviewActivity::class.java)
                .putExtra(EXTRA_BADGE_ID, badgeId)
                .putExtra(EXTRA_FILE, relativePath)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewScreen(fileName: String, file: File, root: File, onBack: () -> Unit) {
    val ext = fileName.substringAfterLast('.', "").lowercase()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(fileName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp) // 图片 / SVG 四周留空隙
        ) {
            when {
                ext in setOf("mp4", "webm", "mkv", "3gp", "mov") -> MediaPreview(file, Modifier.fillMaxSize())
                ext == "svg" -> SvgPreview(file, root, Modifier.fillMaxSize())
                else -> ImagePreview(file, Modifier.fillMaxSize())
            }
        }
    }
}

/** 图片：按屏幕尺寸采样解码，避免大图直接占满内存。 */
@Composable
private fun ImagePreview(file: File, modifier: Modifier = Modifier) {
    val bmp = remember(file.absolutePath, file.length(), file.lastModified()) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            while (maxSide / options.inSampleSize > 2048) options.inSampleSize *= 2
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Exception) { null }
    }
    DisposableEffect(bmp) {
        onDispose { bmp?.recycle() }
    }
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(stringResource(R.string.image_preview_failed))
        }
    }
}

/** SVG：WebView 渲染（Android 原生控件不支持 SVG），关闭脚本和跨目录文件访问。 */
@Composable
private fun SvgPreview(file: File, root: File, modifier: Modifier = Modifier) {
    val rootPath = remember(root) { root.canonicalPath + File.separator }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                fun allowed(url: String): Boolean {
                    val uri = Uri.parse(url)
                    if (uri.scheme != "file") return true
                    return try {
                        (uri.path ?: return false).startsWith(rootPath)
                    } catch (_: Exception) { false }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                        url != null && (!allowed(url) || !url.startsWith("file:"))

                    override fun shouldInterceptRequest(view: WebView?, url: String): WebResourceResponse? =
                        if (allowed(url)) null
                        else WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), null)
                }
                loadUrl(Uri.fromFile(file).toString())
            }
        },
        onRelease = { web ->
            web.stopLoading()
            web.destroy()
        }
    )
}

/** 视频：VideoView + MediaController（进度条/播放暂停），播完循环。
 *  VideoView 撑满内容区（内部保持宽高比居中，不拉伸画面），控制条随之贴到底部。 */
@Composable
private fun MediaPreview(file: File, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val video = remember { mutableStateOf<VideoView?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> video.value?.start()
                Lifecycle.Event.ON_PAUSE -> video.value?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            video.value?.let {
                it.stopPlayback()
                it.setMediaController(null)
                it.setOnCompletionListener(null)
            }
            video.value = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { vctx ->
            VideoView(vctx).apply {
                video.value = this
                setVideoPath(file.absolutePath)
                setMediaController(MediaController(vctx).also { it.setAnchorView(this) })
                setOnCompletionListener { it.seekTo(0); it.start() }
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
            }
        },
        onRelease = { video.value = null }
    )
}

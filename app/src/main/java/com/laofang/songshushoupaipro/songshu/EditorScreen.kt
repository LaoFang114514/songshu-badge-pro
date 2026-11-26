// 松鼠兽牌 PRO — 独立文件编辑器
// Copyright (C) 2026  laofang
package com.laofang.songshushoupaipro.songshu

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.laofang.songshushoupaipro.songshu.core.WebBadgeManager
import com.laofang.songshushoupaipro.songshu.ui.theme.SongshushoupaiAutoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val EditorCardShape = RoundedCornerShape(16.dp)

class EditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val badgeId = intent.getStringExtra(EXTRA_BADGE_ID) ?: ""
        val relativePath = intent.getStringExtra(EXTRA_FILE) ?: ""
        val badge = WebBadgeManager.findBadge(this, badgeId)
        val file = if (badge != null && relativePath.isNotBlank()) File(badge.folder, relativePath) else null

        val safeFile = badge != null && file != null && runCatching {
            file.isFile && file.canonicalPath.startsWith(badge.folder.canonicalPath + java.io.File.separator)
        }.getOrDefault(false)
        if (!safeFile || !WebBadgeManager.isTextPath(relativePath) || file!!.length() > 2L * 1024 * 1024
        ) {
            Toast.makeText(this, getString(R.string.not_text_file), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            SongshushoupaiAutoTheme {
                EditorScreen(
                    file = file,
                    onBack = { finish() },
                    onSaved = { setResult(Activity.RESULT_OK) }
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
            Intent(context, EditorActivity::class.java)
                .putExtra(EXTRA_BADGE_ID, badgeId)
                .putExtra(EXTRA_FILE, relativePath)
    }
}

@SuppressLint("LocalContextResourcesRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    file: File,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var textValue by remember(file.absolutePath) { mutableStateOf("") }
    var original by remember(file.absolutePath) { mutableStateOf("") }
    var loaded by remember(file.absolutePath) { mutableStateOf(false) }
    var loadError by remember(file.absolutePath) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showConflict by remember { mutableStateOf(false) }
    var editView by remember { mutableStateOf<EditText?>(null) }
    var initialized by remember { mutableStateOf(false) }
    val verticalScroll = rememberScrollState()
    val toolbarScroll = rememberScrollState()

    val lineCount = textValue.count { it == '\n' } + 1
    val dirty = loaded && textValue != original
    val gutterWidthDp = (lineCount.toString().length * 8 + 10).dp
    val gutterWidthPx = with(density) { gutterWidthDp.roundToPx() }
    val editorTextColor = MaterialTheme.colorScheme.onSurface.toArgb()

    LaunchedEffect(file.absolutePath) {
        loadError = null
        loaded = false
        initialized = false
        if (!file.isFile) {
            loadError = "文件不存在"
            return@LaunchedEffect
        }
        try {
            val content = withContext(Dispatchers.IO) { WebBadgeManager.readText(file) }
            textValue = content
            original = content
            loaded = true
        } catch (e: Exception) {
            loadError = e.localizedMessage ?: "读取失败"
        }
    }

    fun save(afterSave: (() -> Unit)? = null) {
        if (!loaded || saving) return
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    WebBadgeManager.writeTextIfUnchanged(file, original, textValue).getOrThrow()
                }
                original = textValue
                Toast.makeText(ctx, ctx.getString(R.string.saved), Toast.LENGTH_SHORT).show()
                onSaved()
                afterSave?.invoke()
            } catch (e: Exception) {
                if (e is WebBadgeManager.FileChangedException) {
                    showConflict = true
                } else {
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.save_failed, e.localizedMessage ?: e.javaClass.simpleName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                saving = false
            }
        }
    }

    fun insertSymbol(symbol: String) {
        val view = editView ?: return
        val start = view.selectionStart.coerceIn(0, view.text.length)
        val end = view.selectionEnd.coerceIn(start, view.text.length)
        view.text.replace(start, end, symbol)
        view.setSelection(start + symbol.length)
        view.requestFocus()
    }

    BackHandler {
        if (dirty) showDiscard = true else onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(file.name) },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) showDiscard = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (dirty) {
                        Text(
                            stringResource(R.string.unsaved_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    TextButton(onClick = { save() }, enabled = loaded && dirty && !saving) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        },
        bottomBar = {
            SymbolToolbar(onInsert = { insertSymbol(it) }, scrollState = toolbarScroll)
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                when {
                    loadError != null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(loadError!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    !loaded -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    else -> {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(verticalScroll)
                                .padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                        ) {
                            AndroidView(
                                factory = { context ->
                                    val edit = LineNumberEditText(context).apply {
                                        setBackgroundColor(AndroidColor.TRANSPARENT)
                                        setTextColor(editorTextColor)
                                        typeface = Typeface.MONOSPACE
                                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                                        gravity = Gravity.TOP or Gravity.START
                                        isSingleLine = false
                                        maxLines = Int.MAX_VALUE
                                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                        setHorizontallyScrolling(true)
                                        setLineSpacing(0f, 1f)
                                        includeFontPadding = false
                                        isFocusableInTouchMode = true
                                        gutterWidth = gutterWidthPx
                                        gutterNumberColor = android.graphics.Color.GRAY
                                        setPadding(gutterWidthPx, 0, 0, 0)
                                    }
                                    edit.addTextChangedListener(object : TextWatcher {
                                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                                        override fun afterTextChanged(s: Editable?) {
                                            textValue = s?.toString() ?: ""
                                        }
                                    })
                                    edit
                                },
                                update = { edit ->
                                    if (editView == null) editView = edit
                                    if (!initialized) {
                                        edit.setText(textValue)
                                        edit.setSelection(textValue.length)
                                        initialized = true
                                    } else if (edit.text.toString() != textValue) {
                                        edit.setText(textValue)
                                    }
                                    edit.gutterWidth = gutterWidthPx
                                    edit.setPadding(gutterWidthPx, 0, 0, 0)
                                    edit.invalidate()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConflict) {
        AlertDialog(
            onDismissRequest = { showConflict = false },
            title = { Text(stringResource(R.string.file_conflict_title)) },
            text = { Text(stringResource(R.string.file_conflict_msg)) },
            confirmButton = {
                Button(onClick = { showConflict = false }, shape = EditorCardShape) {
                    Text(stringResource(R.string.confirm))
                }
            },
            shape = EditorCardShape
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_msg)) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        showDiscard = false
                        onBack()
                    }, shape = EditorCardShape) { Text(stringResource(R.string.discard_changes)) }
                    Button(onClick = {
                        showDiscard = false
                        save { onBack() }
                    }, shape = EditorCardShape) { Text(stringResource(R.string.save_and_exit)) }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDiscard = false }, shape = EditorCardShape) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = EditorCardShape
        )
    }
}

@Composable
private fun SymbolToolbar(
    onInsert: (String) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier
) {
    // 常用英文符号，点一下插入到光标处
    val symbols = listOf(
        "Tab", "{", "}", "(", ")", "[", "]", "<", ">", "/", "\\",
        "=", "+", "-", "*", "%", "!", "?", ":", ";", ",", ".",
        "\"", "'", "_", "#", "&", "|", "^", "~", "@", "$"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            symbols.forEach { symbol ->
                val insertText = if (symbol == "Tab") "    " else symbol
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onInsert(insertText) }
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text(
                        symbol,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** 在编辑框内部直接绘制行号，和正文共用同一套布局基线，换行时也逐行对齐。 */
class LineNumberEditText(context: Context) : EditText(context) {
    var gutterWidth: Int = 0
    var gutterNumberColor: Int = android.graphics.Color.GRAY

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout
        if (textLayout != null && gutterWidth > 0) {
            paint.typeface = typeface
            paint.textSize = textSize
            paint.color = gutterNumberColor
            paint.isAntiAlias = true
            val scrollXOffset = scrollX.toFloat() // 行号固定在左侧，不跟横向滚动
            for (i in 0 until textLayout.lineCount) {
                val number = (i + 1).toString()
                val baseline = totalPaddingTop + textLayout.getLineBaseline(i)
                val x = scrollXOffset + gutterWidth - 8f - paint.measureText(number)
                canvas.drawText(number, x, baseline.toFloat(), paint)
            }
        }
        super.onDraw(canvas)
    }
}


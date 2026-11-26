package com.laofang.songshushoupaipro.songshu

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Html
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.laofang.songshushoupaipro.songshu.core.WebBadge
import com.laofang.songshushoupaipro.songshu.core.WebBadgeManager
import com.laofang.songshushoupaipro.songshu.core.WebFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FileCardShape = RoundedCornerShape(16.dp)

private fun isImagePath(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

private fun isVideoPath(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in setOf("mp4", "webm", "mkv", "3gp", "mov")

@Composable
fun FilesPage(
    badges: List<WebBadge>,
    selectedIndex: Int,
    onOpenFolder: (WebBadge) -> Unit,
    onPreview: (WebBadge) -> Unit,
    onImport: () -> Unit,
    onSelect: (Int) -> Unit,
    onRename: (WebBadge, String) -> Unit,
    onDelete: (WebBadge) -> Unit,
    modifier: Modifier = Modifier
) {
    var renameTarget by remember { mutableStateOf<WebBadge?>(null) }
    var deleteTarget by remember { mutableStateOf<WebBadge?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        userScrollEnabled = badges.isNotEmpty()
    ) {
        if (badges.isEmpty()) {
            item {
                Column(Modifier.fillParentMaxSize()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = FileCardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                stringResource(R.string.files_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onImport,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.import_badge))
                            }
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.Folder, null,
                            Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.files_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FileCardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(
                            stringResource(R.string.files_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.import_badge))
                        }
                    }
                }
            }
        }

        items(badges, key = { it.id }) { badge ->
            val index = badges.indexOf(badge)
            FolderRow(
                badge = badge,
                isSelected = index == selectedIndex,
                onOpen = { onOpenFolder(badge) },
                onPreview = { onPreview(badge) },
                onSelect = { onSelect(index) },
                onRename = { renameTarget = badge },
                onDelete = { deleteTarget = badge }
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    renameTarget?.let { badge ->
        RenameBadgeDialog(
            badge = badge,
            onDismiss = { renameTarget = null },
            onConfirm = { onRename(badge, it) }
        )
    }

    deleteTarget?.let { badge ->
        DeleteBadgeDialog(
            badge = badge,
            onDismiss = { deleteTarget = null },
            onConfirm = { onDelete(badge) }
        )
    }
}

@Composable
private fun FolderRow(
    badge: WebBadge,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var meta by remember(badge.id) { mutableStateOf("") }
    val stamp = badge.folder.lastModified()
    LaunchedEffect(badge.id, stamp) {
        meta = withContext(Dispatchers.IO) {
            val count = WebBadgeManager.fileCount(badge.folder)
            val size = WebBadgeManager.folderSize(badge.folder)
            "${count} 个文件 · ${WebBadgeManager.sizeLabel(size)}"
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(FileCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                FileCardShape
            )
            .clickable { onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Folder, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(badge.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.preview)) },
                    onClick = { expanded = false; onPreview() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.currently_in_use)) },
                    onClick = { expanded = false; onSelect() },
                    enabled = !isSelected
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = { expanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { expanded = false; onDelete() }
                )
            }
        }
    }
}

@Composable
fun BadgeFolderScreen(
    badge: WebBadge,
    onOpenFile: (String) -> Unit,
    onPreview: () -> Unit,
    onRenameBadge: (String) -> Unit,
    onDeleteBadge: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // 首次进文件夹同步加载（目录遍历仅几毫秒，保证首帧就有内容，不会"空一下"）；
    // 之后的刷新走 IO 线程，不阻塞组合
    var files by remember(badge.id) {
        mutableStateOf(WebBadgeManager.listFiles(badge.folder))
    }
    fun refreshFiles() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { WebBadgeManager.listFiles(badge.folder) }
            files = list
        }
    }
    LaunchedEffect(badge.id, badge.folder.lastModified()) { refreshFiles() }

    var showNewFile by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameFileTarget by remember { mutableStateOf<WebFile?>(null) }
    var deleteFileTarget by remember { mutableStateOf<WebFile?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { WebBadgeManager.importFileInto(ctx, badge.folder, uri) }
                result.onSuccess {
                    Toast.makeText(ctx, ctx.getString(R.string.file_added), Toast.LENGTH_SHORT).show()
                    refreshFiles()
                    onChanged()
                }.onFailure {
                    Toast.makeText(ctx, it.localizedMessage ?: "导入失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val totalSize = remember(files) { files.sumOf { it.size } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FileCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(badge.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                stringResource(R.string.folder_meta, files.size.toString(), WebBadgeManager.sizeLabel(totalSize)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            var expanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename)) },
                                    onClick = { expanded = false; showRename = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = { expanded = false; showDelete = true }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPreview, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.preview))
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.import_file))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showNewFile = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.new_file))
                    }
                    if (!badge.hasIndex) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.index_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (files.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.empty_files),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(files, key = { it.relativePath }) { file ->
            FileRow(
                file = file,
                onOpen = {
                    if (file.isText) {
                        if (file.size > 2L * 1024 * 1024) {
                            Toast.makeText(ctx, ctx.getString(R.string.file_too_large), Toast.LENGTH_SHORT).show()
                        } else {
                            onOpenFile(file.relativePath)
                        }
                    } else if (isImagePath(file.relativePath) || isVideoPath(file.relativePath)) {
                        // 图片/视频：独立预览页（和编辑器一样的全屏页面）
                        ctx.startActivity(
                            PreviewActivity.createIntent(ctx, badge.id, file.relativePath),
                            androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                                ctx, R.anim.slide_in_bottom, R.anim.no_anim
                            ).toBundle()
                        )
                    } else {
                        Toast.makeText(ctx, ctx.getString(R.string.not_text_file), Toast.LENGTH_SHORT).show()
                    }
                },
                onRename = { renameFileTarget = file },
                onPreview = {
                    ctx.startActivity(
                        PreviewActivity.createIntent(ctx, badge.id, file.relativePath),
                        androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                            ctx, R.anim.slide_in_bottom, R.anim.no_anim
                            ).toBundle()
                        )
                },
                onDelete = {
                    if (file.relativePath == WebBadgeManager.INDEX_FILE) {
                        Toast.makeText(ctx, ctx.getString(R.string.index_readonly), Toast.LENGTH_LONG).show()
                    } else {
                        deleteFileTarget = file
                    }
                }
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showNewFile) {
        var name by remember { mutableStateOf("new.html") }
        AlertDialog(
            onDismissRequest = { showNewFile = false },
            title = { Text(stringResource(R.string.new_file)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.file_name)) },
                    isError = name.contains('/') || name.contains("..")
                )
            },
            confirmButton = {
                Button(onClick = {
                    val result = WebBadgeManager.createEmptyFile(badge.folder, name)
                    result.onSuccess {
                        showNewFile = false
                        refreshFiles()
                        onChanged()
                    }.onFailure {
                        Toast.makeText(ctx, it.localizedMessage ?: "创建失败", Toast.LENGTH_SHORT).show()
                    }
                }, shape = FileCardShape) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewFile = false }, shape = FileCardShape) { Text(stringResource(R.string.cancel)) }
            },
            shape = FileCardShape
        )
    }

    if (showRename) {
        var name by remember { mutableStateOf(badge.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.name_label)) }) },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) onRenameBadge(name.trim())
                    showRename = false
                }, shape = FileCardShape) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRename = false }, shape = FileCardShape) { Text(stringResource(R.string.cancel)) }
            },
            shape = FileCardShape
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(stringResource(R.string.confirm_delete_msg, badge.name)) },
            confirmButton = {
                Button(onClick = { showDelete = false; onDeleteBadge() }, shape = FileCardShape) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDelete = false }, shape = FileCardShape) { Text(stringResource(R.string.cancel)) }
            },
            shape = FileCardShape
        )
    }

    renameFileTarget?.let { file ->
        val oldName = file.relativePath.substringAfterLast('/')
        var name by remember { mutableStateOf(oldName) }
        AlertDialog(
            onDismissRequest = { renameFileTarget = null },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.file_name)) }) },
            confirmButton = {
                Button(onClick = {
                    val result = WebBadgeManager.renameFile(badge.folder, file.relativePath, name)
                    result.onSuccess {
                        renameFileTarget = null
                        refreshFiles()
                        onChanged()
                    }.onFailure {
                        Toast.makeText(ctx, it.localizedMessage ?: "重命名失败", Toast.LENGTH_SHORT).show()
                    }
                }, shape = FileCardShape) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { renameFileTarget = null }, shape = FileCardShape) { Text(stringResource(R.string.cancel)) }
            },
            shape = FileCardShape
        )
    }

    deleteFileTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteFileTarget = null },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(stringResource(R.string.confirm_delete_file, file.relativePath)) },
            confirmButton = {
                Button(onClick = {
                    WebBadgeManager.deleteFile(badge.folder, file.relativePath)
                        .onFailure { Toast.makeText(ctx, it.localizedMessage ?: "删除失败", Toast.LENGTH_SHORT).show() }
                    deleteFileTarget = null
                    refreshFiles()
                    onChanged()
                }, shape = FileCardShape) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteFileTarget = null }, shape = FileCardShape) { Text(stringResource(R.string.cancel)) }
            },
            shape = FileCardShape
        )
    }
}

@Composable
private fun FileRow(
    file: WebFile,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val fileIcon = when {
            file.relativePath == WebBadgeManager.INDEX_FILE -> Icons.Outlined.Html
            isImagePath(file.relativePath) -> Icons.Outlined.Image
            isVideoPath(file.relativePath) -> Icons.Filled.PlayArrow
            file.isText -> Icons.Outlined.Description
            else -> Icons.AutoMirrored.Outlined.InsertDriveFile
        }
        // 统一用简单图标，不做缩略图解码（避免视频抽帧/图片解码拖慢导航）
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                fileIcon,
                null,
                Modifier.size(24.dp),
                tint = if (file.isText || isImagePath(file.relativePath) || isVideoPath(file.relativePath)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
        Spacer(Modifier.width(2.dp))
        Column(Modifier.weight(1f)) {
            Text(file.relativePath, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(
                WebBadgeManager.sizeLabel(file.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (file.relativePath == WebBadgeManager.INDEX_FILE) {
            Text(
                "入口",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options), Modifier.size(20.dp))
            }
            DropdownMenu(expanded, { expanded = false }, shape = RoundedCornerShape(12.dp)) {
                if (file.relativePath.substringAfterLast('.', "").lowercase() == "svg") {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preview)) },
                        onClick = { expanded = false; onPreview() }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = { expanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { expanded = false; onDelete() },
                    enabled = file.relativePath != WebBadgeManager.INDEX_FILE
                )
            }
        }
    }
}

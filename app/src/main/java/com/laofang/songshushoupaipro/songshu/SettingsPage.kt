package com.laofang.songshushoupaipro.songshu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.laofang.songshushoupaipro.songshu.core.AppSettings
import com.laofang.songshushoupaipro.songshu.core.BackupManager
import com.laofang.songshushoupaipro.songshu.core.McpConfig
import com.laofang.songshushoupaipro.songshu.core.McpServer
import com.laofang.songshushoupaipro.songshu.core.SettingsManager
import com.laofang.songshushoupaipro.songshu.core.WebBadgeManager
import com.laofang.songshushoupaipro.songshu.core.WebDavConfig
import com.laofang.songshushoupaipro.songshu.ui.theme.BluePrimaryLight
import com.laofang.songshushoupaipro.songshu.ui.theme.GreenPrimaryLight
import com.laofang.songshushoupaipro.songshu.ui.theme.OrangePrimaryLight
import com.laofang.songshushoupaipro.songshu.ui.theme.PinkPrimaryLight
import com.laofang.songshushoupaipro.songshu.ui.theme.PurplePrimaryLight
import com.laofang.songshushoupaipro.songshu.ui.theme.RedPrimaryLight
import com.laofang.songshushoupaipro.songshu.ui.theme.TealPrimaryLight
import com.laofang.songshushoupaipro.songshu.ui.theme.YellowPrimaryLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SettingsCardShape = RoundedCornerShape(16.dp)
private val SettingsBtnShape = RoundedCornerShape(12.dp)

@Composable
private fun settingsBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun SettingsScaffold(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) { content() }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().border(settingsBorder(), SettingsCardShape).clip(SettingsCardShape).clickable(onClick = onClick),
        shape = SettingsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(" ▶", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsPage(
    onNavigateToBasic: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToBrowserInfo: () -> Unit = {},
    onNavigateToTutorial: () -> Unit = {},
    onNavigateToMcp: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    val ctx = LocalContext.current

    SettingsScaffold {
        Card(
            modifier = Modifier.fillMaxWidth().border(settingsBorder(), SettingsCardShape).clip(SettingsCardShape),
            shape = SettingsCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                val aprilFools = remember { isAprilFools() }
                // 付费文案只是愚人节玩笑，请勿当真
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (aprilFools) stringResource(R.string.trial_expiring) else stringResource(R.string.support_developer),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "V${BuildConfig.VERSION_NAME}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (aprilFools) stringResource(R.string.please_purchase) else stringResource(R.string.sponsor_via),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { openUrl(ctx, "https://www.ifdian.net/a/laofang") },
                        modifier = Modifier.weight(1f),
                        shape = SettingsBtnShape
                    ) { Text(if (aprilFools) stringResource(R.string.purchase_channel_1) else stringResource(R.string.ifdian)) }
                    OutlinedButton(
                        onClick = { openUrl(ctx, "https://ko-fi.com/laofang") },
                        modifier = Modifier.weight(1f),
                        shape = SettingsBtnShape
                    ) { Text(if (aprilFools) stringResource(R.string.purchase_channel_2) else "Ko-fi") }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        NavRow(Icons.Outlined.Tune, stringResource(R.string.basic_settings), onNavigateToBasic)
        NavRow(Icons.Outlined.ColorLens, stringResource(R.string.theme_settings), onNavigateToTheme)
        NavRow(Icons.Outlined.SettingsBackupRestore, stringResource(R.string.backup_settings), onNavigateToBackup)
        NavRow(Icons.Outlined.Language, stringResource(R.string.browser_info), onNavigateToBrowserInfo)
        NavRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.tutorial_settings), onNavigateToTutorial)
        NavRow(Icons.Outlined.Terminal, stringResource(R.string.mcp_settings), onNavigateToMcp)
        NavRow(Icons.Outlined.Info, stringResource(R.string.about_settings), onNavigateToAbout)
    }
}

@Composable
fun BrowserInfoSettingsPage() {
    val ctx = LocalContext.current
    val info = remember { com.laofang.songshushoupaipro.songshu.core.WebViewInfo.load(ctx) }
    val unknown = stringResource(R.string.browser_unknown)
    val cs = MaterialTheme.colorScheme

    SettingsScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.browser_current), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            BrowserInfoLine(
                stringResource(R.string.browser_provider),
                listOf(info.providerLabel.ifBlank { unknown }, info.providerPackage.ifBlank { unknown })
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            )
            BrowserInfoLine(
                stringResource(R.string.browser_version),
                if (info.versionName.isBlank()) unknown
                else "${info.versionName} (${info.versionCode})"
            )
            BrowserInfoLine(
                stringResource(R.string.browser_chromium),
                info.chromiumMajor.ifBlank { unknown }
            )
            BrowserInfoLine(stringResource(R.string.browser_sdk), info.sdkInt.toString())
            BrowserInfoLine(
                stringResource(R.string.browser_ua),
                info.userAgent.ifBlank { unknown },
                mono = true
            )
            if (com.laofang.songshushoupaipro.songshu.core.WebViewInfo.isOutdated(info)) {
                Text(
                    stringResource(
                        R.string.browser_outdated_hint,
                        info.chromiumMajor.toIntOrNull() ?: 0,
                        com.laofang.songshushoupaipro.songshu.core.WebViewInfo.MIN_CHROMIUM_MAJOR
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.error
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                stringResource(R.string.browser_info_desc),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

private fun isAprilFools(): Boolean {
    val cal = java.util.Calendar.getInstance()
    return cal.get(java.util.Calendar.MONTH) == java.util.Calendar.APRIL &&
        cal.get(java.util.Calendar.DAY_OF_MONTH) == 1
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: Exception) {
    }
}

@Composable
fun BasicSettingsPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(SettingsManager.loadSettings(ctx)) }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(s)
        s = next
        scope.launch { withContext(Dispatchers.IO) { SettingsManager.saveSettings(ctx, next) } }
    }

    SettingsScaffold {
        Column {
            SettingsSwitchRow(
                stringResource(R.string.flip_display),
                stringResource(R.string.flip_display_desc),
                s.flipDisplay
            ) { value -> update { it.copy(flipDisplay = value) } }
            SettingsRowDivider()
            SettingsSwitchRow(
                stringResource(R.string.keep_screen_on),
                stringResource(R.string.keep_screen_on_desc),
                s.keepScreenOn
            ) { value -> update { it.copy(keepScreenOn = value) } }
            SettingsRowDivider()
            SettingsSwitchRow(
                stringResource(R.string.show_battery),
                stringResource(R.string.show_battery_desc),
                s.showBattery
            ) { value -> update { it.copy(showBattery = value) } }
            SettingsRowDivider()
            SettingsSwitchRow(
                stringResource(R.string.anti_burn_in),
                stringResource(R.string.anti_burn_in_desc),
                s.antiBurnIn
            ) { value -> update { it.copy(antiBurnIn = value) } }
            SettingsRowDivider()
            SettingsSwitchRow(
                stringResource(R.string.long_press_copy),
                stringResource(R.string.long_press_copy_desc),
                s.longPressCopy
            ) { value -> update { it.copy(longPressCopy = value) } }
        }
    }
}

@Composable
private fun SettingsRowDivider() = HorizontalDivider(Modifier.padding(vertical = 8.dp))

@Composable
private fun SettingsSwitchRow(label: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ThemeSettingsPage(onThemeChanged: (Int) -> Unit, onDarkModeChanged: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(SettingsManager.loadSettings(ctx)) }
    val cs = MaterialTheme.colorScheme

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(s)
        s = next
        scope.launch { withContext(Dispatchers.IO) { SettingsManager.saveSettings(ctx, next) } }
    }

    SettingsScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dark_mode), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            val darkOpts = remember {
                listOf(
                    0 to R.string.follow_system,
                    1 to R.string.light_mode,
                    2 to R.string.dark_mode_label
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                darkOpts.forEach { (mode, labelRes) ->
                    val sel = s.darkMode == mode
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) cs.primary else cs.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                update { it.copy(darkMode = mode) }
                                onDarkModeChanged(mode)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(labelRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (sel) cs.onPrimary else cs.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.theme_color), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            val themeOpts = remember {
                buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(8 to (R.string.monet_color to Color.Unspecified))
                    add(6 to (R.string.color_red to RedPrimaryLight))
                    add(2 to (R.string.color_orange to OrangePrimaryLight))
                    add(7 to (R.string.color_yellow to YellowPrimaryLight))
                    add(1 to (R.string.color_green to GreenPrimaryLight))
                    add(5 to (R.string.color_teal to TealPrimaryLight))
                    add(0 to (R.string.color_blue to BluePrimaryLight))
                    add(4 to (R.string.color_purple to PurplePrimaryLight))
                    add(3 to (R.string.color_pink to PinkPrimaryLight))
                }
            }
            themeOpts.forEach { (index, pair) ->
                val (label, preview) = pair
                val sel = s.themeColorIndex == index
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable {
                            update { it.copy(themeColorIndex = index) }
                            onThemeChanged(index)
                        }
                        .background(if (sel) cs.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (index == 8) cs.surfaceVariant else preview),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index == 8) {
                            Icon(
                                Icons.Filled.Star,
                                null,
                                Modifier.size(16.dp),
                                cs.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
                    if (sel) Text("✓", style = MaterialTheme.typography.bodyMedium, color = cs.primary)
                }
            }
        }
    }
}

private enum class BackupOperation { EXPORT, IMPORT, WEBDAV_UPLOAD, WEBDAV_DOWNLOAD }

@Composable
fun BackupSettingsPage(onDataChanged: () -> Unit = {}) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var dataSize by remember { mutableStateOf(0L) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var pendingOp by remember { mutableStateOf<BackupOperation?>(null) }
    var webdavUrl by remember { mutableStateOf("") }
    var webdavUser by remember { mutableStateOf("") }
    var webdavPass by remember { mutableStateOf("") }
    var savedUrl by remember { mutableStateOf("") }
    var savedUser by remember { mutableStateOf("") }
    var savedPass by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        dataSize = withContext(Dispatchers.IO) { BackupManager.totalDataSize(appCtx) }
        val c = BackupManager.loadWebDavConfig(appCtx)
        webdavUrl = c.url; webdavUser = c.username; webdavPass = c.password
        savedUrl = c.url; savedUser = c.username; savedPass = c.password
    }

    val isConfigModified = webdavUrl != savedUrl || webdavUser != savedUser || webdavPass != savedPass

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        scope.launch {
            val err = withContext(Dispatchers.IO) { BackupManager.exportToZip(appCtx, uri) }
            isLoading = false
            statusMsg = if (err == null) MSG_EXPORT_OK else "$MSG_EXPORT_FAIL:$err"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        scope.launch {
            val err = withContext(Dispatchers.IO) { BackupManager.importFromZip(appCtx, uri) }
            isLoading = false
            statusMsg = if (err == null) MSG_IMPORT_OK else "$MSG_IMPORT_FAIL:$err"
            if (err == null) {
                dataSize = withContext(Dispatchers.IO) { BackupManager.totalDataSize(appCtx) }
                onDataChanged()
            }
        }
    }

    fun exec(op: BackupOperation) {
        when (op) {
            BackupOperation.EXPORT -> exportLauncher.launch("songshu-badge-pro-backup.zip")
            BackupOperation.IMPORT -> importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            BackupOperation.WEBDAV_UPLOAD, BackupOperation.WEBDAV_DOWNLOAD -> {
                val cfg = BackupManager.loadWebDavConfig(appCtx)
                if (cfg.url.isBlank()) { statusMsg = MSG_CONFIG_SERVER_FIRST; return }
                isLoading = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        if (op == BackupOperation.WEBDAV_UPLOAD) BackupManager.webdavUpload(appCtx, cfg)
                        else BackupManager.webdavDownload(appCtx, cfg)
                    }
                    isLoading = false
                    statusMsg = result ?: if (op == BackupOperation.WEBDAV_UPLOAD) MSG_BACKUP_OK else MSG_RESTORE_OK
                    if (result == null && op == BackupOperation.WEBDAV_DOWNLOAD) {
                        dataSize = withContext(Dispatchers.IO) { BackupManager.totalDataSize(appCtx) }
                        onDataChanged()
                    }
                }
            }
        }
    }

    statusMsg?.let { code ->
        AlertDialog(
            onDismissRequest = { statusMsg = null },
            title = { Text(stringResource(R.string.tip)) },
            text = { Text(backupErrorMsg(code)) },
            confirmButton = {
                Button(onClick = { statusMsg = null }, shape = SettingsBtnShape) { Text(stringResource(R.string.ok)) }
            },
            shape = SettingsBtnShape
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false; pendingOp = null },
            title = { Text(stringResource(R.string.confirm_action)) },
            text = { Text(stringResource(R.string.confirm_override_data)) },
            confirmButton = {
                Button(onClick = {
                    showConfirm = false
                    pendingOp?.let { exec(it) }
                    pendingOp = null
                }, shape = SettingsBtnShape) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirm = false; pendingOp = null }, shape = SettingsBtnShape) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = SettingsBtnShape
        )
    }

    SettingsScaffold {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.local_backup), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.local_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pendingOp = BackupOperation.EXPORT; showConfirm = true },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    shape = SettingsBtnShape
                ) { Text(stringResource(R.string.export_config)) }
                Button(
                    onClick = { pendingOp = BackupOperation.IMPORT; showConfirm = true },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    shape = SettingsBtnShape
                ) { Text(stringResource(R.string.import_config)) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.webdav_backup), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.webdav_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
                OutlinedTextField(
                    value = webdavUrl,
                    onValueChange = { webdavUrl = it },
                    label = { Text(stringResource(R.string.server_address)) },
                    placeholder = { Text("https://example.com/remote.php/dav/files/user/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting
                )
                OutlinedTextField(
                    value = webdavUser,
                    onValueChange = { webdavUser = it },
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting
                )
                OutlinedTextField(
                    value = webdavPass,
                    onValueChange = { webdavPass = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (webdavUrl.isBlank()) { statusMsg = MSG_FILL_SERVER; return@OutlinedButton }
                            isTesting = true
                            scope.launch {
                                val err = BackupManager.webdavTestConnection(WebDavConfig(webdavUrl, webdavUser, webdavPass))
                                isTesting = false
                                statusMsg = err ?: MSG_CONN_OK
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isTesting,
                        shape = SettingsBtnShape
                    ) { ProgressOrText(isTesting, stringResource(R.string.test)) }
                    Button(
                        onClick = { pendingOp = BackupOperation.WEBDAV_UPLOAD; showConfirm = true },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && webdavUrl.isNotBlank(),
                        shape = SettingsBtnShape
                    ) { ProgressOrText(isLoading, stringResource(R.string.backup)) }
                    Button(
                        onClick = { pendingOp = BackupOperation.WEBDAV_DOWNLOAD; showConfirm = true },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && webdavUrl.isNotBlank(),
                        shape = SettingsBtnShape
                    ) { ProgressOrText(isLoading, stringResource(R.string.restore)) }
                }
                AnimatedVisibility(
                    visible = isConfigModified,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Button(
                        onClick = {
                            BackupManager.saveWebDavConfig(appCtx, WebDavConfig(webdavUrl, webdavUser, webdavPass))
                            savedUrl = webdavUrl; savedUser = webdavUser; savedPass = webdavPass
                            statusMsg = MSG_WEBDAV_SAVED
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isTesting,
                        shape = SettingsBtnShape
                    ) { Text(stringResource(R.string.save_config)) }
                }
                if (webdavUrl.isBlank()) {
                    Text(
                        stringResource(R.string.config_webdav_first),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.backup_size), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    WebBadgeManager.sizeLabel(dataSize),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ProgressOrText(showProgress: Boolean, text: String) {
    if (showProgress) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(text)
}

private const val MSG_EXPORT_OK = "_export_ok"
private const val MSG_EXPORT_FAIL = "_export_fail"
private const val MSG_IMPORT_OK = "_import_ok"
private const val MSG_IMPORT_FAIL = "_import_fail"
private const val MSG_CONFIG_SERVER_FIRST = "_config_server"
private const val MSG_BACKUP_OK = "_backup_ok"
private const val MSG_RESTORE_OK = "_restore_ok"
private const val MSG_FILL_SERVER = "_fill_server"
private const val MSG_CONN_OK = "_conn_ok"
private const val MSG_WEBDAV_SAVED = "_webdav_saved"

@Composable
private fun backupErrorMsg(code: String): String {
    val ctx = LocalContext.current
    fun detail(prefix: String): String = code.removePrefix(prefix).removePrefix(":")
    return when {
        code == BackupManager.ERR_AUTH_FAIL -> ctx.getString(R.string.err_auth_fail)
        code == BackupManager.ERR_NO_PERMISSION -> ctx.getString(R.string.err_no_permission)
        code.startsWith(BackupManager.ERR_CONNECTION_FAILED) -> ctx.getString(R.string.err_connection_failed, detail(BackupManager.ERR_CONNECTION_FAILED))
        code == BackupManager.ERR_NETWORK_ERROR -> ctx.getString(R.string.err_network_error)
        code == BackupManager.ERR_UPLOAD_AUTH_FAIL -> ctx.getString(R.string.err_upload_auth_fail)
        code == BackupManager.ERR_UPLOAD_NO_PERMISSION -> ctx.getString(R.string.err_upload_no_permission)
        code == BackupManager.ERR_UPLOAD_FAILED_SIMPLE -> ctx.getString(R.string.err_upload_failed_simple)
        code.startsWith(BackupManager.ERR_UPLOAD_FAILED) -> ctx.getString(R.string.err_upload_failed, detail(BackupManager.ERR_UPLOAD_FAILED))
        code == BackupManager.ERR_REDIRECT_NO_LOCATION -> ctx.getString(R.string.err_redirect_no_location)
        code == BackupManager.ERR_BACKUP_NOT_FOUND -> ctx.getString(R.string.err_backup_not_found)
        code == BackupManager.ERR_DOWNLOAD_NO_PERMISSION -> ctx.getString(R.string.err_download_no_permission)
        code.startsWith(BackupManager.ERR_SERVER_RESPONSE) -> ctx.getString(R.string.err_server_response, detail(BackupManager.ERR_SERVER_RESPONSE))
        code == BackupManager.ERR_NO_CONFIG_IN_ZIP -> ctx.getString(R.string.err_no_config_in_zip)
        code == BackupManager.ERR_UNKNOWN_ERROR -> ctx.getString(R.string.err_unknown_error)
        code == MSG_EXPORT_OK -> ctx.getString(R.string.export_success)
        code.startsWith(MSG_EXPORT_FAIL) -> ctx.getString(R.string.export_failed, detail(MSG_EXPORT_FAIL))
        code == MSG_IMPORT_OK -> ctx.getString(R.string.backup_import_success)
        code.startsWith(MSG_IMPORT_FAIL) -> ctx.getString(R.string.backup_import_failed, detail(MSG_IMPORT_FAIL))
        code == MSG_CONFIG_SERVER_FIRST -> ctx.getString(R.string.fill_server_address)
        code == MSG_BACKUP_OK -> ctx.getString(R.string.backup_success)
        code == MSG_RESTORE_OK -> ctx.getString(R.string.restore_success)
        code == MSG_FILL_SERVER -> ctx.getString(R.string.fill_server_address)
        code == MSG_CONN_OK -> ctx.getString(R.string.connection_success)
        code == MSG_WEBDAV_SAVED -> ctx.getString(R.string.webdav_config_saved)
        else -> code
    }
}

@Composable
fun McpSettingsPage() {
    val ctx = LocalContext.current
    var config by remember { mutableStateOf(McpServer.loadConfig(ctx)) }
    var running by remember { mutableStateOf(McpServer.isRunning) }
    var status by remember {
        mutableStateOf(
            if (McpServer.isRunning) ctx.getString(R.string.mcp_status_running)
            else ctx.getString(R.string.mcp_status_stopped)
        )
    }
    var portText by remember { mutableStateOf(config.port.toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val ip = remember { McpServer.localIp() }
    val url = if (ip.isNullOrBlank()) "未获取到局域网 IP" else "http://$ip:${config.port}/mcp"

    fun copyText(text: String) {
        try {
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("mcp", text))
            Toast.makeText(ctx, ctx.getString(R.string.mcp_copied), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    fun startServer(cfg: McpConfig) {
        val err = McpServer.start(ctx, cfg.port, cfg.token)
        running = McpServer.isRunning
        errorText = err
        status = if (err == null) ctx.getString(R.string.mcp_status_running)
        else ctx.getString(R.string.mcp_start_failed, err)
    }

    fun applyConfig(cfg: McpConfig, restartIfRunning: Boolean) {
        config = cfg
        McpServer.saveConfig(ctx, cfg)
        if (restartIfRunning && running) {
            McpServer.stop()
            running = false
            if (cfg.enabled) startServer(cfg)
        }
    }

    SettingsScaffold {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = SettingsCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = settingsBorder()
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                SettingsSwitchRow(
                    stringResource(R.string.mcp_enable),
                    stringResource(R.string.mcp_enable_desc),
                    config.enabled
                ) { on ->
                    val p = portText.toIntOrNull()?.takeIf { it in 1024..65535 } ?: config.port
                    val next = config.copy(enabled = on, port = p)
                    config = next
                    McpServer.saveConfig(ctx, next)
                    if (on) {
                        startServer(next)
                    } else {
                        McpServer.stop()
                        running = false
                        errorText = null
                        status = ctx.getString(R.string.mcp_status_stopped)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = SettingsCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = settingsBorder()
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.mcp_url), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        url,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { copyText(url) }) { Text(stringResource(R.string.mcp_copy)) }
                }

                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.mcp_token), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        config.token,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { copyText(config.token) }) { Text(stringResource(R.string.mcp_copy)) }
                    TextButton(onClick = {
                        val newToken = McpServer.regenerateToken(ctx)
                        val next = config.copy(token = newToken)
                        applyConfig(next, restartIfRunning = true)
                    }) { Text(stringResource(R.string.mcp_regenerate)) }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { text ->
                            if (text.length <= 5 && text.all { it.isDigit() }) portText = text
                        },
                        label = { Text(stringResource(R.string.mcp_port)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val p = portText.toIntOrNull()?.takeIf { it in 1024..65535 }
                            if (p == null) {
                                errorText = "端口需在 1024-65535"
                                status = errorText!!
                            } else {
                                errorText = null
                                applyConfig(config.copy(port = p), restartIfRunning = true)
                                status = if (running) ctx.getString(R.string.mcp_status_running)
                                else ctx.getString(R.string.mcp_status_stopped)
                            }
                        },
                        shape = SettingsBtnShape
                    ) { Text(stringResource(R.string.mcp_apply_port)) }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = SettingsCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = settingsBorder()
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    stringResource(R.string.mcp_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.mcp_tools_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.mcp_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TutorialSettingsPage() {
    SettingsScaffold {
        TutorialCard(stringResource(R.string.tut_import_title), stringResource(R.string.tut_import_desc), 1)
        TutorialCard(stringResource(R.string.tut_edit_title), stringResource(R.string.tut_edit_desc), 2)
        TutorialCard(stringResource(R.string.tut_home_title), stringResource(R.string.tut_home_desc), 3)
        TutorialCard(stringResource(R.string.tut_play_title), stringResource(R.string.tut_play_desc), 4)
        TutorialCard(stringResource(R.string.tut_exit_title), stringResource(R.string.tut_exit_desc), 5)
        TutorialCard(stringResource(R.string.tut_flip_title), stringResource(R.string.tut_flip_desc), 6)
        TutorialCard(stringResource(R.string.tut_mcp_title), stringResource(R.string.tut_mcp_desc), 7)
        TutorialCard(stringResource(R.string.tut_browser_title), stringResource(R.string.tut_browser_desc), 8)
        TutorialCard(stringResource(R.string.tut_backup_title), stringResource(R.string.tut_backup_desc), 9)
        TutorialCard(stringResource(R.string.tut_faq_title), stringResource(R.string.tut_faq_desc), 10)
    }
}

@Composable
private fun TutorialCard(title: String, desc: String, no: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = settingsBorder()
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("$no", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AboutSettingsPage(
    onOpenLicense: () -> Unit = {},
    onBlackVinylEgg: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var logoClicks by remember { mutableIntStateOf(0) }

    SettingsScaffold {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(cs.surfaceVariant)
                    .clickable {
                        logoClicks++
                        if (logoClicks >= 10) {
                            logoClicks = 0
                            onBlackVinylEgg()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.qidong),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(6.dp), color = cs.primary.copy(alpha = 0.12f)) {
                Text(
                    "V${BuildConfig.VERSION_NAME}",
                    Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.app_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutLinkBtn(stringResource(R.string.qq_group), "https://qm.qq.com/q/RbJoe54fEA")
                AboutLinkBtn(stringResource(R.string.discord), "https://discord.gg/vBNtab9yU")
                AboutLinkBtn("GitHub", "https://github.com/LaoFang114514/songshu-badge-pro")
                AboutLinkBtn("GitCode", "https://gitcode.com/LaoFang233/songshu-badge-pro")
                OutlinedButton(onClick = onOpenLicense, Modifier.fillMaxWidth(), shape = SettingsBtnShape) {
                    Text(stringResource(R.string.open_source_license))
                }
            }
        }
    }
}

@Composable
private fun BrowserInfoLine(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ) else MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AboutLinkBtn(label: String, url: String) {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = { openUrl(ctx, url) },
        modifier = Modifier.fillMaxWidth(),
        shape = SettingsBtnShape
    ) { Text(label) }
}

private data class LibInfo(val name: Int, val desc: Int, val author: Int, val url: String)

@Composable
fun LicenseSettingsPage() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val libs = listOf(
        LibInfo(R.string.lib_compose, R.string.lib_compose_desc, R.string.lib_compose_author, "https://github.com/JetBrains/compose-multiplatform"),
        LibInfo(R.string.lib_kotlin, R.string.lib_kotlin_desc, R.string.lib_kotlin_author, "https://github.com/JetBrains/kotlin"),
        LibInfo(R.string.lib_activity, R.string.lib_activity_desc, R.string.lib_activity_author, "https://developer.android.com/jetpack/androidx/releases/activity"),
        LibInfo(R.string.lib_lifecycle, R.string.lib_lifecycle_desc, R.string.lib_lifecycle_author, "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
        LibInfo(R.string.lib_core, R.string.lib_core_desc, R.string.lib_core_author, "https://developer.android.com/jetpack/androidx/releases/core"),
        LibInfo(R.string.lib_splashscreen, R.string.lib_splashscreen_desc, R.string.lib_splashscreen_author, "https://developer.android.com/jetpack/androidx/releases/core"),
        LibInfo(R.string.lib_material, R.string.lib_material_desc, R.string.lib_material_author, "https://github.com/material-components/material-components-android"),
        LibInfo(R.string.lib_documentfile, R.string.lib_documentfile_desc, R.string.lib_documentfile_author, "https://github.com/androidx/androidx"),
        LibInfo(R.string.lib_webview, R.string.lib_webview_desc, R.string.lib_webview_author, "https://developer.android.com/reference/android/webkit/WebView")
    )
    SettingsScaffold {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            libs.forEach { lib ->
                Card(
                    modifier = Modifier.fillMaxWidth().border(settingsBorder(), SettingsCardShape).clip(SettingsCardShape).clickable {
                        openUrl(ctx, lib.url)
                    },
                    shape = SettingsCardShape,
                    colors = CardDefaults.cardColors(containerColor = cs.surface)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(lib.name),
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.primary
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                stringResource(lib.desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.author_by, stringResource(lib.author)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(shape = RoundedCornerShape(6.dp), color = cs.primary.copy(alpha = 0.1f)) {
                                    Text(
                                        stringResource(R.string.lib_apache2),
                                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.primary
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("›", style = MaterialTheme.typography.titleLarge, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// 兼容：旧代码里可能用到的空实现

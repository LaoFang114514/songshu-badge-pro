package com.laofang.songshushoupaipro.songshu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.outlined.Html
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laofang.songshushoupaipro.songshu.core.WebBadge

private val HomeCardShape = RoundedCornerShape(16.dp)

private val BadgeIconChoices: List<ImageVector> = listOf(
    Icons.Filled.Language,
    Icons.Outlined.Html,
    Icons.Filled.Code,
    Icons.Filled.Terminal,
    Icons.Filled.Folder,
    Icons.Filled.Image,
    Icons.Filled.PhotoCamera,
    Icons.Filled.Star,
    Icons.Filled.Favorite,
    Icons.Filled.Pets,
    Icons.Filled.MusicNote,
    Icons.Filled.Headphones,
    Icons.Filled.Movie,
    Icons.Filled.VideogameAsset,
    Icons.Filled.SportsEsports,
    Icons.Filled.Palette,
    Icons.Filled.Brush,
    Icons.Filled.Bolt,
    Icons.Filled.RocketLaunch,
    Icons.Filled.AutoAwesome
)

private val IconTileBackground = Color(0xFFF6FBFF)
private val IconTileTint = Color(0xFF1565C0)

@Composable
fun HomePage(
    badges: List<WebBadge>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onPlay: () -> Unit,
    onRename: (WebBadge, String) -> Unit,
    onChangeIcon: (WebBadge, Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDelete: (WebBadge) -> Unit,
    modifier: Modifier = Modifier
) {
    var renameTarget by remember { mutableStateOf<WebBadge?>(null) }
    var deleteTarget by remember { mutableStateOf<WebBadge?>(null) }
    var iconTarget by remember { mutableStateOf<WebBadge?>(null) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp)
        ) {
            if (badges.isEmpty()) {
                item {
                    Column(
                        Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.Language, null,
                            Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            itemsIndexed(badges, key = { _, badge -> badge.id }) { index, badge ->
                BadgeCard(
                    badge = badge,
                    isSelected = index == selectedIndex,
                    onSelect = { onSelect(index) },
                    onRename = { renameTarget = badge },
                    onChangeIcon = { iconTarget = badge },
                    onDelete = { deleteTarget = badge },
                    onMoveUp = { onMoveUp(index) },
                    onMoveDown = { onMoveDown(index) },
                    canMoveUp = index > 0,
                    canMoveDown = index < badges.size - 1
                )
            }
        }

        if (badges.isNotEmpty()) {
            // 当前选中兽牌是否处于 MCP 编辑窗口（1s 轮询，保证 5s 窗口过后按钮自动恢复）
            val editingBadgeId = badges.getOrNull(selectedIndex)?.id
            var playEditing by remember { mutableStateOf(false) }
            LaunchedEffect(editingBadgeId) {
                while (true) {
                    playEditing = editingBadgeId != null && com.laofang.songshushoupaipro.songshu.core.DataEvents.isEditing(editingBadgeId)
                    kotlinx.coroutines.delay(1000)
                }
            }
            WidePlayButton(
                onClick = onPlay,
                editing = playEditing,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
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

    iconTarget?.let { badge ->
        AlertDialog(
            onDismissRequest = { iconTarget = null },
            title = { Text(stringResource(R.string.change_icon)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BadgeIconChoices.chunked(4).forEachIndexed { rowIndex, rowIcons ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowIcons.forEachIndexed { colIndex, icon ->
                                val choiceIndex = rowIndex * 4 + colIndex
                                val selected = choiceIndex == badge.iconIndex
                                Box(
                                    Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            else IconTileBackground
                                        )
                                        .clickable {
                                            onChangeIcon(badge, choiceIndex)
                                            iconTarget = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        icon, null,
                                        Modifier.size(30.dp),
                                        tint = IconTileTint
                                    )
                                }
                            }
                            repeat(4 - rowIcons.size) {
                                Spacer(Modifier.size(56.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { iconTarget = null }, shape = HomeCardShape) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = HomeCardShape
        )
    }
}

@Composable
private fun WidePlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    editing: Boolean = false
) {
    Surface(
        onClick = { if (!editing) onClick() },
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = if (editing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
        contentColor = if (editing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(26.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                if (editing) stringResource(R.string.mcp_editing_ing) else stringResource(R.string.play_badge),
                style = MaterialTheme.typography.titleMedium,
                fontSize = 17.sp
            )
        }
    }
}

@Composable
private fun BadgeCard(
    badge: WebBadge,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onChangeIcon: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val borderWidth by animateFloatAsState(if (isSelected) 2f else 1f, tween(250), label = "cardBorder")

    Row(
        Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(HomeCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(
                    borderWidth.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                HomeCardShape
            )
            .clickable { onSelect() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(IconTileBackground),
            contentAlignment = Alignment.Center
        ) {
            val icon = BadgeIconChoices.getOrElse(badge.iconIndex) { BadgeIconChoices[0] }
            Icon(
                icon,
                badge.name,
                Modifier.size(38.dp),
                tint = IconTileTint
            )
            if (isSelected) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
        }

        Column(Modifier.weight(1f)) {
            Text(badge.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (isSelected) stringResource(R.string.currently_in_use) else stringResource(R.string.web_badge),
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                modifier = Modifier.border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(12.dp)
                )
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.change_icon)) },
                    onClick = { expanded = false; onChangeIcon() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = { expanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_up)) },
                    onClick = { expanded = false; onMoveUp() },
                    enabled = canMoveUp
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_down)) },
                    onClick = { expanded = false; onMoveDown() },
                    enabled = canMoveDown
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { expanded = false; onDelete() }
                )
            }
        }
    }
}

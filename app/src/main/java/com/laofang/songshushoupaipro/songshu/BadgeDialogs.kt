package com.laofang.songshushoupaipro.songshu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.laofang.songshushoupaipro.songshu.core.WebBadge

private val BadgeDialogShape = RoundedCornerShape(16.dp)

@Composable
fun RenameBadgeDialog(
    badge: WebBadge,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(badge.id) { mutableStateOf(badge.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.name_label)) }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.folder_rename_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) onConfirm(text.trim())
                    onDismiss()
                },
                shape = BadgeDialogShape
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = BadgeDialogShape) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = BadgeDialogShape
    )
}

@Composable
fun DeleteBadgeDialog(
    badge: WebBadge,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_delete)) },
        text = { Text(stringResource(R.string.confirm_delete_msg, badge.name)) },
        confirmButton = {
            Button(onClick = { onConfirm(); onDismiss() }, shape = BadgeDialogShape) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = BadgeDialogShape) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = BadgeDialogShape
    )
}

package com.phantomcrowd.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.phantomcrowd.ui.theme.DesignSystem

/**
 * Accessible dialog using design-system tokens.
 *
 * @param title           Dialog title.
 * @param message         Body text.
 * @param confirmLabel    Text for the confirm button.
 * @param dismissLabel    Text for the dismiss button (null hides button).
 * @param onConfirm       Confirm callback.
 * @param onDismiss       Dismiss callback.
 * @param modifier        Outer modifier.
 */
@Composable
fun PDialog(
    title: String,
    message: String,
    confirmLabel: String = "OK",
    dismissLabel: String? = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = DesignSystem.Typography.titleLarge,
                color = DesignSystem.Colors.onSurface
            )
        },
        text = {
            Text(
                text = message,
                style = DesignSystem.Typography.bodyMedium,
                color = DesignSystem.Colors.onSurface
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.semantics { contentDescription = confirmLabel }
            ) {
                Text(confirmLabel, color = DesignSystem.Colors.primary)
            }
        },
        dismissButton = if (dismissLabel != null) {
            {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { contentDescription = dismissLabel }
                ) {
                    Text(dismissLabel, color = DesignSystem.Colors.neutralMuted)
                }
            }
        } else null,
        containerColor = DesignSystem.Colors.surface,
        shape = DesignSystem.Shapes.dialog,
        modifier = modifier.semantics { contentDescription = "Dialog: $title" }
    )
}

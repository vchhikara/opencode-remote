package com.opencode.remote.ui.screens.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.opencode.remote.ui.components.OcButton
import com.opencode.remote.ui.components.OcButtonKind
import com.opencode.remote.ui.components.OcChip
import com.opencode.remote.ui.components.OcTextField
import com.opencode.remote.ui.components.topRule
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType

data class ComposerChip(val label: String, val onClick: () -> Unit)

/**
 * Bottom prompt bar shared by Home and the Run log. Sends through the real
 * RemoteSessionManager.sendPrompt pathway (via [onSend]); disabled while the socket is
 * down so a prompt never *looks* sent when it couldn't be.
 */
@Composable
fun PromptComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: (String) -> Unit,
    placeholder: String,
    connected: Boolean,
    modifier: Modifier = Modifier,
    chips: List<ComposerChip> = emptyList()
) {
    val c = Oc.colors
    val canSend = connected && draft.isNotBlank()
    val send = { if (canSend) onSend(draft.trim()) }
    Column(
        modifier
            .fillMaxWidth()
            .topRule(c.rule)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (chips.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { OcChip(it.label, it.onClick) }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OcTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = if (connected) placeholder else "Offline — waiting for the bridge",
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            OcButton(
                text = "Send",
                onClick = send,
                kind = if (canSend) OcButtonKind.Gold else OcButtonKind.Neutral,
                enabled = canSend,
                height = 46.dp,
                textStyle = OcType.buttonLargeStrong,
                contentPadding = PaddingValues(horizontal = 18.dp)
            )
        }
    }
}

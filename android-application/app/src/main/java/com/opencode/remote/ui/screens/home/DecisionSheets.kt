package com.opencode.remote.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opencode.remote.data.dto.PermissionDecision
import com.opencode.remote.data.dto.PermissionRequestDto
import com.opencode.remote.data.dto.QuestionRequestDto
import com.opencode.remote.ui.components.CodeText
import com.opencode.remote.ui.components.Eyebrow
import com.opencode.remote.ui.components.OcButton
import com.opencode.remote.ui.components.OcButtonKind
import com.opencode.remote.ui.components.OcTextField
import com.opencode.remote.ui.components.startRule
import com.opencode.remote.ui.components.topRule
import com.opencode.remote.ui.theme.Oc
import com.opencode.remote.ui.theme.OcType
import kotlinx.coroutines.launch

/**
 * The interrupt sheet from the prototype. Shows only what PERMISSION_REQUEST genuinely
 * carries (tool, session id, request id) and replies through
 * RemoteSessionManager.replyPermission via [onDecision]. The decision is bound to the
 * [request] rendered *in this sheet*, so a newer request can't be answered by a stale tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSheet(
    request: PermissionRequestDto,
    onDecision: (permissionId: String, decision: PermissionDecision) -> Unit,
    onDismiss: () -> Unit
) {
    val c = Oc.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val decide: (PermissionDecision) -> Unit = { decision ->
        val id = request.permissionId
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDecision(id, decision) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = c.panel,
        contentColor = c.ink,
        scrimColor = c.sheetScrim,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .topRule(c.gold)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 26.dp)
        ) {
            SheetHandle()
            Eyebrow("The agent needs a decision", color = c.goldInk)
            Spacer(Modifier.height(12.dp))
            Text("Allow ${request.tool ?: "this tool"}?", style = OcType.sheetTitle, color = c.ink)
            Spacer(Modifier.height(10.dp))
            Text(
                "The agent paused before using this tool. Nothing runs until you answer.",
                style = OcType.bodyLarge, color = c.ink2
            )
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.goldWash)
                    .startRule(c.gold, 2.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Eyebrow("Tool")
                Spacer(Modifier.height(8.dp))
                CodeText(request.tool ?: "unspecified", color = c.ink, maxLines = 3)
                request.sessionId?.let {
                    Spacer(Modifier.height(10.dp))
                    Eyebrow("Session")
                    Spacer(Modifier.height(4.dp))
                    CodeText(it, color = c.ink2, style = OcType.monoSmall)
                }
                Spacer(Modifier.height(10.dp))
                Eyebrow("Request")
                Spacer(Modifier.height(4.dp))
                CodeText(request.permissionId, color = c.ink2, style = OcType.monoSmall)
            }
            Spacer(Modifier.height(20.dp))
            OcButton(
                text = "Allow once",
                onClick = { decide(PermissionDecision.AllowOnce) },
                kind = OcButtonKind.Gold,
                height = 50.dp,
                textStyle = OcType.buttonHero,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OcButton(
                    text = "Always allow",
                    onClick = { decide(PermissionDecision.AlwaysAllow) },
                    height = 46.dp,
                    textStyle = OcType.buttonLarge,
                    modifier = Modifier.weight(1f)
                )
                OcButton(
                    text = "Deny",
                    onClick = { decide(PermissionDecision.Deny) },
                    kind = OcButtonKind.NegativeOutline,
                    height = 46.dp,
                    textStyle = OcType.buttonLarge,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * QUESTION_REQUEST in the same interrupt language. Options (when the bridge sends
 * them) are offered as equal neutral choices; with no options, a free-text answer is
 * sent through the same RemoteSessionManager.replyQuestion pathway.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionSheet(
    request: QuestionRequestDto,
    onAnswer: (questionId: String, answer: String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = Oc.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(request.questionId) { mutableStateOf("") }
    val answer: (String) -> Unit = { value ->
        val id = request.questionId
        scope.launch { sheetState.hide() }.invokeOnCompletion { onAnswer(id, value) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = c.panel,
        contentColor = c.ink,
        scrimColor = c.sheetScrim,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .topRule(c.gold)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 26.dp)
        ) {
            SheetHandle()
            Eyebrow("The agent has a question", color = c.goldInk)
            Spacer(Modifier.height(12.dp))
            Text(request.text ?: "The agent is waiting for an answer.", style = OcType.sheetTitle, color = c.ink)
            Spacer(Modifier.height(20.dp))
            val options = request.options.orEmpty().filter { it.isNotBlank() }
            if (options.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { option ->
                        OcButton(
                            text = option,
                            onClick = { answer(option) },
                            height = 46.dp,
                            textStyle = OcType.buttonLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                OcTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = "Type your answer",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) answer(draft.trim()) })
                )
                Spacer(Modifier.height(10.dp))
                OcButton(
                    text = "Send answer",
                    onClick = { answer(draft.trim()) },
                    kind = OcButtonKind.Gold,
                    enabled = draft.isNotBlank(),
                    height = 50.dp,
                    textStyle = OcType.buttonHero,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(bottom = 18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(44.dp).height(2.dp).background(Oc.colors.ruleStrong))
    }
}

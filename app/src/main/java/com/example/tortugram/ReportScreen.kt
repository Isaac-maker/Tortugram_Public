package com.example.tortugram

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

/**
 * Report screen for a chat or channel, submitted to Telegram.
 *
 * Opened from the chat header button (MainActivity.kt) as an overlay. It renders each step
 * returned by TelegramManager.reportChat (submenu, free text or result) and keeps a local stack
 * of menus for the "Back" button. The block/unblock button is local-only and independent of the
 * report (see BlockedChatsManager).
 *
 * isaac-maker 2026
 */
private sealed class ReportUiState {
    object Loading : ReportUiState()
    data class Menu(val title: String, val options: List<TelegramManager.ReportOptionItem>) : ReportUiState()
    data class TextInput(val optionId: ByteArray, val isOptional: Boolean) : ReportUiState()
    object Done : ReportUiState()
    data class Error(val message: String?) : ReportUiState()
}

@Composable
fun ReportScreen(
    chatId: Long,
    chatTitle: String,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var uiState by remember { mutableStateOf<ReportUiState>(ReportUiState.Loading) }

    // Stack of previously visited menus, used by the "Back" button (TDLib has no previous
    // step).
    val menuStack = remember { mutableStateListOf<ReportUiState.Menu>() }

    var customText by remember { mutableStateOf("") }
    var isBlocked by remember { mutableStateOf(BlockedChatsManager.isBlocked(chatId)) }

    // Requests focus on the first interactive element of each step so the D-pad can navigate.
    val firstOptionFocusRequester = remember { FocusRequester() }
    val textInputFocusRequester = remember { FocusRequester() }
    val primaryActionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState) {
        val target = when (uiState) {
            is ReportUiState.Menu -> firstOptionFocusRequester
            is ReportUiState.TextInput -> textInputFocusRequester
            is ReportUiState.Done, is ReportUiState.Error -> primaryActionFocusRequester
            else -> null
        }
        try {
            target?.requestFocus()
        } catch (_: Exception) {}
    }

    fun applyResult(result: TelegramManager.ReportStepResult) {
        uiState = when (result) {
            is TelegramManager.ReportStepResult.Ok ->
                ReportUiState.Done

            is TelegramManager.ReportStepResult.ChooseOption ->
                ReportUiState.Menu(result.title, result.options)

            is TelegramManager.ReportStepResult.NeedsText ->
                ReportUiState.TextInput(result.optionId, result.isOptional)

            is TelegramManager.ReportStepResult.Failed ->
                ReportUiState.Error(result.message)
        }
    }

    fun goToStep(optionId: ByteArray, text: String = "", pushCurrentMenu: Boolean = false) {
        if (pushCurrentMenu) {
            (uiState as? ReportUiState.Menu)?.let { menuStack.add(it) }
        }
        keyboardController?.hide()
        customText = ""
        uiState = ReportUiState.Loading
        scope.launch {
            applyResult(TelegramManager.reportChat(chatId, optionId, text))
        }
    }

    // Initial step: root menu (empty optionId).
    LaunchedEffect(chatId) {
        applyResult(TelegramManager.reportChat(chatId, ByteArray(0), ""))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Report",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Button(onClick = onBack) {
                Text("Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Local block/unblock, independent of the report flow.
        Button(
            onClick = {
                if (isBlocked) {
                    BlockedChatsManager.unblock(context.applicationContext, chatId)
                } else {
                    BlockedChatsManager.block(context.applicationContext, chatId)
                }
                isBlocked = !isBlocked
                Toast.makeText(
                    context,
                    if (isBlocked) "Chat blocked. It's hidden from your Home now." else "Chat unblocked.",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isBlocked) "Unblock this chat" else "Block this chat")
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (val state = uiState) {

            is ReportUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            is ReportUiState.Menu -> {
                Text(
                    text = state.title.ifBlank { "What is wrong with \"$chatTitle\"?" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.options) { index, option ->
                        Button(
                            onClick = {
                                goToStep(option.id, "", pushCurrentMenu = true)
                            },
                            modifier = if (index == 0) {
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(firstOptionFocusRequester)
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        ) {
                            Text(option.text)
                        }
                    }
                }

                if (menuStack.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { uiState = menuStack.removeAt(menuStack.lastIndex) }) {
                        Text("Back")
                    }
                }
            }

            is ReportUiState.TextInput -> {
                Text(
                    text = if (state.isOptional) {
                        "Add more details (optional)"
                    } else {
                        "Please describe the problem"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Explicit IME action required for Fire TV's on-screen keyboard to close.
                BasicTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            goToStep(state.optionId, customText)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(textInputFocusRequester)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(16.dp)
                ) { innerTextField ->
                    if (customText.isEmpty()) {
                        Text(
                            text = "Type here...",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontSize = 18.sp
                        )
                    }
                    innerTextField()
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row {
                    if (state.isOptional) {
                        Button(onClick = { goToStep(state.optionId, "") }) {
                            Text("Skip")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Button(
                        onClick = { goToStep(state.optionId, customText) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send report")
                    }
                }
            }

            is ReportUiState.Done -> {
                LaunchedEffect(Unit) {
                    Toast.makeText(
                        context,
                        "Report sent to Telegram's moderation team.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                Text(
                    text = "Report sent",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Thanks for helping keep Tortugram safe. Telegram's " +
                        "moderation team will review \"$chatTitle\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(primaryActionFocusRequester)
                ) {
                    Text("Done")
                }
            }

            is ReportUiState.Error -> {
                LaunchedEffect(state.message) {
                    Toast.makeText(
                        context,
                        "Couldn't send the report. Try again later.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                Text(
                    text = "Report not sent",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Something went wrong sending the report.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Raw TDLib error detail, shown on screen.
                if (!state.message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row {
                    Button(
                        onClick = {
                            menuStack.clear()
                            goToStep(ByteArray(0), "")
                        },
                        modifier = Modifier.focusRequester(primaryActionFocusRequester)
                    ) {
                        Text("Try again")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(onClick = onBack) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

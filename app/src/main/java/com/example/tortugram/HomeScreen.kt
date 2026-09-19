package com.example.tortugram

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.Chat
import kotlinx.coroutines.delay
import java.io.File

/**
 * Pantalla "Dentro del Home"
 * isaac-maker 2026
 */

@Composable
fun HomeScreen(
    onChatClick: (Long) -> Unit = {},
    onOpenStorage: () -> Unit = {}
) {
    val chats by TelegramManager.chats.collectAsState()
    val folders by TelegramManager.folders.collectAsState()
    val selectedFolderId by TelegramManager.selectedFolderId.collectAsState()

    // FocusRequester para forzar el foco en el primer chat al montar la pantalla
    val firstChatFocusRequester = remember { FocusRequester() }

    // El enfoque inicial solo se ejecuta una vez sin robar el foco al cambiar de pestaña
    LaunchedEffect(Unit) {
        while (chats.isEmpty()) {
            delay(100)
        }
        try {
            firstChatFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    var showExitDialog by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    var showLanguageDialog by remember { mutableStateOf(false) }
    val languages = remember {
        listOf(
            "es" to "Español",
            "en" to "English",
            "pt" to "Português",
            "fr" to "Français",
            "ru" to "Русский",
            "zh" to "中文",
            "hi" to "हिन्दी",
            "ja" to "日本語"
        )
    }

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = stringResource(R.string.exit_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.exit_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            confirmButton = {
                Button(onClick = { activity?.finish() }) {
                    Text(stringResource(R.string.btn_yes))
                }
            },
            dismissButton = {
                Button(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.btn_no))
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = stringResource(R.string.language_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Column {
                    languages.forEach { (tag, nativeName) ->
                        Button(
                            onClick = {
                                showLanguageDialog = false
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(tag)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(nativeName)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(verticalAlignment = Alignment.CenterVertically) {

                if (chats.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.chats_count, chats.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(16.dp))
                }

                Button(onClick = { showLanguageDialog = true }) {
                    Text(stringResource(R.string.btn_language))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = onOpenStorage) {
                    Text(stringResource(R.string.btn_storage))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Barra de pestañas para las carpetas (Chat Folders)
        if (folders.isNotEmpty()) {
            val selectedFolderIndex = folders.indexOfFirst { it.id == selectedFolderId }.coerceAtLeast(0)
            var focusedFolderIndex by remember { mutableStateOf(selectedFolderIndex) }

            TabRow(
                selectedTabIndex = selectedFolderIndex,
                modifier = Modifier.fillMaxWidth(),
                indicator = { tabPositions, doesTabRowHaveFocus ->
                    // Indicador de FOCO: resalta la pestaña por la que vas pasando con el control
                    tabPositions.getOrNull(focusedFolderIndex)?.let { position ->
                        TabRowDefaults.PillIndicator(
                            currentTabPosition = position,
                            doesTabRowHaveFocus = doesTabRowHaveFocus,
                            activeColor = Color(0xFF43A047),
                            inactiveColor = Color.Transparent
                        )
                    }
                    // Indicador de SELECCIÓN: marca la carpeta actualmente elegida
                    tabPositions.getOrNull(selectedFolderIndex)?.let { position ->
                        TabRowDefaults.PillIndicator(
                            currentTabPosition = position,
                            doesTabRowHaveFocus = doesTabRowHaveFocus,
                            activeColor = Color(0xFF1B5E20),
                            inactiveColor = Color(0xFF1B5E20).copy(alpha = 0.6f)
                        )
                    }
                }
            ) {
                folders.forEachIndexed { index, folder ->
                    val isSelected = folder.id == selectedFolderId

                    Tab(
                        selected = isSelected,
                        onFocus = { focusedFolderIndex = index },
                        onClick = { TelegramManager.selectFolder(folder.id) },
                        colors = TabDefaults.pillIndicatorTabColors(
                            contentColor = Color.White.copy(alpha = 0.7f),
                            selectedContentColor = Color.White,
                            focusedContentColor = Color.White,
                            focusedSelectedContentColor = Color.White
                        )
                    ) {
                        Text(
                            text = folder.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (chats.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.loading_folder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
                    ChatCard(
                        chat = chat,
                        onClick = { onChatClick(chat.id) },
                        modifier = if (index == 0) Modifier.focusRequester(firstChatFocusRequester) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatCard(
    chat: Chat,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photoFile = chat.photo?.small
    var localPath by remember(photoFile?.id, photoFile?.local?.path) {
        mutableStateOf(photoFile?.local?.path ?: "")
    }

    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(photoFile?.id) {
        if (localPath.isEmpty() && photoFile != null) {
            TelegramManager.downloadFile(photoFile.id) { path ->
                localPath = path
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(7f / 7f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isFocused) 8.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (localPath.isNotEmpty() && File(localPath).exists()) {
                AsyncImage(
                    model = File(localPath),
                    contentDescription = chat.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.canal_grupo),
                    contentDescription = chat.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = chat.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
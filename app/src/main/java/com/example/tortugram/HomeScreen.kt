package com.example.tortugram

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.User
import kotlinx.coroutines.delay
import java.io.File

/**
 * Pantalla principal: cuadrícula de chats, carpetas y buscador global.
 *
 * isaac-maker 2026
 */

@Composable
fun HomeScreen(
    onChatClick: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val chats by TelegramManager.chats.collectAsState()
    val currentUser by TelegramManager.currentUser.collectAsState()
    val folders by TelegramManager.folders.collectAsState()
    val selectedFolderId by TelegramManager.selectedFolderId.collectAsState()

    // «chats» es la lista sin filtrar; «visibleChats» excluye los chats ocultados localmente.
    val blockedIds by BlockedChatsManager.blockedIds.collectAsState()
    val visibleChats = remember(chats, blockedIds) {
        chats.filterNot { it.id in blockedIds }
    }

    // Solicita el foco en el primer chat al montar la pantalla.
    val firstChatFocusRequester = remember { FocusRequester() }

    // El enfoque inicial se ejecuta una sola vez, sin robar el foco al cambiar de pestaña.
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

    // Buscador global (Chats, Contactos y Global).
    var isSearchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val searchResults by TelegramManager.searchResults.collectAsState()
    val isSearching by TelegramManager.isSearching.collectAsState()
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    fun closeSearch() {
        isSearchActive = false
        searchText = ""
        TelegramManager.clearSearch()
    }

    BackHandler {
        if (isSearchActive) {
            closeSearch()
        } else {
            showExitDialog = true
        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        if (isSearchActive) {
            // Modo búsqueda: campo de texto y botón cancelar.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = searchText,
                    onValueChange = { newValue ->
                        searchText = newValue
                        TelegramManager.searchGlobal(newValue)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) { innerTextField ->
                    if (searchText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontSize = 18.sp
                        )
                    }
                    innerTextField()
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = { closeSearch() }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        } else {
            // Título centrado y botones de la derecha.
            Box(modifier = Modifier.fillMaxWidth()) {
                // Foto de perfil del usuario; abre ProfileScreen.
                ProfileAvatarButton(
                    user = currentUser,
                    onClick = onOpenProfile,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                Text(
                    text = stringResource(R.string.app_home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.align(Alignment.Center)
                )

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    if (chats.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.chats_count, visibleChats.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    // Acceso al buscador global.
                    Button(onClick = { isSearchActive = true }) {
                        Text(stringResource(R.string.btn_search))
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Botón de configuración (emoji como ícono).
                    Button(onClick = onOpenSettings) {
                        Text("🛠")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pestañas de carpetas; se ocultan durante la búsqueda.
        if (!isSearchActive && folders.isNotEmpty()) {
            val selectedFolderIndex = folders.indexOfFirst { it.id == selectedFolderId }.coerceAtLeast(0)
            var focusedFolderIndex by remember { mutableStateOf(selectedFolderIndex) }

            TabRow(
                selectedTabIndex = selectedFolderIndex,
                modifier = Modifier.fillMaxWidth(),
                indicator = { tabPositions, doesTabRowHaveFocus ->
                    // Indicador de foco: resalta la pestaña sobre la que se encuentra el D-pad.
                    tabPositions.getOrNull(focusedFolderIndex)?.let { position ->
                        TabRowDefaults.PillIndicator(
                            currentTabPosition = position,
                            doesTabRowHaveFocus = doesTabRowHaveFocus,
                            activeColor = Color(0xFF43A047),
                            inactiveColor = Color.Transparent
                        )
                    }
                    // Indicador de selección: marca la carpeta activa.
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

        if (isSearchActive) {
            // Resultados del buscador global en tres secciones.
            SearchResultsList(
                results = searchResults,
                isSearching = isSearching,
                hasQuery = searchText.isNotBlank(),
                onChatClick = { chatId ->
                    closeSearch()
                    onChatClick(chatId)
                },
                onContactClick = { user ->
                    TelegramManager.openPrivateChat(user.id) { chatId ->
                        if (chatId != null) {
                            closeSearch()
                            onChatClick(chatId)
                        }
                    }
                }
            )
        } else if (chats.isEmpty()) {
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
                itemsIndexed(visibleChats, key = { _, chat -> chat.id }) { index, chat ->
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

/**
 * Lista de resultados del buscador global en tres secciones: Chats, Contactos y Global. Usa
 * LazyColumn para una navegación predecible con el D-pad.
 */
@Composable
private fun SearchResultsList(
    results: GlobalSearchResults,
    isSearching: Boolean,
    hasQuery: Boolean,
    onChatClick: (Long) -> Unit,
    onContactClick: (User) -> Unit
) {
    if (!hasQuery) {
        // Con el campo vacío no se consulta a TDLib.
        return
    }

    if (isSearching && results.isEmpty) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!isSearching && results.isEmpty) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (results.localChats.isNotEmpty()) {
            item {
                SearchSectionHeader(stringResource(R.string.search_section_chats))
            }
            items(results.localChats, key = { "chat_${it.id}" }) { chat ->
                SearchResultRow(
                    title = chat.title,
                    photoFileId = chat.photo?.small?.id,
                    onClick = { onChatClick(chat.id) }
                )
            }
        }

        if (results.contacts.isNotEmpty()) {
            item {
                SearchSectionHeader(stringResource(R.string.search_section_contacts))
            }
            items(results.contacts, key = { "user_${it.id}" }) { user ->
                SearchResultRow(
                    title = "${user.firstName} ${user.lastName}".trim(),
                    photoFileId = user.profilePhoto?.small?.id,
                    onClick = { onContactClick(user) }
                )
            }
        }

        if (results.globalChats.isNotEmpty()) {
            item {
                SearchSectionHeader(stringResource(R.string.search_section_global))
            }
            items(results.globalChats, key = { "global_${it.id}" }) { chat ->
                SearchResultRow(
                    title = chat.title,
                    photoFileId = chat.photo?.small?.id,
                    onClick = { onChatClick(chat.id) }
                )
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SearchResultRow(
    title: String,
    photoFileId: Int?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isFocused) 1f else 0.4f))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchAvatar(fileId = photoFileId, contentDescription = title)

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Círculo con la foto de perfil del usuario; al pulsarlo abre ProfileScreen.
 */
@Composable
private fun ProfileAvatarButton(
    user: User?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var localPath by remember(user?.profilePhoto?.small?.id) { mutableStateOf("") }

    LaunchedEffect(user?.profilePhoto?.small?.id) {
        val fileId = user?.profilePhoto?.small?.id
        if (fileId != null) {
            TelegramManager.downloadFile(fileId) { path ->
                localPath = path
            }
        }
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (localPath.isNotEmpty() && File(localPath).exists()) {
            AsyncImage(
                model = File(localPath),
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.canal_grupo),
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Avatar pequeño para las filas de resultados de búsqueda.
 */
@Composable
private fun SearchAvatar(fileId: Int?, contentDescription: String) {
    var localPath by remember(fileId) { mutableStateOf("") }

    LaunchedEffect(fileId) {
        if (fileId != null) {
            TelegramManager.downloadFile(fileId) { path ->
                localPath = path
            }
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (localPath.isNotEmpty() && File(localPath).exists()) {
            AsyncImage(
                model = File(localPath),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.canal_grupo),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
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
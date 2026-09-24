package com.example.tortugram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.MessageAudio
import kotlinx.coroutines.delay
import java.io.File

/**
 * Pantalla «Música» de un chat: cuadrícula con paginación que restaura el scroll y el foco al
 * cerrar el reproductor.
 *
 * isaac-maker 2026
 */

// Recuerda la última canción enfocada por chat.
private object MusicGalleryFocusMemory {
    val lastIndexByChat = mutableMapOf<Long, Int>()
}

@Composable
fun MusicScreen(
    chatId: Long,
    audioMessages: List<Pair<Long, MessageAudio>>,
    // Evita que la paginación se detenga cuando un lote no trae canciones nuevas.
    totalMessagesLoaded: Int,
    musicPlayerVisible: Boolean,
    onMusicClick: (audios: List<MessageAudio>, index: Int, title: String) -> Unit
) {

    val gridState = rememberLazyGridState()

    val restoredFocusRequester = remember { FocusRequester() }
    val savedIndex = MusicGalleryFocusMemory.lastIndexByChat[chatId]

    LaunchedEffect(gridState, audioMessages.size, totalMessagesLoaded, chatId) {

        snapshotFlow {

            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            totalItems > 0 && lastVisible >= totalItems - 6

        }.collect { nearEnd ->
            if (nearEnd) {
                TelegramManager.loadMoreMessages(chatId)
            }
        }
    }

    // Al cerrarse el reproductor, restaura el scroll y el foco.
    LaunchedEffect(musicPlayerVisible, audioMessages.size, chatId) {
        if (!musicPlayerVisible && savedIndex != null && savedIndex < audioMessages.size) {
            gridState.scrollToItem(savedIndex)
            delay(50)
            try {
                restoredFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (audioMessages.isEmpty()) {
        EmptyGalleryState(chatId = chatId)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {

        itemsIndexed(items = audioMessages, key = { _, item -> item.first }) { index, item ->

            val fallbackTitle = stringResource(R.string.video_title_fallback)

            MusicThumbnailCard(
                audioContent = item.second,
                modifier = if (index == savedIndex) {
                    Modifier.focusRequester(restoredFocusRequester)
                } else {
                    Modifier
                },
                onClick = {
                    val title = item.second.audio.title
                        .ifEmpty { item.second.audio.fileName }
                        .ifEmpty { item.second.caption.text }
                        .ifEmpty { fallbackTitle }

                    // Guarda el índice antes de navegar.
                    MusicGalleryFocusMemory.lastIndexByChat[chatId] = index

                    onMusicClick(audioMessages.map { it.second }, index, title)
                }
            )
        }
    }
}

@Composable
private fun MusicThumbnailCard(
    audioContent: MessageAudio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cover = audioContent.audio.albumCoverThumbnail?.file

    var localPath by remember(cover?.id) {
        mutableStateOf(cover?.local?.path ?: "")
    }

    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(cover?.id) {
        if (localPath.isEmpty() && cover != null) {
            TelegramManager.downloadFile(cover.id) { path ->
                localPath = path
            }
        }
    }

    val displayTitle = audioContent.audio.title
        .ifEmpty { audioContent.audio.fileName }
        .ifEmpty { stringResource(R.string.video_no_name) }

    val displayPerformer = audioContent.audio.performer

    val accentBlue = Color(0xFFFF5722)

    // Tarjeta con portada y nombre.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 5.dp else 1.dp,
                color = if (isFocused) accentBlue else accentBlue.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        // Portada del álbum, o ícono musical si no tiene.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (localPath.isNotEmpty() && File(localPath).exists()) {
                AsyncImage(
                    model = File(localPath),
                    contentDescription = stringResource(R.string.video_thumbnail_desc),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
            }

            // Duración.
            Text(
                text = formatTime(audioContent.audio.duration * 1000L),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Transparent)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // Título e intérprete.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (displayPerformer.isNotEmpty()) {
                Text(
                    text = displayPerformer,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

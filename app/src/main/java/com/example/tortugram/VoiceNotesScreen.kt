package com.example.tortugram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.MessageVideoNote
import kotlinx.coroutines.delay
import java.io.File

/**
 * "Voice notes" tab: Telegram's round video messages, shown with circular thumbnails. It reuses
 * VideoPlayer.kt and VideoPlayerScreen.kt; this screen only supplies the file and title. Text
 * is defined in code (no strings.xml).
 *
 * isaac-maker 2026
 */

// Remembers the last focused item per chat to restore scroll and focus after the player closes.
private object VideoNoteGalleryFocusMemory {
    val lastIndexByChat = mutableMapOf<Long, Int>()
}

@Composable
fun VoiceNotesScreen(
    chatId: Long,
    videoNoteMessages: List<Pair<Long, MessageVideoNote>>,
    // Prevents pagination from stalling when a page has no new voice notes.
    totalMessagesLoaded: Int,
    videoNotePlayerVisible: Boolean,
    onVideoNoteClick: (videoNotes: List<MessageVideoNote>, index: Int, title: String) -> Unit
) {
    val gridState = rememberLazyGridState()

    val restoredFocusRequester = remember { FocusRequester() }
    val savedIndex = VideoNoteGalleryFocusMemory.lastIndexByChat[chatId]

    LaunchedEffect(gridState, videoNoteMessages.size, totalMessagesLoaded, chatId) {
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

    LaunchedEffect(videoNotePlayerVisible, videoNoteMessages.size, chatId) {
        if (!videoNotePlayerVisible && savedIndex != null && savedIndex < videoNoteMessages.size) {
            gridState.scrollToItem(savedIndex)
            delay(50)
            try {
                restoredFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (videoNoteMessages.isEmpty()) {
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
        itemsIndexed(items = videoNoteMessages, key = { _, item -> item.first }) { index, item ->

            val title = "Voice note ${index + 1}"

            VideoNoteThumbnailCard(
                videoNoteContent = item.second,
                modifier = if (index == savedIndex) {
                    Modifier.focusRequester(restoredFocusRequester)
                } else {
                    Modifier
                },
                onClick = {
                    VideoNoteGalleryFocusMemory.lastIndexByChat[chatId] = index
                    onVideoNoteClick(videoNoteMessages.map { it.second }, index, title)
                }
            )
        }
    }
}

@Composable
private fun VideoNoteThumbnailCard(
    videoNoteContent: MessageVideoNote,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnail = videoNoteContent.videoNote.thumbnail?.file

    var localPath by remember(thumbnail?.id) {
        mutableStateOf(thumbnail?.local?.path ?: "")
    }

    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(thumbnail?.id) {
        if (localPath.isEmpty() && thumbnail != null) {
            TelegramManager.downloadFile(thumbnail.id) { path ->
                localPath = path
            }
        }
    }

    val accentOrange = Color(0xFFFF3E17)

    // Card layout shared with videos, using a circular thumbnail.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 5.dp else 1.dp,
                color = if (isFocused) accentOrange else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (localPath.isNotEmpty() && File(localPath).exists()) {
                AsyncImage(
                    model = File(localPath),
                    contentDescription = "Voice note thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Text(
            text = formatTime(videoNoteContent.videoNote.duration * 1000L),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = "Voice note",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

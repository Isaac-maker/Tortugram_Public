package com.example.tortugram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.MessageAnimation
import java.io.File

@Composable
fun GifScreen(
    chatId: Long,
    gifMessages: List<Pair<Long, MessageAnimation>>,
    // Ver comentario equivalente en ImagenScreen.kt: sin esto, en cuanto una
    // tanda de historial no trae ningún gif nuevo, la paginación se congela.
    totalMessagesLoaded: Int,
    onLoadMore: () -> Unit,
    onGifClick: (index: Int) -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, gifMessages.size, totalMessagesLoaded) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 6
        }.collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }

    if (gifMessages.isEmpty()) {
        EmptyGalleryState(chatId = chatId)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(items = gifMessages, key = { _, item -> item.first }) { index, item ->
            GifThumbnailCard(
                gifContent = item.second,
                onClick = { onGifClick(index) }
            )
        }
    }
}

@Composable
private fun GifThumbnailCard(
    gifContent: MessageAnimation,
    onClick: () -> Unit
) {
    // 1. Intentar con el thumbnail; si no existe, usar la animación directa
    val animationFile = gifContent.animation.thumbnail?.file ?: gifContent.animation.animation

    var localPath by remember(animationFile.id) {
        mutableStateOf(animationFile.local.path)
    }

    var isFocused by remember { mutableStateOf(false) }
    val accentOrange = Color(0xFFFF3E17)

    LaunchedEffect(animationFile.id) {
        if (localPath.isNotEmpty() && File(localPath).exists()) {
            return@LaunchedEffect
        }

        // Se solicita la descarga explícita del archivo
        TelegramManager.downloadFile(animationFile.id) { path ->
            localPath = path
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 6.dp else 0.dp,
                color = if (isFocused) accentOrange else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (localPath.isNotEmpty() && File(localPath).exists()) {
            AsyncImage(
                model = File(localPath),
                contentDescription = stringResource(R.string.gif_thumbnail_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
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
import dev.g000sha256.tdl.dto.MessagePhoto
import java.io.File

/**
 * Galería de imágenes del chat: solo el grid de 5 columnas. El modo
 * pantalla completa vive en ImageViewerScreen.kt, montado como overlay
 * a nivel de ChatScreen (así cubre TODA la pantalla, tabs incluidos).
 *
 * isaac-maker 2026
 */

@Composable
fun ImagenScreen(
    chatId: Long,
    imageMessages: List<Pair<Long, MessagePhoto>>,
    // Total de mensajes cargados del chat (sin filtrar por tipo). Se usa como
    // "llave" del LaunchedEffect de abajo para que siga pidiendo más historial
    // aunque una tanda no traiga NINGUNA imagen nueva — antes, si una tanda no
    // sumaba imágenes, imageMessages.size no cambiaba y snapshotFlow dejaba de
    // emitir (mismo valor "cerca del final" de antes), congelando la paginación.
    totalMessagesLoaded: Int,
    onLoadMore: () -> Unit,
    onImageClick: (index: Int) -> Unit
) {

    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, imageMessages.size, totalMessagesLoaded) {

        snapshotFlow {

            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            totalItems > 0 && lastVisible >= totalItems - 6

        }.collect { nearEnd ->
            if (nearEnd) onLoadMore()
        }
    }

    if (imageMessages.isEmpty()) {
        EmptyGalleryState(chatId = chatId)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {

        itemsIndexed(items = imageMessages, key = { _, item -> item.first }) { index, item ->
            ImageThumbnailCard(
                photoContent = item.second,
                onClick = { onImageClick(index) }
            )
        }
    }
}

@Composable
private fun ImageThumbnailCard(
    photoContent: MessagePhoto,
    onClick: () -> Unit
) {

    // Tamaño más pequeño disponible: suficiente para el grid y más
    // liviano/rápido de descargar que la foto completa.
    val previewSize = remember(photoContent) {
        photoContent.photo.sizes.minByOrNull { it.width }
    }

    var localPath by remember(previewSize?.photo?.id) {
        mutableStateOf(previewSize?.photo?.local?.path ?: "")
    }

    var isFocused by remember { mutableStateOf(false) }
    val accentOrange = Color(0xFFFF3E17)

    LaunchedEffect(previewSize?.photo?.id) {
        if (localPath.isEmpty() && previewSize != null) {
            TelegramManager.downloadFile(previewSize.photo.id) { path ->
                localPath = path
            }
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
            )
    ) {
        if (localPath.isNotEmpty() && File(localPath).exists()) {
            AsyncImage(
                model = File(localPath),
                contentDescription = stringResource(R.string.image_thumbnail_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

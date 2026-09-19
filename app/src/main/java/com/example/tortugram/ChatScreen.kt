package com.example.tortugram

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageVideo
import kotlinx.coroutines.delay
import java.io.File

/**
 * Pantalla "Dentro del Chat/Canal/Grupo"
 * isaac-maker 2026
 */

private enum class GalleryTab { VIDEOS, IMAGENES, GIFS, MUSIC }

// Recuerda, por chat, el índice del último video que se tocó/enfocó. Al ser un
// objeto simple (no un remember de Compose) sobrevive aunque ChatScreen se
// destruya por completo al navegar a VideoPlayerScreen y se vuelva a crear al
// regresar — así podemos restaurar el scroll y el foco en el mismo video.
private object VideoGalleryFocusMemory {
    val lastIndexByChat = mutableMapOf<Long, Int>()
}

@Composable
fun ChatScreen(
    chatId: Long,
    // Ahora entrega también la lista completa de videos del chat y el
    // índice del que se tocó, para poder navegar "anterior"/"siguiente"
    // desde VideoPlayerScreen sin que esa pantalla necesite conocer TDLib.
    onVideoClick: (videos: List<MessageVideo>, index: Int, title: String) -> Unit,
    // Mismo patrón que onVideoClick/videoPlayerVisible pero para música:
    // MusicPlayerScreen también se pinta como overlay en MainActivity.
    onMusicClick: (audios: List<MessageAudio>, index: Int, title: String) -> Unit = { _, _, _ -> },
    // Indica si el reproductor de video está actualmente visible ENCIMA de esta
    // pantalla (como overlay en MainActivity). ChatScreen ya no se destruye al
    // abrir un video, así que usamos este flag para saber cuándo el overlay se
    // cerró y así restaurar el scroll/foco en el video que se estaba viendo.
    videoPlayerVisible: Boolean = false,
    // Igual que videoPlayerVisible, pero para saber cuándo MusicPlayerScreen
    // se cerró y restaurar el scroll/foco en la canción que sonaba.
    musicPlayerVisible: Boolean = false,
    onBack: () -> Unit = {}
) {

    // Antes, el botón "volver" del control remoto no estaba interceptado
    // aquí, así que el sistema lo tomaba como "salir de la app". Ahora lo
    // capturamos para volver al HomeScreen en su lugar.
    BackHandler(onBack = onBack)

    val messages by TelegramManager.messages.collectAsState()

    var selectedTab by remember { mutableStateOf(GalleryTab.VIDEOS) }

    // Estado del visor fullscreen de imágenes/gifs. Se guarda aquí (y NO
    // dentro de ImagenScreen/GifScreen) para poder pintarlo como overlay
    // que cubra TODA la pantalla, tabs y botón "Volver a Home" incluidos.
    var fullscreenImages by remember {
        mutableStateOf<Pair<List<Pair<Long, MessagePhoto>>, Int>?>(null)
    }
    var fullscreenGifs by remember {
        mutableStateOf<Pair<List<Pair<Long, MessageAnimation>>, Int>?>(null)
    }

    LaunchedEffect(chatId) {
        TelegramManager.loadMessages(chatId)
    }

    val videoMessages = remember(messages) {
        messages.mapNotNull { message ->
            (message.content as? MessageVideo)?.let { message.id to it }
        }
    }

    val imageMessages = remember(messages) {
        messages.mapNotNull { message ->
            (message.content as? MessagePhoto)?.let { message.id to it }
        }
    }

    val gifMessages = remember(messages) {
        messages.mapNotNull { message ->
            (message.content as? MessageAnimation)?.let { message.id to it }
        }
    }

    val audioMessages = remember(messages) {
        messages.mapNotNull { message ->
            (message.content as? MessageAudio)?.let { message.id to it }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {

            // Menú lateral fijo (solo íconos): al estar en un Row junto al
            // grid, con el control remoto basta una pulsación IZQUIERDA desde
            // cualquier fila del contenido para llegar a él — antes, con los
            // tabs arriba, tocaba subir con muchos "arriba" para alcanzarlos
            // si uno estaba abajo del todo en la grilla.
            GalleryLateralSidebar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (selectedTab) {

                    GalleryTab.VIDEOS -> VideoGridSection(
                        chatId = chatId,
                        videoMessages = videoMessages,
                        totalMessagesLoaded = messages.size,
                        videoPlayerVisible = videoPlayerVisible,
                        onVideoClick = onVideoClick
                    )

                    GalleryTab.IMAGENES -> ImagenScreen(
                        chatId = chatId,
                        imageMessages = imageMessages,
                        totalMessagesLoaded = messages.size,
                        onLoadMore = { TelegramManager.loadMoreMessages(chatId) },
                        onImageClick = { index ->
                            fullscreenImages = imageMessages to index
                        }
                    )

                    GalleryTab.GIFS -> GifScreen(
                        chatId = chatId,
                        gifMessages = gifMessages,
                        totalMessagesLoaded = messages.size,
                        onLoadMore = { TelegramManager.loadMoreMessages(chatId) },
                        onGifClick = { index ->
                            fullscreenGifs = gifMessages to index
                        }
                    )

                    GalleryTab.MUSIC -> MusicScreen(
                        chatId = chatId,
                        audioMessages = audioMessages,
                        totalMessagesLoaded = messages.size,
                        musicPlayerVisible = musicPlayerVisible,
                        onMusicClick = onMusicClick
                    )
                }
            }
        }

        // Overlays a pantalla completa con Dialog para ocultar las barras y tabs por completo
        fullscreenImages?.let { (images, startIndex) ->
            Dialog(
                onDismissRequest = { fullscreenImages = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                ImageViewerScreen(
                    images = images,
                    startIndex = startIndex,
                    onDismiss = { fullscreenImages = null }
                )
            }
        }

        fullscreenGifs?.let { (gifs, startIndex) ->
            Dialog(
                onDismissRequest = { fullscreenGifs = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                GifViewerScreen(
                    gifs = gifs,
                    startIndex = startIndex,
                    onDismiss = { fullscreenGifs = null }
                )
            }
        }
    }
}

/* ─────────────── Menú lateral fijo: solo íconos ─────────────── */

@Composable
private fun GalleryLateralSidebar(
    selectedTab: GalleryTab,
    onTabSelected: (GalleryTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(Color(0xFF0C0C0C))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GalleryTabIcon(
            icon = Icons.Default.Videocam,
            contentDescription = "Videos",
            selected = selectedTab == GalleryTab.VIDEOS,
            onClick = { onTabSelected(GalleryTab.VIDEOS) }
        )
        GalleryTabIcon(
            icon = Icons.Default.Image,
            contentDescription = "Fotos",
            selected = selectedTab == GalleryTab.IMAGENES,
            onClick = { onTabSelected(GalleryTab.IMAGENES) }
        )
        GalleryTabIcon(
            icon = Icons.Default.GifBox,
            contentDescription = "Gifs",
            selected = selectedTab == GalleryTab.GIFS,
            onClick = { onTabSelected(GalleryTab.GIFS) }
        )
        GalleryTabIcon(
            icon = Icons.Default.MusicNote,
            contentDescription = "Música",
            selected = selectedTab == GalleryTab.MUSIC,
            onClick = { onTabSelected(GalleryTab.MUSIC) }
        )
    }
}

@Composable
private fun GalleryTabIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val accentColor = Color(0xFF2196F3)

    val borderColor = when {
        isFocused -> accentColor
        selected -> Color.White.copy(alpha = 0.6f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (selected || isFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent
            )
            .border(
                width = if (isFocused) 5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) accentColor else Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

/* ─────────────── Sección de videos (SIN CAMBIOS respecto al original) ─────────────── */

@Composable
private fun VideoGridSection(
    chatId: Long,
    videoMessages: List<Pair<Long, MessageVideo>>,
    // Ver comentario en ImagenScreen.kt / GifScreen.kt: sin esto, si una tanda
    // de historial no trae ningún video nuevo, la paginación podría congelarse.
    totalMessagesLoaded: Int,
    videoPlayerVisible: Boolean,
    onVideoClick: (videos: List<MessageVideo>, index: Int, title: String) -> Unit
) {

    val gridState = rememberLazyGridState()

    // FocusRequester del video que se debe re-enfocar al volver del reproductor
    val restoredFocusRequester = remember { FocusRequester() }
    val savedIndex = VideoGalleryFocusMemory.lastIndexByChat[chatId]

    LaunchedEffect(gridState, videoMessages.size, totalMessagesLoaded, chatId) {

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

    // Como ChatScreen ya NO se destruye al abrir el video (ahora es un overlay
    // en MainActivity), lo que dispara la restauración es que el overlay se
    // cierre: cuando "videoPlayerVisible" pasa de true a false, saltamos el
    // scroll al video guardado y le pedimos el foco.
    LaunchedEffect(videoPlayerVisible, videoMessages.size, chatId) {
        if (!videoPlayerVisible && savedIndex != null && savedIndex < videoMessages.size) {
            gridState.scrollToItem(savedIndex)
            delay(50) // deja que la miniatura objetivo se componga antes de pedir foco
            try {
                restoredFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (videoMessages.isEmpty()) {
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

        itemsIndexed(items = videoMessages, key = { _, item -> item.first }) { index, item ->

            val fallbackVideoTitle = stringResource(R.string.video_title_fallback)

            VideoThumbnailCard(
                videoContent = item.second,
                modifier = if (index == savedIndex) {
                    Modifier.focusRequester(restoredFocusRequester)
                } else {
                    Modifier
                },
                onClick = {
                    val title = item.second.video.fileName
                        .ifEmpty { item.second.caption.text }
                        .ifEmpty { fallbackVideoTitle }

                    // Guardamos el índice ANTES de navegar para poder restaurarlo al volver
                    VideoGalleryFocusMemory.lastIndexByChat[chatId] = index

                    onVideoClick(videoMessages.map { it.second }, index, title)
                }
            )
        }
    }
}

@Composable
private fun VideoThumbnailCard(
    videoContent: MessageVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnail = videoContent.video.thumbnail?.file

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

    val displayName = videoContent.video.fileName
        .ifEmpty { videoContent.caption.text }
        .ifEmpty { stringResource(R.string.video_no_name) }

    val accentOrange = Color(0xFFFF3E17)

    // Contenedor principal que agrupa la miniatura y la sección del nombre
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E)) // Fondo oscuro inferior / base
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 5.dp else 1.dp,
                color = if (isFocused) accentOrange else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        // Sección superior: Miniatura y tiempo del video
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 7f)
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
            }

            // Duración del video (esquina inferior derecha)
            Text(
                text = formatTime(videoContent.video.duration * 1000L),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Transparent)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // Sección inferior: Fondo diferenciado con nombre en texto blanco
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
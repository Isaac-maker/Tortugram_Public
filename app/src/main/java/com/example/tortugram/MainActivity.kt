package com.example.tortugram

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.tortugram.ui.theme.TortugramTheme
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageVideo

/**
 * MainActivity"
 * isaac-maker 2026
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Login: SIN CAMBIOS, tal cual funcionaba ---
        TelegramManager.initClient(applicationContext)

        // --- Nuevo: gestor de almacenamiento (no toca login/QR) ---
        StorageManager.init(applicationContext)

        // --- Nuevo: arranca el servidor local que permite el streaming de video ---
        StreamingServer.start()

        setContent {
            TortugramTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        StreamingServer.stop()
    }
}

@Composable
fun AppNavigation() {
    val isLoggedIn by TelegramManager.isLoggedIn.collectAsState()
    val isPasswordRequired by TelegramManager.isPasswordRequired.collectAsState()
    val qrLink by TelegramManager.qrCodeLink.collectAsState()

    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    var selectedVideo by remember { mutableStateOf<Pair<MessageVideo, String>?>(null) }
    // Lista completa de videos del chat actual + índice del que se está
    // viendo, para poder armar los botones de "anterior"/"siguiente" en
    // VideoPlayerScreen sin que esa pantalla tenga que hablar con TDLib.
    var currentVideoList by remember { mutableStateOf<List<MessageVideo>>(emptyList()) }
    var currentVideoIndex by remember { mutableStateOf(0) }

    var selectedMusic by remember { mutableStateOf<Pair<MessageAudio, String>?>(null) }
    // Mismo motivo que currentVideoList/currentVideoIndex, pero para música
    var currentMusicList by remember { mutableStateOf<List<MessageAudio>>(emptyList()) }
    var currentMusicIndex by remember { mutableStateOf(0) }

    var showStorage by remember { mutableStateOf(false) }

    // Mismo criterio de nombre que ya usa ChatScreen: nombre de archivo,
    // si no hay, el caption, y si no hay nada, "Video".
    fun titleFor(video: MessageVideo): String =
        video.video.fileName
            .ifEmpty { video.caption.text }
            .ifEmpty { "Video" }

    // Igual que titleFor(video), pero para audio: título del audio, si no
    // hay, nombre de archivo, si no hay, el caption, y si no hay nada, "Audio".
    fun titleFor(audio: MessageAudio): String =
        audio.audio.title
            .ifEmpty { audio.audio.fileName }
            .ifEmpty { audio.caption.text }
            .ifEmpty { "Audio" }

    // Box en vez de "when" puro: así el reproductor de video puede dibujarse
    // como overlay ENCIMA de ChatScreen sin sacarlo de la composición.
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // --- Login: SIN CAMBIOS ---
            !isLoggedIn && isPasswordRequired -> PasswordScreen()
            !isLoggedIn -> LoginScreen(qrLink = qrLink)

            // --- Nuevo: pantalla de almacenamiento ---
            showStorage -> StorageScreen(
                onBack = { showStorage = false }
            )

            // --- Cuadrícula de videos del canal/grupo ---
            // IMPORTANTE: el reproductor de video ya NO es una rama exclusiva de este
            // "when". Antes, al abrir un video, esta rama de ChatScreen se destruía por
            // completo; al volver, ChatScreen se creaba de cero, recargaba los mensajes
            // desde el principio y perdía el scroll/paginación que habías hecho para
            // llegar a un video antiguo. Ahora ChatScreen se mantiene SIEMPRE compuesto
            // mientras haya un chat seleccionado, y el reproductor se dibuja como un
            // overlay encima (ver más abajo) — así ChatScreen nunca se destruye ni
            // recarga mensajes al cerrar el video.
            selectedChatId != null -> {
                Column {
                    Button(
                        onClick = { selectedChatId = null },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(stringResource(R.string.btn_back_home))
                    }
                    ChatScreen(
                        chatId = selectedChatId!!,
                        videoPlayerVisible = selectedVideo != null,
                        musicPlayerVisible = selectedMusic != null,
                        onVideoClick = { videos, index, title ->
                            currentVideoList = videos
                            currentVideoIndex = index
                            selectedVideo = videos[index] to title
                        },
                        onMusicClick = { audios, index, title ->
                            currentMusicList = audios
                            currentMusicIndex = index
                            selectedMusic = audios[index] to title
                        },
                        onBack = { selectedChatId = null }
                    )
                }
            }

            // --- Cuadrícula de canales/grupos ---
            else -> HomeScreen(
                onChatClick = { id -> selectedChatId = id },
                onOpenStorage = { showStorage = true }
            )
        }

        // --- Reproductor a pantalla completa (streaming), como overlay ---
        // Al dibujarse aparte del "when" de arriba, NO destruye ChatScreen: solo lo
        // cubre visualmente. Al cerrar el video, ChatScreen sigue teniendo el mismo
        // scroll/foco/mensajes cargados que tenía antes de abrirlo.
        selectedVideo?.let { (videoContent, label) ->
            VideoPlayerScreen(
                file = videoContent.video.video,
                title = label,
                onBack = { selectedVideo = null },
                onPrevious = if (currentVideoIndex > 0) {
                    {
                        currentVideoIndex -= 1
                        val prev = currentVideoList[currentVideoIndex]
                        selectedVideo = prev to titleFor(prev)
                    }
                } else null,
                onNext = if (currentVideoIndex < currentVideoList.lastIndex) {
                    {
                        currentVideoIndex += 1
                        val next = currentVideoList[currentVideoIndex]
                        selectedVideo = next to titleFor(next)
                    }
                } else null
            )
        }

        // --- Reproductor de música a pantalla completa, como overlay ---
        // Mismo patrón que selectedVideo de arriba: se dibuja aparte del
        // "when", así no destruye ChatScreen ni pierde su scroll/foco.
        selectedMusic?.let { (audioContent, label) ->
            MusicPlayerScreen(
                file = audioContent.audio.audio,
                title = label,
                performer = audioContent.audio.performer,
                coverFile = audioContent.audio.albumCoverThumbnail?.file,
                mimeType = audioContent.audio.mimeType,
                onBack = { selectedMusic = null },
                onPrevious = if (currentMusicIndex > 0) {
                    {
                        currentMusicIndex -= 1
                        val prev = currentMusicList[currentMusicIndex]
                        selectedMusic = prev to titleFor(prev)
                    }
                } else null,
                onNext = if (currentMusicIndex < currentMusicList.lastIndex) {
                    {
                        currentMusicIndex += 1
                        val next = currentMusicList[currentMusicIndex]
                        selectedMusic = next to titleFor(next)
                    }
                } else null
            )
        }
    }
}

package com.example.tortugram

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.tortugram.ui.theme.TortugramTheme
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote

/**
 * Actividad principal: inicializa los servicios y gestiona la navegación entre pantallas.
 *
 * isaac-maker 2026
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si el usuario ya aceptó «Privacy & Data», TDLib se inicia de inmediato; de lo
        // contrario, tras aceptarlo en ConsentScreen.
        if (ConsentManager.hasAgreed(applicationContext)) {
            TelegramManager.initClient(applicationContext)
        }

        // Gestor de almacenamiento.
        StorageManager.init(applicationContext)

        // Gestor de chats bloqueados localmente.
        BlockedChatsManager.init(applicationContext)

        // Servidor local para el streaming de video.
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
    val context = LocalContext.current
    // Pantalla de consentimiento previa al login.
    var hasAgreedConsent by remember { mutableStateOf(ConsentManager.hasAgreed(context)) }

    val isLoggedIn by TelegramManager.isLoggedIn.collectAsState()
    val isPasswordRequired by TelegramManager.isPasswordRequired.collectAsState()
    val qrLink by TelegramManager.qrCodeLink.collectAsState()

    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    var selectedVideo by remember { mutableStateOf<Pair<MessageVideo, String>?>(null) }
    // Lista de videos del chat actual e índice del video en reproducción, para navegar entre
    // anterior y siguiente.
    var currentVideoList by remember { mutableStateOf<List<MessageVideo>>(emptyList()) }
    var currentVideoIndex by remember { mutableStateOf(0) }

    var selectedMusic by remember { mutableStateOf<Pair<MessageAudio, String>?>(null) }
    // Ídem, para música.
    var currentMusicList by remember { mutableStateOf<List<MessageAudio>>(emptyList()) }
    var currentMusicIndex by remember { mutableStateOf(0) }

    // Notas de voz (MessageVideoNote), con el mismo patrón que los videos.
    var selectedVideoNote by remember { mutableStateOf<Pair<MessageVideoNote, String>?>(null) }
    var currentVideoNoteList by remember { mutableStateOf<List<MessageVideoNote>>(emptyList()) }
    var currentVideoNoteIndex by remember { mutableStateOf(0) }

    var showStorage by remember { mutableStateOf(false) }
    // Pantalla de configuración.
    var showSettings by remember { mutableStateOf(false) }
    // Pantalla About.
    var showAbout by remember { mutableStateOf(false) }
    // Pantalla de perfil.
    var showProfile by remember { mutableStateOf(false) }
    // Pantalla de reporte del chat.
    var showReport by remember { mutableStateOf(false) }

    // Modal de bienvenida: se muestra una vez por cada apertura completa de la app (estado solo
    // en memoria).
    var showWelcomeDialog by remember { mutableStateOf(true) }

    // Título del video: nombre de archivo, descripción o «Video».
    fun titleFor(video: MessageVideo): String =
        video.video.fileName
            .ifEmpty { video.caption.text }
            .ifEmpty { "Video" }

    // Título del audio: título, nombre de archivo, descripción o «Audio».
    fun titleFor(audio: MessageAudio): String =
        audio.audio.title
            .ifEmpty { audio.audio.fileName }
            .ifEmpty { audio.caption.text }
            .ifEmpty { "Audio" }

    // Permite dibujar los reproductores como overlay sin destruir la composición.
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // Consentimiento, mostrado una sola vez.
            !hasAgreedConsent -> ConsentScreen(
                onAgree = {
                    ConsentManager.setAgreed(context)
                    // Inicia TDLib tras la aceptación.
                    TelegramManager.initClient(context.applicationContext)
                    hasAgreedConsent = true
                },
                onDecline = {
                    // Rechazar cierra la app, pues requiere una cuenta de Telegram.
                    (context as? android.app.Activity)?.finish()
                }
            )

            // Login.
            !isLoggedIn && isPasswordRequired -> PasswordScreen()
            !isLoggedIn -> LoginScreen(qrLink = qrLink)

            // Almacenamiento.
            showStorage -> StorageScreen(
                onBack = { showStorage = false }
            )

            // Configuración.
            showSettings -> ConfiguracionScreen(
                onBack = { showSettings = false },
                onOpenStorage = {
                    // Abre Almacenamiento; al volver regresa a Home.
                    showSettings = false
                    showStorage = true
                },
                onOpenAbout = {
                    showSettings = false
                    showAbout = true
                }
            )

            // About.
            showAbout -> AboutScreen(
                onBack = {
                    // Regresa a Configuración.
                    showAbout = false
                    showSettings = true
                }
            )

            // Perfil.
            showProfile -> ProfileScreen(
                onBack = { showProfile = false },
                onLoggedOut = {
                    // Limpia la navegación; el cambio a Login ocurre al pasar isLoggedIn a
                    // false.
                    showProfile = false
                    showSettings = false
                    showStorage = false
                    showAbout = false
                    showReport = false
                    selectedChatId = null
                    selectedVideo = null
                    selectedMusic = null
                }
            )

            // Cuadrícula de videos del canal o grupo. ChatScreen permanece compuesto y el
            // reproductor se dibuja encima, para conservar scroll, paginación y foco.
            selectedChatId != null -> {
                // Título del chat centrado, obtenido de la lista de TelegramManager.
                val chats by TelegramManager.chats.collectAsState()
                val chatTitle = chats.firstOrNull { it.id == selectedChatId }?.title ?: ""

                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = chatTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.align(Alignment.Center)
                        )

                        Button(
                            onClick = {
                                selectedChatId = null
                                showReport = false
                            },
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Text(stringResource(R.string.btn_back_home))
                        }

                        // Botón de reporte; abre ReportScreen como overlay.
                        Button(
                            onClick = { showReport = true },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("!")
                        }
                    }
                    ChatScreen(
                        chatId = selectedChatId!!,
                        videoPlayerVisible = selectedVideo != null,
                        musicPlayerVisible = selectedMusic != null,
                        videoNotePlayerVisible = selectedVideoNote != null,
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
                        onVideoNoteClick = { videoNotes, index, title ->
                            currentVideoNoteList = videoNotes
                            currentVideoNoteIndex = index
                            selectedVideoNote = videoNotes[index] to title
                        },
                        onBack = {
                            selectedChatId = null
                            showReport = false
                        }
                    )
                }
            }

            // Cuadrícula de canales y grupos.
            else -> HomeScreen(
                onChatClick = { id -> selectedChatId = id },
                onOpenSettings = { showSettings = true },
                onOpenProfile = { showProfile = true }
            )
        }

        // Reproductor de video a pantalla completa, como overlay sobre ChatScreen.
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

        // Reproductor de notas de voz, como overlay (reutiliza VideoPlayerScreen).
        selectedVideoNote?.let { (videoNoteContent, label) ->
            VideoPlayerScreen(
                file = videoNoteContent.videoNote.video,
                title = label,
                onBack = { selectedVideoNote = null },
                onPrevious = if (currentVideoNoteIndex > 0) {
                    {
                        currentVideoNoteIndex -= 1
                        val prev = currentVideoNoteList[currentVideoNoteIndex]
                        selectedVideoNote = prev to "Voice note ${currentVideoNoteIndex + 1}"
                    }
                } else null,
                onNext = if (currentVideoNoteIndex < currentVideoNoteList.lastIndex) {
                    {
                        currentVideoNoteIndex += 1
                        val next = currentVideoNoteList[currentVideoNoteIndex]
                        selectedVideoNote = next to "Voice note ${currentVideoNoteIndex + 1}"
                    }
                } else null
            )
        }

        // Reproductor de música a pantalla completa, como overlay.
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

        // Modal de bienvenida, visible solo con sesión iniciada.
        if (showWelcomeDialog && isLoggedIn) {
            WelcomeDialog(onDismiss = { showWelcomeDialog = false })
        }

        // Pantalla de reporte, como overlay sobre el chat.
        if (showReport && selectedChatId != null) {
            val chatsForReport by TelegramManager.chats.collectAsState()
            val reportChatTitle = chatsForReport.firstOrNull { it.id == selectedChatId }?.title ?: ""

            ReportScreen(
                chatId = selectedChatId!!,
                chatTitle = reportChatTitle,
                onBack = { showReport = false }
            )
        }
    }
}

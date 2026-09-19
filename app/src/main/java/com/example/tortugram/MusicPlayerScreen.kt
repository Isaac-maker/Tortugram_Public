package com.example.tortugram

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.delay
import java.io.File as JavaFile

/**
 * Reproductor de música a pantalla completa. Calcado de VideoPlayerScreen.kt
 * (mismo manejo de foco/teclas del control remoto de Fire TV, ya con el fix
 * de foco aplicado) pero mostrando la portada del álbum en vez del video.
 *
 * Usa AudioPlayer.kt (mismo StreamingServer que VideoPlayer.kt, pero sin
 * PlayerView y con el mimeType real del audio) en vez de VideoPlayer, que
 * forzaba MimeTypes.VIDEO_MP4 y rompía cualquier audio que no fuera mp4.
 * isaac-maker 2026
 */

@Composable
fun MusicPlayerScreen(
    file: File,
    title: String,
    performer: String = "",
    coverFile: File? = null,
    // mimeType real del audio (audio.mimeType en TDLib). Se lo pasamos a
    // AudioPlayer para que ExoPlayer no intente decodificarlo como mp4.
    mimeType: String? = null,
    onBack: () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null
) {
    val state = rememberVideoPlayerState()
    var controlsVisible by remember { mutableStateOf(true) }
    var userActivityTrigger by remember { mutableStateOf(0L) }

    val playButtonFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    val blueThemeColor = Color(0xFF2196F3)

    var coverLocalPath by remember(coverFile?.id) {
        mutableStateOf(coverFile?.local?.path ?: "")
    }

    LaunchedEffect(coverFile?.id) {
        if (coverLocalPath.isEmpty() && coverFile != null) {
            TelegramManager.downloadFile(coverFile.id) { path ->
                coverLocalPath = path
            }
        }
    }

    BackHandler {
        if (controlsVisible) {
            controlsVisible = false
        } else {
            onBack()
        }
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            playButtonFocusRequester.requestFocus()
        } else {
            // Mismo fix que en VideoPlayerScreen: si nadie queda enfocado al
            // ocultar los controles, las teclas del control remoto dejan de
            // llegar al onKeyEvent del Box raíz.
            rootFocusRequester.requestFocus()
        }
    }

    // A diferencia del video, en música normalmente queremos que los
    // controles se queden visibles (es lo que se está mirando). Se ocultan
    // igual tras inactividad para dejar ver la portada completa.
    LaunchedEffect(controlsVisible, state.isPlaying, userActivityTrigger) {
        if (controlsVisible && state.isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    fun showControls() {
        controlsVisible = true
        userActivityTrigger = System.currentTimeMillis()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            state.playPause()
                            showControls()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                            state.forward()
                            showControls()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                            state.rewind()
                            showControls()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            showControls()
                            onNext?.invoke()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            showControls()
                            onPrevious?.invoke()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!controlsVisible) {
                                showControls()
                                true
                            } else {
                                showControls()
                                false
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        /* Fondo: portada difuminada a pantalla completa */
        if (coverLocalPath.isNotEmpty() && JavaFile(coverLocalPath).exists()) {
            AsyncImage(
                model = JavaFile(coverLocalPath),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp),
                contentScale = ContentScale.Crop
            )
            // Oscurece la portada difuminada para que el texto/controles resalten
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }

        /* Motor de audio: streaming real, sin superficie de video */
        key(file.id) {
            AudioPlayer(
                file = file,
                state = state,
                mimeType = mimeType
            )
        }

        // Capa clickeable transparente cuando los controles están ocultos
        if (!controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showControls()
                    }
            )
        }

        /* Portada centrada + título/intérprete */
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1C1E)),
                contentAlignment = Alignment.Center
            ) {
                if (coverLocalPath.isNotEmpty() && JavaFile(coverLocalPath).exists()) {
                    AsyncImage(
                        model = JavaFile(coverLocalPath),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            if (performer.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = performer,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        /* Overlay de controles (barra superior de cierre + barra inferior) */
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvIconButton(
                        onClick = onBack,
                        icon = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        activeColor = blueThemeColor
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Slider(
                        value = if (state.duration > 0) {
                            (state.currentPosition.toFloat() / state.duration.toFloat())
                                .coerceIn(0f, 1f)
                        } else 0f,
                        onValueChange = { fraction ->
                            if (state.duration > 0) {
                                state.seekTo((fraction * state.duration).toLong())
                            }
                            showControls()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = blueThemeColor,
                            activeTrackColor = blueThemeColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvIconButton(
                            onClick = {
                                showControls()
                                onPrevious?.invoke()
                            },
                            icon = Icons.Default.SkipPrevious,
                            contentDescription = "Canción anterior",
                            enabled = onPrevious != null,
                            activeColor = blueThemeColor
                        )

                        TvIconButton(
                            onClick = {
                                showControls()
                                state.rewind()
                            },
                            icon = Icons.Default.FastRewind,
                            contentDescription = "Retroceder 10s",
                            activeColor = blueThemeColor
                        )

                        TvIconButton(
                            onClick = {
                                showControls()
                                state.playPause()
                            },
                            icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                            modifier = Modifier.focusRequester(playButtonFocusRequester),
                            activeColor = blueThemeColor,
                            iconSize = 36.dp
                        )

                        TvIconButton(
                            onClick = {
                                showControls()
                                state.forward()
                            },
                            icon = Icons.Default.FastForward,
                            contentDescription = "Avanzar 10s",
                            activeColor = blueThemeColor
                        )

                        TvIconButton(
                            onClick = {
                                showControls()
                                onNext?.invoke()
                            },
                            icon = Icons.Default.SkipNext,
                            contentDescription = "Siguiente canción",
                            enabled = onNext != null,
                            activeColor = blueThemeColor
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(state.currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = formatTime(state.duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        if (state.isBuffering) {
            CircularProgressIndicator(
                color = blueThemeColor,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

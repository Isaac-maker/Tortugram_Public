package com.example.tortugram

import android.app.Activity
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.delay

/**
 * Reproductor de audio para MusicPlayerScreen. Comparte StreamingServer y VideoPlayerState con
 * VideoPlayer.kt, pero sin PlayerView y con el mimeType real del archivo.
 *
 * isaac-maker 2026
 */
@OptIn(UnstableApi::class)
@Composable
fun AudioPlayer(
    file: File,
    state: VideoPlayerState,
    // MimeType real del audio; si está vacío, ExoPlayer lo detecta a partir de la URL.
    mimeType: String? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val streamUrl = remember(file.id) {
        StreamingServer.urlFor(file.id, file.size)
    }

    val exoPlayer = remember(file.id) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItemBuilder = MediaItem.Builder().setUri(streamUrl)
            if (!mimeType.isNullOrBlank()) {
                mediaItemBuilder.setMimeType(mimeType)
            }
            setMediaItem(mediaItemBuilder.build())
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        state.exoPlayer = exoPlayer
        val activity = view.context as? Activity

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    state.duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                state.isPlaying = playing
                // Mantiene la pantalla encendida solo durante la reproducción.
                if (playing) {
                    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("AudioPlayer", "Error de reproducción", error)
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            state.exoPlayer = null
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            state.currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }
}

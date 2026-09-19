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
 * Reproductor de audio puro para MusicPlayerScreen: mismo StreamingServer
 * que VideoPlayer.kt y el mismo VideoPlayerState (es genérico, solo envuelve
 * un ExoPlayer), pero SIN inflar ningún PlayerView -no hace falta superficie
 * para audio- y usando el mimeType real del archivo en vez de forzar
 * VIDEO_MP4 como hace VideoPlayer.kt (eso rompía la reproducción para
 * cualquier audio que no fuera literalmente un mp4).
 * isaac-maker 2026
 */
@OptIn(UnstableApi::class)
@Composable
fun AudioPlayer(
    file: File,
    state: VideoPlayerState,
    // mimeType real del audio (viene de audio.mimeType en TDLib, ej.
    // "audio/mpeg", "audio/ogg"). Si llega vacío, se deja que ExoPlayer lo
    // detecte solo a partir de la URL/extensión servida por StreamingServer.
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
                // Igual que en VideoPlayer: pantalla encendida solo mientras suena
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

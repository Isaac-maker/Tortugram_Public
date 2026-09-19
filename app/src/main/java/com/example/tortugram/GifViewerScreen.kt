package com.example.tortugram

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.g000sha256.tdl.dto.MessageAnimation
import kotlinx.coroutines.launch

/**
 * Visor de GIFs a PANTALLA COMPLETA. Mismo patrón que
 * ImageViewerScreen.kt: overlay a nivel de ChatScreen, swipe + botones
 * anterior/siguiente + D-pad.
 *
 * isaac-maker 2026
 */

@Composable
fun GifViewerScreen(
    gifs: List<Pair<Long, MessageAnimation>>,
    startIndex: Int,
    onDismiss: () -> Unit
) {

    BackHandler(onBack = onDismiss)

    val pagerState = rememberPagerState(initialPage = startIndex) { gifs.size }
    val coroutineScope = rememberCoroutineScope()

    fun goPrevious() {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }

    fun goNext() {
        if (pagerState.currentPage < gifs.size - 1) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { goPrevious(); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { goNext(); true }
                        else -> false
                    }
                } else false
            }
    ) {

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            FullscreenGifPage(gifContent = gifs[page].second)
        }

        TvIconButton(
            onClick = onDismiss,
            icon = Icons.Default.Close,
            contentDescription = stringResource(R.string.close_desc),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
        )

        if (pagerState.currentPage > 0) {
            TvIconButton(
                onClick = ::goPrevious,
                icon = Icons.Default.ChevronLeft,
                contentDescription = "Anterior",
                iconSize = 40.dp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp)
            )
        }

        if (pagerState.currentPage < gifs.size - 1) {
            TvIconButton(
                onClick = ::goNext,
                icon = Icons.Default.ChevronRight,
                contentDescription = "Siguiente",
                iconSize = 40.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun FullscreenGifPage(gifContent: MessageAnimation) {

    // Un "gif" de Telegram es en realidad un video mp4 corto y silencioso
    // (mismo tipo File que un MessageVideo), así que lo reproducimos con el
    // mismo VideoPlayer de VideoPlayerScreen, pero sin controles y con
    // loop = true para que se vea y se comporte como un gif real.
    val animationFile = gifContent.animation.animation
    val state = rememberVideoPlayerState()

    Box(modifier = Modifier.fillMaxSize()) {
        key(animationFile.id) {
            VideoPlayer(
                file = animationFile,
                state = state,
                modifier = Modifier.fillMaxSize(),
                loop = true
            )
        }

        if (state.isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

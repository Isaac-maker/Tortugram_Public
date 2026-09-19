package com.example.tortugram

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import dev.g000sha256.tdl.dto.MessagePhoto
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ImageViewerScreen(
    images: List<Pair<Long, MessagePhoto>>,
    startIndex: Int,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    fun goPrevious() {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }

    fun goNext() {
        if (pagerState.currentPage < images.size - 1) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            goPrevious()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            goNext()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            FullscreenImagePage(photoContent = images[page].second)
        }
    }
}

@Composable
private fun FullscreenImagePage(photoContent: MessagePhoto) {
    val fullSize = remember(photoContent) {
        photoContent.photo.sizes.maxByOrNull { it.width }
    }

    var localPath by remember(fullSize?.photo?.id) {
        mutableStateOf(fullSize?.photo?.local?.path ?: "")
    }

    LaunchedEffect(fullSize?.photo?.id) {
        if (localPath.isEmpty() && fullSize != null) {
            TelegramManager.downloadFile(fullSize.photo.id) { path ->
                localPath = path
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (localPath.isNotEmpty() && File(localPath).exists()) {
            AsyncImage(
                model = File(localPath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
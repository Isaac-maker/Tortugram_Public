package com.example.tortugram

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Estado de lista vacía compartido por las pestañas de videos, fotos, GIFs y música. Mientras
 * el historial no esté completo solicita páginas adicionales; si se agota, muestra un mensaje.
 *
 * isaac-maker 2026
 */
@Composable
fun EmptyGalleryState(chatId: Long) {

    val isLoading by TelegramManager.isLoading.collectAsState()
    val endReachedChatId by TelegramManager.endReachedChatId.collectAsState()
    val endReached = endReachedChatId == chatId

    // Al terminar cada página solicita la siguiente, hasta hallar contenido o llegar al final.
    LaunchedEffect(chatId, isLoading, endReached) {
        if (!endReached && !isLoading) {
            TelegramManager.loadMoreMessages(chatId)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (endReached) {
            Text(
                text = "There are no files of this type here.",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

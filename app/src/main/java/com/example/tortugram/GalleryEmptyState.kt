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
 * Estado "lista vacía" compartido por videos / fotos / gifs (y música si quieres).
 *
 * Se compone SOLO cuando la lista filtrada de la pestaña está vacía. Mientras
 * TDLib no haya llegado al inicio del historial, sigue pidiendo páginas por su
 * cuenta (antes solo se pedían al hacer scroll, y sin items no hay scroll:
 * por eso había que cambiar de pestaña o salir y volver a entrar).
 * Si el historial se agotó y sigue vacía, muestra el mensaje en inglés.
 *
 * isaac-maker 2026
 */
@Composable
fun EmptyGalleryState(chatId: Long) {

    val isLoading by TelegramManager.isLoading.collectAsState()
    val endReachedChatId by TelegramManager.endReachedChatId.collectAsState()
    val endReached = endReachedChatId == chatId

    // Al terminar cada página, isLoading pasa a false y este efecto se
    // reinicia pidiendo la siguiente, hasta encontrar algo o llegar al final.
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

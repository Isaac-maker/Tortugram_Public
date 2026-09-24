package com.example.tortugram

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Modal de bienvenida, mostrado una vez por cada apertura completa de la app (estado en
 * memoria, gestionado desde MainActivity). Solo se cierra con el botón «ok».
 *
 * isaac-maker 2026
 */
@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Solo se cierra con el botón "ok" */ },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Welcome to Tortugram!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        text = {
            Text(
                text = "This app is 100% free and open-source, built to give " +
                    "you the best experience without restrictions.\n" +
                    "Enjoy, and thanks for being part of this project! \n\n " +
                                         "Isaac-maker"
                ,

                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("ok")
            }
        }
    )
}

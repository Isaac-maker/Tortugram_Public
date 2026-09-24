package com.example.tortugram

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Pantalla «About», accesible desde Configuración: descripción de la app y enlaces de interés
 * (sitio web, política de privacidad, GitHub, eliminación de datos y reporte de errores).
 *
 * Los textos se definen en el código, sin usar strings.xml.
 *
 * isaac-maker 2026
 */
private data class AboutLink(
    val label: String,
    val url: String
)

private val ABOUT_LINKS = listOf(
    AboutLink("Website", "https://isaac-maker.github.io/Tortugram_Public/"),
    AboutLink("Privacy Policy", "https://isaac-maker.github.io/Tortugram_Public/privacy-policy.html"),
    AboutLink("GitHub", "https://github.com/Isaac-maker/Tortugram_Public"),
    AboutLink("Delete your data", "https://isaac-maker.github.io/Tortugram_Public/data-deletion.html"),
    AboutLink("Report a bug or suggest a feature", "https://github.com/Isaac-maker/Tortugram_Public/issues")
)

@Composable
fun AboutScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current

    fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // Evita fallos si no hay navegador instalado.
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "About",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Button(onClick = onBack) {
                Text("Back")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Tortugram is a free, open-source Telegram client, built " +
                "to browse and stream videos, photos, gifs and music from " +
                "your chats without downloading full files first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ABOUT_LINKS) { link ->
                Button(
                    onClick = { openLink(link.url) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(link.label)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Created by isaac-maker",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

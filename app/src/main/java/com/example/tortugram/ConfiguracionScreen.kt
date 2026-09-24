package com.example.tortugram

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Pantalla de Configuración: selección de idioma y acceso a Almacenamiento y About.
 *
 * isaac-maker 2026
 */
@Composable
fun ConfiguracionScreen(
    onBack: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenAbout: () -> Unit = {}
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    val languages = remember {
        listOf(
            "es" to "Español",
            "en" to "English",
            "pt" to "Português",
            "fr" to "Français",
            "ru" to "Русский",
            "zh" to "中文",
            "hi" to "हिन्दी",
            "ja" to "日本語"
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = stringResource(R.string.language_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Column {
                    languages.forEach { (tag, nativeName) ->
                        Button(
                            onClick = {
                                showLanguageDialog = false
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(tag)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(nativeName)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
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
            // Emoji como ícono, sin depender de material-icons.
            Text(
                text = "🛠",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Button(onClick = onBack) {
                Text(stringResource(R.string.btn_back))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { showLanguageDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_language))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onOpenStorage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_storage))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Acceso a AboutScreen.
        Button(
            onClick = onOpenAbout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("About")
        }
    }
}

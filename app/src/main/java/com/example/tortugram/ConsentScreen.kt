package com.example.tortugram

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text

/**
 * Pantalla «Privacy & Data», mostrada una sola vez antes del login. Diseñada para D-pad: foco
 * inicial en el botón principal y Atrás equivale a «Decline & Exit».
 *
 * isaac-maker 2026
 */
private const val PRIVACY_POLICY_URL =
    "https://isaac-maker.github.io/Tortugram_Public/privacy-policy.html"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ConsentScreen(
    onAgree: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current

    // Atrás (botón físico) equivale a «Decline & Exit».
    BackHandler(enabled = true) {
        onDecline()
    }

    // Foco inicial en el botón principal al mostrar la pantalla.
    val agreeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        agreeFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 620.dp)
        ) {
            Text(
                text = "Privacy & Data",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Tortugram is an unofficial client that connects directly to " +
                    "Telegram using your Telegram account.",
                color = Color.White,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            ConsentBullet("Login is done with Telegram itself, via QR code or your Telegram password (2FA).")
            ConsentBullet("Tortugram does not run its own servers and does not collect your messages or credentials.")
            ConsentBullet("You can read the full Privacy Policy before deciding.")

            Spacer(modifier = Modifier.height(16.dp))

            // Controles en fila para navegar con el D-pad sin desplazamiento vertical.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("View Policy", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Decline & Exit", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF2AABEE), Color(0xFF229ED9))
                        )
                    )
            ) {
                Button(
                    onClick = onAgree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(agreeFocusRequester)
                ) {
                    Text(
                        text = "I Agree & Continue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConsentBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = "•  ", color = Color.White, fontSize = 13.sp)
        Text(text = text, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
    }
}

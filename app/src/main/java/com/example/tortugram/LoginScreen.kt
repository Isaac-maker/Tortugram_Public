package com.example.tortugram

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Login screen / QR code screen
 * isaac-maker 2026
 */
@Composable
fun LoginScreen(
    qrLink: String? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Tortugram",
                style = MaterialTheme.typography.headlineLarge,
                color = ComposeColor.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (qrLink != null) {
                Text(
                    text = "Scan this QR code with Telegram",
                    style = MaterialTheme.typography.titleMedium,
                    color = ComposeColor.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                val qrBitmap = remember(qrLink) {
                    generateQrBitmap(qrLink, size = 600)
                }

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ComposeColor.White)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Login QR code",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ComposeColor.White.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "This code can only be scanned from inside the Telegram app — your phone or tablet's regular camera will not work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ComposeColor.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "1. Open Telegram on your phone or tablet",
                        style = MaterialTheme.typography.bodySmall,
                        color = ComposeColor.White
                    )
                    Text(
                        text = "2. Go to Settings > Devices > Link Desktop Device",
                        style = MaterialTheme.typography.bodySmall,
                        color = ComposeColor.White
                    )
                    Text(
                        text = "3. Point the camera that opens inside Telegram at this QR code",
                        style = MaterialTheme.typography.bodySmall,
                        color = ComposeColor.White
                    )
                }
            } else {
                CircularProgressIndicator(color = ComposeColor.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Connecting to Telegram...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ComposeColor.White
                )
            }
        }
    }
}

@Composable
fun PasswordScreen() {
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(24.dp)
        ) {
            Text(
                text = "Two-Factor Verification",
                style = MaterialTheme.typography.headlineMedium,
                color = ComposeColor.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter your Telegram password",
                style = MaterialTheme.typography.bodyMedium,
                color = ComposeColor.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    TelegramManager.checkPassword(password) { success ->
                        isLoading = false
                        if (!success) {
                            errorMessage = "Incorrect password"
                        }
                    }
                },
                enabled = password.isNotEmpty() && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Log In")
                }
            }
        }
    }
}

fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}
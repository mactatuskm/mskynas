@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.mskynas.tv.data.Plex
import kotlinx.coroutines.delay

/** Inicio de sesión con código: nada de teclear contraseñas con el control remoto. */
@Composable
fun LoginScreen(onDone: () -> Unit) {
    var code by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val pin = runCatching { Plex.createPin() }.getOrNull()
            if (pin == null) {
                failure = "No pude conectar con plex.tv. Reintentando…"
                delay(5000)
            } else {
                failure = null
                code = pin.code
                val until = System.currentTimeMillis() + 14 * 60_000L
                var token: String? = null
                while (token.isNullOrBlank() && System.currentTimeMillis() < until) {
                    delay(2000)
                    token = runCatching { Plex.checkPin(pin.id).authToken }.getOrNull()
                }
                if (!token.isNullOrBlank()) {
                    Plex.session.userToken = token
                    onDone()
                    return@LaunchedEffect
                }
                // El código expiró: se genera uno nuevo.
            }
        }
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("mskynas", color = Accent, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        Text("En tu teléfono o computadora abre", color = Dim, fontSize = 20.sp)
        Text("plex.tv/link", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(28.dp))
        Text(
            code ?: "····",
            color = Color.White,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 14.sp,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            failure ?: "Escribe este código; la pantalla avanzará sola.",
            color = Dim,
            fontSize = 16.sp,
        )
    }
}

@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.darkColorScheme

// ---- Paleta (referencia: Silo) ----
// Neutra: el color lo pone el arte de cada título (el fondo se tiñe con él).
val Bg = Color(0xFF0D0D10)          // Fondo: casi negro neutro
val Panel = Color(0xFF1B1B20)       // Tarjetas y paneles
val PanelHi = Color(0xFF26262D)     // Paneles enfocados o elevados
val Accent = Color(0xFF7B61FF)      // Morado: avatar, cursor y detalles mínimos
val OnAccent = Color.White
val Dim = Color(0xFFA3A3AD)         // Texto secundario
val Faint = Color(0xFF6E6E78)       // Texto terciario
val Gold = Color(0xFFF5C518)        // Estrella de calificación
val Chip = Color.White.copy(alpha = 0.12f)   // Píldoras y botones sin foco

// ---- Medidas (capturas de referencia a 1080p ÷ 2 ≈ dp) ----
val ContentStart = 46.dp            // Margen izquierdo del contenido
val TopBarHeight = 64.dp            // Alto de la barra superior

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = OnAccent,
            background = Bg,
            onBackground = Color.White,
            surface = Panel,
            onSurface = Color.White,
            surfaceVariant = Panel,
            onSurfaceVariant = Dim,
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            colors = SurfaceDefaults.colors(containerColor = Bg, contentColor = Color.White),
        ) {
            content()
        }
    }
}

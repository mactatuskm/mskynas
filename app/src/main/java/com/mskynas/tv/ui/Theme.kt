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

// ---- Paleta ----
// Cambia estos valores para ajustar el look de toda la app.
val Bg = Color(0xFF0A0A12)          // Fondo: casi negro, ligeramente azulado
val Panel = Color(0xFF171826)       // Tarjetas y paneles
val PanelHi = Color(0xFF222438)     // Paneles enfocados o elevados
val Accent = Color(0xFF7B61FF)      // Morado de acento
val OnAccent = Color.White
val Dim = Color(0xFFA4A7BA)         // Texto secundario
val Faint = Color(0xFF6C6F85)       // Texto terciario
val Gold = Color(0xFFF5C518)        // Estrella de calificación

// ---- Medidas ----
val RailCollapsed = 72.dp           // Ancho de la barra lateral cerrada
val ContentStart = 96.dp            // Margen izquierdo del contenido (barra + aire)

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

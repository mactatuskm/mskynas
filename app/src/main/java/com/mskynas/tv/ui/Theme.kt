@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)

package com.mskynas.tv.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
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

/**
 * Desplazamiento al mover el foco (como Plex y Silo): el elemento enfocado siempre queda en el mismo
 * lugar, a la altura del margen izquierdo en las filas y arriba en las listas, y la fila se desliza
 * con un resorte suave. El resorte conserva la velocidad si mantienes presionado el control, así que
 * el movimiento no se reinicia a tirones en cada paso.
 */
private class PivotScroll(private val leadPx: Float) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Si el elemento no cabe (más alto que la lista), solo se alinea su inicio.
        val lead = if (size + leadPx > containerSize) 0f else leadPx
        return offset - lead
    }
}

/** Desplazamiento normal: solo se mueve lo necesario para que se vea (p. ej., el detalle). */
private val MinimalScroll = object : BringIntoViewSpec {}

@Composable
private fun rememberPivot(): BringIntoViewSpec {
    val density = LocalDensity.current
    return remember(density) { PivotScroll(with(density) { ContentStart.toPx() }) }
}

/** Para listas que no deben saltar al enfocar (el detalle, donde arriba va la información). */
@Composable
fun MinimalScrolling(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll, content = content)

/** Vuelve al desplazamiento con foco fijo (filas dentro de una lista con MinimalScrolling). */
@Composable
fun PivotScrolling(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(), content = content)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val pivot = rememberPivot()
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
            CompositionLocalProvider(LocalBringIntoViewSpec provides pivot, content = content)
        }
    }
}

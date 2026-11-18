@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mskynas.tv.data.Cache

private val TileShape = RoundedCornerShape(8.dp)

/** Pide el foco una vez, cuando el elemento aparece en pantalla. */
@Composable
fun Modifier.initialFocus(enabled: Boolean): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(enabled) {
        if (enabled) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    return this.focusRequester(requester)
}

/** Recuerda qué elemento estaba enfocado para volver ahí al regresar. */
fun Modifier.trackFocus(screen: String, key: String): Modifier =
    onFocusChanged { if (it.isFocused || it.hasFocus) Cache.lastFocus[screen] = key }

/** Tarjeta base (estilo Silo): al enfocarse crece y se marca con un borde blanco fino. */
@Composable
fun TileCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(shape = TileShape),
        colors = CardDefaults.colors(containerColor = Panel),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = TileShape)
        ),
        // Sin sombra al enfocar: en Fire TV dibujar sombras grandes en cada tarjeta cuesta cuadros.
        scale = CardDefaults.scale(focusedScale = 1.07f),
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}

@Composable
fun PosterCard(
    title: String,
    subtitle: String?,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspect: Float = 2f / 3f,
    progress: Float? = null,
    watched: Boolean = false,
    badge: Int = 0,
    focus: Boolean = false,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        TileCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .initialFocus(focus)
                .onFocusChanged {
                    focused = it.isFocused || it.hasFocus
                    if (focused) onFocused()
                },
        ) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    title,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                    color = Dim,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (progress != null) {
                ProgressBar(
                    progress,
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp, vertical = 0.dp),
                )
            }
            when {
                watched -> CircleBadge(Modifier.align(Alignment.TopEnd)) {
                    Icon(AppIcons.Check, contentDescription = "Visto", modifier = Modifier.size(12.dp), tint = Bg)
                }
                badge > 0 -> CircleBadge(Modifier.align(Alignment.TopEnd)) {
                    Text(
                        if (badge > 99) "99+" else badge.toString(),
                        color = Bg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = Dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CircleBadge(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .padding(6.dp)
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(Color.White, CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(3.dp)
            .background(Color.White.copy(alpha = 0.22f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                .fillMaxHeight()
                .background(Color.White)
        )
    }
}

/** Fila con título (y un ícono opcional, como ▶ en Continuar viendo) y desplazamiento horizontal. */
@Composable
fun Shelf(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier) {
        Row(Modifier.padding(start = ContentStart), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier.size(17.dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(11.dp), tint = Bg)
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(start = ContentStart, end = 48.dp, top = 11.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(19.dp),
            content = content,
        )
    }
}

/**
 * Botón en forma de píldora. Enfocado: blanco con texto oscuro.
 * `highlighted` lo pinta en morado cuando no tiene el foco (acción principal o pestaña activa).
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (highlighted) Color.White.copy(alpha = 0.22f) else Chip,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Bg,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Línea de datos: ★ 7.2 · 2011 · 2 h 11 min · R · Acción, Thriller */
@Composable
fun MetaLine(score: Double?, parts: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (score != null) {
            Icon(AppIcons.Star, contentDescription = null, modifier = Modifier.size(17.dp), tint = Gold)
            Spacer(Modifier.width(5.dp))
            Text("%.1f".format(score), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (parts.isNotEmpty()) Text("   ·   ", color = Dim, fontSize = 15.sp)
        }
        Text(
            parts.joinToString("   ·   "),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

class Action(val label: String, val onClick: () -> Unit)

@Composable
fun MessageBox(message: String, actions: List<Action> = emptyList()) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                actions.forEachIndexed { i, action ->
                    ActionButton(
                        action.label,
                        action.onClick,
                        modifier = Modifier.initialFocus(i == 0),
                        highlighted = i == 0,
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Cargando…", color = Dim, fontSize = 18.sp)
    }
}

@Composable
fun ToastPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .padding(32.dp)
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(50))
            .padding(horizontal = 22.dp, vertical = 12.dp),
        color = Color.White,
        fontSize = 16.sp,
    )
}

/** 2h 11m / 42m */
fun fmtDuration(ms: Long?): String {
    if (ms == null || ms <= 0) return ""
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

fun fmtClock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mskynas.tv.data.Cache

private val TileShape = RoundedCornerShape(10.dp)

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
            focusedBorder = Border(border = BorderStroke(3.dp, Accent), shape = TileShape)
        ),
        scale = CardDefaults.scale(focusedScale = 1.05f),
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
    focus: Boolean = false,
    onFocused: () -> Unit = {},
) {
    Column(modifier) {
        TileCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .initialFocus(focus)
                .onFocusChanged { if (it.isFocused || it.hasFocus) onFocused() },
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
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Accent))
                }
            }
            if (watched) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .background(Accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Shelf(title: String, content: LazyListScope.() -> Unit) {
    Column {
        Text(
            title,
            modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = if (highlighted) Accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.07f),
            contentColor = if (highlighted) Accent else Color.White,
            focusedContainerColor = Accent,
            focusedContentColor = OnAccent,
        ),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(50))
            .padding(horizontal = 22.dp, vertical = 12.dp),
        color = Color.White,
        fontSize = 16.sp,
    )
}

fun fmtDuration(ms: Long?): String {
    if (ms == null || ms <= 0) return ""
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

fun fmtClock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

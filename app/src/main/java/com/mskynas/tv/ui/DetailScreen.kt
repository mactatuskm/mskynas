@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.episodeLabel
import com.mskynas.tv.data.isFullyWatched
import com.mskynas.tv.data.progress
import java.io.IOException

@Composable
fun DetailScreen(ratingKey: String, nav: NavController) {
    var item by remember { mutableStateOf<Item?>(null) }
    var seasons by remember { mutableStateOf<List<Item>>(emptyList()) }
    var seasonKey by remember { mutableStateOf(Cache.season[ratingKey]) }
    var episodes by remember { mutableStateOf<List<Item>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ratingKey) {
        val result = runCatching { Plex.metadata(ratingKey) ?: throw IOException("No encontrado") }
        val m = result.getOrNull()
        if (m == null) {
            failure = "No pude cargar este título."
            return@LaunchedEffect
        }
        item = m
        if (m.type == "show") {
            val list = runCatching { Plex.children(ratingKey) }.getOrDefault(emptyList())
            seasons = list
            val current = seasonKey
            if (current == null || list.none { it.ratingKey == current }) {
                // Por defecto: la primera temporada con episodios sin ver.
                seasonKey = (list.firstOrNull { (it.viewedLeafCount ?: 0) < (it.leafCount ?: 0) } ?: list.firstOrNull())
                    ?.ratingKey
            }
        }
    }

    LaunchedEffect(seasonKey) {
        val k = seasonKey ?: return@LaunchedEffect
        Cache.season[ratingKey] = k
        episodes = null
        episodes = runCatching { Plex.children(k) }.getOrDefault(emptyList())
    }

    val m = item
    when {
        failure != null -> MessageBox(failure.orEmpty(), listOf(Action("Volver") { nav.popBackStack() }))
        m == null -> LoadingBox()
        else -> DetailContent(
            m = m,
            seasons = seasons,
            seasonKey = seasonKey,
            episodes = episodes,
            onSeason = { seasonKey = it },
            nav = nav,
        )
    }
}

@Composable
private fun DetailContent(
    m: Item,
    seasons: List<Item>,
    seasonKey: String?,
    episodes: List<Item>?,
    onSeason: (String) -> Unit,
    nav: NavController,
) {
    val screen = "detail-${m.ratingKey}"
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = Plex.image(m.art, 1280, 720),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Bg, 0.55f to Bg.copy(alpha = 0.8f), 1f to Color.Transparent)
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Bg)
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(top = 56.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item(key = "info") { Info(m, episodes, nav, screen) }

            if (m.type == "show" && seasons.isNotEmpty()) {
                item(key = "seasons") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(seasons, key = { it.ratingKey }) { s ->
                            ActionButton(
                                s.title,
                                onClick = { onSeason(s.ratingKey) },
                                highlighted = s.ratingKey == seasonKey,
                            )
                        }
                    }
                }
                item(key = "episodes") { Episodes(episodes, nav, screen) }
            }
        }
    }
}

@Composable
private fun Info(m: Item, episodes: List<Item>?, nav: NavController, screen: String) {
    val saved = Cache.lastFocus[screen]
    val focusPlay = saved == null || saved == "play"

    Column(Modifier.padding(horizontal = 48.dp).widthIn(max = 640.dp)) {
        Text(
            m.title,
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Text(metaLine(m), color = Dim, fontSize = 16.sp)
        val summary = m.summary
        if (!summary.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                summary,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val playModifier = Modifier.initialFocus(focusPlay).trackFocus(screen, "play")
            when (m.type) {
                "movie" -> {
                    val offset = m.viewOffset ?: 0L
                    if (offset > 0) {
                        ActionButton(
                            "▶  Continuar desde ${fmtClock(offset)}",
                            onClick = { nav.navigate(Routes.player(m.ratingKey, resume = true)) },
                            modifier = playModifier,
                            highlighted = true,
                        )
                        ActionButton(
                            "Desde el inicio",
                            onClick = { nav.navigate(Routes.player(m.ratingKey, resume = false)) },
                            modifier = Modifier.trackFocus(screen, "restart"),
                        )
                    } else {
                        ActionButton(
                            "▶  Reproducir",
                            onClick = { nav.navigate(Routes.player(m.ratingKey, resume = false)) },
                            modifier = playModifier,
                            highlighted = true,
                        )
                    }
                }
                "show" -> {
                    // Siguiente episodio de la temporada elegida: el que va a medias o el primero sin ver.
                    val next = episodes?.let { list ->
                        list.firstOrNull { (it.viewOffset ?: 0L) > 0 }
                            ?: list.firstOrNull { (it.viewCount ?: 0) == 0 }
                            ?: list.firstOrNull()
                    }
                    if (next != null) {
                        ActionButton(
                            "▶  ${next.episodeLabel}",
                            onClick = { nav.navigate(Routes.player(next.ratingKey, (next.viewOffset ?: 0L) > 0)) },
                            modifier = playModifier,
                            highlighted = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Episodes(episodes: List<Item>?, nav: NavController, screen: String) {
    if (episodes == null) {
        Text("Cargando episodios…", modifier = Modifier.padding(horizontal = 48.dp), color = Dim)
        return
    }
    val saved = Cache.lastFocus[screen]
    LazyRow(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(episodes, key = { it.ratingKey }) { e ->
            val k = "ep-${e.ratingKey}"
            PosterCard(
                title = "${e.index ?: ""}. ${e.title}",
                subtitle = fmtDuration(e.duration),
                image = Plex.image(e.thumb, 520, 292),
                aspect = 16f / 9f,
                progress = e.progress,
                watched = e.isFullyWatched,
                modifier = Modifier.width(260.dp),
                focus = saved == k,
                onFocused = { Cache.lastFocus[screen] = k },
                onClick = { nav.navigate(Routes.player(e.ratingKey, (e.viewOffset ?: 0L) > 0)) },
            )
        }
    }
}

private fun metaLine(m: Item): String {
    val parts = mutableListOf<String>()
    m.year?.let { parts += it.toString() }
    m.contentRating?.takeIf { it.isNotBlank() }?.let { parts += it }
    when (m.type) {
        "movie" -> fmtDuration(m.duration).takeIf { it.isNotBlank() }?.let { parts += it }
        "show" -> m.childCount?.let { parts += if (it == 1) "1 temporada" else "$it temporadas" }
    }
    m.genres.take(2).forEach { parts += it.tag }
    return parts.joinToString("  ·  ")
}

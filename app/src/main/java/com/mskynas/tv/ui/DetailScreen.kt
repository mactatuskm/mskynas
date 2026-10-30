@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
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

    LaunchedEffect(ratingKey, Plex.playbackTick) {
        val result = runCatching { Plex.metadata(ratingKey) ?: throw IOException("No encontrado") }
        val m = result.getOrNull()
        if (m == null) {
            if (item == null) failure = "No pude cargar este título."
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

    var loadedSeason by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seasonKey, Plex.playbackTick) {
        val k = seasonKey ?: return@LaunchedEffect
        Cache.season[ratingKey] = k
        // Al cambiar de temporada se limpia la lista; al volver del reproductor solo se refresca.
        if (loadedSeason != k) episodes = null
        val list = runCatching { Plex.children(k) }.getOrNull()
        if (list != null) {
            episodes = list
            loadedSeason = k
        } else if (episodes == null) {
            episodes = emptyList()
        }
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

private val DetailStart = 64.dp

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
    val hero = heroFor(m, m)
    Box(Modifier.fillMaxSize()) {
        Backdrop(hero.art)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(top = 52.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(key = "info") {
                Column(Modifier.padding(start = DetailStart, end = 48.dp)) {
                    HeroText(hero, summaryLines = 4)
                    Spacer(Modifier.height(22.dp))
                    Buttons(m, episodes, nav, screen)
                }
            }

            if (m.type == "show" && seasons.isNotEmpty()) {
                item(key = "seasons") {
                    LazyRow(
                        contentPadding = PaddingValues(start = DetailStart, end = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
private fun Buttons(m: Item, episodes: List<Item>?, nav: NavController, screen: String) {
    val saved = Cache.lastFocus[screen]
    val focusPlay = saved == null || saved == "play"

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val playModifier = Modifier.initialFocus(focusPlay).trackFocus(screen, "play")
        when (m.type) {
            "movie" -> {
                val offset = m.viewOffset ?: 0L
                if (offset > 0) {
                    ActionButton(
                        "Continuar desde ${fmtClock(offset)}",
                        onClick = { play(nav, m.ratingKey, resume = true) },
                        modifier = playModifier,
                        highlighted = true,
                        icon = AppIcons.Play,
                    )
                    ActionButton(
                        "Desde el inicio",
                        onClick = { play(nav, m.ratingKey, resume = false) },
                        modifier = Modifier.trackFocus(screen, "restart"),
                        icon = AppIcons.Restart,
                    )
                } else {
                    ActionButton(
                        "Reproducir",
                        onClick = { play(nav, m.ratingKey, resume = false) },
                        modifier = playModifier,
                        highlighted = true,
                        icon = AppIcons.Play,
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
                    val resume = (next.viewOffset ?: 0L) > 0
                    ActionButton(
                        (if (resume) "Continuar " else "Reproducir ") + next.episodeLabel,
                        onClick = { play(nav, next.ratingKey, resume) },
                        modifier = playModifier,
                        highlighted = true,
                        icon = AppIcons.Play,
                    )
                }
            }
        }
    }
}

@Composable
private fun Episodes(episodes: List<Item>?, nav: NavController, screen: String) {
    if (episodes == null) {
        Text("Cargando episodios…", modifier = Modifier.padding(start = DetailStart), color = Dim)
        return
    }
    val saved = Cache.lastFocus[screen]
    LazyRow(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(start = DetailStart, end = 48.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(episodes, key = { it.ratingKey }) { e ->
            val k = "ep-${e.ratingKey}"
            PosterCard(
                title = "${e.index ?: ""}. ${e.title}",
                subtitle = fmtDuration(e.duration),
                image = Plex.image(e.thumb, 464, 262),
                aspect = 16f / 9f,
                progress = e.progress,
                watched = e.isFullyWatched,
                modifier = Modifier.width(232.dp),
                focus = saved == k,
                onFocused = { Cache.lastFocus[screen] = k },
                onClick = { play(nav, e.ratingKey, (e.viewOffset ?: 0L) > 0) },
            )
        }
    }
}

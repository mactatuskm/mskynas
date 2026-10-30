@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Directory
import com.mskynas.tv.data.HomeData
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.episodeLabel
import com.mskynas.tv.data.isEpisode
import com.mskynas.tv.data.progress
import com.mskynas.tv.data.showTitle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SCREEN = "home"

private suspend fun loadHome(): HomeData = coroutineScope {
    val libraries = async { Plex.sections() }
    val deck = async { runCatching { Plex.onDeck() }.getOrDefault(emptyList()) }
    val watchlist = async { runCatching { Plex.watchlist() }.getOrDefault(emptyList()) }
    HomeData(deck.await(), libraries.await(), watchlist.await())
}

@Composable
fun HomeScreen(nav: NavController) {
    var data by remember { mutableStateOf(Cache.home) }
    var failure by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Muestra lo guardado al instante y refresca en segundo plano.
    LaunchedEffect(attempt) {
        failure = null
        val result = runCatching { loadHome() }
            .recoverCatching { if (Plex.reconnect()) loadHome() else throw it }
        result.onSuccess {
            data = it
            Cache.home = it
        }.onFailure {
            if (data == null) failure = "No pude conectar con tu servidor Plex."
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3500)
            toast = null
        }
    }

    val current = data
    Box(Modifier.fillMaxSize()) {
        when {
            current != null -> HomeContent(
                data = current,
                nav = nav,
                onWatchlist = { item ->
                    scope.launch {
                        val guid = item.guid
                        val local = if (guid != null) runCatching { Plex.findLocal(guid) }.getOrNull() else null
                        if (local != null) {
                            nav.navigate(Routes.detail(local.ratingKey))
                        } else {
                            toast = "“${item.title}” no está en tu servidor."
                        }
                    }
                },
            )
            failure != null -> MessageBox(
                failure.orEmpty(),
                listOf(
                    Action("Reintentar") { attempt += 1 },
                    Action("Cambiar servidor") { nav.navigate(Routes.SERVERS) },
                ),
            )
            else -> LoadingBox()
        }
        toast?.let { ToastPill(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
private fun HomeContent(data: HomeData, nav: NavController, onWatchlist: (Item) -> Unit) {
    val keys = buildList {
        data.deck.forEach { add("deck-${it.ratingKey}") }
        data.libraries.forEach { add("lib-${it.key}") }
        data.watchlist.forEach { add("wl-${it.ratingKey}") }
    }
    val saved = Cache.lastFocus[SCREEN]
    val target = if (saved != null && saved in keys) saved else keys.firstOrNull()
    fun mark(key: String) {
        Cache.lastFocus[SCREEN] = key
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(top = 36.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "header") {
            Header(onServer = { nav.navigate(Routes.SERVERS) })
        }

        if (data.deck.isNotEmpty()) {
            item(key = "deck") {
                Shelf("Continuar viendo") {
                    items(data.deck, key = { it.ratingKey }) { m ->
                        val k = "deck-${m.ratingKey}"
                        PosterCard(
                            title = m.showTitle,
                            subtitle = deckSubtitle(m),
                            image = Plex.image(if (m.isEpisode) m.thumb else m.art ?: m.thumb, 560, 316),
                            aspect = 16f / 9f,
                            progress = m.progress,
                            modifier = Modifier.width(300.dp),
                            focus = target == k,
                            onFocused = { mark(k) },
                            onClick = { nav.navigate(Routes.player(m.ratingKey, resume = true)) },
                        )
                    }
                }
            }
        }

        if (data.libraries.isNotEmpty()) {
            item(key = "libraries") {
                Shelf("Bibliotecas") {
                    items(data.libraries, key = { it.key }) { dir ->
                        val k = "lib-${dir.key}"
                        LibraryTile(
                            dir = dir,
                            modifier = Modifier
                                .width(280.dp)
                                .aspectRatio(16f / 9f)
                                .initialFocus(target == k)
                                .trackFocus(SCREEN, k),
                            onClick = {
                                Cache.titles[dir.key] = dir.title
                                nav.navigate(Routes.library(dir.key))
                            },
                        )
                    }
                }
            }
        }

        if (data.watchlist.isNotEmpty()) {
            item(key = "watchlist") {
                Shelf("Mi Watchlist") {
                    items(data.watchlist, key = { it.ratingKey }) { m ->
                        val k = "wl-${m.ratingKey}"
                        PosterCard(
                            title = m.title,
                            subtitle = m.year?.toString(),
                            image = Plex.image(m.thumb, 300, 450),
                            modifier = Modifier.width(150.dp),
                            focus = target == k,
                            onFocused = { mark(k) },
                            onClick = { onWatchlist(m) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(onServer: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("mskynas", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(Plex.session.serverName.orEmpty(), color = Dim, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        ActionButton("Servidor", onServer)
    }
}

@Composable
private fun LibraryTile(dir: Directory, modifier: Modifier, onClick: () -> Unit) {
    TileCard(onClick = onClick, modifier = modifier) {
        AsyncImage(
            model = Plex.image(dir.art, 560, 316),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.35f,
        )
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(dir.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(if (dir.type == "movie") "Películas" else "Series", color = Dim, fontSize = 14.sp)
        }
    }
}

private fun deckSubtitle(m: Item): String {
    if (m.isEpisode) return "${m.episodeLabel} · ${m.title}"
    val left = (m.duration ?: 0L) - (m.viewOffset ?: 0L)
    return if (left > 60_000) "Quedan ${fmtDuration(left)}" else (m.year?.toString() ?: "")
}

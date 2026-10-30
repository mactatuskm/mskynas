@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Directory
import com.mskynas.tv.data.HomeData
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.episodeLabel
import com.mskynas.tv.data.isEpisode
import com.mskynas.tv.data.isFullyWatched
import com.mskynas.tv.data.posterPath
import com.mskynas.tv.data.progress
import com.mskynas.tv.data.showTitle
import com.mskynas.tv.data.unwatchedCount
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SCREEN = "home"
private val HeroHeight = 262.dp
private val LandscapeWidth = 208.dp
private val PosterWidth = 116.dp

private suspend fun loadHome(): HomeData = coroutineScope {
    val librariesJob = async { Plex.sections() }
    val deck = async { runCatching { Plex.onDeck() }.getOrDefault(emptyList()) }
    val watchlist = async { runCatching { Plex.watchlist() }.getOrDefault(emptyList()) }
    val libraries = librariesJob.await()
    val recent = libraries.map { dir ->
        async { dir to runCatching { Plex.recentlyAdded(dir.key) }.getOrDefault(emptyList()) }
    }.awaitAll()
    HomeData(deck.await(), libraries, watchlist.await(), recent)
}

@Composable
fun HomeScreen(nav: NavController) {
    var data by remember { mutableStateOf(Cache.home) }
    var failure by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Muestra lo guardado al instante y refresca en segundo plano.
    LaunchedEffect(attempt, Plex.playbackTick) {
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
    RailScaffold(nav, Sections.HOME) {
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
}

/** Qué se enfocó: con eso se arma el hero (rápido con lo que hay, y luego con metadatos completos). */
private class HeroTarget(val quick: () -> HeroInfo, val full: suspend () -> HeroInfo?)

private fun itemTarget(m: Item, local: Boolean): HeroTarget {
    // Para un episodio, el logo y los géneros vienen de la serie.
    val fullKey = if (m.isEpisode) m.grandparentRatingKey else m.ratingKey
    return HeroTarget(
        quick = { heroFor(m, fullKey?.let { Cache.meta[it] }) },
        full = { if (local && fullKey != null) Plex.cachedMetadata(fullKey)?.let { heroFor(m, it) } else null },
    )
}

private sealed interface HomeRow {
    val key: String
    data class Deck(val items: List<Item>) : HomeRow { override val key = "deck" }
    data class Watchlist(val items: List<Item>) : HomeRow { override val key = "watchlist" }
    data class Recent(val dir: Directory, val items: List<Item>) : HomeRow { override val key = "recent-${dir.key}" }
}

@Composable
private fun HomeContent(data: HomeData, nav: NavController, onWatchlist: (Item) -> Unit) {
    val rows = buildList {
        if (data.deck.isNotEmpty()) add(HomeRow.Deck(data.deck))
        if (data.watchlist.isNotEmpty()) add(HomeRow.Watchlist(data.watchlist))
        data.recent.forEach { (dir, items) -> if (items.isNotEmpty()) add(HomeRow.Recent(dir, items)) }
    }
    val keys = buildList {
        rows.forEach { row ->
            when (row) {
                is HomeRow.Deck -> row.items.forEach { add("deck-${it.ratingKey}") }
                is HomeRow.Watchlist -> row.items.forEach { add("wl-${it.ratingKey}") }
                is HomeRow.Recent -> row.items.forEach { add("rec-${row.dir.key}-${it.ratingKey}") }
            }
        }
    }
    val saved = Cache.lastFocus[SCREEN]
    val target = if (saved != null && saved in keys) saved else keys.firstOrNull()

    var heroTarget by remember { mutableStateOf<HeroTarget?>(null) }
    var hero by remember { mutableStateOf<HeroInfo?>(null) }
    LaunchedEffect(heroTarget) {
        val t = heroTarget ?: return@LaunchedEffect
        // Pequeña espera: al recorrer una fila rápido no se carga el fondo de cada tarjeta.
        delay(140)
        hero = t.quick()
        t.full()?.let { hero = it }
    }

    fun focused(key: String, t: HeroTarget) {
        Cache.lastFocus[SCREEN] = key
        heroTarget = t
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Backdrop(hero?.art)
        Column(Modifier.fillMaxSize()) {
            HeroText(
                hero,
                Modifier
                    .fillMaxWidth()
                    .height(HeroHeight)
                    .padding(start = ContentStart, top = 40.dp, end = 48.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                state = listState,
                // Espacio al final para que la última fila también pueda subir bajo el hero.
                contentPadding = PaddingValues(bottom = 220.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    // La fila enfocada siempre queda justo debajo del hero.
                    val rowModifier = Modifier.onFocusChanged {
                        if (it.hasFocus) scope.launch { listState.animateScrollToItem(index) }
                    }
                    when (row) {
                        is HomeRow.Deck -> Shelf("Continuar viendo", rowModifier) {
                            items(row.items, key = { it.ratingKey }) { m ->
                                val k = "deck-${m.ratingKey}"
                                PosterCard(
                                    title = m.showTitle,
                                    subtitle = deckSubtitle(m),
                                    image = Plex.image(if (m.isEpisode) m.thumb else m.art ?: m.thumb, 416, 234),
                                    aspect = 16f / 9f,
                                    progress = m.progress,
                                    modifier = Modifier.width(LandscapeWidth),
                                    focus = target == k,
                                    onFocused = { focused(k, itemTarget(m, local = true)) },
                                    onClick = { play(nav, m.ratingKey, resume = true) },
                                )
                            }
                        }
                        is HomeRow.Watchlist -> Shelf("Mi lista", rowModifier) {
                            items(row.items, key = { it.ratingKey }) { m ->
                                val k = "wl-${m.ratingKey}"
                                PosterCard(
                                    title = m.title,
                                    subtitle = m.year?.toString(),
                                    image = Plex.image(m.thumb, 232, 348),
                                    modifier = Modifier.width(PosterWidth),
                                    focus = target == k,
                                    onFocused = { focused(k, itemTarget(m, local = false)) },
                                    onClick = { onWatchlist(m) },
                                )
                            }
                        }
                        is HomeRow.Recent -> Shelf("Recién agregado en ${row.dir.title}", rowModifier) {
                            items(row.items, key = { it.ratingKey }) { m ->
                                val k = "rec-${row.dir.key}-${m.ratingKey}"
                                PosterCard(
                                    title = m.title,
                                    subtitle = m.year?.toString(),
                                    image = Plex.image(m.posterPath, 232, 348),
                                    progress = m.progress,
                                    watched = m.isFullyWatched,
                                    badge = m.unwatchedCount,
                                    modifier = Modifier.width(PosterWidth),
                                    focus = target == k,
                                    onFocused = { focused(k, itemTarget(m, local = true)) },
                                    onClick = { openItem(nav, m) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun deckSubtitle(m: Item): String {
    if (m.isEpisode) return "${m.episodeLabel}  ${m.title}"
    val left = (m.duration ?: 0L) - (m.viewOffset ?: 0L)
    return if (left > 60_000) "Quedan ${fmtDuration(left)}" else (m.year?.toString() ?: "")
}

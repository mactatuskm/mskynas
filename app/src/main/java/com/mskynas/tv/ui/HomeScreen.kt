@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.HomeData
import com.mskynas.tv.data.Hub
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.Store
import com.mskynas.tv.data.isContinue
import com.mskynas.tv.data.isEpisode
import com.mskynas.tv.data.isFullyWatched
import com.mskynas.tv.data.posterPath
import com.mskynas.tv.data.progress
import com.mskynas.tv.data.showTitle
import com.mskynas.tv.data.unwatchedCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SCREEN = "home"

// Proporciones de la referencia (Silo) a 1080p ÷ 2.
private val HeroHeight = 284.dp
private val LandscapeWidth = 176.dp
private val PosterWidth = 88.dp

/**
 * Las filas del Home de Plex (mismo orden y categorías especiales, incluidas las colecciones
 * promovidas al inicio) más Continuar viendo. La Watchlist va aparte y nunca retrasa el inicio.
 */
private suspend fun loadHome(keepWatchlist: List<Item>): HomeData = coroutineScope {
    val libraries = Plex.sections()
    val deck = async { runCatching { Plex.onDeck() }.getOrDefault(emptyList()) }
    val hubs = async { runCatching { Plex.homeHubs(libraries) }.getOrDefault(emptyList()) }
    HomeData(deck.await(), libraries, keepWatchlist, hubs = hubs.await())
}

@Composable
fun HomeScreen(nav: NavController) {
    var data by remember { mutableStateOf(Cache.home ?: Store.loadHome()?.also { Cache.home = it }) }
    var failure by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    // Sube cuando Atrás debe regresar el foco a la primera fila.
    var backToTop by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Muestra lo guardado (memoria o disco) al instante y refresca en segundo plano.
    LaunchedEffect(attempt, Plex.playbackTick) {
        failure = null
        val keep = data?.watchlist.orEmpty()
        val result = runCatching { loadHome(keep) }
            .recoverCatching { if (Plex.reconnect()) loadHome(keep) else throw it }
        result.onSuccess {
            data = it
            Cache.home = it
        }.onFailure {
            if (data == null) failure = "No pude conectar con tu servidor Plex."
        }
        if (result.isSuccess) {
            val list = runCatching { Plex.watchlist() }.getOrNull()
            val current = data
            if (list != null && current != null) {
                data = current.copy(watchlist = list)
                Cache.home = data
            }
            data?.let { withContext(Dispatchers.IO) { Store.saveHome(it) } }
        }
    }

    // Atrás desde una fila de abajo: vuelve a la primera.
    val onBack: () -> Boolean = {
        if (listState.firstVisibleItemIndex > 0) {
            Cache.lastFocus.remove(SCREEN)
            backToTop += 1
            scope.launch { listState.scrollToItem(0) }
            true
        } else {
            false
        }
    }

    val current = data
    AppScaffold(nav, Sections.HOME, overlay = true, onBack = onBack) {
        when {
            current != null -> HomeContent(current, nav, listState, backToTop)
            failure != null -> MessageBox(
                failure.orEmpty(),
                listOf(
                    Action("Reintentar") { attempt += 1 },
                    Action("Cambiar servidor") { nav.navigate(Routes.SERVERS) },
                ),
            )
            else -> LoadingBox()
        }
    }
}

/** Qué se enfocó: con eso se arma el hero (rápido con lo que hay, y luego con metadatos completos). */
private class HeroTarget(val quick: () -> HeroInfo, val full: suspend () -> HeroInfo?)

private fun itemTarget(m: Item): HeroTarget {
    // Para un episodio, el logo, géneros y reparto vienen de la serie.
    val fullKey = when (m.type) {
        "episode" -> m.grandparentRatingKey
        "season" -> m.parentRatingKey
        else -> m.ratingKey
    }
    return HeroTarget(
        quick = { heroFor(m, fullKey?.let { Cache.meta[it] }) },
        full = { fullKey?.let { Plex.cachedMetadata(it) }?.let { heroFor(m, it) } },
    )
}

/** Filas a mostrar: los hubs de Plex; si Plex no trae Continuar viendo, se agrega al principio. */
private fun rowsOf(data: HomeData): List<Hub> {
    val hubs = data.hubs.filter { it.metadata.isNotEmpty() }
    return if (hubs.any { it.isContinue } || data.deck.isEmpty()) hubs
    else listOf(Hub(title = "Continue Watching", hubIdentifier = "home.continue", metadata = data.deck)) + hubs
}

@Composable
private fun HomeContent(data: HomeData, nav: NavController, listState: LazyListState, backToTop: Int) {
    val rows = rowsOf(data)
    val keys = rows.flatMap { hub -> hub.metadata.map { "${hub.hubIdentifier}-${it.ratingKey}" } }
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
    // Antes de enfocar nada, el hero muestra el primer título.
    LaunchedEffect(Unit) {
        if (heroTarget == null) rows.firstOrNull()?.metadata?.firstOrNull()?.let { heroTarget = itemTarget(it) }
    }
    val tint = rememberTint(hero?.artTiny)
    val scope = rememberCoroutineScope()

    fun focused(k: String, m: Item) {
        Cache.lastFocus[SCREEN] = k
        heroTarget = itemTarget(m)
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(hero?.art, tint)
        Column(Modifier.fillMaxSize()) {
            HeroText(
                hero,
                Modifier
                    .fillMaxWidth()
                    .height(HeroHeight)
                    .padding(start = ContentStart, top = TopBarHeight + 40.dp, end = 48.dp),
            )
            key(backToTop) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = listState,
                    // Espacio al final para que la última fila también pueda subir bajo el hero.
                    contentPadding = PaddingValues(bottom = 260.dp),
                ) {
                    itemsIndexed(rows, key = { _, hub -> hub.hubIdentifier.ifBlank { hub.title } }) { index, hub ->
                        // La fila enfocada siempre queda justo debajo del hero.
                        val rowModifier = Modifier.onFocusChanged {
                            if (it.hasFocus) scope.launch { listState.animateScrollToItem(index) }
                        }
                        val landscape = hub.isContinue
                        Shelf(hub.title, rowModifier, icon = if (landscape) AppIcons.Play else null) {
                            items(hub.metadata, key = { it.ratingKey }) { m ->
                                val k = "${hub.hubIdentifier}-${m.ratingKey}"
                                if (landscape) {
                                    PosterCard(
                                        title = m.showTitle,
                                        subtitle = continueSubtitle(m),
                                        image = Plex.image(if (m.isEpisode) m.thumb else m.art ?: m.thumb, 352, 198),
                                        aspect = 16f / 9f,
                                        progress = m.progress,
                                        modifier = Modifier.width(LandscapeWidth),
                                        focus = target == k,
                                        onFocused = { focused(k, m) },
                                        onClick = { play(nav, m.ratingKey, resume = true) },
                                    )
                                } else {
                                    PosterCard(
                                        title = m.showTitle,
                                        subtitle = posterSubtitle(m),
                                        image = Plex.image(m.posterPath, 176, 264),
                                        progress = m.progress,
                                        watched = m.isFullyWatched,
                                        badge = m.unwatchedCount,
                                        modifier = Modifier.width(PosterWidth),
                                        focus = target == k,
                                        onFocused = { focused(k, m) },
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
}

/** "S01E01 · Pilot" para episodios; el año para películas (como en la referencia). */
private fun continueSubtitle(m: Item): String =
    if (m.isEpisode) "S%02dE%02d · %s".format(m.parentIndex ?: 0, m.index ?: 0, m.title) else m.year?.toString().orEmpty()

private fun posterSubtitle(m: Item): String? = when (m.type) {
    "episode" -> "S%02dE%02d".format(m.parentIndex ?: 0, m.index ?: 0)
    "season" -> m.title
    "collection" -> m.childCount?.let { "$it títulos" }
    else -> m.year?.toString()
}

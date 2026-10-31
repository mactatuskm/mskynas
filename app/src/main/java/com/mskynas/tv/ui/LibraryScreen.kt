@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.isFullyWatched
import com.mskynas.tv.data.posterPath
import com.mskynas.tv.data.progress
import com.mskynas.tv.data.unwatchedCount

@Composable
fun CollectionScreen(collectionKey: String, nav: NavController) {
    val title = Cache.titles[collectionKey] ?: "Colección"
    var list by remember { mutableStateOf(Cache.collectionItems[collectionKey]) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(collectionKey, Plex.playbackTick) {
        runCatching { Plex.collectionItems(collectionKey) }
            .onSuccess { list = it; Cache.collectionItems[collectionKey] = it }
            .onFailure { if (list == null) failure = "No pude cargar esta colección." }
    }

    RailScaffold(nav, current = "") {
        Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
            Text(
                title,
                modifier = Modifier.padding(start = ContentStart, end = 48.dp),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            val current = list
            when {
                current == null && failure != null -> MessageBox(
                    failure.orEmpty(),
                    listOf(Action("Volver") { nav.popBackStack() }),
                )
                current == null -> LoadingBox()
                current.isEmpty() -> MessageBox("Esta colección está vacía.")
                else -> PosterGrid(current, screen = "col-$collectionKey", nav = nav)
            }
        }
    }
}

/**
 * Cuadrícula de pósters. `local = false` para títulos de plex.tv (Watchlist): sin progreso ni insignias.
 * `onClick` reemplaza la acción por defecto (abrir el título).
 */
@Composable
fun PosterGrid(
    list: List<Item>,
    screen: String,
    nav: NavController,
    local: Boolean = true,
    subtitle: ((Item) -> String?)? = null,
    titleOf: ((Item) -> String)? = null,
    onClick: ((Item) -> Unit)? = null,
) {
    val saved = Cache.lastFocus[screen]
    val target = if (saved != null && list.any { it.ratingKey == saved }) saved else list.firstOrNull()?.ratingKey

    LazyVerticalGrid(
        columns = GridCells.Adaptive(116.dp),
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyGridState(),
        contentPadding = PaddingValues(start = ContentStart, end = 48.dp, top = 12.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(list, key = { it.ratingKey }) { m ->
            PosterCard(
                title = titleOf?.invoke(m) ?: m.title,
                subtitle = subtitle?.invoke(m) ?: gridSubtitle(m),
                image = Plex.image(m.posterPath, 232, 348),
                progress = if (local) m.progress else null,
                watched = local && m.isFullyWatched,
                badge = if (local) m.unwatchedCount else 0,
                focus = m.ratingKey == target,
                onFocused = { Cache.lastFocus[screen] = m.ratingKey },
                onClick = { if (onClick != null) onClick(m) else openItem(nav, m) },
            )
        }
    }
}

private fun gridSubtitle(m: Item): String? = when (m.type) {
    "collection" -> m.childCount?.let { "$it títulos" }
    "playlist" -> m.leafCount?.let { if (it == 1) "1 título" else "$it títulos" }
    else -> m.year?.toString()
}

@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(sectionKey: String, nav: NavController) {
    val title = Cache.titles[sectionKey] ?: "Biblioteca"
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var all by remember { mutableStateOf(Cache.library[sectionKey]) }
    var collections by remember { mutableStateOf(Cache.collections[sectionKey]) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sectionKey) {
        coroutineScope {
            launch {
                runCatching { Plex.sectionItems(sectionKey) }
                    .onSuccess { all = it; Cache.library[sectionKey] = it }
                    .onFailure { if (all == null) failure = "No pude cargar esta biblioteca." }
            }
            launch {
                runCatching { Plex.sectionCollections(sectionKey) }
                    .onSuccess { collections = it; Cache.collections[sectionKey] = it }
                    .onFailure { if (collections == null) collections = emptyList() }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(32.dp))
            ActionButton(
                "Todo" + (all?.size?.let { " · $it" } ?: ""),
                onClick = { tab = 0 },
                highlighted = tab == 0,
            )
            Spacer(Modifier.width(12.dp))
            ActionButton(
                "Colecciones" + (collections?.size?.let { " · $it" } ?: ""),
                onClick = { tab = 1 },
                highlighted = tab == 1,
            )
        }
        Spacer(Modifier.height(16.dp))

        val list = if (tab == 0) all else collections
        when {
            list == null && failure != null -> MessageBox(
                failure.orEmpty(),
                listOf(Action("Volver") { nav.popBackStack() }),
            )
            list == null -> LoadingBox()
            list.isEmpty() -> MessageBox(
                if (tab == 1) "Esta biblioteca no tiene colecciones." else "Esta biblioteca está vacía."
            )
            else -> key(tab) { PosterGrid(list, screen = "lib-$sectionKey-$tab", nav = nav) }
        }
    }
}

@Composable
fun CollectionScreen(collectionKey: String, nav: NavController) {
    val title = Cache.titles[collectionKey] ?: "Colección"
    var list by remember { mutableStateOf(Cache.collectionItems[collectionKey]) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(collectionKey) {
        runCatching { Plex.collectionItems(collectionKey) }
            .onSuccess { list = it; Cache.collectionItems[collectionKey] = it }
            .onFailure { if (list == null) failure = "No pude cargar esta colección." }
    }

    Column(Modifier.fillMaxSize().padding(top = 36.dp)) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 48.dp),
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
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

@Composable
fun PosterGrid(list: List<Item>, screen: String, nav: NavController) {
    val saved = Cache.lastFocus[screen]
    val target = if (saved != null && list.any { it.ratingKey == saved }) saved else list.firstOrNull()?.ratingKey

    LazyVerticalGrid(
        columns = GridCells.Adaptive(128.dp),
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyGridState(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(list, key = { it.ratingKey }) { m ->
            PosterCard(
                title = m.title,
                subtitle = gridSubtitle(m),
                image = Plex.image(m.posterPath, 300, 450),
                progress = m.progress,
                watched = m.isFullyWatched,
                focus = m.ratingKey == target,
                onFocused = { Cache.lastFocus[screen] = m.ratingKey },
                onClick = { openItem(nav, m) },
            )
        }
    }
}

private fun gridSubtitle(m: Item): String? = when (m.type) {
    "collection" -> m.childCount?.let { "$it títulos" }
    "show" -> {
        val pending = (m.leafCount ?: 0) - (m.viewedLeafCount ?: 0)
        if (pending > 0 && (m.viewedLeafCount ?: 0) > 0) "$pending sin ver" else m.year?.toString()
    }
    else -> m.year?.toString()
}

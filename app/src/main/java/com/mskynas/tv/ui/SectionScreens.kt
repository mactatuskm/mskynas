@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.CalendarEntry
import com.mskynas.tv.data.Tmdb
import com.mskynas.tv.data.Directory
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.RowData
import com.mskynas.tv.data.episodeLabel
import com.mskynas.tv.data.isEpisode
import com.mskynas.tv.data.isFullyWatched
import com.mskynas.tv.data.posterPath
import com.mskynas.tv.data.progress
import com.mskynas.tv.data.score
import com.mskynas.tv.data.showTitle
import com.mskynas.tv.data.unwatchedCount
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
private fun ScreenHeader(title: String, content: @Composable () -> Unit = {}) {
    Row(
        Modifier.padding(start = ContentStart, end = 48.dp, top = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(18.dp))
        content()
    }
}

/** Bibliotecas del tipo pedido ("movie" o "show"). */
private suspend fun librariesOf(type: String): List<Directory> =
    (Cache.home?.libraries ?: runCatching { Plex.sections() }.getOrDefault(emptyList())).filter { it.type == type }

// ---------- Movies / Series ----------

private enum class SectionTab(val label: String) { RECOMMENDED("Recommended"), BROWSE("Browse"), COLLECTIONS("Collections") }

/** Movies o Series: Recommended, Browse y Collections, según el mapa de pantallas. */
@Composable
fun SectionScreen(type: String, nav: NavController) {
    val title = if (type == "movie") "Movies" else "Series"
    var tab by rememberSaveable(type) { mutableStateOf(SectionTab.RECOMMENDED) }
    var libraries by remember(type) {
        mutableStateOf(Cache.home?.libraries?.filter { it.type == type })
    }
    var libKey by rememberSaveable(type) { mutableStateOf<String?>(null) }

    LaunchedEffect(type) { libraries = librariesOf(type) }

    val libs = libraries
    val selected = libs?.firstOrNull { it.key == libKey } ?: libs?.firstOrNull()

    AppScaffold(nav, type) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title) {
                SectionTab.entries.forEachIndexed { i, t ->
                    ActionButton(
                        t.label,
                        onClick = { tab = t },
                        highlighted = t == tab,
                        modifier = Modifier.initialFocus(i == 0 && Cache.lastFocus["section-$type"] == null),
                    )
                }
                // Si tienes más de una biblioteca de este tipo, aquí se elige cuál ver.
                if (tab != SectionTab.RECOMMENDED && (libs?.size ?: 0) > 1) {
                    Spacer(Modifier.width(18.dp))
                    libs.orEmpty().forEach { dir ->
                        ActionButton(dir.title, onClick = { libKey = dir.key }, highlighted = dir.key == selected?.key)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                libs == null -> LoadingBox()
                libs.isEmpty() -> MessageBox("No tienes bibliotecas de ${title.lowercase()} en este servidor.")
                else -> key(tab, selected?.key) {
                    when (tab) {
                        SectionTab.RECOMMENDED -> Recommended(type, libs, nav)
                        SectionTab.BROWSE -> BrowseGrid(selected!!.key, type, nav)
                        SectionTab.COLLECTIONS -> LibraryGrid(selected!!.key, collections = true, nav = nav)
                    }
                }
            }
        }
    }
}

/** Filas armadas solo con tu servidor: continuar, recientes, mejor calificadas y por ver. */
private suspend fun loadRecommended(type: String, libs: List<Directory>): List<RowData> = coroutineScope {
    suspend fun query(q: String) =
        libs.map { dir -> async { runCatching { Plex.sectionQuery(dir.key, q) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()

    val deck = async { runCatching { Plex.onDeck() }.getOrDefault(emptyList()) }
    val recent = async { query("sort=addedAt:desc").sortedByDescending { it.addedAt ?: 0L } }
    val top = async { query("sort=audienceRating:desc").sortedByDescending { it.score ?: 0.0 } }
    val unwatched = async { query("unwatched=1&sort=addedAt:desc") }

    val inDeck = deck.await().filter { if (type == "movie") it.type == "movie" else it.isEpisode }
    listOfNotNull(
        inDeck.takeIf { it.isNotEmpty() }?.let { RowData("deck", "Continuar viendo", it, landscape = true) },
        recent.await().take(24).takeIf { it.isNotEmpty() }?.let { RowData("recent", "Recién agregadas", it) },
        top.await().take(24).takeIf { it.isNotEmpty() }?.let { RowData("top", "Mejor calificadas", it) },
        unwatched.await().take(24).takeIf { it.isNotEmpty() }?.let { RowData("unwatched", "Por ver", it) },
    )
}

@Composable
private fun Recommended(type: String, libs: List<Directory>, nav: NavController) {
    var rows by remember(type) { mutableStateOf(Cache.recommended[type]) }
    val screen = "section-$type"

    LaunchedEffect(type, Plex.playbackTick) {
        runCatching { loadRecommended(type, libs) }.onSuccess {
            rows = it
            Cache.recommended[type] = it
        }
    }

    val current = rows
    when {
        current == null -> LoadingBox()
        current.isEmpty() -> MessageBox("Todavía no hay nada que recomendar aquí.")
        else -> {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val saved = Cache.lastFocus[screen]
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 200.dp),
            ) {
                itemsIndexed(current, key = { _, r -> r.key }) { i, row ->
                    Shelf(
                        row.title,
                        Modifier.onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(i) } },
                    ) {
                        items(row.items, key = { it.ratingKey }) { m ->
                            val k = "${row.key}-${m.ratingKey}"
                            if (row.landscape) {
                                PosterCard(
                                    title = m.showTitle,
                                    subtitle = if (m.isEpisode) "${m.episodeLabel}  ${m.title}" else m.year?.toString(),
                                    image = Plex.image(if (m.isEpisode) m.thumb else m.art ?: m.thumb, 416, 234),
                                    aspect = 16f / 9f,
                                    progress = m.progress,
                                    modifier = Modifier.width(208.dp),
                                    focus = saved == k,
                                    onFocused = { Cache.lastFocus[screen] = k },
                                    onClick = { play(nav, m.ratingKey, resume = true) },
                                )
                            } else {
                                PosterCard(
                                    title = m.title,
                                    subtitle = m.year?.toString(),
                                    image = Plex.image(m.posterPath, 232, 348),
                                    progress = m.progress,
                                    watched = m.isFullyWatched,
                                    badge = m.unwatchedCount,
                                    modifier = Modifier.width(116.dp),
                                    focus = saved == k,
                                    onFocused = { Cache.lastFocus[screen] = k },
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

/** Browse (todo el catálogo) o Collections de una biblioteca, en cuadrícula. */
@Composable
private fun LibraryGrid(libKey: String, collections: Boolean, nav: NavController) {
    val cache = if (collections) Cache.collections else Cache.library
    var list by remember(libKey, collections) { mutableStateOf(cache[libKey]) }
    var failed by remember(libKey, collections) { mutableStateOf(false) }

    LaunchedEffect(libKey, collections, Plex.playbackTick) {
        runCatching { if (collections) Plex.sectionCollections(libKey) else Plex.sectionItems(libKey) }
            .onSuccess { list = it; cache[libKey] = it }
            .onFailure { if (list == null) failed = true }
    }

    val current = list
    when {
        current == null && failed -> MessageBox("No pude cargar esta biblioteca.")
        current == null -> LoadingBox()
        current.isEmpty() -> MessageBox(if (collections) "Esta biblioteca no tiene colecciones." else "Esta biblioteca está vacía.")
        else -> PosterGrid(current, screen = "grid-$libKey-$collections", nav = nav)
    }
}

/** Orden del catálogo (como en la referencia: "Sort · Date Added Newest"). */
private enum class BrowseSort(val label: String) {
    ADDED("Date Added Newest"),
    TITLE("Title A–Z"),
    YEAR("Year Newest"),
    RATING("Rating Highest"),
}

private fun sortKey(m: Item): String = (m.titleSort ?: m.title).trim().uppercase(Locale.ROOT)

private fun sorted(list: List<Item>, sort: BrowseSort): List<Item> = when (sort) {
    BrowseSort.ADDED -> list.sortedByDescending { it.addedAt ?: 0L }
    BrowseSort.TITLE -> list.sortedBy { sortKey(it) }
    BrowseSort.YEAR -> list.sortedByDescending { it.year ?: 0 }
    BrowseSort.RATING -> list.sortedByDescending { it.score ?: 0.0 }
}

/** Letra del salto A–Z: "#" para lo que empieza con número o símbolo. */
private fun jumpLetter(m: Item): Char = sortKey(m).firstOrNull()?.takeIf { it in 'A'..'Z' } ?: '#'

private val sortMemory = HashMap<String, BrowseSort>()
private val unwatchedMemory = HashMap<String, Boolean>()

/** Browse: todo el catálogo con Sort, Filter (sin ver), Shuffle y salto A–Z (referencia: Silo). */
@Composable
private fun BrowseGrid(libKey: String, type: String, nav: NavController) {
    var all by remember(libKey) { mutableStateOf(Cache.library[libKey]) }
    var failed by remember(libKey) { mutableStateOf(false) }
    var sort by remember(libKey) { mutableStateOf(sortMemory[libKey] ?: BrowseSort.ADDED) }
    var unwatched by remember(libKey) { mutableStateOf(unwatchedMemory[libKey] ?: false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(libKey, Plex.playbackTick) {
        runCatching { Plex.sectionItems(libKey) }
            .onSuccess { all = it; Cache.library[libKey] = it }
            .onFailure { if (all == null) failed = true }
    }

    val source = all
    if (source == null) {
        if (failed) MessageBox("No pude cargar esta biblioteca.") else LoadingBox()
        return
    }
    // Ordenar y filtrar en la TV: sin volver a pedir el catálogo al servidor.
    val list = remember(source, sort, unwatched) {
        sorted(if (unwatched) source.filterNot { it.isFullyWatched } else source, sort)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = ContentStart, end = 48.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton("Sort · ${sort.label}", onClick = {
                sort = BrowseSort.entries[(sort.ordinal + 1) % BrowseSort.entries.size]
                sortMemory[libKey] = sort
                scope.launch { gridState.scrollToItem(0) }
            })
            ActionButton(if (unwatched) "Filter · Unwatched" else "Filter · All", onClick = {
                unwatched = !unwatched
                unwatchedMemory[libKey] = unwatched
                scope.launch { gridState.scrollToItem(0) }
            })
            if (list.isNotEmpty()) {
                ActionButton("Shuffle", icon = AppIcons.Play, onClick = {
                    // Películas: reproduce una al azar (de preferencia sin ver). Series: abre una al azar.
                    val pool = list.filterNot { it.isFullyWatched }.ifEmpty { list }
                    val pick = pool.random()
                    if (type == "movie") play(nav, pick.ratingKey, resume = false) else openItem(nav, pick)
                })
            }
            Spacer(Modifier.width(8.dp))
            Text("${list.size} títulos", color = Faint, fontSize = 11.sp)
        }

        when {
            source.isEmpty() -> MessageBox("Esta biblioteca está vacía.")
            list.isEmpty() -> MessageBox("Ya viste todo en esta biblioteca.")
            else -> Row(Modifier.fillMaxSize()) {
                PosterGrid(
                    list,
                    screen = "grid-$libKey-false",
                    nav = nav,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    state = gridState,
                )
                // Salto A–Z: solo tiene sentido con el orden por título.
                if (sort == BrowseSort.TITLE) {
                    val firstIndex = remember(list) {
                        val map = LinkedHashMap<Char, Int>()
                        list.forEachIndexed { i, m -> map.getOrPut(jumpLetter(m)) { i } }
                        map
                    }
                    JumpBar(firstIndex) { i -> scope.launch { gridState.scrollToItem(i) } }
                }
            }
        }
    }
}

/** Columna de letras a la derecha; las que no tienen títulos se ven apagadas y no se enfocan. */
@Composable
private fun JumpBar(firstIndex: Map<Char, Int>, onJump: (Int) -> Unit) {
    val letters = listOf('#') + ('A'..'Z').toList()
    Column(
        Modifier.fillMaxHeight().padding(end = 14.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { c ->
            val index = firstIndex[c]
            if (index == null) {
                Text(c.toString(), color = Color.White.copy(alpha = 0.18f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            } else {
                JumpLetter(c, onClick = { onJump(index) })
            }
        }
    }
}

@Composable
private fun JumpLetter(c: Char, onClick: () -> Unit) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.size(18.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = 0.7f),
            focusedContainerColor = Color.White,
            focusedContentColor = Bg,
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            Text(c.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- Calendar ----------

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "MX"))

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Hoy"
        today.plusDays(1) -> "Mañana"
        else -> if (date.isBefore(today)) "Ya disponible · " + date.format(dayFormat) else date.format(dayFormat)
    }.replaceFirstChar { it.uppercase() }
}

private suspend fun loadCalendar(): List<CalendarEntry> = coroutineScope {
    val shows = async {
        librariesOf("show").map { dir -> async { runCatching { Plex.sectionWithGuids(dir.key) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()
    }
    val watchlist = async { runCatching { Plex.watchlist() }.getOrDefault(emptyList()) }
    Tmdb.upcoming(shows.await(), watchlist.await())
}

/** Calendar: próximos episodios de tus series y estrenos en digital de tu Watchlist (TMDB). */
@Composable
fun CalendarScreen(nav: NavController) {
    var entries by remember { mutableStateOf(Cache.calendar) }
    var failed by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!Tmdb.configured) return@LaunchedEffect
        runCatching { loadCalendar() }
            .onSuccess { entries = it; Cache.calendar = it }
            .onFailure { if (entries == null) failed = true }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(3000); toast = null } }

    AppScaffold(nav, Sections.CALENDAR) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader("Calendar")
                Spacer(Modifier.height(10.dp))
                val list = entries
                when {
                    !Tmdb.configured -> MessageBox("Falta conectar TMDB para ver los próximos estrenos.")
                    list == null && failed -> MessageBox("No pude cargar los próximos estrenos.")
                    list == null -> LoadingBox()
                    list.isEmpty() -> MessageBox("No hay estrenos próximos de tus series ni de tu Watchlist.")
                    else -> {
                        val days = list.groupBy { it.date }.toList()
                        val listState = rememberLazyListState()
                        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 120.dp)) {
                            itemsIndexed(days, key = { _, d -> d.first.toString() }) { i, (date, items) ->
                                Shelf(
                                    dayLabel(date),
                                    Modifier.onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(i) } },
                                ) {
                                    items(items, key = { "${it.title}-${it.detail}" }) { e ->
                                        PosterCard(
                                            title = e.title,
                                            subtitle = e.detail,
                                            image = e.image,
                                            modifier = Modifier.width(116.dp),
                                            focus = i == 0 && e == items.first(),
                                            onClick = {
                                                val key = e.localKey
                                                val movie = e.watchlistItem
                                                when {
                                                    key != null -> nav.navigate(Routes.detail(key))
                                                    movie != null -> scope.launch {
                                                        if (!openFromWatchlist(movie, nav)) toast = "“${e.title}” aún no está en tu servidor."
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            toast?.let { ToastPill(it, Modifier.align(Alignment.BottomCenter)) }
        }
    }
}

// ---------- Menú del perfil: Watchlist, Lists, History ----------

/** Abre un título de plex.tv (Watchlist) si está en tu servidor. */
private suspend fun openFromWatchlist(item: Item, nav: NavController): Boolean {
    val local = item.guid?.let { runCatching { Plex.findLocal(it) }.getOrNull() } ?: return false
    nav.navigate(Routes.detail(local.ratingKey))
    return true
}

@Composable
fun WatchlistScreen(nav: NavController) {
    var list by remember { mutableStateOf(Cache.home?.watchlist?.takeIf { it.isNotEmpty() }) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { runCatching { Plex.watchlist() }.onSuccess { list = it } }
    LaunchedEffect(toast) { if (toast != null) { delay(3000); toast = null } }

    AppScaffold(nav, current = "") {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader("Watchlist")
                Spacer(Modifier.height(10.dp))
                val current = list
                when {
                    current == null -> LoadingBox()
                    current.isEmpty() -> MessageBox("Tu Watchlist está vacía.")
                    else -> PosterGrid(current, screen = "watchlist", nav = nav, local = false) { m ->
                        scope.launch {
                            if (!openFromWatchlist(m, nav)) toast = "“${m.title}” no está en tu servidor."
                        }
                    }
                }
            }
            toast?.let { ToastPill(it, Modifier.align(Alignment.BottomCenter)) }
        }
    }
}

@Composable
fun ListsScreen(nav: NavController) {
    var list by remember { mutableStateOf(Cache.playlists) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { Plex.playlists() }
            .onSuccess { list = it; Cache.playlists = it }
            .onFailure { if (list == null) failed = true }
    }

    AppScaffold(nav, current = "") {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Lists")
            Spacer(Modifier.height(10.dp))
            val current = list
            when {
                current == null && failed -> MessageBox("No pude cargar tus listas.")
                current == null -> LoadingBox()
                current.isEmpty() -> MessageBox("Todavía no tienes listas. Puedes crearlas en Plex.")
                else -> PosterGrid(current, screen = "lists", nav = nav) { m ->
                    Cache.titles[m.ratingKey] = m.title
                    nav.navigate(Routes.list(m.ratingKey))
                }
            }
        }
    }
}

@Composable
fun ListItemsScreen(listKey: String, nav: NavController) {
    var list by remember { mutableStateOf<List<Item>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(listKey, Plex.playbackTick) {
        runCatching { Plex.playlistItems(listKey) }
            .onSuccess { list = it }
            .onFailure { if (list == null) failed = true }
    }

    AppScaffold(nav, current = "") {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(Cache.titles[listKey] ?: "Lista")
            Spacer(Modifier.height(10.dp))
            val current = list
            when {
                current == null && failed -> MessageBox("No pude cargar esta lista.")
                current == null -> LoadingBox()
                current.isEmpty() -> MessageBox("Esta lista está vacía.")
                else -> PosterGrid(current, screen = "list-$listKey", nav = nav)
            }
        }
    }
}

@Composable
fun HistoryScreen(nav: NavController) {
    var list by remember { mutableStateOf(Cache.history) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Plex.playbackTick) {
        runCatching { Plex.history() }
            .onSuccess { list = it; Cache.history = it }
            .onFailure { if (list == null) failed = true }
    }

    AppScaffold(nav, current = "") {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("History")
            Spacer(Modifier.height(10.dp))
            val current = list
            when {
                current == null && failed -> MessageBox("No pude cargar tu historial.")
                current == null -> LoadingBox()
                current.isEmpty() -> MessageBox("Aún no has visto nada en este perfil.")
                else -> PosterGrid(
                    current,
                    screen = "history",
                    nav = nav,
                    subtitle = { m -> historyLabel(m) },
                    titleOf = { m -> m.showTitle },
                ) { m ->
                    // Un episodio lleva a su serie; una película, a su detalle.
                    val target = if (m.isEpisode) m.grandparentRatingKey else m.ratingKey
                    if (target != null) nav.navigate(Routes.detail(target))
                }
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("d MMM", Locale("es", "MX"))

private fun historyLabel(m: Item): String {
    val date = m.viewedAt?.let { dateFormat.format(Date(it * 1000)) }
    val what = if (m.isEpisode) m.episodeLabel else null
    return listOfNotNull(what, date).joinToString("  ·  ")
}

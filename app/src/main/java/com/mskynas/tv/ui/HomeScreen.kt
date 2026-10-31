@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Directory
import com.mskynas.tv.data.HomeData
import com.mskynas.tv.data.HomeTab
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.RowData
import com.mskynas.tv.data.TabContent
import com.mskynas.tv.data.clearLogo
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

private const val SCREEN = "home"
private const val HERO_KEY = "hero"

/** Alto de la zona del carrusel: la primera fila asoma debajo, como en la maqueta. */
private val HeroHeight = 410.dp
private val LandscapeWidth = 220.dp
private val PosterWidth = 112.dp

// ---------- Datos ----------

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

/** Reparte por turnos: uno de cada biblioteca, luego el segundo de cada una… */
private fun interleave(lists: List<List<Item>>): List<Item> {
    val out = mutableListOf<Item>()
    var i = 0
    while (true) {
        var added = false
        for (l in lists) if (i < l.size) {
            out += l[i]
            added = true
        }
        if (!added) break
        i++
    }
    return out
}

/** Arma el carrusel y las filas de cada pestaña (solo con contenido de tu servidor y tu lista). */
private suspend fun buildTab(tab: HomeTab, d: HomeData): TabContent = coroutineScope {
    val movieLibs = d.libraries.filter { it.type == "movie" }
    val showLibs = d.libraries.filter { it.type == "show" }

    fun recentOf(libs: List<Directory>): List<Item> =
        d.recent.filter { (dir, _) -> libs.any { it.key == dir.key } }
            .flatMap { it.second }
            .sortedByDescending { it.addedAt ?: 0L }

    suspend fun query(libs: List<Directory>, q: String): List<Item> =
        libs.map { dir -> async { runCatching { Plex.sectionQuery(dir.key, q) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()

    when (tab) {
        HomeTab.FOR_YOU -> TabContent(
            carousel = interleave(d.recent.map { it.second }).filter { it.art != null }.take(6),
            rows = buildList {
                if (d.deck.isNotEmpty()) add(RowData("deck", "Continuar viendo", d.deck, landscape = true))
                if (d.watchlist.isNotEmpty()) add(RowData("wl", "Mi lista", d.watchlist, local = false))
                d.recent.forEach { (dir, items) ->
                    if (items.isNotEmpty()) add(RowData("rec-${dir.key}", "Recién agregado en ${dir.title}", items))
                }
            },
        )

        HomeTab.MOVIES -> {
            val top = async { query(movieLibs, "sort=audienceRating:desc") }
            val unwatched = async { query(movieLibs, "unwatched=1&sort=addedAt:desc") }
            val recent = recentOf(movieLibs)
            TabContent(
                carousel = recent.filter { it.art != null }.take(6),
                rows = listOfNotNull(
                    d.deck.filter { it.type == "movie" }.takeIf { it.isNotEmpty() }
                        ?.let { RowData("deck", "Continuar viendo", it, landscape = true) },
                    recent.takeIf { it.isNotEmpty() }?.let { RowData("recent", "Recién agregadas", it) },
                    top.await().sortedByDescending { it.score ?: 0.0 }.take(24).takeIf { it.isNotEmpty() }
                        ?.let { RowData("top", "Mejor calificadas", it) },
                    unwatched.await().takeIf { it.isNotEmpty() }?.let { RowData("unwatched", "Por ver", it) },
                ),
            )
        }

        HomeTab.SHOWS -> {
            val top = async { query(showLibs, "sort=audienceRating:desc") }
            val recent = recentOf(showLibs)
            TabContent(
                carousel = recent.filter { it.art != null }.take(6),
                rows = listOfNotNull(
                    d.deck.filter { it.isEpisode }.takeIf { it.isNotEmpty() }
                        ?.let { RowData("deck", "Continuar viendo", it, landscape = true) },
                    recent.takeIf { it.isNotEmpty() }?.let { RowData("recent", "Recién agregadas", it) },
                    top.await().sortedByDescending { it.score ?: 0.0 }.take(24).takeIf { it.isNotEmpty() }
                        ?.let { RowData("top", "Mejor calificadas", it) },
                ),
            )
        }

        HomeTab.LIST -> TabContent(
            carousel = d.watchlist.filter { it.art != null }.take(6),
            rows = listOfNotNull(
                d.watchlist.filter { it.type == "movie" }.takeIf { it.isNotEmpty() }
                    ?.let { RowData("wl-movies", "Películas en tu lista", it, local = false) },
                d.watchlist.filter { it.type == "show" }.takeIf { it.isNotEmpty() }
                    ?.let { RowData("wl-shows", "Series en tu lista", it, local = false) },
            ),
            localCarousel = false,
        )
    }
}

// ---------- Pantalla ----------

@Composable
fun HomeScreen(nav: NavController) {
    var data by remember { mutableStateOf(Cache.home) }
    var failure by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(HomeTab.FOR_YOU) }
    val contents = remember { mutableStateMapOf<HomeTab, TabContent>().apply { putAll(Cache.tabs) } }
    val listState = rememberLazyListState()
    val heroRequester = remember { FocusRequester() }
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

    LaunchedEffect(tab, data) {
        val d = data ?: return@LaunchedEffect
        runCatching { buildTab(tab, d) }.onSuccess {
            contents[tab] = it
            Cache.tabs[tab] = it
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3000)
            toast = null
        }
    }

    // Atrás desde las filas: vuelve arriba, al carrusel.
    val onBack: () -> Boolean = {
        if (listState.firstVisibleItemIndex > 0) {
            scope.launch { listState.animateScrollToItem(0) }
            runCatching { heroRequester.requestFocus() }
            true
        } else {
            false
        }
    }

    RailScaffold(nav, Sections.HOME, onBack = onBack) {
        Box(Modifier.fillMaxSize()) {
            val d = data
            when {
                d != null -> HomeContent(
                    data = d,
                    tab = tab,
                    content = contents[tab],
                    onTab = {
                        if (it != tab) {
                            tab = it
                            scope.launch { listState.scrollToItem(0) }
                        }
                    },
                    nav = nav,
                    listState = listState,
                    heroRequester = heroRequester,
                    onToast = { toast = it },
                    onListChanged = { attempt += 1 },
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

@Composable
private fun HomeContent(
    data: HomeData,
    tab: HomeTab,
    content: TabContent?,
    onTab: (HomeTab) -> Unit,
    nav: NavController,
    listState: LazyListState,
    heroRequester: FocusRequester,
    onToast: (String) -> Unit,
    onListChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val slides = content?.carousel.orEmpty()
    val localSlides = content?.localCarousel ?: true
    var index by rememberSaveable(tab) { mutableIntStateOf(0) }
    val current = if (slides.isEmpty()) 0 else index % slides.size
    val slide = slides.getOrNull(current)
    var heroFocused by remember { mutableStateOf(false) }

    // Títulos en "Mi lista" (por su identificador de plex.tv), para el botón de agregar/quitar.
    val listed = remember(data) { mutableStateListOf<String>().apply { addAll(data.watchlist.mapNotNull { it.guid }) } }

    // Metadatos completos de la diapositiva (logo, géneros, clasificación).
    var full by remember(slide?.ratingKey) { mutableStateOf(slide?.ratingKey?.let { Cache.meta[it] }) }
    LaunchedEffect(slide?.ratingKey) {
        if (slide != null && localSlides) Plex.cachedMetadata(slide.ratingKey)?.let { full = it }
    }

    // El carrusel avanza solo mientras estás en él.
    LaunchedEffect(current, heroFocused, slides.size) {
        if (heroFocused && slides.size > 1) {
            delay(9000)
            index = (current + 1) % slides.size
        }
    }

    // Al bajar a las filas, el fondo se oscurece.
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val dim by animateFloatAsState(if (scrolled) 0.78f else 0f, tween(250), label = "dim")

    val saved = Cache.lastFocus[SCREEN]
    val rowKeys = content?.rows.orEmpty().flatMap { r -> r.items.map { "${r.key}-${it.ratingKey}" } }
    val target = if (saved != null && saved in rowKeys) saved else HERO_KEY

    fun next() {
        if (slides.size > 1) index = (current + 1) % slides.size
    }

    fun prev(): Boolean {
        if (current == 0) return false
        index = current - 1
        return true
    }

    fun playSlide(s: Item) {
        scope.launch {
            val item = if (localSlides) s else s.guid?.let { runCatching { Plex.findLocal(it) }.getOrNull() }
            if (item == null) {
                onToast("“${s.title}” no está en tu servidor.")
                return@launch
            }
            when (item.type) {
                "movie" -> play(nav, item.ratingKey, (item.viewOffset ?: 0L) > 0)
                "show" -> {
                    val ep = runCatching { Plex.nextEpisode(item.ratingKey) }.getOrNull()
                    if (ep != null) play(nav, ep.ratingKey, (ep.viewOffset ?: 0L) > 0)
                    else nav.navigate(Routes.detail(item.ratingKey))
                }
                else -> openItem(nav, item)
            }
        }
    }

    fun openSlide(s: Item) {
        scope.launch {
            val item = if (localSlides) s else s.guid?.let { runCatching { Plex.findLocal(it) }.getOrNull() }
            if (item == null) onToast("“${s.title}” no está en tu servidor.")
            else nav.navigate(Routes.detail(item.ratingKey))
        }
    }

    fun toggleList(s: Item) {
        val guid = s.guid ?: return onToast("Este título no se puede agregar a tu lista.")
        val add = guid !in listed
        scope.launch {
            if (Plex.setWatchlisted(s, add)) {
                if (add) listed.add(guid) else listed.remove(guid)
                onToast(if (add) "Agregado a Mi lista" else "Quitado de Mi lista")
                onListChanged()
            } else {
                onToast("No pude actualizar tu lista.")
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(
            art = slide?.let { Plex.image(full?.art ?: it.art, 1280, 720) },
            dim = dim,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 140.dp),
        ) {
            item(key = "hero") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(HeroHeight)
                        .onFocusChanged {
                            heroFocused = it.hasFocus
                            if (it.hasFocus) {
                                Cache.lastFocus[SCREEN] = HERO_KEY
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        },
                ) {
                    Tabs(tab, onTab, Modifier.align(Alignment.TopCenter).padding(top = 24.dp))

                    if (slide != null) {
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = ContentStart, bottom = 40.dp)
                                .widthIn(max = 440.dp)
                        ) {
                            Crossfade(targetState = slide to full, animationSpec = tween(300), label = "slide") { (s, f) ->
                                SlideInfo(s, f, inDeck = data.deck.any { it.ratingKey == s.ratingKey }, listTab = !localSlides)
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val resume = (slide.viewOffset ?: 0L) > 0
                                HeroButton(
                                    text = if (resume) "CONTINUAR" else "REPRODUCIR",
                                    icon = AppIcons.Play,
                                    onClick = { playSlide(slide) },
                                    modifier = Modifier
                                        .focusRequester(heroRequester)
                                        .initialFocus(target == HERO_KEY)
                                        // Izquierda en el primer botón: diapositiva anterior.
                                        .onPreviewKeyEvent {
                                            it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft && prev()
                                        },
                                )
                                val inList = slide.guid != null && slide.guid in listed
                                HeroButton(
                                    text = if (inList) "En mi lista" else "Mi lista",
                                    icon = if (inList) AppIcons.Check else AppIcons.Add,
                                    secondary = true,
                                    onClick = { toggleList(slide) },
                                )
                                HeroButton(
                                    text = "Detalles",
                                    icon = AppIcons.Info,
                                    secondary = true,
                                    onClick = { openSlide(slide) },
                                    // Derecha en el último botón: siguiente diapositiva.
                                    modifier = Modifier.onPreviewKeyEvent {
                                        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight && slides.size > 1) {
                                            next()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                )
                            }
                        }
                        Dots(slides.size, current, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
                    } else if (content == null) {
                        Text(
                            "Cargando…",
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = ContentStart),
                            color = Dim,
                            fontSize = 16.sp,
                        )
                    }
                }
            }

            itemsIndexed(content?.rows.orEmpty(), key = { _, r -> r.key }) { i, row ->
                Shelf(
                    row.title,
                    Modifier
                        .padding(top = if (i == 0) 0.dp else 8.dp)
                        .onFocusChanged {
                            if (it.hasFocus) scope.launch { listState.animateScrollToItem(i + 1) }
                        },
                ) {
                    items(row.items, key = { it.ratingKey }) { m ->
                        val k = "${row.key}-${m.ratingKey}"
                        RowCard(
                            m,
                            row,
                            focus = target == k,
                            onFocused = { Cache.lastFocus[SCREEN] = k },
                            onClick = {
                                when {
                                    row.key == "deck" -> play(nav, m.ratingKey, resume = true)
                                    !row.local -> openSlideFromList(m, nav, scope, onToast)
                                    else -> openItem(nav, m)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun openSlideFromList(
    m: Item,
    nav: NavController,
    scope: kotlinx.coroutines.CoroutineScope,
    onToast: (String) -> Unit,
) {
    scope.launch {
        val local = m.guid?.let { runCatching { Plex.findLocal(it) }.getOrNull() }
        if (local != null) nav.navigate(Routes.detail(local.ratingKey))
        else onToast("“${m.title}” no está en tu servidor.")
    }
}

// ---------- Piezas ----------

/** Fondo del carrusel con degradados a la izquierda y abajo; se oscurece al bajar a las filas. */
@Composable
private fun HeroBackdrop(art: String?, dim: Float) {
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = art, animationSpec = tween(500), label = "art") { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopEnd,
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Bg,
                    0.24f to Bg.copy(alpha = 0.88f),
                    0.52f to Bg.copy(alpha = 0.25f),
                    0.68f to Color.Transparent,
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    0.74f to Bg.copy(alpha = 0.82f),
                    1f to Bg,
                )
            )
        )
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Bg.copy(alpha = dim)))
    }
}

/** Etiqueta, logo (o título), frase, clasificación + datos y sinopsis de la diapositiva. */
@Composable
private fun SlideInfo(s: Item, full: Item?, inDeck: Boolean, listTab: Boolean) {
    val type = full?.type ?: s.type
    Column {
        Text(
            if (type == "show") "SERIE" else "PELÍCULA",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(6.dp))

        val logo = (full?.clearLogo ?: s.clearLogo)?.let { Plex.rawImage(it) }
        var logoFailed by remember(logo) { mutableStateOf(false) }
        if (logo != null && !logoFailed) {
            AsyncImage(
                model = logo,
                contentDescription = s.title,
                modifier = Modifier.heightIn(max = 104.dp).widthIn(max = 330.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onState = { if (it is AsyncImagePainter.State.Error) logoFailed = true },
            )
        } else {
            Text(
                s.title,
                color = Color.White,
                fontSize = 44.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))

        Text(
            when {
                listTab -> "En tu lista"
                inDeck -> "Continúa donde te quedaste"
                else -> "Recién agregada a tu biblioteca"
            },
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            val rating = full?.contentRating ?: s.contentRating
            if (!rating.isNullOrBlank()) {
                Text(
                    rating,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
            }
            val parts = buildList {
                (full?.year ?: s.year)?.let { add(it.toString()) }
                if (type == "show") {
                    (full?.childCount ?: s.childCount)?.let { add(if (it == 1) "1 temporada" else "$it temporadas") }
                } else {
                    fmtDuration(full?.duration ?: s.duration).takeIf { it.isNotBlank() }?.let { add(it) }
                }
                val genres = (full?.genres ?: s.genres).take(2).joinToString(", ") { it.tag }
                if (genres.isNotBlank()) add(genres)
            }
            Text(
                parts.joinToString(" • "),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val score = full?.score ?: s.score
            if (score != null) {
                Spacer(Modifier.width(10.dp))
                Icon(AppIcons.Star, contentDescription = null, modifier = Modifier.size(13.dp), tint = Gold)
                Spacer(Modifier.width(3.dp))
                Text("%.1f".format(score), color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        val summary = s.summary?.takeIf { it.isNotBlank() } ?: full?.summary
        if (!summary.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                summary,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Botón rectangular del carrusel. Enfocado: blanco con texto oscuro. */
@Composable
private fun HeroButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(6.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.16f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Bg,
        ),
        contentPadding = PaddingValues(horizontal = if (secondary) 16.dp else 20.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = if (secondary) FontWeight.Bold else FontWeight.ExtraBold,
            letterSpacing = if (secondary) 0.sp else 0.5.sp,
        )
    }
}

/** Pestañas de arriba: filtran el carrusel y las filas del inicio. */
@Composable
private fun Tabs(selected: HomeTab, onSelect: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .background(Color(0xFF10101A).copy(alpha = 0.62f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HomeTab.entries.forEach { t ->
            val isSelected = t == selected
            Surface(
                onClick = { onSelect(t) },
                modifier = Modifier.height(26.dp),
                shape = ClickableSurfaceDefaults.shape(shape = shape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) Color.White else Color.Transparent,
                    contentColor = if (isSelected) Bg else Color.White.copy(alpha = 0.72f),
                    focusedContainerColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                    focusedContentColor = if (isSelected) Bg else Color.White,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            ) {
                Box(Modifier.padding(horizontal = 15.dp).height(26.dp), contentAlignment = Alignment.Center) {
                    Text(
                        t.label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun Dots(count: Int, active: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .width(if (i == active) 20.dp else 6.dp)
                    .height(6.dp)
                    .background(
                        if (i == active) Color.White else Color.White.copy(alpha = 0.45f),
                        RoundedCornerShape(50),
                    )
            )
        }
    }
}

@Composable
private fun RowCard(m: Item, row: RowData, focus: Boolean, onFocused: () -> Unit, onClick: () -> Unit) {
    if (row.landscape) {
        PosterCard(
            title = m.showTitle,
            subtitle = if (m.isEpisode) "${m.episodeLabel}  ${m.title}" else leftLabel(m),
            image = Plex.image(if (m.isEpisode) m.thumb else m.art ?: m.thumb, 440, 248),
            aspect = 16f / 9f,
            progress = m.progress,
            modifier = Modifier.width(LandscapeWidth),
            focus = focus,
            onFocused = onFocused,
            onClick = onClick,
        )
    } else {
        PosterCard(
            title = m.title,
            subtitle = m.year?.toString(),
            image = Plex.image(if (row.local) m.posterPath else m.thumb, 224, 336),
            progress = if (row.local) m.progress else null,
            watched = row.local && m.isFullyWatched,
            badge = if (row.local) m.unwatchedCount else 0,
            modifier = Modifier.width(PosterWidth),
            focus = focus,
            onFocused = onFocused,
            onClick = onClick,
        )
    }
}

private fun leftLabel(m: Item): String {
    val left = (m.duration ?: 0L) - (m.viewOffset ?: 0L)
    return if (left > 60_000) "Quedan ${fmtDuration(left)}" else m.year?.toString().orEmpty()
}

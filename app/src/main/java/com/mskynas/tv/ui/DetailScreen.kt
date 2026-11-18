@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.Stream
import com.mskynas.tv.data.audioLabel
import com.mskynas.tv.data.audioStreams
import com.mskynas.tv.data.isFullyWatched
import com.mskynas.tv.data.languageName
import com.mskynas.tv.data.partId
import com.mskynas.tv.data.progress
import com.mskynas.tv.data.resolutionLabel
import com.mskynas.tv.data.selectedAudio
import com.mskynas.tv.data.selectedSubtitle
import com.mskynas.tv.data.subtitleStreams
import kotlinx.coroutines.launch
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

/** Siguiente episodio de la temporada elegida: el que va a medias o el primero sin ver. */
private fun nextEpisode(episodes: List<Item>?): Item? = episodes?.let { list ->
    list.firstOrNull { (it.viewOffset ?: 0L) > 0 }
        ?: list.firstOrNull { (it.viewCount ?: 0) == 0 }
        ?: list.firstOrNull()
}

private fun epCode(e: Item) = "S%02dE%02d".format(e.parentIndex ?: 0, e.index ?: 0)

/** Qué selector de pistas está abierto. */
private enum class Picker { AUDIO, SUBTITLES }

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
    val tint = rememberTint(hero.artTiny)
    val scope = rememberCoroutineScope()
    val next = if (m.type == "show") nextEpisode(episodes) else null

    // Las pistas (audio y subtítulos) son del archivo que se va a reproducir: la película o el siguiente episodio.
    val sourceKey = if (m.type == "movie") m.ratingKey else next?.ratingKey
    var streamsTick by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf<Item?>(if (m.type == "movie") m else null) }
    LaunchedEffect(sourceKey, streamsTick) {
        if (sourceKey == null) return@LaunchedEffect
        if (sourceKey == m.ratingKey && streamsTick == 0) {
            source = m
            return@LaunchedEffect
        }
        runCatching { Plex.metadata(sourceKey) }.getOrNull()?.let { source = it }
    }
    var picker by remember { mutableStateOf<Picker?>(null) }

    Box(Modifier.fillMaxSize()) {
        Backdrop(hero.art, tint)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(top = 56.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "info") {
                Column(Modifier.padding(start = ContentStart, end = 48.dp)) {
                    HeroText(hero, summaryLines = 3, showScore = true, castPrefix = "Starring ")
                    source?.let {
                        Spacer(Modifier.height(14.dp))
                        StreamsLine(it)
                    }
                    Spacer(Modifier.height(18.dp))
                    Buttons(
                        m = m,
                        next = next,
                        hasAudio = (source?.audioStreams?.size ?: 0) > 1,
                        hasSubtitles = source?.subtitleStreams?.isNotEmpty() == true,
                        onPicker = { picker = it },
                        onToggleWatched = { scope.launch { runCatching { Plex.setWatched(m.ratingKey, !m.isFullyWatched) } } },
                        nav = nav,
                        screen = screen,
                    )
                }
            }

            if (m.type == "show" && seasons.isNotEmpty()) {
                item(key = "seasons") {
                    LazyRow(
                        contentPadding = PaddingValues(start = ContentStart, end = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(seasons, key = { it.ratingKey }) { s ->
                            SeasonPill(s.title, selected = s.ratingKey == seasonKey, onClick = { onSeason(s.ratingKey) })
                        }
                    }
                }
                item(key = "episodes") { Episodes(episodes, nav, screen) }
            }
        }
    }

    val src = source
    if (picker != null && src != null) {
        TrackPicker(
            src = src,
            kind = picker!!,
            onDismiss = { picker = null },
            onPick = { audioId, subtitleId ->
                picker = null
                val part = src.partId ?: return@TrackPicker
                scope.launch {
                    runCatching { Plex.selectStreams(part, audioId, subtitleId) }
                    streamsTick += 1
                }
            },
        )
    }
}

/** VERSION Auto · 1080p   AUDIO English · AC3 5.1   SUBTITLES None (como en la referencia). */
@Composable
private fun StreamsLine(src: Item) {
    val audio = src.selectedAudio
    val sub = src.selectedSubtitle
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        src.resolutionLabel?.let { StreamInfo("VERSION", "Auto · $it") }
        if (audio != null) StreamInfo("AUDIO", listOfNotNull(audio.languageName, audioLabel(audio)).joinToString(" · "))
        StreamInfo("SUBTITLES", sub?.languageName ?: "None")
    }
}

@Composable
private fun StreamInfo(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(Modifier.width(6.dp))
        Text(value, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun Buttons(
    m: Item,
    next: Item?,
    hasAudio: Boolean,
    hasSubtitles: Boolean,
    onPicker: (Picker) -> Unit,
    onToggleWatched: () -> Unit,
    nav: NavController,
    screen: String,
) {
    val saved = Cache.lastFocus[screen]
    val focusPlay = saved == null || saved == "play"
    val playModifier = Modifier.initialFocus(focusPlay).trackFocus(screen, "play")

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        // Qué reproduce el botón principal.
        val target = if (m.type == "show") next else m
        if (target != null) {
            val offset = target.viewOffset ?: 0L
            val resume = offset > 0
            val label = when {
                m.type == "show" -> (if (resume) "Resume " else "Play ") + "S${target.parentIndex ?: 0}:E${target.index ?: 0}"
                resume -> "Resume ${fmtClock(offset)}"
                else -> "Play"
            }
            PrimaryPill(label, modifier = playModifier, onClick = { play(nav, target.ratingKey, resume) })
            if (resume) {
                CircleButton(AppIcons.Restart, "Desde el inicio", Modifier.trackFocus(screen, "restart")) {
                    play(nav, target.ratingKey, resume = false)
                }
            }
        }
        if (hasAudio) {
            CircleButton(AppIcons.Audio, "Audio", Modifier.trackFocus(screen, "audio")) { onPicker(Picker.AUDIO) }
        }
        if (hasSubtitles) {
            CircleButton(AppIcons.Subtitles, "Subtítulos", Modifier.trackFocus(screen, "subs")) { onPicker(Picker.SUBTITLES) }
        }
        CircleButton(
            AppIcons.Check,
            if (m.isFullyWatched) "Marcar como no visto" else "Marcar como visto",
            Modifier.trackFocus(screen, "watched"),
            active = m.isFullyWatched,
            onClick = onToggleWatched,
        )
    }
}

/** Botón principal: píldora blanca con ▶. */
@Composable
private fun PrimaryPill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.88f),
            contentColor = Bg,
            focusedContainerColor = Color.White,
            focusedContentColor = Bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 18.dp).height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Botón redondo con ícono (reiniciar, audio, subtítulos, visto). */
@Composable
private fun CircleButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(34.dp),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) Color.White.copy(alpha = 0.3f) else Chip,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(17.dp))
        }
    }
}

/** Píldora de temporada: gris translúcido si es la elegida, blanca al enfocarla. */
@Composable
private fun SeasonPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(28.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            focusedContainerColor = Color.White,
            focusedContentColor = Bg,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0f else 0.18f)), shape = RoundedCornerShape(50)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(Modifier.padding(horizontal = 14.dp).height(28.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Episodes(episodes: List<Item>?, nav: NavController, screen: String) {
    if (episodes == null) {
        Text("Cargando episodios…", modifier = Modifier.padding(start = ContentStart), color = Dim, fontSize = 12.sp)
        return
    }
    val saved = Cache.lastFocus[screen]
    LazyRow(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(start = ContentStart, end = 48.dp, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(episodes, key = { it.ratingKey }) { e ->
            val k = "ep-${e.ratingKey}"
            PosterCard(
                title = "${epCode(e)} · ${e.title}",
                subtitle = fmtDuration(e.duration),
                image = Plex.image(e.thumb, 400, 225),
                aspect = 16f / 9f,
                progress = e.progress,
                watched = e.isFullyWatched,
                modifier = Modifier.width(200.dp),
                focus = saved == k,
                onFocused = { Cache.lastFocus[screen] = k },
                onClick = { play(nav, e.ratingKey, (e.viewOffset ?: 0L) > 0) },
            )
        }
    }
}

/** Elegir audio o subtítulos; Plex lo recuerda para este archivo. */
@Composable
private fun TrackPicker(
    src: Item,
    kind: Picker,
    onDismiss: () -> Unit,
    onPick: (audioId: Long?, subtitleId: Long?) -> Unit,
) {
    AppDialog(onDismiss = onDismiss, width = 420.dp) {
        Text(
            if (kind == Picker.AUDIO) "Audio" else "Subtitles",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        val options: List<Pair<String, () -> Unit>> = when (kind) {
            Picker.AUDIO -> src.audioStreams.map { s -> trackName(s) to { onPick(s.id, null) } }
            Picker.SUBTITLES -> listOf<Pair<String, () -> Unit>>("None" to { onPick(null, 0L) }) +
                src.subtitleStreams.map { s -> trackName(s) to { onPick(null, s.id) } }
        }
        val selectedIndex = when (kind) {
            Picker.AUDIO -> src.audioStreams.indexOf(src.selectedAudio)
            Picker.SUBTITLES -> src.selectedSubtitle?.let { src.subtitleStreams.indexOf(it) + 1 } ?: 0
        }
        LazyColumn(Modifier.height((options.size.coerceAtMost(6) * 46).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options.size) { i ->
                val (label, action) = options[i]
                ActionButton(
                    label,
                    onClick = action,
                    modifier = Modifier.initialFocus(i == selectedIndex.coerceAtLeast(0)),
                    highlighted = i == selectedIndex,
                    icon = if (i == selectedIndex) AppIcons.Check else null,
                )
            }
        }
    }
}

private fun trackName(s: Stream): String =
    (s.extendedDisplayTitle ?: s.displayTitle ?: s.languageName).let { t ->
        if (s.forced) "$t (forzados)" else t
    }.let { if (it.length > 48) it.take(47) + "…" else it }

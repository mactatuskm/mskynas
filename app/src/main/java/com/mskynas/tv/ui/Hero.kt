@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.mskynas.tv.data.Directory
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.clearLogo
import com.mskynas.tv.data.episodeLabel
import com.mskynas.tv.data.isEpisode
import com.mskynas.tv.data.progress
import com.mskynas.tv.data.score
import com.mskynas.tv.data.showTitle

/** Lo que muestra la parte superior de la pantalla para el elemento enfocado. */
data class HeroInfo(
    val title: String,
    val logo: String? = null,
    val art: String? = null,
    val kicker: String? = null,
    val score: Double? = null,
    val meta: List<String> = emptyList(),
    val progress: Float? = null,
    val left: String? = null,
    val summary: String? = null,
)

/**
 * Arma el hero de una película, serie o episodio.
 * `full` son los metadatos completos (de la serie, si es un episodio); pueden faltar.
 */
fun heroFor(item: Item, full: Item?): HeroInfo {
    val ep = item.isEpisode
    val meta = buildList {
        when (item.type) {
            "movie" -> {
                (full?.year ?: item.year)?.let { add(it.toString()) }
                fmtDuration(item.duration ?: full?.duration).takeIf { it.isNotBlank() }?.let { add(it) }
            }
            "show" -> {
                (full?.year ?: item.year)?.let { add(it.toString()) }
                (full?.childCount ?: item.childCount)?.let { add(if (it == 1) "1 temporada" else "$it temporadas") }
            }
            "episode" -> fmtDuration(item.duration).takeIf { it.isNotBlank() }?.let { add(it) }
            "collection" -> item.childCount?.let { add("$it títulos") }
            else -> item.year?.let { add(it.toString()) }
        }
        (full?.contentRating ?: item.contentRating)?.takeIf { it.isNotBlank() }?.let { add(it) }
        val genres = (full?.genres ?: item.genres).take(3).joinToString(", ") { it.tag }
        if (genres.isNotBlank()) add(genres)
    }
    val left = item.progress?.let { fmtDuration((item.duration ?: 0L) - (item.viewOffset ?: 0L)) }
    return HeroInfo(
        title = item.showTitle,
        logo = (full?.clearLogo ?: item.clearLogo)?.let { Plex.rawImage(it) },
        art = Plex.image(
            if (ep) full?.art ?: item.grandparentArt ?: item.art else full?.art ?: item.art,
            1280, 720,
        ),
        kicker = if (ep) "${item.episodeLabel}  ·  ${item.title}" else null,
        score = if (ep) item.score ?: full?.score else full?.score ?: item.score,
        meta = meta,
        progress = item.progress,
        left = left?.takeIf { it.isNotBlank() }?.let { "Quedan $it" },
        summary = item.summary?.takeIf { it.isNotBlank() } ?: full?.summary,
    )
}

fun heroFor(dir: Directory): HeroInfo = HeroInfo(
    title = dir.title,
    art = Plex.image(dir.art, 1280, 720),
    meta = listOf(if (dir.type == "movie") "Películas" else "Series"),
)

/** Fondo a pantalla completa con degradados para que el texto y las filas se lean bien. */
@Composable
fun Backdrop(art: String?) {
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = art, animationSpec = tween(450), label = "backdrop") { url ->
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
                    0.3f to Bg.copy(alpha = 0.85f),
                    0.65f to Bg.copy(alpha = 0.25f),
                    1f to Color.Transparent,
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.38f to Bg.copy(alpha = 0.25f),
                    0.58f to Bg.copy(alpha = 0.9f),
                    1f to Bg,
                )
            )
        )
    }
}

/** Logo (o título), datos, progreso y sinopsis del elemento enfocado. */
@Composable
fun HeroText(hero: HeroInfo?, modifier: Modifier = Modifier, summaryLines: Int = 3) {
    Crossfade(targetState = hero, animationSpec = tween(220), modifier = modifier, label = "hero") { h ->
        if (h != null) HeroBody(h, summaryLines)
    }
}

@Composable
private fun HeroBody(h: HeroInfo, summaryLines: Int) {
    Column(Modifier.widthIn(max = 580.dp)) {
        if (h.kicker != null) {
            Text(
                h.kicker,
                color = Dim,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
        }

        var logoFailed by remember(h.logo) { mutableStateOf(false) }
        if (h.logo != null && !logoFailed) {
            AsyncImage(
                model = h.logo,
                contentDescription = h.title,
                modifier = Modifier.heightIn(max = 84.dp).widthIn(max = 340.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onState = { if (it is AsyncImagePainter.State.Error) logoFailed = true },
            )
        } else {
            Text(
                h.title,
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 42.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(12.dp))
        MetaLine(h.score, h.meta)

        if (h.progress != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(h.progress, Modifier.width(150.dp))
                if (h.left != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(h.left, color = Dim, fontSize = 13.sp)
                }
            }
        }

        if (!h.summary.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                h.summary,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                maxLines = summaryLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.mskynas.tv.data.Directory
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.clearLogo
import com.mskynas.tv.data.episodeLabel
import com.mskynas.tv.data.isEpisode
import com.mskynas.tv.data.progress
import com.mskynas.tv.data.qualityBadges
import com.mskynas.tv.data.scoreLabel
import com.mskynas.tv.data.showTitle

/** Lo que muestra la parte superior de la pantalla para el elemento enfocado. */
data class HeroInfo(
    val title: String,
    val logo: String? = null,
    val art: String? = null,
    /** Versión diminuta del arte: de ella sale el color con que se tiñe el fondo. */
    val artTiny: String? = null,
    val kicker: String? = null,
    val contentRating: String? = null,
    val score: String? = null,
    val meta: List<String> = emptyList(),
    val progress: Float? = null,
    val summary: String? = null,
    val cast: List<String> = emptyList(),
    val badges: List<String> = emptyList(),
)

/**
 * Arma el hero de una película, serie o episodio.
 * `full` son los metadatos completos (de la serie, si es un episodio); pueden faltar.
 */
fun heroFor(item: Item, full: Item?): HeroInfo {
    val ep = item.isEpisode
    val genre = (full?.genres ?: item.genres).firstOrNull()?.tag
    val meta = buildList {
        when (item.type) {
            "movie" -> {
                (full?.year ?: item.year)?.let { add(it.toString()) }
                genre?.let { add(it) }
                fmtDuration(item.duration ?: full?.duration).takeIf { it.isNotBlank() }?.let { add(it) }
            }
            "show" -> {
                (full?.year ?: item.year)?.let { add(it.toString()) }
                (full?.childCount ?: item.childCount)?.let { add(if (it == 1) "1 temporada" else "$it temporadas") }
                genre?.let { add(it) }
            }
            "episode" -> {
                add(item.episodeLabel)
                fmtDuration(item.duration).takeIf { it.isNotBlank() }?.let { add(it) }
            }
            "collection" -> item.childCount?.let { add("$it títulos") }
            else -> item.year?.let { add(it.toString()) }
        }
        if (item.progress != null) {
            val left = (item.duration ?: 0L) - (item.viewOffset ?: 0L)
            fmtDuration(left).takeIf { it.isNotBlank() }?.let { add("quedan $it") }
        }
    }
    val artPath = if (ep) full?.art ?: item.grandparentArt ?: item.art else full?.art ?: item.art
    // Las insignias de calidad y el reparto vienen de los metadatos completos del propio título.
    val own = if (ep) null else full
    return HeroInfo(
        title = item.showTitle,
        logo = (full?.clearLogo ?: item.clearLogo)?.let { Plex.rawImage(it) },
        art = Plex.image(artPath, 1280, 720),
        artTiny = Plex.image(artPath, 32, 18),
        kicker = if (ep) item.title else null,
        contentRating = (full?.contentRating ?: item.contentRating)?.takeIf { it.isNotBlank() },
        score = (full ?: item).scoreLabel,
        meta = meta,
        progress = item.progress,
        summary = item.summary?.takeIf { it.isNotBlank() } ?: full?.summary,
        cast = (full?.roles ?: item.roles).take(3).map { it.tag },
        badges = (own ?: item).qualityBadges,
    )
}

fun heroFor(dir: Directory): HeroInfo = HeroInfo(
    title = dir.title,
    art = Plex.image(dir.art, 1280, 720),
    artTiny = Plex.image(dir.art, 32, 18),
    meta = listOf(if (dir.type == "movie") "Películas" else "Series"),
)

/**
 * Color del fondo a partir del arte (como Silo): promedio de una versión diminuta de la imagen,
 * oscurecido para que el texto se lea. Cuesta una imagen de 32×18: casi nada.
 */
@Composable
fun rememberTint(tinyUrl: String?): Color {
    val context = LocalContext.current
    var target by remember { mutableStateOf(Bg) }
    LaunchedEffect(tinyUrl) {
        if (tinyUrl == null) return@LaunchedEffect
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context).data(tinyUrl).allowHardware(false).size(32, 18).build()
        )
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (x in 0 until bitmap.width step 2) for (y in 0 until bitmap.height step 2) {
            val p = bitmap.getPixel(x, y)
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
            n++
        }
        if (n == 0) return@LaunchedEffect
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
        hsv[1] = (hsv[1] * 0.75f).coerceAtMost(0.55f)
        hsv[2] = hsv[2].coerceIn(0.10f, 0.24f)
        target = Color(android.graphics.Color.HSVToColor(hsv))
    }
    val tint by animateColorAsState(target, tween(600), label = "tint")
    return tint
}

/** Fondo: color del arte de base, el arte a la derecha y degradados hacia la izquierda y abajo. */
@Composable
fun Backdrop(art: String?, tint: Color = Bg) {
    Box(Modifier.fillMaxSize().background(tint)) {
        Crossfade(
            targetState = art,
            animationSpec = tween(450),
            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.72f).fillMaxHeight(0.86f),
            label = "backdrop",
        ) { url ->
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
                    0f to tint,
                    0.28f to tint,
                    0.5f to tint.copy(alpha = 0.55f),
                    0.72f to Color.Transparent,
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Transparent,
                    0.86f to tint,
                    1f to tint,
                )
            )
        )
    }
}

/** Logo (o título), clasificación y datos, sinopsis, reparto e insignias del elemento enfocado. */
@Composable
fun HeroText(
    hero: HeroInfo?,
    modifier: Modifier = Modifier,
    summaryLines: Int = 2,
    showScore: Boolean = false,
    castPrefix: String = "",
) {
    Crossfade(targetState = hero, animationSpec = tween(220), modifier = modifier, label = "hero") { h ->
        if (h != null) HeroBody(h, summaryLines, showScore, castPrefix)
    }
}

@Composable
private fun HeroBody(h: HeroInfo, summaryLines: Int, showScore: Boolean, castPrefix: String) {
    Column(Modifier.widthIn(max = 440.dp)) {
        var logoFailed by remember(h.logo) { mutableStateOf(false) }
        if (h.logo != null && !logoFailed) {
            AsyncImage(
                model = h.logo,
                contentDescription = h.title,
                modifier = Modifier.heightIn(max = 72.dp).widthIn(max = 270.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onState = { if (it is AsyncImagePainter.State.Error) logoFailed = true },
            )
        } else {
            Text(
                h.title,
                color = Color.White,
                fontSize = 36.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (h.kicker != null) {
            Spacer(Modifier.height(6.dp))
            Text(h.kicker, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (h.contentRating != null) {
                OutlinedTag(h.contentRating, fontSize = 9.sp)
                Spacer(Modifier.width(7.dp))
            }
            val parts = buildList {
                if (showScore) h.score?.let { add(it) }
                addAll(h.meta)
            }
            Text(
                parts.joinToString(" · "),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!h.summary.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                h.summary,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = summaryLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (h.cast.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                castPrefix + h.cast.joinToString(", "),
                color = Dim,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (h.badges.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                h.badges.forEach { OutlinedTag(it, fontSize = 8.sp) }
            }
        }
    }
}

/** Etiqueta con contorno: clasificación (PG-13) o calidad (4K, DOLBY VISION, ATMOS). */
@Composable
fun OutlinedTag(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    val shape = RoundedCornerShape(3.dp)
    Text(
        text,
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.45f), shape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = Color.White.copy(alpha = 0.9f),
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        maxLines = 1,
    )
}

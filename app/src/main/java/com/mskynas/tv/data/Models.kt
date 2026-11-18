package com.mskynas.tv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Envelope(
    @SerialName("MediaContainer") val container: Container = Container(),
)

@Serializable
data class Container(
    val size: Int = 0,
    @SerialName("Metadata") val metadata: List<Item> = emptyList(),
    @SerialName("Directory") val directories: List<Directory> = emptyList(),
    @SerialName("Hub") val hubs: List<Hub> = emptyList(),
)

/** Una fila del Home de Plex (o un grupo de resultados de búsqueda). */
@Serializable
data class Hub(
    val type: String = "",
    val title: String = "",
    val hubIdentifier: String = "",
    val style: String? = null,
    @SerialName("Metadata") val metadata: List<Item> = emptyList(),
)

/** Continuar viendo / On Deck: se muestran como tarjetas horizontales y reanudan al elegirlas. */
val Hub.isContinue: Boolean
    get() = hubIdentifier.contains("continue", true) || hubIdentifier.contains("ondeck", true)

/** Una biblioteca del servidor (Películas, Series...). */
@Serializable
data class Directory(
    val key: String = "",
    val title: String = "",
    val type: String = "",
    val art: String? = null,
    val thumb: String? = null,
)

@Serializable
data class Item(
    val ratingKey: String = "",
    val key: String = "",
    val guid: String? = null,
    val type: String = "",
    val title: String = "",
    val parentTitle: String? = null,
    val grandparentTitle: String? = null,
    val parentRatingKey: String? = null,
    val grandparentRatingKey: String? = null,
    val index: Int? = null,
    val parentIndex: Int? = null,
    val year: Int? = null,
    val summary: String? = null,
    val contentRating: String? = null,
    val thumb: String? = null,
    val art: String? = null,
    val parentThumb: String? = null,
    val grandparentThumb: String? = null,
    val grandparentArt: String? = null,
    val duration: Long? = null,
    val viewOffset: Long? = null,
    val viewCount: Int? = null,
    val leafCount: Int? = null,
    val viewedLeafCount: Int? = null,
    val childCount: Int? = null,
    val addedAt: Long? = null,
    /** Historial: cuándo se vio. */
    val viewedAt: Long? = null,
    /** Listas (playlists): imagen compuesta de sus títulos. */
    val composite: String? = null,
    val playlistType: String? = null,
    val rating: Double? = null,
    val audienceRating: Double? = null,
    /** De dónde viene la calificación ("imdb://image.rating", "rottentomatoes://…"). */
    val audienceRatingImage: String? = null,
    val titleSort: String? = null,
    val originallyAvailableAt: String? = null,
    @SerialName("Role") val roles: List<Tag> = emptyList(),
    @SerialName("Media") val media: List<Media> = emptyList(),
    @SerialName("Genre") val genres: List<Tag> = emptyList(),
    /** Imágenes extra; aquí viene el logo del título ("clearLogo") cuando Plex lo tiene. */
    @SerialName("Image") val images: List<ImageRef> = emptyList(),
    /** Identificadores externos ("tmdb://123", "imdb://tt…"); se piden con includeGuids=1. */
    @SerialName("Guid") val guids: List<GuidRef> = emptyList(),
)

@Serializable
data class GuidRef(val id: String = "")

/** Número de TMDB del título, si Plex lo tiene. */
val Item.tmdbId: Int? get() = guids.firstOrNull { it.id.startsWith("tmdb://") }?.id?.removePrefix("tmdb://")?.toIntOrNull()

@Serializable
data class ImageRef(
    val type: String = "",
    val url: String = "",
)

@Serializable
data class Media(
    val id: Long = 0,
    val container: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val videoResolution: String? = null,
    val audioChannels: Int? = null,
    @SerialName("Part") val parts: List<Part> = emptyList(),
)

@Serializable
data class Part(
    val id: Long = 0,
    val key: String = "",
    val container: String? = null,
    @SerialName("Stream") val streams: List<Stream> = emptyList(),
)

/** Pista de un archivo: 1 video, 2 audio, 3 subtítulos. */
@Serializable
data class Stream(
    val id: Long = 0,
    val streamType: Int = 0,
    val codec: String? = null,
    val language: String? = null,
    val languageCode: String? = null,
    val languageTag: String? = null,
    val displayTitle: String? = null,
    val extendedDisplayTitle: String? = null,
    val title: String? = null,
    val channels: Int? = null,
    val selected: Boolean = false,
    val forced: Boolean = false,
    @SerialName("DOVIPresent") val dolbyVision: Boolean = false,
    val colorTrc: String? = null,
)

@Serializable
data class Tag(val tag: String = "")

// ---- plex.tv ----

@Serializable
data class Pin(
    val id: Long = 0,
    val code: String = "",
    val authToken: String? = null,
)

@Serializable
data class Resource(
    val name: String = "",
    val provides: String = "",
    val clientIdentifier: String = "",
    val accessToken: String? = null,
    val owned: Boolean = false,
    val connections: List<Connection> = emptyList(),
)

@Serializable
data class Connection(
    val uri: String = "",
    val local: Boolean = false,
    val relay: Boolean = false,
)

/** Un perfil de Plex Home. */
@Serializable
data class HomeUser(
    val id: Long = 0,
    val uuid: String = "",
    val title: String = "",
    val thumb: String? = null,
    val admin: Boolean = false,
    @SerialName("protected") val isProtected: Boolean = false,
    val hasPassword: Boolean = false,
    val restricted: Boolean = false,
) {
    /** Pide PIN para entrar. */
    val locked: Boolean get() = isProtected || hasPassword
}

/** Una fila de pósters (secciones Movies / Series). */
data class RowData(
    val key: String,
    val title: String,
    val items: List<Item>,
    val landscape: Boolean = false,
)

@Serializable
data class HomeData(
    val deck: List<Item>,
    val libraries: List<Directory>,
    val watchlist: List<Item>,
    /** Fila "Recién agregado" por biblioteca. */
    val recent: List<Pair<Directory, List<Item>>> = emptyList(),
    /** Filas del Home de Plex (hubs), en el orden en que Plex las muestra. */
    val hubs: List<Hub> = emptyList(),
)

// ---- Ayudantes de presentación ----

val Item.isEpisode get() = type == "episode"

/** Título "grande": para un episodio, el nombre de la serie. */
val Item.showTitle: String get() = if (isEpisode) grandparentTitle ?: title else title

val Item.episodeLabel: String
    get() = "T${parentIndex ?: "?"} · E${index ?: "?"}"

val Item.posterPath: String?
    get() = when (type) {
        "episode" -> grandparentThumb ?: parentThumb ?: thumb
        "season" -> thumb ?: parentThumb
        "playlist" -> composite ?: thumb
        else -> thumb
    }

val Item.progress: Float?
    get() {
        val d = duration ?: return null
        val o = viewOffset ?: return null
        if (d <= 0 || o <= 0) return null
        return (o.toFloat() / d).coerceIn(0f, 1f)
    }

val Item.clearLogo: String? get() = images.firstOrNull { it.type == "clearLogo" }?.url

/** Calificación del público si existe; si no, la de la crítica. */
val Item.score: Double? get() = (audienceRating ?: rating)?.takeIf { it > 0 }

/** Episodios sin ver de una serie o temporada. */
val Item.unwatchedCount: Int
    get() = if (type == "show" || type == "season") ((leafCount ?: 0) - (viewedLeafCount ?: 0)).coerceAtLeast(0) else 0

val Item.isFullyWatched: Boolean
    get() = when (type) {
        "show", "season" -> (leafCount ?: 0) > 0 && (viewedLeafCount ?: 0) >= (leafCount ?: 0)
        "collection" -> false
        else -> (viewCount ?: 0) > 0 && (viewOffset ?: 0L) == 0L
    }

// ---- Calidad, pistas y reparto (detalle y hero) ----

private val Item.firstPart: Part? get() = media.firstOrNull()?.parts?.firstOrNull()

val Item.videoStream: Stream? get() = firstPart?.streams?.firstOrNull { it.streamType == 1 }
val Item.audioStreams: List<Stream> get() = firstPart?.streams?.filter { it.streamType == 2 }.orEmpty()
val Item.subtitleStreams: List<Stream> get() = firstPart?.streams?.filter { it.streamType == 3 }.orEmpty()
val Item.partId: Long? get() = firstPart?.id

/** Audio elegido (o el primero) y subtítulos elegidos (null = sin subtítulos). */
val Item.selectedAudio: Stream? get() = audioStreams.firstOrNull { it.selected } ?: audioStreams.firstOrNull()
val Item.selectedSubtitle: Stream? get() = subtitleStreams.firstOrNull { it.selected }

/** "4K", "1080P", "720P"… */
val Item.resolutionLabel: String?
    get() = media.firstOrNull()?.videoResolution?.let { r ->
        when {
            r.equals("4k", true) -> "4K"
            r.all { it.isDigit() } -> "${r}P"
            else -> r.uppercase()
        }
    }

/** Insignias de calidad como en la referencia: 4K · DOLBY VISION · ATMOS / DTS-HD MA 5.1. */
val Item.qualityBadges: List<String>
    get() = buildList {
        resolutionLabel?.let { add(it) }
        val video = videoStream
        when {
            video?.dolbyVision == true -> add("DOLBY VISION")
            video?.colorTrc in setOf("smpte2084", "arib-std-b67") -> add("HDR")
        }
        val audio = selectedAudio
        val audioTitle = (audio?.extendedDisplayTitle ?: audio?.displayTitle).orEmpty()
        when {
            audioTitle.contains("atmos", true) -> add("ATMOS")
            audio != null -> audioLabel(audio)?.let { add(it) }
        }
    }

/** "AC3 · 5.1", "DTS-HD MA · 7.1"… */
fun audioLabel(s: Stream): String? {
    val codec = when (s.codec?.lowercase()) {
        null -> null
        "eac3" -> "DD+"
        "ac3" -> "AC3"
        "dca", "dts" -> if ((s.displayTitle ?: "").contains("MA", false)) "DTS-HD MA" else "DTS"
        "truehd" -> "TRUEHD"
        else -> s.codec.uppercase()
    }
    val ch = when (s.channels) {
        null -> null
        1 -> "MONO"
        2 -> "STEREO"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${s.channels} CH"
    }
    return listOfNotNull(codec, ch).joinToString(" ").ifBlank { null }
}

/** Nombre corto del idioma de una pista ("English", "Español"). */
val Stream.languageName: String get() = language ?: languageCode?.uppercase() ?: "Desconocido"

/** "IMDb 9.5" o "★ 7.8" según de dónde venga la calificación. */
val Item.scoreLabel: String?
    get() {
        val v = (audienceRating ?: rating)?.takeIf { it > 0 } ?: return null
        val src = audienceRatingImage.orEmpty()
        return when {
            src.startsWith("imdb") -> "IMDb %.1f".format(v)
            src.startsWith("rottentomatoes") -> "RT %d%%".format((v * 10).toInt())
            src.startsWith("themoviedb") -> "TMDB %.1f".format(v)
            else -> "★ %.1f".format(v)
        }
    }

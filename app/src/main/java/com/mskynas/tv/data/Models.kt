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
)

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
    val rating: Double? = null,
    val audienceRating: Double? = null,
    @SerialName("Media") val media: List<Media> = emptyList(),
    @SerialName("Genre") val genres: List<Tag> = emptyList(),
    /** Imágenes extra; aquí viene el logo del título ("clearLogo") cuando Plex lo tiene. */
    @SerialName("Image") val images: List<ImageRef> = emptyList(),
)

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
    @SerialName("Part") val parts: List<Part> = emptyList(),
)

@Serializable
data class Part(
    val id: Long = 0,
    val key: String = "",
    val container: String? = null,
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

@Serializable
data class HomeData(
    val deck: List<Item>,
    val libraries: List<Directory>,
    val watchlist: List<Item>,
    /** Fila "Recién agregado" por biblioteca. */
    val recent: List<Pair<Directory, List<Item>>> = emptyList(),
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

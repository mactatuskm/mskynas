package com.mskynas.tv.data

import android.net.Uri
import com.mskynas.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Un estreno del Calendar. */
data class CalendarEntry(
    val date: LocalDate,
    val title: String,
    val detail: String,
    val image: String?,
    val isEpisode: Boolean,
    /** Serie de tu servidor (para abrir su detalle). */
    val localKey: String? = null,
    /** Película de tu Watchlist (para buscarla en tu servidor). */
    val watchlistItem: Item? = null,
)

/**
 * TMDB solo para el Calendar (decisión del dueño): próximos episodios de tus series y fecha de
 * estreno en digital de las películas de tu Watchlist. Nada de metadatos ni pósters de TMDB.
 */
object Tmdb {
    private const val BASE = "https://api.themoviedb.org/3"
    private val countries = listOf("MX", "US")

    val configured: Boolean get() = BuildConfig.TMDB_TOKEN.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** TMDB permite muchas peticiones, pero no hace falta saturar la red de la TV. */
    private val gate = Semaphore(8)

    @Serializable
    private data class TvShow(
        val name: String = "",
        @SerialName("next_episode_to_air") val next: Episode? = null,
    )

    @Serializable
    private data class Episode(
        val name: String? = null,
        @SerialName("air_date") val airDate: String? = null,
        @SerialName("season_number") val season: Int = 0,
        @SerialName("episode_number") val episode: Int = 0,
    )

    @Serializable
    private data class ReleaseDates(val results: List<CountryReleases> = emptyList())

    @Serializable
    private data class CountryReleases(
        @SerialName("iso_3166_1") val country: String = "",
        @SerialName("release_dates") val dates: List<Release> = emptyList(),
    )

    @Serializable
    private data class Release(
        @SerialName("release_date") val date: String = "",
        val type: Int = 0,
    )

    @Serializable
    private data class SearchResults(val results: List<SearchHit> = emptyList())

    @Serializable
    private data class SearchHit(val id: Int = 0)

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        gate.withPermit {
            val request = Request.Builder()
                .url("$BASE$path")
                .header("Authorization", "Bearer ${BuildConfig.TMDB_TOKEN}")
                .header("Accept", "application/json")
                .build()
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw IOException("TMDB HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
        }
    }

    private fun parseDate(s: String?): LocalDate? =
        s?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Fecha de estreno en digital (tipo 4): México primero y, si no hay, Estados Unidos. */
    private suspend fun digitalRelease(movieId: Int): LocalDate? {
        val data = json.decodeFromString(ReleaseDates.serializer(), get("/movie/$movieId/release_dates"))
        for (c in countries) {
            val date = data.results.firstOrNull { it.country == c }?.dates
                ?.filter { it.type == 4 }
                ?.mapNotNull { parseDate(it.date) }
                ?.minOrNull()
            if (date != null) return date
        }
        return null
    }

    private suspend fun searchMovie(title: String, year: Int?): Int? {
        val q = "/search/movie?query=${Uri.encode(title)}" + (year?.let { "&year=$it" } ?: "")
        return json.decodeFromString(SearchResults.serializer(), get(q)).results.firstOrNull()?.id
    }

    /**
     * Próximos estrenos desde hoy, en orden de fecha. Cada serie aporta su siguiente episodio;
     * cada película de la Watchlist, su estreno digital (si aún no llega o llegó hace poco).
     */
    suspend fun upcoming(shows: List<Item>, watchlist: List<Item>): List<CalendarEntry> = coroutineScope {
        val today = LocalDate.now()
        val recentLimit = today.minusDays(7)

        val episodes = shows.mapNotNull { show -> show.tmdbId?.let { show to it } }.map { (show, id) ->
            async {
                runCatching {
                    val tv = json.decodeFromString(TvShow.serializer(), get("/tv/$id?language=es-MX"))
                    val next = tv.next ?: return@runCatching null
                    val date = parseDate(next.airDate)?.takeIf { !it.isBefore(today) } ?: return@runCatching null
                    CalendarEntry(
                        date = date,
                        title = show.title,
                        detail = "T${next.season} · E${next.episode}" + (next.name?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        image = Plex.image(show.thumb, 232, 348),
                        isEpisode = true,
                        localKey = show.ratingKey,
                    )
                }.getOrNull()
            }
        }

        val movies = watchlist.filter { it.type == "movie" }.map { movie ->
            async {
                runCatching {
                    val id = movie.tmdbId ?: searchMovie(movie.title, movie.year) ?: return@runCatching null
                    val date = digitalRelease(id)?.takeIf { !it.isBefore(recentLimit) } ?: return@runCatching null
                    CalendarEntry(
                        date = date,
                        title = movie.title,
                        detail = "Estreno en digital",
                        image = Plex.image(movie.thumb, 232, 348),
                        isEpisode = false,
                        watchlistItem = movie,
                    )
                }.getOrNull()
            }
        }

        (episodes + movies).awaitAll().filterNotNull().sortedBy { it.date }
    }
}

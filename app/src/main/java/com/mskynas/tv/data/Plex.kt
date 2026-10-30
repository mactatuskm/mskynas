package com.mskynas.tv.data

import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Cliente mínimo de la API de Plex: solo lo que la app usa.
 * Nada de reseñas, amigos ni contenido en línea de Plex.
 */
object Plex {
    const val PRODUCT = "mskynas"
    private const val VERSION = "1.0"

    lateinit var session: Session
        private set

    /**
     * Sube cada vez que termina una reproducción y su progreso ya quedó guardado.
     * Las pantallas lo observan para refrescar "Continuar viendo" y las barras de progreso.
     */
    var playbackTick by mutableIntStateOf(0)
        private set

    /** Para tareas que deben terminar aunque la pantalla se cierre (reportar progreso). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val quick = http.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    fun init(session: Session) {
        this.session = session
    }

    private fun ascii(s: String) = s.filter { it.code in 32..126 }.ifBlank { "Android TV" }

    private fun baseHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "X-Plex-Product" to PRODUCT,
        "X-Plex-Version" to VERSION,
        "X-Plex-Client-Identifier" to session.clientId,
        "X-Plex-Platform" to "Android",
        "X-Plex-Platform-Version" to ascii(Build.VERSION.RELEASE ?: ""),
        "X-Plex-Device" to "Android TV",
        "X-Plex-Device-Name" to ascii(Build.MODEL ?: ""),
    )

    private suspend fun call(
        url: String,
        token: String?,
        post: Boolean = false,
        client: OkHttpClient = http,
    ): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        baseHeaders().forEach { (k, v) -> builder.header(k, v) }
        if (token != null) builder.header("X-Plex-Token", token)
        if (post) builder.post(ByteArray(0).toRequestBody())
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    // ---------- Inicio de sesión (plex.tv/link) ----------

    suspend fun createPin(): Pin =
        json.decodeFromString(call("https://plex.tv/api/v2/pins?strong=false", null, post = true))

    suspend fun checkPin(id: Long): Pin =
        json.decodeFromString(call("https://plex.tv/api/v2/pins/$id", null))

    suspend fun servers(): List<Resource> =
        json.decodeFromString<List<Resource>>(
            call("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1", session.userToken)
        ).filter { it.provides.contains("server") }

    /** Prueba las conexiones del servidor: primero la red local, luego remota y al final el relay. */
    suspend fun pickConnection(resource: Resource): String? {
        val token = resource.accessToken ?: session.userToken
        val ordered = resource.connections.sortedBy {
            when {
                it.local && !it.relay -> 0
                !it.relay -> 1
                else -> 2
            }
        }
        for (c in ordered) {
            val ok = runCatching { call("${c.uri}/identity", token, client = quick) }.isSuccess
            if (ok) return c.uri
        }
        return null
    }

    /** Si la dirección guardada dejó de funcionar (cambió la IP, etc.), busca otra. */
    suspend fun reconnect(): Boolean {
        val id = session.serverId ?: return false
        val resource = runCatching { servers() }.getOrNull()
            ?.firstOrNull { it.clientIdentifier == id } ?: return false
        val uri = pickConnection(resource) ?: return false
        session.setServer(resource, uri)
        return true
    }

    // ---------- Servidor ----------

    private suspend fun pms(path: String): Container {
        val uri = session.serverUri ?: throw IOException("Sin servidor")
        return json.decodeFromString<Envelope>(call(uri + path, session.serverToken)).container
    }

    suspend fun onDeck(): List<Item> = pms("/library/onDeck").metadata

    suspend fun sections(): List<Directory> =
        pms("/library/sections").directories.filter { it.type == "movie" || it.type == "show" }

    suspend fun sectionItems(key: String): List<Item> = pms("/library/sections/$key/all").metadata

    suspend fun sectionCollections(key: String): List<Item> =
        pms("/library/sections/$key/collections").metadata

    suspend fun collectionItems(ratingKey: String): List<Item> =
        pms("/library/collections/$ratingKey/children").metadata

    suspend fun children(ratingKey: String): List<Item> =
        pms("/library/metadata/$ratingKey/children").metadata

    suspend fun metadata(ratingKey: String): Item? =
        pms("/library/metadata/$ratingKey").metadata.firstOrNull()

    /** Busca en tu servidor un título de la Watchlist (que vive en plex.tv). */
    suspend fun findLocal(guid: String): Item? =
        pms("/library/all?guid=${Uri.encode(guid)}").metadata.firstOrNull()

    // ---------- Watchlist (plex.tv) ----------

    suspend fun watchlist(): List<Item> {
        val hosts = listOf("https://discover.provider.plex.tv", "https://metadata.provider.plex.tv")
        for (host in hosts) {
            val result = runCatching {
                json.decodeFromString<Envelope>(
                    call("$host/library/sections/watchlist/all?includeCollections=1&includeExternalMedia=1", session.userToken)
                ).container.metadata
            }
            if (result.isSuccess) return result.getOrThrow()
        }
        return emptyList()
    }

    // ---------- URLs de imágenes y video ----------

    fun image(path: String?, width: Int, height: Int): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) return path
        val uri = session.serverUri ?: return null
        return "$uri/photo/:/transcode?width=$width&height=$height&minSize=1&upscale=1" +
            "&url=${Uri.encode(path)}&X-Plex-Token=${session.serverToken.orEmpty()}"
    }

    /** Encabezados para las peticiones del reproductor. */
    fun streamHeaders(): Map<String, String> = mapOf(
        "X-Plex-Token" to session.serverToken.orEmpty(),
        "X-Plex-Client-Identifier" to session.clientId,
        "X-Plex-Product" to PRODUCT,
    )

    /** Reproducción directa: el archivo original, sin que el servidor lo convierta. */
    fun directUrl(item: Item): String? {
        val partKey = item.media.firstOrNull()?.parts?.firstOrNull()?.key ?: return null
        val uri = session.serverUri ?: return null
        return "$uri$partKey?X-Plex-Token=${session.serverToken.orEmpty()}"
    }

    /** Respaldo: el servidor convierte a HLS cuando la TV no soporta el formato. */
    fun hlsUrl(ratingKey: String, offsetMs: Long): String {
        val sid = UUID.randomUUID().toString()
        val params = linkedMapOf(
            "hasMDE" to "1",
            "path" to "/library/metadata/$ratingKey",
            "mediaIndex" to "0",
            "partIndex" to "0",
            "protocol" to "hls",
            "fastSeek" to "1",
            "directPlay" to "0",
            "directStream" to "1",
            "directStreamAudio" to "1",
            "videoQuality" to "100",
            "maxVideoBitrate" to "20000",
            "videoResolution" to "1920x1080",
            "subtitleSize" to "100",
            "audioBoost" to "100",
            "location" to "lan",
            "offset" to (offsetMs / 1000).toString(),
            "session" to sid,
            "X-Plex-Session-Identifier" to sid,
            "X-Plex-Client-Identifier" to session.clientId,
            "X-Plex-Product" to PRODUCT,
            "X-Plex-Platform" to "Chrome",
            "X-Plex-Token" to session.serverToken.orEmpty(),
        )
        return session.serverUri.orEmpty() + "/video/:/transcode/universal/start.m3u8?" +
            params.entries.joinToString("&") { "${it.key}=${Uri.encode(it.value)}" }
    }

    // ---------- Progreso ----------

    fun report(ratingKey: String, state: String, timeMs: Long, durationMs: Long) {
        val uri = session.serverUri ?: return
        val token = session.serverToken
        scope.launch {
            runCatching {
                call(
                    "$uri/:/timeline?ratingKey=$ratingKey&key=${Uri.encode("/library/metadata/$ratingKey")}" +
                        "&identifier=com.plexapp.plugins.library&state=$state&time=$timeMs&duration=$durationMs",
                    token,
                )
            }
        }
    }

    /** Al salir del reproductor: guarda la posición y marca como visto si pasó del 90 %. */
    fun finish(ratingKey: String, timeMs: Long, durationMs: Long) {
        val uri = session.serverUri ?: return
        val token = session.serverToken
        scope.launch {
            runCatching {
                call(
                    "$uri/:/timeline?ratingKey=$ratingKey&key=${Uri.encode("/library/metadata/$ratingKey")}" +
                        "&identifier=com.plexapp.plugins.library&state=stopped&time=$timeMs&duration=$durationMs",
                    token,
                )
            }
            if (durationMs > 0 && timeMs >= durationMs * 0.9) {
                runCatching {
                    call("$uri/:/scrobble?identifier=com.plexapp.plugins.library&key=$ratingKey", token)
                }
            }
            playbackTick += 1
        }
    }
}

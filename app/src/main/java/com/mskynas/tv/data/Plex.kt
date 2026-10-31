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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

    /** Para plex.tv (Watchlist): si internet va lento, no se queda esperando 30 s. */
    private val cloud = http.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
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

    suspend fun servers(token: String? = session.userToken): List<Resource> =
        json.decodeFromString<List<Resource>>(
            call("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1", token)
        ).filter { it.provides.contains("server") }

    // ---------- Perfiles (Plex Home) ----------

    /** Perfiles de la cuenta. Si la cuenta no usa Plex Home, devuelve uno solo (o ninguno). */
    suspend fun homeUsers(): List<HomeUser> {
        val element = json.parseToJsonElement(call("https://plex.tv/api/v2/home/users", session.accountToken, client = cloud))
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> element["users"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return array.map { json.decodeFromJsonElement(HomeUser.serializer(), it) }
    }

    enum class SwitchResult { OK, WRONG_PIN, NO_ACCESS, FAILED }

    /**
     * Cambia al perfil: Plex devuelve un token para ese perfil y, con él, se obtiene
     * su acceso al servidor actual (cada perfil tiene su propio progreso y lista).
     */
    suspend fun switchProfile(user: HomeUser, pin: String?): SwitchResult {
        val query = if (pin != null) "?pin=${Uri.encode(pin)}" else ""
        val body = try {
            call("https://plex.tv/api/home/users/${user.id}/switch$query", session.accountToken, post = true)
        } catch (e: IOException) {
            val code = e.message?.removePrefix("HTTP ")?.toIntOrNull()
            return if (pin != null && code != null && code in setOf(401, 403, 422)) SwitchResult.WRONG_PIN else SwitchResult.FAILED
        }
        // La respuesta puede venir en XML o JSON según la versión de plex.tv.
        val token = Regex("authenticationToken=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: Regex("\"(?:authToken|authenticationToken)\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: return SwitchResult.FAILED
        val serverId = session.serverId ?: return SwitchResult.FAILED
        val resources = runCatching { servers(token) }.getOrNull() ?: return SwitchResult.FAILED
        val server = resources.firstOrNull { it.clientIdentifier == serverId } ?: return SwitchResult.NO_ACCESS
        session.setProfile(user, token, server.accessToken ?: token)
        return SwitchResult.OK
    }

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

    /** Continuar viendo: nunca caben más de unas decenas en la fila. */
    suspend fun onDeck(): List<Item> =
        pms("/library/onDeck?X-Plex-Container-Start=0&X-Plex-Container-Size=30").metadata

    suspend fun sections(): List<Directory> =
        pms("/library/sections").directories.filter { it.type == "movie" || it.type == "show" }

    suspend fun sectionItems(key: String): List<Item> = pms("/library/sections/$key/all").metadata

    /** Lo más reciente de una biblioteca (solo los primeros, para una fila del inicio). */
    suspend fun recentlyAdded(key: String, count: Int = 24): List<Item> =
        pms("/library/sections/$key/all?sort=addedAt:desc&X-Plex-Container-Start=0&X-Plex-Container-Size=$count").metadata

    /** Una biblioteca con orden o filtros (p. ej. "sort=audienceRating:desc"), solo los primeros. */
    suspend fun sectionQuery(key: String, query: String, count: Int = 24): List<Item> =
        pms("/library/sections/$key/all?$query&X-Plex-Container-Start=0&X-Plex-Container-Size=$count").metadata

    /** Lists: las listas de video (playlists) del perfil. */
    suspend fun playlists(): List<Item> =
        pms("/playlists?playlistType=video").metadata.filter { it.playlistType == null || it.playlistType == "video" }

    suspend fun playlistItems(ratingKey: String): List<Item> =
        pms("/playlists/$ratingKey/items").metadata

    /**
     * History: lo que vio este perfil, lo más reciente primero (sin repetir títulos).
     * En el servidor, la cuenta principal es la 1 y los perfiles usan su id de Plex.
     */
    suspend fun history(): List<Item> {
        val id = session.profileId?.takeIf { pid -> Cache.profiles.none { it.id == pid && it.admin } } ?: 1L
        return pms("/status/sessions/history/all?sort=viewedAt:desc&accountID=$id&X-Plex-Container-Start=0&X-Plex-Container-Size=120")
            .metadata.distinctBy { it.ratingKey }
    }

    suspend fun sectionCollections(key: String): List<Item> =
        pms("/library/sections/$key/collections").metadata

    suspend fun collectionItems(ratingKey: String): List<Item> =
        pms("/library/collections/$ratingKey/children").metadata

    suspend fun children(ratingKey: String): List<Item> =
        pms("/library/metadata/$ratingKey/children").metadata

    suspend fun metadata(ratingKey: String): Item? =
        pms("/library/metadata/$ratingKey").metadata.firstOrNull()

    /** Metadatos completos con caché en memoria: el hero los pide al mover el foco. */
    suspend fun cachedMetadata(ratingKey: String): Item? {
        Cache.meta[ratingKey]?.let { return it }
        return runCatching { metadata(ratingKey) }.getOrNull()?.also { Cache.meta[ratingKey] = it }
    }

    /** Busca en tu servidor un título de la Watchlist (que vive en plex.tv). */
    suspend fun findLocal(guid: String): Item? =
        pms("/library/all?guid=${Uri.encode(guid)}").metadata.firstOrNull()

    // ---------- Watchlist (plex.tv) ----------

    suspend fun watchlist(): List<Item> {
        val hosts = listOf("https://discover.provider.plex.tv", "https://metadata.provider.plex.tv")
        for (host in hosts) {
            val result = runCatching {
                json.decodeFromString<Envelope>(
                    call(
                        "$host/library/sections/watchlist/all?includeCollections=1&includeExternalMedia=1" +
                            "&X-Plex-Container-Start=0&X-Plex-Container-Size=50",
                        session.userToken,
                        client = cloud,
                    )
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

    /**
     * Imagen original sin pasar por el transcodificador. Para los logos de título, que son PNG
     * con transparencia: el transcodificador de fotos los convertiría a JPG con fondo.
     */
    fun rawImage(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) return path
        val uri = session.serverUri ?: return null
        val sep = if (path.contains("?")) "&" else "?"
        return "$uri$path${sep}X-Plex-Token=${session.serverToken.orEmpty()}"
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

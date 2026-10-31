package com.mskynas.tv.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.mskynas.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Actualizaciones desde GitHub Releases: cada push a main publica "build-N" con mskynas.apk.
 * Si N es mayor que el versionCode instalado, se ofrece actualizar.
 */
object Updater {
    private const val REPO = "mactatuskm/mskynas"
    private const val APK = "mskynas.apk"

    data class Update(
        val versionCode: Int,
        val versionName: String,
        val notes: String,
        val url: String,
        val size: Long,
    )

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tag: String = "",
        val body: String? = null,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
        val size: Long = 0,
    )

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Actualización encontrada y pendiente de mostrar. La ventana de actualización la observa. */
    var pending by mutableStateOf<Update?>(null)

    /** Para no preguntar más de una vez cada vez que se abre la app. */
    var checkedThisSession = false

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Devuelve la actualización disponible, o null si ya tienes la más reciente (o no hay red). */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            val release = http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                json.decodeFromString(Release.serializer(), r.body?.string().orEmpty())
            }
            val code = release.tag.removePrefix("build-").toIntOrNull() ?: return@runCatching null
            if (code <= BuildConfig.VERSION_CODE) return@runCatching null
            val asset = release.assets.firstOrNull { it.name == APK } ?: return@runCatching null
            Update(code, "1.0.$code", cleanNotes(release.body), asset.url, asset.size)
        }.getOrNull()
    }

    /** Las notas son el mensaje del commit; se quitan las líneas técnicas del final. */
    private fun cleanNotes(body: String?): String =
        body.orEmpty().lines()
            .filterNot { it.startsWith("Co-Authored-By") || it.startsWith("Claude-Session") }
            .joinToString("\n")
            .trim()

    /** Descarga el APK al caché de la app, informando el avance de 0 a 1. */
    suspend fun download(context: Context, update: Update, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, APK)
            val request = Request.Builder().url(update.url).build()
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val body = r.body ?: throw IOException("Respuesta vacía")
                val total = body.contentLength().takeIf { it > 0 } ?: update.size
                var read = 0L
                var lastReported = -1
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt()
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(pct / 100f)
                                }
                            }
                        }
                    }
                }
            }
            file
        }

    /** Android pide permiso para que una app instale otras (aquí: sus propias actualizaciones). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Abre la pantalla del permiso. Devuelve false si Fire OS no la tiene accesible así. */
    fun openInstallPermission(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    /** Abre el instalador del sistema con el APK descargado. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

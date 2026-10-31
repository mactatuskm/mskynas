package com.mskynas.tv.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Caché en disco: guarda lo último que se cargó del inicio y los perfiles para que,
 * al abrir la app, se muestren al instante (sin esperar al servidor) mientras se
 * actualizan en segundo plano. Es pequeño (JSON de unos cuantos KB).
 */
object Store {
    private lateinit var dir: File
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun init(context: Context) {
        dir = File(context.filesDir, "store").apply { mkdirs() }
    }

    /** Cada perfil y servidor tiene su propio inicio (su Continuar viendo, su lista). */
    private fun homeFile(): File {
        val s = Plex.session
        val profile = s.profileId?.toString() ?: "main"
        val server = s.serverId ?: "none"
        return File(dir, "home-$server-$profile.json")
    }

    fun loadHome(): HomeData? = runCatching {
        json.decodeFromString(HomeData.serializer(), homeFile().readText())
    }.getOrNull()

    fun saveHome(data: HomeData) {
        runCatching { write(homeFile(), json.encodeToString(HomeData.serializer(), data)) }
    }

    fun loadProfiles(): List<HomeUser> = runCatching {
        json.decodeFromString(ListSerializer(HomeUser.serializer()), File(dir, "profiles.json").readText())
    }.getOrDefault(emptyList())

    fun saveProfiles(list: List<HomeUser>) {
        runCatching { write(File(dir, "profiles.json"), json.encodeToString(ListSerializer(HomeUser.serializer()), list)) }
    }

    /** Al cerrar sesión no debe quedar nada de la cuenta anterior. */
    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    /** Escribe a un archivo temporal y lo renombra: nunca queda un JSON a medias. */
    private fun write(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }
}

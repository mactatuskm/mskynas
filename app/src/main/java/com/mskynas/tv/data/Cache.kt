package com.mskynas.tv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Caché en memoria: al volver a una pantalla se muestra al instante lo último
 * que se cargó mientras se refresca en segundo plano.
 */
object Cache {
    /** Observable: la barra lateral lee de aquí las bibliotecas. */
    var home by mutableStateOf<HomeData?>(null)

    val titles = HashMap<String, String>()
    val library = HashMap<String, List<Item>>()
    val collections = HashMap<String, List<Item>>()
    val collectionItems = HashMap<String, List<Item>>()

    /** Filas de "Recommended" por sección (movie / show). */
    val recommended = HashMap<String, List<RowData>>()
    var playlists: List<Item>? = null
    var history: List<Item>? = null
    var calendar: List<CalendarEntry>? = null

    /** Metadatos completos (logo, géneros, calificación) para el hero. */
    val meta = HashMap<String, Item>()

    /** Perfiles de Plex Home (no se borran al cambiar de perfil). */
    var profiles: List<HomeUser> = emptyList()

    /** Último elemento enfocado por pantalla, para regresar al mismo lugar. */
    val lastFocus = HashMap<String, String>()

    /** Temporada elegida por serie. */
    val season = HashMap<String, String>()

    /** Al cambiar de perfil: lo del perfil nuevo se lee del disco si ya existe. */
    fun clear() {
        home = null
        titles.clear(); library.clear(); collections.clear(); collectionItems.clear()
        meta.clear(); lastFocus.clear(); season.clear()
        recommended.clear(); playlists = null; history = null; calendar = null
    }
}

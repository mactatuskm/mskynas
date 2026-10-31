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

    /** Contenido de cada pestaña del inicio. */
    val tabs = HashMap<HomeTab, TabContent>()

    /** Todas las colecciones de todas las bibliotecas. */
    var allCollections: List<Item>? = null

    /** Metadatos completos (logo, géneros, calificación) para el hero. */
    val meta = HashMap<String, Item>()

    /** Perfiles de Plex Home (no se borran al cambiar de perfil). */
    var profiles: List<HomeUser> = emptyList()

    /** Último elemento enfocado por pantalla, para regresar al mismo lugar. */
    val lastFocus = HashMap<String, String>()

    /** Temporada elegida por serie. */
    val season = HashMap<String, String>()

    fun clear() {
        home = null
        titles.clear(); library.clear(); collections.clear(); collectionItems.clear()
        meta.clear(); lastFocus.clear(); season.clear()
        tabs.clear(); allCollections = null
    }
}

package com.mskynas.tv.data

/**
 * Caché en memoria: al volver a una pantalla se muestra al instante lo último
 * que se cargó mientras se refresca en segundo plano.
 */
object Cache {
    var home: HomeData? = null
    val titles = HashMap<String, String>()
    val library = HashMap<String, List<Item>>()
    val collections = HashMap<String, List<Item>>()
    val collectionItems = HashMap<String, List<Item>>()

    /** Último elemento enfocado por pantalla, para regresar al mismo lugar. */
    val lastFocus = HashMap<String, String>()

    /** Temporada elegida por serie. */
    val season = HashMap<String, String>()

    fun clear() {
        home = null
        titles.clear(); library.clear(); collections.clear(); collectionItems.clear()
        lastFocus.clear(); season.clear()
    }
}

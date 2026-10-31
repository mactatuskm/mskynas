package com.mskynas.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.Session
import com.mskynas.tv.data.Store
import com.mskynas.tv.ui.AppNav
import com.mskynas.tv.ui.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Plex.init(Session(applicationContext))
        Store.init(applicationContext)
        setupImages()
        setContent {
            AppTheme {
                AppNav()
            }
        }
    }

    /**
     * Pósters y fondos se guardan en el almacenamiento de la TV: la segunda vez que
     * abres la app ya no se descargan. El servidor de Plex a veces marca sus imágenes
     * como "no guardar"; aquí se ignora eso, porque un póster no cambia de un día a otro.
     */
    private fun setupImages() {
        val loader = ImageLoader.Builder(applicationContext)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(applicationContext).maxSizePercent(0.20).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("images"))
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            .build()
        Coil.setImageLoader(loader)
    }
}

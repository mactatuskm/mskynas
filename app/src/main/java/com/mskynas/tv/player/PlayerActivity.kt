package com.mskynas.tv.player

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.Session
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reproductor de pantalla completa, sin Compose: PlayerView con SurfaceView,
 * la ruta más directa y eficiente para el video en la TV.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_KEY = "ratingKey"
        private const val EXTRA_RESUME = "resume"

        fun start(context: Context, ratingKey: String, resume: Boolean) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_KEY, ratingKey)
                    .putExtra(EXTRA_RESUME, resume)
            )
        }
    }

    private lateinit var view: PlayerView
    private var player: ExoPlayer? = null
    private var item: Item? = null

    /** Al transcodificar, el servidor arranca el video en este punto y el reproductor cuenta desde 0. */
    private var offsetMs = 0L
    private var transcoding = false
    private var progressJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Plex.init(Session(applicationContext))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        view = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            useController = true
            controllerShowTimeoutMs = 4000
            setShowSubtitleButton(true)
            keepScreenOn = true
        }
        setContentView(view)

        // "Atrás" primero oculta los controles; si ya están ocultos, sale.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (view.isControllerFullyVisible) view.hideController() else finish()
            }
        })

        val key = intent.getStringExtra(EXTRA_KEY) ?: return finish()
        val resume = intent.getBooleanExtra(EXTRA_RESUME, false)

        lifecycleScope.launch {
            val m = runCatching { Plex.metadata(key) }.getOrNull()
            if (m == null) {
                Toast.makeText(this@PlayerActivity, "No pude cargar este título.", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            item = m
            startPlayback(m, if (resume) m.viewOffset ?: 0L else 0L)
        }
    }

    private fun startPlayback(m: Item, startMs: Long) {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(Plex.streamHeaders())
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        player = p
        view.player = p

        val direct = Plex.directUrl(m)
        if (direct != null) {
            p.setMediaItem(MediaItem.fromUri(direct), startMs)
        } else {
            transcoding = true
            offsetMs = startMs
            p.setMediaItem(hls(m, startMs))
        }

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!transcoding) {
                    // La TV no pudo con el archivo original: pedimos al servidor que lo convierta.
                    val from = if (p.currentPosition > 0) p.currentPosition else startMs
                    transcoding = true
                    offsetMs = from
                    Toast.makeText(
                        this@PlayerActivity,
                        "Formato no compatible: el servidor lo está convirtiendo",
                        Toast.LENGTH_LONG,
                    ).show()
                    p.setMediaItem(hls(m, from))
                    p.prepare()
                    p.playWhenReady = true
                } else {
                    Toast.makeText(
                        this@PlayerActivity,
                        "No se pudo reproducir (${error.errorCodeName}).",
                        Toast.LENGTH_LONG,
                    ).show()
                    finish()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) finish()
            }
        })

        p.prepare()
        p.playWhenReady = true
        view.requestFocus()

        // Reporta el progreso cada 10 s para que "Continuar viendo" quede al día.
        progressJob = lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                val current = player ?: break
                Plex.report(m.ratingKey, if (current.isPlaying) "playing" else "paused", position(), duration(m))
            }
        }
    }

    private fun hls(m: Item, fromMs: Long): MediaItem =
        MediaItem.Builder()
            .setUri(Plex.hlsUrl(m.ratingKey, fromMs))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

    private fun position(): Long = offsetMs + (player?.currentPosition ?: 0L).coerceAtLeast(0L)

    private fun duration(m: Item): Long = m.duration ?: player?.duration?.coerceAtLeast(0L) ?: 0L

    // Envía las teclas del control al reproductor (play/pausa, adelantar, etc.).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        view.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun onStop() {
        super.onStop()
        // Si sales con el botón Inicio, el video se pausa.
        player?.pause()
    }

    override fun onDestroy() {
        progressJob?.cancel()
        val m = item
        val p = player
        if (m != null && p != null) {
            val pos = position()
            val total = duration(m)
            p.release()
            Plex.finish(m.ratingKey, pos, if (total > 0) total else pos)
        }
        player = null
        super.onDestroy()
    }
}

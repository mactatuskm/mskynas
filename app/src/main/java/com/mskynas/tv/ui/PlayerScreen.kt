@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(ratingKey: String, resume: Boolean, onExit: () -> Unit) {
    var item by remember { mutableStateOf<Item?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ratingKey) {
        val m = runCatching { Plex.metadata(ratingKey) }.getOrNull()
        if (m == null) failure = "No pude cargar este título." else item = m
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val current = item
        when {
            failure != null -> MessageBox(failure.orEmpty(), listOf(Action("Volver", onExit)))
            current == null -> LoadingBox()
            else -> VideoPlayer(current, resume, onExit, onFail = { failure = it })
        }
    }
}

private class ViewRef {
    var view: PlayerView? = null
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(item: Item, resume: Boolean, onExit: () -> Unit, onFail: (String) -> Unit) {
    val context = LocalContext.current
    val duration = item.duration ?: 0L
    val startMs = if (resume) item.viewOffset ?: 0L else 0L

    // Al transcodificar, el servidor arranca el video en `offsetMs`; el reproductor cuenta desde 0.
    var offsetMs by remember { mutableLongStateOf(0L) }
    var transcoding by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val ref = remember { ViewRef() }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(Plex.streamHeaders())
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
    }

    fun position(): Long = offsetMs + player.currentPosition.coerceAtLeast(0L)

    fun hls(fromMs: Long): MediaItem =
        MediaItem.Builder()
            .setUri(Plex.hlsUrl(item.ratingKey, fromMs))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

    DisposableEffect(player) {
        val direct = Plex.directUrl(item)
        if (direct != null) {
            player.setMediaItem(MediaItem.fromUri(direct), startMs)
        } else {
            transcoding = true
            offsetMs = startMs
            player.setMediaItem(hls(startMs))
        }
        player.prepare()
        player.playWhenReady = true

        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!transcoding) {
                    // La TV no pudo con el archivo original: pedimos al servidor que lo convierta.
                    val from = if (player.currentPosition > 0) player.currentPosition else startMs
                    transcoding = true
                    offsetMs = from
                    notice = "Formato no compatible con esta TV: el servidor lo está convirtiendo"
                    player.setMediaItem(hls(from))
                    player.prepare()
                    player.playWhenReady = true
                } else {
                    onFail("No se pudo reproducir (${error.errorCodeName}).")
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onExit()
            }
        }
        player.addListener(listener)

        onDispose {
            val pos = position()
            player.removeListener(listener)
            player.release()
            Plex.finish(item.ratingKey, pos, if (duration > 0) duration else pos)
        }
    }

    // Reporta el progreso cada 10 s para que "Continuar viendo" quede al día.
    LaunchedEffect(player) {
        while (true) {
            delay(10_000)
            Plex.report(
                item.ratingKey,
                if (player.isPlaying) "playing" else "paused",
                position(),
                duration,
            )
        }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(5000)
            notice = null
        }
    }

    // "Atrás" primero oculta los controles; si ya están ocultos, sale.
    BackHandler {
        val view = ref.view
        if (view != null && view.isControllerFullyVisible) view.hideController() else onExit()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 4000
                    keepScreenOn = true
                    setShowSubtitleButton(true)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    ref.view = this
                    post { requestFocus() }
                }
            },
        )
        notice?.let {
            Text(
                it,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                color = Color.White,
                fontSize = 16.sp,
            )
        }
    }
}

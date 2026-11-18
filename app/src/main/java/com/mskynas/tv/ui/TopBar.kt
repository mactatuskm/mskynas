@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.mskynas.tv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Plex

/** Claves de sección para marcar en la barra superior dónde estás (mapa de pantallas). */
object Sections {
    const val HOME = "home"
    const val SEARCH = "search"
    const val MOVIES = "movie"
    const val SERIES = "show"
    const val CALENDAR = "calendar"
}

/**
 * Envuelve una pantalla con la barra superior (referencia: Silo): logo a la izquierda,
 * búsqueda y secciones al centro, tu avatar a la derecha.
 * `overlay = true` deja que el contenido pase por debajo de la barra (el hero del Home).
 * En el Home, Atrás primero deja actuar a la pantalla (`onBack`), luego sube el foco a la barra
 * y, si ya está ahí, pregunta si quieres salir.
 */
@Composable
fun AppScaffold(
    nav: NavController,
    current: String,
    overlay: Boolean = false,
    onBack: (() -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
    val selectedRequester = remember { FocusRequester() }
    var barFocused by remember { mutableStateOf(false) }
    var askExit by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    BackHandler(enabled = current == Sections.HOME) {
        when {
            barFocused -> askExit = true
            onBack?.invoke() == true -> Unit
            else -> runCatching { selectedRequester.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(top = if (overlay) 0.dp else 40.dp)) { content() }
        TopBar(nav, current, selectedRequester, onFocusChange = { barFocused = it })
    }

    if (askExit) {
        ConfirmDialog(
            title = "¿Salir de mskynas?",
            confirm = "Salir",
            onConfirm = { activity?.finish() },
            onDismiss = { askExit = false },
        )
    }
}

@Composable
private fun TopBar(
    nav: NavController,
    current: String,
    selectedRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val tabs = listOf(
        Sections.HOME to "Home",
        Sections.MOVIES to "Movies",
        Sections.SERIES to "Series",
        Sections.CALENDAR to "Calendar",
    )
    val hasSelected = current == Sections.SEARCH || tabs.any { it.first == current }

    fun go(section: String) = when (section) {
        Sections.HOME -> nav.goHome()
        Sections.SEARCH -> nav.goSection(Routes.SEARCH)
        Sections.CALENDAR -> nav.goSection(Routes.CALENDAR)
        else -> nav.goSection(Routes.section(section))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(Brush.verticalGradient(listOf(Bg.copy(alpha = 0.75f), Color.Transparent)))
            .onFocusChanged { onFocusChange(it.hasFocus) }
            // Al subir a la barra, el foco cae en la sección actual.
            .focusProperties { enter = { if (hasSelected) selectedRequester else FocusRequester.Default } }
            .focusGroup()
            .padding(horizontal = ContentStart),
    ) {
        Wordmark(Modifier.align(Alignment.CenterStart))

        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavPill(
                selected = current == Sections.SEARCH,
                requester = if (current == Sections.SEARCH) selectedRequester else null,
                onClick = { go(Sections.SEARCH) },
            ) {
                Icon(AppIcons.Search, contentDescription = "Search", modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(2.dp))
            tabs.forEach { (section, label) ->
                NavPill(
                    selected = current == section,
                    requester = if (current == section) selectedRequester else null,
                    onClick = { go(section) },
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (current == section) FontWeight.Bold else FontWeight.SemiBold,
                    )
                }
            }
        }

        // Tu avatar: abre el menú del perfil.
        val (title, thumb) = currentProfile()
        Surface(
            onClick = { menuOpen = true },
            modifier = Modifier.align(Alignment.CenterEnd).size(30.dp),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Chip, focusedContainerColor = Chip),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White), shape = CircleShape)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        ) {
            Avatar(title, thumb, Modifier.fillMaxSize(), initialSize = 13.sp)
        }
    }

    if (menuOpen) ProfileMenu(nav, onDismiss = { menuOpen = false })
}

/** Logo provisional: marca morada + "mskynas" (se reemplaza por el tuyo). */
@Composable
private fun Wordmark(modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(20.dp).background(Accent, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
        }
        Spacer(Modifier.width(7.dp))
        Text("mskynas", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/** Pestaña de la barra: gris translúcido si es la actual, blanca al enfocarla. */
@Composable
private fun NavPill(
    selected: Boolean,
    requester: FocusRequester?,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(28.dp)
            .let { if (requester != null) it.focusRequester(requester) else it },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Chip else Color.Transparent,
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            focusedContainerColor = Color.White,
            focusedContentColor = Bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(Modifier.padding(horizontal = 15.dp).height(28.dp), contentAlignment = Alignment.Center) { content() }
    }
}

/** Nombre y foto del perfil activo (o de la cuenta principal). */
fun currentProfile(): Pair<String, String?> {
    val admin = Cache.profiles.firstOrNull { it.admin }
    val title = Plex.session.profileTitle ?: admin?.title ?: "Perfil"
    val thumb = Plex.session.profileThumb ?: admin?.thumb
    return title to thumb
}

@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)

package com.mskynas.tv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Plex

/** Claves de sección para marcar en la barra lateral dónde estás (mapa de pantallas). */
object Sections {
    const val HOME = "home"
    const val SEARCH = "search"
    const val MOVIES = "movie"
    const val SERIES = "show"
    const val CALENDAR = "calendar"
}

/**
 * Envuelve una pantalla con la barra lateral flotando a la izquierda.
 * En el inicio, Atrás primero lleva el foco a la barra; si ya está ahí, pregunta si quieres salir.
 */
@Composable
fun RailScaffold(nav: NavController, current: String, content: @Composable () -> Unit) {
    val selectedRequester = remember { FocusRequester() }
    var railFocused by remember { mutableStateOf(false) }
    var askExit by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    BackHandler(enabled = current == Sections.HOME) {
        if (railFocused) {
            askExit = true
            return@BackHandler
        }
        runCatching { selectedRequester.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        SideRail(nav, current, selectedRequester, onFocusChange = { railFocused = it })
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
private fun SideRail(
    nav: NavController,
    current: String,
    selectedRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (expanded) 240.dp else RailCollapsed,
        animationSpec = tween(160),
        label = "rail",
    )
    var menuOpen by remember { mutableStateOf(false) }
    val entries = listOf(
        Triple(Sections.SEARCH, AppIcons.Search, "Search"),
        Triple(Sections.HOME, AppIcons.Home, "Home"),
        Triple(Sections.MOVIES, AppIcons.Movie, "Movies"),
        Triple(Sections.SERIES, AppIcons.Tv, "Series"),
        Triple(Sections.CALENDAR, AppIcons.Calendar, "Calendar"),
    )
    val hasSelected = entries.any { it.first == current }

    Column(
        Modifier
            .fillMaxHeight()
            .width(width)
            .background(
                Brush.horizontalGradient(
                    if (expanded) listOf(Bg, Bg.copy(alpha = 0.94f))
                    else listOf(Bg.copy(alpha = 0.92f), Bg.copy(alpha = 0f))
                )
            )
            .onFocusChanged {
                expanded = it.hasFocus
                onFocusChange(it.hasFocus)
            }
            // Al entrar a la barra, el foco cae en la sección actual (no en la más cercana).
            .focusProperties { enter = { if (hasSelected) selectedRequester else FocusRequester.Default } }
            .focusGroup()
            .padding(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Tu avatar arriba: abre el menú del perfil.
        val (title, thumb) = currentProfile()
        RailItem(
            icon = null,
            label = title,
            selected = false,
            expanded = expanded,
            requester = null,
            avatar = thumb to title,
        ) { menuOpen = true }
        Spacer(Modifier.weight(1f))

        entries.forEach { (section, icon, label) ->
            RailItem(icon, label, current == section, expanded, if (section == current) selectedRequester else null) {
                when (section) {
                    Sections.HOME -> nav.goHome()
                    Sections.CALENDAR -> nav.goSection(Routes.CALENDAR)
                    Sections.SEARCH -> nav.goSection(Routes.SEARCH)
                    else -> nav.goSection(Routes.section(section))
                }
            }
        }
        Spacer(Modifier.weight(1.4f))
    }

    if (menuOpen) ProfileMenu(nav, onDismiss = { menuOpen = false })
}

/** Nombre y foto del perfil activo (o de la cuenta principal). */
fun currentProfile(): Pair<String, String?> {
    val admin = Cache.profiles.firstOrNull { it.admin }
    val title = Plex.session.profileTitle ?: admin?.title ?: "Perfil"
    val thumb = Plex.session.profileThumb ?: admin?.thumb
    return title to thumb
}

@Composable
private fun RailItem(
    icon: ImageVector?,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    requester: FocusRequester?,
    avatar: Pair<String?, String>? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .let { if (requester != null) it.focusRequester(requester) else it },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
            contentColor = if (selected) Color.White else Dim,
            focusedContainerColor = Color.White,
            focusedContentColor = Bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (avatar != null) {
                Avatar(avatar.second, avatar.first, Modifier.size(26.dp), initialSize = 12.sp)
            } else if (icon != null) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            }
            if (expanded) {
                Spacer(Modifier.width(16.dp))
                Text(
                    label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

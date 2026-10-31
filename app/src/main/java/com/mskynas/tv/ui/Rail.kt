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

/** Claves de sección para marcar en el menú dónde estás. */
object Sections {
    const val HOME = "home"
    const val SEARCH = "search"
    const val EXPLORE = "explore"
    const val COLLECTIONS = "collections"
    const val SETTINGS = "settings"
}

/**
 * Envuelve una pantalla con el menú lateral flotando a la izquierda.
 * En el inicio, Atrás primero deja que la pantalla lo use (`onBack`, p. ej. volver arriba),
 * luego lleva el foco al menú y, si ya está ahí, pregunta si quieres salir.
 */
@Composable
fun RailScaffold(
    nav: NavController,
    current: String,
    onBack: (() -> Boolean)? = null,
    content: @Composable () -> Unit,
) {
    val selectedRequester = remember { FocusRequester() }
    var railFocused by remember { mutableStateOf(false) }
    var askExit by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    BackHandler(enabled = current == Sections.HOME) {
        if (railFocused) {
            askExit = true
            return@BackHandler
        }
        if (onBack?.invoke() == true) return@BackHandler
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

private class RailEntry(val section: String, val icon: ImageVector, val label: String, val go: () -> Unit)

@Composable
private fun SideRail(
    nav: NavController,
    current: String,
    selectedRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (expanded) 230.dp else RailCollapsed,
        animationSpec = tween(160),
        label = "rail",
    )
    val entries = listOf(
        RailEntry(Sections.SEARCH, AppIcons.Search, "Buscar") { nav.goSection(Routes.SEARCH) },
        RailEntry(Sections.HOME, AppIcons.Home, "Inicio") { nav.goHome() },
        RailEntry(Sections.EXPLORE, AppIcons.Grid, "Explorar") { nav.goSection(Routes.EXPLORE) },
        RailEntry(Sections.COLLECTIONS, AppIcons.Collections, "Colecciones") { nav.goSection(Routes.COLLECTIONS) },
        RailEntry(Sections.SETTINGS, AppIcons.Settings, "Ajustes") { nav.goSection(Routes.SETTINGS) },
    )
    val hasSelected = entries.any { it.section == current }
    val showProfile = Cache.profiles.size > 1

    Column(
        Modifier
            .fillMaxHeight()
            .width(width)
            .background(
                Brush.horizontalGradient(
                    if (expanded) listOf(Bg, Bg.copy(alpha = 0.95f))
                    else listOf(Bg.copy(alpha = 0.7f), Bg.copy(alpha = 0f))
                )
            )
            .onFocusChanged {
                expanded = it.hasFocus
                onFocusChange(it.hasFocus)
            }
            // Al entrar al menú, el foco cae en la sección actual (no en la más cercana).
            .focusProperties { enter = { if (hasSelected) selectedRequester else FocusRequester.Default } }
            .focusGroup()
            .padding(horizontal = 10.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Perfil arriba: tu avatar; al elegirlo vuelves a "¿Quién está viendo?".
        if (showProfile) {
            val title = Plex.session.profileTitle ?: Cache.profiles.firstOrNull { it.admin }?.title ?: "Perfil"
            val thumb = Plex.session.profileThumb ?: Cache.profiles.firstOrNull { it.admin }?.thumb
            RailItem(
                icon = null,
                label = title,
                selected = false,
                expanded = expanded,
                requester = null,
                avatar = thumb to title,
            ) { nav.navigate(Routes.PROFILES) }
        } else {
            LogoMark()
        }
        Spacer(Modifier.height(34.dp))

        entries.forEach { e ->
            RailItem(
                icon = e.icon,
                label = e.label,
                selected = e.section == current,
                expanded = expanded,
                requester = if (e.section == current) selectedRequester else null,
                onClick = e.go,
            )
        }
    }
}

@Composable
private fun LogoMark() {
    Box(
        Modifier
            .padding(start = 7.dp)
            .size(30.dp)
            .background(Accent, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Play, contentDescription = "mskynas", modifier = Modifier.size(18.dp), tint = Color.White)
    }
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
            .height(44.dp)
            .let { if (requester != null) it.focusRequester(requester) else it },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
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
                Avatar(avatar.second, avatar.first, Modifier.size(30.dp).padding(0.dp), initialSize = 13.sp)
            } else if (icon != null) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            }
            if (expanded) {
                Spacer(Modifier.width(16.dp))
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

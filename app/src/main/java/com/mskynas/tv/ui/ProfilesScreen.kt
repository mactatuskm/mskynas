@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.HomeUser
import com.mskynas.tv.data.Plex
import kotlinx.coroutines.launch

/** ¿Es el perfil que ya está activo? Si nunca se eligió, el activo es el de la cuenta principal. */
private fun isActive(u: HomeUser): Boolean {
    val id = Plex.session.profileId
    return if (id == null) u.admin else u.id == id
}

/**
 * "¿Quién está viendo?": aparece al abrir la app. Si la cuenta no tiene perfiles de
 * Plex Home (o no se pueden cargar), pasa directo al inicio.
 */
@Composable
fun ProfilesScreen(nav: NavController) {
    var users by remember { mutableStateOf(Cache.profiles.takeIf { it.size > 1 }) }
    var pinFor by remember { mutableStateOf<HomeUser?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun goHome() = nav.navigate(Routes.HOME) { popUpTo(nav.graph.id) { inclusive = true } }

    LaunchedEffect(Unit) {
        val list = runCatching { Plex.homeUsers() }.getOrNull()
        when {
            list != null && list.size > 1 -> {
                users = list
                Cache.profiles = list
            }
            list != null -> {
                Cache.profiles = list
                goHome()
            }
            users == null -> goHome() // sin red hacia plex.tv: no bloquear la app
        }
    }

    fun select(u: HomeUser, pin: String? = null) {
        if (busy) return
        if (isActive(u)) {
            goHome()
            return
        }
        if (u.locked && pin == null) {
            pinError = null
            pinFor = u
            return
        }
        scope.launch {
            busy = true
            status = "Entrando como ${u.title}…"
            when (Plex.switchProfile(u, pin)) {
                Plex.SwitchResult.OK -> {
                    Cache.clear()
                    pinFor = null
                    goHome()
                }
                Plex.SwitchResult.WRONG_PIN -> {
                    pinError = "PIN incorrecto"
                    status = null
                }
                Plex.SwitchResult.NO_ACCESS -> {
                    pinFor = null
                    status = "${u.title} no tiene acceso a ${Plex.session.serverName ?: "este servidor"}."
                }
                Plex.SwitchResult.FAILED -> {
                    pinFor = null
                    status = "No pude cambiar de perfil. Intenta de nuevo."
                }
            }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val list = users
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("¿Quién está viendo?", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(44.dp))
            if (list == null) {
                Text("Cargando perfiles…", color = Dim, fontSize = 16.sp)
            } else {
                val focusIndex = list.indexOfFirst { isActive(it) }.coerceAtLeast(0)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    itemsIndexed(list, key = { _, u -> u.id }) { i, u ->
                        ProfileTile(u, focus = i == focusIndex, onClick = { select(u) })
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(status ?: " ", color = Dim, fontSize = 15.sp)
        }
    }

    pinFor?.let { u ->
        PinDialog(
            user = u,
            error = pinError,
            busy = busy,
            onPin = { select(u, it) },
            onDismiss = { if (!busy) pinFor = null },
        )
    }
}

@Composable
private fun ProfileTile(u: HomeUser, focus: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(132.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(112.dp)
                .initialFocus(focus)
                .onFocusChanged { focused = it.isFocused || it.hasFocus },
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Accent, focusedContainerColor = Accent),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(4.dp, Color.White), shape = CircleShape)
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Accent.copy(alpha = 0.6f), elevation = 16.dp)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        ) {
            Avatar(u.title, u.thumb, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                u.title,
                color = if (focused) Color.White else Dim,
                fontSize = 16.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (u.locked) {
                Spacer(Modifier.width(6.dp))
                Icon(AppIcons.Lock, contentDescription = "Con PIN", modifier = Modifier.size(14.dp), tint = Dim)
            }
        }
    }
}

/** Foto del perfil, o su inicial sobre morado si no tiene. */
@Composable
fun Avatar(name: String, thumb: String?, modifier: Modifier = Modifier, initialSize: TextUnit = 40.sp) {
    Box(modifier.clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1).uppercase(),
            color = Color.White,
            fontSize = initialSize,
            fontWeight = FontWeight.Bold,
        )
        if (!thumb.isNullOrBlank()) {
            AsyncImage(
                model = thumb,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** Teclado numérico para el PIN de 4 dígitos (los controles de Fire TV no tienen números). */
@Composable
private fun PinDialog(
    user: HomeUser,
    error: String?,
    busy: Boolean,
    onPin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var digits by remember { mutableStateOf("") }

    fun press(key: String) {
        if (busy) return
        digits = when (key) {
            "⌫" -> digits.dropLast(1)
            else -> if (digits.length < 4) digits + key else digits
        }
        if (digits.length == 4) {
            onPin(digits)
            digits = "" // si es incorrecto, queda listo para otro intento
        }
    }

    AppDialog(onDismiss = onDismiss, width = 360.dp, centered = true) {
        Avatar(user.title, user.thumb, Modifier.size(64.dp), initialSize = 26.sp)
        Spacer(Modifier.height(12.dp))
        Text("PIN de ${user.title}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (i < digits.length) Color.White else Color.White.copy(alpha = 0.2f))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                busy -> "Comprobando…"
                error != null -> error
                else -> " "
            },
            color = if (error != null && !busy) Color(0xFFFF8A80) else Dim,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.size(72.dp, 52.dp))
                        } else {
                            PinKey(key, focus = key == "1", onClick = { press(key) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(key: String, focus: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(72.dp, 52.dp).initialFocus(focus),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (key == "⌫") {
                Icon(AppIcons.Backspace, contentDescription = "Borrar", modifier = Modifier.size(22.dp))
            } else {
                Text(key, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

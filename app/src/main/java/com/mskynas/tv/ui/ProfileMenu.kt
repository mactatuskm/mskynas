@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Plex

/**
 * Menú del perfil (al elegir tu avatar): encabezado con foto, nombre, rol y servidor,
 * y las opciones personales y de cuenta del mapa de pantallas.
 */
@Composable
fun ProfileMenu(nav: NavController, onDismiss: () -> Unit) {
    var askSignOut by remember { mutableStateOf(false) }
    val (name, thumb) = currentProfile()
    val isAdmin = Plex.session.profileId.let { id ->
        if (id == null) true else Cache.profiles.any { it.id == id && it.admin }
    }
    val role = if (isAdmin) "ADMIN" else "PERFIL"
    val server = Plex.session.serverName?.uppercase().orEmpty()

    fun go(route: String) {
        onDismiss()
        nav.goSection(route)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 76.dp, top = 24.dp)
                    .width(300.dp)
                    .background(Color(0xFF1E2030), RoundedCornerShape(16.dp))
                    .padding(vertical = 14.dp)
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name, thumb, Modifier.size(46.dp), initialSize = 20.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (server.isNotEmpty()) "$role · $server" else role,
                            color = Dim,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                        )
                    }
                }
                Divider()
                MenuItem(AppIcons.People, "Switch Profile", Modifier.initialFocus(true)) {
                    onDismiss()
                    nav.navigate(Routes.PROFILES)
                }
                MenuItem(AppIcons.Bookmark, "Watchlist") { go(Routes.WATCHLIST) }
                MenuItem(AppIcons.ListIcon, "Lists") { go(Routes.LISTS) }
                MenuItem(AppIcons.History, "History") { go(Routes.HISTORY) }
                Divider()
                MenuItem(AppIcons.Settings, "Settings") { go(Routes.SETTINGS) }
                MenuItem(AppIcons.Server, "Switch Server") {
                    onDismiss()
                    nav.navigate(Routes.SERVERS)
                }
                MenuItem(AppIcons.Logout, "Sign Out") { askSignOut = true }
            }
        }
    }

    if (askSignOut) {
        ConfirmDialog(
            title = "¿Cerrar sesión?",
            message = "Tendrás que volver a vincular esta TV en plex.tv/link.",
            confirm = "Cerrar sesión",
            onConfirm = {
                askSignOut = false
                onDismiss()
                nav.logout()
            },
            onDismiss = { askSignOut = false },
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .fillMaxWidth()
            .height(42.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import com.mskynas.tv.data.Plex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class Category(val title: String, val icon: ImageVector)

private val categories = listOf(
    Category("Servidor", AppIcons.Tv),
    Category("Cuenta", AppIcons.Bookmark),
    Category("Acerca de", AppIcons.Settings),
)

@Composable
fun SettingsScreen(nav: NavController) {
    var selected by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3000)
            toast = null
        }
    }

    RailScaffold(nav, Sections.SETTINGS) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(start = ContentStart, top = 40.dp, end = 48.dp)) {
                Text("Ajustes", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxSize()) {
                    // Columna de categorías: al enfocar una, cambia el panel de la derecha.
                    Column(Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEachIndexed { i, c ->
                            CategoryItem(
                                c,
                                selected = i == selected,
                                modifier = Modifier.initialFocus(i == 0),
                                onFocus = { selected = i },
                            )
                        }
                    }
                    Spacer(Modifier.width(32.dp))
                    Column(
                        Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (selected) {
                            0 -> {
                                SectionLabel("SERVIDOR")
                                SettingRow(
                                    "Servidor Plex",
                                    "El servidor del que se cargan tus bibliotecas.",
                                    value = Plex.session.serverName ?: "—",
                                ) { nav.navigate(Routes.SERVERS) }
                                SettingRow(
                                    "Reconectar",
                                    "Busca de nuevo la mejor conexión (útil si cambió la IP del servidor).",
                                ) {
                                    scope.launch {
                                        toast = "Buscando conexión…"
                                        toast = if (Plex.reconnect()) "Conectado a ${Plex.session.serverName}" else "No pude reconectar"
                                    }
                                }
                                SettingRow(
                                    "Dirección",
                                    Plex.session.serverUri ?: "—",
                                ) {}
                            }
                            1 -> {
                                SectionLabel("CUENTA")
                                SettingRow(
                                    "Cerrar sesión",
                                    "Tendrás que volver a vincular esta TV en plex.tv/link.",
                                ) { nav.logout() }
                            }
                            else -> {
                                SectionLabel("ACERCA DE")
                                SettingRow("Versión", "mskynas para Fire TV", value = version) {}
                                SettingRow("Código", "github.com/mactatuskm/mskynas") {}
                            }
                        }
                    }
                }
            }
            toast?.let { ToastPill(it, Modifier.align(Alignment.BottomCenter)) }
        }
    }
}

@Composable
private fun CategoryItem(c: Category, selected: Boolean, modifier: Modifier, onFocus: () -> Unit) {
    Surface(
        onClick = onFocus,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent,
            contentColor = if (selected) Color.White else Dim,
            focusedContainerColor = Color.White,
            focusedContentColor = Bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(c.icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(c.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
        color = Faint,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun SettingRow(title: String, description: String, value: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Panel,
            contentColor = Color.White,
            focusedContainerColor = PanelHi,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Accent),
                shape = RoundedCornerShape(10.dp),
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    color = Dim,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (value != null) {
                Spacer(Modifier.width(16.dp))
                Text(value, color = Dim, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

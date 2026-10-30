@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Plex
import com.mskynas.tv.data.Resource
import kotlinx.coroutines.launch

@Composable
fun ServerScreen(onSelected: () -> Unit, onLogout: () -> Unit) {
    var servers by remember { mutableStateOf<List<Resource>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val choose: (Resource) -> Unit = { resource ->
        if (!busy) {
            scope.launch {
                busy = true
                status = "Conectando con ${resource.name}…"
                val uri = Plex.pickConnection(resource)
                if (uri == null) {
                    status = "No pude conectar con ${resource.name}. ¿Está encendido?"
                    busy = false
                } else {
                    Plex.session.setServer(resource, uri)
                    Cache.clear()
                    onSelected()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val list = runCatching { Plex.servers() }.getOrNull()
        servers = list ?: emptyList()
        if (list == null) {
            status = "No pude obtener tus servidores."
        } else if (list.isEmpty()) {
            status = "Tu cuenta no tiene servidores disponibles."
        } else if (list.size == 1 && Plex.session.serverUri == null) {
            // Si solo hay uno y es la primera vez, se elige solo.
            choose(list[0])
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 56.dp)) {
        Text("Elige tu servidor", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(status ?: "Selecciona el servidor Plex que quieres usar.", color = Dim, fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))

        val list = servers
        if (list == null) {
            Text("Buscando servidores…", color = Dim, fontSize = 16.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(list, key = { _, r -> r.clientIdentifier }) { i, r ->
                    ActionButton(
                        text = r.name + if (r.owned) "" else "  ·  compartido",
                        onClick = { choose(r) },
                        modifier = Modifier.widthIn(min = 360.dp).initialFocus(i == 0),
                        highlighted = r.clientIdentifier == Plex.session.serverId,
                    )
                }
                item {
                    Spacer(Modifier.height(24.dp))
                    ActionButton("Cerrar sesión", onLogout, modifier = Modifier.initialFocus(list.isEmpty()))
                }
            }
        }
    }
}

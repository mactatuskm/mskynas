@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Directory
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

@Composable
private fun ScreenTitle(text: String) {
    Text(text, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
}

/** Explorar: el catálogo completo de cada biblioteca, en cuadrícula. */
@Composable
fun ExploreScreen(nav: NavController) {
    var libraries by remember { mutableStateOf(Cache.home?.libraries.orEmpty()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val libKey = selected ?: libraries.firstOrNull()?.key
    var items by remember(libKey) { mutableStateOf(libKey?.let { Cache.library[it] }) }
    var failed by remember(libKey) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (libraries.isEmpty()) libraries = runCatching { Plex.sections() }.getOrDefault(emptyList())
    }
    LaunchedEffect(libKey, Plex.playbackTick) {
        val k = libKey ?: return@LaunchedEffect
        runCatching { Plex.sectionItems(k) }
            .onSuccess { items = it; Cache.library[k] = it }
            .onFailure { if (items == null) failed = true }
    }

    RailScaffold(nav, Sections.EXPLORE) {
        Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
            Row(
                Modifier.padding(start = ContentStart, end = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScreenTitle("Explorar")
                Spacer(Modifier.width(16.dp))
                libraries.forEach { dir: Directory ->
                    ActionButton(
                        dir.title,
                        onClick = { selected = dir.key },
                        highlighted = dir.key == libKey,
                        icon = if (dir.type == "movie") AppIcons.Movie else AppIcons.Tv,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val list = items
            when {
                list == null && failed -> MessageBox("No pude cargar esta biblioteca.")
                list == null -> LoadingBox()
                list.isEmpty() -> MessageBox("Esta biblioteca está vacía.")
                else -> key(libKey) { PosterGrid(list, screen = "explore-$libKey", nav = nav) }
            }
        }
    }
}

/** Colecciones: las de todas tus bibliotecas juntas. */
@Composable
fun AllCollectionsScreen(nav: NavController) {
    var list by remember { mutableStateOf(Cache.allCollections) }

    LaunchedEffect(Unit) {
        val libs = Cache.home?.libraries ?: runCatching { Plex.sections() }.getOrDefault(emptyList())
        val all = coroutineScope {
            libs.map { dir ->
                async { runCatching { Plex.sectionCollections(dir.key) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten().sortedBy { it.title.lowercase() }
        }
        list = all
        Cache.allCollections = all
    }

    RailScaffold(nav, Sections.COLLECTIONS) {
        Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
            Box(Modifier.padding(start = ContentStart)) { ScreenTitle("Colecciones") }
            Spacer(Modifier.height(12.dp))
            val current = list
            when {
                current == null -> LoadingBox()
                current.isEmpty() -> MessageBox("Tus bibliotecas no tienen colecciones.")
                else -> PosterGrid(current, screen = "collections", nav = nav)
            }
        }
    }
}

/** Buscar en todo el servidor. El teclado de Fire TV aparece al presionar OK en el campo. */
@Composable
fun SearchScreen(nav: NavController) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Item>?>(null) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(400) // espera a que termines de escribir
        searching = true
        results = runCatching { Plex.search(q) }.getOrDefault(emptyList())
        searching = false
    }

    RailScaffold(nav, Sections.SEARCH) {
        Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
            Row(Modifier.padding(start = ContentStart), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Buscar")
                Spacer(Modifier.width(24.dp))
                SearchField(query, onChange = { query = it }, modifier = Modifier.width(480.dp).initialFocus(true))
            }
            Spacer(Modifier.height(16.dp))
            val list = results
            when {
                list == null -> Text(
                    "Escribe al menos dos letras del título de una película o serie.",
                    modifier = Modifier.padding(start = ContentStart),
                    color = Dim,
                    fontSize = 15.sp,
                )
                list.isEmpty() && !searching -> Text(
                    "Sin resultados para “${query.trim()}”.",
                    modifier = Modifier.padding(start = ContentStart),
                    color = Dim,
                    fontSize = 15.sp,
                )
                else -> PosterGrid(list, screen = "search", nav = nav)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(50)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            focusManager.moveFocus(FocusDirection.Down)
        }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(Color.White.copy(alpha = if (focused) 0.16f else 0.08f), shape)
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = Dim)
                Spacer(Modifier.width(12.dp))
                Box {
                    if (value.isEmpty()) {
                        Text("Películas y series de tu servidor", color = Dim, fontSize = 17.sp)
                    }
                    inner()
                }
            }
        },
    )
}

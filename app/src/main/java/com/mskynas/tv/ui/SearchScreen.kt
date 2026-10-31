@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mskynas.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import kotlinx.coroutines.delay

/** Search: películas y series de tu servidor. El teclado de Fire TV aparece al presionar OK en el campo. */
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
            Row(Modifier.padding(start = ContentStart, end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Search", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(24.dp))
                SearchField(query, onChange = { query = it }, modifier = Modifier.width(480.dp).initialFocus(true))
            }
            Spacer(Modifier.height(16.dp))
            val list = results
            when {
                list == null -> Hint("Escribe al menos dos letras del título de una película o serie.")
                list.isEmpty() && !searching -> Hint("Sin resultados para “${query.trim()}”.")
                else -> PosterGrid(list, screen = "search", nav = nav)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, modifier = Modifier.padding(start = ContentStart), color = Dim, fontSize = 15.sp)
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
                    if (value.isEmpty()) Text("Películas y series de tu servidor", color = Dim, fontSize = 17.sp)
                    inner()
                }
            }
        },
    )
}

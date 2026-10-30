package com.mskynas.tv.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex
import com.mskynas.tv.player.PlayerActivity

object Routes {
    const val LOGIN = "login"
    const val SERVERS = "servers"
    const val HOME = "home"
    const val SETTINGS = "settings"
    fun library(key: String) = "library/$key"
    fun collection(key: String) = "collection/$key"
    fun detail(key: String) = "detail/$key"
}

/**
 * El reproductor vive en su propia Activity nativa (no en Compose): así el video va directo
 * a la superficie de hardware de la TV, que es lo más eficiente para 4K/HEVC.
 */
fun play(nav: NavController, ratingKey: String, resume: Boolean) {
    PlayerActivity.start(nav.context, ratingKey, resume)
}

/** Regresa al inicio sin apilar pantallas. */
fun NavController.goHome() {
    if (!popBackStack(Routes.HOME, inclusive = false)) navigate(Routes.HOME)
}

/** Secciones de la barra lateral: se abren encima del inicio, sin apilar una sobre otra. */
fun NavController.goSection(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME)
        launchSingleTop = true
    }
}

fun NavController.logout() {
    Plex.session.clear()
    Cache.clear()
    navigate(Routes.LOGIN) { popUpTo(graph.id) { inclusive = true } }
}

/** A dónde lleva tocar un elemento, según su tipo. */
fun openItem(nav: NavController, item: Item) {
    when (item.type) {
        "collection" -> {
            Cache.titles[item.ratingKey] = item.title
            nav.navigate(Routes.collection(item.ratingKey))
        }
        "episode" -> play(nav, item.ratingKey, (item.viewOffset ?: 0L) > 0)
        else -> nav.navigate(Routes.detail(item.ratingKey))
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val start = remember {
        when {
            Plex.session.userToken == null -> Routes.LOGIN
            Plex.session.serverUri == null -> Routes.SERVERS
            else -> Routes.HOME
        }
    }

    NavHost(
        navController = nav,
        startDestination = start,
        // Transiciones cortas: la app debe sentirse rápida.
        enterTransition = { fadeIn(tween(150)) },
        exitTransition = { fadeOut(tween(100)) },
        popEnterTransition = { fadeIn(tween(150)) },
        popExitTransition = { fadeOut(tween(100)) },
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(onDone = {
                nav.navigate(Routes.SERVERS) { popUpTo(nav.graph.id) { inclusive = true } }
            })
        }
        composable(Routes.SERVERS) {
            ServerScreen(
                onSelected = {
                    nav.navigate(Routes.HOME) { popUpTo(nav.graph.id) { inclusive = true } }
                },
                onLogout = { nav.logout() },
            )
        }
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable("library/{key}") { entry ->
            LibraryScreen(entry.arguments?.getString("key").orEmpty(), nav)
        }
        composable("collection/{key}") { entry ->
            CollectionScreen(entry.arguments?.getString("key").orEmpty(), nav)
        }
        composable("detail/{key}") { entry ->
            DetailScreen(entry.arguments?.getString("key").orEmpty(), nav)
        }
    }
}

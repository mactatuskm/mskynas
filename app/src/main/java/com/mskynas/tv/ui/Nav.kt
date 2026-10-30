package com.mskynas.tv.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mskynas.tv.data.Cache
import com.mskynas.tv.data.Item
import com.mskynas.tv.data.Plex

object Routes {
    const val LOGIN = "login"
    const val SERVERS = "servers"
    const val HOME = "home"
    fun library(key: String) = "library/$key"
    fun collection(key: String) = "collection/$key"
    fun detail(key: String) = "detail/$key"
    fun player(key: String, resume: Boolean) = "player/$key?resume=$resume"
}

/** A dónde lleva tocar un elemento, según su tipo. */
fun openItem(nav: NavController, item: Item) {
    when (item.type) {
        "collection" -> {
            Cache.titles[item.ratingKey] = item.title
            nav.navigate(Routes.collection(item.ratingKey))
        }
        "episode" -> nav.navigate(Routes.player(item.ratingKey, (item.viewOffset ?: 0L) > 0))
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
                onLogout = {
                    Plex.session.clear()
                    Cache.clear()
                    nav.navigate(Routes.LOGIN) { popUpTo(nav.graph.id) { inclusive = true } }
                },
            )
        }
        composable(Routes.HOME) { HomeScreen(nav) }
        composable("library/{key}") { entry ->
            LibraryScreen(entry.arguments?.getString("key").orEmpty(), nav)
        }
        composable("collection/{key}") { entry ->
            CollectionScreen(entry.arguments?.getString("key").orEmpty(), nav)
        }
        composable("detail/{key}") { entry ->
            DetailScreen(entry.arguments?.getString("key").orEmpty(), nav)
        }
        composable(
            "player/{key}?resume={resume}",
            arguments = listOf(navArgument("resume") {
                type = NavType.BoolType
                defaultValue = false
            }),
        ) { entry ->
            PlayerScreen(
                ratingKey = entry.arguments?.getString("key").orEmpty(),
                resume = entry.arguments?.getBoolean("resume") ?: false,
                onExit = {
                    if (nav.currentBackStackEntry?.destination?.route?.startsWith("player") == true) {
                        nav.popBackStack()
                    }
                },
            )
        }
    }
}

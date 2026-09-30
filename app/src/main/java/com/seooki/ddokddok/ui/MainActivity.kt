package com.seooki.ddokddok.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.seooki.ddokddok.ui.apps.AppsScreen
import com.seooki.ddokddok.ui.conditions.ConditionsScreen
import com.seooki.ddokddok.ui.history.HistoryScreen
import com.seooki.ddokddok.ui.home.HomeScreen
import com.seooki.ddokddok.ui.method.WakeMethodScreen
import com.seooki.ddokddok.ui.theme.DdokTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DdokTheme {
                DdokNavHost()
            }
        }
    }
}

object Destinations {
    const val HOME = "home"
    const val METHOD = "method"
    const val CONDITIONS = "conditions"
    const val APPS = "apps"
    const val HISTORY = "history"
}

@Composable
private fun DdokNavHost() {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(navController = navController, startDestination = Destinations.HOME) {
        composable(Destinations.HOME) {
            HomeScreen(onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } })
        }
        composable(Destinations.METHOD) { WakeMethodScreen(onBack = back) }
        composable(Destinations.CONDITIONS) { ConditionsScreen(onBack = back) }
        composable(Destinations.APPS) { AppsScreen(onBack = back) }
        composable(Destinations.HISTORY) { HistoryScreen(onBack = back) }
    }
}

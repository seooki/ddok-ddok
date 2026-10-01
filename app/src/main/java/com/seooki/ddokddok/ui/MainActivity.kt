package com.seooki.ddokddok.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.seooki.ddokddok.ui.about.AboutScreen
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
    const val ABOUT = "about"
}

@Composable
private fun DdokNavHost() {
    val navController = rememberNavController()
    // 화면 전환 애니메이션 중에 한 번 더 눌러 홈까지 닫히거나 같은 화면이 두 번 열리지 않게, 지금 화면이 완전히 보일 때만 이동한다.
    val navigate: (String) -> Unit = { route ->
        if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
            navController.navigate(route) { launchSingleTop = true }
        }
    }
    NavHost(navController = navController, startDestination = Destinations.HOME) {
        composable(Destinations.HOME) { HomeScreen(onNavigate = navigate) }
        composable(Destinations.METHOD) { WakeMethodScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Destinations.CONDITIONS) { ConditionsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Destinations.APPS) { AppsScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Destinations.HISTORY) { HistoryScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
        composable(Destinations.ABOUT) { AboutScreen(onBack = dropUnlessResumed { navController.popBackStack() }) }
    }
}

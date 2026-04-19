package com.parc.fitnesstimer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.parc.fitnesstimer.ui.connection.ConnectionScreen
import com.parc.fitnesstimer.ui.presets.PresetsScreen
import com.parc.fitnesstimer.ui.settings.SettingsScreen
import com.parc.fitnesstimer.ui.theme.AccentRed
import com.parc.fitnesstimer.ui.theme.BackgroundDeep
import com.parc.fitnesstimer.ui.theme.SurfaceCard
import com.parc.fitnesstimer.ui.theme.TextSecondary
import com.parc.fitnesstimer.ui.timer.TimerScreen

// ── Route constants ────────────────────────────────────────────────────────────
object Routes {
    const val TIMER      = "timer"
    const val PRESETS    = "presets"
    const val SETTINGS   = "settings"
    const val CONNECTION = "connection"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.TIMER,    "Timer",   Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    BottomNavItem(Routes.PRESETS,  "Presets", Icons.Filled.List,          Icons.Outlined.List),
    BottomNavItem(Routes.SETTINGS, "Device",  Icons.Filled.Settings,      Icons.Outlined.Settings)
)

@Composable
fun ParcNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Bottom bar is hidden on the Connection screen
    val showBottomBar = currentRoute != Routes.CONNECTION

    Scaffold(
        containerColor = BackgroundDeep,
        bottomBar = {
            AnimatedVisibility(visible = showBottomBar) {
                NavigationBar(
                    containerColor = SurfaceCard,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == item.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick  = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = AccentRed,
                                selectedTextColor   = AccentRed,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor      = AccentRed.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TIMER,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.TIMER) {
                TimerScreen(
                    onNavigateToConnection = {
                        navController.navigate(Routes.CONNECTION)
                    }
                )
            }
            composable(Routes.PRESETS) {
                PresetsScreen(
                    onPresetLoaded = {
                        navController.navigate(Routes.TIMER) {
                            popUpTo(Routes.TIMER) { inclusive = false }
                        }
                    }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(Routes.CONNECTION) {
                ConnectionScreen(
                    onConnected = {
                        navController.navigate(Routes.TIMER) {
                            popUpTo(Routes.CONNECTION) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}



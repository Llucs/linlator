package com.linlator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linlator.ui.screens.AboutScreen
import com.linlator.ui.screens.ContainerDetailScreen
import com.linlator.ui.screens.ContainerListScreen
import com.linlator.ui.screens.NewContainerScreen
import com.linlator.ui.screens.SettingsScreen
import com.linlator.ui.theme.LinlatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LinlatorTheme {
                MainScreen()
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Containers : Screen("containers", "Containers", Icons.Filled.List)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object About : Screen("about", "About", Icons.Filled.Info)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val screens = listOf(Screen.Containers, Screen.Settings, Screen.About)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Containers.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Containers.route) {
                ContainerListScreen(
                    onContainerClick = { containerId ->
                        navController.navigate("container/$containerId")
                    },
                    onNewContainer = {
                        navController.navigate("new_container")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(Screen.About.route) {
                AboutScreen()
            }
            composable("new_container") {
                NewContainerScreen(
                    onCreated = {
                        navController.popBackStack()
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable("container/{containerId}") { backStackEntry ->
                val containerId = backStackEntry.arguments?.getString("containerId") ?: return@composable
                ContainerDetailScreen(
                    containerId = containerId,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

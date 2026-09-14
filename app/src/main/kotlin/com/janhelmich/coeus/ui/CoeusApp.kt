package com.janhelmich.coeus.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.janhelmich.coeus.di.AppContainer
import com.janhelmich.coeus.ui.ar.ArScreen
import com.janhelmich.coeus.ui.ar.ArViewModel
import com.janhelmich.coeus.ui.devices.DevicesScreen
import com.janhelmich.coeus.ui.devices.DevicesViewModel
import com.janhelmich.coeus.ui.settings.SettingsScreen
import com.janhelmich.coeus.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@Serializable private object ArRoute
@Serializable private object DevicesRoute
@Serializable private object SettingsRoute

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(ArRoute, "AR", Icons.Default.Home),
    Tab(DevicesRoute, "Devices", Icons.AutoMirrored.Filled.List),
    Tab(SettingsRoute, "Settings", Icons.Default.Settings),
)

@Composable
fun CoeusApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hasRoute(tab.route::class) == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = ArRoute, modifier = Modifier.padding(padding)) {
            composable<ArRoute> {
                ArScreen(
                    viewModel = viewModel(factory = viewModelFactory {
                        initializer { ArViewModel(container.repository) }
                    }),
                )
            }
            composable<DevicesRoute> {
                DevicesScreen(
                    viewModel = viewModel(factory = viewModelFactory {
                        initializer { DevicesViewModel(container.repository) }
                    }),
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    viewModel = viewModel(factory = viewModelFactory {
                        initializer {
                            SettingsViewModel(container.settings, container::testConnection)
                        }
                    }),
                )
            }
        }
    }
}

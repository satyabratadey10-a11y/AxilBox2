package com.axilbox.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.axilbox.app.ui.screens.AddEditInstanceScreen
import com.axilbox.app.ui.screens.BootScreen
import com.axilbox.app.ui.screens.MainMenuScreen
import com.axilbox.app.ui.viewmodel.InstanceViewModel

@Composable
fun AxilBoxNavHost(
    navController: NavHostController,
    viewModel: InstanceViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.MainMenu.route,
        modifier = modifier
    ) {
        composable(Screen.MainMenu.route) {
            MainMenuScreen(
                viewModel = viewModel,
                onNavigateToAddInstance = {
                    viewModel.initCreateForm()
                    navController.navigate(Screen.AddInstance.route)
                },
                onNavigateToEditInstance = { instanceId ->
                    viewModel.loadInstanceForEdit(instanceId)
                    navController.navigate(Screen.EditInstance.createRoute(instanceId))
                },
                onNavigateToBootScreen = { instanceId ->
                    navController.navigate(Screen.BootScreen.createRoute(instanceId))
                }
            )
        }

        composable(Screen.AddInstance.route) {
            AddEditInstanceScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.EditInstance.route,
            arguments = listOf(
                navArgument("instanceId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getLong("instanceId") ?: 0L
            AddEditInstanceScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onSaveSuccess = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.BootScreen.route,
            arguments = listOf(
                navArgument("instanceId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getLong("instanceId") ?: 0L
            BootScreen(
                instanceId = instanceId,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

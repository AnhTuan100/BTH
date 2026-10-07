package com.example.baith2.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.baith2.screen.ContactDetailScreen
import com.example.baith2.screen.ContactEditScreen
import com.example.baith2.screen.ContactListScreen
import com.example.baith2.viewmodel.ContactViewModel

@Composable
fun ContactNavGraph() {
    val navController = rememberNavController()
    val viewModel: ContactViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.ContactList.route
    ) {
        composable(route = Screen.ContactList.route) {
            ContactListScreen(
                viewModel = viewModel,
                onNavigateToDetail = { contactId ->
                    navController.navigate(Screen.ContactDetail.createRoute(contactId))
                },
                onNavigateToEdit = { contactId ->
                    navController.navigate(Screen.ContactEdit.createRoute(contactId))
                },
                onNavigateToAdd = {
                    navController.navigate(Screen.ContactEdit.NEW)
                }
            )
        }

        composable(
            route = Screen.ContactDetail.route,
            arguments = listOf(navArgument("contactId") { type = NavType.LongType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getLong("contactId") ?: return@composable
            ContactDetailScreen(
                contactId = contactId,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id ->
                    navController.navigate(Screen.ContactEdit.createRoute(id))
                }
            )
        }

        composable(
            route = Screen.ContactEdit.route,
            arguments = listOf(navArgument("contactId") { type = NavType.LongType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getLong("contactId") ?: -1L
            ContactEditScreen(
                contactId = contactId,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

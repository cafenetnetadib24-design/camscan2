package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui.camera.CameraScanScreen
import com.example.ui.camera.CameraViewModel
import com.example.ui.edit.EditDocumentScreen
import com.example.ui.edit.EditViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.privacy.PrivacyPolicyScreen
import com.example.ui.save.SaveExportScreen
import com.example.ui.splash.SplashScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = ScreenRoutes.Splash.route
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(ScreenRoutes.Splash.route) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(ScreenRoutes.Home.route) {
                        popUpTo(ScreenRoutes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(ScreenRoutes.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(context))
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToCamera = { folderId, isColorScan ->
                    navController.navigate(ScreenRoutes.CameraScan.createRoute(folderId, isColorScan))
                },
                onNavigateToEdit = { docId ->
                    navController.navigate(ScreenRoutes.EditDocument.createRoute(docId))
                },
                onNavigateToDetail = { docId ->
                    navController.navigate(ScreenRoutes.EditDocument.createRoute(docId))
                },
                onNavigateToPrivacy = { navController.navigate(ScreenRoutes.PrivacyPolicy.route) }
            )
        }

        composable(
            route = ScreenRoutes.CameraScan.route,
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("isColorScan") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val folderIdArg = backStackEntry.arguments?.getLong("folderId")?.takeIf { it > 0 }
            val isColorScanArg = backStackEntry.arguments?.getBoolean("isColorScan") ?: false
            val cameraViewModel: CameraViewModel = viewModel(factory = CameraViewModel.Factory(context))

            androidx.compose.runtime.LaunchedEffect(folderIdArg, isColorScanArg) {
                cameraViewModel.setFolderId(folderIdArg)
                cameraViewModel.setScanMode(isColorScanArg)
            }

            CameraScanScreen(
                viewModel = cameraViewModel,
                folderId = folderIdArg,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { docId ->
                    navController.navigate(ScreenRoutes.EditDocument.createRoute(docId)) {
                        popUpTo(ScreenRoutes.Home.route)
                    }
                }
            )
        }

        composable(
            route = ScreenRoutes.EditDocument.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.LongType },
                navArgument("pageIndex") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("documentId") ?: 0L
            val editViewModel: EditViewModel = viewModel(factory = EditViewModel.Factory(context, docId))

            EditDocumentScreen(
                viewModel = editViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSaveExport = { documentId ->
                    navController.navigate(ScreenRoutes.SaveExport.createRoute(documentId))
                },
                onNavigateToRescan = {
                    val folderId = editViewModel.uiState.value.document?.folderId
                    navController.navigate(ScreenRoutes.CameraScan.createRoute(folderId = folderId)) {
                        popUpTo(ScreenRoutes.Home.route)
                    }
                }
            )
        }

        composable(
            route = ScreenRoutes.SaveExport.route,
            arguments = listOf(
                navArgument("documentId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("documentId") ?: 0L
            SaveExportScreen(
                documentId = docId,
                onNavigateHome = {
                    navController.navigate(ScreenRoutes.Home.route) {
                        popUpTo(ScreenRoutes.Home.route) { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ScreenRoutes.PrivacyPolicy.route) {
            PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

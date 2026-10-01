package com.example.siapel.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.siapel.ui.auth.LoginScreen
import com.example.siapel.ui.auth.RegisterScreen
import com.example.siapel.ui.home.HomeScreen
import com.example.siapel.ui.profile.AboutScreen
import com.example.siapel.ui.profile.HelpScreen
import com.example.siapel.ui.profile.ProfileScreen
import com.example.siapel.ui.splash.SplashScreen
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.siapel.SiapelApplication
import com.example.siapel.ui.akta.AktaKelahiranFormScreen
import com.example.siapel.ui.disabilitas.DisabilitasFormScreen
import com.example.siapel.ui.home.AllServicesScreen
import com.example.siapel.viewmodel.AktaKelahiranViewModel
import com.example.siapel.viewmodel.ViewModelFactory
import com.example.siapel.viewmodel.AuthViewModel
import com.example.siapel.viewmodel.DisabilitasViewModel
import com.example.siapel.viewmodel.HomeViewModel
import com.example.siapel.viewmodel.KiaViewModel
import com.example.siapel.viewmodel.KkViewModel
import com.example.siapel.viewmodel.KtpViewModel
import com.example.siapel.viewmodel.ProfileViewModel
import com.example.siapel.viewmodel.StatusViewModel

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    profileViewModel: ProfileViewModel
) {
    val context = LocalContext.current
    val application = context.applicationContext as SiapelApplication
    
    val factory = remember {
        ViewModelFactory(
            authRepository = application.authRepository,
            applicationRepository = application.applicationRepository,
            userPreferencesRepository = application.userPreferencesRepository
        )
    }

    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val homeViewModel: HomeViewModel = viewModel(factory = factory)
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onTimeout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                navController = navController,
                profileViewModel = profileViewModel,
                onNavigateToService = { serviceRoute ->
                    navController.navigate(serviceRoute)
                },
                onNavigateToAllServices = {
                    navController.navigate(Screen.AllServices.route)
                }
            )
        }

        composable(Screen.AllServices.route) {
            AllServicesScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToService = { serviceRoute ->
                    navController.navigate(serviceRoute)
                }
            )
        }

        composable(Screen.Status.route) {
            val statusViewModel: StatusViewModel = viewModel(factory = factory)
            com.example.siapel.ui.status.StatusScreen(
                viewModel = statusViewModel,
                navController = navController,
                onNavigateToDetail = { code ->
                    navController.navigate("status_detail/$code")
                }
            )
        }

        composable(
            route = "status_detail/{code}",
            arguments = listOf(androidx.navigation.navArgument("code") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("code") ?: ""
            val statusViewModel: StatusViewModel = viewModel(factory = factory)
            val snackbarHostState = remember { SnackbarHostState() }
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    com.example.siapel.ui.status.StatusDetailScreen(
                        code = code,
                        viewModel = statusViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        snackbarHostState = snackbarHostState,
                        navController = navController
                    )
                }
            }
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                viewModel = profileViewModel,
                navController = navController,
                onNavigateToHelp = { navController.navigate(Screen.Help.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Help.route) {
            HelpScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.About.route) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = "kia?applicationId={applicationId}",
            arguments = listOf(navArgument("applicationId") { type = NavType.IntType; defaultValue = 0 })
        ) { backStackEntry ->
            val applicationId = backStackEntry.arguments?.getInt("applicationId") ?: 0
            val kiaViewModel: KiaViewModel = viewModel(factory = factory)
            LaunchedEffect(applicationId) {
                if (applicationId > 0) {
                    kiaViewModel.loadApplication(applicationId)
                }
            }
            com.example.siapel.ui.kia.KiaFormScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = kiaViewModel
            )
        }

        composable(
            route = "ktp?applicationId={applicationId}",
            arguments = listOf(navArgument("applicationId") { type = NavType.IntType; defaultValue = 0 })
        ) { backStackEntry ->
            val applicationId = backStackEntry.arguments?.getInt("applicationId") ?: 0
            val ktpViewModel: KtpViewModel = viewModel(factory = factory)
            LaunchedEffect(applicationId) {
                if (applicationId > 0) {
                    ktpViewModel.loadApplication(applicationId)
                }
            }
            com.example.siapel.ui.ktp.KtpFormScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = ktpViewModel
            )
        }

        composable(
            route = "kk?applicationId={applicationId}",
            arguments = listOf(navArgument("applicationId") { type = NavType.IntType; defaultValue = 0 })
        ) { backStackEntry ->
            val applicationId = backStackEntry.arguments?.getInt("applicationId") ?: 0
            val kkViewModel: KkViewModel = viewModel(factory = factory)
            LaunchedEffect(applicationId) {
                if (applicationId > 0) {
                    kkViewModel.loadApplication(applicationId)
                }
            }
            com.example.siapel.ui.kk.KkFormScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = kkViewModel
            )
        }

        composable(
            route = "akta?applicationId={applicationId}",
            arguments = listOf(navArgument("applicationId") { type = NavType.IntType; defaultValue = 0 })
        ) { backStackEntry ->
            val applicationId = backStackEntry.arguments?.getInt("applicationId") ?: 0
            val aktaViewModel: AktaKelahiranViewModel = viewModel(factory = factory)
            LaunchedEffect(applicationId) {
                if (applicationId > 0) {
                    aktaViewModel.loadApplication(applicationId)
                }
            }
            AktaKelahiranFormScreen(
                navController = navController,
                viewModel = aktaViewModel
            )
        }

        composable(
            route = "disabilitas?applicationId={applicationId}",
            arguments = listOf(navArgument("applicationId") { type = NavType.IntType; defaultValue = 0 })
        ) { backStackEntry ->
            val applicationId = backStackEntry.arguments?.getInt("applicationId") ?: 0
            val disabilitasViewModel: DisabilitasViewModel = viewModel(factory = factory)
            LaunchedEffect(applicationId) {
                if (applicationId > 0) {
                    disabilitasViewModel.loadApplication(applicationId)
                }
            }
            DisabilitasFormScreen(
                navController = navController,
                viewModel = disabilitasViewModel
            )
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, navController: NavHostController) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.popBackStack() }) {
                Text("Kembali")
            }
        }
    }
}

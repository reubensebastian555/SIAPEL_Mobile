package com.example.siapel.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
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
import com.example.siapel.ui.status.StatusDetailScreen
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
    val application =
        context.applicationContext as SiapelApplication

    // Shared Laravel dependencies.

    val factory = remember(application) {
        ViewModelFactory(
            userPreferencesRepository =
                application.userPreferencesRepository,
            laravelAuthRepository =
                application.laravelAuthRepository,
            laravelApplicationRepository =
                application.laravelApplicationRepository
        )
    }

    val authViewModel: AuthViewModel =
        viewModel(factory = factory)

    val homeViewModel: HomeViewModel =
        viewModel(factory = factory)

    fun resetNavigation(target: String) {
        if (navController.currentDestination?.route == target) return
        // Discard previously saved tab stacks as well as the active stack.
        listOf(Screen.Splash.route, Screen.Home.route, Screen.Status.route,
            Screen.Profile.route, Screen.Login.route, Screen.Register.route).forEach { route ->
            navController.clearBackStack(route)
        }
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
            restoreState = false
        }
    }

    LaunchedEffect(navController, application) {
        var activeGeneration: String? = null
        combine(
            application.userPreferencesRepository.userSessionFlow,
            navController.currentBackStackEntryFlow
        ) { session, entry -> session to entry }
            .collectLatest { (session, entry) ->
                val route = entry.destination.route
                if (route == Screen.Splash.route || route == Screen.Login.route ||
                    route == Screen.Register.route) {
                    activeGeneration = null
                    return@collectLatest
                }
                val verified = try {
                    application.laravelAuthRepository.getVerifiedLaravelSession()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (navController.currentBackStackEntry != entry) return@collectLatest
                if (verified == null) {
                    activeGeneration = null
                    resetNavigation(Screen.Login.route)
                    return@collectLatest
                }
                // A stale flow emission must not invalidate a newer login.
                if (session.generation != verified.generation ||
                    session.userId != verified.localUserId ||
                    session.remoteUserUuid != verified.remoteUserUuid ||
                    session.token != verified.token || !session.isLoggedIn) {
                    return@collectLatest
                }
                if (activeGeneration != null && activeGeneration != verified.generation) {
                    activeGeneration = null
                    resetNavigation(Screen.Splash.route)
                } else {
                    activeGeneration = verified.generation
                }
            }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {

        // ==========================================
        // SPLASH ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â LARAVEL SESSION VALIDATION
        // ==========================================

        composable(Screen.Splash.route) {
            val scope = rememberCoroutineScope()

            SplashScreen(
                onTimeout = {
                    scope.launch {
                        val isSessionValid =
                            application.laravelAuthRepository
                                .validateSessionWithServer()

                        val targetScreen =
                            if (isSessionValid) {
                                profileViewModel.loadUserProfile()
                                Screen.Home.route
                            } else {
                                Screen.Login.route
                            }

                        resetNavigation(targetScreen)
                    }
                }
            )
        }

        // ==========================================
        // LOGIN
        // ==========================================

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = {
                    navController.navigate(
                        Screen.Register.route
                    )
                },
                onNavigateToHome = {
                    profileViewModel.loadUserProfile()
                    resetNavigation(Screen.Home.route)
                }
            )
        }

        // ==========================================
        // REGISTER
        // ==========================================

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        // ==========================================
        // HOME
        // ==========================================

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                navController = navController,
                profileViewModel = profileViewModel,
                onNavigateToService = { serviceRoute ->
                    navController.navigate(serviceRoute)
                },
                onNavigateToAllServices = {
                    navController.navigate(
                        Screen.AllServices.route
                    )
                }
            )
        }

        // ==========================================
        // ALL SERVICES
        // ==========================================

        composable(Screen.AllServices.route) {
            AllServicesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToService = { serviceRoute ->
                    navController.navigate(serviceRoute)
                }
            )
        }

        // ==========================================
        // APPLICATION STATUS
        // ==========================================

        composable(Screen.Status.route) {
            val statusViewModel: StatusViewModel =
                viewModel(factory = factory)

            com.example.siapel.ui.status.StatusScreen(
                viewModel = statusViewModel,
                navController = navController,
                onNavigateToDetail = { code ->
                    navController.navigate(
                        "status_detail/$code"
                    )
                }
            )
        }

        // ==========================================
        // APPLICATION STATUS DETAIL
        // ==========================================

        composable(
            route = "status_detail/{code}",
            arguments = listOf(
                navArgument("code") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val code =
                backStackEntry.arguments
                    ?.getString("code") ?: ""

            val statusViewModel: StatusViewModel =
                viewModel(factory = factory)

            val snackbarHostState =
                remember { SnackbarHostState() }

            StatusDetailScreen(
                code = code,
                viewModel = statusViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                snackbarHostState = snackbarHostState,
                navController = navController
            )
        }

        // ==========================================
        // PROFILE
        // ==========================================

        composable(Screen.Profile.route) {
            ProfileScreen(
                viewModel = profileViewModel,
                navController = navController,
                onNavigateToHelp = {
                    navController.navigate(Screen.Help.route)
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                },
                onLogout = {
                    resetNavigation(Screen.Login.route)
                }
            )
        }

        // ==========================================
        // HELP & ABOUT
        // ==========================================

        composable(Screen.Help.route) {
            HelpScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // ==========================================
        // KIA SERVICE
        // ==========================================

        composable(
            route = "kia?applicationId={applicationId}",
            arguments = listOf(
                navArgument("applicationId") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->

            val applicationId =
                backStackEntry.arguments
                    ?.getInt("applicationId") ?: 0

            val kiaViewModel: KiaViewModel =
                viewModel(factory = factory)

            LaunchedEffect(applicationId) {
                if (applicationId > 0) {
                    kiaViewModel.loadApplication(applicationId)
                }
            }

            com.example.siapel.ui.kia.KiaFormScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = kiaViewModel
            )
        }

        // ==========================================
        // KTP SERVICE
        // ==========================================

        composable(
            route = "ktp?applicationId={applicationId}",
            arguments = listOf(
                navArgument("applicationId") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->

            val applicationId =
                backStackEntry.arguments
                    ?.getInt("applicationId") ?: 0

            val ktpViewModel: KtpViewModel =
                viewModel(factory = factory)

            LaunchedEffect(applicationId) {
                if (applicationId > 0) {
                    ktpViewModel.loadApplication(applicationId)
                }
            }

            com.example.siapel.ui.ktp.KtpFormScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = ktpViewModel
            )
        }

        // ==========================================
        // KK SERVICE
        // ==========================================

        composable(
            route = "kk?applicationId={applicationId}",
            arguments = listOf(
                navArgument("applicationId") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->

            val applicationId =
                backStackEntry.arguments
                    ?.getInt("applicationId") ?: 0

            val kkViewModel: KkViewModel =
                viewModel(factory = factory)

            LaunchedEffect(applicationId) {
                if (applicationId > 0) {
                    kkViewModel.loadApplication(applicationId)
                }
            }

            com.example.siapel.ui.kk.KkFormScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = kkViewModel
            )
        }

        // ==========================================
        // AKTA SERVICE
        // ==========================================

        composable(
            route = "akta?applicationId={applicationId}",
            arguments = listOf(
                navArgument("applicationId") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->

            val applicationId =
                backStackEntry.arguments
                    ?.getInt("applicationId") ?: 0

            val aktaViewModel: AktaKelahiranViewModel =
                viewModel(factory = factory)

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

        // ==========================================
        // DISABILITAS SERVICE
        // ==========================================

        composable(
            route = "disabilitas?applicationId={applicationId}",
            arguments = listOf(
                navArgument("applicationId") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->

            val applicationId =
                backStackEntry.arguments
                    ?.getInt("applicationId") ?: 0

            val disabilitasViewModel: DisabilitasViewModel =
                viewModel(factory = factory)

            LaunchedEffect(applicationId) {
                if (applicationId > 0) {
                    disabilitasViewModel.loadApplication(
                        applicationId
                    )
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
fun PlaceholderScreen(
    title: String,
    navController: NavHostController
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Button(
                onClick = {
                    navController.popBackStack()
                }
            ) {
                Text("Kembali")
            }
        }
    }
}


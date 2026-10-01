package com.example.siapel.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Status : Screen("status")
    object Profile : Screen("profile")
    object Help : Screen("help")
    object About : Screen("about")
    object Kia : Screen("kia")
    object Ktp : Screen("ktp")
    object Kk : Screen("kk")
    object Akta : Screen("akta")
    object Disabilitas : Screen("disabilitas")
    object AllServices : Screen("all_services")
}

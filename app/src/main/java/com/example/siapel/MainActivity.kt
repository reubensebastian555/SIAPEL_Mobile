package com.example.siapel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import com.example.siapel.navigation.AppNavigation
import com.example.siapel.ui.theme.SIAPELTheme
import com.example.siapel.viewmodel.ProfileViewModel
import com.example.siapel.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val application = applicationContext as SiapelApplication
            val factory = remember {
                ViewModelFactory(
                    authRepository = application.authRepository,
                    userPreferencesRepository = application.userPreferencesRepository
                )
            }
            val profileViewModel: ProfileViewModel = viewModel(factory = factory)
            val isDarkMode by profileViewModel.isDarkMode.collectAsState()
            
            SIAPELTheme(darkTheme = isDarkMode) {
                AppNavigation(profileViewModel = profileViewModel)
            }
        }
    }
}

package com.example.siapel.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siapel.navigation.Screen

data class ServiceItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

val allServices = listOf(
    ServiceItem("Kartu Identitas Anak", Icons.Filled.ChildCare, Screen.Kia.route),
    ServiceItem("KTP Elektronik", Icons.Filled.Badge, Screen.Ktp.route),
    ServiceItem("Kartu Keluarga", Icons.Filled.Groups, Screen.Kk.route),
    ServiceItem("Paket Akta Kelahiran", Icons.Filled.Description, Screen.Akta.route),
    ServiceItem("Layanan Disabilitas", Icons.AutoMirrored.Filled.Accessible, Screen.Disabilitas.route)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllServicesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToService: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Daftar Layanan",
                        color = MaterialTheme.colorScheme.onSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
        ) {
            Text(
                text = "Semua Layanan Kependudukan",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Silakan pilih layanan dokumen kependudukan yang ingin Anda ajukan",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allServices) { service ->
                    ServiceCardItem(
                        title = service.title,
                        icon = service.icon,
                        onClick = { onNavigateToService(service.route) }
                    )
                }
            }
        }
    }
}

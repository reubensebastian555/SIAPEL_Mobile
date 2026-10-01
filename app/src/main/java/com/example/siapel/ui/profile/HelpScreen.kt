package com.example.siapel.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onNavigateBack: () -> Unit) {
    val helpItems = listOf(
        HelpItem(
            "Cara membuat akun",
            "Buka aplikasi SIAPEL, pilih 'Daftar' pada halaman login. Isi data diri Anda dengan benar termasuk NIK, Email, dan Password. Setelah itu Anda dapat langsung masuk ke aplikasi."
        ),
        HelpItem(
            "Cara mengajukan layanan",
            "Pada halaman Beranda, pilih jenis layanan yang Anda butuhkan (KIA, KTP, KK, Akta, atau Disabilitas). Isi formulir yang disediakan dan unggah dokumen persyaratan yang diminta."
        ),
        HelpItem(
            "Cara melihat status permohonan",
            "Pilih menu 'Status' pada bilah navigasi bawah. Di sana Anda dapat melihat daftar permohonan yang telah Anda ajukan beserta status terbarunya (Proses, Selesai, atau Ditolak)."
        ),
        HelpItem(
            "Cara mengunggah dokumen",
            "Saat mengisi formulir layanan, klik pada area unggah dokumen. Pastikan dokumen dalam format gambar (JPG/PNG) atau PDF dengan ukuran maksimal yang ditentukan dan terbaca dengan jelas."
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pusat Bantuan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(helpItems) { item ->
                HelpExpandableCard(item)
            }
        }
    }
}

data class HelpItem(val title: String, val content: String)

@Composable
fun HelpExpandableCard(item: HelpItem) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

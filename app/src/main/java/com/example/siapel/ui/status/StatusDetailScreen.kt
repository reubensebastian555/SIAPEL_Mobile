package com.example.siapel.ui.status

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.model.ApplicationStatus
import com.example.siapel.model.ApplicationStatusItem
import com.example.siapel.ui.theme.*
import com.example.siapel.viewmodel.StatusViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDetailScreen(
    code: String,
    viewModel: StatusViewModel,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    navController: NavController
) {
    val appState by viewModel.getApplicationByCodeFlow(code).collectAsState(initial = null)
    val app = appState
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isDeleting by viewModel.isDeleting.collectAsState()
    val context = LocalContext.current
    val documentViewLoading by viewModel.documentViewLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.deleteResult.collect { success ->
            if (success) {
                onNavigateBack()
            } else {
                snackbarHostState.showSnackbar("Gagal menghapus pengajuan. Silakan coba lagi.")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openDocumentUrl.collect { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membuka tautan dokumen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.documentViewError.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text("Hapus Pengajuan", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Apakah Anda yakin ingin menghapus data pengajuan ini? Tindakan ini tidak dapat dibatalkan.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        showDeleteDialog = false
                        app?.let { viewModel.deleteApplication(it) }
                    }
                ) {
                    Text(if (isDeleting) "Menghapus..." else "Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Batal", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Detail Pengajuan", color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                    }
                },
                actions = {
                    if (app != null && (app.status == ApplicationStatus.SUBMITTED || app.status == ApplicationStatus.NEED_REVISION)) {
                        IconButton(onClick = {
                            val route = when (app.serviceType.uppercase()) {
                                "KIA" -> "kia?applicationId=${app.id}"
                                "KTP" -> "ktp?applicationId=${app.id}"
                                "KK" -> "kk?applicationId=${app.id}"
                                "AKTA" -> "akta?applicationId=${app.id}"
                                "DISABILITAS" -> "disabilitas?applicationId=${app.id}"
                                else -> null
                            }
                            route?.let { navController.navigate(it) }
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Pengajuan", tint = MaterialTheme.colorScheme.onSecondary)
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.onSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondary)
            )
        }
    ) { innerPadding ->
        if (app == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text("Permohonan tidak ditemukan")
            }
        } else {
            val application = app
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Info Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DetailItem(label = "Kode Permohonan", value = application.submissionCode, isBold = true)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            DetailItem(label = "Jenis Layanan", value = application.serviceType)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            DetailItem(label = "Kategori", value = application.category)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            DetailItem(label = "Tanggal Pengajuan", value = application.submissionDate)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Status Saat Ini", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                StatusChipDetail(status = application.status)
                            }
                        }
                    }
                }

                // Data Pelapor
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Data Pelapor", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            DetailItem(label = "NIK Pelapor", value = application.nikPelapor)
                            DetailItem(label = "Nama Pelapor", value = application.namaPelapor)
                            DetailItem(label = "Email", value = application.email)
                            DetailItem(label = "Nomor WhatsApp", value = application.whatsapp)
                            DetailItem(label = "Kecamatan", value = application.kecamatan)
                            DetailItem(label = "Kelurahan", value = application.kelurahan)
                        }
                    }
                }

                // Documents Section (Read-Only Summary + Remote View Button)
                val requirements = getDocumentRequirements(application)
                if (requirements.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Dokumen Terlampir", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                val isDark = isSystemInDarkTheme()
                                val successColor = if (isDark) SuccessDark else Success
                                requirements.forEachIndexed { index, req ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = req.label,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        val statusText = if (req.isAttached) "Terlampir" else "Belum Dilampirkan"
                                        val statusColor = if (req.isAttached) successColor else MaterialTheme.colorScheme.error

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = statusText,
                                                color = statusColor,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            val slot = if (req.isAttached) getSlotForDocumentRequirement(application, index) else null
                                            if (slot != null) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = {
                                                        viewModel.viewKiaDocument(
                                                            submissionCode = application.submissionCode,
                                                            documentSlot = slot
                                                        )
                                                    },
                                                    enabled = !documentViewLoading,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    if (documentViewLoading) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.Visibility,
                                                            contentDescription = "Lihat dokumen",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (index < requirements.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Conditional Note
                if (application.status == ApplicationStatus.NEED_REVISION && application.note != null) {
                    item {
                        val isDark = MaterialTheme.colorScheme.primary == Color(0xFF29A9E8)
                        val warningColor = if (isDark) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.error
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF2A171A) else Color(0xFFFFF1F1)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                warningColor.copy(alpha = if (isDark) 0.4f else 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = warningColor, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Perlu Perbaikan",
                                        color = warningColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = application.note ?: "", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val route = when (application.serviceType.uppercase()) {
                                            "KIA" -> "kia?applicationId=${application.id}"
                                            "KTP" -> "ktp?applicationId=${application.id}"
                                            "KK" -> "kk?applicationId=${application.id}"
                                            "AKTA" -> "akta?applicationId=${application.id}"
                                            "DISABILITAS" -> "disabilitas?applicationId=${application.id}"
                                            else -> null
                                        }
                                        route?.let { navController.navigate(it) }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Perbaiki Dokumen", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (application.status == ApplicationStatus.COMPLETED) {
                    item {
                        val isDark = isSystemInDarkTheme()
                        val successColor = if (isDark) SuccessDark else Success
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = successColor.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, successColor.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = successColor,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Permohonan Selesai",
                                        color = successColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Permohonan Anda telah selesai diproses.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Progress Timeline
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Timeline Progress", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(16.dp))

                            val finalStageTitle = if (application.status == ApplicationStatus.NEED_REVISION) "Perlu Perbaikan" else "Selesai"
                            val stages = listOf("Diajukan", "Sedang Diproses", finalStageTitle)
                            stages.forEachIndexed { index, stage ->
                                val state = getStageState(index, application.status)
                                TimelineItem(
                                    title = stage,
                                    subtitle = getStageSubtitle(index, application.status),
                                    state = state,
                                    isLast = index == stages.lastIndex
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isKiaPlusKk(category: String): Boolean {
    val normalized = category
        .uppercase()
        .replace(" ", "")
        .replace("&", "+")
        .replace("DAN", "+")

    return normalized.contains("KIA+KK")
}

fun getSlotForDocumentRequirement(application: ApplicationEntity, index: Int): String? {
    val service = application.serviceType.uppercase()
    val cat = application.category.uppercase()

    if (service != "KIA") return null
    if (isKiaPlusKk(cat)) return null

    return when {
        cat.contains("RUSAK") -> when (index) {
            0 -> "doc1"
            1 -> "doc2"
            2 -> "docSelfie"
            else -> null
        }
        cat.contains("HILANG") -> when (index) {
            0 -> "doc1"
            1 -> "doc2"
            2 -> "docSelfie"
            else -> null
        }
        else -> when (index) { // KIA BARU & KIA RUBAH
            0 -> "doc1"
            1 -> "doc2"
            2 -> "doc3"
            3 -> "docSelfie"
            else -> null
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun StatusChipDetail(status: ApplicationStatus) {
    val isDark = isSystemInDarkTheme()
    val (text, color) = when (status) {
        ApplicationStatus.SUBMITTED -> "Diajukan" to MaterialTheme.colorScheme.primary
        ApplicationStatus.PROCESSING -> "Sedang Diproses" to Color(0xFFFFA000)
        ApplicationStatus.COMPLETED -> "Selesai" to if (isDark) SuccessDark else Success
        ApplicationStatus.NEED_REVISION -> "Perlu Perbaikan" to MaterialTheme.colorScheme.error
    }

    Surface(
        color = color.copy(alpha = if (isDark) 0.2f else 0.12f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class TimelineState { COMPLETED, ACTIVE, INACTIVE, ERROR }

fun getStageState(index: Int, status: ApplicationStatus): TimelineState {
    return when (status) {
        ApplicationStatus.SUBMITTED -> when (index) {
            0 -> TimelineState.ACTIVE
            else -> TimelineState.INACTIVE
        }
        ApplicationStatus.PROCESSING -> when (index) {
            0 -> TimelineState.COMPLETED
            1 -> TimelineState.ACTIVE
            else -> TimelineState.INACTIVE
        }
        ApplicationStatus.COMPLETED -> TimelineState.COMPLETED
        ApplicationStatus.NEED_REVISION -> when (index) {
            0, 1 -> TimelineState.COMPLETED
            2 -> TimelineState.ERROR
            else -> TimelineState.INACTIVE
        }
    }
}

fun getStageSubtitle(index: Int, status: ApplicationStatus): String? {
    if (status == ApplicationStatus.NEED_REVISION && index == 2) {
        return "Dokumen perlu diperbaiki"
    }
    return null
}

@Composable
fun TimelineItem(title: String, subtitle: String?, state: TimelineState, isLast: Boolean) {
    val isDark = isSystemInDarkTheme()
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val color = when (state) {
                TimelineState.COMPLETED -> if (isDark) SuccessDark else Color(0xFF4CAF50)
                TimelineState.ACTIVE -> MaterialTheme.colorScheme.primary
                TimelineState.INACTIVE -> MaterialTheme.colorScheme.outline
                TimelineState.ERROR -> MaterialTheme.colorScheme.error
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (state == TimelineState.ACTIVE) FontWeight.Bold else FontWeight.Normal,
                color = if (state == TimelineState.INACTIVE) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = if (state == TimelineState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

data class DocumentRequirement(val label: String, val isAttached: Boolean)

fun getDocumentRequirements(application: ApplicationEntity): List<DocumentRequirement> {
    val service = application.serviceType.uppercase()
    val cat = application.category.uppercase()

    return when (service) {
        "KIA" -> {
            when {
                isKiaPlusKk(cat) -> emptyList()
                cat.contains("RUSAK") -> listOf(
                    DocumentRequirement("Upload Kartu Keluarga Terbaru", !application.doc1.isNullOrBlank()),
                    DocumentRequirement("Upload KIA Lama", !application.doc2.isNullOrBlank()),
                    DocumentRequirement("Upload Selfie", !application.docSelfie.isNullOrBlank())
                )
                cat.contains("HILANG") -> listOf(
                    DocumentRequirement("Upload Kartu Keluarga Terbaru", !application.doc1.isNullOrBlank()),
                    DocumentRequirement("Upload Surat Kehilangan", !application.doc2.isNullOrBlank()),
                    DocumentRequirement("Upload Selfie", !application.docSelfie.isNullOrBlank())
                )
                else -> listOf(
                    DocumentRequirement("Upload Kartu Keluarga Terbaru", !application.doc1.isNullOrBlank()),
                    DocumentRequirement("Upload Akta Lahir Anak", !application.doc2.isNullOrBlank()),
                    DocumentRequirement("Upload Pas Foto Anak 3x4", !application.doc3.isNullOrBlank()),
                    DocumentRequirement("Upload Selfie", !application.docSelfie.isNullOrBlank())
                )
            }
        }
        "KTP" -> {
            when {
                cat.contains("RUSAK") || cat.contains("RUBAH") -> listOf(
                    DocumentRequirement("Upload Kartu Keluarga Terbaru", !application.doc1.isNullOrBlank()),
                    DocumentRequirement("Upload KTP Lama", !application.doc2.isNullOrBlank()),
                    DocumentRequirement("Upload Foto Selfie", !application.docSelfie.isNullOrBlank())
                )
                cat.contains("HILANG") -> listOf(
                    DocumentRequirement("Upload Kartu Keluarga Terbaru", !application.doc1.isNullOrBlank()),
                    DocumentRequirement("Upload Surat Keterangan Hilang", !application.doc2.isNullOrBlank()),
                    DocumentRequirement("Upload Foto Selfie", !application.docSelfie.isNullOrBlank())
                )
                cat.contains("PEREKAMAN") -> listOf(
                    DocumentRequirement("Upload Kartu Keluarga Terbaru", !application.doc1.isNullOrBlank()),
                    DocumentRequirement("Upload Foto Selfie", !application.docSelfie.isNullOrBlank())
                )
                else -> listOf(
                    DocumentRequirement("Upload Kartu Keluarga Terbaru", !application.doc1.isNullOrBlank()),
                    DocumentRequirement("Upload KTP Lama", !application.doc2.isNullOrBlank()),
                    DocumentRequirement("Upload Foto Selfie", !application.docSelfie.isNullOrBlank())
                )
            }
        }
        "KK" -> {
            val list = mutableListOf<DocumentRequirement>()
            list.add(DocumentRequirement("Kartu Keluarga Terbaru", !application.doc1.isNullOrBlank()))
            if (!application.doc2.isNullOrBlank()) list.add(DocumentRequirement("F1-01", true))
            if (!application.doc3.isNullOrBlank()) list.add(DocumentRequirement("F1-02", true))
            if (!application.doc4.isNullOrBlank()) list.add(DocumentRequirement("F1-03", true))
            if (!application.doc5.isNullOrBlank()) list.add(DocumentRequirement("F1-06", true))
            if (!application.doc6.isNullOrBlank()) list.add(DocumentRequirement("SPTJM Pendaftaran Penduduk", true))
            if (!application.doc7.isNullOrBlank()) list.add(DocumentRequirement("Surat Pernyataan Tempat Tinggal", true))
            if (!application.doc8.isNullOrBlank()) list.add(DocumentRequirement("Surat Keterangan Hilang / KK Rusak", true))
            if (!application.doc9.isNullOrBlank()) list.add(DocumentRequirement("KTP Hilang / Rusak", true))
            if (!application.doc10.isNullOrBlank()) list.add(DocumentRequirement("Dokumen Pendukung", true))
            list.add(DocumentRequirement("Foto Selfie", !application.docSelfie.isNullOrBlank()))
            list
        }
        "AKTA" -> {
            val list = mutableListOf<DocumentRequirement>()
            list.add(DocumentRequirement("Upload Kartu Keluarga Terbaru", !application.doc1.isNullOrBlank()))
            list.add(DocumentRequirement("Upload KTP Ibu", !application.doc2.isNullOrBlank()))
            list.add(DocumentRequirement("Upload KTP Bapak", !application.doc3.isNullOrBlank()))
            list.add(DocumentRequirement("Upload Formulir Pelaporan Pencatatan Sipil (F-2.01)", !application.doc4.isNullOrBlank()))
            list.add(DocumentRequirement("Upload Formulir Peristiwa Kependudukan (F-1.02)", !application.doc5.isNullOrBlank()))
            list.add(DocumentRequirement("Upload Surat Kelahiran / SPTJM", !application.doc6.isNullOrBlank()))
            list.add(DocumentRequirement("Upload Buku Nikah / SPTJM", !application.doc7.isNullOrBlank()))

            val isTerlambatKiaKkNoKtp = cat.contains("TERLAMBAT + KIA + KK") && !cat.contains("KTP")
            val isTerlambatOrKtp = cat.contains("TERLAMBAT") || cat.contains("KTP")

            if (isTerlambatKiaKkNoKtp) {
                list.add(DocumentRequirement("Upload SPTJM Kebenaran Data", !application.doc8.isNullOrBlank()))
                list.add(DocumentRequirement("Upload Formulir Pelaporan Kelahiran", !application.doc9.isNullOrBlank()))
            } else if (isTerlambatOrKtp) {
                list.add(DocumentRequirement("Upload KTP Anak", !application.doc8.isNullOrBlank()))
            }

            list.add(DocumentRequirement("Upload Selfie Pelapor", !application.docSelfie.isNullOrBlank()))
            list
        }
        "DISABILITAS" -> {
            listOf(
                DocumentRequirement("Kartu Keluarga Terbaru", !application.doc1.isNullOrBlank()),
                DocumentRequirement("KTP", !application.doc2.isNullOrBlank()),
                DocumentRequirement("Foto Selfie Pelapor", !application.docSelfie.isNullOrBlank())
            )
        }
        else -> {
            listOf(
                DocumentRequirement("Kartu Keluarga Terbaru", !application.doc1.isNullOrBlank()),
                DocumentRequirement("Foto Selfie", !application.docSelfie.isNullOrBlank())
            )
        }
    }
}

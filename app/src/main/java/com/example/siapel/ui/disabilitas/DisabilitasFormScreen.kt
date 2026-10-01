package com.example.siapel.ui.disabilitas

import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.siapel.data.WilayahData
import com.example.siapel.model.SelectedDocument
import com.example.siapel.ui.components.SiapelDropdown
import com.example.siapel.ui.components.SiapelPrimaryButton
import com.example.siapel.ui.components.SiapelTextField
import com.example.siapel.ui.components.SiapelDocumentPicker
import com.example.siapel.ui.theme.*
import com.example.siapel.viewmodel.DisabilitasViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisabilitasFormScreen(
    navController: NavController,
    viewModel: DisabilitasViewModel
) {
    val uiState by viewModel.uiState
    val isEditMode by viewModel.isEditMode
    val submitState by viewModel.submitState
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.submissionResult.collect { success ->
            if (success) {
                navController.popBackStack()
            } else {
                snackbarHostState.showSnackbar(if (isEditMode) "Gagal menyimpan perubahan. Silakan coba lagi." else "Pengajuan gagal dikirim. Silakan coba lagi.")
            }
        }
    }

    val kategoriOptions = listOf("Biometric", "Perekaman KTP")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Layanan Disabilitas",
                        color = MaterialTheme.colorScheme.onSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Formulir Pelayanan Khusus Disabilitas",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Lengkapi data dan dokumen untuk pengajuan layanan disabilitas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Formulir terdiri dari data pelapor, pilihan layanan, dan dokumen pendukung.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoCard(text = "Pastikan dokumen dan foto yang dipilih jelas dan dapat dibaca.")
                }
            }

            // SECTION 1 — INFORMASI LAYANAN
            item {
                FormSection(title = "Informasi Layanan") {
                    SiapelDropdown(
                        label = "Kategori Layanan",
                        selectedValue = uiState.kategoriLayanan,
                        options = kategoriOptions,
                        onOptionSelected = { viewModel.onKategoriLayananChange(it) },
                        placeholder = "Pilih kategori layanan"
                    )
                    uiState.kategoriError?.let { ErrorText(it) }
                }
            }

            // SECTION 2 — WILAYAH DAN KONTAK
            item {
                FormSection(title = "Wilayah dan Kontak") {
                    SiapelDropdown(
                        label = "Kecamatan",
                        selectedValue = uiState.kecamatan,
                        options = WilayahData.daftarKecamatan,
                        onOptionSelected = { viewModel.onKecamatanChange(it) },
                        placeholder = "Pilih kecamatan"
                    )
                    uiState.kecamatanError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelDropdown(
                        label = "Kelurahan",
                        selectedValue = uiState.kelurahan,
                        options = WilayahData.getKelurahan(uiState.kecamatan),
                        onOptionSelected = { viewModel.onKelurahanChange(it) },
                        placeholder = "Pilih kelurahan",
                        enabled = uiState.kecamatan.isNotEmpty()
                    )
                    uiState.kelurahanError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.onEmailChange(it) },
                        placeholder = "Masukkan email",
                        keyboardType = KeyboardType.Email
                    )
                    uiState.emailError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelTextField(
                        value = uiState.whatsapp,
                        onValueChange = { viewModel.onWhatsappChange(it) },
                        placeholder = "Masukkan nomor WhatsApp",
                        keyboardType = KeyboardType.Phone
                    )
                    uiState.whatsappError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.keterangan,
                        onValueChange = { viewModel.onKeteranganChange(it) },
                        placeholder = { Text("Tambahkan keterangan jika diperlukan", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            // SECTION 3 — DATA PELAPOR
            item {
                FormSection(title = "Data Pelapor") {
                    SiapelTextField(
                        value = uiState.nikPelapor,
                        onValueChange = { viewModel.onNikPelaporChange(it) },
                        placeholder = "Masukkan 16 digit NIK pelapor",
                        keyboardType = KeyboardType.Number
                    )
                    uiState.nikPelaporError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelTextField(
                        value = uiState.namaPelapor,
                        onValueChange = { viewModel.onNamaPelaporChange(it) },
                        placeholder = "Masukkan nama lengkap pelapor"
                    )
                    uiState.namaPelaporError?.let { ErrorText(it) }
                }
            }

            // SECTION 4 — DOKUMEN PERSYARATAN
            item {
                FormSection(title = "Dokumen Persyaratan") {
                    SiapelDocumentPicker(
                        title = "Kartu Keluarga Terbaru",
                        subtitle = "Unggah file KK terbaru",
                        selectedDoc = uiState.docKk,
                        onDocumentSelected = { viewModel.onDocKkChange(it) },
                        onClearFile = { viewModel.onDocKkChange(null) }
                    )
                    uiState.docKkError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelDocumentPicker(
                        title = "KTP",
                        subtitle = "Unggah file KTP",
                        selectedDoc = uiState.docKtp,
                        onDocumentSelected = { viewModel.onDocKtpChange(it) },
                        onClearFile = { viewModel.onDocKtpChange(null) }
                    )
                    uiState.docKtpError?.let { ErrorText(it) }
                }
            }

            // SECTION 5 — FOTO SELFIE
            item {
                FormSection(title = "Foto Selfie") {
                    SiapelDocumentPicker(
                        title = "Foto Selfie Pelapor",
                        subtitle = "Pilih Foto Selfie dari perangkat",
                        selectedDoc = uiState.docSelfie,
                        onDocumentSelected = { viewModel.onDocSelfieChange(it) },
                        onClearFile = { viewModel.onDocSelfieChange(null) }
                    )
                    uiState.docSelfieError?.let { ErrorText(it) }
                }
            }

            // SECTION 6 — SUBMIT
            item {
                SiapelPrimaryButton(
                    text = if (isEditMode) "Simpan Perubahan" else "Kirim Pengajuan",
                    onClick = {
                        viewModel.submitForm()
                    },
                    submitState = submitState,
                    loadingText = if (isEditMode) "Menyimpan perubahan" else "Mengirim pengajuan",
                    successText = if (isEditMode) "Perubahan berhasil disimpan" else "Berhasil dikirim",
                    errorText = if (isEditMode) "Gagal menyimpan" else "Gagal dikirim",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
fun FormSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun InfoCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ErrorText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

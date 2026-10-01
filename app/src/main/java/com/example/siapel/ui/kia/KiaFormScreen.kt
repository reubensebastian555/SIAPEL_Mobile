package com.example.siapel.ui.kia

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.siapel.data.WilayahData
import com.example.siapel.ui.components.SiapelDropdown
import com.example.siapel.ui.components.SiapelPrimaryButton
import com.example.siapel.ui.components.SiapelTextField
import com.example.siapel.ui.components.SiapelDocumentPicker
import com.example.siapel.viewmodel.KiaViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KiaFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: KiaViewModel
) {
    val uiState by viewModel.uiState
    val isEditMode by viewModel.isEditMode
    val submitState by viewModel.submitState
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        viewModel.submissionResult.collect { success ->
            if (success) {
                onNavigateBack()
            } else {
                snackbarHostState.showSnackbar(if (isEditMode) "Gagal menyimpan perubahan. Silakan coba lagi." else "Pengajuan gagal dikirim. Silakan coba lagi.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Formulir KIA", 
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Kartu Identitas Anak",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Lengkapi data pengajuan layanan KIA",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                InfoCard()
            }

            // Section 1: Informasi Layanan
            item {
                FormSection(title = "Informasi Layanan") {
                    SiapelDropdown(
                        label = "Kategori Layanan",
                        selectedValue = uiState.kategoriLayanan,
                        options = listOf("KIA BARU", "KIA RUSAK", "KIA HILANG", "KIA RUBAH", "KIA + KK"),
                        onOptionSelected = { viewModel.onKategoriLayananChange(it) },
                        placeholder = "Pilih kategori layanan"
                    )
                    uiState.kategoriError?.let { ErrorText(it) }
                }
            }

            // Section 2: Wilayah dan Kontak
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
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    SiapelDropdown(
                        label = "Kelurahan",
                        selectedValue = uiState.kelurahan,
                        options = WilayahData.getKelurahan(uiState.kecamatan),
                        onOptionSelected = { viewModel.onKelurahanChange(it) },
                        placeholder = "Pilih kelurahan",
                        enabled = uiState.kecamatan.isNotEmpty()
                    )
                    uiState.kelurahanError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(8.dp))

                    SiapelTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.onEmailChange(it) },
                        placeholder = "Masukkan email",
                        leadingIcon = null,
                        keyboardType = KeyboardType.Email
                    )
                    uiState.emailError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(8.dp))

                    SiapelTextField(
                        value = uiState.whatsapp,
                        onValueChange = { viewModel.onWhatsappChange(it) },
                        placeholder = "Masukkan nomor WhatsApp",
                        leadingIcon = null,
                        keyboardType = KeyboardType.Phone
                    )
                    uiState.whatsappError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(8.dp))

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

            // Section 3: Data Pelapor
            item {
                FormSection(title = "Data Pelapor") {
                    SiapelTextField(
                        value = uiState.nikPelapor,
                        onValueChange = { viewModel.onNikPelaporChange(it) },
                        placeholder = "Masukkan 16 digit NIK pelapor",
                        leadingIcon = Icons.Default.Person,
                        keyboardType = KeyboardType.Number
                    )
                    uiState.nikPelaporError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(8.dp))

                    SiapelTextField(
                        value = uiState.namaPelapor,
                        onValueChange = { viewModel.onNamaPelaporChange(it) },
                        placeholder = "Masukkan nama lengkap pelapor",
                        leadingIcon = Icons.Default.Person
                    )
                    uiState.namaPelaporError?.let { ErrorText(it) }
                }
            }

            // Section 4: Data Anak
            item {
                FormSection(title = "Data Anak") {
                    SiapelTextField(
                        value = uiState.nikAnak,
                        onValueChange = { viewModel.onNikAnakChange(it) },
                        placeholder = "Masukkan 16 digit NIK anak",
                        leadingIcon = Icons.Default.ChildCare,
                        keyboardType = KeyboardType.Number
                    )
                    uiState.nikAnakError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(8.dp))

                    SiapelTextField(
                        value = uiState.namaAnak,
                        onValueChange = { viewModel.onNamaAnakChange(it) },
                        placeholder = "Masukkan nama lengkap anak",
                        leadingIcon = Icons.Default.Person
                    )
                    uiState.namaAnakError?.let { ErrorText(it) }
                }
            }

            // Section 5: Dokumen Persyaratan (Conditional)
            when (uiState.kategoriLayanan.uppercase()) {
                "KIA BARU", "KIA RUBAH" -> {
                    item {
                        FormSection(title = "Dokumen Persyaratan") {
                            SiapelDocumentPicker(
                                title = "Upload Kartu Keluarga Terbaru",
                                subtitle = "Pilih file Kartu Keluarga",
                                selectedDoc = uiState.docKkTerbaru,
                                onDocumentSelected = { viewModel.onDocKkTerbaruChange(it) },
                                onClearFile = { viewModel.onDocKkTerbaruChange(null) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SiapelDocumentPicker(
                                title = "Upload Akta Lahir Anak",
                                subtitle = "Pilih file Akta Kelahiran Anak",
                                selectedDoc = uiState.docAktaLahirAnak,
                                onDocumentSelected = { viewModel.onDocAktaLahirAnakChange(it) },
                                onClearFile = { viewModel.onDocAktaLahirAnakChange(null) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SiapelDocumentPicker(
                                title = "Upload Pas Foto Anak 3x4",
                                subtitle = "Jika usia lebih dari 5 tahun",
                                selectedDoc = uiState.docPasFotoAnak,
                                onDocumentSelected = { viewModel.onDocPasFotoAnakChange(it) },
                                onClearFile = { viewModel.onDocPasFotoAnakChange(null) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SiapelDocumentPicker(
                                title = "Upload Selfie",
                                subtitle = "Pilih foto selfie pelapor",
                                selectedDoc = uiState.docSelfie,
                                onDocumentSelected = { viewModel.onDocSelfieChange(it) },
                                onClearFile = { viewModel.onDocSelfieChange(null) }
                            )
                        }
                    }
                }
                "KIA RUSAK" -> {
                    item {
                        FormSection(title = "Dokumen Persyaratan") {
                            SiapelDocumentPicker(
                                title = "Upload Kartu Keluarga Terbaru",
                                subtitle = "Pilih file Kartu Keluarga",
                                selectedDoc = uiState.docKkTerbaru,
                                onDocumentSelected = { viewModel.onDocKkTerbaruChange(it) },
                                onClearFile = { viewModel.onDocKkTerbaruChange(null) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SiapelDocumentPicker(
                                title = "Upload KIA Lama",
                                subtitle = "Pilih file KIA yang rusak",
                                selectedDoc = uiState.docKiaLama,
                                onDocumentSelected = { viewModel.onDocKiaLamaChange(it) },
                                onClearFile = { viewModel.onDocKiaLamaChange(null) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SiapelDocumentPicker(
                                title = "Upload Selfie",
                                subtitle = "Pilih foto selfie pelapor",
                                selectedDoc = uiState.docSelfie,
                                onDocumentSelected = { viewModel.onDocSelfieChange(it) },
                                onClearFile = { viewModel.onDocSelfieChange(null) }
                            )
                        }
                    }
                }
                "KIA HILANG" -> {
                    item {
                        FormSection(title = "Dokumen Persyaratan") {
                            SiapelDocumentPicker(
                                title = "Upload Kartu Keluarga Terbaru",
                                subtitle = "Pilih file Kartu Keluarga",
                                selectedDoc = uiState.docKkTerbaru,
                                onDocumentSelected = { viewModel.onDocKkTerbaruChange(it) },
                                onClearFile = { viewModel.onDocKkTerbaruChange(null) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SiapelDocumentPicker(
                                title = "Upload Surat Kehilangan",
                                subtitle = "Pilih surat kehilangan dari kepolisian",
                                selectedDoc = uiState.docSuratKehilangan,
                                onDocumentSelected = { viewModel.onDocSuratKehilanganChange(it) },
                                onClearFile = { viewModel.onDocSuratKehilanganChange(null) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SiapelDocumentPicker(
                                title = "Upload Selfie",
                                subtitle = "Pilih foto selfie pelapor",
                                selectedDoc = uiState.docSelfie,
                                onDocumentSelected = { viewModel.onDocSelfieChange(it) },
                                onClearFile = { viewModel.onDocSelfieChange(null) }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SiapelPrimaryButton(
                    text = if (isEditMode) "Simpan Perubahan" else "Kirim Pengajuan",
                    onClick = {
                        viewModel.submitForm()
                    },
                    submitState = submitState,
                    loadingText = if (isEditMode) "Menyimpan perubahan" else "Mengirim pengajuan",
                    successText = if (isEditMode) "Perubahan berhasil disimpan" else "Berhasil dikirim",
                    errorText = if (isEditMode) "Gagal menyimpan" else "Gagal dikirim"
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
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
fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pastikan data yang dimasukkan sesuai dengan dokumen kependudukan.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 16.sp
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

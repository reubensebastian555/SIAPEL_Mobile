package com.example.siapel.ui.akta

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.siapel.data.WilayahData
import com.example.siapel.ui.components.*
import com.example.siapel.ui.theme.*
import com.example.siapel.viewmodel.AktaKelahiranViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AktaKelahiranFormScreen(
    navController: NavController,
    viewModel: AktaKelahiranViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val isEditMode by viewModel.isEditMode
    val submitState by viewModel.submitState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        viewModel.submissionResult.collect { success ->
            if (success) {
                navController.popBackStack()
            } else {
                snackbarHostState.showSnackbar(if (isEditMode) "Gagal menyimpan perubahan. Silakan coba lagi." else "Pengajuan gagal dikirim. Silakan coba lagi.")
            }
        }
    }

    val kategoriOptions = listOf(
        "Paket Kelahiran Umum + KIA + KK",
        "Paket Kelahiran Terlambat + KIA + KK",
        "Kelahiran Terlambat Tunggal",
        "Paket Kelahiran Umum + KIA + KK + KTP",
        "Paket Kelahiran Terlambat + KIA + KK + KTP",
        "Paket Kelahiran Terlambat + KK + KTP",
        "Paket Kelahiran Terlambat + KTP"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Formulir Akta Kelahiran",
                        color = MaterialTheme.colorScheme.onSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSecondary)
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
                        text = "Formulir Akta Kelahiran",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Lengkapi data pengajuan Paket Akta Kelahiran",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Formulir terdiri dari data pengajuan, data bayi, dan informasi keluarga.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoCard(text = "Pastikan data bayi, ibu, pelapor, dan Kartu Keluarga sesuai dengan dokumen kependudukan.")
                }
            }

            // Section 1: Informasi Layanan
            item {
                FormSection(title = "Informasi Layanan") {
                    SiapelDropdown(
                        label = "Kategori Layanan",
                        selectedValue = uiState.kategoriLayanan,
                        options = kategoriOptions,
                        onOptionSelected = { viewModel.updateKategori(it) },
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
                        onOptionSelected = { viewModel.updateKecamatan(it) },
                        placeholder = "Pilih Kecamatan"
                    )
                    uiState.kecamatanError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelDropdown(
                        label = "Kelurahan",
                        selectedValue = uiState.kelurahan,
                        options = if (uiState.kecamatan.isNotEmpty()) WilayahData.getKelurahan(uiState.kecamatan) else emptyList(),
                        onOptionSelected = { viewModel.updateKelurahan(it) },
                        placeholder = "Pilih Kelurahan",
                        enabled = uiState.kecamatan.isNotEmpty()
                    )
                    uiState.kelurahanError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.updateEmail(it) },
                        placeholder = "Email",
                        leadingIcon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email
                    )
                    uiState.emailError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelTextField(
                        value = uiState.whatsapp,
                        onValueChange = { viewModel.updateWhatsapp(it) },
                        placeholder = "Nomor WhatsApp",
                        leadingIcon = Icons.Default.Phone,
                        keyboardType = KeyboardType.Phone
                    )
                    uiState.whatsappError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.keterangan,
                        onValueChange = { viewModel.updateKeterangan(it) },
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
                        onValueChange = { viewModel.updateNikPelapor(it) },
                        placeholder = "Masukkan 16 digit NIK pelapor",
                        leadingIcon = Icons.Default.Person,
                        keyboardType = KeyboardType.Number
                    )
                    uiState.nikPelaporError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelTextField(
                        value = uiState.namaPelapor,
                        onValueChange = { viewModel.updateNamaPelapor(it) },
                        placeholder = "Masukkan nama lengkap pelapor",
                        leadingIcon = Icons.Default.AccountCircle
                    )
                    uiState.namaPelaporError?.let { ErrorText(it) }
                }
            }

            // Section 4: Data Bayi
            item {
                FormSection(title = "Data Bayi") {
                    SiapelTextField(
                        value = uiState.nikBayi,
                        onValueChange = { viewModel.updateNikBayi(it) },
                        placeholder = "Masukkan NIK bayi (opsional)",
                        leadingIcon = Icons.Default.Fingerprint,
                        keyboardType = KeyboardType.Number
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelTextField(
                        value = uiState.namaBayi,
                        onValueChange = { viewModel.updateNamaBayi(it) },
                        placeholder = "Masukkan nama lengkap bayi",
                        leadingIcon = Icons.Default.ChildCare
                    )
                    uiState.namaBayiError?.let { ErrorText(it) }
                }
            }

            // Section 5: Data Ibu dan Keluarga
            item {
                FormSection(title = "Data Ibu dan Keluarga") {
                    SiapelTextField(
                        value = uiState.nikIbu,
                        onValueChange = { viewModel.updateNikIbu(it) },
                        placeholder = "Masukkan 16 digit NIK ibu",
                        leadingIcon = Icons.Default.Face,
                        keyboardType = KeyboardType.Number
                    )
                    uiState.nikIbuError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelTextField(
                        value = uiState.namaIbu,
                        onValueChange = { viewModel.updateNamaIbu(it) },
                        placeholder = "Masukkan nama lengkap ibu",
                        leadingIcon = Icons.Default.AccountBox
                    )
                    uiState.namaIbuError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    SiapelTextField(
                        value = uiState.nomorKk,
                        onValueChange = { viewModel.updateNomorKk(it) },
                        placeholder = "Masukkan nomor Kartu Keluarga",
                        leadingIcon = Icons.Default.FormatListNumbered,
                        keyboardType = KeyboardType.Number
                    )
                    uiState.nomorKkError?.let { ErrorText(it) }
                }
            }

            // Section 6: Dokumen Persyaratan (Conditional)
            if (uiState.kategoriLayanan.isNotEmpty()) {
                item {
                    FormSection(title = "Dokumen Persyaratan") {
                        SiapelDocumentPicker(
                            title = "Upload Kartu Keluarga Terbaru",
                            subtitle = "Pilih file Kartu Keluarga",
                            selectedDoc = uiState.docKkTerbaru,
                            onDocumentSelected = { viewModel.updateDocKkTerbaru(it) },
                            onClearFile = { viewModel.updateDocKkTerbaru(null) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SiapelDocumentPicker(
                            title = "Upload KTP Ibu",
                            subtitle = "Pilih file KTP Ibu",
                            selectedDoc = uiState.docKtpIbu,
                            onDocumentSelected = { viewModel.updateDocKtpIbu(it) },
                            onClearFile = { viewModel.updateDocKtpIbu(null) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SiapelDocumentPicker(
                            title = "Upload KTP Bapak",
                            subtitle = "Pilih file KTP Bapak",
                            selectedDoc = uiState.docKtpBapak,
                            onDocumentSelected = { viewModel.updateDocKtpBapak(it) },
                            onClearFile = { viewModel.updateDocKtpBapak(null) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SiapelDocumentPicker(
                            title = "Upload Formulir Pelaporan Pencatatan Sipil (F-2.01)",
                            subtitle = "Pilih file F-2.01",
                            selectedDoc = uiState.docFormF201,
                            onDocumentSelected = { viewModel.updateDocFormF201(it) },
                            onClearFile = { viewModel.updateDocFormF201(null) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SiapelDocumentPicker(
                            title = "Upload Formulir Peristiwa Kependudukan (F-1.02)",
                            subtitle = "Pilih file F-1.02",
                            selectedDoc = uiState.docFormF102,
                            onDocumentSelected = { viewModel.updateDocFormF102(it) },
                            onClearFile = { viewModel.updateDocFormF102(null) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SiapelDocumentPicker(
                            title = "Upload Surat Kelahiran / SPTJM",
                            subtitle = "Kebenaran Data Kelahiran (F-2.03)",
                            selectedDoc = uiState.docSuratKelahiran,
                            onDocumentSelected = { viewModel.updateDocSuratKelahiran(it) },
                            onClearFile = { viewModel.updateDocSuratKelahiran(null) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SiapelDocumentPicker(
                            title = "Upload Buku Nikah / SPTJM",
                            subtitle = "Kebenaran Pasangan Suami Istri (F-2.04)",
                            selectedDoc = uiState.docBukuNikah,
                            onDocumentSelected = { viewModel.updateDocBukuNikah(it) },
                            onClearFile = { viewModel.updateDocBukuNikah(null) }
                        )

                        if (uiState.kategoriLayanan.contains("Terlambat + KIA + KK", ignoreCase = true) && !uiState.kategoriLayanan.contains("KTP", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(12.dp))
                            SiapelDocumentPicker(
                                title = "Upload SPTJM Kebenaran Data",
                                subtitle = "Kelahiran lebih dari 60 hari",
                                selectedDoc = uiState.docSptjmKelahiran,
                                onDocumentSelected = { viewModel.updateDocSptjmKelahiran(it) },
                                onClearFile = { viewModel.updateDocSptjmKelahiran(null) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SiapelDocumentPicker(
                                title = "Upload Formulir Pelaporan Kelahiran",
                                subtitle = "Kelahiran lebih dari 60 hari",
                                selectedDoc = uiState.docFormPelaporanKelahiran,
                                onDocumentSelected = { viewModel.updateDocFormPelaporanKelahiran(it) },
                                onClearFile = { viewModel.updateDocFormPelaporanKelahiran(null) }
                            )
                        } else if (uiState.kategoriLayanan.contains("Terlambat", ignoreCase = true) || uiState.kategoriLayanan.contains("KTP", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(12.dp))
                            SiapelDocumentPicker(
                                title = "Upload KTP Anak",
                                subtitle = "Apabila sudah berusia lebih dari 17 tahun",
                                selectedDoc = uiState.docKtpAnak,
                                onDocumentSelected = { viewModel.updateDocKtpAnak(it) },
                                onClearFile = { viewModel.updateDocKtpAnak(null) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        SiapelDocumentPicker(
                            title = "Upload Selfie Pelapor",
                            subtitle = "Pilih foto selfie pelapor",
                            selectedDoc = uiState.docSelfie,
                            onDocumentSelected = { viewModel.updateDocSelfie(it) },
                            onClearFile = { viewModel.updateDocSelfie(null) }
                        )
                    }
                }
            }

            // Submit Button
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
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

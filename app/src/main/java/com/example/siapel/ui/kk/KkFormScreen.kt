package com.example.siapel.ui.kk

import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.siapel.data.WilayahData
import com.example.siapel.model.SelectedDocument
import com.example.siapel.ui.components.SiapelDropdown
import com.example.siapel.ui.components.SiapelPrimaryButton
import com.example.siapel.ui.components.SiapelTextField
import com.example.siapel.ui.components.SiapelDocumentPicker
import com.example.siapel.ui.theme.Border
import com.example.siapel.ui.theme.PrimaryBlue
import com.example.siapel.ui.theme.PrimaryNavy
import com.example.siapel.ui.theme.Surface
import com.example.siapel.ui.theme.TextMuted
import com.example.siapel.ui.theme.TextPrimary
import com.example.siapel.ui.theme.TextSecondary
import com.example.siapel.viewmodel.KkViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KkFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: KkViewModel
) {
    val uiState by viewModel.uiState
    val isEditMode by viewModel.isEditMode
    val submitState by viewModel.submitState
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
                        "Formulir Kartu Keluarga",
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
                        text = "Formulir Kartu Keluarga",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Lengkapi data dan dokumen pengajuan Kartu Keluarga",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Formulir terdiri dari data pengajuan dan dokumen pendukung.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // SECTION 1 — INFORMASI LAYANAN
            item {
                FormSection(title = "Informasi Layanan") {
                    SiapelDropdown(
                        label = "Kategori Produk",
                        selectedValue = uiState.kategoriProduk,
                        options = listOf("KK Tunggal", "KK + KTP", "KK + KIA"),
                        onOptionSelected = { viewModel.onKategoriProdukChange(it) },
                        placeholder = "Pilih kategori produk"
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
                        keyboardType = KeyboardType.Email
                    )
                    uiState.emailError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(8.dp))

                    SiapelTextField(
                        value = uiState.whatsapp,
                        onValueChange = { viewModel.onWhatsappChange(it) },
                        placeholder = "Masukkan nomor WhatsApp",
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

            // SECTION 3 — DATA PELAPOR
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

            // SECTION 4 — DATA KELUARGA
            item {
                FormSection(title = "Data Keluarga") {
                    SiapelTextField(
                        value = uiState.nikAnak,
                        onValueChange = { viewModel.onNikAnakChange(it) },
                        placeholder = "Masukkan 16 digit NIK anak",
                        leadingIcon = Icons.Default.Person,
                        keyboardType = KeyboardType.Number
                    )
                    uiState.nikAnakError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(8.dp))

                    SiapelTextField(
                        value = uiState.namaAnak,
                        onValueChange = { viewModel.onNamaAnakChange(it) },
                        placeholder = "Masukkan nama anak",
                        leadingIcon = Icons.Default.Person
                    )
                    uiState.namaAnakError?.let { ErrorText(it) }

                    Spacer(modifier = Modifier.height(8.dp))

                    SiapelTextField(
                        value = uiState.nomorKk,
                        onValueChange = { viewModel.onNomorKkChange(it) },
                        placeholder = "Masukkan nomor Kartu Keluarga",
                        leadingIcon = Icons.Default.Groups,
                        keyboardType = KeyboardType.Number
                    )
                    uiState.nomorKkError?.let { ErrorText(it) }
                }
            }

            // INFORMATION CARD BEFORE DOCUMENTS
            item {
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
                            text = "Unggah dokumen sesuai dengan kebutuhan layanan yang dipilih. Pastikan dokumen dapat dibaca dengan jelas.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // SECTION 5 — DOKUMEN PERSYARATAN
            item {
                FormSection(title = "Dokumen Persyaratan (Unggah sesuai kebutuhan layanan)") {
                    SiapelDocumentPicker(
                        title = "Kartu Keluarga Terbaru",
                        subtitle = "Unggah file KK terbaru",
                        selectedDoc = uiState.docKkTerbaru,
                        onDocumentSelected = { viewModel.onDocKkTerbaruChange(it) },
                        onClearFile = { viewModel.onDocKkTerbaruChange(null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SiapelDocumentPicker(
                        title = "F1-01",
                        subtitle = "Perubahan Elemen KK / Pisah KK",
                        selectedDoc = uiState.docF101,
                        onDocumentSelected = { viewModel.onDocF101Change(it) },
                        onClearFile = { viewModel.onDocF101Change(null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SiapelDocumentPicker(
                        title = "F1-02",
                        subtitle = "Pendaftaran Peristiwa Kependudukan",
                        selectedDoc = uiState.docF102,
                        onDocumentSelected = { viewModel.onDocF102Change(it) },
                        onClearFile = { viewModel.onDocF102Change(null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SiapelDocumentPicker(
                        title = "F1-03",
                        subtitle = "Perubahan Alamat / Pindah Dalam Kota / Pisah KK",
                        selectedDoc = uiState.docF103,
                        onDocumentSelected = { viewModel.onDocF103Change(it) },
                        onClearFile = { viewModel.onDocF103Change(null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SiapelDocumentPicker(
                        title = "F1-06",
                        subtitle = "Perubahan Elemen KK Barcode",
                        selectedDoc = uiState.docF106,
                        onDocumentSelected = { viewModel.onDocF106Change(it) },
                        onClearFile = { viewModel.onDocF106Change(null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SiapelDocumentPicker(
                        title = "SPTJM Pendaftaran Penduduk",
                        subtitle = "Surat Pernyataan Tanggung Jawab Mutlak",
                        selectedDoc = uiState.docSptjm,
                        onDocumentSelected = { viewModel.onDocSptjmChange(it) },
                        onClearFile = { viewModel.onDocSptjmChange(null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SiapelDocumentPicker(
                        title = "Surat Pernyataan Tempat Tinggal",
                        subtitle = "Unggah surat pernyataan tempat tinggal",
                        selectedDoc = uiState.docSuratPernyataanTempatTinggal,
                        onDocumentSelected = { viewModel.onDocSuratPernyataanTempatTinggalChange(it) },
                        onClearFile = { viewModel.onDocSuratPernyataanTempatTinggalChange(null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SiapelDocumentPicker(
                        title = "Surat Keterangan Hilang / KK Rusak",
                        subtitle = "Unggah surat kehilangan dari kepolisian / KK rusak",
                        selectedDoc = uiState.docSuratKehilanganKkRusak,
                        onDocumentSelected = { viewModel.onDocSuratKehilanganKkRusakChange(it) },
                        onClearFile = { viewModel.onDocSuratKehilanganKkRusakChange(null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SiapelDocumentPicker(
                        title = "KTP Hilang / Rusak",
                        subtitle = "Unggah KTP hilang / rusak jika ada",
                        selectedDoc = uiState.docKtpHilangRusak,
                        onDocumentSelected = { viewModel.onDocKtpHilangRusakChange(it) },
                        onClearFile = { viewModel.onDocKtpHilangRusakChange(null) }
                    )
                }
            }

            // SECTION 6 — DOKUMEN PENDUKUNG & SELFIE
            item {
                FormSection(title = "Dokumen Pendukung & Selfie") {
                    SiapelDocumentPicker(
                        title = "Dokumen Pendukung",
                        subtitle = "Unggah dokumen pendukung lainnya jika ada",
                        selectedDoc = uiState.docPendukung,
                        onDocumentSelected = { viewModel.onDocPendukungChange(it) },
                        onClearFile = { viewModel.onDocPendukungChange(null) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SiapelDocumentPicker(
                        title = "Foto Selfie",
                        subtitle = "Pilih Foto Selfie dari perangkat",
                        selectedDoc = uiState.docSelfie,
                        onDocumentSelected = { viewModel.onDocSelfieChange(it) },
                        onClearFile = { viewModel.onDocSelfieChange(null) }
                    )
                }
            }

            // SECTION 7 — SUBMIT
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
fun ErrorText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

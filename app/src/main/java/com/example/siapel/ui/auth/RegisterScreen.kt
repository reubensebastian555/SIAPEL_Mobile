package com.example.siapel.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.siapel.data.WilayahData
import com.example.siapel.ui.components.SiapelDropdown
import com.example.siapel.ui.components.SiapelPasswordField
import com.example.siapel.ui.components.SiapelPrimaryButton
import com.example.siapel.ui.components.SiapelTextField
import com.example.siapel.ui.theme.*
import com.example.siapel.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit
) {
    val state by viewModel.registerState.collectAsState()
    val errorMsg by viewModel.registerError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val kelurahanOptions = remember(state.kecamatan) {
        WilayahData.getKelurahan(state.kecamatan)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            contentPadding = PaddingValues(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            item {
                Text(
                    text = "Pendaftaran Akun",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lengkapi data untuk membuat akun SIAPEL",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Error display
            if (errorMsg != null) {
                item {
                    Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 1. Nama Lengkap
            item {
                FormLabel(text = "Nama Lengkap")
                SiapelTextField(
                    value = state.namaLengkap,
                    onValueChange = { viewModel.onNamaLengkapChanged(it) },
                    placeholder = "Masukan nama lengkap"
                )
            }

            // 2. NIK
            item {
                FormLabel(text = "NIK")
                SiapelTextField(
                    value = state.nik,
                    onValueChange = { viewModel.onNikChanged(it) },
                    placeholder = "Masukan 16 digit NIK",
                    keyboardType = KeyboardType.Number
                )
            }

            // 3. Email
            item {
                FormLabel(text = "Email")
                SiapelTextField(
                    value = state.email,
                    onValueChange = { viewModel.onEmailRegisterChanged(it) },
                    placeholder = "Masukan email",
                    keyboardType = KeyboardType.Email
                )
            }

            // 4. Nomor WhatsApp
            item {
                FormLabel(text = "Nomor WhatsApp")
                SiapelTextField(
                    value = state.nomorWhatsapp,
                    onValueChange = { viewModel.onNomorWhatsappChanged(it) },
                    placeholder = "Masukan nomor WhatsApp",
                    keyboardType = KeyboardType.Phone
                )
            }

            // 5. Kecamatan
            item {
                FormLabel(text = "Kecamatan")
                SiapelDropdown(
                    label = "Pilih Kecamatan",
                    selectedValue = state.kecamatan,
                    options = WilayahData.daftarKecamatan,
                    onOptionSelected = { viewModel.onKecamatanChanged(it) },
                    placeholder = "Pilih Kecamatan Anda"
                )
            }

            // 6. Kelurahan
            item {
                FormLabel(text = "Kelurahan / Desa")
                SiapelDropdown(
                    label = "Pilih Kelurahan",
                    selectedValue = state.kelurahan,
                    options = kelurahanOptions,
                    onOptionSelected = { viewModel.onKelurahanChanged(it) },
                    placeholder = if (state.kecamatan.isEmpty()) "Pilih kecamatan dahulu" else "Pilih kelurahan Anda",
                    enabled = state.kecamatan.isNotEmpty()
                )
            }

            // 7. Password
            item {
                FormLabel(text = "Password")
                SiapelPasswordField(
                    value = state.password,
                    onValueChange = { viewModel.onPasswordRegisterChanged(it) },
                    placeholder = "Buat Password"
                )
            }

            // 8. Konfirmasi Password
            item {
                FormLabel(text = "Konfirmasi Password")
                SiapelPasswordField(
                    value = state.konfirmasiPassword,
                    onValueChange = { viewModel.onKonfirmasiPasswordChanged(it) },
                    placeholder = "Ulangi Password"
                )
            }

            // Submit Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SiapelPrimaryButton(
                    text = "Daftar",
                    onClick = {
                        viewModel.validateAndRegister {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Pendaftaran berhasil",
                                    duration = SnackbarDuration.Short
                                )
                                delay(1500)
                                onNavigateToLogin()
                            }
                        }
                    }
                )
            }

            // Footer Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Sudah punya akun? ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Masuk",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigateToLogin() }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun FormLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 6.getOrZeroDp())
    )
}

// Inline extension function to safely give small padding
private fun Int.getOrZeroDp() = this.dp

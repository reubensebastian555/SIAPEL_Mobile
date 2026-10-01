# Walkthrough - Konsistensi Total Label "Sedang Diproses" SIAPEL

## Overview
Menyelaraskan seluruh label tampilan status (*filter chips*, *status chips*, dan *home active application*) dari `"Diproses"` menjadi **"Sedang Diproses"** secara konsisten 1:1 di seluruh aplikasi.

## Changes Made

### 1. Konsistensi Label Teks (`StatusScreen.kt`, `HomeViewModel.kt`, `StatusViewModel.kt`)
- Memperbarui `StatusChip` di `StatusScreen.kt` dan `ActiveApplication` di `HomeViewModel.kt` dari `"Diproses"` menjadi `"Sedang Diproses"`.
- Mempertahankan fleksibilitas *filter* di `StatusViewModel.kt` untuk mendukung `"Sedang Diproses"` maupun `"Diproses"`.

## Verification Results
- **Build Check**: `:app:clean :app:assembleDebug` berhasil dengan sukses (`Build finished successfully.`).
- **Runtime Consistency**: Seluruh elemen UI (`StatusScreen` filter, `StatusScreen` chips, `StatusDetailScreen` chips, `HomeScreen` card, dan `StatusDetailScreen` timeline) kini menampilkan **"Sedang Diproses"** secara seragam.

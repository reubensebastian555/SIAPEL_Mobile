package com.example.siapel.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.siapel.model.ApplicationStatus

@Entity(tableName = "applications")
data class ApplicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,
    val serviceType: String, // e.g., "KIA", "KTP", "KK", "AKTA", "DISABILITAS"
    val category: String,
    val submissionCode: String,
    val submissionDate: String,
    val status: ApplicationStatus,
    val note: String? = null,

    // Wilayah & Kontak
    val kecamatan: String,
    val kelurahan: String,
    val email: String,
    val whatsapp: String,
    val keterangan: String,

    // Data Pelapor
    val nikPelapor: String,
    val namaPelapor: String,

    // Specific Fields (Nullable)
    val nikAnak: String? = null,
    val namaAnak: String? = null,
    val nikBayi: String? = null,
    val namaBayi: String? = null,
    val nikIbu: String? = null,
    val namaIbu: String? = null,
    val nomorKk: String? = null,

    // Document URIs (Simplified as Strings for Beta)
    val doc1: String? = null,
    val doc2: String? = null,
    val doc3: String? = null,
    val doc4: String? = null,
    val doc5: String? = null,
    val doc6: String? = null,
    val doc7: String? = null,
    val doc8: String? = null,
    val doc9: String? = null,
    val doc10: String? = null,
    val docSelfie: String? = null,
    
    val createdAt: Long = System.currentTimeMillis()
)

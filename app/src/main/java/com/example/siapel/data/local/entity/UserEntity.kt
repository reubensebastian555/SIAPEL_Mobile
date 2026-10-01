package com.example.siapel.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["nik"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val namaLengkap: String,
    val nik: String,
    val email: String,
    val whatsapp: String,
    val kecamatan: String,
    val kelurahan: String,
    val passwordHash: String,
    val createdAt: Long = System.currentTimeMillis()
)

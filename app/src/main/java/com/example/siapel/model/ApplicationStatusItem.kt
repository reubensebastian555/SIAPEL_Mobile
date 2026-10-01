package com.example.siapel.model

data class ApplicationStatusItem(
    val code: String,
    val serviceName: String,
    val category: String,
    val submittedDate: String,
    val status: ApplicationStatus,
    val note: String? = null
)

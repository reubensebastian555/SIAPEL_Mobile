package com.example.siapel.data.local.database

import androidx.room.TypeConverter
import com.example.siapel.model.ApplicationStatus

class Converters {
    @TypeConverter
    fun fromApplicationStatus(value: ApplicationStatus): String {
        return value.name
    }

    @TypeConverter
    fun toApplicationStatus(value: String): ApplicationStatus {
        return ApplicationStatus.valueOf(value)
    }
}

package com.example.siapel.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.siapel.data.local.dao.ApplicationDao
import com.example.siapel.data.local.dao.UserDao
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.local.entity.UserEntity

@Database(
    entities = [UserEntity::class, ApplicationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SiapelDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun applicationDao(): ApplicationDao

    companion object {
        @Volatile
        private var INSTANCE: SiapelDatabase? = null

        fun getDatabase(context: Context): SiapelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SiapelDatabase::class.java,
                    "siapel_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

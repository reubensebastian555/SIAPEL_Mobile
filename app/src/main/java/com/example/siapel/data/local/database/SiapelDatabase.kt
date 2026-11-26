package com.example.siapel.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.siapel.data.local.dao.ApplicationDao
import com.example.siapel.data.local.dao.UserDao
import com.example.siapel.data.local.entity.ApplicationEntity
import com.example.siapel.data.local.entity.UserEntity

@Database(
    entities = [UserEntity::class, ApplicationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SiapelDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun applicationDao(): ApplicationDao

    companion object {
        @Volatile
        private var INSTANCE: SiapelDatabase? = null

        /**
         * Migrasi non-destruktif dari Room Database v1 ke v2.
         * Menambahkan kolom remoteUuid dan remoteUserUuid untuk integrasi identitas Laravel REST API.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Tambah kolom remoteUuid & remoteUserUuid di tabel applications
                db.execSQL("ALTER TABLE applications ADD COLUMN remoteUuid TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE applications ADD COLUMN remoteUserUuid TEXT DEFAULT NULL")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_applications_remoteUuid` ON `applications` (`remoteUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_applications_userId` ON `applications` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_applications_remoteUserUuid` ON `applications` (`remoteUserUuid`)")

                // 2. Tambah kolom remoteUserUuid di tabel users
                db.execSQL("ALTER TABLE users ADD COLUMN remoteUserUuid TEXT DEFAULT NULL")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_remoteUserUuid` ON `users` (`remoteUserUuid`)")
            }
        }

        fun getDatabase(context: Context): SiapelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SiapelDatabase::class.java,
                    "siapel_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

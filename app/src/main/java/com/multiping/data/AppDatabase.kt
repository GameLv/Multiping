package com.multiping.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Host::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun hostDao(): HostDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hosts ADD COLUMN isFavourite INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE hosts ADD COLUMN alertSent INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hosts ADD COLUMN avgWindowSec INTEGER NOT NULL DEFAULT 60")
                database.execSQL("ALTER TABLE hosts ADD COLUMN avgPingMs INTEGER NOT NULL DEFAULT -1")
            }
        }

        // v3→v4: переход с useCurl: Boolean на checkMode: String
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hosts ADD COLUMN checkMode TEXT NOT NULL DEFAULT 'PING'")
                // Если был useCurl=1 — ставим HTTP
                database.execSQL("UPDATE hosts SET checkMode = 'HTTP' WHERE useCurl = 1")
            }
        }

        // v4→v5: добавляем TCP порт
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE hosts ADD COLUMN tcpPort INTEGER NOT NULL DEFAULT 80")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "multiping.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { INSTANCE = it }
            }
    }
}

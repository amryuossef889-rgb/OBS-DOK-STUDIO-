package com.dokstudio.obs

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dokstudio.obs.data.AppDatabase
import com.dokstudio.obs.data.SceneRepository

class DokStudioApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var repository: SceneRepository

    override fun onCreate() {
        super.onCreate()
        val migration1to2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stream_profiles (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        serverUrl TEXT NOT NULL,
                        encryptedStreamKey TEXT NOT NULL,
                        bitrate INTEGER NOT NULL,
                        fps INTEGER NOT NULL,
                        resolution TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
        database = Room.databaseBuilder(this, AppDatabase::class.java, "dokstudio.db")
            .addMigrations(migration1to2)
            .build()
        repository = SceneRepository(
            database.sceneDao(),
            database.sourceDao(),
            database.recordingDao(),
        )
    }
}

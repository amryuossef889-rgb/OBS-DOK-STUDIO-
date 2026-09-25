package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.database.dao.ProjectDao
import com.example.data.database.dao.RecordingDao
import com.example.data.database.dao.SceneDao
import com.example.data.database.dao.SourceDao
import com.example.data.database.dao.StreamProfileDao
import com.example.data.database.entity.ProjectEntity
import com.example.data.database.entity.RecordingEntity
import com.example.data.database.entity.SceneEntity
import com.example.data.database.entity.SourceEntity
import com.example.data.database.entity.StreamProfileEntity

@Database(
    entities = [
        ProjectEntity::class,
        SceneEntity::class,
        SourceEntity::class,
        RecordingEntity::class,
        StreamProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ObsDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun sceneDao(): SceneDao
    abstract fun sourceDao(): SourceDao
    abstract fun recordingDao(): RecordingDao
    abstract fun streamProfileDao(): StreamProfileDao

    companion object {
        @Volatile
        private var INSTANCE: ObsDatabase? = null

        fun getInstance(context: Context): ObsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ObsDatabase::class.java,
                    "obs_dok_studio.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

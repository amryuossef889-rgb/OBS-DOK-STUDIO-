package com.dokstudio.obs
import android.app.Application
import androidx.room.Room
import com.dokstudio.obs.data.AppDatabase
import com.dokstudio.obs.data.SceneRepository
class DokStudioApplication:Application(){ lateinit var database:AppDatabase; lateinit var repository:SceneRepository
 override fun onCreate(){super.onCreate();database=Room.databaseBuilder(this,AppDatabase::class.java,"dokstudio.db").fallbackToDestructiveMigration().build();repository=SceneRepository(database.sceneDao(),database.sourceDao())}
}
package com.example

import android.app.Application
import com.example.data.database.ObsDatabase
import com.example.data.repository.StudioRepository

class ObsDokApplication : Application() {

    lateinit var database: ObsDatabase
        private set

    lateinit var repository: StudioRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = ObsDatabase.getInstance(this)
        repository = StudioRepository(database)
    }

    companion object {
        lateinit var instance: ObsDokApplication
            private set
    }
}

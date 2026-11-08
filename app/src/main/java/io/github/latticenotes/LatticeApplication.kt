package io.github.latticenotes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import io.github.latticenotes.data.AppDatabase
import io.github.latticenotes.data.NotesRepository
import io.github.latticenotes.data.SettingsRepository

class AppContainer(application: Application) {
    private val database = AppDatabase.build(application)
    val notes = NotesRepository(application, database.notesDao())
    val settings = SettingsRepository(application)
}

class LatticeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "note_reminders",
                "Note reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}

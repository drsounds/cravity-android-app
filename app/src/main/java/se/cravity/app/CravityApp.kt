package se.cravity.app

import android.app.Application
import se.cravity.app.data.CravityDatabase
import se.cravity.app.data.IntakeRepository
import se.cravity.app.data.Settings
import se.cravity.app.widget.WidgetRefreshWorker

class CravityApp : Application() {
    val repository: IntakeRepository by lazy { IntakeRepository(CravityDatabase.get(this).intakeDao()) }
    val settings: Settings by lazy { Settings(this) }

    override fun onCreate() {
        super.onCreate()
        WidgetRefreshWorker.schedule(this)
    }
}

val android.content.Context.cravity: CravityApp get() = applicationContext as CravityApp

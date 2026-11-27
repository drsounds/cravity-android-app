package se.cravity.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [IntakeEntity::class], version = 1, exportSchema = true)
abstract class CravityDatabase : RoomDatabase() {
    abstract fun intakeDao(): IntakeDao

    companion object {
        @Volatile private var instance: CravityDatabase? = null

        fun get(context: Context): CravityDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CravityDatabase::class.java, "cravity.db")
                .build().also { instance = it }
        }
    }
}

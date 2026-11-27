package se.cravity.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeDao {
    @Query("SELECT * FROM intake ORDER BY timeMillis DESC")
    fun observeAll(): Flow<List<IntakeEntity>>

    @Query("SELECT * FROM intake ORDER BY timeMillis DESC")
    suspend fun getAll(): List<IntakeEntity>

    @Query("SELECT * FROM intake WHERE timeMillis >= :fromMillis ORDER BY timeMillis")
    suspend fun since(fromMillis: Long): List<IntakeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: IntakeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<IntakeEntity>)

    @Query("DELETE FROM intake WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM intake")
    suspend fun deleteAll()
}

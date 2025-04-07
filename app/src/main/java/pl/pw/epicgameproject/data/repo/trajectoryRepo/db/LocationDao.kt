package pl.pw.epicgameproject.data.repo.trajectoryRepo.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pl.pw.epicgameproject.data.repo.trajectoryRepo.db.TrajPointEntity
@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(
        location: TrajPointEntity
    )

    @Query("SELECT * FROM locations")
    fun getAll(): Flow<List<TrajPointEntity>>
}

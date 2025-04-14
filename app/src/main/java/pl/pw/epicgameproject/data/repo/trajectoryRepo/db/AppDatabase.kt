package pl.pw.epicgameproject.data.repo.trajectoryRepo.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrajPointEntity::class,
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao
}

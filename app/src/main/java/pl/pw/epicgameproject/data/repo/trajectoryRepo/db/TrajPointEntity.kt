package pl.pw.epicgameproject.data.repo.trajectoryRepo.db

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "locations")
data class TrajPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val userid: Long
)
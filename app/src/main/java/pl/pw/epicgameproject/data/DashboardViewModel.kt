package pl.pw.epicgameproject.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import pl.pw.epicgameproject.data.repo.trajectoryRepo.db.LocationDao
import pl.pw.epicgameproject.data.repo.trajectoryRepo.db.TrajPointEntity
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val locationDao: LocationDao
) : ViewModel() {

    val locations: Flow<List<TrajPointEntity>> = locationDao.getAll()

    fun saveLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            locationDao.save(
                TrajPointEntity(
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = System.currentTimeMillis(),
                    userid = 1
                )
            )
        }
    }
}


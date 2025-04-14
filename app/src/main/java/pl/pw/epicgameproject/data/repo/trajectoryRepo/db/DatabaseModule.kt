package pl.pw.epicgameproject.data.repo.trajectoryRepo.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pl.pw.epicgameproject.data.repo.trajectoryRepo.db.LocationDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "test_app_db"
    ).build()

    @Provides
    fun provideLocationDao(
        database: AppDatabase
    ): LocationDao = database.locationDao()
}

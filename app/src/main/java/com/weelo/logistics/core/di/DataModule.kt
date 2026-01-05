package com.weelo.logistics.core.di

import android.content.Context
import androidx.room.Room
import com.weelo.logistics.core.util.Constants
import com.weelo.logistics.data.local.WeeloDatabase
import com.weelo.logistics.data.local.dao.LocationDao
import com.weelo.logistics.data.local.dao.VehicleDao
import com.weelo.logistics.data.local.preferences.PreferencesManager
import com.weelo.logistics.data.repository.LocationRepositoryImpl
import com.weelo.logistics.data.repository.VehicleRepositoryImpl
import com.weelo.logistics.data.repository.BookingRepositoryImpl
import com.weelo.logistics.domain.repository.LocationRepository
import com.weelo.logistics.domain.repository.VehicleRepository
import com.weelo.logistics.domain.repository.BookingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for data layer dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideWeeloDatabase(@ApplicationContext context: Context): WeeloDatabase {
        return Room.databaseBuilder(
            context,
            WeeloDatabase::class.java,
            Constants.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideLocationDao(database: WeeloDatabase): LocationDao {
        return database.locationDao()
    }

    @Provides
    @Singleton
    fun provideVehicleDao(database: WeeloDatabase): VehicleDao {
        return database.vehicleDao()
    }

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideLocationRepository(
        locationDao: LocationDao,
        preferencesManager: PreferencesManager
    ): LocationRepository {
        return LocationRepositoryImpl(locationDao, preferencesManager)
    }

    @Provides
    @Singleton
    fun provideVehicleRepository(
        vehicleDao: VehicleDao,
        @ApplicationContext context: Context
    ): VehicleRepository {
        return VehicleRepositoryImpl(vehicleDao, context)
    }

    @Provides
    @Singleton
    fun provideBookingRepository(): BookingRepository {
        return BookingRepositoryImpl()
    }
}

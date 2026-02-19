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
import com.weelo.logistics.data.repository.BookingApiRepository
import com.weelo.logistics.data.repository.AuthRepositoryImpl
import com.weelo.logistics.data.repository.PricingRepository
import com.weelo.logistics.domain.repository.LocationRepository
import com.weelo.logistics.domain.repository.VehicleRepository
import com.weelo.logistics.domain.repository.BookingRepository
import com.weelo.logistics.domain.repository.AuthRepository
import com.weelo.logistics.data.remote.api.WeeloApiService
import com.weelo.logistics.data.remote.TokenManager
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
    fun providePendingOperationDao(database: WeeloDatabase): com.weelo.logistics.data.local.dao.PendingOperationDao {
        return database.pendingOperationDao()
    }
    
    @Provides
    @Singleton
    fun provideCachedBookingDao(database: WeeloDatabase): com.weelo.logistics.data.local.dao.CachedBookingDao {
        return database.cachedBookingDao()
    }
    
    @Provides
    @Singleton
    fun provideSyncManager(
        pendingOperationDao: com.weelo.logistics.data.local.dao.PendingOperationDao,
        cachedBookingDao: com.weelo.logistics.data.local.dao.CachedBookingDao,
        apiService: WeeloApiService,
        tokenManager: com.weelo.logistics.data.remote.TokenManager,
        networkMonitor: com.weelo.logistics.core.util.NetworkMonitor,
        resilientExecutor: com.weelo.logistics.core.network.ResilientApiExecutor
    ): com.weelo.logistics.data.sync.SyncManager {
        return com.weelo.logistics.data.sync.SyncManager(
            pendingOperationDao,
            cachedBookingDao,
            apiService,
            tokenManager,
            networkMonitor,
            resilientExecutor
        )
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
    fun provideBookingRepository(
        apiService: WeeloApiService,
        tokenManager: TokenManager
    ): BookingRepository {
        return BookingApiRepository(apiService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        apiService: WeeloApiService,
        tokenManager: TokenManager
    ): AuthRepository {
        return AuthRepositoryImpl(apiService, tokenManager)
    }

    @Provides
    @Singleton
    fun providePricingRepository(
        apiService: WeeloApiService,
        tokenManager: TokenManager
    ): PricingRepository {
        return PricingRepository(apiService, tokenManager)
    }

    @Provides
    @Singleton
    fun provideTrackingRepository(
        apiService: WeeloApiService,
        tokenManager: TokenManager,
        webSocketService: com.weelo.logistics.data.remote.WebSocketService
    ): com.weelo.logistics.data.repository.TrackingRepository {
        return com.weelo.logistics.data.repository.TrackingRepository(apiService, tokenManager, webSocketService)
    }

}

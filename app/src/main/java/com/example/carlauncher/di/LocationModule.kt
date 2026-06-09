package com.example.carlauncher.di

import android.content.Context
import com.example.carlauncher.data.location.LocationProcessor
import com.example.carlauncher.data.location.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideFusedLocationClient(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideLocationProcessor(): LocationProcessor = LocationProcessor()

    @Provides
    @Singleton
    fun provideLocationRepository(
        @ApplicationContext context: Context,
        fusedClient: FusedLocationProviderClient,
        processor: LocationProcessor
    ): LocationRepository = LocationRepository(context, fusedClient, processor)
}

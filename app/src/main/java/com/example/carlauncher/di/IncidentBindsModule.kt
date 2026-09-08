package com.example.carlauncher.di

import com.example.carlauncher.data.incident.MlKitPlateDetector
import com.example.carlauncher.data.incident.PlateDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the [PlateDetector] interface to its shipping implementation. Swap
 * [MlKitPlateDetector] for a TFLite-backed detector here without touching the recorder or UI.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IncidentBindsModule {

    @Binds
    @Singleton
    abstract fun bindPlateDetector(impl: MlKitPlateDetector): PlateDetector
}

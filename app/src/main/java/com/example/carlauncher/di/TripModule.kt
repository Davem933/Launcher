package com.example.carlauncher.di

import android.content.Context
import androidx.room.Room
import com.example.carlauncher.data.trip.TripDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TripModule {

    @Provides
    @Singleton
    fun providesTripDatabase(@ApplicationContext context: Context): TripDatabase =
        Room.databaseBuilder(context, TripDatabase::class.java, "trip_database")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun providesTripDao(db: TripDatabase) = db.tripDao()
}

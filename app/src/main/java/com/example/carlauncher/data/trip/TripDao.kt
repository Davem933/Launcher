package com.example.carlauncher.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startTime DESC LIMIT :limit")
    fun getLastTrips(limit: Int): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE startTime >= :from AND startTime <= :to ORDER BY startTime DESC")
    fun getTripsInRange(from: Long, to: Long): Flow<List<TripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripEntity)
}

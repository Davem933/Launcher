package com.example.carlauncher.data.trip

import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val dao: TripDao
) {
    val lastTrips: Flow<List<TripEntity>> = dao.getLastTrips(30)

    val tripsThisWeek: Flow<List<TripEntity>>
        get() {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            }
            val weekStart = cal.timeInMillis
            val weekEnd = weekStart + 7L * 24 * 60 * 60 * 1000
            return dao.getTripsInRange(weekStart, weekEnd)
        }

    suspend fun save(trip: TripEntity) = dao.insert(trip)
}

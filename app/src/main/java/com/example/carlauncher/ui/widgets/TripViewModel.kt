package com.example.carlauncher.ui.widgets

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.trip.CsvExporter
import com.example.carlauncher.data.trip.LiveTripState
import com.example.carlauncher.data.trip.TripDetector
import com.example.carlauncher.data.trip.TripEntity
import com.example.carlauncher.data.trip.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TripViewModel @Inject constructor(
    tripDetector: TripDetector,
    private val tripRepository: TripRepository
) : ViewModel() {

    val liveTrip: StateFlow<LiveTripState> = tripDetector.state

    val lastTrips: StateFlow<List<TripEntity>> = tripRepository.lastTrips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tripsThisWeek: StateFlow<List<TripEntity>> = tripRepository.tripsThisWeek
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            CsvExporter.share(context, lastTrips.value)
        }
    }
}

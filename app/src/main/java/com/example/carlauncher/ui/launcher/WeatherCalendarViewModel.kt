package com.example.carlauncher.ui.launcher

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.calendar.CalendarEvent
import com.example.carlauncher.data.calendar.CalendarRepository
import com.example.carlauncher.data.weather.WeatherData
import com.example.carlauncher.data.weather.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeatherCalendarViewModel @Inject constructor(
    private val weatherRepo: WeatherRepository,
    private val calendarRepo: CalendarRepository,
) : ViewModel() {

    private val _weather = MutableStateFlow<WeatherData?>(null)
    val weather: StateFlow<WeatherData?> = _weather

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events

    init {
        // Run entirely on IO — MutableStateFlow is thread-safe, no Main dispatch needed
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val result = runCatching { weatherRepo.fetch() }.getOrNull()
                Log.d("WeatherCalVM", "weather updated: ${result?.tempC}°C")
                _weather.value = result
                delay(30 * 60 * 1_000L)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val result = calendarRepo.todayEvents()
                Log.d("WeatherCalVM", "events updated: ${result.size} on ${Thread.currentThread().name}")
                _events.value = result
                delay(5 * 60 * 1_000L)
            }
        }
    }
}

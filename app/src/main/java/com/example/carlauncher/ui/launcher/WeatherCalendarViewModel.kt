package com.example.carlauncher.ui.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.calendar.CalendarEvent
import com.example.carlauncher.data.calendar.CalendarRepository
import com.example.carlauncher.data.weather.WeatherData
import com.example.carlauncher.data.weather.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
        viewModelScope.launch {
            while (true) {
                _weather.value = runCatching { weatherRepo.fetch() }.getOrNull()
                delay(30 * 60 * 1_000L)
            }
        }
        viewModelScope.launch {
            while (true) {
                _events.value = calendarRepo.todayEvents()
                delay(5 * 60 * 1_000L)
            }
        }
    }
}

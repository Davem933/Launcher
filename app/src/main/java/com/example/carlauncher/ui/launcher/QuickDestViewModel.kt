package com.example.carlauncher.ui.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.data.dock.dockDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickDestViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private val KEY_HOME = stringPreferencesKey("quick_dest_home")
        private val KEY_WORK = stringPreferencesKey("quick_dest_work")
        private const val WAZE_PACKAGE = "com.waze"
    }

    val homeAddress: StateFlow<String> = context.dockDataStore.data
        .map { it[KEY_HOME] ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val workAddress: StateFlow<String> = context.dockDataStore.data
        .map { it[KEY_WORK] ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun saveHome(address: String) = viewModelScope.launch {
        context.dockDataStore.edit { it[KEY_HOME] = address.trim() }
    }

    fun saveWork(address: String) = viewModelScope.launch {
        context.dockDataStore.edit { it[KEY_WORK] = address.trim() }
    }

    fun navigateTo(address: String) {
        if (address.isBlank()) return
        try {
            val uri = Uri.parse("waze://?q=${Uri.encode(address)}&navigate=yes")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(WAZE_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Waze not installed — fall back to system geo intent
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

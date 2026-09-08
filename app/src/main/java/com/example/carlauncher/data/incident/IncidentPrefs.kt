package com.example.carlauncher.data.incident

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Single DataStore instance for Incident Recorder prefs (floating button position). */
val Context.incidentDataStore: DataStore<Preferences> by preferencesDataStore(name = "incident_prefs")

package com.example.carlauncher.data.dock

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// Top-level extension — guarantees a single DataStore instance per app
val Context.dockDataStore: DataStore<Preferences> by preferencesDataStore(name = "dock_prefs")

package com.example.carlauncher.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// Top-level extension — guarantees a single DataStore instance per app
val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_prefs")

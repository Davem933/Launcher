package com.example.carlauncher.ui.dock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.data.dock.dockDataStore
import com.example.carlauncher.data.model.DockPosition
import com.example.carlauncher.data.model.DockSlot
import com.example.carlauncher.data.settings.launcherDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

private val DOCK_KEY = stringPreferencesKey("dock_slots_v2")
private val POSITION_KEY = stringPreferencesKey("dock_position")

// 8 configurable slots (Navigate is fixed 9th, not stored)
// Split screen rule: packageName1 = nav (left), packageName2 = music (right)
private const val SLOT_COUNT = 8

private val DEFAULT_SLOTS = listOf(
    DockSlot.App("com.android.vending"),
    DockSlot.App("com.google.android.apps.youtube.music"),
    DockSlot.App("com.google.android.apps.maps"),
    DockSlot.App("cz.seznam.mapy"),
    DockSlot.Empty,
    DockSlot.Empty,
    DockSlot.SplitScreen(
        packageName1 = "com.waze",
        packageName2 = "com.google.android.apps.youtube.music",
        label        = "Waze + Hudba"
    ),
    DockSlot.SplitScreen(
        packageName1 = "com.tomtom.speedcams.android.map",   // verified via PackageCheck logcat
        packageName2 = "com.google.android.apps.youtube.music",
        label        = "TomTom + Hudba"
    )
)

@HiltViewModel
class DockViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _slots = MutableStateFlow<List<DockSlot>>(emptyList())
    val slots: StateFlow<List<DockSlot>> = _slots.asStateFlow()

    private val _showAppDrawer = MutableStateFlow(false)
    val showAppDrawer: StateFlow<Boolean> = _showAppDrawer.asStateFlow()

    fun openAppDrawer()  { _showAppDrawer.value = true }
    fun closeAppDrawer() { _showAppDrawer.value = false }

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    /** Opened from the AppDrawer tile — replaces the drawer rather than stacking on it. */
    fun openSettings() {
        _showAppDrawer.value = false
        _showSettings.value = true
    }
    fun closeSettings() { _showSettings.value = false }

    // Read once synchronously so a LEFT dock doesn't flash as BOTTOM (and re-lay-out the
    // whole screen) on every cold start; later changes arrive through the flow below.
    private val _position = MutableStateFlow(
        runBlocking { DockPosition.fromName(context.launcherDataStore.data.first()[POSITION_KEY]) }
    )
    val position: StateFlow<DockPosition> = _position.asStateFlow()

    fun setPosition(position: DockPosition) {
        viewModelScope.launch {
            context.launcherDataStore.edit { it[POSITION_KEY] = position.name }
        }
    }

    init {
        viewModelScope.launch {
            context.launcherDataStore.data.collect { prefs ->
                _position.value = DockPosition.fromName(prefs[POSITION_KEY])
            }
        }
        if (BuildConfig.DEBUG) logNavPackages()
        viewModelScope.launch {
            context.dockDataStore.data.collect { prefs ->
                val raw = prefs[DOCK_KEY]
                val slots: List<DockSlot> = if (raw.isNullOrEmpty()) {
                    DEFAULT_SLOTS
                } else {
                    withContext(Dispatchers.IO) {
                        val decoded = raw.split(",").take(SLOT_COUNT).map { decode(it) }
                        // Older persisted state may have fewer than SLOT_COUNT entries
                        // (e.g. from before an extra slot was added) — pad it out.
                        decoded + List(SLOT_COUNT - decoded.size) { DockSlot.Empty }
                    }
                }
                _slots.value = slots
            }
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    private fun decode(encoded: String): DockSlot {
        return when {
            encoded.isEmpty() || encoded == "empty" -> DockSlot.Empty
            encoded == "nav"                        -> DockSlot.Navigate
            encoded.startsWith("split:")            -> decodeSplit(encoded)
            else                                    -> DockSlot.App(encoded)
        }
    }

    private fun decodeSplit(encoded: String): DockSlot {
        // Format: split:pkg1:pkg2:label (label may contain "|" replacing ",")
        val parts = encoded.removePrefix("split:").split(":")
        if (parts.size < 2) return DockSlot.Empty
        val pkg1  = parts[0]
        val pkg2  = parts[1]
        val label = parts.getOrElse(2) { "${shortName(pkg1)} + ${shortName(pkg2)}" }
            .replace("|", ",")
        // Verify both packages exist; if not installed show anyway (grey icon handles it)
        return DockSlot.SplitScreen(pkg1, pkg2, label)
    }

    private fun shortName(pkg: String): String = try {
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg.substringAfterLast(".")
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    fun updateSlot(index: Int, slot: DockSlot) {
        viewModelScope.launch {
            val current = _slots.value.toMutableList()
            while (current.size < SLOT_COUNT) current.add(DockSlot.Empty)
            current[index] = slot
            val encoded = current.joinToString(",") { it.encode() }
            context.dockDataStore.edit { it[DOCK_KEY] = encoded }
        }
    }

    // ── Launch actions ────────────────────────────────────────────────────────

    fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        context.startActivity(intent)
    }

fun launchNavigation() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(
            Intent.createChooser(intent, "Navigovat pomocí")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ── Debug helpers ─────────────────────────────────────────────────────────

    private fun logNavPackages() {
        val all = context.packageManager.getInstalledPackages(0).map { it.packageName }
        Log.d("PackageCheck", "Waze:   ${all.filter { it.contains("waze",   ignoreCase = true) }}")
        Log.d("PackageCheck", "TomTom: ${all.filter { it.contains("tomtom", ignoreCase = true) }}")
    }
}

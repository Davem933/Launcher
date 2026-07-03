# Trip Computer & Jízdní deník — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Přidat Trip Computer a Jízdní deník jako dva widgety do horní části WidgetScreen (page 2), s automatickou detekcí jízdy přes GPS rychlost a persistencí v Room databázi.

**Architecture:** `TripDetector` singleton subscribeuje na `LocationRepository.vehicleLocation` flow a implementuje stavový automat IDLE↔ACTIVE. Data jízd se ukládají do Room (`TripEntity`), exponují přes `TripRepository`. `TripViewModel` zásobuje oba Compose widgety přes StateFlow. `WidgetScreen` dostane fixní horní `Row` s oběma widgety.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room 2.6.1 (kapt), Coroutines, Nominatim reverse geocoding (již v projektu), Android FileProvider + Share sheet.

## Global Constraints

- minSdk 31, compileSdk/targetSdk 36, Kotlin 2.0.21, AGP 8.5.2
- Hilt DI všude — žádné manuální singleton instance
- Room kapt (ne KSP) — projekt již používá `kotlin("kapt")`
- CarColors tokeny pro barvy (`CarColors.Surface`, `CarColors.Go`, `CarColors.Text` atd.)
- Pracovat na větvi `main` v adresáři `C:\Users\David\carLauncher\CarLauncher`
- Soubory pod 500 řádků
- Po každém tasku: `./gradlew assembleDebug` musí proběhnout bez chyb
- Commit přímo do main po každém tasku

---

## Task 1: Room závislosti + TripEntity + TripDao + TripDatabase

**Files:**
- Modify: `gradle/libs.versions.toml` — přidat Room verzi
- Modify: `app/build.gradle.kts` — přidat Room dependencies
- Create: `app/src/main/java/com/example/carlauncher/data/trip/TripEntity.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/trip/TripDao.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/trip/TripDatabase.kt`

**Interfaces:**
- Produces:
  - `TripEntity(id, startTime, endTime, distanceKm, avgSpeedKmh, maxSpeedKmh, startAddress, endAddress)`
  - `TripDao.getLastTrips(limit): Flow<List<TripEntity>>`
  - `TripDao.getTripsInRange(from, to): Flow<List<TripEntity>>`
  - `TripDao.insert(trip: TripEntity)`
  - `TripDatabase` — Room database singleton

- [ ] **Step 1: Přidat Room do libs.versions.toml**

V souboru `gradle/libs.versions.toml` přidat na konec sekce `[versions]`:
```toml
room = "2.6.1"
```
A do sekce `[libraries]`:
```toml
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx     = { group = "androidx.room", name = "room-ktx",     version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

- [ ] **Step 2: Přidat Room do app/build.gradle.kts**

Do bloku `dependencies { }` přidat (za DataStore řádek):
```kotlin
// Room — trip data persistence
implementation(libs.room.runtime)
implementation(libs.room.ktx)
kapt(libs.room.compiler)
```

- [ ] **Step 3: Vytvořit TripEntity.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/data/trip/TripEntity.kt
package com.example.carlauncher.data.trip

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,       // epoch ms
    val endTime: Long,         // epoch ms
    val distanceKm: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val startAddress: String,
    val endAddress: String
)
```

- [ ] **Step 4: Vytvořit TripDao.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/data/trip/TripDao.kt
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
```

- [ ] **Step 5: Vytvořit TripDatabase.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/data/trip/TripDatabase.kt
package com.example.carlauncher.data.trip

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TripEntity::class], version = 1, exportSchema = false)
abstract class TripDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
}
```

- [ ] **Step 6: Ověřit build**

```bash
./gradlew assembleDebug
```
Očekávaný výsledek: BUILD SUCCESSFUL (Room kapt vygeneruje implementace)

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/com/example/carlauncher/data/trip/TripEntity.kt \
  app/src/main/java/com/example/carlauncher/data/trip/TripDao.kt \
  app/src/main/java/com/example/carlauncher/data/trip/TripDatabase.kt
git commit -m "feat: Room DB schema — TripEntity, TripDao, TripDatabase"
```

---

## Task 2: TripModule (Hilt) + TripRepository

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/di/TripModule.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/trip/TripRepository.kt`

**Interfaces:**
- Consumes: `TripDatabase`, `TripDao`, `TripEntity` (z Task 1)
- Produces:
  - `TripRepository.lastTrips: Flow<List<TripEntity>>` — posledních 30
  - `TripRepository.tripsThisWeek: Flow<List<TripEntity>>`
  - `TripRepository.save(trip: TripEntity)` — suspend fun

- [ ] **Step 1: Vytvořit TripModule.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/di/TripModule.kt
package com.example.carlauncher.di

import android.content.Context
import androidx.room.Room
import com.example.carlauncher.data.trip.TripDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TripModule {

    @Provides
    @Singleton
    fun providesTripDatabase(@ApplicationContext context: Context): TripDatabase =
        Room.databaseBuilder(context, TripDatabase::class.java, "trip_database")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun providesTripDao(db: TripDatabase) = db.tripDao()
}
```

- [ ] **Step 2: Vytvořit TripRepository.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/data/trip/TripRepository.kt
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
```

- [ ] **Step 3: Ověřit build**

```bash
./gradlew assembleDebug
```
Očekávaný výsledek: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/di/TripModule.kt \
  app/src/main/java/com/example/carlauncher/data/trip/TripRepository.kt
git commit -m "feat: TripModule Hilt provider + TripRepository"
```

---

## Task 3: LiveTripState + TripDetector

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/data/trip/LiveTripState.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/trip/TripDetector.kt`

**Interfaces:**
- Consumes:
  - `LocationRepository.vehicleLocation: StateFlow<VehicleDisplayLocation?>` (package `data.location`)
  - `VehicleDisplayLocation(lat, lng, speedKmh, bearingDeg, accuracyM, timestamp)`
  - `TripRepository.save(trip: TripEntity)` (z Task 2)
- Produces:
  - `LiveTripState` — sealed class
  - `TripDetector.state: StateFlow<LiveTripState>`
  - `TripDetector.start()` — spustí sběr location
  - `TripDetector.stop()` — zastaví sběr

- [ ] **Step 1: Vytvořit LiveTripState.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/data/trip/LiveTripState.kt
package com.example.carlauncher.data.trip

sealed class LiveTripState {
    data class Idle(val lastTrip: TripEntity? = null) : LiveTripState()
    data class Active(
        val distanceKm: Float,
        val durationSec: Long,
        val avgSpeedKmh: Float,
        val maxSpeedKmh: Float
    ) : LiveTripState()
}
```

- [ ] **Step 2: Vytvořit TripDetector.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/data/trip/TripDetector.kt
package com.example.carlauncher.data.trip

import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.VehicleDisplayLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

private const val SPEED_START_KMH = 5f
private const val START_SAMPLES = 10     // 10s @ 1Hz → jízda začala
private const val STOP_SECONDS  = 30L    // 30s @ 0 km/h → jízda skončila

@Singleton
class TripDetector @Inject constructor(
    private val locationRepository: LocationRepository,
    private val tripRepository: TripRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectJob: Job? = null

    private val _state = MutableStateFlow<LiveTripState>(LiveTripState.Idle())
    val state: StateFlow<LiveTripState> = _state.asStateFlow()

    // Ring buffer posledních 10 rychlostních vzorků
    private val speedBuffer = ArrayDeque<Float>(START_SAMPLES)

    // Stav aktivní jízdy
    private var tripStartTime = 0L
    private var startLat = 0.0
    private var startLng = 0.0
    private var prevLat = 0.0
    private var prevLng = 0.0
    private var totalDistanceKm = 0f
    private var maxSpeedKmh = 0f
    private var stopCounter = 0L  // počet sekund s 0 km/h

    fun start() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            locationRepository.vehicleLocation.collect { loc ->
                loc ?: return@collect
                onLocation(loc)
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
    }

    private suspend fun onLocation(loc: VehicleDisplayLocation) {
        val speed = loc.speedKmh
        when (_state.value) {
            is LiveTripState.Idle -> handleIdle(speed, loc)
            is LiveTripState.Active -> handleActive(speed, loc)
        }
    }

    private fun handleIdle(speed: Float, loc: VehicleDisplayLocation) {
        if (speedBuffer.size >= START_SAMPLES) speedBuffer.removeFirst()
        speedBuffer.addLast(speed)
        if (speedBuffer.size == START_SAMPLES && speedBuffer.all { it > SPEED_START_KMH }) {
            // Jízda začala
            tripStartTime = System.currentTimeMillis()
            startLat = loc.lat; startLng = loc.lng
            prevLat = loc.lat;  prevLng = loc.lng
            totalDistanceKm = 0f; maxSpeedKmh = speed; stopCounter = 0
            speedBuffer.clear()
            _state.value = LiveTripState.Active(0f, 0L, speed, speed)
        }
    }

    private suspend fun handleActive(speed: Float, loc: VehicleDisplayLocation) {
        // Přičíst vzdálenost
        totalDistanceKm += haversineKm(prevLat, prevLng, loc.lat, loc.lng)
        prevLat = loc.lat; prevLng = loc.lng

        if (speed > maxSpeedKmh) maxSpeedKmh = speed

        val durationSec = (System.currentTimeMillis() - tripStartTime) / 1000L
        val avgSpeed = if (durationSec > 0) (totalDistanceKm / durationSec * 3600f) else 0f

        if (speed < 1f) {
            stopCounter++
            if (stopCounter >= STOP_SECONDS) {
                finishTrip(loc.lat, loc.lng, durationSec)
                return
            }
        } else {
            stopCounter = 0
        }

        _state.value = LiveTripState.Active(
            distanceKm  = totalDistanceKm,
            durationSec = durationSec,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeedKmh
        )
    }

    private suspend fun finishTrip(endLat: Double, endLng: Double, durationSec: Long) {
        val endTime = System.currentTimeMillis()
        val avgSpeed = if (durationSec > 0) (totalDistanceKm / durationSec * 3600f) else 0f

        // Geocoding na pozadí (best-effort, prázdný string při selhání)
        val startAddr = reverseGeocode(startLat, startLng)
        val endAddr   = reverseGeocode(endLat, endLng)

        val trip = TripEntity(
            startTime    = tripStartTime,
            endTime      = endTime,
            distanceKm   = totalDistanceKm,
            avgSpeedKmh  = avgSpeed,
            maxSpeedKmh  = maxSpeedKmh,
            startAddress = startAddr,
            endAddress   = endAddr
        )
        tripRepository.save(trip)
        _state.value = LiveTripState.Idle(lastTrip = trip)
        speedBuffer.clear()
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng"
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "CarLauncher/1.0")
            conn.connectTimeout = 3000; conn.readTimeout = 3000
            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            // Parsování: hledáme "road":"...", fallback "display_name"
            val road = Regex("\"road\":\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            val city = Regex("\"city\":\"([^\"]+)\"|\"town\":\"([^\"]+)\"|\"village\":\"([^\"]+)\"")
                .find(json)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
            listOfNotNull(road, city).joinToString(", ").ifEmpty { "Neznámé místo" }
        } catch (_: Exception) {
            ""
        }
    }

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return (R * 2 * asin(sqrt(a))).toFloat()
    }
}
```

- [ ] **Step 3: Ověřit build**

```bash
./gradlew assembleDebug
```
Očekávaný výsledek: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/data/trip/LiveTripState.kt \
  app/src/main/java/com/example/carlauncher/data/trip/TripDetector.kt
git commit -m "feat: TripDetector — GPS speed-based trip start/stop detection"
```

---

## Task 4: LauncherViewModel — start/stop TripDetector

**Files:**
- Modify: `app/src/main/java/com/example/carlauncher/ui/launcher/LauncherViewModel.kt`

**Interfaces:**
- Consumes: `TripDetector.start()`, `TripDetector.stop()` (z Task 3)

- [ ] **Step 1: Upravit LauncherViewModel.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/ui/launcher/LauncherViewModel.kt
package com.example.carlauncher.ui.launcher

import androidx.lifecycle.ViewModel
import com.example.carlauncher.data.location.LocationRepository
import com.example.carlauncher.data.model.VehicleDisplayLocation
import com.example.carlauncher.data.trip.TripDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val tripDetector: TripDetector
) : ViewModel() {

    val vehicleLocation: StateFlow<VehicleDisplayLocation?> = repository.vehicleLocation

    init {
        repository.startTracking()
        tripDetector.start()
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopTracking()
        tripDetector.stop()
    }
}
```

- [ ] **Step 2: Ověřit build**

```bash
./gradlew assembleDebug
```
Očekávaný výsledek: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/launcher/LauncherViewModel.kt
git commit -m "feat: LauncherViewModel starts/stops TripDetector with location tracking"
```

---

## Task 5: CsvExporter

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/data/trip/CsvExporter.kt`
- Modify: `app/src/main/AndroidManifest.xml` — přidat FileProvider
- Create: `app/src/main/res/xml/file_paths.xml`

**Interfaces:**
- Consumes: `List<TripEntity>` (z Task 1)
- Produces: `CsvExporter.share(context, trips)` — generuje CSV, spustí Share sheet

- [ ] **Step 1: Přidat FileProvider do AndroidManifest.xml**

Do `<application>` bloku přidat (za ostatní `<provider>` nebo před `</application>`):
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

- [ ] **Step 2: Vytvořit res/xml/file_paths.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="trips" path="." />
</paths>
```

- [ ] **Step 3: Vytvořit CsvExporter.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/data/trip/CsvExporter.kt
package com.example.carlauncher.data.trip

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private val dateFmt  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFmt  = SimpleDateFormat("HH:mm",      Locale.getDefault())

    fun share(context: Context, trips: List<TripEntity>) {
        val file = File(context.filesDir, "trips_export.csv")
        file.bufferedWriter().use { w ->
            w.write("datum,cas_start,cas_konec,trvani,km,prumerna_rychlost,max_rychlost,odkud,kam\n")
            trips.forEach { t ->
                val date     = dateFmt.format(Date(t.startTime))
                val start    = timeFmt.format(Date(t.startTime))
                val end      = timeFmt.format(Date(t.endTime))
                val dur      = formatDuration((t.endTime - t.startTime) / 1000)
                val km       = "%.1f".format(t.distanceKm)
                val avg      = t.avgSpeedKmh.toInt()
                val max      = t.maxSpeedKmh.toInt()
                val from     = t.startAddress.replace(",", ";")
                val to       = t.endAddress.replace(",", ";")
                w.write("$date,$start,$end,$dur,$km,$avg,$max,$from,$to\n")
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportovat jízdy").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun formatDuration(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return "%02d:%02d".format(h, m)
    }
}
```

- [ ] **Step 4: Ověřit build**

```bash
./gradlew assembleDebug
```
Očekávaný výsledek: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml \
  app/src/main/res/xml/file_paths.xml \
  app/src/main/java/com/example/carlauncher/data/trip/CsvExporter.kt
git commit -m "feat: CsvExporter — generuje CSV jízd a sdílí přes Android Share sheet"
```

---

## Task 6: TripViewModel

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/ui/widgets/TripViewModel.kt`

**Interfaces:**
- Consumes:
  - `TripDetector.state: StateFlow<LiveTripState>` (z Task 3)
  - `TripRepository.lastTrips: Flow<List<TripEntity>>` (z Task 2)
  - `TripRepository.tripsThisWeek: Flow<List<TripEntity>>` (z Task 2)
  - `CsvExporter.share(context, trips)` (z Task 5)
- Produces:
  - `TripViewModel.liveTrip: StateFlow<LiveTripState>`
  - `TripViewModel.lastTrips: StateFlow<List<TripEntity>>`
  - `TripViewModel.tripsThisWeek: StateFlow<List<TripEntity>>`
  - `TripViewModel.exportCsv(context: Context)`

- [ ] **Step 1: Vytvořit TripViewModel.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/ui/widgets/TripViewModel.kt
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
```

- [ ] **Step 2: Ověřit build**

```bash
./gradlew assembleDebug
```
Očekávaný výsledek: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/widgets/TripViewModel.kt
git commit -m "feat: TripViewModel — exponuje liveTrip, lastTrips, tripsThisWeek, exportCsv"
```

---

## Task 7: TripComputerWidget

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/ui/widgets/TripComputerWidget.kt`

**Interfaces:**
- Consumes:
  - `TripViewModel.liveTrip: StateFlow<LiveTripState>` (z Task 6)
  - `LiveTripState.Idle(lastTrip)`, `LiveTripState.Active(distanceKm, durationSec, avgSpeedKmh, maxSpeedKmh)`
  - `CarColors.Surface`, `CarColors.Go`, `CarColors.Text`, `CarColors.Text2`

- [ ] **Step 1: Vytvořit TripComputerWidget.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/ui/widgets/TripComputerWidget.kt
package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.trip.LiveTripState
import com.example.carlauncher.data.trip.TripEntity
import com.example.carlauncher.ui.theme.CarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun TripComputerWidget(
    modifier: Modifier = Modifier,
    viewModel: TripViewModel = hiltViewModel()
) {
    val liveTrip by viewModel.liveTrip.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Surface)
            .border(1.dp, Color(0xFF2A2C35), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        when (val state = liveTrip) {
            is LiveTripState.Active -> ActiveTrip(state)
            is LiveTripState.Idle   -> IdleTrip(state.lastTrip)
        }
    }
}

@Composable
private fun ActiveTrip(state: LiveTripState.Active) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Zelená tečka
        androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = CarColors.Go)
        }
        Text(
            text = "  JÍZDA PROBÍHÁ",
            color = CarColors.Go,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatDuration(state.durationSec),
            color = CarColors.Go,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatItem(value = "%.1f km".format(state.distanceKm), label = "vzdálenost")
        StatItem(value = "${state.avgSpeedKmh.toInt()} km/h", label = "průměr")
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        StatItem(value = "${state.maxSpeedKmh.toInt()} km/h", label = "maximum")
    }
}

@Composable
private fun IdleTrip(lastTrip: TripEntity?) {
    Text(
        text = if (lastTrip != null) "Poslední jízda" else "Žádná jízda",
        color = CarColors.Text2,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    )
    if (lastTrip == null) {
        Spacer(Modifier.height(8.dp))
        Text(text = "Start detekován automaticky", color = CarColors.Text2, fontSize = 10.sp)
        return
    }
    val dateLabel = relativeDate(lastTrip.startTime)
    Text(text = dateLabel, color = CarColors.Text2, fontSize = 10.sp)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatItem(value = "%.1f km".format(lastTrip.distanceKm), label = "vzdálenost")
        StatItem(value = "${lastTrip.avgSpeedKmh.toInt()} km/h", label = "průměr")
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatItem(value = formatDuration((lastTrip.endTime - lastTrip.startTime) / 1000), label = "trvání")
        StatItem(value = "${lastTrip.maxSpeedKmh.toInt()} km/h", label = "maximum")
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = CarColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = CarColors.Text2, fontSize = 9.sp)
    }
}

private fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun relativeDate(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - epochMs
    return when {
        diffMs < TimeUnit.HOURS.toMillis(1)  -> "před méně než hodinou"
        diffMs < TimeUnit.DAYS.toMillis(1)   -> "dnes"
        diffMs < TimeUnit.DAYS.toMillis(2)   -> "včera"
        else -> SimpleDateFormat("d. M. yyyy", Locale("cs")).format(Date(epochMs))
    }
}
```

- [ ] **Step 2: Ověřit build**

```bash
./gradlew assembleDebug
```
Očekávaný výsledek: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/widgets/TripComputerWidget.kt
git commit -m "feat: TripComputerWidget — živé hodnoty jízdy + souhrn poslední jízdy"
```

---

## Task 8: TripHistoryScreen + JizdniDenikWidget

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/ui/widgets/TripHistoryScreen.kt`
- Create: `app/src/main/java/com/example/carlauncher/ui/widgets/JizdniDenikWidget.kt`

**Interfaces:**
- Consumes:
  - `TripViewModel.lastTrips`, `TripViewModel.tripsThisWeek`, `TripViewModel.exportCsv(context)` (z Task 6)
  - `TripEntity(startTime, endTime, distanceKm, avgSpeedKmh, startAddress, endAddress)`
  - `CarColors.*`

- [ ] **Step 1: Vytvořit TripHistoryScreen.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/ui/widgets/TripHistoryScreen.kt
package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.trip.TripEntity
import com.example.carlauncher.ui.theme.CarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripHistoryScreen(
    onBack: () -> Unit,
    viewModel: TripViewModel = hiltViewModel()
) {
    val trips by viewModel.lastTrips.collectAsStateWithLifecycle()
    val dateFmt = SimpleDateFormat("d. M. yyyy HH:mm", Locale("cs"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Bg)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Zpět", tint = CarColors.Text)
            }
            Text(
                text = "Historie jízd",
                color = CarColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        if (trips.isEmpty()) {
            Text(text = "Žádné jízdy zatím nezaznamenány.", color = CarColors.Text2, fontSize = 14.sp)
        } else {
            LazyColumn {
                items(trips) { trip ->
                    TripHistoryRow(trip = trip, dateFmt = dateFmt)
                    Divider(color = CarColors.Surface, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun TripHistoryRow(trip: TripEntity, dateFmt: SimpleDateFormat) {
    val dur = (trip.endTime - trip.startTime) / 1000
    val h = dur / 3600; val m = (dur % 3600) / 60
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = dateFmt.format(Date(trip.startTime)),
            color = CarColors.Text2, fontSize = 10.sp
        )
        Text(
            text = "${trip.startAddress.ifEmpty { "?" }} → ${trip.endAddress.ifEmpty { "?" }}",
            color = CarColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium
        )
        Text(
            text = "%.1f km  •  %02d:%02d  •  ø ${trip.avgSpeedKmh.toInt()} km/h  •  max ${trip.maxSpeedKmh.toInt()} km/h".format(trip.distanceKm, h, m),
            color = CarColors.Text2, fontSize = 11.sp
        )
    }
}
```

- [ ] **Step 2: Vytvořit JizdniDenikWidget.kt**

```kotlin
// app/src/main/java/com/example/carlauncher/ui/widgets/JizdniDenikWidget.kt
package com.example.carlauncher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.trip.TripEntity
import com.example.carlauncher.ui.theme.CarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JizdniDenikWidget(
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    viewModel: TripViewModel = hiltViewModel()
) {
    val context      = LocalContext.current
    val lastTrips    by viewModel.lastTrips.collectAsStateWithLifecycle()
    val weekTrips    by viewModel.tripsThisWeek.collectAsStateWithLifecycle()

    val weekKm    = weekTrips.sumOf { it.distanceKm.toDouble() }.toFloat()
    val weekCount = weekTrips.size
    val recentTwo = lastTrips.take(2)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Surface)
            .border(1.dp, Color(0xFF2A2C35), RoundedCornerShape(16.dp))
            .clickable { onOpenHistory() }
            .padding(12.dp)
    ) {
        // Týdenní statistiky
        Text(text = "Tento týden", color = CarColors.Text2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "%.0f km  ·  %d jízd".format(weekKm, weekCount),
            color = CarColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF2A2C35))

        // Poslední 2 jízdy
        if (recentTwo.isEmpty()) {
            Text(text = "Zatím žádné jízdy", color = CarColors.Text2, fontSize = 11.sp)
        } else {
            recentTwo.forEach { trip -> RecentTripRow(trip) }
        }

        Spacer(Modifier.height(8.dp))

        // Export tlačítko
        Button(
            onClick = { viewModel.exportCsv(context) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2C35)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "Exportovat CSV", color = CarColors.Text, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RecentTripRow(trip: TripEntity) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateLabel = when {
        isToday(trip.startTime)     -> "Dnes"
        isYesterday(trip.startTime) -> "Včera"
        else -> SimpleDateFormat("d. M.", Locale("cs")).format(Date(trip.startTime))
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "$dateLabel ${timeFmt.format(Date(trip.startTime))}",
                color = CarColors.Text2, fontSize = 10.sp
            )
            Text(
                text = "%.1f km".format(trip.distanceKm),
                color = CarColors.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
            )
        }
        val dest = trip.endAddress.ifEmpty { trip.startAddress.ifEmpty { "?" } }
        Text(
            text = dest.take(40),
            color = CarColors.Text2, fontSize = 10.sp
        )
    }
}

private fun isToday(epochMs: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    val cal2 = java.util.Calendar.getInstance()
    return cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR) &&
           cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR)
}

private fun isYesterday(epochMs: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
    val cal2 = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    return cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR) &&
           cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR)
}
```

- [ ] **Step 3: Ověřit build**

```bash
./gradlew assembleDebug
```
Očekávaný výsledek: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/widgets/TripHistoryScreen.kt \
  app/src/main/java/com/example/carlauncher/ui/widgets/JizdniDenikWidget.kt
git commit -m "feat: TripHistoryScreen + JizdniDenikWidget — týdenní stats, export CSV"
```

---

## Task 9: Integrace do WidgetScreen

**Files:**
- Modify: `app/src/main/java/com/example/carlauncher/ui/widgets/WidgetScreen.kt`

**Interfaces:**
- Consumes:
  - `TripComputerWidget(modifier)` (z Task 7)
  - `JizdniDenikWidget(modifier, onOpenHistory)` (z Task 8)
  - `TripHistoryScreen(onBack)` (z Task 8)

- [ ] **Step 1: Přečíst aktuální WidgetScreen.kt a najít místo pro vložení**

```bash
head -100 app/src/main/java/com/example/carlauncher/ui/widgets/WidgetScreen.kt
```

- [ ] **Step 2: Přidat state pro historii a fixní header do WidgetScreen**

Najít v `WidgetScreen.kt` blok:
```kotlin
var editSlot by remember { mutableIntStateOf(-1) }
var showTemplatePicker by remember { mutableStateOf(false) }
```

Přidat za to:
```kotlin
var showTripHistory by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Vložit TripHistory dialog / fullscreen**

Najít v `WidgetScreen` před `DisposableEffect` nebo na konci `Box`:
```kotlin
if (showTripHistory) {
    TripHistoryScreen(onBack = { showTripHistory = false })
    return@Box  // nebo return@WidgetScreen
}
```

- [ ] **Step 4: Přidat fixní Row s widgety do horní části WidgetScreen**

Najít v `WidgetScreen` hlavní `Column` nebo `Box` a přidat nahoře (před AppWidget grid):
```kotlin
// Trip widgety — fixní sekce nahoře
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
) {
    TripComputerWidget(modifier = Modifier.weight(1f))
    JizdniDenikWidget(
        modifier = Modifier.weight(1f),
        onOpenHistory = { showTripHistory = true }
    )
}
```

- [ ] **Step 5: Přidat importy**

Na začátek souboru přidat:
```kotlin
import com.example.carlauncher.ui.widgets.TripComputerWidget
import com.example.carlauncher.ui.widgets.JizdniDenikWidget
import com.example.carlauncher.ui.widgets.TripHistoryScreen
```

- [ ] **Step 6: Ověřit build**

```bash
./gradlew assembleDebug
```
Očekávaný výsledek: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/widgets/WidgetScreen.kt
git commit -m "feat: Trip Computer + Jízdní deník widgety integrované do WidgetScreen"
```

---

## Finální ověření

- [ ] `./gradlew assembleDebug` — BUILD SUCCESSFUL
- [ ] `./gradlew lint` — žádné nové chyby
- [ ] Nainstalovat na zařízení: `./gradlew installDebug`
- [ ] Přejít na Page 2 — oba widgety viditelné nahoře
- [ ] Projet se autem nebo simulovat pohyb v emulátoru — TripComputer přejde do ACTIVE stavu
- [ ] Po zastavení — TripComputer zobrazí souhrn, JizdniDeník aktualizuje seznam
- [ ] Tlačítko "Exportovat CSV" — Share sheet se otevře
- [ ] Tap na JizdniDenikWidget — otevře TripHistoryScreen, Zpět funguje

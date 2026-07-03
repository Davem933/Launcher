# Trip Computer & Jízdní deník — Design Spec

**Datum:** 2026-07-03  
**Stav:** Schváleno uživatelem  

---

## Přehled

Dva samostatné CarLauncher widgety přidané jako fixní sekce v horní části WidgetScreen (page 2):

1. **TripComputerWidget** — živé hodnoty aktuální jízdy, po zastavení souhrn poslední jízdy
2. **JizdniDenikWidget** — týdenní statistiky + poslední 2 jízdy + export CSV

---

## Data vrstva

### Room databáze

Nová závislost: `androidx.room` (Room KTX + kapt procesor).

**TripEntity** (`data/trip/TripEntity.kt`):
```kotlin
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,       // epoch ms
    val endTime: Long,         // epoch ms
    val distanceKm: Float,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val startAddress: String,  // Nominatim reverse geocoding
    val endAddress: String
)
```

**TripDao** (`data/trip/TripDao.kt`):
- `getLastTrips(limit: Int): Flow<List<TripEntity>>`
- `getTripsInRange(from: Long, to: Long): Flow<List<TripEntity>>`
- `insert(trip: TripEntity)`

**TripDatabase** (`data/trip/TripDatabase.kt`): Room database singleton, Hilt provides.

**TripRepository** (`data/trip/TripRepository.kt`): Hilt singleton, obaluje DAO. Exponuje:
- `lastTrips: Flow<List<TripEntity>>` (posledních 30)
- `tripsThisWeek: Flow<List<TripEntity>>`
- `suspend fun saveTripAsync(trip: TripEntity)`

---

## Detekce jízdy (TripDetector)

**`TripDetector`** (`data/trip/TripDetector.kt`) — Hilt singleton, injektuje `LocationRepository` a `TripRepository`.

### Stavový automat

```
IDLE → [speed >5 km/h po 10s] → ACTIVE → [speed ==0 po 30s] → IDLE
```

- **IDLE:** poslouchá `vehicleLocation` flow, pamatuje ring-buffer posledních 10 vzorků rychlosti
- **ACTIVE:**
  - Zaznamenává `startTime`, `startLat/Lng`
  - Každý vzorek: přičítá haversine vzdálenost od předchozího bodu
  - Trackuje `maxSpeedKmh`, průběžně počítá `avgSpeedKmh = totalDistance / elapsedTime`
  - Emituje `LiveTripState.Active(...)` každou sekundu
- **Ukončení jízdy:**
  - Nominatim geocoding `startLat/Lng` → `startAddress`
  - Nominatim geocoding `endLat/Lng` → `endAddress`
  - `TripRepository.saveTripAsync(TripEntity(...))`
  - Přechod do IDLE, emituje `LiveTripState.Idle(lastTrip)`

### LiveTripState

```kotlin
sealed class LiveTripState {
    data class Idle(val lastTrip: TripEntity?) : LiveTripState()
    data class Active(
        val distanceKm: Float,
        val durationSec: Long,
        val avgSpeedKmh: Float,
        val maxSpeedKmh: Float
    ) : LiveTripState()
}
```

`TripDetector` startuje v `LauncherViewModel.init`, stopuje v `onCleared` — stejný vzor jako `LocationRepository`.

---

## ViewModel

**`TripViewModel`** (`ui/widgets/TripViewModel.kt`) — `@HiltViewModel`:
- `liveTrip: StateFlow<LiveTripState>` — z TripDetector
- `lastTrips: StateFlow<List<TripEntity>>` — posledních 30 z Room
- `tripsThisWeek: StateFlow<List<TripEntity>>`
- `fun exportCsv(context: Context)` — generuje CSV, spustí Share sheet

---

## Widgety — UI

### TripComputerWidget

**Aktivní jízda** (zelená):
```
┌─────────────────────────────┐
│ 🟢 JÍZDA PROBÍHÁ  00:23:14 │
│                             │
│  14.2 km    68 km/h průměr  │
│             94 km/h max     │
└─────────────────────────────┘
```

**IDLE — poslední jízda** (šedá):
```
┌─────────────────────────────┐
│  Poslední jízda  včera      │
│                             │
│  23.7 km    54 km/h průměr  │
│  00:26:10   71 km/h max     │
└─────────────────────────────┘
```

Styl: `CarColors.Surface`, rounded 16dp, stejné jako ostatní widgety na WidgetScreen.

### JizdniDenikWidget

```
┌─────────────────────────────┐
│  Tento týden                │
│  142 km · 6 jízd            │
│─────────────────────────────│
│  Dnes 08:14  Klenovecká→... │
│  23.7 km                    │
│  Včera 17:02  Centrum→...   │
│  11.2 km                    │
│             [Exportovat CSV]│
└─────────────────────────────┘
```

Tap na widget (mimo tlačítko) → otevře `TripHistoryScreen`.

### TripHistoryScreen

Fullscreen seznam posledních 30 jízd:
- Každá řádka: datum/čas + odkud→kam + km + trvání
- Tlačítko Zpět (nebo swipe)

---

## CSV Export

`CsvExporter` (`data/trip/CsvExporter.kt`):
1. `TripRepository.getLastTrips(30)` → List
2. Zapíše do `context.filesDir/trips_export.csv`
3. `FileProvider` URI + `Intent.ACTION_SEND` → Android Share sheet

**Formát:**
```csv
datum,cas_start,cas_konec,trvani,km,prumerna_rychlost,max_rychlost,odkud,kam
2026-07-02,08:14,08:37,00:23,14.2,68,94,Klenovecká,Centrum Praha
```

---

## Integrace do WidgetScreen

`WidgetScreen.kt` — přidat fixní `Row` nahoře před stávající AppWidget grid:

```kotlin
Column {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = SpaceEvenly) {
        TripComputerWidget(modifier = Modifier.weight(1f))
        Spacer(8.dp)
        JizdniDenikWidget(modifier = Modifier.weight(1f))
    }
    // stávající AppWidget grid beze změny
    AppWidgetGrid(...)
}
```

---

## Soubory k vytvoření

| Soubor | Účel |
|--------|------|
| `data/trip/TripEntity.kt` | Room entita |
| `data/trip/TripDao.kt` | Room DAO |
| `data/trip/TripDatabase.kt` | Room databáze |
| `data/trip/TripRepository.kt` | Data repository |
| `data/trip/TripDetector.kt` | Start/stop logika jízdy |
| `data/trip/CsvExporter.kt` | CSV generátor + Share |
| `ui/widgets/TripViewModel.kt` | ViewModel pro oba widgety |
| `ui/widgets/TripComputerWidget.kt` | Widget UI |
| `ui/widgets/JizdniDenikWidget.kt` | Widget UI |
| `ui/widgets/TripHistoryScreen.kt` | Fullscreen historie |

## Soubory k úpravě

| Soubor | Změna |
|--------|-------|
| `app/build.gradle.kts` | +Room dependencies |
| `di/LocationModule.kt` nebo nový `di/TripModule.kt` | Hilt providers pro Room |
| `ui/launcher/LauncherViewModel.kt` | Start/stop TripDetector |
| `ui/widgets/WidgetScreen.kt` | Přidat fixní sekci nahoře |

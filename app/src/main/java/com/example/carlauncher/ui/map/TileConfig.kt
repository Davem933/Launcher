package com.example.carlauncher.ui.map

import com.example.carlauncher.BuildConfig

object TileConfig {
    // DEV — online demo tiles, works immediately, no setup needed
    const val DEMO = "https://demotiles.maplibre.org/style.json"

    // Mapy.com — klíč je v local.properties (MAPYCZ_API_KEY=...), nikdy ne ve zdrojáku
    val MAPYCZ_API_KEY: String = BuildConfig.MAPYCZ_API_KEY
    val MAPYCZ_BASIC = "https://api.mapy.cz/v1/maptiles/basic/256/{z}/{x}/{y}?apikey=$MAPYCZ_API_KEY"
    val MAPYCZ_OUTDOOR = "https://api.mapy.cz/v1/maptiles/outdoor/256/{z}/{x}/{y}?apikey=$MAPYCZ_API_KEY"

    // PMTiles — zachovat pro budoucí offline použití
    const val PMTILES = "pmtiles:///storage/emulated/0/CarLauncher/czech.pmtiles"
}

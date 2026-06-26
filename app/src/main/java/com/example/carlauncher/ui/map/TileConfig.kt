package com.example.carlauncher.ui.map
import com.example.carlauncher.BuildConfig

enum class TileSource { MAPYCZ, DEMO, PMTILES }

object TileConfig {
    val ACTIVE = TileSource.PMTILES

    // DEV — online demo tiles, works immediately, no setup needed
    const val DEMO = "https://demotiles.maplibre.org/style.json"

    // Mapy.cz — záloha, kdyby PMTiles nefungovalo
    val MAPYCZ_API_KEY: String = BuildConfig.MAPYCZ_API_KEY
    val MAPYCZ_BASIC   = "https://api.mapy.cz/v1/maptiles/basic/256/{z}/{x}/{y}?apikey=$MAPYCZ_API_KEY"
    val MAPYCZ_OUTDOOR = "https://api.mapy.cz/v1/maptiles/outdoor/256/{z}/{x}/{y}?apikey=$MAPYCZ_API_KEY"
    val MAPYCZ_DARK    = "https://api.mapy.cz/v1/maptiles/dark/256/{z}/{x}/{y}?apikey=$MAPYCZ_API_KEY"

    // PMTiles — offline mapa na tabletu, nativní podpora od MapLibre 11.8.0
    const val PMTILES = "pmtiles:///storage/emulated/0/CarLauncher/czech.pmtiles"
}

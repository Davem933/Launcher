package com.example.carlauncher.ui.map

import com.mapbox.maps.Style

object TileConfig {
    // STANDARD is Mapbox's dynamic 3D style (3D buildings/landmarks, lighting presets) — the
    // classic TRAFFIC_NIGHT style used before this has no 3D building support. Traffic congestion
    // coloring is not available on STANDARD without a custom style combining both; dropped for now.
    const val MAP_STYLE_URI = Style.STANDARD

    // Style import ID Mapbox assigns when STANDARD is loaded directly (not nested in a custom
    // style) — "basemap", not "standard" (that id is only used when Standard is imported into a
    // custom style JSON under an explicit "standard" id).
    const val STANDARD_IMPORT_ID = "basemap"
}

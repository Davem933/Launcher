package com.example.carlauncher.ui.map

fun buildMapyczStyleJson(tileUrl: String, isDark: Boolean = true): String {
    // Background is visible only while tiles are loading (flash prevention).
    val bgColor = if (isDark) "#1a1c24" else "#f2f3f7"
    return buildString {
        append("""{"version":8,"sources":{"mapy-cz":{"type":"raster",""")
        append(""""tiles":["$tileUrl"],"tileSize":256,""")
        append(""""attribution":"© Seznam.cz a.s."}},"layers":[""")
        append("""{"id":"background","type":"background","paint":{"background-color":"$bgColor"}},""")
        append("""{"id":"mapy-cz-layer","type":"raster","source":"mapy-cz"}]}""")
    }
}

package com.example.carlauncher.ui.map

fun buildMapyczStyleJson(tileUrl: String, isDark: Boolean = true): String {
    val bgColor = if (isDark) "#1a1c24" else "#f2f3f7"
    return buildString {
        append("""{"version":8,"sources":{"mapy-cz":{"type":"raster",""")
        append(""""tiles":["$tileUrl"],"tileSize":256,""")
        append(""""attribution":"© Seznam.cz a.s."}},"layers":[""")
        append("""{"id":"background","type":"background","paint":{"background-color":"$bgColor"}},""")
        append("""{"id":"mapy-cz-layer","type":"raster","source":"mapy-cz"}]}""")
    }
}

// Minimal dark vector style for offline PMTiles (OpenMapTiles schema).
// Layer IDs match the vector_layers served by PmtilesHttpServer on port 8888.
fun buildPmtilesStyleJson(): String = buildString {
    append("""{"version":8,"sources":{"pmtiles":{"type":"vector",""")
    append(""""tiles":["http://127.0.0.1:8888/tile/{z}/{x}/{y}"],"minzoom":0,"maxzoom":14}},""")
    append(""""layers":[""")
    append("""{"id":"background","type":"background","paint":{"background-color":"#1a1c24"}},""")
    append("""{"id":"water","type":"fill","source":"pmtiles","source-layer":"water","paint":{"fill-color":"#1a3a5c"}},""")
    append("""{"id":"landuse","type":"fill","source":"pmtiles","source-layer":"landuse","paint":{"fill-color":"#1a2a1a","fill-opacity":0.5}},""")
    append("""{"id":"building","type":"fill","source":"pmtiles","source-layer":"building","paint":{"fill-color":"#252830","fill-opacity":0.8}},""")
    append("""{"id":"road-minor","type":"line","source":"pmtiles","source-layer":"transportation","filter":["in","class","minor","service","track"],"paint":{"line-color":"#3a3c4a","line-width":1}},""")
    append("""{"id":"road-major","type":"line","source":"pmtiles","source-layer":"transportation","filter":["in","class","primary","secondary","tertiary","trunk","motorway"],"paint":{"line-color":"#5a5c6a","line-width":2.5}},""")
    append("""{"id":"road-label","type":"symbol","source":"pmtiles","source-layer":"transportation_name","layout":{"text-field":["get","name"],"text-size":11,"symbol-placement":"line"},"paint":{"text-color":"#b8bac8","text-halo-color":"#1a1c24","text-halo-width":1}}""")
    append("""]}""")
}

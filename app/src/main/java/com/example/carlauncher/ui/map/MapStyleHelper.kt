package com.example.carlauncher.ui.map

fun buildMapyczStyleJson(tileUrl: String): String {
    return """
    {
      "version": 8,
      "sources": {
        "mapy-cz": {
          "type": "raster",
          "tiles": ["$tileUrl"],
          "tileSize": 256,
          "attribution": "© Seznam.cz a.s."
        }
      },
      "layers": [
        {
          "id": "background",
          "type": "background",
          "paint": { "background-color": "#1a1c24" }
        },
        {
          "id": "mapy-cz-layer",
          "type": "raster",
          "source": "mapy-cz"
        }
      ]
    }
    """.trimIndent()
}

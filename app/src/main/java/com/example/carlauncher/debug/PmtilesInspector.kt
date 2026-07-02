package com.example.carlauncher.debug

import android.content.Context
import android.util.Log
import com.example.carlauncher.data.map.PmtilesHttpServer
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

private const val TAG = "PMTilesDebug"

/**
 * One-shot debug inspector: opens czech.pmtiles, fetches a tile near CZ center
 * (z=14), decodes the MVT protobuf, and logs all layer names + sample feature
 * fields to Logcat so we know which POI layers/fields Planetiler emitted.
 *
 * Run via: adb logcat -s PMTilesDebug
 */
object PmtilesInspector {

    fun runOnce(context: Context) {
        Thread {
            try { inspect(context) }
            catch (e: Exception) { Log.e(TAG, "Inspector error: ${e.message}", e) }
        }.apply { name = "pmtiles-inspector"; isDaemon = true }.start()
    }

    // ── Main flow ─────────────────────────────────────────────────────────────

    private fun inspect(context: Context) {
        val file = File(context.filesDir, "czech.pmtiles")
        if (!file.exists()) {
            Log.w(TAG, "czech.pmtiles not found in filesDir — copy via SAF picker first")
            return
        }
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "File: ${file.name}  ${file.length() / 1_048_576} MB")

        val server = PmtilesHttpServer(file)
        val z = 14
        val (cx, cy) = latLonToTile(49.8, 15.5, z)
        Log.d(TAG, "Target tile: z=$z x=$cx y=$cy (CZ center lat=49.8 lon=15.5)")

        val (rawBytes, tileComp) = findNearbyTile(server, z, cx, cy)
        if (rawBytes == null) {
            Log.e(TAG, "No tile found in 5×5 grid around CZ center at z=$z")
            return
        }

        Log.d(TAG, "Tile raw: ${rawBytes.size}B  tileComp=$tileComp")
        val mvtBytes = if (tileComp == 2) decompress(rawBytes) else rawBytes
        Log.d(TAG, "MVT decompressed: ${mvtBytes.size}B")
        logLayers(parseTile(mvtBytes))
    }

    private fun findNearbyTile(
        server: PmtilesHttpServer, z: Int, cx: Int, cy: Int
    ): Pair<ByteArray?, Int> {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val (rb, tc) = server.getTileRaw(z, cx + dx, cy + dy)
                if (rb != null) {
                    if (dx != 0 || dy != 0)
                        Log.d(TAG, "Exact tile empty — using neighbour dx=$dx dy=$dy")
                    return rb to tc
                }
            }
        }
        return null to 2
    }

    private fun decompress(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).readBytes()

    private fun latLonToTile(lat: Double, lon: Double, z: Int): Pair<Int, Int> {
        val n = 1 shl z
        val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latR = Math.toRadians(lat)
        val y = ((1.0 - Math.log(Math.tan(latR) + 1.0 / Math.cos(latR)) / Math.PI) / 2.0 * n)
            .toInt().coerceIn(0, n - 1)
        return x to y
    }

    // ── Logcat output ─────────────────────────────────────────────────────────

    private fun logLayers(layers: List<LayerInfo>) {
        Log.d(TAG, "━━━ LAYERS (${layers.size}) ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        for (l in layers) {
            Log.d(TAG, "LAYER \"${l.name}\"  features=${l.featureCount}")
            Log.d(TAG, "  keys (${l.keys.size}): ${l.keys.take(25).joinToString(", ")}")
            l.samples.forEachIndexed { i, f -> Log.d(TAG, "  feature[$i]: $f") }
        }
        Log.d(TAG, "━━━ END PmtilesInspector ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        // Remind about POI candidates
        val poiCandidates = layers.filter {
            it.name in listOf("poi", "place", "amenity", "landuse", "transportation")
                || it.keys.any { k -> k in listOf("class", "subclass", "amenity", "shop", "tourism") }
        }.map { it.name }
        if (poiCandidates.isNotEmpty())
            Log.d(TAG, "POI candidate layers: $poiCandidates")
    }

    // ── MVT protobuf parser ───────────────────────────────────────────────────
    //
    // MVT wire format (Protobuf):
    //   Tile        { repeated Layer layers = 3; }
    //   Layer       { string name=1; repeated Feature features=2; repeated string keys=3;
    //                 repeated Value values=4; uint32 extent=5; uint32 version=15; }
    //   Feature     { uint64 id=1; repeated uint32 tags=2 [packed]; GeomType type=3;
    //                 repeated uint32 geometry=4 [packed]; }
    //   Value       { oneof: string=1, float=2, double=3, int64=4, uint64=5, sint64=6, bool=7 }

    private data class LayerInfo(
        val name: String,
        val featureCount: Int,
        val keys: List<String>,
        val samples: List<Map<String, String>>  // first 3 features' key→value
    )

    private fun parseTile(b: ByteArray): List<LayerInfo> {
        val layers = mutableListOf<LayerInfo>()
        var pos = 0
        while (pos < b.size) {
            val (tag, p1) = vnt(b, pos); pos = p1
            val field = (tag shr 3).toInt()
            val wire  = (tag and 7).toInt()
            when (wire) {
                2 -> {
                    val (len, p2) = vnt(b, pos); pos = p2
                    if (field == 3) layers.add(parseLayer(b.copyOfRange(pos, pos + len.toInt())))
                    pos += len.toInt()
                }
                0 -> vnt(b, pos).also { pos = it.second }
                1 -> pos += 8
                5 -> pos += 4
                else -> break
            }
        }
        return layers
    }

    private fun parseLayer(b: ByteArray): LayerInfo {
        var name = ""
        val keys = mutableListOf<String>()
        val vals = mutableListOf<String>()
        val allTags = mutableListOf<List<Int>>()
        var pos = 0
        while (pos < b.size) {
            val (tag, p1) = vnt(b, pos); pos = p1
            val field = (tag shr 3).toInt(); val wire = (tag and 7).toInt()
            when {
                field == 1 && wire == 2 -> {
                    val (l, p) = vnt(b, pos); pos = p
                    name = String(b, pos, l.toInt()); pos += l.toInt()
                }
                field == 2 && wire == 2 -> {
                    val (l, p) = vnt(b, pos); pos = p
                    allTags.add(featureTags(b.copyOfRange(pos, pos + l.toInt())))
                    pos += l.toInt()
                }
                field == 3 && wire == 2 -> {
                    val (l, p) = vnt(b, pos); pos = p
                    keys.add(String(b, pos, l.toInt())); pos += l.toInt()
                }
                field == 4 && wire == 2 -> {
                    val (l, p) = vnt(b, pos); pos = p
                    vals.add(decodeValue(b.copyOfRange(pos, pos + l.toInt())))
                    pos += l.toInt()
                }
                wire == 0 -> vnt(b, pos).also { pos = it.second }
                wire == 2 -> { val (l, p) = vnt(b, pos); pos = p + l.toInt() }
                wire == 1 -> pos += 8
                wire == 5 -> pos += 4
                else -> break
            }
        }
        val samples = allTags.take(3).map { tags ->
            buildMap {
                for (i in tags.indices step 2) {
                    if (i + 1 < tags.size) {
                        val ki = tags[i]; val vi = tags[i + 1]
                        if (ki < keys.size && vi < vals.size) put(keys[ki], vals[vi])
                    }
                }
            }
        }
        return LayerInfo(name, allTags.size, keys, samples)
    }

    // Extracts packed uint32 tags (field 2) from a Feature message.
    private fun featureTags(b: ByteArray): List<Int> {
        val tags = mutableListOf<Int>()
        var pos = 0
        while (pos < b.size) {
            val (tag, p1) = vnt(b, pos); pos = p1
            val field = (tag shr 3).toInt(); val wire = (tag and 7).toInt()
            when {
                field == 2 && wire == 2 -> {
                    val (len, p2) = vnt(b, pos); pos = p2
                    val end = pos + len.toInt()
                    while (pos < end) { val (v, p3) = vnt(b, pos); pos = p3; tags.add(v.toInt()) }
                }
                wire == 0 -> vnt(b, pos).also { pos = it.second }
                wire == 2 -> { val (l, p) = vnt(b, pos); pos = p + l.toInt() }
                wire == 1 -> pos += 8
                wire == 5 -> pos += 4
                else -> break
            }
        }
        return tags
    }

    // Reads the first set value from a Value message, returns it as String.
    private fun decodeValue(b: ByteArray): String {
        var pos = 0
        while (pos < b.size) {
            val (tag, p1) = vnt(b, pos); pos = p1
            val field = (tag shr 3).toInt(); val wire = (tag and 7).toInt()
            when {
                field == 1 && wire == 2 -> {
                    val (l, p) = vnt(b, pos)
                    return String(b, p, l.toInt())
                }
                field == 2 && wire == 5 ->
                    return ByteBuffer.wrap(b, pos, 4).order(ByteOrder.LITTLE_ENDIAN).float.toString()
                field == 3 && wire == 1 ->
                    return ByteBuffer.wrap(b, pos, 8).order(ByteOrder.LITTLE_ENDIAN).double.toString()
                field == 4 && wire == 0 -> return vnt(b, pos).first.toString()
                field == 5 && wire == 0 -> return vnt(b, pos).first.toString()
                field == 6 && wire == 0 -> {
                    val (v, _) = vnt(b, pos)
                    return ((v ushr 1) xor -(v and 1L)).toString()
                }
                field == 7 && wire == 0 -> {
                    val (v, _) = vnt(b, pos)
                    return if (v != 0L) "true" else "false"
                }
                wire == 0 -> vnt(b, pos).also { pos = it.second }
                wire == 2 -> { val (l, p) = vnt(b, pos); pos = p + l.toInt() }
                wire == 1 -> pos += 8
                wire == 5 -> pos += 4
                else -> break
            }
        }
        return "?"
    }

    // Standard protobuf varint decoder.
    private fun vnt(buf: ByteArray, pos: Int): Pair<Long, Int> {
        var r = 0L; var s = 0; var i = pos
        while (true) {
            val b = buf[i++].toInt() and 0xFF
            r = r or ((b and 0x7F).toLong() shl s)
            if (b and 0x80 == 0) break
            s += 7
        }
        return r to i
    }
}

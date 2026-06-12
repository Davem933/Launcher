package com.example.carlauncher.data.map

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.GZIPInputStream

private const val TAG = "PmtilesServer"

class PmtilesHttpServer(private val file: File) : NanoHTTPD("127.0.0.1", 8888) {

    init { dumpHeader() }

    fun dumpHeader() {
        val raf = java.io.RandomAccessFile(file, "r")
        val header = ByteArray(128)
        raf.readFully(header)
        raf.close()
        Log.d("PmtilesServer", "HEADER: " + header.joinToString(" ") { "%02X".format(it) })
    }

    // Parsed once, cached. Re-reading on every request wastes I/O.
    private val header: Header? by lazy {
        try {
            readHeader().also {
                Log.d(TAG, "Header OK: zoom=${it.minZoom}-${it.maxZoom} " +
                    "rootDir=[${it.rootDirOffset},${it.rootDirLength}] " +
                    "leafDirs=[${it.leafDirsOffset},${it.leafDirsLength}] " +
                    "tileData=${it.tileDataOffset} " +
                    "internalComp=${it.internalCompression} tileComp=${it.tileCompression}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Header parse failed: ${e.message}", e)
            dumpHeaderHex()
            null
        }
    }

    // ── HTTP routing ──────────────────────────────────────────────────────────

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/tilejson" -> serveTileJson()
            uri.matches(Regex("/tile/\\d+/\\d+/\\d+")) -> {
                val parts = uri.split("/")   // ["", "tile", z, x, y]
                serveTile(parts[2].toInt(), parts[3].toInt(), parts[4].toInt())
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun serveTileJson(): Response {
        val h = header
        val json = buildString {
            append("""{"tilejson":"2.2.0",""")
            append(""""tiles":["http://127.0.0.1:8888/tile/{z}/{x}/{y}"],""")
            append(""""minzoom":${h?.minZoom ?: 0},""")
            append(""""maxzoom":${h?.maxZoom ?: 14},""")
            append(""""vector_layers":[""")
            append("""{"id":"transportation"},{"id":"water"},""")
            append("""{"id":"building"},{"id":"place"},{"id":"transportation_name"}""")
            append("""]}""")
        }
        Log.d(TAG, "TileJSON: $json")
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun serveTile(z: Int, x: Int, y: Int): Response {
        val data = findTile(z, x, y)
        Log.d(TAG, "Tile z=$z x=$x y=$y → ${data?.size ?: "NOT FOUND"} bytes")
        return if (data != null) {
            val resp = newFixedLengthResponse(
                Response.Status.OK, "application/x-protobuf",
                data.inputStream(), data.size.toLong()
            )
            resp.addHeader("Content-Encoding", "gzip")
            resp
        } else {
            newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
        }
    }

    // ── PMTiles v3 header ─────────────────────────────────────────────────────
    //
    // Actual byte layout confirmed by hex dump (all little-endian):
    //   0–6    magic "PMTiles"              7 bytes
    //   7      version (3)                  1 byte
    //   8–15   root_dir_offset              uint64
    //   16–23  root_dir_length              uint64
    //   24–31  json_metadata_offset         uint64  (skip)
    //   32–39  json_metadata_length         uint64  (skip)
    //   40–47  leaf_dirs_offset             uint64
    //   48–55  leaf_dirs_length             uint64
    //   56–63  tile_data_offset             uint64
    //   64–71  tile_data_length             uint64  (skip)
    //   72–79  addressed_tiles_count        uint64  (skip)
    //   80–87  tile_entries_count           uint64  (skip)
    //   88–95  tile_contents_count          uint64  (skip)
    //   96     clustered                    uint8   (skip)
    //   97     internal_compression         uint8   (1=none, 2=gzip)
    //   98     tile_compression             uint8
    //   99     tile_type                    uint8   (skip)
    //   100    min_zoom                     uint8
    //   101    max_zoom                     uint8

    private data class Header(
        val rootDirOffset: Long,
        val rootDirLength: Long,
        val leafDirsOffset: Long,
        val leafDirsLength: Long,
        val tileDataOffset: Long,
        val internalCompression: Int,
        val tileCompression: Int,
        val minZoom: Int,
        val maxZoom: Int
    )

    private fun readHeader(): Header {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(7).also { raf.readFully(it) }
            check(String(magic) == "PMTiles") { "Not a PMTiles file (magic=${String(magic)})" }
            val version = raf.read()
            check(version == 3) { "Unsupported PMTiles version: $version" }

            // raf is now at offset 8
            val rootDirOffset  = raf.readUint64Le()   // 8–15
            val rootDirLength  = raf.readUint64Le()   // 16–23
            raf.skipBytes(16)                          // 24–39: json_metadata_offset + length
            val leafDirsOffset = raf.readUint64Le()   // 40–47
            val leafDirsLength = raf.readUint64Le()   // 48–55
            val tileDataOffset = raf.readUint64Le()   // 56–63
            raf.skipBytes(8 + 8 + 8 + 8)             // 64–95: tile_data_length + 3 counts
            raf.skipBytes(1)                          // 96: clustered
            val internalComp   = raf.read()           // 97
            val tileComp       = raf.read()           // 98
            raf.skipBytes(1)                          // 99: tile_type
            val minZoom        = raf.read()           // 100
            val maxZoom        = raf.read()           // 101

            return Header(
                rootDirOffset, rootDirLength,
                leafDirsOffset, leafDirsLength,
                tileDataOffset,
                internalComp, tileComp,
                minZoom, maxZoom
            )
        }
    }

    // ── Tile lookup ───────────────────────────────────────────────────────────

    private data class Entry(
        val tileId: Long,
        val offset: Long,
        val length: Long,
        val runLength: Int
    )

    private fun findTile(z: Int, x: Int, y: Int): ByteArray? {
        val h = header ?: return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(h.rootDirOffset)
                val rootBytes = ByteArray(h.rootDirLength.toInt()).also { raf.readFully(it) }
                val rootEntries = parseDirectory(decompress(rootBytes, h.internalCompression))

                val tileId = zxyToTileId(z, x, y)
                var found = searchDirectory(rootEntries, tileId) ?: return null

                // runLength == 0 → leaf directory pointer
                if (found.runLength == 0) {
                    raf.seek(h.leafDirsOffset + found.offset)
                    val leafBytes = ByteArray(found.length.toInt()).also { raf.readFully(it) }
                    val leafEntries = parseDirectory(decompress(leafBytes, h.internalCompression))
                    found = searchDirectory(leafEntries, tileId) ?: return null
                }

                raf.seek(h.tileDataOffset + found.offset)
                ByteArray(found.length.toInt()).also { raf.readFully(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "findTile($z,$x,$y) error: ${e.message}")
            null
        }
    }

    private fun searchDirectory(entries: List<Entry>, tileId: Long): Entry? {
        var lo = 0; var hi = entries.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val e = entries[mid]
            when {
                e.tileId == tileId -> return e
                e.runLength > 1 && tileId >= e.tileId && tileId < e.tileId + e.runLength -> return e
                tileId < e.tileId -> hi = mid - 1
                else -> lo = mid + 1
            }
        }
        return null
    }

    // ── PMTiles v3 directory parsing ──────────────────────────────────────────
    //
    // Column-wise varint layout:
    //   numEntries          varint        ← NOT a fixed uint32
    //   tileId[0..n]        varint delta-encoded
    //   runLength[0..n]     varint
    //   length[0..n]        varint
    //   offset[0..n]        varint; raw==0 → contiguous with prev (or 0 for first), else absolute = raw-1

    private fun parseDirectory(bytes: ByteArray): List<Entry> {
        var pos = 0

        val (numEntriesL, p0) = readVarint(bytes, pos); pos = p0
        val n = numEntriesL.toInt()

        val tileIds  = LongArray(n)
        val runLens  = IntArray(n)
        val lengths  = LongArray(n)
        val offsets  = LongArray(n)

        var prevId = 0L
        for (i in 0 until n) {
            val (delta, p) = readVarint(bytes, pos); pos = p
            prevId += delta; tileIds[i] = prevId
        }
        for (i in 0 until n) {
            val (v, p) = readVarint(bytes, pos); pos = p; runLens[i] = v.toInt()
        }
        for (i in 0 until n) {
            val (v, p) = readVarint(bytes, pos); pos = p; lengths[i] = v
        }
        for (i in 0 until n) {
            val (raw, p) = readVarint(bytes, pos); pos = p
            offsets[i] = if (raw == 0L) {
                if (i == 0) 0L else offsets[i - 1] + lengths[i - 1]
            } else {
                raw - 1L
            }
        }

        return List(n) { Entry(tileIds[it], offsets[it], lengths[it], runLens[it]) }
    }

    // ── Hilbert curve: (z, x, y) → tileId ────────────────────────────────────

    private fun zxyToTileId(z: Int, x: Int, y: Int): Long {
        if (z == 0) return 0L
        val acc = ((1L shl (2 * z)) - 1L) / 3L
        var d = 0L; var mx = x; var my = y
        var s = 1 shl (z - 1)   // must be 2^(z-1), not z/2
        while (s > 0) {
            val rx = if ((mx and s) > 0) 1 else 0
            val ry = if ((my and s) > 0) 1 else 0
            d += s.toLong() * s.toLong() * ((3 * rx) xor ry).toLong()
            if (ry == 0) {
                if (rx == 1) { mx = s - 1 - mx; my = s - 1 - my }
                val t = mx; mx = my; my = t
            }
            s = s shr 1
        }
        return acc + d
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun decompress(data: ByteArray, compression: Int): ByteArray = when (compression) {
        2 -> GZIPInputStream(ByteArrayInputStream(data)).readBytes()
        else -> data
    }

    private fun RandomAccessFile.readUint64Le(): Long {
        var v = 0L
        for (i in 0..7) v = v or (read().toLong() and 0xFF shl (8 * i))
        return v
    }

    private fun readVarint(buf: ByteArray, pos: Int): Pair<Long, Int> {
        var result = 0L; var shift = 0; var index = pos
        while (true) {
            val b = buf[index++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Pair(result, index)
    }

    // Dumps first 128 bytes as hex — called automatically on header parse failure.
    // Also call manually from ActualMapWidget's LaunchedEffect(Unit) to verify offsets.
    fun dumpHeaderHex() {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val h = ByteArray(128).also { raf.readFully(it) }
                Log.d(TAG, "Header hex: ${h.joinToString(" ") { "%02X".format(it) }}")
                Log.d(TAG, "Magic: ${String(h.slice(0..6).map { it.toInt().toChar() }.toCharArray())}")
                Log.d(TAG, "Version: ${h[7].toInt()}")
                Log.d(TAG, "Byte[97]=${h[97].toInt()} (internalComp) " +
                    "Byte[98]=${h[98].toInt()} (tileComp) " +
                    "Byte[100]=${h[100].toInt()} (minZoom) " +
                    "Byte[101]=${h[101].toInt()} (maxZoom)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "dumpHeaderHex failed: ${e.message}")
        }
    }
}

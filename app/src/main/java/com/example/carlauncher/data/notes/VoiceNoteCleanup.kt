package com.example.carlauncher.data.notes

import java.io.File

/** Nahrávky, ke kterým v DB není řádek (proces zabitý mezi zápisem souboru a INSERTem). */
internal fun findOrphanAudio(files: List<File>, knownPaths: Set<String>): List<File> =
    files.filter { it.extension == "m4a" && it.absolutePath !in knownPaths }

package com.example.carlauncher.data.incident

/**
 * Heuristic license-plate text filter. ML Kit is a generic OCR, so recognized lines are
 * screened here before they become [PlateDetection]s. `text` is always logged into the
 * sidecar JSON so false positives stay auditable.
 *
 * Tuned for Czech plates (the launcher's market). Deliberately strict — a loose
 * `[A-Z0-9]{5,8}` rule matched arbitrary on-screen text (e.g. "24TIS" from "2,4 tis.").
 */
object PlateRegex {

    /** Standard CZ civilian plate: region digit + 2 letters + 4 digits, e.g. `1AB2345`. */
    private val CZ_STANDARD = Regex("^[0-9][A-Z]{2}[0-9]{4}$")

    /**
     * Older / motorcycle / trailer / single-letter-region variants:
     * region digit + 1-2 letters + 3-4 digits + optional trailing letter.
     */
    private val CZ_VARIANT = Regex("^[0-9][A-Z]{1,2}[0-9]{3,4}[A-Z]?$")

    /** Strip spaces, hyphens, dots; uppercase. */
    fun normalize(raw: String): String =
        raw.uppercase().filter { it.isLetterOrDigit() }

    fun looksLikePlate(raw: String): Boolean {
        val s = normalize(raw)
        if (s.length !in 6..8) return false
        // Must start with the region digit and contain at least 3 more digits.
        if (s.count { it.isDigit() } < 4) return false
        return CZ_STANDARD.matches(s) || CZ_VARIANT.matches(s)
    }
}

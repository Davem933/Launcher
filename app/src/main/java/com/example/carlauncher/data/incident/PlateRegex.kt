package com.example.carlauncher.data.incident

/**
 * Heuristic license-plate text filter. ML Kit is a generic OCR, so recognized lines are
 * screened here before they become [PlateDetection]s. `text` is always logged into the
 * sidecar JSON so false positives stay auditable.
 */
object PlateRegex {

    /** Czech standard plate: digit, 1–2 letters, 3–4 digits, optional trailing letter. */
    private val CZ = Regex("^[0-9][A-Z]{1,2}[0-9]{3,4}[A-Z]?$")

    /** Loose EU fallback — mix of 5–8 letters/digits with both classes present. */
    private val GENERIC = Regex("^[A-Z0-9]{5,8}$")

    /** Strip spaces, hyphens and dots; uppercase. */
    fun normalize(raw: String): String =
        raw.uppercase().filter { it.isLetterOrDigit() }

    fun looksLikePlate(raw: String): Boolean {
        val s = normalize(raw)
        if (s.length !in 5..8) return false
        val digits = s.count { it.isDigit() }
        val letters = s.count { it.isLetter() }
        if (digits < 2 || letters < 1) return false
        return CZ.matches(s) || GENERIC.matches(s)
    }
}

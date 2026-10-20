package com.salati.app.domain

/** Converts Arabic-Indic / Persian digits and Arabic decimal separator to ASCII. */
fun normalizeDigits(input: String): String = buildString {
    for (c in input.trim()) {
        when (c) {
            in '\u0660'..'\u0669' -> append('0' + (c - '\u0660'))
            in '\u06F0'..'\u06F9' -> append('0' + (c - '\u06F0'))
            '\u066B', ',' -> append('.')
            '\u2212' -> append('-')
            else -> append(c)
        }
    }
}

fun parseCount(input: String, max: Int = 1_000_000): Int? {
    val n = normalizeDigits(input)
    if (n.isEmpty() || !n.all { it in '0'..'9' }) return null
    return n.toIntOrNull()?.takeIf { it in 0..max }
}

fun parseCoordinate(input: String, limit: Double): Double? {
    val v = normalizeDigits(input).toDoubleOrNull() ?: return null
    return v.takeIf { it.isFinite() && it >= -limit && it <= limit }
}

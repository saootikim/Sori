/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.innertube.utils

private val fourDigits = Regex("""\d{4}""")

/**
 * The release year in an album subtitle's last run. Sori: YouTube Music localizes it ("2026년"
 * in Korean), so a plain `toIntOrNull()` loses every year outside English.
 */
fun releaseYear(text: String?): Int? = text?.let { fourDigits.find(it)?.value?.toIntOrNull() }

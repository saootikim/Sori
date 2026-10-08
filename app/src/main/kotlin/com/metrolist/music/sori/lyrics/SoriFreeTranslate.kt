package com.metrolist.music.sori.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lyrics translation without an API key, through Google Translate's public web endpoint.
 * Upstream translation needs an OpenRouter or DeepL key; with no key set, Sori uses this instead.
 */
object SoriFreeTranslate {
    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t&tl="

    /** True when the user has no key for the chosen provider, so the free path should be used. */
    fun applies(provider: String, apiKey: String, deeplApiKey: String): Boolean =
        (if (provider == "DeepL") deeplApiKey else apiKey).isBlank()

    /** Translates [text] line by line; the result has one entry per input line. */
    suspend fun translate(text: String, targetLanguage: String): Result<List<String>> = runCatching {
        val lines = text.split("\n")
        val joined = request(lines.joinToString("\n"), targetLanguage)
        if (joined.size == lines.size) {
            joined
        } else {
            // Line breaks got merged or split: translate each line on its own.
            val gate = Semaphore(4)
            coroutineScope {
                lines.map { line ->
                    async { if (line.isBlank()) line else gate.withPermit { request(line, targetLanguage).joinToString(" ") } }
                }.awaitAll()
            }
        }
    }

    private suspend fun request(text: String, targetLanguage: String): List<String> = withContext(Dispatchers.IO) {
        val connection = URL(ENDPOINT + URLEncoder.encode(targetLanguage.ifBlank { "ko" }, "UTF-8"))
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            connection.outputStream.use { it.write(("q=" + URLEncoder.encode(text, "UTF-8")).toByteArray()) }
            if (connection.responseCode !in 200..299) error("Translate HTTP ${connection.responseCode}")
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /** The response is `[[["translated", "original", ...], ...], ...]`; segments join into lines. */
    internal fun parse(body: String): List<String> {
        val segments = Json.parseToJsonElement(body).jsonArray[0] as? JsonArray ?: return emptyList()
        val joined = segments.joinToString("") { segment ->
            (segment as? JsonArray)?.getOrNull(0)?.jsonPrimitive?.content ?: ""
        }
        return joined.split("\n").map { it.trim() }
    }
}

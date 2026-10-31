package com.screentextcopier.app

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CaptureStore {
    private const val PREFS = "capture_settings"
    private const val KEY_WORDS = "watch_words"
    @Volatile var watchWords: String = ""
        private set
    private val seen = HashSet<String>()
    private var initialized = false

    @Synchronized fun load(context: Context) {
        watchWords = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_WORDS, "") ?: ""
        if (!initialized) {
            val file = File(context.filesDir, "captured_texts.txt")
            if (file.exists()) {
                val regex = Regex("(?m)^---------------- ENTRY .*? ----------------\\n([\\s\\S]*?)\\n-{20,}$")
                regex.findAll(file.readText()).forEach { match -> hash(normalize(match.groupValues[1])).takeIf { it.isNotBlank() }?.let(seen::add) }
            }
            initialized = true
        }
    }

    @Synchronized fun setWatchWords(context: Context, value: String) {
        watchWords = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_WORDS, value).apply()
    }

    private fun normalize(raw: String) = raw.trim().replace(Regex("[\\t ]+"), " ")
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    @Synchronized fun save(context: Context, raw: String, source: String): Boolean {
        load(context)
        val value = normalize(raw)
        if (value.isBlank()) return false
        val words = watchWords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (words.isNotEmpty() && words.none { value.contains(it, true) }) return false
        val digest = hash(value)
        if (seen.contains(digest)) return false
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return try {
            File(context.filesDir, "captured_texts.txt").appendText("---------------- ENTRY $stamp [${source.lowercase(Locale.US)}] ----------------\n$value\n------------------------------------------------------------\n")
            seen.add(digest)
            true
        } catch (_: Exception) { false }
    }

    @Synchronized fun clear(context: Context) {
        File(context.filesDir, "captured_texts.txt").writeText("")
        seen.clear()
        initialized = true
    }
}

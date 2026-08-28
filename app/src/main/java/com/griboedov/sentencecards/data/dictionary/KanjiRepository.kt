package com.griboedov.sentencecards.data.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class KanjiEntry(
    val literal: String,
    /** English meanings, in KANJIDIC2 order. */
    val meanings: List<String>,
    /** On'yomi (Chinese-derived) readings, in katakana. */
    val onyomi: List<String>,
    /** Kun'yomi (native Japanese) readings, in hiragana with okurigana marked by a "." (e.g. "た.べる"). */
    val kunyomi: List<String>,
)

/**
 * Read-only lookup into the bundled KANJIDIC2-derived per-kanji data (see THIRD_PARTY_NOTICES.md
 * and tools/build_kanji_dictionary.py for where `assets/dictionary/kanjidic.db` comes from).
 *
 * Mirrors [DictionaryRepository]'s approach: a separate pre-built SQLite file, not Room, copied
 * out of the APK to internal storage on first use since Android can't open a db file in place.
 */
class KanjiRepository(private val context: Context) {
    @Volatile
    private var database: SQLiteDatabase? = null

    /**
     * Looks up every unique kanji character in [text] (any non-kanji characters, e.g. kana
     * okurigana, are ignored), in first-occurrence order. Characters with no KANJIDIC2 entry are
     * silently skipped.
     */
    suspend fun lookupInText(text: String?): List<KanjiEntry> =
        withContext(Dispatchers.IO) {
            val literals = extractKanji(text)
            if (literals.isEmpty()) return@withContext emptyList()
            val db = openDatabase()
            val found = HashMap<String, KanjiEntry>()
            db.rawQuery(
                "SELECT literal, meanings, onyomi, kunyomi FROM kanji_entries WHERE literal IN (" +
                    literals.joinToString(",") { "?" } + ")",
                literals.toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val literal = cursor.getString(0)
                    found[literal] = KanjiEntry(
                        literal = literal,
                        meanings = splitField(cursor.getString(1)),
                        onyomi = splitField(cursor.getString(2)),
                        kunyomi = splitField(cursor.getString(3)),
                    )
                }
            }
            literals.mapNotNull { found[it] }
        }

    private fun splitField(value: String?): List<String> =
        value?.split("; ")?.filter { it.isNotEmpty() } ?: emptyList()

    private fun openDatabase(): SQLiteDatabase {
        database?.let { return it }
        synchronized(this) {
            database?.let { return it }
            val dest = File(context.filesDir, "kanjidic.db")
            val assetLength = context.assets.openFd("dictionary/kanjidic.db").use { it.length }
            if (!dest.exists() || dest.length() != assetLength) {
                context.assets.open("dictionary/kanjidic.db").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return SQLiteDatabase.openDatabase(dest.path, null, SQLiteDatabase.OPEN_READONLY).also {
                database = it
            }
        }
    }

    private companion object {
        // CJK Unified Ideographs (+ Extension A and Compatibility Ideographs) - i.e. "is this a kanji".
        val KANJI_RANGES = listOf(0x4E00..0x9FFF, 0x3400..0x4DBF, 0xF900..0xFAFF)

        fun isKanji(c: Char): Boolean = KANJI_RANGES.any { c.code in it }

        fun extractKanji(text: String?): List<String> {
            if (text.isNullOrEmpty()) return emptyList()
            val seen = LinkedHashSet<String>()
            for (c in text) {
                if (isKanji(c)) seen.add(c.toString())
            }
            return seen.toList()
        }
    }
}

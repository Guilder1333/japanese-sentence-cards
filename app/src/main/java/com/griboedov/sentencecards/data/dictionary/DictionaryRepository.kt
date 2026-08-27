package com.griboedov.sentencecards.data.dictionary

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DictionaryEntry(
    val kanji: String?,
    val kana: String,
    /** Pre-formatted "1. (pos) gloss; gloss\n2. ..." summary - see tools/build_dictionary.py. */
    val meaning: String,
)

/**
 * Read-only lookup into the bundled JMdict-derived dictionary (see THIRD_PARTY_NOTICES.md and
 * tools/build_dictionary.py for where `assets/dictionary/jmdict.db` comes from).
 *
 * This is a separate, pre-built SQLite file opened with the plain SQLite APIs - not a Room
 * database, since Room would need to own and generate the schema rather than read one built
 * offline from JMdict data. The asset is copied to internal storage on first use because Android
 * can't open a database file directly out of the APK.
 */
class DictionaryRepository(private val context: Context) {
    @Volatile
    private var database: SQLiteDatabase? = null

    /** Looks up a word by [kanji] and/or [kana] reading (exact match); either may be null/blank. */
    suspend fun lookup(kanji: String?, kana: String?, limit: Int = 5): List<DictionaryEntry> =
        withContext(Dispatchers.IO) {
            if (kanji.isNullOrBlank() && kana.isNullOrBlank()) return@withContext emptyList()
            val db = openDatabase()
            val results = LinkedHashMap<Long, DictionaryEntry>()
            if (!kanji.isNullOrBlank()) collectExact(db, results, "dict_kanji_index", kanji)
            if (!kana.isNullOrBlank()) collectExact(db, results, "dict_kana_index", kana)
            results.values.take(limit)
        }

    /**
     * Browses the dictionary by [query]: an exact/prefix match on any kanji or kana spelling
     * (indexed, fast), plus a substring match against the English meanings (not indexed - a full
     * scan over ~200k rows, but this only runs on an explicit search action, not per keystroke).
     */
    suspend fun search(query: String, limit: Int = 50): List<DictionaryEntry> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val db = openDatabase()
            val results = LinkedHashMap<Long, DictionaryEntry>()
            val prefix = "$trimmed%"
            collectLike(db, results, "dict_kanji_index", prefix, limit)
            collectLike(db, results, "dict_kana_index", prefix, limit)
            db.rawQuery(
                "SELECT id, kanji, kana, meaning FROM dict_entries WHERE meaning LIKE ? LIMIT ?",
                arrayOf("%$trimmed%", limit.toString()),
            ).use { cursor -> readInto(cursor, results) }
            results.values.take(limit)
        }

    /**
     * [search], but for text made up of multiple words - typically a whole pasted/typed sentence,
     * split into its per-word dictionary forms by
     * [com.griboedov.sentencecards.data.importer.JapaneseTokenizer.splitIntoSearchTerms]. Each of
     * [terms] is searched independently and the results are combined ("cumulative", per the
     * feature's whole point - every word in the pasted text should contribute, not just whichever
     * one a single combined query happened to match), deduped by kanji+kana spelling with
     * first-occurrence order kept, so a word appearing later in the text can't get pushed out
     * entirely by an earlier word's especially broad hit.
     *
     * [overallLimit] is split evenly across [terms] (floored at [MIN_PER_TERM_LIMIT] so a long
     * sentence doesn't starve every word down to nothing) rather than handed to each term in full,
     * so no single word's broad match can crowd out the others - for a single term this reduces to
     * exactly [search]'s own behavior and limit.
     */
    suspend fun searchWords(terms: List<String>, overallLimit: Int = 50): List<DictionaryEntry> {
        if (terms.isEmpty()) return emptyList()
        val perTermLimit = (overallLimit / terms.size).coerceAtLeast(MIN_PER_TERM_LIMIT)
        val merged = LinkedHashMap<Pair<String?, String>, DictionaryEntry>()
        for (term in terms) {
            for (entry in search(term, perTermLimit)) {
                merged.putIfAbsent(entry.kanji to entry.kana, entry)
            }
        }
        return merged.values.take(overallLimit)
    }

    private fun collectExact(db: SQLiteDatabase, results: MutableMap<Long, DictionaryEntry>, indexTable: String, value: String) {
        db.rawQuery(
            "SELECT e.id, e.kanji, e.kana, e.meaning FROM dict_entries e " +
                "JOIN $indexTable idx ON idx.entry_id = e.id WHERE idx.text = ?",
            arrayOf(value),
        ).use { cursor -> readInto(cursor, results) }
    }

    private fun collectLike(db: SQLiteDatabase, results: MutableMap<Long, DictionaryEntry>, indexTable: String, likePattern: String, limit: Int) {
        db.rawQuery(
            "SELECT e.id, e.kanji, e.kana, e.meaning FROM dict_entries e " +
                "JOIN $indexTable idx ON idx.entry_id = e.id WHERE idx.text LIKE ? LIMIT ?",
            arrayOf(likePattern, limit.toString()),
        ).use { cursor -> readInto(cursor, results) }
    }

    private fun readInto(cursor: Cursor, results: MutableMap<Long, DictionaryEntry>) {
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            if (results.containsKey(id)) continue
            results[id] = DictionaryEntry(
                kanji = cursor.getString(1),
                kana = cursor.getString(2),
                meaning = cursor.getString(3),
            )
        }
    }

    private fun openDatabase(): SQLiteDatabase {
        database?.let { return it }
        synchronized(this) {
            database?.let { return it }
            val dest = File(context.filesDir, "jmdict.db")
            // Re-copy whenever the on-device copy doesn't match the bundled asset's size - not
            // just when it's missing. Otherwise a rebuilt/updated jmdict.db (e.g. switching from
            // the common-words subset to the full dictionary) silently keeps using whatever was
            // copied on a previous run, since a same-named file already "exists".
            val assetLength = context.assets.openFd("dictionary/jmdict.db").use { it.length }
            if (!dest.exists() || dest.length() != assetLength) {
                context.assets.open("dictionary/jmdict.db").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return SQLiteDatabase.openDatabase(dest.path, null, SQLiteDatabase.OPEN_READONLY).also {
                database = it
            }
        }
    }

    private companion object {
        const val MIN_PER_TERM_LIMIT = 5
    }
}

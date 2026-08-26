package com.griboedov.sentencecards

import android.app.Application
import android.util.Log
import com.griboedov.sentencecards.data.cards.CardGenerator
import com.griboedov.sentencecards.data.db.AppDatabase
import com.griboedov.sentencecards.data.dictionary.DictionaryRepository
import com.griboedov.sentencecards.data.importer.BookImporter
import com.griboedov.sentencecards.data.importer.SentenceImporter
import com.griboedov.sentencecards.data.repository.CardRepository
import com.griboedov.sentencecards.data.repository.SentenceRepository
import com.griboedov.sentencecards.data.repository.WordRepository
import com.griboedov.sentencecards.data.seed.SeedData
import com.griboedov.sentencecards.data.translation.DeepLTranslator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Simple manual-DI container: no DI framework, just plain constructor wiring, kept small enough
 * that it doesn't need one.
 */
class SentenceCardsApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set
    lateinit var wordRepository: WordRepository
        private set
    lateinit var sentenceRepository: SentenceRepository
        private set
    lateinit var cardRepository: CardRepository
        private set
    lateinit var importer: SentenceImporter
        private set
    lateinit var bookImporter: BookImporter
        private set
    lateinit var dictionaryRepository: DictionaryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        // Logged unconditionally at startup (rather than only on first failed translate call) so
        // "translation isn't showing up" is diagnosable without needing to reproduce a card
        // generation first - filter logcat for tag "SentenceCardsApp" to check it.
        Log.i(
            "SentenceCardsApp",
            if (BuildConfig.DEEPL_API_KEY.isBlank()) {
                "DeepL translation disabled: DEEPL_API_KEY was empty/unset when this build was compiled"
            } else {
                "DeepL translation enabled (key ends with '${BuildConfig.DEEPL_API_KEY.takeLast(4)}')"
            },
        )
        val translator = DeepLTranslator(BuildConfig.DEEPL_API_KEY)
        val cardGenerator = CardGenerator(database.sentenceDao(), database.cardDao(), database.wordDao(), translator)
        wordRepository = WordRepository(database.wordDao(), cardGenerator)
        sentenceRepository = SentenceRepository(database.sentenceDao())
        cardRepository = CardRepository(database.cardDao())
        importer = SentenceImporter(database.wordDao(), database.sentenceDao())
        dictionaryRepository = DictionaryRepository(this)
        bookImporter = BookImporter(database.wordDao(), dictionaryRepository, importer)

        appScope.launch {
            if (sentenceRepository.count() == 0) {
                importer.importJson(SeedData.json)
            }
        }
    }
}

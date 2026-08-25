package com.griboedov.sentencecards

import android.app.Application
import com.griboedov.sentencecards.data.cards.CardGenerator
import com.griboedov.sentencecards.data.db.AppDatabase
import com.griboedov.sentencecards.data.dictionary.DictionaryRepository
import com.griboedov.sentencecards.data.importer.SentenceImporter
import com.griboedov.sentencecards.data.repository.CardRepository
import com.griboedov.sentencecards.data.repository.SentenceRepository
import com.griboedov.sentencecards.data.repository.WordRepository
import com.griboedov.sentencecards.data.seed.SeedData
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
    lateinit var dictionaryRepository: DictionaryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        val cardGenerator = CardGenerator(database.sentenceDao(), database.cardDao(), database.wordDao())
        wordRepository = WordRepository(database.wordDao(), cardGenerator)
        sentenceRepository = SentenceRepository(database.sentenceDao())
        cardRepository = CardRepository(database.cardDao())
        importer = SentenceImporter(database.wordDao(), database.sentenceDao())
        dictionaryRepository = DictionaryRepository(this)

        appScope.launch {
            if (sentenceRepository.count() == 0) {
                importer.importJson(SeedData.json)
            }
        }
    }
}

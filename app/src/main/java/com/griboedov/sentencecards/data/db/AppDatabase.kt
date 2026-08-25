package com.griboedov.sentencecards.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [WordEntity::class, SentenceEntity::class, CardEntity::class, SentenceWordCrossRef::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun sentenceDao(): SentenceDao
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "japanese-sentence-cards.db",
                )
                    // The schema is still actively changing during this UI-prototype phase; rather
                    // than write migrations for every iteration, just rebuild on schema changes.
                    // Revisit once the schema stabilizes and real user data needs to survive.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}

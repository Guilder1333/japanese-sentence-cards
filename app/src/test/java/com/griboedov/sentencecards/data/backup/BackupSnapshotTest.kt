package com.griboedov.sentencecards.data.backup

import com.griboedov.sentencecards.data.db.CardEntity
import com.griboedov.sentencecards.data.db.QueueLevel
import com.griboedov.sentencecards.data.db.WordProgress
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

private val json = Json { ignoreUnknownKeys = true }

private fun card(id: Long) = CardEntity(
    id = id,
    sentenceId = id,
    text = "text$id",
    translation = "translation$id",
    structure = emptyList(),
    mainWordIds = listOf(10L, 20L),
    queueLevel = QueueLevel.MEDIUM,
    lastMarkedLevel = QueueLevel.HIGHEST,
)

class BackupSnapshotTest {

    @Test
    fun `round-trips through JSON`() {
        val snapshot = BackupSnapshot(
            exportedAtEpochMillis = 1_700_000_000_000L,
            cards = listOf(card(1), card(2)),
            wordProgress = listOf(
                WordProgress(wordId = 10L, toLearn = true, forceFurigana = false, quizSuccess = 2, quizFails = 1),
                WordProgress(wordId = 20L, toLearn = false, forceFurigana = true, quizSuccess = 0, quizFails = 0),
            ),
        )

        val decoded = json.decodeFromString(BackupSnapshot.serializer(), json.encodeToString(BackupSnapshot.serializer(), snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `decoding tolerates unknown fields for forward compatibility`() {
        val payload = """
            {"version":1,"exportedAtEpochMillis":1,"cards":[],"wordProgress":[],"future":"field"}
        """.trimIndent()

        val decoded = json.decodeFromString(BackupSnapshot.serializer(), payload)

        assertEquals(0, decoded.cards.size)
        assertEquals(0, decoded.wordProgress.size)
    }
}

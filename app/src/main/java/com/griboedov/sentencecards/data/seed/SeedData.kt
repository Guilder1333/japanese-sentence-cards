package com.griboedov.sentencecards.data.seed

/**
 * A handful of sample structured sentences so the app has something to show on first launch,
 * without needing the bulk import screen. Fed through [com.griboedov.sentencecards.data.importer.SentenceImporter]
 * on first run - the exact same code path the Import Sentences screen uses.
 */
object SeedData {
    val json: String = """
    [
      {
        "translation": "This word is English.",
        "structure": [
          { "word": "この", "translation": "this", "kind": 2 },
          { "word": "言葉", "furigana": "ことば", "translation": "word", "kind": 1, "id": 1 },
          { "word": "は", "translation": "(topic marker)", "kind": 2 },
          { "word": "イギリス", "translation": "England/British", "kind": 3 },
          { "word": "語", "furigana": "ご", "translation": "language", "kind": 1, "id": 2 },
          { "word": "です", "translation": "is (polite)", "kind": 2 },
          { "word": "。", "translation": "", "kind": 2 }
        ]
      },
      {
        "translation": "I study Japanese every day.",
        "structure": [
          { "word": "私", "furigana": "わたし", "translation": "I", "kind": 1, "id": 10 },
          { "word": "は", "translation": "(topic marker)", "kind": 2 },
          { "word": "毎日", "furigana": "まいにち", "translation": "every day", "kind": 1, "id": 11 },
          { "word": "日本語", "furigana": "にほんご", "translation": "Japanese language", "kind": 1, "id": 12 },
          { "word": "を", "translation": "(object marker)", "kind": 2 },
          { "word": "勉強します", "furigana": "べんきょうします", "translation": "study", "kind": 1, "id": 13 },
          { "word": "。", "translation": "", "kind": 2 }
        ]
      },
      {
        "translation": "The cat is looking outside the window.",
        "structure": [
          { "word": "猫", "furigana": "ねこ", "translation": "cat", "kind": 1, "id": 20 },
          { "word": "が", "translation": "(subject marker)", "kind": 2 },
          { "word": "窓", "furigana": "まど", "translation": "window", "kind": 1, "id": 21 },
          { "word": "の", "translation": "(possessive marker)", "kind": 2 },
          { "word": "外", "furigana": "そと", "translation": "outside", "kind": 1, "id": 22 },
          { "word": "を", "translation": "(object marker)", "kind": 2 },
          { "word": "見ています", "furigana": "みています", "translation": "is looking", "kind": 1, "id": 23 },
          { "word": "。", "translation": "", "kind": 2 }
        ]
      },
      {
        "translation": "Today is very hot.",
        "structure": [
          { "word": "今日", "furigana": "きょう", "translation": "today", "kind": 1, "id": 30 },
          { "word": "は", "translation": "(topic marker)", "kind": 2 },
          { "word": "とても", "translation": "very", "kind": 2 },
          { "word": "暑い", "furigana": "あつい", "translation": "hot", "kind": 1, "id": 31 },
          { "word": "です", "translation": "is (polite)", "kind": 2 },
          { "word": "。", "translation": "", "kind": 2 }
        ]
      }
    ]
    """.trimIndent()
}

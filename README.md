# Japanese Sentences Flash Cards

## Implementation status

The Android app (Kotlin + Jetpack Compose + Room) implements most of what's described below.
This section is the quick reference for what's actually built - see `ASSUMPTIONS.md` for the
specific interpretation behind anything left ambiguous below, and `THIRD_PARTY_NOTICES.md` for
the bundled dictionary's licensing.

**Done**
- Known kanji/words database with all 7 metrics below (Room `WordEntity`).
- Sentence pool + flash card storage as two separate tables (`SentenceEntity` = raw imported
  sentences, searchable by word via a `sentence_words` index; `CardEntity` = the actual review
  cards generated from it - see below).
- Structured sentence import (bulk JSON) into the raw sentence pool - new kanji/words default
  not-learned, hiragana/katakana aren't tracked, per the rule below. Importing no longer creates
  cards directly.
- "Adding word to learn" sentence-selection algorithm (`data/cards/CardGenerator.kt`,
  `CardScoring.kt`): whenever a word is marked to-learn (4-direction menu, word browser, or
  dictionary "add as to-learn"), the word ends up backed by (at most) 3 cards total - reusing
  existing ones first, so no more cards get spawned than actually needed:
  1. Any sentence containing the word that already backs a card - for some other word, or even
     this one already - is reused: the word is just added to that card's main words (reactivating
     the card if it had already been fully learned), instead of spawning a duplicate card for the
     same sentence.
  2. Only the shortfall, if any, is filled with brand-new cards: the sentence pool is searched for
     remaining (not yet carded) sentences containing the word, scored, and the best ones become
     cards. Scoring per word token in a candidate sentence: +2 if the word isn't tracked yet, +2
     if it's tracked and well-known (not to-learn, furigana no longer forced), +1 if it's tracked
     but still shaky (not to-learn, furigana still forced), +0 if it's itself another to-learn
     target (avoid stacking multiple unlearned words in one card).
- Priority queue (highest/medium/easy/backlog), including "twice in a row demotes a level" and
  "don't jump the queue mid-pass".
- Review UI: flip card, word 4-direction menu (know/learn/force furigana/dictionary) via a
  flick-keyboard-style press-and-hold gesture, word-status coloring (green known / red to-learn /
  blue this card's main words).
- Quiz after "Learned": reading-only multiple-choice for every main word, one round per Learned
  press, success/fail metrics, sentence fully learned once every main word passes.
- Word/kanji browser ("Words to learn") - the internal tracked-words table.
- **Dictionary** - further along than the TODO below suggests: an offline JMdict-derived SQLite
  dictionary (~218k entries) bundled with the app. The word menu's "Up"/tap action looks a word up
  in it; a dedicated Dictionary tab searches the whole thing and can add any result into the
  internal words database as known / to-learn / force-furigana.
- Plain-text book import in-app (`data/importer/BookImporter.kt`) - the same job
  `tools/import_book.py` does offline (sentence splitting, tokenizing/tagging, per-word dictionary
  glosses), but on-device via the bundled Kuromoji IPADIC tokenizer, for when running the Python
  script isn't an option. Reuses the same batched DB-write path as the structured-JSON import.
- Single-sentence import (`data/cards/SingleSentenceImporter.kt`): type one sentence directly
  instead of importing a whole file. It's checked to really be one sentence (same splitting rule
  as book import - anything else is reported back as an error), then shown in a dedicated review
  view - not the normal review `FlashCardView` - with every word tappable to toggle it green (not
  picked) / blue (picked as this card's main word). Import is only enabled once at least one word
  is picked, and creates exactly one card with those words as its main words - no scoring/search,
  no other cards or sentences touched. The sentence is still written to the pool and translated
  like any other card (so its other words are available if picked to-learn later), and word ids
  are resolved against the words table by exact text match first, reusing an already-tracked
  word's id instead of creating a duplicate.

**Partial**
- Knowledge-level formula: placeholder only, per the TODO below (`data/knowledge/KnowledgeLevel.kt`).
- Quiz meaning: only reading is quizzed so far - extracting meaning from a sentence automatically
  isn't straightforward yet, so that half of "quiz answering which reading and meaning" is deferred.

**Not yet done**
- Sentence full-text search / browsing screen - intentionally skipped for now.
- JLPT base-level defaults (N4/N5 auto-known), from the Notes section.
- Any in-app settings screen.

## Main features

### Maintain list of known kanji/words

App should have small local database where it stores information about learned kanji and words.

There should be stored few metrics:
1. Times shown - increments each time flash card with this word or kanji is shown. 
2. Times furigana shown - furigana on the front is hidden by default for every word, new or not; this increments only when the user has specifically requested to show it (see metric 7).
3. Times translation shown - user would need to press special button to see word translation.
4. To-learn marker - true or false, user defines if they want to learn this kanji or word. New words are considered false.
5. Quiz success - how many times user answered "sentence learned quiz" correctly.
6. Quiz fails - how many times user answered "sentence learned quiz" wrongly.
7. Force furigana - whether furigana shows on the front side of the flash card. Off by default for every word, including brand-new ones - it only ever turns on via an explicit "force furigana" action (word browser / dictionary, or the 4-direction menu's down flick), and marking a word known (or a correct quiz answer) clears it again in case it had been forced on. Replaces an earlier separate "hide furigana" marker - one flag now covers both directions, since front display only ever cares about show-or-not.

Based on this information we can assume knowledge level of each word or kanji.

> **TODO**: Come up with proper metric for calculating knowledge level based on metrics above.

### Flash cards storage

List of flash cards for user to see. Of course some of them can be marked as learned.
This database is not searchable except for full text search

Should be few fields.
1. Sentence ID
2. Queue info (one or two fields)
3. Shown times
4. Learned - if user marked this sentence as learned.
5. Main words - list of words for which this sentence was picked.
6. Quiz succeed - after marking as learned, flash card should be shown one more time but now with quiz answering which reading and meaning the main word has.

### Sentence storage

Should be few fields.
1. Text - plain Japanese text 
2. Structure - this is parsed structure of the sentence, see next section for further explanation
3. Translation - full translation of the sentence.

#### Sentence structure

Schema example

```json
[
  {
    "word": "この",
    "translation": "this",
    "kind": 2
  },
  {
    "word": "言葉",
    "furigana": "ことば",
    "translation": "word",
    "kind": 1,
    "id": 1234
  },
  {
    "word": "は",
    "translation": "is",
    "kind": 2
  },
  {
    "word": "イギリス",
    "translation": "english",
    "kind": 3
  },
  {
    "word": "語",
    "furigana": "ご",
    "translation": "language",
    "kind": 1,
    "id": 12354
  }
]
```

There might be more fields to represent what kind of particle is this word, or some other info.

So, point is that the sentence is being parsed and stored as separate items.

ID is assigned to the word present in database.

#### Adding sentence

When new sentence is added to the app, we should assume that all new kanji words (not yet present in the database) are not learned, and any hiragana/katakana words are already known.
I.e. hiragana/katakana words not even added to the database.

### Quiz

Whenever user marks sentence as learned, there is still final quiz before app can actually mark it as learned.

Quiz should include all the main words in the sentence. Words answered correctly increment their success metric, while failed - fail metric.
Also successful words removed from main words of the sentence, if there are no more main words in the sentence, we can assume it as learned.

### UI

Front of the card should display just plain Japanese sentence, and furigana for the words that have forced furigana flag.

Bottom of the flash card should be buttons: learned, easy, medium, hard. This should affect when this card will be shown again.
1. Learned - mark the sentence for quiz. Goes to medium priority.
2. Easy - show again with the lowest priority. So, when there are no more cards to show in medium priority.
3. Medium - second priority, when highest priority is empty.
4. Hard - highest priority.

Priorities work the way that highest priority is shown first, medium is shown only when highest is empty.
But, if sentence is marked as same level two times in a row, it is decreased in priority to level below.
So priorities queue there are 4: highest, medium, easy, backlog.
Also, when priority queue is started, it should not go to higher queue until it is emptied once.

So, for example, all highes priority items were removed from queue, and it is now empty.
We moved to medium priority queue, but marked item from there as hard (goes to the highest priority),
but we still continue with medium queue until previously added items are checked once.

Pressing on the card itself should flip it to show translation and furigana for all the words.

Back side of cards is not flipped back on click. Instead there are extra actions available.

Pressing on word should open 4 directions menu:
1. Right - mark word as known
2. Left - mark word for learning
3. Down - force furigana
4. Up - open dictionary (TODO)

## Sentences source

In general sentence can be imported in two variants - plain text or already structured.

I have python script for making similar kind of flash cards for anki.

### Adding word to learn

Whenever a word is marked to-learn, it should end up backed by (at most) 3 cards total - not 3 *new* cards regardless of what's already there. So first, existing cards are reused, and only the shortfall is filled with newly generated ones (see `data/cards/`):

1. **Reuse.** Search existing cards for any whose sentence already contains the word (carrying it for some other word, or even this one from an earlier pass). Each one just gets the word added to its main words, instead of a duplicate card being spawned for the same sentence. If such a card had already been fully learned (out of the review rotation), adding the word reactivates it, so the word actually gets reviewed/quizzed there.
2. **Fill the shortfall.** If reuse covered fewer than 3, search the remaining (not yet carded) sentence pool for sentences containing the word, score them, and turn the best-fitting ones into new cards, up to the remaining count.

A sentence must contain the word to even be a candidate for either step. Scoring (step 2 only) - each word token in the sentence adds bonus points:
- not yet in the words table at all: +2
- in the words table, not to-learn, but furigana still forced (still shaky): +1
- in the words table, not to-learn, furigana no longer forced (well known): +2
- itself another to-learn word: +0 (a card should isolate the one new word, not stack several)

The bonus sum is normalized to a 0..1 fraction of the maximum possible bonus (+2 per word token, i.e. sum / (word count * 2)), so sentences with different word counts are actually comparable - a long sentence full of well-known words shouldn't automatically outscore a short, tightly-focused one just by having more tokens to rack up bonus on. Ties (equal normalized score) favor fewer word tokens - a shorter sentence is simpler to review - then fall back to sentence id for determinism.

A sentence already backing a card (reused or not) is excluded from step 2's candidates, so a sentence never backs two cards, and marking the same word to-learn again (e.g. after toggling it off and on) fills any remaining shortfall from the next-best batch rather than duplicating what's already there.

# Notes

I also think to define base level, for example all words of level N4 or N5 should be marked as known by default.
Yeah, I know that there is no clear separation for JLPT, but there are databases that can provide this separation approximately.  
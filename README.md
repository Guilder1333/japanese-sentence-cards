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
- Review UI: flip card, word 4-direction menu (know/learn/force furigana/dictionary - kana words
  get every direction but force furigana, see "UI" below) via a
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
    "kind": 4
  },
  {
    "word": "言葉",
    "furigana": "ことば",
    "kind": 1,
    "id": 1234,
    "dictionaryEntryId": 1358280
  },
  {
    "word": "は",
    "kind": 2
  },
  {
    "word": "イギリス",
    "kind": 3
  },
  {
    "word": "だった",
    "kind": 2
  },
  {
    "word": "語",
    "furigana": "ご",
    "kind": 1,
    "id": 12354,
    "dictionaryEntryId": 1421850
  },
  {
    "word": "食べた",
    "dictForm": "食べる",
    "furigana": "たべた",
    "kind": 1,
    "id": 98765,
    "dictionaryEntryId": 1358280
  }
]
```

There might be more fields to represent what kind of particle is this word, or some other info.

`kind` is:

| `kind` | Meaning | Tracked as a word? |
| --- | --- | --- |
| 1 | **Word** - contains kanji (言葉, 食べた) | yes |
| 2 | **Particle/grammar** - particles (は, を), auxiliaries and the copula (です, た, ない), punctuation, and dependent helpers that only attach to another word (the いる of ～ている, the さん of 田中さん) | no |
| 3 | **Katakana** loanword (イギリス, コンピューター) | no |
| 4 | **Hiragana word** - a *content* word (verb, adjective, adverb, noun, pronoun) that happens to be written without kanji (わかる, きれい, とても, ぼく) | no |

Kinds 1 and 3 fall out of the script alone, but 2 and 4 do not: わかる and が are both hiragana-only,
so telling a kana-written word from a particle needs the tokenizer's **part of speech**, not just
the characters. Both parsers do this - `classifyToken` in
`data/importer/BookText.kt` (Kuromoji IPADIC, in-app) and `classify_token` in
`tools/import_book.py` (fugashi/UniDic, offline) - by the same rule, though the two dictionaries
spell their tags differently (IPADIC's 記号/非自立 vs. UniDic's 補助記号/非自立可能). A token with
no part of speech available falls back to kind 2, which is the old script-only behaviour.

Kind 4 is not tracked in the word database (see "Adding sentence" below) - it exists so that a kana
word is still recorded *as a word*: the UI can style it apart from grammar, and the Dictionary
screen searches it (「きれいな花」 looks up きれい as well as 花) instead of discarding it as filler.

So, point is that the sentence is being parsed and stored as separate items.

ID is assigned to the word present in database. For a `kind: 1` (word) token, the id is derived
from the word's **dictionary form**, not its surface form as written in the sentence - "食べた"
("ate") and "食べる" ("eat") are the same tracked word. `word` stays whatever inflected form is
actually in the sentence (needed to render it correctly, since that's what's actually written);
`furigana` is that same occurrence's actual reading, for the same reason. `dictForm` and
`dictionaryEntryId` carry the dictionary form and the bundled dictionary's matching entry id
separately - present when they differ from/aren't derivable from `word` - and are what seed the
tracked word's text and dictionary reference the first time that id is encountered, so the
words-to-learn list shows dictionary forms (and can look up reading/meaning through the dictionary
by reference) instead of whatever inflection happened to be imported first. Neither is a per-token
gloss - the tracked word's reading and meaning are always looked up through `dictionaryEntryId`,
never duplicated in the sentence structure itself.

#### Adding sentence

When new sentence is added to the app, we should assume that all new kanji words (not yet present in the database) are not learned, and any hiragana/katakana words are already known.
I.e. hiragana/katakana words not even added to the database - kinds 2, 3 and 4 never get an `id` or
a row in the words table, only kind 1 does.

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

A plain tap on a word does the same thing as flicking it up.

"Word" here means any token that isn't grammar - `kind` 1, 3 **and** 4 (see the `kind` table above),
not only tracked kanji words. A kana word gets the dictionary lookup and the same 4-direction menu,
with two differences that follow from it not being in the words table:

- **Down (force furigana) is unavailable.** Furigana over a word already written in kana would just
  be the word again. The slot is drawn greyed out and a downward flick doesn't resolve to a
  direction at all, so the action can't be committed by accident. This holds even after the word has
  been promoted (below) - it's a property of the script, not of whether the word is tracked.
- **Right/left promote the word.** Kana words are assumed known and are never imported into the
  words table, so the first time the user says otherwise about one - marks it known, or to-learn -
  is when its row gets created, keyed on the word's dictionary form (わから on the card, わかる in
  the table). From then on it behaves like any other tracked word and shows its status colour on
  the card. Note that card generation for a freshly promoted word only finds sentences that
  reference it by id, and kana words carry no id in a sentence's structure - so promoting one marks
  it to-learn but does not yet pull sentences out of the pool for it.

## Sentences source

In general sentence can be imported in two variants - plain text or already structured.

I have python script for making similar kind of flash cards for anki.

Plain text is split into sentences at 。.！!？?, with two adjustments for how books are actually
typeset (`splitSentences` in `data/importer/BookText.kt` and `split_sentences` in
`tools/import_book.py` implement the same rules):

- A terminator **inside** a 「」/『』 quote doesn't cut the sentence - the quote is part of the
  sentence it's embedded in. A quote that runs for several sentences is instead broken into one
  sentence per quoted sentence, and the surrounding narration dropped.
- A **blank line** - two or more consecutive line breaks - is a paragraph break and ends the
  sentence, terminator or not: headings, verse and unpunctuated fragments are common and would
  otherwise be glued onto the next paragraph. A single line break is just a hard wrap and does not
  split, since books wrap mid-sentence.

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
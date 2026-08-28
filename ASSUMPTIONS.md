# Assumptions made while implementing the UI/data pass

The README leaves a number of behaviors implicit or explicitly open (e.g. the knowledge-level
TODO). Here's what this pass assumed, and where in the code to change it if the real answer is
different. Nothing here is set in stone - it's meant to be a clear, searchable list of decisions
rather than silent guessing.

- **Knowledge level formula** (`data/knowledge/KnowledgeLevel.kt`): the README explicitly leaves
  this as a TODO. Implemented a simple placeholder score from the existing metrics, clearly
  marked - swap the formula whenever a real one is defined; nothing else depends on it.

- **New word defaults** (`data/importer/StructuredImport.kt`): a brand-new kanji/word from import
  gets `toLearn = false` and `forceFurigana = false` - both the README's literal defaults ("new
  words are considered false" for to-learn; front furigana is opt-in for every word per your
  instruction, not on-by-default even for new ones - see "Furigana display" below). An earlier
  version of this note had `toLearn = true`, reasoning that every imported word was automatically
  one of its sentence's main (to-learn) words - that's no longer true now that import only
  populates the raw sentence pool and never creates cards directly (see "Sentences vs. cards split"
  below), so the literal README default applies.

- **4-direction menu effects** (`data/repository/WordRepository.kt`): right (know) clears both
  `toLearn` and `forceFurigana`; left (learn) sets `toLearn` only - it deliberately leaves
  `forceFurigana` untouched, per your instruction that front furigana is a separate, explicit
  opt-in rather than something bundled into "to learn"; down forces furigana back on
  (`forceFurigana = true`) - e.g. for an otherwise-known word the user still wants the crutch for;
  up looks the word up in the bundled dictionary (see `data/dictionary/`) - the README marks
  dictionary as a TODO, but it's since been fully implemented, offline. A correct quiz answer
  applies the same `toLearn`/`forceFurigana` clear as the right-flick "known" action, in case
  `forceFurigana` had been explicitly turned on for that word.

- **Furigana display, front vs. back** (`ui/review/FlashCardView.kt`, `data/db/WordEntity.kt`):
  per your instruction, there is only one furigana flag now - `forceFurigana` - not two, and it
  defaults `false` for every word, new or not (front furigana is opt-in, full stop - not even new
  words get it "for free" the way the README's original metric 2 described). It's set `true` only
  by an explicit "force furigana" action (word browser / dictionary, or the 4-direction menu's down
  flick), and cleared again by marking a word known or a correct quiz answer, in case it had been
  forced on. The back is the reveal side and always shows furigana for every word, unconditionally
  - there's no longer a way to suppress it there. This replaces an earlier separate `hideFurigana`
  field/"strong known" marker, which is now removed entirely (the README's word-tracking metrics
  list is 7 metrics, not 8).

- **Knowledge level's "Known" tier** (`data/knowledge/KnowledgeLevel.kt`): used to be a special
  case keyed off the now-removed `hideFurigana` field (later, briefly, off `!toLearn &&
  !forceFurigana`) - but since `forceFurigana` now defaults `false` for *every* untouched word,
  that combination no longer means "confirmed known," it means "never touched yet," which was
  misclassifying every fresh import as Known. Folded `KNOWN` into the same score ladder as the
  other tiers (top rung, `score >= 20`) instead of a separate shortcut - still just the PLACEHOLDER
  formula the README leaves as a TODO, nothing depends on the exact numbers.

- **Tap vs. the 4-direction menu** (`ui/review/ReviewViewModel.kt`): a plain tap on a back-of-card
  word and flicking it up do the same thing - open the dictionary lookup - per your instruction.
  The tap still separately counts towards the "times translation shown" metric, since the README
  ties that metric to "a special button" distinct from the four directions.

- **Word menu interaction** (`ui/review/FlashCardView.kt`, `FlickMenu.kt`): press-and-hold a word,
  then drag - like a phone's flick keyboard - to pick a direction; lifting the finger commits
  whichever direction was last highlighted. The overlay is a plain layered composable, not
  Compose's `Popup`, specifically so it can't intercept the drag events still arriving at the
  word's own pointer input.

- **Word status colors** (`ui/theme/WordStatusColor.kt`): known/unknown maps directly onto the
  existing `toLearn` flag (`false` → green/known, `true` → red/unknown) rather than a new field -
  so a brand-new, never-reviewed word reads green until explicitly flicked to "learn". A
  sentence's main words always read blue regardless of that, since they're this card's actual
  study focus.

- **New sentence starting queue**: new sentences default to `QueueLevel.HIGHEST` (nothing in the
  README says where brand-new material starts; highest surfaces it soonest).

- **"Marked the same level twice in a row" demotion** (`data/queue/ReviewGrading.kt`): tracked
  per sentence via `lastMarkedLevel`, compared against the *button pressed* (not the resulting
  queue), and demotes exactly one level per repeat (floor at Backlog). Two Hard-in-a-row demotes
  to Medium, not further, unless Hard is pressed a third time in a row.

- **Quiz shape** (`ui/review/QuizCardView.kt`, `data/queue/ReviewGrading.kt`): the quiz shows every
  main word's reading as a 2-4 option multiple-choice question all at once (not one word at a
  time), matching your description. Meaning isn't quizzed yet - you flagged that extracting a
  word's meaning from the sentence isn't easily automatable, so only reading is tested for now;
  revisit once there's a plan for meaning. Picking an option locks it in and shows right/wrong
  immediately, but nothing is written to the database until "Continue" - pressed once every word
  is answered - which grades the whole round at once via `applyQuizResult`.

- **Quiz distractors** (`data/quiz/QuizOptions.kt`): wrong-answer options are other known words'
  furigana, picked at random from the whole word table (not sentence-scoped, not level-matched).
  A word with no furigana at all (shouldn't normally happen for a tracked kind-1 word, but handled
  defensively) is skipped from the quiz and passed through as correct, since there's no reading to
  test.

- **Quiz retry queue**: the quiz only runs once per "Learned" press - `pendingQuiz` always clears
  after that one round, whether the answers were right or wrong. A round with any wrong answers
  sends the card back to *normal* front/back review at the hard queue (not straight into another
  quiz round); marking it Learned again later re-quizzes only the words still remaining. An
  all-correct round marks the sentence `learned` and `quizSucceeded` and it drops out of review
  entirely.

- **Bulk import JSON shape** (`data/importer/StructuredImport.kt`): an array of
  `{ text?, translation, structure }` objects, `structure` matching the README's schema
  verbatim, `text` optional (derived from `structure` if omitted).

- **Import from file** (`ui/importsentences/`): bulk import (structured JSON or a plain-text book)
  is file-only - a system file picker (`ActivityResultContracts.OpenDocument`, accepting any file
  type since not every picker tags `.json`/`.txt` files with a matching MIME type) that reads the
  file directly, since these are expected to reach the megabytes - too large to comfortably paste,
  and too large to dump into an editable text field besides (Compose's text field gets sluggish
  with very large editable content). An earlier paste-a-small-JSON-directly box was removed per
  your note that it was redundant now that file import covers every bulk case; the single-sentence
  text box added later (below) is a deliberately different feature, not a re-add of that one - it
  exists to hand-pick one sentence's main words, not to bulk-import.

- **Plain-text book import** (`data/importer/BookImporter.kt`, `BookText.kt`): the in-app
  equivalent of `tools/import_book.py`, for when running that script isn't an option. Decisions
  that don't have a python-script counterpart to copy from:
  - **Tokenizer**: Kuromoji's IPADIC tokenizer (`com.atilika.kuromoji:kuromoji-ipadic`), not
    fugashi/UniDic - it's pure Kotlin/Java (no native code, no network access, works entirely
    on-device) at the cost of segmenting slightly differently than the script in edge cases, since
    IPADIC and UniDic are different dictionaries. Its per-token `surface`/`baseForm`/`reading` map
    directly onto the script's fugashi `surface`/`orthBase`/`kana`. The dictionary ships as ~28MB
    of resources bundled inside the kuromoji jars (loaded via classpath resource lookup, not
    Android assets) - a real APK size cost, accepted as the price of not needing Python at all.
  - **Word id assignment**: kept deliberately simple and consistent with how the structured-JSON
    path already behaves (`SentenceImporter.importSentences`) - a word id is only ever reused
    *within one import run* (same dictionary-form token seen again in the same book), via an
    in-memory map, exactly like the script's `stable_word_id` hash trick achieves for its own
    single run. This does *not* check the DB for a pre-existing word with the same text, so
    importing two books (or a book and previously-imported JSON) with overlapping vocabulary
    creates a separate `WordEntity` per import for words not already id-linked - matching the
    existing (JSON) import's behavior rather than introducing new cross-import dedup logic.
  - **Dictionary glosses**: reuses the bundled JMdict `DictionaryRepository` used by the Dictionary
    tab, first-gloss-of-first-sense extraction ported verbatim from the script's parsing. Simpler
    than the script's lookup: it doesn't specifically try kanji+kana together first, so distinct
    homographs sharing a kanji spelling (e.g. 本 "book" vs. "origin") can occasionally get the
    wrong gloss - same class of limitation as the script, just via a slightly less precise query.
  - **Whole-file-in-memory**: unlike `importStream`'s element-by-element JSON streaming (built for
    pre-structured datasets that can run into the hundreds of MB), book import reads the entire
    input file into memory up front. Plain-text book files are orders of magnitude smaller than
    their structured-JSON output, so this is fine for a single book; concatenating many books into
    one giant text file should still go through the script + JSON import instead.
  - **No exposed CLI-equivalent knobs**: `BookImportOptions` mirrors the script's filter defaults
    (min 2 chars, min 50% Japanese-script ratio, dedupe exact-duplicate sentences, split multi-
    sentence quotes) but the UI doesn't expose them - no settings screen exists yet (see README's
    "Not yet done"), so tune `BookImportOptions`'s defaults directly if they need to change.
  - Tokenization/dictionary-lookup logic is factored out behind a `SentenceTokenizer` interface
    (`JapaneseTokenizer` is the real Kuromoji-backed implementation) specifically so
    `SingleSentenceImporter` below could reuse the exact same tokenizing step - and so both could be
    unit-tested with a fake in place of `JapaneseTokenizer`, which needs a real Android `Context`
    (via `DictionaryRepository`) and so can't be constructed in a plain JVM test.

- **Single-sentence import** (`data/cards/SingleSentenceImporter.kt`,
  `ui/importsentences/SingleSentenceCardView.kt`): type one sentence directly, pick which of its
  words this card should teach, done - per your spec. Decisions this needed that book import
  didn't:
  - **"Is this really one sentence?"** reuses `BookImporter`'s exact splitting rule
    (`checkSingleSentence`/`splitIntoCleanSentences` in `BookText.kt`) rather than a separate,
    looser check - so what counts as "one sentence" is consistent between the two features, and a
    quote packing more than one sentence is correctly rejected here too, not just merged away.
  - **Word id resolution does check the DB** (`wordDao.findByWord(dictForm)`), unlike book import -
    worth the extra per-word lookups for a single sentence, and specifically to avoid fragmenting
    an already-tracked word's progress into a second `WordEntity` right at the moment the user is
    deliberately hand-picking main words (same convention as
    `WordRepository.addFromDictionary`'s reuse-by-exact-text-match). Matched by dictionary form,
    not surface, for the same reason a brand-new `WordEntity`'s `word` is seeded from
    `SentenceToken.dictForm` rather than the inflected surface (see `data/db/SentenceToken.kt`) -
    otherwise "食べた" wouldn't reuse an already-tracked "食べる".
  - **Translation happens at parse time**, not at card-creation time: the review view is specified
    to look like a normal card back (translation included), and nothing about the sentence changes
    between parsing and the user pressing Import, so there's no reason to translate twice. This
    does mean Cancel doesn't get that translation API call back - accepted as the cost of a
    deliberate, one-sentence-at-a-time flow rather than a bulk one.
  - **Separate, simpler view, not a `FlashCardView` variant** (per your explicit direction): always
    shows translation+furigana (no front/flip), a plain tap toggles a word between "not picked"
    (green) and "picked as main word" (blue) instead of opening the 4-direction flick menu, and the
    bottom row is Import/Cancel (Import disabled until at least one word is picked) instead of
    Hard/Medium/Easy/Learned.
  - **`SentenceImporter.importOne`**: a fourth entry point alongside `importJson`/`importStream`/
    `importParsed`, needed because this feature must act on the just-written `SentenceEntity`
    immediately (to build the `CardEntity` from it) - unlike the other three, which only ever
    report back a summary count.

- **Sentences vs. cards split** (`data/db/SentenceEntity.kt`, `CardEntity.kt`, `SentenceWordCrossRef.kt`,
  `data/cards/`): per your clarification, `sentences` is just the raw imported pool (can be
  enormous - a whole book) and is never itself reviewable. `cards` is a separate table holding the
  actual review entities (queue state, `mainWordIds`, quiz state, etc. - what `SentenceEntity` used
  to be), each pointing back at the sentence it was generated from via `sentenceId`. A card copies
  its text/translation/structure from the sentence at creation time rather than joining live, so a
  card's wording can't shift under a user mid-review. Consequently:
  - Import only writes to `sentences` (plus the `sentence_words` cross-reference table used to find
    "sentences containing word X" without scanning/deserializing every row) - it no longer creates
    any cards, and a freshly imported word's `toLearn` reverts to the README's literal default
    (`false`), since importing no longer means "this sentence exists to teach this word".
  - Cards are only ever created by `CardGenerator`, triggered from every place a word gets marked
    to-learn (`WordRepository.markToLearn`, `.setToLearn(id, true)`, `.addFromDictionary(...,
    TO_LEARN)`) - never by import.
  - A freshly generated card's `mainWordIds` is just `[wordId]` - the one word it was picked for -
    since the scoring formula already discourages candidates containing other to-learn words.

- **Card reuse, not just dedup** (`data/cards/CardGenerator.kt`): per your follow-up, "up to 3
  cards" means at most 3 total covering the word, not 3 new ones piled on top of whatever's already
  there. Before generating anything, `CardGenerator` looks for existing cards whose sentence
  already contains the word (via the same `sentence_words` index) - each one just gets the word
  added to its `mainWordIds`, reusing the card instead of spawning a duplicate for the same
  sentence; a card that had already fully graduated (`learned = true`) is reactivated (`learned`,
  `quizSucceeded`, `pendingQuiz` cleared, `queueLevel` bumped to `HIGHEST`) so the newly added word
  actually surfaces for review/quiz instead of being silently stuck on a card that's dropped out of
  the rotation. Only the shortfall (`3 - reusedCount`) is filled with brand-new cards from the
  remaining not-yet-carded candidates, scored as before - so a sentence never backs two cards, and
  re-marking a word to-learn (e.g. toggling it off and back on) tops up any remaining shortfall
  from the next-best batch instead of duplicating what's already there.

- **Word ids on import**: if a structure token omits `id` for a `kind: 1` (word) token, one is
  assigned (`max known id + 1`). If a `kind: 1` token's `id` already exists in the database, the
  existing word is reused rather than duplicated - this is how the same kanji recurring across
  sentences is meant to converge on one row.

- **Dictionary tab and "add as..."** (`ui/dictionary/`, `WordRepository.addFromDictionary`): the
  Dictionary tab browses the whole bundled JMdict data (prefix match on any kanji/kana spelling,
  plus a substring match on meanings - only run on explicit search, not per keystroke, since the
  meaning match is an unindexed scan over ~200k rows). Adding a result as known/to-learn/force-
  furigana upserts a row in the *internal* words table, matched by exact `word` text (kanji, or
  kana if the entry has no kanji) - adding the same word twice updates it in place rather than
  creating a duplicate. The new word's `dictionaryEntryId` is set to exactly the entry the user
  picked (see "WordEntity references the dictionary" below), and it gets a freshly assigned id the
  same way importer-created words do (`max known id + 1`).

- **WordEntity references the dictionary instead of duplicating it** (`data/db/WordEntity.kt`,
  `data/dictionary/DictionaryRepository.kt`): per your follow-up, `WordEntity` no longer carries its
  own `furigana`/`translation` text - it carries `dictionaryEntryId: Long?`, a reference to
  `dict_entries.id` in the bundled dictionary (JMdict's own stable `ent_seq`, not a build-time
  autoincrement - see `tools/build_dictionary.py` - so it stays valid across a rebuilt/updated
  `jmdict.db`). Reading and meaning are looked up through that reference wherever they're actually
  displayed (`DictionaryRepository.getById`/`getByIds`), rather than duplicated on every word - and
  as a side effect this fixes a related bug: the old `furigana` text was seeded from whichever
  sentence occurrence happened to introduce the word (e.g. "たべた", an *inflected* reading), not
  the word's actual dictionary/citation-form reading ("たべる"); a dictionary reference is always
  the citation-form reading, so the quiz now tests the right thing.
  - `SentenceToken.translation` is gone entirely (not just moved) - it had zero UI consumers even
    before this change (verified by grep - every visible per-word/per-sentence translation reads
    `CardEntity.translation` or `WordEntity.translation`, never a token's own field); it existed only
    to seed `WordEntity.translation` at import time, which no longer exists. `SentenceToken.furigana`
    stays, though - it's this specific occurrence's actual (possibly inflected) reading, needed to
    render ruby text correctly, which is a fundamentally different thing from a dictionary entry's
    citation-form reading and can't be recovered from one (見た's reading isn't 見る's).
  - `SentenceToken` gained `dictionaryEntryId: Long?` alongside the existing `dictForm: String?`
    (from the earlier dictionary-form fix) - same seed-only role: only meaningful the first time a
    WORD token's `id` is minted, ignored on every later token referencing an already-tracked id.
    Following from removing `translation`, katakana/particle tokens no longer do a dictionary lookup
    or hand-picked-table lookup at all during tokenize (`tools/import_book.py`'s `PARTICLE_GLOSSES`
    and the app's mirror of it are removed) - neither kind is ever tracked as a `WordEntity` (only
    `kind: 1` tokens get an `id`), so there was nothing left to seed.
  - The reading-quiz's distractor pool (`data/quiz/QuizOptions.kt`) used to scan `WordEntity.furigana`
    across every tracked word in memory; `readingOptions` now takes plain reading strings instead of
    `WordEntity`, and `ReviewViewModel` resolves the whole tracked-word list's readings in one batched
    `getByIds` call (`resolveReadings`) whenever the word list changes, rather than one dictionary
    lookup per word. The word-menu's furigana display (`FlickMenu`) is unaffected by any of this - it
    already gets the tapped *token's* reading (not a WordEntity/dictionary lookup) via
    `FlashCardView`'s flick-gesture state, which is the contextually-correct one anyway.
  - The Word Browser (`ui/words/WordBrowserViewModel.kt`) similarly batches a `getByIds` call across
    the whole tracked-word list on every emission (small table, unlike the Dictionary tab's 200k-row
    scan) and exposes `WordRow(word, dictionaryEntry)` pairs rather than raw `WordEntity`.
  - The word menu's "Up" dictionary lookup (`ReviewViewModel.lookupDictionary`) now fetches the exact
    entry via `dictionaryEntryId` when one was resolved, instead of re-searching by kanji+kana (which
    could land on a different homograph than the one actually tracked); falls back to a fresh
    kanji-only search only when nothing was resolved at tracking time.
  - **Data loss accepted, not migrated**: per your explicit call, this schema change goes through
    `AppDatabase`'s existing `fallbackToDestructiveMigration` like every prior schema change in this
    project - no migration was written to carry forward already-tracked words' progress.

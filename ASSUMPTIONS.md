# Assumptions made while implementing the UI/data pass

The README leaves a number of behaviors implicit or explicitly open (e.g. the knowledge-level
TODO). Here's what this pass assumed, and where in the code to change it if the real answer is
different. Nothing here is set in stone - it's meant to be a clear, searchable list of decisions
rather than silent guessing.

- **Knowledge level formula** (`data/knowledge/KnowledgeLevel.kt`): the README explicitly leaves
  this as a TODO. Implemented a simple placeholder score from the existing metrics, clearly
  marked - swap the formula whenever a real one is defined; nothing else depends on it.

- **New word defaults** (`data/importer/StructuredImport.kt`): a brand-new kanji/word from import
  gets `forceFurigana = true` (shows furigana until confirmed known - reconciling "furigana shown
  by default for any new kanji/word" from the README's metrics section with "front only shows
  furigana for force-furigana words" from its UI section) and `toLearn = false`, the README's
  literal default. An earlier version of this note had `toLearn = true` here, reasoning that every
  imported word was automatically one of its sentence's main (to-learn) words - that's no longer
  true now that import only populates the raw sentence pool and never creates cards directly (see
  "Sentences vs. cards split" below), so the literal README default applies again.

- **4-direction menu effects** (`data/repository/WordRepository.kt`): right (know) clears both
  `toLearn` and `forceFurigana`; left (learn) sets both `toLearn` and `forceFurigana`; down hides
  furigana; up looks the word up in the bundled dictionary (see `data/dictionary/`) - the README
  marks dictionary as a TODO, but it's since been fully implemented, offline. A correct quiz
  answer applies the same `toLearn`/`forceFurigana` clear as the right-flick "known" action -
  otherwise a word that graduates out of a sentence's main word list keeps showing front-side
  furigana forever (nothing else ever clears `forceFurigana` once it stops being a main word).

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
  verbatim, `text` optional (derived from `structure` if omitted). Only the "already structured"
  import path exists yet, per your note that plain-text import needs the adapted parsing script
  later.

- **Import from file** (`ui/importsentences/`): the primary import path is now a system file
  picker (`ActivityResultContracts.OpenDocument`, accepting any file type since not every picker
  tags `.json` files with a matching MIME type) that reads the file directly, since structured
  JSON files are expected to reach the megabytes - too large to comfortably paste, and too large
  to dump into an editable text field besides (Compose's text field gets sluggish with very large
  editable content). The paste box is kept as a secondary path for quick/small manual tests.

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
  - A sentence that already backs a card is excluded from future candidate pools (any word's), so
    the same sentence never backs two cards, and re-marking a word to-learn (e.g. toggling it off
    and back on) naturally surfaces the *next*-best batch instead of duplicating the first one.
  - A freshly generated card's `mainWordIds` is just `[wordId]` - the one word it was picked for -
    since the scoring formula already discourages candidates containing other to-learn words.

- **Word ids on import**: if a structure token omits `id` for a `kind: 1` (word) token, one is
  assigned (`max known id + 1`). If a `kind: 1` token's `id` already exists in the database, the
  existing word is reused rather than duplicated - this is how the same kanji recurring across
  sentences is meant to converge on one row.

- **Dictionary tab and "add as..."** (`ui/dictionary/`, `WordRepository.addFromDictionary`): the
  Dictionary tab browses the whole bundled JMdict data (prefix match on any kanji/kana spelling,
  plus a substring match on meanings - only run on explicit search, not per keystroke, since the
  meaning match is an unindexed scan over ~200k rows). Adding a result as known/to-learn/hide-
  furigana upserts a row in the *internal* words table, matched by exact `word` text (kanji, or
  kana if the entry has no kanji) - adding the same word twice updates it in place rather than
  creating a duplicate. The new word's `translation` is the dictionary's full multi-sense summary
  (not trimmed to one line, unlike sentence-import translations), and it gets a freshly assigned
  id the same way importer-created words do (`max known id + 1`).

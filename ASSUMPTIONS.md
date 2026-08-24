# Assumptions made while implementing the UI/data pass

The README leaves a number of behaviors implicit or explicitly open (e.g. the knowledge-level
TODO). Here's what this pass assumed, and where in the code to change it if the real answer is
different. Nothing here is set in stone - it's meant to be a clear, searchable list of decisions
rather than silent guessing.

- **Knowledge level formula** (`data/knowledge/KnowledgeLevel.kt`): the README explicitly leaves
  this as a TODO. Implemented a simple placeholder score from the existing metrics, clearly
  marked - swap the formula whenever a real one is defined; nothing else depends on it.

- **New word defaults** (`data/importer/StructuredImport.kt`): a brand-new kanji/word from import
  gets `forceFurigana = true`, `toLearn = false`. This is how "furigana shown by default for any
  new kanji/word not present in database" (README metrics section) is reconciled with "front only
  shows furigana for force-furigana words" (README UI section) - new words are force-furigana by
  default, which fades once you mark them known.

- **4-direction menu effects** (`data/repository/WordRepository.kt`): right (know) clears both
  `toLearn` and `forceFurigana`; left (learn) sets both `toLearn` and `forceFurigana`; down hides
  furigana; up is a dictionary stub (README marks dictionary as TODO too). A correct quiz answer
  applies the same `toLearn`/`forceFurigana` clear as the right-flick "known" action - otherwise a
  word that graduates out of a sentence's main word list keeps showing front-side furigana forever
  (nothing else ever clears `forceFurigana` once it stops being a main word).

- **Per-word translation tooltip vs. the 4-direction menu**: the README mentions both a "times
  translation shown" metric requiring "a special button" and a directional menu, but only
  describes the menu's four directions (know/learn/hide/dictionary) - no "show translation"
  direction. Implemented as: short tap on a back-of-card word shows its translation and counts
  towards that metric; long-press opens the 4-direction menu. Two different gestures on the same
  word, since the README's own four directions don't have room for a fifth action.

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

- **Word ids on import**: if a structure token omits `id` for a `kind: 1` (word) token, one is
  assigned (`max known id + 1`). If a `kind: 1` token's `id` already exists in the database, the
  existing word is reused rather than duplicated - this is how the same kanji recurring across
  sentences is meant to converge on one row.

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
  furigana; up is a dictionary stub (README marks dictionary as TODO too).

- **Per-word translation tooltip vs. the 4-direction menu**: the README mentions both a "times
  translation shown" metric requiring "a special button" and a directional menu, but only
  describes the menu's four directions (know/learn/hide/dictionary) - no "show translation"
  direction. Implemented as: short tap on a back-of-card word shows its translation and counts
  towards that metric; long-press opens the 4-direction menu. Two different gestures on the same
  word, since the README's own four directions don't have room for a fifth action.

- **Word menu interaction**: implemented as tap-to-choose-a-direction inside a popup rather than a
  literal drag gesture, since it's functionally equivalent and considerably simpler to build and
  test than gesture recognition. See `ui/review/WordDirectionalMenu.kt`.

- **New sentence starting queue**: new sentences default to `QueueLevel.HIGHEST` (nothing in the
  README says where brand-new material starts; highest surfaces it soonest).

- **"Marked the same level twice in a row" demotion** (`data/queue/ReviewGrading.kt`): tracked
  per sentence via `lastMarkedLevel`, compared against the *button pressed* (not the resulting
  queue), and demotes exactly one level per repeat (floor at Backlog). Two Hard-in-a-row demotes
  to Medium, not further, unless Hard is pressed a third time in a row.

- **Quiz retry order** (`gradeQuizWord`): an incorrect word cycles to the back of the remaining
  list instead of ending the quiz card - keeps the quiz moving through all main words instead of
  looping on one word.

- **Bulk import JSON shape** (`data/importer/StructuredImport.kt`): an array of
  `{ text?, translation, structure }` objects, `structure` matching the README's schema
  verbatim, `text` optional (derived from `structure` if omitted). Only the "already structured"
  import path exists yet, per your note that plain-text import needs the adapted parsing script
  later.

- **Word ids on import**: if a structure token omits `id` for a `kind: 1` (word) token, one is
  assigned (`max known id + 1`). If a `kind: 1` token's `id` already exists in the database, the
  existing word is reused rather than duplicated - this is how the same kanji recurring across
  sentences is meant to converge on one row.

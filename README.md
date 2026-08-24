# Japanese Sentences Flash Cards

## Main features

### Maintain list of known kanji/words

App should have small local database where it stores information about learned kanji and words.

There should be stored few metrics:
1. Times shown - increments each time flash card with this word or kanji is shown. 
2. Times furigana shown - furigana is shown by default for any new kanji/word not present in database, or if user requests specifically to show it.
3. Times translation shown - user would need to press special button to see word translation.
4. To-learn marker - true or false, user defines if they want to learn this kanji or word. New words are considered false.
5. Hide furigana - true or false, user can request to hide furigana for the word or kanji. Doesn't mean it is learned, but strong marker that it is well known.
6. Quiz success - how many times user answered "sentence learned quiz" correctly.
7. Quiz fails - how many times user answered "sentence learned quiz" wrongly.
8. Force furigana - should show furigana even on the front side of the flash card.

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

Pressing on the card itself should flip it to show translation and furigana for all the words, except with hidden furigana.

Back side of cards is not flipped back on click. Instead there are extra actions available.

Pressing on word should open 4 directions menu:
1. Right - mark word as known
2. Left - mark word for learning
3. Down - hide furigana
4. Up - open dictionary (TODO)

## Sentences source

In general sentence can be imported in two variants - plain text or already structured.

I have python script for making similar kind of flash cards for anki.

### Adding word to learn

All sentences in the database should be calculated how many unknown words this sentence have, and app should choose the one that have the highest amount of known words (in percentage).
Best case scenario, only word to learn is the unknown one.

# Notes

I also think to define base level, for example all words of level N4 or N5 should be marked as known by default.
Yeah, I know that there is no clear separation for JLPT, but there are databases that can provide this separation approximately.  
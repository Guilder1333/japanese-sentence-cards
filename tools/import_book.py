#!/usr/bin/env python3
"""
Turns plain-text Japanese book(s) into the structured sentence-import JSON the app's "Import
sentences" screen accepts (see README.md's "Sentence structure" section and
data/importer/StructuredImport.kt's ImportSentence/SentenceToken schema).

Adapted from the sentence-picking logic in the japanese-flashcards-generator project's
book_grabber.py / word_tokenizer.py / datasets.py, but reworked to:
  - walk *every* sentence in the book(s) instead of just ones containing specific target words,
  - skip machine translation entirely (both per-sentence and, unless you pass --no-dictionary, the
    free local JMdict lookup used only to resolve each new word's dictionary entry id, not to
    generate any gloss text) - full-sentence MT via DeepL/GPT is what actually costs money on a
    whole book, so it's left for a later pass; the output's per-sentence "translation" is always "",
  - take a book file or a directory of book files as input.

Setup:
    pip install fugashi unidic-lite

Usage:
    python tools/import_book.py path/to/book.txt
    python tools/import_book.py path/to/books_dir/ -o dataset.json
    python tools/import_book.py path/to/books_dir/ --recursive --pattern "*.txt"

Output is a JSON array of ImportSentence objects, ready to paste/load into the Import screen.
"""

import argparse
import hashlib
import os
import sqlite3
import sys
import unicodedata
from typing import Dict, Iterable, Iterator, List, Optional, Tuple

try:
    import fugashi
except ImportError:
    sys.exit("Missing dependency: run `pip install fugashi unidic-lite` first.")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DICT_DB = os.path.join(SCRIPT_DIR, "..", "app", "src", "main", "assets", "dictionary", "jmdict.db")

# TokenKind codes, matching app/src/main/java/.../data/db/TokenKind.kt
KIND_WORD = 1
KIND_PARTICLE = 2
KIND_KATAKANA = 3
KIND_HIRAGANA = 4

# UniDic part-of-speech level 1 values that are pure grammar rather than words:
# 助詞 = particle, 助動詞 = auxiliary verb/copula (です, た, ない), 補助記号/記号 = punctuation and
# symbols, 空白 = whitespace. Anything tagged with one of these is a particle whatever its script.
GRAMMAR_POS1 = {"助詞", "助動詞", "補助記号", "記号", "空白"}
# Level-1/level-2 values marking a token that only exists to attach to another word: 接尾辞 =
# suffix (さん, たち, 的), 非自立可能 = "can be dependent", UniDic's tag for the helper verbs of
# ～ている / ～てみる / ～てください and the こと of ～ということ. These carry no meaning on their
# own, so they belong with the grammar even though 非自立可能's level-1 tag says 動詞/名詞/形容詞.
GRAMMAR_POS1_SUFFIX = "接尾辞"
DEPENDENT_POS2 = "非自立可能"

SENTENCE_END_CHARS = set("。.！!？?")
# Half-width corner brackets (｢｣) show up in some digitized/OCR'd books mixed in with the normal
# full-width ones (e.g. a "「...｣" pair) - include both so a stray half-width one doesn't go
# unrecognized as a close and leave the quote looking permanently "open" (see MAX_UNCLOSED_QUOTE
# below for what happens if that still occurs, e.g. from some other bracket variant).
OPEN_BRACKETS = set("「『｢")
CLOSE_BRACKETS = set("」』｣")
# Safety valve: if a bracket never gets matched (unrecognized quote variant, stray/mismatched
# character, etc.), sentence-end tracking would otherwise stay suppressed for the rest of the
# file, silently swallowing every remaining sentence into one giant blob. Once an unmatched quote
# has run on this long, give up on depth-tracking for it and go back to splitting on plain
# terminators.
MAX_UNCLOSED_QUOTE_CHARS = 500


def kata_to_hira(text: str) -> str:
    """Converts katakana to hiragana; anything else passes through unchanged."""
    out = []
    for ch in text:
        code = ord(ch)
        if 0x30A1 <= code <= 0x30F6:
            out.append(chr(code - 0x60))
        else:
            out.append(ch)
    return "".join(out)


def _char_category(ch: str) -> str:
    if ch == "ー":
        return "either"
    try:
        name = unicodedata.name(ch)
    except ValueError:
        return "other"
    if name.startswith("HIRAGANA"):
        return "hira"
    if name.startswith("KATAKANA"):
        return "kata"
    if name.startswith("CJK UNIFIED IDEOGRAPH") or name.startswith("CJK COMPATIBILITY IDEOGRAPH"):
        return "kanji"
    return "other"


def classify_token(surface: str, pos1: Optional[str] = None, pos2: Optional[str] = None) -> int:
    """
    Maps a token to a TokenKind code (see TokenKind.kt for the definitions).

    `pos1`/`pos2` are the tokenizer's part-of-speech levels 1 and 2 (fugashi/UniDic:
    `tok.feature.pos1`/`.pos2`; "*" and None both mean "not known"). They're what separates a
    kana-written *word* from a particle - script alone cannot, since わかる and が are both
    hiragana-only. With no part-of-speech to go on, every kana token falls back to KIND_PARTICLE,
    which is the old, script-only behaviour.

    Kanji and katakana are still decided by script alone, before part-of-speech is even consulted:
    a kanji-containing token stays a tracked KIND_WORD even when it's a dependent suffix (的, 性),
    and a katakana loanword stays KIND_KATAKANA even when it's a filler.

    Mirrors classifyToken in the app's data/importer/BookText.kt, which runs the same rule over
    Kuromoji IPADIC's tag set - the tag *names* differ between the two dictionaries (IPADIC says
    記号/非自立 where UniDic says 補助記号/非自立可能), the rule doesn't.
    """
    categories = {_char_category(c) for c in surface}
    categories.discard("either")
    if "kanji" in categories:
        return KIND_WORD
    if categories == {"kata"}:
        return KIND_KATAKANA
    if pos1 in GRAMMAR_POS1 or pos1 == GRAMMAR_POS1_SUFFIX or pos2 == DEPENDENT_POS2:
        return KIND_PARTICLE
    # A content word (verb/adjective/adverb/noun/pronoun) that happens to be written in kana.
    if "hira" in categories and pos1 and pos1 != "*":
        return KIND_HIRAGANA
    # Digits, latin, punctuation, and - with no part-of-speech to go on - any kana token.
    return KIND_PARTICLE


def stable_word_id(key: str) -> int:
    """
    Deterministic id for a dictionary-form word, stable across separate runs of this script (and
    across different books). This lets the same word reuse the same id everywhere - required so
    the app's importer (data/importer/StructuredImport.kt) recognizes repeats of a word as the
    *same* tracked word instead of creating a duplicate WordEntity per book - and, unlike a plain
    per-run counter, it also won't collide with ids from a previous import batch.

    Kept inside signed 63 bits so it fits a Kotlin Long (2^63-1 max) unambiguously.
    """
    digest = hashlib.sha256(key.encode("utf-8")).digest()
    return int.from_bytes(digest[:8], "big") & 0x7FFFFFFFFFFFFFFF


def split_sentences(text: str) -> Iterator[str]:
    """
    Splits raw book text into sentences at 。.！!？?, tracking 「」/『』 quote depth so a
    terminator *inside* a quote (e.g. 彼は「ああ、そうだ。」と言った。) doesn't cut the
    sentence short - the quote is just part of the sentence it's embedded in.

    OCR'd/digitized books sometimes drop a line's closing quote mark entirely (see
    tools/example.txt), which would otherwise leave depth stuck open and swallow every terminator
    for the rest of the book (MAX_UNCLOSED_QUOTE_CHARS guards the worst case of that, but by then
    several real sentences have already been merged together). As a targeted fix: if a line ends
    with depth still open and the *next* non-blank line opens another bracket, assume the missing
    close happened right there at the line boundary - a new quote starting strongly implies the
    previous one ended.
    """
    lines = text.split("\n")
    buf = []
    depth = 0
    unclosed_start = 0
    for i, line in enumerate(lines):
        if i > 0:
            buf.append("\n")
        for ch in line:
            buf.append(ch)
            if ch in OPEN_BRACKETS:
                if depth == 0:
                    unclosed_start = len(buf) - 1
                depth += 1
            elif ch in CLOSE_BRACKETS:
                depth = max(0, depth - 1)
            if depth > 0 and len(buf) - unclosed_start > MAX_UNCLOSED_QUOTE_CHARS:
                depth = 0
            if depth == 0 and ch in SENTENCE_END_CHARS:
                yield "".join(buf)
                buf = []

        if depth > 0:
            next_line = next((ln for ln in lines[i + 1:] if ln.strip()), "")
            if next_line[:1] in OPEN_BRACKETS:
                depth -= 1
    if buf:
        yield "".join(buf)


def _split_depth_zero(text: str) -> List[str]:
    """
    Splits text into chunks at 。.！!？?, tracking bracket depth the same way split_sentences does
    (so a terminator inside a nested quote doesn't count). Used to count/extract the individual
    sentences packed inside a single 「」/『』 quote span - see split_long_quotes.
    """
    parts = []
    buf = []
    depth = 0
    for ch in text:
        buf.append(ch)
        if ch in OPEN_BRACKETS:
            depth += 1
        elif ch in CLOSE_BRACKETS:
            depth = max(0, depth - 1)
        if depth == 0 and ch in SENTENCE_END_CHARS:
            parts.append("".join(buf))
            buf = []
    if buf:
        parts.append("".join(buf))
    return parts


def extract_top_level_quotes(text: str) -> List[str]:
    """
    Returns the contents (brackets excluded) of each top-level 「」/『』 quote span in text.

    A quote that never closes within `text` (its closing bracket got OCR'd/digitized away
    entirely - the MAX_UNCLOSED_QUOTE_CHARS case in split_sentences, see tools/example.txt) is
    still included, running from its opening bracket to the end of text: split_sentences already
    gave up on depth-tracking for it and yielded whatever piled up as one raw sentence, so without
    this it silently keeps looking like unquoted narration and split_long_quotes never gets a
    chance to break it up.
    """
    spans = []
    depth = 0
    start = None
    for i, ch in enumerate(text):
        if ch in OPEN_BRACKETS:
            if depth == 0:
                start = i + 1
            depth += 1
        elif ch in CLOSE_BRACKETS:
            depth = max(0, depth - 1)
            if depth == 0 and start is not None:
                spans.append(text[start:i])
                start = None
    if depth > 0 and start is not None:
        spans.append(text[start:])
    return spans


def split_long_quotes(raw: str) -> List[str]:
    """
    A quote that runs on for several sentences (a whole stretch of dialogue packed into one
    「」) makes for a needlessly long, unfocused flashcard once it's merged with its surrounding
    narration by split_sentences. So: count how many sentences are packed inside `raw`'s top-level
    quote(s), and if that's more than one, break each quote's contents into its own per-sentence
    pieces and drop the outer sentence (narration and all) in favor of those. Returns `[raw]`
    unchanged when there's nothing to split (no quotes, or every quote holds only one sentence).
    """
    quote_sentences = [s for quote in extract_top_level_quotes(raw) for s in _split_depth_zero(quote)]
    if len(quote_sentences) > 1:
        return quote_sentences
    return [raw]


def clean_sentence(raw: str) -> str:
    # Plain-text books commonly wrap lines and/or use full-width spaces for ruby/indentation -
    # none of that is meaningful in a single flash-card sentence, so strip *all* whitespace
    # (not just leading/trailing), not only newlines.
    return "".join(ch for ch in raw if not ch.isspace())


def japanese_ratio(text: str) -> float:
    """
    Fraction of the sentence's letters (kanji/kana/Latin/etc, via str.isalpha - punctuation and
    digits don't count either way) that are Japanese script (kanji, hiragana, katakana, or the
    ー prolonged-sound mark). Sentences with no letters at all return 1.0 (nothing to judge, so
    the ratio filter doesn't apply to them).
    """
    letters = [ch for ch in text if ch.isalpha()]
    if not letters:
        return 1.0
    japanese = sum(1 for ch in letters if _char_category(ch) in ("hira", "kata", "kanji", "either"))
    return japanese / len(letters)


class Dictionary:
    """Read-only lookup into the same jmdict.db the app bundles (tools/build_dictionary.py)."""

    def __init__(self, db_path: Optional[str]):
        self._conn = None
        self._cache: Dict[Tuple[Optional[str], Optional[str]], Optional[int]] = {}
        if db_path and os.path.exists(db_path):
            self._conn = sqlite3.connect(db_path)

    @property
    def available(self) -> bool:
        return self._conn is not None

    def entry_id(self, kanji: Optional[str], kana: Optional[str]) -> Optional[int]:
        """The matching dict_entries.id (JMdict's stable ent_seq), or None if nothing matched."""
        if self._conn is None:
            return None
        cache_key = (kanji, kana)
        if cache_key in self._cache:
            return self._cache[cache_key]

        # Try kanji+kana together first: JMdict often has several entries sharing the exact same
        # kanji spelling but different readings/meanings (e.g. 本 = "book" (ほん) vs. "origin"
        # (もと)), so a kanji-only match can silently pick the wrong homograph. Fall back to
        # whichever of kanji/kana we have if a combined match isn't found.
        row = None
        if kanji and kana:
            row = self._conn.execute(
                "SELECT e.id FROM dict_entries e "
                "JOIN dict_kanji_index k ON k.entry_id = e.id "
                "JOIN dict_kana_index r ON r.entry_id = e.id "
                "WHERE k.text = ? AND r.text = ? LIMIT 1",
                (kanji, kana),
            ).fetchone()
        if row is None and kanji:
            row = self._conn.execute(
                "SELECT e.id FROM dict_entries e JOIN dict_kanji_index i ON i.entry_id = e.id "
                "WHERE i.text = ? LIMIT 1",
                (kanji,),
            ).fetchone()
        if row is None and kana:
            row = self._conn.execute(
                "SELECT e.id FROM dict_entries e JOIN dict_kana_index i ON i.entry_id = e.id "
                "WHERE i.text = ? LIMIT 1",
                (kana,),
            ).fetchone()

        result = int(row[0]) if row is not None else None
        self._cache[cache_key] = result
        return result


class WordIdCache:
    """Assigns/reuses a stable id per dictionary-form word within this run (see stable_word_id)."""

    def __init__(self):
        self._ids: Dict[str, int] = {}

    def get(self, dict_form: str) -> int:
        if dict_form not in self._ids:
            self._ids[dict_form] = stable_word_id(dict_form)
        return self._ids[dict_form]


def build_structure(tagger, sentence: str, dictionary: Dictionary, word_ids: WordIdCache) -> List[dict]:
    tokens = []
    for tok in tagger(sentence):
        surface = tok.surface
        kind = classify_token(surface, getattr(tok.feature, "pos1", None), getattr(tok.feature, "pos2", None))
        dict_form = getattr(tok.feature, "orthBase", None) or surface
        reading = getattr(tok.feature, "kana", None)

        entry: dict = {"word": surface, "kind": kind}

        if kind == KIND_WORD:
            entry["id"] = word_ids.get(dict_form)
            # Kept distinct from "word" (this token's inflected surface, needed to render the
            # sentence as written) so the app can seed a brand-new tracked WordEntity's text with
            # the dictionary form - same form stable_word_id hashed above - instead of whichever
            # inflection happened to be the first sentence to introduce this id.
            if dict_form != surface:
                entry["dictForm"] = dict_form
            if reading:
                entry["furigana"] = kata_to_hira(reading)
            # Same seed-only role as dictForm above - the id this becomes WordEntity.dictionaryEntryId
            # for, the first time this word's id is encountered.
            entry_id = dictionary.entry_id(dict_form, kata_to_hira(reading) if reading else None)
            if entry_id is not None:
                entry["dictionaryEntryId"] = entry_id
        elif kind in (KIND_KATAKANA, KIND_HIRAGANA):
            # Kana words are never tracked as WordEntity rows on import (see the app's
            # StructuredImport.kt - only kind: 1 tokens get an id), so there's no dictionary entry
            # id to seed and no lookup to do here. They do carry dictForm, though: the review
            # screen looks a kana word up in the dictionary (and promotes it into the words table)
            # by its base form, and わから is not an entry while わかる is.
            if dict_form != surface:
                entry["dictForm"] = dict_form
        # Particles get nothing beyond word/kind - there's no word there to look up.

        tokens.append(entry)
    return tokens


def collect_book_files(input_path: str, pattern: str, recursive: bool) -> List[str]:
    if os.path.isfile(input_path):
        return [input_path]
    if not os.path.isdir(input_path):
        sys.exit(f"Input not found: {input_path}")

    import fnmatch

    files = []
    if recursive:
        for root, _dirs, names in os.walk(input_path):
            for name in names:
                if fnmatch.fnmatch(name, pattern):
                    files.append(os.path.join(root, name))
    else:
        for name in os.listdir(input_path):
            full = os.path.join(input_path, name)
            if os.path.isfile(full) and fnmatch.fnmatch(name, pattern):
                files.append(full)
    return sorted(files)


def read_text(path: str, encoding: str) -> str:
    encodings_to_try = [encoding] + [e for e in ("utf-8-sig", "utf-8", "shift_jis", "cp932") if e != encoding]
    last_error = None
    for enc in encodings_to_try:
        try:
            with open(path, "r", encoding=enc) as f:
                return f.read()
        except (UnicodeDecodeError, LookupError) as e:
            last_error = e
    raise last_error


def default_output_path(input_path: str) -> str:
    if os.path.isfile(input_path):
        stem, _ext = os.path.splitext(input_path)
        return stem + ".import.json"
    return os.path.join(input_path, "import.json")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("input", help="A book file, or a directory containing book files")
    parser.add_argument("-o", "--output", help="Output JSON path (default: alongside the input)")
    parser.add_argument("--pattern", default="*.txt", help="Glob for picking book files inside a directory (default: *.txt)")
    parser.add_argument("--recursive", action="store_true", help="Recurse into subdirectories when input is a directory")
    parser.add_argument("--encoding", default="utf-8", help="Text encoding to try first (default: utf-8; falls back to utf-8-sig/shift_jis/cp932)")
    parser.add_argument("--dict-db", default=DEFAULT_DICT_DB, help="Path to jmdict.db for resolving each new word's dictionary entry id (default: the app's bundled copy)")
    parser.add_argument("--no-dictionary", action="store_true", help="Skip dictionary lookups entirely; every word's dictionaryEntryId is left unresolved")
    parser.add_argument("--min-chars", type=int, default=2, help="Drop sentences shorter than this many characters (default: 2)")
    parser.add_argument("--min-japanese-ratio", type=float, default=0.5, help="Drop sentences whose letters are less than this fraction kanji/kana (default: 0.5; some books mix in English/French sentences). Set to 0 to disable")
    parser.add_argument("--keep-duplicates", action="store_true", help="Keep exact-duplicate sentences instead of deduplicating (books tend to repeat phrases a lot)")
    parser.add_argument("--keep-long-quotes", action="store_true", help="Don't split multi-sentence 「」/『』 quotes into separate sentences (default: split them and drop the enclosing sentence - see split_long_quotes)")
    parser.add_argument("--limit", type=int, default=None, help="Stop after this many sentences (useful for a quick test run)")
    parser.add_argument("--pretty", action="store_true", help="Pretty-print the output JSON (bigger file, easier to read)")
    args = parser.parse_args()

    files = collect_book_files(args.input, args.pattern, args.recursive)
    if not files:
        sys.exit(f"No files matched under {args.input!r} (pattern={args.pattern!r}, recursive={args.recursive})")
    print(f"Found {len(files)} book file(s)", file=sys.stderr)

    dictionary = Dictionary(None if args.no_dictionary else args.dict_db)
    if not args.no_dictionary and not dictionary.available:
        print(f"Warning: dictionary DB not found at {args.dict_db!r} - dictionaryEntryId will be left unresolved for every word", file=sys.stderr)

    tagger = fugashi.Tagger()
    word_ids = WordIdCache()

    seen_sentences = set()
    results = []
    total_from_files = 0

    for path in files:
        text = read_text(path, args.encoding)
        file_count = 0
        done_with_file = False
        for raw in split_sentences(text):
            candidates = [raw] if args.keep_long_quotes else split_long_quotes(raw)
            for candidate in candidates:
                sentence = clean_sentence(candidate)
                if len(sentence) < args.min_chars:
                    continue
                if japanese_ratio(sentence) < args.min_japanese_ratio:
                    continue
                if not args.keep_duplicates:
                    if sentence in seen_sentences:
                        continue
                    seen_sentences.add(sentence)

                structure = build_structure(tagger, sentence, dictionary, word_ids)
                results.append({"text": sentence, "translation": "", "structure": structure})
                file_count += 1
                total_from_files += 1

                if len(results) % 1000 == 0:
                    print(f"...{len(results)} sentences processed", file=sys.stderr)
                if args.limit is not None and len(results) >= args.limit:
                    done_with_file = True
                    break
            if done_with_file:
                break

        print(f"{os.path.basename(path)}: {file_count} sentence(s)", file=sys.stderr)
        if args.limit is not None and len(results) >= args.limit:
            break

    output_path = args.output or default_output_path(args.input)
    import json

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2 if args.pretty else None)

    unique_words = len({t["id"] for s in results for t in s["structure"] if t["kind"] == KIND_WORD})
    print(f"Done: {len(results)} sentence(s), {unique_words} unique tracked word(s) -> {output_path}", file=sys.stderr)


if __name__ == "__main__":
    main()

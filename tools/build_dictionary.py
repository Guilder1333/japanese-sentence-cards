#!/usr/bin/env python3
"""
Builds app/src/main/assets/dictionary/jmdict.db from a jmdict-simplified JSON release.

Usage:
    1. Download a "common words" English JSON release from
       https://github.com/scriptin/jmdict-simplified/releases
       (the "jmdict-eng-common-*.json.tgz" asset - not the full "jmdict-eng-*", which is much
       bigger; swap to that one if you want full dictionary coverage instead of just common words)
    2. Extract the .json file next to this script (or pass its path as the first argument).
    3. Run: python3 build_dictionary.py [path/to/jmdict-eng-common-*.json]

Produces a small SQLite file with three tables:
  - dict_entries(id, kanji, kana, meaning): one row per JMdict entry. `kanji` is the
    entry's primary (first-listed) kanji form, or NULL for kana-only words. `meaning` is a
    "1. (pos) gloss; gloss\n2. ..." formatted summary, capped to keep the file small.
  - dict_kanji_index(text, entry_id): every kanji spelling variant -> entry id, indexed.
  - dict_kana_index(text, entry_id): every kana/reading variant -> entry id, indexed.

See THIRD_PARTY_NOTICES.md for the JMdict/EDICT licensing terms this data is used under.
"""

import json
import os
import sqlite3
import sys

MAX_SENSES = 6
MAX_GLOSSES = 5

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_PATH = os.path.join(SCRIPT_DIR, "..", "app", "src", "main", "assets", "dictionary", "jmdict.db")


def find_default_source() -> str:
    for name in os.listdir(SCRIPT_DIR):
        if name.startswith("jmdict-eng") and name.endswith(".json"):
            return os.path.join(SCRIPT_DIR, name)
    raise SystemExit(
        "No jmdict-eng*.json found next to this script. Download a release from "
        "https://github.com/scriptin/jmdict-simplified/releases and extract it here, "
        "or pass its path as an argument."
    )


def main() -> None:
    src = sys.argv[1] if len(sys.argv) > 1 else find_default_source()

    with open(src, encoding="utf-8") as f:
        data = json.load(f)
    words = data["words"]
    print(f"loaded {len(words)} words from {src} (dictDate={data.get('dictDate')})")

    out_path = os.path.abspath(OUT_PATH)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    if os.path.exists(out_path):
        os.remove(out_path)

    conn = sqlite3.connect(out_path)
    cur = conn.cursor()
    cur.execute("PRAGMA journal_mode=OFF")
    cur.execute("PRAGMA synchronous=OFF")

    cur.execute("""
        CREATE TABLE dict_entries (
            id INTEGER PRIMARY KEY,
            kanji TEXT,
            kana TEXT NOT NULL,
            meaning TEXT NOT NULL
        )
    """)
    cur.execute("CREATE TABLE dict_kanji_index (text TEXT NOT NULL, entry_id INTEGER NOT NULL)")
    cur.execute("CREATE TABLE dict_kana_index (text TEXT NOT NULL, entry_id INTEGER NOT NULL)")

    entry_rows, kanji_rows, kana_rows = [], [], []
    skipped = 0

    for w in words:
        entry_id = int(w["id"])
        kanji_forms = [k["text"] for k in w.get("kanji", [])]
        kana_forms = [k["text"] for k in w.get("kana", [])]
        if not kana_forms:
            skipped += 1
            continue

        sense_lines = []
        for sense in w.get("sense", [])[:MAX_SENSES]:
            glosses = [g["text"] for g in sense.get("gloss", []) if g.get("lang") == "eng" and g.get("text")]
            if not glosses:
                continue
            line = "; ".join(glosses[:MAX_GLOSSES])
            pos = "/".join(sense.get("partOfSpeech", []))
            sense_lines.append(f"({pos}) {line}" if pos else line)

        if not sense_lines:
            skipped += 1
            continue

        meaning = "\n".join(f"{i + 1}. {line}" for i, line in enumerate(sense_lines))
        primary_kanji = kanji_forms[0] if kanji_forms else None
        entry_rows.append((entry_id, primary_kanji, kana_forms[0], meaning))
        kanji_rows += [(k, entry_id) for k in kanji_forms]
        kana_rows += [(k, entry_id) for k in kana_forms]

    print(f"skipped {skipped} entries with no usable kana/gloss")
    print(f"writing {len(entry_rows)} entries, {len(kanji_rows)} kanji rows, {len(kana_rows)} kana rows")

    cur.executemany("INSERT INTO dict_entries (id, kanji, kana, meaning) VALUES (?, ?, ?, ?)", entry_rows)
    cur.executemany("INSERT INTO dict_kanji_index (text, entry_id) VALUES (?, ?)", kanji_rows)
    cur.executemany("INSERT INTO dict_kana_index (text, entry_id) VALUES (?, ?)", kana_rows)
    cur.execute("CREATE INDEX idx_kanji_text ON dict_kanji_index(text)")
    cur.execute("CREATE INDEX idx_kana_text ON dict_kana_index(text)")

    conn.commit()
    cur.execute("VACUUM")
    conn.commit()
    conn.close()

    print(f"done: {out_path} = {os.path.getsize(out_path) / (1024 * 1024):.2f} MB")


if __name__ == "__main__":
    main()

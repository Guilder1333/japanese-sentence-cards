#!/usr/bin/env python3
"""
Builds app/src/main/assets/dictionary/kanjidic.db from a kanjidic2-en JSON release.

Usage:
    1. Download the English JSON release from
       https://github.com/scriptin/jmdict-simplified/releases
       (the "kanjidic2-en-*.json.tgz" asset - same project as the JMdict release used by
       build_dictionary.py).
    2. Extract the .json file next to this script (or pass its path as the first argument).
    3. Run: python3 build_kanji_dictionary.py [path/to/kanjidic2-en-*.json]

Produces a small SQLite file with one table:
  - kanji_entries(literal, meanings, onyomi, kunyomi, grade, jlpt, strokes): one row per kanji
    character. `meanings` / `onyomi` / `kunyomi` are "; "-joined strings (empty string if none).
    `grade` / `jlpt` / `strokes` may be NULL.

See THIRD_PARTY_NOTICES.md for the KANJIDIC2 licensing terms this data is used under.
"""

import json
import os
import sqlite3
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_PATH = os.path.join(SCRIPT_DIR, "..", "app", "src", "main", "assets", "dictionary", "kanjidic.db")


def find_default_source() -> str:
    for name in os.listdir(SCRIPT_DIR):
        if name.startswith("kanjidic2-en") and name.endswith(".json"):
            return os.path.join(SCRIPT_DIR, name)
    raise SystemExit(
        "No kanjidic2-en*.json found next to this script. Download a release from "
        "https://github.com/scriptin/jmdict-simplified/releases and extract it here, "
        "or pass its path as an argument."
    )


def main() -> None:
    src = sys.argv[1] if len(sys.argv) > 1 else find_default_source()

    with open(src, encoding="utf-8") as f:
        data = json.load(f)
    chars = data["characters"]
    print(f"loaded {len(chars)} kanji from {src} (dictDate={data.get('dictDate')})")

    out_path = os.path.abspath(OUT_PATH)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    if os.path.exists(out_path):
        os.remove(out_path)

    conn = sqlite3.connect(out_path)
    cur = conn.cursor()
    cur.execute("PRAGMA journal_mode=OFF")
    cur.execute("PRAGMA synchronous=OFF")

    cur.execute("""
        CREATE TABLE kanji_entries (
            literal TEXT PRIMARY KEY,
            meanings TEXT NOT NULL,
            onyomi TEXT NOT NULL,
            kunyomi TEXT NOT NULL,
            grade INTEGER,
            jlpt INTEGER,
            strokes INTEGER
        )
    """)

    rows = []
    skipped = 0
    for c in chars:
        literal = c["literal"]
        groups = c.get("readingMeaning", {}).get("groups", []) if c.get("readingMeaning") else []
        meanings, onyomi, kunyomi = [], [], []
        for group in groups:
            for m in group.get("meanings", []):
                if m.get("lang") == "en" and m.get("value"):
                    meanings.append(m["value"])
            for r in group.get("readings", []):
                if r.get("type") == "ja_on" and r.get("value"):
                    onyomi.append(r["value"])
                elif r.get("type") == "ja_kun" and r.get("value"):
                    kunyomi.append(r["value"])

        if not meanings and not onyomi and not kunyomi:
            skipped += 1
            continue

        misc = c.get("misc", {})
        strokes = misc.get("strokeCounts", [None])[0]
        rows.append((
            literal,
            "; ".join(meanings),
            "; ".join(onyomi),
            "; ".join(kunyomi),
            misc.get("grade"),
            misc.get("jlptLevel"),
            strokes,
        ))

    print(f"skipped {skipped} characters with no usable reading/meaning")
    print(f"writing {len(rows)} kanji entries")

    cur.executemany(
        "INSERT INTO kanji_entries (literal, meanings, onyomi, kunyomi, grade, jlpt, strokes) "
        "VALUES (?, ?, ?, ?, ?, ?, ?)",
        rows,
    )

    conn.commit()
    cur.execute("VACUUM")
    conn.commit()
    conn.close()

    print(f"done: {out_path} = {os.path.getsize(out_path) / (1024 * 1024):.2f} MB")


if __name__ == "__main__":
    main()

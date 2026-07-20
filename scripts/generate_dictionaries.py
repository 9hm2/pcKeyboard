#!/usr/bin/env python3
"""Generates the compressed word-frequency dictionaries shipped in
app/src/main/assets/dict/.

Input: the OpenSubtitles-2018 frequency lists from
https://github.com/hermitdave/FrequencyWords (CC-BY-SA 4.0) — one
"word count" pair per line, ordered by descending frequency.

Output: <lang_id>.dict — a gzip stream with one lowercase word per
line, ordered by descending frequency (the line number IS the frequency
rank), filtered to alphabetic words of the language and truncated to
MAX_WORDS. The extension is deliberately NOT .gz: aapt2 transparently
decompresses and renames assets ending in .gz while packaging the APK,
which would break the runtime path (and its GZIPInputStream).

Usage:
    python3 scripts/generate_dictionaries.py <input_dir> [<output_dir>]

where <input_dir> contains hu_full.txt / en_full.txt / de_full.txt /
es_full.txt and <output_dir> defaults to app/src/main/assets/dict.
"""

import gzip
import re
import sys
from pathlib import Path

# Hungarian is agglutinative — inflected forms each count as a separate
# dictionary entry, so it needs a much deeper cut than the others to
# reach comparable coverage.
LANGS = {
    "hu_HU": ("hu_full.txt", re.compile(r"^[a-záéíóöőúüű]+$"), 150_000),
    "en_US": ("en_full.txt", re.compile(r"^[a-z]+(?:'[a-z]+)?$"), 80_000),
    "de_DE": ("de_full.txt", re.compile(r"^[a-zäöüß]+$"), 100_000),
    "es_ES": ("es_full.txt", re.compile(r"^[a-záéíóúüñ]+$"), 80_000),
}

# Drop single letters that aren't real words — they'd otherwise become
# top-ranked "completions". Kept per language where they ARE words.
SINGLE_LETTER_WORDS = {
    "hu_HU": {"a", "s", "e", "ő"},
    "en_US": {"a", "i"},
    "de_DE": set(),
    "es_ES": {"a", "e", "o", "u", "y"},
}


def generate(lang_id, src_path, pattern, max_words, out_dir):
    words = []
    seen = set()
    with open(src_path, encoding="utf-8") as f:
        for line in f:
            token = line.split(" ", 1)[0].strip().lower()
            if not pattern.match(token):
                continue
            if len(token) == 1 and token not in SINGLE_LETTER_WORDS[lang_id]:
                continue
            if token in seen:
                continue
            seen.add(token)
            words.append(token)
            if len(words) >= max_words:
                break
    out_path = out_dir / f"{lang_id}.dict"
    with gzip.open(out_path, "wt", encoding="utf-8") as f:
        f.write("\n".join(words))
    print(f"{lang_id}: {len(words)} words -> {out_path} "
          f"({out_path.stat().st_size // 1024} KiB)")


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    src_dir = Path(sys.argv[1])
    out_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else \
        Path(__file__).resolve().parent.parent / "app/src/main/assets/dict"
    out_dir.mkdir(parents=True, exist_ok=True)
    for lang_id, (fname, pattern, max_words) in LANGS.items():
        generate(lang_id, src_dir / fname, pattern, max_words, out_dir)


if __name__ == "__main__":
    main()

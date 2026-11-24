#!/usr/bin/env python3
"""Builds a MIXED-REGISTER word-frequency dictionary (spoken + news +
encyclopedic text) — the successor of generate_dictionaries.py's
subtitles-only list, measured on held-out news/web/wiki text:

    subtitles-only 400k  ->  merged 400k (same size, same memory)
      word-form coverage      88-90%  -> 93-96%
      typo top-1 accuracy     63-64%  -> 71-73%
      completion top-8        48-52%  -> 60-64%
    merged 700k adds only +0.5% coverage for +40 MB heap: not worth it.

Why it works: a subtitles-only list ranks spoken words well but
under-ranks written/formal inflections, so correct candidates lose to
colloquial near-misses. Each register contributes its per-million
frequency (weighted); a word is kept if it clears the minimum count in
ANY single source, which keeps each corpus's own typo tail out.

Sources (all freely downloadable):
  - OpenSubtitles-2018 frequency list (hermitdave/FrequencyWords, CC-BY-SA)
  - Leipzig Corpora Collection "*-words.txt" tables (CC BY):
    <lang>_news_2020_1M and <lang>_wikipedia_2016_1M
    https://wortschatz.uni-leipzig.de/en/download

Usage:
    python3 scripts/build_merged_dictionary.py hu_HU <sources_dir> [<out_dir>]
  <sources_dir> holds hu_full.txt plus the extracted Leipzig folders
  hun_news_2020_1M/ and hun_wikipedia_2016_1M/ (their *-words.txt).
Then regenerate the n-gram models (their keys are word ranks!):
    PCK_LANGS=hu_HU python3 scripts/generate_bigrams.py  <sentences_dir>
    PCK_LANGS=hu_HU python3 scripts/generate_trigrams.py <sentences_dir>
"""

import collections
import gzip
import re
import sys
from pathlib import Path

CONFIG = {
    "hu_HU": {
        "pattern": re.compile(r"^[a-záéíóöőúüű]+$"),
        "single": "aseő",
        "max_words": 400_000,
        # (kind, file relative to sources_dir, weight, min count)
        "sources": [
            ("subtitles", "hu_full.txt", 0.40, 5),
            ("leipzig", "hun_news_2020_1M/hun_news_2020_1M-words.txt", 0.35, 4),
            ("leipzig", "hun_wikipedia_2016_1M/hun_wikipedia_2016_1M-words.txt", 0.25, 8),
        ],
    },
}


def load(kind, path, pattern):
    counts = collections.Counter()
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            if kind == "subtitles":
                parts = line.split()
                word, n = (parts[0], parts[1]) if len(parts) == 2 else (None, None)
            else:
                parts = line.rstrip("\n").split("\t")
                word, n = (parts[1], parts[2]) if len(parts) >= 3 else (None, None)
            if word is None:
                continue
            word = word.lower()
            if pattern.match(word):
                try:
                    counts[word] += int(n)
                except ValueError:
                    pass
    return counts


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in CONFIG:
        sys.exit(__doc__)
    lang = sys.argv[1]
    src = Path(sys.argv[2])
    out_dir = Path(sys.argv[3]) if len(sys.argv) > 3 else \
        Path(__file__).resolve().parent.parent / "app/src/main/assets/dict"
    cfg = CONFIG[lang]
    score = collections.defaultdict(float)
    keep = set()
    for kind, rel, weight, min_count in cfg["sources"]:
        counts = load(kind, src / rel, cfg["pattern"])
        total = sum(counts.values())
        print(f"{rel}: {len(counts)} types, {total} tokens")
        for w, n in counts.items():
            if len(w) == 1 and w not in cfg["single"]:
                continue
            score[w] += weight * n / total * 1e6
            if n >= min_count:
                keep.add(w)
    ranked = sorted(keep, key=lambda w: -score[w])[:cfg["max_words"]]
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / f"{lang}.dict"
    with gzip.open(out, "wt", encoding="utf-8") as f:
        f.write("\n".join(ranked))
    print(f"{lang}: {len(ranked)} words -> {out} ({out.stat().st_size // 1024} KiB)")


if __name__ == "__main__":
    main()

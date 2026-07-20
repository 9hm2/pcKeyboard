#!/usr/bin/env python3
"""Generates the bigram (word-pair frequency) assets shipped in
app/src/main/assets/dict/<lang_id>.bigrams.

Input: a Leipzig Corpora Collection sentences file per language
(https://wortschatz.uni-leipzig.de/en/download — "id<TAB>sentence" per
line, CC BY licensed) plus the unigram dictionary already shipped in
assets/dict/<lang_id>.dict (whose line number IS the word's rank).

Output: gzip stream of "rank1 rank2 count" lines, sorted by
(rank1, rank2), for consecutive word pairs where BOTH words are in the
unigram dictionary, count >= MIN_COUNT, truncated to the MAX_PAIRS most
frequent pairs. The runtime packs each pair into a Long
(rank1 << 20 | rank2) and binary-searches — hence the sort order here.

Usage:
    python3 scripts/generate_bigrams.py <sentences_dir> [<output_dir>]
"""

import gzip
import re
import sys
from collections import Counter
from pathlib import Path

LANGS = {
    "hu_HU": ("hun_news_2020_1M-sentences.txt", re.compile(r"[a-záéíóöőúüű]+")),
    "en_US": ("eng_news_2020_1M-sentences.txt", re.compile(r"[a-z]+(?:'[a-z]+)?")),
    "de_DE": ("deu_news_2020_1M-sentences.txt", re.compile(r"[a-zäöüß]+")),
    "es_ES": ("spa_news_2020_1M-sentences.txt", re.compile(r"[a-záéíóúüñ]+")),
}

MIN_COUNT = 3
MAX_PAIRS = 600_000
# Must match BigramModel.KEY_SHIFT on the Kotlin side.
KEY_SHIFT = 20


def load_vocab(dict_dir, lang_id):
    with gzip.open(dict_dir / f"{lang_id}.dict", "rt", encoding="utf-8") as f:
        return {w: i for i, w in enumerate(f.read().split("\n"))}


def generate(lang_id, sentences_path, pattern, vocab, out_dir):
    counts = Counter()
    with open(sentences_path, encoding="utf-8", errors="replace") as f:
        for line in f:
            tab = line.find("\t")
            sentence = line[tab + 1:] if tab >= 0 else line
            prev = -1
            for tok in pattern.findall(sentence.lower()):
                rank = vocab.get(tok, -1)
                if prev >= 0 and rank >= 0:
                    counts[(prev << KEY_SHIFT) | rank] += 1
                prev = rank
    kept = [(k, c) for k, c in counts.items() if c >= MIN_COUNT]
    kept.sort(key=lambda kc: -kc[1])
    kept = kept[:MAX_PAIRS]
    kept.sort(key=lambda kc: kc[0])
    out_path = out_dir / f"{lang_id}.bigrams"
    with gzip.open(out_path, "wt", encoding="utf-8") as f:
        for key, c in kept:
            f.write(f"{key >> KEY_SHIFT} {key & ((1 << KEY_SHIFT) - 1)} {c}\n")
    print(f"{lang_id}: {len(counts)} raw pairs -> {len(kept)} kept -> "
          f"{out_path} ({out_path.stat().st_size // 1024} KiB)")


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    src_dir = Path(sys.argv[1])
    assets = Path(__file__).resolve().parent.parent / "app/src/main/assets/dict"
    out_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else assets
    out_dir.mkdir(parents=True, exist_ok=True)
    for lang_id, (fname, pattern) in LANGS.items():
        vocab = load_vocab(assets, lang_id)
        generate(lang_id, src_dir / fname, pattern, vocab, out_dir)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Generates the trigram (word-triple frequency) assets shipped in
app/src/main/assets/dict/<lang_id>.trigrams.

Same sources and conventions as generate_bigrams.py (Leipzig news
sentence corpora + the shipped unigram vocabulary). Output lines are
"rank1 rank2 rank3 count", sorted by the packed key
(rank1 << 40 | rank2 << 20 | rank3) so the runtime can binary-search
and scan "continuations of (rank1, rank2)" as one contiguous block.

Usage:
    python3 scripts/generate_trigrams.py <sentences_dir> [<output_dir>]
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
MAX_TRIPLES = 400_000
# Must match TrigramModel.KEY_SHIFT on the Kotlin side.
KEY_SHIFT = 20
MASK = (1 << KEY_SHIFT) - 1


def load_vocab(dict_dir, lang_id):
    with gzip.open(dict_dir / f"{lang_id}.dict", "rt", encoding="utf-8") as f:
        return {w: i for i, w in enumerate(f.read().split("\n"))}


def generate(lang_id, sentences_path, pattern, vocab, out_dir):
    counts = Counter()
    with open(sentences_path, encoding="utf-8", errors="replace") as f:
        for line in f:
            tab = line.find("\t")
            sentence = line[tab + 1:] if tab >= 0 else line
            p2 = -1
            p1 = -1
            for tok in pattern.findall(sentence.lower()):
                rank = vocab.get(tok, -1)
                if p2 >= 0 and p1 >= 0 and rank >= 0:
                    counts[(p2 << (2 * KEY_SHIFT)) | (p1 << KEY_SHIFT) | rank] += 1
                p2 = p1
                p1 = rank
    kept = [(k, c) for k, c in counts.items() if c >= MIN_COUNT]
    del counts
    kept.sort(key=lambda kc: -kc[1])
    kept = kept[:MAX_TRIPLES]
    kept.sort(key=lambda kc: kc[0])
    out_path = out_dir / f"{lang_id}.trigrams"
    with gzip.open(out_path, "wt", encoding="utf-8") as f:
        for key, c in kept:
            f.write(f"{key >> (2 * KEY_SHIFT)} {(key >> KEY_SHIFT) & MASK} "
                    f"{key & MASK} {c}\n")
    print(f"{lang_id}: kept {len(kept)} -> {out_path} "
          f"({out_path.stat().st_size // 1024} KiB)", flush=True)


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

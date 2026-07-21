#!/usr/bin/env python3
"""Measures how well a trained reranker separates the intended word
from realistic typo distractors, BEFORE it ships.

Builds eval cases from held-out corpus sentences: pick a word, noise it
with the shared QWERTZ noise model, and ask the LM to rank the gold
word against edit-distance siblings. Reports top-1 accuracy — compare
against the no-LM baseline to see what the reranker actually buys.
Also includes the hand-collected live-session regression cases.

Usage:
    python3 evaluate.py --lang hu_HU --sentences held_out.txt \
        --model reranker_hu_HU.tflite --chars reranker_hu_HU.chars
"""

import argparse
import random

import numpy as np

from noise_model import build_adjacency, noise_word

# Real cases observed in live typing sessions (context, typo, gold).
LIVE_CASES_HU = [
    ("ez egy", "teljrsem", "teljesen"),
    ("eredményt", "afjom", "adjon"),
    ("ezt", "tavítani", "javítani"),
    ("szót elsőre nem", "értelmrztr", "értelmezte"),
    ("és", "krllrne", "kellene"),
]


def load_lm(model_path, chars_path):
    from ai_edge_litert.interpreter import Interpreter  # or tf.lite.Interpreter
    chars = open(chars_path, encoding="utf-8").read().split("\n")
    charmap = {c: i + 2 for i, c in enumerate(chars)}
    interp = Interpreter(model_path=model_path)
    interp.allocate_tensors()
    inp = interp.get_input_details()[0]
    out = interp.get_output_details()[0]
    seq_len = inp["shape"][1]

    def logprob(context, candidate):
        text = (context + " " + candidate).lower()[-seq_len - 1:]
        ids = [charmap.get(c, 1) for c in text]
        ids = ([0] * (seq_len + 1 - len(ids)) + ids)
        x = np.asarray([ids[:-1]], dtype=np.int32)
        interp.set_tensor(inp["index"], x)
        interp.invoke()
        logits = interp.get_tensor(out["index"])[0]
        # Score only the candidate's char positions.
        n = min(len(candidate), seq_len)
        total = 0.0
        for t in range(seq_len - n, seq_len):
            row = logits[t]
            row = row - row.max()
            p = np.exp(row) / np.exp(row).sum()
            total += np.log(max(p[ids[t + 1]], 1e-9))
        return total / n

    return logprob


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", required=True)
    ap.add_argument("--sentences", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--chars", required=True)
    ap.add_argument("--cases", type=int, default=500)
    args = ap.parse_args()

    lm = load_lm(args.model, args.chars)
    adj = build_adjacency(args.lang)
    rng = random.Random(42)

    correct = 0
    total = 0
    with open(args.sentences, encoding="utf-8", errors="replace") as f:
        for line in f:
            if total >= args.cases:
                break
            tab = line.find("\t")
            words = (line[tab + 1:] if tab >= 0 else line).strip().lower().split()
            if len(words) < 4:
                continue
            i = rng.randrange(2, len(words))
            gold = words[i]
            if len(gold) < 4 or not gold.isalpha():
                continue
            context = " ".join(words[max(0, i - 3):i])
            distractors = {noise_word(gold, adj, rng) for _ in range(3)}
            distractors.discard(gold)
            if not distractors:
                continue
            ranked = sorted([gold, *distractors],
                            key=lambda w: -lm(context, w))
            correct += ranked[0] == gold
            total += 1
    print(f"synthetic: top-1 accuracy {correct}/{total} = {correct / total:.1%}")

    if args.lang == "hu_HU":
        hits = 0
        for context, typo, gold in LIVE_CASES_HU:
            better = lm(context, gold) > lm(context, typo)
            hits += better
            print(f"  {typo!r:14} -> {gold!r:14} LM prefers gold: {better}")
        print(f"live cases: {hits}/{len(LIVE_CASES_HU)}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Trains the on-device character-level LM reranker and exports it to
TFLite. Designed to run on a single free-tier GPU (Colab T4) in a few
hours; see README.md in this directory.

Architecture: char embedding (128) -> 2x GRU(384, seq) -> Dense(V).
~2.5M parameters, dynamic-range INT8 after conversion (~3 MB). The
model is a plain next-char LM trained on CLEAN corpus text — at runtime
the engine uses it to compare P(context + candidate) across correction
candidates, so it can never invent words, only rank the safe ones.

Char-level deliberately: no tokenizer dependency on-device, and
Hungarian's agglutination means no word-level vocabulary is ever
complete.

Outputs (drop both into app/src/main/assets/reranker/):
    reranker_<lang>.tflite
    reranker_<lang>.chars     (one char per line, index order)

Usage:
    python3 train.py --lang hu_HU --sentences hun_news_2020_1M-sentences.txt \
        [--epochs 2] [--seq-len 96] [--limit-sentences N]
"""

import argparse
import os
import random
import subprocess
import sys
from pathlib import Path

import numpy as np

CHARSETS = {
    "hu_HU": "abcdefghijklmnopqrstuvwxyzáéíóöőúüű",
    "en_US": "abcdefghijklmnopqrstuvwxyz'",
    "de_DE": "abcdefghijklmnopqrstuvwxyzäöüß",
    "es_ES": "abcdefghijklmnopqrstuvwxyzáéíóúüñ",
}
COMMON = " .,!?-0123456789"
PAD, UNK = 0, 1  # reserved ids; charset starts at id 2


def build_charmap(lang):
    chars = CHARSETS[lang] + COMMON
    return {c: i + 2 for i, c in enumerate(chars)}, chars


def encode(text, charmap):
    return [charmap.get(c, UNK) for c in text]


def load_windows(path, charmap, seq_len, limit):
    xs = []
    with open(path, encoding="utf-8", errors="replace") as f:
        for n, line in enumerate(f):
            if limit and n >= limit:
                break
            tab = line.find("\t")
            text = (line[tab + 1:] if tab >= 0 else line).strip().lower()
            ids = encode(text, charmap)
            for start in range(0, max(1, len(ids) - seq_len), seq_len):
                w = ids[start:start + seq_len + 1]
                if len(w) < 16:
                    continue
                w = w + [PAD] * (seq_len + 1 - len(w))
                xs.append(w)
    random.shuffle(xs)
    return np.asarray(xs, dtype=np.int32)


def build_model(vocab, seq_len):
    import tensorflow as tf
    inp = tf.keras.Input(shape=(seq_len,), dtype="int32")
    x = tf.keras.layers.Embedding(vocab, 128, mask_zero=False)(inp)
    x = tf.keras.layers.GRU(384, return_sequences=True)(x)
    x = tf.keras.layers.GRU(384, return_sequences=True)(x)
    out = tf.keras.layers.Dense(vocab)(x)
    model = tf.keras.Model(inp, out)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(2e-3),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=True),
    )
    return model


def export_tflite(args, chars, vocab):
    """Runs in a CPU-only process: with a GPU visible, Keras bakes the
    fused CudnnRNNV3 kernel into the traced graph, which the TFLite
    converter cannot translate ("op is neither a custom op nor a flex
    op"). On CPU the GRU traces to portable ops and converts cleanly."""
    import tensorflow as tf

    out = Path(args.out_dir)
    model = build_model(vocab, args.seq_len)
    model.load_weights(out / f"reranker_{args.lang}.weights.h5")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]  # dynamic-range INT8
    tflite = converter.convert()
    (out / f"reranker_{args.lang}.tflite").write_bytes(tflite)
    (out / f"reranker_{args.lang}.chars").write_text(
        "\n".join(chars), encoding="utf-8")
    print(f"exported reranker_{args.lang}.tflite "
          f"({len(tflite) // 1024} KiB) + .chars")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", required=True, choices=sorted(CHARSETS))
    ap.add_argument("--sentences", required=True)
    ap.add_argument("--epochs", type=int, default=2)
    ap.add_argument("--seq-len", type=int, default=96)
    ap.add_argument("--batch", type=int, default=256)
    ap.add_argument("--limit-sentences", type=int, default=0)
    ap.add_argument("--out-dir", default=".")
    ap.add_argument("--export-only", action="store_true",
                    help="internal: convert previously saved weights on CPU")
    args = ap.parse_args()

    charmap, chars = build_charmap(args.lang)
    vocab = len(chars) + 2

    if args.export_only:
        export_tflite(args, chars, vocab)
        return

    data = load_windows(args.sentences, charmap, args.seq_len, args.limit_sentences)
    print(f"{len(data)} training windows, vocab={vocab}")
    x, y = data[:, :-1], data[:, 1:]

    model = build_model(vocab, args.seq_len)
    model.summary()
    model.fit(x, y, batch_size=args.batch, epochs=args.epochs,
              validation_split=0.02)

    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    model.save_weights(out / f"reranker_{args.lang}.weights.h5")

    # Re-run ourselves with the GPU hidden for the conversion step —
    # see export_tflite for why.
    env = dict(os.environ, CUDA_VISIBLE_DEVICES="-1")
    subprocess.check_call(
        [sys.executable, os.path.abspath(__file__),
         "--lang", args.lang, "--sentences", args.sentences,
         "--seq-len", str(args.seq_len), "--out-dir", args.out_dir,
         "--export-only"],
        env=env,
    )


if __name__ == "__main__":
    main()

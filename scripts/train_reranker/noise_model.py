"""Typing-noise model shared by training-data generation and eval.

Mirrors the engine's physical adjacency maps (SuggestionEngine.kt) so
the synthetic errors match what real fingers produce on this keyboard:
adjacent-key substitutions, transpositions, doubled/dropped letters,
dropped accents — the exact classes observed in live sessions.
"""

import random

KEYBOARD_ROWS = {
    "hu_HU": ["         öüó", "qwertzuiopőú", "asdfghjkléáű", "íyxcvbnm"],
    "de_DE": ["qwertzuiopü", "asdfghjklöä", "yxcvbnm"],
    "en_US": ["qwertyuiop", "asdfghjkl", "zxcvbnm"],
    "es_ES": ["qwertyuiop", "asdfghjklñ", "zxcvbnm"],
}

DEACCENT = str.maketrans("áéíóöőúüűäñ", "aeiooouuuan")


def build_adjacency(lang_id):
    rows = KEYBOARD_ROWS[lang_id]
    adj = {}

    def link(a, b):
        if a == " " or b == " " or a == b:
            return
        adj.setdefault(a, set()).add(b)
        adj.setdefault(b, set()).add(a)

    for r, row in enumerate(rows):
        for c, ch in enumerate(row):
            if c + 1 < len(row):
                link(ch, row[c + 1])
            if r + 1 < len(rows):
                for dc in (-1, 0, 1):
                    j = c + dc
                    if 0 <= j < len(rows[r + 1]):
                        link(ch, rows[r + 1][j])
    return adj


def noise_word(word, adj, rng: random.Random, p_multi=0.15):
    """Applies 1 (or with p_multi, 2) realistic slips to a word."""
    ops = ["sub_near", "transpose", "drop", "double", "deaccent"]

    def one(w):
        if len(w) < 2:
            return w
        op = rng.choice(ops)
        i = rng.randrange(len(w))
        if op == "sub_near" and w[i] in adj:
            return w[:i] + rng.choice(sorted(adj[w[i]])) + w[i + 1:]
        if op == "transpose" and i < len(w) - 1 and w[i] != w[i + 1]:
            return w[:i] + w[i + 1] + w[i] + w[i + 2:]
        if op == "drop" and len(w) > 2:
            return w[:i] + w[i + 1:]
        if op == "double":
            return w[:i] + w[i] + w[i:]
        if op == "deaccent":
            d = w.translate(DEACCENT)
            if d != w:
                return d
        return w

    noised = one(word)
    if rng.random() < p_multi:
        noised = one(noised)
    return noised

# Neural reranker — training

A tiny character-level language model that rescores the correction
candidates of the suggestion engine with full sentence context. The
Android side (`NeuralReranker.kt` / `RerankerStore.kt`) is already
wired and fail-open: it activates automatically the moment the model
files appear under `app/src/main/assets/reranker/`, and the keyboard
behaves exactly as before while they're absent.

## Why char-level

No tokenizer dependency on-device, and no word vocabulary is ever
complete for Hungarian — a char LM scores any inflection or compound.
The model only *ranks* candidates that already passed the engine's
safety gates (Hunspell validity, edit-distance, frequency caps), so it
cannot invent words.

## Steps (Colab, free T4 — a few hours total)

1. Get a sentence corpus per language (same source the bigrams use):

   ```
   wget https://downloads.wortschatz-leipzig.de/corpora/hun_news_2020_1M.tar.gz
   tar xzf hun_news_2020_1M.tar.gz --wildcards '*-sentences.txt'
   ```

2. Train + export (repeat per language; hu first, it matters most):

   ```
   pip install tensorflow
   python3 train.py --lang hu_HU \
       --sentences hun_news_2020_1M/hun_news_2020_1M-sentences.txt \
       --epochs 2
   ```

   Produces `reranker_hu_HU.tflite` (~3 MB INT8) and
   `reranker_hu_HU.chars`.

3. Evaluate before shipping (held-out sentences = any file NOT used in
   training, e.g. a different year's corpus):

   ```
   python3 evaluate.py --lang hu_HU --sentences held_out-sentences.txt \
       --model reranker_hu_HU.tflite --chars reranker_hu_HU.chars
   ```

   Expect ≥90% synthetic top-1 and all live cases preferring gold; if
   it's below that, train longer (--epochs 3-4) before shipping.

4. Ship: copy both files to `app/src/main/assets/reranker/` and build.
   `RerankerStore` picks them up by name; no code change needed.

## Runtime contract (keep these in sync)

- Input: int32 `[1, seq_len]` char ids; id 0 = PAD, 1 = UNK, charset
  starts at 2 in `.chars` file order. `seq_len` is read from the model.
- Output: float logits `[1, seq_len, vocab]`, position t predicts t+1.
- The engine calls the reranker only at word boundaries (Auto-mode
  commit decisions), never per keystroke, with a strict time budget —
  a slow device simply skips the rerank.

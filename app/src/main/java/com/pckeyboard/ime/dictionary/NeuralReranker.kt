package com.pckeyboard.ime.dictionary

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device character-level LM that rescores correction candidates with
 * sentence context — the "phase 1" neural layer. Model contract (kept
 * in sync with scripts/train_reranker/train.py):
 *
 *  - input  int32 [1, seqLen] char ids (0 = PAD, 1 = UNK, charset file
 *    order starting at 2), output float [1, seqLen, vocab] logits,
 *    position t predicting position t+1;
 *  - assets: reranker/reranker_<langId>.tflite + .chars.
 *
 * Used ONLY at word boundaries (Auto-mode commit decisions), never per
 * keystroke, and every call is wrapped fail-open: any error or a slow
 * device just means "no rerank".
 */
class NeuralReranker private constructor(
    private val interpreter: Interpreter,
    private val charToId: Map<Char, Int>,
    private val seqLen: Int,
    private val vocab: Int
) {

    /**
     * Average log-probability per character of [candidate] following
     * [context]. Higher = the sentence likes this candidate more.
     * Returns null on any failure.
     */
    fun scoreCandidate(context: String, candidate: String): Double? {
        return try {
            val text = "$context $candidate".lowercase()
            val ids = IntArray(seqLen + 1)
            val encoded = text.takeLast(seqLen + 1).map { charToId[it] ?: UNK }
            val offset = seqLen + 1 - encoded.size
            for ((k, id) in encoded.withIndex()) ids[offset + k] = id

            val input = Array(1) { IntArray(seqLen) { t -> ids[t] } }
            val output = Array(1) { Array(seqLen) { FloatArray(vocab) } }
            interpreter.run(input, output)

            val n = minOf(candidate.length, seqLen)
            var total = 0.0
            for (t in seqLen - n until seqLen) {
                val logits = output[0][t]
                var max = Float.NEGATIVE_INFINITY
                for (v in logits) if (v > max) max = v
                var sum = 0.0
                for (v in logits) sum += kotlin.math.exp((v - max).toDouble())
                val target = ids[t + 1]
                val logP = (logits[target] - max) - kotlin.math.ln(sum)
                total += logP
            }
            total / n
        } catch (_: Throwable) {
            null
        }
    }

    fun close() {
        try { interpreter.close() } catch (_: Throwable) {}
    }

    companion object {
        private const val UNK = 1

        /** Loads the model pair for [langId], or null when absent /
         *  anything at all goes wrong. */
        fun load(context: Context, langId: String): NeuralReranker? {
            return try {
                val assets = context.assets
                val chars = assets.open("reranker/reranker_$langId.chars")
                    .bufferedReader().readText().split("\n")
                val charToId = HashMap<Char, Int>()
                for ((i, line) in chars.withIndex()) {
                    if (line.isNotEmpty()) charToId[line[0]] = i + 2
                }
                val modelBytes = assets.open("reranker/reranker_$langId.tflite")
                    .readBytes()
                val model = ByteBuffer.allocateDirect(modelBytes.size)
                    .order(ByteOrder.nativeOrder())
                model.put(modelBytes)
                model.rewind()
                val interpreter = Interpreter(model, Interpreter.Options().apply {
                    numThreads = 2
                })
                val inShape = interpreter.getInputTensor(0).shape()   // [1, seqLen]
                val outShape = interpreter.getOutputTensor(0).shape() // [1, seqLen, vocab]
                NeuralReranker(
                    interpreter, charToId,
                    seqLen = inShape[1], vocab = outShape[2]
                )
            } catch (_: Throwable) {
                null
            }
        }
    }
}

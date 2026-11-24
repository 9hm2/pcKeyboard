package com.pckeyboard.ime.util

import android.content.Context

/** Tiny persistent int store — an interface so the guard's logic can be
 *  unit-tested on the JVM without Android's SharedPreferences. Writes
 *  must be durable immediately (the whole point is surviving a crash a
 *  moment later). */
interface GuardStorage {
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
}

/**
 * Crash-loop guard for risky NATIVE code (the TFLite reranker).
 *
 * A native crash (SIGSEGV in a delegate, an ABI-mismatched .so) kills
 * the whole IME process and no `try/catch` can intercept it — so the
 * keyboard would crash on every start, forever. The guard turns that
 * into self-healing:
 *
 *  1. [begin] persists a "pending" marker right before the risky call;
 *  2. [end] clears it (and resets the strike counter) when the call
 *     returned normally;
 *  3. if a later process finds the marker still set, the previous run
 *     died inside the risky call — [shouldSkip] counts a strike;
 *  4. after [maxStrikes] CONSECUTIVE deaths the feature is skipped
 *     (the keyboard keeps working without it).
 *
 * Keys embed the app version, so shipping a new build gives the feature
 * a fresh chance automatically.
 */
class NativeCrashGuard(
    private val storage: GuardStorage,
    private val appVersion: Int,
    private val maxStrikes: Int = 2
) {

    private fun pendingKey(feature: String) = "pending_${feature}_$appVersion"
    private fun strikesKey(feature: String) = "strikes_${feature}_$appVersion"

    /** Call once per process before using [feature]. Returns true when
     *  the feature must NOT be used. */
    fun shouldSkip(feature: String): Boolean {
        if (storage.getInt(pendingKey(feature), 0) == 1) {
            // Last process died mid-call: that's a strike.
            storage.putInt(strikesKey(feature), storage.getInt(strikesKey(feature), 0) + 1)
            storage.putInt(pendingKey(feature), 0)
        }
        return storage.getInt(strikesKey(feature), 0) >= maxStrikes
    }

    /** Marks the start of a risky native section. */
    fun begin(feature: String) = storage.putInt(pendingKey(feature), 1)

    /** Marks the risky section as finished without dying. */
    fun end(feature: String) {
        storage.putInt(pendingKey(feature), 0)
        storage.putInt(strikesKey(feature), 0)
    }

    companion object {
        const val FEATURE_RERANKER = "reranker"

        /** Guard backed by **device-protected** prefs (readable during
         *  Direct Boot too) with synchronous commits. */
        fun forContext(context: Context, appVersion: Int): NativeCrashGuard {
            val deContext = context.applicationContext.createDeviceProtectedStorageContext()
            val prefs = deContext.getSharedPreferences("native_crash_guard", Context.MODE_PRIVATE)
            return NativeCrashGuard(object : GuardStorage {
                override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
                override fun putInt(key: String, value: Int) {
                    prefs.edit().putInt(key, value).commit()
                }
            }, appVersion)
        }
    }
}

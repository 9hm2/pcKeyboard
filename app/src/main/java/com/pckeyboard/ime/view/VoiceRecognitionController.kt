package com.pckeyboard.ime.view

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/**
 * Owns one voice-dictation session: the [SpeechRecognizer], its state
 * and the final-text delivery. Deliberately NOT a view — the IME's view
 * tree is torn down and rebuilt on every rotation / fold, and a
 * recogniser living inside it was killed each time. The service holds
 * this controller instead; the overlay ([VoiceInputView]) merely
 * attaches to it as a renderer.
 *
 * If the renderer detaches and no new one attaches within
 * [ORPHAN_TIMEOUT_MS] (the keyboard really went away), the session ends
 * itself and calls [onAbandoned]. A new renderer attaching inside that
 * window — the post-rotation view — just picks the live session up.
 */
class VoiceRecognitionController(
    private val context: Context,
    val recognitionLocale: String,
    /** Final, punctuation-processed transcript. */
    private val onFinalText: (String) -> Unit,
    /** The session ended because nobody re-attached in time. */
    private val onAbandoned: () -> Unit
) {

    interface Ui { fun onVoiceStateChanged() }

    enum class Status {
        IDLE, LISTENING, PERMISSION_MISSING, NOT_AVAILABLE,
        ERROR_NETWORK, ERROR_NO_MATCH, ERROR_OTHER
    }

    var status: Status = Status.IDLE
        private set
    var errorCode: Int = 0
        private set
    var listening: Boolean = false
        private set
    var lastPartial: String = ""
        private set
    var rmsDb: Float = 0f
        private set

    private var ui: Ui? = null
    val hasUi: Boolean get() = ui != null

    private var recognizer: SpeechRecognizer? = null
    private var destroyed = false
    /** True if the user explicitly tapped Stop — so a follow-up
     *  ERROR_CLIENT from the recogniser cancelling isn't misread as a
     *  real recognition failure. */
    private var userCanceled = false

    private val handler = Handler(Looper.getMainLooper())
    private val orphanRunnable = Runnable {
        if (!destroyed && ui == null) {
            destroy()
            onAbandoned()
        }
    }

    fun attach(newUi: Ui?) {
        ui = newUi
        if (newUi != null) {
            handler.removeCallbacks(orphanRunnable)
        } else if (!destroyed) {
            handler.postDelayed(orphanRunnable, ORPHAN_TIMEOUT_MS)
        }
    }

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Starts listening unless a session is already running — a view
     *  re-attaching after a rotation must not restart live dictation. */
    fun startIfIdle() {
        if (!listening && !destroyed) start()
    }

    fun start() {
        if (destroyed) return
        if (!hasRecordPermission()) { setStatus(Status.PERMISSION_MISSING); return }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            setStatus(Status.NOT_AVAILABLE); return
        }
        userCanceled = false
        lastPartial = ""
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            recognizer = it
        }
        rec.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLocale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Android 13+ — ask the recogniser to format the transcript
            // (auto-capitalisation, sentence punctuation); recognisers
            // without the extra ignore it.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
                )
            }
        }
        try {
            rec.startListening(intent)
        } catch (_: Throwable) {
            setStatus(Status.NOT_AVAILABLE)
        }
    }

    fun stop() {
        userCanceled = true
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
        } catch (_: Throwable) {}
        listening = false
        setStatus(Status.IDLE)
    }

    /** Ends the session for good and releases the recogniser. */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        handler.removeCallbacks(orphanRunnable)
        try { recognizer?.cancel() } catch (_: Throwable) {}
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = null
        listening = false
        ui = null
    }

    private fun setStatus(s: Status, code: Int = 0) {
        status = s
        errorCode = code
        ui?.onVoiceStateChanged()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
            lastPartial = ""
            setStatus(Status.LISTENING)
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rms: Float) { rmsDb = rms; ui?.onVoiceStateChanged() }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { listening = false; setStatus(Status.IDLE) }

        override fun onError(error: Int) {
            listening = false
            // SpeechRecognizer routinely fires ERROR_CLIENT after the
            // user finishes speaking and the service shuts down — even
            // when partial results came through cleanly. With a usable
            // transcript and no explicit cancel, commit it instead of
            // showing an error.
            if (!userCanceled && lastPartial.isNotBlank()) {
                val text = lastPartial
                lastPartial = ""
                onFinalText(VoicePunctuation.apply(text, recognitionLocale))
                return
            }
            userCanceled = false
            setStatus(
                when (error) {
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Status.ERROR_NETWORK
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Status.ERROR_NO_MATCH
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Status.PERMISSION_MISSING
                    else -> Status.ERROR_OTHER
                },
                error
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotEmpty()) {
                lastPartial = text
                ui?.onVoiceStateChanged()
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            listening = false
            lastPartial = ""
            if (text.isNotEmpty()) onFinalText(VoicePunctuation.apply(text, recognitionLocale))
            else setStatus(Status.IDLE)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        /** How long a session survives without any attached overlay —
         *  covers the view rebuild after a rotation / fold. */
        const val ORPHAN_TIMEOUT_MS = 2_500L
    }
}

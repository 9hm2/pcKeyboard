package com.pckeyboard.ime.clipboard

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.pckeyboard.ime.R

/**
 * Small full-screen editor for a single clipboard entry. The user opens
 * this from the clipboard manager via long-press; the typed-into EditText
 * is editable through the system IME (which is us). Send commits the
 * edited text back to the original input via [ClipboardEditorBridge];
 * X dismisses without doing anything.
 */
class ClipboardEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clipboard_editor)

        val root = findViewById<android.view.View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        val original = intent.getStringExtra(EXTRA_TEXT) ?: ""
        val editor = findViewById<EditText>(R.id.editor).apply {
            setText(original)
            setSelection(original.length)
            requestFocus()
        }

        // Stale bridge state from any previous edit session is invalidated
        // here, before the user can type anything new.
        ClipboardEditorBridge.pending = null

        findViewById<ImageButton>(R.id.btnSend).setOnClickListener {
            ClipboardEditorBridge.pending = ClipboardEditorBridge.Result(
                original = original,
                edited = editor.text.toString()
            )
            finish()
        }
        findViewById<ImageButton>(R.id.btnCancel).setOnClickListener {
            finish()
        }
    }

    companion object {
        const val EXTRA_TEXT = "extra_text"
    }
}

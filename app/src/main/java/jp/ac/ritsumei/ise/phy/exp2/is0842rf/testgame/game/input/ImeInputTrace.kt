package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import org.json.JSONObject
import java.io.File

/** Opt-in diagnostic trace for this app's test field only. Not enabled in normal play. */
class ImeInputTrace(context: Context, private val host: EditText, clear: Boolean = false) {
    private val file = File(context.filesDir, "ime-input-trace.jsonl")
    private var sequence = 0

    init {
        if (clear) file.writeText("")
        sequence = if (file.exists()) file.useLines { it.count() } else 0
        record("session", Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
    }

    fun record(operation: String, value: CharSequence?) {
        val source = value?.toString().orEmpty()
        val editable = host.text
        val row = JSONObject()
            .put("sequence", ++sequence)
            .put("uptimeMillis", SystemClock.uptimeMillis())
            .put("operation", operation)
            .put("text", source)
            .put("codePoints", source.codePoints().toArray().joinToString(" ") { "U+" + it.toString(16).uppercase() })
            .put("editorText", editable?.toString().orEmpty())
            .put("composingStart", editable?.let { BaseInputConnection.getComposingSpanStart(it) } ?: -1)
            .put("composingEnd", editable?.let { BaseInputConnection.getComposingSpanEnd(it) } ?: -1)
            .put("selectionStart", host.selectionStart)
            .put("selectionEnd", host.selectionEnd)
        file.appendText(row.toString() + "\n")
    }
}


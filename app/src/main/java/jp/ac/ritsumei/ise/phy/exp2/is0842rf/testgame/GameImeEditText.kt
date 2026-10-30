package jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.TextAttribute
import androidx.appcompat.widget.AppCompatEditText
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input.ImeStrokeDecoder
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input.ImeStrokeEdit
import jp.ac.ritsumei.ise.phy.exp2.is0842rf.testgame.game.input.ImeTextDecoder

/** Hidden keyboard transport. Editing/deletion never reaches the game. */
class GameImeEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {
    var inputDecoder: ImeTextDecoder = ImeStrokeDecoder()
    var onGameInput: ((ImeStrokeEdit) -> Boolean)? = null
    var traceSink: ((String, CharSequence?) -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        traceSink?.invoke("createInputConnection", "inputType=${outAttrs.inputType};imeOptions=${outAttrs.imeOptions}")
        return object : InputConnectionWrapper(connection, false) {
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                traceSink?.invoke("setComposingText", text)
                val edit = inputDecoder.composing(text?.toString().orEmpty())
                traceSink?.invoke("decoded", "source=${edit.sourceText};input=${edit.inputText};tokens=${edit.tokens}")
                if (!edit.accepted) return true
                val handled = super.setComposingText(text, newCursorPosition)
                deliver(edit)
                return handled
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                traceSink?.invoke("commitText", text)
                val edit = inputDecoder.commit(text?.toString().orEmpty())
                traceSink?.invoke("decoded", "source=${edit.sourceText};input=${edit.inputText};tokens=${edit.tokens}")
                if (!edit.accepted) return true
                val handled = super.commitText(text, newCursorPosition)
                deliver(edit)
                return handled
            }

            override fun setComposingText(text: CharSequence, newCursorPosition: Int, attribute: TextAttribute?): Boolean =
                setComposingText(text, newCursorPosition)

            override fun commitText(text: CharSequence, newCursorPosition: Int, attribute: TextAttribute?): Boolean =
                commitText(text, newCursorPosition)

            override fun finishComposingText(): Boolean {
                traceSink?.invoke("finishComposingText", "")
                val edit = inputDecoder.finish()
                val handled = super.finishComposingText()
                if (edit.tokens.isNotEmpty()) deliver(edit)
                return handled
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                traceSink?.invoke("deleteSurroundingText", "$beforeLength,$afterLength")
                return true
            }
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                traceSink?.invoke("deleteSurroundingTextInCodePoints", "$beforeLength,$afterLength")
                return true
            }
            override fun replaceText(start: Int, end: Int, text: CharSequence, newCursorPosition: Int,
                                     textAttribute: TextAttribute?): Boolean =
                if (start == end) commitText(text, newCursorPosition) else true
            override fun performContextMenuAction(id: Int) = true
            override fun sendKeyEvent(event: KeyEvent): Boolean =
                if (isDelete(event.keyCode)) true else {
                    traceSink?.invoke("sendKeyEvent", "action=${event.action};code=${event.keyCode};unicode=${event.unicodeChar}")
                    super.sendKeyEvent(event)
                }

            override fun beginBatchEdit(): Boolean {
                traceSink?.invoke("beginBatchEdit", "")
                return super.beginBatchEdit()
            }

            override fun endBatchEdit(): Boolean {
                val handled = super.endBatchEdit()
                traceSink?.invoke("endBatchEdit", "")
                return handled
            }

            override fun setComposingRegion(start: Int, end: Int): Boolean {
                traceSink?.invoke("setComposingRegion", "$start,$end")
                return super.setComposingRegion(start, end)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        traceSink?.invoke("onKeyDown", "code=$keyCode;unicode=${event.unicodeChar}")
        if (isDelete(keyCode) || keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END)) return true
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            deliver(inputDecoder.finish())
            return true
        }
        val codePoint = event.unicodeChar
        if (!event.isCtrlPressed && !event.isMetaPressed && codePoint >= 32) {
            deliver(inputDecoder.commit(String(Character.toChars(codePoint))))
            return true
        }
        if (event.isCtrlPressed || event.isMetaPressed) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onTextContextMenuItem(id: Int) = true

    private fun deliver(edit: ImeStrokeEdit) {
        val hit = onGameInput?.invoke(edit) == true
        if (hit || !edit.composing) {
            inputDecoder.reset()
            text?.let {
                BaseInputConnection.removeComposingSpans(it)
                it.clear()
            }
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .updateSelection(this, 0, 0, -1, -1)
        }
    }

    private fun isDelete(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL
}


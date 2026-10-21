package it.oggisimangia.app

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.text.InputType

/** Model for hand-written recipe steps; numbering is regenerated after edits. */
object RecipeSteps {
    fun split(value: String): List<String> = value.lines()
        .map { it.trim().replace(Regex("^(?:\\d+[.)]\\s*|[-•]\\s+)"), "") }
        .filter { it.isNotBlank() }

    fun numbered(values: List<String>): String = values.map { it.trim() }
        .filter { it.isNotBlank() }
        .mapIndexed { index, step -> (index + 1).toString() + ". " + step }
        .joinToString("\n")
}

/** Expandable rows for ingredients and cooking steps; separate from the 10-item search filter. */
class RecipeListFields(context: Context, initial: String, private val steps: Boolean) : LinearLayout(context) {
    private val fields = mutableListOf<EditText>()
    private val indexes = mutableListOf<TextView>()
    private val limit = if (steps) 40 else 60
    private val addButton = Button(context).apply {
        isAllCaps = false
        text = if (steps) "＋ Aggiungi passaggio" else "＋ Aggiungi ingrediente"
        setTextColor(android.graphics.Color.rgb(21,103,91))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.rgb(234,244,241))
            cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { addRow("") }
    }

    init {
        orientation = VERTICAL
        (if (steps) RecipeSteps.split(initial) else initial.lines().map { it.trim() }.filter { it.isNotBlank() })
            .ifEmpty { listOf("") }.take(limit).forEach { addRow(it) }
        addView(addButton, LayoutParams(-1, LayoutParams.WRAP_CONTENT))
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    private fun addRow(value: String) {
        if (fields.size >= limit) return
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.TOP or Gravity.CENTER_VERTICAL
        }
        val index = TextView(context).apply {
            textSize = 15f
            setPadding(dp(3), dp(13), dp(3), 0)
        }
        val field = EditText(context).apply {
            hint = if (steps) "Descrivi il passaggio" else "Es. 200 g di farina"
            contentDescription = if (steps) "Passaggio " + (fields.size + 1) else "Ingrediente " + (fields.size + 1)
            setText(value)
            textSize = 15f
            setTextColor(android.graphics.Color.rgb(35,61,57))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = dp(11).toFloat()
                setStroke(dp(1), android.graphics.Color.rgb(199,216,210))
            }
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = if (steps) 2 else 1
            maxLines = if (steps) 5 else 3
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { view, action, event ->
                if (action == EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(view.windowToken, 0)
                    view.clearFocus()
                    true
                } else false
            }
        }
        val remove = Button(context).apply {
            text = "✕"
            isAllCaps = false
            contentDescription = if (steps) "Elimina passaggio" else "Elimina ingrediente"
            setTextColor(android.graphics.Color.rgb(21,103,91))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(234,244,241))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(field.windowToken, 0)
                if (fields.size == 1) {
                    field.setText("")
                } else {
                    val at = fields.indexOf(field)
                    if (at >= 0) {
                        fields.removeAt(at); indexes.removeAt(at)
                        removeView(row)
                        refresh()
                    }
                }
            }
        }
        row.addView(index, LayoutParams(dp(29), LayoutParams.WRAP_CONTENT))
        row.addView(field, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(remove, LayoutParams(dp(48), dp(55)))
        fields.add(field)
        indexes.add(index)
        addView(row, if (indexOfChild(addButton) >= 0) childCount - 1 else childCount,
            LayoutParams(-1, LayoutParams.WRAP_CONTENT))
        refresh()
    }

    private fun refresh() {
        indexes.forEachIndexed { at, title ->
            title.text = (at + 1).toString() + "."
            fields[at].contentDescription = (if (steps) "Passaggio " else "Ingrediente ") + (at + 1)
        }
        addButton.isEnabled = fields.size < limit
    }

    fun entries(): List<String> = fields.map { it.text.toString().trim().replace("\n", " ") }
        .filter { it.isNotBlank() }

    fun value(): String = if (steps) RecipeSteps.numbered(entries()) else entries().joinToString("\n")

    fun requireOne(message: String): Boolean {
        if (entries().isNotEmpty()) return true
        fields.first().error = message
        fields.first().requestFocus()
        return false
    }
}

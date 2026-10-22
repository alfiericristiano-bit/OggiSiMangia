package it.oggisimangia.app

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.*

/** One ingredient per field, one field initially, up to ten. */
class IngredientInputs(context: Context, initial: String, private val changed: (String) -> Unit = {}) : LinearLayout(context) {
    private val fields = mutableListOf<EditText>()
    private val buttons = mutableListOf<Button>()
    private fun dp(n: Int) = (n*resources.displayMetrics.density).toInt()
    init {
        orientation = VERTICAL
        val values = RecipeRanking.terms(initial).take(10).ifEmpty { listOf("") }
        values.forEach { append(it) }
    }
    private fun append(value: String = "") {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        val field = EditText(context).apply {
            hint = if (fields.isEmpty()) "Ingrediente principale" else "Ingrediente ${fields.size+1}"
            contentDescription = hint
            setSingleLine(true); textSize = 16f
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { view, actionId, event ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                    (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                        event.action == android.view.KeyEvent.ACTION_UP)) {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as
                        android.view.inputmethod.InputMethodManager)
                        .hideSoftInputFromWindow(view.windowToken, 0)
                    view.clearFocus()
                    true
                } else false
            }
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setText(value)
            setPadding(dp(8),dp(8),dp(8),dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = dp(11).toFloat()
                setStroke(dp(1), android.graphics.Color.rgb(199,216,210))
            }
            setTextColor(android.graphics.Color.rgb(35,61,57))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
                override fun afterTextChanged(s: Editable?) { changed(values().joinToString(", ")) }
            })
        }
        val add = Button(context).apply {
            text = "＋ Ingrediente"; isAllCaps = false; textSize = 12f
            setTextColor(android.graphics.Color.rgb(21,103,91))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(234,244,241))
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as
                    android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(field.windowToken, 0)
                field.clearFocus()
                if (field.text.toString().isBlank()) { field.error = "Scrivi prima l'ingrediente"; return@setOnClickListener }
                if (fields.size < 10) append()
            }
        }
        row.addView(field, LayoutParams(0,dp(48),1f))
        row.addView(add, LayoutParams(dp(108),dp(48)))
        addView(row, LayoutParams(-1,-2).apply { bottomMargin = dp(5) })
        fields.add(field); buttons.add(add)
        buttons.forEachIndexed { index,b -> b.visibility = if(index == buttons.lastIndex) VISIBLE else INVISIBLE }
        if (fields.size == 10) { add.text = "Massimo 10"; add.isEnabled = false }
    }
    fun values() = fields.map { it.text.toString().trim() }.filter { it.isNotBlank() }
    fun query(allowEmpty: Boolean = false): String? {
        for (field in fields) {
            if (field.text.any { it == ',' || it == ';' || it == '\n' }) {
                field.error = "Un solo ingrediente per casella"; field.requestFocus(); return null
            }
        }
        if (values().isNotEmpty() && fields.first().text.toString().isBlank()) {
            fields.first().error = "Indica prima l'ingrediente principale"
            fields.first().requestFocus()
            return null
        }
        if (!allowEmpty && fields.first().text.toString().isBlank()) {
            fields.first().error = "L'ingrediente principale è obbligatorio (es. pasta, riso, pollo, patate)"
            fields.first().requestFocus()
            return null
        }
        return values().joinToString(", ")
    }
}

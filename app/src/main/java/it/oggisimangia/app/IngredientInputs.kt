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
            hint = "Ingrediente ${fields.size+1}"; contentDescription = hint
            setSingleLine(true); textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setText(value)
            setPadding(dp(8),dp(8),dp(8),dp(8))
            setBackgroundColor(android.graphics.Color.WHITE)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
                override fun afterTextChanged(s: Editable?) { changed(values().joinToString(", ")) }
            })
        }
        val add = Button(context).apply {
            text = "Aggiungi ingrediente"; isAllCaps = false; textSize = 12f
            setOnClickListener {
                if (field.text.toString().isBlank()) { field.error = "Scrivi prima l'ingrediente"; return@setOnClickListener }
                if (fields.size < 10) { append(); fields.last().requestFocus() }
            }
        }
        row.addView(field, LayoutParams(0,dp(60),1f))
        row.addView(add, LayoutParams(dp(122),dp(60)))
        addView(row, LayoutParams(-1,-2).apply { bottomMargin = dp(8) })
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
        if (!allowEmpty && values().isEmpty()) { fields.first().error = "Indica almeno un ingrediente"; fields.first().requestFocus(); return null }
        return values().joinToString(", ")
    }
}

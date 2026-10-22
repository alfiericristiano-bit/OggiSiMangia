package it.oggisimangia.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Accessible month grid with separate meal markers; data comes from SQLite. */
class MealCalendar(context: Context, private val selected: LocalDate,
                   private val markers: (YearMonth) -> Map<String, Set<String>>,
                   private val onSelect: (LocalDate) -> Unit,
                   private val onAdd: (LocalDate) -> Unit) : LinearLayout(context) {
    private var month = YearMonth.from(selected)
    private val lunch = Color.rgb(216, 117, 35)
    private val dinner = Color.rgb(63, 117, 180)
    private val teal = Color.rgb(21, 138, 56)
    init { orientation = VERTICAL; render() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun render() {
        removeAllViews()
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        fun arrow(caption: String, description: String, move: Long) = Button(context).apply {
            text = caption; contentDescription = description
            isAllCaps = false
            textSize = 23f
            setTextColor(teal)
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { month = month.plusMonths(move); render() }
        }
        header.addView(arrow("‹", "Mese precedente", -1), LayoutParams(dp(52), dp(48)))
        header.addView(TextView(context).apply {
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN))
            textSize = 20f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(15,47,48))
        }, LayoutParams(0, dp(48), 1f))
        header.addView(arrow("›", "Mese successivo", 1), LayoutParams(dp(52), dp(48)))
        addView(header)
        val grid = GridLayout(context).apply { columnCount = 7 }
        fun cell(view: android.view.View, height: Int) {
            grid.addView(view, GridLayout.LayoutParams().apply {
                width = 0; this.height = dp(height)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom").forEach { day ->
            cell(TextView(context).apply {
                text = day; textSize = 12f
                gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
                setTextColor(if (day == "Dom") Color.rgb(204, 55, 50) else Color.rgb(81, 99, 90))
            }, 28)
        }
        val occupied = markers(month)
        repeat(month.atDay(1).dayOfWeek.value - 1) { cell(TextView(context), 48) }
        for (number in 1..month.lengthOfMonth()) {
            val date = month.atDay(number)
            val meals = occupied[date.toString()].orEmpty()
            // Default Android View long click respects OS and accessibility long-press delay.
            val dayCell = LinearLayout(context).apply {
                orientation = VERTICAL; gravity = Gravity.CENTER
                isFocusable = true; isClickable = true; isLongClickable = true
                isSelected = date == selected
                contentDescription = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)) +
                    if (meals.isEmpty()) ", nessun pasto pianificato" else ", " + meals.joinToString(" e ")
                background = GradientDrawable().apply {
                    setColor(if (date == selected) teal else Color.WHITE)
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), if (date == selected) teal else Color.rgb(245,242,231))
                }
                setOnClickListener { onSelect(date) }
                setOnLongClickListener { onAdd(date); true }
            }
            dayCell.addView(TextView(context).apply {
                text = number.toString(); textSize = 16f; gravity = Gravity.CENTER
                setTextColor(if (date == selected) Color.WHITE else Color.rgb(20, 36, 31))
                if (date == LocalDate.now() || date == selected) setTypeface(null, Typeface.BOLD)
            })
            val dots = LinearLayout(context).apply { gravity = Gravity.CENTER; importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
            for ((meal, color) in listOf("Pranzo" to lunch, "Cena" to dinner)) {
                if (meal in meals) dots.addView(TextView(context).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                }, LayoutParams(dp(7), dp(7)).apply { setMargins(dp(2), dp(3), dp(2), 0) })
            }
            dayCell.addView(dots, LayoutParams(-1, dp(12)))
            cell(dayCell, 48)
        }
        addView(grid, LayoutParams(-1, -2))
        val legend = LinearLayout(context).apply { gravity = Gravity.CENTER }
        for ((meal, color) in listOf("Pranzo" to lunch, "Cena" to dinner)) {
            legend.addView(TextView(context).apply {
                text = "● $meal  "; textSize = 14f; setTextColor(color); setPadding(0, dp(4), 0, dp(4))
            })
        }
        addView(legend)
    }
}


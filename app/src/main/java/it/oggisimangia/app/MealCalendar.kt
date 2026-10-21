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
    private val lunch = Color.rgb(215, 112, 0)
    private val dinner = Color.rgb(31, 102, 191)
    init { orientation = VERTICAL; render() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun render() {
        removeAllViews()
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        fun arrow(caption: String, description: String, move: Long) = Button(context).apply {
            text = caption; contentDescription = description
            setOnClickListener { month = month.plusMonths(move); render() }
        }
        header.addView(arrow("‹", "Mese precedente", -1), LayoutParams(dp(52), dp(48)))
        header.addView(TextView(context).apply {
            text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN))
            textSize = 18f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
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
        listOf("L", "M", "M", "G", "V", "S", "D").forEach { day ->
            cell(TextView(context).apply { text = day; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD) }, 28)
        }
        val occupied = markers(month)
        repeat(month.atDay(1).dayOfWeek.value - 1) { cell(TextView(context), 46) }
        for (number in 1..month.lengthOfMonth()) {
            val date = month.atDay(number)
            val meals = occupied[date.toString()].orEmpty()
            val dayCell = DayCell(context).apply {
                orientation = VERTICAL; gravity = Gravity.CENTER; isFocusable = true; isClickable = true
                isSelected = date == selected
                contentDescription = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)) +
                    if (meals.isEmpty()) ", nessun pasto pianificato" else ", " + meals.joinToString(" e ")
                if (date == selected) background = GradientDrawable().apply {
                    setColor(Color.rgb(222, 240, 219)); cornerRadius = dp(10).toFloat()
                }
                setOnClickListener { onSelect(date) }
                setOnLongClickListener { onAdd(date); true }
                tooltipText = "Tocca per vedere il giorno; tieni premuto oltre 2 secondi per aggiungere una ricetta"
            }
            dayCell.addView(TextView(context).apply {
                text = number.toString(); textSize = 16f; gravity = Gravity.CENTER
                if (date == LocalDate.now()) setTypeface(null, Typeface.BOLD)
            })
            val dots = LinearLayout(context).apply { gravity = Gravity.CENTER; importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
            for ((meal, color) in listOf("Pranzo" to lunch, "Cena" to dinner)) {
                if (meal in meals) dots.addView(TextView(context).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                }, LayoutParams(dp(7), dp(7)).apply { setMargins(dp(2), dp(3), dp(2), 0) })
            }
            dayCell.addView(dots, LayoutParams(-1, dp(12)))
            cell(dayCell, 46)
        }
        addView(grid, LayoutParams(-1, -2))
        val legend = LinearLayout(context).apply { gravity = Gravity.CENTER }
        for ((meal, color) in listOf("Pranzo" to lunch, "Cena" to dinner)) {
            legend.addView(TextView(context).apply {
                text = "● $meal  "; textSize = 14f; setTextColor(color); setPadding(0, dp(8), 0, dp(8))
            })
        }
        addView(legend)
    }
}

/** Owns the physical hold threshold instead of Android's default short long-click delay. */
private class DayCell(context: Context) : LinearLayout(context) {
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var held = false
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private val hold = Runnable {
        if (tracking && isAttachedToWindow) {
            held = true
            isPressed = false
            performLongClick()
        }
    }
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; tracking = true; held = false; isPressed = true
                postDelayed(hold, 2100L)
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) cancelHold()
            }
            android.view.MotionEvent.ACTION_UP -> {
                val click = tracking && !held
                cancelHold()
                if (click) performClick()
            }
            android.view.MotionEvent.ACTION_CANCEL, android.view.MotionEvent.ACTION_POINTER_DOWN -> cancelHold()
        }
        return true
    }
    override fun performClick(): Boolean = super.performClick()
    private fun cancelHold() { removeCallbacks(hold); tracking = false; isPressed = false }
    override fun onDetachedFromWindow() { cancelHold(); super.onDetachedFromWindow() }
}

package it.oggisimangia.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Modal guide: never navigate away from the editor or discard unsaved recipe fields. */
object GuideDialog {
    fun show(activity: Activity, currentScreen: String? = null) {
        if (activity.isFinishing || activity.isDestroyed) return
        val density = activity.resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val ink = Color.rgb(15, 47, 48)
        val green = Color.rgb(21, 123, 52)
        val light = Color.rgb(239, 248, 237)
        fun background(color: Int) = GradientDrawable().apply {
            setColor(color); cornerRadius = dp(15).toFloat()
        }
        fun button(caption: String, onClick: () -> Unit) = Button(activity).apply {
            text = caption; isAllCaps = false; textSize = 13f
            setTextColor(green); this.background = background(light)
            setOnClickListener { onClick() }
        }
        fun text(value: String, font: Float, bold: Boolean = false) = TextView(activity).apply {
            text = value; textSize = font
            setTextColor(ink)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(9))
            setBackgroundColor(Color.rgb(255, 250, 241))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val title = text("Guida all'app", 19f, true)
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(1), dp(10), dp(1), dp(12))
        }
        scroll.addView(body)
        val controls = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
        }
        var dialog: AlertDialog? = null
        var position = currentScreen?.let(HelpGuide::forScreen) ?: -1
        var render: () -> Unit = {}
        header.addView(button("☷ Indice") { position = -1; render() },
            LinearLayout.LayoutParams(dp(85), dp(44)))
        header.addView(button("✕") { dialog?.dismiss() },
            LinearLayout.LayoutParams(dp(48), dp(44)).apply { marginStart = dp(6) })
        root.addView(header)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val previous = button("‹ Precedente") {
            position = if (position <= 0) -1 else position - 1
            render()
        }
        val next = button("Successiva ›") {
            position = if (position < 0) 0 else (position + 1).coerceAtMost(HelpGuide.pages.lastIndex)
            render()
        }
        controls.addView(previous, LinearLayout.LayoutParams(0, dp(52), 1f))
        controls.addView(next, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(7) })
        root.addView(controls)

        render = {
            body.removeAllViews()
            if (position == -1) {
                body.addView(text("Scegli un argomento", 22f, true))
                body.addView(text("Scopri in modo semplice a cosa servono le schede e i pulsanti. Quando chiudi la guida torni esattamente alla schermata precedente.", 15f))
                HelpGuide.pages.forEachIndexed { index, page ->
                    val choice = button((index + 1).toString() + ".  " + page.title + "   ›") {
                        position = index
                        render()
                    }
                    choice.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    choice.setPadding(dp(12), 0, dp(6), 0)
                    body.addView(choice, LinearLayout.LayoutParams(-1, dp(54)).apply {
                        bottomMargin = dp(7)
                    })
                }
            } else {
                val page = HelpGuide.pages[position]
                body.addView(text("CAPITOLO " + (position + 1) + " DI " + HelpGuide.pages.size, 12f, true).apply {
                    setTextColor(green)
                })
                body.addView(text(page.title, 22f, true))
                body.addView(text(page.introduction, 15f))
                body.addView(text("Pulsanti e funzioni", 18f, true))
                page.buttons.forEach { entry ->
                    val card = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        background = background(Color.WHITE)
                        setPadding(dp(11), dp(8), dp(11), dp(9))
                    }
                    card.addView(text(entry.name, 16f, true).apply { setTextColor(green) })
                    card.addView(text(entry.explanation, 14f))
                    body.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(8)
                    })
                }
                if (page.note.isNotBlank()) {
                    body.addView(text("Da ricordare", 16f, true))
                    body.addView(text(page.note, 14f))
                }
            }
            title.text = if (position == -1) "Guida all'app"
                else "Guida · " + (position + 1) + "/" + HelpGuide.pages.size
            previous.isEnabled = position >= 0
            previous.alpha = if (position >= 0) 1f else 0.35f
            next.isEnabled = position < HelpGuide.pages.lastIndex
            next.alpha = if (next.isEnabled) 1f else 0.35f
            scroll.scrollTo(0, 0)
        }
        dialog = AlertDialog.Builder(activity).setView(root).create()
        render()
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels - dp(20)).coerceAtMost(dp(510)),
            (activity.resources.displayMetrics.heightPixels * 0.88f).toInt()
        )
    }
}

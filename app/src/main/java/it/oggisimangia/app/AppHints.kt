package it.oggisimangia.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Reusable, opt-out help dialog for optional usage explanations.
 * Opt-out is stored in app SharedPreferences, independent of app version.
 * Warnings, confirmations and errors must NOT use this facility.
 */
object AppHints {
    private const val PREFS = "oggisimangia_help_preferences"
    private val seenInSession = mutableSetOf<String>()

    fun showOnce(activity: Activity, id: String, title: String, explanation: String) {
        val settings = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (settings.getBoolean("dismiss_$id", false) || !seenInSession.add(id)) return
        showDialog(activity, id, title, explanation)
    }

    /** Explicit help always opens even when automatic tips have been turned off. */
    fun showAgain(activity: Activity, id: String, title: String, explanation: String) =
        showDialog(activity, id, title, explanation)

    private fun showDialog(activity: Activity, id: String, title: String, explanation: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(10))
        }
        content.addView(TextView(activity).apply {
            text = explanation
            textSize = 16f
        })
        val optOut = CheckBox(activity).apply {
            text = "Non mostrare più questo messaggio"
            isChecked = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("dismiss_$id", false)
            textSize = 14f
        }
        content.addView(optOut)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(content)
            .setPositiveButton("Ho capito") { _, _ ->
                // Remember only an explicit choice; unchecked remains temporary for this session.
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("dismiss_$id", optOut.isChecked).apply()
            }
            .create()
        dialog.show()
        dialog.window?.setLayout(
            minOf(dp(350), activity.resources.displayMetrics.widthPixels - dp(32)),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

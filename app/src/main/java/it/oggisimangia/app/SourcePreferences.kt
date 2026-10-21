package it.oggisimangia.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SourcePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("recipe_sources", Context.MODE_PRIVATE)
    fun load(): LinkedHashMap<String,String> {
        // A missing key means a fresh installation; an empty saved list means all sources removed.
        val stored = prefs.getString("ordered", null)
            ?: return LinkedHashMap(RecipeSources.defaults)
        val result = linkedMapOf<String,String>()
        try {
            val array = JSONArray(stored)
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                val name = row.optString("name").trim()
                val domain = row.optString("domain")
                if (name.isNotBlank() && RecipeSources.normalizeDomain(domain) == domain && domain !in result.values) result[name] = domain
            }
        } catch (_: Exception) { return LinkedHashMap(RecipeSources.defaults) }
        // Never silently restore deleted default sites on app restart or update.
        return result
    }
    fun save(sites: Map<String,String>) {
        val array = JSONArray()
        sites.forEach { (name,domain) -> array.put(JSONObject().put("name",name).put("domain",domain)) }
        check(prefs.edit().putString("ordered",array.toString()).commit())
        RecipeSources.configure(sites)
    }
}

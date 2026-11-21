package id.smooth.launcher.data

import org.json.JSONArray
import org.json.JSONObject

data class Prefs(
    val theme: Int = 0,            // 0 sistem, 1 terang, 2 gelap
    val homeCols: Int = 4,
    val drawerCols: Int = 4,
    val iconDp: Int = 56,
    val labelSp: Int = 12,
    val animPercent: Int = 100,    // kecepatan animasi (50..200)
    val gestures: Boolean = true,
    val blur: Boolean = true,
    val favorites: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    fun toJson(): String = JSONObject().apply {
        put("v", 1); put("theme", theme); put("homeCols", homeCols); put("drawerCols", drawerCols)
        put("iconDp", iconDp); put("labelSp", labelSp); put("animPercent", animPercent)
        put("gestures", gestures); put("blur", blur)
        put("favorites", JSONArray(favorites)); put("dock", JSONArray(dock)); put("hidden", JSONArray(hidden.toList()))
    }.toString()

    companion object {
        /** Null jika JSON tidak valid / bukan backup Smooth Launcher. */
        fun fromJsonOrNull(s: String?): Prefs? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                if (!o.has("v")) return null
                val d = Prefs()
                Prefs(
                    theme = o.optInt("theme", d.theme).coerceIn(0, 2),
                    homeCols = o.optInt("homeCols", d.homeCols).coerceIn(3, 6),
                    drawerCols = o.optInt("drawerCols", d.drawerCols).coerceIn(3, 6),
                    iconDp = o.optInt("iconDp", d.iconDp).coerceIn(40, 80),
                    labelSp = o.optInt("labelSp", d.labelSp).coerceIn(9, 16),
                    animPercent = o.optInt("animPercent", d.animPercent).coerceIn(50, 200),
                    gestures = o.optBoolean("gestures", d.gestures),
                    blur = o.optBoolean("blur", d.blur),
                    favorites = o.optJSONArray("favorites").strings(),
                    dock = o.optJSONArray("dock").strings().take(5),
                    hidden = o.optJSONArray("hidden").strings().toSet(),
                )
            } catch (e: Exception) { null }
        }
        fun fromJson(s: String?): Prefs = fromJsonOrNull(s) ?: Prefs()
    }
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }

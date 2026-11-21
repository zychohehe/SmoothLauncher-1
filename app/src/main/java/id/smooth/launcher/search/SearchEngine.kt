package id.smooth.launcher.search

import id.smooth.launcher.data.AppInfo

/** Pencarian instan: awalan label > awalan kata > mengandung. */
object SearchEngine {
    fun filter(apps: List<AppInfo>, query: String): List<AppInfo> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps
        return apps.mapNotNull { a ->
            val l = a.label.lowercase()
            val score = when {
                l.startsWith(q) -> 0
                l.split(' ', '-', '.').any { it.startsWith(q) } -> 1
                l.contains(q) -> 2
                else -> -1
            }
            if (score < 0) null else Pair(score, a)
        }.sortedWith(compareBy({ it.first }, { it.second.label.lowercase() })).map { it.second }
    }
}

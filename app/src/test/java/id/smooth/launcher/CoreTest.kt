package id.smooth.launcher

import id.smooth.launcher.data.AppInfo
import id.smooth.launcher.data.Prefs
import id.smooth.launcher.search.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreTest {
    private val apps = listOf(
        AppInfo("Chrome", "p.chrome", "A"), AppInfo("Camera", "p.cam", "B"),
        AppInfo("Scanner", "p.scan", "C"), AppInfo("Kalkulator", "p.calc", "D"),
    )

    @Test fun blankQueryReturnsAll() = assertEquals(4, SearchEngine.filter(apps, "  ").size)
    @Test fun prefixRanksBeforeContains() = assertEquals(listOf("Camera", "Scanner"), SearchEngine.filter(apps, "ca").map { it.label })
    @Test fun noMatch() = assertEquals(0, SearchEngine.filter(apps, "zzz").size)

    @Test fun prefsRoundTrip() {
        val p = Prefs(theme = 2, homeCols = 5, favorites = listOf("a/b"), dock = listOf("c/d"), hidden = setOf("e/f"))
        assertEquals(p, Prefs.fromJsonOrNull(p.toJson()))
    }
    @Test fun invalidBackupRejected() { assertNull(Prefs.fromJsonOrNull("bukan json")); assertNull(Prefs.fromJsonOrNull("{}")) }
    @Test fun valuesAreClamped() = assertEquals(6, Prefs.fromJsonOrNull("""{"v":1,"homeCols":99}""")!!.homeCols)
}

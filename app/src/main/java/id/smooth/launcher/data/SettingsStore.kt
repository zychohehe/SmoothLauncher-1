package id.smooth.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("smooth_settings")

/** Seluruh pengaturan disimpan sebagai satu JSON di DataStore; backup = isi JSON itu. */
class SettingsStore(private val ctx: Context) {
    private val key = stringPreferencesKey("prefs_json")

    val prefs: Flow<Prefs> = ctx.ds.data.map { Prefs.fromJson(it[key]) }.distinctUntilChanged()

    suspend fun update(f: (Prefs) -> Prefs) {
        ctx.ds.edit { it[key] = f(Prefs.fromJson(it[key])).toJson() }
    }

    suspend fun exportJson(): String = prefs.first().toJson()

    suspend fun restore(json: String): Boolean {
        val p = Prefs.fromJsonOrNull(json) ?: return false
        ctx.ds.edit { it[key] = p.toJson() }
        return true
    }
}

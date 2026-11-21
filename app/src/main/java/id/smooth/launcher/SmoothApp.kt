package id.smooth.launcher

import android.app.Application
import android.content.Context
import id.smooth.launcher.data.AppRepository
import id.smooth.launcher.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SmoothApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store: SettingsStore by lazy { SettingsStore(this) }
    val repo: AppRepository by lazy { AppRepository(this, appScope) }
}

val Context.sm: SmoothApp get() = applicationContext as SmoothApp

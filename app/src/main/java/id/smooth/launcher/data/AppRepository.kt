package id.smooth.launcher.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.LruCache
import android.view.View
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Daftar aplikasi di-cache di memori dan hanya diperbarui lewat LauncherApps.Callback
 * (tanpa polling). Ikon dimuat lazy dan di-cache sebagai Bitmap (LruCache).
 */
class AppRepository(private val ctx: Context, private val scope: CoroutineScope) {
    private val la = ctx.getSystemService(LauncherApps::class.java)
    private val user: UserHandle = Process.myUserHandle()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    @Volatile private var infos: Map<String, LauncherActivityInfo> = emptyMap()
    private var job: Job? = null

    private val icons = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtMost(16 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed()
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed()
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = changed()
    }

    init {
        la.registerCallback(callback, Handler(Looper.getMainLooper()))
        job = scope.launch { load() }
    }

    private fun changed() {
        job?.cancel()
        job = scope.launch { delay(300); load() }   // debounce
    }

    private suspend fun load() = withContext(Dispatchers.IO) {
        val list = la.getActivityList(null, user).filter { it.componentName.packageName != ctx.packageName }
        infos = list.associateBy { "${it.componentName.packageName}/${it.componentName.className}" }
        icons.evictAll()
        _apps.value = list.map {
            AppInfo(it.label.toString(), it.componentName.packageName, it.componentName.className)
        }.sortedBy { it.label.lowercase() }
    }

    fun cachedIcon(app: AppInfo, px: Int): Bitmap? = icons.get("${app.key}@$px")

    suspend fun icon(app: AppInfo, px: Int): Bitmap = cachedIcon(app, px) ?: withContext(Dispatchers.IO) {
        val d = infos[app.key]?.getIcon(0) ?: ctx.packageManager.defaultActivityIcon
        d.toBitmap(px, px).also { icons.put("${app.key}@$px", it) }
    }

    fun launch(app: AppInfo, source: View?) {
        val cn = ComponentName(app.packageName, app.className)
        val opts = source?.let { ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }
        val bounds = source?.let { v -> Rect().also { v.getGlobalVisibleRect(it) } }
        try {
            la.startMainActivity(cn, user, bounds, opts)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Tidak dapat membuka ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun showAppInfo(app: AppInfo) {
        try {
            la.startAppDetailsActivity(ComponentName(app.packageName, app.className), user, null, null)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Tidak dapat membuka info aplikasi", Toast.LENGTH_SHORT).show()
        }
    }
}

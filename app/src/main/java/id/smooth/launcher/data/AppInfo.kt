package id.smooth.launcher.data

/** Representasi ringan aplikasi (tanpa tipe Android agar mudah diuji). */
data class AppInfo(val label: String, val packageName: String, val className: String) {
    val key: String = "$packageName/$className"
}

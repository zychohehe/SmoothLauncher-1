# Smooth Launcher
Launcher Android native (Kotlin, Views + RecyclerView) — Android 16 (minSdk 36), arm64-v8a.

## Build
- Android Studio terbaru (AGP 8.11): *Open* folder ini → Run.
- Terminal: `gradle wrapper --gradle-version 8.14.3` sekali, lalu `./gradlew :app:assembleDebug`.
- GitHub Actions: push ke GitHub → artifact **SmoothLauncher-Android16-ARM64**.
- Release tanpa signing menghasilkan `app-release-unsigned.apk`.
- Setelah install: Pengaturan → Aplikasi default → Aplikasi Home → Smooth Launcher.

## Fitur yang diimplementasikan
Home (jam/tanggal via TextClock tanpa polling, search bar, grid Home, Dock maks 5), laci aplikasi (ikon asli,
pencarian instan, urut abjad, kolom kustom, sembunyikan aplikasi, pembaruan via LauncherApps.Callback + cache),
animasi spring (laci, scale-open, fade/scale Home, feedback sentuh; menghormati "Remove animations"), blur latar
opsional, tema terang/gelap/sistem (untuk permukaan launcher), wallpaper (picker sistem), gestur (geser atas/bawah,
tahan lama), pengaturan (DataStore), backup/restore JSON, info aplikasi, copot pemasangan.

## Belum diimplementasikan
Folder aplikasi, multi-halaman Home, widget host, cuaca, double-tap kunci layar, panel notifikasi lewat swipe-down
(swipe-down membuka pencarian), recent apps.

## Catatan jujur
- Project BELUM dikompilasi/diuji oleh pembuatnya (lingkungan tanpa Android SDK/jaringan). Build pertama di CI/Android Studio
  mungkin menampilkan error kecil; perbaiki berdasarkan log.
- `abiFilters` hanya memfilter library native; app ini murni Kotlin tanpa .so.
- Tidak ada gradle-wrapper.jar (tidak bisa dibuat offline); CI memakai Gradle terpasang.
- Klaim performa belum diukur.

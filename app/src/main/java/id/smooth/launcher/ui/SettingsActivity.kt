package id.smooth.launcher.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import id.smooth.launcher.data.Prefs
import id.smooth.launcher.sm
import id.smooth.launcher.util.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    private lateinit var box: LinearLayout

    private val exportL = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) lifecycleScope.launch {
            val json = sm.store.exportJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } }.isSuccess
            }
            toast(if (ok) "Backup tersimpan" else "Gagal menyimpan backup")
        }
    }
    private val importL = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            val ok = text != null && sm.store.restore(text)
            toast(if (ok) "Pengaturan dipulihkan" else "File backup tidak valid")
            if (ok) build(sm.store.prefs.first())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(24)) }
        val sv = ScrollView(this).apply { addView(box) }
        setContentView(sv)
        ViewCompat.setOnApplyWindowInsetsListener(sv) { v, w ->
            val s = w.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(s.left, s.top, s.right, s.bottom); w
        }
        lifecycleScope.launch { build(sm.store.prefs.first()) }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun save(refresh: Boolean = false, f: (Prefs) -> Prefs) {
        lifecycleScope.launch { sm.store.update(f); if (refresh) build(sm.store.prefs.first()) }
    }

    private fun build(p: Prefs) {
        box.removeAllViews()
        text("Smooth Launcher", 28f, bold = true, top = 16)

        section("Appearance")
        choice("Tema", listOf("Ikuti sistem", "Terang", "Gelap"), p.theme) { v -> save(true) { it.copy(theme = v) } }

        section("Home Screen")
        slider("Kolom Home", 3, 6, p.homeCols) { v -> save { it.copy(homeCols = v) } }
        action("Ganti wallpaper") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")) }
        note("Dock & Home: tahan lama aplikasi di laci, lalu pilih Tambah ke Home / Dock.")

        section("App Drawer")
        slider("Kolom laci aplikasi", 3, 6, p.drawerCols) { v -> save { it.copy(drawerCols = v) } }
        action("Aplikasi tersembunyi (${p.hidden.size})") { hiddenDialog(p) }

        section("Icons")
        slider("Ukuran ikon (dp)", 40, 80, p.iconDp) { v -> save { it.copy(iconDp = v) } }
        slider("Ukuran label (sp)", 9, 16, p.labelSp) { v -> save { it.copy(labelSp = v) } }

        section("Gestures")
        toggle("Aktifkan gestur (geser atas = laci, geser bawah = cari)", p.gestures) { c -> save { it.copy(gestures = c) } }

        section("Animations")
        slider("Kecepatan animasi (%)", 50, 200, p.animPercent) { v -> save { it.copy(animPercent = v) } }
        note("Animasi mengikuti pengaturan sistem 'Remove animations'.")

        section("Performance")
        toggle("Blur latar saat laci terbuka", p.blur) { c -> save { it.copy(blur = c) } }

        section("Backup & Restore")
        action("Ekspor pengaturan") { exportL.launch("smooth-launcher-backup.json") }
        action("Impor pengaturan") { importL.launch(arrayOf("application/json", "text/plain")) }

        section("About Smooth Launcher")
        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        note("Versi $ver · Android 16 · arm64-v8a")
        action("Jadikan launcher default") { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }

    private fun text(t: String, sp: Float, bold: Boolean = false, top: Int = 0) = box.addView(TextView(this).apply {
        text = t; textSize = sp; if (bold) setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(top), 0, dp(4))
    })
    private fun section(t: String) = box.addView(TextView(this).apply {
        text = t.uppercase(); textSize = 12f; setTypeface(typeface, Typeface.BOLD); alpha = 0.6f; setPadding(0, dp(28), 0, dp(6))
    })
    private fun note(t: String) = box.addView(TextView(this).apply { text = t; textSize = 13f; alpha = 0.65f; setPadding(0, dp(6), 0, dp(6)) })

    private fun action(t: String, onClick: () -> Unit) = box.addView(TextView(this).apply {
        text = t; textSize = 16f; gravity = Gravity.CENTER_VERTICAL; minHeight = dp(48)
        setOnClickListener { onClick() }
    })

    private fun toggle(t: String, v: Boolean, onChange: (Boolean) -> Unit) = box.addView(Switch(this).apply {
        text = t; isChecked = v; textSize = 16f; minHeight = dp(48)
        setOnCheckedChangeListener { _, c -> onChange(c) }
    })

    private fun choice(t: String, opts: List<String>, sel: Int, onPick: (Int) -> Unit) = box.addView(TextView(this).apply {
        text = "$t: ${opts[sel]}"; textSize = 16f; gravity = Gravity.CENTER_VERTICAL; minHeight = dp(48)
        setOnClickListener {
            AlertDialog.Builder(this@SettingsActivity).setTitle(t)
                .setSingleChoiceItems(opts.toTypedArray(), sel) { d, w -> onPick(w); d.dismiss() }.show()
        }
    })

    private fun slider(t: String, min: Int, max: Int, value: Int, onCommit: (Int) -> Unit) {
        val label = TextView(this).apply { text = "$t: $value"; textSize = 16f; setPadding(0, dp(8), 0, 0) }
        val bar = SeekBar(this).apply {
            this.max = max - min; progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { label.text = "$t: ${p + min}" }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) { onCommit(s.progress + min) }
            })
        }
        box.addView(label); box.addView(bar)
    }

    private fun hiddenDialog(p: Prefs) {
        val apps = sm.repo.apps.value
        val sel = p.hidden.toMutableSet()
        AlertDialog.Builder(this).setTitle("Aplikasi tersembunyi")
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), BooleanArray(apps.size) { apps[it].key in sel }) { _, i, c ->
                if (c) sel.add(apps[i].key) else sel.remove(apps[i].key)
            }
            .setPositiveButton("Simpan") { _, _ -> save(true) { it.copy(hidden = sel.toSet()) } }
            .setNegativeButton("Batal", null).show()
    }
}

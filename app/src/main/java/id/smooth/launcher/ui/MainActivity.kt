package id.smooth.launcher.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.widget.doAfterTextChanged
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import id.smooth.launcher.R
import id.smooth.launcher.data.AppInfo
import id.smooth.launcher.data.Prefs
import id.smooth.launcher.search.SearchEngine
import id.smooth.launcher.sm
import id.smooth.launcher.util.Palette
import id.smooth.launcher.util.dp
import id.smooth.launcher.util.glass
import id.smooth.launcher.util.motionScale
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private lateinit var root: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var query: EditText
    private lateinit var grid: RecyclerView
    private lateinit var favorites: RecyclerView
    private lateinit var dock: RecyclerView
    private lateinit var hint: TextView
    private lateinit var searchBar: TextView
    private lateinit var drawerAdapter: AppAdapter
    private lateinit var favAdapter: AppAdapter
    private lateinit var dockAdapter: AppAdapter

    private var prefs = Prefs()
    private var allApps = emptyList<AppInfo>()
    private var drawerOpen = false
    private var drawerAnim: SpringAnimation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root); home = findViewById(R.id.home); drawer = findViewById(R.id.drawer)
        query = findViewById(R.id.query); grid = findViewById(R.id.grid)
        favorites = findViewById(R.id.favorites); dock = findViewById(R.id.dock)
        hint = findViewById(R.id.hint); searchBar = findViewById(R.id.searchBar)

        val repo = sm.repo
        drawerAdapter = AppAdapter(repo, lifecycleScope, false, { a, v -> repo.launch(a, v) }, { a, v -> showAppMenu(a, v, DRAWER) })
        favAdapter = AppAdapter(repo, lifecycleScope, false, { a, v -> repo.launch(a, v) }, { a, v -> showAppMenu(a, v, HOME) })
        dockAdapter = AppAdapter(repo, lifecycleScope, true, { a, v -> repo.launch(a, v) }, { a, v -> showAppMenu(a, v, DOCK) })

        grid.layoutManager = GridLayoutManager(this, 4); grid.adapter = drawerAdapter
        favorites.layoutManager = GridLayoutManager(this, 4); favorites.adapter = favAdapter; favorites.itemAnimator = null
        dock.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        dock.adapter = dockAdapter; dock.itemAnimator = null

        query.doAfterTextChanged { renderDrawer() }
        query.setOnEditorActionListener { _, _, _ ->
            drawerAdapter.currentList.firstOrNull()?.let { repo.launch(it, null) }
            true
        }
        searchBar.setOnClickListener { animateDrawer(open = true, focus = true) }

        root.doOnLayout { if (!drawerOpen) drawer.translationY = it.height.toFloat() }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, w ->
            val s = w.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = w.getInsets(WindowInsetsCompat.Type.ime())
            home.setPadding(s.left + dp(8), s.top + dp(24), s.right + dp(8), s.bottom + dp(8))
            drawer.setPadding(s.left + dp(8), s.top + dp(16), s.right + dp(8), maxOf(s.bottom, ime.bottom) + dp(8))
            w
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (drawerOpen) animateDrawer(open = false) }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(sm.store.prefs, repo.apps) { p, a -> p to a }.collect { (p, a) ->
                    prefs = p; allApps = a
                    applyPrefs(); render()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (drawerOpen) animateDrawer(open = false)   // tombol Home menutup laci
    }

    override fun onStop() {
        super.onStop()
        if (drawerOpen) animateDrawer(open = false, instant = true)
    }

    // ---------- Render ----------
    private fun applyPrefs() {
        val dark = Palette.isDark(this, prefs)
        val iconPx = dp(prefs.iconDp)
        favAdapter.style = AppAdapter.Style(iconPx, prefs.labelSp, Color.WHITE)
        dockAdapter.style = AppAdapter.Style(iconPx, prefs.labelSp, Color.WHITE, showLabel = false)
        drawerAdapter.style = AppAdapter.Style(iconPx, prefs.labelSp, Palette.text(dark))
        (favorites.layoutManager as GridLayoutManager).spanCount = prefs.homeCols
        (grid.layoutManager as GridLayoutManager).spanCount = prefs.drawerCols
        drawer.background = glass(this, Palette.drawerGlass(dark), 28f, topOnly = true)
        dock.background = glass(this, Palette.barGlass(dark), 28f)
        searchBar.background = glass(this, Palette.barGlass(dark), 24f)
        query.background = glass(this, Palette.field(dark), 14f)
        query.setTextColor(Palette.text(dark)); query.setHintTextColor(Palette.hint(dark))
    }

    private fun render() {
        val byKey = allApps.associateBy { it.key }
        val fav = prefs.favorites.mapNotNull(byKey::get)
        val dk = prefs.dock.mapNotNull(byKey::get)
        favAdapter.submitList(fav); dockAdapter.submitList(dk)
        dock.visibility = if (dk.isEmpty()) View.GONE else View.VISIBLE
        hint.visibility = if (fav.isEmpty() && dk.isEmpty()) View.VISIBLE else View.GONE
        renderDrawer()
    }

    private fun renderDrawer() {
        val visible = allApps.filter { it.key !in prefs.hidden }
        drawerAdapter.submitList(SearchEngine.filter(visible, query.text.toString()))
    }

    // ---------- Laci (spring) ----------
    private fun animateDrawer(open: Boolean, focus: Boolean = false, instant: Boolean = false) {
        drawerOpen = open
        val target = if (open) 0f else root.height.toFloat()
        drawerAnim?.cancel()
        if (open) { drawer.visibility = View.VISIBLE; setBlur(true) } else hideIme()
        if (instant || motionScale() == 0f) {
            drawer.translationY = target; onProgress(); finishDrawer(open, focus); return
        }
        drawerAnim = SpringAnimation(drawer, DynamicAnimation.TRANSLATION_Y).apply {
            setSpring(SpringForce(target).apply {
                dampingRatio = 0.9f
                stiffness = (600f * prefs.animPercent / 100f).coerceIn(150f, 1500f)
            })
            addUpdateListener { _, _, _ -> onProgress() }
            addEndListener { _, canceled, _, _ -> if (!canceled) finishDrawer(open, focus) }
            start()
        }
    }

    private fun onProgress() {
        val p = (1f - drawer.translationY / root.height.coerceAtLeast(1)).coerceIn(0f, 1f)
        home.alpha = 1f - p; home.scaleX = 1f - 0.06f * p; home.scaleY = 1f - 0.06f * p
    }

    private fun finishDrawer(open: Boolean, focus: Boolean) {
        if (open) {
            if (focus) {
                query.requestFocus()
                WindowCompat.getInsetsController(window, query).show(WindowInsetsCompat.Type.ime())
            }
        } else {
            drawer.visibility = View.INVISIBLE
            query.setText(""); grid.scrollToPosition(0)
            setBlur(false)
        }
    }

    private fun hideIme() { WindowCompat.getInsetsController(window, root).hide(WindowInsetsCompat.Type.ime()) }

    private fun setBlur(on: Boolean) {
        try { window.setBackgroundBlurRadius(if (on && prefs.blur) dp(28) else 0) } catch (_: Exception) {}
    }

    // ---------- Gestur ----------
    private val detector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (!prefs.gestures || e1 == null) return false
                val dx = e2.x - e1.x; val dy = e2.y - e1.y
                if (abs(dy) < abs(dx) * 1.5f || abs(dy) < dp(80)) return false
                when {
                    !drawerOpen && dy < 0 && vy < -600 -> animateDrawer(open = true)
                    !drawerOpen && dy > 0 && vy > 600 -> animateDrawer(open = true, focus = true)
                    drawerOpen && dy > 0 && vy > 600 && !grid.canScrollVertically(-1) -> animateDrawer(open = false)
                }
                return false
            }
            override fun onLongPress(e: MotionEvent) {
                if (drawerOpen || hits(favorites, e) || hits(dock, e)) return
                root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showHomeMenu()
            }
        })
    }

    private fun hits(rv: RecyclerView, e: MotionEvent): Boolean {
        if (rv.visibility != View.VISIBLE) return false
        val l = IntArray(2); rv.getLocationOnScreen(l)
        return rv.findChildViewUnder(e.rawX - l[0], e.rawY - l[1]) != null
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ---------- Menu ----------
    private fun showHomeMenu() {
        PopupMenu(this, searchBar).apply {
            menu.add(0, 1, 0, "Ganti wallpaper"); menu.add(0, 2, 0, "Pengaturan launcher")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
                    2 -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
                true
            }
            show()
        }
    }

    private fun edit(f: (Prefs) -> Prefs) { lifecycleScope.launch { sm.store.update(f) } }

    private fun showAppMenu(app: AppInfo, anchor: View, from: Int) {
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        PopupMenu(this, anchor).apply {
            if (from == DRAWER) {
                if (app.key !in prefs.favorites) menu.add(0, 1, 0, "Tambah ke Home")
                if (app.key !in prefs.dock && prefs.dock.size < 5) menu.add(0, 2, 0, "Tambah ke Dock")
                menu.add(0, 3, 0, "Sembunyikan")
            } else menu.add(0, 4, 0, "Hapus dari Home")
            menu.add(0, 5, 0, "Info aplikasi")
            if (from == DRAWER) menu.add(0, 6, 0, "Copot pemasangan")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> edit { it.copy(favorites = it.favorites + app.key) }
                    2 -> edit { it.copy(dock = it.dock + app.key) }
                    3 -> edit { it.copy(hidden = it.hidden + app.key) }
                    4 -> if (from == HOME) edit { it.copy(favorites = it.favorites - app.key) } else edit { it.copy(dock = it.dock - app.key) }
                    5 -> sm.repo.showAppInfo(app)
                    6 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                }
                true
            }
            show()
        }
    }

    private companion object { const val DRAWER = 0; const val HOME = 1; const val DOCK = 2 }
}

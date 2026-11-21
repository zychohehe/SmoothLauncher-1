package id.smooth.launcher.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
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
    private var launched = false
    private var lastBlur = 0

    // state drag
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragStartDy = 0f
    private var trans0 = 0f
    private var vt: VelocityTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root); home = findViewById(R.id.home); drawer = findViewById(R.id.drawer)
        query = findViewById(R.id.query); grid = findViewById(R.id.grid)
        favorites = findViewById(R.id.favorites); dock = findViewById(R.id.dock)
        hint = findViewById(R.id.hint); searchBar = findViewById(R.id.searchBar)

        drawerAdapter = AppAdapter(sm.repo, lifecycleScope, false, { a, v -> launchApp(a, v) }, { a, v -> showAppMenu(a, v, DRAWER) })
        favAdapter = AppAdapter(sm.repo, lifecycleScope, false, { a, v -> launchApp(a, v) }, { a, v -> showAppMenu(a, v, HOME) })
        dockAdapter = AppAdapter(sm.repo, lifecycleScope, true, { a, v -> launchApp(a, v) }, { a, v -> showAppMenu(a, v, DOCK) })

        grid.layoutManager = GridLayoutManager(this, 4); grid.adapter = drawerAdapter
        favorites.layoutManager = GridLayoutManager(this, 4); favorites.adapter = favAdapter; favorites.itemAnimator = null
        dock.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        dock.adapter = dockAdapter; dock.itemAnimator = null

        query.doAfterTextChanged { renderDrawer() }
        query.setOnEditorActionListener { _, _, _ ->
            drawerAdapter.currentList.firstOrNull()?.let { launchApp(it, null) }
            true
        }
        searchBar.setOnClickListener { animateDrawer(open = true, focus = true) }

        root.doOnLayout { if (!drawerOpen) drawer.translationY = it.height.toFloat() }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, w ->
            val s = w.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = w.getInsets(WindowInsetsCompat.Type.ime())
            home.setPadding(s.left + dp(8), s.top + dp(24), s.right + dp(8), s.bottom + dp(8))
            drawer.setPadding(s.left + dp(8), s.top + dp(12), s.right + dp(8), maxOf(s.bottom, ime.bottom) + dp(8))
            w
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (drawerOpen) animateDrawer(open = false) }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(sm.store.prefs, sm.repo.apps) { p, a -> p to a }.collect { (p, a) ->
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

    override fun onResume() {
        super.onResume()
        if (launched) { launched = false; playReturn() }
    }

    // ---------- Buka / tutup aplikasi ----------
    private fun dur(ms: Long): Long = (ms * 100f / prefs.animPercent).toLong()

    private fun launchApp(app: AppInfo, v: View?) {
        val still = motionScale() == 0f
        val target: View = if (drawerOpen) drawer else home
        if (!still) {
            target.animate().cancel()
            target.animate().scaleX(1.12f).scaleY(1.12f).alpha(0f).setDuration(dur(280))
                .setInterpolator(DecelerateInterpolator(1.5f)).start()   // layar "zoom ke arah pengguna"
        }
        if (sm.repo.launch(app, v)) launched = true
        else if (!still) target.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(dur(200)).start()
    }

    private fun playReturn() {
        home.animate().cancel()
        if (motionScale() == 0f) { home.alpha = 1f; home.scaleX = 1f; home.scaleY = 1f; return }
        home.scaleX = 1.12f; home.scaleY = 1.12f; home.alpha = 0f
        home.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(dur(420))
            .setInterpolator(DecelerateInterpolator(1.8f)).start()
        stagger(favorites); stagger(dock)
    }

    private fun stagger(rv: RecyclerView) {
        for (i in 0 until rv.childCount) {
            val c = rv.getChildAt(i)
            c.translationY = dp(28).toFloat(); c.alpha = 0f
            c.animate().translationY(0f).alpha(1f).setStartDelay(40L + i * 18L).setDuration(dur(380))
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
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
        drawer.background = glass(this, Palette.drawerGlass(dark), 32f, topOnly = true)
        dock.background = glass(this, Palette.barGlass(dark), 34f)
        searchBar.background = glass(this, Palette.barGlass(dark), 26f)
        query.background = glass(this, Palette.field(dark), 16f)
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

    // ---------- Laci: spring + drag mengikuti jari ----------
    private fun progress(): Float = (1f - drawer.translationY / root.height.coerceAtLeast(1)).coerceIn(0f, 1f)

    private fun animateDrawer(open: Boolean, focus: Boolean = false, instant: Boolean = false, velocity: Float = 0f) {
        drawerOpen = open
        val target = if (open) 0f else root.height.toFloat()
        drawerAnim?.cancel(); drawer.animate().cancel()
        if (open) drawer.visibility = View.VISIBLE else hideIme()
        if (instant || motionScale() == 0f) {
            drawer.translationY = target; onProgress(); finishDrawer(open, focus); return
        }
        drawerAnim = SpringAnimation(drawer, DynamicAnimation.TRANSLATION_Y).apply {
            setSpring(SpringForce(target).apply {
                dampingRatio = 0.9f
                stiffness = (500f * prefs.animPercent / 100f).coerceIn(150f, 1500f)
            })
            setMinValue(0f)
            setStartVelocity(velocity)
            addUpdateListener { _, _, _ -> onProgress() }
            addEndListener { _, canceled, _, _ -> if (!canceled) finishDrawer(open, focus) }
            start()
        }
    }

    private fun onProgress() {
        val p = progress()
        home.alpha = 1f - p; home.scaleX = 1f - 0.06f * p; home.scaleY = 1f - 0.06f * p
        if (prefs.blur) {
            val r = ((p * dp(24)) / 4).toInt() * 4      // blur progresif, diquantisasi agar hemat GPU
            if (r != lastBlur) { lastBlur = r; setBlurRadius(r) }
        }
    }

    private fun setBlurRadius(r: Int) { try { window.setBackgroundBlurRadius(r) } catch (_: Exception) {} }

    private fun finishDrawer(open: Boolean, focus: Boolean) {
        if (open) {
            if (focus) {
                query.requestFocus()
                WindowCompat.getInsetsController(window, query).show(WindowInsetsCompat.Type.ime())
            }
        } else {
            drawer.visibility = View.INVISIBLE
            drawer.scaleX = 1f; drawer.scaleY = 1f; drawer.alpha = 1f
            query.setText(""); grid.scrollToPosition(0)
            lastBlur = 0; setBlurRadius(0)
        }
    }

    private fun hideIme() { WindowCompat.getInsetsController(window, root).hide(WindowInsetsCompat.Type.ime()) }

    private fun canDrag(dy: Float): Boolean =
        if (!drawerOpen) dy < 0 && !favorites.canScrollVertically(1) else dy > 0 && !grid.canScrollVertically(-1)

    /** 0 = tidak ditangani, 1 = drag baru mulai, 2 = drag berjalan/selesai (dikonsumsi). */
    private fun handleDrag(ev: MotionEvent): Int {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; dragging = false
                vt?.recycle(); vt = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(ev)
                val dy = ev.y - downY; val dx = ev.x - downX
                if (!dragging) {
                    val slop = ViewConfiguration.get(this).scaledTouchSlop * 2
                    if (abs(dy) > slop && abs(dy) > abs(dx) * 1.5f && canDrag(dy)) {
                        dragging = true; dragStartDy = dy
                        drawerAnim?.cancel(); trans0 = drawer.translationY
                        drawer.visibility = View.VISIBLE
                        if (drawerOpen) hideIme()
                        return 1
                    }
                    return 0
                }
                drawer.translationY = (trans0 + dy - dragStartDy).coerceIn(0f, root.height.toFloat())
                onProgress()
                return 2
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val was = dragging; dragging = false
                val t = vt; vt = null
                if (was && t != null) {
                    t.computeCurrentVelocity(1000)
                    val vy = t.yVelocity; t.recycle()
                    val open = when {
                        vy < -900f -> true
                        vy > 900f -> false
                        else -> progress() > 0.5f
                    }
                    if (open != drawerOpen) root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    animateDrawer(open, velocity = vy)
                    return 2
                }
                t?.recycle()
            }
        }
        return 0
    }

    // ---------- Gestur lain ----------
    private val detector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (!prefs.gestures || e1 == null || drawerOpen) return false
                val dx = e2.x - e1.x; val dy = e2.y - e1.y
                if (dy > dp(80) && dy > abs(dx) * 1.5f && vy > 700) animateDrawer(open = true, focus = true)   // swipe down = cari
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
        if (prefs.gestures) {
            when (handleDrag(ev)) {
                1 -> {
                    val c = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(c); detector.onTouchEvent(c); c.recycle()
                    return true
                }
                2 -> return true
            }
        }
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

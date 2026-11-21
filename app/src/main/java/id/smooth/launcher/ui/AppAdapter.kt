package id.smooth.launcher.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import id.smooth.launcher.data.AppInfo
import id.smooth.launcher.data.AppRepository
import id.smooth.launcher.util.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppAdapter(
    private val repo: AppRepository,
    private val scope: CoroutineScope,
    private val wrapWidth: Boolean,
    private val onClick: (AppInfo, View) -> Unit,
    private val onLong: (AppInfo, View) -> Unit,
) : ListAdapter<AppInfo, AppAdapter.VH>(Diff) {

    data class Style(val iconPx: Int, val labelSp: Int, val labelColor: Int, val showLabel: Boolean = true)

    var style = Style(0, 12, Color.WHITE)
        set(v) { if (field != v) { field = v; notifyDataSetChanged() } }

    class VH(val root: LinearLayout, val icon: ImageView, val label: TextView) : RecyclerView.ViewHolder(root) {
        var job: Job? = null
        var key: String? = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val c = parent.context
        val icon = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val label = TextView(c).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
        }
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = c.dp(6); setPadding(p, p, p, p)
            addView(icon)
            addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = c.dp(4) })
            layoutParams = RecyclerView.LayoutParams(
                if (wrapWidth) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        // Umpan balik sentuhan: skala halus saat ditekan.
        root.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        return VH(root, icon, label)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val app = getItem(position)
        h.key = app.key
        h.icon.layoutParams = LinearLayout.LayoutParams(style.iconPx, style.iconPx)
        h.label.apply {
            text = app.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.labelSp.toFloat())
            setTextColor(style.labelColor)
            setShadowLayer(if (style.labelColor == Color.WHITE) 4f else 0f, 0f, 1f, 0x66000000)
            visibility = if (style.showLabel) View.VISIBLE else View.GONE
        }
        h.root.setOnClickListener { onClick(app, h.icon) }
        h.root.setOnLongClickListener { onLong(app, h.root); true }

        h.job?.cancel()
        val cached = repo.cachedIcon(app, style.iconPx)
        if (cached != null) {
            h.icon.setImageBitmap(cached)
        } else {
            h.icon.setImageDrawable(null)
            h.job = scope.launch {
                val b = repo.icon(app, style.iconPx)
                if (h.key == app.key) h.icon.setImageBitmap(b)
            }
        }
    }

    override fun onViewRecycled(h: VH) { h.job?.cancel(); h.key = null }

    private object Diff : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(a: AppInfo, b: AppInfo) = a.key == b.key
        override fun areContentsTheSame(a: AppInfo, b: AppInfo) = a == b
    }
}

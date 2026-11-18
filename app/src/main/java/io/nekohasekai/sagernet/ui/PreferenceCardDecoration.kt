package io.nekohasekai.sagernet.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.dp2px

/**
 * Draws one rounded card behind each PreferenceCategory's rows in the settings
 * list (FlClash-style grouped cards), with a thin divider between rows.
 * Category headers themselves are styled by layout/pref_category.xml and are
 * not part of the card.
 */
class PreferenceCardDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val radius = dp2px(20).toFloat()
    private val marginH = dp2px(16).toFloat()
    private val strokeWidth = dp2px(1).toFloat()
    private val rowInsetH = dp2px(16)

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.nkb_card)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@PreferenceCardDecoration.strokeWidth
        color = ContextCompat.getColor(context, R.color.nkb_card_stroke)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp2px(1).toFloat()
        color = ContextCompat.getColor(context, R.color.nkb_divider)
    }

    private val rect = RectF()

    // Inset preference rows so their content (icon / switch) sits 16dp inside
    // the card edges instead of hugging them. Category headers keep full width.
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        if (adapter.getItem(pos) is PreferenceCategory) return
        outRect.left = rowInsetH
        outRect.right = rowInsetH
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val lm = parent.layoutManager ?: return

        var groupStart = -1
        var groupEnd = -1
        fun drawGroup(start: Int, end: Int) {
            var first: android.view.View? = null
            var last: android.view.View? = null
            for (pos in start..end) {
                val child = lm.findViewByPosition(pos) ?: continue
                if (first == null) first = child
                last = child
            }
            val firstView = first ?: return
            val lastView = last ?: return
            val left = parent.paddingLeft + marginH
            val right = parent.width - parent.paddingRight - marginH
            rect.set(left, firstView.top.toFloat(), right, lastView.bottom.toFloat())
            canvas.drawRoundRect(rect, radius, radius, cardPaint)
            // Inset the stroke by half its width so it stays inside the card bounds.
            rect.inset(strokeWidth / 2, strokeWidth / 2)
            canvas.drawRoundRect(rect, radius, radius, strokePaint)
            // Dividers between the visible rows of this group.
            for (pos in start until end) {
                val child = lm.findViewByPosition(pos) ?: continue
                val y = child.bottom.toFloat()
                canvas.drawLine(left, y, right, y, dividerPaint)
            }
        }

        for (pos in 0 until adapter.itemCount) {
            val pref = adapter.getItem(pos)
            if (pref is PreferenceCategory) {
                if (groupStart >= 0) drawGroup(groupStart, groupEnd)
                groupStart = -1
            } else {
                if (groupStart < 0) groupStart = pos
                groupEnd = pos
            }
        }
        if (groupStart >= 0) drawGroup(groupStart, groupEnd)
    }
}

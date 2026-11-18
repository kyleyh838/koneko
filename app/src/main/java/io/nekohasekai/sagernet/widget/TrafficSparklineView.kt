package io.nekohasekai.sagernet.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.R

/** Draws recent throughput (tx + rx) as a FlClash-style area sparkline. */
class TrafficSparklineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val samples = TrafficSamples()

    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.nkb_accent)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.nkb_accent)
        alpha = 38
        style = Paint.Style.FILL
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.nkb_card_stroke)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    fun push(txBytesPerSec: Long, rxBytesPerSec: Long) {
        samples.push((txBytesPerSec + rxBytesPerSec).toFloat())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = samples.snapshot()
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 4f * density
        if (data.size < 2) {
            canvas.drawLine(0f, h - pad, w, h - pad, baselinePaint)
            return
        }
        val max = samples.max().coerceAtLeast(1f)
        val usableH = (h - pad * 2).coerceAtLeast(1f)
        val stepX = w / (samples.capacity - 1)
        val linePath = Path()
        val fillPath = Path()
        data.forEachIndexed { i, v ->
            val x = w - (data.size - 1 - i) * stepX
            val y = h - pad - (v / max) * usableH
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo((data.size - 1) * stepX, h)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }
}

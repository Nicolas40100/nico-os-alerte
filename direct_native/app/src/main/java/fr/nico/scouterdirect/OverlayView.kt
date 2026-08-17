package fr.nico.scouterdirect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class OverlayView(context: Context) : View(context) {
    private var detections: List<Detection> = emptyList()
    private var sourceW: Int = 1
    private var sourceH: Int = 1
    private var target: String? = null

    private val normalPaint = Paint().apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val targetPaint = Paint().apply {
        color = Color.rgb(40, 255, 100)
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        isAntiAlias = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }
    private val targetTextPaint = Paint(textPaint).apply {
        color = Color.rgb(40, 255, 100)
        textSize = 36f
    }

    fun update(items: List<Detection>, srcW: Int, srcH: Int, targetLabel: String?) {
        detections = items
        sourceW = srcW.coerceAtLeast(1)
        sourceH = srcH.coerceAtLeast(1)
        target = targetLabel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width.toFloat() / sourceW, height.toFloat() / sourceH)
        val drawnW = sourceW * scale
        val drawnH = sourceH * scale
        val dx = (width - drawnW) / 2f
        val dy = (height - drawnH) / 2f
        for (d in detections.take(12)) {
            val isTarget = target != null && canonical(d.label) == target
            if (!isTarget && d.score < 0.25f) continue
            val r = RectF(
                dx + d.rect.left * scale,
                dy + d.rect.top * scale,
                dx + d.rect.right * scale,
                dy + d.rect.bottom * scale,
            )
            canvas.drawRect(r, if (isTarget) targetPaint else normalPaint)
            val txt = "${d.label} ${(d.score * 100).toInt()}%"
            canvas.drawText(
                txt,
                r.left.coerceAtLeast(4f),
                (r.top - 8f).coerceAtLeast(32f),
                if (isTarget) targetTextPaint else textPaint,
            )
        }
    }

    private fun canonical(s: String): String = s.lowercase().trim()
        .replace("sneakers", "shoe")
        .replace("sneaker", "shoe")
        .replace("shoes", "shoe")
}

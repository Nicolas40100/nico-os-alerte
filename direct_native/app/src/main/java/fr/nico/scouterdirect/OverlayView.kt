package fr.nico.scouterdirect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View

class OverlayView(context: Context) : View(context) {
    private var detections: List<Detection> = emptyList()
    private var sourceW: Int = 1
    private var sourceH: Int = 1
    private var lockedTarget: Detection? = null
    private var targetDisplay: String = ""
    private var lockStartedMs: Long = 0L

    // Free scan: unchanged from the stable V1, deliberately discreet.
    private val normalPaint = Paint().apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        isAntiAlias = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    // Search target: intentionally obvious even in dim light.
    private val targetGlowPaint = Paint().apply {
        color = Color.argb(110, 255, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 18f
        isAntiAlias = true
    }
    private val targetPaint = Paint().apply {
        color = Color.rgb(255, 20, 20)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }
    private val targetTextPaint = Paint().apply {
        color = Color.rgb(255, 35, 35)
        textSize = 38f
        isAntiAlias = true
        isFakeBoldText = true
        setShadowLayer(7f, 0f, 0f, Color.BLACK)
    }
    private val reticlePaint = Paint(targetPaint).apply { strokeWidth = 5f }
    private val lockTextPaint = Paint().apply {
        color = Color.rgb(255, 20, 20)
        textSize = 58f
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(9f, 0f, 0f, Color.BLACK)
    }

    fun update(
        items: List<Detection>,
        srcW: Int,
        srcH: Int,
        targetDetection: Detection?,
        displayName: String,
        lockStart: Long,
    ) {
        detections = items
        sourceW = srcW.coerceAtLeast(1)
        sourceH = srcH.coerceAtLeast(1)
        lockedTarget = targetDetection
        targetDisplay = displayName
        lockStartedMs = lockStart
        invalidate()
    }

    fun clearTarget() {
        lockedTarget = null
        targetDisplay = ""
        lockStartedMs = 0L
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width.toFloat() / sourceW, height.toFloat() / sourceH)
        val drawnW = sourceW * scale
        val drawnH = sourceH * scale
        val dx = (width - drawnW) / 2f
        val dy = (height - drawnH) / 2f
        val target = lockedTarget

        // Keep the stable V1 free-scan presentation. The target is excluded here and drawn last.
        for (d in detections.take(12)) {
            if (d === target) continue
            if (d.score < 0.25f) continue
            val r = mapRect(d.rect, scale, dx, dy)
            canvas.drawRect(r, normalPaint)
            val txt = "${d.label} ${(d.score * 100).toInt()}%"
            canvas.drawText(
                txt,
                r.left.coerceAtLeast(4f),
                (r.top - 8f).coerceAtLeast(32f),
                textPaint,
            )
        }

        // Critical V1.2 fix: the requested target is always drawn, even if it ranks 13th–30th.
        if (target != null) {
            drawTarget(canvas, mapRect(target.rect, scale, dx, dy), target)
        }
    }

    private fun mapRect(rect: RectF, scale: Float, dx: Float, dy: Float) = RectF(
        dx + rect.left * scale,
        dy + rect.top * scale,
        dx + rect.right * scale,
        dy + rect.bottom * scale,
    )

    private fun drawTarget(canvas: Canvas, r: RectF, d: Detection) {
        canvas.drawRect(r, targetGlowPaint)
        canvas.drawRect(r, targetPaint)

        val cx = r.centerX()
        val cy = r.centerY()
        val arm = 22f
        val gap = 7f
        canvas.drawLine(cx - arm, cy, cx - gap, cy, reticlePaint)
        canvas.drawLine(cx + gap, cy, cx + arm, cy, reticlePaint)
        canvas.drawLine(cx, cy - arm, cx, cy - gap, reticlePaint)
        canvas.drawLine(cx, cy + gap, cx, cy + arm, reticlePaint)
        canvas.drawCircle(cx, cy, 4f, reticlePaint)

        val label = if (targetDisplay.isBlank()) d.label.uppercase() else targetDisplay.uppercase()
        val txt = "$label — ${(d.score * 100).toInt()} %"
        canvas.drawText(
            txt,
            r.left.coerceAtLeast(4f),
            (r.top - 10f).coerceAtLeast(40f),
            targetTextPaint,
        )

        // Starts from the actual post-inference acquisition time, so the user really sees it.
        val elapsed = if (lockStartedMs > 0L) SystemClock.elapsedRealtime() - lockStartedMs else Long.MAX_VALUE
        if (elapsed in 0L..600L) {
            canvas.drawText("LOCK", cx, cy - 34f, lockTextPaint)
            postInvalidateDelayed(40L)
        }
    }
}

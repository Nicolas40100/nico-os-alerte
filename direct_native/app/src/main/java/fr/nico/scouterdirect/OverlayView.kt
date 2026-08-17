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
    private var targetDisplay: String = ""
    private var lockStartedMs: Long = 0L

    // Free scan stays deliberately discreet.
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

    // Target lock: bright red, full rectangle, subtle glow, center reticle.
    private val targetGlowPaint = Paint().apply {
        color = Color.argb(95, 255, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 16f
        isAntiAlias = true
    }
    private val targetPaint = Paint().apply {
        color = Color.rgb(255, 25, 25)
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
    private val reticlePaint = Paint(targetPaint).apply {
        strokeWidth = 5f
    }
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
        targetLabel: String?,
        displayName: String,
        lockStart: Long,
    ) {
        detections = items
        sourceW = srcW.coerceAtLeast(1)
        sourceH = srcH.coerceAtLeast(1)
        target = targetLabel
        targetDisplay = displayName
        lockStartedMs = lockStart
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

            if (isTarget) {
                drawTarget(canvas, r, d)
            } else {
                canvas.drawRect(r, normalPaint)
                val txt = "${d.label} ${(d.score * 100).toInt()}%"
                canvas.drawText(
                    txt,
                    r.left.coerceAtLeast(4f),
                    (r.top - 8f).coerceAtLeast(32f),
                    textPaint,
                )
            }
        }
    }

    private fun drawTarget(canvas: Canvas, r: RectF, d: Detection) {
        // Full red rectangle with a halo, easy to spot in dim light.
        canvas.drawRect(r, targetGlowPaint)
        canvas.drawRect(r, targetPaint)

        // Simple reticle at the center of the detected object.
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

        // Acquisition flash: LOCK remains visible for about half a second.
        val elapsed = if (lockStartedMs > 0L) System.currentTimeMillis() - lockStartedMs else Long.MAX_VALUE
        if (elapsed in 0L..550L) {
            canvas.drawText("LOCK", cx, cy - 34f, lockTextPaint)
            // Keep redrawing during the short lock animation even between camera frames.
            postInvalidateDelayed(45L)
        }
    }

    private fun canonical(s: String): String = s.lowercase().trim()
        .replace("sneakers", "shoe")
        .replace("sneaker", "shoe")
        .replace("shoes", "shoe")
}

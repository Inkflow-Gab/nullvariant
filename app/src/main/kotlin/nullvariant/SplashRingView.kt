package com.nullvariant.official

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat

class SplashRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = ContextCompat.getColor(context, R.color.loader_text_primary)
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(10f)
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.loader_status_active)
        maskFilter = BlurMaskFilter(dp(10f), BlurMaskFilter.Blur.NORMAL)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.loader_status_active)
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.loader_accent_cyan)
    }

    private val ringRect = RectF()
    private val pulseRect = RectF()
    private var pulse = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1900L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val inset = dp(14f)
        ringRect.set(inset, inset, width - inset, height - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (ringRect.isEmpty) return

        val scale = 1f + 0.06f * pulse
        val halfWidth = ringRect.width() * 0.5f * scale
        val halfHeight = ringRect.height() * 0.5f * scale
        val centerX = ringRect.centerX()
        val centerY = ringRect.centerY()
        pulseRect.set(
            centerX - halfWidth,
            centerY - halfHeight,
            centerX + halfWidth,
            centerY + halfHeight
        )

        glowPaint.alpha = (40 + 55 * pulse).toInt()
        glowPaint.strokeWidth = dp(9f + 4f * pulse)
        canvas.drawArc(pulseRect, 0f, 360f, false, glowPaint)

        basePaint.alpha = (26 + 24 * pulse).toInt()
        canvas.drawArc(pulseRect, 0f, 360f, false, basePaint)

        arcPaint.alpha = (185 + 55 * pulse).toInt()
        canvas.drawArc(pulseRect, -90f, 92f, false, arcPaint)

        accentPaint.alpha = (95 + 85 * pulse).toInt()
        canvas.drawArc(pulseRect, 34f, 42f, false, accentPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}

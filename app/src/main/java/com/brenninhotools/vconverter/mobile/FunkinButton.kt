package com.brenninhotools.vconverter.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

class FunkinButton @JvmOverloads constructor(
    context: Context,
    private var label: String = "",
    private var onClickAction: (() -> Unit)? = null
) : View(context) {

    var enabledButton = true
        set(value) {
            field = value
            invalidate()
        }

    var buttonColor = Color.rgb(255, 190, 0)
        set(value) {
            field = value
            invalidate()
        }

    var textColor = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    var textSizeSp = 22f
        set(value) {
            field = value
            invalidate()
        }

    var cornerRadius = 14f
    var pressedScale = 0.92f
    var animationDuration = 110L
    var hapticEnabled = true

    private var currentScale = 1f
    private var pressed = false
    private var buttonImage: Bitmap? = null
    private var pressedImage: Bitmap? = null

    private val interpolator = DecelerateInterpolator()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.BOLD
        )
        textAlign = Paint.Align.CENTER
        setShadowLayer(3f, 2f, 3f, Color.BLACK)
    }

    init {
        isClickable = true
        isFocusable = true
        minimumHeight = dp(52f).toInt()
        minimumWidth = dp(120f).toInt()
    }

    fun setLabel(value: String) {
        label = value
        contentDescription = value
        invalidate()
    }

    fun setAction(action: () -> Unit) {
        onClickAction = action
    }

    fun setImage(
        normal: Bitmap?,
        pressed: Bitmap? = null
    ) {
        buttonImage = normal
        pressedImage = pressed
        invalidate()
    }

    fun loadImageFromAssets(
        normalPath: String,
        pressedPath: String? = null
    ) {
        buttonImage = loadBitmap(normalPath)
        pressedImage = pressedPath?.let {
            loadBitmap(it)
        }
        invalidate()
    }

    private fun loadBitmap(path: String): Bitmap? {
        return try {
            context.assets.open(path).use {
                BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val width = resolveSize(
            suggestedMinimumWidth,
            widthMeasureSpec
        )
        val height = resolveSize(
            suggestedMinimumHeight,
            heightMeasureSpec
        )
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val save = canvas.save()
        val cx = width / 2f
        val cy = height / 2f

        canvas.scale(currentScale, currentScale, cx, cy)

        val bounds = RectF(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (width - paddingRight).toFloat(),
            (height - paddingBottom).toFloat()
        )

        val bitmap = if (pressed) {
            pressedImage ?: buttonImage
        } else {
            buttonImage
        }

        if (bitmap != null) {
            paint.color = Color.WHITE
            paint.alpha = if (enabledButton) 255 else 120

            canvas.drawBitmap(
                bitmap,
                null,
                bounds,
                paint
            )
        } else {
            paint.color = if (pressed) {
                darken(buttonColor, 0.78f)
            } else {
                buttonColor
            }

            paint.alpha = if (enabledButton) 255 else 120

            canvas.drawRoundRect(
                bounds,
                dp(cornerRadius),
                dp(cornerRadius),
                paint
            )
        }

        if (label.isNotEmpty()) {
            textPaint.color = textColor
            textPaint.alpha = if (enabledButton) 255 else 120
            textPaint.textSize = textSizeSp *
                resources.displayMetrics.scaledDensity

            val availableWidth = bounds.width() - dp(20f)
            val originalSize = textPaint.textSize

            if (textPaint.measureText(label) > availableWidth &&
                availableWidth > 0f
            ) {
                textPaint.textSize *=
                    availableWidth / textPaint.measureText(label)
            }

            val metrics = textPaint.fontMetrics
            val baseline = cy -
                (metrics.ascent + metrics.descent) / 2f

            canvas.drawText(label, cx, baseline, textPaint)
            textPaint.textSize = originalSize
        }

        canvas.restoreToCount(save)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledButton || !isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                parent?.requestDisallowInterceptTouchEvent(true)
                animateScale(pressedScale)

                if (hapticEnabled) {
                    performHapticFeedback(
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val inside = event.x >= 0f &&
                    event.x <= width &&
                    event.y >= 0f &&
                    event.y <= height

                if (pressed != inside) {
                    pressed = inside
                    animateScale(if (inside) pressedScale else 1f)
                    invalidate()
                }

                return true
            }

            MotionEvent.ACTION_UP -> {
                val shouldClick = pressed
                pressed = false
                animateScale(1f)
                parent?.requestDisallowInterceptTouchEvent(false)

                if (shouldClick) {
                    performClick()
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                animateScale(1f)
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (enabledButton && isEnabled) {
            onClickAction?.invoke()
            return true
        }
        return false
    }

    private fun animateScale(target: Float) {
        animate().cancel()

        val start = currentScale

        val animator = android.animation.ValueAnimator.ofFloat(
            start,
            target
        )

        animator.duration = animationDuration
        animator.interpolator = interpolator

        animator.addUpdateListener {
            currentScale = it.animatedValue as Float
            invalidate()
        }

        animator.start()
    }

    private fun darken(color: Int, factor: Float): Int {
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        )
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        super.onDetachedFromWindow()
    }
}

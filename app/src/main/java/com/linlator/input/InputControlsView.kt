package com.linlator.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

data class ControlButton(
    val label: String,
    val keycode: Int,
    var x: Float = 0f,
    var y: Float = 0f,
    var radius: Float = 40f,
    var isCircle: Boolean = true
)

class InputControlsView(context: Context) : View(context) {

    var buttons: List<ControlButton> = emptyList()
    var onKeyEvent: ((Int, Int) -> Unit)? = null

    private val buttonPaint = Paint().apply {
        color = 0x66000000.toInt()
        style = Paint.Style.FILL
    }

    private val buttonStrokePaint = Paint().apply {
        color = 0xAAFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val rect = RectF()
    private val pressedButtons = mutableMapOf<Int, Boolean>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (button in buttons) {
            if (button.isCircle) {
                canvas.drawCircle(button.x, button.y, button.radius, buttonPaint)
                canvas.drawCircle(button.x, button.y, button.radius, buttonStrokePaint)
                canvas.drawText(
                    button.label, button.x,
                    button.y + textPaint.textSize / 3,
                    textPaint
                )
            } else {
                rect.set(
                    button.x - button.radius,
                    button.y - button.radius,
                    button.x + button.radius,
                    button.y + button.radius
                )
                canvas.drawRoundRect(rect, 8f, 8f, buttonPaint)
                canvas.drawRoundRect(rect, 8f, 8f, buttonStrokePaint)
                canvas.drawText(
                    button.label, button.x,
                    button.y + textPaint.textSize / 3,
                    textPaint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                for (button in buttons) {
                    if (isInsideButton(x, y, button)) {
                        pressedButtons[button.keycode] = true
                        onKeyEvent?.invoke(button.keycode, 0)
                        invalidate()
                        break
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                for (button in buttons) {
                    if (pressedButtons[button.keycode] == true) {
                        pressedButtons[button.keycode] = false
                        onKeyEvent?.invoke(button.keycode, 1)
                        invalidate()
                    }
                }
            }
        }
        return true
    }

    private fun isInsideButton(x: Float, y: Float, button: ControlButton): Boolean {
        val dx = x - button.x
        val dy = y - button.y
        return (dx * dx + dy * dy) <= (button.radius * button.radius)
    }
}

package com.linlator.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

class TouchpadView(context: Context) : View(context) {

    var sensitivity: Float = 2.0f
    var onPointerMove: ((Float, Float) -> Unit)? = null
    var onPointerAction: ((Int) -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f
    private var pointerId = -1

    private val borderPaint = Paint().apply {
        color = 0x33FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                pointerId = event.getPointerId(idx)
                lastX = event.getX(idx)
                lastY = event.getY(idx)
                onPointerAction?.invoke(MotionEvent.ACTION_DOWN)
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(pointerId)
                if (idx >= 0) {
                    val dx = (event.getX(idx) - lastX) * sensitivity
                    val dy = (event.getY(idx) - lastY) * sensitivity
                    lastX = event.getX(idx)
                    lastY = event.getY(idx)
                    onPointerMove?.invoke(dx, dy)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                pointerId = -1
                onPointerAction?.invoke(MotionEvent.ACTION_UP)
            }
        }
        return true
    }
}

package com.linlator.input

import android.content.Context
import android.view.MotionEvent
import android.view.View

class TouchpadView(context: Context) : View(context) {

    var onPointerMoved: ((Float, Float) -> Unit)? = null
    var onButtonPress: ((Int) -> Unit)? = null
    var onButtonRelease: ((Int) -> Unit)? = null
    var onScroll: ((Float, Float) -> Unit)? = null

    private var pointerId = -1
    private var scrollId = -1
    private var lastPointerX = 0f
    private var lastPointerY = 0f
    private var lastScrollX = 0f
    private var lastScrollY = 0f
    private var downTime = 0L
    private var pointerDownX = 0f
    private var pointerDownY = 0f
    private var isPointerDown = false
    private var isDragging = false
    private var tapConsumed = false

    private val sensitivity = 2.0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                this.pointerId = pointerId
                lastPointerX = x
                lastPointerY = y
                pointerDownX = x
                pointerDownY = y
                downTime = event.eventTime
                isPointerDown = true
                isDragging = false
                tapConsumed = false
                onPointerMoved?.invoke(x, y)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (scrollId < 0) {
                    scrollId = pointerId
                    lastScrollX = x
                    lastScrollY = y
                }
                if (tapConsumed) {
                    onButtonPress?.invoke(3)
                    onButtonRelease?.invoke(3)
                    tapConsumed = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                var handled = false
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val px = event.getX(i)
                    val py = event.getY(i)

                    if (pid == this.pointerId) {
                        val dx = (px - lastPointerX) * sensitivity
                        val dy = (py - lastPointerY) * sensitivity
                        if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                            isDragging = true
                            onPointerMoved?.invoke(dx, dy)
                        }
                        lastPointerX = px
                        lastPointerY = py
                        handled = true
                    }

                    if (pid == scrollId) {
                        val scrollDx = px - lastScrollX
                        val scrollDy = py - lastScrollY
                        if (Math.abs(scrollDx) > 2f || Math.abs(scrollDy) > 2f) {
                            onScroll?.invoke(scrollDx, scrollDy)
                            lastScrollX = px
                            lastScrollY = py
                        }
                        handled = true
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (pointerId == scrollId) {
                    scrollId = -1
                }
                if (pointerId == this.pointerId && event.pointerCount > 1) {
                    for (i in 0 until event.pointerCount) {
                        val pid = event.getPointerId(i)
                        if (pid != pointerId) {
                            this.pointerId = pid
                            lastPointerX = event.getX(i)
                            lastPointerY = event.getY(i)
                            break
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                val elapsed = event.eventTime - downTime

                if (!isDragging && scrollId < 0) {
                    if (elapsed < 200) {
                        onButtonPress?.invoke(1)
                        onButtonRelease?.invoke(1)
                    } else if (elapsed > 500) {
                        onButtonPress?.invoke(2)
                        onButtonRelease?.invoke(2)
                    }
                }

                this.pointerId = -1
                scrollId = -1
                isPointerDown = false
                isDragging = false
                tapConsumed = false
            }

            MotionEvent.ACTION_CANCEL -> {
                this.pointerId = -1
                scrollId = -1
                isPointerDown = false
                isDragging = false
                tapConsumed = false
            }
        }

        return true
    }
}

package com.linlator.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class InputControlsView(context: Context) : View(context) {

    var onKeyPress: ((Int) -> Unit)? = null
    var onKeyRelease: ((Int) -> Unit)? = null

    private val controls = mutableListOf<ControlButton>()
    private val activeButtons = mutableSetOf<String>()

    private val buttonPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 36f
    }

    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.argb(100, 255, 255, 255)
        strokeWidth = 2f
    }

    private val hitCache = mutableMapOf<String, RectF>()
    private var cachedW = 0
    private var cachedH = 0

    private val defaultControls by lazy { createDefaultControls() }

    fun setControls(buttons: List<ControlButton>) {
        controls.clear()
        controls.addAll(buttons)
        hitCache.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedW = w
        cachedH = h
        rebuildHitCache()
    }

    private fun rebuildHitCache() {
        hitCache.clear()
        for (btn in controls) {
            val bx = btn.xFraction * cachedW
            val by = btn.yFraction * cachedH
            val bw = btn.widthFraction * cachedW
            val bh = btn.heightFraction * cachedH
            hitCache[btn.id] = RectF(bx, by, bx + bw, by + bh)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val buttons = if (controls.isEmpty()) defaultControls else controls

        for (btn in buttons) {
            val bx = btn.xFraction * w
            val by = btn.yFraction * h
            val bw = btn.widthFraction * w
            val bh = btn.heightFraction * h

            val isActive = btn.id in activeButtons
            buttonPaint.color = if (isActive) {
                Color.argb(120, 200, 200, 200)
            } else {
                Color.argb(80, 100, 100, 100)
            }

            when (btn.shape) {
                "circle" -> {
                    val cx = bx + bw / 2f
                    val cy = by + bh / 2f
                    val radius = Math.min(bw, bh) / 2f
                    canvas.drawCircle(cx, cy, radius, buttonPaint)
                    canvas.drawCircle(cx, cy, radius, borderPaint)
                }
                "rect" -> {
                    canvas.drawRect(bx, by, bx + bw, by + bh, buttonPaint)
                    canvas.drawRect(bx, by, bx + bw, by + bh, borderPaint)
                }
                else -> {
                    val r = Math.min(bw, bh) * 0.15f
                    canvas.drawRoundRect(bx, by, bx + bw, by + bh, r, r, buttonPaint)
                    canvas.drawRoundRect(bx, by, bx + bw, by + bh, r, r, borderPaint)
                }
            }

            val tx = bx + bw / 2f
            val ty = by + bh / 2f + textPaint.textSize / 3f
            canvas.drawText(btn.label, tx, ty, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val btn = hitTest(x, y)
                if (btn != null) {
                    activeButtons.add(btn.id)
                    onKeyPress?.invoke(btn.keyCode)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val hitId = hitTest(x, y)?.id
                val toRemove = activeButtons.filter { it != hitId }
                for (id in toRemove) {
                    val btn = controls.find { it.id == id }
                    if (btn != null) {
                        activeButtons.remove(id)
                        onKeyRelease?.invoke(btn.keyCode)
                    }
                }
                if (hitId != null && hitId !in activeButtons) {
                    val btn = controls.find { it.id == hitId }
                    if (btn != null) {
                        activeButtons.add(hitId)
                        onKeyPress?.invoke(btn.keyCode)
                    }
                }
                if (toRemove.isNotEmpty() || (hitId != null && hitId !in activeButtons)) {
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                for (id in activeButtons.toList()) {
                    val btn = controls.find { it.id == id }
                    if (btn != null) {
                        onKeyRelease?.invoke(btn.keyCode)
                    }
                }
                activeButtons.clear()
                invalidate()
                return true
            }
        }

        return false
    }

    private fun hitTest(x: Float, y: Float): ControlButton? {
        val buttons = if (controls.isEmpty()) defaultControls else controls
        val w = width.toFloat()
        val h = height.toFloat()

        for (btn in buttons) {
            val bx = btn.xFraction * w
            val by = btn.yFraction * h
            val bw = btn.widthFraction * w
            val bh = btn.heightFraction * h

            when (btn.shape) {
                "circle" -> {
                    val cx = bx + bw / 2f
                    val cy = by + bh / 2f
                    val radius = Math.min(bw, bh) / 2f
                    val dx = x - cx
                    val dy = y - cy
                    if (dx * dx + dy * dy <= radius * radius) return btn
                }
                else -> {
                    if (x >= bx && x <= bx + bw && y >= by && y <= by + bh) return btn
                }
            }
        }
        return null
    }

    private fun createDefaultControls(): List<ControlButton> {
        val list = mutableListOf<ControlButton>()

        val btnSize = 0.09f
        val halfW = 0.5f
        val col1 = halfW - btnSize * 1.8f
        val col2 = halfW - btnSize * 0.6f
        val col3 = halfW + btnSize * 0.6f
        val rowMid = 0.55f
        val rowTop = rowMid - btnSize * 1.1f

        list.add(ControlButton("w", col2, rowTop, btnSize, btnSize, "W", 17, "roundrect"))
        list.add(ControlButton("a", col1, rowMid, btnSize, btnSize, "A", 30, "roundrect"))
        list.add(ControlButton("s", col2, rowMid, btnSize, btnSize, "S", 31, "roundrect"))
        list.add(ControlButton("d", col3, rowMid, btnSize, btnSize, "D", 32, "roundrect"))
        list.add(ControlButton("q", col1, rowTop, btnSize, btnSize, "Q", 16, "roundrect"))
        list.add(ControlButton("e", col3, rowTop, btnSize, btnSize, "E", 18, "roundrect"))

        val arrowSize = 0.08f
        val rightHalfW = 0.75f
        list.add(ControlButton("up", rightHalfW, rowTop, arrowSize, arrowSize, "^", 103, "circle"))
        list.add(ControlButton("left", rightHalfW - arrowSize * 1.1f, rowMid, arrowSize, arrowSize, "<", 105, "circle"))
        list.add(ControlButton("down", rightHalfW, rowMid, arrowSize, arrowSize, "v", 108, "circle"))
        list.add(ControlButton("right", rightHalfW + arrowSize * 1.1f, rowMid, arrowSize, arrowSize, ">", 106, "circle"))

        val bottomY = 0.88f
        val bottomBtnW = 0.08f
        val bottomBtnH = 0.06f
        val spacing = bottomBtnW * 1.3f
        val startX = 0.5f - spacing * 2f

        list.add(ControlButton("esc", startX, bottomY, bottomBtnW, bottomBtnH, "Esc", 1, "roundrect"))
        list.add(ControlButton("tab", startX + spacing, bottomY, bottomBtnW, bottomBtnH, "Tab", 15, "roundrect"))
        list.add(ControlButton("enter", startX + spacing * 2, bottomY, bottomBtnW * 1.5f, bottomBtnH, "Enter", 28, "roundrect"))
        list.add(ControlButton("space", startX + spacing * 3.5f, bottomY, bottomBtnW * 2f, bottomBtnH, "Space", 57, "roundrect"))

        return list
    }
}

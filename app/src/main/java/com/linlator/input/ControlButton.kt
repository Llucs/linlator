package com.linlator.input

data class ControlButton(
    val id: String,
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val label: String,
    val keyCode: Int,
    val shape: String = "roundrect"
)

package com.linlator.x11

data class Window(
    val id: Int,
    var x: Int = 0,
    var y: Int = 0,
    var width: Int = 1,
    var height: Int = 1,
    var damaged: Boolean = false,
    var mapped: Boolean = false,
    var pixelData: IntArray? = null
)

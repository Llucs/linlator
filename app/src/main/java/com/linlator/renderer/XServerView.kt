package com.linlator.renderer

import android.content.Context
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class XServerView(context: Context) : GLSurfaceView(context) {

    private val xRenderer = XServerRenderer()

    init {
        setEGLContextClientVersion(3)
        setRenderer(xRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onPause() {
        super.onPause()
        xRenderer.stopXServer()
    }

    override fun onResume() {
        super.onResume()
        xRenderer.startXServer()
    }

    inner class XServerRenderer : Renderer {
        private var nativeCtx: Long = 0

        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            nativeCtx = nativeInit()
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            if (nativeCtx != 0L) {
                nativeResize(nativeCtx, width, height)
            }
        }

        override fun onDrawFrame(gl: GL10) {
            if (nativeCtx != 0L) {
                nativeRender(nativeCtx)
            }
        }

        fun startXServer() {
            if (nativeCtx != 0L) {
                nativeStartXServer(nativeCtx)
            }
        }

        fun stopXServer() {
            if (nativeCtx != 0L) {
                nativeStopXServer(nativeCtx)
                nativeDestroy(nativeCtx)
                nativeCtx = 0
            }
        }

        private external fun nativeInit(): Long
        private external fun nativeDestroy(ctx: Long)
        private external fun nativeResize(ctx: Long, width: Int, height: Int)
        private external fun nativeRender(ctx: Long)
        private external fun nativeStartXServer(ctx: Long): Boolean
        private external fun nativeStopXServer(ctx: Long)
    }

    companion object {
        init {
            System.loadLibrary("linlator")
        }
    }
}

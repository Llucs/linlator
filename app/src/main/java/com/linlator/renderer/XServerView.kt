package com.linlator.renderer

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.linlator.x11.X11Server
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class XServerView(context: Context) : GLSurfaceView(context) {

    private var xServer: X11Server? = null
    private val textureMap = mutableMapOf<Int, TextureInfo>()
    private var screenW = 1f
    private var screenH = 1f

    private data class TextureInfo(
        var texId: Int = 0,
        var width: Int = 0,
        var height: Int = 0
    )

    private val renderer = ScreenRenderer()

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setXServer(server: X11Server) {
        xServer = server
        server.onNewFrame = { window ->
            queueEvent {
                updateTexture(window.id, window.pixelData ?: return@queueEvent, window.width, window.height)
            }
        }
    }

    fun updateTexture(winId: Int, pixels: IntArray, w: Int, h: Int) {
        val existing = textureMap[winId]
        if (existing != null) {
            if (existing.width != w || existing.height != h) {
                X11Renderer.deleteTexture(existing.texId)
                val newTexId = X11Renderer.createTexture(w, h)
                existing.texId = newTexId
                existing.width = w
                existing.height = h
            }
            X11Renderer.updateTexture(existing.texId, pixels, w, h)
        } else {
            val texId = X11Renderer.createTexture(w, h)
            X11Renderer.updateTexture(texId, pixels, w, h)
            textureMap[winId] = TextureInfo(texId, w, h)
        }
    }

    private inner class ScreenRenderer : GLSurfaceView.Renderer {

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES30.glClearColor(0.078f, 0.071f, 0.094f, 1f)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            X11Renderer.initialize()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES30.glViewport(0, 0, width, height)
            screenW = width.toFloat()
            screenH = height.toFloat()
        }

        override fun onDrawFrame(gl: GL10?) {
            val server = xServer ?: return

            server.processRequests()

            val rootWin = server.getRootWindow()
            val bgColor = 0xFF141218.toInt()
            val bgR = ((bgColor shr 16) and 0xFF) / 255f
            val bgG = ((bgColor shr 8) and 0xFF) / 255f
            val bgB = (bgColor and 0xFF) / 255f
            GLES30.glClearColor(bgR, bgG, bgB, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

            if (rootWin != null && rootWin.pixelData != null) {
                val rootTex = textureMap[1]
                if (rootTex != null) {
                    X11Renderer.drawTexturedQuad(rootTex.texId, 0f, 0f, screenW, screenH, screenW, screenH)
                } else {
                    val texId = X11Renderer.createTexture(rootWin.width, rootWin.height)
                    X11Renderer.updateTexture(texId, rootWin.pixelData!!, rootWin.width, rootWin.height)
                    textureMap[1] = TextureInfo(texId, rootWin.width, rootWin.height)
                    X11Renderer.drawTexturedQuad(texId, 0f, 0f, screenW, screenH, screenW, screenH)
                }
            }

            val visibleWindows = server.getVisibleWindows()
            for (win in visibleWindows) {
                if (win.damaged && win.pixelData != null) {
                    updateTexture(win.id, win.pixelData!!, win.width, win.height)
                    win.damaged = false
                }
                val texInfo = textureMap[win.id]
                if (texInfo != null) {
                    X11Renderer.drawTexturedQuad(
                        texInfo.texId,
                        win.x.toFloat(),
                        win.y.toFloat(),
                        win.width.toFloat(),
                        win.height.toFloat(),
                        screenW,
                        screenH
                    )
                }
            }
        }
    }
}

package com.linlator

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.linlator.container.ContainerRuntime
import com.linlator.input.InputControlsView
import com.linlator.input.TouchpadView
import com.linlator.renderer.XServerView
import com.linlator.x11.X11Server
import java.io.File

class XServerDisplayActivity : Activity() {

    private lateinit var xServerView: XServerView
    private var x11Server: X11Server? = null
    private var containerRuntime: ContainerRuntime? = null
    private var containerProcess: Process? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveMode()

        val xserverDir = File(filesDir, "xserver")
        xserverDir.mkdirs()
        File(filesDir, "xserver/X0").delete()

        val socketPath = File(filesDir, "xserver/X0").absolutePath
        x11Server = X11Server(socketPath)

        xServerView = XServerView(this)

        val touchpadView = TouchpadView(this)
        val controlsView = InputControlsView(this)

        val server = x11Server ?: return

        touchpadView.onPointerMoved = { dx, dy ->
            server.injectPointerMotion(dx, dy)
        }
        touchpadView.onButtonPress = { button ->
            server.injectButtonPress(button)
        }
        touchpadView.onButtonRelease = { button ->
            server.injectButtonRelease(button)
        }
        touchpadView.onScroll = { dx, dy ->
            server.injectScroll(dx, dy)
        }

        controlsView.onKeyPress = { keyCode ->
            server.injectKeyPress(keyCode)
        }
        controlsView.onKeyRelease = { keyCode ->
            server.injectKeyRelease(keyCode)
        }

        val rootLayout = android.widget.FrameLayout(this)
        rootLayout.addView(xServerView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))
        rootLayout.addView(touchpadView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))
        rootLayout.addView(controlsView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(rootLayout)

        val containerId = intent?.getStringExtra("container_id")
        if (containerId != null) {
            val app = application as LinlatorApp
            val config = app.containerManager.get(containerId)
            if (config != null) {
                val displayW = resources.displayMetrics.widthPixels
                val displayH = resources.displayMetrics.heightPixels

                x11Server?.start(displayW, displayH)
                xServerView.setXServer(x11Server!!)

                containerRuntime = ContainerRuntime(filesDir)
                containerRuntime?.start(config) { proc ->
                    containerProcess = proc
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        xServerView.onPause()
        containerProcess?.let { proc ->
            try {
                val m = proc::class.java.getMethod("pid")
                val pid = m.invoke(proc)
                Runtime.getRuntime().exec(arrayOf("kill", "-SIGSTOP", pid.toString())).waitFor()
            } catch (_: Exception) {}
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        xServerView.onResume()
        containerProcess?.let { proc ->
            try {
                val m = proc::class.java.getMethod("pid")
                val pid = m.invoke(proc)
                Runtime.getRuntime().exec(arrayOf("kill", "-SIGCONT", pid.toString())).waitFor()
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        x11Server?.stop()
        x11Server = null
        containerRuntime?.stop(containerProcess)
        containerProcess = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    private fun enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }
}

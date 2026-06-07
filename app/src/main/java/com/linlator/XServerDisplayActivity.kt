package com.linlator

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import com.linlator.container.ContainerRuntime
import com.linlator.renderer.XServerView

class XServerDisplayActivity : Activity() {

    private lateinit var xServerView: XServerView
    private var containerRuntime: ContainerRuntime? = null
    private var containerProcess: Process? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )

        xServerView = XServerView(this)
        setContentView(xServerView)

        val containerId = intent?.getStringExtra("container_id")
        if (containerId != null) {
            val app = application as LinlatorApp
            val config = app.containerManager.get(containerId)
            if (config != null) {
                containerRuntime = ContainerRuntime(filesDir)
                containerRuntime?.start(config) { proc ->
                    containerProcess = proc
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        containerProcess?.let { proc ->
            try {
                val m = proc::class.java.getMethod("pid")
                val pid = m.invoke(proc)
                Runtime.getRuntime().exec(arrayOf("kill", "-SIGSTOP", pid.toString())).waitFor()
            } catch (_: Exception) { }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        containerProcess?.let { proc ->
            try {
                val m = proc::class.java.getMethod("pid")
                val pid = m.invoke(proc)
                Runtime.getRuntime().exec(arrayOf("kill", "-SIGCONT", pid.toString())).waitFor()
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        containerRuntime?.stop(containerProcess)
    }
}

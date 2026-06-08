package com.linlator.container

import java.io.File
import java.util.concurrent.TimeUnit

class ContainerRuntime(private val filesDir: File) {

    private var process: Process? = null

    fun start(config: ContainerConfig, processCallback: ((Process) -> Unit)? = null): Process {
        val containerDir = File(filesDir, "containers/container-${config.id}")
        val rootfs = File(containerDir, "rootfs")
        val prootBinary = File(filesDir, "proot/proot")

        if (!prootBinary.exists()) {
            throw IllegalStateException("PRoot binary not found at ${prootBinary.absolutePath}")
        }

        val x11SocketDir = File(filesDir, "xserver")
        x11SocketDir.mkdirs()

        val command = mutableListOf(
            prootBinary.absolutePath,
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${x11SocketDir.absolutePath}:/tmp/.X11-unix",
            "-b", "${containerDir.absolutePath}/tmp:/tmp",
            "-w", "/root",
            "--kill-on-exit"
        )

        for (bind in config.extraBinds) {
            command.add("-b")
            command.add(bind)
        }

        val desktopEntry = when (config.desktop) {
            "openbox" -> "openbox-session"
            "xfce4" -> "startxfce4"
            "lxde" -> "startlxde"
            "fluxbox" -> "startfluxbox"
            else -> "startxfce4"
        }
        command.add(desktopEntry)

        val pb = ProcessBuilder(command)
        pb.directory(rootfs)
        pb.redirectErrorStream(true)

        val env = pb.environment()
        env["DISPLAY"] = ":0"
        env["PULSE_SERVER"] = "tcp:127.0.0.1:4713"
        env["HOME"] = "/root"
        env["SHELL"] = "/bin/bash"
        env["USER"] = "root"
        env["XDG_RUNTIME_DIR"] = "/tmp/runtime-root"
        env["PATH"] = "/usr/local/bin:/usr/bin:/bin:/usr/games"
        env["TERM"] = "xterm-256color"
        for ((key, value) in config.envVars) {
            env[key] = value
        }

        process = pb.start()
        processCallback?.invoke(process!!)
        return process!!
    }

    fun stop(proc: Process?): Boolean {
        val p = proc ?: process ?: return false
        return try {
            p.destroy()
            p.waitFor(5, TimeUnit.SECONDS)
            p.destroyForcibly()
            process = null
            true
        } catch (_: Exception) {
            false
        }
    }

    fun pause(): Boolean {
        val pid = getPid() ?: return false
        return try {
            Runtime.getRuntime().exec(arrayOf("kill", "-SIGSTOP", pid)).waitFor()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun resume(): Boolean {
        val pid = getPid() ?: return false
        return try {
            Runtime.getRuntime().exec(arrayOf("kill", "-SIGCONT", pid)).waitFor()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getPid(): String? {
        return try {
            val m = process!!::class.java.getMethod("pid")
            m.invoke(process).toString()
        } catch (_: Exception) {
            null
        }
    }
}

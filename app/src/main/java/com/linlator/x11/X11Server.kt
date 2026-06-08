package com.linlator.x11

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class X11Server(private val socketPath: String) : Closeable {

    private val running = AtomicBoolean(false)
    private val windowMap = ConcurrentHashMap<Int, Window>()
    private val gcMap = ConcurrentHashMap<Int, GcState>()
    private val atoms = ConcurrentHashMap<String, Int>()
    private var rootWindow: Window? = null
    private var screenWidth = 1280
    private var screenHeight = 720
    private var lastWindowId = AtomicInteger(1)
    private var lastGcId = AtomicInteger(1)
    private var lastAtomId = AtomicInteger(100)
    private val connectionThreads = CopyOnWriteArrayList<Thread>()
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    private var connections = CopyOnWriteArrayList<Connection>()

    var onNewFrame: ((Window) -> Unit)? = null

    data class GcState(
        val id: Int,
        var foreground: Int = 0xFFFFFFFF.toInt(),
        var background: Int = 0xFF000000.toInt(),
        var function: Int = 3,
        var planeMask: Int = -1,
        var lineWidth: Int = 0,
        var lineStyle: Int = 0,
        var capStyle: Int = 0,
        var joinStyle: Int = 0,
        var fillStyle: Int = 0,
        var fillRule: Int = 0,
        var arcMode: Int = 0,
        var tile: Int = 0,
        var stipple: Int = 0,
        var tsXOrigin: Int = 0,
        var tsYOrigin: Int = 0,
        var font: Int = 0,
        var subwindowMode: Int = 0,
        var graphicsExposures: Int = 1,
        var clipXOrigin: Int = 0,
        var clipYOrigin: Int = 0,
        var clipMask: Int = 0,
        var dashOffset: Int = 0,
        var dashes: Int = 0
    )

    private class Connection(val input: InputStream, val output: OutputStream, val byteOrder: ByteOrder)

    fun start(width: Int = 1280, height: Int = 720) {
        if (running.getAndSet(true)) return
        screenWidth = width
        screenHeight = height

        val rootId = 1
        val root = Window(
            id = rootId,
            x = 0, y = 0,
            width = screenWidth, height = screenHeight,
            mapped = true,
            pixelData = IntArray(screenWidth * screenHeight) { 0xFF141218.toInt() }
        )
        windowMap[rootId] = root
        rootWindow = root
        lastWindowId.set(rootId + 1)

        serverSocket = ServerSocket(6000, 5, InetAddress.getByName("127.0.0.1"))

        acceptThread = thread(name = "X11-accept") {
            while (running.get()) {
                try {
                    val sock = serverSocket?.accept() ?: break
                    sock.keepAlive = true
                    sock.setSoTimeout(0)
                    val clientInput = sock.getInputStream()
                    val clientOutput = sock.getOutputStream()
                    val conn = handleConnection(clientInput, clientOutput)
                    if (conn != null) {
                        connections.add(conn)
                        val t = thread(name = "X11-client") {
                            processClient(conn)
                            connections.remove(conn)
                            try { clientInput.close() } catch (_: Exception) {}
                            try { clientOutput.close() } catch (_: Exception) {}
                        }
                        connectionThreads.add(t)
                    } else {
                        try { clientInput.close() } catch (_: Exception) {}
                        try { clientOutput.close() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    if (running.get()) {
                        try { Thread.sleep(100) } catch (_: InterruptedException) {}
                    }
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        try {
            acceptThread?.interrupt()
            for (t in connectionThreads) {
                try { t.interrupt() } catch (_: Exception) {}
            }
            connectionThreads.clear()
            for (conn in connections) {
                try { conn.input.close() } catch (_: Exception) {}
                try { conn.output.close() } catch (_: Exception) {}
            }
            connections.clear()
        } catch (_: Exception) {}
    }

    override fun close() {
        stop()
    }

    fun processRequests() {
        for (conn in connections) {
            try {
                val avail = conn.input.available()
                if (avail > 0) {
                    readAndProcess(conn)
                }
            } catch (_: Exception) {}
        }
    }

    fun getVisibleWindows(): List<Window> {
        return windowMap.values.filter { it.mapped && it.id != 1 }
    }

    fun getRootWindow(): Window? = rootWindow

    fun getWindow(id: Int): Window? = windowMap[id]

    private fun handleConnection(input: InputStream, output: OutputStream): Connection? {
        try {
            val header = ByteArray(12)
            var offset = 0
            while (offset < 12) {
                val read = input.read(header, offset, 12 - offset)
                if (read < 0) return null
                offset += read
            }

            val byteOrder: ByteOrder = when (header[0].toInt()) {
                0x42 -> ByteOrder.BIG_ENDIAN
                0x6c -> ByteOrder.LITTLE_ENDIAN
                else -> return null
            }
            val buf = ByteBuffer.wrap(header).order(byteOrder)

            val protocolMajor = buf.getShort(2).toInt() and 0xFFFF
            val protocolMinor = buf.getShort(4).toInt() and 0xFFFF
            val authNameLen = (buf.getShort(6).toInt() and 0xFFFF)
            val authDataLen = (buf.getShort(8).toInt() and 0xFFFF)
            val authPadding = (4 - (authNameLen % 4)) % 4
            val dataPadding = (4 - (authDataLen % 4)) % 4

            if (authNameLen > 0) {
                input.readNBytes(authNameLen + authPadding)
            }
            if (authDataLen > 0) {
                input.readNBytes(authDataLen + dataPadding)
            }

            val numFormats = 7
            val numScreens = 1
            val screenBufLen = 40
            val formatsLen = numFormats * 8
            var pad = (8 - (8 + formatsLen) % 8) % 8

            val totalLen = 8 + formatsLen + pad + screenBufLen
            val setupBuf = ByteBuffer.allocate(totalLen).order(byteOrder)

            setupBuf.put(1.toByte())
            setupBuf.put(6.toByte())
            setupBuf.putShort((totalLen / 4).toShort())
            setupBuf.putShort(0x0100.toShort())
            setupBuf.putShort(0x0000.toShort())
            setupBuf.putInt(0)
            setupBuf.putShort(0.toShort())
            setupBuf.putShort(numFormats.toShort())
            setupBuf.putShort(255.toShort())
            setupBuf.putShort(0.toShort())
            setupBuf.putShort(0.toShort())

            val formats = listOf(
                listOf<Any>(1, 1, 0, 0, 0),
                listOf<Any>(4, 8, 0, 0, 0),
                listOf<Any>(8, 8, 0, 0, 0),
                listOf<Any>(12, 16, 1, 5, 5),
                listOf<Any>(16, 16, 0, 0, 0),
                listOf<Any>(20, 32, 1, 8, 8),
                listOf<Any>(32, 32, 0, 0, 0)
            )
            for (f in formats) {
                setupBuf.put(f[0] as Byte)
                setupBuf.put(f[1] as Byte)
                setupBuf.putShort(0)
                val depth = f[2] as Int
                val bpp = f[3] as Int
                val pad2 = f[4] as Int
                setupBuf.put(depth.toByte())
                setupBuf.put(bpp.toByte())
                setupBuf.put(pad2.toByte())
                setupBuf.put(0.toByte())
                setupBuf.putShort(0)
                setupBuf.putInt(0)
                setupBuf.putInt(0)
            }

            pad = (8 - (8 + formatsLen) % 8) % 8
            for (i in 0 until pad) setupBuf.put(0.toByte())

            val rootWinId = 1
            setupBuf.putInt(rootWinId)
            setupBuf.putInt(rootWinId)
            setupBuf.putInt(rootWinId)
            val blackPixel = 0xFF000000.toInt()
            val whitePixel = 0xFFFFFFFF.toInt()
            setupBuf.putInt(blackPixel)
            setupBuf.putInt(whitePixel)
            setupBuf.putInt(0)
            setupBuf.putInt(screenWidth)
            setupBuf.putInt(0)
            setupBuf.putInt(screenHeight)
            setupBuf.putShort(0)
            setupBuf.putShort(0)
            setupBuf.putShort(screenWidth.toShort())
            setupBuf.putShort(screenHeight.toShort())
            setupBuf.putShort(0)
            setupBuf.put(24.toByte())
            setupBuf.put(0.toByte())
            setupBuf.putShort(0)
            setupBuf.putInt(0)
            setupBuf.putInt(0)
            setupBuf.putInt(0)
            setupBuf.putShort(0)
            setupBuf.putShort(0)
            setupBuf.putShort(0)
            setupBuf.putShort(0)
            setupBuf.put(1.toByte())
            setupBuf.put(0.toByte())

            output.write(setupBuf.array())
            output.flush()

            return Connection(input, output, byteOrder)
        } catch (_: Exception) {
            return null
        }
    }

    private fun readAndProcess(conn: Connection) {
        try {
            val avail = conn.input.available()
            if (avail <= 0) return
            val data = conn.input.readNBytes(avail)
            if (data.isEmpty()) return
            val buf = ByteBuffer.wrap(data).order(conn.byteOrder)
            while (buf.remaining() >= 4) {
                buf.mark()
                val opcode = buf.get().toInt() and 0xFF
                val dataByte = buf.get().toInt() and 0xFF
                val length = (buf.getShort().toInt() and 0xFFFF)
                if (length < 1) { buf.reset(); break }
                val bodyLen = (length - 1) * 4
                if (buf.remaining() < bodyLen) { buf.reset(); break }
                val requestData = ByteArray(bodyLen.coerceAtLeast(0))
                if (bodyLen > 0) buf.get(requestData)
                processRequest(conn, opcode, dataByte, length, requestData)
            }
        } catch (_: Exception) {}
    }

    private fun processClient(conn: Connection) {
        try {
            val input = conn.input
            val header = ByteArray(4)
            while (running.get()) {
                var offset = 0
                while (offset < 4) {
                    val read = input.read(header, offset, 4 - offset)
                    if (read < 0) return
                    offset += read
                }
                val buf = ByteBuffer.wrap(header).order(conn.byteOrder)
                val opcode = buf.get().toInt() and 0xFF
                val dataByte = buf.get().toInt() and 0xFF
                val length = (buf.getShort().toInt() and 0xFFFF)
                if (length < 1) continue

                val bodyLen = (length - 1) * 4
                val requestData = ByteArray(bodyLen.coerceAtLeast(0))
                if (bodyLen > 0) {
                    var readOffset = 0
                    while (readOffset < bodyLen) {
                        val read = input.read(requestData, readOffset, bodyLen - readOffset)
                        if (read < 0) return
                        readOffset += read
                    }
                }
                processRequest(conn, opcode, dataByte, length, requestData)
            }
        } catch (_: Exception) {}
    }

    private fun processRequest(conn: Connection, opcode: Int, dataByte: Int, length: Int, data: ByteArray) {
        val buf = ByteBuffer.wrap(data).order(conn.byteOrder)
        when (opcode) {
            1 -> requestCreateWindow(conn, buf, dataByte)
            2 -> requestChangeWindowAttributes(conn, buf)
            3 -> requestGetWindowAttributes(conn, buf)
            4 -> requestDestroyWindow(conn, buf)
            8 -> requestMapWindow(conn, buf)
            9 -> requestUnmapWindow(conn, buf)
            12 -> requestConfigureWindow(conn, buf)
            14 -> requestGetGeometry(conn, buf)
            16 -> requestInternAtom(conn, buf)
            18 -> requestChangeProperty(conn, buf)
            20 -> requestGetProperty(conn, buf)
            55 -> requestCreateGC(conn, buf)
            56 -> requestChangeGC(conn, buf)
            60 -> requestFreeGC(conn, buf)
            61 -> requestClearArea(conn, buf)
            62 -> requestCopyArea(conn, buf)
            70 -> requestPolyFillRectangle(conn, buf)
            72 -> requestPutImage(conn, buf, length)
            98 -> requestQueryExtension(conn, buf)
            104 -> requestGetSelectionOwner(conn, buf)
        }
    }

    private fun requestCreateWindow(conn: Connection, buf: ByteBuffer, depth: Int) {
        try {
            val wid = buf.getInt()
            val parentId = buf.getInt()
            val x = buf.getShort().toInt()
            val y = buf.getShort().toInt()
            val w = (buf.getShort().toInt() and 0xFFFF)
            val h = (buf.getShort().toInt() and 0xFFFF)
            val borderWidth = buf.getShort().toInt()
            val classId = (buf.getShort().toInt() and 0xFFFF)
            buf.get()
            buf.get()
            buf.get()
            buf.get()
            buf.getInt()
            buf.getInt()
            val eventMask = buf.getInt()
            val attrMask = buf.getInt()
            if (attrMask != 0) {
                var mask = attrMask
                while (mask != 0) {
                    buf.getInt()
                    mask = mask ushr 1
                }
            }

            val win = Window(
                id = wid,
                x = x, y = y,
                width = if (w > 0) w else 1,
                height = if (h > 0) h else 1,
                mapped = false,
                pixelData = IntArray(if (w > 0 && h > 0) w * h else 1) { 0 }
            )
            windowMap[wid] = win
        } catch (_: Exception) {}
    }

    private fun requestChangeWindowAttributes(conn: Connection, buf: ByteBuffer) {
        try {
            val winId = buf.getInt()
            val win = windowMap[winId] ?: return
            val attrMask = buf.getInt()
            var bitIndex = 0
            var mask = attrMask
            while (mask != 0) {
                if (mask and 1 != 0) {
                    buf.getInt()
                }
                mask = mask ushr 1
                bitIndex++
            }
        } catch (_: Exception) {}
    }

    private fun requestGetWindowAttributes(conn: Connection, buf: ByteBuffer) {
        try {
            val winId = buf.getInt()
            val reply = ByteBuffer.allocate(40).order(conn.byteOrder)
            reply.put(1.toByte())
            reply.put(1.toByte())
            reply.putShort(0)
            reply.putInt(2)
            reply.putInt(winId)
            reply.putShort(1)
            reply.put(0.toByte())
            reply.put(1.toByte())
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putShort(0)
            reply.putShort(0)
            reply.putShort(0)
            reply.put(0.toByte())
            reply.put(0.toByte())
            reply.putInt(0)
            val sendBuf = ByteArray(40)
            reply.position(0)
            reply.get(sendBuf)
            conn.output.write(sendBuf)
            conn.output.flush()
        } catch (_: Exception) {}
    }

    private fun requestDestroyWindow(conn: Connection, buf: ByteBuffer) {
        try {
            val winId = buf.getInt()
            windowMap.remove(winId)
        } catch (_: Exception) {}
    }

    private fun requestMapWindow(conn: Connection, buf: ByteBuffer) {
        try {
            val winId = buf.getInt()
            val win = windowMap[winId]
            if (win != null) {
                win.mapped = true
                sendExpose(conn, win)
            }
        } catch (_: Exception) {}
    }

    private fun requestUnmapWindow(conn: Connection, buf: ByteBuffer) {
        try {
            val winId = buf.getInt()
            val win = windowMap[winId]
            if (win != null) {
                win.mapped = false
            }
        } catch (_: Exception) {}
    }

    private fun requestConfigureWindow(conn: Connection, buf: ByteBuffer) {
        try {
            val winId = buf.getInt()
            val win = windowMap[winId] ?: return
            val mask = buf.getShort().toInt() and 0xFFFF
            if (mask and 1 != 0) win.x = buf.getShort().toInt()
            if (mask and 2 != 0) win.y = buf.getShort().toInt()
            if (mask and 4 != 0) {
                val newW = (buf.getShort().toInt() and 0xFFFF)
                if (newW > 0) {
                    val oldSize = win.width * win.height
                    win.width = newW
                    if (win.width * win.height != oldSize) {
                        val newData = IntArray(win.width * win.height) { 0 }
                        win.pixelData = newData
                    }
                }
            }
            if (mask and 8 != 0) {
                val newH = (buf.getShort().toInt() and 0xFFFF)
                if (newH > 0) {
                    val oldSize = win.width * win.height
                    win.height = newH
                    if (win.width * win.height != oldSize) {
                        val newData = IntArray(win.width * win.height) { 0 }
                        win.pixelData = newData
                    }
                }
            }
            if (win.mapped) {
                sendExpose(conn, win)
            }
        } catch (_: Exception) {}
    }

    private fun requestGetGeometry(conn: Connection, buf: ByteBuffer) {
        try {
            val drawable = buf.getInt()
            val win = windowMap[drawable]
            val reply = ByteBuffer.allocate(32).order(conn.byteOrder)
            reply.put(1.toByte())
            reply.put(24.toByte())
            reply.putShort(0)
            reply.putInt(0)
            reply.putInt(1)
            if (win != null) {
                reply.putShort(win.x.toShort())
                reply.putShort(win.y.toShort())
                reply.putShort(win.width.toShort())
                reply.putShort(win.height.toShort())
            } else {
                reply.putShort(0)
                reply.putShort(0)
                reply.putShort(0)
                reply.putShort(0)
            }
            reply.putShort(0)
            reply.putShort(0)
            reply.putInt(0)
            reply.putInt(0)
            val sendBuf = ByteArray(32)
            reply.position(0)
            reply.get(sendBuf)
            conn.output.write(sendBuf)
            conn.output.flush()
        } catch (_: Exception) {}
    }

    private fun requestInternAtom(conn: Connection, buf: ByteBuffer) {
        try {
            val nameLen = (buf.getShort().toInt() and 0xFFFF)
            buf.getShort()
            val nameBytes = ByteArray(nameLen)
            buf.get(nameBytes)
            val name = String(nameBytes, Charsets.US_ASCII)

            val atom = atoms.getOrPut(name) { lastAtomId.getAndIncrement() }

            val reply = ByteBuffer.allocate(32).order(conn.byteOrder)
            reply.put(1.toByte())
            reply.put(1.toByte())
            reply.putShort(0)
            reply.putInt(atom)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            val sendBuf = ByteArray(32)
            reply.position(0)
            reply.get(sendBuf)
            conn.output.write(sendBuf)
            conn.output.flush()
        } catch (_: Exception) {}
    }

    private fun requestChangeProperty(conn: Connection, buf: ByteBuffer) {
        try {
            buf.getInt()
            buf.getInt()
            buf.getInt()
            buf.get()
            buf.get()
            val dataUnits = (buf.getShort().toInt() and 0xFFFF)
            val dataBytes = dataUnits * 4
            val remaining = buf.remaining()
            if (remaining > 0) {
                buf.position(buf.position() + Math.min(remaining, dataBytes))
            }
        } catch (_: Exception) {}
    }

    private fun requestGetProperty(conn: Connection, buf: ByteBuffer) {
        try {
            buf.getInt()
            buf.getInt()
            buf.getInt()
            buf.getInt()
            buf.getInt()
            val reply = ByteBuffer.allocate(32).order(conn.byteOrder)
            reply.put(1.toByte())
            reply.put(0.toByte())
            reply.putShort(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            val sendBuf = ByteArray(32)
            reply.position(0)
            reply.get(sendBuf)
            conn.output.write(sendBuf)
            conn.output.flush()
        } catch (_: Exception) {}
    }

    private fun requestCreateGC(conn: Connection, buf: ByteBuffer) {
        try {
            val gcId = buf.getInt()
            val drawable = buf.getInt()
            val mask = buf.getInt()
            val gc = GcState(id = gcId)
            var bitIndex = 0
            var m = mask
            while (m != 0) {
                if (m and 1 != 0) {
                    val value = buf.getInt()
                    when (bitIndex) {
                        0 -> gc.function = value
                        1 -> gc.planeMask = value
                        2 -> gc.foreground = value
                        3 -> gc.background = value
                        4 -> gc.lineWidth = value
                        5 -> gc.lineStyle = value
                        6 -> gc.capStyle = value
                        7 -> gc.joinStyle = value
                        8 -> gc.fillStyle = value
                        9 -> gc.fillRule = value
                        10 -> gc.arcMode = value
                        11 -> gc.tile = value
                        12 -> gc.stipple = value
                        13 -> gc.tsXOrigin = value
                        14 -> gc.tsYOrigin = value
                        15 -> gc.font = value
                        16 -> gc.subwindowMode = value
                        17 -> gc.graphicsExposures = value
                        18 -> gc.clipXOrigin = value
                        19 -> gc.clipYOrigin = value
                        20 -> gc.clipMask = value
                        21 -> gc.dashOffset = value
                        22 -> gc.dashes = value
                    }
                }
                m = m ushr 1
                bitIndex++
            }
            gcMap[gcId] = gc
        } catch (_: Exception) {}
    }

    private fun requestChangeGC(conn: Connection, buf: ByteBuffer) {
        try {
            val gcId = buf.getInt()
            val gc = gcMap[gcId] ?: return
            val mask = buf.getInt()
            var bitIndex = 0
            var m = mask
            while (m != 0) {
                if (m and 1 != 0) {
                    val value = buf.getInt()
                    when (bitIndex) {
                        0 -> gc.function = value
                        1 -> gc.planeMask = value
                        2 -> gc.foreground = value
                        3 -> gc.background = value
                    }
                }
                m = m ushr 1
                bitIndex++
            }
        } catch (_: Exception) {}
    }

    private fun requestFreeGC(conn: Connection, buf: ByteBuffer) {
        try {
            val gcId = buf.getInt()
            gcMap.remove(gcId)
        } catch (_: Exception) {}
    }

    private fun requestClearArea(conn: Connection, buf: ByteBuffer) {
        try {
            val exposures = buf.get().toInt()
            buf.get()
            buf.getShort()
            val winId = buf.getInt()
            val x = buf.getShort().toInt()
            val y = buf.getShort().toInt()
            val w = (buf.getShort().toInt() and 0xFFFF)
            val h = (buf.getShort().toInt() and 0xFFFF)
            val win = windowMap[winId] ?: return
            if (win.pixelData != null) {
                val pd = win.pixelData!!
                for (row in y until (y + h)) {
                    for (col in x until (x + w)) {
                        val idx = row * win.width + col
                        if (idx in pd.indices) {
                            pd[idx] = 0
                        }
                    }
                }
                win.damaged = true
            }
            if (exposures != 0 && win.mapped) {
                sendExpose(conn, win)
            }
        } catch (_: Exception) {}
    }

    private fun requestCopyArea(conn: Connection, buf: ByteBuffer) {
        try {
            val srcDrawable = buf.getInt()
            val dstDrawable = buf.getInt()
            val gc = buf.getInt()
            val srcX = buf.getShort().toInt()
            val srcY = buf.getShort().toInt()
            val dstX = buf.getShort().toInt()
            val dstY = buf.getShort().toInt()
            val w = (buf.getShort().toInt() and 0xFFFF)
            val h = (buf.getShort().toInt() and 0xFFFF)
            val srcWin = windowMap[srcDrawable]
            val dstWin = windowMap[dstDrawable]
            if (srcWin != null && dstWin != null && srcWin.pixelData != null && dstWin.pixelData != null) {
                for (row in 0 until h) {
                    for (col in 0 until w) {
                        val sx = srcX + col
                        val sy = srcY + row
                        val dx = dstX + col
                        val dy = dstY + row
                        if (sx in 0 until srcWin.width && sy in 0 until srcWin.height &&
                            dx in 0 until dstWin.width && dy in 0 until dstWin.height) {
                            val si = sy * srcWin.width + sx
                            val di = dy * dstWin.width + dx
                            dstWin.pixelData!![di] = srcWin.pixelData!![si]
                        }
                    }
                }
                dstWin.damaged = true
            }
            val srcGc = gcMap[gc]
            if (srcGc != null && srcGc.graphicsExposures != 0) {
                sendExpose(conn, dstWin ?: return)
            }
        } catch (_: Exception) {}
    }

    private fun requestPolyFillRectangle(conn: Connection, buf: ByteBuffer) {
        try {
            val drawable = buf.getInt()
            val gcId = buf.getInt()
            val gc = gcMap[gcId]
            val win = windowMap[drawable] ?: return
            val numRects = (buf.remaining() / 8)
            if (win.pixelData == null) return
            val pd = win.pixelData!!
            val fg = gc?.foreground ?: 0xFFFFFFFF.toInt()
            for (i in 0 until numRects) {
                val x = buf.getShort().toInt()
                val y = buf.getShort().toInt()
                val w = (buf.getShort().toInt() and 0xFFFF)
                val h = (buf.getShort().toInt() and 0xFFFF)
                for (row in y until (y + h)) {
                    for (col in x until (x + w)) {
                        val idx = row * win.width + col
                        if (idx in pd.indices && row >= 0 && row < win.height && col >= 0 && col < win.width) {
                            pd[idx] = fg
                        }
                    }
                }
            }
            win.damaged = true
        } catch (_: Exception) {}
    }

    private fun requestPutImage(conn: Connection, buf: ByteBuffer, length: Int) {
        try {
            val format = buf.get().toInt() and 0xFF
            val drawable = buf.getInt()
            val gcId = buf.getInt()
            val dstX = buf.getShort().toInt()
            val dstY = buf.getShort().toInt()
            val w = (buf.getShort().toInt() and 0xFFFF)
            val h = (buf.getShort().toInt() and 0xFFFF)
            val zero = buf.get().toInt() and 0xFF
            val depth = buf.get().toInt() and 0xFF
            buf.getShort()
            val win = windowMap[drawable] ?: return

            val dataSize = when (format) {
                2 -> w * h * 4
                else -> w * h * (if (depth <= 8) 1 else if (depth <= 16) 2 else 4)
            }

            if (win.pixelData == null || win.pixelData!!.size < win.width * win.height) {
                win.pixelData = IntArray(win.width * win.height) { 0 }
            }
            val pd = win.pixelData!!

            if (format == 2) {
                val pixelBytes = ByteArray(w * h * 4)
                val bytesToRead = Math.min(pixelBytes.size, buf.remaining())
                buf.get(pixelBytes, 0, bytesToRead)
                for (row in 0 until h) {
                    for (col in 0 until w) {
                        val srcIdx = (row * w + col) * 4
                        val dstIdx = (dstY + row) * win.width + (dstX + col)
                        if (dstIdx in pd.indices && srcIdx + 3 < pixelBytes.size) {
                            val a = pixelBytes[srcIdx + 3].toInt() and 0xFF
                            val r = pixelBytes[srcIdx + 2].toInt() and 0xFF
                            val g = pixelBytes[srcIdx + 1].toInt() and 0xFF
                            val b = pixelBytes[srcIdx].toInt() and 0xFF
                            pd[dstIdx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                }
            } else if (format == 1) {
                for (row in 0 until h) {
                    for (col in 0 until w) {
                        val byteIdx = row * ((w + 3) / 4 * 4) + col
                        if (byteIdx >= buf.remaining()) break
                        val pixel = buf.get(buf.position() + byteIdx).toInt() and 0xFF
                        val dstIdx = (dstY + row) * win.width + (dstX + col)
                        if (dstIdx in pd.indices) {
                            pd[dstIdx] = pixel or (pixel shl 8) or (pixel shl 16) or (0xFF shl 24)
                        }
                    }
                }
            }

            win.damaged = true
            if (win.mapped) {
                onNewFrame?.invoke(win)
            }
        } catch (_: Exception) {}
    }

    private fun requestQueryExtension(conn: Connection, buf: ByteBuffer) {
        try {
            val nameLen = (buf.getShort().toInt() and 0xFFFF)
            buf.getShort()
            val nameBytes = ByteArray(nameLen)
            buf.get(nameBytes)
            val reply = ByteBuffer.allocate(32).order(conn.byteOrder)
            reply.put(1.toByte())
            reply.put(1.toByte())
            reply.putShort(0)
            reply.put(0.toByte())
            reply.put(0.toByte())
            reply.put(0.toByte())
            reply.put(0.toByte())
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            val sendBuf = ByteArray(32)
            reply.position(0)
            reply.get(sendBuf)
            conn.output.write(sendBuf)
            conn.output.flush()
        } catch (_: Exception) {}
    }

    private fun requestGetSelectionOwner(conn: Connection, buf: ByteBuffer) {
        try {
            buf.getInt()
            val reply = ByteBuffer.allocate(32).order(conn.byteOrder)
            reply.put(1.toByte())
            reply.put(1.toByte())
            reply.putShort(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            reply.putInt(0)
            val sendBuf = ByteArray(32)
            reply.position(0)
            reply.get(sendBuf)
            conn.output.write(sendBuf)
            conn.output.flush()
        } catch (_: Exception) {}
    }

    private fun sendExpose(conn: Connection, win: Window) {
        try {
            val ev = ByteBuffer.allocate(32).order(conn.byteOrder)
            ev.put(12.toByte())
            ev.put(0.toByte())
            ev.putShort(0)
            ev.putShort(0)
            ev.putShort(0)
            ev.putShort(0)
            ev.putShort(win.width.toShort())
            ev.putShort(win.height.toShort())
            ev.putShort(0.toShort())
            ev.putInt(0)
            ev.putInt(0)
            ev.putInt(0)
            ev.putInt(0)
            val sendBuf = ByteArray(32)
            ev.position(0)
            ev.get(sendBuf)
            conn.output.write(sendBuf)
            conn.output.flush()
        } catch (_: Exception) {}
    }

    private var cursorX = 0f
    private var cursorY = 0f

    fun injectPointerMotion(dx: Float, dy: Float) {
        cursorX = (cursorX + dx).coerceIn(0f, screenWidth.toFloat())
        cursorY = (cursorY + dy).coerceIn(0f, screenHeight.toFloat())
        sendEventToClients { byteOrder ->
            val ev = ByteBuffer.allocate(32).order(byteOrder)
            ev.put(6.toByte())
            ev.put(0.toByte())
            ev.putShort(0)
            ev.putInt(0)
            ev.putInt(rootWindow?.id ?: 1)
            ev.putInt(rootWindow?.id ?: 1)
            ev.putInt(0)
            ev.putShort(cursorX.toInt().toShort())
            ev.putShort(cursorY.toInt().toShort())
            ev.putShort(cursorX.toInt().toShort())
            ev.putShort(cursorY.toInt().toShort())
            ev.putShort(0)
            ev.put(1.toByte())
            ev.put(0.toByte())
            ev.putInt(0)
            ev.putInt(0)
            ev.putInt(0)
            val buf = ByteArray(32)
            ev.position(0)
            ev.get(buf)
            buf
        }
    }

    fun injectButtonPress(button: Int) {
        sendEventToClients { byteOrder ->
            val ev = ByteBuffer.allocate(32).order(byteOrder)
            ev.put(4.toByte())
            ev.put(button.toByte())
            ev.putShort(0)
            ev.putInt(0)
            ev.putInt(rootWindow?.id ?: 1)
            ev.putInt(rootWindow?.id ?: 1)
            ev.putInt(0)
            ev.putShort(cursorX.toInt().toShort())
            ev.putShort(cursorY.toInt().toShort())
            ev.putShort(cursorX.toInt().toShort())
            ev.putShort(cursorY.toInt().toShort())
            ev.putShort(0)
            ev.put(1.toByte())
            ev.put(0.toByte())
            ev.putInt(0)
            ev.putInt(0)
            ev.putInt(0)
            val buf = ByteArray(32)
            ev.position(0)
            ev.get(buf)
            buf
        }
    }

    fun injectButtonRelease(button: Int) {
        sendEventToClients { byteOrder ->
            val ev = ByteBuffer.allocate(32).order(byteOrder)
            ev.put(5.toByte())
            ev.put(button.toByte())
            ev.putShort(0)
            ev.putInt(0)
            ev.putInt(rootWindow?.id ?: 1)
            ev.putInt(rootWindow?.id ?: 1)
            ev.putInt(0)
            ev.putShort(cursorX.toInt().toShort())
            ev.putShort(cursorY.toInt().toShort())
            ev.putShort(cursorX.toInt().toShort())
            ev.putShort(cursorY.toInt().toShort())
            ev.putShort(0)
            ev.put(1.toByte())
            ev.put(0.toByte())
            ev.putInt(0)
            ev.putInt(0)
            ev.putInt(0)
            val buf = ByteArray(32)
            ev.position(0)
            ev.get(buf)
            buf
        }
    }

    fun injectKeyPress(keyCode: Int) {
        sendEventToClients { byteOrder ->
            val ev = ByteBuffer.allocate(32).order(byteOrder)
            ev.put(2.toByte())
            ev.put((keyCode and 0xFF).toByte())
            ev.putShort(0)
            ev.putInt(0)
            ev.putInt(rootWindow?.id ?: 1)
            ev.putInt(rootWindow?.id ?: 1)
            ev.putInt(0)
            ev.putShort(cursorX.toInt().toShort())
            ev.putShort(cursorY.toInt().toShort())
            ev.putShort(cursorX.toInt().toShort())
            ev.putShort(cursorY.toInt().toShort())
            ev.putShort(0)
            ev.put(1.toByte())
            ev.put(0.toByte())
            ev.putInt(0)
            ev.putInt(0)
            ev.putInt(0)
            val buf = ByteArray(32)
            ev.position(0)
            ev.get(buf)
            buf
        }
    }

    fun injectKeyRelease(keyCode: Int) {
        sendEventToClients { byteOrder ->
            val ev = ByteBuffer.allocate(32).order(byteOrder)
            ev.put(3.toByte())
            ev.put((keyCode and 0xFF).toByte())
            ev.putShort(0)
            ev.putInt(0)
            ev.putInt(rootWindow?.id ?: 1)
            ev.putInt(rootWindow?.id ?: 1)
            ev.putInt(0)
            ev.putShort(cursorX.toInt().toShort())
            ev.putShort(cursorY.toInt().toShort())
            ev.putShort(cursorX.toInt().toShort())
            ev.putShort(cursorY.toInt().toShort())
            ev.putShort(0)
            ev.put(1.toByte())
            ev.put(0.toByte())
            ev.putInt(0)
            ev.putInt(0)
            ev.putInt(0)
            val buf = ByteArray(32)
            ev.position(0)
            ev.get(buf)
            buf
        }
    }

    fun injectScroll(dx: Float, dy: Float) {
        if (dy < -10f) {
            injectButtonPress(4)
            injectButtonRelease(4)
        } else if (dy > 10f) {
            injectButtonPress(5)
            injectButtonRelease(5)
        }
        if (dx < -10f) {
            injectButtonPress(6)
            injectButtonRelease(6)
        } else if (dx > 10f) {
            injectButtonPress(7)
            injectButtonRelease(7)
        }
    }

    private fun sendEventToClients(eventBuilder: (ByteOrder) -> ByteArray) {
        val bufferedEvents = mutableListOf<Pair<Connection, ByteArray>>()
        for (conn in connections) {
            try {
                val bytes = eventBuilder(conn.byteOrder)
                bufferedEvents.add(conn to bytes)
            } catch (_: Exception) {}
        }
        for ((conn, bytes) in bufferedEvents) {
            try {
                conn.output.write(bytes)
                conn.output.flush()
            } catch (_: Exception) {}
        }
    }

}

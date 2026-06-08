package com.linlator.renderer

import android.opengl.GLES30

object X11Renderer {

    private val vertexShaderSource = """
#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vTexCoord;
uniform mat4 uTransform;
void main() {
    gl_Position = uTransform * aPosition;
    vTexCoord = aTexCoord;
}
""".trimIndent()

    private val fragmentShaderSource = """
#version 300 es
precision mediump float;
in vec2 vTexCoord;
out vec4 fragColor;
uniform sampler2D uTexture;
void main() {
    fragColor = texture(uTexture, vTexCoord);
}
""".trimIndent()

    private var program: Int = 0
    private var initialized = false

    private val vertices = floatArrayOf(
        -1f, -1f, 0f, 0f,
         1f, -1f, 1f, 0f,
        -1f,  1f, 0f, 1f,
         1f,  1f, 1f, 1f
    )

    private var vbo: Int = 0
    private var vao: Int = 0

    private var uTextureLocation: Int = 0
    private var uTransformLocation: Int = 0

    fun initialize() {
        if (initialized) return
        program = createProgram(vertexShaderSource, fragmentShaderSource)
        if (program == 0) throw RuntimeException("Failed to create shader program")
        uTextureLocation = GLES30.glGetUniformLocation(program, "uTexture")
        uTransformLocation = GLES30.glGetUniformLocation(program, "uTransform")

        val vboArr = IntArray(1)
        val vaoArr = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoArr, 0)
        GLES30.glGenBuffers(1, vboArr, 0)
        vao = vaoArr[0]
        vbo = vboArr[0]

        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)

        val vertBuf = java.nio.ByteBuffer.allocateDirect(vertices.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertBuf.put(vertices)
        vertBuf.position(0)

        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertBuf, GLES30.GL_STATIC_DRAW)

        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)
        GLES30.glEnableVertexAttribArray(1)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)

        initialized = true
    }

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES30.glDeleteShader(vertexShader)
            return 0
        }
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vertexShader)
        GLES30.glAttachShader(prog, fragmentShader)
        GLES30.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            GLES30.glDeleteProgram(prog)
            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)
            return 0
        }
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        return prog
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun createTexture(width: Int, height: Int): Int {
        val texArr = IntArray(1)
        GLES30.glGenTextures(1, texArr, 0)
        val texId = texArr[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return texId
    }

    fun updateTexture(texId: Int, pixels: IntArray, width: Int, height: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        val buf = java.nio.ByteBuffer.allocateDirect(pixels.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asIntBuffer()
        buf.put(pixels)
        buf.position(0)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    fun drawTexturedQuad(texId: Int, x: Float, y: Float, w: Float, h: Float, screenW: Float, screenH: Float) {
        val left = x / screenW * 2f - 1f
        val right = (x + w) / screenW * 2f - 1f
        val bottom = 1f - (y + h) / screenH * 2f
        val top = 1f - y / screenH * 2f

        val transform = floatArrayOf(
            right - left, 0f, 0f, left,
            0f, top - bottom, 0f, bottom,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uTransformLocation, 1, false, transform, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glUniform1i(uTextureLocation, 0)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    fun deleteTexture(texId: Int) {
        GLES30.glDeleteTextures(1, intArrayOf(texId), 0)
    }
}

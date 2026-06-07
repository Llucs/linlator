package com.linlator.container

import com.google.gson.Gson

data class ContainerConfig(
    val id: String = "",
    val name: String = "",
    val distro: String = "ubuntu",
    val screenWidth: Int = 1280,
    val screenHeight: Int = 720,
    val desktop: String = "xfce4",
    val graphicsDriver: String = "virgl",
    val envVars: Map<String, String> = emptyMap(),
    val cpuList: List<Int> = emptyList(),
    val audioDriver: String = "pulseaudio",
    val dpi: Int = 160,
    val startupMode: String = "normal",
    val extraBinds: List<String> = emptyList(),
    val installPackages: List<String> = emptyList()
) {
    fun toJson(): String {
        return Gson().toJson(this)
    }

    companion object {
        fun fromJson(json: String): ContainerConfig {
            return Gson().fromJson(json, ContainerConfig::class.java)
        }
    }
}

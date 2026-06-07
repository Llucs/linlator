package com.linlator.container

import java.io.File

class ContainerManager(private val filesDir: File) {

    private val containersDir: File
        get() = File(filesDir, "containers").also { it.mkdirs() }

    fun list(): List<ContainerConfig> {
        val result = mutableListOf<ContainerConfig>()
        val dir = containersDir
        if (!dir.exists()) return result
        dir.listFiles()?.forEach { child ->
            val configFile = File(child, ".container.json")
            if (configFile.exists()) {
                try {
                    val config = ContainerConfig.fromJson(configFile.readText())
                    result.add(config)
                } catch (_: Exception) { }
            }
        }
        return result.sortedBy { it.name }
    }

    fun create(config: ContainerConfig): ContainerConfig {
        val containerDir = File(containersDir, "container-${config.id}")
        containerDir.mkdirs()
        val configFile = File(containerDir, ".container.json")
        configFile.writeText(config.toJson())
        return config
    }

    fun get(id: String): ContainerConfig? {
        val containerDir = File(containersDir, "container-$id")
        val configFile = File(containerDir, ".container.json")
        if (!configFile.exists()) return null
        return try {
            ContainerConfig.fromJson(configFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun update(config: ContainerConfig): ContainerConfig {
        val containerDir = File(containersDir, "container-${config.id}")
        val configFile = File(containerDir, ".container.json")
        configFile.writeText(config.toJson())
        return config
    }

    fun delete(id: String): Boolean {
        val containerDir = File(containersDir, "container-$id")
        if (!containerDir.exists()) return false
        containerDir.deleteRecursively()
        return true
    }
}

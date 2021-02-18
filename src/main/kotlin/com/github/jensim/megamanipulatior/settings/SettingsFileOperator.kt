package com.github.jensim.megamanipulatior.settings

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.jensim.megamanipulatior.settings.ProjectOperator.project
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import java.io.File
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.text.Charsets.UTF_8
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SettingsFileOperator {

    private val validationFuse = AtomicBoolean()
    private val NOTIFICATION_GROUP = NotificationGroup("Custom Notification Group", NotificationDisplayType.BALLOON, true)
    private const val fileName = "mega-manipulator.yml"
    var lastSeenChange: Long = 0
    val objectMapper: ObjectMapper by lazy {
        ObjectMapper(YAMLFactory())
            .registerKotlinModule()
    }
    private val settingsFile: File
        get() = File("${project.basePath}", fileName)

    private val dummyYaml: String by lazy {
        """
# Please edit this file to set up the plugin
# Removing the file will reset the file to the example state

${objectMapper.writeValueAsString(dummy())}
"""
    }

    fun initFileWatcher() {
        println("Init coroutine")
        GlobalScope.launch {
            while (true) {
                try {
                    if (!settingsFile.exists()) {
                        println("Creating settings file")
                        writeSettings(dummyYaml)
                    } else {
                        readSettings()
                        if (validationFuse.getAndSet(false)) {
                            lastSeenChange = settingsFile.lastModified()
                            NOTIFICATION_GROUP.createNotification("Okay validation", NotificationType.INFORMATION)
                                .notify(project)
                        }
                    }
                } catch (e: Exception) {
                    validationFuse.set(true)
                    if (lastSeenChange < settingsFile.lastModified()) {
                        lastSeenChange = settingsFile.lastModified()
                        NOTIFICATION_GROUP.createNotification("Failed validation: ${e.message}", NotificationType.WARNING)
                            .notify(project)
                        e.printStackTrace()
                    }
                }
                delay(Duration.ofSeconds(5).toMillis())
            }
        }
    }

    private fun readSettings(): MegaManipulatorSettings {
        try {
            val yaml = String(settingsFile.readBytes(), UTF_8)
            return objectMapper.readValue(yaml)
        } catch (e: Exception) {
            throw RuntimeException(e.message, e)
        }
    }

    fun readSettingsOrNull(): MegaManipulatorSettings? {
        return try {
            return readSettings()
        } catch (e: Exception) {
            null
        }
    }

    private fun dummy() = MegaManipulatorSettings(
        sourceGraphSettings = SourceGraphSettings(baseUrl = "https://sourcegraph.example.com"),
        codeHostSettings = listOf(
            CodeHostSettingsWrapper(
                type = CodeHostType.BITBUCKET_SERVER,
                BitBucketSettings(
                    baseUrl = "https://bitbucket.example.com",
                    sourceGraphName = "bitbucket"
                )
            )
        )
    )

    private fun writeSettings(yaml: String) {
        if (!settingsFile.exists()) {
            settingsFile.createNewFile()
        }
        settingsFile.writeBytes(yaml.toByteArray(charset = UTF_8))
    }
}

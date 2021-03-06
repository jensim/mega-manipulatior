package com.github.jensim.megamanipulator.settings

import com.github.jensim.megamanipulator.actions.NotificationsOperator
import com.github.jensim.megamanipulator.settings.passwords.ProjectOperator
import com.github.jensim.megamanipulator.settings.types.MegaManipulatorSettings
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.fileEditor.FileDocumentManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.text.Charsets.UTF_8

class SettingsFileOperator(
    private val settingsFileName: String = "config/mega-manipulator.json",
    private val scriptFileName: String = "config/mega-manipulator.bash",
    private val projectOperator: ProjectOperator,
    private val notificationsOperator: NotificationsOperator,
) {

    private val lastPeek = AtomicLong(0L)
    private val lastUpdated: AtomicLong = AtomicLong(0L)
    private val bufferedSettings: AtomicReference<MegaManipulatorSettings?> = AtomicReference(null)
    private val settingsFile: File
        get() = File(projectOperator.project.basePath!!, settingsFileName)
    val scriptFile: File
        get() = File(projectOperator.project.basePath!!, scriptFileName)

    private val okValidationText = "Settings are valid"
    private val privateValidationText = AtomicReference("Settings are not yet validated")
    val validationText: String
        get() = privateValidationText.acquire

    internal fun readSettings(): MegaManipulatorSettings? {
        if (System.currentTimeMillis() - lastPeek.get() < 250) {
            return bufferedSettings.get()
        }
        try {
            try {
                FileDocumentManager.getInstance().saveAllDocuments()
            } catch (e: Exception) {
                // e.printStackTrace()
            }
            if (lastUpdated.get() == settingsFile.lastModified()) {
                lastPeek.set(System.currentTimeMillis())
                bufferedSettings.get()
            } else {
                val json = String(settingsFile.readBytes(), UTF_8)
                val readValue: MegaManipulatorSettings? = Json.decodeFromString(json)
                readValue?.let {
                    lastUpdated.set(settingsFile.lastModified())
                    bufferedSettings.set(it)
                    lastPeek.set(System.currentTimeMillis())
                }
                privateValidationText.set(okValidationText)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            privateValidationText.set(e.message)
            notificationsOperator.show(
                title = "Failed settings validation",
                body = "Failed settings validation: ${e.message}",
                type = WARNING
            )
        }
        return bufferedSettings.get()
    }
}

package com.predictivecoder.ayinamaerik.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.InputValidator
import com.predictivecoder.ayinamaerik.config.PredictiveCoderConfig
import java.io.File

class ConfigureApiKeyAction : AnAction("Configure OpenAI API Key") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val config = PredictiveCoderConfig.getInstance()

        val validator = object : InputValidator {
            override fun checkInput(inputString: String): Boolean {
                return inputString.trim().length >= 20 // Vérification basique de la longueur
            }

            override fun canClose(inputString: String): Boolean {
                return checkInput(inputString)
            }
        }

        val dialog = Messages.showInputDialog(
            project,
            "Enter your OpenAI API Key:",
            "Configure PredictiveCoder",
            Messages.getQuestionIcon(),
            config.apiKey,
            validator
        )

        if (dialog != null) {
            try {
                // Sauvegarde dans la configuration
                config.apiKey = dialog.trim()

                // Sauvegarde dans le fichier
                val configDir = File(System.getProperty("user.home") + "/.predictivecoder")
                configDir.mkdirs()
                File(configDir, "config").writeText("OPENAI_API_KEY=${dialog.trim()}")

                // Test de validation
                if (config.isConfigured()) {
                    Messages.showInfoMessage(
                        project,
                        "API key configured successfully!",
                        "PredictiveCoder Configuration"
                    )
                } else {
                    Messages.showErrorDialog(
                        project,
                        "API key validation failed",
                        "Configuration Error"
                    )
                }
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to save configuration: ${e.message}",
                    "Configuration Error"
                )
            }
        }
    }
}
package com.predictivecoder.ayinamaerik.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.predictivecoder.ayinamaerik.services.SettingsService
import com.predictivecoder.ayinamaerik.services.OpenAIService
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.*

class PredictiveCoderConfigurable : Configurable {
    private var mainPanel: JPanel? = null
    private var enabledCheckbox: JBCheckBox? = null
    private var autoSuggestCheckbox: JBCheckBox? = null
    private var delayField: JBTextField? = null
    private val settings = SettingsService.getInstance()

    override fun createComponent(): JComponent? {
        enabledCheckbox = JBCheckBox("Enable PredictiveCoder", settings.isEnabled)
        autoSuggestCheckbox = JBCheckBox("Enable automatic suggestions", settings.autoSuggestEnabled)
        delayField = JBTextField(settings.suggestionDelay.toString())

        val testButton = createTestButton()

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(enabledCheckbox!!)
            .addComponent(autoSuggestCheckbox!!)
            .addLabeledComponent(JBLabel("Suggestion delay (ms):"), delayField!!)
            .addComponent(testButton)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return mainPanel
    }

    private fun createTestButton(): JButton {
        return JButton("Test API Key").apply {
            addActionListener {
                val config = com.predictivecoder.ayinamaerik.config.PredictiveCoderConfig.getInstance()
                if (!config.isConfigured()) {
                    Messages.showErrorDialog(
                        "API key not configured",
                        "Configuration Error"
                    )
                    return@addActionListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val service = OpenAIService.getInstance()
                        val isValid = service.testApiKey()
                        withContext(Dispatchers.Main) {
                            if (isValid) {
                                Messages.showInfoMessage(
                                    "API key is valid and working!",
                                    "API Test Success"
                                )
                            } else {
                                Messages.showErrorDialog(
                                    "API key test failed: Invalid key or API error",
                                    "API Test Failed"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Messages.showErrorDialog(
                                "API key test failed: ${e.message}",
                                "API Test Failed"
                            )
                        }
                    }
                }
            }
        }
    }

    override fun isModified(): Boolean {
        return settings.isEnabled != enabledCheckbox?.isSelected
                || settings.autoSuggestEnabled != autoSuggestCheckbox?.isSelected
                || settings.suggestionDelay.toString() != delayField?.text
    }

    override fun apply() {
        settings.isEnabled = enabledCheckbox?.isSelected ?: true
        settings.autoSuggestEnabled = autoSuggestCheckbox?.isSelected ?: true
        settings.suggestionDelay = delayField?.text?.toIntOrNull() ?: 500
    }

    override fun getDisplayName(): String = "PredictiveCoder"

    override fun reset() {
        enabledCheckbox?.isSelected = settings.isEnabled
        autoSuggestCheckbox?.isSelected = settings.autoSuggestEnabled
        delayField?.text = settings.suggestionDelay.toString()
    }
}
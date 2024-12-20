package com.predictivecoder.ayinamaerik.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.predictivecoder.ayinamaerik.services.SettingsService
import java.awt.BorderLayout
import javax.swing.JPanel

class PredictiveCoderToolWindow : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentPanel = PredictiveCoderToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(contentPanel, "", false)
        Disposer.register(project, contentPanel)
        toolWindow.contentManager.addContent(content)
    }

    private class PredictiveCoderToolWindowPanel(project: Project) : JBPanel<PredictiveCoderToolWindowPanel>(), Disposable {
        private val settings = SettingsService.getInstance()

        init {
            layout = BorderLayout()
            add(JBLabel("PredictiveCoder Status: ${if (settings.isEnabled) "Enabled" else "Disabled"}"), BorderLayout.NORTH)
        }

        override fun dispose() {
            // Cleanup resources
        }
    }
}
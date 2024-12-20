package com.predictivecoder.ayinamaerik.services

import com.intellij.ide.DataManager
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.predictivecoder.ayinamaerik.config.PredictiveCoderConfig

@Service(Service.Level.PROJECT)
class PredictiveCoderProjectService(private val project: Project) : Disposable {
    init {
        checkApiKeyConfiguration()
    }

    private fun checkApiKeyConfiguration() {
        val config = PredictiveCoderConfig.getInstance()
        if (!config.isConfigured()) {
            ApplicationManager.getApplication().invokeLater {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("PredictiveCoder Notifications")
                    .createNotification(
                        "PredictiveCoder needs configuration",
                        "Please configure your OpenAI API key in Tools -> Configure OpenAI API Key",
                        NotificationType.WARNING
                    )
                    .addAction(NotificationAction.create("Configure") { _, notification ->
                        ActionManager.getInstance()
                            .getAction("PredictiveCoder.ConfigureApiKey")
                            .actionPerformed(
                                AnActionEvent.createFromDataContext(
                                "Configure",
                                Presentation(),
                                DataManager.getInstance().getDataContext()
                            ))
                        notification.expire()
                    })
                    .notify(project)
            }
        }
    }

    override fun dispose() {}
}
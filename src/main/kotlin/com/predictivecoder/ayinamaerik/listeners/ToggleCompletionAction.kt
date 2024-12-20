package com.predictivecoder.ayinamaerik.listeners

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.predictivecoder.ayinamaerik.services.SettingsService

class ToggleCompletionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val settings = SettingsService.getInstance()
        settings.autoSuggestEnabled = !settings.autoSuggestEnabled

        val message = if (settings.autoSuggestEnabled)
            "PredictiveCoder auto-suggestions enabled"
        else
            "PredictiveCoder auto-suggestions disabled"

        Messages.showInfoMessage(
            e.project,
            message,
            "PredictiveCoder"
        )
    }
}
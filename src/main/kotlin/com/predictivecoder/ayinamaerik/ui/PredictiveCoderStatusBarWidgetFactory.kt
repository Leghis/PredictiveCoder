package com.predictivecoder.ayinamaerik.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import com.predictivecoder.ayinamaerik.services.SettingsService
import java.awt.event.MouseEvent

class PredictiveCoderStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "PredictiveCoderStatus"
    override fun getDisplayName(): String = "PredictiveCoder"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = PredictiveCoderStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }
    override fun isEnabledByDefault(): Boolean = true
}

private class PredictiveCoderStatusBarWidget(private val project: Project) : StatusBarWidget,
    StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null
    private val settings = SettingsService.getInstance()

    override fun ID(): String = "PredictiveCoderStatus"
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }
    override fun dispose() {
        statusBar = null
    }

    override fun getText(): String {
        return if (settings.isEnabled) "PredictiveCoder: On" else "PredictiveCoder: Off"
    }

    override fun getAlignment(): Float = 0f

    override fun getTooltipText(): String {
        return if (settings.isEnabled) {
            "PredictiveCoder is enabled (Click to disable)"
        } else {
            "PredictiveCoder is disabled (Click to enable)"
        }
    }

    // Correction ici : utilisation du bon type de Consumer
    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer<MouseEvent> {
        settings.isEnabled = !settings.isEnabled
        statusBar?.updateWidget(ID())
    }
}
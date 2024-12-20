package com.predictivecoder.ayinamaerik.listeners

import com.intellij.openapi.components.Service
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ProjectOpenCloseListener : ProjectManagerListener {
    private val projectListeners = ConcurrentHashMap<Project, EditorListener>()

    override fun projectOpened(project: Project) {
        val listener = EditorListener(project)
        projectListeners[project] = listener

        Disposer.register(project, Disposable {
            EditorFactory.getInstance().eventMulticaster.removeDocumentListener(listener)
            EditorFactory.getInstance().eventMulticaster.removeCaretListener(listener)
            listener.dispose()
        })

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, project)
        EditorFactory.getInstance().eventMulticaster.addCaretListener(listener, project)
    }

    override fun projectClosed(project: Project) {
        projectListeners.remove(project)?.dispose()
    }

    companion object {
        fun getInstance(project: Project): ProjectOpenCloseListener =
            project.getService(ProjectOpenCloseListener::class.java)
    }
}
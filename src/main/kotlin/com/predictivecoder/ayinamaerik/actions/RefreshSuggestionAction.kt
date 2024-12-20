package com.predictivecoder.ayinamaerik.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.predictivecoder.ayinamaerik.services.SuggestionService
import com.intellij.openapi.editor.EditorFactory

class RefreshSuggestionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val document = editor.document

        // Effacer d'abord les suggestions actuelles
        SuggestionService.getInstance(project).clearSuggestions(editor)

        // Forcer une nouvelle suggestion
        val offset = editor.caretModel.offset
        val lineNumber = document.getLineNumber(offset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val currentLine = document.getText(
            com.intellij.openapi.util.TextRange(
                lineStartOffset,
                offset
            )
        )

        // Déclencher une nouvelle suggestion
        SuggestionService.getInstance(project).showSuggestions(
            editor,
            document.text,
            currentLine,
            editor.virtualFile?.fileType?.name ?: "unknown"
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }
}
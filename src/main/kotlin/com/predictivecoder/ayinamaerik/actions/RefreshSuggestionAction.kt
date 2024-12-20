package com.predictivecoder.ayinamaerik.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.predictivecoder.ayinamaerik.services.SuggestionService
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.TextRange

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
            TextRange(
                lineStartOffset,
                offset
            )
        )

        // Récupérer le type de fichier
        val fileType = editor.virtualFile?.fileType?.name ?: "unknown"

        // Construire le contexte
        val context = buildContext(document, lineNumber)

        // Déclencher une nouvelle suggestion
        SuggestionService.getInstance(project).showSuggestions(
            editor,
            context,
            currentLine,
            fileType,
            false
        )
    }

    private fun buildContext(document: com.intellij.openapi.editor.Document, currentLineNumber: Int): String {
        return buildString {
            // Récupérer quelques lignes avant la position actuelle
            val startLine = maxOf(0, currentLineNumber - 10)
            for (i in startLine until currentLineNumber) {
                val lineStart = document.getLineStartOffset(i)
                val lineEnd = document.getLineEndOffset(i)
                appendLine(document.getText(TextRange(lineStart, lineEnd)))
            }

            // Ajouter la ligne courante
            val currentLineStart = document.getLineStartOffset(currentLineNumber)
            val currentLineEnd = document.getLineEndOffset(currentLineNumber)
            append(document.getText(TextRange(currentLineStart, currentLineEnd)))

            // Récupérer quelques lignes après la position actuelle
            val endLine = minOf(document.lineCount - 1, currentLineNumber + 5)
            for (i in (currentLineNumber + 1)..endLine) {
                val lineStart = document.getLineStartOffset(i)
                val lineEnd = document.getLineEndOffset(i)
                appendLine(document.getText(TextRange(lineStart, lineEnd)))
            }
        }
    }

    override fun update(e: AnActionEvent) {
        // Activer l'action uniquement si un éditeur est disponible
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    companion object {
        private const val CONTEXT_LINES_BEFORE = 10
        private const val CONTEXT_LINES_AFTER = 5
    }
}
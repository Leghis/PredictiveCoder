package com.predictivecoder.ayinamaerik.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.ui.JBColor
import java.awt.Graphics
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean

class InlaySuggestionManager(private val editor: Editor) {
    private val logger = Logger.getInstance(InlaySuggestionManager::class.java)
    private var currentInlay: Inlay<SuggestionRenderer>? = null
    private var currentSuggestion: String = ""
    private var remainingSuggestions: List<String> = emptyList()
    private val isUpdating = AtomicBoolean(false)

    fun showSuggestion(offset: Int, suggestion: String, currentLine: String) {
        if (isUpdating.get()) return

        try {
            isUpdating.set(true)
            clearCurrentSuggestion()

            val cleanedSuggestion = if (suggestion.startsWith(currentLine)) {
                suggestion.substring(currentLine.length)
            } else {
                suggestion
            }

            if (cleanedSuggestion.isNotBlank()) {
                currentSuggestion = cleanedSuggestion
                remainingSuggestions = cleanedSuggestion.split("\n").filter { it.isNotBlank() }
                if (remainingSuggestions.isNotEmpty()) {
                    showCurrentSuggestion(offset)
                }
            }
        } finally {
            isUpdating.set(false)
        }
    }

    private fun showCurrentSuggestion(offset: Int) {
        if (remainingSuggestions.isEmpty()) return

        try {
            currentInlay = editor.inlayModel.addInlineElement(
                offset,
                true,
                SuggestionRenderer(remainingSuggestions.joinToString("\n"), editor)
            )
        } catch (e: Exception) {
            logger.error("Error showing suggestion", e)
        }
    }

    fun acceptCurrentLine(): Boolean {
        if (isUpdating.get() || remainingSuggestions.isEmpty()) return false

        try {
            isUpdating.set(true)
            val currentLine = remainingSuggestions.first()

            WriteCommandAction.runWriteCommandAction(editor.project) {
                try {
                    val offset = editor.caretModel.offset
                    editor.document.insertString(offset, currentLine)

                    // Ajouter une nouvelle ligne si ce n'est pas la dernière suggestion
                    if (remainingSuggestions.size > 1) {
                        editor.document.insertString(offset + currentLine.length, "\n")
                    }
                } catch (e: Exception) {
                    logger.error("Error inserting suggestion", e)
                }
            }

            // Mettre à jour les suggestions restantes
            remainingSuggestions = remainingSuggestions.drop(1)

            // Si il reste des suggestions, les afficher
            if (remainingSuggestions.isNotEmpty()) {
                clearCurrentSuggestion()
                showCurrentSuggestion(editor.caretModel.offset)
                return true
            } else {
                clearCurrentSuggestion()
                return false
            }
        } finally {
            isUpdating.set(false)
        }
    }

    fun acceptCurrentSuggestion() {
        if (isUpdating.get() || currentSuggestion.isBlank()) return

        try {
            isUpdating.set(true)
            WriteCommandAction.runWriteCommandAction(editor.project) {
                try {
                    editor.document.insertString(editor.caretModel.offset, currentSuggestion)
                } catch (e: Exception) {
                    logger.error("Error accepting full suggestion", e)
                }
            }
        } finally {
            clearCurrentSuggestion()
            isUpdating.set(false)
        }
    }

    fun clearCurrentSuggestion() {
        try {
            currentInlay?.dispose()
            currentInlay = null
            currentSuggestion = ""
            remainingSuggestions = emptyList()
        } catch (e: Exception) {
            logger.error("Error clearing suggestion", e)
        }
    }

    private class SuggestionRenderer(
        private val suggestion: String,
        private val editor: Editor
    ) : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val metrics = editor.component.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))
            return metrics.stringWidth(suggestion)
        }

        override fun paint(
            inlay: Inlay<*>,
            g: Graphics,
            targetRegion: Rectangle,
            textAttributes: TextAttributes
        ) {
            g.color = JBColor(
                JBColor.GRAY.brighter(),
                JBColor.GRAY.darker()
            )
            g.font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
            g.drawString(suggestion, targetRegion.x, targetRegion.y + targetRegion.height - 2)
        }
    }
}
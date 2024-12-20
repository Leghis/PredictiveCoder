package com.predictivecoder.ayinamaerik.listeners

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.predictivecoder.ayinamaerik.services.SettingsService
import com.predictivecoder.ayinamaerik.services.SuggestionService
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

class EditorListener(private val project: Project) : DocumentListener, CaretListener, CoroutineScope {
    private val logger = Logger.getInstance(EditorListener::class.java)
    private val settings = SettingsService.getInstance()
    private val suggestionService = SuggestionService.getInstance(project)
    private val job = SupervisorJob()
    private val contextCache = ConcurrentHashMap<Int, String>()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job + CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) {
                logger.error("Coroutine exception", throwable)
            }
        }

    private val isProcessing = AtomicBoolean(false)
    private var currentJob: Job? = null
    private var lastProcessedText = ""
    private var lastProcessedLine = -1
    private val debounceDelay = 100L // Réduit pour plus de réactivité

    override fun documentChanged(event: DocumentEvent) {
        if (!settings.isEnabled || !settings.autoSuggestEnabled) return

        val newText = event.newFragment.toString()
        val oldText = event.oldFragment.toString()
        val isNewLine = newText == "\n" || newText.endsWith("\n")
        val isBackspace = newText.isEmpty() && oldText == "\n"

        if (isNewLine || isBackspace) {
            // Traitement immédiat pour les sauts de ligne
            processChange(event.document, true)
        } else {
            processChange(event.document, false)
        }
    }

    override fun caretPositionChanged(event: CaretEvent) {
        if (!settings.isEnabled || !settings.autoSuggestEnabled) return

        ApplicationManager.getApplication().runReadAction {
            val document = event.editor.document
            val caret = event.caret ?: return@runReadAction
            val newLine = document.getLineNumber(caret.offset)

            if (newLine != lastProcessedLine) {
                lastProcessedLine = newLine
                processChange(document, true)
            }
        }
    }

    private fun processChange(document: com.intellij.openapi.editor.Document, isNewLine: Boolean) {
        currentJob?.cancel()

        currentJob = launch {
            try {
                // Délai réduit pour les nouvelles lignes
                delay(if (isNewLine) 50L else debounceDelay)

                val editorAndOffset = withContext(Dispatchers.Default) {
                    var result: Triple<com.intellij.openapi.editor.Editor, Int, String>? = null

                    ApplicationManager.getApplication().invokeAndWait({
                        val editor = EditorFactory.getInstance()
                            .getEditors(document, project)
                            .firstOrNull()

                        if (editor != null) {
                            val offset = editor.caretModel.offset
                            val lineNumber = document.getLineNumber(offset)
                            val lineStart = document.getLineStartOffset(lineNumber)
                            val lineEnd = document.getLineEndOffset(lineNumber)

                            // Vérifier si le curseur est à la fin de la ligne ou si c'est une nouvelle ligne
                            if (offset == lineEnd || isNewLine) {
                                val currentLine = document.getText(TextRange(lineStart, offset))
                                result = Triple(editor, offset, currentLine)
                            }
                        }
                    }, ModalityState.defaultModalityState())

                    result
                } ?: return@launch

                val (editor, offset, currentLine) = editorAndOffset

                ApplicationManager.getApplication().runReadAction {
                    val fileType = editor.virtualFile?.fileType
                    if (fileType != null) {
                        val context = buildSmartContext(document, offset, currentLine, fileType, isNewLine)

                        if (context.isNotEmpty()) {
                            ApplicationManager.getApplication().invokeLater({
                                try {
                                    suggestionService.clearSuggestions(editor)
                                    suggestionService.showSuggestions(
                                        editor,
                                        context,
                                        currentLine,
                                        fileType.name,
                                        isNewLine
                                    )
                                } catch (e: Exception) {
                                    logger.error("Error showing suggestions", e)
                                }
                            }, ModalityState.any())
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Ignorer les annulations normales
            } catch (e: Exception) {
                logger.error("Error processing change", e)
            }
        }
    }

    private fun buildSmartContext(
        document: com.intellij.openapi.editor.Document,
        offset: Int,
        currentLine: String,
        fileType: FileType,
        isNewLine: Boolean
    ): String {
        return ApplicationManager.getApplication().runReadAction<String> {
            buildString {
                val lineNumber = document.getLineNumber(offset)

                // Ajouter des métadonnées utiles
                appendLine("// FileType: ${fileType.name}")
                appendLine("// Current line number: $lineNumber")

                // Analyser le bloc actuel
                val blockInfo = analyzeCurrentBlock(document, lineNumber)
                appendLine("// Block type: ${blockInfo.first}")
                appendLine("// Indentation level: ${blockInfo.second}")

                // Contexte précédent enrichi
                val startLine = maxOf(0, lineNumber - if (isNewLine) 15 else 8)
                var previousIndentation = -1

                for (i in startLine until lineNumber) {
                    val lineStart = document.getLineStartOffset(i)
                    val lineEnd = document.getLineEndOffset(i)
                    val lineText = document.getText(TextRange(lineStart, lineEnd))

                    // Calculer l'indentation
                    val currentIndentation = lineText.takeWhile { it.isWhitespace() }.length

                    // Ajouter des marqueurs de structure
                    if (currentIndentation != previousIndentation) {
                        appendLine("// Indentation change: $currentIndentation")
                        previousIndentation = currentIndentation
                    }

                    appendLine(lineText)
                }

                // Ligne courante avec son contexte
                append(currentLine)

                // Contexte suivant (réduit pour les nouvelles lignes)
                val endLine = minOf(document.lineCount - 1, lineNumber + if (isNewLine) 1 else 3)
                for (i in lineNumber + 1..endLine) {
                    val lineStart = document.getLineStartOffset(i)
                    val lineEnd = document.getLineEndOffset(i)
                    appendLine(document.getText(TextRange(lineStart, lineEnd)))
                }
            }
        }
    }

    private fun analyzeCurrentBlock(document: com.intellij.openapi.editor.Document, lineNumber: Int): Pair<String, Int> {
        var blockType = "unknown"
        var indentationLevel = 0

        try {
            val currentLine = document.getText(TextRange(
                document.getLineStartOffset(lineNumber),
                document.getLineEndOffset(lineNumber)
            ))

            indentationLevel = currentLine.takeWhile { it.isWhitespace() }.length

            // Détecter le type de bloc
            blockType = when {
                currentLine.trimStart().startsWith("if") -> "if-block"
                currentLine.trimStart().startsWith("for") -> "for-loop"
                currentLine.trimStart().startsWith("while") -> "while-loop"
                currentLine.trimStart().startsWith("class") -> "class-definition"
                currentLine.trimStart().startsWith("fun") -> "function-definition"
                currentLine.trimStart().startsWith("val") -> "value-declaration"
                currentLine.trimStart().startsWith("var") -> "variable-declaration"
                else -> "code-block"
            }
        } catch (e: Exception) {
            logger.error("Error analyzing block", e)
        }

        return Pair(blockType, indentationLevel)
    }

    fun dispose() {
        currentJob?.cancel()
        job.cancel()
        contextCache.clear()
    }
}
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
    private val debounceDelay = 100L

    override fun documentChanged(event: DocumentEvent) {
        if (!settings.isEnabled || !settings.autoSuggestEnabled) return

        val newText = event.newFragment.toString()
        val oldText = event.oldFragment.toString()
        val isNewLine = newText == "\n" || newText.endsWith("\n")
        val isBackspace = newText.isEmpty() && oldText == "\n"

        if (isNewLine || isBackspace) {
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
                val totalLines = document.lineCount

                // Métadonnées de base
                appendLine("// FileType: ${fileType.name}")
                appendLine("// Current line number: $lineNumber")
                appendLine("// Total lines: $totalLines")

                // Récupérer le contexte complet du fichier
                val fileContent = document.text
                val currentFilePackage = fileContent.substringBefore("\n\n")
                if (currentFilePackage.startsWith("package ")) {
                    appendLine(currentFilePackage)
                    appendLine()
                }

                // Récupérer les imports
                val imports = fileContent.lines()
                    .filter { it.trim().startsWith("import ") }
                if (imports.isNotEmpty()) {
                    imports.forEach { appendLine(it) }
                    appendLine()
                }

                // Analyser le bloc actuel
                val blockInfo = analyzeCurrentBlock(document, lineNumber)
                val (blockStart, blockEnd) = findBlockBoundaries(document, lineNumber, blockInfo.second)

                // Contexte précédent
                val contextStart = maxOf(0, blockStart - 10)
                for (i in contextStart until lineNumber) {
                    val lineStart = document.getLineStartOffset(i)
                    val lineEnd = document.getLineEndOffset(i)
                    val lineText = document.getText(TextRange(lineStart, lineEnd))

                    if (i == blockStart) {
                        appendLine("// Start of current block")
                    }
                    appendLine(lineText)
                }

                // Ligne courante
                append(currentLine)

                // Contexte suivant
                val contextEnd = minOf(totalLines - 1, blockEnd + 5)
                for (i in (lineNumber + 1)..contextEnd) {
                    val lineStart = document.getLineStartOffset(i)
                    val lineEnd = document.getLineEndOffset(i)
                    val lineText = document.getText(TextRange(lineStart, lineEnd))
                    appendLine(lineText)
                    if (i == blockEnd) {
                        appendLine("// End of current block")
                    }
                }
            }
        }
    }

    private fun findBlockBoundaries(
        document: com.intellij.openapi.editor.Document,
        currentLine: Int,
        currentIndentation: Int
    ): Pair<Int, Int> {
        var start = currentLine
        var end = currentLine

        // Rechercher le début du bloc
        for (i in currentLine downTo 0) {
            val lineStart = document.getLineStartOffset(i)
            val lineEnd = document.getLineEndOffset(i)
            val lineText = document.getText(TextRange(lineStart, lineEnd))
            val indentation = lineText.takeWhile { it.isWhitespace() }.length

            if (indentation < currentIndentation) {
                start = i
                break
            }
        }

        // Rechercher la fin du bloc
        for (i in currentLine until document.lineCount) {
            val lineStart = document.getLineStartOffset(i)
            val lineEnd = document.getLineEndOffset(i)
            val lineText = document.getText(TextRange(lineStart, lineEnd))
            val indentation = lineText.takeWhile { it.isWhitespace() }.length

            if (indentation < currentIndentation) {
                end = i
                break
            }
        }

        return Pair(start, end)
    }

    private fun analyzeCurrentBlock(document: com.intellij.openapi.editor.Document, lineNumber: Int): Pair<String, Int> {
        var blockType = "unknown"
        var indentationLevel = 0

        try {
            val lineStart = document.getLineStartOffset(lineNumber)
            val lineEnd = document.getLineEndOffset(lineNumber)
            val currentLine = document.getText(TextRange(lineStart, lineEnd))

            indentationLevel = currentLine.takeWhile { it.isWhitespace() }.length

            blockType = when {
                currentLine.contains("class") -> "class-definition"
                currentLine.contains("fun") -> "function-definition"
                currentLine.contains("if") -> "if-block"
                currentLine.contains("for") -> "for-loop"
                currentLine.contains("while") -> "while-loop"
                currentLine.contains("when") -> "when-expression"
                currentLine.contains("try") -> "try-block"
                currentLine.contains("catch") -> "catch-block"
                currentLine.contains("do") -> "do-while-loop"
                currentLine.contains("else") -> "else-block"
                else -> "code-block"
            }

            if (isInString(currentLine)) blockType = "string-content"
            if (isInComment(currentLine)) blockType = "comment"

        } catch (e: Exception) {
            logger.error("Error analyzing block", e)
        }

        return Pair(blockType, indentationLevel)
    }

    private fun isInString(line: String): Boolean {
        var inString = false
        var escaped = false
        for (char in line) {
            when {
                char == '\\' -> escaped = !escaped
                char == '"' && !escaped -> inString = !inString
                else -> escaped = false
            }
        }
        return inString
    }

    private fun isInComment(line: String): Boolean {
        val trimmedLine = line.trim()
        return trimmedLine.startsWith("//") || trimmedLine.startsWith("/*") || trimmedLine.endsWith("*/")
    }

    fun dispose() {
        currentJob?.cancel()
        job.cancel()
        contextCache.clear()
    }

    companion object {
        private const val CONTEXT_LINES_BEFORE = 10
        private const val CONTEXT_LINES_AFTER = 5
    }
}
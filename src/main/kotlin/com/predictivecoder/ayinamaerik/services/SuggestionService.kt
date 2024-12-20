package com.predictivecoder.ayinamaerik.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.predictivecoder.ayinamaerik.ui.InlaySuggestionManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class SuggestionService(private val project: Project) {
    private val logger = Logger.getInstance(SuggestionService::class.java)
    private val inlayManagers = ConcurrentHashMap<Editor, InlaySuggestionManager>()
    private val openAIService = ApplicationManager.getApplication().getService(OpenAIService::class.java)
    private val settings = SettingsService.getInstance()
    private val isProcessing = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun showSuggestions(
        editor: Editor,
        context: String,
        currentLine: String,
        fileType: String,
        isNewLine: Boolean = false
    ) {
        if (!settings.isEnabled || isProcessing.get()) return

        scope.launch {
            if (isProcessing.compareAndSet(false, true)) {
                try {
                    val suggestions = withContext(Dispatchers.IO) {
                        // Ajuster les paramètres pour les nouvelles lignes
                        if (isNewLine) {
                            openAIService.getSuggestions(
                                context,
                                currentLine,
                                fileType,
                            )
                        } else {
                            openAIService.getSuggestions(context, currentLine, fileType)
                        }
                    }

                    if (suggestions.isNotEmpty()) {
                        withContext(Dispatchers.Default) {
                            ApplicationManager.getApplication().invokeLater({
                                try {
                                    getOrCreateInlayManager(editor).showSuggestion(
                                        editor.caretModel.offset,
                                        suggestions.first().trimStart('.'),
                                        currentLine
                                    )
                                    logger.debug("Suggestion displayed successfully")
                                } catch (e: Exception) {
                                    logger.error("Error displaying suggestion", e)
                                }
                            }, ModalityState.defaultModalityState())
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing suggestions", e)
                } finally {
                    isProcessing.set(false)
                }
            }
        }
    }

    private fun getOrCreateInlayManager(editor: Editor): InlaySuggestionManager {
        return inlayManagers.computeIfAbsent(editor) {
            InlaySuggestionManager(it)
        }
    }

    fun acceptFullSuggestion(editor: Editor) {
        if (isProcessing.get()) return

        ApplicationManager.getApplication().runWriteAction {
            try {
                inlayManagers[editor]?.let { manager ->
                    manager.acceptCurrentSuggestion()
                    IndentationService.getInstance(project).autoIndent(editor)
                }
            } catch (e: Exception) {
                logger.error("Error accepting full suggestion", e)
            }
        }
    }

    fun acceptSingleLine(editor: Editor) {
        if (isProcessing.get()) return

        ApplicationManager.getApplication().runWriteAction {
            try {
                inlayManagers[editor]?.let { manager ->
                    val hasMoreLines = manager.acceptCurrentLine()
                    IndentationService.getInstance(project).autoIndent(editor)
                }
            } catch (e: Exception) {
                logger.error("Error accepting single line", e)
            }
        }
    }

    fun clearSuggestions(editor: Editor) {
        ApplicationManager.getApplication().invokeLater({
            try {
                inlayManagers[editor]?.clearCurrentSuggestion()
            } catch (e: Exception) {
                logger.error("Error clearing suggestions", e)
            }
        }, ModalityState.defaultModalityState())
    }

    fun dispose() {
        scope.cancel()
        inlayManagers.forEach { (_, manager) ->
            try {
                ApplicationManager.getApplication().invokeLater({
                    manager.clearCurrentSuggestion()
                }, ModalityState.defaultModalityState())
            } catch (e: Exception) {
                logger.error("Error disposing manager", e)
            }
        }
        inlayManagers.clear()
    }

    companion object {
        fun getInstance(project: Project): SuggestionService =
            project.getService(SuggestionService::class.java)
    }
}
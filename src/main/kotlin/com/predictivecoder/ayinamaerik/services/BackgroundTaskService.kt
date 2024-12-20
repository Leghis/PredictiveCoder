package com.predictivecoder.ayinamaerik.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

@Service(Service.Level.PROJECT)
class BackgroundTaskService(private val project: Project) : CoroutineScope {
    private val logger = Logger.getInstance(BackgroundTaskService::class.java)
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job +
            CoroutineExceptionHandler { _, throwable ->
                logger.error("Coroutine exception", throwable)
            }

    private val taskChannel = Channel<SuggestionTask>(Channel.BUFFERED)
    private val isProcessing = AtomicBoolean(false)
    private val _currentSuggestion = MutableStateFlow<String?>(null)
    val currentSuggestion = _currentSuggestion.asStateFlow()
    private val openAIService = project.getService(OpenAIService::class.java)
    private var lastTask: SuggestionTask? = null

    init {
        launch {
            processTaskQueue()
        }
    }

    data class SuggestionTask(
        val context: String,
        val currentLine: String,
        val fileType: String,
        val callback: suspend (String) -> Unit
    )

    private suspend fun processTaskQueue() {
        for (task in taskChannel) {
            if (lastTask?.currentLine == task.currentLine) {
                logger.debug("Skipping duplicate task")
                continue
            }
            lastTask = task

            if (!isProcessing.compareAndSet(false, true)) {
                logger.debug("Task processing already in progress")
                continue
            }

            try {
                withContext(Dispatchers.IO) {
                    try {
                        logger.debug("Processing suggestion task for ${task.fileType}")
                        val suggestions = openAIService.getSuggestions(
                            task.context,
                            task.currentLine,
                            task.fileType
                        )

                        val suggestion = suggestions.firstOrNull()
                        if (suggestion?.isNotBlank() == true) {
                            logger.debug("Received valid suggestion")
                            _currentSuggestion.value = suggestion
                            task.callback(suggestion)
                        } else {
                            logger.warn("Empty suggestion received")
                        }
                    } catch (e: CancellationException) {
                        logger.debug("Task cancelled")
                        throw e
                    } catch (e: Exception) {
                        logger.error("Error getting suggestions", e)
                    }
                }
            } finally {
                isProcessing.set(false)
            }
        }
    }

    fun queueSuggestionTask(
        context: String,
        currentLine: String,
        fileType: String,
        callback: suspend (String) -> Unit
    ) {
        launch {
            try {
                logger.debug("Queueing suggestion task for $fileType")
                taskChannel.send(SuggestionTask(context, currentLine, fileType, callback))
            } catch (e: Exception) {
                logger.error("Error queueing suggestion task", e)
            }
        }
    }

    fun clearCurrentSuggestion() {
        _currentSuggestion.value = null
        lastTask = null
        logger.debug("Cleared current suggestion")
    }

    fun dispose() {
        launch {
            try {
                taskChannel.close()
                clearCurrentSuggestion()
                logger.debug("Background task service disposed")
            } finally {
                job.cancel()
            }
        }
    }

    companion object {
        fun getInstance(project: Project): BackgroundTaskService =
            project.getService(BackgroundTaskService::class.java)
    }
}
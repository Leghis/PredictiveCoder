package com.predictivecoder.ayinamaerik.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.diagnostic.Logger

@Service(Service.Level.PROJECT)
class IndentationService(private val project: Project) {
    private val logger = Logger.getInstance(IndentationService::class.java)

    fun autoIndent(editor: Editor) {
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    val document = editor.document
                    val psiFile = com.intellij.psi.PsiDocumentManager
                        .getInstance(project)
                        .getPsiFile(document)

                    if (psiFile != null) {
                        CodeStyleManager.getInstance(project).reformat(psiFile)
                    }
                } catch (e: Exception) {
                    logger.debug("Auto-indent skipped", e)
                }
            }
        }
    }

    fun indentSelection(editor: Editor) {
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    val selectionModel = editor.selectionModel
                    if (selectionModel.hasSelection()) {
                        val document = editor.document
                        val psiFile = com.intellij.psi.PsiDocumentManager
                            .getInstance(project)
                            .getPsiFile(document)

                        if (psiFile != null) {
                            val startOffset = selectionModel.selectionStart
                            val endOffset = selectionModel.selectionEnd
                            CodeStyleManager.getInstance(project)
                                .reformatRange(psiFile, startOffset, endOffset)
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error indenting selection", e)
                }
            }
        }
    }

    companion object {
        fun getInstance(project: Project): IndentationService =
            project.getService(IndentationService::class.java)
    }
}
package com.myai.assistant

import android.content.Context
import android.os.Environment
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

class BrainRouter(private val context: Context) {

    private val memoryFile = File(context.filesDir, "chat_memory.txt")
    private val factsFile = File(context.filesDir, "user_facts.txt")
    private var llmInference: LlmInference? = null
    private var initError: String = ""

    private fun initModel() {
        val extAppDir = context.getExternalFilesDir(null)
        val extAppTask = if (extAppDir != null) File(extAppDir, "gemma.task") else null
        val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dlTask = File(dlDir, "gemma.task")
        val localTask = File(context.filesDir, "gemma.task")

        val sourceTask = when {
            extAppTask != null && extAppTask.exists() && extAppTask.length() > 0 -> extAppTask
            dlTask.exists() && dlTask.length() > 0 -> dlTask
            else -> null
        }

        if (sourceTask != null && (!localTask.exists() || localTask.length() != sourceTask.length())) {
            try {
                sourceTask.copyTo(localTask, overwrite = true)
            } catch (e: Exception) {
                initError = "Ошибка копирования: ${e.message}"
            }
        }

        val targetPath = when {
            localTask.exists() && localTask.length() > 0 -> localTask.absolutePath
            extAppTask != null && extAppTask.exists() -> extAppTask.absolutePath
            dlTask.exists() -> dlTask.absolutePath
            else -> null
        }

        if (targetPath != null) {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(targetPath)
                    .setMaxTokens(2048)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
            } catch (e: Exception) {
                e.printStackTrace()
                initError = e.message ?: e.toString()
            }
        }
    }

    fun loadMemory(): String {
        return if (memoryFile.exists()) memoryFile.readText() else ""
    }

    fun saveToMemory(role: String, text: String) {
        memoryFile.appendText("$role: $text\n")
    }

    fun askAI(userMessage: String, isOnlineMode: Boolean): String {
        saveToMemory("Вы", userMessage)
        if (llmInference == null) {
            initModel()
        }
        val engine = llmInference ?: return "Модель не готова. Положи gemma.task в Download или Android/data/com.myai.assistant/files/. (Детали: $initError)"

        val eternalFacts = if (factsFile.exists()) factsFile.readText() else ""
        val allHistory = loadMemory()
        val recentChat = allHistory.takeLast(1000)

        val prompt = """
            <start_of_turn>user
            Ты мой личный офлайн-ассистент на смартфоне. Отвечай кратко и по делу на русском языке.
            
            Вот что ты помнишь обо мне из прошлых чатов:
            $eternalFacts
            
            Недавняя переписка:
            $recentChat
            
            Новый вопрос: $userMessage<end_of_turn>
            <start_of_turn>model
        """.trimIndent()

        val aiReply = try {
            engine.generateResponse(prompt)
        } catch (e: Exception) {
            "Ошибка генерации: ${e.message}"
        }

        saveToMemory("ИИ", aiReply)
        return aiReply
    }
}

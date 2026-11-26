package com.myai.assistant

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

class BrainRouter(private val context: Context) {

    private val memoryFile = File(context.filesDir, "chat_memory.txt")
    private val factsFile = File(context.filesDir, "user_facts.txt")
    private var llmInference: LlmInference? = null
    private var initError: String = ""

    init {
        initModel()
    }

    private fun initModel() {
        val taskFile = File("/sdcard/Download/gemma.task")
        val modelFile = File("/sdcard/Download/model.gguf")
        val targetPath = when {
            taskFile.exists() -> taskFile.absolutePath
            modelFile.exists() -> modelFile.absolutePath
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
        val lower = text.lowercase()
        if (role == "Пользователь" && (lower.contains("меня зовут") || lower.contains("запомни") || lower.contains("мой телефон"))) {
            factsFile.appendText("- $text\n")
        }
    }

    suspend fun askAI(userMessage: String, isOnlineMode: Boolean = false): String {
        saveToMemory("Пользователь", userMessage)

        if (userMessage.lowercase().contains("включи камеру")) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val reply = "Открываю камеру."
            saveToMemory("ИИ", reply)
            return reply
        }

        val engine = llmInference
        if (engine == null) {
            val err = "Модель не найдена. Положи файл gemma.task в папку Download и дай приложению разрешение на доступ к файлам. (Файл: ${File("/sdcard/Download/gemma.task").exists()}, Ошибка: $initError)"
            saveToMemory("ИИ", err)
            return err
        }

        val eternalFacts = if (factsFile.exists()) factsFile.readText() else "Пока нет сохраненных фактов."
        val recentChat = loadMemory().takeLast(4000)

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

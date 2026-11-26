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
    var statusMessage: String = "Ожидание..."

    fun isModelLoaded(): Boolean = llmInference != null

    fun initModel(): String {
        if (llmInference != null) return "Модель уже готова!"
        val taskFile = File("/sdcard/Download/gemma.task")
        val modelFile = File("/sdcard/Download/model.gguf")
        val targetPath = when {
            taskFile.exists() && taskFile.canRead() -> taskFile.absolutePath
            modelFile.exists() && modelFile.canRead() -> modelFile.absolutePath
            else -> null
        }

        if (targetPath == null) {
            statusMessage = "Файл gemma.task не найден в Download или нет доступа!"
            return statusMessage
        }

        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(targetPath)
                .setMaxTokens(2048)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            statusMessage = "Офлайн-мозг Gemma 4 активен!"
            statusMessage
        } catch (e: Throwable) {
            statusMessage = "Ошибка: ${e.message}"
            statusMessage
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

    suspend fun askAI(
        userMessage: String,
        onProgress: suspend (String) -> Unit
    ): String {
        saveToMemory("Пользователь", userMessage)

        if (userMessage.lowercase().contains("включи камеру")) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val reply = "Открываю камеру."
            saveToMemory("vang quer", reply)
            return reply
        }

        if (llmInference == null) {
            val res = initModel()
            if (llmInference == null) {
                val err = "Не удалось запустить мозг ($res). Проверь 'Доступ ко всем файлам' в настройках!"
                saveToMemory("vang quer", err)
                return err
            }
        }

        onProgress("100% — Думаю над ответом...")

        val eternalFacts = if (factsFile.exists()) factsFile.readText() else "Пока нет сохраненных фактов."
        val recentChat = loadMemory().takeLast(4000)

        val prompt = """
            <start_of_turn>user
            Ты мой личный офлайн-ассистент vang quer на смартфоне. Отвечай кратко, тепло и по делу на русском языке.
            Вот что ты помнишь обо мне из прошлых чатов:
            $eternalFacts
            
            Недавняя переписка:
            $recentChat
            
            Новый вопрос: $userMessage<end_of_turn>
            <start_of_turn>model
        """.trimIndent()

        val aiReply = try {
            llmInference!!.generateResponse(prompt)
        } catch (e: Throwable) {
            "Ошибка генерации: ${e.message}"
        }

        saveToMemory("vang quer", aiReply)
        return aiReply
    }
}

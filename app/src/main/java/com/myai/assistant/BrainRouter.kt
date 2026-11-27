package com.myai.assistant

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import java.io.File

class BrainRouter(private val context: Context) {

    private val memoryFile = File(context.filesDir, "chat_memory.txt")
    private val factsFile = File(context.filesDir, "user_facts.txt")
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    var statusMessage: String = "Ожидание..."

    fun isModelLoaded(): Boolean = conversation != null

    fun initModel(): String {
        if (conversation != null) return "Модель уже готова!"

        val oldInternalFile = File(context.filesDir, "gemma.litertlm")
        if (oldInternalFile.exists()) {
            oldInternalFile.delete()
        }

        val downloadFile = File("/sdcard/Download/gemma.litertlm")
        if (!downloadFile.exists() || !downloadFile.canRead()) {
            statusMessage = "Нет доступа к /sdcard/Download/gemma.litertlm! Выдай разрешение 'Доступ ко всем файлам' в настройках."
            return statusMessage
        }

        return try {
            val config = EngineConfig(
                modelPath = downloadFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath,
                maxNumTokens = 1024
            )
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            conversation = newEngine.createConversation(ConversationConfig())
            statusMessage = "Офлайн-мозг Ван Сюер активен!"
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

        if (conversation == null) {
            onProgress("Загружаю мозг Ван Сюер в память (подождите ~20 сек)...")
            val res = initModel()
            if (conversation == null) {
                val err = "Не удалось запустить мозг ($res)"
                saveToMemory("vang quer", err)
                return err
            }
        }

        onProgress("100% — Думаю над ответом...")

        val eternalFacts = if (factsFile.exists()) factsFile.readText() else "Пока нет сохраненных фактов."
        val prompt = """
            Ты мой личный офлайн-ассистент Ван Сюер (vang quer) на смартфоне. Отвечай кратко, тепло и по делу на русском языке.
            Вот что ты помнишь обо мне:
            $eternalFacts

            Вопрос: $userMessage
        """.trimIndent()

        val aiReply = try {
            val response = conversation!!.sendMessage(Message.of(prompt))
            response.toString()
        } catch (e: Throwable) {
            "Ошибка генерации: ${e.message}"
        }

        saveToMemory("vang quer", aiReply)
        return aiReply
    }
}

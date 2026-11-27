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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            statusMessage = "Нет доступа к /sdcard/Download/gemma.litertlm!"
            return statusMessage
        }

        return try {
            val config = EngineConfig(
                modelPath = downloadFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath,
                maxNumTokens = 2048
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
        if (text.startsWith("Ошибка генерации:") || text.startsWith("Не удалось запустить")) return
        val time = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
        memoryFile.appendText("[$time] $role: $text\n")

        val lower = text.lowercase()
        val triggers = listOf("меня зовут", "запомни", "мой телефон", "я люблю", "я работаю", "моя привычка", "не люблю")
        if (role == "Пользователь" && triggers.any { lower.contains(it) }) {
            factsFile.appendText("- $text\n")
        }
    }

    private fun searchOldChats(query: String): String {
        if (!memoryFile.exists()) return ""
        val lines = memoryFile.readLines().filter {
            !it.contains("Ошибка генерации:") && !it.contains("Не удалось запустить")
        }
        if (lines.isEmpty()) return ""

        val lower = query.lowercase()
        val recallTriggers = listOf("вспомни", "спомни", "помнишь", "раньше", "вчера", "стары", "чат", "говорил", "писал")
        val isRecall = recallTriggers.any { lower.contains(it) }

        val words = lower.split(Regex("[^а-яёa-z0-9]+")).filter { it.length > 3 }
        val matched = if (words.isNotEmpty()) {
            lines.filter { line ->
                val l = line.lowercase()
                words.any { w -> l.contains(w) }
            }.takeLast(6)
        } else emptyList()

        return when {
            matched.isNotEmpty() -> "Найденные записи из старых чатов:\n" + matched.joinToString("\n")
            isRecall -> "Последние записи из архива чатов:\n" + lines.takeLast(6).joinToString("\n")
            else -> ""
        }
    }

    suspend fun askAI(
        userMessage: String,
        onProgress: suspend (String) -> Unit
    ): String {
        if (userMessage.lowercase().contains("включи камеру")) {
            saveToMemory("Пользователь", userMessage)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val reply = "Открываю камеру."
            saveToMemory("vang quer", reply)
            return reply
        }

        if (conversation == null) {
            onProgress("Загружаю мозг Ван Сюер в память (~15 сек)...")
            val res = initModel()
            if (conversation == null) {
                return "Не удалось запустить мозг ($res)"
            }
        }

        onProgress("100% — Думаю над ответом...")

        val eternalFacts = if (factsFile.exists()) factsFile.readText().lines().takeLast(15).joinToString("\n") else "Пока нет фактов."
        val archiveContext = searchOldChats(userMessage)

        saveToMemory("Пользователь", userMessage)

        val prompt = buildString {
            appendLine("Ты мой личный офлайн-ассистент Ван Сюер (vang quer) на смартфоне. Отвечай тепло, кратко и по делу на русском языке.")
            appendLine("Постоянные факты о пользователе:\n$eternalFacts")
            if (archiveContext.isNotEmpty()) {
                appendLine("\n$archiveContext")
            }
            appendLine("\nТекущий вопрос пользователя: $userMessage")
        }

        val aiReply = try {
            conversation!!.sendMessage(Message.of(prompt)).toString()
        } catch (e: Throwable) {
            try {
                conversation = engine?.createConversation(ConversationConfig())
                conversation!!.sendMessage(Message.of(prompt)).toString()
            } catch (e2: Throwable) {
                "Ошибка генерации: ${e2.message}"
            }
        }

        saveToMemory("vang quer", aiReply)
        return aiReply
    }
}

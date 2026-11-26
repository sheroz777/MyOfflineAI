package com.myai.assistant

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var brain: BrainRouter
    private lateinit var chatHistoryView: TextView
    private lateinit var chatScroll: ScrollView
    private lateinit var statusText: TextView
    private var baseHistoryText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        brain = BrainRouter(this)
        chatHistoryView = findViewById(R.id.chatHistoryView)
        chatScroll = findViewById(R.id.chatScroll)
        statusText = findViewById(R.id.statusText)
        val userInput = findViewById<EditText>(R.id.userInput)
        val sendButton = findViewById<Button>(R.id.sendButton)

        baseHistoryText = brain.loadMemory()
        chatHistoryView.text = baseHistoryText

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            statusText.text = "Статус: Дай разрешение на доступ ко всем файлам!"
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            statusText.text = "Статус: Офлайн режим (Память активна)"
        }

        sendButton.setOnClickListener {
            val text = userInput.text.toString().trim()
            if (text.isNotEmpty()) {
                userInput.text.clear()
                baseHistoryText += "Вы: $text\n\n"
                updateChatWithStatus("vang quer: Загрузка 0% [░░░░░░░░░░]")

                CoroutineScope(Dispatchers.IO).launch {
                    val progressJob = launch {
                        var percent = 0
                        while (isActive && percent < 99) {
                            val filled = percent / 10
                            val bar = "█".repeat(filled) + "░".repeat(10 - filled)
                            withContext(Dispatchers.Main) {
                                if (!brain.isModelLoaded()) {
                                    updateChatWithStatus("vang quer: Загружаю модель $percent% [$bar]")
                                }
                            }
                            delay(250)
                            percent += if (percent < 70) 3 else 1
                        }
                    }

                    val response = brain.askAI(text) { status ->
                        progressJob.cancel()
                        withContext(Dispatchers.Main) {
                            statusText.text = "Статус: ${brain.statusMessage}"
                            updateChatWithStatus("vang quer: $status")
                        }
                    }

                    progressJob.cancel()
                    withContext(Dispatchers.Main) {
                        statusText.text = "Статус: ${brain.statusMessage}"
                        baseHistoryText += "vang quer: $response\n\n"
                        chatHistoryView.text = baseHistoryText
                        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            }
        }
    }

    private fun updateChatWithStatus(tempLine: String) {
        chatHistoryView.text = baseHistoryText + tempLine
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}

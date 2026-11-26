package com.myai.assistant

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var brain: BrainRouter
    private lateinit var chatHistoryView: TextView
    private lateinit var chatScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        brain = BrainRouter(this)
        chatHistoryView = findViewById(R.id.chatHistoryView)
        chatScroll = findViewById(R.id.chatScroll)
        val userInput = findViewById<EditText>(R.id.userInput)
        val sendButton = findViewById<Button>(R.id.sendButton)

        chatHistoryView.text = brain.loadMemory()

        sendButton.setOnClickListener {
            val text = userInput.text.toString().trim()
            if (text.isNotEmpty()) {
                userInput.text.clear()
                appendMessage("Вы: $text")

                CoroutineScope(Dispatchers.IO).launch {
                    val response = brain.askAI(text, isOnlineMode = false)
                    withContext(Dispatchers.Main) {
                        appendMessage("ИИ: $response")
                    }
                }
            }
        }
    }

    private fun appendMessage(msg: String) {
        chatHistoryView.append("$msg\n\n")
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}

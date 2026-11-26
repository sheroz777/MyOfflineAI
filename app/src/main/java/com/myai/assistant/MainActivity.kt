package com.myai.assistant

import android.Manifest
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
import androidx.core.app.ActivityCompat
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

        requestAllPermissions()

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

    private fun requestAllPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            101
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
    }

    private fun appendMessage(msg: String) {
        chatHistoryView.append("$msg\n\n")
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
